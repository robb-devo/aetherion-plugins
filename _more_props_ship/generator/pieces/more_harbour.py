"""Second pass - harbour pieces (Anker Harbour quays, headlands, breakwaters)."""
import math

from aeprops.core import Build, bname
from aeprops.style import (STONE_BRICK, COBBLE, PAVERS, PLASTER, ROCK, soil_patch, hang_lantern, pebbles, post, beam,
                           corbel, gable_roof, gable_wall_tri, fill_under_roof, brace, strut_down, leaf_clump, Noise2)
from . import piece
from ._kit import (ground_anchor, waterlog_below, disc, ring, boulder, plant, hang, stairs_ring, corbel_ring,
                   fence_ring, wall_sign, tidy, clear_box, QUAY_STONE, WHITEWASH, DARK_BAND, TILE_ROOF, BLACK_ROOF,
                   SEABED)


def to_center(x, z, cx, cz):
    dx, dz = cx - x, cz - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def away(x, z, cx, cz):
    return {"east": "west", "west": "east", "north": "south", "south": "north"}[to_center(x, z, cx, cz)]


# ---------------------------------------------------------------------------------------------
@piece("ae_harbour_lighthouse", "harbour",
       "Anker Light: a whitewashed round tower banded in deepslate on a mossy rock plinth, tapering to a corbelled "
       "iron-railed gallery, a glazed lamp room with a warm shroomlight core and a verdigris copper cap with a "
       "lightning-rod finial. Ladder to the top, keeper's oil store at the foot.",
       "Headland ground; door points ahead.", group="landmark")
def harbour_lighthouse():
    b = Build("ae_harbour_lighthouse", seed=301)
    rng = b.rng
    # rock plinth: lumpy outcrop, grips 3 blocks down
    nz = Noise2(301)
    for (x, z) in disc(0, 0, 6.5):
        r = math.hypot(x, z)
        h = int(1.6 - (r - 4.5) * 0.9 + 0.8 * nz.fbm(x * 0.35, z * 0.35)) if r > 4.5 else 1
        for y in range(-3, h + 1):
            b.set(x, y, z, b._pick(ROCK))
        if r > 4.4 and rng.random() < 0.55:
            b.set(x, h, z, b._pick([("moss_block", 3), ("mossy_cobblestone", 2), ("gravel", 1)]))
    # platform + low ring wall
    for (x, z) in disc(0, 0, 4.6):
        b.set(x, 1, z, b._pick(PAVERS))
        for y in range(2, 5):
            b.clear(x, y, z)
    for (x, z) in ring(0, 0, 4.6):
        if z >= 3 and abs(x) <= 1:
            continue
        b.set(x, 2, z, "stone_brick_wall" if rng.random() < 0.7 else "mossy_stone_brick_wall")
    # steps up to the platform (south)
    for x in (-1, 0, 1):
        b.set(x, 1, 5, "stone_brick_stairs[facing=north]")
        b.set(x, 0, 6, "stone_brick_stairs[facing=north]")
        b.set(x, 1, 6, "air")
        b.set(x, -1, 6, "cobblestone")
        b.set(x, 0, 5, "stone_bricks")
    for x in (-2, 2):
        b.set(x, 2, 4, "stone_brick_wall")
        b.set(x, 3, 4, "lantern")
    # tower
    TOP = 24
    band = lambda y: "base" if y < 5 else ("dark" if (y - 5) % 6 in (4, 5) else "white")
    for y in range(2, TOP):
        r = 3.6 - (y - 2) * (1.0 / (TOP - 2))
        for (x, z) in ring(0, 0, r):
            kind = band(y)
            mat = {"base": [("stone_bricks", 3), ("mossy_stone_bricks", 1), ("polished_andesite", 1)],
                   "dark": [("deepslate_bricks", 4), ("polished_deepslate", 1)],
                   "white": WHITEWASH}[kind]
            b.set(x, y, z, b._pick(mat))
        for (x, z) in disc(0, 0, r):
            if (x, z) not in set(ring(0, 0, r)):
                b.clear(x, y, z)
    for (x, z) in disc(0, 0, 3):
        b.set(x, 1, z, "spruce_planks")
    # door + arch + plaque
    b.clear(0, 2, 3); b.clear(0, 3, 3)
    b.door(0, 2, 3, "dark_oak", "north")
    b.set(0, 2, 4, "air")
    b.set(0, 4, 4, "stone_brick_stairs[facing=north,half=top]")
    b.set(-1, 4, 4, "stone_brick_stairs[facing=east,half=top]")
    b.set(1, 4, 4, "stone_brick_stairs[facing=west,half=top]")
    b.set(0, 4, 3, "chiseled_stone_bricks")
    wall_sign(b, 1, 3, 4, "south", ["", "ANKER", "~ LIGHT ~", ""], wood="dark_oak")
    b.set(1, 2, 4, "air")
    b.set(1, 3, 3, b.get(1, 3, 3) or "stone_bricks")
    # spiral of windows
    for i, y in enumerate(range(7, TOP - 1, 3)):
        a = i * 1.35 + 0.6
        r = 3.6 - (y - 2) * (1.0 / (TOP - 2))
        x, z = round(math.cos(a) * r), round(math.sin(a) * r)
        if b.has(x, y, z) and (x, z) != (0, 3):
            b.set(x, y, z, "glass_pane")
            b.set(x, y + 1, z, "glass_pane")
    # ladder up the inside north wall
    for y in range(2, TOP + 1):
        r = 3.6 - (y - 2) * (1.0 / (TOP - 2)) if y < TOP else 2.6
        zin = -int(r - 0.5)
        if b.has(0, y, zin - 1) or y >= TOP:
            b.set(0, y, zin, "ladder[facing=south]")
    # gallery
    G = TOP
    for (x, z) in disc(0, 0, 4.4):
        b.set(x, G, z, "dark_oak_planks" if math.hypot(x, z) < 3.4 else "polished_andesite")
    b.set(0, G, -2, "ladder[facing=south]")
    b.set(0, G, -3, "stone_bricks")
    for (x, z) in ring(0, 0, 4.4):
        b.set(x, G - 1, z, f"stone_brick_stairs[facing={to_center(x, z, 0, 0)},half=top]")
        b.set(x, G + 1, z, "iron_bars")
    for (x, z) in ring(0, 0, 3.4):
        if not b.has(x, G - 1, z):
            b.set(x, G - 1, z, "stone_bricks")
    for (x, z) in ((4, 0), (-4, 0), (0, 4), (0, -4)):
        b.set(x, G + 1, z, "polished_blackstone_wall")
        b.set(x, G + 2, z, "lantern")
    # lamp room: warm glass drum, four slim mullions, shroomlight core
    L0, L1 = G + 1, G + 4
    lamp = ring(0, 0, 2.4)
    for (x, z) in lamp:
        for y in range(L0, L1 + 1):
            mullion = abs(x) == 2 and abs(z) == 2 or (abs(x) + abs(z) == 3 and abs(x) != abs(z) and False)
            b.set(x, y, z, "stripped_dark_oak_log[axis=y]" if (abs(x) == abs(z)) else "yellow_stained_glass_pane")
    for (x, z) in lamp:
        b.set(x, L0, z, "polished_blackstone" if abs(x) != abs(z) else b.get(x, L0, z))
    b.clear(0, L0, -2); b.clear(0, L0 + 1, -2)          # hatch from the gallery
    b.set(0, L0, -2, "dark_oak_door[facing=south,half=lower,hinge=left,open=false]")
    b.set(0, L0 + 1, -2, "dark_oak_door[facing=south,half=upper,hinge=left,open=false]")
    for y in range(L0, L1):
        b.set(0, y, 0, "shroomlight" if y > L0 else "polished_blackstone")
    for (x, z) in ((1, 0), (-1, 0), (0, 1)):
        b.set(x, L0 + 1, z, "shroomlight")
        b.set(x, L0, z, "polished_blackstone_slab[type=bottom]")
    b.set(0, L1, 0, "shroomlight")
    for (x, z) in ring(0, 0, 2.4):
        b.set(x, L1 + 1, z, "stripped_dark_oak_wood[axis=y]")
    # copper cap: tall verdigris cone with a lightning-rod finial
    C = L1 + 2
    tiers = [(3.0, "waxed_weathered_cut_copper"), (2.2, "waxed_weathered_cut_copper"),
             (1.5, "waxed_oxidized_cut_copper"), (0.9, "waxed_oxidized_cut_copper")]
    for i, (r, mat) in enumerate(tiers):
        rr = ring(0, 0, r)
        for (x, z) in disc(0, 0, r):
            b.set(x, C + i, z, f"{mat}_stairs[facing={to_center(x, z, 0, 0)},half=bottom]" if (x, z) in set(rr)
                  else mat)
    b.set(0, C + 4, 0, "waxed_oxidized_cut_copper_slab[type=bottom]")
    b.set(0, C + 5, 0, "lightning_rod[facing=up]")
    for (x, z) in ((3, 0), (-3, 0), (0, 3), (0, -3)):
        b.set(x, C - 1, z, "waxed_weathered_cut_copper_stairs[facing=" + to_center(x, z, 0, 0) + ",half=top]") \
            if not b.has(x, C - 1, z) else None
    # keeper's oil store (lean-to on the east flank of the plinth)
    for z in range(-2, 2):
        for x in (4, 5):
            b.set(x, 1, z, "spruce_planks")
            for y in range(2, 5):
                b.clear(x, y, z)
    for z in (-2, 1):
        post(b, 5, 2, 3, z, "stripped_spruce_log[axis=y]")
    for z in range(-2, 2):
        b.set(5, 4, z, "spruce_stairs[facing=west,half=bottom]")
        b.set(4, 4, z, "spruce_slab[type=top]") if not b.has(4, 4, z) else None
    b.container(4, 2, -1, "barrel[facing=up]")
    b.container(5, 2, -1, "barrel[facing=up]")
    b.container(4, 2, 0, "barrel[facing=east]")
    b.set(4, 3, -1, "lantern")
    b.set(5, 2, 0, "composter[level=0]")
    # sea-worn plinth: kelp-dark sand patches, a coil of chain, gulls' perch
    for (x, z) in ((-5, 3), (-6, 1), (5, 4), (3, -6)):
        top = max([y for y in range(-3, 4) if b.has(x, y, z)] or [0])
        b.set(x, top, z, "sand")
    b.set(-4, 2, -2, "chain[axis=x]")
    b.set(-3, 2, 3, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    tidy(b)
    ground_anchor(b, 1)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_harbour_warehouse", "harbour",
       "Anker Stores: a stone-and-timber bonded warehouse with a jettied plaster upper floor, a loading gable "
       "whose hoist beam swings a lashed cargo pallet over the apron, a deepslate-tile roof with a chimney, a "
       "broad cargo arch stacked with goods, and a paved loading apron with lamps and a hand cart.",
       "Cargo arch points the way you look.", group="landmark")
def harbour_warehouse():
    b = Build("ae_harbour_warehouse", seed=311)
    rng = b.rng
    X1, X2, Z1, Z2 = -7, 7, -5, 4
    # foundation + apron
    b.fill(X1, -1, Z1, X2, -1, Z2, "cobblestone")
    for x in range(X1 - 1, X2 + 2):
        for z in range(Z1 - 1, Z2 + 5):
            b.set(x, 0, z, b._pick(PAVERS) if z > Z2 else b._pick(COBBLE))
    for x in range(X1 - 1, X2 + 2):
        b.set(x, 0, Z2 + 4, "polished_andesite")
    b.fill(X1 + 1, 0, Z1 + 1, X2 - 1, 0, Z2 - 1, "spruce_planks")
    # stone ground floor y=1..5
    for y in range(1, 6):
        for x in range(X1, X2 + 1):
            for z in (Z1, Z2):
                corner = x in (X1, X2)
                b.set(x, y, z, ("polished_andesite" if y % 2 else "stone_bricks") if corner else b._pick(QUAY_STONE))
        for z in range(Z1 + 1, Z2):
            for x in (X1, X2):
                b.set(x, y, z, b._pick(QUAY_STONE))
    # plinth course (stairs skirting)
    for x in range(X1, X2 + 1):
        b.set(x, 1, Z1 - 1, "stone_brick_stairs[facing=south]")
    for z in range(Z1, Z2 + 1):
        b.set(X1 - 1, 1, z, "stone_brick_stairs[facing=east]")
        b.set(X2 + 1, 1, z, "stone_brick_stairs[facing=west]")
    # cargo arch (south), x=-2..2
    for x in range(-2, 3):
        for y in range(1, 5):
            b.clear(x, y, Z2)
    for x in (-2, 2):
        b.set(x, 4, Z2, f"stone_brick_stairs[facing={'east' if x < 0 else 'west'},half=top]")
    for x in range(-1, 2):
        b.set(x, 5, Z2, "chiseled_stone_bricks" if x == 0 else "stone_bricks")
    for x in (-3, 3):
        b.set(x, 1, Z2 + 1, "stone_brick_wall")                 # wheel guards
    # side windows with iron bars
    for x in (-5, 5):
        for y in (2, 3):
            b.set(x, y, Z2, "iron_bars")
        b.set(x, 4, Z2, "stone_bricks")
    for z in (-3, 1):
        for x in (X1, X2):
            b.set(x, 3, z, "iron_bars")
    # upper floor: jettied on the front, timber frame + plaster, y=6..10
    F = 6
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 2):
            b.set(x, F, z, "dark_oak_planks" if X1 < x < X2 and Z1 < z < Z2 + 1 else "stripped_dark_oak_wood[axis=x]")
    for x in range(X1, X2 + 1):
        b.set(x, F - 1, Z2 + 1, "dark_oak_stairs[facing=north,half=top]") if x % 2 else None
    ZF = Z2 + 1                           # jettied front wall plane
    frame_x = (X1, -4, -1, 1, 4, X2)
    for y in range(F + 1, F + 5):
        for x in range(X1, X2 + 1):
            b.set(x, y, ZF, "stripped_dark_oak_log[axis=y]" if x in frame_x else b._pick(PLASTER))
            b.set(x, y, Z1, "stripped_dark_oak_log[axis=y]" if x in frame_x else b._pick(PLASTER))
        for z in range(Z1, ZF + 1):
            for x in (X1, X2):
                b.set(x, y, z, "stripped_dark_oak_log[axis=y]" if z in (Z1, -1, ZF) else b._pick(PLASTER))
    for x in range(X1, X2 + 1):
        for z in (Z1, ZF):
            b.set(x, F + 4, z, "stripped_dark_oak_wood[axis=x]")
    for z in range(Z1, ZF + 1):
        for x in (X1, X2):
            b.set(x, F + 4, z, "stripped_dark_oak_wood[axis=z]")
    # windows + shutters
    for x in (-6, -3, 3, 6):
        if x in (-3, 3):
            continue
        for zz, f in ((ZF, "south"), (Z1, "north")):
            b.set(x, F + 2, zz, "glass_pane")
            b.set(x, F + 1, zz + (1 if f == "south" else -1), f"dark_oak_trapdoor[facing={f},half=top,open=false]")
    for x in (-3, 3):
        b.set(x, F + 2, ZF, "glass_pane")
        b.set(x - 1 if x < 0 else x + 1, F + 2, ZF + 1, "dark_oak_trapdoor[facing=south,half=bottom,open=true]")
    for z in (-3, 0):
        for x, f in ((X1, "west"), (X2, "east")):
            b.set(x, F + 2, z, "glass_pane")
    # main roof: ridge east-west
    top = gable_roof(b, X1, X2, Z1, ZF, F + 5, ridge_axis="x", stairs=TILE_ROOF, fill="deepslate_tiles",
                     slab="deepslate_tile_slab", overhang=1, edge_stairs="dark_oak_stairs")
    for end in (X1, X2):
        gable_wall_tri(b, end, Z1 + 1, ZF - 1, F + 5, "x", PLASTER)
    # loading gable projecting south: x=-2..2, ridge north-south
    LG1, LG2 = -2, 2
    for y in range(F + 1, F + 5):
        for x in range(LG1, LG2 + 1):
            b.set(x, y, ZF + 1, "stripped_dark_oak_log[axis=y]" if x in (LG1, LG2) else b._pick(PLASTER))
        for x in (LG1, LG2):
            b.set(x, y, ZF, "stripped_dark_oak_log[axis=y]")
    for x in range(LG1, LG2 + 1):
        b.set(x, F, ZF + 1, "stripped_dark_oak_wood[axis=x]")
        b.set(x, F + 4, ZF + 1, "stripped_dark_oak_wood[axis=x]")
        b.set(x, F - 1, ZF + 1, "dark_oak_stairs[facing=north,half=top]")
    # loading door: open trapdoor leaves either side
    for y in (F + 1, F + 2, F + 3):
        b.clear(0, y, ZF + 1)
        b.clear(0, y, ZF)
    b.set(-1, F + 2, ZF + 2, "dark_oak_trapdoor[facing=south,half=bottom,open=true]")
    b.set(1, F + 2, ZF + 2, "dark_oak_trapdoor[facing=south,half=bottom,open=true]")
    b.set(-1, F + 1, ZF + 1, "spruce_planks")
    gable_roof(b, LG1, LG2, Z1 + 2, ZF + 1, F + 5, ridge_axis="z", stairs=TILE_ROOF, fill="deepslate_tiles",
               slab="deepslate_tile_slab", overhang=1, edge_stairs="dark_oak_stairs")
    gable_wall_tri(b, ZF + 1, LG1 + 1, LG2 - 1, F + 5, "z", PLASTER)
    b.set(0, F + 5, ZF + 1, "stripped_dark_oak_log[axis=y]")
    # hoist beam out of the loading gable + pulley + hanging pallet
    HB = F + 6
    for z in range(ZF - 1, ZF + 6):
        b.set(0, HB, z, "stripped_dark_oak_wood[axis=z]")
    b.set(0, HB - 1, ZF + 2, "dark_oak_stairs[facing=north,half=top]")
    b.set(0, HB - 1, ZF + 5, "grindstone[face=ceiling,facing=north]")
    for y in range(7, HB - 1):
        b.set(0, y, ZF + 5, "chain[axis=y]")
    for x in (-1, 0, 1):
        b.set(x, 4, ZF + 5, "spruce_trapdoor[facing=north,half=top,open=false]")
    for x in (-1, 1):
        b.set(x, 6, ZF + 5, "chain[axis=y]")
    b.container(-1, 5, ZF + 5, "barrel[facing=east]")
    b.set(0, 5, ZF + 5, "white_wool")
    b.set(0, 6, ZF + 5, "chain[axis=y]")
    b.set(1, 5, ZF + 5, "composter[level=0]")
    b.set(-1, 6, ZF + 5, "chain[axis=y]")
    b.set(-1, 7, ZF + 5, "spruce_fence")
    b.set(0, 7, ZF + 5, "spruce_fence")
    b.set(1, 7, ZF + 5, "spruce_fence")
    b.set(1, 6, ZF + 5, "chain[axis=y]")
    # signboards either side of the arch
    wall_sign(b, 3, 5, Z2 + 1, "south", ["", "ANKER", "STORES", "~ bonded ~"], wood="dark_oak")
    wall_sign(b, -3, 5, Z2 + 1, "south", ["", "SALT", "ROPE", "TAR"], wood="dark_oak")
    b.set(3, 5, Z2, "stone_bricks"); b.set(-3, 5, Z2, "stone_bricks")
    # weathering on the roof: moss tufts, a patched section
    for (x, y, z), st in list(b.blocks.items()):
        if 'deepslate_tile_stairs' in st and rng.random() < 0.06 and not b.has(x, y + 1, z) and 'half=bottom' in st:
            b.set(x, y, z, st.replace('deepslate_tile_stairs', 'cobbled_deepslate_stairs'))
    # chimney (east gable)
    for y in range(1, top + 4):
        for z in (-2, -1):
            if y > 5 or not b.has(X2 + 1, y, z) or "stairs" in (b.get(X2 + 1, y, z) or ""):
                b.set(X2 + 1, y, z, b._pick(STONE_BRICK) if y > 3 else b._pick(COBBLE))
    b.campfire(X2 + 1, top + 4, -2, lit=True)
    b.set(X2 + 1, top + 4, -1, "stone_brick_slab[type=bottom]")
    # inside: crate stacks visible through the arch, lantern
    for (x, z, h) in ((-5, -3, 3), (-4, -3, 2), (-5, -2, 2), (5, -3, 3), (4, -3, 1), (5, -2, 2), (-1, -3, 2), (1, -3, 1),
                      (0, -3, 3), (-5, 2, 1), (5, 2, 2)):
        for y in range(1, h + 1):
            k = (x + y + z) % 4
            if k == 0:
                b.container(x, y, z, "barrel[facing=up]")
            elif k == 1:
                b.set(x, y, z, "composter[level=0]")
            elif k == 2:
                b.set(x, y, z, "white_wool" if (x + z) % 2 else "brown_wool")
            else:
                b.set(x, y, z, "stripped_spruce_wood[axis=y]")
    hang(b, 0, 5, -1, 1)
    for (x, z, st) in ((-2, 2, 'hay_block[axis=y]'), (-2, 1, 'white_wool'), (2, 2, 'brown_wool'), (2, 1, 'composter[level=0]'),
                       (-2, 1, 'white_wool')):
        b.set(x, 1, z, st)
    b.set(-2, 2, 1, 'brown_carpet')
    b.container(2, 2, 2, 'barrel[facing=up]')
    hang(b, 0, 5, 2, 0)
    for x in range(X1 + 1, X2):
        for z in range(Z1 + 1, Z2):
            if not b.has(x, 5, z):
                b.set(x, 5, z, "stripped_dark_oak_wood[axis=z]" if x % 3 == 0 else "dark_oak_planks")
    # apron clutter
    b.container(-6, 1, Z2 + 2, "barrel[facing=up]")
    b.container(-5, 1, Z2 + 2, "barrel[facing=south]")
    b.set(-6, 2, Z2 + 2, "white_carpet")
    b.set(-6, 1, Z2 + 3, "hay_block[axis=x]")
    b.set(-5, 1, Z2 + 3, "white_wool")
    b.set(6, 1, Z2 + 2, "composter[level=0]")
    b.set(6, 2, Z2 + 2, "composter[level=0]")
    b.set(5, 1, Z2 + 2, "brown_wool")
    b.set(6, 1, Z2 + 3, "chain[axis=x]")
    # hand cart
    b.set(3, 1, Z2 + 3, "spruce_trapdoor[facing=south,half=top,open=false]")
    b.set(4, 1, Z2 + 3, "spruce_trapdoor[facing=south,half=top,open=false]")
    b.set(3, 2, Z2 + 3, "barrel[facing=east]", be={"id": "minecraft:barrel", "Items": []})
    b.set(2, 1, Z2 + 3, "spruce_fence")
    # lamp posts at the apron corners
    for x in (X1 - 1, X2 + 1):
        b.set(x, 1, Z2 + 4, "stone_brick_wall")
        post(b, x, 2, 4, Z2 + 4, "stripped_dark_oak_log[axis=y]")
        b.set(x, 5, Z2 + 4, "lantern")
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_moored_sloop", "harbour",
       "A single-masted trading sloop moored alongside the quay: dark-oak hull with a pale gunwale wale and rounded "
       "bilge, raised sterncastle cabin with lit windows and a stern lantern, forecastle and bowsprit, a set square "
       "sail with a red centre stripe under a crow's nest and pennant, deck cargo, a hatch, a catted anchor, "
       "mooring chains to two quay bollards and a gangplank.",
       "Aim at the quay edge, 1 above water.", group="landmark")
def moored_sloop():
    b = Build("ae_moored_sloop", seed=321)
    rng = b.rng
    W = -1                       # waterline (quay top = y 0, one above the water)
    C = 5                        # hull centre line z
    XS, XB = -12, 12             # transom .. stem

    def hb(x):
        if x < -8:
            return 3 - (-8 - x) * 0.33
        if x > 3:
            return max(0.0, 3.05 * (1 - ((x - 3) / 9.6) ** 1.7))
        return 3.05

    def keel(x):
        return -4 + (1 if x < -9 or x > 8 else 0) + (1 if x > 10 or x < -11 else 0)

    def deck(x):
        if x <= -6:
            return 4
        if x >= 7:
            return 2
        return 1

    def width(x, y):
        k = 0.0 if y >= 0 else 0.55 * (-y)
        return hb(x) - k

    hull = set()
    for x in range(XS, XB + 1):
        for y in range(keel(x), deck(x) + 1):
            w = width(x, y)
            if w < 0.2:
                continue
            for z in range(C - 4, C + 5):
                if abs(z - C) <= w + 0.25:
                    hull.add((x, y, z))
    for (x, y, z) in hull:
        edge = any((x, y, z + dz) not in hull for dz in (-1, 1)) or (x, y, z) in () or \
            (x + 1, y, z) not in hull or (x - 1, y, z) not in hull
        top = y == deck(x)
        if top and not edge:
            b.set(x, y, z, "spruce_planks" if (z - C) % 2 else "stripped_spruce_wood[axis=x]")
            continue
        if y <= W - 1:
            mat = "dark_oak_planks"
        elif y == W:
            mat = "stripped_dark_oak_wood[axis=x]"
        elif y == deck(x) and edge:
            mat = "stripped_spruce_wood[axis=x]"
        else:
            mat = "dark_oak_planks"
        if not edge:
            mat = "dark_oak_planks"
        b.set(x, y, z, mat)
    # rounded bilge: upside-down stairs where the hull steps in below
    for (x, y, z) in list(hull):
        if y >= W or (x, y - 1, z) in hull:
            continue
        side = [dz for dz in (-1, 1) if (x, y, z + dz) not in hull]
        if len(side) == 1:
            f = "north" if side[0] == 1 else "south"
            b.set(x, y, z, f"dark_oak_stairs[facing={f},half=top]")
    # interior of the castles is hollow (cabin), everything else below deck stays solid
    for x in range(XS + 1, -5):
        for z in range(C - 2, C + 3):
            for y in (2, 3):
                if (x, y, z) in hull and all((x, y, z + dz) in hull for dz in (-1, 1)):
                    b.clear(x, y, z)
    # cabin front wall (faces the main deck) with door + windows
    for z in range(C - 2, C + 3):
        for y in (2, 3):
            b.set(-5, y, z, "dark_oak_planks")
        b.set(-5, 4, z, "stripped_dark_oak_wood[axis=z]")
    b.door(-5, 2, C, "dark_oak", "east")
    b.set(-5, 3, C - 2, "glass_pane"); b.set(-5, 3, C + 2, "glass_pane")
    # stern windows (transom) glowing
    for z in (C - 1, C + 1):
        b.set(XS, 3, z, "yellow_stained_glass_pane") if (XS, 3, z) in hull else None
    for z in range(C - 1, C + 2):
        b.set(XS + 1, 2, z, "shroomlight") if b.get(XS + 1, 2, z) is None else None
    b.set(XS + 2, 2, C, "shroomlight")
    # cabin inside: bunk, table, lantern
    b.set(-10, 2, C - 2, "red_carpet") if b.get(-10, 1, C - 2) else None
    b.container(-9, 2, C + 2, "barrel[facing=up]")
    b.set(-7, 2, C - 2, "dark_oak_fence"); b.set(-7, 3, C - 2, "lantern")
    # bulwarks / rails
    for x in range(XS, XB + 1):
        for z in range(C - 4, C + 5):
            if (x, deck(x), z) in hull and not all((x, deck(x), z + dz) in hull for dz in (-1, 1)):
                b.set(x, deck(x) + 1, z, "dark_oak_fence")
            elif (x, deck(x), z) in hull and ((x - 1, deck(x), z) not in hull or (x + 1, deck(x), z) not in hull):
                b.set(x, deck(x) + 1, z, "dark_oak_fence")
    for x in (-5,):
        for z in range(C - 2, C + 3):
            b.set(x, 5, z, "dark_oak_fence")              # sterncastle front rail
    b.clear(-5, 5, C + 2)
    # ladder to the sterncastle: stairs along the cabin side
    b.set(-4, 2, C + 2, "spruce_stairs[facing=west,half=bottom]")
    b.set(-4, 3, C + 2, "air")
    b.set(-5, 3, C + 2, "spruce_stairs[facing=west,half=bottom]")
    b.set(-5, 2, C + 2, "dark_oak_planks")
    b.set(6, 2, C, "spruce_stairs[facing=east]")             # step to forecastle
    b.clear(6, 2, C - 1); b.clear(6, 2, C + 1)
    # entry port + gangplank to the quay
    for zz in range(C - 4, C - 1):
        b.clear(0, 2, zz)
    b.set(0, 1, 0, "spruce_slab[type=bottom]")
    b.set(0, 1, 1, "spruce_slab[type=bottom]")
    # mast, crow's nest, yard, sail, pennant
    MX = 1
    for y in range(2, 19):
        b.set(MX, y, C, "stripped_spruce_log[axis=y]")
    b.set(MX - 1, 2, C, "spruce_stairs[facing=east]"); b.set(MX + 1, 2, C, "spruce_stairs[facing=west]")
    b.set(MX, 2, C - 1, "spruce_stairs[facing=south]"); b.set(MX, 2, C + 1, "spruce_stairs[facing=north]")
    NEST = 14
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                b.set(MX + dx, NEST, C + dz, "spruce_slab[type=top]")
                b.set(MX + dx, NEST + 1, C + dz, "spruce_fence")
                b.set(MX + dx, NEST - 1, C + dz, None)
    for dz in (-1, 1):
        b.set(MX, NEST - 1, C + dz, f"spruce_stairs[facing={'south' if dz < 0 else 'north'},half=top]")
    YARD = 12
    for z in range(C - 5, C + 6):
        b.set(MX + 1, YARD, z, "stripped_spruce_wood[axis=z]")
    b.set(MX + 1, YARD, C, "stripped_spruce_wood[axis=x]")
    for y in range(YARD - 1, 3, -1):
        for z in range(C - 4, C + 5):
            belly = 1 if (abs(z - C) <= 2 and 5 <= y <= YARD - 2) else 0
            if abs(z - C) == 4 and y < 5:
                continue
            mat = "red_wool" if z == C else ("white_wool" if y != 7 else "light_gray_wool")
            b.set(MX + 2 + belly, y, z, mat)
            if belly:
                b.set(MX + 2, y, z, None)
    for z in (C - 5, C + 5):
        b.set(MX + 1, YARD - 1, z, "chain[axis=y]")
        b.set(MX + 1, YARD - 2, z, "lantern[hanging=true]")
    b.set(MX, 19, C, "spruce_fence")
    b.set(MX, 20, C, "spruce_fence")
    b.banner(MX, 21, C, "red_banner[rotation=4]")
    # shrouds: straight chain stays from the nest arms down to the rails either side
    for zside in (-1, 1):
        zz = C + 3 * zside
        for y in range(3, NEST - 1):
            b.set(MX - 1, y, zz, "chain[axis=y]")
        b.set(MX - 1, NEST - 1, zz, "chain[axis=z]")
        b.set(MX - 1, NEST - 1, C + 2 * zside, "chain[axis=z]")
        b.set(MX - 1, NEST - 1, C + zside, "spruce_stairs[facing=east,half=top]")
    # bowsprit
    for i, (x, y) in enumerate(((12, 3), (13, 3), (14, 4), (15, 4))):
        b.set(x, y, C, "stripped_spruce_wood[axis=x]" if i < 3 else "spruce_fence")
    b.set(15, 5, C, "lantern")
    # stern lantern post
    b.set(XS, 5, C, "dark_oak_fence")
    b.set(XS, 6, C, "dark_oak_fence")
    b.set(XS, 7, C, "lantern")
    b.set(XS + 1, 5, C, "dark_oak_fence")
    # tiller on the sterncastle, rudder on the transom
    b.set(-9, 5, C, "spruce_fence"); b.set(-10, 5, C, "spruce_fence")
    for y in range(W - 1, 3):
        b.set(XS - 1, y, C, "stripped_dark_oak_wood[axis=y]")
    # deck cargo, hatch, net pile
    for x in (-2, -1):
        for z in (C - 1, C, C + 1):
            b.set(x, 1, z, "spruce_trapdoor[facing=north,half=top,open=false]")
    b.container(3, 2, C - 2, "barrel[facing=up]")
    b.container(3, 2, C - 1, "barrel[facing=east]")
    b.set(4, 2, C - 2, "composter[level=0]")
    b.set(3, 3, C - 2, "white_carpet")
    b.set(4, 2, C + 2, "white_wool"); b.set(4, 3, C + 2, "brown_carpet")
    b.set(3, 2, C + 2, "hay_block[axis=x]")
    b.set(-3, 2, C + 2, "cobweb")
    b.set(-3, 2, C - 2, "chain[axis=x]")
    b.set(9, 3, C, "barrel[facing=up]", be={"id": "minecraft:barrel", "Items": []})
    b.set(10, 3, C, "lantern")
    # catted anchor on the sea side of the bow
    for y in (1, 2):
        b.set(9, y, C + 4, "chain[axis=y]")
    b.set(9, 0, C + 4, "anvil[facing=east]")
    # mooring chains to quay bollards
    for bx in (-9, 8):
        b.set(bx, 1, 0, "polished_blackstone_wall")
        b.set(bx, 1, 1, "chain[axis=z]")
    waterlog_below(b, W)
    b.meta["context"] = ("water", W)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_anchor_monument", "harbour",
       "The harbour's namesake: a rust-streaked giant iron anchor standing on its crown in a stepped plinth, "
       "stock crossing front-to-back, ring and chain draped down to a coil at its foot, a waxed plaque "
       "'ANKER HARBOUR - held fast since the first tide', lamp posts either side.",
       "Plaque points the way you look.", group="accent")
def anchor_monument():
    b = Build("ae_prop_anchor_monument", seed=331)
    rng = b.rng
    IRON = [("polished_blackstone", 5), ("raw_iron_block", 1), ("polished_basalt[axis=y]", 1)]
    for x in range(-4, 5):
        for z in range(-3, 4):
            b.set(x, 0, z, b._pick(PAVERS))
    b.fill(-2, 1, -2, 2, 1, 2, [("stone_bricks", 3), ("mossy_stone_bricks", 1)])
    stairs_ring(b, -3, 3, -3, 3, 1, "stone_brick_stairs")
    b.fill(-1, 2, -1, 1, 2, 1, "polished_andesite")
    stairs_ring(b, -2, 2, -2, 2, 2, "polished_andesite_stairs")
    b.set(0, 1, 3, "stone_brick_stairs[facing=north]")
    # anchor in the x-y plane (z = 0), standing on its crown at y = 3
    def S_in(x):
        return f"polished_blackstone_stairs[facing={'east' if x < 0 else 'west'},half=top]"

    def S_out(x):
        return f"polished_blackstone_stairs[facing={'west' if x < 0 else 'east'},half=bottom]"
    rows = {3: {-2: "si", -1: "B", 0: "B", 1: "B", 2: "si"},
            4: {-3: "si", -2: "B", 0: "B", 2: "B", 3: "si"},
            5: {-4: "si", -3: "B", 0: "B", 3: "B", 4: "si"},
            6: {-4: "B", -3: "so", 0: "B", 3: "so", 4: "B"},
            7: {-4: "W", 4: "W"}}
    for y, row in rows.items():
        for x, k in row.items():
            b.set(x, y, 0, {"B": b._pick(IRON), "si": S_in(x), "so": S_out(x), "W": "polished_blackstone_wall"}[k])
    for y in range(7, 13):
        b.set(0, y, 0, b._pick(IRON) if y != 9 else "raw_iron_block")
    # stock: crosses front-to-back just under the ring
    for z in range(-3, 4):
        b.set(0, 12, z, "stripped_dark_oak_wood[axis=z]" if abs(z) < 3 else "polished_blackstone_wall")
    b.set(0, 12, 0, "polished_blackstone")
    # ring + chain
    b.set(0, 13, 0, "polished_blackstone_wall")
    for x in (-1, 1):
        b.set(x, 13, 0, "chain[axis=x]")
        b.set(x, 14, 0, "chain[axis=y]")
        b.set(x, 15, 0, "chain[axis=x]")
    b.set(0, 15, 0, "chain[axis=x]")
    for y in range(4, 13):
        b.set(2, y, 1, "chain[axis=y]")
    b.set(2, 13, 0, "chain[axis=x]")
    b.set(2, 12, 0, "air")
    b.set(2, 13, 1, "chain[axis=z]")
    # coil at the foot
    for (x, z, ax) in ((2, 1, "y"), (1, 1, "x"), (0, 1, "x"), (-1, 1, "z"), (-2, 1, "x")):
        if not b.has(x, 3, z):
            b.set(x, 3, z, f"chain[axis={ax}]")
    # plaque + lamps + flowers
    b.set(0, 1, 3, "stone_bricks")
    wall_sign(b, 0, 1, 4, "south", ["ANKER HARBOUR", "held fast since", "the first tide", ""], wood="dark_oak")
    b.set(-1, 1, 3, "stone_brick_stairs[facing=north]")
    b.set(1, 1, 3, "stone_brick_stairs[facing=north]")
    for x in (-4, 4):
        b.set(x, 1, 3, "stone_brick_wall")
        post(b, x, 2, 3, 3, "stripped_dark_oak_log[axis=y]")
        b.set(x, 4, 3, "lantern")
    for (x, z) in ((-3, -3), (3, -3), (-4, -2), (4, -2)):
        b.set(x, 0, z, "moss_block")
        b.set(x, 1, z, b._pick([("azure_bluet", 1), ("fern", 2), ("blue_orchid", 1)]))
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_quay_capstan", "harbour",
       "Quay-edge working corner: a spoked capstan on a stone drum, twin iron bollards with chain leads running "
       "out to the edge, a coiled chain, the tally-keeper's lectern with a lamp and a barrel of pitch.",
       "Aim 1 block in from the quay edge.", group="accent")
def quay_capstan():
    b = Build("ae_prop_quay_capstan", seed=341)
    for x in range(-3, 4):
        for z in range(-2, 3):
            b.set(x, 0, z, b._pick(QUAY_STONE) if abs(x) < 3 else b._pick(PAVERS))
    for x in range(-3, 4):
        b.set(x, 0, 2, "polished_andesite")
    # capstan
    for (x, z) in ((0, 0),):
        b.set(x, 1, z, "polished_andesite")
        b.set(x, 2, z, "stripped_spruce_log[axis=y]")
        b.set(x, 3, z, "stripped_spruce_log[axis=y]")
        b.set(x, 4, z, "spruce_slab[type=bottom]")
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(dx, 1, dz, f"polished_andesite_stairs[facing={'west' if dx > 0 else 'east' if dx < 0 else 'north' if dz > 0 else 'south'}]")
        b.set(dx, 3, dz, "spruce_fence")
        b.set(2 * dx, 3, 2 * dz, "spruce_fence") if (dx, dz) != (0, 1) else None
    b.set(0, 2, 1, "chain[axis=z]")
    # bollards
    for x in (-3, 3):
        b.set(x, 1, 1, "polished_blackstone_wall")
        b.set(x, 2, 1, "polished_blackstone_button[face=floor,facing=north]")
        b.set(x, 1, 2, "chain[axis=z]")
    # chain lead from the capstan to the edge
    b.set(0, 1, 2, "chain[axis=z]")
    # coil of chain
    for (x, z, ax) in ((-2, -1, "x"), (-2, 0, "z"), (-2, -2, "x")):
        b.set(x, 1, z, f"chain[axis={ax}]")
    # tally-keeper
    b.lectern(2, 1, -1, "south")
    b.container(3, 1, -1, "barrel[facing=up]")
    b.set(3, 2, -1, "lantern")
    b.container(3, 1, -2, "barrel[facing=north]")
    b.set(2, 1, -2, "spruce_stairs[facing=south]")
    b.set(-3, 1, -2, "composter[level=0]")
    b.set(-3, 2, -2, "white_carpet")
    tidy(b)
    b.anchor = (0, 0, 1)
    return b
