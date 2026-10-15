"""Skill ladders: one silhouette per family, five tiers in the house tier language
(T1 base, T2 ice, T3 violet, T4 ember, T5 gold)."""
import math

from forge import Icon, Mat, ramp, mix
import palette as P
from kit import (uv_set, uv_bump, uv_get, uv_fill, uv_shade, bands, blade, haft, box, disc_uv, ring_uv, wrap,
                 pommel, gem_plus, gem_diamond)

REG = {}

T_BLADE = {
    1: ramp('#9aa1ae', n=7, at=3, lo=0.20, hi=0.97, shadow_h=290, light_h=220, ls=0.2),
    2: ramp('#8fc6e6', n=7, at=3, lo=0.22, hi=0.99, shadow_h=265, light_h=195),
    3: ramp('#9a7ad8', n=7, at=3, lo=0.18, hi=0.97, shadow_h=285, light_h=310),
    4: ramp('#4a3a40', n=7, at=3, lo=0.13, hi=0.86, shadow_h=320, light_h=40, ls=0.3),
    5: ramp('#f4dc8e', n=7, at=4, lo=0.30, hi=0.995, shadow_h=35, ss=0.6, light_h=100, ls=0.5, c_hi=0.3),
}
T_TRIM = {1: P.IRON_DARK, 2: P.SILVER, 3: P.AMETHYST, 4: P.IRON_DARK, 5: P.GOLD}
T_GLOW = {1: None, 2: P.ICE, 3: P.RIFT, 4: P.MAGMA, 5: P.SUN}
T_GEM = {1: None, 2: P.ICE, 3: P.AMETHYST, 4: P.EMBER, 5: P.CRIMSON}
T_GRIP = {1: P.CLOTH_RED, 2: ramp('#2f5a8a', n=6, at=2, lo=0.15, hi=0.85, shadow_h=270),
          3: ramp('#4a2a6a', n=6, at=2, lo=0.12, hi=0.80, shadow_h=290), 4: P.LEATHER_DARK, 5: P.CLOTH_RED}


def sparkle(ic, tier, x, y):
    g = T_GLOW[tier]
    if tier >= 4:
        ic.spark(x, y, P.WHITE, g[4], size=2 if tier == 5 else 1)
    elif tier == 3:
        ic.spark(x, y, P.WHITE, g[3], size=1)


# --------------------------------------------------------------------------- combat swords
COMBAT_SWORDS = {1: 'combat_sword', 2: 'combat_sword_2', 3: 'combat_sword_3', 4: 'combat_sword_4', 5: 'combat_sword_5'}


def combat_sword(tier):
    glow = T_GLOW[tier]
    mats = {
        'B': Mat(T_BLADE[tier], base=3 if tier != 5 else 4, model='flat', z=0),
        'F': Mat(glow or P.IRON_DARK, base=2 if glow else 1, model='flat', z=0),
        'E': Mat(glow or T_BLADE[tier], base=4, model='flat', z=0),
        'G': Mat(T_TRIM[tier], base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'A': Mat(T_GEM[tier] or P.IRON, base=4, model='gem', z=3),
        'W': Mat(T_GRIP[tier], base=2, model='flat', z=1),
        'P': Mat(T_TRIM[tier], base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
    }
    ic = Icon(mats)
    half = 2 if tier == 1 else 3
    blade(ic, 'B', -5, 26, half, half, k=2)
    tbl = {-3: 3, -2: 2, -1: 1, 0: 0, 1: 0, 2: -1, 3: -2} if half == 3 else {-2: 3, -1: 1, 0: 0, 1: -1, 2: -2}
    bands(ic, 'B', tbl)
    # fuller: a groove for T1, a glowing channel from T2 up
    for v in range(-4, 16 + tier):
        uv_set(ic, 31, v, 'F', 1 if (glow and v < 4) else 0)
    if tier == 4:
        # ember edge on the lit side
        uv_fill(ic, 'E', lambda u, v: u == 28 and v >= -2, only='B')
    if tier == 5:
        uv_fill(ic, 'E', lambda u, v: u == 28 and v >= -2, only='B')
        uv_shade(ic, 'E', lambda u, v: 1)
    # guard grows with the tier
    reach = {1: 5, 2: 6, 3: 6, 4: 7, 5: 7}[tier]
    for u in range(31 - reach, 32 + reach):
        for v in (-7, -8):
            uv_set(ic, u, v, 'G')
    if tier >= 2:
        # quillons curling toward the tip
        for s in (-1, 1):
            u = 31 + s * (reach + 1)
            for v in (-6, -5) if tier < 4 else (-6, -5, -4):
                uv_set(ic, u, v, 'G')
            if tier >= 4:
                uv_set(ic, u + s, -3, 'G')
    if tier == 5:
        # gold wings flaring off the guard
        for u, v in ((22, -3), (23, -4), (22, -5), (21, -4), (40, -11), (41, -12), (40, -13), (41, -10)):
            uv_set(ic, u, v, 'G')
    if T_GEM[tier]:
        gem_plus(ic, 'A', 12, 19)
    else:
        ic.set(12, 19, 'G', 2)
    haft(ic, 'W', -16, -9)
    wrap(ic, 'W', period=3, dark=(0,))
    if tier == 1:
        pommel(ic, 'P', -18, r=1.6)
    else:
        pommel(ic, 'P', -18, r=1.8)
        ic.set(5, 25, 'A')
    if tier >= 2:
        ic.fix[(29, 2)] = P.glint(T_BLADE[tier][6], 0.5)
    sparkle(ic, tier, 26, 5)
    return ic


for _t, _n in COMBAT_SWORDS.items():
    REG[_n] = (lambda t=_t: combat_sword(t))


# --------------------------------------------------------------------------- early bows (16px placeholders in live)
def early_bow(kind):
    """simple_shortbow / reinforced_shortbow / simple_longbow / iron_longbow / hunter_bow."""
    span = {'simple_shortbow': 17, 'reinforced_shortbow': 18, 'simple_longbow': 22, 'iron_longbow': 22,
            'hunter_bow': 20}[kind]
    wood = {'simple_shortbow': P.WOOD, 'reinforced_shortbow': P.WOOD_DARK, 'simple_longbow': P.WOOD,
            'iron_longbow': P.WOOD_DARK, 'hunter_bow': ramp('#6a4a2a', n=6, at=3, lo=0.18, hi=0.80, shadow_h=20)}[kind]
    mats = {
        'S': Mat(wood, base=3, model='flat', z=1),
        'q': Mat(P.SILK, base=3, model='flat', z=0),
        'W': Mat({'hunter_bow': ramp('#3f6e3a', n=6, at=2, lo=0.16, hi=0.80, shadow_h=200)}.get(kind, P.LEATHER),
                 base=2, model='flat', z=2),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
    }
    ic = Icon(mats)
    bulge = 7.0 if 'short' in kind else 8.6

    def stave_u(v):
        s = v / span
        return 31 - bulge * (1 - s * s)

    def pred(u, v):
        if not (-span <= v <= span):
            return False
        th = 1.3 if abs(v) < 5 else (0.95 if abs(v) < span - 5 else 0.6)
        return abs(u - stave_u(v)) <= th
    uv_fill(ic, 'S', pred)
    uv_shade(ic, 'S', lambda u, v: 1 if u < stave_u(v) - 0.3 else (-1 if u > stave_u(v) + 0.3 else 0))
    for v in range(-span + 1, span):
        uv_set(ic, 31 + ((31 + v) % 2), v, 'q')
    uv_fill(ic, 'W', lambda u, v: -3 <= v <= 3 and abs(u - stave_u(v)) <= 1.8)
    wrap(ic, 'W', period=2, dark=(0,))
    if kind in ('reinforced_shortbow', 'iron_longbow'):
        for vv in (-span + 2, -10, 10, span - 2):
            uv_fill(ic, 'M', lambda u, v, vv=vv: abs(v - vv) <= 1 and abs(u - stave_u(vv)) <= 1.4)
    if kind == 'hunter_bow':
        for vv in (-span + 1, span - 1):
            uv_fill(ic, 'M', lambda u, v, vv=vv: abs(v - vv) <= 1 and abs(u - stave_u(vv)) <= 1.2)
    return ic


for _k in ('simple_shortbow', 'reinforced_shortbow', 'simple_longbow', 'iron_longbow', 'hunter_bow'):
    REG[_k] = (lambda k=_k: early_bow(k))
