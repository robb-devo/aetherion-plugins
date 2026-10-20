"""Pixel art for the status panel, the radar and the world VFX (called by gen_panel.py).

One art language with the pack's items: the radar is a large gold medallion built like the charm
medallions (outline, lit top-left lip, shaded bottom-right, dark inner rim), markers and icons are
hand-placed sprites with tinted outlines. Every glyph keeps its GUI size and advance, so the shader and
the Java layout are unchanged; textures hold 2 texels per GUI pixel, like the pack's 32 px items.
"""
import math

import numpy as np

from px_art import (AMBER, ASH, BONE, CRIMSON, EMERALD, GOLD, SKY, SLATE, STEEL, VIOLET, WHITE, Px, check_advance,
                    edge, grow, hx, mix, rim, shrink, upscale)

S = 2           # texels per GUI pixel for every panel glyph

RADAR_W = 64
PLATE_W = 87
PLATE_H = 12

FACE = hx('#13202a')
FACE_DK = hx('#0d161e')
FACE_LINE = hx('#22384a')
FACE_GLASS = hx('#2a4558')
FACE_ALPHA = 214


# ----------------------------------------------------------------------------- radar disc

def _ring_band(t, rmp):
    """Colour index along a metal ring lit from the top left: t = cos(angle - light)."""
    return np.select([t > 0.62, t > 0.05, t > -0.55], [4, 3, 2], 1)


def radar():
    n = RADAR_W * S
    p = Px(n, n)
    g = p.g
    c = n / 2
    d = g.dist(c, c)
    t = np.cos(g.angle(c, c) - math.radians(225))

    disc = d <= 63.6
    ring = disc & (d > 56.6)
    face = d <= 56.6
    p.put(disc & ~shrink(disc), GOLD[0])                       # outline
    body = ring & shrink(disc)
    idx = _ring_band(t, GOLD)
    for k in (1, 2, 3, 4):
        p.put(body & (idx == k), GOLD[k])
    outer = body & ~shrink(shrink(disc))                       # lip next to the outline
    p.put(outer & (t > 0.35), GOLD[5])
    p.put(outer & (t < -0.6), GOLD[1])
    inner = body & ~grow(face) & (d <= 58.6)                   # bevel next to the face: lit on the far side
    p.put(inner & (t > 0.2), GOLD[2])
    p.put(inner & (t < -0.35), GOLD[4])
    # engraved notches every 30 degrees and four studs on the diagonals
    for k in range(12):
        a = math.radians(k * 30 - 90)
        if k % 3 == 0:
            continue
        x0, y0 = c + math.cos(a) * 58.4, c + math.sin(a) * 58.4
        x1, y1 = c + math.cos(a) * 61.2, c + math.sin(a) * 61.2
        p.put(g.line(x0 - 0.5, y0 - 0.5, x1 - 0.5, y1 - 0.5) & body, GOLD[1])
    for k in range(4):
        a = math.radians(45 + 90 * k)
        sx, sy = c + math.cos(a) * 60.1, c + math.sin(a) * 60.1
        stud = g.disc(sx, sy, 2.3)
        p.put(stud, GOLD[0])
        p.put(g.disc(sx, sy, 1.5), GOLD[3])
        p.at(int(sx - 1), int(sy - 1), GOLD[5])
        p.at(int(sx), int(sy), GOLD[2])

    # face: dark translucent glass, a deeper band at the rim and a shadow under the top lip
    p.put(face, FACE, FACE_ALPHA)
    p.put(face & (d > 52.5), FACE_DK, FACE_ALPHA + 12)
    p.put(face & ~shrink(face), GOLD[0], 240)
    lip = face & (d > 54.6) & (t > 0.25)
    p.put(lip & ~edge(face, 0, 0), hx('#091017'), 236)
    # range rings: dashed, at a third and two thirds of the range
    ang = g.angle(c, c)
    for r, dashes in ((18.5, 16), (37.0, 32)):
        band = face & (np.abs(d - r) < 0.55)
        on = np.mod(ang / (2 * math.pi) * dashes, 1.0) < 0.55
        p.put(band & on, FACE_LINE, 236)
    # crosshair, leaving a gap round the player
    for x0, y0, x1, y1 in ((c, c - 52, c, c - 10), (c, c + 10, c, c + 52), (c - 52, c, c - 10, c), (c + 10, c, c + 52, c)):
        line = g.line(x0 - 1, y0 - 1, x1 - 1, y1 - 1) | g.line(x0, y0, x1, y1)
        p.put(line & face & (d < 52), FACE_LINE, 226)
    # bearing ticks: gold at N/E/S/W, steel every 30 degrees
    for k in range(12):
        a = math.radians(k * 30 - 90)
        major = k % 3 == 0
        length = 5.0 if major else 2.4
        r0 = 55.6
        x0, y0 = c + math.cos(a) * r0, c + math.sin(a) * r0
        x1, y1 = c + math.cos(a) * (r0 - length), c + math.sin(a) * (r0 - length)
        tick = g.line(x0 - 0.5, y0 - 0.5, x1 - 0.5, y1 - 0.5)
        if major:
            tick = tick | g.line(x0 - (0.5 if abs(math.cos(a)) > 0.5 else -0.5), y0 - (0.5 if abs(math.sin(a)) > 0.5 else -0.5),
                                 x1 - (0.5 if abs(math.cos(a)) > 0.5 else -0.5), y1 - (0.5 if abs(math.sin(a)) > 0.5 else -0.5))
            p.put(tick & face, GOLD[3])
        else:
            p.put(tick & face, STEEL[2], 240)
    # glass glint: a thin crescent in the top-left of the face
    glint = face & (np.abs(d - 46.5) < 0.8) & (ang > math.radians(196)) & (ang < math.radians(238))
    p.put(glint, FACE_GLASS, 236)
    glint2 = face & (np.abs(d - 46.5) < 0.8) & (ang > math.radians(242)) & (ang < math.radians(250))
    p.put(glint2, FACE_GLASS, 236)

    # forward marker: a gold arrowhead set into the top of the ring
    fw = Px(10, 6)
    fw.sprite(['....oo....',
               '...ohlo...',
               '..ohllso..',
               '.ohlllsso.',
               'ohlllsssso',
               'oooooooooo'], {'o': GOLD[0], 'h': GOLD[5], 'l': GOLD[4], 's': GOLD[2]})
    _stamp(p, fw, 59, 1)
    # player arrow, forward is always up
    arrow = Px(10, 11)
    arrow.sprite(['....oo....',
                  '...oWHo...',
                  '...oWHo...',
                  '..oWWHHo..',
                  '..oWWHHo..',
                  '.oWWWHHHo.',
                  '.oWWWHHHo.',
                  'oWWWooHHHo',
                  'oWWo..oHHo',
                  'oWo....oHo',
                  'oo......oo'], {'o': hx('#0c1430'), 'W': WHITE, 'H': STEEL[4]})
    _stamp(p, arrow, 59, 58)

    p.pin_advance()
    im = p.image()
    check_advance('radar', im, RADAR_W, RADAR_W + 1)
    return im


def _stamp(dst, src, x0, y0):
    m = src.px[..., 3] > 0
    ys, xs = np.nonzero(m)
    for y, x in zip(ys, xs):
        if 0 <= x0 + x < dst.w and 0 <= y0 + y < dst.h:
            dst.px[y0 + y, x0 + x] = src.px[y, x]


# ----------------------------------------------------------------------------- markers

def _gem(n, rmp, arrow=None):
    """A faceted diamond n x n: light upper-left facet, dark lower-right, tinted outline."""
    p = Px(n, n)
    g = p.g
    c = n / 2
    m = np.abs(g.x - c) + np.abs(g.y - c) <= c
    p.put(m, rmp[3])
    p.put(m & (g.x < c) & (g.y < c), rmp[4])
    p.put(m & (g.x >= c) & (g.y >= c), rmp[2])
    p.put(m & (g.x < c) & (g.y >= c), rmp[3])
    p.put(m & (g.x >= c) & (g.y < c), rmp[3])
    p.put(m & ~shrink(m), rmp[0])
    if arrow is None:
        p.at(int(c) - 3, int(c) - 2, rmp[5])
        p.at(int(c) - 2, int(c) - 3, rmp[5])
        p.at(int(c) - 2, int(c) - 2, WHITE)
    else:
        rows = ['..oo..', '.oWWo.', 'oWWWWo', 'oooooo'] if arrow == 'up' else ['oooooo', 'oWWWWo', '.oWWo.', '..oo..']
        p.sprite(rows, {'o': rmp[0], 'W': WHITE}, int(c) - 3, int(c) - 2)
    return p


def quest(variant=None):
    p = _gem(7 * S, GOLD, variant)
    p.pin_advance()
    im = p.image()
    check_advance('quest', im, 7, 8)
    return im


def pip(rmp):
    n = 5 * S
    p = Px(n, n)
    m = p.g.disc(n / 2, n / 2, n / 2 - 0.1)
    p.shade(m, rmp, soft=True)
    p.put(m & ~shrink(m), rmp[0])
    p.at(3, 3, rmp[5])
    p.at(3, 4, rmp[4])
    p.at(4, 3, rmp[4])
    p.pin_advance()
    im = p.image()
    check_advance('pip', im, 5, 6)
    return im


def boss():
    """Horned red skull."""
    rows = ['.b..........b.',
            'bLb........bLb',
            'bLLb......bLLb',
            '.bLLboooobLLb.',
            '..bLoRRRRoLb..',
            '...oRRRRRRo...',
            '..oRRRRRRRRo..',
            '..oRkkRRkkRo..',
            '..oRkyRRkyRo..',
            '..orRRRRRRro..',
            '...orRssRro...',
            '...oowowowo...',
            '....owowowo...',
            '.....ooooo....']
    pal = {'b': BONE[0], 'L': BONE[4], 'o': CRIMSON[0], 'R': CRIMSON[3], 'r': CRIMSON[2], 's': CRIMSON[1],
           'k': hx('#14020a'), 'y': AMBER[4], 'w': BONE[5]}
    p = Px(14, 14)
    p.sprite(rows, pal)
    # light on the skull: top-left texels one step brighter
    p.at(5, 5, CRIMSON[4])
    p.at(4, 6, CRIMSON[4])
    p.at(5, 6, CRIMSON[4])
    p.pin_advance()
    im = p.image()
    check_advance('boss', im, 7, 8)
    return im


def north():
    p = Px(10, 10)
    m = p.g.disc(5, 5, 4.9)
    p.put(m, CRIMSON[2])
    p.put(m & (p.g.y < 5), CRIMSON[3])
    p.put(m & ~shrink(m), CRIMSON[0])
    p.sprite(['W..W', 'WW.W', 'WW.W', 'W.WW', 'W.WW', 'W..W'], {'W': WHITE}, 3, 2)
    p.pin_advance()
    im = p.image()
    check_advance('north', im, 5, 6)
    return im


def markers():
    return {
        'quest': quest(),
        'npc': pip(EMERALD),
        'party': pip(SKY),
        'boss': boss(),
        'north': north(),
        'quest_up': quest('up'),
        'quest_down': quest('down'),
    }


# ----------------------------------------------------------------------------- panel icons (7 GUI px = 14 texels)

def _icon(rows, pal):
    p = Px(14, 14)
    p.sprite(rows, pal)
    p.pin_advance()
    im = p.image()
    check_advance('icon', im, 7, 8)
    return im


def _r(rmp):
    return {'o': rmp[0], 'd': rmp[1], 's': rmp[2], 'm': rmp[3], 'l': rmp[4], 'h': rmp[5], 'W': WHITE}


def icon_coin():
    return _icon(['....oooooo....',
                  '..oollllllo...',
                  '.olhhllllmmo..',
                  '.olhmmmmmmso..',
                  'olhmmshhsmmso.',
                  'olhmsmhhmsmso.',
                  'olmmshhhhsmso.',
                  'olmmsmhhmsmso.',
                  'olmmmshhsmsso.',
                  '.olmmmmmmsso..',
                  '.osmmmmmsssdo.',
                  '..osssssssdo..',
                  '...oddddddo...',
                  '....oooooo....'], {**_r(AMBER), 'o': AMBER[0]})


def icon_shard():
    return _icon(['......oo......',
                  '.....ohlo.....',
                  '....ohhlmo....',
                  '...ohhlmmso...',
                  '...ohllmmso...',
                  '..ohllmmmsso..',
                  '..ohlllmmsso..',
                  '..olllmmmsso..',
                  '..ollmmmssdo..',
                  '...olmmmsdo...',
                  '...olmmssdo...',
                  '....olmsdo....',
                  '.....omdo.....',
                  '......oo......'], _r(SKY))


def icon_pin():
    return _icon(['....oooooo....',
                  '...ollllllo...',
                  '..olhhllllmo..',
                  '.olhlooooomso.',
                  '.olloWWWWomso.',
                  '.olmoWWWWomso.',
                  '.olmoWWWWosso.',
                  '..olmoooossdo.',
                  '..olmmmmsssdo.',
                  '...olmmssddo..',
                  '....olmsddo...',
                  '.....olsdo....',
                  '......odo.....',
                  '.......o......'], _r(CRIMSON))


def icon_cloud():
    return _icon(['..............',
                  '.....oooo.....',
                  '....ohhhlo....',
                  '..ooohhlllooo.',
                  '.ohhohllllllmo',
                  'ohhllllllllmmo',
                  'ohllllllllmmmo',
                  'olllllllmmmmso',
                  'ommmmmmmmmssso',
                  '.ossssssssssdo',
                  '..oooooooooo..',
                  '..............',
                  '..............',
                  '..............'], _r(STEEL))


def icon_crest():
    pal = _r(VIOLET)
    pal.update({'O': GOLD[0], 'G': GOLD[3], 'Y': GOLD[5], 'D': GOLD[2], 'W': GOLD[5]})
    return _icon(['.O....O....O..',
                  'OYO..OYO..OYO.',
                  'OGO.OGGGO.OGO.',
                  'OGGOGGYGGOGDO.',
                  'OGGGGGGGGGDDO.',
                  'OOOOOOOOOOOOO.',
                  'ohllllmmmmsso.',
                  'ohllmWWmmmsso.',
                  'ollmWWWWmmsso.',
                  '.olmmWWmmsso..',
                  '.olmmmmmmsso..',
                  '..olmmmmsso...',
                  '...ommmsso....',
                  '....ooooo.....'], pal)


ICONS = {'coin': icon_coin, 'shard': icon_shard, 'pin': icon_pin, 'cloud': icon_cloud, 'crest': icon_crest}


def icons():
    return {name: fn() for name, fn in ICONS.items()}


# ----------------------------------------------------------------------------- panel plates

BODY = SLATE[2]
BODY_ALPHA = 226


def plate(kind):
    """One 12 px row of the panel. Rows stack, so the body and the side rails are identical in all three
    kinds; only the top trim and the bottom lip differ."""
    w, h = PLATE_W * S, PLATE_H * S
    p = Px(w, h)
    p.put(p.g.rect(0, 0, w, h), BODY, BODY_ALPHA)
    # left rail: outline, gold bar lit on its left, dark seam
    p.put(p.g.rect(0, 0, 1, h), GOLD[0])
    p.put(p.g.rect(1, 0, 2, h), GOLD[4])
    p.put(p.g.rect(2, 0, 3, h), GOLD[3])
    p.put(p.g.rect(3, 0, 4, h), GOLD[2])
    p.put(p.g.rect(4, 0, 5, h), SLATE[0])
    p.put(p.g.rect(5, 0, 6, h), SLATE[3], BODY_ALPHA)
    # a stud on the rail in the middle of every row
    mid = h // 2
    p.sprite(['oo', 'hl', 'ls', 'oo'], {'o': GOLD[0], 'h': GOLD[5], 'l': GOLD[4], 's': GOLD[1]}, 1, mid - 2)
    # right edge: outline and a shaded inner line
    p.put(p.g.rect(w - 1, 0, w, h), SLATE[0])
    p.put(p.g.rect(w - 2, 0, w - 1, h), SLATE[1], BODY_ALPHA)
    if kind == 'top':
        p.erase(p.g.rect(0, 0, 1, 1))
        p.put(p.g.rect(1, 0, w - 1, 1), GOLD[0])
        p.put(p.g.rect(0, 1, 1, 2), GOLD[0])
        p.put(p.g.rect(1, 1, w - 1, 2), GOLD[5])
        p.put(p.g.rect(1, 2, w - 1, 3), GOLD[4])
        p.put(p.g.rect(1, 3, w - 1, 4), GOLD[2])
        p.put(p.g.rect(1, 4, w - 1, 5), GOLD[0])
        p.put(p.g.rect(6, 5, w - 2, 6), SLATE[4], BODY_ALPHA)
        p.put(p.g.rect(w - 1, 0, w, 1), (0, 0, 0), 0)
        p.put(p.g.rect(w - 1, 1, w, 5), GOLD[0])
        # gold rail top cap: the rail joins the trim with a lit corner
        p.at(1, 1, GOLD[5])
        p.at(1, 2, GOLD[5])
    if kind == 'bot':
        p.erase(p.g.rect(0, h - 1, 1, h))
        p.erase(p.g.rect(w - 1, h - 1, w, h))
        p.put(p.g.rect(6, h - 4, w - 2, h - 3), SLATE[1], BODY_ALPHA)
        p.put(p.g.rect(1, h - 3, w - 1, h - 2), GOLD[0])
        p.put(p.g.rect(1, h - 2, w - 1, h - 1), GOLD[2])
        p.put(p.g.rect(1, h - 1, w - 1, h), GOLD[0])
        p.put(p.g.rect(0, h - 2, 1, h - 1), GOLD[0])
        p.put(p.g.rect(w - 1, h - 3, w, h - 1), GOLD[0])
    p.pin_advance()
    im = p.image()
    check_advance('plate_' + kind, im, PLATE_H, PLATE_W + 1)
    return im


def plates():
    return {k: plate(k) for k in ('top', 'mid', 'bot')}


# ----------------------------------------------------------------------------- world VFX (white, tinted in game)

def vfx(kind):
    """64-texel pixel art, doubled to the 128 texels the font expects (same glyph metrics as before)."""
    n = 64
    p = Px(n, n)
    g = p.g
    c = n / 2
    d = g.dist(c, c)
    ang = g.angle(c, c)
    hi, mid, lo = (255, 255, 255), (214, 214, 222), (160, 160, 172)
    if kind == 'slash':
        outer = g.disc(c, c, 30.4)
        inner = g.disc(c, c + 11.5, 29.0)
        cres = outer & ~inner & (g.y < c + 6)
        p.put(cres, mid, 230)
        p.put(cres & ~shift_down(cres), hi)
        p.put(cres & ~grow(~cres & outer & (g.y < c + 6)) & (np.abs(g.x - c) < 18), hi)
        tips = cres & (np.abs(g.x - c) > 21)
        p.put(tips, lo, 150)
    elif kind == 'ring':
        band = (d <= 29.6) & (d > 26.4)
        p.put(band, mid, 235)
        p.put(band & (d > 28.6), hi)
        p.put(band & (d <= 27.4), lo, 200)
    elif kind == 'rune':
        p.put((d <= 30.6) & (d > 29.0), hi)
        p.put((d <= 23.4) & (d > 22.2), mid, 235)
        for k in range(24):
            a0 = 2 * math.pi * k / 24
            on = np.abs(np.angle(np.exp(1j * (ang - a0)))) < (0.06 if k % 3 else 0.11)
            p.put(on & (d < 28.6) & (d > 24.4), hi if k % 3 == 0 else mid, 235)
        for k in range(6):
            a = math.radians(k * 60 - 90)
            b = math.radians(k * 60 + 30)
            p.put(g.line(c - 0.5 + math.cos(a) * 21.5, c - 0.5 + math.sin(a) * 21.5,
                         c - 0.5 + math.cos(b + math.radians(60)) * 21.5, c - 0.5 + math.sin(b + math.radians(60)) * 21.5),
                  mid, 235)
        p.put(d < 2.6, hi)
    elif kind == 'tele_edge':
        band = (d <= 30.6) & (d > 28.2)
        on = np.mod(ang / (2 * math.pi) * 32, 1.0) < 0.58
        p.put(band & on, hi)
        p.put(band & ~on, lo, 110)
    elif kind == 'tele_fill':
        disc = d <= 30.4
        p.put(disc, mid, 70)
        p.put(disc & (d > 20.0), mid, 100)
        p.put(disc & (d > 26.0), hi, 140)
    return upscale(p.image(), 2)


def shift_down(mask):
    from px_art import shift
    return shift(mask, 0, 1)
