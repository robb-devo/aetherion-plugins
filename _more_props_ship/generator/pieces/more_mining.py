"""Second pass - mining pieces (headframes, tipples, smoke, rails, damp stone)."""
from aeprops.core import Build, bname
from aeprops.style import (STONE_BRICK, COBBLE, DARK_ROCK, PATH, PAVERS, PLASTER, soil_patch, hang_lantern, pebbles,
                           post, beam, corbel, gable_roof, brace, strut_down, Noise2)
from . import piece
from ._kit import (ground_anchor, disc, ring, hang, fence_ring, wall_sign, tidy, clear_box, stairs_ring, corbel_ring,
                   boulder)

ORE_LOAD = [("coal_ore", 3), ("gravel", 2), ("iron_ore", 2), ("raw_iron_block", 1), ("cobblestone", 2),
            ("copper_ore", 1), ("tuff", 1)]
YARD = [("gravel", 3), ("cobblestone", 2), ("coarse_dirt", 2), ("tuff", 1), ("andesite", 1), ("packed_mud", 1)]


def tub(b: Build, x, y, z, load="coal_block"):
    """Ore tub = hopper body with a heaped load on top."""
    b.set(x, y, z, "hopper[facing=down,enabled=true]")
    if load:
        b.set(x, y + 1, z, load)


# ---------------------------------------------------------------------------------------------
@piece("ae_mine_tipple", "mining",
       "Timber ore tipple: a heaped ore bin on six legs straddling the rail line, a funnel chute over a waiting "
       "tub, a steep rail incline on a braced trestle climbing to the dump deck, a brake lever and winch drum, "
       "and a roofed headhouse on top. Coal-black yard with spill.",
       "Rails run across your view under the bin.", group="landmark")
def mine_tipple():
    b = Build("ae_mine_tipple", seed=501)
    rng = b.rng
    # yard
    for x in range(-8, 9):
        for z in range(-10, 9):
            if abs(x) <= 6 or abs(z - 4) <= 1:
                if abs(x) + max(0, -z - 6) + max(0, z - 7) <= 9 or abs(z - 4) <= 1:
                    b.set(x, 0, z, b._pick(YARD))
    for x in range(-8, 9):
        b.set(x, 0, 4, "gravel")
        b.set(x, 1, 4, "rail[shape=east_west]")
    for (x, z) in ((-5, 2), (-4, 6), (4, 3), (5, 5), (1, 7), (-1, 1)):
        b.set(x, 0, z, "black_concrete_powder")
    # legs (x = -2, 0, 2 at z = 2 and 6) - the track passes between them at z = 4
    for x in (-3, 3):
        for z in (2, 6):
            post(b, x, 1, 9, z, "stripped_spruce_log[axis=y]")
            b.set(x, 0, z, "cobblestone")
    # x-bracing on the long sides
    for z in (2, 6):
        for x in range(-2, 3):
            b.set(x, 3, z, "spruce_fence")
        b.set(-2, 2, z, "spruce_stairs[facing=west,half=top]")
        b.set(2, 2, z, "spruce_stairs[facing=east,half=top]")
    # bin: floor y=5, walls y=6..9 over x=-3..3, z=2..6
    for x in range(-3, 4):
        for z in range(2, 7):
            b.set(x, 5, z, "spruce_planks")
    corbel_ring(b, -3, 3, 2, 6, 4, "spruce_stairs")
    for y in range(6, 10):
        for x in range(-3, 4):
            for z in (2, 6):
                b.set(x, y, z, "stripped_spruce_log[axis=y]" if x in (-3, 3) else
                      ("stripped_dark_oak_wood[axis=x]" if y in (6, 9) else "spruce_planks"))
        for z in range(3, 6):
            for x in (-3, 3):
                b.set(x, y, z, "stripped_dark_oak_wood[axis=z]" if y in (6, 9) else "spruce_planks")
    for x in range(-2, 3):
        for z in range(3, 6):
            h = 9 - (abs(x) + abs(z - 4) > 2)
            for y in range(6, h + 1):
                b.set(x, y, z, b._pick(ORE_LOAD))
    b.set(0, 10, 4, "coal_ore"); b.set(-1, 10, 4, "gravel"); b.set(1, 10, 3, "cobblestone_slab[type=bottom]")
    # funnel chute over the track
    b.set(0, 5, 4, "hopper[facing=down,enabled=true]")
    b.set(0, 4, 4, "spruce_trapdoor[facing=south,half=top,open=true]")
    for (dx, dz, f) in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "south"), (0, 1, "north")):
        b.set(dx, 4, 4 + dz, f"spruce_stairs[facing={f},half=top]")
    tub(b, 0, 1, 4, "coal_block")
    tub(b, 5, 1, 4, "raw_iron_block")
    tub(b, 7, 1, 4, None)
    b.set(-8, 1, 4, "stripped_spruce_log[axis=x]")
    b.set(-8, 2, 4, "spruce_trapdoor[facing=west,half=bottom,open=false]")
    # dump deck on the north side at y = 9 (walk at 10)
    for x in range(-2, 3):
        for z in (0, 1):
            b.set(x, 9, z, "spruce_planks")
    for x in (-2, 2):
        b.set(x, 8, 0, "spruce_stairs[facing=south,half=top]")
        post(b, x, 1, 8, 0, "stripped_spruce_log[axis=y]")
        b.set(x, 0, 0, "cobblestone")
    for z in (0, 1):
        b.set(-2, 10, z, "spruce_fence")
        b.set(2, 10, z, "spruce_fence")
    b.set(0, 10, 0, "rail[shape=north_south]")
    b.set(0, 10, 1, "rail[shape=north_south]")
    b.set(0, 10, 2, "spruce_fence")                           # tipping stop
    b.set(-1, 10, 1, "lever[face=floor,facing=east,powered=false]")
    b.set(1, 10, 0, "stripped_spruce_log[axis=x]")             # winch drum
    b.set(1, 11, 0, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    # incline trestle: rails rise one per step from z = -9 (y 1) to z = -1 (y 9)
    for z in range(-9, 0):
        y = z + 10
        b.set(0, y - 1, z, "stripped_spruce_wood[axis=z]")
        b.set(0, y, z, "rail[shape=ascending_south]")
        b.set(-1, y - 1, z, "spruce_slab[type=top]")
        b.set(1, y - 1, z, "spruce_slab[type=top]")
        if z % 2 == 1 or z == -1:
            for x in (-1, 1):
                for yy in range(1, y - 1):
                    b.set(x, yy, z, "stripped_spruce_log[axis=y]")
                b.set(x, 0, z, "cobblestone")
    for z in range(-7, 0, 2):
        y = z + 10
        for yy in range(2, y - 2, 3):
            b.set(0, yy, z, "stripped_spruce_wood[axis=x]")
    b.set(0, 0, -10, "gravel")
    b.set(0, 1, -10, "rail[shape=north_south]")
    b.set(0, 0, -11, "gravel")
    b.set(0, 1, -11, "stripped_spruce_log[axis=x]")
    # headhouse roof over the bin
    for (x, z) in ((-3, 1), (3, 1), (-3, 7), (3, 7)):
        post(b, x, 10 if z == 1 else 10, 12, z, "stripped_spruce_log[axis=y]")
    for (x, z) in ((-3, 7), (3, 7)):
        post(b, x, 1, 9, z, "stripped_spruce_log[axis=y]")
        b.set(x, 0, z, "cobblestone")
    for (x, z) in ((-3, 1), (3, 1)):
        post(b, x, 1, 9, z, "stripped_spruce_log[axis=y]")
        b.set(x, 0, z, "cobblestone")
    for x in range(-3, 4):
        b.set(x, 12, 1, "stripped_spruce_wood[axis=x]")
        b.set(x, 12, 7, "stripped_spruce_wood[axis=x]")
    for z in range(1, 8):
        b.set(-3, 12, z, "stripped_spruce_wood[axis=z]")
        b.set(3, 12, z, "stripped_spruce_wood[axis=z]")
    gable_roof(b, -3, 3, 1, 7, 13, ridge_axis="z", stairs=[("spruce_stairs", 3), ("dark_oak_stairs", 1)],
               fill="spruce_planks", slab="spruce_slab", overhang=1, edge_stairs="dark_oak_stairs")
    for z in (1, 7):
        for x in range(-2, 3):
            for y in range(13, 13 + 3 - abs(x)):
                if not b.has(x, y, z):
                    b.set(x, y, z, "spruce_planks")
    b.clear(0, 13, 1); b.clear(0, 14, 1)
    hang(b, 0, 12, 4, 1)
    # sign + lamps
    wall_sign(b, 3, 3, 7, "south", ["", "TIPPLE", "~ No. 2 ~", ""])
    b.set(-4, 5, 2, "lantern")
    b.set(-4, 4, 2, "spruce_fence")
    b.set(-4, 3, 2, "spruce_fence")
    b.set(-4, 2, 2, "spruce_fence")
    b.set(-4, 1, 2, "cobblestone_wall")
    # ore spill + a shovel-leaning crate pile
    for (x, z, st) in ((1, 2, "gravel"), (-1, 6, "coal_ore"), (2, 7, "cobblestone_slab[type=bottom]"),
                       (-2, 7, "gravel"), (6, 6, "raw_iron_block"), (5, 6, "iron_ore"), (-6, 6, "coal_ore")):
        b.set(x, 1, z, st)
    b.container(-6, 1, 2, "barrel[facing=up]")
    b.set(-6, 2, 2, "lantern")
    b.set(-5, 1, 2, "composter[level=0]")
    tidy(b)
    b.anchor = (0, 0, 4)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_mine_smelter", "mining",
       "Stone blast furnace: a battered stone-and-tuff stack tapering to a smoking chimney, a glowing hearth arch "
       "with lit furnaces over magma, leather bellows at the tuyere, a timber charging ramp up to the mouth with "
       "an ore barrow, and a lean-to casting shed with ingot stacks, anvil, quench trough and charcoal heap.",
       "Hearth points the way you look.", group="landmark")
def mine_smelter():
    b = Build("ae_mine_smelter", seed=511)
    rng = b.rng
    STACK = [("stone_bricks", 4), ("tuff_bricks", 3), ("cracked_stone_bricks", 1), ("mossy_stone_bricks", 1)]
    for x in range(-7, 8):
        for z in range(-12, 9):
            if abs(x) + max(0, abs(z + 2) - 6) <= 9:
                b.set(x, 0, z, b._pick(YARD))
    # stack: 7x7 (y1-4) -> 5x5 (y5-9) -> 3x3 chimney (y10-16)
    tiers = [(3, 1, 4), (2, 5, 9), (1, 10, 19)]
    for (r, y0, y1) in tiers:
        for y in range(y0, y1 + 1):
            for x in range(-r, r + 1):
                for z in range(-r, r + 1):
                    shell = max(abs(x), abs(z)) == r
                    if shell:
                        corner = abs(x) == r and abs(z) == r
                        b.set(x, y, z, "polished_andesite" if (corner and y % 2) else b._pick(STACK))
                    else:
                        b.clear(x, y, z)
        if r > 1:
            stairs_ring(b, -r, r, -r, r, y1 + 1, "stone_brick_stairs")
            for x in range(-r + 1, r):
                for z in range(-r + 1, r):
                    if max(abs(x), abs(z)) == r - 1:
                        pass
    for y in (9, 15, 19):
        r = 2 if y == 9 else 1
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if max(abs(x), abs(z)) == r:
                    b.set(x, y, z, "deepslate_bricks")
    corbel_ring(b, -2, 2, -2, 2, 18, "deepslate_brick_stairs")
    for x in range(-2, 3):
        for z in range(-2, 3):
            if max(abs(x), abs(z)) == 2:
                b.set(x, 19, z, "deepslate_brick_slab[type=bottom]")
    b.campfire(0, 19, 0, lit=True)
    b.set(0, 18, 0, "magma_block")
    # hearth arch on the south face (z = 3)
    for x in (-1, 0, 1):
        for y in (1, 2):
            b.clear(x, y, 3)
    b.set(-1, 3, 3, "stone_brick_stairs[facing=east,half=top]")
    b.set(1, 3, 3, "stone_brick_stairs[facing=west,half=top]")
    b.set(0, 3, 3, "chiseled_stone_bricks")
    for (x, z) in ((-1, 2), (0, 2), (1, 2), (-1, 1), (0, 1), (1, 1)):
        b.set(x, 0, z, "magma_block")
    # glow that never goes out: lit campfires on a magma bed (furnaces would un-light without fuel)
    b.campfire(0, 1, 1, lit=True, facing="south")
    b.campfire(-1, 1, 2, lit=True, facing="south")
    b.set(1, 1, 1, "blast_furnace[facing=south,lit=false]")
    for x in (-2, 2):
        b.set(x, 1, 4, "cobblestone_wall")
        b.set(x, 2, 4, "lantern")
    # tuyere + bellows on the east face
    b.set(3, 2, 0, "iron_bars")
    b.set(4, 2, 0, "stripped_spruce_log[axis=x]")
    b.set(5, 1, 0, "brown_wool"); b.set(5, 2, 0, "brown_wool"); b.set(6, 1, 0, "brown_wool")
    b.set(5, 3, 0, "spruce_trapdoor[facing=east,half=bottom,open=false]")
    b.set(6, 2, 0, "spruce_trapdoor[facing=east,half=bottom,open=false]")
    b.set(5, 1, -1, "spruce_trapdoor[facing=north,half=bottom,open=true]")
    b.set(5, 1, 1, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    b.set(7, 1, 0, "spruce_fence"); b.set(7, 2, 0, "spruce_fence")
    # charging ramp from the north up to the 5x5 ledge (y = 10 walk)
    for i, z in enumerate(range(-11, -3)):
        y = i + 2
        for x in (-1, 0):
            b.set(x, y, z, "spruce_stairs[facing=south,half=bottom]")
            b.set(x, y - 1, z, "spruce_planks") if y - 1 >= 1 else None
        if i % 2 == 0:
            for x in (-2, 1):
                post(b, x, 1, y, z, "stripped_spruce_log[axis=y]")
                b.set(x, y + 1, z, "spruce_fence")
                b.set(x, 0, z, "cobblestone")
    for x in (-1, 0):
        b.set(x, 9, -3, "spruce_planks")
    b.set(-2, 10, -3, "spruce_fence"); b.set(1, 10, -3, "spruce_fence")
    # charging mouth + barrow of ore on the ledge
    b.set(0, 10, -1, "cauldron")
    b.set(-1, 10, -2, "raw_iron_block") if not b.has(-1, 10, -2) else None
    b.set(1, 10, -2, "composter[level=0]")
    # casting shed (lean-to) in front of the hearth, z = 4..7
    for (x, z) in ((-4, 7), (4, 7)):
        post(b, x, 1, 3, z, "stripped_spruce_log[axis=y]")
        b.set(x, 0, z, "cobblestone")
    for x in range(-4, 5):
        b.set(x, 4, 7, "stripped_spruce_wood[axis=x]")
        b.set(x, 5, 6, "spruce_stairs[facing=south,half=bottom]")
        b.set(x, 5, 5, "spruce_planks")
        b.set(x, 6, 5, "spruce_stairs[facing=south,half=bottom]")
        b.set(x, 6, 4, "spruce_planks")
        b.set(x, 4, 8, "spruce_stairs[facing=south,half=bottom]")
    for x in range(-3, 4):
        b.set(x, 0, 5, "sand")
        b.set(x, 0, 6, "sand")
    # ingots cooling in the sand bed, stacks, anvil, trough
    for x in (-3, -1, 1, 3):
        b.set(x, 1, 5, "iron_trapdoor[facing=north,half=bottom,open=false]")
    b.set(-3, 1, 7, "iron_trapdoor[facing=north,half=bottom,open=false]")
    b.set(-3, 1, 6, "iron_block")
    b.set(-3, 2, 6, "iron_trapdoor[facing=north,half=bottom,open=false]")
    b.set(3, 1, 6, "anvil[facing=east]")
    b.set(2, 1, 7, "water_cauldron[level=3]")
    b.set(4, 1, 6, "barrel[facing=up]", be={"id": "minecraft:barrel", "Items": []})
    hang(b, 0, 4, 7, 0)
    # charcoal heap (west) + ore pile (east)
    boulder(b, -5, 1, 1, 1, 1, 2, mix=[("coal_block", 3), ("black_concrete_powder", 2), ("blackstone", 1)],
            top=[("coal_block", 1)], seed=17, moss=0.0, bury=0, rough=0.2)
    boulder(b, 5, 1, -3, 1, 1, 1, mix=[("raw_iron_block", 2), ("iron_ore", 2), ("gravel", 2), ("tuff", 1)],
            top=[("gravel", 1)], seed=19, moss=0.0, bury=0, rough=0.2)
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_ore_carts", "mining",
       "A rail spur with three loaded ore tubs (coal, iron, copper) against a timber buffer stop, sorting bins "
       "with chalked plaques behind, a tally lamp and spilled ore on the sleepers.",
       "Rails run across your view to the buffer.", group="accent")
def ore_carts():
    b = Build("ae_prop_ore_carts", seed=521)
    rng = b.rng
    for x in range(-6, 7):
        for z in range(-3, 2):
            b.set(x, 0, z, b._pick(YARD))
    for x in range(-6, 6):
        b.set(x, 0, 0, "gravel")
        b.set(x, 1, 0, "rail[shape=east_west]")
    # buffer stop
    b.set(6, 0, 0, "cobblestone")
    b.set(6, 1, 0, "stripped_spruce_log[axis=z]")
    b.set(6, 2, 0, "spruce_trapdoor[facing=east,half=bottom,open=false]")
    b.set(7, 1, 0, "spruce_stairs[facing=west]")
    for z in (-1, 1):
        b.set(6, 1, z, "spruce_stairs[facing=" + ("south" if z < 0 else "north") + "]")
    tub(b, 4, 1, 0, "raw_copper_block")
    tub(b, 2, 1, 0, "raw_iron_block")
    tub(b, 0, 1, 0, "coal_block")
    # sorting bins
    kinds = [(-4, "coal_ore", "COAL"), (0, "iron_ore", "IRON"), (4, "copper_ore", "COPPER")]
    for (cx, ore, label) in kinds:
        for x in (cx - 1, cx + 1):
            b.set(x, 1, -2, "spruce_planks")
            b.set(x, 1, -3, "spruce_planks")
            b.set(x, 2, -3, "spruce_fence")
        b.set(cx, 1, -3, "spruce_planks")
        b.set(cx, 1, -2, ore)
        b.set(cx, 2, -2, "gravel" if ore == "coal_ore" else ore)
        b.set(cx, 2, -3, "stripped_spruce_log[axis=x]")
    # plaques on the back rail facing south
    for (cx, ore, label) in kinds:
        b.set(cx, 3, -3, "spruce_planks")
        wall_sign(b, cx, 3, -2, "south", ["", label, "", ""])
    # lamp + tally
    b.set(-6, 1, 1, "cobblestone_wall")
    post(b, -6, 2, 3, 1, "stripped_spruce_log[axis=y]")
    b.set(-6, 4, 1, "lantern")
    for (x, z) in ((1, 1), (3, 1), (-2, 1), (5, -1)):
        b.set(x, 1, z, rng.choice(["coal_ore", "gravel", "cobblestone_slab[type=bottom]", "iron_ore"]))
    tidy(b)
    ground_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_timber_stack", "mining",
       "Pit-prop yard: a cribbed stack of stripped logs with cross-spacers, props stood upright in a rack, "
       "lagging boards piled as slabs, a sawhorse with a half-cut log, a chopping block and a lamp stake in "
       "sawdust.",
       "Log ends point the way you look.", group="accent")
def timber_stack():
    b = Build("ae_prop_timber_stack", seed=531)
    rng = b.rng
    for x in range(-5, 6):
        for z in range(-3, 3):
            b.set(x, 0, z, b._pick([("coarse_dirt", 3), ("gravel", 1), ("sand", 1), ("packed_mud", 1)]))
    # cribbed log stack
    for layer, y in enumerate((1, 2, 3)):
        for z in (-2, -1, 0):
            for x in range(-4, 1):
                if layer == 2 and z == 0:
                    continue
                b.set(x, y, z, "stripped_spruce_log[axis=z]" if (x + z + layer) % 5 else "spruce_log[axis=z]")
        if layer < 2:
            for z in (-2, -1, 0):
                pass
    for z in (-2, -1, 0):
        b.set(-5, 1, z, "spruce_slab[type=bottom]")
    for (x, y) in ((-4, 4), (0, 4)):
        for z in (-2, -1):
            b.set(x, y, z, "spruce_slab[type=bottom]")
    # upright props in a rack
    for z in (-2, -1, 0):
        for y in (1, 2, 3):
            b.set(3, y, z, "stripped_spruce_log[axis=y]") if (z, y) != (0, 3) else None
    b.set(4, 1, -2, "spruce_fence"); b.set(4, 2, -2, "spruce_fence"); b.set(4, 3, -2, "spruce_fence")
    b.set(4, 3, -1, "spruce_fence"); b.set(4, 3, 0, "spruce_fence")
    b.set(4, 1, 0, "spruce_fence"); b.set(4, 2, 0, "spruce_fence")
    # lagging boards
    for (x, z, n) in ((1, -2, 2), (2, -2, 1), (1, -1, 1)):
        for k in range(n):
            b.set(x, 1 + k, z, "spruce_slab[type=double]" if k < n - 1 else "spruce_slab[type=bottom]")
    # sawhorse with a half-cut log
    b.set(-3, 1, 2, "spruce_fence"); b.set(0, 1, 2, "spruce_fence")
    for x in range(-3, 1):
        b.set(x, 2, 2, "stripped_spruce_log[axis=x]")
    b.set(-1, 1, 2, "spruce_trapdoor[facing=south,half=top,open=false]")
    # chopping block + lamp stake
    b.set(2, 1, 1, "oak_log[axis=y]")
    b.set(2, 2, 1, "oak_pressure_plate")
    b.set(5, 1, 1, "cobblestone_wall")
    post(b, 5, 2, 3, 1, "stripped_spruce_log[axis=y]")
    b.set(5, 4, 1, "lantern")
    for (x, z) in ((1, 1), (3, 2), (-4, 1)):
        b.set(x, 0, z, "sand")
    tidy(b)
    ground_anchor(b)
    return b
