"""HD art for the status panel and radar (called by gen_panel.py).

Each glyph keeps its GUI size, so the shader and the Java layout are unchanged; only the texture holds
more texels per GUI pixel: the radar, markers and icons at 4x, the panel plates at 2x (Minecraft drops
font glyphs larger than 256 texels, and a plate is 87 GUI px wide).

Light comes from the top left, like the Manager chrome. Gold is the panel trim, violet the XP accent.
"""
import math

import numpy as np

from hd_art import (Canvas, angle, band, check_advance, fill, hx, lerp, linear, noise, sd_box, sd_circle,
                    sd_polygon, sd_segment)

S = 4           # radar, markers, icons
PS = 2          # panel plates

OUT = hx('#0a090e')
BODY_HI = hx('#2a2737')
BODY_LO = hx('#17151f')
GOLD_DK = hx('#6b4a1c')
GOLD = hx('#d9a441')
GOLD_HI = hx('#ffe6a3')
VIOLET = hx('#a77bea')
FACE_IN = hx('#1d2a33')
FACE_OUT = hx('#0c1117')
WHITE = (255, 255, 255)

RADAR_W = 64
PLATE_W = 87
PLATE_H = 12


def pin_advance(cv):
    """Minecraft measures a glyph's advance up to its last non-transparent column. A texel with alpha 1/255
    in that column fixes the advance and is never drawn (the text shader discards alpha below 0.1)."""
    col = cv.a[:, -1]
    if col.max() * 255.0 < 1.0:
        mid = cv.h // 2
        cv.a[mid, -1] = 1.0 / 255.0
        cv.rgb[mid, -1] = 0.0


def metal(cv, cx, cy, lo, mid, hi, light=225.0):
    """Gold-like colour field lit from screen angle ``light`` (degrees, clockwise from +x)."""
    t = 0.5 + 0.5 * np.cos(angle(cv, cx, cy) - math.radians(light))
    grain = noise(cv, 7, 3) * 0.06
    t = np.clip(t + grain, 0.0, 1.0)
    low = lerp(lo, mid, np.clip(t * 2.0, 0.0, 1.0))
    return np.where((t > 0.5)[..., None], lerp(mid, hi, np.clip(t * 2.0 - 1.0, 0.0, 1.0)), low)


# ----------------------------------------------------------------------------- radar disc

def radar():
    n = RADAR_W * S
    cv = Canvas(n, n)
    c = n / 2
    r_out = c - 0.4
    r_bez_out = r_out - 1.0 * S
    r_bez_in = r_bez_out - 2.0 * S
    r_face = r_bez_in - 1.0 * S
    d = np.hypot(cv.x - c, cv.y - c)
    ang = angle(cv, c, c)

    # outline + bezel + groove
    cv.paint(fill(d - r_out), OUT)
    bez = fill(d - r_bez_out) * (1.0 - fill(d - r_bez_in))
    col = metal(cv, c, c, GOLD_DK, GOLD, GOLD_HI)
    # radial profile: bright lip on the outer edge, shadow toward the face
    rt = np.clip((d - r_bez_in) / (r_bez_out - r_bez_in), 0.0, 1.0)
    col = lerp(col, lerp(GOLD_DK, OUT, 0.4), np.clip(0.55 - rt, 0.0, 0.55))
    col = lerp(col, GOLD_HI, np.clip((rt - 0.78) * 2.2, 0.0, 0.45) * (0.5 + 0.5 * np.cos(ang - math.radians(225))))
    cv.paint(bez, col)
    # engraved notches every 30 degrees across the bezel
    for k in range(12):
        a = math.radians(k * 30 - 90)
        ux, uy = math.cos(a), math.sin(a)
        seg = sd_segment(cv, c + ux * (r_bez_in + 1), c + uy * (r_bez_in + 1),
                         c + ux * (r_bez_out - 1.5), c + uy * (r_bez_out - 1.5))
        cv.paint(band(seg, 0.9) * bez, lerp(GOLD_DK, OUT, 0.5), 0.75)

    # face: translucent radial gradient, darker toward the rim
    face = fill(d - r_face)
    ft = np.clip(d / r_face, 0.0, 1.0)
    cv.erase(face)
    cv.paint(face, lerp(FACE_IN, FACE_OUT, ft ** 1.6), 0.74 + 0.16 * ft ** 2)

    # range rings (dashed) at a third and two thirds of the range
    for frac, dash in ((1 / 3, 18), (2 / 3, 36)):
        ring = band(d - r_face * frac * 0.98, 0.9)
        on = (np.mod(ang / (2 * math.pi) * dash, 1.0) < 0.62).astype(np.float64)
        cv.paint(ring * on * face, GOLD, 0.34)
    # crosshair with a gap around the player
    for x0, y0, x1, y1 in ((c, c - r_face, c, c - 16), (c, c + 16, c, c + r_face),
                           (c - r_face, c, c - 16, c), (c + 16, c, c + r_face, c)):
        cv.paint(band(sd_segment(cv, x0, y0, x1, y1), 0.8) * face, WHITE, 0.08)
    # bearing ticks just inside the face: minor every 10 degrees, major every 90
    for k in range(36):
        a = math.radians(k * 10 - 90)
        ux, uy = math.cos(a), math.sin(a)
        major = k % 9 == 0
        length = 9.0 if major else 4.5
        seg = sd_segment(cv, c + ux * (r_face - 2), c + uy * (r_face - 2),
                         c + ux * (r_face - 2 - length), c + uy * (r_face - 2 - length))
        cv.paint(band(seg, 1.0 if major else 0.8) * face, GOLD if major else WHITE, 0.6 if major else 0.2)
    # inner shadow under the groove
    cv.paint(face * np.clip((d - (r_face - 10)) / 10, 0.0, 1.0) ** 2, OUT, 0.45)
    # glass: a soft highlight across the top-left
    gl = np.clip(1.0 - np.hypot((cv.x - c * 0.72) / (c * 0.9), (cv.y - c * 0.45) / (c * 0.55)), 0.0, 1.0)
    cv.paint(face * gl ** 1.5, WHITE, 0.10)

    # forward marker: a small gold wedge on the bezel at the top
    wedge = sd_polygon(cv, [(c, r_out - r_bez_out + 1.5), (c + 7, c - r_bez_in + 2), (c - 7, c - r_bez_in + 2)])
    cv.paint(fill(wedge - 1.4), OUT)
    cv.paint(fill(wedge), lerp(GOLD_HI, WHITE, 0.3))

    # player chevron (forward is always up)
    glow = np.clip(1.0 - np.hypot(cv.x - c, cv.y - c) / 18.0, 0.0, 1.0)
    cv.paint(glow ** 2, WHITE, 0.22)
    chev = sd_polygon(cv, [(c, c - 11), (c + 8.5, c + 9), (c, c + 4), (c - 8.5, c + 9)])
    cv.paint(fill(chev - 2.2), OUT, 0.95)
    cv.paint(fill(chev), lerp(WHITE, hx('#cfe2ff'), linear(cv, c, c - 11, c, c + 9)))

    pin_advance(cv)
    im = cv.image()
    check_advance('radar', im, RADAR_W, RADAR_W + 1)
    return im


# ----------------------------------------------------------------------------- markers

def _halo(cv, c, r, colour, alpha):
    h = np.clip(1.0 - (np.hypot(cv.x - c, cv.y - c) - r * 0.45) / (r * 0.55), 0.0, 1.0)
    cv.paint(h ** 1.8, colour, alpha)


def _diamond(cv, c, r):
    return sd_polygon(cv, [(c, c - r), (c + r, c), (c, c + r), (c - r, c)])


def quest(variant=None):
    """Gold diamond; ``up``/``down`` carry a white arrow for a target above or below the player."""
    n = 7 * S
    cv = Canvas(n, n)
    c = n / 2
    _halo(cv, c, c, hx('#ffd34d'), 0.55)
    dm = _diamond(cv, c, 11.0)
    cv.paint(fill(dm - 2.2), lerp(GOLD_DK, OUT, 0.45))
    cv.paint(fill(dm), lerp(GOLD_HI, hx('#f2a92c'), linear(cv, c - 6, c - 6, c + 6, c + 6)))
    # facet: lighter upper-left half
    facet = fill(dm) * (cv.x + cv.y < 2 * c).astype(np.float64)
    cv.paint(facet, WHITE, 0.22)
    if variant == 'up':
        cv.paint(fill(sd_polygon(cv, [(c, c - 6), (c + 5, c + 2.5), (c - 5, c + 2.5)]) - 1.0), lerp(GOLD_DK, OUT, 0.3))
        cv.paint(fill(sd_polygon(cv, [(c, c - 5), (c + 3.6, c + 1.6), (c - 3.6, c + 1.6)])), WHITE)
    elif variant == 'down':
        cv.paint(fill(sd_polygon(cv, [(c, c + 6), (c - 5, c - 2.5), (c + 5, c - 2.5)]) - 1.0), lerp(GOLD_DK, OUT, 0.3))
        cv.paint(fill(sd_polygon(cv, [(c, c + 5), (c - 3.6, c - 1.6), (c + 3.6, c - 1.6)])), WHITE)
    else:
        cv.paint(fill(sd_circle(cv, c - 1.5, c - 1.5, 2.6)), WHITE, 0.95)
    pin_advance(cv)
    im = cv.image()
    check_advance('quest', im, 7, 8)
    return im


def pip(ink, ring):
    n = 5 * S
    cv = Canvas(n, n)
    c = n / 2
    _halo(cv, c, c, ink, 0.35)
    cv.paint(fill(sd_circle(cv, c, c, 8.2)), OUT, 0.95)
    cv.paint(fill(sd_circle(cv, c, c, 6.4)), ring)
    cv.paint(fill(sd_circle(cv, c, c, 4.6)), lerp(lerp(ink, WHITE, 0.35), ink, linear(cv, c, c - 4, c, c + 4)))
    cv.paint(fill(sd_circle(cv, c - 1.6, c - 1.8, 1.7)), WHITE, 0.85)
    pin_advance(cv)
    im = cv.image()
    check_advance('pip', im, 5, 6)
    return im


def boss():
    """Horned skull on a red glow."""
    n = 7 * S
    cv = Canvas(n, n)
    c = n / 2
    red = hx('#ff4a3d')
    _halo(cv, c, c, red, 0.5)
    horns = [sd_polygon(cv, [(c - 5.5, c - 3), (c - 11.5, c - 11.5), (c - 2.5, c - 6.5)]),
             sd_polygon(cv, [(c + 5.5, c - 3), (c + 11.5, c - 11.5), (c + 2.5, c - 6.5)])]
    head = np.minimum(sd_circle(cv, c, c - 0.5, 7.2), sd_box(cv, c - 4.6, c + 1, c + 4.6, c + 9.5, 1.6))
    shape = np.minimum(np.minimum(horns[0], horns[1]), head)
    cv.paint(fill(shape - 2.0), hx('#2a0606'))
    cv.paint(fill(np.minimum(horns[0], horns[1])), lerp(hx('#f5e6c8'), hx('#9c8a6a'), linear(cv, c, c - 11, c, c - 3)))
    cv.paint(fill(head), lerp(hx('#ff8a7a'), hx('#c4221a'), linear(cv, c, c - 7, c, c + 9)))
    for ex in (-3.0, 3.0):
        cv.paint(fill(sd_circle(cv, c + ex, c - 0.5, 2.1)), hx('#1a0303'))
        cv.paint(fill(sd_circle(cv, c + ex, c - 0.5, 0.9)), hx('#ffd36b'), 0.9)
    for tx in (-2.4, 0.0, 2.4):
        cv.paint(fill(sd_box(cv, c + tx - 0.7, c + 5.6, c + tx + 0.7, c + 9.0)), hx('#2a0606'), 0.8)
    pin_advance(cv)
    im = cv.image()
    check_advance('boss', im, 7, 8)
    return im


def north():
    n = 5 * S
    cv = Canvas(n, n)
    c = n / 2
    cv.paint(fill(sd_circle(cv, c, c, 9.9)), hx('#5a1410'), 0.92)
    cv.paint(band(sd_circle(cv, c, c, 8.9), 0.9), hx('#e0574a'), 0.9)
    strokes = np.minimum(np.minimum(sd_segment(cv, c - 3.6, c + 4.8, c - 3.6, c - 4.8),
                                    sd_segment(cv, c + 3.6, c + 4.8, c + 3.6, c - 4.8)),
                         sd_segment(cv, c - 3.6, c - 4.8, c + 3.6, c + 4.8))
    cv.paint(band(strokes, 2.6), OUT, 0.8)
    cv.paint(band(strokes, 1.5), WHITE)
    pin_advance(cv)
    im = cv.image()
    check_advance('north', im, 5, 6)
    return im


def markers():
    return {
        'quest': quest(),
        'npc': pip(hx('#62d977'), hx('#d9ffe2')),
        'party': pip(hx('#6fb8ff'), WHITE),
        'boss': boss(),
        'north': north(),
        'quest_up': quest('up'),
        'quest_down': quest('down'),
    }


# ----------------------------------------------------------------------------- panel icons (7 GUI px)

def _icon_canvas():
    n = 7 * S
    return Canvas(n, n), n / 2


def icon_coin():
    cv, c = _icon_canvas()
    cv.paint(fill(sd_circle(cv, c, c, 13.6)), OUT)
    cv.paint(fill(sd_circle(cv, c, c, 11.6)), metal(cv, c, c, hx('#8a5a12'), hx('#f5c542'), hx('#fff1b8')))
    cv.paint(band(sd_circle(cv, c, c, 8.4), 0.9), hx('#a8721c'), 0.9)
    star = []
    for k in range(10):
        r = 6.2 if k % 2 == 0 else 2.7
        a = math.radians(-90 + k * 36)
        star.append((c + r * math.cos(a), c + r * math.sin(a)))
    cv.paint(fill(sd_polygon(cv, star)), hx('#fff6cf'))
    pin_advance(cv)
    return cv.image()


def icon_shard():
    cv, c = _icon_canvas()
    sd = sd_polygon(cv, [(c, 0.8), (c + 9.5, c - 2), (c + 6, c + 9), (c, 27.2), (c - 6, c + 9), (c - 9.5, c - 2)])
    cv.paint(fill(sd - 2.0), OUT)
    cv.paint(fill(sd), lerp(hx('#e8fbff'), hx('#3fa6d9'), linear(cv, c - 8, 2, c + 8, 26)))
    # facets
    left = sd_polygon(cv, [(c, 3), (c - 7.5, c - 2), (c, c + 2)])
    cv.paint(fill(left), WHITE, 0.45)
    right = sd_polygon(cv, [(c, c + 2), (c + 7.5, c - 2), (c + 4.6, c + 8)])
    cv.paint(fill(right), hx('#1d6f9c'), 0.35)
    cv.paint(band(sd_segment(cv, c, 3, c, 25), 0.7) * fill(sd), WHITE, 0.25)
    # sparkle reaches the right edge so the advance stays 8
    cv.paint(band(sd_segment(cv, 24.0, 5.0, 27.6, 5.0), 0.8), WHITE, 0.8)
    cv.paint(band(sd_segment(cv, 25.8, 3.2, 25.8, 6.8), 0.8), WHITE, 0.8)
    pin_advance(cv)
    return cv.image()


def icon_pin():
    cv, c = _icon_canvas()
    head = sd_circle(cv, c, 10.5, 9.6)
    tail = sd_polygon(cv, [(c - 7.2, 15.5), (c + 7.2, 15.5), (c, 27.4)])
    sd = np.minimum(head, tail)
    cv.paint(fill(sd - 2.0), OUT)
    cv.paint(fill(sd), lerp(hx('#ff9b86'), hx('#c23a2c'), linear(cv, c - 6, 2, c + 6, 26)))
    cv.paint(fill(sd_circle(cv, c, 10.5, 4.0)), hx('#fff1e8'))
    cv.paint(fill(sd_circle(cv, c - 4.5, 6.5, 2.2)), WHITE, 0.55)
    pin_advance(cv)
    return cv.image()


def icon_cloud():
    cv, c = _icon_canvas()
    sd = np.minimum.reduce([sd_circle(cv, 9.5, 15.5, 6.5), sd_circle(cv, 16.0, 11.5, 8.0),
                            sd_circle(cv, 21.8, 16.5, 5.6), sd_box(cv, 3.5, 15.5, 27.4, 22.0, 3.2)])
    cv.paint(fill(sd - 2.0), OUT)
    cv.paint(fill(sd), lerp(WHITE, hx('#a9c4dc'), linear(cv, 0, 5, 0, 22)))
    cv.paint(fill(sd_circle(cv, 14.0, 9.5, 3.2)) * fill(sd), WHITE, 0.7)
    pin_advance(cv)
    return cv.image()


def icon_crest():
    cv, c = _icon_canvas()
    shield = sd_polygon(cv, [(2.5, 8), (25.5, 8), (25.5, 15), (c, 27.4), (2.5, 15)])
    crown = sd_polygon(cv, [(3.0, 9.5), (3.0, 1.5), (8.5, 6), (c, 0.6), (19.5, 6), (25.0, 1.5), (25.0, 9.5)])
    sd = np.minimum(shield, crown)
    cv.paint(fill(sd - 2.0), OUT)
    cv.paint(fill(crown), metal(cv, c, 5, hx('#8a5a12'), hx('#ffd86b'), hx('#fff4c4')))
    cv.paint(fill(shield), lerp(hx('#c9a0ff'), hx('#5b3aa8'), linear(cv, 0, 8, 0, 27)))
    cv.paint(fill(sd_polygon(cv, [(c, 12), (c + 4.5, 16.5), (c, 21), (c - 4.5, 16.5)])), hx('#fff1b8'))
    cv.paint(band(sd_box(cv, 2.5, 8, 25.5, 9.6), 0.6), hx('#ffe08a'), 0.9)
    # right-edge reach for the advance: the shield touches x 25.5 + outline 2 = 27.5
    pin_advance(cv)
    return cv.image()


ICONS = {'coin': icon_coin, 'shard': icon_shard, 'pin': icon_pin, 'cloud': icon_cloud, 'crest': icon_crest}


def icons():
    out = {}
    for name, fn in ICONS.items():
        im = fn()
        check_advance('icon_' + name, im, 7, 8)
        out[name] = im
    return out


# ----------------------------------------------------------------------------- panel plates

def plate(kind):
    """One 12 px row of the panel. Rows stack, so the body tone is the same in all three kinds; only the
    top trim and the bottom lip differ."""
    w, h = PLATE_W * PS, PLATE_H * PS
    cv = Canvas(w, h)
    grain = noise(cv, {'top': 11, 'mid': 12, 'bot': 13}[kind], 2)[..., None] * 2.0
    body = lerp(BODY_HI, BODY_LO, 0.55)
    cv.paint(np.ones(cv.x.shape), np.clip(body + grain, 0, 255), 0.86)
    # left rail: outline, a gold rail that cools toward violet down the panel, then a dark seam
    cv.paint((cv.x < 2).astype(np.float64), OUT)
    rail_t = {'top': 0.0, 'mid': 0.35, 'bot': 0.7}[kind] + cv.y / h * 0.3
    cv.paint(((cv.x >= 2) & (cv.x < 4)).astype(np.float64), lerp(GOLD, VIOLET, rail_t))
    cv.paint(((cv.x >= 4) & (cv.x < 5)).astype(np.float64), OUT, 0.55)
    # right edge with a faint lit inner line
    cv.paint((cv.x > w - 2).astype(np.float64), OUT)
    cv.paint(((cv.x > w - 3) & (cv.x <= w - 2)).astype(np.float64), BODY_HI, 0.6)
    inner = ((cv.x >= 2) & (cv.x <= w - 2)).astype(np.float64)
    if kind == 'top':
        cv.erase(((cv.y < 2) & ((cv.x < 2) | (cv.x > w - 2))).astype(np.float64))
        cv.paint((cv.y < 2).astype(np.float64) * inner, OUT)
        sheen = np.clip(1.0 - np.abs(cv.x - w * 0.3) / (w * 0.5), 0.0, 1.0)
        cv.paint(((cv.y >= 2) & (cv.y < 4)).astype(np.float64) * inner, lerp(GOLD, GOLD_HI, sheen * 0.8))
        cv.paint(((cv.y >= 4) & (cv.y < 5)).astype(np.float64) * inner, lerp(GOLD_DK, OUT, 0.3), 0.8)
        cv.paint(((cv.y >= 5) & (cv.y < 6)).astype(np.float64) * inner, WHITE, 0.04)
    if kind == 'bot':
        cv.erase(((cv.y >= h - 2) & ((cv.x < 2) | (cv.x > w - 2))).astype(np.float64))
        cv.paint((cv.y >= h - 2).astype(np.float64) * inner, OUT)
        cv.paint(((cv.y >= h - 4) & (cv.y < h - 2)).astype(np.float64) * inner, lerp(BODY_LO, OUT, 0.5), 0.9)
        fade = np.clip(1.0 - cv.x / (w * 0.7), 0.0, 1.0)
        cv.paint(((cv.y >= h - 4) & (cv.y < h - 3)).astype(np.float64) * inner * fade, GOLD_DK, 0.7)
    pin_advance(cv)
    im = cv.image()
    check_advance('plate_' + kind, im, PLATE_H, PLATE_W + 1)
    return im


def plates():
    return {k: plate(k) for k in ('top', 'mid', 'bot')}
