"""The Morning Bell: the summit where everyone arrives.

A free-standing timber belfry after the Nordic klockstapel: a square body with a shingled skirt round its base,
an open bell chamber, a steep hat roof and a vane. Red boards, pale trim, a bronze bell. Beside it the Landing
(a paved circle), a small bell on a frame that players ring themselves, and the Bell Steps down to the island.
"""
import math

import layout as L
from build import Canvas, Mix, RoofMat, mix_set, mixed, stair, slab, lantern_post, bench, sign, disc
from noise import hash01

G = L.BELL_Y                      # ground block of the summit
TX, TZ = L.BELL[0] - 4, L.BELL[1] - 1     # centre of the belfry
LX, LZ = L.BELL[0] + 8, L.BELL[1]         # centre of the Landing, at the head of the steps
STEP_TOP_X = -45                  # first stair of the Bell Steps
STEPS = 14

STONE = Mix([("cobblestone", 4), ("andesite", 3), ("stone_bricks", 2), ("mossy_cobblestone", 1.2),
             ("cracked_stone_bricks", 0.8)], scale=2.6, seed=301)
PAVE = Mix([("cobblestone", 3), ("andesite", 2.5), ("gravel", 1.2), ("stone_bricks", 1.5),
            ("mossy_cobblestone", 0.8)], scale=2.2, seed=307)
STAIR_MATS = ["cobblestone", "cobblestone", "andesite", "stone_brick", "mossy_cobblestone", "cobblestone",
              "mossy_stone_brick"]
RED = "mangrove_planks"
POST = "stripped_mangrove_log"
TRIM = "stripped_birch_wood"
ROOF = RoofMat("dark_oak", "spruce", 0.22, ridge="dark_oak", seed=311)


def step_y(x):
    """y of the stair block of the Bell Steps at column x."""
    return G - (x - STEP_TOP_X)


def prepare(t):
    import sites
    z0 = L.STEPS_Z
    for x in range(STEP_TOP_X - 5, STEP_TOP_X + STEPS + 4):
        for z in range(z0 - 3, z0 + 4):
            zi, xi = z - L.Z0, x - L.X0
            if x < STEP_TOP_X:
                lvl = G
            elif x < STEP_TOP_X + STEPS:
                lvl = step_y(x) - 1
            else:
                lvl = step_y(STEP_TOP_X + STEPS - 1) - 1
            if abs(z - z0) <= 2:
                t.H[zi, xi] = lvl
            else:
                t.H[zi, xi] = int(round((int(t.H[zi, xi]) + lvl) / 2.0))
    sites.clear_disc(t, L.BELL[0] + 2, L.BELL[1], 14)
    sites.clear_box(t, STEP_TOP_X - 2, z0 - 9, STEP_TOP_X + STEPS + 14, z0 + 9)


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _plaza(c, t)
    _belfry(c)
    _ring_bell(c)
    _steps(c, t)
    _rim(c, t)
    c.mark("arrival", LX, G + 1, LZ, yaw=-90)
    c.mark("bell_big", TX, G + 11, TZ)
    c.mark("oda", LX - 2, G + 1, LZ - 3, yaw=-45)
    # Tolly waits on the other side of the bell frame, turned to whoever wakes on the Landing
    c.mark("tolly", LX + 3, G + 1, LZ - 2, yaw=56)
    c.mark("steps_top", STEP_TOP_X - 1, G + 1, L.STEPS_Z)
    c.mark("steps_foot", STEP_TOP_X + STEPS + 1, step_y(STEP_TOP_X + STEPS - 1), L.STEPS_Z)


def _plaza(c, t):
    # the Landing: a paved circle with a carved centre stone
    for (x, z) in disc(LX, LZ, 3.3):
        d = math.hypot(x - LX, z - LZ)
        if d < 0.8:
            c.set(x, G, z, "chiseled_stone_bricks")
        elif d < 2.0:
            c.set(x, G, z, "polished_andesite")
        elif d < 2.9:
            c.set(x, G, z, "stone_bricks" if (x + z) % 2 == 0 else "andesite")
        else:
            mix_set(c, x, G, z, PAVE)
    # the way from the belfry door over the Landing to the head of the steps
    for x in range(TX + 4, STEP_TOP_X):
        for z in range(LZ - 1, LZ + 2):
            if math.hypot(x - LX, z - LZ) > 3.3:
                mix_set(c, x, G, z, PAVE)


def _belfry(c):
    x1, x2, z1, z2 = TX - 2, TX + 2, TZ - 2, TZ + 2          # the body, 5 x 5
    y = G + 1
    # plinth, 7 x 7, with two steps up on the east
    mixed(c, x1 - 1, y, z1 - 1, x2 + 1, y, z2 + 1, STONE)
    for z in range(TZ - 1, TZ + 2):
        c.set(x2 + 2, y, z, stair(STAIR_MATS[(z + 50) % len(STAIR_MATS)], "west"))
    floor = y + 1                                             # feet level inside the porch
    # porch posts at the plinth corners and the skirt roof they carry
    for px, pz in ((x1 - 1, z1 - 1), (x2 + 1, z1 - 1), (x1 - 1, z2 + 1), (x2 + 1, z2 + 1)):
        for yy in range(floor, floor + 4):
            c.set(px, yy, pz, POST + "[axis=y]")
    skirt = floor + 4
    for x in range(x1 - 1, x2 + 2):
        c.set(x, skirt, z1 - 1, stair(ROOF.pick(x, skirt, z1 - 1), "south"))
        c.set(x, skirt, z2 + 1, stair(ROOF.pick(x, skirt, z2 + 1), "north"))
    for z in range(z1, z2 + 1):
        c.set(x1 - 1, skirt, z, stair(ROOF.pick(x1 - 1, skirt, z), "east"))
        c.set(x2 + 1, skirt, z, stair(ROOF.pick(x2 + 1, skirt, z), "west"))
    # the body: corner posts all the way up, boarded walls, a pale band above the skirt
    top = floor + 12                                          # last post block
    for px, pz in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        for yy in range(floor, top + 1):
            c.set(px, yy, pz, POST + "[axis=y]")
    for yy in range(floor, floor + 8):
        band = yy == skirt + 1
        for x in range(x1 + 1, x2):
            for z in (z1, z2):
                c.set(x, yy, z, TRIM + "[axis=x]" if band else RED)
        for z in range(z1 + 1, z2):
            for x in (x1, x2):
                c.set(x, yy, z, TRIM + "[axis=z]" if band else RED)
    # the doorway on the east, three wide with shoulders
    for z in range(TZ - 1, TZ + 2):
        for yy in range(floor, floor + 3):
            c.set(x2, yy, z, "air")
    c.set(x2, floor + 2, TZ - 1, stair("mangrove", "south", top=True))
    c.set(x2, floor + 2, TZ + 1, stair("mangrove", "north", top=True))
    # small shuttered openings high in the boarded part
    for (x, z, f) in ((TX, z1, "north"), (TX, z2, "south"), (x1, TZ, "west")):
        c.set(x, floor + 6, z, "mangrove_trapdoor[facing=%s,open=true,half=bottom]" % f)
    # inside: a ladder up the west wall into the bell chamber
    chamber = floor + 8                                       # floor block of the bell chamber
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, chamber, z, "spruce_planks")
    c.set(x1 + 1, chamber, TZ, "air")
    for yy in range(floor, chamber + 1):
        c.set(x1 + 1, yy, TZ, "ladder[facing=east]")
    c.set(x1 + 2, floor, z1 + 1, "barrel[facing=up]")
    c.set(x1 + 3, floor, z1 + 1, "barrel[facing=east]")
    c.set(x1 + 2, floor, z2 - 1, "lantern[hanging=false]")
    # bell chamber: open on all sides, a rail between the posts, a pale lintel under the eaves
    for x in range(x1 + 1, x2):
        for z in (z1, z2):
            c.set(x, chamber, z, TRIM + "[axis=x]")
            c.set(x, chamber + 1, z, "mangrove_fence")
    for z in range(z1 + 1, z2):
        for x in (x1, x2):
            c.set(x, chamber, z, TRIM + "[axis=z]")
            c.set(x, chamber + 1, z, "mangrove_fence")
    lintel = top
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if x in (x1, x2) or z in (z1, z2):
                c.set(x, lintel, z, TRIM + ("[axis=x]" if z in (z1, z2) else "[axis=z]"))
    for (x, z, f) in ((x1 + 1, z1, "east"), (x2 - 1, z1, "west"), (x1 + 1, z2, "east"), (x2 - 1, z2, "west")):
        c.set(x, lintel - 1, z, stair("mangrove", f, top=True))
    for (x, z, f) in ((x1, z1 + 1, "south"), (x1, z2 - 1, "north"), (x2, z1 + 1, "south"), (x2, z2 - 1, "north")):
        c.set(x, lintel - 1, z, stair("mangrove", f, top=True))
    # the bell: bronze, hung from a beam
    for x in range(x1 + 1, x2):
        c.set(x, lintel, TZ, POST + "[axis=x]")
    by = chamber + 2
    c.set(TX, by + 1, TZ, "waxed_copper_block")
    c.set(TX, by, TZ, "waxed_copper_block")
    for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        c.set(TX + dx, by, TZ + dz, "waxed_cut_copper_stairs[facing=%s,half=bottom]" % f)
    for dx in (-1, 1):
        for dz in (-1, 1):
            c.set(TX + dx, by, TZ + dz, "waxed_cut_copper_slab[type=bottom]")
    c.set(TX, by - 1, TZ, "lightning_rod[facing=down]")
    # the hat: a flared eave, then steep to the point
    ry = lintel + 1
    for x in range(x1 - 1, x2 + 2):
        c.set(x, ry, z1 - 1, stair(ROOF.pick(x, ry, z1 - 1), "south"))
        c.set(x, ry, z2 + 1, stair(ROOF.pick(x, ry, z2 + 1), "north"))
    for z in range(z1, z2 + 1):
        c.set(x1 - 1, ry, z, stair(ROOF.pick(x1 - 1, ry, z), "east"))
        c.set(x2 + 1, ry, z, stair(ROOF.pick(x2 + 1, ry, z), "west"))
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, ry, z, "dark_oak_planks")
    for x in range(x1, x2 + 1):
        c.set(x, ry + 1, z1, stair(ROOF.pick(x, ry + 1, z1), "south"))
        c.set(x, ry + 1, z2, stair(ROOF.pick(x, ry + 1, z2), "north"))
    for z in range(z1 + 1, z2):
        c.set(x1, ry + 1, z, stair(ROOF.pick(x1, ry + 1, z), "east"))
        c.set(x2, ry + 1, z, stair(ROOF.pick(x2, ry + 1, z), "west"))
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            c.set(x, ry + 1, z, "dark_oak_planks")
            c.set(x, ry + 2, z, "dark_oak_planks")
    for x in range(x1 + 1, x2):
        c.set(x, ry + 3, z1 + 1, stair(ROOF.pick(x, ry + 3, z1 + 1), "south"))
        c.set(x, ry + 3, z2 - 1, stair(ROOF.pick(x, ry + 3, z2 - 1), "north"))
    c.set(x1 + 1, ry + 3, TZ, stair("dark_oak", "east"))
    c.set(x2 - 1, ry + 3, TZ, stair("dark_oak", "west"))
    c.set(TX, ry + 3, TZ, "dark_oak_planks")
    c.set(TX, ry + 4, TZ, "dark_oak_planks")
    c.set(TX, ry + 5, TZ, "dark_oak_fence")
    c.set(TX, ry + 6, TZ, "dark_oak_fence")
    c.set(TX, ry + 7, TZ, "lightning_rod[facing=up]")
    c.set(TX + 1, ry + 6, TZ, "end_rod[facing=east]")       # the vane: it points to the mainland
    # lights under the skirt beside the door, a name board over it
    c.set(x2 + 1, skirt - 1, TZ - 2, "lantern[hanging=true]")
    c.set(x2 + 1, skirt - 1, TZ + 2, "lantern[hanging=true]")
    c.w.mark("belfry_door", x2 + 2, floor, TZ)


def _ring_bell(c):
    """The small bell on its frame beside the Landing: the one every newcomer rings."""
    bx, bz = LX, LZ - 4
    y = G + 1
    for px in (bx - 1, bx + 1):
        c.set(px, y - 1, bz, "stone_bricks")
        for yy in range(y, y + 3):
            c.set(px, yy, bz, "mangrove_fence")
    for px in range(bx - 1, bx + 2):
        c.set(px, y + 3, bz, POST + "[axis=x]")
    c.set(bx, y + 2, bz, "bell[attachment=ceiling,facing=north]")
    c.set(bx - 2, y + 3, bz, stair("dark_oak", "east", top=False))
    c.set(bx + 2, y + 3, bz, stair("dark_oak", "west", top=False))
    c.mark("bell", bx, y + 2, bz)


def _steps(c, t):
    z0 = L.STEPS_Z
    for i in range(STEPS):
        x = STEP_TOP_X + i
        y = step_y(x)
        for z in range(z0 - 1, z0 + 2):
            m = STAIR_MATS[int(hash01(x, 0, z, 313) * 97) % len(STAIR_MATS)]
            c.set(x, y, z, stair(m, "west"))
            mix_set(c, x, y - 1, z, STONE)
            for yy in range(y + 1, y + 5):
                c.set(x, yy, z, "air")
        # cheeks: a stone curb each side, stepping down with the flight
        for z in (z0 - 2, z0 + 2):
            mix_set(c, x, y, z, STONE)
            mix_set(c, x, y - 1, z, STONE)
            c.column_down(x, y - 2, z, "cobblestone", 6)
            for yy in range(y + 1, y + 4):
                c.set(x, yy, z, "air")
            if i % 4 == 1:
                c.set(x, y + 1, z, "cobblestone_wall")
                c.set(x, y + 2, z, "lantern[hanging=false]")
            elif i % 2 == 0:
                c.set(x, y + 1, z, "cobblestone_wall" if (x + z) % 3 else "mossy_cobblestone_wall")
    # the foot: a small paved landing where the trail begins
    fy = step_y(STEP_TOP_X + STEPS - 1) - 1
    for x in range(STEP_TOP_X + STEPS, STEP_TOP_X + STEPS + 3):
        for z in range(z0 - 2, z0 + 3):
            mix_set(c, x, fy, z, PAVE)
            for yy in range(fy + 1, fy + 5):
                c.set(x, yy, z, "air")
    lantern_post(c, STEP_TOP_X + STEPS + 1, fy + 1, z0 - 3, 3, "spruce")
    # head of the steps: two pillars with lanterns
    for z in (z0 - 2, z0 + 2):
        mix_set(c, STEP_TOP_X - 1, G + 1, z, STONE)
        c.set(STEP_TOP_X - 1, G + 2, z, "stone_brick_wall")
        c.set(STEP_TOP_X - 1, G + 3, z, "lantern[hanging=false]")


def _rim(c, t):
    """A low wall along the seaward edge of the summit, a bench at the lookout, flowers by the tower."""
    cx, cz = L.BELL[0] + 2, L.BELL[1]
    ring = []
    for deg10 in range(1050, 2560, 5):
        a = math.radians(deg10 / 10.0)
        x = int(round(cx + 12.4 * 1.2 * math.cos(a)))
        z = int(round(cz + 12.4 * math.sin(a)))
        if ring and ring[-1][:2] == (x, z):
            continue
        # keep the ring 4-connected: add the corner cell when both coordinates changed
        if ring and x != ring[-1][0] and z != ring[-1][1]:
            ring.append((x, ring[-1][1], deg10 / 10.0))
        ring.append((x, z, deg10 / 10.0))
    for (x, z, deg) in ring:
        if not t.inb(x, z) or 173 <= deg <= 187:
            continue                                          # the lookout gap, due west
        gy = t.h(x, z)
        if gy < G - 2 or not c.is_air(x, gy + 1, z):
            continue
        c.set(x, gy + 1, z, "cobblestone_wall" if int(hash01(x, 1, z, 317) * 10) > 2 else "mossy_cobblestone_wall")
    bench(c, TX - 6, G + 1, TZ, facing="west", wood="spruce", length=3)
    bench(c, LX - 1, G + 1, LZ + 5, facing="north", wood="spruce", length=2)
    for (x, z, f) in ((TX - 1, TZ + 5, "allium"), (TX + 1, TZ + 5, "cornflower"), (TX, TZ + 6, "allium"),
                      (TX + 2, TZ + 6, "oxeye_daisy"), (TX - 2, TZ - 5, "cornflower"), (TX, TZ - 5, "oxeye_daisy"),
                      (TX + 1, TZ - 6, "allium"), (LX - 4, LZ - 5, "poppy"), (LX - 5, LZ - 4, "poppy")):
        if c.is_air(x, G + 1, z) and c.name(x, G, z) == "grass_block":
            c.set(x, G + 1, z, f)
