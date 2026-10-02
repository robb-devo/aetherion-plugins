"""Harbour pieces (Anker Harbour / fishing pads): market stall, dock crane, watchtower, boathouse."""
from aeprops.core import Build
from aeprops.style import (STONE_BRICK, COBBLE, PAVERS, PLASTER, soil_patch, hang_lantern, pebbles, post, beam,
                           corbel, gable_roof, hip_roof, thatch_roof, gable_wall_tri, brace, strut_down,
                           fill_under_roof)
from . import piece, center_anchor


# ---------------------------------------------------------------------------------------------
@piece("ae_market_stall", "harbour / anywhere (plazas, pad landings, along the quay)",
       "Striped canvas stall on a spruce frame: stepped carpet canopy with fascia, barrel counter with wares, "
       "back rack, hanging shop sign, produce and sacks at the side.",
       "7x5 (+ produce). Counter faces south; the shopkeeper stands behind it. Great in a row of 2-3 with //rotate 180 for variety.",
       group="mid")
def market_stall():
    b = Build("ae_market_stall", seed=101)
    b.fill(-3, 0, -2, 3, 0, 0, "spruce_planks")
    for x in range(-3, 4):
        b.set(x, 0, -2, "stripped_spruce_wood[axis=x]")
        b.set(x, 0, 1, b._pick(PAVERS))
        b.set(x, 0, 2, b._pick(PAVERS)) if b.rng.random() < 0.7 else None
    for x in (-3, 3):
        post(b, x, 1, 4, -2)
        post(b, x, 1, 3, 1)
    beam(b, (-2, 4, -2), (2, 4, -2))
    beam(b, (-2, 3, 1), (2, 3, 1))
    stripe = {x: ("brown" if x % 2 else "white") for x in range(-3, 4)}
    for x in range(-3, 4):
        b.set(x, 5, -2, f"{stripe[x]}_carpet")
        b.set(x, 4, -1, "spruce_trapdoor[facing=north,half=top,open=false]")
        b.set(x, 5, -1, f"{stripe[x]}_carpet")
        if x not in (-3, 3):
            b.set(x, 3, 0, "spruce_trapdoor[facing=north,half=top,open=false]")
        else:
            b.set(x, 3, 0, "stripped_spruce_wood[axis=z]")
        b.set(x, 4, 0, f"{stripe[x]}_carpet")
        b.set(x, 4, 1, f"{stripe[x]}_carpet")
        b.set(x, 3, 2, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    # counter
    counter = ["barrel[facing=south]", "composter[level=0]", "barrel[facing=up]", "composter[level=0]", "barrel[facing=south]"]
    for i, st in enumerate(counter):
        x = i - 2
        if "barrel" in st:
            b.container(x, 1, 0, st)
        else:
            b.set(x, 1, 0, st)
            b.set(x, 1, 1, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    b.set(-2, 2, 0, "sea_pickle[pickles=3,waterlogged=false]")
    b.set(-1, 2, 0, "potted_azalea_bush")
    b.pot(0, 2, 0, "south", ["minecraft:brick", "minecraft:angler_pottery_sherd", "minecraft:brick", "minecraft:brick"])
    b.set(1, 2, 0, "potted_blue_orchid")
    b.set(2, 2, 0, "lantern")
    # back rack
    for x in range(-2, 3):
        if x % 2 == 0:
            b.container(x, 1, -2, "barrel[facing=south]")
        else:
            b.set(x, 1, -2, "hay_block[axis=y]")
        b.set(x, 2, -2, "spruce_trapdoor[facing=north,half=top,open=false]")
    b.set(-1, 3, -2, "flower_pot")
    b.set(1, 3, -2, "potted_fern")
    b.sign(0, 2, 1, "spruce_hanging_sign[rotation=0,attached=false]", ["", "~ Wares ~", "", ""])
    # produce at the side
    b.set(4, 1, -1, "hay_block[axis=y]")
    b.set(4, 2, -1, "pumpkin")
    b.set(4, 1, 0, "white_wool")
    b.set(4, 1, 1, "melon")
    b.set(-4, 1, -1, "composter[level=0]")
    b.set(-4, 1, 0, "brown_wool")
    b.set(-4, 2, -1, "brown_carpet")
    for (x, z) in ((4, -1), (4, 0), (4, 1), (-4, -1), (-4, 0)):
        b.set(x, 0, z, b._pick(PAVERS))
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_dock_crane", "harbour (quay edge, pier heads) / fishing docks",
       "Stone pier head with a spruce jib crane: braced mast, counterweight, grindstone pulley, hoisted cargo "
       "pallet on a chain, winch drum, bollards and a sea ladder.",
       "y=0 is quay level; the pier runs 6 blocks down into the water. Jib reaches 7 blocks south over the water.",
       group="mid")
def dock_crane():
    b = Build("ae_dock_crane", seed=111)
    for x in range(-2, 3):
        for z in range(-2, 3):
            for y in range(-6, 0):
                edge = max(abs(x), abs(z)) == 2
                if edge or y == -1:
                    b.set(x, y, z, b._pick([("mossy_stone_bricks", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 1)])
                          if y < -3 else b._pick(STONE_BRICK))
            edge = max(abs(x), abs(z)) == 2
            b.set(x, 0, z, "polished_andesite" if edge else b._pick(PAVERS))
    # mast with flared foot
    post(b, 0, 1, 9, -1)
    b.set(-1, 1, -1, "spruce_stairs[facing=east]")
    b.set(1, 1, -1, "spruce_stairs[facing=west]")
    b.set(0, 1, -2, "spruce_stairs[facing=south]")
    b.set(0, 1, 0, "spruce_stairs[facing=north]")
    # jib
    for z in range(-4, 7):
        b.set(0, 10, z, "stripped_spruce_wood[axis=z]")
    b.set(0, 11, -1, "stripped_spruce_log[axis=y]")
    b.set(0, 12, -1, "spruce_fence")
    strut_down(b, (0, 9, 2), "north", 3)
    b.set(0, 11, 5, "spruce_fence"); b.set(0, 11, -3, "spruce_fence")
    # pulley + hoisted pallet
    b.set(0, 9, 6, "grindstone[face=ceiling,facing=north]")
    for y in range(4, 9):
        b.set(0, y, 6, "chain[axis=y]")
    for x in (-1, 0, 1):
        b.set(x, 2, 6, "spruce_trapdoor[facing=north,half=top,open=false]")
    b.container(-1, 3, 6, "barrel[facing=east]")
    b.set(0, 3, 6, "white_wool")
    b.container(1, 3, 6, "barrel[facing=up]")
    # counterweight
    b.set(0, 9, -4, "stone_bricks")
    b.set(0, 8, -4, "cobblestone")
    b.set(0, 9, -3, "chain[axis=y]")
    hang_lantern(b, 0, 10, 1, chain=0)
    # winch drum
    for x in (-2, 2):
        post(b, x, 1, 2, 1, "stripped_spruce_log[axis=y]")
    b.fill(-1, 2, 1, 1, 2, 1, "stripped_spruce_log[axis=x]")
    b.set(-1, 1, 1, "spruce_trapdoor[facing=south,half=bottom,open=false]")
    # bollards, crates, ladder
    for (x, z) in ((-2, 2), (2, -2)):
        b.set(x, 1, z, "cobblestone_wall")
    b.container(-2, 1, -2, "barrel[facing=up]")
    b.set(-1, 1, -2, "composter[level=0]")
    b.set(-2, 2, -2, "lantern")
    for y in range(-3, 1):
        b.set(3, y, 0, "ladder[facing=east]")
    b.meta["context"] = ("water", -1)
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_harbour_watchtower", "harbour (quay corners, headlands, the end of a breakwater)",
       "Stone-footed harbour watch: battered stone base with door and slits, dark-oak timber stage with plaster "
       "panels, corbelled lookout gallery, open cabin with tide bell, blackstone hip roof crowned by a signal fire.",
       "9x9 base, 23 tall (+2 footing). The signal fire is a lit campfire - visible from across the harbour.",
       group="landmark")
def harbour_watchtower():
    b = Build("ae_harbour_watchtower", seed=121)
    b.fill(-4, -2, -4, 4, -1, 4, COBBLE)
    b.fill(-4, 0, -4, 4, 0, 4, STONE_BRICK)
    for i in range(-4, 5):
        b.set(i, 1, -4, "stone_brick_stairs[facing=south]")
        b.set(i, 1, 4, "stone_brick_stairs[facing=north]")
        b.set(-4, 1, i, "stone_brick_stairs[facing=east]")
        b.set(4, 1, i, "stone_brick_stairs[facing=west]")
    # stone base
    for y in range(1, 7):
        for i in range(-3, 4):
            for (x, z) in ((i, -3), (i, 3), (-3, i), (3, i)):
                corner = abs(x) == 3 and abs(z) == 3
                b.set(x, y, z, ("polished_andesite" if y % 2 else "stone_bricks") if corner else b._pick(STONE_BRICK))
    b.fill(-2, 0, -2, 2, 0, 2, "spruce_planks")
    b.door(0, 1, 3, "dark_oak", "north")
    b.set(0, 3, 3, "chiseled_stone_bricks")
    for (x, z) in ((0, -3), (-3, 0), (3, 0)):
        b.set(x, 3, z, "iron_bars"); b.set(x, 4, z, "iron_bars")
    for sx in (-2, 2):
        b.set(sx, 1, 4, "stone_brick_wall")
        b.set(sx, 2, 4, "lantern")
    b.sign(1, 2, 4, "dark_oak_wall_sign[facing=south]", ["", "HARBOUR", "WATCH", ""])
    # corbel ring
    for i in range(-4, 5):
        b.set(i, 6, -4, "stone_brick_stairs[facing=south,half=top]")
        b.set(i, 6, 4, "stone_brick_stairs[facing=north,half=top]")
        b.set(-4, 6, i, "stone_brick_stairs[facing=east,half=top]")
        b.set(4, 6, i, "stone_brick_stairs[facing=west,half=top]")
    # timber stage y=7..13
    b.fill(-3, 7, -3, 3, 7, 3, "dark_oak_planks")
    for i in range(-4, 5):
        for (x, z) in ((i, -4), (i, 4), (-4, i), (4, i)):
            b.set(x, 7, z, "dark_oak_slab[type=bottom]")
    posts = [(-3, -3), (3, -3), (-3, 3), (3, 3), (0, -3), (0, 3), (-3, 0), (3, 0)]
    for (x, z) in posts:
        post(b, x, 8, 13, z, "stripped_dark_oak_log[axis=y]")
    for y in (10, 13):
        for i in range(-2, 3):
            if i == 0:
                continue
            b.set(i, y, -3, "stripped_dark_oak_wood[axis=x]")
            b.set(i, y, 3, "stripped_dark_oak_wood[axis=x]")
            b.set(-3, y, i, "stripped_dark_oak_wood[axis=z]")
            b.set(3, y, i, "stripped_dark_oak_wood[axis=z]")
    plaster = [("white_wool", 3), ("diorite", 2), ("polished_diorite", 1), ("white_concrete_powder", 1)]
    for y in (8, 9, 11, 12):
        for i in (-2, -1, 1, 2):
            for (x, z) in ((i, -3), (i, 3), (-3, i), (3, i)):
                b.set(x, y, z, b._pick(plaster))
    for (x, z) in ((-1, 3), (1, 3), (-3, -1), (3, 1), (1, -3), (-1, -3), (-3, 1), (3, -1)):
        b.set(x, 11, z, "gray_stained_glass_pane")
    for (x, z) in ((2, 3), (-2, 3)):
        b.set(x, 8, z, "gray_stained_glass_pane")
    # floor 1 inside stone top (y=7 planks) + ladder shaft
    for y in range(1, 15):
        b.set(0, y, -2, "ladder[facing=south]")
    # gallery y=14
    for x in range(-4, 5):
        for z in range(-4, 5):
            b.set(x, 14, z, "dark_oak_planks" if max(abs(x), abs(z)) < 4 else "stripped_dark_oak_wood[axis=x]"
                  if abs(z) == 4 else "stripped_dark_oak_wood[axis=z]")
    b.set(0, 14, -2, "ladder[facing=south]")
    for i in range(-4, 5):
        b.set(i, 13, -4, "dark_oak_stairs[facing=south,half=top]")
        b.set(i, 13, 4, "dark_oak_stairs[facing=north,half=top]")
        b.set(-4, 13, i, "dark_oak_stairs[facing=east,half=top]")
        b.set(4, 13, i, "dark_oak_stairs[facing=west,half=top]")
    for i in range(-4, 5):
        for (x, z) in ((i, -4), (i, 4), (-4, i), (4, i)):
            b.set(x, 15, z, "dark_oak_fence")
    for (x, z) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        b.set(x, 15, z, "stripped_dark_oak_log[axis=y]")
        b.set(x, 16, z, "lantern")
    # lookout cabin
    for (x, z) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        post(b, x, 15, 17, z, "stripped_dark_oak_log[axis=y]")
    for i in (-1, 0, 1):
        for (x, z) in ((i, -2), (i, 2), (-2, i), (2, i)):
            if (x, z) == (0, -2):
                continue
            b.set(x, 15, z, "dark_oak_planks")
            b.set(x, 16, z, "dark_oak_trapdoor[facing=north,half=bottom,open=false]")
    for i in range(-2, 3):
        b.set(i, 18, -2, "stripped_dark_oak_wood[axis=x]")
        b.set(i, 18, 2, "stripped_dark_oak_wood[axis=x]")
        b.set(-2, 18, i, "stripped_dark_oak_wood[axis=z]")
        b.set(2, 18, i, "stripped_dark_oak_wood[axis=z]")
    beam(b, (-1, 18, 0), (1, 18, 0), "stripped_dark_oak_wood")
    b.bell(0, 17, 0, "north", "ceiling")
    hip_roof(b, -3, 3, -3, 3, 19, stairs="blackstone_stairs", fill="blackstone", slab="blackstone_slab")
    b.campfire(0, 22, 0, lit=True, facing="south")
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_boathouse", "fishing / harbour (shoreline: back on land, bay over water)",
       "Blocky-Verse-style boathouse on stilts: swooping hay thatch with brown-wool rims, timber frame with plaster "
       "and wool bands, open boat bay with a moored plank rowboat, hoist beam in the gable, side jetty with lamp.",
       "y=0 = water surface. Back door (north) meets the shore; stilts run 5 below the waterline. 13x12 house + jetty.",
       group="landmark")
def boathouse():
    b = Build("ae_boathouse", seed=131)
    X1, X2, Z1, Z2 = -6, 6, -6, 5
    grid_x, grid_z = (-6, -3, 3, 6), (-6, -3, 0, 3, 5)
    # stilts + posts
    for x in grid_x:
        for z in grid_z:
            post(b, x, -5, 5, z, "stripped_spruce_log[axis=y]")
    # decks at y=1
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            bay = -2 <= x <= 2 and z >= -3
            if bay or b.has(x, 1, z):
                continue
            b.set(x, 1, z, "spruce_planks" if (x + z) % 5 else "stripped_spruce_wood[axis=z]")
    for z in range(-3, Z2 + 1):
        for x in (-3, 3):
            if not b.has(x, 1, z) or "planks" in b.get(x, 1, z):
                b.set(x, 1, z, "stripped_spruce_wood[axis=z]")
    for x in range(-2, 3):
        b.set(x, 1, -4, "stripped_spruce_wood[axis=x]")
    # under-deck struts
    for z in (-3, 0, 3):
        for x in (-6, 6):
            strut_down(b, (x, 0, z + (1 if z < 3 else -1)), "north" if z < 3 else "south", 2)
    # walls
    def wall_line(pts, axis):
        for (x, z) in pts:
            if b.has(x, 2, z) and "log" in b.get(x, 2, z):
                continue
            b.set(x, 2, z, f"stripped_spruce_wood[axis={axis}]")
            b.set(x, 3, z, b._pick(PLASTER))
            b.set(x, 4, z, b._pick(PLASTER))
            b.set(x, 5, z, f"stripped_spruce_wood[axis={axis}]")
    wall_line([(x, Z1) for x in range(X1, X2 + 1)], "x")
    wall_line([(X1, z) for z in range(Z1, Z2 + 1)], "z")
    wall_line([(X2, z) for z in range(Z1, Z2 + 1)], "z")
    wall_line([(x, Z2) for x in list(range(X1, -2)) + list(range(3, X2 + 1))], "x")
    # brown wool band under the plate (Blocky Verse fishing trim)
    for x in range(X1 + 1, X2):
        if not (-2 <= x <= 2):
            b.set(x, 4, Z2, "brown_wool")
        b.set(x, 4, Z1, "brown_wool")
    for z in range(Z1 + 1, Z2):
        if z not in grid_z:
            b.set(X1, 4, z, "brown_wool")
            b.set(X2, 4, z, "brown_wool")
    # windows with shutters on the side walls
    for z in (-2, 1):
        for x, f_out in ((X1, "west"), (X2, "east")):
            b.set(x, 3, z, "light_gray_stained_glass_pane")
            b.set(x, 3, z + 1, "light_gray_stained_glass_pane")
    b.door(0, 2, Z1, "spruce", "south")
    # boat opening: big front beam + corbels, tie beams across the bay
    beam(b, (-3, 6, Z2), (3, 6, Z2))
    corbel(b, -2, 5, Z2, "west")
    corbel(b, 2, 5, Z2, "east")
    for z in (0, 3):
        beam(b, (-5, 6, z), (5, 6, z))
    for x in range(X1, X2 + 1):
        b.set(x, 6, Z1, "stripped_spruce_wood[axis=x]")
    for z in range(Z1, Z2 + 1):
        b.set(X1, 6, z, "stripped_spruce_wood[axis=z]")
        b.set(X2, 6, z, "stripped_spruce_wood[axis=z]")
    # thatch roof, then gable walls filled exactly up to its underside
    tops = thatch_roof(b, X1, X2, Z1, Z2, 6, ridge_axis="z", overhang=2,
                       profile=[0, 1, 2, 4, 6, 8, 10, 12, 14], rim_mix=[("brown_wool", 3), ("mud_bricks", 1)],
                       moss=0.07)
    for zz in (Z1, Z2):
        fill_under_roof(b, "x", zz, X1 + 1, X2 - 1, 7, PLASTER, trim=[("brown_wool", 1)])
    # ridge pennant over the front gable
    ridge_top = tops.get((0, Z2 + 1), 13)
    for y in range(ridge_top + 1, ridge_top + 3):
        b.set(0, y, Z2 + 1, "spruce_fence")
    b.banner(0, ridge_top + 3, Z2 + 1, "red_banner[rotation=4]")
    # gable window + hoist beam poking out under the ridge
    for x in (-1, 1):
        b.set(x, 8, Z2, "light_gray_stained_glass_pane")
    b.set(0, 8, Z2, "stripped_spruce_log[axis=y]")
    for z in range(Z2 - 1, Z2 + 4):
        b.set(0, 9, z, "stripped_spruce_wood[axis=z]")
    for y in range(5, 9):
        b.set(0, y, Z2 + 3, "chain[axis=y]")
    b.container(0, 4, Z2 + 3, "barrel[facing=up]")
    # rowboat in the bay (water level y=0)
    for z in range(-1, 3):
        b.set(0, 0, z, "spruce_slab[type=bottom]")
        b.set(-1, 0, z, "spruce_stairs[facing=west]")
        b.set(1, 0, z, "spruce_stairs[facing=east]")
    b.set(0, 0, 3, "spruce_stairs[facing=south]")
    b.set(0, 0, -2, "spruce_stairs[facing=north]")
    b.set(0, 1, 1, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(-2, 1, 0, "spruce_fence")
    b.set(0, 1, 3, "spruce_fence")
    b.set(2, 1, -2, "chain[axis=x]")
    # lanterns over the bay
    hang_lantern(b, 0, 6, 0, chain=0)
    hang_lantern(b, -4, 6, 3, chain=0)
    hang_lantern(b, 4, 6, 3, chain=0)
    # inside clutter
    b.container(-5, 2, -5, "barrel[facing=up]")
    b.container(-4, 2, -5, "barrel[facing=south]")
    b.set(-5, 3, -5, "white_carpet")
    b.set(5, 2, -5, "composter[level=0]")
    b.set(4, 2, -5, "hay_block[axis=y]")
    b.set(5, 2, -4, "water_cauldron[level=3]")
    for (x, y, z) in ((-5, 4, -1), (-5, 4, 2), (5, 4, -1)):
        b.set(x, y, z, "cobweb")
    b.set(-5, 2, 4, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(5, 2, 4, "dried_kelp_block")
    # side jetty (east) z = 6..10
    for z in range(Z2 + 1, Z2 + 6):
        for x in (4, 5):
            b.set(x, 1, z, "spruce_planks" if z % 2 else "stripped_spruce_wood[axis=z]")
        if z in (Z2 + 3, Z2 + 5):
            for x in (3, 6):
                post(b, x, -4, 1, z, "stripped_spruce_log[axis=y]")
                b.set(x, 2, z, "spruce_fence")
    b.set(6, 3, Z2 + 5, "spruce_fence")
    b.set(6, 4, Z2 + 5, "spruce_fence")
    b.set(5, 4, Z2 + 5, "spruce_fence")
    b.set(5, 3, Z2 + 5, "lantern[hanging=true]")
    b.meta["context"] = ("water", 0)
    center_anchor(b)
    return b
