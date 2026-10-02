"""More accent props: mining heap, shrine, well, campsite."""
from aeprops.core import Build
from aeprops.style import (STONE_BRICK, COBBLE, PAVERS, soil_patch, hang_lantern, pebbles, post, beam, gable_roof)
from . import piece, center_anchor


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_ore_heap", "mining (yards, shaft mouths, cart tracks)",
       "Spoil heap of tuff/cobble/gravel with ore faces, rail spur ending in a tub, sieve bench and lamp stake.",
       "8x6 footprint. Rails are real rails on a gravel bed - rotate so the spur points at your track or mine mouth.")
def ore_heap():
    b = Build("ae_prop_ore_heap", seed=41)
    rng = b.rng
    ores = [("coal_ore", 3), ("iron_ore", 3), ("copper_ore", 3), ("raw_iron_block", 1), ("raw_copper_block", 1),
            ("gold_ore", 1), ("deepslate_iron_ore", 1)]
    fill = [("cobblestone", 4), ("tuff", 3), ("gravel", 3), ("andesite", 2), ("mossy_cobblestone", 1)]
    slabs = ["cobblestone_slab", "tuff_slab", "andesite_slab"]
    cx, cz = 1, -1
    for x in range(-3, 6):
        for z in range(-4, 3):
            d = ((x - cx) / 3.4) ** 2 + ((z - cz) / 2.7) ** 2
            if d > 1.0 + rng.uniform(-0.15, 0.1):
                continue
            hh = max(1, round(7.2 * (1 - min(d, 1.0)) ** 0.8 + rng.uniform(-0.6, 0.6)))
            full = hh // 2
            b.set(x, 0, z, b._pick(fill))
            for y in range(1, full + 1):
                surface = y == full and hh % 2 == 0
                b.set(x, y, z, b._pick(ores) if surface and rng.random() < 0.35 else b._pick(fill))
            if hh % 2:
                b.set(x, full + 1, z, f"{rng.choice(slabs)}[type=bottom]")
    # rail spur on a gravel bed, ending in an ore tub
    for z in range(1, 5):
        b.set(-2, 0, z, b._pick([("gravel", 2), ("cobblestone", 1)]))
    b.set(-2, 1, 1, "cauldron")
    for z in range(2, 5):
        b.set(-2, 1, z, "rail[shape=north_south]")
    b.set(-3, 0, 1, "gravel")
    b.set(-3, 1, 1, "stripped_spruce_log[axis=x]")
    # sieve bench: barrel under a hopper, trapdoor chute toward the heap
    b.set(-4, 0, -1, "cobblestone")
    b.set(-4, 0, -2, "cobblestone")
    b.set(-4, 0, 0, "cobblestone")
    b.container(-4, 1, -1, "barrel[facing=up]")
    b.set(-4, 2, -1, "hopper[facing=down,enabled=true]")
    b.set(-4, 1, -2, "stripped_spruce_log[axis=y]")
    b.set(-4, 2, -2, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(-3, 2, -1, "spruce_trapdoor[facing=east,half=top,open=false]")
    b.set(-4, 1, 0, "grindstone[face=floor,facing=east]")
    # lamp stake
    b.set(5, 0, 2, "cobblestone")
    post(b, 5, 1, 2, 2, "stripped_spruce_log[axis=y]")
    b.set(5, 3, 2, "lantern")
    for (x, z) in ((0, 3), (3, 3), (-3, 3), (6, 0), (4, 3)):
        b.set(x, 0, z, "gravel")
    pebbles(b, [(0, 3), (3, 3), (-3, 3), (6, 0), (4, 3)])
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_wayside_shrine", "foraging / anywhere (path junctions, glades)",
       "Mossy roadside shrine: stone plinth, arched candle niche, dark tile roof, hanging lantern, flowers and vines.",
       "5x4 plinth. Face it toward the path; candles are lit (light level ~9).")
def wayside_shrine():
    b = Build("ae_prop_wayside_shrine", seed=51)
    b.fill(-2, 0, -2, 2, 0, 1, STONE_BRICK)
    b.fill(-1, -1, -2, 1, -1, 0, COBBLE)
    for x in (-1, 0, 1):
        b.set(x, 1, 1, "stone_brick_stairs[facing=north]")
    b.fill(-1, 1, -2, 1, 3, -1, STONE_BRICK)
    b.set(0, 1, -1, "chiseled_stone_bricks")
    b.set(0, 2, -2, "chiseled_stone_bricks")
    b.set(0, 3, -1, "stone_brick_stairs[facing=north,half=top]")
    b.set(0, 2, -1, "white_candle[candles=3,lit=true]")
    # flanking posts with candles
    for sx in (-2, 2):
        b.set(sx, 1, -1, "mossy_stone_brick_wall")
        b.set(sx, 2, -1, "candle[candles=1,lit=true]")
        b.set(sx, 1, -2, "mossy_stone_brick_wall")
    gable_roof(b, -1, 1, -2, -1, 4, ridge_axis="x", stairs="deepslate_tile_stairs", fill="deepslate_tiles",
               slab="deepslate_tile_slab", overhang=1, edge_stairs="spruce_stairs")
    hang_lantern(b, 0, 4, 0, chain=0)
    # offerings / greenery
    b.set(-2, 1, 0, "potted_azure_bluet")
    b.set(2, 1, 0, "potted_fern")
    b.pot(1, 1, 0, "south", ["minecraft:brick", "minecraft:heart_pottery_sherd", "minecraft:brick", "minecraft:brick"])
    b.vine_on(-2, 3, -2, ["east"])
    b.vine_on(-2, 2, -2, ["east"]) if not b.has(-2, 2, -2) else None
    b.vine_on(2, 3, -2, ["west"])
    soil_patch(b, 0, -1, 4, 3, y=0, plants=0.5, flowers=0.12)
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_well", "anywhere / farming (village squares, pad plazas)",
       "Round cobble well with water, windlass drum and crank, bucket on a chain, spruce gable roof and lantern.",
       "5x5 ring (7x7 incl. paving). Water sits flush with ground inside the ring; the schem includes a stone floor under it.")
def well():
    b = Build("ae_prop_well", seed=61)
    ring = [(x, z) for x in range(-2, 3) for z in range(-2, 3)
            if max(abs(x), abs(z)) == 2 and not (abs(x) == 2 and abs(z) == 2)]
    for x in range(-3, 4):
        for z in range(-3, 4):
            if max(abs(x), abs(z)) == 3 and (abs(x) + abs(z)) <= 5:
                b.set(x, 0, z, b._pick(PAVERS))
    b.fill(-1, -1, -1, 1, -1, 1, "mossy_cobblestone")
    for (x, z) in ring:
        b.set(x, -1, z, "cobblestone")
        b.set(x, 0, z, b._pick(COBBLE))
        b.set(x, 1, z, b._pick(COBBLE))
        b.set(x, 2, z, b._pick(STONE_BRICK))
    for (x, z) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        b.set(x, 0, z, b._pick(COBBLE))
        b.set(x, 1, z, "mossy_cobblestone_slab[type=bottom]")
    b.fill(-1, 0, -1, 1, 0, 1, "water[level=0]")
    # windlass
    for sx in (-2, 2):
        post(b, sx, 3, 5, 0)
    b.fill(-1, 4, 0, 1, 4, 0, "stripped_spruce_log[axis=x]")
    b.set(3, 4, 0, "spruce_fence")
    b.set(3, 4, 1, "spruce_fence")
    b.set(0, 3, 0, "chain[axis=y]")
    b.set(0, 2, 0, "chain[axis=y]")
    b.set(0, 1, 0, "cauldron")
    gable_roof(b, -2, 2, -1, 1, 6, ridge_axis="x", stairs="spruce_stairs", fill="spruce_planks",
               slab="spruce_slab", overhang=1, edge_stairs="dark_oak_stairs")
    beam(b, (-2, 6, 0), (2, 6, 0))
    hang_lantern(b, 1, 6, 0, chain=0)
    b.set(-3, 1, 2, "spruce_trapdoor[facing=west,half=bottom,open=true]")
    b.set(3, 1, -2, "composter[level=0]")
    soil_patch(b, 0, 0, 5, 5, y=0, plants=0.4, flowers=0.08)
    center_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_forest_campsite", "foraging / anywhere (forest clearings, overlooks)",
       "Ranger camp: canvas A-tent with bedroll, fire with spit, log benches, covered firewood, stump and pack.",
       "9x9. Tent opens south toward the fire. The campfire is lit (smoke + light); set lit=false in the schem for a cold camp.")
def forest_campsite():
    b = Build("ae_prop_forest_campsite", seed=71)
    for x in range(-1, 2):
        for z in range(-5, -1):
            b.set(x, 0, z, b._pick([("coarse_dirt", 2), ("rooted_dirt", 1), ("dirt", 1)]))
    b.set(1, 0, 1, "cobblestone")
    # tent: smooth-sandstone canvas A-frame, z=-5..-2
    for z in range(-5, -1):
        b.set(-2, 1, z, "smooth_sandstone_stairs[facing=east]")
        b.set(2, 1, z, "smooth_sandstone_stairs[facing=west]")
        b.set(-1, 2, z, "smooth_sandstone_stairs[facing=east]")
        b.set(1, 2, z, "smooth_sandstone_stairs[facing=west]")
        b.set(0, 3, z, "smooth_sandstone_slab[type=bottom]")
    b.fill(-1, 1, -5, 1, 1, -5, "smooth_sandstone")
    b.set(0, 2, -5, "smooth_sandstone")
    b.set(0, 1, -2, "spruce_fence")
    b.set(0, 2, -2, "spruce_fence")
    b.set(0, 3, -1, "spruce_fence")
    b.set(0, 3, -6, "spruce_fence")
    b.bed(0, 1, -3, "brown", "north")
    b.set(-1, 1, -4, "brown_carpet")
    b.set(-1, 1, -3, "brown_carpet")
    b.set(1, 1, -4, "lantern")
    for (x, z) in ((-3, -2), (3, -2), (-3, -5), (3, -5)):
        b.set(x, 1, z, "spruce_fence")
    # fire with spit
    b.campfire(1, 1, 1, lit=True, facing="south")
    b.set(0, 1, 1, "spruce_fence")
    b.set(2, 1, 1, "spruce_fence")
    b.fill(0, 2, 1, 2, 2, 1, "spruce_fence")
    # log benches
    for x in range(0, 3):
        b.set(x, 1, 3, "stripped_oak_log[axis=x]")
    b.set(3, 1, 0, "stripped_oak_log[axis=z]")
    b.set(3, 1, 1, "stripped_oak_log[axis=z]")
    # covered firewood
    for x in (-4, -3):
        for y in (1, 2):
            b.set(x, y, -3, "oak_log[axis=z]")
        b.set(x, 3, -3, "spruce_slab[type=bottom]")
    b.set(-4, 1, -4, "spruce_fence")
    b.set(-3, 1, -4, "oak_log[axis=x]")
    # stump + pack
    b.set(3, 1, -3, "oak_log[axis=y]")
    b.set(3, 2, -3, "oak_pressure_plate")
    b.container(-3, 1, 1, "barrel[facing=up]")
    b.set(-3, 2, 1, "brown_carpet")
    soil_patch(b, 0, -1, 5, 5, y=0, plants=0.45, flowers=0.1)
    pebbles(b, [(0, 0), (2, 0), (0, 2), (2, 2), (1, 2)])
    b.set(-2, 1, 2, "azalea") if b.get(-2, 0, 2) and "moss" in b.get(-2, 0, 2) else None
    center_anchor(b)
    return b
