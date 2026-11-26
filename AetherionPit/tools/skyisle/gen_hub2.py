"""Hub round 2 (2026-11-26): a smaller island around three free-to-use builds by SkyblockSquad
(Elven Shrine, Elven Tower, Elven House, Planet Minecraft, "free to use anywhere").

Two portals only (Aetherion on the shrine bridge, The Pit at the east edge); Discord = the bell,
Modpack = the spell book at the tower, Pit-Shop = the elven house. The rest is props.

  python gen_hub2.py <outdir> <dir with shrine.bin tower.bin house.bin>

Writes hub_base.nbt, hub_shrine.nbt, hub_tower.nbt, hub_house.nbt, hub_overlay.nbt, hub2.npz (preview)
and hub2.json (placement commands + plugin coordinates).
"""
import json
import math
import os
import sys

import numpy as np

import gen_skyisle as G
from gen_skyisle import V, Vol, X0, Y0, Z0, SX, SY, SZ, island, underside_decor, ground_cover, rnd, choose, \
    disc_iter, paver, lamp_post, stairs, cherry_tree, azalea_bush, put_gateway, sign_nbt, blob, vnoise, fbm
from nbtio import write_structure, Compound, String, Byte, Int, Long, List, Tag
from schemread import load_schem

GROUND = 64
SPAWN = (25.5, 65.0, 0.5, 90.0, -6.0)
INFO = {}

# placements: name -> (pos, rotation, crop(x0,x1,z0,z1) or None)
PLACE = {
    "shrine": ((-50, GROUND, -24), "none", None),
    "tower": ((-3, GROUND, -22), "counterclockwise_90", (0, 36, 0, 36)),
    "house": ((46, GROUND, 46), "180", None),
}


def rot(rotation, lx, lz):
    if rotation == "none":
        return lx, lz
    if rotation == "counterclockwise_90":
        return lz, -lx
    if rotation == "clockwise_90":
        return -lz, lx
    if rotation == "180":
        return -lx, -lz
    raise ValueError(rotation)


SOFT_GROUND = {"minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
               "minecraft:short_grass", "minecraft:grass", "minecraft:tall_grass", "minecraft:fern",
               "minecraft:large_fern", "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:podzol"}


class Schem:
    def __init__(self, name, path):
        self.name = name
        a, pal, bes, root = load_schem(path)
        self.a, self.pal, self.root = a, pal, root
        self.dv = root.get("DataVersion", 2975)
        self.be = {}
        for b in bes:
            p = b.get("Pos")
            if p is None:
                continue
            self.be[(p[0], p[1], p[2])] = b
        self.pos, self.rotation, self.crop = PLACE[name]
        self.air = pal.index("minecraft:air") if "minecraft:air" in pal else -1

    def cells(self):
        """yield (lx, ly, lz, state, wx, wy, wz) for non-air cells after crop"""
        sx, sy, sz = self.a.shape
        x0, x1, z0, z1 = self.crop or (0, sx - 1, 0, sz - 1)
        for lx in range(x0, min(x1, sx - 1) + 1):
            for lz in range(z0, min(z1, sz - 1) + 1):
                col = self.a[lx, :, lz]
                for ly in np.nonzero(col != self.air)[0]:
                    dx, dz = rot(self.rotation, lx - x0, lz - z0)
                    yield lx, int(ly), lz, self.pal[col[ly]], self.pos[0] + dx, self.pos[1] + int(ly), self.pos[2] + dz

    def footprint(self):
        pts = set()
        for lx, ly, lz, s, wx, wy, wz in self.cells():
            if s.split("[")[0] not in SOFT_GROUND or ly > 0:
                pts.add((wx, wz))
        return pts


def be_to_nbt(b):
    """Sponge block entity (DataVersion of the schematic) -> structure block nbt"""
    def conv(v):
        if isinstance(v, Tag):
            return v
        if isinstance(v, bool):
            return Byte(int(v))
        if isinstance(v, int):
            return Int(v)
        if isinstance(v, float):
            return Tag(6, v)
        if isinstance(v, str):
            return String(v)
        if isinstance(v, dict):
            return Compound({k: conv(x) for k, x in v.items()})
        if isinstance(v, list):
            if not v:
                return List(10, [])
            items = [conv(x) for x in v]
            return List(items[0].t, items)
        return String(str(v))
    d = {k: v for k, v in b.items() if k not in ("Pos", "Id")}
    out = {k: conv(v) for k, v in d.items()}
    out["id"] = String(b.get("Id", ""))
    return Compound(out)


# ----------------------------------------------------------------------------- base island

def hub_top(X, Z):
    m = np.clip(np.round((fbm(X, 1, Z, 11, 3, 70) - 0.25) * 3.0), 0, 1)
    return GROUND + m * 0


def build_island(foot):
    # protect circles along every footprint cell so the square builds never hang over the void
    prot = [(x, z, 3.5) for (x, z) in list(foot)[::9]]
    prot += [(20, 0, 14), (34, 13, 9), (42, 0, 6), (-2, 0, 8)]
    X, Z, inside, top, bottom = island(-2, -4, 50, 50, hub_top, 46, 4242, edge_noise=0.12, protect=prot, base=64,
                                       lobes=[(200, 0.10, 0.4), (-20, 0.08, 0.3)])
    # flatten to GROUND inside footprints (the builds bring their own ground)
    INFO["island_box"] = [int(X[inside].min()), int(Z[inside].min()), int(X[inside].max()), int(Z[inside].max())]
    underside_decor(X, Z, inside, top, bottom, 4242)
    G.geode(-6, 34, 10, 4.5, 21)
    G.geode(20, 40, -22, 4, 22)
    return X, Z, inside


def plaza():
    cx, cz, R = 20, 0, 8.5
    for x, z, d in disc_iter(cx, cz, R):
        a = math.degrees(math.atan2(z - cz, x - cx)) % 360
        ray = min(a % 45, 45 - a % 45) * math.pi / 180 * d < 0.6 and d > 2.5
        if d > R - 1.2:
            s = "chiseled_sandstone" if ray else "smooth_sandstone"
        elif ray:
            s = "dark_prismarine"
        elif d < 2.2:
            s = "chiseled_quartz_block" if d < 1 else "smooth_quartz"
        else:
            s = choose([("smooth_sandstone", 3), ("calcite", 2), ("white_terracotta", 1.2), ("polished_diorite", 1)],
                       float(rnd(x // 2, 0, z // 2, 31)))
        V.set(x, GROUND, z, s)
        for y in range(GROUND + 1, GROUND + 6):
            V.set(x, y, z, "air")
    V.set(cx, GROUND, cz, "pearlescent_froglight")
    # benches in four quarters
    for k in range(4):
        a0 = math.radians(45 + k * 90)
        for da in (-10, 0, 10):
            a = a0 + math.radians(da)
            bx, bz = round(cx + math.cos(a) * (R - 1.6)), round(cz + math.sin(a) * (R - 1.6))
            f = ("east" if math.cos(a) > 0 else "west") if abs(math.cos(a)) > abs(math.sin(a)) else \
                ("south" if math.sin(a) > 0 else "north")
            V.set(bx, GROUND + 1, bz, stairs("spruce_stairs", f))
    for k in range(4):
        a = math.radians(k * 90 + 22)
        lx, lz = round(cx + math.cos(a) * (R - 0.5)), round(cz + math.sin(a) * (R - 0.5))
        lamp_post(lx, GROUND, lz)


def path(points, hw=2, seed=3):
    for (x1, z1), (x2, z2) in zip(points, points[1:]):
        n = int(max(abs(x2 - x1), abs(z2 - z1))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            px, pz = x1 + (x2 - x1) * t, z1 + (z2 - z1) * t
            for dx in range(-hw - 1, hw + 2):
                for dz in range(-hw - 1, hw + 2):
                    x, z = round(px + dx), round(pz + dz)
                    d = math.hypot(dx, dz)
                    if d > hw + 0.6:
                        continue
                    cur = V.get(x, GROUND, z)
                    if not cur.startswith(("grass_block", "dirt", "coarse", "moss")):
                        continue
                    edge = d > hw - 0.4
                    r = float(rnd(x, 5, z, seed))
                    if edge and r < 0.45:
                        s = "moss_block" if r < 0.2 else "coarse_dirt"
                    else:
                        s = choose([("dirt_path", 5), ("coarse_dirt", 1), ("packed_mud", 1),
                                    ("mud_bricks", 0.6), ("moss_block", 0.3)], r)
                    V.set(x, GROUND, z, s)
                    for y in range(GROUND + 1, GROUND + 3):
                        if V.get(x, y, z).split("[")[0] in G.SOFT:
                            V.set(x, y, z, "air")


def small_arch(cx, fy, cz, normal, w, sh, mats):
    """thin arch gate (normal size): opening w wide, sh straight + half circle; frame 1 + accents"""
    hw = w / 2.0
    R = hw

    def W(u, v, n):
        return (cx + n, fy + v, cz + u) if normal == "x" else (cx + u, fy + v, cz + n)

    for u in range(-int(hw) - 3, int(hw) + 4):
        for v in range(1, int(sh + R) + 4):
            if v <= sh:
                inner = abs(u) <= hw - 0.5
                dist = abs(u) - (hw - 0.5)
            else:
                rr = math.hypot(u, v - sh)
                inner = rr <= R - 0.3
                dist = rr - (R - 0.3)
            if inner:
                put_gateway(*W(u, v, 0))
            elif dist <= 1.3:
                for n in (-1, 0, 1):
                    V.set(*W(u, v, n), mats["rim"] if n == 0 else mats["body"])
            elif dist <= 2.0 and v > 1:
                V.set(*W(u, v, 0), mats["outer"])
    top = int(sh + R) + 2
    V.set(*W(0, top, 0), mats["key"])
    V.set(*W(0, top + 1, 0), mats["crown"])
    for s in (-1, 1):
        for n in (-1, 1):
            V.set(*W(s * (int(hw) + 2), 1, n), mats["base"])
    return {"top": fy + top}


def belfry(x, z):
    """Discord: a little bell tower on the plaza edge"""
    for dx in (-1, 1):
        for dz in (-1, 1):
            V.set(x + dx, GROUND + 1, z + dz, "stripped_spruce_log[axis=y]")
            for y in range(GROUND + 2, GROUND + 5):
                V.set(x + dx, y, z + dz, "spruce_fence")
            V.set(x + dx, GROUND + 5, z + dz, "stripped_spruce_log[axis=y]")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            V.set(x + dx, GROUND, z + dz, "smooth_sandstone" if (dx or dz) else "chiseled_sandstone")
            V.set(x + dx, GROUND + 6, z + dz, "dark_prismarine_slab[type=bottom,waterlogged=false]")
    for dx, f in ((-2, "west"), (2, "east")):
        for dz in (-1, 0, 1):
            V.set(x + dx, GROUND + 5, z + dz, stairs("dark_prismarine_stairs", f))
    for dz, f in ((-2, "north"), (2, "south")):
        for dx in (-1, 0, 1):
            V.set(x + dx, GROUND + 5, z + dz, stairs("dark_prismarine_stairs", f))
    V.set(x, GROUND + 5, z, "dark_prismarine")
    V.set(x, GROUND + 6, z, "dark_prismarine")
    V.set(x, GROUND + 7, z, "end_rod[facing=up]")
    V.set(x, GROUND + 4, z, "bell[attachment=ceiling,facing=north,powered=false]")
    V.set(x, GROUND + 1, z, "smooth_sandstone_slab[type=bottom,waterlogged=false]")
    return {"min": [x - 1, GROUND + 1, z - 1], "max": [x + 1, GROUND + 5, z + 1], "label": [x + 0.5, GROUND + 7.3, z + 0.5]}


def spellbook(x, z):
    """Modpack: a lectern with a book on a little reading nook"""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                V.set(x + dx, GROUND, z + dz, "mossy_stone_bricks" if (dx + dz) % 3 else "chiseled_stone_bricks")
                for y in range(GROUND + 1, GROUND + 4):
                    V.set(x + dx, y, z + dz, "air")
    V.set(x, GROUND + 1, z, "lectern[facing=south,has_book=true,powered=false]")
    V.nbt[(x, GROUND + 1, z)] = Compound({"id": String("minecraft:lectern"), "Book": Compound({
        "id": String("minecraft:writable_book"), "count": Int(1)}), "Page": Int(0)})
    for dx, dz in ((-2, 0), (2, 0)):
        V.set(x + dx, GROUND + 1, z + dz, "bookshelf")
        V.set(x + dx, GROUND + 2, z + dz, "candle[candles=3,lit=true,waterlogged=false]")
    V.set(x, GROUND + 1, z - 2, "bookshelf")
    V.set(x, GROUND + 2, z - 2, "bookshelf")
    V.set(x, GROUND + 3, z - 2, "amethyst_cluster[facing=up,waterlogged=false]")
    return {"min": [x - 2, GROUND + 1, z - 2], "max": [x + 2, GROUND + 2, z + 2], "label": [x + 0.5, GROUND + 3.4, z + 0.5]}


def signpost(x, z):
    V.set(x, GROUND + 1, z, "spruce_fence")
    V.set(x, GROUND + 2, z, "spruce_fence")
    V.set(x, GROUND + 3, z, "spruce_fence")
    V.set(x, GROUND + 4, z, "lantern[hanging=false,waterlogged=false]")
    lines = {
        (x + 1, GROUND + 3, z): ("east", ["AETHERION", "straight ahead,", "through the arch", ""]),
        (x + 1, GROUND + 2, z): ("east", ["THE PIT", "behind you", "(the red gate)", ""]),
        (x + 1, GROUND + 1, z): ("east", ["Bell = Discord", "Book = Modpack", "House = Pit-Shop", ""]),
    }
    for (sx, sy, sz), (facing, text) in lines.items():
        V.set(sx, sy, sz, "spruce_wall_sign[facing=%s,waterlogged=false]" % facing)
        V.nbt[(sx, sy, sz)] = sign_nbt(text)


def pond_and_duck(cx, cz):
    for x in range(cx - 9, cx + 10):
        for z in range(cz - 9, cz + 10):
            rr = math.hypot((x - cx) / 6.5, (z - cz) / 5.5) + 0.12 * float(vnoise(x, 0, z, 3, 7))
            if rr < 1.0:
                dep = 2 if rr < 0.7 else 1
                for y in range(GROUND + 1 - dep, GROUND + 1):
                    V.set(x, y, z, "water")
                V.set(x, GROUND - dep, z, "clay" if rnd(x, 0, z) < 0.5 else "sand")
                for y in range(GROUND + 1, GROUND + 4):
                    V.set(x, y, z, "air")
            elif rr < 1.2:
                V.set(x, GROUND, z, choose([("moss_block", 2), ("mossy_cobblestone", 1), ("grass_block", 2)],
                                          float(rnd(x, 1, z))))
    rng = np.random.default_rng(5)
    for _ in range(10):
        x, z = int(cx + rng.normal(0, 3)), int(cz + rng.normal(0, 2.5))
        if V.get(x, GROUND, z) == "water" and V.air(x, GROUND + 1, z):
            V.set(x, GROUND + 1, z, "lily_pad")
    duck(cx, GROUND - 1, cz)
    V.set(cx - 6, GROUND + 1, cz - 5, "oak_sign[rotation=6,waterlogged=false]")
    V.nbt[(cx - 6, GROUND + 1, cz - 5)] = sign_nbt(["PLEASE", "DO NOT FEED", "THE DUCK", "(it is rubber)"])


def duck(cx, wy, cz, k=0.62):
    """smaller rubber duck, looking west"""
    for x in range(cx - 6, cx + 7):
        for y in range(wy - 2, wy + 6):
            for z in range(cz - 5, cz + 6):
                dx, dz = (x - cx) / (6.2 * k), (z - cz) / (4.6 * k)
                lift = max(0.0, (x - cx - 2) * 0.5)
                dy = (y - (wy + 1.0)) / (3.3 * k)
                dy2 = (y - (wy + 1.0 + lift)) / (3.3 * k)
                if dx * dx + min(dy * dy, dy2 * dy2) + dz * dz <= 1.0:
                    V.set(x, y, z, "yellow_concrete")
    hx, hy, hz = cx - 2, wy + 4, cz
    for x in range(hx - 3, hx + 3):
        for y in range(hy - 2, hy + 3):
            for z in range(hz - 3, hz + 3):
                if ((x - hx) / 2.1) ** 2 + ((y - hy) / 2.0) ** 2 + ((z - hz) / 2.0) ** 2 <= 1.0:
                    V.set(x, y, z, "yellow_concrete")
    V.set(hx - 2, hy - 1, hz, "orange_concrete")
    V.set(hx - 3, hy - 1, hz, "orange_concrete")
    V.set(hx - 2, hy - 1, hz - 1, "orange_concrete")
    V.set(hx - 2, hy - 1, hz + 1, "orange_concrete")
    for s in (-1, 1):
        V.set(hx - 1, hy + 1, hz + s, "black_concrete")
    INFO["duck"] = {"min": [cx - 5, wy - 2, cz - 4], "max": [cx + 5, hy + 2, cz + 4], "label": [cx + 0.5, hy + 3.6, cz + 0.5]}


# ----------------------------------------------------------------------------- overlay (after the builds)

O = Vol()


def overlay_portal():
    """Aetherion portal at the west end of the shrine bridge, facing the gate; normal size."""
    px = -20                 # plane
    fy = GROUND + 3          # walking surface block top at y=GROUND+3 (feet GROUND+4)
    zc0, zc1 = -2, 1         # opening 4 wide
    h_straight = 4
    for z in range(zc0 - 2, zc1 + 3):
        for y in range(fy + 1, fy + 10):
            for n in (-1, 0, 1):
                O.set(px + n, y, z, "cave_air")
    cz = (zc0 + zc1) / 2.0
    for z in range(zc0 - 2, zc1 + 3):
        u = z - cz
        for v in range(1, 10):
            y = fy + v
            # pointed (gothic) arch: two circles of radius w offset to each side
            w = (zc1 - zc0 + 1) / 2.0
            if v <= h_straight:
                inner = abs(u) <= w - 0.5
                d = abs(u) - (w - 0.5)
            else:
                k = v - h_straight
                r1 = math.hypot(u + w * 0.6, k)
                r2 = math.hypot(u - w * 0.6, k)
                inner = max(r1, r2) <= w * 1.6 - 0.4 and k <= w * 1.3
                d = max(r1, r2) - (w * 1.6 - 0.4) if k <= w * 1.3 + 0.6 else 9
            if inner:
                O.set(px, y, z, "end_gateway")
                O.nbt[(px, y, z)] = G.gateway_nbt()
            elif d <= 1.2:
                O.set(px, y, z, "smooth_sandstone" if (v + int(abs(u))) % 4 else "chiseled_sandstone")
                O.set(px - 1, y, z, "smooth_sandstone")
                O.set(px + 1, y, z, "dark_prismarine" if d < 0.6 else "smooth_sandstone")
    # find top of the frame
    tops = [y for (x, y, z) in O.nbt if O.get(x, y, z) == "end_gateway"]
    ty = max(tops) + 2
    O.set(px, ty, int(cz), "sea_lantern")
    O.set(px, ty + 1, int(cz), "end_rod[facing=up]")
    for z in (zc0 - 2, zc1 + 2):
        O.set(px + 1, fy + 1, z, "dark_prismarine_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
    INFO["gate_aetherion"] = {"min": [px - 1, fy + 1, zc0], "max": [px, ty - 1, zc1],
                              "label": [px + 1.5, ty + 1.6, cz + 0.5]}


# ----------------------------------------------------------------------------- export

def export_vol(vol, outdir, name, dv=3955):
    """returns corner, block count (and stores the size in INFO for forceload)"""
    nz = np.argwhere(vol.a != 0)
    mn, mx = nz.min(0), nz.max(0)
    sub = vol.a[mn[0]:mx[0] + 1, mn[1]:mx[1] + 1, mn[2]:mx[2] + 1]
    corner = [int(mn[0] + X0), int(mn[1] + Y0), int(mn[2] + Z0)]
    pal = [("minecraft:air" if s == "cave_air" else s) for s in vol.pal]
    rel = {(k[0] - corner[0], k[1] - corner[1], k[2] - corner[2]): v for k, v in vol.nbt.items()
           if vol.get(*k) != "air"}
    pts = np.argwhere(sub != 0)
    vals = sub[pts[:, 0], pts[:, 1], pts[:, 2]]
    blocks = [(int(p[0]), int(p[1]), int(p[2]), int(v)) for p, v in zip(pts, vals)]
    write_structure(os.path.join(outdir, name + ".nbt"), (mx - mn + 1).tolist(), pal, blocks, rel, dv)
    INFO.setdefault("bounds", []).append([corner[0], corner[2], corner[0] + int(mx[0] - mn[0]), corner[2] + int(mx[2] - mn[2])])
    return corner, len(blocks)


def export_schem(sc, outdir):
    """the build as its own template (its DataVersion, so vanilla upgrades states + block entities)"""
    x0, x1, z0, z1 = sc.crop or (0, sc.a.shape[0] - 1, 0, sc.a.shape[2] - 1)
    x1, z1 = min(x1, sc.a.shape[0] - 1), min(z1, sc.a.shape[2] - 1)
    pal_idx, pal = {}, []
    blocks, nbt = [], {}
    dropped = 0
    for lx, ly, lz, s, wx, wy, wz in sc.cells():
        if ly == 0 and s.split("[")[0] in SOFT_GROUND and not V.solid(wx, GROUND, wz) and \
                not V.get(wx, GROUND, wz).startswith(("grass", "dirt", "moss", "coarse")):
            dropped += 1
            continue
        if s not in pal_idx:
            pal_idx[s] = len(pal)
            pal.append(s)
        rx, rz = lx - x0, lz - z0
        blocks.append((rx, ly, rz, pal_idx[s]))
        b = sc.be.get((lx, ly, lz))
        if b is not None:
            nbt[(rx, ly, rz)] = be_to_nbt(b)
    size = [x1 - x0 + 1, sc.a.shape[1], z1 - z0 + 1]
    write_structure(os.path.join(outdir, "hub_%s.nbt" % sc.name), size, pal, blocks, nbt, sc.dv)
    return len(blocks), dropped


def preview(outdir, schems):
    """one numpy volume with everything for render.py (states not rotated, fine for a look)"""
    W = Vol()
    W.a[:] = 0
    for i, s in enumerate(V.pal):
        W.p(s)
    W.a[:] = V.a
    for sc in schems:
        for lx, ly, lz, s, wx, wy, wz in sc.cells():
            W.set(wx, wy, wz, s.replace("minecraft:", "").replace("minecraft:grass[", "short_grass[")
                  if s != "minecraft:grass" else "short_grass")
    for idx in np.argwhere(O.a != 0):
        s = O.pal[O.a[tuple(idx)]]
        W.a[tuple(idx)] = 0 if s == "cave_air" else W.p(s)
    nz = np.argwhere(W.a != 0)
    mn, mx = nz.min(0), nz.max(0)
    sub = W.a[mn[0]:mx[0] + 1, mn[1]:mx[1] + 1, mn[2]:mx[2] + 1]
    np.savez_compressed(os.path.join(outdir, "hub2.npz"), a=sub.astype(np.int16),
                        corner=np.array([mn[0] + X0, mn[1] + Y0, mn[2] + Z0]), pal=np.array(W.pal))


def main(outdir, srcdir):
    os.makedirs(outdir, exist_ok=True)
    schems = [Schem(n, os.path.join(srcdir, n + ".bin")) for n in ("shrine", "tower", "house")]
    foot = set()
    for sc in schems:
        foot |= sc.footprint()
    X, Z, inside = build_island(foot)
    plaza()
    path([(12, 0), (-2, 0)], 3, 11)                      # plaza -> shrine gate
    path([(15, -6), (9, -14), (8, -19)], 1, 12)          # -> spell book at the tower
    path([(28, 0), (40, 0)], 2, 13)                      # -> Pit gate
    path([(22, 8), (24, 14), (22, 19)], 1, 14)           # -> elven house
    disc = belfry(19, -12)
    book = spellbook(8, -18)
    signpost(21, 4)
    pond_and_duck(35, 12)
    pit = small_arch(43, GROUND, 0, "x", 3, 3, {"rim": "crying_obsidian", "body": "polished_blackstone_bricks",
                                                "outer": "red_nether_bricks", "key": "chiseled_polished_blackstone",
                                                "crown": "soul_lantern[hanging=false,waterlogged=false]",
                                                "base": "polished_blackstone_wall"})
    for s in (-1, 1):
        V.set(41, GROUND + 1, 4 * s, "polished_blackstone_bricks")
        V.set(41, GROUND + 2, 4 * s, "soul_campfire[facing=west,lit=true,signal_fire=false,waterlogged=false]")
    INFO["gate_pit"] = {"min": [42, GROUND + 1, -1], "max": [43, GROUND + 5, 1], "label": [41.5, pit["top"] + 1.7, 0.5]}
    INFO["prop_discord"] = disc
    INFO["prop_modpack"] = book
    # trees (outside the builds)
    for (x, z, sd) in ((30, -14, 1), (36, -6, 2), (12, 12, 3), (-2, 24, 4), (-4, -30, 5), (40, 28, 6)):
        if (x, z) in foot:
            continue
        gy = V.top(x, z, 80, 50)
        if gy is not None and V.get(x, gy, z).startswith("grass_block"):
            cherry_tree(x, gy, z, 500 + sd, spread=0.9)
    for (x, z, sd) in ((26, -10, 1), (14, 9, 2), (31, 6, 3), (4, -9, 4), (4, 9, 5), (16, -16, 6)):
        gy = V.top(x, z, 80, 50)
        if gy is not None and V.get(x, gy, z).startswith(("grass_block", "moss")):
            azalea_bush(x, gy, z, 600 + sd, 1.3)

    def exclude(x, z):
        return (x, z) in foot or math.hypot(x - 20, z) < 9.5

    ground_cover(X, Z, inside, 77, exclude)
    G.build_pit()
    for k in ("pit_entrance", "pit_board", "gate_home", "pit_box"):
        INFO[k] = G.INFO[k]
    G.build_sky()
    overlay_portal()

    corner, n = export_vol(V, outdir, "hub_base")
    INFO["base_corner"], INFO["base_blocks"] = corner, n
    oc, on = export_vol(O, outdir, "hub_overlay")
    INFO["overlay_corner"] = oc
    for sc in schems:
        nb, dropped = export_schem(sc, outdir)
        INFO["schem_" + sc.name] = {"pos": list(sc.pos), "rotation": sc.rotation, "blocks": nb, "dropped_ground": dropped}
    INFO["spawn"] = list(SPAWN)
    INFO["place"] = (["place template minecraft:hub_base %d %d %d" % tuple(corner)]
                     + ["place template minecraft:hub_%s %d %d %d %s" % (sc.name, *sc.pos, sc.rotation)
                        for sc in schems]
                     + ["place template minecraft:hub_overlay %d %d %d" % tuple(oc)])
    bx0 = min(b[0] for b in INFO["bounds"]); bz0 = min(b[1] for b in INFO["bounds"])
    bx1 = max(b[2] for b in INFO["bounds"]); bz1 = max(b[3] for b in INFO["bounds"])
    xm = (bx0 + bx1) // 2
    INFO["forceload"] = ["forceload add %d %d %d %d" % (bx0, bz0, xm, bz1), "forceload add %d %d %d %d" % (xm + 1, bz0, bx1, bz1)]
    preview(outdir, schems)
    with open(os.path.join(outdir, "hub2.json"), "w") as f:
        json.dump(INFO, f, indent=1)
    print(json.dumps({k: INFO[k] for k in ("place", "island_box", "base_blocks")}, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
