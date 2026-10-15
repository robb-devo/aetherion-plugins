"""P2: charms, cores and boss trinkets.

The 18 skill charms + shiny / forge / estate / void vacuum come from the run1 32px pack (Robb liked
them): copper ring = tier 1, silver with points = tier 2, gold with points + glint = tier 3.
Two motifs are repainted so every skill reads at hotbar size: Farming gets a wheat ear (was green
bars), Wayfinder / Utility gets a compass rose (was a dot ring).
"""
import os
import math
from collections import Counter

from PIL import Image

from forge import Icon, Mat, ramp, mix, hx
import palette as P
from kit import uv_set, uv_fill, uv_shade, haft, disc_uv, ring_uv, box
from armor import rect, ell, put_pts, CX

REG = {}
HERE = os.path.dirname(os.path.abspath(__file__))
RUN1 = os.path.join(HERE, 'run1')


class Raw:
    """Wraps a finished PNG so it can sit in the registry next to forge icons."""

    def __init__(self, im):
        self.im = im

    def render(self):
        return self.im


def run1(name):
    return Image.open(os.path.join(RUN1, name + '.png')).convert('RGBA')


WELL_C = (15.75, 16.5)


def repaint_well(im, motif):
    """Clear the medallion's inner well and paint a motif: motif(px, well, dark) draws pixels."""
    px = im.load()
    inner = [(x, y) for y in range(32) for x in range(32)
             if (x + 0.5 - WELL_C[0] - 0.5) ** 2 + (y + 0.5 - WELL_C[1] - 0.5) ** 2 <= 5.0 ** 2]
    well = Counter(px[x, y][:3] for x, y in inner).most_common(1)[0][0]
    dark = mix(well, (0, 0, 0), 0.35)
    for x, y in inner:
        px[x, y] = (dark if (x + y) > 34 else well) + (255,)
    motif(px, well, dark)
    return im


def wheat(px, well, dark):
    grain_hi, grain, grain_lo = hx('#fff0a8'), hx('#e8b83c'), hx('#a8741c')
    stem, stem_lo = hx('#8ccf4a'), hx('#4f8a2a')
    rows = [
        (16, 11, grain_hi),
        (15, 12, grain_hi), (17, 12, grain_hi),
        (15, 13, grain), (16, 13, grain_hi), (17, 13, grain_lo),
        (15, 14, grain_hi), (16, 14, grain), (17, 14, grain),
        (15, 15, grain), (16, 15, grain_hi), (17, 15, grain_lo),
        (15, 16, grain_hi), (16, 16, grain), (17, 16, grain_lo),
        (16, 17, grain), (16, 18, grain_lo),
        (16, 19, stem), (16, 20, stem), (16, 21, stem_lo),
        (14, 18, stem), (15, 19, stem), (18, 19, stem_lo), (17, 20, stem_lo), (13, 17, stem),
        (14, 11, grain_lo), (18, 11, grain_lo),
    ]
    for x, y, c in rows:
        px[x, y] = c + (255,)


def compass(px, well, dark):
    red, red_lo = hx('#ff5a5a'), hx('#a8202c')
    sil, sil_lo = hx('#e8eef4'), hx('#8292a6')
    core = hx('#8ad8ff')
    pts = [
        (16, 11, red), (16, 12, red), (16, 13, red), (15, 14, red), (16, 14, red_lo), (17, 14, red_lo),
        (16, 19, sil_lo), (16, 20, sil_lo), (16, 21, sil_lo), (15, 18, sil), (16, 18, sil_lo), (17, 18, sil_lo),
        (11, 16, sil), (12, 16, sil), (13, 16, sil), (14, 15, sil), (14, 16, sil), (14, 17, sil_lo),
        (19, 16, sil_lo), (20, 16, sil_lo), (21, 16, sil_lo), (18, 15, sil), (18, 16, sil_lo), (18, 17, sil_lo),
        (15, 15, sil), (17, 15, sil), (15, 17, sil_lo), (17, 17, sil_lo),
        (15, 16, core), (16, 16, (255, 255, 255)), (17, 16, core), (16, 15, core), (16, 17, core),
    ]
    for x, y, c in pts:
        px[x, y] = c + (255,)


SKILLS = ('combat', 'mining', 'foraging', 'farming', 'fishing', 'utility')
for _s in SKILLS:
    for _t in (1, 2, 3):
        _n = 'charm_' + _s + ('' if _t == 1 else f'_{_t}')

        def _mk(n=_n, s=_s):
            im = run1(n)
            if s == 'farming':
                repaint_well(im, wheat)
            elif s == 'utility':
                repaint_well(im, compass)
            return Raw(im)
        REG[_n] = _mk
for _n in ('charm_shiny', 'charm_forge', 'charm_estate', 'void_vacuum_charm', 'dungeon_core_1', 'dungeon_core_2',
           'dungeon_core_3'):
    REG[_n] = (lambda n=_n: Raw(run1(n)))


# --------------------------------------------------------------------------- Thermal Core (Legendary)
@lambda f: REG.__setitem__('thermal_core', f)
def thermal_core():
    mats = {
        'C': Mat(P.MAGMA, base=3, model='sphere', z=1),
        'F': Mat(P.IRON_DARK, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'R': Mat(P.COPPER, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'h': Mat(P.SUN, base=6, model='flat', z=3),
    }
    ic = Icon(mats)
    ic.disc(15.5, 16.0, 8.4, 'C')
    # iron cage: two bands + cooling fins
    for y in range(6, 27):
        for x in range(32):
            if ic.get(x, y) == 'C' and x in (11, 20):
                ic.set(x, y, 'F')
    for x in range(32):
        for y in (12, 20):
            if ic.get(x, y) == 'C':
                ic.set(x, y, 'F')
    for x, y in ((6, 13), (5, 13), (6, 19), (5, 19), (25, 13), (26, 13), (25, 19), (26, 19), (14, 6), (17, 6),
                 (14, 26), (17, 26)):
        ic.set(x, y, 'R')
    for x, y in ((14, 14), (15, 15), (16, 16), (15, 16), (16, 15)):
        ic.set(x, y, 'h')
    ic.spark(25, 6, P.WHITE, P.MAGMA[4], size=1)
    return ic


# --------------------------------------------------------------------------- Pickaxe Core of the Burrower (Legendary)
@lambda f: REG.__setitem__('pickaxe_core_of_the_burrower', f)
def burrower_core():
    rock = ramp('#7a5a44', n=7, at=3, lo=0.16, hi=0.88, shadow_h=320, ss=0.4, light_h=70)
    mats = {
        'S': Mat(rock, base=3, hi=(0, 1, 2), lo=(0, 1, 2), form=1.2, z=0),
        'G': Mat(P.EMBER, base=4, model='gem', z=2),
        'M': Mat(P.STEEL, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=3),
        'W': Mat(P.WOOD, base=3, model='flat', z=2),
    }
    ic = Icon(mats)
    ell(ic, 15.5, 17.0, 11.0, 10.0, 'S')
    for x, y in ((6, 9), (7, 8), (24, 9), (25, 10), (5, 21), (26, 22)):
        ic.clear(x, y)
    # amber core crystal breaking out of the rock
    for dy in range(-4, 5):
        w = 3 - abs(dy) * 0.7
        for dx in range(-3, 4):
            if abs(dx) <= w:
                ic.set(16 + dx, 17 + dy, 'G')
    # a small steel pick crossed over the top
    for i in range(9):
        ic.set(19 + i // 1 - 4, 9 - i // 2 + 4, 'W')
    for x, y in ((17, 4), (18, 4), (19, 5), (20, 5), (21, 6), (15, 4), (14, 5), (13, 5), (12, 6), (16, 4)):
        ic.set(x, y, 'M')
    ic.spark(25, 13, P.WHITE, P.EMBER[4], size=1)
    return ic


# --------------------------------------------------------------------------- books
def _book(cover, trim, emblem, emblem_key_mat, extra=None):
    mats = {
        'C': Mat(cover, base=3, hi=(0, 1, 2), lo=(0, 1, 1), form=1.0, z=1),
        'p': Mat(P.PORCELAIN, base=3, model='flat', z=0),
        'T': Mat(trim, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=2),
        'E': emblem_key_mat,
        's': Mat(cover, base=1, model='flat', z=2),
    }
    if extra:
        mats.update(extra)
    ic = Icon(mats)
    # pages (right / bottom edge), cover, spine
    rect(ic, 8, 5, 26, 27, 'p')
    for y in range(6, 28):
        if y % 2:
            ic.set(26, y, 'p', -1)
    rect(ic, 5, 4, 24, 26, 'C')
    rect(ic, 5, 4, 7, 26, 's')
    for x, y in ((5, 4), (24, 4), (5, 26), (24, 26), (6, 4), (24, 5), (5, 25), (23, 26)):
        ic.set(x, y, 'T')
    for x, y in ((7, 4), (24, 6), (5, 24), (22, 26)):
        ic.set(x, y, 'T', -1)
    emblem(ic)
    return ic


@lambda f: REG.__setitem__('recipe_book', f)
def recipe_book():
    cover = ramp('#2f6f78', n=7, at=3, lo=0.15, hi=0.90, shadow_h=270, light_h=190)

    def emblem(ic):
        # aether rune: a diamond with a cross, gold rim
        for dy in range(-4, 5):
            w = 4 - abs(dy)
            for dx in range(-w, w + 1):
                if abs(dx) == w or abs(dy) == 4 - abs(dx):
                    ic.set(15 + dx, 15 + dy, 'E')
        for d in range(-2, 3):
            ic.set(15 + d, 15, 'E', 1)
            ic.set(15, 15 + d, 'E', 1)
        ic.set(15, 15, 'E', 3)
        ic.spark(24, 3, P.WHITE, P.AETHER[5], size=1)
    return _book(cover, P.GOLD, emblem, Mat(P.AETHER_GLASS, base=3, model='flat', z=3))


@lambda f: REG.__setitem__('insolvent_ledger', f)
def insolvent_ledger():
    cover = ramp('#3e5a34', n=7, at=3, lo=0.14, hi=0.86, shadow_h=290, light_h=100)

    def emblem(ic):
        # red overdue seal, cracked; coins spilling from the pages
        ic.disc(15.5, 14.5, 3.6, 'E')
        for x, y in ((14, 12), (15, 13), (15, 14), (16, 15), (16, 16), (17, 17)):
            ic.set(x, y, 'E', -2)
        for x, y in ((25, 24), (26, 25), (27, 27), (28, 26), (24, 28), (25, 29)):
            ic.set(x, y, 'c')
        ic.set(13, 13, 'E', 2)
    return _book(cover, P.BRASS, emblem, Mat(P.CRIMSON, base=3, hi=(0, 1, 2), lo=(0, 1, 1), z=3),
                 extra={'c': Mat(P.GOLD, base=4, hi=(0, 1, 2), lo=(0, 1, 1), z=3)})
