"""The Old Watch: a ruined watchtower on the knoll east of the village. Hilde keeps the island's handful of
guards in shape here: a ring of packed earth for sparring (the golem drill), straw dummies, archery butts.

The tower is eight-sided, nine across; its west side has fallen, so village and ring look into its shell.
"""
import math

import layout as L
from build import Canvas, Mix, mix_set, stair, slab, lantern_post, disc
from noise import hash01, value

G = L.YARD_Y
TX, TZ = 79, 20                 # the tower's centre
RX, RZ = 71, 29                 # the sparring ring's centre
RING_R = 5.6
TOWER_H = 12

WALL = Mix([("cobblestone", 4), ("andesite", 3), ("stone_bricks", 2.5), ("mossy_cobblestone", 1.5),
            ("cracked_stone_bricks", 1.0), ("mossy_stone_bricks", 1.0)], scale=2.2, seed=1001)
EARTH = Mix([("coarse_dirt", 5), ("dirt_path", 2.2), ("gravel", 1.2), ("rooted_dirt", 1)], scale=2.6, seed=1003)
RUBBLE = ["cobblestone", "mossy_cobblestone", "andesite", "cobblestone_slab[type=bottom]",
          "stone_brick_slab[type=bottom]", "stone_bricks", "cracked_stone_bricks", "andesite_slab[type=bottom]"]


def _in(dx, dz):
    return abs(dx) <= 4 and abs(dz) <= 4 and abs(dx) + abs(dz) <= 6


def _is_wall(dx, dz):
    if not _in(dx, dz):
        return False
    return any(not _in(dx + a, dz + b) for a in (-1, 0, 1) for b in (-1, 0, 1))


_BREACH = math.radians(165)      # the fallen side: west, a little towards the ring


def _away(dx, dz):
    """Angle between this cell (seen from the tower's centre) and the fallen side, 0..pi."""
    d = abs(math.atan2(dz, dx) - _BREACH)
    return min(d, 2 * math.pi - d)


def _height(dx, dz):
    a = _away(dx, dz)
    fall = 9.0 * math.exp(-(a / 0.85) ** 2)
    jag = (hash01(TX + dx, 3, TZ + dz, 1005) - 0.5) * 2.4 if fall > 0.6 else 0.0
    return max(2, int(round(TOWER_H - fall + jag)))


def _toward(dx, dz):
    """How far the cell lies towards the fallen side (blocks along that direction)."""
    return dx * math.cos(_BREACH) + dz * math.sin(_BREACH)


def prepare(t):
    import sites
    sites.clear_disc(t, L.YARD[0], L.YARD[1], 15)
    sites.level_box(t, TX - 4, TZ - 4, TX + 4, TZ + 4, G, 2)
    for (x, z) in disc(RX, RZ, RING_R + 1.5):
        zi, xi = z - L.Z0, x - L.X0
        if t.land[zi, xi]:
            t.H[zi, xi] = G


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _ring(c, t)
    _tower(Canvas(w, TX, G + 1, TZ))
    _rubble(c, t)
    _gear(c, t)
    c.mark("hilde", 75, G + 1, 25, yaw=40)
    c.mark("drill_center", RX, G + 1, RZ, radius=RING_R)
    c.mark("yard_gate", 66, G + 1, 28)
    c.mark("tower_door", TX, G + 1, TZ + 6)
    c.mark("tower_top", TX - 1, G + 1 + 11, TZ - 2)


def _ring(c, t):
    for (x, z) in disc(RX, RZ, RING_R + 1.2):
        d = math.hypot(x - RX, z - RZ)
        y = t.h(x, z)
        if d <= RING_R:
            mix_set(c, x, y, z, EARTH)
        elif c.name(x, y, z) in ("grass_block", "dirt", "dirt_path", "coarse_dirt"):
            # the ring is marked with stones; gaps where the ways come in
            ang = math.degrees(math.atan2(z - RZ, x - RX)) % 360
            gap = (165 < ang < 200) or (290 < ang < 330)
            mix_set(c, x, y, z, EARTH)
            if not gap and hash01(x, 2, z, 1007) < 0.6:
                c.set(x, y + 1, z, ["cobblestone_slab[type=bottom]", "mossy_cobblestone_slab[type=bottom]",
                                    "andesite_slab[type=bottom]"][int(hash01(x, 4, z, 1009) * 3) % 3])


def _tower(c):
    cells = [(dx, dz) for dx in range(-4, 5) for dz in range(-4, 5) if _in(dx, dz)]
    walls = [p for p in cells if _is_wall(*p)]
    inner = [p for p in cells if not _is_wall(*p)]
    # footing and a stone floor
    for (dx, dz) in cells:
        mix_set(c, dx, -1, dz, WALL)
        c.column_down(dx, -2, dz, "cobblestone", 10)
    for (dx, dz) in walls:
        h = _height(dx, dz)
        for dy in range(0, h):
            mix_set(c, dx, dy, dz, WALL)
        if h >= TOWER_H:
            # battlements: every other stone stands a block higher
            if (dx + dz) % 2 == 0:
                mix_set(c, dx, TOWER_H, dz, WALL)
        elif h >= 4 and hash01(TX + dx, 5, TZ + dz, 1011) < 0.5:
            c.set(dx, h, dz, ["cobblestone_slab[type=bottom]", "stone_brick_slab[type=bottom]",
                              "mossy_cobblestone_slab[type=bottom]"][int(hash01(dx, 6, dz, 1013) * 3) % 3])
    # the door, towards the south, and a red banner over it
    for dy in (0, 1):
        c.set(0, dy, 4, "air")
    c.set(0, 2, 4, "chiseled_stone_bricks")
    c.set(0, 6, 5, "red_wall_banner[facing=south]")
    # loops
    for (dx, dz) in ((0, -4), (4, -2), (4, 2)):
        for dy in (3, 8):
            if _height(dx, dz) > dy + 2:
                c.set(dx, dy, dz, "air")
    # the floors that are left: timber at the first storey, the fighting deck under the battlements
    for (dx, dz) in inner:
        toward = _toward(dx, dz)
        if (dx, dz) == (3, 0):
            continue                                   # the ladder well
        if toward < 2.4:
            c.set(dx, 5, dz, "spruce_planks" if toward < 1.2 else slab("spruce", top=True))
        if toward < 0.9:
            c.set(dx, 10, dz, "dark_oak_planks" if toward < 0.0 else slab("dark_oak", top=True))
    for dy in range(0, 11):
        c.set(3, dy, 0, "ladder[facing=west]")
    # beams that still stick out over the gap
    for (dx, dz) in ((-1, 1), (0, 2)):
        if _toward(dx, dz) >= 0.9:
            c.set(dx, 10, dz, "stripped_spruce_log[axis=x]")
    # the watch fire in its basket, and the flag
    c.set(1, 11, -1, "hay_block[axis=y]")
    c.set(1, 12, -1, "campfire[lit=true,signal_fire=true]")
    for (dx, dz, f) in ((2, -1, "east"), (0, -1, "west"), (1, 0, "south"), (1, -2, "north")):
        c.set(dx, 11, dz, "dark_oak_trapdoor[facing=%s,open=true,half=bottom]" % f)
    pole = TOWER_H + 1 if c.name(3, TOWER_H, -3) != "air" else TOWER_H
    for dy in range(pole, pole + 4):
        c.set(3, dy, -3, "spruce_fence")
    c.set(3, pole + 4, -3, "red_banner[rotation=4]")
    # Hilde's quarters on the ground floor
    c.set(-2, 0, -2, "red_bed[facing=east,part=foot]")
    c.set(-1, 0, -2, "red_bed[facing=east,part=head]")
    c.set(2, 0, -2, "chest[facing=south,type=single]")
    c.set(1, 0, -3, "barrel[facing=up]")
    c.set(-3, 0, 0, "crafting_table")
    c.set(0, 4, -1, "lantern[hanging=true]")


def _rubble(c, t):
    """What fell lies at the foot of the gap (never inside the ring, never where Hilde stands)."""
    for dx in range(-10, 11):
        for dz in range(-10, 11):
            if _in(dx, dz):
                continue
            x, z = TX + dx, TZ + dz
            if math.hypot(x - RX, z - RZ) < RING_R + 1.6 or math.hypot(x - 75, z - 25) < 1.6:
                continue
            if not t.inb(x, z) or t.path[z - L.Z0, x - L.X0]:
                continue
            a = _away(dx, dz)
            d = math.hypot(dx, dz)
            if a > 0.8 or d > 9.5:
                continue
            p = (1.0 - a / 0.8) * (1.0 - (d - 4.0) / 5.5) * 0.85
            r = hash01(x, 8, z, 1015)
            if r < p:
                y = t.h(x, z) + 1
                if not c.is_air(x, y, z):
                    continue
                k = int(hash01(x, 9, z, 1017) * 97)
                c.set(x, y, z, RUBBLE[k % len(RUBBLE)])
                if r < p * 0.3 and "slab" not in RUBBLE[k % len(RUBBLE)]:
                    c.set(x, y + 1, z, RUBBLE[(k + 3) % len(RUBBLE)])


def _dummy(c, x, z, facing):
    y = G + 1
    c.set(x, y, z, "spruce_fence")
    c.set(x, y + 1, z, "hay_block[axis=y]")
    c.set(x, y + 2, z, "carved_pumpkin[facing=%s]" % facing)
    for (ax, az) in (((1, 0), (-1, 0)) if facing in ("north", "south") else ((0, 1), (0, -1))):
        c.set(x + ax, y + 1, z + az, "spruce_fence")


def _butt(c, x, z):
    y = G + 1
    c.set(x, y, z, "hay_block[axis=x]")
    c.set(x, y + 1, z, "target")
    c.set(x + 1, y, z, "hay_block[axis=z]")
    c.set(x + 1, y + 1, z, "hay_block[axis=y]")


def _gear(c, t):
    y = G + 1
    _dummy(c, 65, 25, "east")
    _dummy(c, 66, 22, "south")
    _dummy(c, 68, 20, "south")
    _butt(c, 80, 31)
    _butt(c, 82, 28)
    # the spear rack by the door
    for x in (82, 83):
        c.set(x, y, 26, "spruce_fence")
        c.set(x, y + 1, 26, "lightning_rod[facing=up]")
    c.set(84, y, 26, "barrel[facing=up]")
    # a fire to sit at, between the ring and the butts
    c.set(78, y, 29, "campfire[lit=true,signal_fire=false]")
    c.set(76, y, 30, "stripped_spruce_log[axis=x]")
    c.set(77, y, 31, "stripped_spruce_log[axis=x]")
    c.set(79, y, 31, stair("spruce", "north"))
    lantern_post(c, 66, y, 26, 3, "spruce")
    lantern_post(c, 77, y, 25, 3, "spruce")
    lantern_post(c, 76, y, 34, 3, "spruce")
