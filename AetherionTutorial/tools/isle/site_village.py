"""Morningbell village: timber-framed houses round a small square with the village oak, a well, two market
stalls and the inn. The way from the forge to the harbour runs through it; nothing is taught here, it is the
place that shows people live on this island.
"""
import math

import layout as L
from build import Canvas, Mix, mix_set, stair, slab, lantern_post, disc
from houses import House, dress_front, lean_to, PLASTER_WARM, PLASTER_OCHRE
from noise import hash01, Rng

G = L.VILLAGE_Y
SX, SZ = L.VILLAGE                    # the square: the oak stands in its middle
WELL = (SX + 5, SZ - 5)
BRIDGE_Z = 46                         # the west footbridge lies on rows z and z + 1

PLAZA = Mix([("cobblestone", 5), ("andesite", 2.5), ("stone_bricks", 1.3), ("mossy_cobblestone", 1.0),
             ("gravel", 0.9)], scale=2.4, seed=901)
KERB = Mix([("stone_bricks", 5), ("mossy_stone_bricks", 1.2), ("cracked_stone_bricks", 1.0)], scale=1.6, seed=903)
BRICK = Mix([("bricks", 5), ("granite", 1), ("mud_bricks", 1.4)], scale=2.0, seed=905)
PAVED = ("grass_block", "dirt", "dirt_path", "coarse_dirt", "gravel", "cobblestone", "andesite", "mossy_cobblestone",
         "stone_bricks")

# (name, origin x, origin z, rot, w, d, options)   rot: 0 front south, 1 west, 2 north, 3 east
#   rot 0: x ox..ox+w-1, z oz..oz+d-1      rot 1: x ox-d+1..ox, z oz..oz+w-1
#   rot 2: x ox-w+1..ox, z oz-d+1..oz      rot 3: x ox..ox+d-1, z oz-w+1..oz
HOUSES = [
    # the inn, north of the square: x 36..46, z 28..35
    ("inn", 36, 28, 0, 11, 8, dict(floors=2, jetty=True, ridge="x", roof="slate", chimney=("right", 3),
                                    door=5, plaster=PLASTER_OCHRE, seed=1, stone_ground="full")),
    # east of the square, gable to the oak: x 46..52, z 45..51
    ("east", 52, 45, 1, 7, 7, dict(floors=2, jetty=True, ridge="z", roof="shingle", chimney=("left", 3), seed=4)),
    # south of the square: x 38..46, z 56..62
    ("south", 46, 62, 2, 9, 7, dict(floors=2, jetty=True, ridge="x", roof="spruce", chimney=("left", 3),
                                     door=3, seed=5)),
    # west, on the forge road: x 20..26, z 36..44
    ("west", 20, 44, 3, 9, 7, dict(floors=2, jetty=True, ridge="x", roof="shingle", chimney=("right", 2),
                                    door=5, seed=2, plaster=PLASTER_WARM)),
    # the cot by the stream: x 21..26, z 50..55
    ("cot", 21, 55, 3, 6, 6, dict(floors=1, ridge="z", roof="spruce", seed=6, chimney=("left", 2))),
    # the barn behind the south house: x 50..56, z 56..63
    ("barn", 56, 63, 2, 7, 8, dict(floors=1, ridge="z", roof="tile", seed=7, stone_ground="full", gable_window=False,
                                    fit_out=False)),
]


def footprint(ox, oz, rot, w, d):
    """World box (x1, z1, x2, z2) of a house."""
    pts = []
    for (x, z) in ((0, 0), (w - 1, d - 1)):
        if rot == 1:
            x, z = -z, x
        elif rot == 2:
            x, z = -x, -z
        elif rot == 3:
            x, z = z, -x
        pts.append((ox + x, oz + z))
    return (min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts))


def prepare(t):
    import sites
    for (name, ox, oz, rot, w, d, kw) in HOUSES:
        x1, z1, x2, z2 = footprint(ox, oz, rot, w, d)
        sites.level_box(t, x1 - 1, z1 - 1, x2 + 1, z2 + 1, G, 3)
        sites.clear_box(t, x1, z1, x2, z2, 4)
    sites.clear_disc(t, SX, SZ, 13)
    sites.clear_box(t, 32, 29, 35, 34, 2)           # the inn's stable
    # the square reads as a paved place
    for (x, z) in disc(SX, SZ, 8.5):
        zi, xi = z - L.Z0, x - L.X0
        if t.land[zi, xi] and not t.stream[zi, xi]:
            t.H[zi, xi] = G


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _square(c, t)
    built = {}
    for (name, ox, oz, rot, ww, dd, kw) in HOUSES:
        hc = Canvas(w, ox, G + 1, oz, rot)
        h = House(hc, ww, dd, **kw)
        h.build()
        if name != "barn":
            dress_front(hc, ww, dd, h, seed=len(name) * 7 + ww)
        hc.mark("house_" + name, h.door, 0, dd + 1)
        built[name] = (hc, h)
    _well(c)
    # the stalls stand in front of the houses and face the oak
    _stall(c, 44, 53, 2, ("red_wool", "white_wool"),
           ["hay_block[axis=y]", "composter[level=7]", "barrel[facing=up]"],
           ["potted_red_tulip", None, "cake[bites=0]"])
    _stall(c, 29, 46, 3, ("yellow_wool", "white_wool"),
           ["barrel[facing=up]", "barrel[facing=up]", "dried_kelp_block"],
           [None, "sea_pickle[pickles=3,waterlogged=false]", "lantern[hanging=false]"])
    _inn(built["inn"])
    _barn(built["barn"])
    _oven(c)
    _gardens(c, t)
    _footbridge(c, t)
    lantern_post(c, 29, G + 1, 52, 3, "spruce", arm="east")
    lantern_post(c, 46, G + 1, 43, 3, "spruce", arm="west")
    lantern_post(c, 35, G + 1, 38, 3, "spruce", arm="south")
    lantern_post(c, 36, G + 1, 55, 3, "spruce", arm="north")
    c.mark("village_square", SX, G + 1, SZ + 5)
    c.mark("baker", 49, G + 1, 41, yaw=150)
    c.mark("innkeeper", 42, G + 1, 38, yaw=0)
    c.mark("market_woman", 42, G + 1, 52, yaw=160)
    c.mark("fishwife", 30, G + 1, 44, yaw=-90)


def after_flora(w, t, forest):
    y = t.h(SX, SZ)
    forest.old_oak(SX, y, SZ, Rng(4242))
    c = Canvas(w, 0, 0, 0)
    # a ring seat round the village oak
    for (dx, dz, f) in ((-2, 0, "east"), (-2, 1, "east"), (3, 0, "west"), (3, 1, "west"),
                        (0, -2, "south"), (1, -2, "south"), (0, 3, "north"), (1, 3, "north")):
        if c.is_air(SX + dx, y + 1, SZ + dz):
            c.set(SX + dx, y + 1, SZ + dz, stair("spruce", f))
    w.mark("village_oak", SX, y + 1, SZ)


def _square(c, t):
    for (x, z) in disc(SX, SZ, 9.2):
        d = math.hypot(x - SX - 0.5, z - SZ - 0.5)
        if d > 7.2 and hash01(x, 0, z, 903) < (d - 7.2) / 2.2:
            continue
        zi, xi = z - L.Z0, x - L.X0
        if not t.land[zi, xi] or t.stream[zi, xi]:
            continue
        y = t.h(x, z)
        if c.name(x, y, z) not in PAVED:
            continue
        if d < 3.2:
            c.set(x, y, z, "coarse_dirt" if hash01(x, 1, z, 907) < 0.6 else "rooted_dirt")
        elif d < 4.25:
            mix_set(c, x, y, z, KERB)
        else:
            mix_set(c, x, y, z, PLAZA)


def _well(c):
    x, z = WELL
    y = G + 1
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if dx == 0 and dz == 0:
                c.set(x, G, z, "water")
                c.set(x, G - 1, z, "water")
                c.set(x, G - 2, z, "cobblestone")
            else:
                c.set(x + dx, G, z + dz, "cobblestone")
                c.set(x + dx, G - 1, z + dz, "cobblestone")
                c.set(x + dx, y, z + dz, "stone_brick_wall" if (dx + dz) % 2 else "cobblestone_wall")
    # the windlass under a little roof
    for dz in (-1, 1):
        c.set(x, y + 1, z + dz, "spruce_fence")
        c.set(x, y + 2, z + dz, "spruce_fence")
    for dz in (-1, 0, 1):
        c.set(x, y + 3, z + dz, "stripped_spruce_log[axis=z]")
    for dz in (-2, -1, 0, 1, 2):
        c.set(x - 1, y + 3, z + dz, stair("dark_oak", "east"))
        c.set(x + 1, y + 3, z + dz, stair("dark_oak", "west"))
    for dz in (-2, 2):
        c.set(x, y + 3, z + dz, slab("dark_oak", top=True))
    for dz in (-1, 0, 1):
        c.set(x, y + 4, z + dz, slab("dark_oak"))
    c.set(x, y + 2, z, "chain[axis=y]")
    c.set(x, y + 1, z, "chain[axis=y]")
    c.mark("well", x, y, z + 2)


def _stall(c, x, z, rot, wool, goods, counter):
    """Market stall, five wide and three deep: striped awning in two steps, the counter to the front."""
    s = Canvas(c.w, x, G + 1, z, rot)
    for px in (0, 4):
        for yy in range(0, 3):
            s.set(px, yy, 0, "spruce_fence")
        for yy in range(0, 2):
            s.set(px, yy, 2, "spruce_fence")
    for i in range(5):
        col = wool[i % 2]
        s.set(i, 3, 0, col)
        s.set(i, 3, 1, col)
        s.set(i, 2, 2, col)
    for i in (1, 2, 3):
        s.set(i, 0, 2, slab("spruce", top=True))
        g = counter[(i - 1) % len(counter)]
        if g:
            s.set(i, 1, 2, g)
    for i, g in enumerate(goods):
        s.set(1 + i, 0, 0, g)
    s.set(0, 0, 1, "barrel[facing=up]")


def _inn(built):
    """The Sleepy Gull: a stable lean-to on the west side, a bench and barrels by the door, a table outside."""
    hc, h = built
    lean_to(hc, h, "left", 1, 6, depth=3, kind="stable")
    front = h.d + 1                     # the first free row in front of the jetty
    hc.set(2, 0, front, stair("spruce", "north"))
    hc.set(3, 0, front, stair("spruce", "north"))
    hc.set(9, 0, front, "barrel[facing=up]")
    hc.set(10, 0, front, "barrel[facing=up]")
    hc.set(10, 1, front, "barrel[facing=east]")
    hc.set(7, 0, front + 1, "spruce_fence")
    hc.set(7, 1, front + 1, "spruce_pressure_plate")
    hc.set(6, 0, front + 1, stair("spruce", "west"))
    hc.set(8, 0, front + 1, stair("spruce", "east"))
    # the sign bracket over the door (the plugin hangs the name on it)
    hc.set(5, 4, front, "spruce_fence")
    hc.set(5, 3, front, "chain[axis=y]")
    hc.mark("inn_sign", 5, 2, front)
    hc.mark("inn_door", 5, 0, front)


def _barn(built):
    """A wide doorway, hay inside, a cart outside."""
    hc, h = built
    w, d = h.w, h.d
    zf = d - 1
    mid = w // 2
    for x in (mid - 1, mid, mid + 1):
        for yy in (0, 1, 2):
            hc.set(x, yy, zf, "air")
    for x in (mid - 2, mid + 2):
        for yy in (0, 1, 2):
            hc.set(x, yy, zf, "stripped_spruce_log[axis=y]")
    for x in range(mid - 2, mid + 3):
        hc.set(x, 3, zf, "stripped_spruce_log[axis=x]")
    for (x, z, yy) in ((1, 1, 0), (2, 1, 0), (1, 2, 0), (1, 1, 1), (w - 2, 1, 0), (w - 2, 2, 0), (w - 2, 1, 1), (2, 1, 1)):
        hc.set(x, yy, z, "hay_block[axis=%s]" % ("x" if (x + z + yy) % 2 else "y"))
    hc.set(mid, 3, d // 2, "lantern[hanging=true]")
    _cart(hc.sub(mid + 2, 0, zf + 3))


def _cart(c):
    """A small hay cart, its length along x."""
    for x in (0, 1, 2):
        c.set(x, 0, 0, slab("spruce", top=True))
    c.set(0, 1, 0, "hay_block[axis=x]")
    c.set(1, 1, 0, "hay_block[axis=x]")
    for x in (0, 2):
        c.set(x, 0, -1, "dark_oak_trapdoor[facing=north,open=true,half=bottom]")
        c.set(x, 0, 1, "dark_oak_trapdoor[facing=south,open=true,half=bottom]")
    c.set(3, 0, 0, "spruce_fence")


def _oven(c):
    """The bread oven beside the east house: a brick dome with a mouth and a little stack."""
    y = G + 1
    for x in (48, 49, 50):
        for z in (42, 43):
            mix_set(c, x, y, z, BRICK)
    for x in (48, 50):
        for z in (42, 43):
            c.set(x, y + 1, z, stair("brick", "east" if x == 48 else "west"))
    c.set(49, y + 1, 43, "bricks")
    c.set(49, y + 1, 42, "bricks")
    c.set(49, y, 42, "smoker[facing=north,lit=true]")
    c.set(49, y + 2, 43, "brick_wall")
    c.set(49, y + 3, 43, "campfire[lit=true,signal_fire=false]")
    c.set(47, y, 42, "barrel[facing=up]")
    c.set(51, y, 42, "hay_block[axis=y]")


def _gardens(c, t):
    """Fenced plots behind the houses: rows of flowers, a few berry bushes."""
    flowers = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "allium", "azure_bluet", "red_tulip", "white_tulip"]

    def plot(x1, z1, x2, z2, gate, seed):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if not t.inb(x, z) or t.path[z - L.Z0, x - L.X0] or t.stream[z - L.Z0, x - L.X0]:
                    continue
                edge = x in (x1, x2) or z in (z1, z2)
                gy = t.h(x, z)
                if c.name(x, gy, z) not in ("grass_block", "dirt", "coarse_dirt") or not c.is_air(x, gy + 1, z):
                    continue
                if edge:
                    if (x, z) == gate:
                        c.set(x, gy + 1, z, "spruce_fence_gate[facing=%s,open=false,in_wall=false]"
                              % ("south" if z in (z1, z2) else "east"))
                    else:
                        c.set(x, gy + 1, z, "spruce_fence")
                    continue
                r = hash01(x, seed, z, 911)
                if (x - x1) % 2 == 1 and r < 0.8:
                    if r < 0.12:
                        c.set(x, gy + 1, z, "sweet_berry_bush[age=3]")
                    else:
                        c.set(x, gy + 1, z, flowers[int(hash01(x, seed, z // 2, 913) * 97) % len(flowers)])
                elif r < 0.25:
                    c.set(x, gy, z, "coarse_dirt")

    plot(38, 64, 45, 68, (41, 68), 1)          # behind the south house
    plot(19, 30, 23, 34, (23, 32), 3)          # north of the west house
    plot(54, 46, 58, 51, (56, 51), 4)          # behind the east house


def _footbridge(c, t):
    """Plank bridge on the lane to the farms."""
    z = BRIDGE_Z
    xs = [x for x in range(6, 22) if t.stream[z - L.Z0, x - L.X0] or t.stream[z + 1 - L.Z0, x - L.X0]]
    if not xs:
        return
    a, b = min(xs) - 1, max(xs) + 1
    lvl = int(t.water[z - L.Z0, xs[0] - L.X0])
    low = lvl < G                       # water below the banks: the deck lies flush with the ground
    for x in range(a, b + 1):
        for zz in (z, z + 1):
            wood = "spruce" if (x + zz) % 4 else "dark_oak"
            c.set(x, G if low else G + 1, zz, slab(wood, top=low))
    for x in (a, b):
        for zz in (z - 1, z + 2):
            c.set(x, G + 1, zz, "spruce_fence")
            c.set(x, G + 2, zz, "spruce_fence")
    for x in range(a + 1, b):
        for zz in (z - 1, z + 2):
            c.set(x, G + 2, zz, "spruce_fence")
    c.mark("bridge_west", (a + b) // 2, G + 1, z)
