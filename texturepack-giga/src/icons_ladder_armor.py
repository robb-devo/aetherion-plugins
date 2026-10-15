"""Skill armour ladders. Every family keeps one silhouette and motif through its tiers; the tier
shows in the house language (T1 base, T2 ice, T3 violet, T4 ember, T5 gold).

    combat   knightly plate (T1 chainmail)          mining    hard hat + headlamp, straps
    farming  straw hat, denim, wheat                fishing   sou'wester, oilskin, waders
    foraging leaf hood, bark plates, leaves         catcher   (3 tiers) sphere-catcher kit
    diving   brass helmet, canvas suit              simple    plain stitched leather
"""
import math

from forge import Icon, Mat, ramp, mix
import palette as P
import armor as A
from armor import rect, ell, put_pts, recolor, CX
from icons_ladder import T_BLADE

REG = {}
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')

ICE_L = ramp('#9fdcf4', n=6, at=3, lo=0.30, hi=0.99, shadow_h=265, light_h=195)
VIO_L = ramp('#a07ae0', n=6, at=3, lo=0.22, hi=0.97, shadow_h=285, light_h=310)
EMB_L = ramp('#f08a2c', n=6, at=3, lo=0.26, hi=0.99, shadow_h=20, ss=0.6, light_h=95, ls=0.6)
GLD_L = ramp('#f2c440', n=6, at=3, lo=0.30, hi=0.995, shadow_h=35, ss=0.6, light_h=100, ls=0.5)
TIER_ACCENT = {1: None, 2: ICE_L, 3: VIO_L, 4: EMB_L, 5: GLD_L}
TIER_GEM = {1: None, 2: P.ICE, 3: P.AMETHYST, 4: P.EMBER, 5: P.CRIMSON}
TIER_TRIM = {1: P.IRON_DARK, 2: P.SILVER, 3: P.AMETHYST, 4: P.IRON_DARK, 5: P.GOLD}


def name(family, piece, tier):
    return f'{family}_{piece}' + ('' if tier == 1 else f'_{tier}')


def ladder(family, tiers=5):
    def deco(fn):
        for t in range(1, tiers + 1):
            for p in PIECES:
                REG[name(family, p, t)] = (lambda pp=p, tt=t: fn(pp, tt))
        return fn
    return deco


def base_mats(plate, trim, accent=None, gem=None, glow=None, plate_base=3, form=1.1, interior=None, extra=None):
    m = {
        'O': Mat(plate, base=plate_base, hi=(0, 1, 2), lo=(0, 1, 2), form=form, z=0),
        'T': Mat(trim, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'I': Mat(interior or ramp('#1e1828', n=4, at=1), base=1, model='flat', z=-1),
        'A': Mat(accent or trim, base=3, hi=(0, 1, 2), lo=(0, 1, 1), form=0.5, z=1),
        'G': Mat(gem or P.AMETHYST, base=4, model='gem', z=3),
        'R': Mat(glow or P.EMBER, base=3, model='flat', z=2),
    }
    if extra:
        m.update(extra)
    return m


def tier_spark(ic, tier, x, y):
    if tier >= 4:
        acc = TIER_ACCENT[tier]
        ic.spark(x, y, P.WHITE, acc[3], size=1)


def gem_center(ic, x, y, tier):
    if TIER_GEM[tier]:
        ic.set(x, y, 'G')
        ic.set(x + 1, y, 'G')
        ic.set(x, y + 1, 'G')
        ic.set(x + 1, y + 1, 'G')


# =========================================================================== combat
@ladder('combat')
def combat(piece, tier):
    plate = T_BLADE[tier]
    trim = {1: P.IRON_DARK, 2: P.SILVER, 3: P.AMETHYST, 4: EMB_L, 5: P.GOLD}[tier]
    ic = Icon(base_mats(plate, trim, accent=P.CLOTH_RED if tier in (1, 5) else trim, gem=TIER_GEM[tier] or P.CRIMSON,
                        plate_base=4 if tier == 5 else 3))
    if piece == 'helmet':
        A.helmet(ic, face='T')
        for y in range(3, 12):
            if ic.get(15, y) == 'O':
                ic.set(15, y, 'A' if tier >= 3 else 'O', 1)
                ic.set(16, y, 'A' if tier >= 3 else 'O', -1)
        if tier >= 3:
            # plume / crest standing over the dome
            for k, (x, y) in enumerate(((15, 2), (16, 2), (16, 1), (17, 1), (17, 0), (18, 0))):
                ic.set(x, y, 'A', 1 if k % 2 == 0 else 0)
        if tier == 5:
            put_pts(ic, [(3, 9), (2, 8), (1, 7), (2, 7), (1, 6), (3, 8)], 'T')
    elif piece == 'chestplate':
        A.chestplate(ic)
        # crossed swords on the breast (T1-T2), a gem boss from T3
        if tier <= 2:
            for i in range(-3, 4):
                ic.set(15 + i, 15 + i, 'T', 1)
                ic.set(16 - i, 15 + i, 'T', 1)
        else:
            ell(ic, CX, 15.5, 2.6, 2.6, 'T')
            gem_center(ic, 15, 15, tier)
    elif piece == 'leggings':
        A.leggings(ic)
    else:
        A.boots(ic)
    if tier == 1:
        # chainmail: a ring pattern over the plate
        for y in range(32):
            for x in range(32):
                if ic.get(x, y) == 'O':
                    ic.bump(x, y, 1 if (x + 2 * y) % 4 == 0 else (-1 if (x + 2 * y) % 4 == 2 else 0))
    tier_spark(ic, tier, 27, 4)
    return ic


# =========================================================================== mining
@ladder('mining')
def mining(piece, tier):
    plate = T_BLADE[tier]
    lamp = {1: P.SUN, 2: P.ICE, 3: P.RIFT, 4: P.MAGMA, 5: P.SUN}[tier]
    ic = Icon(base_mats(plate, TIER_TRIM[tier], accent=P.LEATHER, gem=TIER_GEM[tier] or P.EMBER, glow=lamp,
                        plate_base=4 if tier == 5 else 3,
                        extra={'L': Mat(P.LEATHER, base=3, hi=(0, 1, 1), lo=(0, 1, 1), z=2),
                               'l': Mat(lamp, base=4, model='flat', z=3)}))
    if piece == 'helmet':
        # hard hat: dome, ridge, wide brim, headlamp
        ell(ic, CX, 16.0, 11.0, 11.5, 'O', pred=lambda x, y: y <= 16)
        rect(ic, 2, 16, 29, 18, 'T')
        for x in (2, 29):
            ic.clear(x, 18)
        for y in range(5, 16):
            if ic.get(15, y) == 'O':
                ic.set(15, y, 'O', 2)
                ic.set(16, y, 'O', 1)
        rect(ic, 12, 9, 19, 14, 'L')
        ell(ic, CX, 11.5, 2.6, 2.6, 'l')
        ic.set(15, 11, 'l', 2)
        ic.fix[(14, 10)] = P.WHITE
        for k in range(3):
            ic.fx.append((CX - 0.5 - 4 - k, 7 - k, lamp[4]))
    elif piece == 'chestplate':
        A.chestplate(ic, pauldrons=tier >= 3)
        # leather cross straps and a pick badge
        for t in range(16):
            x = 8 + t
            y = 8 + int(t * 1.05)
            for xx in (x, int(2 * CX - x)):
                if ic.get(xx, y) in ('O', 'T'):
                    ic.set(xx, y, 'L')
        for x, y in ((13, 18), (14, 17), (15, 16), (16, 16), (17, 17), (18, 18), (15, 17), (15, 18), (15, 19), (15, 20)):
            ic.set(x, y, 'T', 1)
        gem_center(ic, 15, 13, tier)
    elif piece == 'leggings':
        A.leggings(ic)
        # tool belt pouches
        rect(ic, 6, 6, 9, 9, 'L')
        rect(ic, 22, 6, 25, 9, 'L')
    else:
        A.boots(ic)
        recolor(ic, 'A', 'T', lambda x, y: True)
    tier_spark(ic, tier, 27, 4)
    return ic


# =========================================================================== farming
STRAW = ramp('#d9b45c', n=7, at=4, lo=0.26, hi=0.97, shadow_h=40, ss=0.5, light_h=95)
DENIM = ramp('#3f5f94', n=7, at=3, lo=0.18, hi=0.90, shadow_h=270, light_h=220)
PLAID = ramp('#b8424a', n=6, at=3, lo=0.20, hi=0.90, shadow_h=330, light_h=40)
WHEAT = ramp('#e8b83c', n=6, at=3, lo=0.30, hi=0.99, shadow_h=40, light_h=100)


DENIM_T = {1: DENIM, 2: ramp('#78acd8', n=7, at=3, lo=0.22, hi=0.95, shadow_h=265, light_h=200),
           3: ramp('#6a50a8', n=7, at=3, lo=0.17, hi=0.92, shadow_h=285, light_h=310),
           4: ramp('#3e3036', n=7, at=3, lo=0.13, hi=0.82, shadow_h=320, light_h=40),
           5: ramp('#e6d6aa', n=7, at=4, lo=0.28, hi=0.98, shadow_h=40, ss=0.5, light_h=95)}
OILSKIN_T = {1: None, 2: ramp('#cde6f2', n=7, at=4, lo=0.30, hi=0.99, shadow_h=265, light_h=200),
             3: ramp('#9a78d8', n=7, at=3, lo=0.20, hi=0.96, shadow_h=285, light_h=310),
             4: ramp('#8a2e22', n=7, at=3, lo=0.15, hi=0.86, shadow_h=330, light_h=40),
             5: ramp('#f6e8bc', n=7, at=4, lo=0.32, hi=0.995, shadow_h=40, ss=0.5, light_h=95)}
HOOD_T = {1: None, 2: ramp('#7ec6c2', n=7, at=3, lo=0.20, hi=0.96, shadow_h=240, light_h=190),
          3: ramp('#6a4a9c', n=7, at=3, lo=0.15, hi=0.90, shadow_h=285, light_h=310),
          4: ramp('#a8582c', n=7, at=3, lo=0.17, hi=0.90, shadow_h=20, ss=0.5, light_h=70),
          5: ramp('#c9b648', n=7, at=3, lo=0.22, hi=0.97, shadow_h=40, ss=0.5, light_h=100)}


@ladder('farming')
def farming(piece, tier):
    acc = TIER_ACCENT[tier] or PLAID
    ic = Icon(base_mats(DENIM_T[tier], P.LEATHER, accent=acc, gem=TIER_GEM[tier] or P.EMBER,
                        extra={'S': Mat(STRAW, base=4, hi=(0, 1, 2), lo=(0, 1, 2), form=1.0, z=0),
                               'w': Mat(WHEAT, base=3, model='flat', z=3),
                               'p': Mat(PLAID, base=3, hi=(0, 1, 1), lo=(0, 1, 1), form=0.6, z=0)}))
    if piece == 'helmet':
        # straw hat: crown, wide brim, band in the tier colour
        ell(ic, CX, 14.0, 7.0, 8.5, 'S', pred=lambda x, y: y <= 15)
        ell(ic, CX, 17.0, 14.5, 3.6, 'S')
        rect(ic, 9, 12, 22, 14, 'A')
        for y in range(6, 20):
            for x in range(32):
                if ic.get(x, y) == 'S' and (x + y) % 3 == 0:
                    ic.bump(x, y, -1)
        put_pts(ic, [(10, 11), (9, 10)], 'w')
        gem_center(ic, 19, 12, tier)
    elif piece == 'chestplate':
        A.chestplate(ic, pauldrons=False, belt=False)
        recolor(ic, 'O', 'p', lambda x, y: True)
        recolor(ic, 'T', 'p', lambda x, y: True)
        for y in range(32):
            for x in range(32):
                if ic.get(x, y) == 'p' and (x % 4 == 0 or y % 4 == 0):
                    ic.bump(x, y, -1)
        # denim bib and braces
        rect(ic, 10, 15, 21, 28, 'O')
        for x in (10, 11, 20, 21):
            for y in range(6, 15):
                if ic.get(x, y) is not None:
                    ic.set(x, y, 'O')
        rect(ic, 12, 17, 19, 21, 'O', -1)
        for x, y in ((15, 19), (16, 18), (14, 20), (17, 19), (15, 18)):
            ic.set(x, y, 'w', 1)
        ic.set(10, 15, 'A')
        ic.set(21, 15, 'A')
    elif piece == 'leggings':
        A.leggings(ic, knees=False)
        rect(ic, 6, 4, 25, 5, 'T')
        # patched knees in the tier colour
        for cx in (10, 20):
            rect(ic, cx - 1, 17, cx + 2, 19, 'A')
    else:
        A.boots(ic)
        recolor(ic, 'O', 'O', lambda x, y: True)
        ic.mats['O'] = Mat(P.LEATHER, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.0, z=0)
        for side in (8, 18):
            for y in (12, 15, 18):
                ic.set(side + 2, y, 'A')
                ic.set(side + 3, y + 1, 'A')
    tier_spark(ic, tier, 27, 4)
    return ic


# =========================================================================== fishing
OILSKIN = ramp('#e8b830', n=7, at=4, lo=0.30, hi=0.98, shadow_h=40, ss=0.6, light_h=100)
RUBBER = ramp('#2c4a5e', n=7, at=3, lo=0.15, hi=0.85, shadow_h=270, light_h=200)


@ladder('fishing')
def fishing(piece, tier):
    acc = TIER_ACCENT[tier] or P.AETHER
    ic = Icon(base_mats(OILSKIN_T[tier] or OILSKIN, RUBBER, accent=acc, gem=TIER_GEM[tier] or P.AETHER, plate_base=4,
                        extra={'U': Mat(RUBBER, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.0, z=0)}))
    if piece == 'helmet':
        # sou'wester: domed crown, a broad brim, rain flaps drooping at both sides, chin strap
        for y in range(16, 26):
            inset = (y - 16) * 0.55
            for x in range(32):
                d = abs(x + 0.5 - CX)
                if 6.5 < d <= 13.0 - inset:
                    ic.set(x, y, 'O', -1)
        ell(ic, CX, 16.0, 14.0, 3.4, 'O')
        ell(ic, CX, 11.5, 7.0, 7.5, 'O', pred=lambda x, y: y <= 15)
        for y in range(32):
            for x in range(32):
                if ic.get(x, y) == 'O' and y >= 17 and abs(x + 0.5 - CX) <= 6.5:
                    ic.clear(x, y)
        rect(ic, 9, 12, 22, 13, 'A')
        for x, y in ((9, 17), (10, 18), (11, 19), (12, 20), (22, 17), (21, 18), (20, 19), (19, 20), (13, 21), (18, 21),
                     (14, 21), (15, 21), (16, 21), (17, 21)):
            ic.set(x, y, 'U')
        for y in range(5, 12):
            if ic.get(15, y) == 'O':
                ic.bump(15, y, 1)
        gem_center(ic, 15, 12, tier)
    elif piece == 'chestplate':
        A.chestplate(ic, pauldrons=False, belt=False)
        recolor(ic, 'T', 'O', lambda x, y: y > 8)
        # toggles down the front, a fish badge
        for y in range(10, 27, 4):
            ic.set(15, y, 'U')
            ic.set(16, y, 'U')
        for x, y in ((10, 16), (11, 15), (12, 15), (13, 16), (12, 17), (11, 17), (9, 15), (9, 17)):
            ic.set(x, y, 'A')
        gem_center(ic, 20, 12, tier)
    elif piece == 'leggings':
        A.leggings(ic, knees=False)
        recolor(ic, 'O', 'U', lambda x, y: True)
        recolor(ic, 'T', 'U', lambda x, y: True)
        for x in (9, 22):
            for y in range(0, 6):
                ic.set(x, y, 'A')
        rect(ic, 6, 6, 25, 7, 'A')
    else:
        A.boots(ic)
        recolor(ic, 'O', 'U', lambda x, y: True)
        recolor(ic, 'T', 'O', lambda x, y: True)
        recolor(ic, 'A', 'U', lambda x, y: True)
        for side in (8, 18):
            for x in range(side - 1, side + 7):
                if ic.get(x, 10) is not None:
                    ic.set(x, 10, 'A')
    tier_spark(ic, tier, 27, 4)
    return ic


# =========================================================================== foraging
HOOD = ramp('#3f7a3a', n=7, at=3, lo=0.15, hi=0.88, shadow_h=200, light_h=100)
BARK = ramp('#6a4a32', n=7, at=3, lo=0.15, hi=0.82, shadow_h=320, ss=0.4, light_h=70)
LEAF_T1 = ramp('#6cc04a', n=6, at=3, lo=0.22, hi=0.95, shadow_h=170, light_h=100)


def leaf(ic, x, y, flip=False):
    pts = [(0, 0), (1, 0), (1, -1), (2, -1), (2, -2), (0, -1), (1, -2), (3, -2), (3, -3)]
    for dx, dy in pts:
        ic.set(x - dx if flip else x + dx, y + dy, 'A', 1 if (dx + dy) % 2 == 0 else 0)


@ladder('foraging')
def foraging(piece, tier):
    acc = TIER_ACCENT[tier] or LEAF_T1
    ic = Icon(base_mats(HOOD_T[tier] or HOOD, BARK, accent=acc, gem=TIER_GEM[tier] or P.JADE,
                        extra={'B': Mat(BARK, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=0.8, z=1)}))
    if piece == 'helmet':
        A.helmet(ic, face='open', cheeks=True)
        recolor(ic, 'T', 'B', lambda x, y: True)
        leaf(ic, 15, 4)
        leaf(ic, 19, 6)
        leaf(ic, 12, 6, flip=True)
        gem_center(ic, 15, 12, tier)
    elif piece == 'chestplate':
        A.chestplate(ic, pauldrons=True)
        recolor(ic, 'A', 'A', lambda x, y: True)
        # bark plates on the chest and a leaf clasp
        for y in range(12, 24, 4):
            rect(ic, 10, y, 21, y + 2, 'B')
        leaf(ic, 14, 11)
        gem_center(ic, 15, 9, tier)
    elif piece == 'leggings':
        A.leggings(ic)
        recolor(ic, 'O', 'B', lambda x, y: y >= 9)
        for cx in (10, 20):
            leaf(ic, cx - 1, 20)
    else:
        A.boots(ic)
        recolor(ic, 'O', 'B', lambda x, y: True)
        for side in (8, 18):
            for x in range(side - 1, side + 7):
                for y in (9, 10, 11):
                    if ic.get(x, y) is not None:
                        ic.set(x, y, 'A', 1 if (x + y) % 2 else 0)
    tier_spark(ic, tier, 27, 4)
    return ic


# =========================================================================== catcher (3 tiers)
CATCH_TEAL = ramp('#2f9a8a', n=7, at=3, lo=0.16, hi=0.92, shadow_h=240, light_h=170)


@ladder('catcher', tiers=3)
def catcher(piece, tier):
    plate = {1: CATCH_TEAL, 2: ramp('#6f9ad8', n=7, at=3, lo=0.18, hi=0.95, shadow_h=270, light_h=210),
             3: ramp('#e8c050', n=7, at=4, lo=0.28, hi=0.99, shadow_h=35, ss=0.6, light_h=100)}[tier]
    ic = Icon(base_mats(plate, P.PORCELAIN, accent=P.CRIMSON, gem=P.AETHER,
                        extra={'s': Mat(P.CRIMSON, base=3, model='sphere', z=3),
                               'w': Mat(P.PORCELAIN, base=4, model='sphere', z=3)}))

    def sphere(x, y):
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                if dx * dx + dy * dy <= 5:
                    ic.set(x + dx, y + dy, 's' if dy < 0 else 'w')
        ic.set(x, y, 'I')
    if piece == 'helmet':
        A.helmet(ic, face='open')
        # goggles on the brow
        for cx in (11, 20):
            ell(ic, cx, 12.5, 2.4, 2.0, 'A')
            ic.set(cx, 12, 'G')
        rect(ic, 13, 12, 18, 13, 'T')
    elif piece == 'chestplate':
        A.chestplate(ic, pauldrons=False)
        sphere(15, 16)
        for x in range(8, 24):
            if ic.get(x, 24) is not None:
                ic.set(x, 24, 'A')
    elif piece == 'leggings':
        A.leggings(ic, knees=False)
        rect(ic, 6, 12, 9, 15, 'T')
        rect(ic, 22, 12, 25, 15, 'T')
    else:
        A.boots(ic)
        recolor(ic, 'A', 'T', lambda x, y: True)
    if tier == 3:
        ic.spark(27, 4, P.WHITE, P.SUN[4], size=1)
    return ic


# =========================================================================== diving (single tier)
CANVAS = ramp('#b8a07a', n=7, at=4, lo=0.24, hi=0.95, shadow_h=320, ss=0.4, light_h=80)


@lambda f: [REG.__setitem__('diving_' + p, (lambda pp=p: f(pp))) for p in PIECES]
def diving(piece):
    ic = Icon(base_mats(CANVAS, P.BRASS, accent=P.BRASS, gem=P.AETHER_GLASS,
                        extra={'g': Mat(P.AETHER_GLASS, base=3, model='sphere', z=2)}))
    if piece == 'helmet':
        # brass diving helmet with a round front port and side ports
        ic.mats['O'] = Mat(P.BRASS, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.4, z=0)
        ell(ic, CX, 14.0, 11.5, 12.0, 'O')
        ell(ic, CX, 15.0, 5.6, 5.6, 'T')
        ell(ic, CX, 15.0, 4.2, 4.2, 'g')
        for cx in (5.5, 25.5):
            ell(ic, cx, 15.0, 2.2, 2.6, 'g')
        rect(ic, 6, 25, 25, 27, 'T')
        for x in range(7, 25, 3):
            ic.set(x, 26, 'T', 2)
    elif piece == 'chestplate':
        A.chestplate(ic, pauldrons=False)
        ell(ic, CX, 6.0, 9.0, 4.0, 'T', pred=lambda x, y: y >= 4)
        for x in range(8, 24, 4):
            ic.set(x, 7, 'T', 2)
    elif piece == 'leggings':
        A.leggings(ic)
    else:
        A.boots(ic)
        for side in (-1, 1):
            for x in range(2, 14):
                for y in (27, 28):
                    ic.set(x if side < 0 else 31 - x, y, 'T')
    return ic


# =========================================================================== simple leather
@lambda f: [REG.__setitem__('simple_' + p, (lambda pp=p: f(pp))) for p in PIECES]
def simple(piece):
    ic = Icon(base_mats(P.LEATHER, P.LEATHER_DARK, accent=P.LEATHER_DARK))
    getattr(A, piece)(ic) if piece != 'helmet' else A.helmet(ic, face='open')
    # stitching
    for y in range(32):
        for x in range(32):
            if ic.get(x, y) == 'O' and ic.get(x, y - 1) in ('T', None) and x % 2 == 0:
                ic.bump(x, y, 2)
    return ic
