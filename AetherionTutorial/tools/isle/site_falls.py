"""The fall: a plank bridge over the stream above it, the Overlook on the cliff edge (the first full view of the
lower island: village, harbour, ferry), and a stone stair down the escarpment to the pool."""
import layout as L
from build import Canvas, Mix, mix_set, stair, slab, lantern_post, bench
from noise import hash01

BRIDGE_Z = -38
STAIR_X = (-9, -8)
STAIR_TOP_Z = -32
STAIR_TOP_Y = 78
STAIR_N = 8
DECK_X = (-5, -2)

STONE = Mix([("cobblestone", 4), ("andesite", 3), ("mossy_cobblestone", 1.5), ("stone_bricks", 1.5)], scale=2.5, seed=601)
STAIRS = ["cobblestone", "andesite", "mossy_cobblestone", "cobblestone", "stone_brick"]


def stair_y(z):
    return STAIR_TOP_Y - (z - STAIR_TOP_Z)


def prepare(t):
    import sites
    for z in range(STAIR_TOP_Z - 1, STAIR_TOP_Z + STAIR_N + 3):
        for x in range(STAIR_X[0] - 1, STAIR_X[1] + 2):
            zi, xi = z - L.Z0, x - L.X0
            if z < STAIR_TOP_Z:
                lvl = STAIR_TOP_Y
            elif z < STAIR_TOP_Z + STAIR_N:
                lvl = stair_y(z) - 1
            else:
                lvl = stair_y(STAIR_TOP_Z + STAIR_N - 1) - 1
            if STAIR_X[0] <= x <= STAIR_X[1]:
                t.H[zi, xi] = lvl
            else:
                t.H[zi, xi] = max(int(t.H[zi, xi]), lvl + 1) if z >= STAIR_TOP_Z else int(t.H[zi, xi])
    sites.clear_box(t, -12, -40, 12, -18, 2)
    # the Overlook looks down the island to the harbour: no tree may stand in that line
    ox, oz, hx, hz = -3.0, -26.0, 30.0, 92.0
    for k in range(0, 75):
        f = k / 118.0
        sites.clear_disc(t, ox + (hx - ox) * f, oz + (hz - oz) * f, 4.5 + k * 0.03)


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _bridge(c, t)
    _stair(c, t)
    _overlook(c, t)


def _bridge(c, t):
    row = t.stream[BRIDGE_Z - L.Z0]
    xs = [x for x in range(-4, 14) if row[x - L.X0]]
    if not xs:
        return
    x1, x2 = min(xs) - 1, max(xs) + 1
    lvl = int(t.water[BRIDGE_Z - L.Z0, xs[0] - L.X0])
    y = lvl + 1
    for x in range(x1, x2 + 1):
        for z in (BRIDGE_Z, BRIDGE_Z + 1):
            c.set(x, y, z, slab("spruce") if (x + z) % 4 else slab("dark_oak"))
    for x in (x1, x2):
        for z in (BRIDGE_Z - 1, BRIDGE_Z + 2):
            c.set(x, y, z, "spruce_fence")
            c.set(x, y + 1, z, "spruce_fence")
    for x in range(x1 + 1, x2):
        c.set(x, y + 1, BRIDGE_Z - 1, "spruce_fence")
        c.set(x, y, BRIDGE_Z - 1, "spruce_fence")
    c.set(x1, y + 2, BRIDGE_Z - 1, "lantern[hanging=false]")
    c.mark("bridge_falls", (x1 + x2) // 2, y, BRIDGE_Z)


def _stair(c, t):
    for i in range(STAIR_N):
        z = STAIR_TOP_Z + i
        y = stair_y(z)
        for x in range(STAIR_X[0], STAIR_X[1] + 1):
            m = STAIRS[int(hash01(x, 2, z, 603) * 97) % len(STAIRS)]
            c.set(x, y, z, stair(m, "north"))
            mix_set(c, x, y - 1, z, STONE)
            c.column_down(x, y - 2, z, "cobblestone", 10)
            for yy in range(y + 1, y + 5):
                c.set(x, yy, z, "air")
        for x in (STAIR_X[0] - 1, STAIR_X[1] + 1):
            # cheeks: only where the ground does not already stand higher
            if c.is_air(x, y + 1, z):
                if c.is_air(x, y, z):
                    mix_set(c, x, y, z, STONE)
                    c.column_down(x, y - 1, z, "cobblestone", 10)
                if i % 3 == 1:
                    c.set(x, y + 1, z, "cobblestone_wall")
    top_y = STAIR_TOP_Y + 1
    lantern_post(c, STAIR_X[1] + 2, top_y, STAIR_TOP_Z - 1, 3, "spruce")
    foot_z = STAIR_TOP_Z + STAIR_N
    lantern_post(c, STAIR_X[0] - 1, stair_y(foot_z - 1), foot_z + 1, 3, "spruce")
    c.mark("falls_stair_top", STAIR_X[0], top_y, STAIR_TOP_Z - 1)
    c.mark("falls_stair_foot", STAIR_X[0], stair_y(foot_z - 1), foot_z)


def _overlook(c, t):
    """A timber deck that hangs out over the cliff beside the fall."""
    x1, x2 = DECK_X
    # find the edge: the first row south of which the ground has dropped
    edge = None
    for z in range(-34, -22):
        if t.h((x1 + x2) // 2, z) < STAIR_TOP_Y - 3:
            edge = z
            break
    if edge is None:
        return
    y = STAIR_TOP_Y
    z1, z2 = edge - 3, edge + 1
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            c.set(x, y, z, "spruce_planks" if (x + z) % 3 else "dark_oak_planks")
            for yy in range(y + 1, y + 4):
                c.set(x, yy, z, "air")
    # braces under the part that hangs free
    for x in (x1, x2):
        c.set(x, y - 1, edge, "spruce_fence")
        c.set(x, y - 1, edge + 1, stair("spruce", "north", top=True))
        c.set(x, y - 2, edge, "spruce_fence")
    # rail on three sides, a seat, a lamp
    for x in range(x1, x2 + 1):
        c.set(x, y + 1, z2, "spruce_fence")
    for z in range(edge - 1, z2 + 1):
        c.set(x1, y + 1, z, "spruce_fence")
        c.set(x2, y + 1, z, "spruce_fence")
    bench(c, x1 + 1, y + 1, z1, facing="south", wood="spruce", length=2)
    c.set(x2, y + 2, z2, "lantern[hanging=false]")
    c.mark("overlook", (x1 + x2) // 2, y + 1, edge - 1, yaw=-20)
