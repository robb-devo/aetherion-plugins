"""The smithy by the stream: a hammer forge.

Masonry ground floor with a wide open bay to the yard, a framed and jettied upper floor under a slate roof, a
big brick stack, and an undershot water wheel on the mill race that drives the hammer. Inside the bay: hearth,
anvil, quench tub, workbench. Below it the stone bridge that carries the way from the quarry.
"""
import math

import layout as L
from build import Canvas, Mix, mix_set, mixed, stair, slab, lantern_post, disc
from houses import House
from noise import hash01

G = L.FORGE_Y
BX, BZ, BW, BD = 17, 8, 9, 7              # building footprint: x 17..25, z 8..14, the bay opens south
RACE_X1, RACE_X2 = 13, 15                 # the mill race, three wide
RACE_Z1, RACE_Z2 = 3, 18
WHEEL_Y, WHEEL_Z = G + 4, 11              # the wheel's centre: nine across, its foot in the water
BRIDGE_Z = 22

FLOOR = Mix([("cobblestone", 3), ("andesite", 2.5), ("smooth_stone", 1.5), ("blackstone", 1), ("gravel", 0.8)],
            scale=2.0, seed=801)
YARD = Mix([("gravel", 3), ("coarse_dirt", 2.5), ("cobblestone", 2), ("dirt_path", 1.5), ("andesite", 1)],
           scale=2.6, seed=803)
BRICK = Mix([("bricks", 5), ("granite", 1), ("mud_bricks", 1.4)], scale=2.0, seed=805)
STONE = Mix([("cobblestone", 4), ("andesite", 3), ("stone_bricks", 2.5), ("mossy_cobblestone", 1.2),
             ("mossy_stone_bricks", 0.8)], scale=2.4, seed=807)


def prepare(t):
    import sites
    # the mill race: the stream runs straight along the west wall, under the wheel
    for z in range(RACE_Z1, RACE_Z2 + 1):
        for x in range(RACE_X1, RACE_X2 + 1):
            zi, xi = z - L.Z0, x - L.X0
            t.stream[zi, xi] = True
            t.water[zi, xi] = G
            t.H[zi, xi] = G - 1
        for x in (RACE_X1 - 1, RACE_X2 + 1):
            zi, xi = z - L.Z0, x - L.X0
            if not t.stream[zi, xi]:
                t.H[zi, xi] = G
    sites.level_box(t, BX - 1, BZ - 2, BX + BW + 3, BZ + BD + 6, G, 3)
    sites.clear_disc(t, BX + 4, BZ + 6, 16)
    sites.clear_box(t, RACE_X1 - 4, RACE_Z1 - 2, RACE_X2, RACE_Z2 + 2, 1)


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _yard(c, t)
    h = House(Canvas(w, BX, G + 1, BZ), BW, BD, floors=2, jetty=True, ridge="x", roof="slate",
              stone_ground="full", fit_out=False, floor=FLOOR, door=4, seed=3)
    top = h.build()
    _bay(Canvas(w, BX, G + 1, BZ), top)
    _race(c, t)
    _wheel(c)
    _bridge(c, t)
    c.mark("ansel", BX + 6, G + 1, BZ + BD + 2, yaw=160)
    c.mark("anvil", BX + 4, G + 1, BZ + 4)
    c.mark("forge_bench", BX + 2, G + 1, BZ + 4)
    c.mark("forge_sign", BX + 4, G + 5, BZ + BD + 1)


def _bay(c, top):
    """Opens the front, fits the hearth, the stack and the working gear. Local frame of the house."""
    zf = BD - 1
    for x in range(2, 7):
        for y in range(0, 3):
            c.set(x, y, zf, "air")
    for x in (1, 7):
        for y in range(0, 3):
            c.set(x, y, zf, "stone_bricks" if y % 2 == 0 else "andesite")
    # hearth against the back wall, under a brick hood
    for x in (3, 4, 5):
        mix_set(c, x, 0, 1, BRICK)
    c.set(4, 1, 1, "campfire[lit=true,signal_fire=false]")
    c.set(3, 1, 1, "brick_wall")
    c.set(5, 1, 1, "brick_wall")
    for x in (3, 4, 5):
        c.set(x, 3, 1, "brick_stairs[facing=north,half=top]")
    c.set(3, 2, 1, "brick_stairs[facing=east,half=top]")
    c.set(5, 2, 1, "brick_stairs[facing=west,half=top]")
    c.set(6, 0, 1, "blast_furnace[facing=south,lit=true]")
    c.set(2, 0, 1, "barrel[facing=south]")
    c.set(2, 1, 1, "barrel[facing=up]")
    # the stack: two by two, through the back of the roof
    for x in (4, 5):
        for z in (-1, 0):
            for y in range(-1, top + 3):
                mix_set(c, x, y, z, BRICK)
            c.column_down(x, -2, z, "cobblestone", 6)
    for x in (4, 5):
        for z in (-1, 0):
            c.set(x, top + 3, z, "campfire[lit=true,signal_fire=true]")
            c.set(x, top + 2, z, "hay_block[axis=y]")
    for (x, z, f) in ((3, -1, "west"), (3, 0, "west"), (6, -1, "east"), (6, 0, "east"),
                      (4, -2, "north"), (5, -2, "north"), (4, 1, "south"), (5, 1, "south")):
        c.set(x, top + 3, z, "dark_oak_trapdoor[facing=%s,open=true,half=bottom]" % f)
    # anvil, quench tub, workbench, the hammer the wheel drives
    c.set(4, 0, 4, "anvil[facing=east]")
    c.set(5, 0, 4, "water_cauldron[level=3]")
    c.set(2, 0, 4, "crafting_table")
    c.set(1, 3, 3, "stripped_dark_oak_log[axis=x]")       # the shaft comes in from the wheel
    c.set(2, 3, 3, "stripped_dark_oak_log[axis=x]")
    c.set(2, 2, 3, "chain[axis=y]")
    c.set(2, 1, 3, "heavy_core")                          # the hammer head over its block
    c.set(2, 0, 3, "polished_andesite")
    c.set(7, 0, 3, "barrel[facing=up]")
    c.set(7, 0, 2, "composter[level=4]")
    c.set(2, 3, 5, "lantern[hanging=true]")
    c.set(6, 3, 5, "lantern[hanging=true]")


def _yard(c, t):
    for x in range(BX - 1, BX + BW + 3):
        for z in range(BZ + BD, BZ + BD + 6):
            if not t.inb(x, z) or t.path[z - L.Z0, x - L.X0] or t.stream[z - L.Z0, x - L.X0]:
                continue
            if hash01(x, 4, z, 809) < 0.12 and z > BZ + BD + 3:
                continue
            y = t.h(x, z)
            if c.name(x, y, z) in ("grass_block", "dirt"):
                mix_set(c, x, y, z, YARD)
    y = G + 1
    # coal bin, grindstone, stock
    for x in (BX + 9, BX + 10):
        c.set(x, y, BZ + BD + 1, "black_concrete_powder")
        c.set(x, y, BZ + BD + 2, "spruce_trapdoor[facing=south,open=true,half=bottom]")
        c.set(x, G, BZ + BD + 1, "coarse_dirt")
    c.set(BX + 11, y, BZ + BD + 1, "spruce_trapdoor[facing=east,open=true,half=bottom]")
    c.set(BX + 9, y + 1, BZ + BD + 1, "black_concrete_powder")
    c.set(BX, y, BZ + BD + 2, "grindstone[face=floor,facing=east]")
    c.set(BX - 1, y, BZ + BD + 4, "barrel[facing=up]")
    c.set(BX + 8, y, BZ + BD + 4, "iron_bars")
    c.set(BX + 8, y + 1, BZ + BD + 4, "iron_bars")
    lantern_post(c, BX + 11, y, BZ + BD + 5, 3, "spruce")
    lantern_post(c, BX - 2, y, BZ + BD + 3, 3, "spruce")


def _race(c, t):
    """Masonry edges along the race and a raised sluice gate at its head."""
    for z in range(RACE_Z1, RACE_Z2 + 1):
        for x in (RACE_X1 - 1, RACE_X2 + 1):
            if not t.stream[z - L.Z0, x - L.X0] and c.name(x, G, z) in ("grass_block", "dirt", "coarse_dirt", "gravel",
                                                                    "dirt_path", "sand"):
                mix_set(c, x, G, z, STONE)
    z = RACE_Z1 + 1
    for x in (RACE_X1 - 1, RACE_X2 + 1):
        for y in range(G + 1, G + 4):
            c.set(x, y, z, "stripped_spruce_log[axis=y]")
    for x in range(RACE_X1 - 1, RACE_X2 + 2):
        c.set(x, G + 4, z, "stripped_spruce_log[axis=x]")
    for x in range(RACE_X1, RACE_X2 + 1):
        c.set(x, G + 3, z, "spruce_planks")
        c.set(x, G + 2, z, "spruce_trapdoor[facing=south,open=true,half=bottom]")
    c.set(RACE_X1 + 1, G + 5, z, "spruce_fence")           # the screw that lifts the gate
    c.mark("sluice", RACE_X1 + 1, G + 1, z - 2)


def _wheel(c):
    """The undershot wheel: two dark rims three apart, paddles between them, the shaft through the west wall."""
    cy, cz = WHEEL_Y, WHEEL_Z
    rim = [(dz, 4) for dz in (-1, 0, 1)] + [(dz, -4) for dz in (-1, 0, 1)]
    rim += [(4, dy) for dy in (-1, 0, 1)] + [(-4, dy) for dy in (-1, 0, 1)]
    rim += [(a * 2, b * 3) for a in (-1, 1) for b in (-1, 1)] + [(a * 3, b * 2) for a in (-1, 1) for b in (-1, 1)]
    order = sorted(rim, key=lambda p: math.atan2(p[1], p[0]))
    for x in (RACE_X1, RACE_X2):
        for (dz, dy) in rim:
            c.set(x, cy + dy, cz + dz, "dark_oak_planks")
        for k in (1, 2, 3):
            for (dz, dy) in ((k, 0), (-k, 0), (0, k), (0, -k)):
                c.set(x, cy + dy, cz + dz, "dark_oak_fence")
    for i, (dz, dy) in enumerate(order):
        if i % 2 == 0:
            c.set(RACE_X1 + 1, cy + dy, cz + dz, "spruce_planks")
    # the shaft: from its bearing on the far bank through both hubs into the forge
    for x in range(RACE_X1 - 1, BX + 1):
        c.set(x, cy, cz, "stripped_dark_oak_log[axis=x]")
    bx = RACE_X1 - 1
    for y in range(G + 1, cy):
        c.set(bx, y, cz, "stripped_spruce_log[axis=y]")
    c.set(bx, G + 1, cz - 1, stair("spruce", "south"))
    c.set(bx, G + 1, cz + 1, stair("spruce", "north"))
    c.mark("wheel", RACE_X1 - 3, G + 1, cz)


def _bridge(c, t):
    row = t.stream[BRIDGE_Z - L.Z0]
    xs = [x for x in range(4, 22) if row[x - L.X0]]
    if not xs:
        return
    x1, x2 = min(xs) - 1, max(xs) + 1
    y = G + 1
    for x in range(x1, x2 + 1):
        for z in range(BRIDGE_Z - 1, BRIDGE_Z + 2):
            mix_set(c, x, y, z, STONE)
        for z in (BRIDGE_Z - 2, BRIDGE_Z + 2):
            mix_set(c, x, y, z, STONE)
            c.set(x, y + 1, z, "stone_brick_wall" if (x + z) % 3 else "mossy_stone_brick_wall")
    # springers under the ends, steps up at both sides
    for z in range(BRIDGE_Z - 2, BRIDGE_Z + 3):
        c.set(x1 + 1, y - 1, z, "stone_brick_stairs[facing=west,half=top]")
        c.set(x2 - 1, y - 1, z, "stone_brick_stairs[facing=east,half=top]")
    for z in range(BRIDGE_Z - 1, BRIDGE_Z + 2):
        c.set(x1 - 1, y, z, stair("cobblestone" if z % 2 else "stone_brick", "east"))
        c.set(x2 + 1, y, z, stair("cobblestone" if z % 2 else "stone_brick", "west"))
    for z in (BRIDGE_Z - 2, BRIDGE_Z + 2):
        c.set(x1, y + 2, z, "lantern[hanging=false]")
        c.set(x2, y + 2, z, "lantern[hanging=false]")
    c.mark("bridge_forge", (x1 + x2) // 2, y + 1, BRIDGE_Z)
