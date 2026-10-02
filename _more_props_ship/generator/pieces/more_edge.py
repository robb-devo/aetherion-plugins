"""Second pass - isle edges, overlooks and anywhere pieces (wind, cliffs, soft magic)."""
import json
import math

from aeprops.core import Build, bname
from aeprops.style import (STONE_BRICK, COBBLE, PAVERS, PLASTER, ROCK, soil_patch, hang_lantern, pebbles, post, beam,
                           corbel, gable_roof, gable_wall_tri, brace, strut_down, leaf_clump, Noise2)
from . import piece
from ._kit import (ground_anchor, disc, ring, hang, fence_ring, wall_sign, tidy, clear_box, stairs_ring, corbel_ring,
                   boulder, plant, WHITEWASH, QUAY_STONE)

RUIN = [("mossy_stone_bricks", 5), ("stone_bricks", 3), ("cracked_stone_bricks", 3), ("mossy_cobblestone", 1)]


def to_center(x, z, cx, cz):
    dx, dz = cx - x, cz - z
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def two_sided_sign(b: Build, x, y, z, state, lines):
    msgs = [json.dumps({"text": t}) if t else '""' for t in (list(lines) + ["", "", "", ""])[:4]]
    side = {"messages": msgs, "color": "black", "has_glowing_text": False}
    b.set(x, y, z, state, be={
        "id": "minecraft:hanging_sign" if "hanging_sign" in state else "minecraft:sign",
        "front_text": dict(side), "back_text": dict(side), "is_waxed": True})


# ---------------------------------------------------------------------------------------------
@piece("ae_edge_windmill", "edge",
       "Tower windmill for a windy isle edge: a battered stone base under a tapering whitewashed tower, a railed "
       "reefing stage on struts, a timber boat cap, and four 9-long sails (two clothed, two bare lattice) turning "
       "toward your view. Flour sacks, a millstone and a cart at the door.",
       "Sails point the way you look.", group="landmark")
def edge_windmill():
    b = Build("ae_edge_windmill", seed=601)
    rng = b.rng
    soil_patch(b, 0, 0, 7, 7, y=0, mix=[("grass_block", 4), ("moss_block", 1), ("coarse_dirt", 1)], plants=0.3,
               flowers=0.06)
    for (x, z) in disc(0, 0, 4.4):
        b.set(x, 0, z, b._pick(COBBLE))
    for x in (-1, 0, 1):
        for z in range(4, 7):
            b.set(x, 0, z, b._pick([("dirt_path", 3), ("coarse_dirt", 1)]))
    TOP = 13
    for y in range(1, TOP + 1):
        r = 4.0 - (y - 1) * (1.2 / TOP)
        shell = set(ring(0, 0, r))
        for (x, z) in disc(0, 0, r):
            if (x, z) in shell:
                if y <= 3:
                    mat = b._pick([("stone_bricks", 3), ("mossy_stone_bricks", 1), ("cobblestone", 1)])
                else:
                    mat = b._pick(WHITEWASH)
                b.set(x, y, z, mat)
            else:
                b.clear(x, y, z)
    for (x, z) in ring(0, 0, 4.0):
        b.set(x, 1, z, b._pick([("stone_bricks", 3), ("mossy_stone_bricks", 1)]))
    for (x, z) in ring(0, 0, 4.9):
        if not (z >= 4 and abs(x) <= 1):
            b.set(x, 1, z, f"stone_brick_stairs[facing={to_center(x, z, 0, 0)},half=bottom]")
    for (x, z) in disc(0, 0, 3):
        b.set(x, 0, z, "spruce_planks")
    # dark timber window frames up the tower
    for (x, y, z) in ((0, 8, 3), (3, 5, 0), (-3, 10, 0), (0, 11, -3), (2, 4, -2)):
        if b.has(x, y, z):
            b.set(x, y, z, "glass_pane")
    # door
    for y in (1, 2):
        b.clear(0, y, 4) if b.has(0, y, 4) else None
    zd = max(z for (x, z) in ring(0, 0, 4.0) if x == 0)
    b.door(0, 1, zd, "spruce", "north")
    b.set(0, 3, zd, "stripped_dark_oak_wood[axis=x]")
    # reefing stage at y = 6
    S = 6
    for (x, z) in disc(0, 0, 5.3):
        if not b.has(x, S, z):
            b.set(x, S, z, "spruce_planks")
    for (x, z) in ring(0, 0, 5.3):
        b.set(x, S, z, "stripped_spruce_wood[axis=x]" if abs(z) >= abs(x) else "stripped_spruce_wood[axis=z]")
        b.set(x, S + 1, z, "spruce_fence")
    for (x, z) in ((5, 0), (-5, 0), (0, -5), (0, 5), (4, 3), (-4, 3), (4, -3), (-4, -3)):
        f = to_center(x, z, 0, 0)
        b.set(x, S - 1, z, f"spruce_stairs[facing={f},half=top]") if b.has(x, S, z) else None
    zr = max(z for (x, z) in ring(0, 0, 5.3) if x == 0)
    # cap: boat-shaped timber cap, ridge north-south
    capY = TOP + 1
    for (x, z) in disc(0, 0, 3.2):
        b.set(x, capY, z, "stripped_dark_oak_wood[axis=z]" if (x, z) in set(ring(0, 0, 3.2)) else "dark_oak_planks")
    gable_roof(b, -2, 2, -3, 3, capY + 1, ridge_axis="z", stairs=[("dark_oak_stairs", 3), ("spruce_stairs", 1)],
               fill="dark_oak_planks", slab="dark_oak_slab", overhang=1, edge_stairs="spruce_stairs")
    for zz in (-3, 3):
        gable_wall_tri(b, zz, -1, 1, capY + 1, "z", "spruce_planks")
    # windshaft + hub
    HY, HZ = capY + 1, 6
    for z in range(3, HZ + 1):
        b.set(0, HY, z, "stripped_spruce_log[axis=z]")
    b.set(0, HY, HZ + 1, "stripped_dark_oak_log[axis=z]")
    SZ = HZ + 1
    L = 10
    # stocks
    for i in range(1, L + 1):
        b.set(0, HY + i, SZ, "stripped_spruce_wood[axis=y]")
        b.set(0, HY - i, SZ, "stripped_spruce_wood[axis=y]") if HY - i > 0 else None
        b.set(i, HY, SZ, "stripped_spruce_wood[axis=x]")
        b.set(-i, HY, SZ, "stripped_spruce_wood[axis=x]")
    # clothed sails on the vertical arms (up: east side, down: west side)
    for i in range(3, L + 1):
        for k in (1, 2):
            b.set(k, HY + i, SZ, "white_wool" if i < L else "stripped_spruce_wood[axis=x]")
            if HY - i > 1:
                b.set(-k, HY - i, SZ, "white_wool" if i < L else "stripped_spruce_wood[axis=x]")
        b.set(3, HY + i, SZ, "spruce_fence")
        if HY - i > 1:
            b.set(-3, HY - i, SZ, "spruce_fence")
    # bare lattice on the horizontal arms (right: below, left: above)
    for i in range(3, L + 1):
        for k in (1, 2, 3):
            if k == 3 or i % 2 == 1:
                b.set(i, HY - k, SZ, "spruce_fence")
                b.set(-i, HY + k, SZ, "spruce_fence")
    # door yard: sacks, millstone, cart, lamp
    b.set(2, 1, 5, "white_wool"); b.set(3, 1, 5, "white_wool"); b.set(2, 2, 5, "white_carpet")
    b.set(-3, 1, 5, "smooth_stone_slab[type=double]")
    b.set(-3, 2, 5, "smooth_stone_slab[type=bottom]")
    b.set(-2, 1, 6, "hay_block[axis=x]")
    b.set(3, 1, 6, "spruce_trapdoor[facing=south,half=top,open=false]")
    b.set(4, 1, 6, "spruce_trapdoor[facing=south,half=top,open=false]")
    b.container(3, 2, 6, "barrel[facing=up]")
    b.set(5, 1, 6, "spruce_fence")
    b.set(-5, 1, 4, "cobblestone_wall")
    post(b, -5, 2, 3, 4, "stripped_spruce_log[axis=y]")
    b.set(-5, 4, 4, "lantern")
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_edge_broken_bridge", "edge",
       "The bridge that stopped: a mossy stone span leaving the isle lip over one arch and a tall pier that hangs "
       "into the void, snapping off mid-air. Parapets with a last lamp still lit, a chain barrier and a warning "
       "plaque, vines and roots dripping from the arch, and a few rubble stones held floating below the break.",
       "Aim at the last block before the drop.", group="landmark")
def edge_broken_bridge():
    b = Build("ae_edge_broken_bridge", seed=611)
    rng = b.rng
    # abutment on land (z = -5 .. 0)
    for z in range(-5, 1):
        for x in range(-3, 4):
            for y in range(-3, 0):
                b.set(x, y, z, b._pick(RUIN + [("cobblestone", 2)]))
            b.set(x, 0, z, b._pick(PAVERS) if abs(x) <= 1 else b._pick(RUIN))
    for z in range(-7, -5):
        for x in (-1, 0, 1):
            b.set(x, 0, z, b._pick([("dirt_path", 3), ("coarse_dirt", 1), ("cobblestone", 1)]))
    # deck out over the drop, z = 1 .. 14 (ragged end)
    END = 14
    for z in range(1, END + 1):
        for x in range(-2, 3):
            if z >= END - 2 and rng.random() < 0.35 * (z - END + 3):
                continue
            b.set(x, 0, z, b._pick(PAVERS) if abs(x) <= 1 else b._pick(RUIN))
            b.set(x, -1, z, b._pick(RUIN))
    # parapets
    for z in range(-5, END):
        for x in (-2, 2):
            if b.has(x, 0, z) and not (z >= END - 2 and rng.random() < 0.5):
                b.set(x, 1, z, "mossy_stone_brick_wall" if rng.random() < 0.4 else "stone_brick_wall")
    # arch between the lip (z = 0) and the pier (z = 7), soffit rounded with stairs
    PZ = 7
    arch = {1: -3, 2: -4, 3: -5, 4: -5, 5: -4, 6: -3}
    for z, bottom in arch.items():
        for x in range(-2, 3):
            for y in range(bottom, -1):
                b.set(x, y, z, b._pick(RUIN))
            f = "north" if z <= 3 else "south"
            b.set(x, bottom, z, f"stone_brick_stairs[facing={'south' if z <= 3 else 'north'},half=top]")
    # pier hanging into the void
    for y in range(-16, 0):
        for x in range(-2, 3):
            for z in range(PZ - 1, PZ + 2):
                edge = abs(x) == 2 or z in (PZ - 1, PZ + 1)
                if y < -12 and rng.random() < 0.08 * (-12 - y):
                    continue
                b.set(x, y, z, b._pick(RUIN) if edge else "cobblestone")
    for x in range(-3, 4):
        for z in (PZ - 2, PZ + 2):
            b.set(x, -2, z, f"stone_brick_stairs[facing={'south' if z < PZ else 'north'},half=top]")
    for z in range(PZ - 2, PZ + 3):
        for x in (-3, 3):
            b.set(x, -2, z, f"stone_brick_stairs[facing={'east' if x < 0 else 'west'},half=top]")
    # second arch springing from the pier, broken
    for z, bottom in {8: -3, 9: -4, 10: -5, 11: -5}.items():
        for x in range(-2, 3):
            if z == 11 and rng.random() < 0.5:
                continue
            for y in range(bottom, -1):
                b.set(x, y, z, b._pick(RUIN))
            b.set(x, bottom, z, "stone_brick_stairs[facing=north,half=top]")
    # roots and vines dripping off the underside
    for (x, y, z), st in list(b.blocks.items()):
        if y < 0 and not b.has(x, y - 1, z) and rng.random() < 0.18:
            n = rng.randint(1, 3)
            for k in range(1, n + 1):
                if b.has(x, y - k, z):
                    break
                b.set(x, y - k, z, "hanging_roots" if k == 1 and rng.random() < 0.5 else "cave_vines_plant[berries=false]"
                      if k < n else "cave_vines[berries=false]")
                if "hanging_roots" in (b.get(x, y - k, z) or ""):
                    break
    # moss on the deck edges
    for z in range(-5, END):
        for x in (-1, 1):
            if b.has(x, 0, z) and not b.has(x, 1, z) and rng.random() < 0.18:
                b.set(x, 1, z, "moss_carpet")
    # last lamp, chain barrier, warning plaque
    for x in (-2, 2):
        b.set(x, 1, 3, "stone_bricks")
        b.set(x, 2, 3, "stone_brick_wall")
        b.set(x, 3, 3, "lantern") if x == -2 else None
    b.set(2, 3, 3, "stone_brick_slab[type=bottom]")
    b.set(-2, 1, 10, "stone_bricks")
    b.set(-2, 2, 10, "stone_brick_wall")
    b.set(-2, 3, 10, "lantern")
    for x in (-1, 0, 1):
        b.set(x, 2, 9, "chain[axis=x]")
    for x in (-2, 2):
        b.set(x, 1, 9, "stone_bricks")
        b.set(x, 2, 9, "stone_brick_wall")
    b.set(0, 1, 9, "air")
    two_sided_sign(b, 0, 1, 8, "spruce_sign[rotation=8]", ["", "NO FURTHER", "the span is", "long gone"])
    # rubble held in the air below the break (soft magic)
    for (x, y, z, st) in ((0, -4, 15, "mossy_stone_bricks"), (-1, -6, 16, "cracked_stone_bricks"),
                          (1, -9, 15, "stone_brick_slab[type=bottom]"), (-2, -3, 17, "mossy_cobblestone"),
                          (2, -7, 17, "stone_bricks"), (0, -12, 18, "mossy_stone_bricks")):
        b.set(x, y, z, st)
    # grass tufts on the land side
    soil_patch(b, 0, -6, 5, 2, y=0, plants=0.4, flowers=0.08)
    tidy(b)
    b.meta["context"] = ("cliff", 1)
    b.anchor = (0, 0, 0)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_signpost", "anywhere",
       "Crossroads waymarker: a tall post on a stone plinth with four arms, each carrying a two-sided hanging "
       "sign (ANKER HARBOUR, ELDERVALE MINES, ELDER WOODS, FISHING QUAYS), a lamp on top and a milestone reading "
       "AETHERION at its foot.",
       "Rotate so the arms match the roads.", group="accent")
def signpost():
    b = Build("ae_prop_signpost", seed=621)
    for x in range(-1, 2):
        for z in range(-1, 2):
            b.set(x, 0, z, b._pick(PAVERS))
    b.set(0, 1, 0, "stone_bricks")
    for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        b.set(dx, 1, dz, f"stone_brick_stairs[facing={f}]")
    for y in range(2, 8):
        b.set(0, y, 0, "stripped_dark_oak_log[axis=y]")
    b.set(0, 8, 0, "dark_oak_fence")
    b.set(0, 9, 0, "lantern")
    # arms: east/west at y = 6, north/south at y = 5
    names = {"east": ["", "ANKER", "HARBOUR", ""], "west": ["", "ELDERVALE", "MINES", ""],
             "north": ["", "ELDER", "WOODS", ""], "south": ["", "FISHING", "QUAYS", ""]}
    for d, (dx, dz, y) in {"east": (1, 0, 6), "west": (-1, 0, 6), "north": (0, -1, 5), "south": (0, 1, 5)}.items():
        axis = "x" if dx else "z"
        for k in (1, 2):
            b.set(dx * k, y, dz * k, f"stripped_dark_oak_wood[axis={axis}]")
        rot = 0 if dx else 4
        two_sided_sign(b, dx * 2, y - 1, dz * 2, f"dark_oak_hanging_sign[rotation={rot},attached=false]", names[d])
    # milestone
    b.set(0, 2, 1, "dark_oak_wall_sign[facing=south]", be={
        "id": "minecraft:sign", "front_text": {"messages": ['""', json.dumps({"text": "AETHERION"}), '""', '""'],
                                               "color": "black", "has_glowing_text": False},
        "back_text": {"messages": ['""'] * 4, "color": "black", "has_glowing_text": False}, "is_waxed": True})
    tidy(b)
    ground_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_flower_cart", "anywhere",
       "A flower-seller's handcart: plank bed on board wheels, loaded with potted blooms, a pumpkin and hay, under "
       "a striped canvas awning on poles; a stool, a watering can and spare pots on the paving beside it.",
       "Awning side points the way you look.", group="accent")
def flower_cart():
    b = Build("ae_prop_flower_cart", seed=631)
    for x in range(-3, 4):
        for z in range(-2, 3):
            b.set(x, 0, z, b._pick(PAVERS))
    # bed at y = 1: x = -1..1, z = -1..0
    for x in (-1, 0, 1):
        for z in (-1, 0):
            b.set(x, 1, z, "spruce_slab[type=top]")
    b.set(-2, 1, -1, "spruce_trapdoor[facing=west,half=top,open=false]")
    b.set(-2, 1, 0, "spruce_trapdoor[facing=west,half=top,open=false]")
    # wheels (board wheels on both long sides)
    b.set(0, 1, -2, "dark_oak_trapdoor[facing=north,half=bottom,open=true]")
    b.set(0, 1, 1, "dark_oak_trapdoor[facing=south,half=bottom,open=true]")
    # handles resting on the ground
    b.set(2, 1, -1, "spruce_fence"); b.set(2, 1, 0, "spruce_fence")
    b.set(3, 1, -1, "spruce_trapdoor[facing=east,half=bottom,open=false]")
    # load
    pots = ["potted_red_tulip", "potted_azure_bluet", "potted_cornflower", "potted_poppy", "potted_oxeye_daisy",
            "potted_blue_orchid"]
    cells = [(-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0)]
    for i, (x, z) in enumerate(cells):
        if (x, z) == (1, -1):
            b.set(x, 2, z, "pumpkin")
        elif (x, z) == (-1, -1):
            b.set(x, 2, z, "hay_block[axis=x]")
            b.set(x, 3, z, "potted_allium")
        else:
            b.set(x, 2, z, pots[i % len(pots)])
    # awning: poles at the corners, striped carpets on a trapdoor frame
    for (x, z) in ((-2, 1), (2, 1)):
        b.set(x, 1, z, "spruce_fence"); b.set(x, 2, z, "spruce_fence"); b.set(x, 3, z, "spruce_fence")
    for x in range(-2, 3):
        b.set(x, 4, 1, "spruce_trapdoor[facing=south,half=bottom,open=false]")
        b.set(x, 5, 1, "red_carpet" if x % 2 else "white_carpet")
        b.set(x, 4, 0, "spruce_trapdoor[facing=south,half=top,open=false]")
        b.set(x, 5, 0, "red_carpet" if x % 2 else "white_carpet")
    # beside: stool, can, spare pots
    b.set(3, 1, 1, "stripped_oak_log[axis=y]")
    b.set(3, 2, 1, "oak_pressure_plate")
    b.set(-3, 1, 1, "cauldron")
    b.set(-3, 1, -1, "flower_pot")
    b.set(-3, 1, 2, "potted_fern")
    b.set(2, 1, 2, "decorated_pot[facing=south]", be={"id": "minecraft:decorated_pot",
                                                     "sherds": ["minecraft:brick"] * 4})
    tidy(b)
    ground_anchor(b)
    return b


# ---------------------------------------------------------------------------------------------
@piece("ae_prop_overlook_bench", "edge",
       "A seat for the view: a stone bench with trapdoor arms between two planter boxes of azalea and bluets, a "
       "flowering azalea arching behind for shade and a lamp post at one end, on a paved pad.",
       "Seat looks the way you look.", group="accent")
def overlook_bench():
    b = Build("ae_prop_overlook_bench", seed=641)
    for x in range(-4, 5):
        for z in range(-2, 3):
            b.set(x, 0, z, b._pick(PAVERS) if z >= -1 else b._pick([("moss_block", 2), ("grass_block", 2)]))
    for x in (-1, 0, 1):
        b.set(x, 1, 0, "stone_brick_stairs[facing=north]")
    b.set(-2, 1, 0, "spruce_trapdoor[facing=west,half=bottom,open=true]")
    b.set(2, 1, 0, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    # planters
    for sx in (-3, 3):
        for z in (-1, 0):
            b.set(sx, 1, z, "rooted_dirt")
            b.set(sx, 0, z, "stone_bricks")
        for (dx, dz, f) in ((0, -2, "north"), (0, 1, "south")):
            b.set(sx + dx, 1, dz, f"dark_oak_trapdoor[facing={f},half=bottom,open=true]")
        b.set(sx + (1 if sx > 0 else -1), 1, -1, f"dark_oak_trapdoor[facing={'east' if sx > 0 else 'west'},half=bottom,open=true]")
        b.set(sx + (1 if sx > 0 else -1), 1, 0, f"dark_oak_trapdoor[facing={'east' if sx > 0 else 'west'},half=bottom,open=true]")
    b.set(-3, 0, -1, "rooted_dirt"); b.set(-3, 1, -1, "rooted_dirt")
    # plants must sit on soil: rooted_dirt counts as soil
    b.set(-3, 2, 0, "azure_bluet")
    b.set(3, 2, -1, "lily_of_the_valley")
    b.set(3, 2, 0, "cornflower")
    # shade tree behind
    b.set(0, 0, -2, "moss_block")
    for y in range(1, 5):
        b.set(0, y, -2, "oak_log[axis=y]")
    leaf_clump(b, 0, 5, -1, 2, 1, 2, mix=[("flowering_azalea_leaves", 3), ("azalea_leaves", 2)], density=0.9)
    for (x, z) in ((-1, 0), (0, 0), (1, 0)):
        for y in range(2, 5):
            b.clear(x, y, z) if b.get(x, y, z) and "leaves" in b.get(x, y, z) and y < 4 else None
    # lamp post
    b.set(4, 1, -1, "stone_brick_wall")
    post(b, 4, 2, 3, -1, "stripped_dark_oak_log[axis=y]")
    b.set(4, 4, -1, "lantern")
    tidy(b)
    ground_anchor(b)
    return b
