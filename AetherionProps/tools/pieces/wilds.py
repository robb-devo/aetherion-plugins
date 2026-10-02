"""Landscape pieces: stone arch bridge, cliff overlook, forest ruin gate, ranger hut."""
from aeprops.core import Build
from aeprops.style import (STONE_BRICK, COBBLE, PAVERS, PATH, PLASTER, SOIL, soil_patch, plant_on, hang_lantern,
                           pebbles, post, beam, corbel, gable_roof, gable_wall_tri, strut_down, leaf_clump, small_oak,
                           Noise2)
from . import piece, center_anchor

RUIN = [("mossy_stone_bricks", 5), ("stone_bricks", 3), ("cracked_stone_bricks", 3), ("mossy_cobblestone", 1),
        ("cobblestone", 1)]
LOW_MOSS = [("mossy_stone_bricks", 4), ("mossy_cobblestone", 2), ("stone_bricks", 1), ("cracked_stone_bricks", 1)]


# ---------------------------------------------------------------------------------------------
@piece("ae_stone_bridge", "anywhere (streams, gullies, gaps between island lobes)",
       "Hump-backed mossy stone footbridge: stair-rounded arch with a keystone lantern, slab ramps you can walk "
       "without jumping, wall parapets, lantern pillars at both ends, vines hanging over the arch.",
       "15 long (east-west) x 5 wide; y=0 is bank level at both ends, the arch opening goes 4 below. "
       "Best over a 5-7 wide dip; on flat ground it still works as a hump bridge.", group="mid")
def stone_bridge():
    b = Build("ae_stone_bridge", seed=141)
    T = {0: 6, 1: 6, 2: 5, 3: 4, 4: 3, 5: 2, 6: 1, 7: 0}      # walking surface, half-blocks above ground
    O = {0: 1, 1: 1, 2: 0, 3: -1, 4: -3}                      # top of the arch opening
    deck = [("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1), ("andesite", 1)]
    slabs = ["stone_brick_slab", "mossy_stone_brick_slab"]
    for x in range(-7, 8):
        ax = abs(x)
        t = T[ax]
        full, odd = t // 2, t % 2
        for z in range(-2, 3):
            parapet = abs(z) == 2
            y_top = full + (1 if (odd and parapet) else 0)
            for y in range(-4, y_top + 1):
                if ax in O and y <= O[ax]:
                    continue
                mix = LOW_MOSS if y < 0 else (deck if (y == y_top and not parapet) else STONE_BRICK)
                b.set(x, y, z, b._pick(mix))
            if odd and not parapet:
                b.set(x, full + 1, z, f"{b.rng.choice(slabs)}[type=bottom]")
            if parapet and ax < 7:
                b.set(x, y_top + 1, z, "mossy_stone_brick_wall" if b.rng.random() < 0.4 else "stone_brick_wall")
        # rounded soffit
        if ax in (2, 3, 4):
            f = "east" if x > 0 else "west"
            for z in range(-2, 3):
                b.set(x, O[ax] + 1, z, f"stone_brick_stairs[facing={f},half=top]")
    for z in range(-2, 3):
        b.set(0, 2, z, "chiseled_stone_bricks")
    hang_lantern(b, 0, 2, 0, chain=0)
    # end pillars with lanterns
    for x in (-7, 7):
        for z in (-2, 2):
            b.set(x, 1, z, "stone_bricks")
            b.set(x, 2, z, "chiseled_stone_bricks")
            b.set(x, 3, z, "lantern")
    # vines over the arch faces
    for x in (-3, -1, 1, 2, 3):
        for zf, face in ((3, "north"), (-3, "south")):
            if b.rng.random() < 0.7:
                zb = 2 if zf > 0 else -2
                ytop = max(y for (xx, y, zz) in b.blocks if xx == x and zz == zb)
                n = b.rng.randint(2, 4)
                for k in range(n):
                    b.vine_on(x, ytop - 1 - k, zf, [face])
    for (x, z) in ((-5, -1), (4, 1), (6, 0), (-6, 1)):
        top = max(y for (xx, y, zz) in b.blocks if xx == x and zz == z)
        b.set(x, top + 1, z, "moss_carpet")
    b.meta["context"] = None
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_cliff_overlook", "anywhere at an island edge (views over the void / sea)",
       "Cantilevered spruce viewing deck: struts and posts down the cliff face, railing with lantern posts, "
       "azalea-draped pergola over a bench, brass spyglass on a tripod, cartography table.",
       "Paste with the south edge hanging past the cliff lip: the deck's back 2 rows sit on land, the front 5 "
       "overhang; posts and struts run 4-5 blocks down the face.", group="mid")
def cliff_overlook():
    b = Build("ae_cliff_overlook", seed=151)
    # deck
    for x in range(-4, 5):
        for z in range(-2, 5):
            border = abs(x) == 4 or z in (-2, 4)
            b.set(x, 1, z, ("stripped_spruce_wood[axis=z]" if abs(x) == 4 else "stripped_spruce_wood[axis=x]")
                  if border else ("spruce_planks" if (x + 2 * z) % 4 else "stripped_spruce_wood[axis=z]"))
    for x in (-3, 0, 3):
        for z in range(0, 5):
            b.set(x, 0, z, "stripped_spruce_wood[axis=z]")
        strut_down(b, (x, -1, 4), "north", 4)
        post(b, x, -5, -1, 0, "stripped_spruce_log[axis=y]")
    for x in (-4, 4):
        post(b, x, -4, 0, 0, "stripped_spruce_log[axis=y]")
    for x in range(-4, 5):
        b.set(x, 0, -2, b._pick(COBBLE))
        b.set(x, 0, -1, b._pick(COBBLE)) if abs(x) in (4, 0) else None
    # steps up from the land side
    for x in (-1, 0, 1):
        b.set(x, 1, -3, "spruce_stairs[facing=south]")
        b.set(x, 0, -3, b._pick(PAVERS))
    # railing + lantern posts
    for z in range(-1, 5):
        for x in (-4, 4):
            b.set(x, 2, z, "spruce_fence")
    for x in range(-4, 5):
        b.set(x, 2, 4, "spruce_fence")
    for (x, z) in ((-4, 4), (4, 4), (-4, -2), (4, -2), (0, 4)):
        b.set(x, 2, z, "stripped_spruce_log[axis=y]")
        b.set(x, 3, z, "lantern")
    for x in (-3, -2, 2, 3):
        b.set(x, 2, -2, "spruce_fence")
    # pergola
    for (x, z) in ((-3, -2), (3, -2), (-3, 1), (3, 1)):
        post(b, x, 2, 4, z, "stripped_spruce_log[axis=y]")
    beam(b, (-3, 5, -2), (3, 5, -2))
    beam(b, (-3, 5, 1), (3, 5, 1))
    for x in (-1, 1):
        beam(b, (x, 5, -1), (x, 5, 0))
    for x in (-3, 3):
        beam(b, (x, 5, -1), (x, 5, 0))
    leaf_clump(b, 0, 6, -1, 4, 0, 2, mix=[("azalea_leaves", 3), ("flowering_azalea_leaves", 2)], density=0.8)
    for (x, z, face) in ((-2, 2, "north"), (2, 2, "north"), (0, 2, "north"), (-2, -3, "south"), (2, -3, "south"),
                         (-4, 0, "east"), (4, -1, "west")):
        for k in range(b.rng.randint(1, 3)):
            if not b.has(x, 5 - k, z):
                b.vine_on(x, 5 - k, z, [face])
    # bench, spyglass, map table
    for x in (-1, 0, 1):
        b.set(x, 2, -1, "spruce_stairs[facing=north]")
    b.set(-2, 2, -1, "spruce_trapdoor[facing=west,half=bottom,open=true]")
    b.set(2, 2, -1, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    b.set(2, 2, 3, "spruce_fence")
    b.set(2, 3, 3, "lightning_rod[facing=south]")
    b.set(-2, 2, 3, "cartography_table")
    b.set(-3, 2, 3, "potted_azure_bluet")
    soil_patch(b, 0, -4, 5, 2, y=0, plants=0.45, flowers=0.1)
    b.meta["context"] = ("cliff", 0)
    b.anchor = (0, 1, -1)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_forest_ruin_gate", "foraging (forest paths, the edge of a grove)",
       "Two crumbling mossy towers and a half-collapsed arch over a broken paved path: rubble, a fallen pillar, "
       "a lantern still hanging from the keystone, roots and vines, a young oak growing out of the east tower.",
       "15 wide x 8 deep (+ rubble). The path runs north-south through the arch; walk-through clearance 5.",
       group="landmark")
def forest_ruin_gate():
    b = Build("ae_forest_ruin_gate", seed=161)
    rng = b.rng
    nz = Noise2(161)
    towers = [(-7, -4, 9), (4, 7, 11)]
    for (xa, xb, H) in towers:
        # plinth course
        for x in range(xa - 1, xb + 2):
            for z in range(-3, 3):
                b.set(x, 0, z, b._pick(LOW_MOSS))
                b.set(x, -1, z, b._pick(COBBLE))
        for x in range(xa, xb + 1):
            for z in range(-2, 2):
                outer = x in (xa, xb) and (x < 0) == (x == xa)
                h = H - int(2.5 * (nz(x * 0.7, z * 0.7) + 1)) - (3 if (xa > 0 and x >= 6) else 0)
                h -= 1 if outer else 0
                for y in range(1, h + 1):
                    b.set(x, y, z, b._pick(LOW_MOSS) if y < 3 else b._pick(RUIN))
                if rng.random() < 0.35:
                    b.set(x, h + 1, z, rng.choice(["mossy_stone_brick_slab[type=bottom]", "stone_brick_slab[type=bottom]",
                                                   "moss_carpet"]))
        for x in range(xa - 1, xb + 2):
            for (z, f) in ((-3, "south"), (2, "north")):
                b.set(x, 1, z, f"mossy_stone_brick_stairs[facing={f}]" if rng.random() < 0.5 else f"stone_brick_stairs[facing={f}]")
        for z in range(-3, 3):
            for (x, f) in ((xa - 1, "east"), (xb + 1, "west")):
                b.set(x, 1, z, f"stone_brick_stairs[facing={f}]")
        # slit niche on the south face
        sx = xa + 1 if xa < 0 else xb - 1
        b.set(sx, 5, 1, "air")
        b.set(sx, 6, 1, "air")
        b.set(sx, 4, 1, "stone_brick_slab[type=top]")
    # arch between towers (z=-1..0)
    O = {0: 5, 1: 5, 2: 4, 3: 3}
    for x in range(-3, 4):
        for z in (-1, 0):
            for y in range(O[abs(x)] + 1, 9):
                b.set(x, y, z, b._pick(RUIN))
        if abs(x) in (2, 3):
            f = "east" if x > 0 else "west"
            for z in (-1, 0):
                b.set(x, O[abs(x)] + 1, z, f"stone_brick_stairs[facing={f},half=top]")
    for z in (-1, 0):
        b.set(0, 6, z, "chiseled_stone_bricks")
        for x in range(-3, 4):
            b.set(x, 9, z, "stone_brick_slab[type=bottom]") if rng.random() < 0.6 else None
    # collapse on the east half of the arch
    for (x, y) in ((2, 8), (3, 8), (3, 7), (2, 7), (3, 6), (1, 9), (2, 9), (3, 9), (3, 5)):
        for z in (-1, 0):
            b.clear(x, y, z)
    b.set(2, 6, -1, "mossy_stone_brick_stairs[facing=east,half=top]")
    b.set(3, 4, 0, "air")
    hang_lantern(b, 0, 6, 0, chain=0)
    b.set(0, 5, -1, "hanging_roots")
    b.set(-1, 6, 1, "air")
    # path through the gate
    for x in range(-2, 3):
        for z in range(-5, 5):
            if rng.random() < 0.85:
                b.set(x, 0, z, b._pick(PATH) if rng.random() < 0.55 else b._pick(PAVERS))
    # rubble from the collapse + a fallen pillar
    rubble = [("mossy_stone_brick_slab[type=bottom]", 3), ("stone_brick_slab[type=bottom]", 2),
              ("cobblestone_slab[type=bottom]", 1), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1),
              ("stone_brick_stairs[facing=east]", 1), ("mossy_cobblestone_wall", 1)]
    for (x, z) in ((2, 1), (3, 2), (3, 3), (2, 3), (1, 2), (3, -3), (2, -2), (8, 3), (9, 0), (-8, 3)):
        b.set(x, 1, z, b._pick(rubble))
    b.set(3, 2, 2, "mossy_stone_brick_slab[type=bottom]")
    for i, st in enumerate(["chiseled_stone_bricks", "stone_bricks", "mossy_stone_bricks", "stone_brick_wall"]):
        b.set(-7 + i, 1, 4, st)
    b.set(-6, 2, 4, "moss_carpet")
    # overgrowth
    top_w = max(y for (x, y, z) in b.blocks if -7 <= x <= -4 and -2 <= z <= 1)
    leaf_clump(b, -6, top_w, -1, 2, 1, 2, density=0.75)
    ex, ez = 5, -1
    top_e = max(y for (x, y, z) in b.blocks if x == ex and z == ez)
    small_oak(b, ex, top_e + 1, ez, h=3, r=2)
    leaf_clump(b, -1, 9, -1, 2, 0, 1, mix=[("azalea_leaves", 3), ("flowering_azalea_leaves", 1)], density=0.6)
    for (x, z, face) in ((-7, 2, "north"), (-5, 2, "north"), (4, 2, "north"), (7, 2, "north"), (-3, 1, "north"),
                         (-8, -1, "east"), (8, 0, "west"), (-6, -3, "south"), (6, -3, "south")):
        zb = z - 1 if face == "north" else z + 1 if face == "south" else z
        xb = x + 1 if face == "east" else x - 1 if face == "west" else x
        tops = [y for (xx, y, zz) in b.blocks if xx == xb and zz == zb]
        if not tops:
            continue
        ytop = max(tops)
        for k in range(rng.randint(3, 6)):
            y = ytop - 1 - k
            if y < 2 or b.has(x, y, z):
                break
            b.vine_on(x, y, z, [face])
    soil_patch(b, 0, 0, 9, 5, y=0, plants=0.5, flowers=0.1)
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_ranger_hut", "foraging (forest clearings, beside the tree pads)",
       "Log cabin of horizontal spruce logs on a stone plinth: brown-wool-backed shingle gable running out over "
       "a railed porch, stone chimney with a smoking fire, shuttered windows, firewood under the back eave, stump "
       "and woodpile, furnished inside.",
       "7x6 cabin + 3-deep porch (11x15 with roof). Door faces south; the chimney smokes (lit campfire).",
       group="mid")
def ranger_hut():
    b = Build("ae_ranger_hut", seed=171)
    b.fill(-3, -1, -4, 3, -1, 1, COBBLE)
    for x in range(-3, 4):
        for z in range(-4, 2):
            edge = abs(x) == 3 or z in (-4, 1)
            b.set(x, 0, z, b._pick(COBBLE))
            b.set(x, 1, z, b._pick(STONE_BRICK) if edge else "spruce_planks")
    # log walls y=2..5
    for y in range(2, 6):
        for x in range(-2, 3):
            b.set(x, y, -4, "spruce_log[axis=x]")
            b.set(x, y, 1, "spruce_log[axis=x]")
        for z in range(-3, 1):
            b.set(-3, y, z, "spruce_log[axis=z]")
            b.set(3, y, z, "spruce_log[axis=z]")
        for (x, z) in ((-3, -4), (3, -4), (-3, 1), (3, 1)):
            b.set(x, y, z, "stripped_oak_log[axis=y]")
    b.door(0, 2, 1, "spruce", "north")
    for x in (-2, 2):
        b.set(x, 3, 1, "glass_pane")
        b.set(x, 2, 2, "spruce_trapdoor[facing=south,half=top,open=false]")
        b.set(x, 3, 2, "potted_red_tulip" if x < 0 else "potted_azure_bluet")
    for z in (-2, -1):
        b.set(3, 3, z, "glass_pane")
        b.set(-3, 3, z, "glass_pane") if z == -1 else None
    b.set(4, 3, -3, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    b.set(4, 3, 0, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    b.set(4, 2, -2, "spruce_trapdoor[facing=east,half=top,open=false]")
    b.set(4, 2, -1, "spruce_trapdoor[facing=east,half=top,open=false]")
    # porch
    for x in range(-3, 4):
        for z in range(2, 5):
            b.set(x, 1, z, "stripped_spruce_wood[axis=x]" if z == 4 else "spruce_planks")
        b.set(x, 0, 4, b._pick(COBBLE)) if abs(x) == 3 else None
    for x in (-3, 3):
        b.set(x, 0, 2, b._pick(COBBLE))
        post(b, x, 2, 5, 4, "stripped_spruce_log[axis=y]")
        for z in (2, 3):
            b.set(x, 2, z, "spruce_fence")
    beam(b, (-2, 5, 4), (2, 5, 4))
    for x in (-2, 2):
        b.set(x, 2, 4, "spruce_fence")
    for x in (-1, 0, 1):
        b.set(x, 1, 5, "spruce_stairs[facing=north]")
    b.set(-2, 2, 2, "spruce_stairs[facing=north]")
    b.set(-1, 2, 2, "spruce_stairs[facing=north]")
    b.container(2, 2, 3, "barrel[facing=up]")
    b.set(2, 3, 3, "potted_fern")
    # roof over cabin + porch
    gable_roof(b, -3, 3, -4, 4, 6, ridge_axis="z", stairs=[("spruce_stairs", 3), ("dark_oak_stairs", 1)],
               fill="brown_wool", slab="spruce_slab", overhang=1, edge_stairs="dark_oak_stairs")
    for zz in (1, -4):
        gable_wall_tri(b, zz, -2, 2, 6, "z", PLASTER)
        b.set(0, 6, zz, "stripped_spruce_log[axis=y]")
        b.set(0, 7, zz, "spruce_trapdoor[facing=south,half=bottom,open=true]" if zz == 1 else "stripped_spruce_log[axis=y]")
    hang_lantern(b, 0, 9, 3, chain=3)
    hang_lantern(b, 0, 9, -1, chain=3)
    # chimney (west)
    for y in range(0, 10):
        for z in (-3, -2):
            b.set(-4, y, z, b._pick(STONE_BRICK) if y > 2 else b._pick(COBBLE))
    for z in (-3, -2):
        for y in (0, 1, 2):
            b.set(-5, y, z, b._pick(COBBLE))
        b.set(-5, 3, z, "stone_brick_stairs[facing=east]")
    b.campfire(-4, 10, -3, lit=True, facing="south")
    b.set(-4, 10, -2, "stone_brick_slab[type=bottom]")
    # firewood under the back eave, stump + woodpile east
    for x in range(-2, 2):
        for y in (1, 2):
            b.set(x, y, -5, "oak_log[axis=z]")
        b.set(x, 3, -5, "oak_log[axis=z]") if x in (-1, 0) else None
    b.set(5, 1, -4, "oak_log[axis=y]")
    b.set(5, 2, -4, "oak_pressure_plate")
    b.set(5, 1, -3, "oak_log[axis=x]")
    b.set(5, 1, -2, "oak_log[axis=x]")
    b.set(5, 2, -3, "oak_log[axis=x]")
    # interior
    b.bed(-2, 2, -2, "green", "north")
    b.set(2, 2, -3, "crafting_table")
    b.container(2, 2, -2, "barrel[facing=west]")
    b.set(1, 2, 0, "spruce_fence")
    b.set(1, 3, 0, "spruce_pressure_plate")
    b.set(0, 2, -1, "brown_carpet")
    b.set(0, 2, -2, "brown_carpet")
    b.set(-2, 2, 0, "furnace[facing=east,lit=false]")
    soil_patch(b, 0, -1, 7, 7, y=0, plants=0.45, flowers=0.08)
    center_anchor(b)
    return b
