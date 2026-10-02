"""Small accent props (drop into 3x3 .. 9x9 gaps)."""
from aeprops.core import Build
from aeprops.style import (STONE_BRICK, COBBLE, ROCK, SOIL, PATH, PAVERS, PLASTER, soil_patch, plant_on,
                           hang_lantern, pebbles, crate, post, beam, corbel, gable_roof)
from . import piece, center_anchor


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_lantern_post", "harbour / anywhere",
       "Dark-oak harbour lamp: stepped stone plinth, twin bracket arms, lanterns on chains, little shingle cap.",
       "1x1 post on a 3x3 plinth; drop along quays, paths and bridge ends. Arms run east-west - //rotate 90 to run them along a path.")
def lantern_post():
    b = Build("ae_prop_lantern_post", seed=11)
    b.fill(-1, 0, -1, 1, 0, 1, PAVERS)
    b.set(0, -1, 0, "cobblestone")
    # stepped plinth: ring of stairs rising to the post
    for x in (-1, 0, 1):
        b.set(x, 1, -1, "stone_brick_stairs[facing=south]")
        b.set(x, 1, 1, "stone_brick_stairs[facing=north]")
    b.set(-1, 1, 0, "stone_brick_stairs[facing=east]")
    b.set(1, 1, 0, "stone_brick_stairs[facing=west]")
    b.set(0, 1, 0, "chiseled_stone_bricks")
    b.set(0, 2, 0, "stone_brick_wall")
    post(b, 0, 3, 8, 0, "stripped_dark_oak_log[axis=y]")
    # bracket arms
    for sx in (-1, 1):
        f = "west" if sx == 1 else "east"
        b.set(sx, 7, 0, "stripped_dark_oak_wood[axis=x]")
        b.set(2 * sx, 7, 0, "dark_oak_fence")
        b.set(sx, 6, 0, f"dark_oak_stairs[facing={f},half=top]")
        hang_lantern(b, 2 * sx, 7, 0, chain=1)
    # cap: thin trapdoor hat with a slab crown
    for (dx, dz) in ((0, -1), (0, 1), (-1, 0), (1, 0), (-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(dx, 9, dz, "dark_oak_trapdoor[facing=north,half=bottom,open=false]")
    b.set(0, 9, 0, "dark_oak_slab[type=bottom]")
    b.set(0, 8, 1, "dark_oak_button[face=wall,facing=south]")
    b.set(0, 8, -1, "dark_oak_button[face=wall,facing=north]")
    # nails / plaque detail on the post
    b.set(0, 4, 1, "dark_oak_button[face=wall,facing=south]")
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_notice_board", "anywhere (village paths, harbour, pad landings)",
       "Shingle-roofed notice board with birch-sign 'papers', lantern under the eave, barrel and crate at its feet.",
       "Reads from the south. Signs are waxed (not editable in-game); edit text in the schem or re-place signs if you want quest text.")
def notice_board():
    b = Build("ae_prop_notice_board", seed=5)
    for x in range(-2, 3):
        b.set(x, 0, 1, b._pick(PATH))
    soil_patch(b, 0, 0, 3, 2, y=0, plants=0.5)
    for sx in (-2, 2):
        b.set(sx, 0, 0, "cobblestone")
        post(b, sx, 1, 4, 0)
    # board: rails + plank face
    beam(b, (-1, 1, 0), (1, 1, 0))
    beam(b, (-1, 4, 0), (1, 4, 0))
    b.fill(-1, 2, 0, 1, 3, 0, "spruce_planks")
    notes = [
        ((-1, 3), "birch", ["~ NOTICE ~", "Guild seeks", "steady hands.", "Ask within."]),
        ((0, 3), "oak", ["LOST", "one grey cat,", "answers to", "'Pebble'"]),
        ((1, 3), "birch", ["", "Mind the", "tide bell.", ""]),
        ((-1, 2), "spruce", ["Fresh cod", "at the dock", "every dawn", ""]),
        ((1, 2), "birch", ["Ore wanted", "iron & copper", "fair coin", "- the Smithy"]),
    ]
    for (x, y), wood, lines in notes:
        b.sign(x, y, 1, f"{wood}_wall_sign[facing=south]", lines)
    b.set(0, 2, 1, "spruce_button[face=wall,facing=south]")
    # roof
    gable_roof(b, -2, 2, 0, 0, 5, ridge_axis="x", stairs="spruce_stairs", fill="stripped_spruce_wood[axis=x]",
               slab="spruce_slab", overhang=1, edge_stairs="dark_oak_stairs")
    hang_lantern(b, 0, 5, 1, chain=0)
    # feet clutter
    b.container(2, 1, 1, "barrel[facing=up]")
    b.set(2, 2, 1, "potted_fern")
    b.set(-2, 1, 1, "composter[level=0]")
    b.set(-3, 1, 1, "spruce_trapdoor[facing=west,half=bottom,open=false]")
    b.set(-2, 1, -1, "spruce_trapdoor[facing=north,half=bottom,open=true]")
    pebbles(b, [(3, 1), (-3, 0)])
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_cargo_stack", "harbour (quays, dock ends, warehouse doors)",
       "Stepped dockside cargo: slatted crates, barrels on their sides, wool sacks, hay, tarp and amphorae.",
       "6x4 footprint, 3 high at the back. Place with the tall side against a wall or the water.")
def cargo_stack():
    b = Build("ae_prop_cargo_stack", seed=21)
    # pallets flush with the dock/ground
    b.fill(-3, 0, -2, 2, 0, 1, [("spruce_planks", 3), ("stripped_spruce_wood[axis=x]", 1)])
    # back row: 3 high
    col = {(-3, -2): 3, (-2, -2): 3, (-1, -2): 2, (0, -2): 3, (1, -2): 2, (2, -2): 1,
           (-3, -1): 2, (-2, -1): 2, (-1, -1): 2, (0, -1): 1, (1, -1): 1, (2, -1): 1,
           (-3, 0): 1, (-2, 0): 1, (0, 0): 1}
    kinds = ["composter[level=0]", "barrel[facing=east]", "white_wool", "hay_block[axis=y]",
             "barrel[facing=up]", "brown_wool", "stripped_spruce_wood[axis=y]", "composter[level=0]"]
    for (x, z), h in col.items():
        for y in range(1, h + 1):
            k = kinds[(x * 7 + z * 3 + y * 5) % len(kinds)]
            if "barrel" in k:
                b.container(x, y, z, k)
            else:
                b.set(x, y, z, k)
    # tarp over part of the pile + lashing
    for (x, z) in ((-3, -2), (-2, -2), (-3, -1)):
        top = max(y for (xx, y, zz) in b.blocks if xx == x and zz == z)
        b.set(x, top + 1, z, "brown_carpet")
    b.set(0, 4, -2, "white_carpet")
    b.set(-1, 3, -1, "brown_carpet")
    b.set(-2, 2, 0, "decorated_pot[facing=south]", be={"id": "minecraft:decorated_pot",
                                                       "sherds": ["minecraft:brick"] * 4})
    b.pot(1, 1, 0, "south")
    b.set(1, 2, -1, "lantern")
    b.set(2, 1, 0, "spruce_trapdoor[facing=south,half=bottom,open=false]")
    b.set(-1, 1, 1, "spruce_slab[type=bottom]")
    b.set(0, 1, 1, "chain[axis=x]")
    b.set(2, 2, -2, "chain[axis=z]")
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_fish_rack", "fishing / harbour beach",
       "Kelp-and-net drying rack: timber frame hung with dried seaweed and nets, salt barrels, water trough, gutting bench.",
       "7x5 footprint. Built on a sand/moss skirt so it sits well on beaches; nets are cobwebs (slow movement, like the real thing).")
def fish_rack():
    b = Build("ae_prop_fish_rack", seed=31)
    for x in range(-4, 5):
        for z in range(-2, 3):
            d = (x / 4.6) ** 2 + (z / 2.8) ** 2
            if d <= 1.0 + b.rng.uniform(-0.25, 0.1):
                b.set(x, 0, z, b._pick([("sand", 4), ("suspicious_sand", 2), ("coarse_dirt", 1), ("dirt_path", 1)]))
    # frame: two end bents joined by beams, fence ridge on top
    for x in (-3, 3):
        for z in (-1, 1):
            post(b, x, 1, 3, z, "stripped_spruce_log[axis=y]")
        b.set(x, 4, 0, "stripped_spruce_wood[axis=z]")
        b.set(x, 5, 0, "spruce_fence")
    for z in (-1, 1):
        beam(b, (-3, 4, z), (3, 4, z), "stripped_spruce_wood")
    for x in range(-2, 3):
        b.set(x, 5, 0, "spruce_fence")
    b.set(0, 3, -1, "spruce_trapdoor[facing=north,half=top,open=true]")
    # dried kelp / seaweed strands hanging from the beams (cave vines, no berries)
    for (x, z, n) in ((-2, -1, 2), (-1, -1, 1), (1, -1, 2), (2, -1, 1), (-2, 1, 1), (2, 1, 2), (-1, 1, 2)):
        for k in range(n):
            last = k == n - 1
            b.set(x, 3 - k, z, "cave_vines[berries=false]" if last else "cave_vines_plant[berries=false]")
    # nets
    for (x, y, z) in ((0, 4, 0), (-1, 4, 0), (1, 4, 0), (0, 3, 0), (1, 3, 1)):
        if not b.has(x, y, z):
            b.set(x, y, z, "cobweb")
    # work area
    b.set(-4, 1, 1, "water_cauldron[level=3]")
    b.container(-4, 1, 0, "barrel[facing=up]")
    b.set(-4, 2, 0, "white_carpet")
    b.container(4, 1, -1, "barrel[facing=north]")
    b.set(4, 1, 0, "stripped_spruce_log[axis=y]")
    b.set(4, 2, 0, "spruce_pressure_plate")
    b.set(4, 1, 1, "spruce_trapdoor[facing=east,half=top,open=false]")
    b.set(0, 1, 2, "dried_kelp_block")
    b.set(1, 1, 2, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(-2, 1, 2, "sea_pickle[pickles=3,waterlogged=false]")
    pebbles(b, [(3, 2), (-3, -2)])
    center_anchor(b)
    return b
