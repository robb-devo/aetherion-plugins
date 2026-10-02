"""The authored island starters (3 personal + 1 guild).

Each builder returns a Build with meta:
  spawn   = (x, y, z, yaw)  relative to the island origin (surface = y 0, feet = y 1)
  pads    = {name: (cx, cz, half_w, half_l)}  flat, clear build pads the Java side points players at
  sites   = guild project sites {project_id: (cx, cz)}
  board   = guild project board lectern (x, y, z)
Those numbers are mirrored in StarterLayout.java (the Java side does not parse meta).
"""
from __future__ import annotations

import math

from aeprops.core import Build, bname
from aeprops.style import leaf_clump, small_spruce, small_oak, hang_lantern
from aeg.common import (island_body, decorate_surface, rect, flat_pad, lantern_post, pad_ring, small_birch,
                        a_frame_tent, wall_sign, standing_sign, ore_face, pick, GRASSY, EDGE, UNDER, UNDER_DEEP,
                        ROCKY_TOP, PAVERS, STONE_BRICK, PLANKS, gable, gable_ends, scaffold_column)
from aeg.kit import boulder, big_tree, disc, tidy

STARTERS = {}


def starter(name):
    def deco(fn):
        STARTERS[name] = fn
        return fn
    return deco


def _utilities(b: Build, x, z, facing="south"):
    """The three starter blocks every island had before (chest, crafting table, furnace)."""
    b.container(x, 1, z, f"chest[facing={facing}]")
    b.set(x + 1, 1, z, "crafting_table")
    b.set(x + 2, 1, z, f"furnace[facing={facing}]")


# ------------------------------------------------------------------------------------------------
# Grove Camp — mossy woodland isle, big oak, camp + tent, pond
# ------------------------------------------------------------------------------------------------
@starter("starter_grove")
def grove():
    b = Build("starter_grove", seed=211)
    hut_pad = rect(4, 1, 10, 7)          # 7x7 Storage Hut site, centre (7, 4)
    spawn_path = [(0, z) for z in range(2, 14)]

    def top(x, z, t):
        if t > 0.86:
            return EDGE
        return GRASSY

    surf = island_body(b, 15, 3101, top, sub="dirt", rock=UNDER, depth=14, keep=set(hut_pad) | set(spawn_path))
    flat_pad(b, hut_pad, "grass_block")
    hut_ring = pad_ring(b, 4, 1, 10, 7, [("dirt_path", 3), ("coarse_dirt", 1)])
    standing_sign(b, 3, 1, 8, 6, ["Hut Site", "", "Storage Hut", "goes here"])

    # pond (north-east)
    pond = disc(5, -6, 2.6)
    for (x, z) in disc(5, -6, 3.6):
        if (x, z) not in pond and (x, z) in surf:
            b.set(x, 0, z, pick(b, [("sand", 2), ("clay", 1), ("grass_block", 2)]))
    for (x, z) in pond:
        b.set(x, 0, z, "water[level=0]")
        b.set(x, -1, z, "water[level=0]")
        b.set(x, -2, z, pick(b, [("clay", 2), ("sand", 2), ("gravel", 1)]))
        for y in range(1, 6):
            b.clear(x, y, z)
    for (x, z) in [(4, -7), (6, -5), (5, -8)]:
        if (x, z) in pond:
            b.set(x, 1, z, "lily_pad")
    for (x, z) in [(8, -6), (2, -5)]:
        if b.get(x, 0, z) and bname(b.get(x, 0, z)) == "minecraft:sand":
            b.set(x, 1, z, "sugar_cane[age=0]")
            b.set(x, 2, z, "sugar_cane[age=0]")

    # big oak (north-west)
    big_tree(b, -8, 1, -7, h=10, trunk=2, wood="oak",
             leaves=[("oak_leaves", 4), ("azalea_leaves", 2), ("flowering_azalea_leaves", 1)], seed=9, crown=5,
             branches=4)
    for (x, z) in [(-10, -4), (-5, -9), (-11, -8)]:
        if b.get(x, 0, z) and not b.has(x, 1, z):
            b.set(x, 1, z, "moss_carpet")

    # camp: campfire, log benches, tent
    b.campfire(-2, 1, 1)
    b.set(-4, 1, 1, "stripped_oak_log[axis=z]")
    b.set(-2, 1, -1, "stripped_oak_log[axis=x]")
    b.set(0, 1, 1, "stripped_oak_log[axis=z]")
    a_frame_tent(b, -10, 2, 5)
    b.bed(-8, 1, 4, "red", "north")
    b.container(-7, 1, 3, "barrel[facing=up]")
    # starter utilities (kitchen corner of the camp)
    _utilities(b, -4, 4, "north")
    lantern_post(b, -5, 1, 6)

    # berry bushes, stump, boulder
    for (x, z) in [(-12, 1), (-11, 4), (9, 10), (-6, 9)]:
        if b.get(x, 0, z) and bname(b.get(x, 0, z)) in ("minecraft:grass_block", "minecraft:moss_block",
                                                        "minecraft:podzol", "minecraft:coarse_dirt") \
                and not b.has(x, 1, z):
            b.set(x, 1, z, "sweet_berry_bush[age=3]")
    b.set(10, 1, -1, "oak_log[axis=y]")
    b.set(10, 2, -1, "oak_slab[type=bottom]")
    boulder(b, -9, 1, 9, 2, 1, 2, seed=44)

    # path from spawn
    for (x, z) in spawn_path:
        wob = int(round(math.sin(z * 0.7) * 0.6))
        b.set(x + wob, 0, z, "dirt_path")
        b.clear(x + wob, 1, z)
    lantern_post(b, 2, 1, 11)
    small_birch(b, -12, 1, -1, 5)
    small_spruce(b, 11, 1, -8, 7)

    decorate_surface(b, surf, skip=set(hut_pad) | set(hut_ring) | set(spawn_path) | {(x, z) for (x, z) in rect(-11, 0, -3, 7)})
    b.meta.update(spawn=(0, 1, 12, 180.0), pads={"storage_hut": (7, 4, 3, 3)})
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ------------------------------------------------------------------------------------------------
# Quarry Outpost — rocky isle, ore outcrop with a timber mine mouth, tool shed, quarry pad
# ------------------------------------------------------------------------------------------------
@starter("starter_quarry")
def quarry():
    b = Build("starter_quarry", seed=313)
    quarry_pad = rect(5, -3, 9, 1)   # 5x5 quarry housing site, centre (7, -1)
    hut_pad = rect(2, 4, 8, 10)      # 7x7 Storage Hut site, centre (5, 7)
    keep = set(quarry_pad) | set(hut_pad) | {(0, z) for z in range(3, 14)}

    def top(x, z, t):
        if z > 2 and t < 0.8:
            return [("grass_block", 4), ("coarse_dirt", 2), ("gravel", 1), ("stone", 1)]
        return ROCKY_TOP

    surf = island_body(b, 15, 4203, top, sub=[("dirt", 1), ("gravel", 1), ("stone", 2)], rock=UNDER_DEEP, depth=15,
                       keep=keep)
    flat_pad(b, quarry_pad, [("cobblestone", 3), ("gravel", 2), ("andesite", 1)])
    flat_pad(b, hut_pad, [("grass_block", 3), ("coarse_dirt", 1)])
    quarry_ring = pad_ring(b, 5, -3, 9, 1, [("cobblestone", 3), ("mossy_cobblestone", 1)])
    hut_ring = pad_ring(b, 2, 4, 8, 10, [("dirt_path", 3), ("coarse_dirt", 1)])
    standing_sign(b, 10, 1, 2, 12, ["Quarry Pad", "", "Place a quarry", "on the gravel"])

    # rock outcrop with ores facing south
    rock = boulder(b, -2, 1, -9, 6, 6, 4, mix=[("stone", 4), ("andesite", 3), ("tuff", 2), ("cobblestone", 1)],
                   top=[("stone", 2), ("mossy_cobblestone", 1), ("moss_block", 1)], seed=77, moss=0.35, bury=2)
    ore_face(b, rock, "south", [("coal_ore", 4), ("iron_ore", 2), ("copper_ore", 2)], chance=0.28)
    ore_face(b, rock, "east", [("coal_ore", 2), ("iron_ore", 1)], chance=0.18)
    # mine mouth: 3 wide, 3 tall, 3 deep into the outcrop from its south face
    face_z = max(z for (x, y, z) in rock if x == -2 and y == 1)
    for dz in range(0, 4):
        z = face_z - dz
        for x in range(-3, 0):
            for y in range(1, 4):
                b.clear(x, y, z)
            b.set(x, 0, z, pick(b, [("gravel", 2), ("cobblestone", 1)]))
    zf = face_z + 1
    for x in (-4, 0):
        for y in range(1, 5):
            b.set(x, y, zf, "stripped_spruce_log[axis=y]")
    for x in range(-4, 1):
        b.set(x, 5, zf, "stripped_spruce_wood[axis=x]")
    b.set(-4, 4, zf + 1, "spruce_stairs[facing=west,half=top]")
    b.set(0, 4, zf + 1, "spruce_stairs[facing=east,half=top]")
    hang_lantern(b, -2, 5, zf, chain=0)
    wall_sign(b, -2, 5, zf + 1, "south", ["Deepcut Mine", "", "est. by you", ""])
    # a loaded ore tub inside the mouth
    b.set(-2, 1, face_z - 2, "hopper[facing=down,enabled=false]")
    b.set(-3, 1, face_z - 3, "coal_block")

    # tool shed (west) — lean-to
    sx1, sx2, sz1, sz2 = -12, -8, -1, 3
    for (x, z) in rect(sx1, sz1, sx2, sz2):
        b.set(x, 0, z, pick(b, PLANKS))
        for y in range(1, 5):
            b.clear(x, y, z)
    for (x, z) in ((sx1, sz1), (sx1, sz2), (sx2, sz1), (sx2, sz2)):
        for y in range(1, 4):
            b.set(x, y, z, "stripped_spruce_log[axis=y]")
    for z in range(sz1, sz2 + 1):
        b.set(sx1, 4, z, "spruce_slab[type=bottom]")
        b.set(sx1 + 1, 4, z, "spruce_slab[type=bottom]")
        b.set(sx1 + 2, 4, z, "spruce_slab[type=top]")
        b.set(sx1 + 3, 3, z, "spruce_slab[type=top]")
        b.set(sx2, 3, z, "spruce_slab[type=top]")
    for z in range(sz1 + 1, sz2):
        b.set(sx1, 1, z, pick(b, PLANKS))
        b.set(sx1, 2, z, pick(b, PLANKS))
    b.set(sx1 + 1, 1, sz1, "grindstone[face=floor,facing=east]")
    b.set(sx1 + 2, 1, sz1, "anvil[facing=east]")
    b.container(sx1 + 1, 1, sz2, "barrel[facing=up]")
    b.container(sx1 + 1, 1, 1, "chest[facing=east]")
    b.set(sx1 + 1, 1, 2, "crafting_table")
    b.set(sx1 + 2, 1, sz2, "furnace[facing=north]")
    b.set(sx1 + 2, 4, 1, "spruce_planks")
    hang_lantern(b, sx1 + 2, 4, 1, chain=0)

    # timber stack + ore heap near the quarry pad
    for x in range(10, 13):
        b.set(x, 1, 4, "stripped_spruce_log[axis=x]")
    b.set(11, 2, 4, "stripped_spruce_log[axis=x]")
    for (x, z, st) in [(10, -5, "coal_block"), (11, -5, "gravel"), (10, -6, "iron_ore"), (11, -6, "cobblestone"),
                       (10, -5, "coal_block")]:
        if b.has(x, 0, z):
            b.set(x, 1, z, st)
    b.set(10, 2, -5, "cobblestone_slab[type=bottom]")
    lantern_post(b, 4, 1, 1)
    lantern_post(b, -1, 1, 11)
    small_spruce(b, -10, 1, 8, 7)
    small_spruce(b, 12, 1, 9, 6)

    decorate_surface(b, surf, plants=0.25, flowers=0.03,
                     skip=set(hut_pad) | set(quarry_pad) | set(quarry_ring) | set(hut_ring))
    b.meta.update(spawn=(0, 1, 12, 180.0), pads={"quarry_housing": (7, -1, 2, 2), "storage_hut": (5, 7, 3, 3)})
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ------------------------------------------------------------------------------------------------
# Tide Dock — sandy isle with an enclosed lagoon, a plank dock, a net shack and a moored rowboat
# ------------------------------------------------------------------------------------------------
@starter("starter_tide")
def tide():
    b = Build("starter_tide", seed=419)
    hut_pad = rect(4, -10, 10, -4)   # 7x7 Storage Hut site, centre (7, -7)
    lagoon = [(x, z) for x in range(-6, 8) for z in range(0, 11)
              if ((x - 1) / 5.2) ** 2 + ((z - 5) / 4.2) ** 2 <= 1.0]
    keep = set(hut_pad) | {(x, z) for x in range(-8, 10) for z in range(-2, 13)
                           if ((x - 1) / 7.5) ** 2 + ((z - 5) / 6.8) ** 2 <= 1.0}

    def top(x, z, t):
        if z < 0 and t < 0.62:
            return [("grass_block", 5), ("sand", 1), ("coarse_dirt", 1)]
        return [("sand", 9), ("gravel", 1)] if t > 0.8 else "sand"

    surf = island_body(b, 15, 5309, top, sub=[("sandstone", 3), ("sand", 2)], rock=UNDER, depth=13, keep=keep)
    flat_pad(b, hut_pad, [("grass_block", 3), ("sand", 1)])
    hut_ring = pad_ring(b, 4, -10, 10, -4, [("smooth_sandstone", 3), ("cut_sandstone", 1)])
    standing_sign(b, 3, 1, -3, 10, ["Hut Site", "", "Storage Hut", "goes here"])

    # lagoon basin: water y 0..-2, floor -3, sand rim everywhere around it (enclosed = no spills)
    lag = set(lagoon)
    for (x, z) in lagoon:
        for y in range(-2, 1):
            b.set(x, y, z, "water[level=0]")
        b.set(x, -3, z, pick(b, [("sand", 3), ("gravel", 2), ("clay", 1)]))
        b.set(x, -4, z, "sandstone")
        for y in range(1, 8):
            b.clear(x, y, z)
    for (x, z) in lagoon:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            q = (x + dx, z + dz)
            if q not in lag:
                for y in range(-3, 1):
                    if not b.has(q[0], y, q[1]) or bname(b.get(q[0], y, q[1])) == "minecraft:water":
                        b.set(q[0], y, q[1], "sand" if y == 0 else "sandstone")
    # seabed life
    for (x, z) in lagoon:
        r = b.rng.random()
        if r < 0.18:
            b.set(x, -2, z, "seagrass")
        elif r < 0.24:
            b.set(x, -2, z, "sea_pickle[pickles=3,waterlogged=true]")
    for (x, z) in [(-3, 6), (4, 8), (-2, 3)]:
        if (x, z) in lag:
            b.set(x, -2, z, "kelp_plant")
            b.set(x, -1, z, "kelp[age=25]")

    # dock: deck at y=1 from the north shore out over the lagoon, posts down to the floor
    for z in range(-1, 8):
        for x in (0, 1):
            b.set(x, 1, z, pick(b, PLANKS))
            for y in range(2, 5):
                b.clear(x, y, z)
    for (x, z) in ((-1, 3), (2, 3), (-1, 7), (2, 7)):
        b.set(x, 1, z, "spruce_fence")
        for y in range(-2, 1):
            b.set(x, y, z, "spruce_fence[waterlogged=true]")
    b.set(-1, 2, 7, "lantern[hanging=false]")
    b.set(2, 2, 7, "lantern[hanging=false]")
    b.set(-1, 2, 3, "spruce_fence")
    b.set(-1, 3, 3, "lantern[hanging=false]")
    b.container(1, 2, 0, "barrel[facing=up]")

    # moored rowboat alongside the dock (waterlogged slabs/stairs sitting in the water)
    for z in range(3, 7):
        b.set(4, 0, z, "spruce_slab[type=bottom,waterlogged=true]")
        b.set(5, 0, z, "spruce_slab[type=bottom,waterlogged=true]")
    b.set(4, 0, 2, "spruce_stairs[facing=south,half=bottom,waterlogged=true]")
    b.set(5, 0, 2, "spruce_stairs[facing=south,half=bottom,waterlogged=true]")
    b.set(4, 0, 7, "spruce_stairs[facing=north,half=bottom,waterlogged=true]")
    b.set(5, 0, 7, "spruce_stairs[facing=north,half=bottom,waterlogged=true]")
    b.set(4, 1, 4, "spruce_trapdoor[facing=north,half=bottom,open=false]")

    # net shack (west): 5x5, door facing east
    x1, x2, z1, z2 = -13, -9, -4, 0
    for (x, z) in rect(x1, z1, x2, z2):
        b.set(x, 0, z, pick(b, PLANKS))
        for y in range(1, 9):
            b.clear(x, y, z)
    for y in range(1, 4):
        for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
            b.set(x, y, z, "stripped_spruce_log[axis=y]")
        for x in range(x1 + 1, x2):
            b.set(x, y, z1, pick(b, PLANKS))
            b.set(x, y, z2, pick(b, PLANKS))
        for z in range(z1 + 1, z2):
            b.set(x1, y, z, pick(b, PLANKS))
            b.set(x2, y, z, pick(b, PLANKS))
    b.door(x2, 1, (z1 + z2) // 2, "spruce", "east")
    b.set(x1, 2, (z1 + z2) // 2, "glass_pane")
    b.set(x1 + 2, 2, z1, "glass_pane")
    gable(b, x1 - 1, x2 + 1, z1 - 1, z2 + 1, 4, stairs="spruce_stairs", fill="spruce_planks", ridge_axis="x",
          slab="spruce_slab[type=bottom]")
    gable_ends(b, x1 - 1, x2 + 1, z1 - 1, z2 + 1, 4, "spruce_planks", ridge_axis="x")
    b.container(x1 + 1, 1, z1 + 1, "chest[facing=south]")
    b.set(x1 + 2, 1, z1 + 1, "crafting_table")
    b.set(x1 + 3, 1, z1 + 1, "furnace[facing=south]")
    b.container(x1 + 1, 1, z2 - 1, "barrel[facing=up]")
    for x in range(x1, x2 + 1):
        b.set(x, 4, (z1 + z2) // 2, "stripped_spruce_wood[axis=x]")
    hang_lantern(b, x1 + 2, 4, (z1 + z2) // 2, chain=0)
    # net rack + lobster pots outside the shack
    for x in range(x1, x1 + 3):
        b.set(x, 1, z2 + 2, "spruce_fence")
        b.set(x, 2, z2 + 2, "cobweb")
    scaffold_column(b, x2 + 2, 1, z2 + 2, 2)
    b.set(x2 + 3, 1, z2 + 2, "red_wool")

    # birches and grass up north, dune plants on the sand
    small_birch(b, -4, 1, -9, 5)
    small_birch(b, -9, 1, -8, 6)
    small_oak(b, -3, 1, -13, h=4)
    for (x, z), h in surf.items():
        st = b.get(x, h, z)
        if st and bname(st) == "minecraft:sand" and not b.has(x, h + 1, z) and b.rng.random() < 0.05:
            b.set(x, h + 1, z, "dead_bush")
    for (x, z) in [(-5, 1), (7, 2), (-5, 9)]:
        if b.get(x, 0, z) and bname(b.get(x, 0, z)) == "minecraft:sand" and not b.has(x, 1, z):
            b.set(x, 1, z, "sugar_cane[age=0]")
            b.set(x, 2, z, "sugar_cane[age=0]")
    lantern_post(b, -2, 1, -3)
    decorate_surface(b, surf, plants=0.3, flowers=0.05, skip=set(hut_pad) | set(hut_ring) | lag)
    b.meta.update(spawn=(0, 1, -12, 0.0), pads={"storage_hut": (7, -7, 3, 3)})
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ------------------------------------------------------------------------------------------------
# Guild Harbour — plaza, well, banner poles, project board, reserved project sites, gatehouse, sky pier
# ------------------------------------------------------------------------------------------------
HALL_SITE = (0, -9)      # centre of the 15x15 Guild Hall site
BEACON_SITE = (13, -1)   # centre of the 7x7 Harbour Beacon site
BOARD = (0, 1, 1)        # the project-board lectern


@starter("starter_guild")
def guild():
    b = Build("starter_guild", seed=523)
    plaza = rect(-6, 2, 6, 12)
    hall = rect(HALL_SITE[0] - 7, HALL_SITE[1] - 7, HALL_SITE[0] + 7, HALL_SITE[1] + 7)
    beacon = rect(BEACON_SITE[0] - 3, BEACON_SITE[1] - 3, BEACON_SITE[0] + 3, BEACON_SITE[1] + 3)
    road = [(x, z) for x in range(-1, 2) for z in range(12, 20)]
    keep = set(plaza) | set(hall) | set(beacon) | set(road)

    surf = island_body(b, 21, 6007, lambda x, z, t: EDGE if t > 0.88 else GRASSY, sub="dirt", rock=UNDER,
                       depth=17, keep=keep)
    # sites: flattened coarse dirt with corner posts (the project pastes land here)
    flat_pad(b, hall, [("coarse_dirt", 3), ("gravel", 1), ("packed_mud", 1)])
    flat_pad(b, beacon, [("coarse_dirt", 3), ("gravel", 1)])
    for (x1, z1, x2, z2) in ((HALL_SITE[0] - 7, HALL_SITE[1] - 7, HALL_SITE[0] + 7, HALL_SITE[1] + 7),
                             (BEACON_SITE[0] - 3, BEACON_SITE[1] - 3, BEACON_SITE[0] + 3, BEACON_SITE[1] + 3)):
        for (x, z) in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
            b.set(x, 1, z, "stripped_spruce_log[axis=y]")
            b.set(x, 2, z, "stripped_spruce_log[axis=y]")
            b.set(x, 3, z, "lantern[hanging=false]")

    # plaza + well
    flat_pad(b, plaza, PAVERS)
    for (x, z) in road:
        b.set(x, 0, z, pick(b, PAVERS))
        for y in range(1, 8):
            b.clear(x, y, z)
    wx, wz = 0, 7
    for (x, z) in rect(wx - 2, wz - 2, wx + 2, wz + 2):
        b.set(x, 1, z, pick(b, STONE_BRICK))
    for (x, z) in rect(wx - 1, wz - 1, wx + 1, wz + 1):
        b.clear(x, 1, z)
        for y in range(-3, 1):
            b.set(x, y, z, "water[level=0]")
        b.set(x, -4, z, "stone_bricks")
        for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
            pass
    for (x, z) in rect(wx - 2, wz - 2, wx + 2, wz + 2):
        for y in (-3, -2, -1):
            if not (wx - 1 <= x <= wx + 1 and wz - 1 <= z <= wz + 1):
                b.set(x, y, z, "stone_bricks")
    for (x, z) in ((wx - 2, wz - 2), (wx + 2, wz - 2), (wx - 2, wz + 2), (wx + 2, wz + 2)):
        b.set(x, 2, z, "spruce_fence")
        b.set(x, 3, z, "spruce_fence")
    for (x, z) in rect(wx - 2, wz - 2, wx + 2, wz + 2):
        b.set(x, 4, z, "spruce_slab[type=bottom]")
    b.set(wx, 4, wz, "spruce_planks")
    hang_lantern(b, wx, 4, wz, chain=1)

    # banner poles at the plaza corners
    for (x, z, rot) in ((-6, 2, 8), (6, 2, 8), (-6, 12, 0), (6, 12, 0)):
        for y in range(1, 5):
            b.set(x, y, z, "spruce_fence")
        b.banner(x, 5, z, f"white_banner[rotation={rot}]")

    # project board: posts, plank board with signs, lectern in front
    bx, by, bz = BOARD
    for x in (bx - 2, bx + 2):
        for y in range(1, 4):
            b.set(x, y, bz - 1, "stripped_spruce_log[axis=y]")
    for x in range(bx - 1, bx + 2):
        b.set(x, 2, bz - 1, "spruce_planks")
        b.set(x, 3, bz - 1, "spruce_planks")
    for x in range(bx - 2, bx + 3):
        b.set(x, 4, bz - 1, "spruce_slab[type=bottom]")
    wall_sign(b, bx, 3, bz, "south", ["Guild", "Projects", "", "Use the lectern"])
    wall_sign(b, bx - 1, 2, bz, "south", ["Hall site", "north"])
    wall_sign(b, bx + 1, 2, bz, "south", ["Beacon site", "east"])
    b.lectern(bx, by, bz, "south")

    # gatehouse arch at the spawn road
    gz = 16
    for x in (-3, 3):
        for y in range(1, 6):
            b.set(x, y, gz, pick(b, STONE_BRICK))
            b.set(x, y, gz + 1, pick(b, STONE_BRICK))
    for x in range(-3, 4):
        b.set(x, 6, gz, pick(b, STONE_BRICK))
        b.set(x, 6, gz + 1, pick(b, STONE_BRICK))
    for x in (-2, 2):
        b.set(x, 5, gz, "stone_brick_stairs[facing=" + ("east" if x < 0 else "west") + ",half=top]")
        b.set(x, 5, gz + 1, "stone_brick_stairs[facing=" + ("east" if x < 0 else "west") + ",half=top]")
    for x in range(-3, 4):
        b.set(x, 7, gz, "stone_brick_slab[type=bottom]")
        b.set(x, 7, gz + 1, "stone_brick_slab[type=bottom]")
    wall_sign(b, 0, 6, gz + 2, "south", ["", "Guild Harbour", "", ""], glow=True)
    b.set(-3, 7, gz + 1, "lantern[hanging=false]")
    b.set(3, 7, gz + 1, "lantern[hanging=false]")

    # sky pier (west edge) over the void
    edge_x = min(x for (x, z) in surf if z == 6)
    for x in range(edge_x - 7, edge_x + 2):
        for z in (5, 6, 7):
            b.set(x, 0, z, pick(b, PLANKS))
    for x in range(edge_x - 7, edge_x + 1, 3):
        b.set(x, 1, 5, "spruce_fence")
        b.set(x, 1, 7, "spruce_fence")
    b.set(edge_x - 7, 2, 5, "lantern[hanging=false]")
    b.set(edge_x - 7, 2, 7, "lantern[hanging=false]")
    for x in range(edge_x - 7, edge_x + 1):
        b.set(x, -1, 6, "stripped_spruce_wood[axis=x]")

    # crate yard (east of the plaza)
    for (x, z) in [(9, 8), (10, 8), (9, 9), (11, 10)]:
        b.container(x, 1, z, "barrel[facing=up]")
    b.set(10, 2, 8, "barrel[facing=up]")
    b.set(10, 1, 10, "hay_block[axis=y]")

    # trees round the rim
    big_tree(b, -14, 1, 6, h=9, trunk=1, wood="oak", seed=21, crown=4, branches=3)
    small_spruce(b, -13, 1, -12, 8)
    small_spruce(b, 14, 1, 9, 7)
    small_oak(b, 12, 1, 14, h=4)
    small_birch(b, -10, 1, 14, 5)
    for (x, z) in ((-8, 3), (8, 3), (-8, 11), (8, 11)):
        lantern_post(b, x, 1, z)

    decorate_surface(b, surf, plants=0.3, flowers=0.05, skip=set(plaza) | set(hall) | set(beacon) | set(road))
    b.meta.update(spawn=(0, 1, 19, 180.0), sites={"guild_hall": HALL_SITE, "harbour_beacon": BEACON_SITE},
                  board=BOARD)
    tidy(b)
    b.anchor = (0, 0, 0)
    return b
