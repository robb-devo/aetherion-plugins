"""Shared Eldervale / Blocky Verse style vocabulary: material mixes, roofs, small parts.

Mixes are lifted from the block counts of the four Eldervale islands and the OriginBuilds spawn.
"""
from __future__ import annotations

from .core import Build, DIRS, OPP, HORIZ, cw, ccw, bname

# ---- material mixes --------------------------------------------------------------------------
STONE_BRICK = [("stone_bricks", 6), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2), ("andesite", 1)]
COBBLE = [("cobblestone", 5), ("mossy_cobblestone", 3), ("andesite", 2), ("tuff", 1)]
ROCK = [("stone", 4), ("andesite", 3), ("tuff", 3), ("cobblestone", 2), ("mossy_cobblestone", 1)]
DARK_ROCK = [("tuff", 4), ("deepslate", 3), ("cobbled_deepslate", 2), ("stone", 2), ("andesite", 1)]
SOIL = [("moss_block", 5), ("grass_block", 3), ("rooted_dirt", 1), ("coarse_dirt", 1)]
SOIL_EDGE = [("coarse_dirt", 3), ("rooted_dirt", 2), ("dirt", 2), ("moss_block", 1)]
PATH = [("dirt_path", 4), ("coarse_dirt", 2), ("packed_mud", 1), ("rooted_dirt", 1)]
PLASTER = [("white_concrete_powder", 4), ("white_wool", 3), ("light_gray_wool", 2), ("white_concrete", 1)]
DEEP_TILE = [("cracked_deepslate_tiles", 5), ("deepslate_tiles", 3), ("cobbled_deepslate", 1)]
PAVERS = [("stone_bricks", 3), ("andesite", 2), ("cobblestone", 2), ("mossy_cobblestone", 1), ("tuff", 1)]

SOIL_BLOCKS = {"minecraft:moss_block", "minecraft:grass_block", "minecraft:rooted_dirt", "minecraft:coarse_dirt",
               "minecraft:dirt", "minecraft:podzol", "minecraft:mud", "minecraft:muddy_mangrove_roots",
               "minecraft:farmland", "minecraft:mycelium"}

GROUND_PLANTS = [("short_grass", 6), ("fern", 5), ("air", 9), ("moss_carpet", 1)]
FLOWERS = [("azure_bluet", 2), ("oxeye_daisy", 1), ("dandelion", 1), ("blue_orchid", 1), ("white_tulip", 1),
           ("cornflower", 1), ("lily_of_the_valley", 1)]


def soil_patch(b: Build, cx, cz, rx, rz, y=0, mix=SOIL, plants=0.45, flowers=0.06, noise=0.35, depth=1,
               edge_mix=SOIL_EDGE):
    """Irregular elliptical patch of top soil at layer y (flush with terrain) + ground plants on top."""
    rng = b.rng
    for x in range(cx - rx - 1, cx + rx + 2):
        for z in range(cz - rz - 1, cz + rz + 2):
            d = ((x - cx) / (rx + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2
            if d > 1.0 + rng.uniform(-noise, noise * 0.5):
                continue
            if b.has(x, y, z):
                continue
            edge = d > 0.7
            b.set(x, y, z, b._pick(edge_mix if edge and rng.random() < 0.5 else mix))
            for k in range(1, depth):
                if not b.has(x, y - k, z):
                    b.set(x, y - k, z, "dirt")
            top = b.get(x, y, z)
            if bname(top) in SOIL_BLOCKS and not b.has(x, y + 1, z):
                r = rng.random()
                if r < flowers:
                    b.set(x, y + 1, z, b._pick(FLOWERS))
                elif r < flowers + plants:
                    p = b._pick(GROUND_PLANTS)
                    if p != "air":
                        b.set(x, y + 1, z, p)


def plant_on(b: Build, x, y, z, plant):
    """Place a plant at (x,y,z) only if the block below is soil and the spot is free."""
    below = b.get(x, y - 1, z)
    if below and bname(below) in SOIL_BLOCKS and not b.has(x, y, z):
        if plant in ("tall_grass", "large_fern", "lilac", "rose_bush", "peony"):
            if not b.has(x, y + 1, z):
                b.tall(x, y, z, plant)
        else:
            b.set(x, y, z, plant)
        return True
    return False


def hang_lantern(b: Build, x, y_support, z, chain=1, soul=False):
    """Chain(s) under a support block at y_support, lantern at the bottom."""
    for i in range(1, chain + 1):
        b.set(x, y_support - i, z, "chain[axis=y]")
    b.set(x, y_support - chain - 1, z, ("soul_lantern" if soul else "lantern") + "[hanging=true]")


def pebbles(b: Build, pts, y=1):
    for (x, z) in pts:
        if b.has(x, y - 1, z) and not b.has(x, y, z):
            b.set(x, y, z, "stone_button[face=floor,facing=north]")


def crate(b: Build, x, y, z, kind="spruce"):
    """A crate-looking block (composter/barrel/planks with trapdoor lid)."""
    r = b.rng.random()
    if kind == "barrel" or r < 0.35:
        b.container(x, y, z, "barrel[facing=up]")
    elif r < 0.65:
        b.set(x, y, z, "composter[level=0]")
    else:
        b.set(x, y, z, f"stripped_{kind}_wood[axis=y]" if kind != "bamboo" else "bamboo_block[axis=y]")


def post(b: Build, x, y1, y2, z, mat="stripped_spruce_log[axis=y]"):
    for y in range(y1, y2 + 1):
        b.set(x, y, z, mat)


def beam(b: Build, p1, p2, wood="stripped_spruce_wood"):
    """Horizontal beam along x or z using bark-all-round wood (Blocky Verse framing)."""
    (x1, y, z1), (x2, _, z2) = p1, p2
    if z1 == z2:
        for x in range(min(x1, x2), max(x1, x2) + 1):
            b.set(x, y, z1, f"{wood}[axis=x]")
    else:
        for z in range(min(z1, z2), max(z1, z2) + 1):
            b.set(x1, y, z, f"{wood}[axis=z]")


def corbel(b: Build, x, y, z, facing, stairs="spruce_stairs"):
    """Upside-down stair bracket under a beam end; 'facing' = direction toward the wall it hugs."""
    b.set(x, y, z, f"{stairs}[facing={facing},half=top]")


# ---- roofs -----------------------------------------------------------------------------------

def _m(b, m):
    return b._pick(m) if isinstance(m, (list, tuple)) else m


def gable_roof(b: Build, x1, x2, z1, z2, y0, ridge_axis="x", stairs="spruce_stairs", fill="spruce_planks",
               slab="spruce_slab", overhang=1, thick=True, gable_fill=None, gable_inset=0, ridge_cap=None,
               edge_stairs=None, fill_mix=None):
    """Symmetric gable roof over the rectangle [x1..x2]×[z1..z2]; eaves start at y0.

    ridge_axis 'x' -> ridge runs east-west, slopes face north/south.
    gable_fill: material for the triangular end walls (placed gable_inset in from the edge).
    edge_stairs: stairs material for the sloped barge boards (the verge), defaults to `stairs`.
    """
    edge_stairs = edge_stairs or stairs
    if ridge_axis == "x":
        a1, a2 = z1 - overhang, z2 + overhang
        l1, l2 = x1 - overhang, x2 + overhang
    else:
        a1, a2 = x1 - overhang, x2 + overhang
        l1, l2 = z1 - overhang, z2 + overhang
    width = a2 - a1 + 1
    layers = width // 2
    top_y = y0 + layers - 1

    def P(l, y, a):  # map (along, y, across) to (x,y,z)
        return (l, y, a) if ridge_axis == "x" else (a, y, l)

    lo_face, hi_face = ("south", "north") if ridge_axis == "x" else ("east", "west")
    # lo side = low index across (north / west) -> its stairs face toward +across ... careful:
    # stairs on the a1 side step up toward +across => facing = +across direction
    face_a1 = "south" if ridge_axis == "x" else "east"
    face_a2 = "north" if ridge_axis == "x" else "west"
    for i in range(layers):
        y = y0 + i
        for l in range(l1, l2 + 1):
            verge = l in (l1, l2)
            st = edge_stairs if verge else stairs
            b.set(*P(l, y, a1 + i), f"{_m(b, st)}[facing={face_a1},half=bottom]")
            b.set(*P(l, y, a2 - i), f"{_m(b, st)}[facing={face_a2},half=bottom]")
            if thick and i < layers - 1:
                inner1, inner2 = a1 + i + 1, a2 - i - 1
                if inner1 <= inner2:
                    m = fill_mix if fill_mix else fill
                    b.set(*P(l, y, inner1), b._pick(m))
                    b.set(*P(l, y, inner2), b._pick(m))
            if thick and i == 0 and not verge:
                pass
    if width % 2 == 1:
        mid = a1 + layers
        for l in range(l1, l2 + 1):
            b.set(*P(l, top_y, mid), b._pick(fill_mix) if fill_mix else fill)
            b.set(*P(l, top_y + 1, mid), ridge_cap or f"{slab}[type=bottom]")
    elif ridge_cap:
        for l in range(l1, l2 + 1):
            b.set(*P(l, top_y + 1, a1 + layers - 1), ridge_cap)
            b.set(*P(l, top_y + 1, a1 + layers), ridge_cap)
    if gable_fill:
        for end in (l1 + overhang + gable_inset, l2 - overhang - gable_inset):
            for i in range(layers):
                y = y0 + i
                for a in range(a1 + i + 1, a2 - i):
                    if not b.has(*P(end, y, a)):
                        b.set(*P(end, y, a), b._pick(gable_fill))
    return top_y


def hip_roof(b: Build, x1, x2, z1, z2, y0, stairs="spruce_stairs", fill="spruce_planks", slab="spruce_slab",
             cap=None, fill_mix=None, max_layers=99):
    """Hip roof: perimeter rings of inward-rising stairs; fill directly behind each ring."""
    i = 0
    while x1 + i <= x2 - i and z1 + i <= z2 - i and i < max_layers:
        y = y0 + i
        xa, xb, za, zb = x1 + i, x2 - i, z1 + i, z2 - i
        if xa == xb or za == zb:
            for x in range(xa, xb + 1):
                for z in range(za, zb + 1):
                    b.set(x, y, z, cap or f"{slab}[type=bottom]")
            return y
        for x in range(xa, xb + 1):
            b.set(x, y, za, f"{_m(b, stairs)}[facing=south,half=bottom]")
            b.set(x, y, zb, f"{_m(b, stairs)}[facing=north,half=bottom]")
        for z in range(za + 1, zb):
            b.set(xa, y, z, f"{_m(b, stairs)}[facing=east,half=bottom]")
            b.set(xb, y, z, f"{_m(b, stairs)}[facing=west,half=bottom]")
        # thickness ring
        if xa + 1 <= xb - 1 and za + 1 <= zb - 1:
            for x in range(xa + 1, xb):
                for z in (za + 1, zb - 1):
                    b.set(x, y, z, b._pick(fill_mix) if fill_mix else fill)
            for z in range(za + 1, zb):
                for x in (xa + 1, xb - 1):
                    b.set(x, y, z, b._pick(fill_mix) if fill_mix else fill)
        i += 1
    return y0 + i - 1


def thatch_roof(b: Build, x1, x2, z1, z2, y0, ridge_axis="x", profile=None, overhang=2, core="hay_block",
                slab="bamboo_mosaic_slab", rim_mix=None, rim_slab="mud_brick_slab", curl=True, thickness=2,
                moss=0.0):
    """Blocky-Verse style swooping thatch.

    profile = roof height in half-blocks per row, from the eave (row 0) to the ridge.
    The shell is `thickness` blocks deep, thicker where the next row steps up more than a block,
    so it never shows gaps. Gable verges use rim_mix (brown wool / mud bricks) and curl up at the
    eave corners. Returns {(x, z): top_y} of the roof surface for filling gable walls.
    """
    if ridge_axis == "x":
        a1, a2 = z1 - overhang, z2 + overhang
        l1, l2 = x1 - overhang, x2 + overhang
    else:
        a1, a2 = x1 - overhang, x2 + overhang
        l1, l2 = z1 - overhang, z2 + overhang
    width = a2 - a1 + 1
    half = (width + 1) // 2
    if profile is None:
        profile = [0, 1, 2, 4, 6, 8, 10, 11, 12, 13, 14, 14, 15, 15]
    prof = [profile[min(i, len(profile) - 1)] for i in range(half)]

    def P(l, y, a):
        return (l, y, a) if ridge_axis == "x" else (a, y, l)

    tops = {}
    for l in range(l1, l2 + 1):
        de = min(l - l1, l2 - l)
        for dist in range(half):
            hh = prof[dist]
            if curl and de < 2 and dist < 2:
                hh += [2, 1][de]
            nxt = prof[min(dist + 1, half - 1)]
            jump = max(0, (nxt - prof[dist] + 1) // 2)
            T = max(thickness, jump + 1)
            rim = rim_mix is not None and de == 0
            for a in sorted({a1 + dist, a2 - dist}):
                full, odd = hh // 2, hh % 2
                top = y0 - 1
                if full >= 1:
                    top = y0 + full - 1
                    for y in range(max(y0, top - T + 1), top + 1):
                        b.set(*P(l, y, a), b._pick(rim_mix) if rim else core)
                if odd or hh == 0:
                    y = y0 + full
                    b.set(*P(l, y, a), f"{rim_slab if rim else slab}[type=bottom]")
                    top = y
                c = P(l, top, a)
                tops[(c[0], c[2])] = top
                if moss and not rim and full >= 1 and not odd and b.rng.random() < moss:
                    above = P(l, top + 1, a)
                    if not b.has(*above):
                        b.set(*above, "moss_carpet")
    return tops


def fill_under_roof(b: Build, plane, fixed, lo, hi, y0, mat, trim=None, ymax=64):
    """Fill a vertical wall plane (plane='x' -> wall along x at z=fixed) from y0 up to the roof."""
    for v in range(lo, hi + 1):
        y = y0
        col = []
        while y < y0 + ymax:
            p = (v, y, fixed) if plane == "x" else (fixed, y, v)
            if b.has(*p):
                break
            col.append(p)
            y += 1
        else:
            continue
        for i, p in enumerate(col):
            last = i == len(col) - 1
            b.set(*p, b._pick(trim) if (trim and last) else b._pick(mat))


def gable_wall_tri(b: Build, end_l, a1, a2, y0, ridge_axis, mat, inset_layers=0):
    """Fill a triangle wall below a 45° gable spanning a1..a2 (inclusive, walls inside eaves)."""
    i = 0
    while a1 + i <= a2 - i:
        for a in range(a1 + i, a2 - i + 1):
            p = (end_l, y0 + i, a) if ridge_axis == "x" else (a, y0 + i, end_l)
            if not b.has(*p):
                b.set(*p, b._pick(mat))
        i += 1


# ---- support checks --------------------------------------------------------------------------

def center_support(state, face):
    """Vanilla Block.canSupportCenter approximation for the given face of `state` ('up'/'down')."""
    if state is None:
        return False
    from .core import is_full_cube, props_of, is_fence, is_wall, is_pane
    n = bname(state).split(":")[1]
    p = props_of(state)
    if "leaves" in n:
        return False
    if is_full_cube(state):
        return True
    if is_fence(state) or is_wall(state) or is_pane(state):
        return True
    if n == "chain":
        return p.get("axis") == "y"
    if n.endswith("_slab"):
        return p["type"] == "double" or (p["type"] == ("bottom" if face == "down" else "top"))
    if n.endswith("_stairs"):
        return p["half"] == ("bottom" if face == "down" else "top")
    if n.endswith("_trapdoor"):
        return p["open"] == "false" and p["half"] == ("bottom" if face == "down" else "top")
    if n in ("hopper", "lantern", "soul_lantern", "end_rod", "lightning_rod", "scaffolding", "dirt_path",
             "farmland", "composter", "cauldron", "water_cauldron", "barrel", "lectern", "grindstone", "bell"):
        return n not in ("lantern", "soul_lantern") or face == "down"
    return False


NEEDS_SOIL = ("short_grass", "fern", "tall_grass", "large_fern", "dandelion", "poppy", "blue_orchid", "allium",
              "azure_bluet", "tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sweet_berry_bush",
              "azalea", "flowering_azalea", "sapling", "lilac", "rose_bush", "peony", "sunflower", "dead_bush",
              "torchflower", "pink_petals", "nether_sprouts", "crimson_roots", "warped_roots", "brown_mushroom",
              "red_mushroom")


def check_support(b: Build):
    warn = []
    for (x, y, z), st in b.blocks.items():
        n = bname(st).split(":")[1]
        p = st.split("[", 1)[1][:-1] if "[" in st else ""
        below = b.get(x, y - 1, z)
        above = b.get(x, y + 1, z)
        if (any(n == t or n.endswith("_" + t) or n == t for t in NEEDS_SOIL) or n.endswith("_tulip")) and \
                not n.endswith("_block") and not n.startswith("potted"):
            if "half=upper" in p:
                if not below or bname(below) != bname(st):
                    warn.append(f"orphan upper half {st} @ {(x, y, z)}")
                continue
            ok = below is not None and (bname(below) in SOIL_BLOCKS or
                                        (n in ("brown_mushroom", "red_mushroom") and below is not None))
            if n == "dead_bush" and below and bname(below) in ("minecraft:sand", "minecraft:terracotta",
                                                             "minecraft:red_sand"):
                ok = True
            if n == "pink_petals" and below and bname(below) in SOIL_BLOCKS:
                ok = True
            if not ok:
                warn.append(f"plant without soil {n} @ {(x, y, z)} below={below}")
        if n in ("lantern", "soul_lantern"):
            if "hanging=true" in p and not center_support(above, "down"):
                warn.append(f"hanging lantern w/o center support @ {(x, y, z)} above={above}")
            if "hanging=false" in p and not center_support(below, "up"):
                warn.append(f"standing lantern w/o center support @ {(x, y, z)} below={below}")
        if n in ("candle",) or (n.endswith("_candle") and "cake" not in n):
            if not center_support(below, "up"):
                warn.append(f"candle w/o support @ {(x, y, z)} below={below}")
        if n == "chain" and "axis=y" in p and above is None and below is None:
            warn.append(f"floating chain @ {(x, y, z)}")
        if n.endswith("carpet") and below is None:
            warn.append(f"floating carpet @ {(x, y, z)}")
        if n in ("torch", "candle") or (n.endswith("_candle") and "cake" not in n):
            if below is None:
                warn.append(f"floating {n} @ {(x, y, z)}")
        if n == "wall_torch" or n.endswith("_wall_sign") or n == "ladder" or n.endswith("wall_banner"):
            f = [kv.split("=")[1] for kv in p.split(",") if kv.startswith("facing=")][0]
            dx, _, dz = DIRS[OPP[f]]
            if b.get(x + dx, y, z + dz) is None:
                warn.append(f"wall-attached {n} facing {f} w/o backing @ {(x, y, z)}")
        if n == "vine":
            pass
        if n.endswith("_door") and "half=lower" in p and below is None:
            warn.append(f"door w/o floor @ {(x, y, z)}")
        if n == "rail" and below is None:
            warn.append(f"rail w/o floor @ {(x, y, z)}")
        if n.endswith("_button") and "face=floor" in p and below is None:
            warn.append(f"floating button @ {(x, y, z)}")
        if n.endswith("_pressure_plate") and below is None:
            warn.append(f"floating plate @ {(x, y, z)}")
        if n == "flower_pot" or n.startswith("potted_"):
            if below is None:
                warn.append(f"floating pot @ {(x, y, z)}")
        if n in ("lily_pad",) and below is None:
            warn.append(f"lily pad without water @ {(x, y, z)}")
    return warn


# ---- shapes ------------------------------------------------------------------------------------
import math as _math


class Noise2:
    """Smooth 2D value noise in [-1, 1]."""

    def __init__(self, seed=1):
        self.seed = seed

    def _h(self, ix, iz):
        n = (ix * 374761393 + iz * 668265263 + self.seed * 982451653) & 0xFFFFFFFF
        n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0 * 2 - 1

    def __call__(self, x, z):
        ix, iz = _math.floor(x), _math.floor(z)
        fx, fz = x - ix, z - iz
        sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
        a, b_, c, d = self._h(ix, iz), self._h(ix + 1, iz), self._h(ix, iz + 1), self._h(ix + 1, iz + 1)
        top = a + (b_ - a) * sx
        bot = c + (d - c) * sx
        return top + (bot - top) * sz

    def fbm(self, x, z, octaves=3):
        v, amp, f, tot = 0.0, 1.0, 1.0, 0.0
        for _ in range(octaves):
            v += amp * self(x * f, z * f)
            tot += amp
            amp *= 0.5
            f *= 2.0
        return v / tot


def brace(b: Build, start, end_dir, n, wood="spruce", under=True):
    """Diagonal brace rising from `start` toward horizontal direction end_dir, n steps.

    Top surface = bottom stairs facing end_dir; underside filled with upside-down stairs.
    """
    x, y, z = start
    dx, _, dz = DIRS[end_dir]
    for i in range(n):
        b.set(x + dx * i, y + i, z + dz * i, f"{wood}_stairs[facing={end_dir},half=bottom]")
        if under and i < n - 1:
            b.set(x + dx * (i + 1), y + i, z + dz * (i + 1), f"{wood}_stairs[facing={OPP[end_dir]},half=top]")


def strut_down(b: Build, top, toward, n, wood="spruce"):
    """Upside-down stair strut descending from `top` toward horizontal dir `toward` (under a deck/beam)."""
    x, y, z = top
    dx, _, dz = DIRS[toward]
    for i in range(n):
        b.set(x + dx * i, y - i, z + dz * i, f"{wood}_stairs[facing={OPP[toward]},half=top]")


LEAVES = [("oak_leaves", 4), ("azalea_leaves", 3), ("flowering_azalea_leaves", 1), ("birch_leaves", 1)]


def leaf_clump(b: Build, cx, cy, cz, rx, ry, rz, mix=LEAVES, density=0.9, only_air=True):
    rng = b.rng
    for x in range(cx - rx, cx + rx + 1):
        for y in range(cy - ry, cy + ry + 1):
            for z in range(cz - rz, cz + rz + 1):
                d = ((x - cx) / (rx + 0.5)) ** 2 + ((y - cy) / (ry + 0.5)) ** 2 + ((z - cz) / (rz + 0.5)) ** 2
                if d <= 1.0 and rng.random() < density * (1.15 - d * 0.5):
                    if only_air and b.has(x, y, z):
                        continue
                    b.set(x, y, z, b._pick(mix))


def small_spruce(b: Build, x, y, z, h=7):
    for i in range(h - 1):
        b.set(x, y + i, z, "spruce_log[axis=y]")
    levels = []
    r = 0
    for i in range(h + 1):
        yy = y + h - i
        if i == 0:
            rr = 0
        elif i <= 2:
            rr = 1
        else:
            rr = 2 if (i % 2 == 1) else 1
        if yy - y < 2:
            continue
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) + abs(dz) > rr + (1 if rr == 2 else 0):
                    continue
                if (dx, dz) == (0, 0) and yy < y + h - 1:
                    continue
                if not b.has(x + dx, yy, z + dz):
                    b.set(x + dx, yy, z + dz, "spruce_leaves")
    b.set(x, y + h, z, "spruce_leaves")


def small_oak(b: Build, x, y, z, h=4, mix=LEAVES, r=2):
    for i in range(h):
        b.set(x, y + i, z, "oak_log[axis=y]")
    leaf_clump(b, x, y + h, z, r, 1, r, mix=mix, density=0.95)
    leaf_clump(b, x, y + h + 1, z, r - 1, 1, r - 1, mix=mix, density=0.95)
