"""Sky-isle hub generator.

Builds the whole hub (main floating island, Pit arena island, islets, clouds) into one voxel volume
and exports it as a vanilla structure template (placed with /place template on a void world).

  python gen_skyisle.py <outdir>

Writes <outdir>/skyisle.nbt, <outdir>/skyisle.npz (for render.py) and <outdir>/skyisle.json
(placement corner + the coordinates the AetherionPit config needs).
"""
import json
import math
import os
import sys

import numpy as np

from nbtio import Byte, Compound, Long, String, List, write_structure

X0, Y0, Z0 = -120, -30, -115
SX, SY, SZ = 330, 165, 230


# ----------------------------------------------------------------------------- noise

def _h(ix, iy, iz, seed):
    ix = np.asarray(ix, dtype=np.int64)
    iy = np.asarray(iy, dtype=np.int64)
    iz = np.asarray(iz, dtype=np.int64)
    h = (ix * 374761393 + iy * 668265263 + iz * 1440670441 + seed * 1013904223) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0xFFFFFF)


def rnd(x, y, z, seed=0):
    """deterministic 0..1 per position"""
    return _h(x, y, z, seed + 7)


def vnoise(x, y, z, scale, seed=0):
    x = np.asarray(x, dtype=np.float64) / scale
    y = np.asarray(y, dtype=np.float64) / scale
    z = np.asarray(z, dtype=np.float64) / scale
    x0, y0, z0 = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - x0, y - y0, z - z0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    fz = fz * fz * (3 - 2 * fz)
    x0, y0, z0 = x0.astype(np.int64), y0.astype(np.int64), z0.astype(np.int64)
    out = 0.0
    for dx in (0, 1):
        wx = fx if dx else 1 - fx
        for dy in (0, 1):
            wy = fy if dy else 1 - fy
            for dz in (0, 1):
                wz = fz if dz else 1 - fz
                out = out + wx * wy * wz * _h(x0 + dx, y0 + dy, z0 + dz, seed)
    return out * 2 - 1


def fbm(x, y, z, scale, octaves=3, seed=0):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * vnoise(x, y, z, scale / (2 ** o), seed + o * 31)
        norm += amp
        amp *= 0.5
    return total / norm


# ----------------------------------------------------------------------------- volume

class Vol:
    def __init__(self):
        self.a = np.zeros((SX, SY, SZ), np.int32)
        self.pal = ["air"]
        self.idx = {"air": 0}
        self.nbt = {}

    def p(self, s):
        i = self.idx.get(s)
        if i is None:
            i = len(self.pal)
            self.pal.append(s)
            self.idx[s] = i
        return i

    def set(self, x, y, z, s):
        ix, iy, iz = int(x) - X0, int(y) - Y0, int(z) - Z0
        if 0 <= ix < SX and 0 <= iy < SY and 0 <= iz < SZ:
            self.a[ix, iy, iz] = self.p(s) if isinstance(s, str) else s

    def get(self, x, y, z):
        ix, iy, iz = int(x) - X0, int(y) - Y0, int(z) - Z0
        if 0 <= ix < SX and 0 <= iy < SY and 0 <= iz < SZ:
            return self.pal[self.a[ix, iy, iz]]
        return "air"

    def air(self, x, y, z):
        return self.get(x, y, z) == "air"

    def solid(self, x, y, z):
        n = self.get(x, y, z).split("[")[0]
        return n != "air" and n not in SOFT

    def fill(self, x1, y1, z1, x2, y2, z2, s):
        xa, xb = sorted((int(x1), int(x2)))
        ya, yb = sorted((int(y1), int(y2)))
        za, zb = sorted((int(z1), int(z2)))
        xa, xb = max(xa, X0), min(xb, X0 + SX - 1)
        ya, yb = max(ya, Y0), min(yb, Y0 + SY - 1)
        za, zb = max(za, Z0), min(zb, Z0 + SZ - 1)
        if xa > xb or ya > yb or za > zb:
            return
        self.a[xa - X0:xb - X0 + 1, ya - Y0:yb - Y0 + 1, za - Z0:zb - Z0 + 1] = self.p(s)

    def top(self, x, z, ymax=125, ymin=-25):
        for y in range(ymax, ymin, -1):
            if self.solid(x, y, z):
                return y
        return None


SOFT = {"short_grass", "tall_grass", "fern", "large_fern", "poppy", "dandelion", "allium", "azure_bluet",
        "oxeye_daisy", "cornflower", "lily_of_the_valley", "blue_orchid", "pink_tulip", "white_tulip",
        "red_tulip", "orange_tulip", "lilac", "peony", "rose_bush", "pink_petals", "moss_carpet", "vine",
        "glow_lichen", "hanging_roots", "cave_vines", "cave_vines_plant", "pointed_dripstone", "lily_pad",
        "torchflower", "water", "lava", "end_gateway", "lantern", "soul_lantern", "chain", "end_rod",
        "red_banner", "oak_sign", "cherry_sign", "candle", "white_candle", "pink_candle", "amethyst_cluster",
        "big_dripleaf", "big_dripleaf_stem", "azalea", "flowering_azalea", "sugar_cane", "red_carpet",
        "white_carpet", "pink_carpet", "soul_campfire", "campfire", "flower_pot", "light_blue_carpet"}

V = Vol()
INFO = {}


def choose(options, r):
    """options: [(state, weight)], r in 0..1"""
    tot = sum(w for _, w in options)
    acc = 0.0
    for s, w in options:
        acc += w / tot
        if r <= acc:
            return s
    return options[-1][0]


# ----------------------------------------------------------------------------- island core

def island(cx, cz, ax, az, top_fn, depth_max, seed, edge_noise=0.09, mats="light", protect=(), base=None, lobes=()):
    """Floating island. top_fn(X, Z) -> int top height array. Returns (mask, top) on its local grid."""
    xs = np.arange(int(cx - ax * 1.25), int(cx + ax * 1.25) + 1)
    zs = np.arange(int(cz - az * 1.25), int(cz + az * 1.25) + 1)
    X, Z = np.meshgrid(xs, zs, indexing="ij")
    d = np.sqrt(((X - cx) / ax) ** 2 + ((Z - cz) / az) ** 2)
    d = d + edge_noise * fbm(X, 0, Z, 17, 3, seed) + 0.04 * fbm(X, 5, Z, 6, 2, seed + 3)
    ang = np.arctan2(Z - cz, X - cx)
    for (la, ls, lw) in lobes:
        da = np.angle(np.exp(1j * (ang - math.radians(la))))
        d = d - ls * np.exp(-(da / lw) ** 2) * np.clip(d, 0, 1.4)
    for (px, pz, pr) in protect:
        d = np.where((X - px) ** 2 + (Z - pz) ** 2 < pr * pr, np.minimum(d, 0.8), d)
    inside = d < 1.0
    top = top_fn(X, Z).astype(np.int64)
    k = np.clip(1.0 - d, 0, 1)
    depth = 5 + depth_max * k ** 0.85 * (0.78 + 0.32 * fbm(X, 9, Z, 12, 3, seed + 11))
    # hanging spikes
    rng = np.random.default_rng(seed)
    for _ in range(int(ax * az / 110)):
        px, pz = rng.uniform(-0.8, 0.8) * ax + cx, rng.uniform(-0.8, 0.8) * az + cz
        pr = rng.uniform(2.0, 5.5)
        pl = rng.uniform(6, 24) * (0.4 + 0.6 * max(0.0, 1 - math.hypot((px - cx) / ax, (pz - cz) / az)))
        dd = np.sqrt((X - px) ** 2 + (Z - pz) ** 2)
        depth = depth + np.where(dd < pr, pl * np.clip(1 - dd / pr, 0, 1) ** 1.6, 0)
    bottom = ((top if base is None else base) - depth).astype(np.int64)

    ys = np.arange(int(bottom[inside].min()) if inside.any() else 0, int(top.max()) + 1)
    Y = ys[None, :, None]
    m = inside[:, None, :] & (Y >= bottom[:, None, :]) & (Y <= top[:, None, :])
    XX = X[:, None, :] + 0 * Y
    ZZ = Z[:, None, :] + 0 * Y
    YY = Y + 0 * X[:, None, :]
    depth_from_top = top[:, None, :] - YY
    n1 = fbm(XX, YY, ZZ, 9, 2, seed + 21)
    n2 = fbm(XX, YY * 2, ZZ, 5, 2, seed + 22)
    r = rnd(XX, YY, ZZ, seed)
    if mats == "light":
        names = ["grass_block", "dirt", "coarse_dirt", "stone", "andesite", "tuff", "calcite", "deepslate",
                 "cobbled_deepslate", "dripstone_block", "rooted_dirt", "moss_block", "polished_tuff"]
    else:
        names = ["blackstone", "basalt[axis=y]", "smooth_basalt", "blackstone", "tuff", "deepslate", "magma_block",
                 "cobbled_deepslate", "polished_blackstone", "dripstone_block", "coarse_dirt", "deepslate",
                 "crying_obsidian"]
    P = [V.p(s) for s in names]
    yrel = YY - bottom[:, None, :]
    span = np.maximum(1, top[:, None, :] - bottom[:, None, :])
    frac = yrel / span  # 0 bottom .. 1 top
    mat = np.full(m.shape, P[3], np.int32)
    if mats == "light":
        mat = np.where(n1 > 0.18, P[4], mat)
        mat = np.where((n2 > 0.55), P[6], mat)
        mat = np.where(frac < 0.45, np.where(n1 > -0.05, P[5], P[3]), mat)
        mat = np.where(frac < 0.22, np.where(n1 > 0.1, P[7], P[8]), mat)
        mat = np.where((frac < 0.6) & (n2 < -0.5), P[9], mat)
        mat = np.where((frac < 0.35) & (r < 0.08), P[12], mat)
        mat = np.where(depth_from_top <= 3, np.where(r < 0.12, P[2], P[1]), mat)
        mat = np.where((depth_from_top == 4) & (r < 0.4), P[10], mat)
        mat = np.where(depth_from_top == 0, P[0], mat)
    else:
        mat = np.where(n1 > 0.15, P[1], mat)
        mat = np.where(n2 > 0.5, P[2], mat)
        mat = np.where(frac < 0.4, np.where(n1 > 0, P[4], P[5]), mat)
        mat = np.where((frac < 0.25) & (n2 > 0.35), P[7], mat)
        mat = np.where((frac > 0.3) & (n2 < -0.55), P[6], mat)
        mat = np.where((r < 0.015), P[12], mat)
        mat = np.where(depth_from_top <= 1, np.where(r < 0.5, P[0], P[8]), mat)
        mat = np.where(depth_from_top == 0, P[10], mat)
    # write
    ix = (XX - X0)[m]
    iy = (YY - Y0)[m]
    iz = (ZZ - Z0)[m]
    ok = (ix >= 0) & (ix < SX) & (iy >= 0) & (iy < SY) & (iz >= 0) & (iz < SZ)
    V.a[ix[ok], iy[ok], iz[ok]] = mat[m][ok]
    return X, Z, inside, top, bottom


def underside_decor(X, Z, inside, top, bottom, seed, dark=False):
    rng = np.random.default_rng(seed + 99)
    nx, nz = inside.shape
    for i in range(nx):
        for j in range(nz):
            if not inside[i, j]:
                continue
            x, z, b, t = int(X[i, j]), int(Z[i, j]), int(bottom[i, j]), int(top[i, j])
            r = rng.random()
            shallow = (t - b) < 12
            if r < 0.07:
                for k in range(1, rng.integers(2, 6)):
                    V.set(x, b - k, z, "pointed_dripstone[vertical_direction=down,thickness=tip,waterlogged=false]")
            elif r < 0.13 and not dark:
                ln = int(rng.integers(2, 10 if shallow else 7))
                for k in range(1, ln + 1):
                    berries = "true" if rng.random() < 0.35 else "false"
                    st = "cave_vines[age=25,berries=%s]" % berries if k == ln else "cave_vines_plant[berries=%s]" % berries
                    V.set(x, b - k, z, st)
            elif r < 0.17:
                V.set(x, b - 1, z, "glow_lichen[down=false,up=true,north=false,south=false,east=false,west=false,waterlogged=false]")
            elif r < 0.21 and shallow and not dark:
                V.set(x, b - 1, z, "hanging_roots[waterlogged=false]")
    # vines on the cliff edges
    if dark:
        return
    for i in range(1, nx - 1):
        for j in range(1, nz - 1):
            if not inside[i, j]:
                continue
            x, z, t = int(X[i, j]), int(Z[i, j]), int(top[i, j])
            for (di, dj, face) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
                if inside[i + di, j + dj]:
                    continue
                if rng.random() < 0.32:
                    vx, vz = x + di, z + dj
                    ln = int(rng.integers(2, 9))
                    for k in range(0, ln):
                        y = t - k
                        if not V.air(vx, y, vz):
                            break
                        if not V.solid(x, y, z):
                            break
                        V.set(vx, y, vz, "vine[up=false,north=%s,south=%s,east=%s,west=%s]" % tuple(
                            "true" if f == face else "false" for f in ("north", "south", "east", "west")))


def geode(cx, cy, cz, r, seed):
    rng = np.random.default_rng(seed)
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2)
                if d <= r - 1:
                    V.set(x, y, z, "amethyst_block" if rng.random() < 0.85 else "budding_amethyst")
                elif d <= r and not V.air(x, y, z):
                    V.set(x, y, z, "calcite")
                elif d <= r and V.air(x, y, z) and y < cy:
                    V.set(x, y, z, "amethyst_block")
    # clusters on exposed undersides
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            for y in range(int(cy - r - 1), int(cy + 1)):
                if V.get(x, y, z).startswith("amethyst_block") and V.air(x, y - 1, z) and rng.random() < 0.45:
                    V.set(x, y - 1, z, "amethyst_cluster[facing=down,waterlogged=false]")
                    break


# ----------------------------------------------------------------------------- small helpers

def disc_iter(cx, cz, r):
    for x in range(int(math.floor(cx - r)), int(math.ceil(cx + r)) + 1):
        for z in range(int(math.floor(cz - r)), int(math.ceil(cz + r)) + 1):
            d = math.hypot(x - cx, z - cz)
            if d <= r:
                yield x, z, d


def column_fill(x, z, y_top, y_floor, s="stone"):
    """fill below y_top down to existing ground / y_floor"""
    for y in range(y_top, y_floor - 1, -1):
        if V.solid(x, y, z) and y < y_top:
            break
        V.set(x, y, z, s)


def paver(x, z, seed=0, grey=False):
    t = rnd(x // 2, 0, z // 2, seed)
    r = rnd(x, 1, z, seed)
    if grey:
        s = choose([("polished_andesite", 3), ("andesite", 1), ("stone", 1), ("polished_diorite", 2)], t)
    else:
        s = choose([("calcite", 4), ("polished_diorite", 3), ("smooth_quartz", 1.2), ("quartz_bricks", 1)], t)
    if r < 0.05:
        s = "mossy_stone_bricks" if not grey else "mossy_cobblestone"
    return s


def lamp_post(x, y, z, kind="lantern"):
    V.set(x, y, z, "chiseled_quartz_block")
    V.set(x, y + 1, z, "diorite_wall")
    V.set(x, y + 2, z, "diorite_wall")
    V.set(x, y + 3, z, "diorite_wall")
    if kind == "lantern":
        V.set(x, y + 4, z, "lantern[hanging=false,waterlogged=false]")
    elif kind == "soul":
        V.set(x, y + 4, z, "soul_lantern[hanging=false,waterlogged=false]")
    else:
        V.set(x, y + 4, z, kind)


def stairs(name, facing, half="bottom"):
    return "%s[facing=%s,half=%s,shape=straight,waterlogged=false]" % (name, facing, half)


def line3(a, b):
    (x1, y1, z1), (x2, y2, z2) = a, b
    n = int(max(abs(x2 - x1), abs(y2 - y1), abs(z2 - z1))) + 1
    for i in range(n + 1):
        t = i / max(1, n)
        yield (round(x1 + (x2 - x1) * t), round(y1 + (y2 - y1) * t), round(z1 + (z2 - z1) * t))


def blob(cx, cy, cz, rx, ry, rz, state_fn, seed, rough=0.25, only_air=True):
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                if d <= 1 + rough * float(vnoise(x, y, z, 2.3, seed)):
                    if only_air and not V.air(x, y, z):
                        continue
                    V.set(x, y, z, state_fn(x, y, z))


# ----------------------------------------------------------------------------- trees

def cherry_tree(x, y, z, seed, h=None, spread=1.0):
    """y = ground block; trunk starts at y+1"""
    rng = np.random.default_rng(seed)
    h = h or int(rng.integers(6, 9))
    lean = rng.uniform(0, 2 * math.pi)
    lx, lz = math.cos(lean) * 0.18, math.sin(lean) * 0.18
    pts = []
    for k in range(1, h + 1):
        px, pz = x + lx * k, z + lz * k
        pts.append((round(px), y + k, round(pz)))
    for p in pts:
        V.set(*p, "cherry_log[axis=y]")
    # roots flare
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if rng.random() < 0.6:
            V.set(x + dx, y + 1, z + dz, "cherry_wood[axis=y]")
    tx, ty, tz = pts[-1]
    nb = int(rng.integers(3, 5))
    base_a = rng.uniform(0, 2 * math.pi)
    for b in range(nb):
        a = base_a + b * 2 * math.pi / nb + rng.uniform(-0.4, 0.4)
        ln = rng.uniform(3.0, 5.0) * spread
        up = rng.uniform(1.5, 3.5)
        sy = ty - int(rng.integers(0, 3))
        ex, ey, ez = tx + math.cos(a) * ln, sy + up, tz + math.sin(a) * ln
        prev = None
        for p in line3((tx, sy, tz), (ex, ey, ez)):
            if prev is not None:
                dx, dy, dz = abs(p[0] - prev[0]), abs(p[1] - prev[1]), abs(p[2] - prev[2])
                axis = "y" if dy >= max(dx, dz) else ("x" if dx >= dz else "z")
            else:
                axis = "y"
            V.set(*p, "cherry_log[axis=%s]" % axis)
            prev = p
        rx = rng.uniform(3.0, 4.2) * spread
        blob(ex, ey + 0.5, ez, rx, rng.uniform(1.8, 2.6), rx * rng.uniform(0.85, 1.15),
             lambda X, Y, Z: "cherry_leaves[distance=1,persistent=true,waterlogged=false]", seed + b, 0.35)
    blob(tx, ty + 2, tz, 3.2 * spread, 2.2, 3.2 * spread,
         lambda X, Y, Z: "cherry_leaves[distance=1,persistent=true,waterlogged=false]", seed + 50, 0.3)
    # drooping strands
    for _ in range(int(14 * spread)):
        sx, sz = tx + rng.uniform(-5, 5) * spread, tz + rng.uniform(-5, 5) * spread
        sx, sz = round(sx), round(sz)
        for yy in range(ty + 6, ty - 4, -1):
            if V.get(sx, yy, sz).startswith("cherry_leaves") and V.air(sx, yy - 1, sz):
                for k in range(1, int(rng.integers(1, 4))):
                    if V.air(sx, yy - k, sz):
                        V.set(sx, yy - k, sz, "cherry_leaves[distance=1,persistent=true,waterlogged=false]")
                break
    # petals on the ground
    for _ in range(int(40 * spread)):
        px, pz = round(x + rng.normal(0, 3.2 * spread)), round(z + rng.normal(0, 3.2 * spread))
        gy = V.top(px, pz, y + 4, y - 4)
        if gy is not None and V.get(px, gy, pz).startswith(("grass_block", "moss_block")) and V.air(px, gy + 1, pz):
            V.set(px, gy + 1, pz, "pink_petals[facing=%s,flower_amount=%d]" % (
                ("north", "south", "east", "west")[int(rng.integers(0, 4))], int(rng.integers(1, 5))))


def azalea_bush(x, y, z, seed, r=1.6):
    rng = np.random.default_rng(seed)
    V.set(x, y + 1, z, "oak_log[axis=y]")
    blob(x, y + 2, z, r, r * 0.8, r, lambda X, Y, Z: (
        "flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]" if rnd(X, Y, Z, seed) < 0.4
        else "azalea_leaves[distance=1,persistent=true,waterlogged=false]"), seed, 0.3)


def ground_cover(X, Z, inside, seed, exclude_fn):
    rng = np.random.default_rng(seed)
    flowers = [("poppy", 1), ("dandelion", 1), ("allium", 1.4), ("azure_bluet", 1.4), ("oxeye_daisy", 1.6),
               ("cornflower", 1.2), ("lily_of_the_valley", 1.4), ("pink_tulip", 1.2), ("white_tulip", 1.2)]
    tall = [("lilac", 1), ("peony", 1.3), ("rose_bush", 0.5)]
    nx, nz = inside.shape
    for i in range(nx):
        for j in range(nz):
            if not inside[i, j]:
                continue
            x, z = int(X[i, j]), int(Z[i, j])
            if exclude_fn(x, z):
                continue
            t = V.top(x, z, 90, 40)
            if t is None or not V.get(x, t, z).startswith("grass_block") or not V.air(x, t + 1, z):
                continue
            meadow = float(fbm(x, 0, z, 9, 2, seed + 5))
            r = rng.random()
            if r < 0.30:
                V.set(x, t + 1, z, "short_grass")
            elif r < 0.36:
                V.set(x, t + 1, z, "tall_grass[half=lower]")
                V.set(x, t + 2, z, "tall_grass[half=upper]")
            elif r < 0.36 + max(0.02, 0.16 * meadow):
                V.set(x, t + 1, z, choose(flowers, rng.random()))
            elif r < 0.62 and meadow > 0.35 and rng.random() < 0.2:
                f = choose(tall, rng.random())
                V.set(x, t + 1, z, f + "[half=lower]")
                V.set(x, t + 2, z, f + "[half=upper]")
            elif r < 0.66:
                V.set(x, t, z, "moss_block")
                if rng.random() < 0.5:
                    V.set(x, t + 1, z, "moss_carpet")


# ----------------------------------------------------------------------------- gates

def gateway_nbt():
    return Compound({"id": String("minecraft:end_gateway"), "Age": Long(1200), "ExactTeleport": Byte(0)})


def put_gateway(x, y, z):
    V.set(x, y, z, "end_gateway")
    V.nbt[(x, y, z)] = gateway_nbt()


def arch_gate(cx, fy, cz, normal, w, sh, theme, depth=1):
    """Arch gate. normal 'x' = gate plane is x=cx (opening spans z), 'z' = plane z=cz.
    w: opening width (odd), sh: straight side height, top is a half circle."""
    hw = w / 2.0
    R = hw
    t1, t2 = 1.0, 2.2  # inner rim, outer body thickness
    cells = []
    for u in range(-int(hw + t2 + 2), int(hw + t2 + 3)):
        for v in range(1, int(sh + R + t2 + 3)):
            if v <= sh:
                dist = abs(u) - hw + 0.5  # >0 outside opening
                inner = abs(u) <= hw - 0.5
            else:
                rr = math.hypot(u, v - sh)
                dist = rr - R + 0.5
                inner = rr <= R - 0.5
            cells.append((u, v, inner, dist))

    def W(u, v, n):
        return (cx + n, fy + v, cz + u) if normal == "x" else (cx + u, fy + v, cz + n)

    m = THEMES[theme]
    for u, v, inner, dist in cells:
        if inner:
            put_gateway(*W(u, v, 0))
            continue
        if dist <= t1:
            for n in range(-depth, depth + 1):
                V.set(*W(u, v, n), m["rim"])
        elif dist <= t1 + t2:
            for n in range(-depth, depth + 1):
                s = m["body"] if (n == 0 or (u + v) % 7) else m["accent"]
                V.set(*W(u, v, n), s)
            if abs(dist - (t1 + t2 * 0.55)) < 0.5:
                for n in (-depth, depth):
                    V.set(*W(u, v, n), m["band"])
    # keystone + side pillars
    topv = int(sh + R + t1 + t2)
    for n in range(-depth - 1, depth + 2):
        V.set(*W(0, topv, n), m["key"])
        V.set(*W(0, topv - 1, n), m["key"])
    V.set(*W(0, topv + 1, 0), m["crown"])
    for s in (-1, 1):
        pu = s * int(hw + t2 + 2)
        for v in range(1, int(sh) + 3):
            for n in (-depth, 0, depth):
                V.set(*W(pu, v, n), m["pillar"])
        V.set(*W(pu, int(sh) + 3, 0), m["cap"])
        V.set(*W(pu, int(sh) + 4, 0), m["light"])
    return {"u_half": hw, "height": sh + R}


THEMES = {
    "discord": {"rim": "sea_lantern", "body": "prismarine_bricks", "accent": "dark_prismarine",
                "band": "light_blue_glazed_terracotta", "key": "dark_prismarine", "crown": "sea_lantern",
                "pillar": "dark_prismarine", "cap": "prismarine_bricks",
                "light": "soul_lantern[hanging=false,waterlogged=false]"},
    "modpack": {"rim": "verdant_froglight", "body": "mossy_stone_bricks", "accent": "waxed_oxidized_cut_copper",
                "band": "waxed_oxidized_copper", "key": "waxed_oxidized_chiseled_copper", "crown": "verdant_froglight",
                "pillar": "mossy_stone_bricks", "cap": "chiseled_stone_bricks",
                "light": "lantern[hanging=false,waterlogged=false]"},
    "shop": {"rim": "ochre_froglight", "body": "stripped_birch_wood[axis=y]", "accent": "gold_block",
             "band": "raw_gold_block", "key": "gold_block", "crown": "ochre_froglight",
             "pillar": "stripped_oak_log[axis=y]", "cap": "stripped_oak_wood[axis=y]",
             "light": "lantern[hanging=false,waterlogged=false]"},
    "pit": {"rim": "crying_obsidian", "body": "polished_blackstone_bricks", "accent": "red_nether_bricks",
            "band": "gilded_blackstone", "key": "chiseled_polished_blackstone", "crown": "magma_block",
            "pillar": "polished_basalt[axis=y]", "cap": "polished_blackstone",
            "light": "soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]"},
    "home": {"rim": "pearlescent_froglight", "body": "calcite", "accent": "smooth_quartz",
             "band": "waxed_oxidized_cut_copper", "key": "chiseled_quartz_block", "crown": "pearlescent_froglight",
             "pillar": "quartz_pillar[axis=y]", "cap": "chiseled_quartz_block",
             "light": "lantern[hanging=false,waterlogged=false]"},
}


def ring_gate(cx, cy, cz, floor_y, r_in=8.5):
    """Main gate: ring in the plane x=cx, opening spans z/y."""
    r1, r2, r3 = r_in + 1.0, r_in + 2.1, r_in + 3.3
    for u in range(-int(r3 + 2), int(r3 + 3)):
        for v in range(-int(r3 + 2), int(r3 + 3)):
            y = cy + v
            if y <= floor_y - 4:
                continue
            rr = math.hypot(u, v)
            ang = math.degrees(math.atan2(v, u)) % 360
            spoke = min(ang % 30, 30 - ang % 30) < 3.2
            if rr < r_in:
                if y > floor_y:
                    put_gateway(cx, y, cz + u)
            elif rr < r1:
                for n in (-1, 0, 1):
                    V.set(cx + n, y, cz + u, "chiseled_quartz_block")
            elif rr < r2:
                V.set(cx, y, cz + u, "smooth_quartz")
                for n in (-1, 1):
                    V.set(cx + n, y, cz + u, "quartz_bricks" if spoke else "waxed_oxidized_cut_copper")
            elif rr < r3:
                for n in (-1, 0, 1):
                    V.set(cx + n, y, cz + u, "quartz_bricks" if not spoke else "chiseled_quartz_block")
            elif rr < r3 + 1.0 and spoke:
                V.set(cx, y, cz + u, "smooth_quartz")
                for n in (-1, 1):
                    V.set(cx + n, y, cz + u, "smooth_quartz")
    # studs of light on the spokes
    for k in range(12):
        a = math.radians(k * 30)
        u, v = round(math.cos(a) * (r3 + 1.2)), round(math.sin(a) * (r3 + 1.2))
        if cy + v > floor_y + 1:
            V.set(cx, cy + v, cz + u, "waxed_copper_bulb[lit=true,powered=false]")
    # crown
    top = cy + int(r3 + 1)
    for n in (-1, 0, 1):
        V.set(cx + n, top, cz, "chiseled_quartz_block")
        V.set(cx + n, top + 1, cz, "chiseled_quartz_block")
    V.set(cx, top + 2, cz, "quartz_pillar[axis=y]")
    V.set(cx, top + 3, cz, "pearlescent_froglight")
    V.set(cx, top + 4, cz, "end_rod[facing=up]")
    for s in (-1, 1):
        V.set(cx, top + 1, cz + s, "smooth_quartz_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]"
              % ("north" if s > 0 else "south"))
    # buttresses that hold the ring on the floor
    for s in (-1, 1):
        for du in range(int(r_in) - 1, int(r3) + 4):
            u = s * du
            hgt = max(0, int(5 - (du - r_in) * 0.9))
            for n in range(-3, 4):
                ext = 3 - abs(n)
                if du > r3 + ext:
                    continue
                for y in range(floor_y + 1, floor_y + 1 + hgt):
                    if V.air(cx + n, y, cz + u):
                        V.set(cx + n, y, cz + u, "quartz_bricks" if (y + du) % 3 else "chiseled_quartz_block")
        # big pedestal blocks
        for n in (-3, 3):
            V.set(cx + n, floor_y + 1, cz + s * int(r3 + 1), "chiseled_quartz_block")


def colonnade(a0, a1, R):
    """Quarter-circle colonnade around the plaza with a teal copper roof (angles in degrees, atan2(z, x))."""
    def in_sec(a, lo, hi):
        return lo <= a <= hi

    for x, z, d in disc_iter(0, 0, R + 2.6):
        if d <= PLAZA_R:
            continue
        a = math.degrees(math.atan2(z, x)) % 360
        if not in_sec(a, a0 - 4, a1 + 4):
            continue
        V.set(x, 64, z, "smooth_quartz" if abs(d - R) < 0.8 else paver(x, z, 2))
        for y in range(65, 74):
            V.set(x, y, z, "air")
        if V.air(x, 63, z):
            V.set(x, 63, z, "stone")
        if in_sec(a, a0 - 1, a1 + 1) and abs(d - R) <= 1.6:
            V.set(x, 70, z, "smooth_quartz")
            V.set(x, 71, z, "waxed_oxidized_cut_copper" if abs(d - R) < 0.9 else
                  "waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
            if abs(d - R) < 0.6:
                V.set(x, 72, z, "waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
        elif in_sec(a, a0 - 1, a1 + 1) and abs(d - R) <= 2.2:
            V.set(x, 70, z, "smooth_quartz_slab[type=top,waterlogged=false]")
    arc = math.radians(a1 - a0) * R
    n = max(2, int(round(arc / 4.2)) + 1)
    cols = []
    for i in range(n):
        a = math.radians(a0 + (a1 - a0) * i / (n - 1))
        x, z = round(math.cos(a) * R), round(math.sin(a) * R)
        cols.append((x, z))
        V.set(x, 65, z, "chiseled_quartz_block")
        for y in range(66, 69):
            V.set(x, y, z, "quartz_pillar[axis=y]")
        V.set(x, 69, z, "chiseled_quartz_block")
    for (x1, z1), (x2, z2) in zip(cols, cols[1:]):
        mx, mz = round((x1 + x2) / 2), round((z1 + z2) / 2)
        V.set(mx, 69, mz, "chain[axis=y,waterlogged=false]")
        V.set(mx, 68, mz, "lantern[hanging=true,waterlogged=false]")
        if V.air(mx, 69, mz) is False:
            pass


def floating_crystal(cx, cy, cz, h, seed):
    for y in range(-h, h + 1):
        w = (1 - abs(y) / (h + 0.5)) * 1.8
        for x in range(-2, 3):
            for z in range(-2, 3):
                if abs(x) + abs(z) <= w:
                    V.set(cx + x, cy + y, cz + z, "amethyst_block" if (x + y + z) % 4 else "budding_amethyst")
    V.set(cx, cy + h + 1, cz, "amethyst_cluster[facing=up,waterlogged=false]")
    V.set(cx, cy - h - 1, cz, "amethyst_cluster[facing=down,waterlogged=false]")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        V.set(cx + dx * 2, cy, cz + dz * 2, "end_rod[facing=%s]" % {(1, 0): "east", (-1, 0): "west",
                                                                    (0, 1): "south", (0, -1): "north"}[(dx, dz)])


# ----------------------------------------------------------------------------- the main island

MAIN_C = (-3, 0)
TERR_C, TERR_RX, TERR_RZ = (-41, 0), 13.5, 19.0
PLAZA_R = 16.5
SPAWN = (12.5, 65, 0.5, 90.0)


def main_top(X, Z):
    e = np.sqrt(((X - TERR_C[0]) / TERR_RX) ** 2 + ((Z - TERR_C[1]) / TERR_RZ) ** 2)
    dist_out = np.maximum(0, (e - 1) * min(TERR_RX, TERR_RZ))
    hill = np.clip(6 - dist_out * 0.78 + 0.8 * fbm(X, 3, Z, 6, 2, 41), 0, 6)
    hill = np.where(e <= 1, 6, np.floor(hill))
    mound = np.clip(np.round((fbm(X, 1, Z, 13, 3, 7) - 0.12) * 3.5), 0, 2)
    t = 64 + np.maximum(hill, mound)
    # flat around plaza, paths and courts
    flat = (np.hypot(X, Z) < PLAZA_R + 3)
    flat |= (np.abs(Z) <= 4) & (X > 0) & (X < 46)       # east path to the Pit gate
    flat |= (np.abs(X) <= 4) & (np.abs(Z) < 42)          # north/south paths
    flat |= (np.abs(X - 26) <= 9) & (Z < 2) & (Z > -27)  # shop court
    flat |= (np.hypot(X - 31, Z - 27) < 14)              # lagoon
    flat |= (np.abs(Z) <= 9) & (X < -14) & (X > -30)     # grand stairs foot
    t = np.where(flat & (e > 1.05), 64, t)
    return t


def build_main():
    X, Z, inside, top, bottom = island(MAIN_C[0], MAIN_C[1], 60, 52, main_top, 50, 1234,
                                       protect=[(-41, 0, 22), (38, 0, 9), (0, -34, 9), (0, 34, 9), (26, -20, 9), (28, 24, 13)], base=64,
                                       lobes=[(150, 0.14, 0.35), (-115, 0.12, 0.3), (35, 0.10, 0.35), (-20, 0.08, 0.3)])
    INFO["main_box"] = [int(X[inside].min()), int(Z[inside].min()), int(X[inside].max()), int(Z[inside].max())]
    underside_decor(X, Z, inside, top, bottom, 1234)
    geode(-10, 30, 14, 5, 5)
    geode(22, 40, -18, 4, 6)
    geode(-30, 44, -20, 3.5, 7)

    # ---------------- plaza
    for x, z, d in disc_iter(0, 0, PLAZA_R):
        a = math.degrees(math.atan2(z, x)) % 360
        ray = min(a % 45, 45 - a % 45) * math.pi / 180 * d < 0.75
        if d > PLAZA_R - 1.6:
            s = "chiseled_quartz_block" if ray else "polished_diorite"
        elif d > PLAZA_R - 2.6:
            s = "smooth_quartz"
        elif ray and d > 6.5:
            s = "waxed_oxidized_cut_copper"
        elif 6.0 < d <= 7.2:
            s = "smooth_quartz"
        else:
            s = paver(x, z)
        V.set(x, 64, z, s)
        for y in range(65, 72):
            V.set(x, y, z, "air")
        for y in range(63, 60, -1):
            if V.air(x, y, z):
                V.set(x, y, z, "stone")
    # fountain
    for x, z, d in disc_iter(0, 0, 6.2):
        if d < 5.2:
            V.set(x, 62, z, "prismarine_bricks")
            V.set(x, 63, z, "water")
            V.set(x, 64, z, "water")
        else:
            V.set(x, 64, z, "chiseled_quartz_block" if (x + z) % 3 == 0 else "smooth_quartz")
            V.set(x, 63, z, "polished_diorite")
    for y in range(62, 64):
        V.set(0, y, 0, "quartz_pillar[axis=y]")
    for (dx, dz) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        V.set(dx, 64, dz, "chiseled_quartz_block")
    V.set(0, 65, 0, "pearlescent_froglight")
    V.set(0, 66, 0, "end_rod[facing=up]")
    rng = np.random.default_rng(8)
    for _ in range(10):
        x, z = int(round(rng.uniform(-4.5, 4.5))), int(round(rng.uniform(-4.5, 4.5)))
        if 3.2 < math.hypot(x, z) < 5 and V.get(x, 64, z) == "water":
            V.set(x, 65, z, "lily_pad")

    # plaza rim: lamps + benches
    for k in range(8):
        a = math.radians(22.5 + k * 45)
        lx, lz = round(math.cos(a) * (PLAZA_R - 0.8)), round(math.sin(a) * (PLAZA_R - 0.8))
        lamp_post(lx, 64, lz)
    for k in range(8):
        a0 = math.radians(k * 45 + 12)
        for da in (0, 6, 12):
            a = a0 + math.radians(da)
            bx, bz = round(math.cos(a) * (PLAZA_R - 2.2)), round(math.sin(a) * (PLAZA_R - 2.2))
            if abs(bx) <= 3 or abs(bz) <= 3:
                continue
            f = ("east" if math.cos(a) > 0 else "west") if abs(math.cos(a)) > abs(math.sin(a)) else \
                ("south" if math.sin(a) > 0 else "north")
            V.set(bx, 65, bz, stairs("cherry_stairs", f))

    for (a0, a1) in ((28, 62), (118, 152), (208, 242), (298, 332)):
        colonnade(a0, a1, 19.5)

    # ---------------- paths
    def path_strip(x1, z1, x2, z2, hw=2):
        for x in range(min(x1, x2) - hw, max(x1, x2) + hw + 1):
            for z in range(min(z1, z2) - hw, max(z1, z2) + hw + 1):
                if math.hypot(x, z) < PLAZA_R - 0.5:
                    continue
                edge = (abs(z - z1) == hw) if x1 != x2 else (abs(x - x1) == hw)
                if edge and rnd(x, 2, z) < 0.35:
                    V.set(x, 64, z, "moss_block" if rnd(x, 3, z) < 0.5 else "coarse_dirt")
                else:
                    V.set(x, 64, z, "smooth_quartz" if edge else paver(x, z, 3))
                for y in range(65, 70):
                    V.set(x, y, z, "air")
                if V.air(x, 63, z):
                    V.set(x, 63, z, "stone")

    path_strip(16, 0, 34, 0)
    path_strip(0, -16, 0, -30)
    path_strip(0, 16, 0, 30)
    path_strip(26, -3, 26, -16)
    path_strip(22, 3, 22, 14, 1)

    # ---------------- grand stairs + terrace
    for k in range(1, 7):
        y = 64 + k
        for xx in (-15 - 2 * k, -16 - 2 * k):
            for z in range(-6, 7):
                column_fill(xx, z, y - 1, 58, "stone")
                if xx == -15 - 2 * k:
                    V.set(xx, y, z, stairs("smooth_quartz_stairs", "west"))
                else:
                    V.set(xx, y, z, "smooth_quartz" if abs(z) > 1 else "waxed_oxidized_cut_copper")
                for yy in range(y + 1, y + 8):
                    V.set(xx, yy, z, "air")
            for s in (-1, 1):
                for zz in (7 * s, 8 * s):
                    column_fill(xx, zz, y, 58, "stone")
                    V.set(xx, y, zz, "quartz_bricks")
                V.set(xx, y + 1, 7 * s, "diorite_wall")
                for yy in range(y + 2, y + 8):
                    V.set(xx, yy, 7 * s, "air")
    for s in (-1, 1):
        for (px, py) in ((-16, 65), (-29, 71)):
            for y in range(py - 1, py + 3):
                V.set(px, y, 7 * s, "quartz_pillar[axis=y]")
            V.set(px, py + 3, 7 * s, "chiseled_quartz_block")
            V.set(px, py + 4, 7 * s, "lantern[hanging=false,waterlogged=false]")
    # terrace floor
    tx, tz = TERR_C
    for x in range(int(tx - TERR_RX) - 1, int(tx + TERR_RX) + 2):
        for z in range(int(tz - TERR_RZ) - 1, int(tz + TERR_RZ) + 2):
            e = math.hypot((x - tx) / TERR_RX, (z - tz) / TERR_RZ)
            if e > 1.0:
                continue
            if e > 0.93:
                V.set(x, 70, z, "polished_diorite")
                V.set(x, 71, z, "diorite_wall")
                continue
            runway = abs(z) <= 2 and x > -41
            if runway:
                s = "waxed_oxidized_cut_copper" if abs(z) == 2 else "smooth_quartz"
            else:
                s = paver(x, z, 9)
            V.set(x, 70, z, s)
            for y in range(71, 76):
                if not V.get(x, y, z).startswith("end_gateway"):
                    V.set(x, y, z, "air")
    # balustrade posts on the terrace edge
    for k in range(28):
        a = 2 * math.pi * k / 28
        x, z = round(tx + math.cos(a) * TERR_RX * 0.965), round(tz + math.sin(a) * TERR_RZ * 0.965)
        if x > -31 and abs(z) <= 9:
            continue
        V.set(x, 71, z, "quartz_pillar[axis=y]")
        V.set(x, 72, z, "lantern[hanging=false,waterlogged=false]" if k % 2 == 0 else "smooth_quartz_slab[type=bottom,waterlogged=false]")
    # open the balustrade where the stairs arrive
    for x in range(-30, -27):
        for z in range(-6, 7):
            V.set(x, 70, z, "smooth_quartz" if abs(z) > 1 else "waxed_oxidized_cut_copper")
            V.set(x, 71, z, "air")
            V.set(x, 72, z, "air")

    ring_gate(-42, 77, 0, 70, 11)
    INFO["gate_aetherion"] = {"min": [-43, 71, -11], "max": [-41, 88, 11], "label": [-38.5, 95.0, 0.5]}
    floating_crystal(-40, 88, -23, 5, 1)
    floating_crystal(-40, 88, 23, 5, 2)
    for s in (-1, 1):
        for (px, pz) in ((-32, 12 * s), (-51, 10 * s)):
            lamp_post(px, 70, pz)

    # ---------------- small gates on courts
    def court(cx, cz, r):
        for x, z, d in disc_iter(cx, cz, r):
            V.set(x, 64, z, "smooth_quartz" if d > r - 1 else paver(x, z, 5))
            for y in range(65, 72):
                if not V.get(x, y, z).startswith("end_gateway"):
                    V.set(x, y, z, "air")
            if V.air(x, 63, z):
                V.set(x, 63, z, "stone")

    court(0, -32, 7.5)
    arch_gate(0, 64, -35, "z", 7, 4, "discord")
    INFO["gate_discord"] = {"min": [-3, 65, -36], "max": [3, 72, -35], "label": [0.5, 77.2, -34.5]}
    court(0, 32, 7.5)
    arch_gate(0, 64, 35, "z", 7, 4, "modpack")
    INFO["gate_modpack"] = {"min": [-3, 65, 35], "max": [3, 72, 36], "label": [0.5, 77.2, 35.5]}
    court(26, -19, 6.5)
    arch_gate(26, 64, -22, "z", 5, 3, "shop")
    INFO["gate_shop"] = {"min": [24, 65, -23], "max": [28, 70, -22], "label": [26.5, 74.2, -21.5]}
    court(36, 0, 7.5)
    arch_gate(39, 64, 0, "x", 9, 5, "pit")
    INFO["gate_pit"] = {"min": [39, 65, -4], "max": [40, 74, 4], "label": [38.5, 80.2, 0.5]}

    for (x, z) in ((20, -4), (20, 4), (-4, -21), (4, -21), (-4, 21), (4, 21)):
        lamp_post(x, 64, z)

    # shop dressing: barrels + awning posts
    for (bx, bz) in ((21, -17), (21, -18), (31, -17), (31, -18), (22, -16)):
        V.set(bx, 65, bz, "barrel[facing=up,open=false]")
    V.set(21, 66, -17, "barrel[facing=up,open=false]")
    # pit dressing: banners and braziers
    for s in (-1, 1):
        V.set(35, 65, 7 * s, "polished_blackstone_bricks")
        V.set(35, 66, 7 * s, "soul_campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]")
        for y in range(65, 69):
            V.set(40, y, 9 * s, "polished_blackstone_wall")
        V.set(40, 69, 9 * s, "red_banner[rotation=4]")

    # ---------------- duck lagoon
    lc = (28, 24)
    for x in range(lc[0] - 14, lc[0] + 15):
        for z in range(lc[1] - 14, lc[1] + 15):
            rr = math.hypot((x - lc[0]) / 9.5, (z - lc[1]) / 7.5) + 0.12 * float(vnoise(x, 0, z, 4, 77))
            if rr < 1.0:
                dep = 3 if rr < 0.6 else (2 if rr < 0.85 else 1)
                for y in range(65 - dep, 65):
                    V.set(x, y, z, "water")
                V.set(x, 64 - dep, z, "sand" if rnd(x, 0, z) < 0.6 else "clay")
                for y in range(65, 70):
                    V.set(x, y, z, "air")
            elif rr < 1.18:
                V.set(x, 64, z, choose([("moss_block", 2), ("grass_block", 3), ("mossy_cobblestone", 1)],
                                       rnd(x, 1, z)))
    # outflow channel to the cliff + waterfall
    for (x, _y, z) in list(line3((34, 64, 28), (50, 64, 40))):
        if not V.solid(x, 58, z):
            break
        for dx in (0, 1):
            for dz in (0, 1):
                if not V.solid(x + dx, 58, z + dz):
                    continue
                V.set(x + dx, 64, z + dz, "water")
                V.set(x + dx, 63, z + dz, "water")
                V.set(x + dx, 62, z + dz, "stone")
                for y in range(65, 70):
                    V.set(x + dx, y, z + dz, "air")
    # little pier
    for z in range(15, 21):
        for x in (21, 22, 23):
            V.set(x, 64, z, "cherry_planks")
            V.set(x, 65, z, "air")
        if z % 2 == 1:
            for x in (21, 23):
                V.set(x, 65, z, "cherry_fence")
    V.set(21, 66, 19, "lantern[hanging=false,waterlogged=false]")
    # lily pads + dripleaf
    rng = np.random.default_rng(3)
    for _ in range(26):
        x, z = int(lc[0] + rng.normal(0, 5)), int(lc[1] + rng.normal(0, 4))
        if V.get(x, 64, z) == "water" and V.air(x, 65, z):
            V.set(x, 65, z, "lily_pad")
    duck(30, 63, 25)
    INFO["duck"] = {"min": [19, 60, 18], "max": [38, 74, 32], "label": [31.5, 76.0, 25.5]}
    V.set(24, 65, 14, "oak_sign[rotation=8,waterlogged=false]")
    V.nbt[(24, 65, 14)] = sign_nbt(["PLEASE", "DO NOT FEED", "THE DUCK", "(it is rubber)"])

    # ---------------- trees, bushes
    trees = [(-24, -14, 1), (-24, 14, 2), (-12, -26, 3), (-14, 27, 4), (14, -30, 5), (16, 30, 6), (-52, -22, 7),
             (-54, 21, 8), (44, -16, 9), (-30, -32, 10), (-30, 33, 11), (46, 16, 12)]
    for (x, z, sd) in trees:
        gy = V.top(x, z, 90, 50)
        if gy is not None:
            cherry_tree(x, gy, z, 100 + sd, spread=1.15 if abs(x) > 20 else 1.0)
    for (x, z, sd) in ((-20, -20, 1), (-20, 20, 2), (10, -20, 3), (12, 21, 4), (-8, 20, 5), (-8, -20, 6),
                       (32, -10, 7), (30, 10, 8), (-36, 26, 9), (-36, -26, 10), (50, 0, 11), (8, -40, 12),
                       (-8, 40, 13), (40, -30, 14)):
        gy = V.top(x, z, 90, 50)
        if gy is not None and V.get(x, gy, z).startswith(("grass", "moss", "dirt", "coarse")):
            azalea_bush(x, gy, z, 300 + sd, 1.5 + (sd % 3) * 0.3)

    def exclude(x, z):
        return math.hypot(x, z) < PLAZA_R + 0.5

    ground_cover(X, Z, inside, 55, exclude)
    return X, Z, inside


def sign_nbt(lines):
    def txt(lines):
        return Compound({
            "messages": List(8, [String(json.dumps({"text": l})) for l in (lines + ["", "", "", ""])[:4]]),
            "color": String("black"), "has_glowing_text": Byte(0)})
    return Compound({"id": String("minecraft:sign"), "is_waxed": Byte(1), "front_text": txt(lines),
                     "back_text": txt([])})


def duck(cx, wy, cz):
    """Giant rubber duck floating on the lagoon, looking west (-x)."""
    def body(x, y, z):
        return "yellow_concrete"
    # body: ellipsoid, flattened bottom under water
    for x in range(cx - 7, cx + 8):
        for y in range(wy - 2, wy + 6):
            for z in range(cz - 5, cz + 6):
                dx, dy, dz = (x - cx) / 6.2, (y - (wy + 1.2)) / 3.3, (z - cz) / 4.6
                # tail lifts up at the back (+x)
                lift = max(0.0, (x - cx - 3) * 0.45)
                dy2 = (y - (wy + 1.2 + lift)) / 3.3
                if dx * dx + min(dy * dy, dy2 * dy2) + dz * dz <= 1.0:
                    V.set(x, y, z, "yellow_concrete")
    # head
    hx, hy, hz = cx - 4, wy + 6, cz
    for x in range(hx - 4, hx + 4):
        for y in range(hy - 3, hy + 4):
            for z in range(hz - 4, hz + 4):
                if ((x - hx) / 3.1) ** 2 + ((y - hy) / 3.0) ** 2 + ((z - hz) / 3.0) ** 2 <= 1.0:
                    V.set(x, y, z, "yellow_concrete")
    # beak
    for x in range(hx - 6, hx - 1):
        for y in range(hy - 2, hy + 1):
            for z in range(hz - 2, hz + 3):
                if ((x - (hx - 3.6)) / 2.4) ** 2 + ((y - (hy - 0.8)) / 0.9) ** 2 + ((z - hz) / 1.8) ** 2 <= 1.0:
                    if V.air(x, y, z) or x <= hx - 3:
                        V.set(x, y, z, "orange_concrete")
    # eyes
    for s in (-1, 1):
        ex, ey, ez = hx - 2, hy + 1, hz + s * 2
        V.set(ex, ey, ez, "black_concrete")
        V.set(ex - 1, ey, ez, "black_concrete")
        V.set(ex - 1, ey + 1, ez, "white_concrete")
    # wing hint
    for s in (-1, 1):
        for x in range(cx - 1, cx + 4):
            for y in range(wy + 1, wy + 3):
                z = cz + s * 5
                if V.get(x, y, z - s) == "yellow_concrete":
                    V.set(x, y, z, "yellow_terracotta" if (x + y) % 2 else "yellow_concrete")


# ----------------------------------------------------------------------------- the Pit island

PIT_C = (152, 0)
PIT_TOP = 60


def pit_top(X, Z):
    d = np.hypot(X - PIT_C[0], Z - PIT_C[1])
    t = PIT_TOP + np.where(d > 19, np.clip(np.round((d - 19) * 0.0 + 1.0 * fbm(X, 2, Z, 5, 2, 9)), 0, 2), 0)
    return t


def build_pit():
    X, Z, inside, top, bottom = island(PIT_C[0], PIT_C[1], 27, 24, pit_top, 38, 777, edge_noise=0.12,
                                       mats="dark")
    INFO["pit_box"] = [int(X[inside].min()), int(Z[inside].min()), int(X[inside].max()), int(Z[inside].max())]
    underside_decor(X, Z, inside, top, bottom, 777, dark=True)
    cx, cz = PIT_C
    # arena bowl
    for x, z, d in disc_iter(cx, cz, 21.5):
        if d < 14.5:
            fy = PIT_TOP - 2
            V.set(x, fy, z, choose([("coarse_dirt", 3), ("packed_mud", 2), ("mud_bricks", 0.6),
                                    ("red_sand", 1.2)], rnd(x, 0, z, 5)))
            V.set(x, fy - 1, z, "blackstone")
            for y in range(fy + 1, PIT_TOP + 6):
                V.set(x, y, z, "air")
        elif d < 17.5:
            step = int((d - 14.5) / 1.0)  # 0..2
            fy = PIT_TOP - 2 + min(step, 2)
            V.set(x, fy, z, "polished_blackstone_bricks" if rnd(x, 1, z) > 0.15
                  else "cracked_polished_blackstone_bricks")
            for y in range(fy + 1, PIT_TOP + 6):
                V.set(x, y, z, "air")
        elif d < 18.6:
            V.set(x, PIT_TOP, z, "red_nether_bricks")
            V.set(x, PIT_TOP + 1, z, "red_nether_brick_wall")
    # columns
    rng = np.random.default_rng(42)
    for k in range(10):
        a = 2 * math.pi * k / 10 + 0.15
        px, pz = round(cx + math.cos(a) * 20.5), round(cz + math.sin(a) * 20.5)
        gy = V.top(px, pz, 80, 40) or PIT_TOP
        h = int(rng.integers(3, 10))
        for y in range(gy + 1, gy + 1 + h):
            for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                if y < gy + h or (dx + dz) % 2 == 0:
                    V.set(px + dx, y, pz + dz, "polished_basalt[axis=y]" if (dx + dz) % 2 else "basalt[axis=y]")
        if h > 6:
            V.set(px, gy + h + 1, pz, "soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    # cover inside the arena
    for (ox, oz, ln, ax) in ((-7, -5, 4, "x"), (5, 6, 4, "x"), (-4, 7, 3, "z"), (6, -7, 3, "z"), (0, 0, 2, "x")):
        for i in range(ln):
            x = cx + ox + (i if ax == "x" else 0)
            z = cz + oz + (i if ax == "z" else 0)
            for y in (PIT_TOP - 1, PIT_TOP):
                V.set(x, y, z, "polished_blackstone_bricks" if y == PIT_TOP - 1 else "polished_blackstone_brick_wall")
    for (ox, oz) in ((-10, 0), (10, 0), (0, -10), (0, 10)):
        V.set(cx + ox, PIT_TOP - 1, cz + oz, "magma_block")
        V.set(cx + ox, PIT_TOP, cz + oz, "chiseled_polished_blackstone")
        V.set(cx + ox, PIT_TOP + 1, cz + oz, "soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]")
    # return gate on the west rim, facing the arena
    gx = cx - 23
    for x, z, d in disc_iter(gx + 1, 0, 5.5):
        V.set(x, PIT_TOP, z, "polished_blackstone_bricks")
        for y in range(PIT_TOP + 1, PIT_TOP + 8):
            V.set(x, y, z, "air")
    arch_gate(gx, PIT_TOP, 0, "x", 5, 3, "home")
    INFO["gate_home"] = {"min": [gx - 1, PIT_TOP + 1, -3], "max": [gx, PIT_TOP + 6, 3],
                         "label": [gx + 1.5, PIT_TOP + 10.2, 0.5]}
    INFO["pit_entrance"] = [gx + 4.5, PIT_TOP + 1, 0.5, -90.0]
    INFO["pit_board"] = [gx + 3.5, PIT_TOP + 4.5, 7.5, -90.0]
    # lava falls
    for ang in (35, -62):
        edge_fall(cx, cz, ang, "lava", 16)
    for k in range(6):
        a = 2 * math.pi * k / 6 + math.pi / 6
        bx, bz = round(cx + math.cos(a) * 18), round(cz + math.sin(a) * 18)
        V.set(bx, PIT_TOP + 1, bz, "red_nether_brick_wall")
        V.set(bx, PIT_TOP + 2, bz, "red_nether_brick_wall")
        V.set(bx, PIT_TOP + 3, bz, "red_banner[rotation=%d]" % (int((k * 16 / 6) + 4) % 16))


# ----------------------------------------------------------------------------- islets + clouds

def islet(cx, cy, cz, r, seed, tree=True, fall=False):
    def tf(X, Z):
        return cy + np.clip(np.round(1.2 * fbm(X, 0, Z, 4, 2, seed)), 0, 1) + 0 * X
    X, Z, inside, top, bottom = island(cx, cz, r, r * 0.85, tf, r * 1.6, seed, edge_noise=0.15)
    underside_decor(X, Z, inside, top, bottom, seed)
    if tree:
        gy = V.top(cx, cz, cy + 5, cy - 5)
        if gy is not None:
            cherry_tree(cx, gy, cz, seed, h=5 + seed % 3, spread=0.8)
    ground_cover(X, Z, inside, seed, lambda x, z: False)
    if fall:
        edge_fall(cx, cz, 0, "water", 2)


def edge_fall(cx, cz, ang, fluid, r0):
    """Put a fluid source on the last top block before the rim along a ray, so it spills over the edge."""
    a = math.radians(ang)
    last = None
    for k in range(r0 * 10, 600):
        r = k / 10.0
        x, z = round(cx + math.cos(a) * r), round(cz + math.sin(a) * r)
        t = V.top(x, z, 110, 0)
        if t is None:
            break
        last = (x, t, z)
    if last is None:
        return
    x, t, z = last
    for (dx, dz) in ((0, 0), (round(-math.sin(a)), round(math.cos(a)))):
        V.set(x + dx, t, z + dz, fluid)
        V.set(x + dx, t - 1, z + dz, "magma_block" if fluid == "lava" else "stone")
        V.set(x + dx, t + 1, z + dz, "air")


def cloud(cx, cy, cz, sx, sz, seed):
    rng = np.random.default_rng(seed)
    for _ in range(int(sx * sz / 18) + 3):
        px, pz = cx + rng.uniform(-sx / 2, sx / 2), cz + rng.uniform(-sz / 2, sz / 2)
        rr = rng.uniform(2.5, 5.0)
        blob(px, cy + rng.uniform(0, 2), pz, rr * 1.5, rr * 0.75, rr, lambda X, Y, Z: (
            "white_wool" if rnd(X, Y, Z, seed) < 0.75 else "snow_block"), seed + _, 0.2, only_air=True)


def build_sky():
    islet(-78, 82, -58, 9, 11)
    islet(-70, 52, 64, 8, 12)
    islet(66, 92, -62, 7, 13)
    islet(26, 44, 82, 10, 14, fall=True)
    islet(92, 70, 40, 6, 15)
    islet(-92, 66, 6, 6, 16)
    for (x, y, z, sx, sz, sd) in ((-40, 28, 70, 30, 14, 1), (60, 22, 50, 26, 18, 2), (-85, 30, -20, 24, 16, 3),
                                  (100, 34, -40, 30, 14, 4), (10, 18, -80, 28, 16, 5), (110, 26, 45, 22, 14, 6),
                                  (-10, -4, -10, 40, 30, 7)):
        cloud(x, y, z, sx, sz, 900 + sd)


# ----------------------------------------------------------------------------- export

def export(outdir):
    os.makedirs(outdir, exist_ok=True)
    nz = np.argwhere(V.a != 0)
    mn, mx = nz.min(0), nz.max(0)
    size = (mx - mn + 1).tolist()
    sub = V.a[mn[0]:mx[0] + 1, mn[1]:mx[1] + 1, mn[2]:mx[2] + 1]
    corner = [int(mn[0] + X0), int(mn[1] + Y0), int(mn[2] + Z0)]
    # block entities relative
    rel_nbt = {(k[0] - corner[0], k[1] - corner[1], k[2] - corner[2]): v for k, v in V.nbt.items()
               if V.get(*k) in ("end_gateway",) or V.get(*k).startswith(("oak_sign", "cherry_sign"))}
    pts = np.argwhere(sub != 0)
    vals = sub[pts[:, 0], pts[:, 1], pts[:, 2]]
    # order: solid blocks first, then attached stuff (so supports exist when shapes update)
    soft_ids = {i for i, s in enumerate(V.pal) if s.split("[")[0] in SOFT}
    order = np.argsort(np.array([1 if v in soft_ids else 0 for v in vals]) * 10 ** 7 + pts[:, 1], kind="stable")
    blocks = [(int(pts[i, 0]), int(pts[i, 1]), int(pts[i, 2]), int(vals[i])) for i in order]
    write_structure(os.path.join(outdir, "skyisle.nbt"), size, V.pal, blocks, rel_nbt)
    np.savez_compressed(os.path.join(outdir, "skyisle.npz"), a=sub.astype(np.int16), corner=np.array(corner),
                        pal=np.array(V.pal))
    INFO["corner"] = corner
    INFO["size"] = size
    INFO["blocks"] = len(blocks)
    INFO["spawn"] = list(SPAWN)
    with open(os.path.join(outdir, "skyisle.json"), "w") as f:
        json.dump(INFO, f, indent=1)
    print(json.dumps({k: INFO[k] for k in ("corner", "size", "blocks")}), "palette", len(V.pal))


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "out"
    build_main()
    build_pit()
    build_sky()
    export(out)
