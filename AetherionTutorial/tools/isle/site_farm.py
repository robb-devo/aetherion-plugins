"""Downs Farm and the windmill, on the open south-west of the island. Scenery: golden wheat that shows from the
bell hill, hedged fields, a yard with house and barn, and a stone tower mill whose sails stand over the downs.

The crops are real (they are part of the picture); the tutorial plugin keeps hands off them.
"""
import layout as L
from build import Canvas, Mix, RoofMat, mix_set, stair, slab, lantern_post, gable_roof, round_cells, shell
from houses import House, dress_front, lean_to, PLASTER_WARM
from noise import hash01, value
from site_village import _barn, _cart

G = 71                              # the farmyard
MX, MZ = -44, 39                    # the mill
MG = 73

YARD = Mix([("coarse_dirt", 4), ("dirt_path", 2.5), ("gravel", 1.5), ("cobblestone", 1.2), ("rooted_dirt", 1)],
           scale=2.6, seed=1401)
MILL = Mix([("cobblestone", 4), ("andesite", 3), ("stone_bricks", 2.5), ("mossy_cobblestone", 1.3),
            ("mossy_stone_bricks", 0.8)], scale=2.2, seed=1403)
CAP = RoofMat("dark_oak", "spruce", 0.2, ridge="dark_oak", seed=1405)

# (x1, z1, x2, z2, crop)
FIELDS = [
    (-52, 63, -31, 76, "wheat"),          # the big field below the yard
    (-28, 63, -14, 72, "roots"),
    (-66, 52, -52, 62, "stubble"),
    (-15, 50, -3, 60, "wheat"),
]


def prepare(t):
    import sites
    sites.level_box(t, -41, 51, -19, 61, G, 3)
    sites.clear_box(t, -43, 49, -17, 62, 2)
    for (x1, z1, x2, z2, crop) in FIELDS:
        sites.clear_box(t, x1, z1, x2, z2, 2)
    sites.clear_disc(t, MX, MZ, 12)
    sites.level_box(t, MX - 3, MZ - 3, MX + 3, MZ + 4, MG, 2)


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _yard(c, t)
    # the farmhouse, west of the yard, front to the east: x -40..-34, z 52..60
    hc = Canvas(w, -40, G + 1, 60, 3)
    h = House(hc, 9, 7, floors=2, jetty=True, ridge="x", roof="shingle", seed=31, door=4, chimney=("right", 2),
              plaster=PLASTER_WARM)
    h.build()
    dress_front(hc, 9, 7, h, seed=7)
    lean_to(hc, h, "left", 1, 5, depth=3, kind="wood")
    hc.mark("farmhouse", 4, 0, 9)
    # the barn, east of the yard, front to the west: x -26..-20, z 52..59
    bc = Canvas(w, -20, G + 1, 52, 1)
    b = House(bc, 8, 7, floors=1, ridge="z", roof="spruce", seed=32, stone_ground="full", gable_window=False,
              fit_out=False)
    b.build()
    _barn((bc, b))
    for (x1, z1, x2, z2, crop) in FIELDS:
        _field(c, t, x1, z1, x2, z2, crop)
    _pen(c, t)
    _mill(Canvas(w, MX, MG + 1, MZ))
    c.mark("farmer", -31, G + 1, 57, yaw=90)
    c.mark("shepherd", -15, G + 1, 47, yaw=0)
    c.mark("farm_yard", -30, G + 1, 56)


def _yard(c, t):
    for x in range(-33, -26):
        for z in range(51, 62):
            if not t.is_land(x, z):
                continue
            y = t.h(x, z)
            if c.name(x, y, z) in ("grass_block", "dirt", "dirt_path", "coarse_dirt"):
                if hash01(x, 1, z, 1407) < 0.88:
                    mix_set(c, x, y, z, YARD)
    y = G + 1
    # the trough and the pump, a dung heap, a cart, hives against the house garden
    c.set(-30, y, 58, "water_cauldron[level=3]")
    c.set(-29, y, 58, "water_cauldron[level=2]")
    c.set(-31, y, 58, "spruce_fence")
    c.set(-31, y + 1, 58, "spruce_fence")
    c.set(-31, y + 2, 58, "spruce_trapdoor[facing=east,open=false,half=bottom]")
    for (x, z) in ((-28, 61), (-27, 61), (-28, 60)):
        c.set(x, y, z, "podzol" if (x + z) % 2 else "coarse_dirt")
    c.set(-27, y + 1, 61, "brown_mushroom")
    _cart(c.sub(-31, y, 53))
    for (x, z) in ((-42, 62), (-40, 62), (-38, 62)):
        c.set(x, y, z, "spruce_fence")
        c.set(x, y + 1, z, "beehive[facing=north,honey_level=5]")
    lantern_post(c, -33, y, 52, 3, "spruce")
    lantern_post(c, -27, y, 60, 3, "spruce")


def _hedge(c, t, x, z, seed):
    y = t.h(x, z)
    if not t.is_land(x, z) or t.path[z - L.Z0, x - L.X0] or not c.is_air(x, y + 1, z):
        return
    r = hash01(x, seed, z, 1409)
    leaves = "flowering_azalea_leaves" if r < 0.1 else ("azalea_leaves" if r < 0.45 else "oak_leaves")
    c.set(x, y + 1, z, "%s[persistent=true,distance=1]" % leaves)
    if hash01(x, seed + 1, z, 1411) < 0.22:
        c.set(x, y + 2, z, "%s[persistent=true,distance=1]" % leaves)


def _field(c, t, x1, z1, x2, z2, crop):
    """A hedged field that lies on the slope as it finds it."""
    gate = (x1 + (x2 - x1) // 3, z1)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not t.is_land(x, z) or t.path[z - L.Z0, x - L.X0]:
                continue
            y = t.h(x, z)
            edge = x in (x1, x2) or z in (z1, z2)
            if edge:
                if (x, z) == gate or (x, z) == (gate[0] + 1, gate[1]):
                    continue
                _hedge(c, t, x, z, 3)
                continue
            if c.name(x, y, z) not in ("grass_block", "dirt", "coarse_dirt") or not c.is_air(x, y + 1, z):
                continue
            r = hash01(x, 5, z, 1413)
            if crop == "wheat":
                c.set(x, y, z, "farmland[moisture=7]")
                c.set(x, y + 1, z, "wheat[age=7]" if r > 0.06 else "wheat[age=6]")
            elif crop == "roots":
                c.set(x, y, z, "farmland[moisture=7]")
                row = (x - x1) % 6
                kind = "carrots[age=7]" if row < 2 else ("potatoes[age=7]" if row < 4 else "beetroots[age=3]")
                c.set(x, y + 1, z, kind)
            else:
                # cut: stubble and the last sheaves
                c.set(x, y, z, "coarse_dirt" if value(x, z, 3.0, 1415) < 0.55 else "farmland[moisture=0]")
                if r < 0.035:
                    c.set(x, y + 1, z, "hay_block[axis=%s]" % ("x" if r < 0.017 else "z"))
    if crop == "wheat":
        sx, sz = (x1 + x2) // 2, (z1 + z2) // 2
        y = t.h(sx, sz)
        c.set(sx, y, sz, "coarse_dirt")
        c.set(sx, y + 1, sz, "spruce_fence")
        c.set(sx, y + 2, sz, "hay_block[axis=y]")
        c.set(sx, y + 3, sz, "carved_pumpkin[facing=north]")
        c.set(sx - 1, y + 2, sz, "spruce_fence")
        c.set(sx + 1, y + 2, sz, "spruce_fence")


def _pen(c, t):
    """The sheep fold north of the lane: a fenced paddock with a shelter."""
    x1, z1, x2, z2 = -22, 38, -11, 44
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            if not t.is_land(x, z) or t.path[z - L.Z0, x - L.X0]:
                continue
            y = t.h(x, z)
            if x in (x1, x2) or z in (z1, z2):
                if (x, z) == (x1 + 5, z2):
                    c.set(x, y + 1, z, "spruce_fence_gate[facing=south,open=false,in_wall=false]")
                elif c.is_air(x, y + 1, z):
                    c.set(x, y + 1, z, "spruce_fence")
    # a shelter in the corner: a low roof of slabs falling into the fold, on four posts
    y = t.h(x2 - 2, z1 + 1)
    for x in (x2 - 4, x2):
        for yy in (y + 1, y + 2, y + 3):
            c.set(x, yy, z1, "spruce_fence")
        for yy in (y + 1, y + 2):
            c.set(x, yy, z1 + 2, "spruce_fence")
    for x in range(x2 - 4, x2 + 1):
        c.set(x, y + 4, z1, slab("spruce"))
        c.set(x, y + 3, z1 + 1, slab("spruce", top=True))
        c.set(x, y + 3, z1 + 2, slab("spruce"))
    c.set(x2 - 2, y + 1, z1 + 1, "hay_block[axis=x]")
    c.set(x1 + 2, y + 1, z1 + 2, "water_cauldron[level=3]")
    c.mark("sheep_fold", (x1 + x2) // 2, y + 1, (z1 + z2) // 2)


def _mill(c):
    """A tower mill: seven across at the foot, five above the shoulder, a boat cap, four sails to the south."""
    wide, slim = round_cells(3.5), round_cells(2.5)
    for (dx, dz) in wide:
        mix_set(c, dx, -1, dz, MILL)
        c.column_down(dx, -2, dz, "cobblestone", 10)
    for y in range(0, 5):
        for (dx, dz) in shell(wide):
            mix_set(c, dx, y, dz, MILL)
    for (dx, dz) in wide:
        if (dx, dz) not in slim:
            c.set(dx, 5, dz, "cobblestone_slab[type=bottom]" if (dx + dz) % 2 else "stone_brick_slab[type=bottom]")
    for y in range(5, 10):
        for (dx, dz) in shell(slim):
            mix_set(c, dx, y, dz, MILL)
    # floors, a ladder, the door to the east, windows
    for (dx, dz) in wide - shell(wide):
        if (dx, dz) != (-2, 0):
            c.set(dx, 4, dz, "spruce_planks")
    for y in range(0, 5):
        c.set(-2, y, 0, "ladder[facing=east]")
    for y in (0, 1):
        c.set(3, y, 0, "air")
    c.set(3, 0, 0, "spruce_door[facing=west,half=lower,hinge=left,open=false]")
    c.set(3, 1, 0, "spruce_door[facing=west,half=upper,hinge=left,open=false]")
    c.set(0, 2, -3, "glass_pane")
    c.set(0, 7, -2, "glass_pane")
    c.set(-2, 7, 0, "glass_pane")
    c.set(0, 0, 0, "smooth_stone_slab[type=bottom]")              # the stones
    c.set(0, 3, 1, "lantern[hanging=true]")
    c.set(1, 0, -2, "barrel[facing=up]")
    # the cap, its ridge along the windshaft
    def fill(x, y, z):
        return "spruce_planks"
    gable_roof(c, -2, -2, 2, 2, 10, CAP, ridge_axis="z", overhang=0, gable_fill=fill)
    # the windshaft and the sails: a cross of four stocks, each with its cloth on the trailing side
    c.set(0, 10, 3, "stripped_dark_oak_log[axis=z]")
    c.set(0, 10, 4, "stripped_dark_oak_log[axis=z]")
    z = 4
    for k in range(1, 8):
        c.set(0, 10 + k, z, "stripped_spruce_log[axis=y]")
        c.set(0, 10 - k, z, "stripped_spruce_log[axis=y]")
        c.set(k, 10, z, "stripped_spruce_log[axis=x]")
        c.set(-k, 10, z, "stripped_spruce_log[axis=x]")
        if k >= 2:
            c.set(1, 10 + k, z, "white_wool")
            c.set(-1, 10 - k, z, "white_wool")
            c.set(k, 9, z, "white_wool")
            c.set(-k, 11, z, "white_wool")
    # sacks and a cart by the door
    c.set(4, 0, 2, "hay_block[axis=y]")
    c.set(4, 0, -2, "barrel[facing=up]")
    c.set(5, 0, -2, "barrel[facing=up]")
    c.set(4, 1, -2, "barrel[facing=up]")
    c.mark("mill_door", 5, 0, 0)
    c.mark("miller", 5, 0, 1, yaw=-90)
