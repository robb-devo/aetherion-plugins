"""Hub round 3 (2026-11-26, polish pass): same island and builds as round 2, everything around them redone.

  python gen_hub3.py <outdir> <dir with shrine.bin tower.bin house.bin>

New against round 2: a quieter plaza with benches, slim lamp posts (display models; the light is a vanilla light
block written here), the signpost as a display prop, an elven bell arch, pointed portal frames for Aetherion and
The Pit (soul-fire braziers instead of smoking campfires), vanilla cherry/azalea trees (placed by the builder with
/place feature), the Sky Path jump and run around the elven tower, seven lost ducklings, and a proper Pit arena.

Writes the same templates as gen_hub2 (hub_base, hub_shrine, hub_tower, hub_house, hub_overlay) plus hub3.json
with placement commands, tree features and every coordinate AetherionPit needs.
"""
import json
import math
import os
import sys

import numpy as np

import gen_skyisle as G
import gen_hub2 as H
from gen_skyisle import V, rnd, choose, disc_iter, stairs, put_gateway, sign_nbt, fbm, vnoise, island, underside_decor

GROUND = H.GROUND
INFO = H.INFO
O = H.O
SPAWN = (25.5, 65.0, 0.5, 90.0, -6.0)
TOWER_C = (6.0, -31.0)


def light(x, y, z, level=14):
    if V.air(x, y, z):
        V.set(x, y, z, "light[level=%d,waterlogged=false]" % level)


# ----------------------------------------------------------------------------- lamps (display models + light)

LAMPS = []


def lamp(x, z, yaw, ground=GROUND):
    """foot at the block centre of (x, z) on top of the ground block; the arm points along yaw (MC yaw)"""
    fx, fz = x + 0.5, z + 0.5
    S = 1.35
    d = (-math.sin(math.radians(yaw)), math.cos(math.radians(yaw)))
    reach = (14.2 / 16 - 0.5) * S
    hx, hz = fx + d[0] * reach, fz + d[1] * reach
    hy = ground + 1 + 2.126 - 0.37
    light(math.floor(hx), math.floor(hy), math.floor(hz), 14)
    LAMPS.append({"type": "lamp", "x": round(fx, 2), "y": float(ground + 1), "z": round(fz, 2), "yaw": float(yaw)})


def yaw_to(x, z, tx, tz):
    return math.degrees(math.atan2(-(tx - x), tz - z))


# ----------------------------------------------------------------------------- plaza

def plaza3():
    cx, cz, R = 20, 0, 8.5
    for x, z, d in disc_iter(cx, cz, R):
        a = math.degrees(math.atan2(z - cz, x - cx)) % 360
        ray = min(a % 45, 45 - a % 45) * math.pi / 180 * d < 0.55 and 3.2 < d < 6.6
        r = float(rnd(x // 2, 0, z // 2, 31))
        if d > R - 1.0:
            s = "chiseled_sandstone" if (round(a) // 15) % 2 == 0 else "cut_sandstone"
        elif R - 1.9 < d <= R - 1.0:
            s = "polished_diorite"
        elif ray:
            s = "cut_sandstone"
        elif 2.6 < d <= 3.3:
            s = "waxed_oxidized_cut_copper"
        elif d <= 1.2:
            s = "chiseled_quartz_block"
        elif d <= 2.6:
            s = "smooth_quartz"
        else:
            s = choose([("smooth_sandstone", 6), ("sandstone", 1), ("calcite", 1.2)], r)
        V.set(x, GROUND, z, s)
        for y in range(GROUND + 1, GROUND + 6):
            V.set(x, y, z, "air")
    light(cx, GROUND - 1, cz, 15)
    V.set(cx, GROUND - 1, cz, "sea_lantern")
    # four benches (stairs with trapdoor armrests), facing the centre, on the diagonals
    for k in range(4):
        a = math.radians(45 + k * 90)
        ux, uz = math.cos(a), math.sin(a)
        bx, bz = cx + ux * (R - 2.2), cz + uz * (R - 2.2)
        facing = ("east" if ux > 0 else "west") if abs(ux) >= abs(uz) else ("south" if uz > 0 else "north")
        # bench runs perpendicular to the radius: along z if facing east/west, else along x
        along = (0, 1) if facing in ("east", "west") else (1, 0)
        cells = [(round(bx - along[0] * 0.5), round(bz - along[1] * 0.5)),
                 (round(bx + along[0] * 0.5), round(bz + along[1] * 0.5))]
        if cells[0] == cells[1]:
            cells[1] = (cells[0][0] + along[0], cells[0][1] + along[1])
        for (x, z) in cells:
            V.set(x, GROUND + 1, z, stairs("spruce_stairs", facing))
        ends = [(cells[0][0] - along[0], cells[0][1] - along[1]), (cells[1][0] + along[0], cells[1][1] + along[1])]
        for (x, z), side in zip(ends, ("a", "b")):
            if along == (0, 1):
                f = "north" if side == "a" else "south"
            else:
                f = "west" if side == "a" else "east"
            V.set(x, GROUND + 1, z, "spruce_trapdoor[facing=%s,half=bottom,open=true,powered=false,waterlogged=false]" % f)
        # flower pots behind the bench
        px, pz = round(bx + ux * 1.4), round(bz + uz * 1.4)
        V.set(px, GROUND + 1, pz, "flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]")
    for k in range(4):
        a = math.radians(k * 90 + 25)
        lx, lz = round(cx + math.cos(a) * (R - 0.6)), round(cz + math.sin(a) * (R - 0.6))
        lamp(lx, lz, yaw_to(lx, lz, cx, cz))


# ----------------------------------------------------------------------------- bell arch (Discord)

def bell_arch(x, z):
    """an elven bell arch: two birch pillars, a cherry beam, the bell on a chain, petals and leaves"""
    for dx in range(-3, 4):
        for dz in range(-2, 3):
            if abs(dx) <= 2 and abs(dz) <= 1:
                V.set(x + dx, GROUND, z + dz, "smooth_sandstone" if (dx + dz) % 2 else "cut_sandstone")
                for y in range(GROUND + 1, GROUND + 7):
                    V.set(x + dx, y, z + dz, "air")
    for s in (-2, 2):
        V.set(x + s, GROUND + 1, z, "chiseled_sandstone")
        for y in range(GROUND + 2, GROUND + 6):
            V.set(x + s, y, z, "stripped_birch_log[axis=y]")
        V.set(x + s, GROUND + 6, z, "stripped_cherry_wood[axis=y]")
    for dx in range(-3, 4):
        if abs(dx) <= 1:
            V.set(x + dx, GROUND + 6, z, "stripped_cherry_log[axis=x]")
        elif abs(dx) == 3:
            V.set(x + dx, GROUND + 6, z, stairs("cherry_stairs", "east" if dx < 0 else "west", "top"))
    for dx in range(-2, 3):
        V.set(x + dx, GROUND + 7, z, "cherry_slab[type=bottom,waterlogged=false]")
    V.set(x, GROUND + 7, z, "cherry_leaves[distance=1,persistent=true,waterlogged=false]")
    for dx in (-1, 1):
        V.set(x + dx, GROUND + 7, z + 1, "cherry_leaves[distance=1,persistent=true,waterlogged=false]")
        V.set(x + dx, GROUND + 7, z - 1, "cherry_leaves[distance=1,persistent=true,waterlogged=false]")
    V.set(x, GROUND + 5, z, "chain[axis=y,waterlogged=false]")
    V.set(x, GROUND + 4, z, "bell[attachment=ceiling,facing=south,powered=false]")
    for dx in (-2, 2):
        V.set(x + dx, GROUND + 5, z + 1, "vine[up=false,north=true,south=false,east=false,west=false]")
    for dx, dz in ((-1, 2), (1, -2), (0, 2), (-2, -2), (2, 2)):
        if V.get(x + dx, GROUND, z + dz).startswith("grass_block"):
            V.set(x + dx, GROUND + 1, z + dz, "pink_petals[facing=north,flower_amount=%d]" % (2 + (dx + 2) % 3))
    light(x, GROUND + 2, z, 12)
    return {"min": [x - 1, GROUND + 1, z - 1], "max": [x + 1, GROUND + 5, z + 1], "label": [x + 0.5, GROUND + 8.6, z + 3.2]}


def shop_stall(x0, z):
    """a little market counter in front of the elven house: three barrels under a cherry awning"""
    for x in range(x0 - 1, x0 + 4):
        for zz in (z - 1, z, z + 1):
            V.set(x, GROUND, zz, "spruce_planks" if zz == z else "cut_sandstone")
            for y in range(GROUND + 1, GROUND + 5):
                V.set(x, y, zz, "air")
    for x in range(x0, x0 + 3):
        V.set(x, GROUND + 1, z, "barrel[facing=north,open=false]")
    V.set(x0 + 1, GROUND + 2, z, "flower_pot")
    for x in (x0 - 1, x0 + 3):
        for y in range(GROUND + 1, GROUND + 4):
            V.set(x, y, z, "cherry_fence")
    for x in range(x0 - 1, x0 + 4):
        V.set(x, GROUND + 4, z, "cherry_slab[type=bottom,waterlogged=false]")
        V.set(x, GROUND + 4, z - 1, "cherry_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]")
    V.set(x0 + 1, GROUND + 3, z - 1, "lantern[hanging=true,waterlogged=false]")
    return {"min": [x0, GROUND + 1, z], "max": [x0 + 2, GROUND + 2, z], "label": [x0 + 1.5, GROUND + 5.9, z - 1.5]}


# ----------------------------------------------------------------------------- portal frames

def pointed_frame(vol, plane_x, floor_y, z0, z1, mats, depth=1):
    """pointed arch in the plane x=plane_x, opening z0..z1 (inclusive), straight part 1..5 rows.
    returns (top_y, gateway cells)"""
    w = z1 - z0 + 1
    straight = 5 if w >= 4 else 4
    cells = []
    rows = []  # (y, opening zs)
    for v in range(1, straight + 1):
        rows.append((floor_y + v, list(range(z0, z1 + 1))))
    lo, hi = z0, z1
    y = floor_y + straight
    while hi - lo >= 2:
        lo, hi, y = lo + 1, hi - 1, y + 1
        rows.append((y, list(range(lo, hi + 1))))
    for (yy, zs) in rows:
        for z in zs:
            cells.append((plane_x, yy, z))
    occupied = {(yy, z) for (yy, zs) in rows for z in zs}
    top = rows[-1][0]
    # frame: pillars beside the straight rows, stairs along the slopes, a keystone on top
    for v in range(1, straight + 1):
        yy = floor_y + v
        for z, side in ((z0 - 1, "n"), (z1 + 1, "s")):
            for n in range(-depth, depth + 1):
                vol.set(plane_x + n, yy, z, mats["inner"] if n == 0 else mats["body"])
    for (yy, zs) in rows[straight:]:
        lo_z, hi_z = min(zs), max(zs)
        for z, facing in ((lo_z - 1, "north"), (hi_z + 1, "south")):
            for n in range(-depth, depth + 1):
                vol.set(plane_x + n, yy, z, "%s[facing=%s,half=top,shape=straight,waterlogged=false]" % (mats["stairs"], facing))
    # cap the last row
    last_y, last_zs = rows[-1]
    for z in range(min(last_zs) - 1, max(last_zs) + 2):
        for n in range(-depth, depth + 1):
            if (last_y + 1, z) not in occupied:
                if z in (min(last_zs) - 1, max(last_zs) + 1):
                    vol.set(plane_x + n, last_y + 1, z, "%s[facing=%s,half=top,shape=straight,waterlogged=false]" % (
                        mats["stairs"], "north" if z < min(last_zs) else "south"))
                else:
                    vol.set(plane_x + n, last_y + 1, z, mats["key"])
    # outer trim on the first straight rows: a slimmer second pillar
    for v in range(1, straight):
        yy = floor_y + v
        for z in (z0 - 2, z1 + 2):
            vol.set(plane_x, yy, z, mats["trim"])
    for z in (z0 - 2, z1 + 2):
        vol.set(plane_x, floor_y + straight, z, mats["trim_cap"])
    return top + 1, cells


def aetherion_portal():
    """moon gate at the west end of the shrine bridge: a round opening, normal size, the bridge walks straight in"""
    px, fy = -20, GROUND + 3
    yc, zc, r_in, r_out = fy + 4.0, 0.0, 3.0, 4.15
    for z in range(-7, 7):
        for y in range(fy + 1, fy + 11):
            for n in (-1, 0, 1):
                O.set(px + n, y, z, "cave_air")
    cells = []
    for z in range(-6, 6):
        for y in range(fy, fy + 10):
            dy, dz = (y + 0.5) - yc, (z + 0.5) - zc
            r = math.hypot(dy, dz)
            ang = math.degrees(math.atan2(dy, dz)) % 360
            if r < r_in and y > fy:
                O.set(px, y, z, "end_gateway")
                O.nbt[(px, y, z)] = G.gateway_nbt()
                cells.append((px, y, z))
            elif r < r_out and y >= fy:
                stud = min(ang % 90, 90 - ang % 90) < 10
                O.set(px + 1, y, z, "dark_prismarine" if r < r_in + 0.6 else ("chiseled_quartz_block" if stud else "smooth_sandstone"))
                O.set(px, y, z, "smooth_sandstone")
                O.set(px - 1, y, z, "dark_prismarine" if r < r_in + 0.6 else ("chiseled_sandstone" if stud else "smooth_sandstone"))
    top = max(y for (_, y, _) in cells)
    for z in (-1, 0):
        O.set(px, top + 2, z, "chiseled_quartz_block")
        O.set(px, top + 3, z, "end_rod[facing=up]")
    for z in range(-3, 3):
        O.set(px + 1, fy, z, "smooth_quartz")
        O.set(px + 2, fy, z, "smooth_quartz_slab[type=top,waterlogged=false]")
    INFO["gate_aetherion"] = {"min": [px - 1, fy + 1, -3], "max": [px, top, 2], "label": [px + 1.5, top + 5.2, 0.0]}


def pit_gate():
    """The Pit gate at the east end of the island: dark pointed arch, banners, soul-fire braziers"""
    px, fy = 43, GROUND
    for z in range(-5, 6):
        for y in range(fy + 1, fy + 10):
            for n in (-1, 0, 1):
                V.set(px + n, y, z, "air")
    mats = {"inner": "red_nether_bricks", "body": "polished_blackstone_bricks", "stairs": "polished_blackstone_brick_stairs",
            "key": "chiseled_polished_blackstone", "trim": "polished_basalt[axis=y]", "trim_cap": "gilded_blackstone"}
    top, cells = pointed_frame(V, px, fy, -1, 1, mats)
    for (x, y, z) in cells:
        put_gateway(x, y, z)
    V.set(px, top + 1, 0, "chain[axis=y,waterlogged=false]")
    V.set(px, top + 2, 0, "soul_lantern[hanging=false,waterlogged=false]")
    for z in (-3, 3):
        V.set(px - 1, fy + 3, z, "red_wall_banner[facing=west]")
    for z in (-4, 4):
        V.set(41, fy + 1, z, "polished_blackstone_bricks")
        V.set(41, fy + 2, z, "soul_soil")
        V.set(41, fy + 3, z, "soul_fire")
        for dz in (-1, 1):
            V.set(41, fy + 1, z + dz, "polished_blackstone_brick_stairs[facing=%s,half=bottom,shape=straight,waterlogged=false]"
                  % ("north" if dz < 0 else "south"))
    for z in range(-1, 2):
        V.set(px - 1, fy, z, "polished_blackstone_bricks")
        V.set(px - 2, fy, z, "polished_blackstone_bricks" if z == 0 else "cracked_polished_blackstone_bricks")
    INFO["gate_pit"] = {"min": [px - 1, fy + 1, -1], "max": [px, top, 1], "label": [px - 1.5, top + 4.0, 0.5]}
    return top


# ----------------------------------------------------------------------------- the Sky Path (jump and run)

def sky_path(tower_cells):
    """stepping stones spiralling up around the elven tower; start by the spell book, finish high on the east side"""
    occupied = set(tower_cells)
    cx, cz = TOWER_C

    def free(x, y, z):
        for dy in range(0, 4):
            if (x, y + dy, z) in occupied or not V.air(x, y + dy, z):
                return False
        return True

    def radius_at(y):
        r = 0
        for (x, yy, z) in tower_cells:
            if yy == y and abs(x - cx) <= 9 and abs(z - cz) <= 9:
                r = max(r, math.hypot(x - cx, z - cz))
        return r

    profile = {y: radius_at(y) for y in range(GROUND + 1, GROUND + 40)}
    mats = ["mossy_stone_bricks", "calcite", "chiseled_stone_bricks", "cherry_leaves[distance=1,persistent=true,waterlogged=false]",
            "polished_andesite", "moss_block", "chiseled_sandstone", "amethyst_block"]
    stones = []
    checkpoints = []
    theta = math.radians(40)
    y = GROUND + 1
    start = (round(cx + math.cos(theta) * 12.5), GROUND + 1, round(cz + math.sin(theta) * 12.5))
    jumps = 0
    plan = []
    total = 34
    for i in range(1, total + 1):
        rise = 0 if i % 4 == 0 else 1
        plan.append(rise)
    prev = (start[0] + 0.5, start[2] + 0.5)
    yb = GROUND
    for i, rise in enumerate(plan, 1):
        yb += rise
        rr = max(8.5, profile.get(yb, 6) + 3.6)
        step = 3.0 if rise else 3.3
        for attempt in range(40):
            theta += step / rr * (1 if attempt == 0 else 0.15)
            x = round(cx + math.cos(theta) * rr - 0.5)
            z = round(cz + math.sin(theta) * rr - 0.5)
            dist = math.hypot(x + 0.5 - prev[0], z + 0.5 - prev[1])
            if dist < 2.2:
                continue
            if free(x, yb, z):
                break
            rr += 0.5
        is_cp = i in (10, 21, 30)
        if is_cp:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    V.set(x + dx, yb, z + dz, "smooth_sandstone" if (dx or dz) else "chiseled_sandstone")
            V.set(x, yb + 1, z, "heavy_weighted_pressure_plate[power=0]")
            V.set(x + 1, yb - 1, z, "hanging_roots[waterlogged=false]")
            checkpoints.append([x, yb + 1, z])
        else:
            V.set(x, yb, z, mats[i % len(mats)])
            if i % 3 == 0:
                V.set(x, yb - 1, z, "pointed_dripstone[vertical_direction=down,thickness=tip,waterlogged=false]")
            elif i % 3 == 1:
                V.set(x, yb - 1, z, "cave_vines[age=25,berries=true]")
        stones.append([x, yb, z])
        prev = (x + 0.5, z + 0.5)
        jumps += 1
    # finish: a small floating terrace with a lantern arch
    fx, fz = stones[-1][0], stones[-1][2]
    theta += 3.4 / max(8.5, profile.get(yb, 6) + 3.6)
    rr = max(9.5, profile.get(yb + 1, 6) + 4.5)
    tx, tz = round(cx + math.cos(theta) * rr), round(cz + math.sin(theta) * rr)
    ty = yb + 1
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3:
                V.set(tx + dx, ty, tz + dz, "cherry_planks" if abs(dx) + abs(dz) < 3 else "stripped_cherry_wood[axis=y]")
                V.set(tx + dx, ty - 1, tz + dz, "moss_block")
    for dx, dz in ((-2, -1), (2, 1), (-1, 2), (1, -2)):
        V.set(tx + dx, ty - 2, tz + dz, "cave_vines[age=25,berries=true]")
    V.set(tx, ty + 1, tz, "light_weighted_pressure_plate[power=0]")
    for dx, dz in ((-2, 0), (2, 0)):
        V.set(tx + dx, ty + 1, tz + dz, "cherry_fence")
        V.set(tx + dx, ty + 2, tz + dz, "lantern[hanging=false,waterlogged=false]")
    V.set(tx, ty + 1, tz + 2, "cherry_leaves[distance=1,persistent=true,waterlogged=false]")
    V.set(tx - 1, ty + 1, tz + 2, "cherry_leaves[distance=1,persistent=true,waterlogged=false]")
    V.set(tx, ty + 2, tz + 2, "flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]")
    V.set(tx + 1, ty + 1, tz - 2, "cherry_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
    V.set(tx - 1, ty + 1, tz - 2, "cherry_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]")
    V.set(tx + 2, ty + 1, tz - 1, "potted_flowering_azalea_bush")
    V.set(tx - 2, ty + 1, tz + 1, "potted_flowering_azalea_bush")
    V.set(start[0], start[1] - 1, start[2], "chiseled_sandstone")
    V.set(start[0], start[1], start[2], "light_weighted_pressure_plate[power=0]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        V.set(start[0] + dx, start[1] - 1, start[2] + dz, "smooth_sandstone")
    INFO["parkour"] = {"start": list(start), "checkpoints": checkpoints, "finish": [tx, ty + 1, tz],
                       "stones": len(stones), "board": [start[0] + 0.5, GROUND + 4.6, start[2] + 2.5],
                       "min_y": GROUND}
    INFO["parkour_finish_spot"] = [tx, ty + 1, tz]
    return stones


# ----------------------------------------------------------------------------- The Pit, round 3

PIT_C = (152, 0)
PIT_TOP = 60


def pit3():
    def tf(X, Z):
        return PIT_TOP + 0 * X
    X, Z, inside, top, bottom = island(PIT_C[0], PIT_C[1], 28, 26, tf, 40, 778, edge_noise=0.1, mats="dark")
    INFO["pit_box"] = [int(X[inside].min()), int(Z[inside].min()), int(X[inside].max()), int(Z[inside].max())]
    underside_decor(X, Z, inside, top, bottom, 778, dark=True)
    cx, cz = PIT_C
    floor_y = PIT_TOP - 1
    for x, z, d in disc_iter(cx, cz, 25.5):
        a = math.degrees(math.atan2(z - cz, x - cx)) % 360
        if d < 13.5:
            r = float(rnd(x, 3, z, 9))
            s = choose([("coarse_dirt", 3), ("packed_mud", 2), ("rooted_dirt", 1), ("mud", 0.6), ("red_sand", 0.8)], r)
            if d < 2.5:
                s = "chiseled_polished_blackstone" if d < 1 else "polished_blackstone_bricks"
            V.set(x, floor_y, z, s)
            V.set(x, floor_y - 1, z, "blackstone")
            for y in range(floor_y + 1, PIT_TOP + 10):
                V.set(x, y, z, "air")
        elif d < 14.6:
            # arena wall, 2 high, with four gaps
            gap = min(abs(((a - 0 + 180) % 360) - 180), abs(((a - 90 + 180) % 360) - 180),
                      abs(((a - 180 + 180) % 360) - 180), abs(((a - 270 + 180) % 360) - 180)) < 8
            V.set(x, floor_y, z, "polished_blackstone_bricks")
            for y in range(floor_y + 1, PIT_TOP + 10):
                V.set(x, y, z, "air")
            if not gap:
                V.set(x, floor_y + 1, z, "polished_blackstone_bricks" if rnd(x, 1, z) > 0.12 else "cracked_polished_blackstone_bricks")
                V.set(x, floor_y + 2, z, "red_nether_brick_wall")
        elif d < 19.6:
            # stands: three tiers rising outward
            tier = int((d - 14.6) / 1.7)
            ty = floor_y + 1 + tier
            for y in range(floor_y, ty + 1):
                V.set(x, y, z, "polished_blackstone_bricks" if y < ty else
                      ("red_nether_bricks" if (round(a) // 20) % 2 else "polished_blackstone_bricks"))
            for y in range(ty + 1, PIT_TOP + 10):
                V.set(x, y, z, "air")
        elif d < 21.0:
            V.set(x, floor_y + 4, z, "polished_blackstone_bricks")
            for y in range(floor_y + 1, floor_y + 4):
                V.set(x, y, z, "blackstone")
            V.set(x, floor_y + 5, z, "polished_blackstone_wall" if (round(a) // 10) % 2 else "air")
            for y in range(floor_y + 6, PIT_TOP + 10):
                V.set(x, y, z, "air")
    # pillars with soul-fire braziers on the outer ring
    for k in range(12):
        a = math.radians(k * 30 + 15)
        px, pz = round(cx + math.cos(a) * 20.3), round(cz + math.sin(a) * 20.3)
        for y in range(floor_y + 5, floor_y + 9):
            V.set(px, y, pz, "polished_basalt[axis=y]")
        V.set(px, floor_y + 9, pz, "polished_blackstone_bricks")
        V.set(px, floor_y + 10, pz, "soul_soil")
        V.set(px, floor_y + 11, pz, "soul_fire")
        if k % 2 == 0:
            inward = (-math.cos(a), -math.sin(a))
            face = ("west" if inward[0] < 0 else "east") if abs(inward[0]) > abs(inward[1]) else ("north" if inward[1] < 0 else "south")
            bx, bz = round(px + inward[0]), round(pz + inward[1])
            if V.air(bx, floor_y + 7, bz):
                V.set(bx, floor_y + 7, bz, "red_wall_banner[facing=%s]" % face)
    # cover inside the arena: four broken pillars and two low walls
    for (ox, oz, h) in ((-7, -6, 4), (7, 6, 3), (6, -7, 5), (-6, 7, 2)):
        for y in range(floor_y + 1, floor_y + 1 + h):
            V.set(cx + ox, y, cz + oz, "polished_blackstone_bricks" if y % 2 else "cracked_polished_blackstone_bricks")
        V.set(cx + ox, floor_y + 1 + h, cz + oz, "polished_blackstone_brick_slab[type=bottom,waterlogged=false]")
    for (ox, oz, ln, ax) in ((-3, -10, 5, "x"), (-2, 9, 5, "x")):
        for i in range(ln):
            x = cx + ox + (i if ax == "x" else 0)
            z = cz + oz + (i if ax == "z" else 0)
            V.set(x, floor_y + 1, z, "polished_blackstone_brick_wall")
    V.set(cx, floor_y + 1, cz, "soul_soil")
    V.set(cx, floor_y + 2, cz, "soul_fire")
    for k in range(4):
        a = math.radians(45 + 90 * k)
        light(round(cx + math.cos(a) * 8), floor_y + 3, round(cz + math.sin(a) * 8), 13)
    # west entrance: causeway from the gate to the arena gap, the return gate faces east
    gx = cx - 25
    for x in range(gx - 2, cx - 13):
        for z in range(-2, 3):
            V.set(x, floor_y, z, "polished_blackstone_bricks" if abs(z) < 2 else "polished_blackstone_brick_slab[type=top,waterlogged=false]")
            for y in range(floor_y + 1, floor_y + 8):
                V.set(x, y, z, "air")
    for x in range(gx + 3, cx - 13, 4):
        for z in (-2, 2):
            V.set(x, floor_y + 1, z, "soul_lantern[hanging=false,waterlogged=false]")
        for z, face in ((-3, "south"), (3, "north")):
            if not V.air(x + 2, floor_y + 3, z):
                V.set(x + 2, floor_y + 3, z + (1 if z < 0 else -1), "red_wall_banner[facing=%s]" % face)
    top, cells = pointed_frame(V, gx, floor_y, -1, 1, {
        "inner": "crying_obsidian", "body": "polished_blackstone_bricks", "stairs": "polished_blackstone_brick_stairs",
        "key": "chiseled_polished_blackstone", "trim": "polished_basalt[axis=y]", "trim_cap": "gilded_blackstone"})
    for (x, y, z) in cells:
        put_gateway(x, y, z)
    for z in (-3, 3):
        V.set(gx + 2, floor_y + 1, z, "polished_blackstone_bricks")
        V.set(gx + 2, floor_y + 2, z, "soul_soil")
        V.set(gx + 2, floor_y + 3, z, "soul_fire")
    INFO["gate_home"] = {"min": [gx, floor_y + 1, -1], "max": [gx + 1, top, 1], "label": [gx + 1.5, top + 4.0, 0.5]}
    INFO["pit_entrance"] = [gx + 4.5, float(floor_y + 1), 0.5, -90.0]
    INFO["pit_board"] = [gx + 5.5, float(floor_y + 5), 5.5, -90.0]
    for ang in (35, -62):
        G.edge_fall(cx, cz, ang, "lava", 22)


# ----------------------------------------------------------------------------- main

def main(outdir, srcdir):
    os.makedirs(outdir, exist_ok=True)
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
    plaza3()
    H.path([(12, 0), (-2, 0)], 3, 11)
    H.path([(15, -6), (10, -14), (9, -18)], 1, 12)
    H.path([(28, 0), (40, 0)], 2, 13)
    H.path([(22, 8), (24, 14), (22, 19)], 1, 14)
    disc = bell_arch(19, -13)
    book = H.spellbook(8, -18)
    book["label"] = [8.5, GROUND + 4.4, -17.5]
    H.pond_and_duck(35, 12)
    INFO["shop_stall"] = shop_stall(27, 18)
    pit_gate()
    aetherion_portal()
    INFO["prop_discord"] = disc
    INFO["prop_modpack"] = book
    # lamps along the paths and at the props
    for (x, z, tx, tz) in ((8, -4, 8, 0), (8, 4, 8, 0), (2, -4, 2, 0), (2, 4, 2, 0), (32, -3, 32, 0), (37, 3, 37, 0),
                           (11, -18, 8, -18), (22, -10, 19, -13), (25, 17, 25, 21), (17, 17, 17, 21)):
        lamp(x, z, yaw_to(x, z, tx, tz))
    for (x, z) in ((-14, -3), (-14, 2)):
        lamp(x, z, yaw_to(x, z, x, 0), ground=GROUND + 3)
    stones = sky_path(tower_cells)
    # trees: vanilla cherry and azalea trees, placed by the builder
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
    for (x, z, sd) in ((26, -10, 1), (14, 9, 2), (4, 9, 5), (16, -17, 6), (-6, -10, 7)):
        gy = V.top(x, z, 80, 50)
        if gy is not None and V.get(x, gy, z).startswith(("grass_block", "moss")):
            G.azalea_bush(x, gy, z, 600 + sd, 1.2)

    def exclude(x, z):
        return (x, z) in foot or math.hypot(x - 20, z) < 9.5

    G.ground_cover(X, Z, inside, 77, exclude)
    pit3()
    G.build_sky()
    INFO["decor"] = LAMPS + [{"type": "boat", "x": 31.5, "y": 64.6, "z": 15.5, "yaw": 30.0}, {"type": "signpost", "x": 22.5, "y": float(GROUND + 1), "z": 4.5, "boards": [
        {"text": "AETHERION", "x": -20, "z": 0, "y": 2.5},
        {"text": "THE PIT", "x": 43, "z": 0, "y": 2.1},
        {"text": "SKY PATH", "x": INFO["parkour"]["start"][0], "z": INFO["parkour"]["start"][2], "y": 1.7},
        {"text": "DISCORD", "x": 19, "z": -13, "y": 1.3},
        {"text": "PIT-SHOP", "x": 27, "z": 22, "y": 0.9}]}]
    INFO["welcome"] = [19.5, GROUND + 4.9, -5.5]
    # seven lost ducklings: two on the Sky Path, the rest tucked away around the island and in the Pit
    cp2 = INFO["parkour"]["checkpoints"][1]
    fin = INFO["parkour"]["finish"]
    INFO["ducklings"] = [
        {"x": fin[0] + 1.5, "y": float(fin[1]), "z": fin[2] + 0.5, "yaw": 0.0},
        {"x": cp2[0] + 1.5, "y": float(cp2[1]), "z": cp2[2] + 1.5, "yaw": 200.0},
        {"x": 39.5, "y": 64.9, "z": 13.5, "yaw": 90.0},
        {"x": -23.5, "y": 68.0, "z": 5.5, "yaw": 200.0},
        {"x": 169.5, "y": 62.0, "z": 0.5, "yaw": 90.0},
        {"x": 19.5, "y": 65.0, "z": -15.5, "yaw": 0.0},
        {"x": 14.5, "y": 66.0, "z": 24.5, "yaw": 180.0},
    ]
    for dk in INFO["ducklings"]:
        x, y, z = math.floor(dk["x"]), math.floor(dk["y"]), math.floor(dk["z"])
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for yy in (y, y + 1):
                    if V.get(x + dx, yy, z + dz).split("[")[0] in ("short_grass", "tall_grass", "fern", "large_fern", "poppy",
                                                                     "dandelion", "allium", "azure_bluet", "oxeye_daisy",
                                                                     "cornflower", "lily_of_the_valley", "pink_tulip",
                                                                     "white_tulip", "lilac", "peony", "rose_bush"):
                        V.set(x + dx, yy, z + dz, "air")

    corner, n = H.export_vol(V, outdir, "hub_base")
    INFO["base_corner"], INFO["base_blocks"] = corner, n
    oc, on = H.export_vol(O, outdir, "hub_overlay")
    INFO["overlay_corner"] = oc
    for sc in schems:
        nb, dropped = H.export_schem(sc, outdir)
        INFO["schem_" + sc.name] = {"pos": list(sc.pos), "rotation": sc.rotation, "blocks": nb, "dropped_ground": dropped}
    INFO["spawn"] = list(SPAWN)
    INFO["place"] = (["place template minecraft:hub_base %d %d %d" % tuple(corner)]
                     + ["place template minecraft:hub_%s %d %d %d %s" % (sc.name, *sc.pos, sc.rotation) for sc in schems]
                     + ["place template minecraft:hub_overlay %d %d %d" % tuple(oc)])
    bx0 = min(b[0] for b in INFO["bounds"])
    bz0 = min(b[1] for b in INFO["bounds"])
    bx1 = max(b[2] for b in INFO["bounds"])
    bz1 = max(b[3] for b in INFO["bounds"])
    xm = (bx0 + bx1) // 2
    INFO["forceload"] = ["forceload add %d %d %d %d" % (bx0, bz0, xm, bz1), "forceload add %d %d %d %d" % (xm + 1, bz0, bx1, bz1)]
    H.preview(outdir, schems)
    with open(os.path.join(outdir, "hub3.json"), "w") as f:
        json.dump(INFO, f, indent=1)
    print(json.dumps({k: INFO[k] for k in ("place", "island_box", "base_blocks", "parkour", "features")}, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
