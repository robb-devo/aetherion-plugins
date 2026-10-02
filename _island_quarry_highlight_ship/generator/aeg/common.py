"""Shared helpers for the Aetherion island / quarry highlight templates.

Conventions (all templates in this pass):
  * front faces SOUTH (+z); x = east, y = up.
  * y = 0 is the ground layer. The Java paster puts template y=0 on world Y 64 for island starters and on the
    looked-at ground block for placed structures.
  * anchor (paste origin) = (0, 0, 0) = ground-layer centre. For starters that is the island origin.
"""
from __future__ import annotations

import math

from aeprops.core import Build, bname, props_of, with_props, DIRS, OPP, HORIZ, cw, ccw
from aeprops.style import Noise2, leaf_clump, SOIL_BLOCKS, hang_lantern

GRASSY = [("grass_block", 6), ("moss_block", 2), ("podzol", 1), ("coarse_dirt", 1)]
EDGE = [("coarse_dirt", 3), ("rooted_dirt", 2), ("grass_block", 3), ("moss_block", 1)]
UNDER = [("stone", 4), ("andesite", 3), ("tuff", 2), ("dirt", 2), ("cobblestone", 1)]
UNDER_DEEP = [("stone", 4), ("tuff", 3), ("deepslate", 2), ("andesite", 2)]
ROCKY_TOP = [("stone", 3), ("andesite", 3), ("gravel", 2), ("coarse_dirt", 2), ("tuff", 1)]
SANDY_TOP = [("sand", 8), ("sand", 1)]
PAVERS = [("stone_bricks", 5), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1), ("polished_andesite", 1)]
STONE_BRICK = [("stone_bricks", 6), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 2)]
PLASTER = [("white_concrete_powder", 3), ("calcite", 2), ("white_wool", 1)]
PLANKS = [("spruce_planks", 5), ("dark_oak_planks", 1)]
GROUND_PLANTS = [("short_grass", 6), ("fern", 3), ("air", 10)]
FLOWERS = [("azure_bluet", 2), ("oxeye_daisy", 2), ("dandelion", 2), ("cornflower", 1), ("poppy", 1)]


def pick(b, mix):
    return b._pick(mix) if isinstance(mix, (list, tuple)) else mix


def island_body(b: Build, R, seed, top, sub="dirt", rock=UNDER, depth=13, taper=0.9, shape=0.2,
                keep=None, heights=None, roots=0.12):
    """Organic floating island. The surface sits at y=0 (+ heights[(x,z)]). `top(x, z, t)` returns a state or mix
    for the surface block (t = 0 centre .. 1 rim). `keep` = cells that must exist even if the noise cuts them.
    Returns {(x, z): surface_y}."""
    n = Noise2(seed)
    n2 = Noise2(seed + 17)
    keep = set(keep or ())
    cells = {}
    for x in range(-R - 5, R + 6):
        for z in range(-R - 5, R + 6):
            r = math.hypot(x, z)
            a = math.atan2(z, x)
            rr = R * (1 + shape * n.fbm(math.cos(a) * 1.6 + 5, math.sin(a) * 1.6 + 5, 3))
            if r > rr and (x, z) not in keep:
                continue
            t = min(1.0, r / max(1.0, rr))
            d = depth * (1 - t) ** taper + 1.5 + 2.4 * n2.fbm(x * 0.23, z * 0.23)
            if t < 0.6 and n2(x * 0.9 + 40, z * 0.9 - 40) > 0.55:
                d += 2 + int(4 * (0.6 - t))   # stalactite tips under the heart of the island
            d = max(2, int(round(d)))
            h = (heights or {}).get((x, z), 0)
            cells[(x, z)] = (h, d, t)
    surf = {}
    for (x, z), (h, d, t) in cells.items():
        for y in range(h - d, h + 1):
            if y == h:
                st = pick(b, top(x, z, t))
            elif y >= h - 2:
                st = pick(b, sub)
            else:
                st = pick(b, rock)
            b.set(x, y, z, st)
        surf[(x, z)] = h
        # hanging roots under shallow rim columns
        if t > 0.55 and b.rng.random() < roots:
            b.set(x, h - d - 1, z, "hanging_roots")
    return surf


def decorate_surface(b: Build, surf, plants=0.35, flowers=0.05, skip=None):
    skip = set(skip or ())
    for (x, z), h in surf.items():
        if (x, z) in skip:
            continue
        top = b.get(x, h, z)
        if top is None or b.has(x, h + 1, z):
            continue
        if bname(top) not in ("minecraft:grass_block", "minecraft:moss_block", "minecraft:podzol"):
            continue
        r = b.rng.random()
        if r < flowers:
            b.set(x, h + 1, z, pick(b, FLOWERS))
        elif r < flowers + plants:
            p = pick(b, GROUND_PLANTS)
            if p != "air":
                b.set(x, h + 1, z, p)


def clear_above(b: Build, cells, y0=1, y1=12):
    for (x, z) in cells:
        for y in range(y0, y1 + 1):
            b.clear(x, y, z)


def rect(x1, z1, x2, z2):
    return [(x, z) for x in range(min(x1, x2), max(x1, x2) + 1) for z in range(min(z1, z2), max(z1, z2) + 1)]


def flat_pad(b: Build, cells, state=None, y=0):
    """Make sure a pad exists and is flat at y (fill the column below a bit), clear above it."""
    for (x, z) in cells:
        if not b.has(x, y, z):
            for k in range(y - 3, y):
                if not b.has(x, k, z):
                    b.set(x, k, z, "dirt")
        if state is not None:
            b.set(x, y, z, pick(b, state))
        elif not b.has(x, y, z):
            b.set(x, y, z, "grass_block")
        for yy in range(y + 1, y + 14):
            b.clear(x, yy, z)


def pad_ring(b: Build, x1, z1, x2, z2, mat, y=0):
    """Mark a build pad with a flat ground ring just outside it (never blocks the structure going on it)."""
    ring = []
    for (x, z) in rect(x1 - 1, z1 - 1, x2 + 1, z2 + 1):
        if x1 <= x <= x2 and z1 <= z <= z2:
            continue
        if not b.has(x, y, z):
            for k in range(y - 3, y):
                if not b.has(x, k, z):
                    b.set(x, k, z, "dirt")
        b.set(x, y, z, pick(b, mat))
        for yy in range(y + 1, y + 4):
            b.clear(x, yy, z)
        ring.append((x, z))
    return ring


def lantern_post(b: Build, x, y, z, h=3, wood="spruce"):
    for i in range(h):
        b.set(x, y + i, z, f"{wood}_fence")
    b.set(x, y + h, z, "lantern[hanging=false]")


def pad_corners(b: Build, x1, z1, x2, z2, y=1, post="cobblestone_wall", cap=None):
    for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
        b.set(x, y, z, post)
        if cap:
            b.set(x, y + 1, z, cap)


def small_birch(b: Build, x, y, z, h=5):
    for i in range(h):
        b.set(x, y + i, z, "birch_log[axis=y]")
    leaf_clump(b, x, y + h, z, 2, 1, 2, mix=[("birch_leaves", 5), ("azalea_leaves", 1)], density=0.95)
    leaf_clump(b, x, y + h + 1, z, 1, 1, 1, mix=[("birch_leaves", 1)], density=0.95)


def a_frame_tent(b: Build, x1, z1, length, facing_open="south", canvas="white_wool", trim="light_gray_wool"):
    """5-wide A-frame tent along z from z1, opening at the +z end."""
    for dz in range(length):
        z = z1 + dz
        c = trim if dz % 2 == 0 else canvas
        b.set(x1, 1, z, c)
        b.set(x1 + 4, 1, z, c)
        b.set(x1 + 1, 2, z, canvas)
        b.set(x1 + 3, 2, z, canvas)
        b.set(x1 + 2, 3, z, "stripped_spruce_log[axis=z]")
    # back wall
    for x in range(x1 + 1, x1 + 4):
        b.set(x, 1, z1, canvas)
    b.set(x1 + 2, 2, z1, canvas)
    # front poles
    zf = z1 + length
    b.set(x1, 1, zf, "spruce_fence")
    b.set(x1 + 4, 1, zf, "spruce_fence")


def wall_sign(b: Build, x, y, z, facing, lines, wood="spruce", glow=False):
    b.sign(x, y, z, f"{wood}_wall_sign[facing={facing}]", lines, glow=glow)


def standing_sign(b: Build, x, y, z, rotation, lines, wood="spruce", glow=False):
    b.sign(x, y, z, f"{wood}_sign[rotation={rotation}]", lines, glow=glow)


def ore_face(b: Build, cells, face_dir, ores, chance=0.3):
    """Swap exposed faces (toward face_dir) of a rock mass for ores."""
    dx, _, dz = DIRS[face_dir]
    for (x, y, z) in cells:
        if (x + dx, y, z + dz) in cells:
            continue
        if b.rng.random() < chance:
            b.set(x, y, z, pick(b, ores))


def scaffold_column(b: Build, x, y, z, h):
    for i in range(h):
        b.set(x, y + i, z, "scaffolding[distance=0,bottom=false]")


def gable(b: Build, x1, x2, z1, z2, y0, stairs="spruce_stairs", fill="spruce_planks", ridge_axis="x", slab=None):
    """Simple gable roof. ridge along x: slopes face north/south."""
    if ridge_axis == "x":
        a1, a2 = z1, z2
    else:
        a1, a2 = x1, x2
    layer = 0
    lo, hi = a1, a2
    while lo <= hi:
        y = y0 + layer
        if ridge_axis == "x":
            for x in range(x1, x2 + 1):
                if lo == hi:
                    b.set(x, y, lo, slab or fill)
                else:
                    b.set(x, y, lo, f"{stairs}[facing=south,half=bottom]")
                    b.set(x, y, hi, f"{stairs}[facing=north,half=bottom]")
        else:
            for z in range(z1, z2 + 1):
                if lo == hi:
                    b.set(lo, y, z, slab or fill)
                else:
                    b.set(lo, y, z, f"{stairs}[facing=east,half=bottom]")
                    b.set(hi, y, z, f"{stairs}[facing=west,half=bottom]")
        lo += 1
        hi -= 1
        layer += 1
    return y0 + layer - 1


def gable_ends(b: Build, x1, x2, z1, z2, y0, mat, ridge_axis="x"):
    """Fill the triangular gable walls under a gable() roof (inset by one)."""
    if ridge_axis == "x":
        lo, hi = z1 + 1, z2 - 1
        layer = 0
        while lo <= hi:
            for z in range(lo, hi + 1):
                b.set(x1 + 1, y0 + layer, z, pick(b, mat))
                b.set(x2 - 1, y0 + layer, z, pick(b, mat))
            lo += 1
            hi -= 1
            layer += 1
    else:
        lo, hi = x1 + 1, x2 - 1
        layer = 0
        while lo <= hi:
            for x in range(lo, hi + 1):
                b.set(x, y0 + layer, z1 + 1, pick(b, mat))
                b.set(x, y0 + layer, z2 - 1, pick(b, mat))
            lo += 1
            hi -= 1
            layer += 1
