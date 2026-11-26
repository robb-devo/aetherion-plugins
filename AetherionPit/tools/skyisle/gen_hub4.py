"""Hub round 4 (2026-11-27, the final polish run): same island and builds as round 3, with Robb's list worked in.

  python gen_hub4.py <outdir> <dir with shrine.bin tower.bin house.bin>

Against round 3: far less grass (meadow patches instead of a carpet), every scroll at eye level, lamps at full light
and a later sun so they really glow, the bell arch replaced by the Discord owl post (a perch for the owl models), the
spell book by the tinkerer's chest (modpack), and The Pit replaced by Sky Brawl: a storm platform in the clouds where
you knock each other off with wind (gate, lobby ledge with the way home, the disc, four floating stepping stones).

Writes hub_base, hub_shrine, hub_tower, hub_house, hub_overlay and hub4.json (placement, features, the builder's
time and biome commands, every coordinate AetherionPit needs).
"""
import json
import math
import os
import sys

import numpy as np

import gen_skyisle as G
import gen_hub2 as H
import gen_hub3 as H3
from gen_skyisle import V, rnd, choose, disc_iter, stairs, put_gateway, fbm, island, underside_decor

GROUND = H.GROUND
INFO = H.INFO
EYE = GROUND + 1 + 1.75          # where a scroll's centre hangs: eye height of a standing player
SPAWN = H3.SPAWN
TIME = 12350                      # the sun on the horizon: warm light, and the lamps visibly glow

FLOWERS = [("poppy", 1), ("dandelion", 0.6), ("allium", 1.4), ("azure_bluet", 1.4), ("oxeye_daisy", 1.6),
           ("cornflower", 1.2), ("lily_of_the_valley", 1.4), ("pink_tulip", 1.2), ("white_tulip", 1.0)]


def light(x, y, z, level=15):
    if V.air(x, y, z) or V.get(x, y, z).startswith("light["):
        V.set(x, y, z, "light[level=%d,waterlogged=false]" % level)


# ----------------------------------------------------------------------------- ground: meadow patches, not a carpet

def ground_cover4(X, Z, inside, seed, exclude_fn, density=1.0):
    """Short grass and flowers only in a few meadow patches (fbm > 0.42), the rest stays clean lawn with a rare
    flower; never on the paths (those are not grass anyway) or next to the props (exclude_fn)."""
    rng = np.random.default_rng(seed)
    tall = [("lilac", 1), ("peony", 1.3), ("rose_bush", 0.4)]
    nx, nz = inside.shape
    for i in range(nx):
        for j in range(nz):
            if not inside[i, j]:
                continue
            x, z = int(X[i, j]), int(Z[i, j])
            if exclude_fn(x, z):
                continue
            t = V.top(x, z, 100, 30)
            if t is None or not V.get(x, t, z).startswith("grass_block") or not V.air(x, t + 1, z):
                continue
            meadow = float(fbm(x, 0, z, 7, 2, seed + 5))
            r = rng.random() / density
            if meadow > 0.42:
                if r < 0.20:
                    V.set(x, t + 1, z, "short_grass")
                elif r < 0.23:
                    V.set(x, t + 1, z, "tall_grass[half=lower]")
                    V.set(x, t + 2, z, "tall_grass[half=upper]")
                elif r < 0.33:
                    V.set(x, t + 1, z, choose(FLOWERS, rng.random()))
                elif r < 0.345:
                    f = choose(tall, rng.random())
                    V.set(x, t + 1, z, f + "[half=lower]")
                    V.set(x, t + 2, z, f + "[half=upper]")
            else:
                if r < 0.018:
                    V.set(x, t + 1, z, "short_grass")
                elif r < 0.026:
                    V.set(x, t + 1, z, choose(FLOWERS, rng.random()))


def thin_soft(x0, z0, x1, z1, keep=0.3, seed=5):
    """thin out vegetation the schematics and features brought along (keep a share of it)"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(GROUND - 4, GROUND + 14):
                s = V.get(x, y, z).split("[")[0]
                if s in ("short_grass", "fern", "tall_grass", "large_fern") and rnd(x, y, z, seed) > keep:
                    V.set(x, y, z, "air")
                    if s in ("tall_grass", "large_fern") and V.get(x, y + 1, z).split("[")[0] == s:
                        V.set(x, y + 1, z, "air")


# ----------------------------------------------------------------------------- lamps (display models + full light)

def lamp(x, z, yaw, ground=GROUND):
    fx, fz = x + 0.5, z + 0.5
    S = 1.35
    d = (-math.sin(math.radians(yaw)), math.cos(math.radians(yaw)))
    reach = (14.2 / 16 - 0.5) * S
    hx, hz = fx + d[0] * reach, fz + d[1] * reach
    hy = ground + 1 + 2.126 - 0.37
    light(math.floor(hx), math.floor(hy), math.floor(hz), 15)
    light(math.floor(fx), ground + 1, math.floor(fz), 12) if V.air(math.floor(fx), ground + 1, math.floor(fz)) else None
    H3.LAMPS.append({"type": "lamp", "x": round(fx, 2), "y": float(ground + 1), "z": round(fz, 2), "yaw": float(yaw)})


# ----------------------------------------------------------------------------- Discord: the owl post

def owl_post(x, z):
    """a round calcite dais with blue flowers; the perch and the owl are display models (AetherionPit HubOwl)"""
    for xx, zz, d in disc_iter(x, z, 2.7):
        V.set(xx, GROUND, zz, "chiseled_quartz_block" if d < 0.6 else ("calcite" if d < 1.6 else
                                                                       ("smooth_quartz" if d < 2.2 else "cut_sandstone")))
        for y in range(GROUND + 1, GROUND + 6):
            V.set(xx, y, zz, "air")
    for (dx, dz, f) in ((3, 0, "cornflower"), (-3, 1, "blue_orchid"), (1, 3, "cornflower"), (-2, -3, "blue_orchid"),
                        (3, -2, "lily_of_the_valley"), (-3, -1, "cornflower")):
        if V.get(x + dx, GROUND, z + dz).startswith("grass_block") and V.air(x + dx, GROUND + 1, z + dz):
            V.set(x + dx, GROUND + 1, z + dz, f)
    light(x, GROUND + 4, z, 15)
    INFO["owl"] = {"x": x + 0.5, "y": float(GROUND + 1), "z": z + 0.5, "yaw": 0.0}
    return {"min": [x - 1, GROUND + 1, z - 1], "max": [x + 1, GROUND + 3, z + 1], "label": [x + 3.2, EYE, z + 1.8]}


# ----------------------------------------------------------------------------- modpack: the tinkerer's chest

def chest_nook(x, z):
    """the reading nook stays (bookshelves, candles, amethyst); the lectern makes room for the chest display"""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                V.set(x + dx, GROUND, z + dz, "mossy_stone_bricks" if (dx + dz) % 3 else "chiseled_stone_bricks")
                for y in range(GROUND + 1, GROUND + 5):
                    V.set(x + dx, y, z + dz, "air")
    V.set(x, GROUND, z, "polished_andesite")
    for dx, dz in ((-2, 0), (2, 0)):
        V.set(x + dx, GROUND + 1, z + dz, "bookshelf")
        V.set(x + dx, GROUND + 2, z + dz, "candle[candles=3,lit=true,waterlogged=false]")
    V.set(x, GROUND + 1, z - 2, "bookshelf")
    V.set(x, GROUND + 2, z - 2, "bookshelf")
    V.set(x, GROUND + 3, z - 2, "amethyst_cluster[facing=up,waterlogged=false]")
    V.set(x - 1, GROUND + 1, z - 2, "barrel[facing=up,open=false]")
    V.set(x + 1, GROUND + 1, z - 2, "chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,"
                                    "slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=true,slot_5_occupied=false]")
    light(x, GROUND + 3, z, 15)
    INFO["chest"] = {"x": x + 0.5, "y": float(GROUND + 1), "z": z + 0.5, "yaw": 0.0}
    return {"min": [x - 2, GROUND + 1, z - 2], "max": [x + 2, GROUND + 2, z + 2], "label": [x + 3.6, EYE, z + 1.6]}


# ----------------------------------------------------------------------------- Sky Brawl: gate on the island

def brawl_gate():
    """storm gate at the east end: deepslate and oxidized copper, lightning rods, soul-fire braziers"""
    px, fy = 43, GROUND
    for z in range(-5, 6):
        for y in range(fy + 1, fy + 10):
            for n in (-1, 0, 1):
                V.set(px + n, y, z, "air")
    mats = {"inner": "waxed_oxidized_cut_copper", "body": "polished_deepslate", "stairs": "polished_deepslate_stairs",
            "key": "chiseled_deepslate", "trim": "deepslate_tile_wall[east=none,north=none,south=none,up=true,"
                                                  "waterlogged=false,west=none]",
            "trim_cap": "lightning_rod[facing=up,powered=false,waterlogged=false]"}
    top, cells = H3.pointed_frame(V, px, fy, -1, 1, mats)
    for (x, y, z) in cells:
        put_gateway(x, y, z)
    V.set(px, top + 1, 0, "waxed_oxidized_copper")
    V.set(px, top + 2, 0, "lightning_rod[facing=up,powered=false,waterlogged=false]")
    for z in (-3, 3):
        V.set(px - 1, fy + 3, z, "light_blue_wall_banner[facing=west]")
    for z in (-4, 4):
        V.set(41, fy + 1, z, "polished_deepslate")
        V.set(41, fy + 2, z, "soul_soil")
        V.set(41, fy + 3, z, "soul_fire")
        for dz in (-1, 1):
            V.set(41, fy + 1, z + dz, "polished_deepslate_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]"
                  % ("north" if dz < 0 else "south"))
    for z in range(-1, 2):
        V.set(px - 1, fy, z, "waxed_oxidized_cut_copper" if z == 0 else "polished_deepslate")
        V.set(px - 2, fy, z, "polished_deepslate")
    INFO["gate_brawl"] = {"min": [px - 1, fy + 1, -1], "max": [px, top, 1], "label": [px - 3.0, EYE, -3.6]}
    return top


# ----------------------------------------------------------------------------- Sky Brawl: the storm platform

BRAWL_C = (152, 0)
BRAWL_Y = 60          # floor block y, players stand at 61


def brawl_arena():
    cx, cz = BRAWL_C
    fy = BRAWL_Y

    def flat(X, Z):
        return fy + 0 * X

    # the disc
    X, Z, inside, top, bottom = island(cx, cz, 11.6, 11.6, flat, 26, 4242, edge_noise=0.03)
    underside_decor(X, Z, inside, top, bottom, 4242)
    for x, z, d in disc_iter(cx, cz, 11.4):
        a = math.degrees(math.atan2(z - cz, x - cx)) % 360
        ray = min(a % 45, 45 - a % 45) * math.pi / 180 * d < 0.6 and 1.8 < d < 9.0
        r = float(rnd(x, 7, z, 33))
        if d < 1.6:
            s = "waxed_oxidized_chiseled_copper"
        elif ray:
            s = "waxed_oxidized_cut_copper"
        elif d < 9.0:
            s = choose([("smooth_quartz", 5), ("calcite", 2), ("polished_diorite", 1)], r)
        elif d < 10.2:
            s = "polished_deepslate"
        else:
            s = "deepslate_tiles"
        V.set(x, fy, z, s)
        V.set(x, fy - 1, z, "deepslate")
        for y in range(fy + 1, fy + 16):
            V.set(x, y, z, "air")
    # copper bulbs in the floor between the rays: the disc glows at dusk
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        V.set(round(cx + math.cos(a) * 6.6), fy, round(cz + math.sin(a) * 6.6), "waxed_copper_bulb[lit=true,powered=false]")
    # four floating stepping stones at different heights: reachable with a wind charge
    stones = []
    for k, (ang, dist, dy, rr) in enumerate(((40, 17.5, -2, 3.3), (130, 17.0, 2, 2.9), (220, 17.5, -1, 3.1),
                                             (310, 17.0, 3, 2.7))):
        px = round(cx + math.cos(math.radians(ang)) * dist)
        pz = round(cz + math.sin(math.radians(ang)) * dist)
        py = fy + dy
        Xs, Zs, ins, tops, bots = island(px, pz, rr, rr, lambda X, Z, py=py: py + 0 * X, 12, 4300 + k, edge_noise=0.08)
        underside_decor(Xs, Zs, ins, tops, bots, 4300 + k)
        for x, z, d in disc_iter(px, pz, rr - 0.4):
            if V.get(x, py, z) not in ("air", "cave_air"):
                V.set(x, py, z, "calcite" if d < rr - 1.3 else "polished_deepslate")
                for y in range(py + 1, py + 8):
                    V.set(x, y, z, "air")
        V.set(px, py, pz, "waxed_copper_bulb[lit=true,powered=false]")
        V.set(px, py + 1, pz, "lightning_rod[facing=up,powered=false,waterlogged=false]")
        stones.append([px, py + 1, pz])
    # bridge from the lobby ledge to the disc
    for x in range(135, cx - 10):
        for z in range(-1, 2):
            V.set(x, fy, z, "polished_deepslate" if z else "waxed_oxidized_cut_copper")
            V.set(x, fy - 1, z, "deepslate_tile_slab[type=top,waterlogged=false]")
            for y in range(fy + 1, fy + 8):
                V.set(x, y, z, "air")
    # the lobby ledge: a safe little terrace with low walls, the way home and the board
    lx0, lx1, lz0, lz1 = 122, 134, -7, 7
    Xl, Zl, insl, topl, botl = island(128, 0, 8.0, 9.5, flat, 14, 4400, edge_noise=0.05)
    underside_decor(Xl, Zl, insl, topl, botl, 4400)
    for x in range(lx0, lx1 + 1):
        for z in range(lz0, lz1 + 1):
            edge = x in (lx0, lx1) or z in (lz0, lz1)
            V.set(x, fy, z, "deepslate_tiles" if edge else ("waxed_oxidized_cut_copper" if z == 0 and x > 126 else
                                                            ("polished_deepslate" if (x + z) % 2 else "deepslate_bricks")))
            V.set(x, fy - 1, z, "deepslate")
            for y in range(fy + 1, fy + 10):
                V.set(x, y, z, "air")
            if edge and not (x == lx1 and -1 <= z <= 1):
                V.set(x, fy + 1, z, "deepslate_tile_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    for z in (-7, 7):
        for x in (lx0, lx1):
            V.set(x, fy + 2, z, "soul_lantern[hanging=false,waterlogged=false]")
    gx = 124
    top, cells = H3.pointed_frame(V, gx, fy, -1, 1, {
        "inner": "waxed_oxidized_cut_copper", "body": "polished_deepslate", "stairs": "polished_deepslate_stairs",
        "key": "chiseled_deepslate", "trim": "deepslate_tile_wall[east=none,north=none,south=none,up=true,"
                                             "waterlogged=false,west=none]",
        "trim_cap": "lightning_rod[facing=up,powered=false,waterlogged=false]"})
    for (x, y, z) in cells:
        put_gateway(x, y, z)
    for (x, z) in ((127, -4), (127, 4), (132, -4), (132, 4)):
        light(x, fy + 3, z, 15)
    # clouds below: you fall into them
    G.cloud(cx - 6, fy - 22, cz, 52, 36, 951)
    INFO["gate_home"] = {"min": [gx, fy + 1, -1], "max": [gx + 1, top, 1], "label": [gx + 3.0, fy + 1 + 1.75, -3.4]}
    INFO["brawl"] = {
        "center": [cx + 0.5, fy + 1.0, cz + 0.5], "radius": 11.0, "floor": float(fy + 1), "catch_y": float(fy - 12),
        "ledge": {"min": [lx0, fy - 6, lz0], "max": [lx1, fy + 12, lz1]},
        "arrive": [130.5, float(fy + 1), 0.5, -90.0],
        "orb": [cx + 0.5, fy + 1.2, cz + 0.5],
        "spire": [cx + 0.5, fy + 7.5, cz + 0.5],
        "board": [129.5, fy + 1 + 1.9, 4.6],
        "stones": stones,
    }
    INFO["pit_entrance"] = INFO["brawl"]["arrive"]
    INFO["pit_board"] = [129.5, fy + 1 + 1.9, 4.6, -90.0]
    INFO["pit_box"] = [lx0 - 2, cz - 24, cx + 24, cz + 24]


# ----------------------------------------------------------------------------- main

def main(outdir, srcdir):
    os.makedirs(outdir, exist_ok=True)
    G.ground_cover = lambda X, Z, inside, seed, ex: ground_cover4(X, Z, inside, seed, ex, density=0.8)
    schems = [H.Schem(n, os.path.join(srcdir, n + ".bin")) for n in ("shrine", "tower", "house")]
    foot = set()
    for sc in schems:
        foot |= sc.footprint()
    tower_cells = set()
    for sc in schems:
        if sc.name == "tower":
            for lx, ly, lz, s, wx, wy, wz in sc.cells():
                tower_cells.add((wx, wy, wz))
    X, Z, inside = H.build_island(foot)
    H3.lamp = lamp                 # the plaza lamps too: full light
    H3.plaza3()
    H.path([(12, 0), (-2, 0)], 3, 11)
    H.path([(15, -6), (10, -14), (9, -18)], 1, 12)
    H.path([(28, 0), (40, 0)], 2, 13)
    H.path([(22, 8), (24, 14), (22, 19)], 1, 14)
    disc = owl_post(19, -13)
    book = chest_nook(8, -18)
    H.pond_and_duck(35, 12)
    INFO["shop_stall"] = H3.shop_stall(27, 18)
    INFO["shop_stall"]["label"] = [24.6, EYE, 16.2]
    brawl_gate()
    H3.aetherion_portal()
    ga = INFO["gate_aetherion"]
    ga["label"] = [-16.5, GROUND + 3 + 1 + 1.75, -4.6]
    INFO["prop_discord"] = disc
    INFO["prop_modpack"] = book
    for (x, z, tx, tz) in ((8, -4, 8, 0), (8, 4, 8, 0), (2, -4, 2, 0), (2, 4, 2, 0), (32, -3, 32, 0), (37, 3, 37, 0),
                           (11, -18, 8, -18), (22, -10, 19, -13), (25, 17, 25, 21), (17, 17, 17, 21)):
        lamp(x, z, H3.yaw_to(x, z, tx, tz))
    for (x, z) in ((-14, -3), (-14, 2)):
        lamp(x, z, H3.yaw_to(x, z, x, 0), ground=GROUND + 3)
    H3.sky_path(tower_cells)
    pk = INFO["parkour"]
    pk["board"] = [pk["start"][0] + 2.6, EYE + 0.15, pk["start"][2] + 1.4]
    trees = [("minecraft:cherry", 33, -14), ("minecraft:cherry", 40, -6), ("minecraft:cherry", 11, 13),
             ("minecraft:cherry", -4, 26), ("minecraft:cherry", 42, 26), ("minecraft:cherry", 28, -24),
             ("minecraft:cherry", 18, 30), ("minecraft:azalea_tree", 4, -10), ("minecraft:azalea_tree", 30, 7),
             ("minecraft:azalea_tree", 46, 12), ("minecraft:cherry", -6, -24)]
    INFO["features"] = []
    for (f, x, z) in trees:
        if (x, z) in foot:
            continue
        gy = V.top(x, z, 80, 50)
        if gy is not None and V.get(x, gy, z).startswith(("grass_block", "moss_block")):
            V.set(x, gy, z, "grass_block[snowy=false]" if f.endswith("cherry") else "rooted_dirt")
            for y in range(gy + 1, gy + 9):
                if V.get(x, y, z).split("[")[0] in G.SOFT:
                    V.set(x, y, z, "air")
            INFO["features"].append("place feature %s %d %d %d" % (f, x, gy + 1, z))
    for (x, z, sd) in ((26, -10, 1), (14, 9, 2), (4, 9, 5), (-6, -10, 7)):
        gy = V.top(x, z, 80, 50)
        if gy is not None and V.get(x, gy, z).startswith(("grass_block", "moss")):
            G.azalea_bush(x, gy, z, 600 + sd, 1.2)

    def exclude(x, z):
        near_prop = (math.hypot(x - 19, z + 13) < 4.5 or math.hypot(x - 8, z + 18) < 4.5 or
                     math.hypot(x - 22.5, z - 4.5) < 2.5)
        return (x, z) in foot or math.hypot(x - 20, z) < 9.5 or near_prop

    ground_cover4(X, Z, inside, 77, exclude)
    brawl_arena()
    G.build_sky()
    thin_soft(-103, -90, 181, 91, keep=0.35)
    signs = [
        {"text": "AETHERION", "x": -20, "z": 0, "y": 2.5},
        {"text": "SKY BRAWL", "x": 43, "z": 0, "y": 2.1},
        {"text": "SKY PATH", "x": INFO["parkour"]["start"][0], "z": INFO["parkour"]["start"][2], "y": 1.7},
        {"text": "DISCORD", "x": 19, "z": -13, "y": 1.3},
        {"text": "MODPACK", "x": 8, "z": -18, "y": 0.9}]
    INFO["decor"] = H3.LAMPS + [{"type": "boat", "x": 31.5, "y": 64.6, "z": 15.5, "yaw": 30.0},
                                {"type": "signpost", "x": 22.5, "y": float(GROUND + 1), "z": 4.5, "boards": signs}]
    INFO["welcome"] = [19.5, EYE + 0.25, -5.5]
    INFO["duck"]["label"] = [29.4, EYE, 7.6]
    cp2 = INFO["parkour"]["checkpoints"][1]
    fin = INFO["parkour"]["finish"]
    st = INFO["brawl"]["stones"]
    INFO["ducklings"] = [
        {"x": fin[0] + 1.5, "y": float(fin[1]), "z": fin[2] + 0.5, "yaw": 0.0},
        {"x": cp2[0] + 1.5, "y": float(cp2[1]), "z": cp2[2] + 1.5, "yaw": 200.0},
        {"x": 39.5, "y": 64.9, "z": 13.5, "yaw": 90.0},
        {"x": -23.5, "y": 68.0, "z": 5.5, "yaw": 200.0},
        {"x": st[1][0] + 1.5, "y": float(st[1][1]), "z": st[1][2] + 0.5, "yaw": 90.0},
        {"x": 21.5, "y": 65.0, "z": -15.5, "yaw": 20.0},
        {"x": 14.5, "y": 66.0, "z": 24.5, "yaw": 180.0},
    ]
    for dk in INFO["ducklings"]:
        x, y, z = math.floor(dk["x"]), math.floor(dk["y"]), math.floor(dk["z"])
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for yy in (y, y + 1):
                    if V.get(x + dx, yy, z + dz).split("[")[0] in G.SOFT:
                        V.set(x + dx, yy, z + dz, "air")

    corner, n = H.export_vol(V, outdir, "hub_base")
    INFO["base_corner"], INFO["base_blocks"] = corner, n
    oc, on = H.export_vol(H.O, outdir, "hub_overlay")
    INFO["overlay_corner"] = oc
    for sc in schems:
        nb, dropped = H.export_schem(sc, outdir)
        INFO["schem_" + sc.name] = {"pos": list(sc.pos), "rotation": sc.rotation, "blocks": nb, "dropped_ground": dropped}
    INFO["spawn"] = list(SPAWN)
    INFO["time"] = TIME
    INFO["place"] = (["place template minecraft:hub_base %d %d %d" % tuple(corner)]
                     + ["place template minecraft:hub_%s %d %d %d %s" % (sc.name, *sc.pos, sc.rotation) for sc in schems]
                     + ["place template minecraft:hub_overlay %d %d %d" % tuple(oc)])
    bx0 = min(b[0] for b in INFO["bounds"])
    bz0 = min(b[1] for b in INFO["bounds"])
    bx1 = max(b[2] for b in INFO["bounds"])
    bz1 = max(b[3] for b in INFO["bounds"])
    xm = (bx0 + bx1) // 2
    INFO["forceload"] = ["forceload add %d %d %d %d" % (bx0, bz0, xm, bz1), "forceload add %d %d %d %d" % (xm + 1, bz0, bx1, bz1)]
    INFO.pop("gate_pit", None)
    H.preview(outdir, schems)
    with open(os.path.join(outdir, "hub4.json"), "w") as f:
        json.dump(INFO, f, indent=1)
    print(json.dumps({k: INFO[k] for k in ("place", "island_box", "base_blocks", "parkour", "brawl")}, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
