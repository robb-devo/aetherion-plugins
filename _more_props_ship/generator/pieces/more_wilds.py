"""Second pass - foraging / forest pieces."""
import math

from aeprops.core import Build, bname
from aeprops.style import (STONE_BRICK, COBBLE, PATH, SOIL, PLASTER, soil_patch, hang_lantern, pebbles, post, beam,
                           corbel, gable_roof, gable_wall_tri, strut_down, leaf_clump, small_oak, small_spruce, Noise2,
                           GROUND_PLANTS, FLOWERS, brace)
from . import piece
from ._kit import (ground_anchor, boulder, plant, mushroom_ok, hang, disc, ring, FOREST_FLOOR, BOULDER, MOSS_TOP,
                   big_tree, fence_ring, wall_sign, corbel_ring, stairs_ring, tidy)

RUIN = [("mossy_stone_bricks", 5), ("stone_bricks", 3), ("cracked_stone_bricks", 3), ("mossy_cobblestone", 1),
        ("cobblestone", 1)]


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_fallen_log", "foraging",
       "Uprooted giant: a 5-wide hollow oak trunk lying east-west that you can walk through, its root plate torn "
       "up on end at the west with hanging roots over a muddy crater, moss and an azalea sprouting along the back, "
       "podzol mushrooms at the den mouth.",
       "Lies across your view; walk-through hollow.", group="accent")
def fallen_log():
    b = Build("ae_prop_fallen_log", seed=201)
    rng = b.rng
    X0, X1 = -5, 6            # trunk
    CY = 2.6                  # trunk axis height
    for (x, z) in disc(0, 0, 6):
        if abs(z) <= 4:
            b.set(x, 0, z, b._pick(FOREST_FLOOR))
    shell = set()
    for x in range(X0, X1 + 1):
        for y in range(0, 6):
            for z in range(-3, 4):
                d = (y - CY) ** 2 + z ** 2
                if d > 2.45 ** 2:
                    continue
                hollow = d <= 1.25 ** 2 and x >= -1
                if hollow:
                    b.clear(x, y, z) if y >= 1 else None
                    continue
                if y == 0:
                    continue
                shell.add((x, y, z))
                b.set(x, y, z, "oak_log[axis=x]" if rng.random() < 0.93 else "dark_oak_log[axis=x]")
    # jagged broken east end
    for (y, z) in ((5, 0), (4, 1), (4, 2), (3, 2), (5, -1)):
        b.clear(X1, y, z)
    b.set(X1 + 1, 1, -2, "oak_log[axis=x]")
    b.set(X1 + 1, 2, -2, "stripped_oak_log[axis=y]")
    b.set(X1 + 1, 3, -2, "oak_fence")
    b.set(X1 + 1, 1, 2, "stripped_oak_log[axis=y]")
    # inside: rotten heartwood floor
    for x in range(-1, X1 + 1):
        b.set(x, 1, 0, b._pick([("stripped_oak_wood[axis=x]", 2), ("rooted_dirt", 1), ("coarse_dirt", 1)]))
    # moss mantle along the top
    for x in range(X0, X1 + 1):
        for z in range(-2, 3):
            ys = [y for (xx, y, zz) in shell if xx == x and zz == z]
            if not ys:
                continue
            top = max(ys)
            r = rng.random()
            if r < 0.35:
                b.set(x, top, z, "moss_block")
                if rng.random() < 0.45 and not b.has(x, top + 1, z):
                    b.set(x, top + 1, z, b._pick([("moss_carpet", 3), ("fern", 2), ("short_grass", 1)]))
            elif r < 0.55 and not b.has(x, top + 1, z):
                b.set(x, top + 1, z, "moss_carpet")
    b.set(-3, 5, 0, "moss_block")
    b.set(-3, 6, 0, "flowering_azalea")
    b.set(2, 5, 0, "moss_block")
    b.set(2, 6, 0, "azalea")
    # branch stub with a clump
    b.set(0, 5, -2, "oak_log[axis=y]") if not b.has(0, 5, -2) else None
    b.set(0, 6, -2, "oak_log[axis=y]")
    b.set(0, 7, -3, "oak_log[axis=z]")
    leaf_clump(b, 0, 8, -3, 1, 0, 1, mix=[("oak_leaves", 3), ("azalea_leaves", 1)], density=0.85)
    # root plate on end at the west
    RX = X0 - 1
    plate = set()
    for z in range(-5, 6):
        for y in range(-1, 10):
            d = (z / 4.6) ** 2 + ((y - 3.2) / 4.4) ** 2
            if d <= 1.0 + rng.uniform(-0.14, 0.06):
                plate.add((y, z))
                b.set(RX, y, z, b._pick([("rooted_dirt", 4), ("coarse_dirt", 2), ("mangrove_roots", 3), ("dirt", 1),
                                         ("mud", 1)]))
    for (y, z) in plate:
        if rng.random() < 0.28 and y > 0:
            b.set(RX - 1, y, z, b._pick([("mangrove_roots", 3), ("rooted_dirt", 1)]))
        if rng.random() < 0.2 and y > 1 and not b.has(RX + 1, y, z):
            b.set(RX + 1, y, z, "mangrove_roots")
    # hanging roots under every overhang of the plate
    for x in (RX - 1, RX, RX + 1):
        for z in range(-5, 6):
            for y in range(9, 1, -1):
                if b.has(x, y, z) and not b.has(x, y - 1, z) and bname(b.get(x, y, z)) in (
                        "minecraft:rooted_dirt", "minecraft:mangrove_roots", "minecraft:dirt", "minecraft:coarse_dirt",
                        "minecraft:mud") and rng.random() < 0.55:
                    b.set(x, y - 1, z, "hanging_roots")
                    break
    # crater where the roots tore out (a dip, mud and a puddle-dark rim)
    for z in range(-3, 4):
        for x in (RX - 1, RX - 2, RX - 3):
            if abs(z) + (RX - x) <= 5:
                b.set(x, 0, z, b._pick([("mud", 2), ("coarse_dirt", 2), ("rooted_dirt", 1), ("packed_mud", 1)]))
                b.clear(x, 1, z) if (x, 1, z) != (RX - 1, 1, 0) else None
    for z in (-1, 0, 1):
        b.set(RX - 2, -1, z, "mud")
        b.set(RX - 2, 0, z, "mud")
    # den mouth + mushrooms on podzol
    for (x, z) in ((X1 + 1, 0), (X1 + 2, 0), (X1 + 1, 1), (X1 + 2, -1), (X1 + 1, -1)):
        b.set(x, 0, z, "podzol")
    b.set(X1 + 2, 1, 0, "brown_mushroom")
    b.set(X1 + 2, 1, -1, "red_mushroom")
    b.set(X1 + 1, 1, -1, "brown_mushroom")
    # undergrowth
    for (x, z) in disc(0, 0, 6):
        if abs(z) > 4 or b.has(x, 1, z):
            continue
        if rng.random() < 0.33:
            plant(b, x, 1, z, b._pick([("fern", 4), ("short_grass", 3), ("large_fern", 1), ("lily_of_the_valley", 1)]))
    for (x, z) in ((-2, 4), (3, -4), (1, 4), (5, 3)):
        b.set(x, 0, z, "podzol")
        b.clear(x, 1, z); b.clear(x, 2, z)
        b.set(x, 1, z, rng.choice(["brown_mushroom", "red_mushroom"]))
    tidy(b)
    ground_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_mossy_boulders", "anywhere",
       "Glacial erratic: a head-high split boulder with a young birch forcing up through the crack, two mossy "
       "cousins, lichen on the shady faces, ferns and bluets in the gaps. Grips the ground a block deep.",
       "Grips a block into the ground.", group="accent")
def mossy_boulders():
    b = Build("ae_prop_mossy_boulders", seed=211)
    rng = b.rng
    soil_patch(b, 0, 0, 5, 4, y=0, mix=[("moss_block", 3), ("grass_block", 3), ("coarse_dirt", 1), ("rooted_dirt", 1)],
               plants=0.0, flowers=0.0)
    big = boulder(b, -1, 2, 0, 3, 3, 2, seed=5, moss=0.5, bury=2, rough=0.18)
    # the split runs north-south through x=-1; a birch pushes up through it
    for y in range(1, 8):
        for z in range(-3, 4):
            if (-1, y, z) in big:
                b.clear(-1, y, z)
    for z in range(-2, 3):
        b.set(-1, 0, z, "moss_block")
    for y in range(1, 6):
        b.set(-1, y, 0, "birch_log[axis=y]")
    leaf_clump(b, -1, 6, 0, 2, 1, 2, mix=[("birch_leaves", 4), ("azalea_leaves", 1)], density=0.85)
    b.set(-1, 7, 0, "birch_leaves")
    b.set(-1, 1, 1, "fern")
    b.set(-1, 1, -1, "fern")
    boulder(b, 3, 1, 2, 1, 1, 1, seed=9, moss=0.5, rough=0.15)
    boulder(b, -5, 1, 2, 1, 0, 1, seed=13, moss=0.7, rough=0.1)
    # lichen on the shaded (north) faces
    for (x, y, z) in list(big):
        if b.has(x, y, z) and not b.has(x, y, z - 1) and y >= 1 and rng.random() < 0.25:
            b.set(x, y, z - 1, "glow_lichen[north=false,east=false,south=true,west=false,up=false,down=false]")
    for (x, z) in disc(0, 0, 5):
        if not b.has(x, 1, z) and rng.random() < 0.3:
            plant(b, x, 1, z, b._pick([("fern", 4), ("short_grass", 4), ("azure_bluet", 1), ("large_fern", 1)]))
    pebbles(b, [(5, 0), (-5, -1), (1, 3)])
    tidy(b)
    ground_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_forest_treehouse", "foraging",
       "Ranger lookout in a giant oak: a 3x3 trunk on a flared root plate, branching crown, a railed deck wrapped "
       "round the trunk on diagonal struts, a shingled hut with a green ranger banner, a ladder up the trunk to a "
       "hatch, a pulley basket hanging from a branch, lanterns on chains, firewood and a barrel at the roots.",
       "Hut door points the way you look.", group="landmark")
def forest_treehouse():
    b = Build("ae_forest_treehouse", seed=221)
    rng = b.rng
    for (x, z) in disc(0, 0, 7):
        b.set(x, 0, z, b._pick(FOREST_FLOOR))
    tips = big_tree(b, -1, 1, -1, h=21, trunk=3, wood="oak",
                    leaves=[("oak_leaves", 4), ("dark_oak_leaves", 2), ("azalea_leaves", 1),
                            ("flowering_azalea_leaves", 1)], seed=7, crown=6, branches=6)
    D = 9
    # clear foliage out of the deck + hut volume
    for x in range(-7, 8):
        for z in range(-7, 8):
            for y in range(D - 2, D + 8):
                st = b.get(x, y, z)
                if st and "leaves" in st and (y < D + 6 or x * x + z * z <= 30):
                    b.clear(x, y, z)
    deck = [(x, z) for (x, z) in disc(0, 0, 5.4)]
    for (x, z) in deck:
        if not (-1 <= x <= 1 and -1 <= z <= 1):
            b.set(x, D, z, "spruce_planks" if (x + z) % 4 else "stripped_spruce_wood[axis=x]")
    rim = ring(0, 0, 5.4)
    for (x, z) in rim:
        b.set(x, D, z, "stripped_spruce_wood[axis=x]" if abs(z) >= abs(x) else "stripped_spruce_wood[axis=z]")
        if not (z >= 4 and abs(x) <= 1):
            b.set(x, D + 1, z, "spruce_fence")
    # struts from the trunk to the deck rim
    for (d, sx, sz) in (("north", 0, -1), ("south", 0, 1), ("east", 1, 0), ("west", -1, 0)):
        for off in (-1, 1):
            px = sx * 5 + (off if sx == 0 else 0) * 2
            pz = sz * 5 + (off if sz == 0 else 0) * 2
            toward = {"north": "south", "south": "north", "east": "west", "west": "east"}[d]
            strut_down(b, (px, D - 1, pz), toward, 3)
    # hut on the south side of the trunk: x=-2..2, z=2..4
    HX1, HX2, HZ1, HZ2 = -2, 2, 2, 4
    for y in range(D + 1, D + 4):
        for x in range(HX1, HX2 + 1):
            for z in (HZ1, HZ2):
                b.set(x, y, z, "stripped_spruce_log[axis=y]" if x in (HX1, HX2) else "spruce_planks")
        for z in range(HZ1, HZ2 + 1):
            for x in (HX1, HX2):
                b.set(x, y, z, "stripped_spruce_log[axis=y]" if z in (HZ1, HZ2) else "spruce_planks")
    for x in range(HX1 + 1, HX2):
        for z in range(HZ1 + 1, HZ2):
            for y in range(D + 1, D + 4):
                b.clear(x, y, z)
    b.door(0, D + 1, HZ2, "spruce", "south")
    b.set(-2, D + 2, 3, "glass_pane"); b.set(2, D + 2, 3, "glass_pane")
    gable_roof(b, HX1, HX2, HZ1, HZ2, D + 4, ridge_axis="x", stairs=[("spruce_stairs", 3), ("dark_oak_stairs", 1)],
               fill="brown_wool", slab="spruce_slab", overhang=1, edge_stairs="dark_oak_stairs")
    for xx in (HX1, HX2):
        gable_wall_tri(b, xx, HZ1 + 1, HZ2 - 1, D + 4, "x", "spruce_planks")
    b.banner(1, D + 3, HZ2 + 1, "green_wall_banner[facing=south]")
    b.set(-1, D + 1, HZ2 + 1, "barrel[facing=up]", be={"id": "minecraft:barrel", "Items": []})
    b.set(-1, D + 2, HZ2 + 1, "lantern")
    # ladder up the trunk (north face) to a hatch
    for y in range(1, D):
        b.set(0, y, -2, "ladder[facing=north]")
    b.set(0, D, -2, "spruce_trapdoor[facing=north,half=top,open=true]")
    # lanterns on chains from branch tips
    for (x, y, z) in tips[:4]:
        if not b.has(x, y - 1, z) and not b.has(x, y - 2, z):
            hang(b, x, y, z, 1)
    # pulley basket from a branch on the east side
    b.set(6, D + 5, 0, "oak_log[axis=x]"); b.set(5, D + 5, 0, "oak_log[axis=x]"); b.set(4, D + 5, 0, "oak_log[axis=x]")
    b.set(6, D + 4, 0, "grindstone[face=ceiling,facing=north]")
    for y in range(4, D + 4):
        if not b.has(6, y, 0):
            b.set(6, y, 0, "chain[axis=y]")
    b.container(6, 3, 0, "barrel[facing=up]")
    # roots dressing
    for (x, z) in disc(0, 0, 7):
        if not b.has(x, 1, z) and rng.random() < 0.3:
            plant(b, x, 1, z, b._pick([("fern", 4), ("short_grass", 3), ("large_fern", 1), ("lily_of_the_valley", 1)]))
    for (x, z) in ((-4, 3), (-5, 2), (-4, 2)):
        b.set(x, 1, z, "oak_log[axis=z]")
    b.set(-4, 2, 2, "oak_log[axis=z]")
    b.container(4, 1, 4, "barrel[facing=up]")
    b.set(4, 1, 3, "composter[level=0]")
    for (x, z) in ((3, -4), (-3, -5), (5, -2)):
        b.set(x, 0, z, "podzol")
        b.clear(x, 1, z)
        b.set(x, 1, z, rng.choice(["brown_mushroom", "red_mushroom"]))
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_forest_standing_stones", "foraging",
       "An old stone ring in a flowered glade: eight leaning mossy menhirs (one pair lintelled into a trilithon), "
       "glow-lichen runes on their inner faces, a low altar at the centre where an amethyst cluster grows between "
       "lit candles. Soft magic, no glow-up.",
       "Aim at the glade centre; 15 across.", group="mid")
def forest_standing_stones():
    b = Build("ae_forest_standing_stones", seed=231)
    rng = b.rng
    import math as _m
    STONE = [("mossy_cobblestone", 3), ("stone", 3), ("andesite", 2), ("tuff", 2), ("mossy_stone_bricks", 1),
             ("cobblestone", 1)]
    soil_patch(b, 0, 0, 7, 7, y=0, mix=[("grass_block", 4), ("moss_block", 3), ("rooted_dirt", 1)], plants=0.0,
               flowers=0.0)
    for (x, z) in ring(0, 0, 4.2):
        b.set(x, 0, z, b._pick([("dirt_path", 3), ("coarse_dirt", 1), ("moss_block", 1)]))
    stones = []
    for i in range(8):
        a = i * _m.pi / 4 + 0.15
        x, z = round(_m.cos(a) * 6), round(_m.sin(a) * 6)
        h = rng.randint(4, 6) if i != 2 else 6
        stones.append((x, z, h, a))
    for (x, z, h, a) in stones:
        tang_x = abs(_m.sin(a)) > abs(_m.cos(a))
        cells = [(x, z), (x + 1, z) if tang_x else (x, z + 1)]
        lean = rng.choice([0, 0, 1])
        for (cx, cz) in cells:
            b.set(cx, 0, cz, "cobblestone")
            for y in range(1, h + 1):
                ox = (round(_m.cos(a)) if (lean and y >= h - 1) else 0)
                oz = (round(_m.sin(a)) if (lean and y >= h - 1) else 0)
                b.set(cx + ox, y, cz + oz, b._pick(STONE))
        top = cells[rng.randint(0, 1)]
        b.set(top[0], h + 1, top[1], rng.choice(["mossy_cobblestone_slab[type=bottom]", "stone_slab[type=bottom]",
                                                  "moss_carpet"]))
        # rune: glow lichen on the face toward the centre
        fx, fz = cells[0]
        ix, iz = (-1 if x > 0 else 1 if x < 0 else 0), (-1 if z > 0 else 1 if z < 0 else 0)
        face = {(1, 0): "west", (-1, 0): "east", (0, 1): "north", (0, -1): "south"}
        dirv = (ix, 0) if abs(x) >= abs(z) else (0, iz)
        if dirv in face:
            px, pz = fx + dirv[0], fz + dirv[1]
            for y in (2, 3):
                if not b.has(px, y, pz):
                    f = face[dirv]
                    b.set(px, y, pz, "glow_lichen[" + ",".join(
                        f"{d}={'true' if d == f else 'false'}" for d in ("north", "east", "south", "west")) +
                          ",up=false,down=false]")
    # trilithon: lintel across two neighbouring stones (north side)
    n1, n2 = stones[5], stones[6]
    ytop = max(n1[2], n2[2]) + 1
    for k in range(0, 5):
        t = k / 4
        lx = round(n1[0] + (n2[0] - n1[0]) * t)
        lz = round(n1[1] + (n2[1] - n1[1]) * t)
        b.set(lx, ytop, lz, b._pick([("mossy_stone_bricks", 2), ("stone", 2), ("andesite", 1)]))
    for (x, z, h, a) in (n1, n2):
        for y in range(h + 1, ytop):
            b.set(x, y, z, b._pick(STONE))
    # altar
    for x in (-1, 0, 1):
        b.set(x, 1, 0, "polished_andesite")
    b.set(-2, 1, 0, "polished_andesite_stairs[facing=east]")
    b.set(2, 1, 0, "polished_andesite_stairs[facing=west]")
    b.set(0, 1, -1, "chiseled_stone_bricks")
    b.set(0, 1, 1, "stone_brick_stairs[facing=north]")
    b.set(0, 2, 0, "amethyst_block")
    b.set(0, 3, 0, "amethyst_cluster[facing=up]")
    b.set(0, 2, -1, "white_candle[candles=3,lit=true]")
    b.set(-1, 2, 0, "purple_candle[candles=2,lit=true]")
    b.set(1, 2, 0, "white_candle[candles=1,lit=true]")
    # flowers
    for (x, z) in disc(0, 0, 7):
        if b.has(x, 1, z):
            continue
        r = rng.random()
        if r < 0.1:
            plant(b, x, 1, z, b._pick([("azure_bluet", 2), ("lily_of_the_valley", 2), ("oxeye_daisy", 1),
                                       ("cornflower", 1), ("allium", 1)]))
        elif r < 0.35:
            plant(b, x, 1, z, b._pick([("short_grass", 3), ("fern", 2)]))
        elif r < 0.4:
            plant(b, x, 1, z, "pink_petals[facing=north,flower_amount=3]")
    tidy(b)
    b.anchor = (0, 0, 0)
    return b
