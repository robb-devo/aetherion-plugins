"""Pixel art for the player HUD bars (called by gen_hud.py).

Frames, fill strips and icons hold 2 texels per GUI pixel, like the pack's 32 px items and the status
panel; every glyph keeps its GUI size and advance, so HudGlyphs and the shader need no change. The 3x5
pixel font stays 1:1 on purpose: it is tuned per pixel for legibility on any fill.
"""
import numpy as np
from PIL import Image

from px_art import (AMBER, ASH, CRIMSON, EMERALD, GOLD, SKY, SLATE, STEEL, TEAL, WHITE, Px, edge, hx, mix, ramp,
                    rim, shrink)

S = 2
FS = IS = S

POISON = ramp('#0f2008', '#24480f', '#386b17', '#5fae2e', '#9fe064', '#e2ffb8')
WITHER = ramp('#08070a', '#1c1920', '#2c2832', '#3e3946', '#6d6677', '#a59eb0')
FROST = ramp('#0b2236', '#1d5578', '#3f86ad', '#7cc8ec', '#c2ecff', '#f4fcff')
DENY = ramp('#2a0606', '#5c0e0e', '#a3201a', '#e8452f', '#ff8a6a', '#ffd0bc')
READY = EMERALD
ACCENTS = {'hp': CRIMSON, 'cd': GOLD, 'cd_deny': DENY, 'cd_ready': READY, 'def': TEAL}


# ----------------------------------------------------------------------------- frames

def frame(kind, frame_w, frame_h, def_w, well, hp_well, cd_socket):
    """One HUD frame cell (frame_w x frame_h GUI px). Outline, a bevelled accent ring (lit top-left), a
    recessed well and an icon socket."""
    acc = ACCENTS[kind]
    w, h = frame_w * S, frame_h * S
    box_w = (def_w if kind == 'def' else frame_w) * S
    p = Px(w, h)
    g = p.g
    box = g.rect(0, 0, box_w, h)
    for x, y in ((0, 0), (box_w - 1, 0), (0, h - 1), (box_w - 1, h - 1)):
        box[y, x] = False
    well_c = mix(SLATE[1], acc[1], 0.18)
    p.put(box, well_c)
    p.put(box & ~shrink(box), acc[0])
    ring = shrink(box) & ~shrink(shrink(shrink(box)))
    p.put(ring, acc[3])
    p.put(ring & (g.y < 2) | ring & (g.x < 2), acc[4])
    p.put(ring & (g.y >= h - 2) | ring & (g.x >= box_w - 2), acc[1])
    p.put(ring & ((g.y == 2) & (g.x >= 2) & (g.x < box_w - 2)), acc[3])
    p.put(ring & ((g.y == h - 3) & (g.x >= 3) & (g.x < box_w - 3)), acc[2])
    p.at(1, 1, acc[5])
    p.at(2, 1, acc[5])
    p.at(1, 2, acc[5])
    inside = shrink(shrink(shrink(box)))
    # lip shadow under the top ring and a lit floor line
    p.put(inside & (g.y < 4), mix(well_c, acc[0], 0.6))
    p.put(inside & (g.y >= h - 4) & (g.y < h - 3), mix(well_c, acc[2], 0.35))

    def socket(x0):
        """Icon bowl from GUI x0 (11 px incl. its divider)."""
        bx0, bx1 = x0 * S + 3, (x0 + 10) * S - 1
        bowl = g.rect(bx0, 3, bx1, h - 3)
        bowl[3, bx0] = bowl[3, bx1 - 1] = bowl[h - 4, bx0] = bowl[h - 4, bx1 - 1] = False
        p.put(bowl, mix(SLATE[2], acc[1], 0.35))
        p.put(bowl & (g.y < 5), mix(SLATE[0], acc[0], 0.5))
        p.put(bowl & (g.y >= h - 5), mix(SLATE[3], acc[2], 0.3))

    def divider(x):
        cols = g.rect(x * S - 1, 0, x * S + 1, h) & box
        p.put(cols, acc[0])
        p.put(g.rect(x * S + 1, 3, x * S + 2, h - 3), acc[2])

    if kind == 'def':
        socket(0)
        divider(10)
    else:
        sx, wx0 = (0, hp_well) if kind == 'hp' else (cd_socket, 2)
        socket(sx)
        divider(sx + 10 if kind == 'hp' else sx)
        for q in (1, 2, 3):
            x = (wx0 + round(well * q / 4) - 1) * S
            p.put(g.rect(x, h - 6, x + 1, h - 3), mix(well_c, acc[3], 0.55))
    if p.px[:, box_w - 1, 3].max() == 0:
        raise SystemExit('frame %s: right column empty' % kind)
    return p.image()


# ----------------------------------------------------------------------------- fill strips

# Grey bands (tinted per state by the glyph colour): a bright top edge, a flat body, a deeper foot.
FILL_PROFILE = (255, 246, 232, 232, 214, 214, 214, 214, 198, 198, 198, 172, 172, 134)


def fill_strip(k, well, inner):
    w, h = well * S, inner * S
    px = np.zeros((h, w, 4), dtype=np.uint8)
    width = k * S
    for x in range(width):
        cap = k >= 3 and x >= width - S
        seam = (x % (8 * S)) == 8 * S - 1 and not cap
        for y, v in enumerate(FILL_PROFILE):
            c = v
            if cap:
                c = min(255, v + 38)
            elif seam and 2 <= y <= 11:
                c = int(v * 0.8)
            px[y, x] = (c, c, c, 255)
    return Image.fromarray(px, 'RGBA')


# ----------------------------------------------------------------------------- icons (7 GUI px = 14 texels)

def _r(rmp, **extra):
    pal = {'o': rmp[0], 'd': rmp[1], 's': rmp[2], 'm': rmp[3], 'l': rmp[4], 'h': rmp[5], 'W': WHITE}
    pal.update(extra)
    return pal


HEART = ['..............',
         '..oooo..oooo..',
         '.ohhlloolmmso.',
         'ohWllllmmmmmso',
         'ohllmmmmmmmmso',
         'ohlmmmmmmmmmso',
         'olmmmmmmmmmsso',
         '.olmmmmmmmsso.',
         '..ommmmmmsso..',
         '...ommmmsso...',
         '....ommsso....',
         '.....osso.....',
         '......oo......',
         '..............']

HOURGLASS = ['.oooooooooooo.',
             '.ohllllllllso.',
             '.oooooooooooo.',
             '..oWaaaaaaGo..',
             '...oWaaaaGo...',
             '....oWaaGo....',
             '.....oWao.....',
             '.....oGWo.....',
             '....oG.aWo....',
             '...oG..a.Wo...',
             '..oGAaaaaAWo..',
             '.oooooooooooo.',
             '.ohllllllllso.',
             '.oooooooooooo.']


def _shape_icon(mask, rmp):
    p = Px(14, 14)
    p.shade(mask, rmp, soft=True)
    p.put(mask & ~shrink(mask), rmp[0])
    return p


def check_icon():
    p = Px(14, 14)
    g = p.g
    m = g.line(2, 7, 5, 10, 2) | g.line(5, 10, 11, 3, 2) | g.line(3, 7, 5, 9, 2)
    out = rim(m, True) | m
    p.put(out, READY[0])
    p.put(m, READY[3])
    p.put(m & edge(m, -1, 0) | m & edge(m, 0, -1), READY[4])
    p.put(m & edge(m, 1, 0) & edge(m, 0, 1), READY[2])
    return p


def cross_icon():
    p = Px(14, 14)
    g = p.g
    m = g.line(3, 3, 9, 9, 2) | g.line(9, 3, 3, 9, 2)
    out = rim(m, True) | m
    p.put(out, DENY[0])
    p.put(m, DENY[3])
    p.put(m & (edge(m, -1, 0) | edge(m, 0, -1)), DENY[4])
    return p


def shield_icon():
    p = Px(14, 14)
    p.sprite(['.oooooooooooo.',
              '.ohllllllllso.',
              '.ohlmmmmmmmso.',
              '.olmmmWWmmmso.',
              '.olmmmWWmmmso.',
              '.olmWWWWWWmso.',
              '.olmWWWWWWmso.',
              '.olmmmWWmmsso.',
              '..olmmWWmmso..',
              '..olmmmmmsso..',
              '...olmmmsso...',
              '....olmsso....',
              '.....ossdo....',
              '......oo......'], _r(TEAL, W=TEAL[5]))
    return p


def star_icon():
    p = Px(14, 14)
    p.sprite(['......oo......',
              '......oho.....',
              '.....ohlmo....',
              '.....ohlmo....',
              'oooooohlmooooo',
              'ohhllllmmmmmso',
              '.ohllWlmmmmso.',
              '..olllmmmmso..',
              '...olmmmmso...',
              '..olmmmmmmso..',
              '..olmmsosmso..',
              '.olmso..osmso.',
              '.olso....osso.',
              '.oo........oo.'], _r(AMBER))
    return p


HEART_RAMPS = {'heart': CRIMSON, 'heart_poison': POISON, 'heart_wither': WITHER, 'heart_frozen': FROST,
               'heart_gold': AMBER}


def icon(name):
    if name in HEART_RAMPS:
        p = Px(14, 14)
        p.sprite(HEART, _r(HEART_RAMPS[name]))
    elif name == 'hourglass':
        p = Px(14, 14)
        p.sprite(HOURGLASS, _r(GOLD, a=AMBER[4], A=AMBER[2], G=STEEL[3]))
    else:
        p = {'check': check_icon, 'cross': cross_icon, 'shield': shield_icon, 'star': star_icon}[name]()
    p.pin_advance()
    return p.image()
