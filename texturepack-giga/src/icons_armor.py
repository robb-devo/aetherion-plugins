"""P1 armour sets: boss sets first (each piece decorates the shared bases in armor.py)."""
import math

from forge import Icon, Mat, ramp, mix
import palette as P
import armor as A
from armor import rect, ell, put_pts, recolor, CX

REG = {}
PIECES = ('helmet', 'chestplate', 'leggings', 'boots')


def set_mats(plate, trim, interior=None, accent=None, gem=None, glow=None, plate_base=3, form=1.2, extra=None):
    m = {
        'O': Mat(plate, base=plate_base, hi=(0, 1, 2), lo=(0, 1, 2), form=form, z=0),
        'T': Mat(trim, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'I': Mat(interior or P.VOID, base=1, model='flat', z=-1),
        'A': Mat(accent or trim, base=3, hi=(0, 1, 2), lo=(0, 1, 1), form=0.6, z=1),
        'G': Mat(gem or P.AMETHYST, base=4, model='gem', z=3),
        'R': Mat(glow or P.EMBER, base=3, model='flat', z=2),
    }
    if extra:
        m.update(extra)
    return m


def armor_set(prefix, names=None):
    """Register prefix_helmet.. (or explicit names) from fn(piece)."""
    def deco(fn):
        for i, p in enumerate(PIECES):
            n = names[i] if names else f'{prefix}_{p}'
            REG[n] = (lambda pp=p: fn(pp))
        return fn
    return deco


def ridge(ic, x, y0, y1, key='O', d=2):
    for y in range(y0, y1 + 1):
        if ic.get(x, y) == key:
            ic.bump(x, y, d)
        if ic.get(x + 1, y) == key:
            ic.bump(x + 1, y, -1)


def rays(ic, cx, cy, r0, r1, angles, key, width=1):
    for a in angles:
        ra = math.radians(a)
        for r in [r0 + i * 0.5 for i in range(int((r1 - r0) * 2) + 1)]:
            x, y = int(round(cx + math.cos(ra) * r)), int(round(cy + math.sin(ra) * r))
            if ic.get(x, y) is None or ic.get(x, y) == key:
                ic.set(x, y, key)
            if width > 1 and r < r0 + (r1 - r0) * 0.5:
                for dx, dy in ((1, 0), (0, 1)):
                    if ic.get(x + dx, y + dy) is None:
                        ic.set(x + dx, y + dy, key)


# =========================================================================== Hollow Sun (Mythic)
HS_PLATE = ramp('#4a2630', n=7, at=3, lo=0.13, hi=0.85, shadow_h=300, light_h=40, ls=0.3)


@armor_set('hollow_sun')
def hollow_sun(piece):
    ic = Icon(set_mats(HS_PLATE, P.GOLD, interior=ramp('#2a0d10', n=4, at=1), accent=P.CRIMSON, gem=P.SUN,
                       glow=P.MAGMA))
    if piece == 'helmet':
        # corona: gold rays fanning off the top of the dome
        rays(ic, CX, 15.0, 11.0, 15.0, range(200, 345, 20), 'T', width=2)
        A.helmet(ic, face='slit')
        recolor(ic, 'I', 'R', lambda x, y: y in (16, 17))
        for x, y in ((15, 12), (16, 12), (15, 11), (16, 11)):
            ic.set(x, y, 'G')
        ridge(ic, 15, 4, 10)
        ic.spark(27, 4, P.WHITE, P.SUN[4], size=1)
    elif piece == 'chestplate':
        A.chestplate(ic)
        recolor(ic, 'A', 'A', lambda x, y: True)
        # hollow heart: gold ring cage around a caged star
        ell(ic, CX, 16.0, 5.2, 5.2, 'T')
        ell(ic, CX, 16.0, 3.6, 3.6, 'R')
        ell(ic, CX, 16.0, 1.6, 1.6, 'G')
        for x in (13, 18):
            for y in range(12, 21):
                if ic.get(x, y) == 'R':
                    ic.set(x, y, 'T', 1)
        ic.spark(15, 16, P.WHITE, P.SUN[5], size=1)
    elif piece == 'leggings':
        A.leggings(ic)
        recolor(ic, 'O', 'A', lambda x, y: y >= 9)
        for cx in (10.5, 20.5):
            ell(ic, cx, 18.0, 2.2, 2.2, 'G')
        for x, y in ((9, 23), (10, 24), (9, 25), (21, 22), (22, 23), (21, 24), (12, 11), (19, 12)):
            ic.set(x, y, 'R', -1)
    else:
        A.boots(ic)
        star = ramp('#8a4fd8', n=6, at=3, lo=0.25, hi=0.99, shadow_h=290, light_h=320)
        ic.mats['s'] = Mat(star, base=4, model='flat', z=2)
        for x0 in (9, 18):
            for i in range(5):
                ic.set(x0 + i, 12 + i * 2, 's', 1 - i // 2)
                ic.set(x0 + i, 13 + i * 2, 's', 0 - i // 2)
        ic.spark(5, 5, P.WHITE, star[4], size=1)
        ic.spark(26, 3, P.WHITE, star[4], size=1)
    return ic


# =========================================================================== Dawnbearer (Mythic, Helios)
DAWN_PLATE = ramp('#efe4cf', n=7, at=4, lo=0.30, hi=0.995, shadow_h=320, ss=0.55, light_h=95, ls=0.3)
DAWN_ROSE = ramp('#f0a07a', n=6, at=3, lo=0.30, hi=0.97, shadow_h=340, light_h=80)


@armor_set('helios', names=['helios_crown', 'helios_heartplate', 'helios_orbit_greaves', 'helios_dawn_treads'])
def dawnbearer(piece):
    ic = Icon(set_mats(DAWN_PLATE, P.GOLD, interior=ramp('#3a2a3a', n=4, at=1), accent=DAWN_ROSE, gem=P.SUN,
                       glow=P.SUN, plate_base=4))
    if piece == 'helmet':
        A.helmet(ic, face='T')
        recolor(ic, 'O', 'T', lambda x, y: 14 <= y <= 24 and (x in (9, 22) or (y == 24 and 12 <= x <= 19)))
        # sun crown: five tall gold rays standing on the brow
        for i, x in enumerate((7, 11, 15, 20, 24)):
            h = (3, 5, 7, 5, 3)[i]
            for k in range(h):
                ic.set(x, 3 - k + (7 - h), 'T')
                if k < h - 1:
                    ic.set(x + 1, 3 - k + (7 - h), 'T', -1)
        recolor(ic, 'O', 'O', lambda x, y: True)
        rect(ic, 4, 12, 27, 12, 'T')
        for x, y in ((15, 13), (16, 13), (15, 12), (16, 12)):
            ic.set(x, y, 'G')
        ridge(ic, 15, 5, 10)
        ic.spark(16, 0, P.WHITE, P.SUN[4], size=1)
    elif piece == 'chestplate':
        A.chestplate(ic)
        ell(ic, CX, 15.5, 4.2, 4.2, 'T')
        ell(ic, CX, 15.5, 2.8, 2.8, 'G')
        for a in range(0, 360, 45):
            ra = math.radians(a)
            for r in (5.0, 6.0):
                x, y = int(round(CX + math.cos(ra) * r)), int(round(15.5 + math.sin(ra) * r))
                if ic.get(x, y) in ('O',):
                    ic.set(x, y, 'T', 1)
        ic.spark(15, 15, P.WHITE, P.SUN[5], size=1)
    elif piece == 'leggings':
        A.leggings(ic)
        # orbit rings: gold ellipses swinging around each thigh
        for cx, s in ((10.5, 1), (20.5, -1)):
            for t in range(0, 360, 6):
                a = math.radians(t)
                x = cx + math.cos(a) * 4.6
                y = 13.5 + math.sin(a) * 1.4 + s * math.cos(a) * 1.2
                xi, yi = int(round(x)), int(round(y))
                if ic.get(xi, yi) in ('O', None) and 0 <= xi < 32:
                    ic.set(xi, yi, 'T', 1 if math.sin(a) < 0 else -1)
        ic.set(5, 13, 'G')
        ic.set(26, 14, 'G')
    else:
        A.boots(ic)
        # little dawn wings flaring from the cuffs
        wing = [(6, 9), (5, 9), (4, 9), (5, 8), (4, 8), (3, 8), (4, 7), (3, 7), (2, 7), (3, 6), (2, 6), (2, 5),
                (6, 10), (5, 10), (4, 10), (6, 11), (5, 11)]
        put_pts(ic, wing, 'T')
        put_pts(ic, [(3, 7), (2, 6), (4, 8)], 'T', 2)
        recolor(ic, 'A', 'A', lambda x, y: True)
    return ic


# =========================================================================== Seraphine (Legendary, leather)
GLASS = ramp('#cfe6f4', n=6, at=3, lo=0.40, hi=0.995, shadow_h=260, light_h=190)


@armor_set('seraphine', names=['seraphine_veil', 'seraphine_bodice', 'seraphine_bell_skirt', 'seraphine_pointe_slippers'])
def seraphine(piece):
    ic = Icon(set_mats(P.PORCELAIN, P.GOLD, interior=ramp('#4a3a4a', n=4, at=1), accent=P.ROSE, gem=P.ROSE,
                       glow=P.GOLD, plate_base=4, form=1.0,
                       extra={'v': Mat(GLASS, base=3, hi=(0, 1, 1), lo=(0, 0, 1), z=-1),
                              'k': Mat(P.GOLD, base=4, model='flat', z=2)}))
    if piece == 'helmet':
        # glass veil falling to both sides, porcelain mask, gold halo above
        for y in range(9, 29):
            w = 9.5 + (y - 9) * 0.28
            for x in range(32):
                if abs(x + 0.5 - 16) <= w and y >= 9:
                    ic.set(x, y, 'v', 1 if (x + y) % 5 == 0 else 0)
        ell(ic, CX, 16.0, 7.0, 9.0, 'O')
        # closed eyes, a quiet mouth
        for x, y in ((10, 15), (11, 16), (12, 16), (13, 15)):
            put_pts(ic, [(x, y)], 'I')
        ic.set(15, 21, 'I')
        ic.set(16, 21, 'I')
        # kintsugi crack
        for x, y in ((18, 8), (18, 9), (19, 10), (19, 11), (20, 12), (20, 13), (21, 14), (19, 17), (20, 18), (21, 19)):
            ic.set(x, y, 'k')
        # halo
        for t in range(0, 360, 5):
            a = math.radians(t)
            x, y = int(round(CX + math.cos(a) * 7.5)), int(round(3.0 + math.sin(a) * 1.6))
            if ic.get(x, y) is None:
                ic.set(x, y, 'T', 1 if math.sin(a) < 0 else 0)
        ic.spark(26, 2, P.WHITE, P.GOLD[5], size=1)
    elif piece == 'chestplate':
        # corset: sweetheart neckline, narrow waist, flared hip
        for y in range(6, 29):
            if y < 9:
                h = 9.5 - abs(y - 7.5) * 0.4
            elif y < 21:
                h = 9.6 - (y - 9) * 0.32
            else:
                h = 5.8 + (y - 21) * 0.55
            rect(ic, int(math.ceil(CX - h)), y, int(math.floor(CX + h)), y, 'O')
        for x, y in ((14, 6), (15, 7), (16, 7), (17, 6), (15, 6), (16, 6)):
            ic.clear(x, y)
        ic.clear(15, 8)
        ic.clear(16, 8)
        # straps
        put_pts(ic, [(9, 3), (9, 4), (9, 5), (10, 3)], 'T')
        # gold lacing down the front
        for y in range(10, 27, 2):
            ic.set(15, y, 'k')
            ic.set(16, y + 1, 'k')
        # kintsugi cracks on the porcelain
        for x, y in ((9, 12), (10, 13), (10, 14), (11, 15), (21, 18), (22, 19), (22, 20), (23, 21), (20, 10), (21, 11)):
            ic.set(x, y, 'k')
        rect(ic, 10, 27, 21, 28, 'T')
        for x, y in ((14, 9), (17, 9), (15, 9), (16, 9), (15, 10), (16, 10)):
            ic.set(x, y, 'A')
    elif piece == 'leggings':
        # bell skirt: eight hinged panels, gold hem
        for y in range(4, 29):
            if y < 7:
                h = 6.0
            else:
                t = (y - 7) / 21.0
                h = 6.0 + 9.4 * math.sin(t * math.pi / 2) ** 0.8
            rect(ic, int(math.ceil(CX - h)), y, int(math.floor(CX + h)), y, 'O')
        for y in range(8, 27):
            for x in range(32):
                if ic.get(x, y) == 'O':
                    rel = (x + 0.5 - CX)
                    h = 6.0 + 9.4 * math.sin(((y - 7) / 21.0) * math.pi / 2) ** 0.8
                    panel = int((rel + h) / (2 * h) * 8)
                    if panel % 2:
                        ic.bump(x, y, -1)
        rect(ic, 9, 4, 22, 6, 'T')
        for x in range(32):
            for y in (27, 28):
                if ic.get(x, y) == 'O':
                    ic.set(x, y, 'T')
        for x in (5, 9, 13, 18, 22, 26):
            for y in (26,):
                if ic.get(x, y) == 'O':
                    ic.set(x, y, 'k')
        for x, y in ((14, 7), (15, 8), (16, 8), (17, 7), (13, 8), (18, 8)):
            ic.set(x, y, 'A')
    else:
        # pointe slippers: slim satin shoes on their toes, gold tips, ribbons up the ankle
        for side in (-1, 1):
            def X(x):
                return x if side < 0 else int(2 * CX - x)
            for y in range(8, 26):
                w = 2 if y < 18 else 3
                for x in range(11 - w, 12 + w):
                    ic.set(X(x), y, 'O')
            for y in (26, 27):
                for x in (10, 11, 12):
                    ic.set(X(x), y, 'k')
            for y in range(8, 20, 3):
                ic.set(X(8), y, 'A')
                ic.set(X(9), y + 1, 'A')
                ic.set(X(13), y + 1, 'A')
                ic.set(X(14), y + 2, 'A')
            for x, y in ((6, 20), (5, 21), (6, 22), (16, 20)):
                ic.set(X(x), y, 'A')
        ic.spark(15, 4, P.WHITE, P.GOLD[5], size=1)
    return ic


# =========================================================================== Worldhide (Mythic)
@armor_set('worldhide')
def worldhide(piece):
    ic = Icon(set_mats(P.VOID, P.AMETHYST, interior=ramp('#0a0610', n=4, at=1), accent=P.ENAMEL, gem=P.AMETHYST,
                       glow=P.RIFT, plate_base=2, form=1.4))
    ic.mats['T'] = Mat(P.AMETHYST, base=2, hi=(0, 1, 2), lo=(0, 1, 1), z=1)
    ic.mats['R'] = Mat(P.RIFT, base=3, model='flat', z=2)
    if piece == 'helmet':
        A.helmet(ic, face='closed')
        # crown of teeth along the dome
        for i, x in enumerate(range(6, 26, 3)):
            h = 3 if i % 2 else 4
            for k in range(h):
                ic.set(x, 6 - k + (4 - h) * 0 - (1 if 9 <= x <= 22 else -1) - 1, 'A', 1 if k == h - 1 else 0)
                if k < h - 2:
                    ic.set(x + 1, 5 - k - (1 if 9 <= x <= 22 else -1), 'A', -1)
        recolor(ic, 'I', 'R', lambda x, y: y == 17)
        for x in (10, 11, 20, 21):
            ic.set(x, 17, 'R', 2)
        ridge(ic, 15, 8, 12)
        ic.spark(26, 3, P.WHITE, P.RIFT[3], size=1)
    elif piece == 'chestplate':
        A.chestplate(ic)
        # rib segments + glowing rift down the sternum
        for y in range(11, 24, 3):
            for x in range(32):
                if ic.get(x, y) == 'O':
                    ic.bump(x, y, -1)
                if ic.get(x, y - 1) == 'O':
                    ic.bump(x, y - 1, 1)
        for y, x in ((10, 15), (11, 16), (12, 16), (13, 15), (14, 15), (15, 16), (16, 16), (17, 15), (18, 15),
                     (19, 16), (20, 16), (21, 15), (22, 15)):
            ic.set(x, y, 'R', 1 if y < 16 else 0)
        for x, y in ((11, 7), (13, 9), (18, 9), (20, 7)):
            ic.set(x, y, 'A', 1)
    elif piece == 'leggings':
        A.leggings(ic, knees=False)
        for y in range(9, 27, 3):
            for x in range(32):
                if ic.get(x, y) == 'O':
                    ic.set(x, y, 'T', -1)
        for x, y in ((10, 15), (21, 15), (10, 21), (21, 21)):
            ic.set(x, y, 'R', 1)
    else:
        A.boots(ic, toe=False)
        for side in (-1, 1):
            for x, y in ((1, 26), (1, 27), (2, 25), (3, 27), (4, 26)):
                ic.set(x if side < 0 else 31 - x, y, 'A', 1)
        for y in range(12, 22, 3):
            for x in range(32):
                if ic.get(x, y) == 'O':
                    ic.set(x, y, 'T', -1)
    return ic


# =========================================================================== Aetherion (Mythic)
AE_SILVER = ramp('#c8c4dc', n=7, at=4, lo=0.24, hi=0.99, shadow_h=285, light_h=250)


@armor_set('aetherion')
def aetherion(piece):
    ic = Icon(set_mats(AE_SILVER, P.LILAC, interior=ramp('#1a1630', n=4, at=1), accent=P.LILAC, gem=P.AETHER,
                       glow=P.AETHER_GLASS, plate_base=4))
    wing = [(3, 10), (2, 9), (1, 8), (2, 8), (3, 9), (1, 7), (2, 7), (3, 8), (2, 6), (3, 7), (4, 9)]
    if piece == 'helmet':
        A.helmet(ic, face='T')
        put_pts(ic, wing, 'A')
        for x, y in ((15, 12), (16, 12), (15, 13), (16, 13), (15, 11), (16, 11)):
            ic.set(x, y, 'G')
        ridge(ic, 15, 4, 10)
        ic.spark(26, 3, P.WHITE, P.AETHER[5], size=1)
    elif piece == 'chestplate':
        A.chestplate(ic)
        ell(ic, CX, 15.0, 3.0, 3.6, 'G')
        for x, y in ((15, 19), (16, 20), (15, 21), (12, 15), (11, 14), (19, 15), (20, 14), (15, 10), (16, 10)):
            ic.set(x, y, 'R', 1)
        ridge(ic, 15, 20, 23)
    elif piece == 'leggings':
        A.leggings(ic)
        for cx in (10, 20):
            ic.set(cx, 18, 'G')
            ic.set(cx + 1, 18, 'G')
    else:
        A.boots(ic)
        put_pts(ic, [(6, 9), (5, 8), (4, 8), (5, 7), (3, 7), (4, 6), (6, 10), (5, 10)], 'A')
        for x, y in ((11, 15), (20, 15)):
            ic.set(x, y, 'G')
    return ic


# =========================================================================== Ironhide (Epic)
@armor_set('ironhide')
def ironhide(piece):
    ic = Icon(set_mats(P.IRON, P.IRON_DARK, accent=P.BRONZE, gem=P.CRIMSON, glow=P.CRIMSON,
                       extra={'L': Mat(P.LEATHER, base=2, hi=(0, 1, 1), lo=(0, 1, 1), z=2),
                              'b': Mat(P.BRONZE, base=4, model='flat', z=3)}))
    if piece == 'helmet':
        A.helmet(ic, face='closed', cheeks=True)
        for x, y in ((6, 8), (25, 8), (5, 14), (26, 14), (8, 22), (23, 22), (15, 3), (16, 3)):
            ic.set(x, y, 'b')
        ridge(ic, 15, 3, 12)
    elif piece == 'chestplate':
        A.chestplate(ic)
        for i in range(9):
            for x, y in ((9 + i, 10 + i * 2 // 1 - i), ):
                pass
        for t in range(14):
            x1 = 8 + t
            y1 = 9 + int(t * 1.1)
            for x in (x1, int(2 * CX - x1)):
                if ic.get(x, y1) in ('O', 'T'):
                    ic.set(x, y1, 'L')
        for x, y in ((9, 13), (22, 13), (11, 20), (20, 20), (15, 22), (16, 22)):
            ic.set(x, y, 'b')
    elif piece == 'leggings':
        A.leggings(ic)
        for x, y in ((7, 10), (24, 10), (8, 24), (23, 24), (12, 9), (19, 9)):
            ic.set(x, y, 'b')
    else:
        A.boots(ic)
        for x, y in ((9, 13), (22, 13), (9, 18), (22, 18)):
            ic.set(x, y, 'b')
    return ic


# =========================================================================== Squid's Boot (Legendary)
@lambda f: REG.__setitem__('squids_boot', f)
def squids_boot():
    skin = ramp('#6a4fb0', n=7, at=3, lo=0.16, hi=0.94, shadow_h=280, light_h=320)
    ic = Icon(set_mats(skin, ramp('#3a2a6a', n=6, at=2), accent=ramp('#e7c6f2', n=6, at=3, lo=0.4), gem=P.AETHER))
    for y in range(6, 24):
        for x in range(12, 22):
            ic.set(x, y, 'O')
    for y in range(22, 29):
        x0 = 5 if y >= 24 else 8
        for x in range(x0, 22):
            ic.set(x, y, 'O')
    for x in range(5, 22):
        ic.set(x, 29, 'T')
    # suction cups down the shaft and along the foot
    for x, y in ((17, 9), (17, 13), (17, 17), (13, 25), (9, 26), (17, 21)):
        ic.set(x, y, 'A')
        ic.set(x + 1, y, 'A', -1)
    # tentacles curling over the cuff
    for pts in (((12, 5), (11, 4), (10, 3), (10, 2), (11, 1)), ((21, 5), (22, 4), (23, 4), (24, 5), (24, 6)),
                ((16, 5), (16, 4), (17, 3), (18, 3))):
        for x, y in pts:
            ic.set(x, y, 'O', 1)
    ic.set(19, 12, 'G')
    return ic


# =========================================================================== sealed dungeon relic armour
VIOLET_GLOW = ramp('#a35ae8', n=6, at=2, lo=0.30, hi=0.99, shadow_h=290, light_h=320, c_hi=0.3)
AMBER_GLOW = ramp('#ffa53a', n=6, at=2, lo=0.40, hi=0.995, shadow_h=30, light_h=95, ls=0.6, c_hi=0.3)
RELIC_STONE = ramp('#6a6272', n=7, at=3, lo=0.15, hi=0.86, shadow_h=290, light_h=60, ls=0.2)


def relic_piece(tier, piece):
    glow = VIOLET_GLOW if tier == 2 else AMBER_GLOW
    ic = Icon(set_mats(RELIC_STONE, P.IRON_DARK, accent=P.IRON_DARK, gem=glow, glow=glow,
                       extra={'c': Mat(P.IRON, base=3, hi=(0, 1, 1), lo=(0, 1, 1), z=2)}))
    getattr(A, piece)(ic)
    recolor(ic, 'A', 'T', lambda x, y: True)
    # chains across the piece and a glowing seal in the middle
    for x in range(32):
        y = int(round(9 + x * 0.55))
        if ic.get(x, y) is not None and x % 2 == 0:
            ic.set(x, y, 'c', 1 if x % 4 == 0 else 0)
    cy = {'helmet': 10, 'chestplate': 16, 'leggings': 12, 'boots': 16}[piece]
    ell(ic, CX, cy, 2.6, 2.6, 'G')
    ic.spark(int(CX) + 4, cy - 4, P.WHITE, glow[4], size=1)
    return ic


for _t in (2, 3):
    for _p in PIECES:
        REG[f'dungeon_t{_t}_relic_{_p}'] = (lambda tt=_t, pp=_p: relic_piece(tt, pp))
