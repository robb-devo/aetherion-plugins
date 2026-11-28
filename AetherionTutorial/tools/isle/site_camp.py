"""Burr's Clearing: a charcoal burner's camp in the north wood.

After the real thing: a Kohlenmeiler (billets stood on end round a central shaft, covered with turf and earth,
smoking from the crown), a second one half built, the burner's low hut (Koete), stacked cordwood, a heap of
finished charcoal, water close by. Here the first tree is felled and the first tool made.
"""
import math

import layout as L
from build import Canvas, Mix, RoofMat, gable_roof, mix_set, stair, slab, lantern_post, log_pile, disc
from noise import hash01

G = L.BURR_Y
CX, CZ = L.BURR

EARTH = Mix([("coarse_dirt", 4), ("rooted_dirt", 2), ("dirt", 2), ("podzol", 1.5), ("mud", 1.2), ("moss_block", 1.0)],
            scale=2.0, seed=501)
YARD = Mix([("coarse_dirt", 3), ("podzol", 1.5), ("dirt_path", 2.5), ("grass_block", 3), ("gravel", 0.8)],
           scale=3.0, seed=503)
HUT_ROOF = RoofMat("spruce", "dark_oak", 0.3, ridge="dark_oak", seed=505)

KILN_A = (31, -67)
KILN_B = (17, -71)
FIRE = (22, -62)
BENCH = (22, -55)          # the crafting table under its shelter
TREES = [(39, -58), (37, -50), (29, -49)]


def prepare(t):
    import sites
    sites.clear_disc(t, CX, CZ, 17)
    for (x, z) in TREES:
        sites.clear_disc(t, x, z, 6)
    sites.level_box(t, 9, -67, 15, -59, G, 2)
    sites.level_box(t, KILN_A[0] - 4, KILN_A[1] - 4, KILN_A[0] + 4, KILN_A[1] + 4, G, 2)
    sites.level_box(t, KILN_B[0] - 4, KILN_B[1] - 4, KILN_B[0] + 4, KILN_B[1] + 4, G, 2)


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _ground(c, t)
    _kiln(c, *KILN_A)
    _kiln_half(c, *KILN_B)
    _hut(c)
    _shelter(c)
    _fire(c)
    _stores(c)
    c.mark("burr", FIRE[0] + 2, G + 1, FIRE[1] - 2, yaw=135)
    c.mark("workbench", BENCH[0], G + 1, BENCH[1])
    c.mark("kiln", KILN_A[0], G + 4, KILN_A[1])
    c.mark("chop_block", 27, G + 1, -58)


def after_flora(w, t, forest):
    from noise import Rng
    for i, (x, z) in enumerate(TREES):
        y = t.h(x, z)
        forest.oak(x, y, z, Rng(900 + i), big=False)
        w.set(x, y, z, "dirt")
        w.mark("lesson_tree_%d" % (i + 1), x, y + 1, z)


def _ground(c, t):
    for (x, z) in disc(CX, CZ, 13):
        if not t.inb(x, z) or t.path[z - L.Z0, x - L.X0]:
            continue
        d = math.hypot(x - CX, z - CZ)
        if d > 9 and hash01(x, 0, z, 507) < (d - 9) / 5.0:
            continue
        y = t.h(x, z)
        if c.name(x, y, z) == "grass_block":
            mix_set(c, x, y, z, YARD)


def _kiln(c, cx, cz):
    """The burning kiln: an earth dome, billets showing at its foot, smoke from the crown."""
    for (x, z) in disc(cx, cz, 3.7):
        d = math.hypot(x - cx, z - cz)
        h = int(round(4.2 * math.sqrt(max(0.0, 1 - (d / 4.0) ** 2))))
        for y in range(G + 1, G + 1 + h):
            mix_set(c, x, y, z, EARTH)
        c.set(x, G, z, "coarse_dirt")
    # the fire in the crown: hidden in the top, only its smoke shows
    c.set(cx, G + 2, cz, "hay_block[axis=y]")
    c.set(cx, G + 3, cz, "campfire[lit=true,signal_fire=true]")
    c.set(cx, G + 4, cz, "air")
    # billets stood round the foot, a ladder to tend the crown, a ring of charcoal dust
    for k in range(16):
        a = k * math.pi / 8
        x = int(round(cx + 4.3 * math.cos(a)))
        z = int(round(cz + 4.3 * math.sin(a)))
        if k % 3 == 0:
            c.set(x, G + 1, z, "oak_log[axis=y]")
        elif k % 3 == 1 and c.is_air(x, G + 1, z):
            c.set(x, G, z, "black_concrete_powder")
    c.set(cx - 4, G + 1, cz + 1, "ladder[facing=west]")
    c.set(cx - 4, G + 2, cz + 1, "ladder[facing=west]")
    mix_set(c, cx - 3, G + 2, cz + 1, EARTH)
    c.set(cx + 5, G + 1, cz - 1, "water_cauldron[level=3]")
    c.set(cx + 5, G + 1, cz, "water_cauldron[level=2]")
    c.set(cx + 5, G + 1, cz + 2, "composter[level=6]")


def _kiln_half(c, cx, cz):
    """A kiln being built: the stacked billets still show, one side already under turf."""
    for tier, r in enumerate((3.2, 2.2, 1.2)):
        for (x, z) in disc(cx, cz, r):
            c.set(x, G + 1 + tier, z, "oak_log[axis=y]" if (x + z + tier) % 4 else "spruce_log[axis=y]")
    for y in range(G + 1, G + 6):
        c.set(cx, y, cz, "spruce_fence")
    for (x, z) in disc(cx, cz, 4.0):
        d = math.hypot(x - cx, z - cz)
        if x < cx - 0.5 and d > 1.8:
            h = int(round(3.4 * math.sqrt(max(0.0, 1 - (d / 4.2) ** 2))))
            top = G + h
            if c.is_air(x, top, z) or True:
                mix_set(c, x, top, z, EARTH)
                if h >= 2 and c.is_air(x, top - 1, z):
                    mix_set(c, x, top - 1, z, EARTH)
        c.set(x, G, z, "coarse_dirt")
    log_pile(c, cx + 5, G + 1, cz - 1, length=3, layers=2, axis="z", wood="oak")


def _hut(c):
    """The Koete: a roof standing on the ground, open to the fire."""
    x1, x2 = 10, 14
    z1, z2 = -66, -60
    y = G + 1

    def gable(x, yy, z):
        return "spruce_planks" if x == x1 else None

    gable_roof(c, x1, z1, x2, z2, y, HUT_ROOF, ridge_axis="x", overhang=0, gable_fill=gable)
    for x in range(x1, x2 + 1):
        for z in range(z1 + 1, z2):
            c.set(x, G, z, "coarse_dirt" if (x + z) % 3 else "podzol")
    # the open east end: a frame of poles
    mid = (z1 + z2) // 2
    c.set(x2, y + 3, mid, "spruce_fence")
    # inside: a straw bed, a chest, a lamp
    c.set(x1 + 1, y, mid - 1, "hay_block[axis=x]")
    c.set(x1 + 2, y, mid - 1, "hay_block[axis=x]")
    c.set(x1 + 1, y, mid + 1, "barrel[facing=up]")
    c.set(x1 + 1, y + 2, mid, "lantern[hanging=true]")
    c.set(x1 + 1, y + 3, mid, "spruce_planks")


def _shelter(c):
    """Four posts and a plank roof over the workbench."""
    x1, x2, z1, z2 = BENCH[0] - 2, BENCH[0] + 2, BENCH[1] - 1, BENCH[1] + 1
    y = G + 1
    for (x, z) in ((x1, z1), (x2, z1), (x1, z2), (x2, z2)):
        for yy in range(y, y + 3):
            c.set(x, yy, z, "spruce_fence")
    for x in range(x1 - 1, x2 + 2):
        for z in range(z1 - 1, z2 + 2):
            lowered = z == z2 + 1
            c.set(x, y + 3 if not lowered else y + 2, z, slab("spruce", top=not lowered) if (x + z) % 5 else slab("dark_oak", top=not lowered))
    c.set(BENCH[0], y, BENCH[1], "crafting_table")
    c.set(BENCH[0] - 1, y, BENCH[1], "barrel[facing=up]")
    c.set(BENCH[0] + 1, y, BENCH[1], "grindstone[face=floor,facing=north]")
    c.set(BENCH[0] + 1, y + 2, BENCH[1] - 1, "lantern[hanging=true]")
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if c.name(x, G, z) == "grass_block":
                c.set(x, G, z, "coarse_dirt")


def _fire(c):
    fx, fz = FIRE
    y = G + 1
    c.set(fx, y, fz, "campfire[lit=true,signal_fire=false]")
    c.set(fx, G, fz, "coarse_dirt")
    for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
        c.set(fx + dx, y, fz + dz, "cobblestone_slab[type=bottom]" if (dx + dz) > 0 else "mossy_cobblestone_slab[type=bottom]")
    # two log seats
    for i in range(-1, 2):
        c.set(fx + i, y, fz + 3, "oak_log[axis=x]")
    for i in range(-1, 2):
        c.set(fx - 3, y, fz + i, "spruce_log[axis=z]")


def _stores(c):
    y = G + 1
    c.set(27, y, -58, "oak_log[axis=y]")                          # the chopping block
    log_pile(c, 30, y, -56, length=5, layers=2, axis="x", wood="oak")
    log_pile(c, 11, y, -57, length=4, layers=2, axis="z", wood="birch")
    # finished charcoal: a dark heap, sacks and baskets
    for (x, z) in disc(36, -63, 1.7):
        c.set(x, y, z, "black_concrete_powder")
        c.set(x, G, z, "coarse_dirt")
    c.set(36, y + 1, -63, "black_concrete_powder")
    c.set(35, y + 1, -63, "blackstone")
    c.set(38, y, -64, "barrel[facing=up]")
    c.set(38, y, -62, "composter[level=7]")
    c.set(38, y + 1, -64, "barrel[facing=east]")
    lantern_post(c, 8, y, -62, 3, "spruce")
    lantern_post(c, 17, y, -54, 3, "spruce")
