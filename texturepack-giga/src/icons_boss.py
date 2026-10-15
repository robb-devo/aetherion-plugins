"""P0 hero weapons: boss drops and showcase uniques (all 32x32, diagonal, light top-left)."""
import math

from forge import Icon, Mat, ramp, mix, hx
import palette as P
from kit import (AXIS, at, uv_set, uv_bump, uv_get, uv_fill, uv_shade, bands, blade, shaped_blade, haft, box,
                 disc_uv, ring_uv, wrap, pommel, gem_plus, gem_diamond)

REG = {}


def icon(name):
    def deco(fn):
        REG[name] = fn
        return fn
    return deco


def tip_glint(ic, x, y, c):
    ic.fix[(x, y)] = c


# --------------------------------------------------------------------------- Worldbite (Mythic)
@icon('worldbite')
def worldbite():
    mats = {
        'V': Mat(P.VOID, base=2, model='flat', z=0),
        'E': Mat(P.ENAMEL, base=4, model='flat', z=1),
        'R': Mat(P.RIFT, base=2, model='flat', z=0),
        'G': Mat(P.IRON_DARK, base=2, hi=(0, 2, 3), lo=(0, 1, 1), z=2),
        'A': Mat(P.AMETHYST, base=4, model='gem', z=3),
        'W': Mat(P.LEATHER_DARK, base=2, model='flat', z=1),
        'P': Mat(P.AMETHYST, base=2, model='sphere', z=1),
    }
    ic = Icon(mats)
    blade(ic, 'V', -5, 27, 3, 3, k=2)
    bands(ic, 'V', {-3: 3, -2: 2, -1: 1, 0: 0, 1: 0, 2: -1, 3: -1})
    path = [(31, v) for v in range(-5, 3)] + [(30, v) for v in range(3, 9)] + [(31, v) for v in range(9, 15)] + \
           [(32, v) for v in range(15, 19)] + [(31, v) for v in range(19, 22)]
    for u, v in path:
        uv_set(ic, u, v, 'R', 1 if v < 3 else 0)
        uv_set(ic, u, v - 1, 'R', 1 if v < 3 else 0)
    for v in range(-5, 2):
        uv_set(ic, 30, v, 'R', 0)
        uv_set(ic, 32, v, 'R', -1)
    for va in (19, 11, 3):
        for u, (lo, hi), sh in ((27, (va - 3, va + 3), -1), (26, (va - 1, va + 3), 0), (25, (va + 1, va + 3), 1),
                                (24, (va + 3, va + 3), 2)):
            for v in range(lo, hi + 1):
                uv_set(ic, u, v, 'E', sh)
    for u in range(25, 38):
        for v in (-7, -8):
            uv_set(ic, u, v, 'G')
    for p in ((8, 15), (8, 16), (8, 14), (9, 13), (10, 12), (16, 23), (16, 22), (17, 22), (18, 21), (19, 20)):
        ic.set(*p, 'G')
    for p in ((12, 18), (11, 19), (12, 19), (13, 19), (12, 20)):
        ic.set(*p, 'A')
    haft(ic, 'W', -16, -9)
    wrap(ic, 'W')
    ic.disc(5.6, 25.4, 1.9, 'P')
    tip_glint(ic, 29, 2, P.glint(P.LILAC[6], 0.4))
    ic.spark(25, 4, P.glint(P.RIFT[5], 0.5), P.RIFT[3], size=1)
    return ic


# --------------------------------------------------------------------------- Solstice (Mythic, Helios)
@icon('helios_solstice')
def helios_solstice():
    mats = {
        'B': Mat(P.SUNSTEEL, base=4, model='flat', z=0),
        'C': Mat(P.MAGMA, base=2, model='flat', z=0),
        'S': Mat(P.SUN, base=5, model='flat', z=1),
        'O': Mat(P.GOLD, base=3, hi=(0, 1, 2), lo=(0, 1, 2), z=2),
        'Y': Mat(P.GOLD, base=4, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'W': Mat(P.LEATHER, base=2, model='flat', z=1),
        'P': Mat(P.GOLD, base=4, model='gem', z=1),
    }
    ic = Icon(mats)
    blade(ic, 'B', -4, 27, 3, 3, k=2)
    bands(ic, 'B', {-3: 2, -2: 1, -1: 0, 0: 0, 1: -1, 2: -2, 3: -3})
    for v in range(-4, 19):
        uv_set(ic, 31, v, 'C', 2 if v < 4 else (1 if v < 11 else 0))
    for v in range(-4, 6):
        uv_set(ic, 30, v, 'C', 1)
        uv_set(ic, 32, v, 'C', 0)
    cu, cv = 31, -8
    disc_uv(ic, 'S', cu, cv, 4.3)
    uv_shade(ic, 'S', lambda u, v: 1 if (u - cu) + (v - cv) < -2 else (-1 if (u - cu) - (v - cv) > 4 else 0))
    ring_uv(ic, 'O', cu, cv, 4.3, 6.1)
    for ang in (0, 45, 135, 180, 225, 315):
        a = math.radians(ang)
        for r in (6.6, 7.4, 8.2, 9.0):
            if ang % 90 and r > 8.3:
                continue
            uv_set(ic, int(round(cu + math.cos(a) * r)), int(round(cv + math.sin(a) * r)), 'Y')
            uu, vv = cu + math.cos(a) * r, cv + math.sin(a) * r
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    if r < 7.5 and abs(du) + abs(dv) == 1:
                        uv_set(ic, int(round(uu)) + du, int(round(vv)) + dv, 'Y')
    haft(ic, 'W', -20, -15)
    wrap(ic, 'W', period=3, dark=(0,))
    gem_diamond(ic, 'P', 4, 27, 1)
    ic.set(3, 28, 'P')
    tip_glint(ic, 29, 2, P.glint(P.SUN[6], 0.6))
    ic.spark(26, 3, P.WHITE, P.SUN[5], size=2)
    ic.spark(5, 12, P.glint(P.SUN[6], 0.5), P.SUN[4], size=1)
    return ic


# --------------------------------------------------------------------------- Severance (Legendary, Seraphine)
@icon('seraphine_needle')
def seraphine_needle():
    mats = {
        'N': Mat(P.PORCELAIN, base=4, model='flat', z=0),
        'K': Mat(P.GOLD, base=4, model='flat', z=0),
        'H': Mat(P.GOLD, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'R': Mat(P.GOLD, base=4, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'G': Mat(P.PORCELAIN, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
        'b': Mat(P.GOLD, base=4, model='flat', z=1),
        'p': Mat(P.PORCELAIN, base=5, model='sphere', z=2),
        'T': Mat(P.ROSE, base=3, hi=(0, 1, 1), lo=(0, 1, 1), z=1),
    }
    ic = Icon(mats)
    ring_uv(ic, 'R', 31, 4, 2.7, 3.9)
    blade(ic, 'N', -6, 28, 1, 1, k=2)
    bands(ic, 'N', {-1: 2, 0: 0, 1: -2})
    for u, v in ((30, 10), (31, 11), (31, 12), (32, 13), (31, 14), (30, 15), (30, 16), (31, 17), (31, 20), (32, 21), (31, 22)):
        uv_set(ic, u, v, 'K', 0)
    for u in range(26, 37):
        for v in (-7, -8):
            uv_set(ic, u, v, 'H')
    ring_uv(ic, 'H', 31, -13, 4.6, 6.0, arc=(275, 85))
    haft(ic, 'G', -18, -9)
    uv_fill(ic, 'b', lambda u, v: 30 <= u <= 32 and v in (-11, -12, -15, -16), only=None)
    for p in ((9, 16), (16, 23)):
        ic.set(*p, 'p')
    ic.disc(5.0, 26.0, 1.6, 'p')
    for p in ((3, 28), (3, 29), (2, 30), (5, 28), (6, 29), (6, 30)):
        ic.set(*p, 'T')
    tip_glint(ic, 30, 1, P.glint(P.GOLD[6], 0.7))
    ic.spark(25, 3, P.WHITE, P.GOLD[5], size=1)
    return ic


# --------------------------------------------------------------------------- Aetherblade (Mythic)
@icon('aetherblade')
def aetherblade():
    crystal = ramp('#b9a2ec', n=7, at=4, lo=0.24, hi=0.99, shadow_h=285, light_h=300, c_hi=0.5)
    mats = {
        'B': Mat(crystal, base=3, model='flat', z=0),
        'L': Mat(P.AMETHYST, base=3, model='flat', z=0),
        'C': Mat(P.AETHER_GLASS, base=5, model='flat', z=0),
        'S': Mat(ramp('#d4cce8', n=7, at=4, lo=0.24, hi=0.99, shadow_h=285, light_h=250), base=3, hi=(0, 1, 2),
                 lo=(0, 1, 2), z=2),
        'A': Mat(P.AETHER, base=4, model='gem', z=3),
        'W': Mat(P.AMETHYST, base=1, model='flat', z=1),
        'P': Mat(P.AETHER, base=4, model='gem', z=1),
    }
    ic = Icon(mats)
    blade(ic, 'B', -4, 27, 3, 3, k=2)
    bands(ic, 'B', {-3: 3, -2: 2, -1: 1, 0: 1, 1: 0, 2: -1, 3: -2})
    uv_fill(ic, 'L', lambda u, v: u >= 33 and v >= -4, only='B')
    bands(ic, 'L', {2: 0, 3: -1})
    for v in range(-2, 22):
        uv_set(ic, 31, v, 'C', 0 if v % 7 else -1)
    # crystal facets: chevrons across the blade
    uv_shade(ic, 'B', lambda u, v: -1 if (v - abs(u - 31) * 2) % 8 == 0 else 0)
    def wing(uu, v):
        return 21 <= uu <= 29 and v >= -9 + (29 - uu) * 0.30 and v <= -5 + (29 - uu) * 1.05
    uv_fill(ic, 'S', lambda u, v: wing(u, v) or wing(62 - u, v))
    for u in range(27, 36):
        for v in (-6, -7):
            uv_set(ic, u, v, 'S')
    gem_plus(ic, 'A', 12, 19)
    haft(ic, 'W', -16, -8)
    wrap(ic, 'W', period=4, dark=(0, 1))
    gem_diamond(ic, 'P', 5, 26, 2)
    tip_glint(ic, 29, 2, P.WHITE)
    ic.spark(25, 5, P.WHITE, P.AETHER[5], size=2)
    ic.spark(20, 3, P.glint(P.LILAC[6], 0.4), P.LILAC[4], size=1)
    return ic


# --------------------------------------------------------------------------- Stormcaller Maul (Legendary)
@icon('stormcaller_maul')
def stormcaller_maul():
    mats = {
        'H': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.0, z=1),
        'C': Mat(P.COPPER, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'R': Mat(P.BOLT, base=3, model='flat', z=3),
        'T': Mat(P.WOOD_DARK, base=2, model='flat', z=0),
        'W': Mat(P.LEATHER, base=2, model='flat', z=0),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=1),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 12)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -11)
    wrap(ic, 'W', period=3, dark=(0,))
    box(ic, 'M', 29, 33, -11, -9)
    box(ic, 'M', 29, 33, 9, 11)
    box(ic, 'H', 23, 39, 12, 22, chamfer=2)
    box(ic, 'C', 23, 25, 12, 22, chamfer=1)
    box(ic, 'C', 37, 39, 12, 22, chamfer=1)
    # lightning rune across the face (screen space)
    for p, d in (((26, 3), 1), ((25, 4), 1), ((24, 5), 0), ((25, 5), 0), ((26, 5), 0), ((25, 6), 0), ((24, 7), -1),
                 ((23, 8), -1), ((24, 8), -1)):
        ic.set(*p, 'R', d)
    ic.spark(29, 1, P.WHITE, P.BOLT[4], size=1)
    ic.fix[(30, 4)] = P.BOLT[4]
    ic.fix[(31, 5)] = P.BOLT[3]
    return ic


# --------------------------------------------------------------------------- Staff of Technical Difficulties (Legendary)
@icon('staff_of_technical_difficulties')
def staff_of_technical_difficulties():
    glass = ramp('#3a2a5a', n=7, at=2, lo=0.14, hi=0.85, shadow_h=290, light_h=300)
    mats = {
        'T': Mat(P.WOOD, base=3, model='flat', z=0),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'O': Mat(glass, base=3, model='sphere', z=1),
        'c': Mat(P.GLITCH_C, base=3, model='flat', z=1),
        'm': Mat(P.GLITCH_M, base=3, model='flat', z=1),
        'w': Mat(P.SILVER, base=6, model='flat', z=1),
    }
    ic = Icon(mats)
    haft(ic, 'T', -24, 8)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    uv_shade(ic, 'T', lambda u, v: -1 if v % 6 == 0 else 0)
    disc_uv(ic, 'O', 31, 17, 5.4)
    box(ic, 'M', 28, 34, 7, 10)
    for u, v in ((26, 10), (26, 11), (25, 12), (25, 13), (25, 14), (36, 10), (36, 11), (37, 12), (37, 13), (37, 14),
                 (27, 10), (35, 10)):
        uv_set(ic, u, v, 'M')
    # glitch: screen rows of the orb shifted into cyan / magenta, with displaced slivers outside
    for x in range(21, 26):
        if ic.get(x, 6) == 'O':
            ic.set(x, 6, 'c', 0)
    for x in range(23, 29):
        if ic.get(x, 9) == 'O':
            ic.set(x, 9, 'm', 0)
    ic.set(29, 9, 'm', -1)
    ic.set(30, 9, 'm', -1)
    ic.set(19, 6, 'c', -1)
    ic.set(20, 6, 'c', 0)
    for p in ((24, 7), (22, 8), (26, 8)):
        if ic.get(*p) == 'O':
            ic.set(*p, 'w', 0)
    ic.fx.append((28, 2, P.GLITCH_M[3]))
    ic.fx.append((29, 2, P.GLITCH_C[3]))
    ic.fx.append((18, 10, P.GLITCH_C[2]))
    return ic


# --------------------------------------------------------------------------- Hunter Crossbow (Uncommon) + frames
def _crossbow(frame):
    green = ramp('#3f6e3a', n=6, at=2, lo=0.16, hi=0.80, shadow_h=200, light_h=100)
    mats = {
        'S': Mat(P.WOOD, base=3, model='flat', z=0),
        'G': Mat(green, base=2, model='flat', z=1),
        'L': Mat(P.IRON_DARK, base=3, hi=(0, 2, 3), lo=(0, 1, 1), z=2),
        'N': Mat(P.STEEL, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'q': Mat(P.SILK, base=4, model='flat', z=1),
        'a': Mat(P.WOOD, base=5, model='flat', z=3),
        'h': Mat(P.STEEL, base=5, model='flat', z=3),
        'r': Mat(P.CLOTH_RED, base=3, model='flat', z=3),
        'k': Mat(P.CLOTH_RED, base=4, model='flat', z=3),
    }
    ic = Icon(mats)
    # stock runs past the prod into a steel nose: that nose is what keeps it from reading as a pick
    haft(ic, 'S', -22, 21)
    bands(ic, 'S', {-1: 1, 0: 0, 1: -1})
    box(ic, 'S', 29, 33, -23, -17)
    haft(ic, 'G', -14, -6)
    wrap(ic, 'G', period=3, dark=(0,))
    box(ic, 'N', 30, 32, 19, 22)
    box(ic, 'N', 30, 32, 8, 10)
    for u, v in ((33, -4), (34, -5), (35, -5), (35, -6)):
        uv_set(ic, u, v, 'N')

    pc, pr = -7.0, 17.0

    def prod(u, v):
        d = math.hypot(u - 31, v - pc)
        ang = math.degrees(math.atan2(v - pc, u - 31))
        if not (57 <= ang <= 123):
            return False
        th = 1.6 if abs(ang - 90) < 14 else 0.9
        return abs(d - pr) <= th
    uv_fill(ic, 'L', prod)
    tips = [(31 + pr * math.cos(math.radians(a)), pc + pr * math.sin(math.radians(a))) for a in (122, 58)]
    pull = {'standby': 0, 'pulling_0': 2, 'pulling_1': 4, 'pulling_2': 6, 'arrow': 6, 'firework': 6}[frame]
    nock_v = tips[0][1] - pull if pull else None

    def scr(u, v):
        return int(round((u + v) / 2)), int(round((u - v) / 2))
    for eu, ev in tips:
        x0, y0 = scr(eu, ev)
        if nock_v is None:
            x1, y1 = scr(62 - eu, ev)
        else:
            x1, y1 = scr(31, nock_v)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err, x, y = dx + dy, x0, y0
        while True:
            if ic.get(x, y) in (None, 'S', 'G'):
                ic.set(x, y, 'q')
            if x == x1 and y == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x += sx
            if e2 <= dx:
                err += dx
                y += sy
    if frame == 'arrow':
        nv = int(round(nock_v))
        uv_fill(ic, 'a', lambda u, v: u == 29 and nv - 1 <= v <= 23)
        for u, v in ((29, 24), (30, 25), (28, 25), (29, 26), (30, 23)):
            uv_set(ic, u, v, 'h')
        for u, v in ((28, nv), (27, nv - 1), (28, nv - 2), (27, nv + 1)):
            uv_set(ic, u, v, 'k')
    if frame == 'firework':
        nv = int(round(nock_v))
        uv_fill(ic, 'r', lambda u, v: 28 <= u <= 29 and nv <= v <= 22)
        uv_shade(ic, 'r', lambda u, v: 2 if v % 4 == 0 else 0)
        for u, v in ((29, 23), (28, 23), (28, 24), (29, 25)):
            uv_set(ic, u, v, 'q')
    return ic


for _f in ('standby', 'pulling_0', 'pulling_1', 'pulling_2', 'arrow', 'firework'):
    REG['hunter_crossbow' + ('' if _f == 'standby' else '_' + _f)] = (lambda f=_f: _crossbow(f))
# The Hunter Crossbow item is a BOW (CMD 2205): it gets one static icon, shown loaded.
REG['hunter_crossbow_loaded'] = lambda: _crossbow('arrow')


# --------------------------------------------------------------------------- Cleavers
def _cleaver(steel, edge, haft_r, grip_r, rot=False):
    mats = {
        'B': Mat(steel, base=3, model='flat', z=1),
        'E': Mat(edge, base=3 if not rot else 2, model='flat', z=1),
        'T': Mat(haft_r, base=2, model='flat', z=0),
        'W': Mat(grip_r, base=3 if rot else 2, model='flat', z=0),
        'M': Mat(P.IRON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'x': Mat(P.RUST, base=3, model='flat', z=1),
    }
    ic = Icon(mats)
    haft(ic, 'T', -23, 18)
    bands(ic, 'T', {-1: 1, 0: 0, 1: -1})
    haft(ic, 'W', -23, -10)
    wrap(ic, 'W', period=3, dark=(0,))

    def body(u, v):
        if not (19 <= u <= 31 and 2 <= v <= 21):
            return False
        if u - 19 + 21 - v < 3:          # rounded top-front corner
            return False
        if v < 2 + (31 - u) * 0.35:        # heel slopes into the haft
            return False
        return True
    uv_fill(ic, 'B', body)
    uv_shade(ic, 'B', lambda u, v: (2 if u <= 21 else (1 if u <= 24 else (0 if u <= 28 else -1))))
    uv_fill(ic, 'E', lambda u, v: u <= 20, only='B')
    uv_shade(ic, 'E', lambda u, v: 1 if v % 5 in (0, 1) else 0)
    box(ic, 'M', 29, 33, 18, 21)
    for u, v in ((29, 16), (29, 10), (29, 4)):
        uv_set(ic, u, v, 'M')
    return ic


@icon('ashen_cleaver')
def ashen_cleaver():
    ash = ramp('#4e4a56', n=7, at=3, lo=0.13, hi=0.86, shadow_h=295, light_h=40, ls=0.25)
    ic = _cleaver(ash, P.MAGMA, P.WOOD_DARK, P.LEATHER_DARK)
    ic.mats['E'] = Mat(P.MAGMA, base=2, model='flat', z=1)
    # ember cracks running in from the edge, a few ash flecks on the flat
    for u, v in ((21, 15), (22, 15), (23, 16), (21, 8), (22, 8), (23, 7), (24, 7), (21, 19), (22, 18)):
        uv_set(ic, u, v, 'E', 0)
    for u, v in ((26, 13), (27, 11), (25, 17), (28, 16), (26, 6)):
        uv_bump(ic, u, v, 3)
    for p, c in (((8, 9), P.MAGMA[4]), ((6, 12), P.MAGMA[3]), ((10, 6), P.MAGMA[5]), ((5, 9), P.EMBER[3]),
                 ((12, 3), P.EMBER[4])):
        ic.fx.append(p + (c,))
    return ic


@icon('rotten_cleaver')
def rotten_cleaver():
    rotsteel = ramp('#6c7350', n=7, at=3, lo=0.15, hi=0.86, shadow_h=320, ss=0.4, light_h=100)
    linen = ramp('#b3a37c', n=6, at=3, lo=0.22, hi=0.92, shadow_h=40, ss=0.4)
    ic = _cleaver(rotsteel, P.ROT, P.WOOD_DARK, linen, rot=True)
    ic.mats['W'] = Mat(linen, base=2, model='flat', z=0)
    for u, v in ((25, 12), (26, 13), (24, 13), (25, 14), (27, 6), (28, 7), (23, 18), (24, 19), (28, 9), (26, 19)):
        uv_set(ic, u, v, 'x', 0)
    for u, v in ((19, 12), (20, 13), (19, 14), (20, 11), (21, 12)):
        if uv_get(ic, u, v):
            ic.clear((u + v) // 2, (u - v) // 2)
    for p in ((7, 6), (5, 8), (9, 4)):
        ic.fx.append(p + (P.CHITIN[0],))
    return ic


# --------------------------------------------------------------------------- Webweave Fang (Uncommon)
@icon('webweave_fang')
def webweave_fang():
    mats = {
        'F': Mat(P.BONE, base=4, model='flat', z=0),
        'T': Mat(P.CHITIN, base=2, model='flat', z=0),
        'V': Mat(P.POISON, base=4, model='flat', z=1),
        'S': Mat(P.SILK, base=3, model='flat', z=1),
        'C': Mat(P.CHITIN, base=2, hi=(0, 2, 3), lo=(0, 1, 1), z=2),
        'P': Mat(P.CRIMSON, base=3, model='gem', z=2),
    }
    ic = Icon(mats)

    def half(v):
        t = (v + 4) / 26.0
        w = 2.6 * (1 - t) ** 0.8 + 0.2
        bend = 2.2 * t * t
        return w + bend, w - bend
    shaped_blade(ic, 'F', -4, 22, half)
    uv_shade(ic, 'F', lambda u, v: 2 if u <= 29 else (1 if u == 30 else (0 if u <= 32 else -1)))
    uv_fill(ic, 'T', lambda u, v: v >= 16, only='F')
    uv_shade(ic, 'T', lambda u, v: 1 if u <= 29 else 0)
    haft(ic, 'S', -16, -8)
    uv_shade(ic, 'S', lambda u, v: (1 if (u + v) % 4 == 0 else (-1 if (u - v) % 4 == 0 else 0)))
    for u, v in ((26, -6), (27, -5), (28, -6), (29, -6), (33, -6), (34, -6), (35, -5), (36, -6), (30, -6), (32, -6),
                 (31, -6), (31, -7), (30, -7), (32, -7)):
        uv_set(ic, u, v, 'C')
    pommel(ic, 'P', -19, r=1.5)
    ic.fx.append((25, 3, P.POISON[5]))
    ic.fx.append((25, 4, P.POISON[4]))
    ic.fx.append((24, 6, P.POISON[3]))
    for p in ((1, 27), (2, 28), (1, 29), (3, 27)):
        ic.fx.append(p + (P.glint(P.SILK[3], 0.0),))
    return ic


# --------------------------------------------------------------------------- Warped Blade (Epic)
@icon('warped_blade')
def warped_blade():
    steel = ramp('#5b4a78', n=7, at=3, lo=0.15, hi=0.90, shadow_h=290, light_h=330)
    mats = {
        'B': Mat(steel, base=3, model='flat', z=0),
        'E': Mat(P.CRIMSON, base=4, model='flat', z=0),
        'G': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'A': Mat(P.CRIMSON, base=4, model='gem', z=3),
        'W': Mat(P.LEATHER_DARK, base=2, model='flat', z=1),
        'P': Mat(P.CRIMSON, base=3, model='sphere', z=1),
    }
    ic = Icon(mats)

    def half(v):
        taper = max(0.0, min(1.0, (27 - v) / 7.0))
        wave = math.sin(v * 0.62)
        return (2.6 + 0.9 * wave) * taper + 0.3, (2.6 - 0.9 * wave) * taper + 0.3
    shaped_blade(ic, 'B', -5, 27, half)
    uv_shade(ic, 'B', lambda u, v: 2 if u <= 29 else (1 if u == 30 else (0 if u <= 32 else -1)))
    for y in range(32):
        for x in range(32):
            if ic.key[y][x] != 'B':
                continue
            u, v = x + y, x - y
            if uv_get(ic, u - 1, v - 1) is None and uv_get(ic, u - 2, v) is None:
                ic.set(x, y, 'E', 1)
    for v in range(-4, 16):
        uv_set(ic, 31, v, 'E', -1)
    for u in range(25, 38):
        for v in (-7, -8):
            uv_set(ic, u, v, 'G')
    for u, v in ((24, -6), (23, -5), (38, -9), (39, -10), (24, -9), (38, -6)):
        uv_set(ic, u, v, 'G')
    gem_plus(ic, 'A', 12, 19)
    haft(ic, 'W', -17, -9)
    wrap(ic, 'W')
    pommel(ic, 'P', -20, r=1.7)
    tip_glint(ic, 29, 2, P.glint(P.CRIMSON[6], 0.5))
    return ic


# --------------------------------------------------------------------------- Void Stick (Mythic)
@icon('aetherion_void_stick')
def aetherion_void_stick():
    mats = {
        'T': Mat(P.VOID, base=3, model='flat', z=0),
        'O': Mat(P.RIFT, base=2, model='sphere', z=1),
        'C': Mat(P.AMETHYST, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'R': Mat(P.LILAC, base=4, model='flat', z=3),
        'b': Mat(P.LILAC, base=4, model='flat', z=1),
    }
    ic = Icon(mats)
    haft(ic, 'T', -24, 11, w=1)
    bands(ic, 'T', {-1: 2, 0: 0, 1: -1})
    uv_fill(ic, 'b', lambda u, v: 30 <= u <= 32 and v % 7 == 0 and v < 8, only='T')
    disc_uv(ic, 'O', 31, 17, 4.6)
    for u, v in ((26, 12), (25, 13), (25, 14), (36, 12), (37, 13), (37, 14), (28, 11), (34, 11), (27, 11), (35, 11)):
        uv_set(ic, u, v, 'C')
    ring_uv(ic, 'R', 31, 17, 6.0, 6.9, arc=(200, 340))
    ic.noline.add('R')
    ic.spark(27, 3, P.WHITE, P.RIFT[4], size=1)
    ic.fx.append((18, 5, P.LILAC[5]))
    ic.fx.append((30, 11, P.LILAC[4]))
    return ic
