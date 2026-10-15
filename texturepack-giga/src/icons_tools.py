"""P5 tools: pickaxe / axe / hoe / rod ladders (T1..T5 in the house tier language), the starter
tools, and the Aetherion Manager tome (kept as a blue rune book, which players already know)."""
import math

from forge import Icon, Mat, ramp, mix
import palette as P
from kit import (uv_set, uv_bump, uv_get, uv_fill, uv_shade, bands, blade, haft, box, disc_uv, ring_uv, wrap,
                 pommel, gem_plus, gem_diamond)
from icons_ladder import T_BLADE, T_GEM, T_GLOW

REG = {}

PLAIN = ramp('#a3a7b2', n=7, at=3, lo=0.20, hi=0.97, shadow_h=290, light_h=220, ls=0.2)
STARTER = ramp('#4fc2b4', n=7, at=3, lo=0.20, hi=0.97, shadow_h=240, light_h=170)
HEAD = dict(T_BLADE)
HEAD[0] = PLAIN
HEAD['starter'] = STARTER
GRIP = {0: P.LEATHER, 'starter': P.LEATHER, 1: P.LEATHER, 2: ramp('#2f5a8a', n=6, at=2, lo=0.15, hi=0.85, shadow_h=270),
        3: ramp('#4a2a6a', n=6, at=2, lo=0.12, hi=0.80, shadow_h=290), 4: P.LEATHER_DARK, 5: P.CLOTH_RED}
SHAFT = {0: P.WOOD, 'starter': P.WOOD, 1: P.WOOD, 2: P.WOOD, 3: P.WOOD_DARK, 4: P.WOOD_DARK, 5: P.WOOD_DARK}
TRIM = {0: P.IRON_DARK, 'starter': P.IRON_DARK, 1: P.IRON_DARK, 2: P.SILVER, 3: P.AMETHYST, 4: P.IRON_DARK, 5: P.GOLD}


def tool_mats(tier, extra=None):
    m = {
        'H': Mat(HEAD[tier], base=4 if tier == 5 else 3, hi=(0, 1, 2), lo=(0, 1, 2), form=0.8, z=2),
        'E': Mat((T_GLOW.get(tier) if isinstance(tier, int) and tier >= 2 else None) or HEAD[tier], base=4,
                 model='flat', z=2),
        'T': Mat(SHAFT[tier], base=3, model='flat', z=0),
        'W': Mat(GRIP[tier], base=2, model='flat', z=1),
        'C': Mat(TRIM[tier], base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=3),
        'A': Mat((T_GEM.get(tier) if isinstance(tier, int) else None) or P.AETHER, base=4, model='gem', z=4),
    }
    if extra:
        m.update(extra)
    return m


def shaft(ic, tier, top=13):
    haft(ic, 'T', -23, top)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -12)
    wrap(ic, 'W', period=3, dark=(0,))
    if isinstance(tier, int) and tier >= 4:
        box(ic, 'C', 30, 32, -24, -23)


def sparkle(ic, tier, x, y):
    if isinstance(tier, int) and tier >= 3:
        g = T_GLOW[tier]
        ic.spark(x, y, P.WHITE, g[4] if tier >= 4 else g[3], size=2 if tier == 5 else 1)


# --------------------------------------------------------------------------- pickaxe
def pickaxe(tier):
    ic = Icon(tool_mats(tier))
    shaft(ic, tier)

    def vc(u):
        return 16.5 - 0.05 * (u - 31) ** 2

    def th(u):
        return 2.3 - 1.7 * (abs(u - 31) / 12.0) ** 1.4
    uv_fill(ic, 'H', lambda u, v: 19 <= u <= 43 and abs(v - vc(u)) <= th(u))
    if isinstance(tier, int) and tier >= 2:
        uv_fill(ic, 'E', lambda u, v: (u <= 21 or u >= 41), only='H')
    box(ic, 'C', 29, 33, 13, 15)
    if isinstance(tier, int) and tier >= 3:
        for u, v in ((31, 17), (30, 18), (32, 18), (31, 19)):
            uv_set(ic, u, v, 'A')
    sparkle(ic, tier, 27, 4)
    return ic


# --------------------------------------------------------------------------- axe
def axe(tier, leafy=False):
    extra = {'L': Mat(ramp('#6cc04a', n=6, at=3, lo=0.22, hi=0.95, shadow_h=170, light_h=100), base=3,
                      model='flat', z=3)} if leafy else None
    ic = Icon(tool_mats(tier, extra))
    shaft(ic, tier, top=19)

    def blade_p(u, v):
        if not (18 <= u <= 30):
            return False
        h = 2.6 + (30 - u) * 0.62
        if abs(v - 15) > h:
            return False
        return u >= 18 + 0.05 * (v - 15) ** 2
    uv_fill(ic, 'H', blade_p)
    uv_fill(ic, 'E', lambda u, v: u <= 18 + 0.05 * (v - 15) ** 2 + 1.2, only='H')
    box(ic, 'H', 32, 35, 13, 17)
    box(ic, 'C', 29, 33, 12, 18)
    if leafy:
        for u, v in ((33, 5), (34, 4), (34, 6), (35, 5), (33, 3), (32, 4)):
            uv_set(ic, u, v, 'L', 1 if (u + v) % 4 == 0 else 0)
    if isinstance(tier, int) and tier >= 3:
        uv_set(ic, 31, 15, 'A')
        uv_set(ic, 31, 13, 'A')
    sparkle(ic, tier, 26, 4)
    return ic


# --------------------------------------------------------------------------- hoe
def hoe(tier, wheat=False):
    extra = {'w': Mat(ramp('#e8b83c', n=6, at=3, lo=0.30, hi=0.99, shadow_h=40, light_h=100), base=3,
                      model='flat', z=3)} if wheat else None
    ic = Icon(tool_mats(tier, extra))
    shaft(ic, tier, top=19)
    # a long thin head on the lit side, its blade dropping back toward the user (an L, not an axe)
    uv_fill(ic, 'H', lambda u, v: 19 <= u <= 30 and 17 <= v <= 18)
    uv_fill(ic, 'H', lambda u, v: 18 <= u <= 20 and 9 <= v <= 18)
    uv_fill(ic, 'E', lambda u, v: u <= 18, only='H')
    box(ic, 'C', 29, 33, 16, 19)
    if wheat:
        for u, v in ((34, 8), (35, 9), (35, 7), (36, 8), (36, 6), (37, 7), (33, 9), (34, 10)):
            uv_set(ic, u, v, 'w', 1 if v % 2 else 0)
    if isinstance(tier, int) and tier >= 3:
        uv_set(ic, 31, 17, 'A')
    sparkle(ic, tier, 26, 4)
    return ic


# --------------------------------------------------------------------------- fishing rod
BOBBER = {1: P.CRIMSON, 2: P.ICE, 3: P.AMETHYST, 4: P.EMBER, 5: P.SUN}
ROD = {1: P.WOOD, 2: ramp('#8fc6e6', n=6, at=3, lo=0.24, hi=0.98, shadow_h=265, light_h=195),
       3: ramp('#8a6ac8', n=6, at=3, lo=0.20, hi=0.95, shadow_h=285, light_h=310),
       4: ramp('#5a3a34', n=6, at=3, lo=0.15, hi=0.80, shadow_h=320, light_h=40),
       5: ramp('#e8c050', n=6, at=3, lo=0.28, hi=0.99, shadow_h=35, ss=0.6, light_h=100)}


def rod(tier):
    mats = {
        'T': Mat(ROD[tier], base=3, model='flat', z=1),
        'W': Mat(ramp('#b8925a', n=6, at=3, lo=0.25, hi=0.92, shadow_h=30), base=3, model='flat', z=1),
        'C': Mat(TRIM[tier] if tier != 1 else P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'q': Mat(P.SILK, base=3, model='flat', z=0),
        'b': Mat(BOBBER[tier], base=3, model='sphere', z=3),
        'k': Mat(P.PORCELAIN, base=4, model='flat', z=3),
    }
    ic = Icon(mats)
    ic.noline.add('q')
    # tapering rod: thick cork handle, then a single line to the tip
    haft(ic, 'W', -23, -11)
    wrap(ic, 'W', period=4, dark=(0, 1))
    haft(ic, 'T', -10, 6, w=1)
    uv_fill(ic, 'T', lambda u, v: u == 31 and 6 <= v <= 26)
    uv_fill(ic, 'T', lambda u, v: u == 30 and 6 <= v <= 18)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    # reel on the shadow side, guides along the rod
    disc_uv(ic, 'C', 35, -9, 2.6)
    uv_set(ic, 37, -12, 'C')
    for v in (2, 12, 20):
        uv_set(ic, 32, v + ((32 + v) % 2), 'C')
    # line hanging from the tip, bobber, hook
    for y in range(3, 15):
        ic.set(29, y, 'q')
    for x, y in ((28, 15), (29, 15), (30, 15), (28, 16), (29, 16), (30, 16), (29, 14), (29, 17)):
        ic.set(x, y, 'b')
    for x, y in ((28, 16), (29, 16), (30, 16)):
        ic.set(x, y, 'k')
    for x, y in ((29, 19), (29, 20), (30, 21), (31, 20)):
        ic.set(x, y, 'C')
    ic.set(29, 18, 'q')
    if tier >= 3:
        g = T_GLOW[tier]
        ic.spark(25, 5, P.WHITE, g[4] if tier >= 4 else g[3], size=1)
    return ic


for _t in range(1, 6):
    _sfx = '' if _t == 1 else f'_{_t}'
    REG['mining_pickaxe' + _sfx] = (lambda t=_t: pickaxe(t))
    REG['foraging_axe' + _sfx] = (lambda t=_t: axe(t, leafy=True))
    REG['farming_hoe' + _sfx] = (lambda t=_t: hoe(t, wheat=True))
    REG[f'fishing_rod_t{_t}'] = (lambda t=_t: rod(t))

REG['beginner_pickaxe'] = lambda: pickaxe('starter')
REG['simple_pickaxe'] = lambda: pickaxe(0)
REG['simple_axe'] = lambda: axe(0)
REG['simple_hoe'] = lambda: hoe(0)


@lambda f: REG.__setitem__('simple_sword', f)
def simple_sword():
    mats = {
        'B': Mat(PLAIN, base=3, model='flat', z=0),
        'G': Mat(P.WOOD_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'W': Mat(P.LEATHER, base=2, model='flat', z=1),
        'P': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
    }
    ic = Icon(mats)
    blade(ic, 'B', -5, 25, 2, 2, k=2)
    bands(ic, 'B', {-2: 3, -1: 1, 0: 0, 1: -1, 2: -2})
    for u in range(26, 37):
        for v in (-7, -8):
            uv_set(ic, u, v, 'G')
    haft(ic, 'W', -16, -9)
    wrap(ic, 'W', period=3, dark=(0,))
    pommel(ic, 'P', -18, r=1.5)
    return ic


# --------------------------------------------------------------------------- Aetherion Manager tome (hotbar)
@lambda f: REG.__setitem__('aetherion_manager', f)
def aetherion_manager():
    cover = ramp('#2a3a8a', n=7, at=3, lo=0.13, hi=0.88, shadow_h=280, light_h=230)
    mats = {
        'C': Mat(cover, base=3, hi=(0, 1, 2), lo=(0, 1, 1), form=1.1, z=1),
        'p': Mat(P.PORCELAIN, base=3, model='flat', z=0),
        'T': Mat(P.GOLD, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        's': Mat(cover, base=1, model='flat', z=2),
        'r': Mat(P.AETHER_GLASS, base=4, model='flat', z=3),
        'b': Mat(P.LILAC, base=3, hi=(0, 1, 1), lo=(0, 1, 1), z=0),
    }
    ic = Icon(mats)
    for y in range(5, 28):
        for x in range(8, 27):
            ic.set(x, y, 'p', -1 if (x == 26 and y % 2) else 0)
    for y in range(4, 27):
        for x in range(5, 25):
            ic.set(x, y, 'C')
    for y in range(4, 27):
        for x in (5, 6, 7):
            ic.set(x, y, 's')
    # gold corner caps
    for x, y in ((5, 4), (6, 4), (5, 5), (24, 4), (23, 4), (24, 5), (5, 26), (6, 26), (5, 25), (24, 26), (23, 26),
                 (24, 25)):
        ic.set(x, y, 'T')
    # the glowing A rune
    rune = [(15, 8), (14, 9), (16, 9), (14, 10), (16, 10), (13, 11), (17, 11), (13, 12), (14, 12), (15, 12), (16, 12),
            (17, 12), (12, 13), (18, 13), (12, 14), (18, 14), (11, 15), (19, 15), (11, 16), (19, 16)]
    for x, y in rune:
        ic.set(x, y, 'r', 1 if y < 12 else 0)
    for x, y in ((10, 19), (11, 20), (12, 19), (18, 19), (19, 20), (20, 19), (15, 21), (15, 18)):
        ic.set(x, y, 'T')
    # lilac ribbon bookmark
    for y in range(26, 31):
        ic.set(21, y, 'b')
        ic.set(22, y, 'b', -1)
    ic.clear(21, 30)
    ic.spark(26, 3, P.WHITE, P.AETHER[5], size=1)
    return ic
