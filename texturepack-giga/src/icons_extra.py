"""P5 polish: the remaining weapons and tools that still carried old live art."""
import math

from forge import Icon, Mat, ramp, mix
import palette as P
from kit import (uv_set, uv_bump, uv_get, uv_fill, uv_shade, bands, blade, shaped_blade, haft, box, disc_uv,
                 ring_uv, wrap, pommel, gem_plus, gem_diamond)
import icons_tools as TL
from icons_ladder import early_bow

REG = {}


def reg(name):
    def deco(fn):
        REG[name] = fn
        return fn
    return deco


# --------------------------------------------------------------------------- compacted / crafted gear
DIAMOND_BRICK = ramp('#5fd6e0', n=7, at=3, lo=0.22, hi=0.99, shadow_h=250, light_h=180)
IRON_PLATE = ramp('#b6bcc8', n=7, at=4, lo=0.24, hi=0.99, shadow_h=285, light_h=220)
COBBLE = ramp('#8d8a92', n=7, at=3, lo=0.18, hi=0.92, shadow_h=290, light_h=80, ls=0.2)
STONE_LAYER = ramp('#9a958e', n=7, at=3, lo=0.20, hi=0.92, shadow_h=320, light_h=80, ls=0.2)
EMERALD = ramp('#3fcf7a', n=7, at=3, lo=0.20, hi=0.98, shadow_h=200, light_h=120)
COPPER_B = ramp('#d8804e', n=7, at=3, lo=0.22, hi=0.96, shadow_h=15, ss=0.5, light_h=70, ls=0.4)
MIDAS = ramp('#f2c440', n=7, at=4, lo=0.26, hi=0.995, shadow_h=35, ss=0.6, light_h=100, ls=0.5)

for key, pal in (('diamond_brick', DIAMOND_BRICK), ('iron_plate', IRON_PLATE), ('cobble', COBBLE),
                 ('stone_layer', STONE_LAYER), ('emerald', EMERALD), ('copper', COPPER_B), ('midas', MIDAS),
                 ('bounty', P.GOLD)):
    TL.HEAD[key] = pal
    TL.GRIP[key] = P.LEATHER
    TL.SHAFT[key] = P.WOOD
    TL.TRIM[key] = P.IRON_DARK


def brick(ic, key='H', period=3):
    """Compacted look: a brick seam pattern over the head."""
    for y in range(32):
        for x in range(32):
            if ic.get(x, y) == key and ((x + y) % period == 0):
                ic.bump(x, y, -1)


@reg('compacted_diamond_pickaxe')
def compacted_diamond_pickaxe():
    ic = TL.pickaxe('diamond_brick')
    brick(ic)
    return ic


@reg('compacted_iron_pickaxe')
def compacted_iron_pickaxe():
    ic = TL.pickaxe('iron_plate')
    brick(ic, period=4)
    return ic


@reg('compressed_stone_pickaxe')
def compressed_stone_pickaxe():
    ic = TL.pickaxe('stone_layer')
    for y in range(32):
        for x in range(32):
            if ic.get(x, y) == 'H' and (x - y) % 3 == 0:
                ic.bump(x, y, -1)
    return ic


def sword_of(pal, grip=P.LEATHER, half=2, dagger=False, guard=P.IRON_DARK, pommel_key=None, edge=None, gem=None):
    mats = {
        'B': Mat(pal, base=3, model='flat', z=0),
        'E': Mat(edge or pal, base=4, model='flat', z=0),
        'G': Mat(guard, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'W': Mat(grip, base=2, model='flat', z=1),
        'P': Mat(guard, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'A': Mat(gem or P.CRIMSON, base=4, model='gem', z=3),
    }
    ic = Icon(mats)
    tip = 18 if dagger else 25
    blade(ic, 'B', -5, tip, half, half, k=2 if not dagger else 1.6)
    tbl = {-3: 3, -2: 2, -1: 1, 0: 0, 1: 0, 2: -1, 3: -2} if half == 3 else {-2: 3, -1: 1, 0: 0, 1: -1, 2: -2}
    if half == 1:
        tbl = {-1: 2, 0: 0, 1: -2}
    bands(ic, 'B', tbl)
    if edge:
        uv_fill(ic, 'E', lambda u, v: u == 31 - half and v >= -3, only='B')
    reach = 4 if dagger else 5
    for u in range(31 - reach, 32 + reach):
        for v in (-7, -8):
            uv_set(ic, u, v, 'G')
    haft(ic, 'W', -15, -9)
    wrap(ic, 'W', period=3, dark=(0,))
    pommel(ic, 'P', -17, r=1.6)
    if gem:
        ic.set(12, 19, 'A')
        ic.set(5, 25, 'A')
    return ic


@reg('compacted_diamond_sword')
def compacted_diamond_sword():
    ic = sword_of(DIAMOND_BRICK, half=3, gem=P.AETHER)
    brick(ic, 'B', period=4)
    ic.fix[(29, 2)] = P.WHITE
    return ic


@reg('copper_sword')
def copper_sword():
    ic = sword_of(COPPER_B, half=2, guard=P.BRONZE)
    for x, y in ((22, 9), (19, 13), (25, 6)):
        if ic.get(x, y) == 'B':
            ic.set(x, y, 'B', -2)          # a little verdigris-dark pitting
    return ic


@reg('compressed_gold_sword')
def compressed_gold_sword():
    ic = sword_of(MIDAS, half=2, guard=P.BRASS, gem=P.CRIMSON)
    brick(ic, 'B', period=4)
    return ic


@reg('compacted_midas_dagger')
def compacted_midas_dagger():
    ic = sword_of(MIDAS, half=1, dagger=True, guard=P.GOLD, gem=P.CRIMSON)
    ic.spark(24, 7, P.WHITE, P.SUN[4], size=1)
    return ic


@reg('compacted_cobble_hammer')
def compacted_cobble_hammer():
    mats = {
        'H': Mat(COBBLE, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.0, z=1),
        'T': Mat(P.WOOD, base=3, model='flat', z=0),
        'W': Mat(P.LEATHER, base=2, model='flat', z=0),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 11)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -12)
    wrap(ic, 'W', period=3, dark=(0,))
    box(ic, 'H', 24, 38, 11, 21, chamfer=2)
    for y in range(32):
        for x in range(32):
            if ic.get(x, y) == 'H' and (x * 7 + y * 3) % 5 == 0:
                ic.bump(x, y, -1)
            elif ic.get(x, y) == 'H' and (x * 3 + y * 5) % 7 == 0:
                ic.bump(x, y, 1)
    box(ic, 'M', 29, 33, 9, 11)
    return ic


@reg('compacted_emerald_scythe')
def compacted_emerald_scythe():
    return scythe(EMERALD, P.WOOD, glow=None, rings=False)


# --------------------------------------------------------------------------- scythes
def scythe(blade_r, shaft_r, glow=None, rings=False):
    mats = {
        'S': Mat(blade_r, base=3, model='flat', z=2),
        'E': Mat(blade_r, base=5, model='flat', z=2),
        'T': Mat(shaft_r, base=3, model='flat', z=0),
        'W': Mat(P.LEATHER_DARK, base=2, model='flat', z=1),
        'C': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=3),
        'R': Mat(glow or blade_r, base=4, model='flat', z=1),
    }
    ic = Icon(mats)
    haft(ic, 'T', -24, 20)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -24, -14)
    wrap(ic, 'W', period=3, dark=(0,))
    haft(ic, 'W', -2, 1)
    cu, cv, r0 = 31.0, 4.0, 14.5

    def bl(u, v):
        d = math.hypot(u - cu, v - cv)
        a = math.degrees(math.atan2(v - cv, u - cu))
        if not (88 <= a <= 172):
            return False
        t = (a - 88) / 84.0
        th = 2.6 * (1 - t) + 0.5
        return r0 - th <= d <= r0 + 0.6
    uv_fill(ic, 'S', bl)
    uv_fill(ic, 'E', lambda u, v: math.hypot(u - cu, v - cv) >= r0 - 0.4, only='S')
    box(ic, 'C', 29, 33, 18, 21)
    if rings:
        for rr in (4.0, 6.0):
            ring_uv(ic, 'R', 24, 10, rr - 0.5, rr + 0.3, arc=(200, 330))
        ic.noline.add('R')
    return ic


@reg('resonance_scythe')
def resonance_scythe():
    ic = scythe(ramp('#b9a8e6', n=7, at=4, lo=0.24, hi=0.99, shadow_h=285, light_h=250), P.WOOD_DARK,
                glow=P.LILAC, rings=True)
    ic.spark(9, 7, P.WHITE, P.LILAC[5], size=1)
    return ic


# --------------------------------------------------------------------------- legendary bows
@reg('hollow_longbow')
def hollow_longbow():
    ic = early_bow('simple_longbow')
    ic.mats['S'] = Mat(P.BONE, base=4, model='flat', z=1)
    ic.mats['q'] = Mat(P.AETHER_GLASS, base=4, model='flat', z=0)
    ic.mats['W'] = Mat(P.VOID, base=3, model='flat', z=2)
    ic.mats['h'] = Mat(ramp('#7fe0f0', n=5, at=2, lo=0.40, hi=0.99, shadow_h=250), base=3, model='flat', z=3)
    # the hollow: an empty ring glowing in the curve
    for x, y in ((11, 11), (12, 10), (13, 10), (14, 11), (14, 12), (13, 13), (12, 13), (11, 12)):
        ic.set(x, y, 'h')
    ic.clear(12, 11)
    ic.clear(13, 12)
    ic.clear(12, 12)
    ic.clear(13, 11)
    for p in ((9, 6), (7, 9), (16, 4)):
        ic.fx.append(p + (P.AETHER_GLASS[4],))
    return ic


@reg('skuldugery_shortbow')
def skuldugery_shortbow():
    ic = early_bow('simple_shortbow')
    ic.mats['S'] = Mat(P.WOOD_DARK, base=2, model='flat', z=1)
    ic.mats['q'] = Mat(P.MAGMA, base=3, model='flat', z=0)
    ic.mats['k'] = Mat(P.BONE, base=4, hi=(0, 1, 1), lo=(0, 1, 2), z=3)
    ic.mats['e'] = Mat(ramp('#2a1010', n=4, at=1), base=1, model='flat', z=4)
    # skull on the grip
    for x, y in ((12, 13), (13, 13), (14, 13), (11, 14), (12, 14), (13, 14), (14, 14), (15, 14), (11, 15), (12, 15),
                 (13, 15), (14, 15), (15, 15), (12, 16), (14, 16), (13, 17), (12, 17), (14, 17)):
        ic.set(x, y, 'k')
    ic.set(12, 15, 'e')
    ic.set(14, 15, 'e')
    ic.set(13, 16, 'e')
    for p, c in (((24, 3), P.MAGMA[4]), ((25, 2), P.MAGMA[3]), ((4, 24), P.MAGMA[4]), ((3, 25), P.MAGMA[2]),
                 ((26, 4), P.EMBER[3])):
        ic.fx.append(p + (c,))
    return ic


# --------------------------------------------------------------------------- Bridged Axe (Legendary, Bridge Troll)
@reg('bridged_axe')
def bridged_axe():
    troll = ramp('#5f6f58', n=6, at=3, lo=0.18, hi=0.85, shadow_h=200)
    mats = {
        'H': Mat(P.GOLD, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=0.8, z=2),
        'E': Mat(P.SUNSTEEL, base=5, model='flat', z=2),
        'T': Mat(troll, base=3, model='flat', z=0),
        'W': Mat(P.LEATHER, base=2, model='flat', z=1),
        'C': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=3),
        'A': Mat(P.JADE, base=4, model='gem', z=4),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 22)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -12)
    wrap(ic, 'W', period=3, dark=(0,))

    def bit(u, v, side):
        uu = 31 + (u - 31) * side          # mirror the right bit onto the left
        if not (18 <= uu <= 30):
            return False
        h = 2.4 + (30 - uu) * 0.6
        if abs(v - 15) > h:
            return False
        return uu >= 18 + 0.05 * (v - 15) ** 2
    uv_fill(ic, 'H', lambda u, v: bit(u, v, 1) or bit(u, v, -1))
    uv_fill(ic, 'E', lambda u, v: min(u, 62 - u) <= 18 + 0.05 * (v - 15) ** 2 + 1.0, only='H')
    box(ic, 'C', 29, 33, 11, 19)
    for u, v in ((31, 15), (30, 16), (32, 16), (31, 17), (31, 14)):
        uv_set(ic, u, v, 'A')
    ic.spark(26, 3, P.WHITE, P.SUN[4], size=1)
    return ic


# --------------------------------------------------------------------------- Voided 455 (Mythic)
@reg('voided_455')
def voided_455():
    ic = TL.pickaxe(5)
    ic.mats['H'] = Mat(P.VOID, base=3, hi=(0, 2, 3), lo=(0, 1, 1), form=0.8, z=2)
    ic.mats['E'] = Mat(P.AMETHYST, base=4, model='flat', z=2)
    ic.mats['T'] = Mat(ramp('#2c2238', n=6, at=2, lo=0.12, hi=0.70, shadow_h=290), base=3, model='flat', z=0)
    ic.mats['W'] = Mat(P.LILAC, base=2, model='flat', z=1)
    ic.mats['A'] = Mat(P.RIFT, base=4, model='gem', z=4)
    ic.mats['c'] = Mat(P.GLITCH_C, base=3, model='flat', z=5)
    ic.mats['m'] = Mat(P.GLITCH_M, base=3, model='flat', z=5)
    ic.fx = []
    # glitch: displaced slivers of the head
    for x, y, k in ((20, 5, 'c'), (21, 5, 'c'), (23, 9, 'm'), (24, 9, 'm'), (25, 9, 'm'), (16, 7, 'c')):
        ic.set(x, y, k)
    ic.fx.append((29, 3, P.GLITCH_M[3]))
    ic.fx.append((30, 3, P.GLITCH_C[3]))
    ic.spark(10, 4, P.WHITE, P.RIFT[3], size=1)
    return ic


# --------------------------------------------------------------------------- Obsidian Maul (Rare)
@reg('obsidian_maul')
def obsidian_maul():
    mats = {
        'H': Mat(ramp('#3a2a52', n=7, at=3, lo=0.10, hi=0.85, shadow_h=290, light_h=300), base=3, hi=(0, 2, 3),
                 lo=(0, 1, 2), form=1.2, z=1),
        'K': Mat(P.AMETHYST, base=4, model='gem', z=2),
        'T': Mat(P.WOOD_DARK, base=2, model='flat', z=0),
        'W': Mat(P.LEATHER_DARK, base=2, model='flat', z=0),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 11)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -12)
    wrap(ic, 'W', period=3, dark=(0,))
    box(ic, 'H', 23, 39, 11, 21, chamfer=3)
    # crystalline facets: a cross of light lines on the head
    uv_shade(ic, 'H', lambda u, v: 1 if (u - v) % 6 == 0 else (-1 if (u + v) % 6 == 0 else 0))
    for u, v in ((31, 23), (30, 24), (32, 24), (31, 25), (31, 26), (22, 16), (21, 16), (40, 16), (41, 16)):
        uv_set(ic, u, v, 'K')
    box(ic, 'M', 29, 33, 9, 11)
    return ic


# --------------------------------------------------------------------------- staves and wands
def staff_base(shaft_r, top=12, w=1):
    mats = {
        'T': Mat(shaft_r, base=3, model='flat', z=0),
        'W': Mat(P.LEATHER, base=2, model='flat', z=1),
        'C': Mat(P.GOLD, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
    }
    ic = Icon(mats)
    haft(ic, 'T', -24, top, w=w)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    return ic


@reg('mender_staff')
def mender_staff():
    ic = staff_base(P.WOOD)
    ic.mats['G'] = Mat(ramp('#9ef0c8', n=7, at=3, lo=0.30, hi=0.995, shadow_h=200, light_h=120), base=4,
                       model='gem', z=3)
    ic.mats['L'] = Mat(P.JADE, base=3, model='flat', z=2)
    haft(ic, 'W', -8, -3)
    wrap(ic, 'W', period=2, dark=(0,))
    # golden leaf prongs cradling a healing crystal
    for u, v in ((27, 12), (26, 13), (26, 14), (35, 12), (36, 13), (36, 14), (28, 11), (34, 11)):
        uv_set(ic, u, v, 'C')
    for dv in range(-4, 5):
        w = 2.2 - abs(dv) * 0.45
        for du in range(-3, 4):
            if abs(du) <= w:
                uv_set(ic, 31 + du, 18 + dv, 'G')
    for u, v in ((25, 8), (24, 7), (37, 8), (38, 7)):
        uv_set(ic, u, v, 'L')
    ic.spark(26, 3, P.WHITE, P.JADE[5], size=1)
    return ic


@reg('ember_rod')
def ember_rod():
    ic = staff_base(ramp('#3a2a2a', n=6, at=2, lo=0.12, hi=0.70, shadow_h=320), top=13)
    ic.mats['O'] = Mat(P.MAGMA, base=3, model='sphere', z=3)
    ic.mats['F'] = Mat(P.EMBER, base=4, model='flat', z=2)
    for u, v in ((28, 12), (34, 12), (27, 13), (35, 13)):
        uv_set(ic, u, v, 'C')
    disc_uv(ic, 'O', 31, 17, 3.6)
    for u, v in ((31, 22), (30, 23), (32, 23), (31, 24), (29, 21), (33, 21), (31, 26)):
        uv_set(ic, u, v, 'F', 1)
    # cracked, still-glowing seams in the charred rod
    for u, v in ((31, 2), (30, -5), (32, -11)):
        uv_set(ic, u, v, 'F', -1)
    for p, c in (((27, 2), P.MAGMA[4]), ((22, 3), P.EMBER[3]), ((28, 6), P.EMBER[4])):
        ic.fx.append(p + (c,))
    return ic


@reg('frost_shard')
def frost_shard():
    mats = {
        'I': Mat(P.ICE, base=4, model='gem', z=1),
        'J': Mat(ramp('#cdefff', n=6, at=3, lo=0.40, hi=0.995, shadow_h=260), base=4, model='gem', z=2),
        'W': Mat(P.LEATHER, base=2, model='flat', z=0),
    }
    ic = Icon(mats)
    # a long ice shard with a wrapped grip and two smaller crystals at its base
    def shard(u, v):
        if not (-14 <= v <= 26):
            return False
        t = (v + 14) / 40.0
        w = 3.2 * math.sin(math.pi * min(1.0, t * 1.1)) + 0.4
        return abs(u - 31) <= w
    uv_fill(ic, 'I', shard)
    uv_shade(ic, 'I', lambda u, v: 1 if u < 31 else (-1 if u > 32 else 0))
    haft(ic, 'W', -22, -14)
    wrap(ic, 'W', period=2, dark=(0,))
    for u, v, r in ((26, -10, 2.2), (36, -8, 1.8)):
        disc_uv(ic, 'J', u, v, r)
    ic.spark(25, 4, P.WHITE, P.ICE[5], size=1)
    ic.fx.append((9, 14, P.ICE[5]))
    return ic


@reg('copper_rod')
def copper_rod():
    ic = staff_base(P.COPPER, top=20)
    ic.mats['K'] = Mat(P.BRONZE, base=4, hi=(0, 1, 2), lo=(0, 1, 1), z=2)
    ic.mats['B'] = Mat(P.BOLT, base=4, model='flat', z=3)
    # copper coil around the upper rod, a spark crackling at the tip
    for v in range(8, 20, 3):
        uv_set(ic, 29, v + ((29 + v) % 2), 'K')
        uv_set(ic, 33, v + 1 + ((33 + v + 1) % 2), 'K')
    for x, y in ((27, 2), (28, 3), (27, 4), (28, 5), (26, 3)):
        ic.set(x, y, 'B')
    ic.spark(29, 1, P.WHITE, P.BOLT[4], size=1)
    return ic


# --------------------------------------------------------------------------- blades with a story
@reg('splinter_glaive')
def splinter_glaive():
    mats = {
        'B': Mat(P.STEEL, base=3, model='flat', z=1),
        'T': Mat(ramp('#8a6a44', n=6, at=3, lo=0.20, hi=0.85, shadow_h=20), base=3, model='flat', z=0),
        'W': Mat(P.LEATHER, base=2, model='flat', z=0),
        'C': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
    }
    ic = Icon(mats)
    haft(ic, 'T', -24, 10)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    # splinters: little timber shards sticking off the haft
    for u, v in ((28, -2), (34, 4), (28, 6), (34, -6)):
        uv_set(ic, u, v, 'T', 1)
    haft(ic, 'W', -24, -16)
    wrap(ic, 'W', period=3, dark=(0,))
    # glaive: a single-edged blade, cutting edge on the lit side, hooked spine
    def gl(u, v):
        if not (10 <= v <= 27):
            return False
        edge = 31 - 3.2 * math.sin(math.pi * (v - 10) / 19.0)
        return edge <= u <= 32 - (1 if v > 22 else 0)
    uv_fill(ic, 'B', gl)
    uv_shade(ic, 'B', lambda u, v: 2 if u <= 29 else (0 if u <= 31 else -1))
    box(ic, 'C', 29, 33, 9, 11)
    return ic


@reg('bone_knife')
def bone_knife():
    ic = sword_of(P.BONE, half=1, dagger=True, guard=ramp('#8a7a6a', n=6, at=3, lo=0.2, hi=0.85))
    for x, y in ((21, 10), (23, 8)):
        if ic.get(x, y) == 'B':
            ic.bump(x, y, -2)
    return ic


@reg('venom_dagger')
def venom_dagger():
    ic = sword_of(ramp('#5a6a58', n=7, at=3, lo=0.15, hi=0.88, shadow_h=290, light_h=110), half=1, dagger=True,
                  guard=P.IRON_DARK, edge=P.POISON, gem=P.POISON)
    for p, c in (((24, 8), P.POISON[4]), ((24, 9), P.POISON[3]), ((24, 11), P.POISON[2])):
        ic.fx.append(p + (c,))
    return ic


@reg('canopy_cleaver')
def canopy_cleaver():
    from icons_boss import _cleaver
    leafsteel = ramp('#8a9a8a', n=7, at=3, lo=0.18, hi=0.93, shadow_h=290, light_h=110)
    ic = _cleaver(leafsteel, ramp('#6cc04a', n=6, at=3, lo=0.22, hi=0.95, shadow_h=170, light_h=100),
                  P.WOOD, P.LEATHER)
    ic.mats['L'] = Mat(P.JADE, base=3, model='flat', z=3)
    for u, v in ((32, -3), (33, -2), (30, -6), (29, -5), (32, -9), (33, -10)):
        uv_set(ic, u, v, 'L')
    return ic


# --------------------------------------------------------------------------- blueprint tools (Rare)
@reg('vein_siphon')
def vein_siphon():
    ic = TL.pickaxe(1)
    ic.mats['V'] = Mat(P.AMETHYST, base=3, model='gem', z=4)
    ic.mats['G'] = Mat(ramp('#b4e8f4', n=5, at=2, lo=0.45, hi=0.99), base=3, model='flat', z=3)
    # a glass siphon tube along the head, a crystal drop in it
    for u, v in ((27, 19), (28, 19), (29, 20), (33, 20), (34, 19), (35, 19)):
        uv_set(ic, u, v, 'G')
    for u, v in ((31, 21), (30, 22), (32, 22), (31, 23)):
        uv_set(ic, u, v, 'V')
    return ic


@reg('bounty_hoe')
def bounty_hoe():
    ic = TL.hoe('bounty', wheat=True)
    ic.spark(25, 5, P.WHITE, P.SUN[4], size=1)
    return ic


@reg('tide_latch')
def tide_latch():
    ic = TL.rod(2)
    ic.mats['T'] = Mat(ramp('#2f7aa8', n=6, at=3, lo=0.18, hi=0.95, shadow_h=270, light_h=190), base=3,
                       model='flat', z=1)
    ic.mats['b'] = Mat(P.AETHER, base=3, model='sphere', z=3)
    ic.fx = []
    # latch hook instead of a bobber
    for x, y in ((28, 15), (29, 15), (30, 15), (28, 16), (29, 16), (30, 16), (29, 14), (29, 17)):
        ic.clear(x, y)
    for x, y in ((29, 15), (29, 16), (29, 17), (30, 18), (31, 18), (32, 17), (32, 16), (31, 15)):
        ic.set(x, y, 'C')
    ic.spark(26, 5, P.WHITE, P.AETHER[4], size=1)
    return ic


@reg('wild_sight')
def wild_sight():
    mats = {
        'B': Mat(P.BRASS, base=3, hi=(0, 1, 2), lo=(0, 1, 2), z=1),
        'L': Mat(P.LEATHER, base=2, model='flat', z=2),
        'G': Mat(ramp('#6cd08a', n=6, at=3, lo=0.25, hi=0.99, shadow_h=180, light_h=120), base=4,
                 model='sphere', z=3),
    }
    ic = Icon(mats)
    # a scout's spyglass: three telescoping brass sections, leather grips, a green lens
    haft(ic, 'B', -18, -6, w=2)
    haft(ic, 'B', -6, 6, w=2)
    haft(ic, 'B', 6, 16, w=3)
    haft(ic, 'L', -14, -10, w=2)
    haft(ic, 'L', 0, 3, w=2)
    disc_uv(ic, 'G', 31, 18, 3.2)
    for u in (28, 34):
        uv_set(ic, u, 6 + ((u + 6) % 2), 'B')
    ic.spark(26, 4, P.WHITE, P.JADE[4], size=1)
    return ic


# --------------------------------------------------------------------------- catcher gaffs (pet catching)
def gaff(tier):
    head = {1: P.IRON, 2: P.ICE, 3: ramp('#4a3a48', n=7, at=3, lo=0.13, hi=0.86, shadow_h=320)}[tier]
    mats = {
        'H': Mat(head, base=3, hi=(0, 1, 2), lo=(0, 1, 2), z=2),
        'T': Mat(P.WOOD if tier < 3 else P.WOOD_DARK, base=3, model='flat', z=0),
        'W': Mat(ramp('#2f9a8a', n=6, at=3, lo=0.16, hi=0.92, shadow_h=240), base=2, model='flat', z=1),
        's': Mat(P.CRIMSON, base=3, model='sphere', z=3),
        'w': Mat(P.PORCELAIN, base=4, model='sphere', z=3),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 16)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -13)
    wrap(ic, 'W', period=3, dark=(0,))
    # hook curling to the lit side with a barb
    ring_uv(ic, 'H', 26, 18, 3.4, 5.0, arc=(0, 230))
    for u, v in ((22, 15), (23, 14), (21, 14)):
        uv_set(ic, u, v, 'H')
    # a little catch-sphere charm tied under the hook
    for x, y, k in ((19, 13, 's'), (20, 13, 's'), (19, 14, 'w'), (20, 14, 'w')):
        ic.set(x, y, k)
    if tier == 3:
        ic.spark(26, 3, P.WHITE, P.EMBER[4], size=1)
    return ic


REG['catcher_gaff'] = lambda: gaff(1)
REG['catcher_gaff_2'] = lambda: gaff(2)
REG['catcher_gaff_3'] = lambda: gaff(3)
