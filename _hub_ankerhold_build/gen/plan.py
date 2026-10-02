"""Terraform layer: pads (flattened building plots) and paths, applied to the heightmap before voxelising."""
from __future__ import annotations

import math

import numpy as np
from scipy.spatial import cKDTree

from terrain import GX, GZ, smoothstep, dist
from world import X0, Z0, NX, NZ, World, stairs, slab


class Plan:
    def __init__(self, T):
        self.T = T
        self.paths = []
        T.occ = np.zeros((NX, NZ), dtype=bool)        # no vegetation here
        T.pathmat = np.full((NX, NZ), -1, dtype=np.int32)  # index into self.mats
        T.pathstep = np.full((NX, NZ), -1, dtype=np.int32)
        T.pathy = np.full((NX, NZ), np.nan)
        self.mats = []

    # ------------------------------------------------------------------ pads
    def rect(self, x1, z1, x2, z2, y, blend=3.0, mode="set"):
        T = self.T
        xa, xb = sorted((x1, x2))
        za, zb = sorted((z1, z2))
        dx = np.maximum(np.maximum(xa - GX, GX - xb), 0)
        dz = np.maximum(np.maximum(za - GZ, GZ - zb), 0)
        d = np.hypot(dx, dz)
        w = smoothstep(blend + 0.5, 0.0, d) if blend > 0 else (d <= 0).astype(float)
        w = np.where(T.island_all, w, 0)
        if mode == "max":
            T.h = np.where(w > 0, np.maximum(T.h, T.h * (1 - w) + y * w), T.h)
        elif mode == "min":
            T.h = np.where(w > 0, np.minimum(T.h, T.h * (1 - w) + y * w), T.h)
        else:
            T.h = T.h * (1 - w) + y * w
        inside = (d <= 0) & T.island_all
        T.h = np.where(inside, y, T.h)
        T.occ |= inside
        return inside

    def circle(self, cx, cz, r, y, blend=3.0, mode="set"):
        T = self.T
        d = np.maximum(dist(cx, cz) - r, 0)
        w = smoothstep(blend + 0.5, 0.0, d) if blend > 0 else (d <= 0).astype(float)
        w = np.where(T.island_all, w, 0)
        if mode == "max":
            T.h = np.where(w > 0, np.maximum(T.h, T.h * (1 - w) + y * w), T.h)
        elif mode == "min":
            T.h = np.where(w > 0, np.minimum(T.h, T.h * (1 - w) + y * w), T.h)
        else:
            T.h = T.h * (1 - w) + y * w
        inside = (d <= 0) & T.island_all
        T.h = np.where(inside, y, T.h)
        T.occ |= inside
        return inside

    def occupy_rect(self, x1, z1, x2, z2):
        xa, xb = sorted((x1, x2))
        za, zb = sorted((z1, z2))
        self.T.occ |= (GX >= xa) & (GX <= xb) & (GZ >= za) & (GZ <= zb)

    # ------------------------------------------------------------------ paths
    def path(self, pts, width=3.0, mats=(("dirt_path", 1.0),), step="mud_brick", smooth=6, maxgrade=0.5,
             blend=2.5, set_height=True, seed=0, edge_mats=None):
        """pts: list of (x, z) or (x, z, y). Heights interpolate between given y; free vertices follow terrain."""
        T = self.T
        mi = len(self.mats)
        self.mats.append(dict(mats=mats, step=step, edge=edge_mats, seed=seed))
        # densify
        P = []
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            L = math.dist(a[:2], b[:2])
            n = max(1, int(L / 0.5))
            for k in range(n):
                t = k / n
                P.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, i, t))
        P.append((pts[-1][0], pts[-1][1], len(pts) - 2, 1.0))
        P = np.array(P)
        xsP, zsP = P[:, 0], P[:, 1]
        # terrain heights along path
        ix = np.clip(np.round(xsP).astype(int) - X0, 0, NX - 1)
        iz = np.clip(np.round(zsP).astype(int) - Z0, 0, NZ - 1)
        th = np.nan_to_num(T.h[ix, iz], nan=64)
        # moving average
        k = max(1, int(smooth / 0.5))
        ker = np.ones(2 * k + 1) / (2 * k + 1)
        pad = np.concatenate([np.full(k, th[0]), th, np.full(k, th[-1])])
        ys = np.convolve(pad, ker, mode="valid")
        # explicit heights: piecewise-linear between vertices having y
        given = [(i, p[2]) for i, p in enumerate(pts) if len(p) > 2 and p[2] is not None]
        if given:
            # cumulative distance at each vertex
            seg = P[:, 2].astype(int)
            cum = np.concatenate([[0], np.cumsum(np.hypot(np.diff(xsP), np.diff(zsP)))])
            vcum = []
            for vi in range(len(pts)):
                idx = np.nonzero((seg == vi) & (P[:, 3] == 0))[0]
                if vi == len(pts) - 1:
                    idx = [len(P) - 1]
                vcum.append(cum[idx[0]] if len(idx) else cum[-1])
            gx = [vcum[i] for i, _ in given]
            gy = [y for _, y in given]
            yi = np.interp(cum, gx, gy)
            # where between two given vertices: use interpolation; outside: blend into terrain
            first, last = gx[0], gx[-1]
            inside = (cum >= first) & (cum <= last)
            ys = np.where(inside, yi, ys)
            # outside ranges: anchor to nearest given and limit grade
        # grade limit (both directions)
        ds = 0.5
        for _ in range(2):
            for i in range(1, len(ys)):
                ys[i] = np.clip(ys[i], ys[i - 1] - maxgrade * ds, ys[i - 1] + maxgrade * ds)
            for i in range(len(ys) - 2, -1, -1):
                ys[i] = np.clip(ys[i], ys[i + 1] - maxgrade * ds, ys[i + 1] + maxgrade * ds)
        if given:
            ys = np.where(inside, yi, ys)
        # rasterise
        tree = cKDTree(np.column_stack([xsP, zsP]))
        x1, x2 = int(xsP.min() - width - blend - 2), int(xsP.max() + width + blend + 2)
        z1, z2 = int(zsP.min() - width - blend - 2), int(zsP.max() + width + blend + 2)
        x1, x2 = max(x1, X0), min(x2, X0 + NX - 1)
        z1, z2 = max(z1, Z0), min(z2, Z0 + NZ - 1)
        sx = slice(x1 - X0, x2 - X0 + 1)
        sz = slice(z1 - Z0, z2 - Z0 + 1)
        cx = GX[sx, sz] + 0.0
        cz = GZ[sx, sz] + 0.0
        d, j = tree.query(np.column_stack([cx.ravel(), cz.ravel()]))
        d = d.reshape(cx.shape)
        j = j.reshape(cx.shape)
        yy = ys[j]
        half = width / 2.0
        core = d <= half
        isl = T.island_all[sx, sz]
        if set_height:
            w = np.where(core, 1.0, smoothstep(half + blend, half, d))
            w = np.where(isl, w, 0)
            hcur = T.h[sx, sz]
            T.h[sx, sz] = np.where(w > 0, hcur * (1 - w) + yy * w, hcur)
        sel = core & isl
        T.pathmat[sx, sz] = np.where(sel, mi, T.pathmat[sx, sz])
        T.pathy[sx, sz] = np.where(sel, yy, T.pathy[sx, sz])
        T.occ[sx, sz] |= (d <= half + 1) & isl
        self.paths.append(dict(pts=pts, width=width, ys=ys, P=P))
        return ys

    # ------------------------------------------------------------------ after voxelise
    def surface(self, W: World, rng=None):
        """Paint path materials onto the top block and add half-step slabs."""
        T = self.T
        rng = rng or np.random.default_rng(3)
        top = np.round(np.nan_to_num(T.h, nan=-999)).astype(int)
        T.top = top
        r = rng.random(top.shape)
        cells = np.argwhere(T.pathmat >= 0)
        for ix, iz in cells:
            m = self.mats[T.pathmat[ix, iz]]
            x, z, y = ix + X0, iz + Z0, top[ix, iz]
            if y < -60:
                continue
            # weighted pick
            acc, pick = 0.0, m["mats"][0][0]
            rv = r[ix, iz]
            tot = sum(wt for _, wt in m["mats"])
            for name, wt in m["mats"]:
                acc += wt / tot
                if rv <= acc:
                    pick = name
                    break
            cur = W.getn(x, y, z)
            if "water" in cur:
                continue
            W.set(x, y, z, pick)
            # clear plants above
            above = W.getn(x, y + 1, z)
            if above != "minecraft:air" and ("grass" in above or "snow" in above or "fern" in above):
                W.set(x, y + 1, z, 0)
        # steps: on path cells, if a 4-neighbour path cell is exactly +1 higher, add slab / stairs
        for ix, iz in cells:
            m = self.mats[T.pathmat[ix, iz]]
            if not m["step"]:
                continue
            y = top[ix, iz]
            face = None
            for dx, dz, f in ((1, 0, "east"), (-1, 0, "west"), (0, 1, "south"), (0, -1, "north")):
                jx, jz = ix + dx, iz + dz
                if 0 <= jx < NX and 0 <= jz < NZ and T.pathmat[jx, jz] >= 0 and top[jx, jz] >= y + 1:
                    face = f
                    break
            if face:
                x, z = ix + X0, iz + Z0
                if W.is_air(x, y + 1, z) or "slab" in W.getn(x, y + 1, z):
                    st = m["step"]
                    if st.startswith("stairs:"):
                        W.set(x, y + 1, z, stairs(st[7:], face))
                    else:
                        W.set(x, y + 1, z, slab(st, "bottom"))
