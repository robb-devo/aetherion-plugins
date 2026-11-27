"""The lighthouse on the east point: a white tower with a red band, a gallery, a lantern under a green copper
cap, and the keeper's cot beside it. No lesson happens here; it is there to be seen from everywhere, and to be
found by those who wander."""
import layout as L
from build import Canvas, Mix, RoofMat, mix_set, stair, slab, lantern_post, pyramid_roof, round_cells, shell
from houses import House, dress_front, lean_to
from noise import hash01

G = L.LIGHT_Y
TX, TZ = 121, -22
FOOT = Mix([("stone_bricks", 4), ("cobblestone", 2), ("mossy_stone_bricks", 1.3), ("andesite", 1)], scale=2.0, seed=1301)
CAP = RoofMat("waxed_weathered_cut_copper", None, 0.0, ridge="waxed_weathered_cut_copper", seed=1303)

SHAFT = round_cells(3.5)         # the tower: seven across all the way up
KERB = round_cells(4.5)
H = 18                           # the gallery floor (courses above the ground)


def prepare(t):
    import sites
    sites.clear_disc(t, TX, TZ, 13)
    sites.level_box(t, TX - 4, TZ - 4, TX + 4, TZ + 4, G, 2)
    sites.level_box(t, 111, -30, 117, -24, G, 2)


def build(w, t):
    c = Canvas(w, TX, G + 1, TZ)
    _tower(c)
    hc = Canvas(w, 112, G + 1, -29, 0)
    h = House(hc, 5, 5, floors=1, ridge="x", roof="slate", seed=21, chimney=("left", 2), gable_window=False)
    h.build()
    dress_front(hc, 5, 5, h, seed=3)
    lean_to(hc, h, "right", 1, 3, depth=3, kind="wood")
    g = Canvas(w, 0, 0, 0)
    for (x, z) in ((116, -20), (116, -24)):
        lantern_post(g, x, G + 1, z, 3, "spruce")
    g.set(117, G + 1, -19, "barrel[facing=up]")
    g.set(118, G + 1, -18, "barrel[facing=up]")
    g.set(118, G + 2, -18, "barrel[facing=up]")
    # a bench that looks out to sea
    g.set(126, G + 1, -21, stair("spruce", "west"))
    g.set(126, G + 1, -22, stair("spruce", "west"))
    g.mark("lighthouse_door", TX - 4, G + 1, TZ)
    g.mark("lighthouse_gallery", TX, G + 1 + H + 1, TZ - 3)
    g.mark("keeper", 115, G + 1, -22, yaw=-90)


def _tower(c):
    wall = shell(SHAFT)
    inside = SHAFT - wall
    for (dx, dz) in KERB:
        mix_set(c, dx, -1, dz, FOOT)
        c.column_down(dx, -2, dz, "cobblestone", 10)
        if (dx, dz) not in SHAFT:
            c.set(dx, 0, dz, "stone_brick_slab[type=bottom]")
    for y in range(0, H):
        for (dx, dz) in wall:
            if y < 2:
                mix_set(c, dx, y, dz, FOOT)
            else:
                c.set(dx, y, dz, "red_terracotta" if 9 <= y <= 11 else "calcite")
    # the newel: a stone column up the middle that carries the light; the ladder climbs it
    for y in range(-1, H + 2):
        c.set(0, y, 0, "stone_bricks" if y % 4 else "chiseled_stone_bricks")
    for y in range(0, H + 1):
        c.set(1, y, 0, "ladder[facing=east]")
    for fy in (6, 12):
        for (dx, dz) in inside:
            if (dx, dz) not in ((0, 0), (1, 0)):
                c.set(dx, fy, dz, "spruce_planks")
    # the gallery: a square deck on brackets, a rail, and the lantern in its middle
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if (dx, dz) in ((0, 0), (1, 0)):
                continue
            ring = max(abs(dx), abs(dz))
            c.set(dx, H, dz, "spruce_planks" if ring <= 2 else "smooth_stone_slab[type=top]")
            if ring == 4:
                c.set(dx, H + 1, dz, "iron_bars")
            if ring == 2 and not (dx == 0 and dz == -2):
                for y in (H + 1, H + 2, H + 3):
                    c.set(dx, y, dz, "glass_pane")
    c.set(0, H + 3, -2, "glass_pane")
    for (dx, dz, f) in ((4, 0, "west"), (-4, 0, "east"), (0, 4, "north"), (0, -4, "south"),
                        (4, 2, "west"), (4, -2, "west"), (-4, 2, "east"), (-4, -2, "east"),
                        (2, 4, "north"), (-2, 4, "north"), (2, -4, "south"), (-2, -4, "south")):
        c.set(dx, H - 1, dz, stair("stone_brick", f, top=True))
    c.set(0, H + 2, 0, "ochre_froglight[axis=y]")
    c.set(0, H + 3, 0, "chiseled_stone_bricks")
    top = pyramid_roof(c, -2, -2, 2, 2, H + 4, CAP, overhang=0)
    c.set(0, top + 1, 0, "lightning_rod[facing=up]")
    # the door (west, towards the way in), and windows up the shaft
    for y in (0, 1):
        c.set(-3, y, 0, "air")
    c.set(-3, 0, 0, "spruce_door[facing=east,half=lower,hinge=left,open=false]")
    c.set(-3, 1, 0, "spruce_door[facing=east,half=upper,hinge=left,open=false]")
    c.set(-3, 2, 0, "chiseled_stone_bricks")
    for (dx, y, dz) in ((0, 4, 3), (0, 4, -3), (0, 8, 3), (-3, 14, 0), (0, 15, -3), (3, 10, 0), (0, 15, 3)):
        c.set(dx, y, dz, "glass_pane")
    c.set(-1, 5, -1, "lantern[hanging=true]")
    c.set(-1, 11, 1, "lantern[hanging=true]")
    c.set(-2, 0, 1, "barrel[facing=up]")
    c.set(-1, 0, 2, "barrel[facing=up]")
