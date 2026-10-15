"""Warden's Prison dungeon weapons (5 callings x 3 tiers) + weapon/armour relics + schematics.

One silhouette per calling, carried through all three tiers:
    sword  Riot Warden's Breaker -> Vanguard Edge     broad riot blade, chained guard
    bow    Escapist's Shortbow   -> Shade String      recurve strung with cell-door wire
    wand   Sculkbound Rod        -> Spiritbind Wand   guard's lantern pole the sculk grew into
    mace   Turnkey's Cell Maul   -> Bulwark Maul      padlock head
    staff  Chaplain's Censer     -> Sanctuary Staff   crook with a hanging censer
Tier language: T1 prison iron, T2 violet (Epic), T3 amber-gold (Legendary).
"""
import math

from forge import Icon, Mat, ramp, mix
import palette as P
from kit import (at, uv_set, uv_bump, uv_get, uv_fill, uv_shade, bands, blade, haft, box, disc_uv, ring_uv, wrap,
                 pommel, gem_plus, gem_diamond)

REG = {}

VIOLET_GLOW = ramp('#a35ae8', n=6, at=2, lo=0.30, hi=0.99, shadow_h=290, light_h=320, c_hi=0.3)
AMBER_GLOW = ramp('#ffa53a', n=6, at=2, lo=0.40, hi=0.995, shadow_h=30, light_h=95, ls=0.6, c_hi=0.3)
SCULK_GLOW = ramp('#2fd6c8', n=6, at=2, lo=0.30, hi=0.99, shadow_h=240, light_h=180, c_hi=0.35)
VIOLET_STEEL = ramp('#7a6c96', n=7, at=3, lo=0.16, hi=0.94, shadow_h=290, light_h=300)
PRISON_IRON = ramp('#77737f', n=7, at=3, lo=0.16, hi=0.90, shadow_h=295, light_h=60, ls=0.15)

TIERS = {
    1: dict(metal=PRISON_IRON, trim=P.IRON_DARK, glow=None, gem=None, spark=None),
    2: dict(metal=VIOLET_STEEL, trim=P.AMETHYST, glow=VIOLET_GLOW, gem=P.AMETHYST, spark=P.RIFT),
    3: dict(metal=P.STEEL, trim=P.GOLD, glow=AMBER_GLOW, gem=P.EMBER, spark=P.SUN),
}
# calling accents (lore colours: sword c, bow 5, wand 3, mace b, staff e)
ACCENT = {
    'sword': P.CLOTH_RED,
    'bow': ramp('#6a3a8a', n=6, at=2, lo=0.15, hi=0.80, shadow_h=290),
    'wand': P.SCULK,
    'mace': ramp('#3f8fb0', n=6, at=3, lo=0.18, hi=0.92, shadow_h=260, light_h=190),
    'staff': P.BRASS,
}


def name(kind, tier):
    return 'dungeon_' + kind if tier == 1 else f'dungeon_t{tier}_{kind}'


def reg(kind):
    def deco(fn):
        for t in (1, 2, 3):
            REG[name(kind, t)] = (lambda tt=t: fn(tt))
        return fn
    return deco


def sparkle(ic, tier, x, y):
    t = TIERS[tier]
    if t['spark'] is None:
        return
    ic.spark(x, y, P.WHITE, t['spark'][4], size=2 if tier == 3 else 1)


# --------------------------------------------------------------------------- sword
@reg('sword')
def sword(tier):
    t = TIERS[tier]
    mats = {
        'B': Mat(t['metal'], base=3, model='flat', z=0),
        'F': Mat(t['glow'] or P.IRON_DARK, base=2 if t['glow'] else 1, model='flat', z=0),
        'G': Mat(t['trim'] if tier > 1 else P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'C': Mat(P.IRON, base=4, hi=(0, 1, 1), lo=(0, 1, 1), z=3),
        'A': Mat(t['gem'] or P.IRON, base=4, model='gem', z=3),
        'W': Mat(ACCENT['sword'], base=2, model='flat', z=1),
        'P': Mat(t['trim'] if tier > 1 else PRISON_IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
    }
    ic = Icon(mats)
    blade(ic, 'B', -5, 25, 3, 3, k=0.75)
    bands(ic, 'B', {-3: 3, -2: 2, -1: 1, 0: 0, 1: 0, 2: -1, 3: -2})
    for v in range(-4, 19):
        uv_set(ic, 31, v, 'F', 1 if (t['glow'] and v < 4) else 0)
    if tier == 1:
        for u, v in ((29, 9), (30, 10), (33, 14), (32, 15), (28, 18), (34, 5)):
            uv_bump(ic, u, v, -2)         # riot nicks
    box(ic, 'G', 24, 38, -8, -6)
    if tier == 3:
        for u, v in ((23, -5), (22, -4), (39, -9), (40, -10)):
            uv_set(ic, u, v, 'G')
        for u, v in ((23, -9), (39, -5)):
            uv_set(ic, u, v, 'G')
    if tier == 1:
        # chain links hanging off the guard
        for p in ((15, 23), (16, 24), (15, 25), (14, 24), (16, 26), (17, 27), (16, 28)):
            ic.set(*p, 'C')
    if t['gem']:
        gem_plus(ic, 'A', 12, 19)
    else:
        ic.set(12, 19, 'C')
    haft(ic, 'W', -16, -9)
    wrap(ic, 'W', period=3, dark=(0,))
    if tier == 1:
        ic.disc(5.5, 25.5, 1.8, 'P')
        ic.lineless_px.add((5, 25))
        ic.clear(5, 25)
    else:
        ic.disc(5.5, 25.5, 1.7, 'P')
        ic.set(5, 25, 'A')
    sparkle(ic, tier, 26, 4)
    return ic


# --------------------------------------------------------------------------- bow
@reg('bow')
def bow(tier):
    t = TIERS[tier]
    stave = {1: P.WOOD_DARK, 2: ramp('#4a3466', n=6, at=2, lo=0.14, hi=0.82, shadow_h=290, light_h=310),
             3: ramp('#a8682e', n=6, at=3, lo=0.20, hi=0.90, shadow_h=20, ss=0.5, light_h=80)}[tier]
    string = {1: P.IRON, 2: VIOLET_GLOW, 3: AMBER_GLOW}[tier]
    mats = {
        'S': Mat(stave, base=3, model='flat', z=1),
        'q': Mat(string, base=4 if tier == 1 else 3, model='flat', z=0),
        'W': Mat(ACCENT['bow'], base=2, model='flat', z=2),
        'M': Mat(t['trim'] if tier > 1 else P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'A': Mat(t['gem'] or P.IRON, base=4, model='gem', z=3),
    }
    ic = Icon(mats)
    span = 21

    def stave_u(v):
        s = v / span
        return 31 - 8.2 * (1 - s * s) + (2.2 * (abs(s) - 0.82) / 0.18 if abs(s) > 0.82 else 0)

    def pred(u, v):
        if not (-span <= v <= span):
            return False
        c = stave_u(v)
        th = 1.6 if abs(v) < 6 else (1.1 if abs(v) < 15 else 0.7)
        return abs(u - c) <= th
    uv_fill(ic, 'S', pred)
    uv_shade(ic, 'S', lambda u, v: 1 if u < stave_u(v) - 0.4 else (-1 if u > stave_u(v) + 0.4 else 0))
    # string from tip to tip, just behind the limbs
    for v in range(-span + 1, span):
        uv_set(ic, 31 + ((31 + v) % 2), v, 'q')
    uv_fill(ic, 'W', lambda u, v: -4 <= v <= 4 and abs(u - stave_u(v)) <= 2.0)
    wrap(ic, 'W', period=3, dark=(0,))
    for vv in (-span, span):
        uv_fill(ic, 'M', lambda u, v, vv=vv: abs(v - vv) <= 1.5 and abs(u - stave_u(vv)) <= 1.6)
    if t['gem']:
        c = stave_u(0)
        x, y = int(round((c - 2 + 0) / 2)), int(round((c - 2) / 2))
        uv_fill(ic, 'A', lambda u, v: abs(v) <= 1 and abs(u - (stave_u(0) - 2.4)) <= 1.1)
    sparkle(ic, tier, 27, 3)
    return ic


# --------------------------------------------------------------------------- wand
@reg('wand')
def wand(tier):
    t = TIERS[tier]
    glow = {1: SCULK_GLOW, 2: VIOLET_GLOW, 3: AMBER_GLOW}[tier]
    mats = {
        'T': Mat(PRISON_IRON if tier < 3 else P.IRON_DARK, base=3, model='flat', z=0),
        'K': Mat(t['trim'] if tier > 1 else P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'L': Mat(glow, base=3, model='sphere', z=1),
        's': Mat(P.SCULK, base=2, model='flat', z=1),
        'g': Mat(glow, base=4, model='flat', z=3),
    }
    ic = Icon(mats)
    haft(ic, 'T', -24, 9)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    # lantern cage: posts, cap, glowing core
    disc_uv(ic, 'L', 31, 15, 4.4)
    for u in (26, 36):
        uv_fill(ic, 'K', lambda uu, v, u=u: abs(uu - u) <= 0.6 and 10 <= v <= 20)
    box(ic, 'K', 26, 36, 9, 10)
    box(ic, 'K', 27, 35, 20, 21)
    for u, v in ((31, 22), (31, 23), (30, 24), (32, 24)):
        uv_set(ic, u, v, 'K')
    uv_fill(ic, 'K', lambda u, v: u == 31 and 10 <= v <= 20 and v % 3 == 0, only='L')
    if tier == 1:
        # sculk creeping down the pole and over the cage
        for u, v in ((30, 6), (29, 5), (32, 3), (33, 2), (30, -2), (29, -3), (32, -8), (33, -9), (30, -12),
                     (25, 12), (25, 13), (37, 16), (37, 17), (28, 8), (34, 8)):
            uv_set(ic, u, v, 's')
        ic.fx.append((26, 2, SCULK_GLOW[4]))
        ic.fx.append((15, 7, SCULK_GLOW[3]))
    else:
        for u, v in ((31, 26), (30, 27), (32, 28)):
            uv_set(ic, u, v, 'g')
    sparkle(ic, tier, 27, 3)
    return ic


# --------------------------------------------------------------------------- mace
@reg('mace')
def mace(tier):
    t = TIERS[tier]
    body = {1: PRISON_IRON, 2: VIOLET_STEEL, 3: P.GOLD}[tier]
    mats = {
        'H': Mat(body, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.0, z=1),
        'S': Mat(t['trim'] if tier > 1 else P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'k': Mat(t['glow'] or P.VOID, base=3 if t['glow'] else 1, model='flat', z=2),
        'T': Mat(P.WOOD_DARK, base=2, model='flat', z=0),
        'W': Mat(ACCENT['mace'], base=2, model='flat', z=0),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'R': Mat(P.RUST, base=3, model='flat', z=1),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 10)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -12)
    wrap(ic, 'W', period=3, dark=(0,))
    box(ic, 'M', 29, 33, 7, 9)
    # padlock: body + shackle loop toward the tip
    box(ic, 'H', 23, 39, 9, 19, chamfer=2)
    ring_uv(ic, 'S', 31, 19, 3.6, 5.6, arc=(0, 180))
    # keyhole
    for u, v in ((31, 15), (31, 14), (31, 13), (30, 12), (32, 12), (31, 11)):
        uv_set(ic, u, v, 'k')
    if tier == 1:
        for u, v in ((25, 11), (26, 12), (37, 17), (36, 18), (35, 10)):
            uv_set(ic, u, v, 'R')
    if tier == 3:
        for u, v in ((24, 18), (38, 18), (24, 10), (38, 10)):
            uv_bump(ic, u, v, 2)
    sparkle(ic, tier, 28, 2)
    return ic


# --------------------------------------------------------------------------- staff
@reg('staff')
def staff(tier):
    t = TIERS[tier]
    censer = {1: P.BRASS, 2: VIOLET_STEEL, 3: P.GOLD}[tier]
    glow = {1: AMBER_GLOW, 2: VIOLET_GLOW, 3: AMBER_GLOW}[tier]
    smoke = {1: ramp('#b7b2c2', n=5, at=2, lo=0.45, hi=0.95), 2: ramp('#b9a2dc', n=5, at=2, lo=0.45, hi=0.97),
             3: ramp('#ffe7a8', n=5, at=2, lo=0.55, hi=0.99)}[tier]
    mats = {
        'T': Mat(P.WOOD, base=3, model='flat', z=0),
        'B': Mat(t['trim'] if tier > 1 else P.BRASS, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'C': Mat(censer, base=3, model='sphere', z=2),
        'g': Mat(glow, base=3, model='flat', z=3),
        'c': Mat(P.IRON_DARK, base=3, model='flat', z=1),
        'm': Mat(smoke, base=2, model='flat', z=0),
    }
    ic = Icon(mats)
    ic.noline.add('m')
    haft(ic, 'T', -24, 16)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    for vv in (-14, -3, 8):
        box(ic, 'B', 30, 32, vv, vv + 1)
    # crook bending to the shadow side
    ring_uv(ic, 'B', 35, 16, 2.6, 4.2, arc=(330, 180))
    # chain + censer hanging below the crook
    for u, v in ((39, 15), (39, 13), (39, 11), (40, 12), (40, 10)):
        uv_set(ic, u, v, 'c')
    disc_uv(ic, 'C', 40, 6, 3.6)
    for u, v in ((39, 7), (41, 5), (40, 4)):
        uv_set(ic, u, v, 'g')
    for p in ((27, 12), (28, 11), (27, 10), (26, 9), (27, 8), (26, 7)):
        ic.fx.append(p + (smoke[3 if p[1] % 2 else 2],))
    if tier > 1:
        ic.fx.append((25, 6, smoke[4]))
    sparkle(ic, tier, 29, 3)
    return ic


# --------------------------------------------------------------------------- relics
def _seal_mats(tier):
    glow = {2: VIOLET_GLOW, 3: AMBER_GLOW}[tier]
    return {
        'w': Mat(ramp('#6b5a4a', n=6, at=3, lo=0.15, hi=0.85, shadow_h=320, ss=0.4), base=3, hi=(0, 1, 2),
                 lo=(0, 1, 1), form=0.8, z=0),
        'c': Mat(P.IRON, base=3, hi=(0, 1, 1), lo=(0, 1, 1), z=2),
        's': Mat(glow, base=3, model='gem', z=3),
        'k': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
    }


def weapon_relic(tier):
    ic = Icon(_seal_mats(tier))
    # a sword-shaped bundle wrapped in old cloth, chained, sealed
    blade(ic, 'w', -14, 24, 3, 3, k=1.0)
    uv_shade(ic, 'w', lambda u, v: (1 if (v + u) % 6 in (0, 1) else 0) - (1 if u >= 33 else 0))
    haft(ic, 'k', -22, -15)
    pommel(ic, 'k', -22, r=1.6)
    for vv in (14, 2):
        uv_fill(ic, 'c', lambda u, v, vv=vv: 27 <= u <= 35 and vv <= v <= vv + 1)
    ic.disc(15.0, 16.0, 2.6, 's')
    sparkle(ic, tier, 26, 4)
    return ic


REG['dungeon_t2_weapon_relic'] = lambda: weapon_relic(2)
REG['dungeon_t3_weapon_relic'] = lambda: weapon_relic(3)


# --------------------------------------------------------------------------- schematics
def schematic(kind):
    paper = ramp('#3a6fae', n=6, at=3, lo=0.20, hi=0.90, shadow_h=270, light_h=200)
    mats = {
        'p': Mat(paper, base=3, hi=(0, 1, 1), lo=(0, 1, 1), z=0),
        'r': Mat(P.PORCELAIN, base=3, model='flat', z=1),
        'l': Mat(paper, base=5, model='flat', z=1),
        'x': Mat(P.WOOD_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'a': Mat(VIOLET_GLOW if kind == 'dungeon' else P.GOLD, base=3, model='gem', z=3),
    }
    ic = Icon(mats)
    for y in range(6, 27):
        for x in range(5, 27):
            ic.set(x, y, 'p')
    for y in (5, 27):
        for x in range(3, 29):
            ic.set(x, y, 'x')
    for y in (4, 6, 26, 28):
        for x in (3, 4, 27, 28):
            ic.set(x, y, 'x')
    # grid
    for y in range(8, 26, 4):
        for x in range(6, 26):
            ic.set(x, y, 'l', -1)
    for x in range(8, 26, 4):
        for y in range(7, 26):
            ic.set(x, y, 'l', -1)
    # sword diagram
    for i in range(0, 12):
        ic.set(9 + i, 22 - i, 'r', 1)
        ic.set(10 + i, 22 - i, 'r', 0)
    for p in ((10, 17), (11, 18), (12, 19), (13, 20), (14, 21)):
        ic.set(*p, 'r', 0)
    for p in ((7, 24), (8, 23)):
        ic.set(*p, 'r', -1)
    gem_diamond(ic, 'a', 22, 20, 2)
    return ic


REG['weapon_schematic'] = lambda: schematic('weapon')
REG['dungeon_weapon_schematic'] = lambda: schematic('dungeon')
