"""HD art for the player HUD bars (called by gen_hud.py).

Frames and fill strips are drawn at 2x and the 7 px icons at 4x; every glyph keeps its GUI size and
advance, so HudGlyphs and the shader need no change. The pixel font stays 1:1 on purpose: it is tuned
per pixel for legibility on any fill.
"""
import math

import numpy as np

from hd_art import Canvas, band, fill, hx, lerp, linear, noise, sd_box, sd_circle, sd_polygon, sd_segment
from gen_panel_hd import pin_advance

FS = 2          # frames and fills
IS = 4          # icons

OUT = hx('#0a090e')
WHITE = (255, 255, 255)
TRAY = hx('#14121a')
BODY = hx('#211f2a')
WELL_BG = hx('#120f17')


def _mixc(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


# ----------------------------------------------------------------------------- frames

def frame(kind, accent, frame_w, frame_h, def_w, well, hp_well, cd_socket):
    """One HUD frame cell (frame_w x frame_h GUI px) at FS texels per px."""
    s = FS
    w, h = frame_w * s, frame_h * s
    cv = Canvas(w, h)
    box_w = (def_w if kind == 'def' else frame_w) * s
    ring = _mixc(TRAY, accent, 0.55)
    hi = _mixc(accent, WHITE, 0.45)
    well_c = _mixc(WELL_BG, accent, 0.10)
    socket_bg = _mixc(BODY, accent, 0.20)

    outer = sd_box(cv, 0, 0, box_w, h, 3.0)
    cv.paint(fill(outer), OUT)
    ring_sd = sd_box(cv, s, s, box_w - s, h - s, 2.0)
    shade = linear(cv, 0, s, 0, h - s)
    cv.paint(fill(ring_sd), lerp(_mixc(ring, WHITE, 0.25), _mixc(ring, OUT, 0.35), shade))
    body_sd = sd_box(cv, 2 * s, 2 * s, box_w - 2 * s, h - 2 * s, 1.0)
    cv.paint(fill(body_sd), well_c)
    # recessed well: shadow under the top lip, faint light on the floor
    cv.paint(fill(body_sd) * np.clip(1.0 - (cv.y - 2 * s) / 3.0, 0.0, 1.0), OUT, 0.55)
    cv.paint(fill(body_sd) * ((cv.y >= h - 3 * s) & (cv.y < h - 2 * s)), accent, 0.14)

    def socket(x0):
        sx0, sx1 = (x0 + 1) * s, (x0 + 10) * s
        cv.paint(fill(sd_box(cv, sx0, s, sx1, h - s, 2.0)), lerp(_mixc(ring, WHITE, 0.25), _mixc(ring, OUT, 0.4),
                                                                   linear(cv, 0, s, 0, h - s)))
        bowl = sd_box(cv, sx0 + s, 2 * s, sx1 - s, h - 2 * s, 2.0)
        cx, cy = (sx0 + sx1) / 2, h / 2
        glow = np.clip(1.0 - np.hypot(cv.x - cx + 2, cv.y - cy + 2) / 9.0, 0.0, 1.0)
        cv.paint(fill(bowl), lerp(_mixc(socket_bg, OUT, 0.35), _mixc(socket_bg, WHITE, 0.10), glow))

    def divider(x):
        cv.paint(((cv.x >= x * s) & (cv.x < (x + 1) * s)).astype(np.float64) * fill(outer), OUT)

    if kind == 'def':
        socket(0)
        divider(10)
        cv.paint(fill(sd_box(cv, 11 * s, 2 * s, (def_w - 2) * s, 3 * s)), OUT, 0.35)
    else:
        if kind == 'hp':
            sx, wx0 = 0, hp_well
        else:
            sx, wx0 = cd_socket, 2
        socket(sx)
        divider(sx + 10 if kind == 'hp' else sx)
        # quarter marks on the well floor
        for q in (1, 2, 3):
            x = (wx0 + round(well * q / 4) - 1) * s
            cv.paint(fill(sd_box(cv, x, h - 4 * s, x + s, h - 2 * s)), accent, 0.4)
    # bright corner chevrons (tooltip brackets)
    for (x, y), (dx, dy) in (((0, 0), (1, 1)), ((box_w, 0), (-1, 1)), ((0, h), (1, -1)), ((box_w, h), (-1, -1))):
        arm = 3.0 * s
        seg = np.minimum(sd_segment(cv, x + dx * 1.2, y + dy * 1.2, x + dx * arm, y + dy * 1.2),
                         sd_segment(cv, x + dx * 1.2, y + dy * 1.2, x + dx * 1.2, y + dy * arm))
        cv.paint(band(seg, 0.9), hi, 0.95)
    pin_box = cv.a[:, box_w - 1].max()
    if pin_box * 255.0 < 1.0:
        raise SystemExit('frame %s: right column empty' % kind)
    return cv.image()


# ----------------------------------------------------------------------------- fill strips

# Grey ramp (tinted per state by the glyph colour): a crisp top edge, gloss, then a deeper foot.
FILL_PROFILE = (226, 255, 250, 238, 228, 218, 208, 199, 190, 180, 168, 150, 128, 108)


def fill_strip(k, well, inner):
    s = FS
    w, h = well * s, inner * s
    px = np.zeros((h, w, 4), dtype=np.uint8)
    width = k * s
    for x in range(width):
        edge = k >= 3 and x >= width - s
        seam = (x % (8 * s)) == 8 * s - 1 and not edge
        for y, v in enumerate(FILL_PROFILE):
            c = v
            if edge:
                c = min(255, v + 40)
            elif seam and 2 <= y <= 11:
                c = int(v * 0.84)
            px[y, x] = (c, c, c, 255)
    from PIL import Image
    return Image.fromarray(px, 'RGBA')


# ----------------------------------------------------------------------------- icons (7 GUI px)

def _icon():
    n = 7 * IS
    return Canvas(n, n), n / 2


def _heart_sd(cv, c):
    lobes = np.minimum(sd_circle(cv, c - 5.6, c - 3.8, 6.8), sd_circle(cv, c + 5.6, c - 3.8, 6.8))
    tip = sd_polygon(cv, [(c - 12.2, c - 2.0), (c + 12.2, c - 2.0), (c, c + 12.6)])
    return np.minimum(lobes, tip)


def heart(body, hi_c, lo):
    cv, c = _icon()
    sd = _heart_sd(cv, c)
    cv.paint(fill(sd - 1.8), _mixc(lo, OUT, 0.55))
    cv.paint(fill(sd), lerp(hi_c, lo, linear(cv, c - 9, c - 9, c + 7, c + 9)))
    cv.paint(fill(sd) * fill(sd + 3.2), body, 0.55)
    cv.paint(fill(sd_circle(cv, c - 6.0, c - 5.5, 2.6)), WHITE, 0.75)
    return cv


def hourglass(body, hi_c, lo):
    cv, c = _icon()
    glass = np.minimum(sd_polygon(cv, [(c - 8, 5), (c + 8, 5), (c + 1.2, c), (c - 1.2, c)]),
                       sd_polygon(cv, [(c - 1.2, c), (c + 1.2, c), (c + 8, 23), (c - 8, 23)]))
    caps = np.minimum(sd_box(cv, 3, 1.5, 25, 5.5, 1.2), sd_box(cv, 3, 22.5, 25, 26.5, 1.2))
    sd = np.minimum(glass, caps)
    cv.paint(fill(sd - 1.8), OUT)
    cv.paint(fill(glass), hx('#2a2433'), 0.9)
    sand = np.minimum(sd_polygon(cv, [(c - 6, 22), (c + 6, 22), (c, 15)]),
                      sd_polygon(cv, [(c - 4.2, 8.5), (c + 4.2, 8.5), (c, 12.5)]))
    cv.paint(fill(sand) * fill(glass), hi_c)
    cv.paint(fill(caps), lerp(_mixc(body, WHITE, 0.3), lo, linear(cv, 0, 1.5, 0, 6)))
    cv.paint(fill(sd_box(cv, 3, 22.5, 25, 26.5, 1.2)), lo, 0.5)
    return cv


def check(body, hi_c, lo):
    cv, c = _icon()
    sd = np.minimum(sd_segment(cv, 4.5, 15.5, 11.0, 22.0), sd_segment(cv, 11.0, 22.0, 24.0, 6.0))
    cv.paint(band(sd, 5.0), OUT)
    cv.paint(band(sd, 3.2), lerp(hi_c, body, linear(cv, 0, 6, 0, 22)))
    return cv


def cross(body, hi_c, lo):
    cv, c = _icon()
    sd = np.minimum(sd_segment(cv, 6, 6, 22, 22), sd_segment(cv, 22, 6, 6, 22))
    cv.paint(band(sd, 5.0), OUT)
    cv.paint(band(sd, 3.2), lerp(hi_c, body, linear(cv, 0, 6, 0, 22)))
    return cv


def shield(body, hi_c, lo):
    cv, c = _icon()
    sd = sd_polygon(cv, [(3, 3), (25, 3), (25, 13), (c, 26.5), (3, 13)])
    cv.paint(fill(sd - 1.8), OUT)
    cv.paint(fill(sd), lerp(_mixc(body, WHITE, 0.2), hi_c, linear(cv, 0, 3, 0, 26)))
    cv.paint(fill(sd + 3.4), lerp(hi_c, lo, linear(cv, 0, 3, 0, 26)))
    cv.paint(fill(sd_polygon(cv, [(c, 7.5), (c + 5, 12.5), (c, 19), (c - 5, 12.5)])), _mixc(body, WHITE, 0.45))
    return cv


def star(body, hi_c, lo):
    cv, c = _icon()
    pts = []
    for k in range(10):
        r = 13.2 if k % 2 == 0 else 5.6
        a = math.radians(-90 + k * 36)
        pts.append((c + r * math.cos(a), c + 0.8 + r * math.sin(a)))
    sd = sd_polygon(cv, pts)
    cv.paint(fill(sd - 1.8), _mixc(lo, OUT, 0.6))
    cv.paint(fill(sd), lerp(hi_c, lo, linear(cv, c - 8, c - 10, c + 8, c + 10)))
    cv.paint(fill(sd + 2.6), body, 0.45)
    cv.paint(fill(sd_circle(cv, c - 2, c - 2.5, 1.8)), WHITE, 0.8)
    return cv


ICON_FN = {'heart': heart, 'hourglass': hourglass, 'check': check, 'cross': cross, 'shield': shield, 'star': star}


def icon(name, colours):
    body, hi_c, lo = colours
    fn = ICON_FN['heart'] if name.startswith('heart') else ICON_FN[name]
    cv = fn(body, hi_c, lo)
    pin_advance(cv)
    return cv.image()
