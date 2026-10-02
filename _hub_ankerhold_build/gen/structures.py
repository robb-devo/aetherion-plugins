"""Reusable build primitives for Ankerhold (houses, roofs, towers, piers, props)."""
from __future__ import annotations

import math
import random

import numpy as np

from world import (World, stairs, slab, log, leaves, lantern, fence, wall, pane, bars, chain, door, bell, barrel,
                   campfire, trapdoor, ladder, double_plant, OPP, DIRV, GRASS, WATER)

# ----------------------------------------------------------------------------------------------- styles
STYLES = {
    # harbour: spruce frame, white plaster, dark spruce roof
    "harbour": dict(found="cobblestone", found2="mossy_cobblestone", wall="white_terracotta", frame="spruce_log",
                    beam="stripped_spruce_log", floor="spruce_planks", roof="spruce", roof_full="spruce_planks",
                    window=pane("glass_pane"), door="spruce", trim="stripped_spruce_log", gable="spruce_planks"),
    "harbour_blue": dict(found="cobblestone", found2="stone_bricks", wall="spruce_planks", frame="stripped_spruce_log",
                         beam="stripped_spruce_log", floor="spruce_planks", roof="deepslate_tile",
                         roof_full="deepslate_tiles", window=pane("glass_pane"), door="spruce",
                         trim="stripped_spruce_log", gable="spruce_planks"),
    "harbour_warm": dict(found="stone_bricks", found2="cobblestone", wall="birch_planks", frame="spruce_log",
                         beam="spruce_log", floor="oak_planks", roof="dark_oak", roof_full="dark_oak_planks",
                         window=pane("glass_pane"), door="dark_oak", trim="spruce_log", gable="birch_planks"),
    # capital: stone ground floor, plaster upper, slate or copper roofs
    "capital": dict(found="stone_bricks", found2="polished_andesite", wall="white_terracotta", frame="dark_oak_log",
                    beam="stripped_dark_oak_log", floor="dark_oak_planks", roof="deepslate_tile",
                    roof_full="deepslate_tiles", window=pane("glass_pane"), door="dark_oak",
                    trim="stripped_dark_oak_log", gable="white_terracotta", ground="stone_bricks"),
    "capital_copper": dict(found="stone_bricks", found2="polished_andesite", wall="smooth_stone",
                           frame="stripped_dark_oak_log", beam="stripped_dark_oak_log", floor="dark_oak_planks",
                           roof="waxed_weathered_cut_copper", roof_full="waxed_weathered_cut_copper",
                           window=pane("glass_pane"), door="dark_oak", trim="polished_andesite",
                           gable="smooth_stone", ground="stone_bricks"),
    "capital_brick": dict(found="stone_bricks", found2="polished_andesite", wall="bricks", frame="spruce_log",
                          beam="stripped_spruce_log", floor="spruce_planks", roof="deepslate_tile",
                          roof_full="deepslate_tiles", window=pane("glass_pane"), door="spruce",
                          trim="stripped_spruce_log", gable="bricks", ground="stone_bricks"),
    "capital_warm": dict(found="stone_bricks", found2="polished_andesite", wall="white_terracotta",
                         frame="spruce_log", beam="stripped_spruce_log", floor="spruce_planks", roof="dark_oak",
                         roof_full="dark_oak_planks", window=pane("glass_pane"), door="spruce",
                         trim="stripped_spruce_log", gable="spruce_planks", ground="stone_bricks"),
    # farm
    "farm": dict(found="cobblestone", found2="mossy_cobblestone", wall="oak_planks", frame="oak_log",
                 beam="stripped_oak_log", floor="oak_planks", roof="spruce", roof_full="spruce_planks",
                 window=pane("glass_pane"), door="oak", trim="stripped_oak_log", gable="oak_planks"),
    "barn": dict(found="cobblestone", found2="cobblestone", wall="red_terracotta", frame="dark_oak_log",
                 beam="stripped_dark_oak_log", floor="spruce_planks", roof="dark_oak", roof_full="dark_oak_planks",
                 window=pane("glass_pane"), door="dark_oak", trim="stripped_dark_oak_log", gable="red_terracotta"),
    # mine / forge
    "mine": dict(found="cobbled_deepslate", found2="cobblestone", wall="spruce_planks", frame="dark_oak_log",
                 beam="stripped_dark_oak_log", floor="spruce_planks", roof="cobbled_deepslate",
                 roof_full="cobbled_deepslate", window=pane("glass_pane"), door="spruce",
                 trim="stripped_dark_oak_log", gable="spruce_planks"),
    "forge": dict(found="cobbled_deepslate", found2="cobblestone", wall="cobblestone", frame="dark_oak_log",
                  beam="stripped_dark_oak_log", floor="stone_bricks", roof="deepslate_brick",
                  roof_full="deepslate_bricks", window=pane("glass_pane"), door="dark_oak",
                  trim="stripped_dark_oak_log", gable="cobblestone"),
    "border": dict(found="cobblestone", found2="mossy_cobblestone", wall="spruce_planks", frame="spruce_log",
                   beam="spruce_log", floor="spruce_planks", roof="spruce", roof_full="spruce_planks",
                   window=pane("glass_pane"), door="spruce", trim="spruce_log", gable="spruce_planks"),
}


def axis_of(state_log, axis):
    return f"minecraft:{state_log}[axis={axis}]"


# ----------------------------------------------------------------------------------------------- basic shapes
def foundation(W: World, x1, z1, x2, z2, y, block, block2=None, depth=14, rng=None):
    """Fill from y down to the first solid block under each column (max depth)."""
    rng = rng or random
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for yy in range(y, y - depth, -1):
                cur = W.getn(x, yy, z)
                if yy < y and cur != "minecraft:air" and "water" not in cur and "leaves" not in cur \
                        and "grass[" not in cur and "short_grass" not in cur and "fern" not in cur:
                    break
                b = block2 if (block2 and rng.random() < 0.25) else block
                W.set(x, yy, z, b)


def clear(W: World, x1, y1, z1, x2, y2, z2):
    W.fill(x1, y1, z1, x2, y2, z2, 0)


def gable_roof(W: World, x1, z1, x2, z2, y, mat, full, gable_block, axis="x", oh=1, ridge_slab=True):
    """Gable roof whose ridge runs along `axis`. y = first roof layer (top of walls + 1)."""
    if axis == "x":
        a1, a2 = z1 - oh, z2 + oh
        k = 0
        while a1 + k <= a2 - k:
            yy = y + k
            lo, hi = a1 + k, a2 - k
            for x in range(x1 - oh, x2 + oh + 1):
                if lo == hi:
                    W.set(x, yy, lo, slab(mat.replace("_tile", "_tile") if True else mat, "bottom") if ridge_slab
                          else full)
                elif hi - lo == 1:
                    W.set(x, yy, lo, stairs(mat, "south"))
                    W.set(x, yy, hi, stairs(mat, "north"))
                else:
                    W.set(x, yy, lo, stairs(mat, "south"))
                    W.set(x, yy, hi, stairs(mat, "north"))
            # gable infill (only inside wall span)
            for z in range(max(lo + 1, z1), min(hi - 1, z2) + 1):
                W.set(x1, yy, z, gable_block)
                W.set(x2, yy, z, gable_block)
            # underside of overhang at ends
            k += 1
        return y + k
    else:
        a1, a2 = x1 - oh, x2 + oh
        k = 0
        while a1 + k <= a2 - k:
            yy = y + k
            lo, hi = a1 + k, a2 - k
            for z in range(z1 - oh, z2 + oh + 1):
                if lo == hi:
                    W.set(lo, yy, z, slab(mat, "bottom") if ridge_slab else full)
                else:
                    W.set(lo, yy, z, stairs(mat, "east"))
                    W.set(hi, yy, z, stairs(mat, "west"))
            for x in range(max(lo + 1, x1), min(hi - 1, x2) + 1):
                W.set(x, yy, z1, gable_block)
                W.set(x, yy, z2, gable_block)
            k += 1
        return y + k


def fix_slab_name(mat):
    # deepslate_tile -> deepslate_tile_slab ; spruce -> spruce_slab ; cobbled_deepslate -> cobbled_deepslate_slab
    return mat


def house(W: World, x1, z1, x2, z2, y, style, floors=1, door_side="south", roof_axis=None, oh=1,
          chimney=False, rng=None, storey=5, door_open=True, interior=True, lamp=True, jetty=False,
          windows=True, roof_style="gable"):
    """Timber-framed house. Floor block at y; walls from y+1. Returns dict with door position + roof top."""
    rng = rng or random.Random(x1 * 31 + z1)
    S = STYLES[style] if isinstance(style, str) else style
    if roof_axis is None:
        roof_axis = "x" if (x2 - x1) >= (z2 - z1) else "z"
    foundation(W, x1, z1, x2, z2, y, S["found"], S.get("found2"), rng=rng)
    W.fill(x1 + 1, y, z1 + 1, x2 - 1, y, z2 - 1, S["floor"])
    top = y + storey * floors
    clear(W, x1, y + 1, z1, x2, top + 12, z2)
    for f in range(floors):
        fy = y + f * storey
        wall_mat = S["wall"]
        if f == 0 and S.get("ground"):
            wall_mat = S["ground"]
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                edge = x in (x1, x2) or z in (z1, z2)
                if not edge:
                    continue
                corner = x in (x1, x2) and z in (z1, z2)
                for yy in range(fy + 1, fy + storey):
                    if corner:
                        W.set(x, yy, z, axis_of(S["frame"], "y"))
                    else:
                        W.set(x, yy, z, wall_mat)
                # beam layer
                if corner:
                    W.set(x, fy + storey, z, axis_of(S["frame"], "y"))
                else:
                    ax = "x" if z in (z1, z2) else "z"
                    W.set(x, fy + storey, z, axis_of(S["beam"], ax))
        # posts every 4
        for x in range(x1 + 4, x2, 4):
            for z in (z1, z2):
                for yy in range(fy + 1, fy + storey):
                    W.set(x, yy, z, axis_of(S["frame"], "y"))
        for z in range(z1 + 4, z2, 4):
            for x in (x1, x2):
                for yy in range(fy + 1, fy + storey):
                    W.set(x, yy, z, axis_of(S["frame"], "y"))
        # windows between posts
        if windows:
            wy = (fy + 2, fy + 3)
            for x in range(x1 + 1, x2):
                if (x - x1) % 4 in (2,) and x not in (x1, x2):
                    for z in (z1, z2):
                        for yy in wy:
                            W.set(x, yy, z, S["window"])
            for z in range(z1 + 1, z2):
                if (z - z1) % 4 in (2,):
                    for x in (x1, x2):
                        for yy in wy:
                            W.set(x, yy, z, S["window"])
        # floor of next storey
        if f < floors - 1:
            W.fill(x1 + 1, fy + storey, z1 + 1, x2 - 1, fy + storey, z2 - 1, S["floor"])
            if lamp:
                W.set((x1 + x2) // 2, fy + storey - 1, (z1 + z2) // 2, lantern(True))
    # ceiling/attic floor
    W.fill(x1 + 1, top, z1 + 1, x2 - 1, top, z2 - 1, S["floor"])
    if lamp:
        W.set((x1 + x2) // 2, top - 1, (z1 + z2) // 2, lantern(True))
    # door
    cx, cz = (x1 + x2) // 2, (z1 + z2) // 2
    if door_side == "south":
        dx, dz = cx, z2
    elif door_side == "north":
        dx, dz = cx, z1
    elif door_side == "east":
        dx, dz = x2, cz
    else:
        dx, dz = x1, cz
    # door can't be on a post column; nudge
    if door_side in ("south", "north") and (dx - x1) % 4 == 0:
        dx += 1
    if door_side in ("east", "west") and (dz - z1) % 4 == 0:
        dz += 1
    if door_open:
        W.set(dx, y + 1, dz, 0)
        W.set(dx, y + 2, dz, 0)
    else:
        fac = OPP[door_side]
        W.set(dx, y + 1, dz, door(S["door"], fac, "lower"))
        W.set(dx, y + 2, dz, door(S["door"], fac, "upper"))
    W.set(dx, y, dz, S["floor"])
    # lantern over door outside
    ox, oz = DIRV[door_side]
    W.set(dx + ox, y + 3, dz + oz, lantern(True)) if False else None
    # step in front of door
    roof_y = top + 1
    if roof_style == "gable":
        ridge = gable_roof(W, x1, z1, x2, z2, roof_y, S["roof"], S["roof_full"], S["gable"], axis=roof_axis, oh=oh)
    else:
        ridge = hip_roof(W, x1, z1, x2, z2, roof_y, S["roof"], S["roof_full"], oh=oh)
    if chimney:
        chx = x1 + 1 if rng.random() < 0.5 else x2 - 1
        chz = z1 + 1 if rng.random() < 0.5 else z2 - 1
        for yy in range(y + 1, ridge + 2):
            W.set(chx, yy, chz, "bricks" if style not in ("forge", "mine") else "cobbled_deepslate")
        W.set(chx, ridge + 2, chz, campfire(True))
    if interior:
        furnish(W, x1, z1, x2, z2, y, S, rng, door_side)
    return dict(door=(dx, y + 1, dz), door_out=(dx + ox, y + 1, dz + oz), ridge=ridge, top=top)


def hip_roof(W, x1, z1, x2, z2, y, mat, full, oh=1):
    k = 0
    while True:
        ax1, ax2, az1, az2 = x1 - oh + k, x2 + oh - k, z1 - oh + k, z2 + oh - k
        if ax1 > ax2 or az1 > az2:
            break
        yy = y + k
        if ax1 == ax2 or az1 == az2:
            W.fill(ax1, yy, az1, ax2, yy, az2, slab(mat, "bottom"))
            k += 1
            break
        for x in range(ax1, ax2 + 1):
            W.set(x, yy, az1, stairs(mat, "south"))
            W.set(x, yy, az2, stairs(mat, "north"))
        for z in range(az1, az2 + 1):
            W.set(ax1, yy, z, stairs(mat, "east"))
            W.set(ax2, yy, z, stairs(mat, "west"))
        W.set(ax1, yy, az1, stairs(mat, "south", shape="outer_left"))
        W.set(ax2, yy, az1, stairs(mat, "south", shape="outer_right"))
        W.set(ax1, yy, az2, stairs(mat, "north", shape="outer_right"))
        W.set(ax2, yy, az2, stairs(mat, "north", shape="outer_left"))
        if ax2 - ax1 >= 2 and az2 - az1 >= 2:
            W.fill(ax1 + 1, yy, az1 + 1, ax2 - 1, yy, az2 - 1, full)
        k += 1
    return y + k


def furnish(W, x1, z1, x2, z2, y, S, rng, door_side):
    inner = [(x, z) for x in range(x1 + 1, x2) for z in range(z1 + 1, z2)]
    if not inner:
        return
    # keep a corridor from the door free; place a few props along back wall
    props = ["crafting_table", barrel("up"), "bookshelf", barrel("up"), "smoker[facing=north,lit=false]",
             "cartography_table", "loom[facing=north]", "composter[level=0]"]
    back = {"south": z1 + 1, "north": z2 - 1}.get(door_side)
    spots = []
    if back is not None:
        spots = [(x, back) for x in range(x1 + 1, x2)]
    else:
        bx = x1 + 1 if door_side == "east" else x2 - 1
        spots = [(bx, z) for z in range(z1 + 1, z2)]
    rng.shuffle(spots)
    for (x, z) in spots[: max(1, len(spots) // 2)]:
        W.set(x, y + 1, z, rng.choice(props))
    # a bed-ish carpet + table
    tx, tz = (x1 + x2) // 2, (z1 + z2) // 2
    W.set(tx, y + 1, tz, fence("spruce"))
    W.set(tx, y + 2, tz, "minecraft:spruce_pressure_plate[powered=false]")


# ----------------------------------------------------------------------------------------------- small props
def lamp_post(W, x, y, z, style="spruce", h=3, hanging=False):
    """Fence post with lantern on top. y = ground block; post starts at y+1."""
    for k in range(1, h + 1):
        W.set(x, y + k, z, fence(style))
    W.set(x, y + h + 1, z, lantern(False))


def stone_lamp(W, x, y, z, mat="stone_brick"):
    W.set(x, y + 1, z, f"minecraft:{mat}_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    W.set(x, y + 2, z, f"minecraft:{mat}_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]")
    W.set(x, y + 3, z, lantern(False))


def bench(W, x, y, z, facing="south", length=3, mat="spruce"):
    """Stairs-bench, length along the perpendicular axis."""
    dx, dz = (1, 0) if facing in ("north", "south") else (0, 1)
    for i in range(length):
        W.set(x + dx * i, y + 1, z + dz * i, stairs(mat, facing))
    # armrests
    W.set(x - dx, y + 1, z - dz, trapdoor(mat, OPP[facing] if False else ("west" if dx else "north"), open_=True))
    W.set(x + dx * length, y + 1, z + dz * length, trapdoor(mat, "east" if dx else "south", open_=True))


def crates(W, x, y, z, n=3, rng=None):
    rng = rng or random
    opts = [barrel("up"), "minecraft:spruce_planks", barrel("north"), "minecraft:hay_block[axis=y]",
            "minecraft:oak_planks", barrel("east")]
    for i in range(n):
        dx, dz = rng.randint(-1, 1), rng.randint(-1, 1)
        yy = y + 1
        while not W.is_air(x + dx, yy, z + dz) and yy < y + 3:
            yy += 1
        W.set(x + dx, yy, z + dz, rng.choice(opts))


def signpost(W, x, y, z, lines, mat="spruce", rot=0):
    W.set(x, y + 1, z, fence(mat))
    W.set(x, y + 2, z, f"minecraft:{mat}_sign[rotation={rot},waterlogged=false]")
    W.sign_text(x, y + 2, z, lines)


def wall_sign_at(W, x, y, z, facing, lines, mat="spruce"):
    W.set(x, y, z, f"minecraft:{mat}_wall_sign[facing={facing},waterlogged=false]")
    W.sign_text(x, y, z, lines)


def flower_box(W, x, y, z, rng=None):
    rng = rng or random
    W.set(x, y, z, "minecraft:spruce_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")


def cylinder(W, cx, cz, r, y1, y2, block, hollow=False, thick=1.0):
    ri = int(math.ceil(r))
    for dx in range(-ri, ri + 1):
        for dz in range(-ri, ri + 1):
            d = math.hypot(dx, dz)
            if d <= r + 0.3 and (not hollow or d >= r - thick + 0.3 - 1e-6):
                for yy in range(y1, y2 + 1):
                    W.set(cx + dx, yy, cz + dz, block)


def disc(W, cx, cz, r, y, block):
    cylinder(W, cx, cz, r, y, y, block)


def cone_roof(W, cx, cz, r, y, block, slope=1.0, tip=None):
    k = 0
    rr = r
    while rr > 0.4:
        cylinder(W, cx, cz, rr, y + k, y + k, block)
        k += 1
        rr = r - k / slope
    if tip:
        W.set(cx, y + k, cz, tip)
    return y + k


def thick_line(W, p0, p1, rad, block, step=0.5):
    x0, y0, z0 = p0
    x1, y1, z1 = p1
    L = math.dist(p0, p1)
    n = max(1, int(L / step))
    ri = int(math.ceil(rad))
    for i in range(n + 1):
        t = i / n
        x, y, z = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, z0 + (z1 - z0) * t
        for dx in range(-ri, ri + 1):
            for dy in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    if dx * dx + dy * dy + dz * dz <= rad * rad + 0.25:
                        W.set(round(x + dx), round(y + dy), round(z + dz), block)


# ----------------------------------------------------------------------------------------------- trees
def oak_tree(W, x, y, z, rng, h=None, mat="oak", leaf=None, rad=None):
    h = h if h is not None else rng.randint(5, 8)
    rad = rad or rng.choice([2.2, 2.6, 3.0])
    lf = leaves(leaf or mat)
    for k in range(1, h + 1):
        W.set(x, y + k, z, log(f"{mat}_log"))
    cy = y + h
    ri = int(math.ceil(rad)) + 1
    for dx in range(-ri, ri + 1):
        for dy in range(-2, 3):
            for dz in range(-ri, ri + 1):
                d = math.sqrt(dx * dx + (dy * 1.35) ** 2 + dz * dz)
                if d <= rad + rng.random() * 0.6:
                    W.set_if_air(x + dx, cy + dy, z + dz, lf)
    # a branch
    if h > 6 and rng.random() < 0.6:
        bx, bz = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        W.set(x + bx, y + h - 2, z + bz, log(f"{mat}_log", "x" if bx else "z"))


def birch_tree(W, x, y, z, rng):
    h = rng.randint(6, 9)
    for k in range(1, h + 1):
        W.set(x, y + k, z, log("birch_log"))
    lf = leaves("birch")
    for dy in range(-3, 2):
        r = 2.2 if dy < 0 else 1.3
        ri = int(math.ceil(r))
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                if dx * dx + dz * dz <= r * r + rng.random() * 0.8:
                    W.set_if_air(x + dx, y + h + dy, z + dz, lf)
    W.set_if_air(x, y + h + 2, z, lf)


def spruce_tree(W, x, y, z, rng, h=None):
    h = h or rng.randint(8, 14)
    for k in range(1, h + 1):
        W.set(x, y + k, z, log("spruce_log"))
    lf = leaves("spruce")
    k = h + 1
    W.set_if_air(x, k, z, lf) if False else None
    W.set_if_air(x, y + h + 1, z, lf)
    W.set_if_air(x, y + h + 2, z, lf)
    r = 0.8
    for yy in range(y + h, y + 2, -1):
        rr = r + ((y + h - yy) % 3) * 0.9 + (y + h - yy) * 0.12
        ri = int(math.ceil(rr))
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                if 0 < dx * dx + dz * dz <= rr * rr + 0.3:
                    W.set_if_air(x + dx, yy, z + dz, lf)


def cherry_tree(W, x, y, z, rng):
    h = rng.randint(4, 6)
    for k in range(1, h + 1):
        W.set(x, y + k, z, log("cherry_log"))
    lf = leaves("cherry")
    # 2-3 arching branches
    tops = []
    for _ in range(rng.randint(2, 3)):
        ang = rng.random() * 2 * math.pi
        L = rng.randint(3, 4)
        px, pz, py = x, z, y + h
        for s in range(1, L + 1):
            px2 = x + round(math.cos(ang) * s)
            pz2 = z + round(math.sin(ang) * s)
            py2 = y + h + (1 if s > 1 else 0)
            W.set(px2, py2, pz2, log("cherry_log", "x" if abs(math.cos(ang)) > 0.7 else "z"))
            px, pz, py = px2, pz2, py2
        tops.append((px, py, pz))
    for (px, py, pz) in tops:
        for dx in range(-3, 4):
            for dy in range(-1, 3):
                for dz in range(-3, 4):
                    d = math.sqrt(dx * dx + (dy * 1.6) ** 2 + dz * dz)
                    if d <= 3.0 + rng.random() * 0.4:
                        W.set_if_air(px + dx, py + dy + 1, pz + dz, lf)


def dead_tree(W, x, y, z, rng):
    h = rng.randint(4, 8)
    mat = rng.choice(["dark_oak_log", "spruce_log", "stripped_dark_oak_log"])
    for k in range(1, h + 1):
        W.set(x, y + k, z, log(mat))
    for _ in range(rng.randint(1, 3)):
        bx, bz = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        by = y + rng.randint(2, h)
        for s in range(1, rng.randint(2, 4)):
            W.set(x + bx * s, by + (s // 2), z + bz * s, log(mat, "x" if bx else "z"))


def bush(W, x, y, z, rng, mat="azalea"):
    lf = leaves(mat) if mat != "flowering_azalea" else leaves("flowering_azalea")
    W.set_if_air(x, y + 1, z, lf)
    for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
        if rng.random() < 0.6:
            W.set_if_air(x + dx, y + 1, z + dz, lf)
    if rng.random() < 0.4:
        W.set_if_air(x, y + 2, z, lf)
