"""Second pass - fishing pieces (quays, stilts, buoys, kelp air)."""
from aeprops.core import Build, bname
from aeprops.style import (STONE_BRICK, COBBLE, PAVERS, PLASTER, soil_patch, hang_lantern, pebbles, post, beam,
                           corbel, gable_roof, gable_wall_tri, fill_under_roof, brace, strut_down, leaf_clump)
from . import piece
from ._kit import (ground_anchor, waterlog_below, disc, ring, hang, fence_ring, wall_sign, tidy, clear_box,
                   SEABED, QUAY_STONE)


def rowboat(b: Build, x0, y, z0, length=5, axis="z"):
    """Little plank rowboat at the waterline y, bow toward +axis. (x0, z0) = stern centre."""
    for i in range(length):
        if axis == "z":
            c, l, r = (x0, z0 + i), (x0 - 1, z0 + i), (x0 + 1, z0 + i)
            fl, fr, fb, ff = "west", "east", "north", "south"
        else:
            c, l, r = (x0 + i, z0), (x0 + i, z0 - 1), (x0 + i, z0 + 1)
            fl, fr, fb, ff = "north", "south", "west", "east"
        if 0 < i < length - 1:
            b.set(c[0], y, c[1], "spruce_slab[type=bottom]")
            b.set(l[0], y, l[1], f"spruce_stairs[facing={fl}]")
            b.set(r[0], y, r[1], f"spruce_stairs[facing={fr}]")
        elif i == 0:
            b.set(c[0], y, c[1], f"spruce_stairs[facing={fb}]")
        else:
            b.set(c[0], y, c[1], f"spruce_stairs[facing={ff}]")
    mid = length // 2
    if axis == "z":
        b.set(x0, y + 1, z0 + mid, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    else:
        b.set(x0 + mid, y + 1, z0, "spruce_trapdoor[facing=west,half=bottom,open=false]")


# ---------------------------------------------------------------------------------------------
@piece("ae_fishing_pier", "fishing",
       "A 16-long plank pier on paired piles running out to a railed T-head: fog bell on a gallows post, lamp "
       "posts, a bench facing the sea, bait barrel and net chest, a rod rack, a sea ladder and a rowboat tied "
       "alongside. Cross-braced under the deck, piles run 7 down.",
       "Aim at the quay edge; runs out ahead.", group="mid")
def fishing_pier():
    b = Build("ae_fishing_pier", seed=401)
    rng = b.rng
    W = -1
    ZT0, ZT1 = 17, 20                     # T-head
    # walkway x=-1..1, z=0..16 (z = 0 is the landing board on the quay edge you look at)
    for z in range(0, ZT0):
        for x in (-1, 0, 1):
            b.set(x, 0, z, "spruce_planks" if rng.random() < 0.8 else "stripped_spruce_wood[axis=x]")
        for x in (-1, 1):
            b.set(x, -1, z, "stripped_spruce_wood[axis=z]")
    # T-head
    for z in range(ZT0, ZT1 + 1):
        for x in range(-5, 6):
            edge = abs(x) == 5 or z == ZT1
            b.set(x, 0, z, ("stripped_spruce_wood[axis=z]" if abs(x) == 5 else "stripped_spruce_wood[axis=x]")
                  if edge else ("spruce_planks" if rng.random() < 0.85 else "dark_oak_planks"))
        for x in (-5, 5):
            b.set(x, -1, z, "stripped_spruce_wood[axis=z]")
    for x in range(-5, 6):
        b.set(x, -1, ZT0, "stripped_spruce_wood[axis=x]")
        b.set(x, -1, ZT1, "stripped_spruce_wood[axis=x]")
    # piles: pairs along the walkway, grid under the head
    piles = [(x, z) for z in (2, 6, 10, 14) for x in (-2, 2)] + \
            [(x, z) for x in (-5, -2, 2, 5) for z in (ZT0, ZT1)]
    for (x, z) in piles:
        top = 1 if (abs(x) == 2 and z < ZT0) else 0
        for y in range(-7, top + 1):
            b.set(x, y, z, "stripped_spruce_log[axis=y]" if y > -3 else "spruce_log[axis=y]")
    for z in (2, 6, 10, 14):
        for x in (-2, 2):
            b.set(x, 0, z, "stripped_spruce_log[axis=y]")
        # cross brace under the deck
        b.set(-1, -2, z, "spruce_stairs[facing=west,half=top]")
        b.set(1, -2, z, "spruce_stairs[facing=east,half=top]")
        b.set(0, -2, z, "stripped_spruce_wood[axis=x]")
    # walkway lamp posts
    for (x, z) in ((2, 6), (-2, 14)):
        b.set(x, 2, z, "spruce_fence")
        b.set(x, 3, z, "spruce_fence")
        b.set(x, 4, z, "lantern")
    # railing on the head
    skip = {(-5, 20)}
    for x in range(-5, 6):
        b.set(x, 1, ZT1, "spruce_fence")
    for z in range(ZT0, ZT1 + 1):
        for x in (-5, 5):
            if (x, z) not in skip:
                b.set(x, 1, z, "spruce_fence")
    for x in (-4, -3, 3, 4):
        b.set(x, 1, ZT0, "spruce_fence")
    b.set(-5, 1, ZT0, "stripped_spruce_log[axis=y]")
    b.set(-5, 2, ZT0, "spruce_fence")
    b.set(-5, 3, ZT0, "lantern")
    # sea ladder down the west side
    for y in range(-4, 1):
        b.set(-6, y, ZT1, "ladder[facing=west]")
    # fog bell on a gallows post (east corner)
    for y in range(1, 5):
        b.set(5, y, ZT1, "stripped_spruce_log[axis=y]")
    b.set(4, 4, ZT1, "stripped_spruce_wood[axis=x]")
    b.set(3, 4, ZT1, "stripped_spruce_wood[axis=x]")
    b.set(4, 3, ZT1, "spruce_stairs[facing=east,half=top]")
    b.bell(3, 3, ZT1, "north", "ceiling")
    b.set(5, 5, ZT1, "lantern")
    # bench facing the sea
    for x in (0, 1, 2):
        b.set(x, 1, ZT1 - 1, "spruce_stairs[facing=north]")
    b.set(-1, 1, ZT1 - 1, "spruce_trapdoor[facing=west,half=bottom,open=true]")
    b.set(3, 1, ZT1 - 1, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    # bait + nets + rods
    b.container(-4, 1, ZT0 + 1, "barrel[facing=up]")
    b.set(-4, 2, ZT0 + 1, "white_carpet")
    b.set(-3, 1, ZT0 + 1, "water_cauldron[level=2]")
    b.container(-4, 1, ZT0 + 2, "barrel[facing=east]")
    b.set(-3, 1, ZT1 - 1, "cobweb")
    for x in (3, 4):
        b.set(x, 1, ZT0 + 1, "spruce_fence")
        b.set(x, 2, ZT0 + 1, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(4, 1, ZT0 + 2, "sea_pickle[pickles=2,waterlogged=false]")
    # rowboat tied on the east side of the head
    rowboat(b, 7, W, ZT0 - 1, length=5, axis="z")
    b.set(6, 0, ZT0 + 1, "chain[axis=x]")
    waterlog_below(b, W)
    b.meta["context"] = ("water", W)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_fishing_net_loft", "fishing",
       "Tall, narrow two-storey net loft on stilts over the water: board-and-batten spruce walls on dark-oak "
       "corners, a steep sawtooth roof, a hoist beam out of the sea gable lifting a net bundle, wide sea doors "
       "onto a landing deck with a ladder and rowboat, drying nets on a rack, lamps, window boxes of kelp.",
       "Aim at the shore edge, 1 above water.", group="landmark")
def fishing_net_loft():
    b = Build("ae_fishing_net_loft", seed=411)
    rng = b.rng
    W = -1
    X1, X2, Z1, Z2 = -4, 4, 2, 9                        # house
    # stilts
    for x in (X1, 0, X2):
        for z in (Z1, 5, Z2, 12):
            for y in range(-7, 1):
                b.set(x, y, z, "stripped_spruce_log[axis=y]" if y > -3 else "spruce_log[axis=y]")
    # gangway from the shore (z = 0 = the edge block you look at) + platform
    for x in (-1, 0, 1):
        b.set(x, 0, 0, "stripped_spruce_wood[axis=x]")
        b.set(x, 0, 1, "spruce_planks")
    for x in range(X1 - 1, X2 + 2):
        for z in range(Z1, 13):
            if not b.has(x, 0, z):
                b.set(x, 0, z, "spruce_planks" if rng.random() < 0.85 else "stripped_spruce_wood[axis=x]")
    for x in range(X1 - 1, X2 + 2):
        b.set(x, -1, 12, "stripped_spruce_wood[axis=x]")
    for z in range(Z1, 13):
        for x in (X1 - 1, X2 + 1):
            b.set(x, -1, z, "stripped_spruce_wood[axis=z]")
    for z in (5, 9):
        strut_down(b, (X1 - 1, -2, z), "east", 2)
        strut_down(b, (X2 + 1, -2, z), "west", 2)
    # walls: board-and-batten, two storeys (y=1..8), dark oak corners
    H = 8
    for y in range(1, H + 1):
        for x in range(X1, X2 + 1):
            for z in (Z1, Z2):
                corner = x in (X1, X2)
                b.set(x, y, z, "dark_oak_log[axis=y]" if corner else
                      ("stripped_mangrove_log[axis=y]" if x % 2 == 0 else "mangrove_planks"))
        for z in range(Z1 + 1, Z2):
            for x in (X1, X2):
                b.set(x, y, z, "stripped_mangrove_log[axis=y]" if z % 2 == 0 else "mangrove_planks")
    for x in range(X1, X2 + 1):
        for z in (Z1, Z2):
            b.set(x, 4, z, "stripped_dark_oak_wood[axis=x]")
    for z in range(Z1, Z2 + 1):
        for x in (X1, X2):
            b.set(x, 4, z, "stripped_dark_oak_wood[axis=z]")
    b.fill(X1 + 1, 4, Z1 + 1, X2 - 1, 4, Z2 - 1, "spruce_planks")         # loft floor
    b.fill(X1 + 1, 0, Z1 + 1, X2 - 1, 0, Z2 - 1, "spruce_planks")
    # steep sawtooth gable roof, ridge north-south
    y0 = H + 1
    half = (X2 - X1) // 2 + 2                            # overhang 1
    for z in range(Z1 - 1, Z2 + 2):
        for k in range(2 * half):
            off = k // 2
            y = y0 + k
            for side, x in ((1, X1 - 1 + off), (-1, X2 + 1 - off)):
                if side == 1 and x > 0 or side == -1 and x < 0:
                    continue
                f = "east" if side == 1 else "west"
                if x == 0:
                    b.set(x, y, z, "dark_oak_planks")
                    continue
                if k % 2 == 1:
                    b.set(x, y, z, f"dark_oak_stairs[facing={f},half=bottom]" if rng.random() < 0.75
                          else f"spruce_stairs[facing={f},half=bottom]")
                else:
                    b.set(x, y, z, "dark_oak_planks" if rng.random() < 0.8 else "spruce_planks")
    top = y0 + 2 * half - 1
    for z in range(Z1 - 1, Z2 + 2):
        b.set(0, top, z, "dark_oak_slab[type=bottom]") if not b.has(0, top, z) else None
    # gable infill (triangle) front and back
    for zz in (Z1, Z2):
        for y in range(y0, top):
            for x in range(X1 + 1, X2):
                if not b.has(x, y, zz):
                    ok = any(b.has(x2, y, zz) for x2 in range(x, X2 + 2)) and any(
                        b.has(x2, y, zz) for x2 in range(X1 - 1, x + 1))
                    if ok:
                        b.set(x, y, zz, "stripped_mangrove_log[axis=y]" if x % 2 == 0 else "mangrove_planks")
    # moss on the roof
    for (x, y, z), st in list(b.blocks.items()):
        if "dark_oak_stairs" in st and y > y0 + 1 and rng.random() < 0.08 and not b.has(x, y + 1, z):
            b.set(x, y + 1, z, "moss_carpet")
    # front (sea) gable: big doors, loft hatch, hoist
    for x in (-1, 0, 1):
        for y in (1, 2, 3):
            b.clear(x, y, Z2)
    b.set(-2, 2, Z2 + 1, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    b.set(2, 2, Z2 + 1, "spruce_trapdoor[facing=south,half=bottom,open=true]")
    for y in (5, 6):
        b.clear(0, y, Z2)
    for z in range(Z2 - 1, Z2 + 5):
        b.set(0, 10, z, "stripped_spruce_wood[axis=z]")
    b.set(0, 9, Z2 + 1, "spruce_stairs[facing=north,half=top]")
    b.set(0, 9, Z2 + 4, "grindstone[face=ceiling,facing=north]")
    for y in range(5, 9):
        b.set(0, y, Z2 + 4, "chain[axis=y]")
    b.set(0, 4, Z2 + 4, "cobweb")
    b.container(0, 3, Z2 + 4, "barrel[facing=up]")
    # windows
    for (x, z, f) in ((-2, Z1, "north"), (2, Z1, "north"), (X1, 4, "west"), (X2, 7, "east"), (X1, 7, "west"),
                      (X2, 4, "east")):
        for y in (2, 6):
            if b.has(x, y, z):
                b.set(x, y, z, "glass_pane")
    # pale birch shutters either side of each window
    for (x, y, z), st in list(b.blocks.items()):
        if st.endswith("glass_pane[east=false,north=false,south=false,waterlogged=false,west=false]") or "glass_pane" in st:
            if z == Z1 and not b.has(x - 1, y, z - 1):
                b.set(x - 1, y, z - 1, "birch_trapdoor[facing=north,half=bottom,open=true]")
                b.set(x + 1, y, z - 1, "birch_trapdoor[facing=north,half=bottom,open=true]")
            elif x in (X1, X2):
                dx = -1 if x == X1 else 1
                f = "west" if x == X1 else "east"
                for dz in (-1, 1):
                    if not b.has(x + dx, y, z + dz):
                        b.set(x + dx, y, z + dz, f"birch_trapdoor[facing={f},half=bottom,open=true]")
    # window boxes with kelp-dark plants (potted) on the side walls
    for (x, z, f) in ((X1 - 1, 7, "west"), (X2 + 1, 4, "east")):
        b.set(x, 5, z, f"spruce_trapdoor[facing={f},half=top,open=false]")
        b.set(x, 6, z, "potted_fern")
    # back door to the shore
    b.door(0, 1, Z1, "spruce", "north")
    wall_sign(b, 1, 3, Z1 - 1, "north", ["", "NET LOFT", "mind the hook", ""])
    # landing deck: drying rack, lamp, ladder, rowboat
    for x in (X1 - 1, X2 + 1):
        b.set(x, 1, 12, "stripped_spruce_log[axis=y]")
        b.set(x, 2, 12, "spruce_fence")
    b.set(X1 - 1, 3, 12, "lantern")
    b.set(X2 + 1, 3, 12, "lantern")
    for x in range(-3, 0):
        b.set(x, 1, 11, "spruce_fence")
        b.set(x, 2, 11, "cobweb")
    b.set(-3, 3, 11, "spruce_fence"); b.set(-1, 3, 11, "spruce_fence")
    b.set(-2, 3, 11, "spruce_fence")
    b.container(3, 1, 11, "barrel[facing=up]")
    b.set(3, 2, 11, "sea_pickle[pickles=3,waterlogged=false]")
    b.set(2, 1, 11, "composter[level=0]")
    for y in range(-4, 1):
        b.set(0, y, 13, "ladder[facing=south]")
    rowboat(b, -3, W, 14, length=5, axis="x")
    # interior: net heaps, barrels, lantern
    b.set(-3, 1, 3, "cobweb"); b.container(3, 1, 3, "barrel[facing=up]")
    b.set(3, 1, 4, "composter[level=0]"); b.set(-3, 1, 8, "hay_block[axis=y]")
    hang(b, 0, 4, 5, 0)
    for y in range(1, 4):
        b.set(3, y, 7, "ladder[facing=west]")
    b.clear(3, 4, 7)
    waterlog_below(b, W)
    tidy(b)
    b.meta["context"] = ("water", W)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_channel_marker", "fishing",
       "Pile-driven channel marker: three lashed tarred piles on a rubble cairn, barnacle crusts, a trapdoor-hatted "
       "lamp on the tallest, a warning bell on a bracket and a red pennant. Stands on the sea floor.",
       "Aim at the sea floor, 2–8 deep.", group="accent")
def channel_marker():
    b = Build("ae_prop_channel_marker", seed=421)
    rng = b.rng
    for (x, z) in disc(0, 0, 2.5):
        b.set(x, 0, z, b._pick(SEABED))
    for (x, z) in disc(0, 0, 1.6):
        b.set(x, 1, z, b._pick([("cobblestone", 3), ("mossy_cobblestone", 3), ("gravel", 1)]))
    piles = {(0, 0): 13, (1, 0): 11, (0, 1): 10}
    for (x, z), h in piles.items():
        for y in range(1, h + 1):
            b.set(x, y, z, "spruce_log[axis=y]" if y > 3 else "dark_oak_log[axis=y]")
    # lashing bands
    for y in (5, 9):
        for (x, z) in piles:
            if piles[(x, z)] >= y:
                b.set(x, y, z, "stripped_dark_oak_wood[axis=y]")
    # barnacle crusts low down (dead coral never dies further)
    for (x, z, f) in ((2, 0, "east"), (-1, 0, "west"), (0, 2, "south"), (1, 1, "south"), (0, -1, "north")):
        for y in (2, 3):
            if not b.has(x, y, z) and rng.random() < 0.8:
                b.set(x, y, z, f"dead_brain_coral_wall_fan[facing={f}]")
    # lamp on top with a trapdoor hat
    T = piles[(0, 0)]
    b.set(0, T + 1, 0, "stripped_spruce_log[axis=y]")
    b.set(0, T + 2, 0, "lantern")
    for (dx, dz) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        b.set(dx, T + 3, dz, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    b.set(0, T + 3, 0, "spruce_slab[type=bottom]")
    # bell on a bracket
    b.set(-1, T, 0, "stripped_spruce_wood[axis=x]")
    b.set(-2, T, 0, "stripped_spruce_wood[axis=x]")
    b.bell(-2, T - 1, 0, "east", "ceiling")
    # pennant
    b.set(1, 12, 0, "spruce_fence")
    b.set(1, 13, 0, "spruce_fence")
    b.banner(1, 14, 0, "red_banner[rotation=4]")
    # seagrass around the foot
    for (x, z) in ((2, 1), (-2, 0), (-1, -2), (1, 2), (-2, 1), (2, -1)):
        if b.has(x, 0, z) and not b.has(x, 1, z):
            b.set(x, 1, z, "seagrass")
    b.meta["context"] = ("water", 6)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_lobster_pots", "fishing",
       "Pot-and-float heap: a pyramid of lattice creels with red cork floats, a coil of line, crossed oars, a "
       "salt bucket, a net heap and a crab-cracking block on a sandy plank pallet.",
       "Beach or quay; tall side at the back.", group="accent")
def lobster_pots():
    b = Build("ae_prop_lobster_pots", seed=431)
    rng = b.rng
    for x in range(-3, 4):
        for z in range(-2, 3):
            if abs(x) + abs(z) <= 4 + rng.randint(0, 1):
                b.set(x, 0, z, b._pick([("sand", 4), ("suspicious_sand", 1), ("gravel", 1)]))
    for x in range(-2, 2):
        for z in (-1, 0):
            b.set(x, 0, z, "spruce_planks" if (x + z) % 2 else "stripped_spruce_wood[axis=x]")
    # creel pyramid (scaffolding = lattice cages), floats on top
    for (x, y, z) in ((-2, 1, -1), (-1, 1, -1), (0, 1, -1), (-2, 1, 0), (-1, 1, 0), (-2, 2, -1), (-1, 2, -1),
                      (-2, 3, -1)):
        b.set(x, y, z, "scaffolding[bottom=false,distance=0]")
    for (x, y, z, c) in ((-2, 4, -1, "red_candle[candles=2,lit=false]"), (-1, 3, -1, "red_candle[candles=1,lit=false]"),
                         (0, 2, -1, "white_candle[candles=1,lit=false]"), (-1, 2, 0, "red_candle[candles=3,lit=false]")):
        b.set(x, y, z, c)
    b.set(2, 1, 1, "scaffolding[bottom=false,distance=0]")
    b.set(2, 2, 1, "red_candle[candles=2,lit=false]")
    # coil of line
    for (x, z, ax) in ((1, -1, "x"), (1, 0, "z"), (2, -1, "z")):
        b.set(x, 1, z, f"chain[axis={ax}]")
    # crossed oars against the heap
    b.set(0, 1, 0, "spruce_fence")
    b.set(0, 2, 0, "spruce_fence")
    b.set(1, 1, 1, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    # bucket, net heap, cracking block
    b.set(-3, 1, 1, "water_cauldron[level=2]")
    b.set(-2, 1, 1, "cobweb")
    b.set(3, 1, -1, "stripped_spruce_log[axis=y]")
    b.set(3, 2, -1, "spruce_pressure_plate")
    b.container(3, 1, 0, "barrel[facing=up]")
    b.set(3, 2, 0, "sea_pickle[pickles=4,waterlogged=false]")
    b.set(-3, 1, -1, "dried_kelp_block")
    pebbles(b, [(1, 2), (-1, 2)])
    tidy(b)
    ground_anchor(b)
    return b
