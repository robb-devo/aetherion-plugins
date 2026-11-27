"""The harbour: a stone quay, the pier, the ferry alongside it (a cog under sail, bow to the open sea, her
sail square to the island so the red bell on it shows from the village and from the Overlook), the empty frame
the harbour bell belongs in, the net loft and a fisher's hut.

Everything stands two blocks over the water: quay, pier and the ferry's deck share one level, so the way from
the village walks straight on board.
"""
import math

import layout as L
from build import Canvas, Mix, mix_set, stair, slab, lantern_post
from houses import House, dress_front, lean_to
from noise import hash01

G = 64                                  # the ground of the harbour apron (two over the sea)
SEA = L.SEA
QUAY_Z = 79                             # the last row of land between QUAY_X1 and QUAY_X2
QUAY_X1, QUAY_X2 = 17, 36
PIER_X1, PIER_X2 = 25, 27               # three wide
PIER_Z1, PIER_Z2 = 80, 99
HEAD_X1 = 21                            # the pier's end widens to the west (z HEAD_Z1..PIER_Z2)
HEAD_Z1 = 96
SHIP = (32, 96)                         # the ferry's centre; she lies north-south on the pier's east side
GANG_Z = (93, 94)                       # the gangway crosses the gap at x = 28
BELL = (22, 77)                         # the bell frame on the quay

APRON = Mix([("cobblestone", 5), ("andesite", 2.5), ("gravel", 1.5), ("stone_bricks", 1.2),
             ("mossy_cobblestone", 1.0)], scale=2.4, seed=1101)
QUAY = Mix([("stone_bricks", 4), ("cobblestone", 2.5), ("mossy_stone_bricks", 1.6), ("mossy_cobblestone", 1.4),
            ("andesite", 1.0)], scale=2.0, seed=1103)
DECK = Mix([("spruce_planks", 6), ("dark_oak_planks", 1.3)], scale=1.7, seed=1105)


def prepare(t):
    import sites
    sites.level_box(t, 18, 66, 46, QUAY_Z, G, 2)
    sites.clear_box(t, 14, 62, 50, QUAY_Z, 2)
    # a dredged berth where the ferry lies
    sx, sz = SHIP
    for z in range(sz - 15, sz + 18):
        for x in range(sx - 5, sx + 6):
            zi, xi = z - L.Z0, x - L.X0
            if not t.land[zi, xi]:
                t.H[zi, xi] = min(int(t.H[zi, xi]), SEA - 5)


def build(w, t):
    c = Canvas(w, 0, 0, 0)
    _apron(c, t)
    _quay(c, t)
    _pier(c, t)
    _bell_frame(c)
    _buildings(w, c, t)
    _clutter(c, t)
    _skiff(Canvas(w, 18, SEA, 83, 0))
    _skiff(Canvas(w, 9, SEA, 86, 1))
    _ship(Canvas(w, SHIP[0], SEA, SHIP[1], 1))
    for z in GANG_Z:
        c.set(PIER_X2 + 1, G, z, slab("spruce", top=True))
    c.mark("harbour", 26, G + 1, 77)
    c.mark("pier_head", 24, G + 1, 97)
    c.mark("captain", 26, G + 1, 91, yaw=-120)
    c.mark("gangway", PIER_X2 + 1, G + 1, GANG_Z[0])
    c.mark("bell_post", BELL[0], G + 2, BELL[1])


# ------------------------------------------------------------------------------------------------ the shore
def _apron(c, t):
    for x in range(17, 40):
        for z in range(72, QUAY_Z + 1):
            if not t.inb(x, z) or not t.land[z - L.Z0, x - L.X0] or t.stream[z - L.Z0, x - L.X0]:
                continue
            edge = min(x - 17, 39 - x, z - 72)
            if edge < 2 and hash01(x, 1, z, 1107) < (0.75 if edge == 0 else 0.4):
                continue
            y = t.h(x, z)
            if c.name(x, y, z) in ("grass_block", "dirt", "dirt_path", "coarse_dirt", "gravel", "sand", "cobblestone"):
                mix_set(c, x, y, z, APRON)


def _quay(c, t):
    for x in range(QUAY_X1, QUAY_X2 + 1):
        if not t.land[QUAY_Z - L.Z0, x - L.X0]:
            continue
        for y in range(SEA - 2, G + 1):
            mix_set(c, x, y, QUAY_Z, QUAY)
        c.set(x, G, QUAY_Z, "stone_bricks" if hash01(x, 2, QUAY_Z, 1109) < 0.8 else "mossy_stone_bricks")
    # bollards
    for x in (19, 23, 30, 34):
        c.set(x, G + 1, QUAY_Z, "cobblestone_wall")
    # the landing steps west of the pier
    for x in (20, 21):
        c.set(x, G, QUAY_Z, stair("stone_brick", "north"))
        c.set(x, G - 1, QUAY_Z + 1, stair("stone_brick", "north"))
        c.set(x, G - 2, QUAY_Z + 2, stair("mossy_stone_brick", "north"))
        for z in (QUAY_Z + 1, QUAY_Z + 2):
            for y in range(SEA - 3, G - 1 - (z - QUAY_Z - 1)):
                mix_set(c, x, y, z, QUAY)
    c.mark("landing_steps", 20, G, QUAY_Z + 1)


def _pier(c, t):
    def pile(x, z, top):
        for y in range(t.h(x, z) + 1, top + 1):
            c.set(x, y, z, "stripped_spruce_log[axis=y]")

    for z in range(PIER_Z1, PIER_Z2 + 1):
        for x in range(HEAD_X1 if z >= HEAD_Z1 else PIER_X1, PIER_X2 + 1):
            mix_set(c, x, G, z, DECK)
    # piles and the beams they carry; on the ferry's side they end under the deck and serve as fenders
    for i, z in enumerate(range(PIER_Z1 + 1, PIER_Z2 + 1, 3)):
        west = HEAD_X1 - 1 if z >= HEAD_Z1 else PIER_X1 - 1
        pile(west, z, G + (1 if i % 2 == 0 else 0))
        if z not in GANG_Z:
            pile(PIER_X2 + 1, z, G - 1)
        for x in range(west + 1, PIER_X2 + 1):
            c.set(x, G - 1, z, "stripped_spruce_log[axis=x]")
        if i % 2 == 0:
            c.set(west, G + 2, z, "lantern[hanging=false]")
    for x in (HEAD_X1 - 1, HEAD_X1 + 2):
        pile(x, PIER_Z2 + 1, G)
    pile(PIER_X2, PIER_Z2 + 1, G + 1)
    c.set(PIER_X2, G + 2, PIER_Z2 + 1, "lantern[hanging=false]")
    # freight waiting at the head
    for (x, z, b) in ((21, 96, "barrel[facing=up]"), (22, 96, "barrel[facing=up]"), (21, 97, "barrel[facing=east]"),
                      (21, 99, "hay_block[axis=x]"), (22, 99, "barrel[facing=up]"), (23, 99, "composter[level=2]")):
        c.set(x, G + 1, z, b)
    c.set(21, G + 2, 96, "barrel[facing=up]")
    # mooring lines to the ferry
    for z in (87, 98):
        c.set(PIER_X2 + 1, G + 1, z, "chain[axis=x]")


def _bell_frame(c):
    """Two posts and a beam on a low plinth, a hook, and no bell: Old Clanger wears it."""
    bx, bz = BELL
    for x in range(bx - 2, bx + 3):
        for z in (bz - 1, bz, bz + 1):
            c.set(x, G, z, "stone_bricks" if (x + z) % 3 else "mossy_stone_bricks")
    for x in (bx - 1, bx + 1):
        for y in range(G + 1, G + 4):
            c.set(x, y, bz, "stripped_spruce_log[axis=y]")
    for x in range(bx - 2, bx + 3):
        c.set(x, G + 4, bz, "stripped_spruce_log[axis=x]")
    for x in (bx - 2, bx + 2):
        c.set(x, G + 3, bz, stair("spruce", "east" if x < bx else "west", top=True))
    c.set(bx, G + 3, bz, "chain[axis=y]")
    c.set(bx, G + 5, bz, slab("dark_oak"))
    for x in (bx - 1, bx + 1):
        c.set(x, G + 5, bz, slab("dark_oak"))


def _buildings(w, c, t):
    # the net loft: tall and narrow, gable to the water, a hoist under the ridge. x 31..37, z 66..72
    hc = Canvas(w, 31, G + 1, 66, 0)
    h = House(hc, 7, 7, floors=2, jetty=True, ridge="z", roof="spruce", seed=11, door=3, stone_ground="low",
              chimney=("left", 2))
    top = h.build()
    dress_front(hc, 7, 7, h, seed=5)
    for z in (8, 9):
        hc.set(3, top - 1, z, "stripped_spruce_log[axis=z]")
    hc.set(3, top - 2, 9, "chain[axis=y]")
    hc.set(3, top - 3, 9, "chain[axis=y]")
    hc.set(3, top - 4, 9, "barrel[facing=up]")
    lean_to(hc, h, "right", 1, 5, depth=3, kind="store")
    hc.mark("net_loft", 3, 0, 9)
    # the fisher's hut: x 42..46, z 68..72
    fc = Canvas(w, 42, G + 1, 68, 0)
    f = House(fc, 5, 5, floors=1, ridge="x", roof="spruce", seed=12, chimney=("right", 2), gable_window=False)
    f.build()
    dress_front(fc, 5, 5, f, seed=9)
    fc.mark("fisher_hut", 2, 0, 6)


def _clutter(c, t):
    y = G + 1
    # nets drying on a rack in front of the hut
    for x in (42, 45):
        for yy in (y, y + 1, y + 2):
            c.set(x, yy, 74, "spruce_fence")
    for x in (43, 44):
        c.set(x, y + 2, 74, "spruce_fence")
        c.set(x, y + 1, 74, "cobweb")
    c.set(43, y, 74, "cobweb")
    # a hand crane on the quay
    for yy in range(y, y + 4):
        c.set(33, yy, QUAY_Z - 1, "stripped_spruce_log[axis=y]")
    c.set(33, y + 3, QUAY_Z, "stripped_spruce_log[axis=z]")
    c.set(33, y + 3, QUAY_Z + 1, "stripped_spruce_log[axis=z]")
    c.set(33, y + 2, QUAY_Z + 1, "chain[axis=y]")
    c.set(33, y + 1, QUAY_Z + 1, "chain[axis=y]")
    c.set(32, y, QUAY_Z - 1, "barrel[facing=up]")
    c.set(34, y, QUAY_Z - 2, "barrel[facing=up]")
    c.set(34, y + 1, QUAY_Z - 2, "barrel[facing=up]")
    c.set(35, y, QUAY_Z - 2, "composter[level=3]")
    # pots and a coil by the bell frame, a bench looking out
    c.set(18, y, 77, "composter[level=0]")
    c.set(18, y, 76, "composter[level=0]")
    c.set(19, y, 75, "barrel[facing=up]")
    c.set(30, y, 75, stair("spruce", "north"))
    c.set(31, y, 75, stair("spruce", "north"))
    lantern_post(c, 24, y, QUAY_Z - 1, 3, "spruce", arm="east")
    lantern_post(c, 29, y, 73, 3, "spruce")
    lantern_post(c, 38, y, 75, 3, "spruce")


def _skiff(c):
    """A small rowing boat lying in the water (its bow towards +x): a dry floor of slabs inside thin gunwales."""
    for x in range(0, 4):
        c.set(x, 0, 0, "spruce_slab[type=bottom,waterlogged=false]")
        c.set(x, 0, -1, "spruce_trapdoor[facing=north,open=true,half=bottom]")
        c.set(x, 0, 1, "spruce_trapdoor[facing=south,open=true,half=bottom]")
    c.set(4, 0, 0, "spruce_trapdoor[facing=east,open=true,half=bottom]")
    c.set(-1, 0, 0, "spruce_trapdoor[facing=west,open=true,half=bottom]")


# ------------------------------------------------------------------------------------------------ the ferry
def _hw(x):
    """Half the beam at this station (x from the stern at -12 to the stem at 12)."""
    if x <= -12:
        return 2
    if x <= 8:
        return 3
    if x <= 10:
        return 2
    if x == 11:
        return 1
    return 0


def _ship(c):
    """The ferry: a cog, 25 long and 7 in the beam. Local frame: +x the bow, y = 0 the row of blocks that lies in
    the water, the main deck at y = 2 (level with pier and quay)."""
    hull, wale, top, deck = "dark_oak_planks", "stripped_birch_log[axis=x]", "spruce_planks", "spruce_planks"
    for x in range(-12, 12):
        hw = _hw(x)
        # the bottom and the turn of the bilge
        for z in range(-max(hw - 1, 0), max(hw - 1, 0) + 1):
            c.set(x, -2, z, hull)
        for z in (-hw, hw):
            c.set(x, -1, z, hull)
            c.set(x, 0, z, hull)
            c.set(x, 1, z, wale)
            c.set(x, 2, z, top)
            c.set(x, 3, z, hull)
        for z in range(-hw + 1, hw):
            for y in (-1, 0, 1):
                c.set(x, y, z, "air")
            c.set(x, 2, z, "stripped_spruce_log[axis=x]" if z == 0 and -5 <= x <= 6 else deck)
            c.set(x, 3, z, "air")
    # stern: a flat transom; stem: a post that rakes up, and the bowsprit
    for z in range(-2, 3):
        for y in range(-1, 6):
            c.set(-12, y, z, hull if y in (-1, 0, 5) else (wale if y == 1 else top))
    for y in range(-2, 7):
        c.set(12, y, 0, "stripped_dark_oak_log[axis=y]")
    c.set(11, 3, 0, hull)
    c.set(13, 6, 0, "stripped_spruce_log[axis=x]")
    c.set(14, 7, 0, "stripped_spruce_log[axis=x]")
    c.set(15, 7, 0, "stripped_spruce_log[axis=x]")
    for y in range(-2, 2):
        c.set(-13, y, 0, "dark_oak_planks")          # the rudder
    # --- the sterncastle: x -12..-6, its deck at y 5
    for x in range(-12, -5):
        hw = _hw(x)
        for z in (-hw, hw):
            c.set(x, 3, z, top)
            c.set(x, 4, z, top)
            c.set(x, 5, z, hull)
        for z in range(-hw + 1, hw):
            c.set(x, 5, z, deck)
    for z in range(-2, 3):
        for y in (3, 4):
            c.set(-6, y, z, top)
    c.set(-6, 3, 0, "spruce_door[facing=west,half=lower,hinge=left,open=false]")
    c.set(-6, 4, 0, "spruce_door[facing=west,half=upper,hinge=left,open=false]")
    c.set(-6, 4, -2, "glass_pane")                   # (the other side carries the ladder)
    for z in (-1, 1):
        c.set(-12, 4, z, "glass_pane")
    for x in (-10, -8):
        for z in (-3, 3):
            c.set(x, 4, z, "glass_pane")
    # the rail round the sterncastle, the ladder up, two stern lanterns
    for x in range(-12, -5):
        hw = _hw(x)
        for z in (-hw, hw):
            c.set(x, 6, z, "spruce_fence")
    for z in range(-2, 3):
        c.set(-12, 6, z, "spruce_fence")
    for z in (-2, -1, 0, 1):
        c.set(-6, 6, z, "spruce_fence")
    for y in (3, 4, 5):
        c.set(-5, y, 2, "ladder[facing=east]")
    for z in (-2, 2):
        c.set(-12, 7, z, "lantern[hanging=false]")
    c.set(-12, 7, 0, "red_banner[rotation=12]")
    # the captain's cabin
    c.set(-11, 3, -2, "red_bed[facing=west,part=head]")
    c.set(-10, 3, -2, "red_bed[facing=west,part=foot]")
    c.set(-11, 3, 1, "cartography_table")
    c.set(-11, 3, 2, "chest[facing=east,type=single]")
    c.set(-8, 3, 2, "barrel[facing=up]")
    c.set(-9, 4, 0, "lantern[hanging=true]")
    # --- the forecastle: x 7..11, its deck at y 4
    for x in range(7, 12):
        hw = _hw(x)
        for z in range(-hw, hw + 1):
            c.set(x, 4, z, hull if abs(z) == hw else deck)
            if abs(z) < hw:
                c.set(x, 3, z, hull if x == 7 else "air")
        for z in (-hw, hw):
            c.set(x, 5, z, "spruce_fence")
    for z in range(-2, 3):
        c.set(7, 3, z, hull)
    for z in (-2, 2):
        c.set(7, 5, z, "spruce_fence")
    c.set(11, 5, 0, "spruce_fence")
    c.set(11, 6, 0, "lantern[hanging=false]")
    # steps up to it on the starboard side
    c.set(5, 3, -1, stair("spruce", "east"))
    c.set(6, 3, -1, hull)
    c.set(6, 4, -1, stair("spruce", "east"))
    c.set(7, 5, -1, "air")
    # --- the gangway port in the bulwark (the pier lies on the +z side)
    for x in (-3, -2):
        c.set(x, 3, 3, "air")
    # --- mast, yard, top, sail
    for y in range(-1, 24):
        c.set(0, y, 0, "stripped_spruce_log[axis=y]")
    for z in range(-5, 6):
        c.set(1, 17, z, "stripped_spruce_log[axis=z]")       # the yard, slung before the mast
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx or dz) and (dx, dz) != (-1, 0):           # the top; the ladder comes up through the gap
                c.set(dx, 20, dz, slab("spruce", top=True))
                c.set(dx, 21, dz, "spruce_fence")
    c.set(0, 24, 0, "red_banner[rotation=4]")
    for y in range(3, 20):
        c.set(-1, y, 0, "ladder[facing=west]")
    bell = {(0, 15), (-1, 14), (0, 14), (1, 14), (-1, 13), (0, 13), (1, 13), (-1, 12), (0, 12), (1, 12),
            (-2, 11), (-1, 11), (0, 11), (1, 11), (2, 11), (-2, 10), (-1, 10), (0, 10), (1, 10), (2, 10), (0, 8)}
    for y in range(7, 17):
        for z in range(-4, 5):
            belly = 1.7 * (1.0 - (z / 4.8) ** 2) * math.sin(math.pi * (16.6 - y) / 10.4)
            x = 1 + int(round(belly))
            c.set(x, y, z, "red_wool" if (z, y) in bell else "white_wool")
    # --- on deck
    c.set(3, 3, 2, "barrel[facing=up]")
    c.set(4, 3, 2, "barrel[facing=up]")
    c.set(3, 4, 2, "barrel[facing=up]")
    c.set(-4, 3, -2, "barrel[facing=up]")
    c.set(2, 3, -2, "hay_block[axis=x]")
    c.set(-3, 3, 0, "spruce_trapdoor[facing=north,open=false,half=bottom]")
    c.set(1, 3, 0, "lantern[hanging=false]")
    c.mark("ferry_deck", -2, 3, 1)
    c.mark("ferry_helm", -9, 6, 0)
    c.mark("ferry_bow", 9, 5, 0)
