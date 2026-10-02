"""Placeable island structures + guild project stages.

Ground layer y = 0 (the block the player looks at when placing), anchor = footprint centre, front = south (+z).
Ports (mirrored in StructureType.java):
  * mill, forge, quarry_housing: output chute on the front edge centre (0, 1, 2); the belt starts on the cell
    in front of it, (0, 1, 3).
  * storage_hut, depot, mill, forge: accept a belt that runs into any wall / edge of the footprint.
"""
from __future__ import annotations

from aeprops.core import Build
from aeprops.style import hang_lantern
from aeg.common import (pick, rect, wall_sign, standing_sign, gable, gable_ends, scaffold_column, PAVERS,
                        STONE_BRICK, PLASTER, PLANKS)
from aeg.kit import tidy

STRUCTURES = {}


def structure(name):
    def deco(fn):
        STRUCTURES[name] = fn
        return fn
    return deco


def _finish(b: Build):
    tidy(b)
    b.anchor = (0, 0, 0)
    return b


# ------------------------------------------------------------------------------------------------
@structure("st_storage_hut")
def storage_hut():
    b = Build("st_storage_hut", seed=1001)
    for (x, z) in rect(-3, -3, 3, 3):
        edge = abs(x) == 3 or abs(z) == 3
        b.set(x, 0, z, pick(b, STONE_BRICK) if edge else pick(b, PLANKS))
    for y in range(1, 5):
        for (x, z) in rect(-3, -3, 3, 3):
            if not (abs(x) == 3 or abs(z) == 3):
                continue
            corner = abs(x) == 3 and abs(z) == 3
            if corner:
                b.set(x, y, z, "stripped_spruce_log[axis=y]")
            elif y == 1:
                b.set(x, y, z, pick(b, STONE_BRICK))
            elif y == 4:
                b.set(x, y, z, "stripped_spruce_wood[axis=" + ("x" if abs(z) == 3 else "z") + "]")
            else:
                b.set(x, y, z, pick(b, PLASTER))
    # windows
    for (x, z) in ((-3, 0), (3, 0), (-1, -3), (1, -3)):
        b.set(x, 2, z, "glass_pane")
        b.set(x, 3, z, "glass_pane")
    # door
    b.door(0, 1, 3, "spruce", "south")
    wall_sign(b, 0, 3, 4, "south", ["", "Storage Hut", "", ""])
    # roof
    gable(b, -4, 4, -4, 4, 5, stairs="spruce_stairs", fill="spruce_planks", ridge_axis="x",
          slab="spruce_slab[type=bottom]")
    gable_ends(b, -4, 4, -4, 4, 5, "spruce_planks", ridge_axis="x")
    # inside: barrel wall + chests
    for x in range(-2, 3):
        b.container(x, 1, -2, "barrel[facing=south]")
        if x != 0:
            b.container(x, 2, -2, "barrel[facing=south]")
    b.container(-2, 1, 1, "chest[facing=east]")
    b.container(2, 1, 1, "chest[facing=west]")
    for x in range(-3, 4):
        b.set(x, 5, 0, "stripped_spruce_wood[axis=x]")
    hang_lantern(b, 0, 5, 0, chain=0)
    # outside crates by the door
    b.container(-2, 1, 4, "barrel[facing=up]")
    b.set(2, 1, 4, "composter[level=0]")
    b.set(-2, 0, 4, "spruce_planks")
    b.set(2, 0, 4, "spruce_planks")
    b.resolve()
    return _finish(b)


# ------------------------------------------------------------------------------------------------
@structure("st_workshop")
def workshop():
    b = Build("st_workshop", seed=1002)
    for (x, z) in rect(-4, -3, 4, 3):
        b.set(x, 0, z, pick(b, PLANKS) if abs(x) < 4 and abs(z) < 3 else pick(b, STONE_BRICK))
    posts = [(-4, -3), (0, -3), (4, -3), (-4, 3), (0, 3), (4, 3)]
    for (x, z) in posts:
        for y in range(1, 5):
            b.set(x, y, z, "stripped_spruce_log[axis=y]")
    # back wall with tool rack
    for x in range(-3, 4):
        if x == 0:
            continue
        for y in range(1, 4):
            b.set(x, y, -3, pick(b, PLANKS))
    for x in (-3, -2, 2, 3):
        b.set(x, 2, -2, "spruce_trapdoor[facing=south,half=top,open=true]")
    # top beams
    for x in range(-4, 5):
        b.set(x, 4, -3, "stripped_spruce_wood[axis=x]")
        b.set(x, 4, 3, "stripped_spruce_wood[axis=x]")
    for z in range(-2, 3):
        b.set(-4, 4, z, "stripped_spruce_wood[axis=z]")
        b.set(4, 4, z, "stripped_spruce_wood[axis=z]")
    gable(b, -5, 5, -4, 4, 5, stairs="dark_oak_stairs", fill="dark_oak_planks", ridge_axis="x",
          slab="dark_oak_slab[type=bottom]")
    gable_ends(b, -5, 5, -4, 4, 5, "spruce_planks", ridge_axis="x")
    # stations
    b.lectern(0, 1, 1, "south")                       # the blueprint desk
    b.set(-3, 1, -2, "crafting_table")
    b.set(-2, 1, -2, "smithing_table")
    b.set(2, 1, -2, "stonecutter[facing=south]")
    b.set(3, 1, -2, "grindstone[face=floor,facing=south]")
    b.set(-3, 1, 1, "anvil[facing=east]")
    b.container(3, 1, 1, "barrel[facing=up]")
    b.container(3, 2, 1, "barrel[facing=up]")
    b.set(3, 1, 2, "scaffolding[distance=0,bottom=false]")
    b.set(-2, 5, 0, "spruce_planks")
    hang_lantern(b, -2, 5, 0, chain=0)
    b.set(2, 5, 0, "spruce_planks")
    hang_lantern(b, 2, 5, 0, chain=0)
    wall_sign(b, 0, 3, -2, "south", ["", "Workshop", "Blueprints at", "the lectern"])
    b.resolve()
    return _finish(b)


# ------------------------------------------------------------------------------------------------
@structure("st_depot")
def depot():
    b = Build("st_depot", seed=1003)
    for (x, z) in rect(-1, -1, 1, 1):
        b.set(x, 0, z, "spruce_planks")
    b.container(-1, 1, -1, "barrel[facing=up]")
    b.container(0, 1, -1, "barrel[facing=up]")
    b.container(1, 1, -1, "barrel[facing=up]")
    b.container(-1, 2, -1, "barrel[facing=up]")
    b.set(1, 1, 0, "composter[level=0]")
    b.set(-1, 1, 0, "hay_block[axis=y]")
    b.set(1, 2, -1, "spruce_slab[type=bottom]")
    standing_sign(b, 0, 1, 1, 0, ["", "Depot", "", ""])
    return _finish(b)


# ------------------------------------------------------------------------------------------------
@structure("st_mill")
def mill():
    b = Build("st_mill", seed=1004)
    for (x, z) in rect(-2, -2, 2, 2):
        b.set(x, 0, z, pick(b, [("cobblestone", 3), ("stone_bricks", 2), ("andesite", 1)]))
    for (x, z) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(1, 4):
            b.set(x, y, z, "stripped_oak_log[axis=y]")
    for x in range(-2, 3):
        b.set(x, 4, -2, "stripped_oak_wood[axis=x]")
        b.set(x, 4, 2, "stripped_oak_wood[axis=x]")
    for z in range(-1, 2):
        b.set(-2, 4, z, "stripped_oak_wood[axis=z]")
        b.set(2, 4, z, "stripped_oak_wood[axis=z]")
    gable(b, -3, 3, -3, 3, 5, stairs="dark_oak_stairs", fill="dark_oak_planks", ridge_axis="x",
          slab="dark_oak_slab[type=bottom]")
    # millstone plinth; the spinning stone is a BlockDisplay spawned by the plugin above it at (0, 2, 0)
    b.set(0, 1, 0, "smooth_stone")
    b.set(-1, 1, 0, "smooth_stone_slab[type=bottom]")
    b.set(1, 1, 0, "smooth_stone_slab[type=bottom]")
    # intake hopper at the back, output chute at the front
    b.set(0, 1, -2, "hopper[facing=south,enabled=true]")
    b.set(0, 1, 2, "hopper[facing=south,enabled=true]")
    # grain sacks
    b.set(-1, 1, -1, "brown_wool")
    b.set(1, 1, 1, "hay_block[axis=y]")
    b.set(1, 1, -1, "brown_wool")
    standing_sign(b, -1, 1, 2, 0, ["", "Mill", "raw -> compressed", ""])
    b.resolve()
    return _finish(b)


# ------------------------------------------------------------------------------------------------
@structure("st_forge")
def forge():
    b = Build("st_forge", seed=1005)
    for (x, z) in rect(-2, -2, 2, 2):
        b.set(x, 0, z, pick(b, [("stone_bricks", 3), ("cracked_stone_bricks", 1), ("polished_blackstone_bricks", 1)]))
    b.set(0, 0, 0, "magma_block")
    # chimney (back-left)
    for y in range(1, 7):
        b.set(-2, y, -2, pick(b, [("bricks", 3), ("stone_bricks", 1)]))
        b.set(-1, y, -2, pick(b, [("bricks", 3), ("stone_bricks", 1)]))
    b.campfire(-2, 7, -2)
    b.campfire(-1, 7, -2)
    # back wall
    for x in range(0, 3):
        for y in range(1, 3):
            b.set(x, y, -2, pick(b, STONE_BRICK))
    b.set(0, 1, -1, "blast_furnace[facing=south,lit=true]")
    b.set(1, 1, -1, "anvil[facing=east]")
    b.set(-1, 1, 0, "smithing_table")
    # posts + half roof over the front
    for (x, z) in ((2, 2), (-2, 2), (2, -2)):
        for y in range(1, 4):
            b.set(x, y, z, "polished_blackstone_wall")
    for (x, z) in rect(-2, -1, 2, 2):
        if (x, z) not in ((-2, -1), (-1, -1)):
            b.set(x, 4, z, "deepslate_tile_slab[type=bottom]")
    b.set(0, 1, 2, "hopper[facing=south,enabled=true]")
    standing_sign(b, -1, 1, 2, 0, ["", "Forge", "compressed ->", "compacted"])
    b.resolve()
    return _finish(b)


# ------------------------------------------------------------------------------------------------
@structure("st_quarry_housing")
def quarry_housing():
    """Headframe around a quarry. Pasted NON-destructively (only fills air) around the quarry stand, which
    stands on the centre block. The centre column above y=0 stays open for the stand."""
    b = Build("st_quarry_housing", seed=1006)
    for (x, z) in rect(-2, -2, 2, 2):
        edge = abs(x) == 2 or abs(z) == 2
        b.set(x, 0, z, pick(b, [("cobblestone", 3), ("gravel", 1)]) if edge else pick(b, [("gravel", 2), ("cobblestone", 1)]))
    for (x, z) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(1, 5):
            b.set(x, y, z, "stripped_spruce_log[axis=y]")
    for x in range(-2, 3):
        b.set(x, 5, -2, "stripped_spruce_wood[axis=x]")
        b.set(x, 5, 2, "stripped_spruce_wood[axis=x]")
    for z in range(-1, 2):
        b.set(-2, 5, z, "stripped_spruce_wood[axis=z]")
        b.set(2, 5, z, "stripped_spruce_wood[axis=z]")
    for x in range(-1, 2):
        b.set(x, 5, 0, "stripped_spruce_wood[axis=x]")
    b.set(0, 4, 0, "chain[axis=y]")
    # braces
    b.set(-1, 4, -2, "spruce_stairs[facing=east,half=top]")
    b.set(1, 4, -2, "spruce_stairs[facing=west,half=top]")
    b.set(-1, 4, 2, "spruce_stairs[facing=east,half=top]")
    b.set(1, 4, 2, "spruce_stairs[facing=west,half=top]")
    # output chute (front) + spoil heap (back)
    b.set(0, 1, 2, "hopper[facing=south,enabled=true]")
    b.set(-1, 1, -2, "gravel")
    b.set(1, 1, -2, "cobblestone_slab[type=bottom]")
    b.set(0, 1, -2, "cobblestone")
    b.set(-2, 6, 2, "lantern[hanging=false]")
    b.set(2, 6, -2, "lantern[hanging=false]")
    return _finish(b)


# ------------------------------------------------------------------------------------------------
# Guild project site markers + stages
# ------------------------------------------------------------------------------------------------
@structure("gp_site_hall")
def site_hall():
    b = Build("gp_site_hall", seed=2001)
    for (x, z) in ((-7, -7), (7, -7), (-7, 7), (7, 7)):
        b.set(x, 1, z, "stripped_spruce_log[axis=y]")
        b.set(x, 2, z, "stripped_spruce_log[axis=y]")
        b.set(x, 3, z, "lantern[hanging=false]")
    for x in range(-6, 7, 3):
        b.set(x, 1, -7, "spruce_fence")
        b.set(x, 1, 7, "spruce_fence")
    standing_sign(b, 0, 1, 7, 0, ["Guild Hall", "", "site claimed", ""])
    b.set(0, 0, 0, "coarse_dirt")
    return _finish(b)


@structure("gp_site_beacon")
def site_beacon():
    b = Build("gp_site_beacon", seed=2002)
    for (x, z) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        b.set(x, 1, z, "stripped_spruce_log[axis=y]")
        b.set(x, 2, z, "stripped_spruce_log[axis=y]")
        b.set(x, 3, z, "lantern[hanging=false]")
    standing_sign(b, 0, 1, 3, 0, ["Harbour", "Beacon", "site claimed", ""])
    b.set(0, 0, 0, "coarse_dirt")
    return _finish(b)


def _hall_floor_and_base(b: Build):
    for (x, z) in rect(-7, -7, 7, 7):
        b.set(x, 0, z, pick(b, PAVERS))
    for (x, z) in rect(-7, -7, 7, 7):
        if abs(x) == 7 or abs(z) == 7:
            if z == 7 and abs(x) <= 1:
                continue
            b.set(x, 1, z, pick(b, STONE_BRICK))


@structure("gp_hall_1")
def hall_1():
    b = Build("gp_hall_1", seed=2101)
    _hall_floor_and_base(b)
    for (x, z) in ((-7, -7), (7, -7), (-7, 7), (7, 7)):
        for y in range(2, 4):
            b.set(x, y, z, pick(b, STONE_BRICK))
    for (x, z) in ((-7, 0), (7, 0), (0, -7)):
        b.set(x, 2, z, pick(b, STONE_BRICK))
    scaffold_column(b, -6, 1, -6, 5)
    scaffold_column(b, 6, 1, -6, 5)
    scaffold_column(b, -6, 1, 6, 3)
    for x in range(-3, 0):
        b.set(x, 1, 3, "stripped_spruce_log[axis=x]")
    b.set(-2, 2, 3, "stripped_spruce_log[axis=x]")
    for x in (3, 4):
        b.set(x, 1, 3, "stone_bricks")
    b.set(3, 2, 3, "stone_brick_slab[type=bottom]")
    # crane
    for y in range(1, 6):
        b.set(3, y, -3, "spruce_fence")
    b.set(4, 5, -3, "spruce_fence")
    b.set(5, 5, -3, "spruce_fence")
    b.set(5, 4, -3, "chain[axis=y]")
    b.set(5, 3, -3, "chain[axis=y]")
    b.set(5, 2, -3, "lantern[hanging=true]")
    standing_sign(b, 0, 1, 5, 0, ["Guild Hall", "stage 1", "Foundations", ""])
    b.resolve()
    return _finish(b)


def _hall_walls(b: Build, scaffold=True):
    _hall_floor_and_base(b)
    for y in range(1, 6):
        for (x, z) in rect(-7, -7, 7, 7):
            if not (abs(x) == 7 or abs(z) == 7):
                continue
            if z == 7 and abs(x) <= 1 and y <= 3:
                continue
            post = (abs(x) == 7 and abs(z) == 7) or (abs(z) == 7 and x in (-4, 4)) or (abs(x) == 7 and z in (-4, 0, 4))
            if post:
                b.set(x, y, z, "stripped_spruce_log[axis=y]")
            elif y <= 2:
                b.set(x, y, z, pick(b, STONE_BRICK))
            else:
                b.set(x, y, z, pick(b, PLASTER))
    for (x, z) in rect(-7, -7, 7, 7):
        if abs(x) == 7 or abs(z) == 7:
            ax = "x" if abs(z) == 7 else "z"
            b.set(x, 6, z, f"stripped_spruce_wood[axis={ax}]")
    # windows
    for (x, z) in ((-7, -2), (-7, 2), (7, -2), (7, 2), (-2, -7), (2, -7), (-5, 7), (5, 7)):
        b.set(x, 3, z, "glass_pane")
        b.set(x, 4, z, "glass_pane")
    # entrance: doorway with arch
    b.door(0, 1, 7, "spruce", "south")
    b.set(-1, 1, 7, pick(b, STONE_BRICK))
    b.set(1, 1, 7, pick(b, STONE_BRICK))
    b.set(-1, 2, 7, pick(b, STONE_BRICK))
    b.set(1, 2, 7, pick(b, STONE_BRICK))
    b.set(-1, 3, 7, "stone_brick_stairs[facing=east,half=top]")
    b.set(1, 3, 7, "stone_brick_stairs[facing=west,half=top]")
    b.set(0, 3, 7, pick(b, STONE_BRICK))
    if scaffold:
        scaffold_column(b, -5, 1, -6, 6)
        scaffold_column(b, 5, 1, -6, 6)
        scaffold_column(b, -6, 1, 5, 4)


@structure("gp_hall_2")
def hall_2():
    b = Build("gp_hall_2", seed=2102)
    _hall_walls(b, scaffold=True)
    standing_sign(b, 3, 1, 5, 0, ["Guild Hall", "stage 2", "Walls", ""])
    b.resolve()
    return _finish(b)


@structure("gp_hall_3")
def hall_3():
    b = Build("gp_hall_3", seed=2103)
    _hall_walls(b, scaffold=False)
    # cross beams + roof
    for x in range(-6, 7):
        b.set(x, 6, -3, "stripped_spruce_wood[axis=x]")
        b.set(x, 6, 3, "stripped_spruce_wood[axis=x]")
    gable(b, -8, 8, -8, 8, 7, stairs="deepslate_tile_stairs", fill="deepslate_tiles", ridge_axis="z",
          slab="deepslate_tile_slab[type=bottom]")
    gable_ends(b, -8, 8, -8, 8, 7, PLASTER, ridge_axis="z")
    # chimney through the east slope, smoking
    for y in range(7, 15):
        b.set(5, y, -4, pick(b, [("bricks", 3), ("stone_bricks", 1)]))
    b.campfire(5, 15, -4)
    # interior: long table, benches, lectern, chests, lanterns, bell
    for z in range(-4, 4):
        b.set(0, 1, z, "spruce_slab[type=top]")
        b.set(-1, 1, z, "spruce_stairs[facing=east,half=bottom]")
        b.set(1, 1, z, "spruce_stairs[facing=west,half=bottom]")
    b.lectern(0, 1, -6, "south")
    for z in (-2, 0, 2):
        b.container(-6, 1, z, "chest[facing=east]")
        b.container(6, 1, z, "chest[facing=west]")
    for (x, z) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        b.set(x, 5, z, "lantern[hanging=true]")
    # facade: banners + name sign
    for x in (-4, 4):
        b.banner(x, 5, 8, "white_wall_banner[facing=south]")
    wall_sign(b, 0, 4, 8, "south", ["", "Guild Hall", "", ""], glow=True)
    b.resolve()
    return _finish(b)


@structure("gp_beacon_1")
def beacon_1():
    b = Build("gp_beacon_1", seed=2201)
    _beacon_plinth(b)
    scaffold_column(b, 3, 1, 3, 4)
    standing_sign(b, 0, 3, 2, 0, ["Harbour Beacon", "stage 1", "Plinth", ""])
    b.resolve()
    return _finish(b)


def _beacon_plinth(b: Build):
    for (x, z) in rect(-3, -3, 3, 3):
        b.set(x, 0, z, pick(b, STONE_BRICK))
    for (x, z) in rect(-3, -3, 3, 3):
        if abs(x) == 3 or abs(z) == 3:
            if abs(x) == 3 and abs(z) == 3:
                b.set(x, 1, z, "stone_bricks")
            else:
                f = "south" if z == -3 else "north" if z == 3 else "east" if x == -3 else "west"
                b.set(x, 1, z, f"stone_brick_stairs[facing={f},half=bottom]")
        else:
            b.set(x, 1, z, pick(b, STONE_BRICK))
    for (x, z) in rect(-2, -2, 2, 2):
        b.set(x, 2, z, "chiseled_stone_bricks" if (x, z) == (0, 0) else pick(b, STONE_BRICK))


def _beacon_tower(b: Build):
    _beacon_plinth(b)
    for y in range(3, 15):
        band = y in (6, 10, 14)
        for (x, z) in rect(-1, -1, 1, 1):
            b.set(x, y, z, "deepslate_bricks" if band else pick(b, STONE_BRICK))
    for y in (5, 9, 12):
        b.set(0, y, 1, "iron_bars")
        b.set(0, y, -1, "iron_bars")
    for (x, z, f) in ((0, 2, "north"), (0, -2, "south"), (2, 0, "west"), (-2, 0, "east")):
        b.set(x, 3, z, f"stone_brick_stairs[facing={f},half=bottom]")


@structure("gp_beacon_2")
def beacon_2():
    b = Build("gp_beacon_2", seed=2202)
    _beacon_tower(b)
    scaffold_column(b, 2, 3, 2, 10)
    b.resolve()
    return _finish(b)


@structure("gp_beacon_3")
def beacon_3():
    b = Build("gp_beacon_3", seed=2203)
    _beacon_tower(b)
    # gallery
    for (x, z) in rect(-2, -2, 2, 2):
        b.set(x, 15, z, pick(b, STONE_BRICK))
    for (x, z) in rect(-2, -2, 2, 2):
        if abs(x) == 2 or abs(z) == 2:
            f = "north" if z == 2 else "south" if z == -2 else "west" if x == 2 else "east"
            b.set(x, 14, z, f"stone_brick_stairs[facing={f},half=top]")
    # lamp room
    for (x, z) in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(16, 19):
            b.set(x, y, z, "polished_blackstone_wall")
    for y in (16, 17):
        b.set(0, y, 0, "sea_lantern")
    b.set(0, 18, 0, "glowstone")
    for (x, z) in rect(-2, -2, 2, 2):
        if (abs(x) == 2) != (abs(z) == 2):
            b.set(x, 17, z, "glass_pane")
    for (x, z) in rect(-2, -2, 2, 2):
        b.set(x, 19, z, "waxed_cut_copper")
    for (x, z) in rect(-1, -1, 1, 1):
        b.set(x, 20, z, "waxed_cut_copper_slab[type=bottom]")
    b.set(0, 20, 0, "waxed_cut_copper")
    b.set(0, 21, 0, "lightning_rod[facing=up]")
    b.resolve()
    return _finish(b)
