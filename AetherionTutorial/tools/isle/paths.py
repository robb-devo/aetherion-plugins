"""The ways of the island: gentle profiles cut into the ground, half-step slabs at every rise, earthy surfaces.

carve(t) runs before the ground is painted (it levels the ground under each way), pave(w, t) after.
"""
import math

import numpy as np

import layout as L
from noise import value, hash01
from terrain import chaikin

KINDS = {"trail": (1, 3.0), "road": (2, 3.2), "lane": (1, 2.0)}
MAX_RISE = 0.21          # per half block of way: a rise of one block every ~2.4 blocks at most


def resample(pts, spacing=0.5):
    out = [pts[0]]
    for i in range(len(pts) - 1):
        ax, az = pts[i][:2]
        bx, bz = pts[i + 1][:2]
        n = max(1, int(math.hypot(bx - ax, bz - az) / spacing))
        for k in range(1, n + 1):
            out.append((ax + (bx - ax) * k / n, az + (bz - az) * k / n))
    return out


def centre_line(pts):
    return resample(chaikin(pts, 2, closed=False))


def carve(t):
    H = t.H
    t.path_level = np.zeros(H.shape, np.int32)
    t.path_dist = np.full(H.shape, 99.0)
    t.path_lines = {}
    for name, kind, pts in L.PATHS:
        code, width = KINDS[kind]
        line = centre_line(pts)
        t.path_lines[name] = line
        raw = []
        for (x, z) in line:
            xi, zi = int(math.floor(x)), int(math.floor(z))
            raw.append(float(H[zi - L.Z0, xi - L.X0]))
        raw = np.array(raw)
        # smooth along the way, then limit the gradient in both directions
        k = 13
        padded = np.pad(raw, k // 2, mode="edge")
        prof = np.convolve(padded, np.ones(k) / k, mode="valid")
        prof[0], prof[-1] = raw[0], raw[-1]
        for i in range(1, len(prof)):
            prof[i] = min(max(prof[i], prof[i - 1] - MAX_RISE), prof[i - 1] + MAX_RISE)
        for i in range(len(prof) - 2, -1, -1):
            prof[i] = min(max(prof[i], prof[i + 1] - MAX_RISE), prof[i + 1] + MAX_RISE)
        levels = np.floor(prof + 0.5).astype(int)
        r = width / 2.0
        reach = int(math.ceil(r + 2))
        for (x, z), lv in zip(line, levels):
            cx, cz = int(math.floor(x)), int(math.floor(z))
            for dz in range(-reach, reach + 1):
                for dx in range(-reach, reach + 1):
                    px, pz = cx + dx, cz + dz
                    if not t.inb(px, pz):
                        continue
                    zi, xi = pz - L.Z0, px - L.X0
                    d = math.hypot(px + 0.5 - x, pz + 0.5 - z)
                    if d >= t.path_dist[zi, xi]:
                        continue
                    if t.stream[zi, xi] or not t.land[zi, xi]:
                        continue
                    if d <= r:
                        t.path_dist[zi, xi] = d
                        t.path_level[zi, xi] = lv
                        t.path[zi, xi] = max(code, t.path[zi, xi]) if t.path[zi, xi] else code
                    elif d <= r + 1.6 and t.path[zi, xi] == 0:
                        # shoulder: ease big steps beside the way (never under water level changes)
                        if abs(int(H[zi, xi]) - lv) >= 2:
                            H[zi, xi] = int(round((int(H[zi, xi]) + lv) / 2.0))
    on = t.path > 0
    H[on] = t.path_level[on]
    # keep trees off the ways
    p = np.pad(on, 2)
    near = np.zeros(on.shape, bool)
    for dz in range(5):
        for dx in range(5):
            near |= p[dz:dz + on.shape[0], dx:dx + on.shape[1]]
    t.keep_clear |= near


def pave(w, t):
    H = t.H
    seed = L.SEED
    zs, xs = np.nonzero(t.path > 0)
    slab_names = ("cobblestone_slab", "cobblestone_slab", "cobblestone_slab", "mossy_cobblestone_slab",
                  "andesite_slab")
    for zi, xi in zip(zs.tolist(), xs.tolist()):
        x, z = xi + L.X0, zi + L.Z0
        y = int(H[zi, xi])
        code = int(t.path[zi, xi])
        d = float(t.path_dist[zi, xi])
        patch = float(value(x, z, 4.0, seed + 71))
        speck = float(hash01(x, 0, z, seed + 73))
        edge = d > (1.05 if code == 1 else 1.15)
        if code == 1:
            if edge and speck < 0.35:
                block = "grass_block"
            elif edge and speck < 0.6:
                block = "coarse_dirt"
            elif patch > 0.80:
                block = "gravel"
            elif patch < 0.16:
                block = "coarse_dirt"
            elif speck < 0.06:
                block = "rooted_dirt"
            else:
                block = "dirt_path"
        else:
            if edge and speck < 0.3:
                block = "dirt_path"
            elif patch > 0.78:
                block = "gravel"
            elif patch < 0.2:
                block = "andesite"
            elif speck < 0.10:
                block = "mossy_cobblestone"
            else:
                block = "cobblestone"
        w.set(x, y, z, block)
        if w.name(x, y - 1, z) in ("grass_block", "air", "water"):
            w.set(x, y - 1, z, "dirt")
        # clear anything above
        for yy in range(y + 1, y + 4):
            w.set(x, yy, z, "air")
        # a half step where the way rises
        rises = False
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            a, b = zi + dz, xi + dx
            if 0 <= a < H.shape[0] and 0 <= b < H.shape[1] and t.path[a, b] and int(H[a, b]) == y + 1:
                rises = True
        if rises:
            w.set(x, y + 1, z, slab_names[int(speck * 997) % len(slab_names)] + "[type=bottom]")
            if block == "dirt_path":
                w.set(x, y, z, "coarse_dirt")


def lamps(w, t, spacing=10.0):
    """Lantern posts along the ways the lessons follow (layout.ROUTE): the island's own breadcrumb trail.
    They stand on the shoulder, on alternating sides, and keep away from lamps the places set up themselves."""
    placed = []
    ground_ok = ("grass_block", "dirt", "coarse_dirt", "podzol", "moss_block", "rooted_dirt")

    def lit_nearby(x, y, z, r=6):
        for (px, py, pz) in placed:
            if abs(px - x) <= r + 2 and abs(pz - z) <= r + 2:
                return True
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                for dy in range(0, 6):
                    if w.name(x + dx, y + dy, z + dz) == "lantern":
                        return True
        return False

    side = 1
    for name in L.ROUTE:
        line = t.path_lines.get(name)
        if not line:
            continue
        travelled = spacing * 0.5
        for i in range(1, len(line) - 1):
            (ax, az), (bx, bz) = line[i - 1], line[i]
            step = math.hypot(bx - ax, bz - az)
            travelled += step
            if travelled < spacing or step == 0:
                continue
            nx, nz = -(bz - az) / step, (bx - ax) / step          # the normal of the way here
            for off in (2.4, 2.9, 3.4):
                x = int(round(bx + nx * off * side))
                z = int(round(bz + nz * off * side))
                if not t.inb(x, z) or not t.is_land(x, z) or t.path[z - L.Z0, x - L.X0] or t.quarry[z - L.Z0, x - L.X0]:
                    continue
                y = t.h(x, z)
                if w.name(x, y, z) not in ground_ok or not w.is_air(x, y + 1, z) or not w.is_air(x, y + 2, z):
                    continue
                if abs(y - int(t.path_level[int(round(bz)) - L.Z0, int(round(bx)) - L.X0])) > 1:
                    continue
                if lit_nearby(x, y + 1, z):
                    travelled = spacing * 0.6               # one is close: try again a little further on
                    break
                w.set(x, y + 1, z, "cobblestone_wall")
                w.set(x, y + 2, z, "spruce_fence")
                w.set(x, y + 3, z, "spruce_fence")
                w.set(x, y + 4, z, "lantern[hanging=false]")
                placed.append((x, y + 1, z))
                t.keep_clear[max(z - 2 - L.Z0, 0):z + 3 - L.Z0, max(x - 2 - L.X0, 0):x + 3 - L.X0] = True
                travelled = 0.0
                side = -side
                break
    return placed
