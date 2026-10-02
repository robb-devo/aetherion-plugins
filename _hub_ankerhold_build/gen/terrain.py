"""Ankerhold terrain: island outline, district heights, lagoon, underside, voxelisation."""
from __future__ import annotations

import math

import numpy as np
import opensimplex
from matplotlib.path import Path as MPath
from scipy import ndimage

from world import (X0, X1, Z0, Z1, Y0, Y1, NX, NZ, WATER_Y, World, GRASS, WATER)

xs = np.arange(X0, X1 + 1)
zs = np.arange(Z0, Z1 + 1)
GX, GZ = np.meshgrid(xs, zs, indexing="ij")  # [ix, iz]


def perlin(xf, zf, seed):
    """Vectorised 2D gradient noise, roughly in [-1, 1]."""
    rng = np.random.default_rng(seed)
    perm = rng.permutation(512)
    perm = np.concatenate([perm, perm])
    ang = rng.random(512) * 2 * np.pi
    gvx, gvz = np.cos(ang), np.sin(ang)
    x0 = np.floor(xf).astype(np.int64)
    z0 = np.floor(zf).astype(np.int64)
    fx, fz = xf - x0, zf - z0

    def g(ix, iz, dx, dz):
        hsh = perm[(perm[ix & 511] + iz) & 511]
        return gvx[hsh] * dx + gvz[hsh] * dz

    def fade(t):
        return t * t * t * (t * (t * 6 - 15) + 10)

    n00 = g(x0, z0, fx, fz)
    n10 = g(x0 + 1, z0, fx - 1, fz)
    n01 = g(x0, z0 + 1, fx, fz - 1)
    n11 = g(x0 + 1, z0 + 1, fx - 1, fz - 1)
    u, v = fade(fx), fade(fz)
    return 1.414 * ((n00 * (1 - u) + n10 * u) * (1 - v) + (n01 * (1 - u) + n11 * u) * v)


def noise(scale, seed, octaves=1, lac=2.0, gain=0.5):
    out = np.zeros((NX, NZ))
    amp, f, norm = 1.0, 1.0 / scale, 0.0
    for o in range(octaves):
        out += amp * perlin(GX * f + 1000 + o * 37.1, GZ * f + 1000 - o * 11.3, seed + o * 101)
        norm += amp
        amp *= gain
        f *= lac
    return out / norm


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def dist(cx, cz):
    return np.hypot(GX - cx, GZ - cz)


def poly_mask(pts):
    p = MPath(np.array(pts, dtype=float))
    flat = np.column_stack([GX.ravel() + 0.5, GZ.ravel() + 0.5])
    return p.contains_points(flat).reshape(GX.shape)


def seg_dist(ax, az, bx, bz):
    vx, vz = bx - ax, bz - az
    L2 = vx * vx + vz * vz
    t = np.clip(((GX - ax) * vx + (GZ - az) * vz) / L2, 0, 1)
    return np.hypot(GX - (ax + t * vx), GZ - (az + t * vz))


# ------------------------------------------------------------------------------------------- districts
D_VOID, D_CAP, D_HARB, D_WHIS, D_RIDGE, D_FIELD, D_MTN, D_BORD, D_COLO, D_LIGHT, D_DEBRIS = range(11)
DNAMES = {D_CAP: "capital", D_HARB: "harbour", D_WHIS: "whisperwood", D_RIDGE: "ridge", D_FIELD: "fields",
          D_MTN: "mountain", D_BORD: "borderlands", D_COLO: "colosseum", D_LIGHT: "lighthouse", D_DEBRIS: "debris"}

CENTERS = {
    D_CAP: (-12, -2, 78),
    D_HARB: (100, 6, 66),
    D_WHIS: (88, -96, 72),
    D_RIDGE: (52, 108, 72),
    D_FIELD: (-92, 84, 74),
    D_MTN: (-48, -110, 88),
    D_BORD: (-120, -38, 69),
}

LAGOON_C = (106.0, 6.0)
LAGOON_R = (34.0, 25.0)

PLATEAU = [(-66, -30), (-44, -44), (-6, -48), (26, -42), (44, -20), (47, 18), (38, 42), (12, 52),
           (-28, 50), (-58, 36), (-70, 8)]
PLATEAU_Y = 78

MTN_PEAK = (-50, -118)

# Vex's wall line (north -> south) + the coast it encloses (the Borderlands)
WALL_LINE = [(-94, -100), (-91, -68), (-89, -40), (-88, -14), (-93, 8), (-107, 26), (-128, 36), (-152, 40), (-182, 38)]
BORDER_POLY = WALL_LINE + [(-200, 36), (-200, -130), (-100, -130)]

COAST = [(-60, -176), (-30, -168), (-5, -150), (15, -138), (25, -126), (38, -146), (60, -160), (95, -152),
         (125, -134), (146, -106), (152, -72), (146, -46), (158, -4), (158, 18), (150, 44), (138, 62), (116, 72),
         (124, 94), (112, 120), (96, 146), (70, 160), (52, 178), (34, 178), (20, 160), (0, 150), (-16, 130),
         (-30, 140), (-50, 158), (-80, 164), (-112, 160), (-138, 156), (-152, 140), (-166, 112), (-168, 80),
         (-160, 52), (-172, 30), (-178, 0), (-170, -34), (-180, -60), (-166, -86), (-140, -102), (-118, -110),
         (-104, -132), (-100, -156), (-82, -172)]


class Terrain:
    pass


def build_heights(seed=7) -> Terrain:
    T = Terrain()
    # ---------------------------------------------------------------- island outline (smooth union)
    mask0 = poly_mask(COAST)
    s = ndimage.distance_transform_edt(mask0) - ndimage.distance_transform_edt(~mask0)
    s = ndimage.gaussian_filter(s, 3.0)
    s += noise(40, seed, 3) * 9 + noise(12, seed + 5, 2) * 3.0
    # west edge (Borderlands) is torn: deeper bites
    west = smoothstep(-70, -160, GX) * smoothstep(50, -10, GZ)
    s -= west * np.clip(noise(9, seed + 9, 2) * 14 + 3, 0, None)
    island = s > 0

    # lagoon mouth must reach the east edge: open the rim there
    lag_d = np.hypot((GX - LAGOON_C[0]) / LAGOON_R[0], (GZ - LAGOON_C[1]) / LAGOON_R[1])
    lag_d = lag_d + noise(14, seed + 33, 2) * 0.10 + 0.08 * np.sin(np.arctan2(GZ - LAGOON_C[1], GX - LAGOON_C[0]) * 3 + 1.0)
    lagoon = lag_d <= 1.0
    cn = noise(6, seed + 34, 1) * 1.5
    channel = (GX >= 126) & (GX <= 158) & (GZ >= -1 + cn) & (GZ <= 17 - cn)
    channel &= island | (GX < 150)

    # satellites
    sats = np.zeros_like(island)
    T.sat_list = []

    def sat(cx, cz, r, nseed):
        d = dist(cx, cz) + noise(9, nseed, 2) * r * 0.25
        m = d < r
        T.sat_list.append((cx, cz, r))
        return m

    colo = sat(-162, -143, 25, 301)
    light = sat(168, -42, 8, 302)
    debris = np.zeros_like(island)
    rng = np.random.default_rng(seed)
    debris_pts = [(-172, -20, 5), (-168, 18, 4), (-178, -70, 6), (-150, 46, 4), (-120, -150, 5),
                  (-60, -175, 4), (165, -70, 3), (-110, 150, 4), (120, 130, 3), (176, 58, 3), (60, -178, 4)]
    for cx, cz, r in debris_pts:
        debris |= sat(cx, cz, r, 400 + cx)
    sats = colo | light | debris
    island_all = island | sats
    T.island = island
    T.island_all = island_all
    T.colo, T.light, T.debris = colo, light, debris
    T.edge = ndimage.distance_transform_edt(island) - 0.5

    # ---------------------------------------------------------------- base heights (district blend)
    W = np.zeros((NX, NZ))
    Hs = np.zeros((NX, NZ))
    for did, (cx, cz, hh) in CENTERS.items():
        w = np.exp(-(dist(cx, cz) / 46.0) ** 2) + 1e-6
        W += w
        Hs += w * hh
    h = Hs / W
    h += noise(30, seed + 21, 3) * 2.2

    # district map = argmax of weights (refined later)
    dw = np.stack([np.exp(-(dist(cx, cz) / 46.0) ** 2) for (cx, cz, _) in CENTERS.values()])
    dmap = np.array(list(CENTERS.keys()))[np.argmax(dw, axis=0)]

    # ---------------------------------------------------------------- harbour basin
    water_mask = (lagoon | channel) & island
    dl = ndimage.distance_transform_edt(~water_mask)
    harb_m = smoothstep(78, 46, dist(*LAGOON_C)) * smoothstep(-40, -10, GX - 46)
    h_h = 64 + 6.5 * smoothstep(2, 42, dl)
    h = h * (1 - harb_m) + h_h * harb_m

    # ---------------------------------------------------------------- reliefs (max)
    md = dist(*MTN_PEAK)
    ridge_n = 1 - np.abs(noise(18, seed + 31, 4))
    mt = np.zeros_like(h)
    for (cx, cz, hh, rr, p) in [(-50, -120, 64, 62, 1.5), (-16, -104, 40, 40, 1.3), (-86, -110, 38, 40, 1.4),
                                (-56, -152, 36, 30, 1.3), (-28, -138, 50, 40, 1.4),
                                (-24, -86, 38, 30, 1.05)]:
        t = np.clip(1 - dist(cx, cz) / rr, 0, 1)
        mt = np.maximum(mt, hh * t ** p)
    mfoot = np.clip(1 - md / 84, 0, 1)
    mt = mt + mfoot * (ridge_n * 14 - 6) * smoothstep(0, 10, mt)
    h = h + mt
    T.mtn = mt
    rg = np.zeros_like(h)
    for (cx, cz, hh, rr, p) in [(58, 116, 32, 34, 1.3), (24, 126, 22, 28, 1.3), (92, 106, 24, 26, 1.4),
                                (40, 140, 18, 24, 1.2)]:
        t = np.clip(1 - dist(cx, cz) / rr, 0, 1)
        rg = np.maximum(rg, hh * t ** p)
    rg = rg + smoothstep(0, 6, rg) * (1 - np.abs(noise(10, seed + 41, 3))) * 7
    h = h + rg
    wd = dist(92, -104)
    wh = 15 * np.clip(1 - wd / 52, 0, 1) ** 1.3
    h = h + wh + noise(12, seed + 51, 2) * 1.5 * np.clip(1 - wd / 52, 0, 1)

    fh = 7 * np.clip(1 - dist(-112, 58) / 26, 0, 1) ** 1.2 + 4 * np.clip(1 - dist(-70, 118) / 22, 0, 1)
    h = h + fh
    # Borderlands: craters + uneven
    bpoly = poly_mask(BORDER_POLY)
    bsdf = ndimage.distance_transform_edt(bpoly) - ndimage.distance_transform_edt(~bpoly)
    T.bsdf = bsdf
    bord_m = smoothstep(-2, 10, bsdf)
    crater = np.zeros_like(h)
    for cx, cz, r, dep in [(-118, -24, 9, 3), (-138, 2, 7, 2), (-100, -62, 8, 3), (-150, -30, 12, 4)]:
        d = dist(cx, cz)
        crater -= dep * np.clip(1 - (d / r) ** 2, 0, 1)
        crater += 1.5 * np.exp(-((d - r) / 2.5) ** 2)
    h = h + bord_m * (crater + noise(7, seed + 61, 2) * 1.6)

    # ---------------------------------------------------------------- capital plateau
    plateau = poly_mask(PLATEAU)
    T.plateau = plateau
    h = np.where(plateau, PLATEAU_Y, h)
    # just outside the plateau: drop to at most the plateau (walls handle the rest)
    # west side ramps into the fields
    # ---------------------------------------------------------------- lagoon floor
    lag_floor = WATER_Y - 2 - 5.5 * np.clip(1 - lag_d, 0, 1) ** 0.7
    lag_floor = np.where(channel & ~lagoon, WATER_Y - 3, lag_floor)
    h = np.where(water_mask, np.minimum(h, lag_floor), h)
    # quay band: flatten ring at 64
    quay = (dl > 0) & (dl <= 4.5) & island & (harb_m > 0.35)
    h = np.where(quay, 64, h)
    T.water = water_mask
    T.quay = quay
    T.dl = dl

    # ---------------------------------------------------------------- satellites heights
    h = np.where(colo, 73 + noise(6, 77, 1) * 0.6, h)
    h = np.where(light, 68 + np.clip(1 - dist(168, -42) / 8, 0, 1) * 4, h)
    for cx, cz, r in debris_pts:
        d = dist(cx, cz)
        m = (d < r + 2) & debris
        h = np.where(m, 62 + (cz % 7) + 3 * np.clip(1 - d / r, 0, 1), h)

    h = np.where(island_all, h, np.nan)
    T.h = h
    # district map refine
    dmap = np.where(plateau, D_CAP, dmap)
    dmap = np.where(colo, D_COLO, dmap)
    dmap = np.where(light, D_LIGHT, dmap)
    dmap = np.where(debris, D_DEBRIS, dmap)
    dmap = np.where((bsdf + noise(6, seed + 71, 2) * 4 > 1) & island, D_BORD, dmap)
    dmap = np.where((dmap == D_BORD) & ~(bsdf > -6), D_FIELD, dmap)
    dmap = np.where(island_all, dmap, D_VOID)
    T.dmap = dmap
    T.lag_d = lag_d
    T.lagoon = lagoon
    T.channel = channel
    T.harb_m = harb_m
    T.bord_m = bord_m
    return T


def underside(T: Terrain, seed=7):
    """bottom y (float) of each column."""
    s = np.clip(T.edge, 0, None)
    island = T.island
    sat = T.island_all & ~island
    ref = np.minimum(np.nan_to_num(T.h, nan=60), 70)
    th = 4 + 0.55 * s ** 1.05
    th *= 0.75 + 0.5 * (noise(40, seed + 81, 3) + 1) / 2
    th += 10 * np.clip(noise(16, seed + 82, 2), 0, None) * smoothstep(4, 20, s)
    # inverted peaks (keels) under the mountain, the capital and the harbour
    for (cx, cz, a, r) in ((-40, -100, 26, 60), (-10, 10, 22, 70), (100, 20, 14, 45), (50, 110, 16, 40),
                           (-100, 80, 14, 50), (-120, -40, 12, 40)):
        th += a * np.clip(1 - dist(cx, cz) / r, 0, 1) ** 1.6
    th = np.clip(th, 3, 90)
    bottom = ref - th
    # satellites: inverted cones
    for cx, cz, r in T.sat_list:
        d = dist(cx, cz)
        cone = ref - (2 + (r * 1.4) * np.clip(1 - d / (r + 1), 0, 1) ** 1.1)
        bottom = np.where(sat & (d < r + 3), np.minimum(cone, ref - 2), bottom)
    bottom = np.where(T.island_all, bottom, np.nan)
    T.bottom = bottom
    return bottom


# ------------------------------------------------------------------------------------------- voxelise
def voxelise(Wd: World, T: Terrain, seed=7):
    from world import Y0, NY
    h = T.h
    isl = T.island_all
    top = np.where(isl, np.round(np.nan_to_num(h, nan=0)), Y0 - 10).astype(int)
    bot = np.where(isl, np.floor(np.nan_to_num(T.bottom, nan=0)), Y0 + 10).astype(int)
    bot = np.minimum(bot, top - 3)
    T.top = top
    gx, gz = np.gradient(np.nan_to_num(h, nan=0).astype(float))
    slope = np.hypot(gx, gz)
    T.slope = slope
    rng = np.random.default_rng(seed)
    n1 = noise(9, seed + 101, 2)
    n2 = noise(4, seed + 102, 1)
    r = rng.random(top.shape)
    B = Wd.bid
    dm = T.dmap

    # ---------------- surface
    surf = np.full(top.shape, B(GRASS), dtype=np.uint16)
    sub = np.full(top.shape, B("dirt"), dtype=np.uint16)
    # cliffs / steep
    rocky = slope > 1.55
    semi = (slope > 1.0) & ~rocky
    surf[semi & (n2 > 0.2)] = B("coarse_dirt")
    surf[semi & (n2 < -0.45)] = B("stone")
    surf[rocky] = B("stone")
    surf[rocky & (n1 > 0.25)] = B("andesite")
    surf[rocky & (n1 < -0.35)] = B("tuff")
    sub[rocky] = B("stone")
    # whisperwood: podzol/moss under trees, grass otherwise
    w = (dm == D_WHIS) & ~rocky
    surf[w & (n1 > 0.35)] = B("podzol")
    surf[w & (n1 < -0.45) & (r < .6)] = B("moss_block")
    # fields: some coarse patches near paths later
    # mountain upper
    mtop = (dm == D_MTN)
    hi = top >= 126 + (n1 * 4).astype(int)
    surf[mtop & hi & ~rocky] = B("stone")
    surf[mtop & hi & ~rocky & (n2 > 0.1)] = B("andesite")
    surf[mtop & hi & rocky & (n2 > 0.3)] = B("calcite")
    snow = mtop & (top >= 141 + (n2 * 3).astype(int))
    surf[snow & (slope < 2.2)] = B("snow_block")
    # borderlands ash & mud
    bd = (dm == D_BORD)
    bmix = r
    surf[bd] = B("coarse_dirt")
    surf[bd & (n2 > 0.35)] = B("gravel")
    surf[bd & (n2 < -0.4)] = B("packed_mud")
    surf[bd & (n1 > 0.45)] = B("tuff")
    surf[bd & (bmix < 0.06)] = B("rooted_dirt")
    surf[bd & (n1 < -0.5) & (bmix < .5)] = B("basalt[axis=y]")
    sub[bd] = B("coarse_dirt")
    # ridge rock faces
    rd = (dm == D_RIDGE) & (top >= 82) & (slope > 0.8)
    surf[rd & (n2 > 0)] = B("andesite")
    surf[rd & (n2 <= 0)] = B("stone")
    # satellites
    surf[dm == D_COLO] = B("grass_block[snowy=false]")
    # lagoon bottom
    lagb = T.water & T.island
    surf[lagb] = B("sand")
    surf[lagb & (n2 > 0.3)] = B("gravel")
    surf[lagb & (n1 < -0.4)] = B("clay")
    sub[lagb] = B("sand")
    T.surf, T.sub = surf, sub

    # ---------------- strata for deep rock
    strata_ids = np.array([B("stone"), B("andesite"), B("stone"), B("tuff"), B("stone"), B("stone"), B("andesite"),
                  B("stone"), B("stone"), B("dripstone_block")], dtype=np.uint16)
    off = (n1 * 5).astype(int)
    A = Wd.a
    for yy in range(Y0, top.max() + 1):
        iy = yy - Y0
        inside = (yy <= top) & (yy >= bot)
        if not inside.any():
            continue
        layer = np.zeros(top.shape, dtype=np.uint16)
        band = strata_ids[((yy + off) // 3) % len(strata_ids)]
        layer[inside] = band[inside]
        # dirt layers near top (not on rocky)
        d = top - yy
        dirtzone = inside & (d >= 1) & (d <= 3 + (n2 > 0.3)) & ~rocky
        layer[dirtzone] = sub[dirtzone]
        layer[inside & (d == 0)] = surf[inside & (d == 0)]
        # underside skin: last 1-2 layers dripstone/tuff mix
        skin = inside & (yy - bot <= 1)
        layer[skin & (r < 0.35)] = B("dripstone_block")
        layer[skin & (r > 0.8)] = B("tuff")
        # rooted dirt near the rim on underside
        rim = inside & (yy - bot <= 2) & (T.edge < 6) & (bot > 40)
        layer[rim & (r < 0.5)] = B("rooted_dirt")
        A[:, iy, :] = np.where(inside, layer, A[:, iy, :])
    # water
    wcol = T.water & T.island
    for yy in range(int(top[wcol].min()) + 1, WATER_Y + 1):
        m = wcol & (top < yy)
        A[:, yy - Y0, :] = np.where(m, B(WATER), A[:, yy - Y0, :])
    # snow layers on top of snow blocks
    sn = snow & (slope < 2.2)
    for ix, iz in zip(*np.nonzero(sn)):
        if r[ix, iz] < 0.7:
            Wd.set(ix + X0, top[ix, iz] + 1, iz + Z0, f"snow[layers={1 + int(r[ix, iz] * 3)}]")
    T.bot = bot
    return top


def stalactites(Wd: World, T: Terrain, seed=7):
    rng = np.random.default_rng(seed + 9)
    isl = T.island_all
    cand = np.argwhere(isl & (T.edge > 2))
    pick = cand[rng.random(len(cand)) < 1 / 260.0]
    for ix, iz in pick:
        x, z = ix + X0, iz + Z0
        b = T.bot[ix, iz]
        th = T.top[ix, iz] - b
        L = int(rng.integers(5, 10) + min(34, th * 0.6) * rng.random() ** 0.7)
        rad = max(1.5, L / 4.5 * rng.uniform(0.6, 1.1))
        mat = ["stone", "andesite", "tuff", "dripstone_block", "stone"][int(rng.integers(0, 5))]
        for k in range(L):
            rr = rad * (1 - k / L) ** 1.3
            ri = int(math.ceil(rr))
            for dx in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    if dx * dx + dz * dz <= rr * rr + 0.3:
                        Wd.set_if_air(x + dx, b - 1 - k, z + dz, mat)
        # dripstone tip
        tip_y = b - 1 - L
        if Wd.is_air(x, tip_y, z):
            Wd.set(x, tip_y, z, "pointed_dripstone[thickness=frustum,vertical_direction=down,waterlogged=false]")
            Wd.set(x, tip_y - 1, z, "pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]")
    # hanging roots near rims
    rim = np.argwhere(isl & (T.edge < 10) & (T.edge > 1))
    pick = rim[rng.random(len(rim)) < 0.08]
    for ix, iz in pick:
        x, z = ix + X0, iz + Z0
        b = T.bot[ix, iz]
        if Wd.is_air(x, b - 1, z) and Wd.getn(x, b, z) in ("minecraft:rooted_dirt", "minecraft:dirt"):
            Wd.set(x, b - 1, z, "hanging_roots[waterlogged=false]")
