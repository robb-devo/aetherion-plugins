"""The Flat: bare sand at the level of the sea, south-east of the harbour. Old Clanger lives here.

An old fish weir (a curved fence of stakes) closes the seaward side, so the place is a ring without looking
like one; tide pools with sea pickles lie towards the rim, the ribs of a wrecked boat stand in the east. The
middle stays empty: that is where the harbour bell lies until it gets up and walks.
"""
import math

import layout as L
from build import Canvas, mix_set, stair, slab, disc
from noise import hash01, value

CX, CZ = L.FLAT
Y = L.FLAT_Y                      # the sand; feet stand one above
ARENA_R = 13
WEIR_R = 15.2

POOLS = [(-8, 5, 2.3, 1.7), (-4, 10, 1.9, 1.5), (6, 9, 2.5, 1.9), (10, -5, 1.7, 1.5), (-11, -1, 1.6, 2.0),
         (10, 3, 1.6, 1.4)]


def _weir_side(x, z):
    """True for the directions the weir closes: east, south and south-west (the land lies north and west)."""
    ang = math.degrees(math.atan2(z - CZ, x - CX)) % 360
    return ang <= 205 or ang >= 322


def prepare(t):
    import sites
    sites.clear_disc(t, CX, CZ, WEIR_R + 3)
    for (x, z) in disc(CX, CZ, WEIR_R + 1.5):
        zi, xi = z - L.Z0, x - L.X0
        if t.land[zi, xi]:
            t.H[zi, xi] = Y


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _sand(c, t)
    _pools(c, t)
    _weir(c, t)
    _wreck(c, t)
    _flotsam(c, t)
    c.mark("clanger", CX, Y + 1, CZ)
    c.mark("arena", CX, Y + 1, CZ, radius=ARENA_R)
    c.mark("arena_gate_west", CX - 12, Y + 1, CZ - 7)
    c.mark("arena_gate_north", CX + 2, Y + 1, CZ - 13)


def _sand(c, t):
    """Wet sand: patches of clay and gravel, darker where the water stood."""
    for (x, z) in disc(CX, CZ, WEIR_R + 1.5):
        if not t.is_land(x, z) or t.h(x, z) != Y:
            continue
        v = value(x, z, 5.0, 1201)
        r = hash01(x, 1, z, 1203)
        if v > 0.78 and r < 0.6:
            c.set(x, Y, z, "clay")
        elif v < 0.2 and r < 0.5:
            c.set(x, Y, z, "gravel")
        else:
            c.set(x, Y, z, "sand")


def _pools(c, t):
    for i, (dx, dz, rx, rz) in enumerate(POOLS):
        px, pz = CX + dx, CZ + dz
        cells = []
        for x in range(px - 4, px + 5):
            for z in range(pz - 4, pz + 5):
                d = ((x - px) / rx) ** 2 + ((z - pz) / rz) ** 2
                if d <= 1.0 + (hash01(x, 2 + i, z, 1205) - 0.5) * 0.5 and t.is_land(x, z) and t.h(x, z) == Y:
                    cells.append((x, z))
        for (x, z) in cells:
            c.set(x, Y - 1, z, "gravel" if hash01(x, 3, z, 1207) < 0.4 else "sand")
            c.set(x, Y, z, "water")
        for (x, z) in cells:
            r = hash01(x, 4, z, 1209)
            if r < 0.16:
                c.set(x, Y, z, "sea_pickle[pickles=%d,waterlogged=true]" % (1 + int(r * 25) % 3))
            elif r < 0.30:
                c.set(x, Y, z, "seagrass")
        c.mark("pool_%d" % (i + 1), px, Y + 1, pz)


def _weir(c, t):
    """Stakes on an arc; wattle between most of them, a few gaps where the sea broke through."""
    last = None
    n = int(2 * math.pi * WEIR_R)
    for k in range(n):
        a = 2 * math.pi * k / n
        x = int(round(CX + WEIR_R * math.cos(a)))
        z = int(round(CZ + WEIR_R * math.sin(a)))
        if (x, z) == last or not _weir_side(x, z) or not t.inb(x, z):
            continue
        if last is not None and x != last[0] and z != last[1] and abs(x - last[0]) + abs(z - last[1]) == 2:
            _stake(c, t, x, last[1])                     # close the corner, so the line has no slits
        last = (x, z)
        _stake(c, t, x, z)


def _stake(c, t, x, z):
    base = t.h(x, z) + 1                                 # in the shallows the stake stands on the sea bed
    gap = value(x, z, 4.0, 1211) > 0.83
    top = Y + 2 if not gap else Y
    tall = hash01(x, 5, z, 1213) < 0.28
    for yy in range(base, top + 1 + (1 if tall and not gap else 0)):
        c.set(x, yy, z, "spruce_fence" if yy > Y else "stripped_spruce_log[axis=y]")


def _wreck(c, t):
    """The ribs of a fishing boat, keel north-south, half in the sand."""
    kx = CX + 11
    z1, z2 = CZ - 9, CZ - 1
    for z in range(z1, z2 + 1):
        c.set(kx, Y, z, "stripped_dark_oak_log[axis=z]")
    c.set(kx, Y + 1, z1, "stripped_dark_oak_log[axis=y]")
    c.set(kx, Y + 2, z1, "stripped_dark_oak_log[axis=y]")
    c.set(kx, Y + 1, z2, "dark_oak_fence")
    for i, z in enumerate(range(z1 + 1, z2, 2)):
        lean = 1 if i % 3 == 2 else 0                    # one pair has fallen in
        for sgn in (-1, 1):
            c.set(kx + sgn, Y + 1, z, "dark_oak_fence")
            if not lean:
                c.set(kx + sgn * 2, Y + 1, z, stair("dark_oak", "east" if sgn > 0 else "west", top=True))
                c.set(kx + sgn * 2, Y + 2, z, "dark_oak_fence")
    for (dx, dz) in ((-2, 2), (2, 5), (-1, 7)):
        c.set(kx + dx, Y + 1, z1 + dz, slab("dark_oak"))
    c.mark("wreck", kx, Y + 1, (z1 + z2) // 2)


def _flotsam(c, t):
    rocks = ["mossy_cobblestone", "tuff", "andesite", "mossy_cobblestone", "cobblestone"]
    for (dx, dz, n) in ((-13, 8, 4), (4, 14, 5), (13, 9, 3), (-6, -13, 3)):
        x0, z0 = CX + dx, CZ + dz
        for k in range(n):
            x = x0 + int(hash01(x0, k, z0, 1215) * 3) - 1
            z = z0 + int(hash01(x0, k + 9, z0, 1217) * 3) - 1
            if not t.inb(x, z):
                continue
            y = max(t.h(x, z) + 1, Y)
            if c.name(x, y, z) in ("air", "water"):
                c.set(x, y, z, rocks[(k + x) % len(rocks)])
                if k == 0:
                    c.set(x, y + 1, z, rocks[(k + z) % len(rocks)])
    # driftwood, a stove-in barrel, kelp thrown up by the tide
    for (dx, dz, state) in ((-9, -8, "stripped_birch_log[axis=x]"), (-8, -8, "stripped_birch_log[axis=x]"),
                            (3, 11, "stripped_birch_log[axis=z]"), (-12, 4, "barrel[facing=east]"),
                            (8, 11, "dried_kelp_block"), (-3, -11, "dried_kelp_block"),
                            (12, 6, "composter[level=1]")):
        x, z = CX + dx, CZ + dz
        if t.is_land(x, z) and c.is_air(x, Y + 1, z) and c.name(x, Y, z) != "water":
            c.set(x, Y + 1, z, state)
