"""Shared helpers for the second prop pass (more_*.py).

Anchor convention for this pass (see README in _more_props_ship):
  The Prop Wand pastes the schematic ORIGIN exactly on the block you look at
  (PropWandListener.lookAnchor -> FaweIslandPaste.paste(..., hitX, hitY, hitZ, ignoreAir=true, rot)).
  So every new piece anchors on a block that *replaces the block you look at*:
    * ground pieces: centre of the ground layer (y = 0)  -> the ground layer sits flush with the terrain
    * shore / pier / boat pieces: the quay-edge block the piece hangs off (y = 0 = quay top)
    * cliff pieces: the last land block before the drop (y = 0)
    * sea-floor pieces: the sea-floor block the piece stands on (y = 0)
  Front still faces SOUTH (+z); the wand turns it to face the way you look.
"""
from __future__ import annotations

import math

from aeprops.core import Build, bname, props_of, with_props, registry, is_full_cube, DIRS, OPP, HORIZ, cw, ccw
from aeprops.style import SOIL_BLOCKS, Noise2, leaf_clump, soil_patch  # noqa

# ---- palettes ------------------------------------------------------------------------------------
SALT_WOOD = [("spruce_planks", 5), ("stripped_spruce_wood[axis=y]", 1)]
WEATHERED_PLANK = [("spruce_planks", 6), ("dark_oak_planks", 2), ("oak_planks", 1)]
QUAY_STONE = [("stone_bricks", 5), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 2), ("andesite", 1),
              ("polished_andesite", 1)]
WHITEWASH = [("white_concrete_powder", 3), ("calcite", 3), ("white_wool", 1), ("diorite", 1)]
DARK_BAND = [("deepslate_bricks", 3), ("cracked_deepslate_bricks", 1), ("polished_deepslate", 1)]
BOULDER = [("stone", 4), ("andesite", 3), ("tuff", 2), ("cobblestone", 1), ("mossy_cobblestone", 2)]
MOSS_TOP = [("moss_block", 5), ("mossy_cobblestone", 2)]
FOREST_FLOOR = [("moss_block", 3), ("podzol", 3), ("grass_block", 2), ("rooted_dirt", 1), ("coarse_dirt", 1)]
SEABED = [("sand", 4), ("gravel", 3), ("clay", 1), ("mud", 1)]
TILE_ROOF = [("deepslate_tile_stairs", 5), ("cobbled_deepslate_stairs", 1)]
BLACK_ROOF = [("blackstone_stairs", 5), ("polished_blackstone_stairs", 1)]


def ground_anchor(b: Build, y: int = 0):
    """Footprint centre of the ground layer."""
    ground = [p for p in b.blocks if p[1] == y] or list(b.blocks)
    xs = [p[0] for p in ground]
    zs = [p[2] for p in ground]
    b.anchor = ((min(xs) + max(xs)) // 2, y, (min(zs) + max(zs)) // 2)


def waterlog_below(b: Build, level: int):
    """Everything strictly below the waterline that can hold water gets waterlogged=true.

    FAWE pastes states as-is: a dry fence/stair/chain under water shows an air pocket around it.
    The surface layer itself (y == level) stays dry on purpose, so boats and decks don't fill up.
    """
    reg = registry()
    for pos, st in list(b.blocks.items()):
        if pos[1] >= level:
            continue
        name = bname(st)
        allowed = reg[name][0]
        if "waterlogged" in allowed:
            b.blocks[pos] = with_props(st, waterlogged="true")


def disc(cx, cz, r):
    """Cells of a filled disc (nice-looking voxel circle)."""
    out = []
    rr = (r + 0.45) ** 2
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if (x - cx) ** 2 + (z - cz) ** 2 <= rr:
                out.append((x, z))
    return out


def ring(cx, cz, r):
    """Outer shell of a disc (4-connected outline)."""
    cells = set(disc(cx, cz, r))
    out = []
    for (x, z) in cells:
        if any((x + dx, z + dz) not in cells for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out.append((x, z))
    return out


def boulder(b: Build, cx, cy, cz, rx, ry, rz, mix=BOULDER, top=MOSS_TOP, seed=7, moss=0.55, bury=1, rough=0.22):
    """Lumpy noisy boulder; mossy on top. `bury` layers go below cy so it grips the terrain."""
    nz = Noise2(seed)
    cells = []
    for x in range(cx - rx - 1, cx + rx + 2):
        for z in range(cz - rz - 1, cz + rz + 2):
            for y in range(cy - bury, cy + ry + 2):
                dx, dy, dz = (x - cx) / (rx + 0.5), (y - cy) / (ry + 0.5), (z - cz) / (rz + 0.5)
                d = dx * dx + max(dy, -0.2) ** 2 * (1 if dy > 0 else 0.3) + dz * dz
                if d <= 1.0 + rough * nz.fbm(x * 0.45 + y * 0.3, z * 0.45 - y * 0.2):
                    cells.append((x, y, z))
    cs = set(cells)
    for (x, y, z) in cells:
        b.set(x, y, z, b._pick(mix))
    for (x, y, z) in cells:
        if (x, y + 1, z) not in cs and y >= cy and b.rng.random() < moss:
            b.set(x, y, z, b._pick(top))
            if b.rng.random() < 0.25 and not b.has(x, y + 1, z):
                b.set(x, y + 1, z, "moss_carpet")
    return cs


def plant(b: Build, x, y, z, what):
    """Place a plant only if there is soil below and the spot is free."""
    below = b.get(x, y - 1, z)
    if below is None or bname(below) not in SOIL_BLOCKS or b.has(x, y, z):
        return False
    if what in ("tall_grass", "large_fern", "lilac", "rose_bush", "peony"):
        if b.has(x, y + 1, z):
            return False
        b.tall(x, y, z, what)
    else:
        b.set(x, y, z, what)
    return True


def mushroom_ok(b: Build, x, y, z, kind="brown_mushroom"):
    """Mushrooms only on podzol/mycelium so they survive daylight block updates."""
    below = b.get(x, y - 1, z)
    if below and bname(below) in ("minecraft:podzol", "minecraft:mycelium") and not b.has(x, y, z):
        b.set(x, y, z, kind)
        return True
    return False


def hang(b: Build, x, y_support, z, n, end="lantern[hanging=true]"):
    """Chain of n under the support block, then `end`."""
    for i in range(1, n + 1):
        b.set(x, y_support - i, z, "chain[axis=y]")
    if end:
        b.set(x, y_support - n - 1, z, end)


def stairs_ring(b: Build, x1, x2, z1, z2, y, mat, half="bottom", inward=True):
    """Ring of stairs around a rectangle; inward=True -> stairs rise toward the centre."""
    for x in range(x1, x2 + 1):
        b.set(x, y, z1, f"{mat}[facing={'south' if inward else 'north'},half={half}]")
        b.set(x, y, z2, f"{mat}[facing={'north' if inward else 'south'},half={half}]")
    for z in range(z1 + 1, z2):
        b.set(x1, y, z, f"{mat}[facing={'east' if inward else 'west'},half={half}]")
        b.set(x2, y, z, f"{mat}[facing={'west' if inward else 'east'},half={half}]")


def corbel_ring(b: Build, x1, x2, z1, z2, y, mat):
    """Upside-down stairs flaring outward (under a gallery / eave)."""
    for x in range(x1, x2 + 1):
        b.set(x, y, z1, f"{mat}[facing=south,half=top]")
        b.set(x, y, z2, f"{mat}[facing=north,half=top]")
    for z in range(z1 + 1, z2):
        b.set(x1, y, z, f"{mat}[facing=east,half=top]")
        b.set(x2, y, z, f"{mat}[facing=west,half=top]")


def fence_ring(b: Build, x1, x2, z1, z2, y, mat="spruce_fence", skip=()):
    for x in range(x1, x2 + 1):
        for z in (z1, z2):
            if (x, z) not in skip:
                b.set(x, y, z, mat)
    for z in range(z1 + 1, z2):
        for x in (x1, x2):
            if (x, z) not in skip:
                b.set(x, y, z, mat)


def wall_sign(b: Build, x, y, z, facing, lines, wood="spruce", glow=False, color="black"):
    b.sign(x, y, z, f"{wood}_wall_sign[facing={facing}]", lines, color=color, glow=glow)


def clear_box(b: Build, x1, y1, z1, x2, y2, z2):
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                b.clear(x, y, z)


def big_tree(b: Build, x, y, z, h=18, trunk=2, wood="dark_oak", leaves=None, seed=3, crown=6, branches=5,
             roots=True):
    """Chunky fantasy tree: flared root plate, trunk of `trunk`x`trunk`, branches, multi-lobe crown.

    Returns list of (bx, by, bz) branch tips (handy for hanging lanterns)."""
    import random
    rng = random.Random(seed)
    leaves = leaves or [("dark_oak_leaves", 3), ("oak_leaves", 3), ("azalea_leaves", 1)]
    log = f"{wood}_log"
    wood_block = f"{wood}_wood"
    cells = [(x + i, z + k) for i in range(trunk) for k in range(trunk)]
    for yy in range(y, y + h):
        for (cx, cz) in cells:
            b.set(cx, yy, cz, f"{log}[axis=y]")
    # root flare
    if roots:
        cxm, czm = x + (trunk - 1) / 2, z + (trunk - 1) / 2
        for d in HORIZ:
            dx, _, dz = DIRS[d]
            for side in range(trunk):
                ox = x + (side if dx == 0 else (trunk if dx > 0 else -1))
                oz = z + (side if dz == 0 else (trunk if dz > 0 else -1))
                n = rng.randint(1, 3)
                for k in range(n):
                    px, pz = ox + dx * k, oz + dz * k
                    top = n - k
                    for yy in range(y, y + top):
                        b.set(px, yy, pz, f"{wood_block}[axis=y]" if yy > y else f"{log}[axis={'x' if dx else 'z'}]")
    tips = []
    angs = [i * (2 * math.pi / branches) + rng.uniform(-0.4, 0.4) for i in range(branches)]
    for i, a in enumerate(angs):
        by = y + int(h * (0.55 + 0.35 * (i / max(1, branches - 1)))) - rng.randint(0, 2)
        ln = rng.randint(3, 6)
        px, pz = x + trunk / 2 - 0.5, z + trunk / 2 - 0.5
        for k in range(1, ln + 1):
            bx = round(px + math.cos(a) * (k + trunk / 2))
            bz = round(pz + math.sin(a) * (k + trunk / 2))
            byy = by + k // 2
            axis = "x" if abs(math.cos(a)) > abs(math.sin(a)) else "z"
            b.set(bx, byy, bz, f"{log}[axis={axis}]")
        tips.append((bx, byy, bz))
        leaf_clump(b, bx, byy + 1, bz, rng.randint(2, 3), 2, rng.randint(2, 3), mix=leaves, density=0.9)
    top = y + h
    leaf_clump(b, x + trunk // 2, top, z + trunk // 2, crown, 3, crown, mix=leaves, density=0.92)
    leaf_clump(b, x + trunk // 2, top + 2, z + trunk // 2, crown - 2, 2, crown - 2, mix=leaves, density=0.9)
    return tips


def tidy(b: Build):
    """Drop anything that would pop off on the first block update: carpets / plants / buttons with no
    support, orphan tall-plant halves. Runs until stable."""
    from aeprops.style import NEEDS_SOIL
    changed = True
    while changed:
        changed = False
        for (x, y, z), st in list(b.blocks.items()):
            n = bname(st).split(":")[1]
            below = b.get(x, y - 1, z)
            p = props_of(st)
            drop = False
            if n.endswith("carpet") and below is None:
                drop = True
            elif (n in NEEDS_SOIL or n.endswith("_tulip") or n in ("azalea", "flowering_azalea")) \
                    and not n.startswith("potted") and not n.endswith("_block"):
                if p.get("half") == "upper":
                    drop = below is None or bname(below) != bname(st)
                elif n in ("brown_mushroom", "red_mushroom"):
                    drop = below is None
                else:
                    drop = below is None or bname(below) not in SOIL_BLOCKS
            elif n.endswith("_button") and p.get("face") == "floor" and below is None:
                drop = True
            if drop:
                b.clear(x, y, z)
                if p.get("half") == "lower":
                    b.clear(x, y + 1, z)
                changed = True
