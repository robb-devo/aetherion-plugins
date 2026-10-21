"""Aetherion HUD: the status frame (top left) and the radar block (top right).

Usage: python gen_carrier.py [preview_dir]

The HUD is the title of an invisible BLUE boss bar (Java: de.aetherion.items.hud.HudCarrier). A boss bar
never fades and nothing else writes into it, unlike the ActionBar the old panel rode on. Every glyph of
that title is coloured (76, 4 * code, palette index) and the text shader below moves it: code 0..39 is
the status frame (y + 2 * code), 40..63 the radar block (see ``shader``). The shader also scales the HUD
to a fixed share of the screen (``eff`` px per art px) whatever the GUI scale is, in whole screen pixels.

Art is drawn 1:1 (one texel = one HUD pixel) in the chest-menu chrome palette (gen_gui_chrome.py).

Writes into AetherionItems/resourcepack:
  assets/aetherion/font/carrier.json + textures/font/carrier/*.png
  assets/minecraft/shaders/core/rendertype_text.{json,vsh}   palette parsed from PanelPalette.java
  assets/minecraft/shaders/core/position_tex.{vsh,fsh}       hides the carrier's own bar
  assets/minecraft/textures/gui/sprites/boss_bar/blue_background.png   alpha 253 = "maybe the carrier"
With a preview_dir it also writes hud_preview.png there (a mock-up at 3x, for checking the art).
"""
import json
import math
import os
import re
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen_gui_chrome import BEV_HI, BEV_LO, BODY, OUT, RIM_HI, RIM_LO, RIM_MID, TRAY, WELL_SH  # noqa: E402
from gen_gui_chrome import FONT as BIG  # noqa: E402  5x7 caps of the menu titles
from gen_hud import FONT as SMALL  # noqa: E402  3x5 HUD digits and caps
from px_art import AMBER, CRIMSON, EMERALD, GOLD, ROSE, SKY, STEEL, TEAL, VIOLET, WHITE, Px, hx, mix, shrink  # noqa: E402

PACK = os.path.join(HERE, '..', 'resourcepack')
JAVA = os.path.join(HERE, '..', 'src', 'main', 'java', 'de', 'aetherion', 'items', 'hud', 'PanelPalette.java')

NEG_SPACE, POS_SPACE = 0xF801, 0xF821
LILAC = hx('#9d86cf')
LAV = mix(LILAC, (255, 255, 255), 0.25)
LAV_DIM = mix(BODY, LILAC, 0.42)
LAV_FAINT = mix(BODY, LILAC, 0.22)
INK = hx('#0a090e')
BODY_A = 236                     # frame body opacity: the world shows through a hair

# ----------------------------------------------------------------------------- layout (mirrored in Java)

FRAME_W = 124
CREST = (4, 4, 26, 24)           # x, y, w, h
BAR_X, BAR_W = 33, 87            # HP / stamina / XP frames
HP_Y, HP_H = 4, 11
ST_Y, ST_H = 16, 7
XP_Y, XP_H = 24, 4
INFO_Y = 32
CD_Y0, CD_STEP, CD_H = 44, 12, 9
CD_X, CD_W = 4, 116
SOCKET = 9                       # icon socket width inside HP and cooldown frames (divider at x = 9)
SOCKETED = ('hp', 'cd', 'cd_ready', 'cd_deny')
PLATE_H = (43, 57, 69, 81)       # by cooldown rows 0..3
RADAR = 64
LOC_Y, LOC_H = 68, 13            # place plate under the radar (text row at 70)
CHIP_Y, CHIP_H = 84, 11          # weather chip (text row at 86)

# ----------------------------------------------------------------------------- codepoints (mirrored in CarrierFont.java)

# Glyph tops sit on even y (the colour code steps 2 px); "phase 1" twins sit one pixel lower.
CP_PLATE = 0xE700                # + rows (0..3)
CP_CREST = 0xE704
CP_FRAME = 0xE705                # hp, stamina, xp, cd, cd_ready, cd_deny
FRAMES = ('hp', 'stamina', 'xp', 'cd', 'cd_ready', 'cd_deny')
CP_ICON = (0xE710, 0xE760)       # phase 0 / 1
ICONS = ('heart', 'heart_poison', 'heart_wither', 'heart_frozen', 'heart_gold', 'shield', 'coin', 'shard',
         'hourglass', 'check', 'cross', 'star', 'cloud', 'divider')
CP_FILL = {'hp': 0xE720, 'stamina': 0xE728, 'xp': 0xE730, 'cd': 0xE738}   # + log2(width); 1 px under the frame top
FILL_ROWS = {'hp': (255, 236, 214, 204, 204, 204, 196, 176, 146),
             'stamina': (255, 232, 212, 194, 162),
             'xp': (255, 186),
             'cd': (255, 228, 206, 206, 200, 182, 156)}
CP_RADAR = 0xE740
MARKERS = ('quest', 'quest_up', 'quest_down', 'npc', 'party', 'boss', 'north')
CP_MARK = 0xE741
CP_LOC = 0xE750                  # left cap, right cap, then mids of 1..64; 2 px under the code's row
CP_CHIP = 0xE770                 # same for the weather chip
CP_SMALL = (0xE780, 0xE800)      # 3x5 text, glyph top at 0 / 1
CP_BIG = (0xE880, 0xE900)        # 5x7 text, glyph top at 0 / 1


def out(*parts):
    p = os.path.join(PACK, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


# ----------------------------------------------------------------------------- helpers

def canvas(w, h):
    return Px(w, h)


def frame_rect(p, x0, y0, w, h, outline=OUT, cut=True):
    """1px outline round a w x h box, corners cut."""
    x1, y1 = x0 + w - 1, y0 + h - 1
    for x in range(x0 + (1 if cut else 0), x1 + (0 if cut else 1)):
        p.at(x, y0, outline)
        p.at(x, y1, outline)
    for y in range(y0 + (1 if cut else 0), y1 + (0 if cut else 1)):
        p.at(x0, y, outline)
        p.at(x1, y, outline)


def fill(p, x0, y0, x1, y1, c, a=255):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p.at(x, y, c, a)


def diamond(p, cx, cy, r, c, core=None):
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if abs(x - cx) + abs(y - cy) <= r:
                p.at(x, y, c)
    if core:
        p.at(cx, cy, core)


def pin(p):
    """Advance = last opaque column + 1: an alpha-1 texel in the last column pins it (shader drops it)."""
    if p.px[:, -1, 3].max() == 0:
        p.px[p.h // 2, -1] = (0, 0, 0, 1)


# ----------------------------------------------------------------------------- status frame

def plate(rows):
    h = PLATE_H[rows]
    p = canvas(FRAME_W, h)
    fill(p, 1, 1, FRAME_W - 2, h - 2, BODY, BODY_A)
    # bevel: light on the top/left lip, shadow on the bottom/right one
    for x in range(2, FRAME_W - 2):
        p.at(x, 1, BEV_HI)
        p.at(x, h - 2, BEV_LO)
    for y in range(2, h - 2):
        p.at(1, y, BEV_HI)
        p.at(FRAME_W - 2, y, BEV_LO)
    frame_rect(p, 0, 0, FRAME_W, h)
    # lilac corner brackets, like the menu frames
    for cx, cy, sx, sy in ((3, 3, 1, 1), (FRAME_W - 4, 3, -1, 1), (3, h - 4, 1, -1), (FRAME_W - 4, h - 4, -1, -1)):
        for k in range(3):
            p.at(cx + sx * k, cy - sy, LAV_DIM)
            p.at(cx - sx, cy + sy * k, LAV_DIM)
        p.at(cx - sx, cy - sy, LAV)
    if rows:
        # divider between the vitals and the cooldown rows: a hairline with a centred stud
        y = CD_Y0 - 3
        for x in range(8, FRAME_W - 8):
            p.at(x, y, LAV_FAINT)
        for x in (7, FRAME_W - 8):
            p.at(x, y, LAV_DIM)
        diamond(p, FRAME_W // 2, y, 1, LAV_DIM, LAV)
    pin(p)
    return p.image()


def crest():
    """Level socket: an octagon with a gold rim, the number is drawn on top by the Java side."""
    x0, y0, w, h = CREST
    p = canvas(x0 + w, y0 + h)
    cut = 3

    def inside(x, y, inset):
        lx, ly = x - x0 - inset, y - y0 - inset
        ww, hh, c = w - 2 * inset, h - 2 * inset, max(0, cut - inset)
        if lx < 0 or ly < 0 or lx >= ww or ly >= hh:
            return False
        return lx + ly >= c and (ww - 1 - lx) + ly >= c and lx + (hh - 1 - ly) >= c and (ww - 1 - lx) + (hh - 1 - ly) >= c

    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if not inside(x, y, 0):
                continue
            if not inside(x, y, 1):
                p.at(x, y, OUT)
            elif not inside(x, y, 2):
                lit = (x - x0) + (y - y0) < (w + h) * 0.5
                p.at(x, y, GOLD[4] if lit else GOLD[2])
            elif not inside(x, y, 3):
                p.at(x, y, GOLD[0])
            else:
                p.at(x, y, hx('#17141f'))
    # inner glow on the top edge of the face, a stud under the number
    for x in range(x0 + 6, x0 + w - 6):
        p.at(x, y0 + 3, hx('#221d2c'))
    diamond(p, x0 + w // 2, y0 + h - 4, 1, GOLD[1], GOLD[3])
    return p.image().crop((x0, y0, x0 + w, y0 + h))


def bar_frame(kind):
    w = CD_W if kind.startswith('cd') else BAR_W
    h = {'hp': HP_H, 'stamina': ST_H, 'xp': XP_H}.get(kind, CD_H)
    p = canvas(w, h)
    well = hx('#121018')
    fill(p, 1, 1, w - 2, h - 2, well)
    for x in range(1, w - 1):
        p.at(x, 1, hx('#0c0b10'))
    if kind in SOCKETED:
        # icon socket on the left: a raised stone face, then a divider
        fill(p, 1, 1, SOCKET - 1, h - 2, hx('#262331'))
        for x in range(1, SOCKET):
            p.at(x, 1, hx('#3a3648'))
            p.at(x, h - 2, hx('#1a1822'))
        for y in range(1, h - 1):
            p.at(SOCKET, y, OUT)
    frame_rect(p, 0, 0, w, h)
    if kind in ('cd_ready', 'cd_deny'):
        edge = EMERALD[3] if kind == 'cd_ready' else CRIMSON[3]
        for x in range(1, w - 1):
            p.at(x, 0, mix(OUT, edge, 0.55))
            p.at(x, h - 1, mix(OUT, edge, 0.35))
    pin(p)
    return p.image()


def fill_strip(kind, width):
    rows = FILL_ROWS[kind]
    p = canvas(64, len(rows))
    for y, v in enumerate(rows):
        for x in range(width):
            p.at(x, y, (v, v, v))
    return p.image()


# 7x7 icons, drawn by hand. o = tinted outline, letters index a ramp (d dark, m mid, l light, h highlight).
ICON_ART = {
    'heart': ['.oo.oo.', 'ohmomlo', 'ommmmmo', 'ommmmdo', '.ommdo.', '..odo..', '...o...'],
    'shield': ['ooooooo', 'ohlolmo', 'ollolmo', 'olmodmo', '.olodo.', '..odo..', '...o...'],
    'coin': ['..ooo..', '.ohllo.', 'ohlmmdo', 'olmmmdo', 'olmmddo', '.odddo.', '..ooo..'],
    'shard': ['...o...', '..oho..', '.ohlmo.', '.olmdo.', '.olmdo.', '..odo..', '...o...'],
    'hourglass': ['ooooooo', '.ohlmo.', '..olo..', '...o...', '..omo..', '.ommdo.', 'ooooooo'],
    'check': ['.......', '.....oo', '....olo', 'oo.olo.', 'olollo.', '.ollo..', '..oo...'],
    'cross': ['oo...oo', 'olo.olo', '.olllo.', '..olo..', '.olllo.', 'olo.olo', 'oo...oo'],
    'star': ['...o...', '..oho..', 'oohlmoo', 'ohllmmo', '.olmmo.', 'olmomdo', 'oo...oo'],
    'cloud': ['.......', '..ooo..', '.ohllo.', 'ohllllo', 'olllmmo', '.ooooo.', '.......'],
    'divider': ['.......', '.......', '...o...', '..olo..', '...o...', '.......', '.......'],
}
HEART_RAMPS = {'heart': CRIMSON, 'heart_poison': EMERALD, 'heart_wither': ((20, 18, 24), (44, 40, 50), (62, 57, 70),
               (86, 80, 96), (130, 122, 142), (180, 172, 192)), 'heart_frozen': SKY, 'heart_gold': GOLD}
ICON_RAMPS = {'shield': STEEL, 'coin': GOLD, 'shard': SKY, 'hourglass': AMBER, 'check': EMERALD, 'cross': CRIMSON,
              'star': GOLD, 'cloud': STEEL, 'divider': VIOLET}


def icon(name):
    art = ICON_ART['heart' if name.startswith('heart') else name]
    rmp = HEART_RAMPS.get(name) or ICON_RAMPS[name]
    pal = {'o': mix(rmp[0], INK, 0.4), 'd': rmp[2], 'm': rmp[3], 'l': rmp[4], 'h': rmp[5]}
    p = canvas(7, 7)
    p.sprite(art, pal)
    pin(p)
    return p.image()


# ----------------------------------------------------------------------------- text

def text_sheet(font, core_h, top):
    """16 x 6 cells (U+0020..007F), each glyph = core + 1px ink outline, ``top`` px of headroom."""
    cw = max(len(r[0]) for r in font.values()) + 2
    ch = core_h + 2 + top
    im = Image.new('RGBA', (16 * cw, 6 * ch), (0, 0, 0, 0))
    px = im.load()
    for code in range(0x20, 0x80):
        c = chr(code)
        if c not in font or c == ' ':
            continue
        rows = font[c]
        cx, cy = ((code - 0x20) % 16) * cw, ((code - 0x20) // 16) * ch + top
        core = {(x + 1, y + 1) for y, r in enumerate(rows) for x, v in enumerate(r) if v == '#'}
        # a hard drop shadow down-right and a faint one-texel rim on the other sides: readable on any
        # fill or sky without boxing the word in
        for (x, y) in core:
            for dx, dy, a in ((1, 0, 225), (0, 1, 225), (1, 1, 225), (-1, 0, 70), (0, -1, 70)):
                q = (x + dx, y + dy)
                if q not in core:
                    old = px[cx + q[0], cy + q[1]][3]
                    px[cx + q[0], cy + q[1]] = INK + (max(a, old),)
        for (x, y) in core:
            px[cx + x, cy + y] = (255, 255, 255, 255)
    return im, cw, ch


BIG_EXTRA = {
    '0': [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
    '1': [".#.", "##.", ".#.", ".#.", ".#.", ".#.", "###"],
    '2': [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    '3': ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
    '4': ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    '5': ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    '6': [".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."],
    '7': ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
    '8': [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    '9': [".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."],
    "'": ["#", "#", ".", ".", ".", ".", "."],
    '-': ["...", "...", "...", "###", "...", "...", "..."],
    '.': [".", ".", ".", ".", ".", ".", "#"],
    '(': [".#", "#.", "#.", "#.", "#.", "#.", ".#"],
    ')': ["#.", ".#", ".#", ".#", ".#", ".#", "#."],
}


# ----------------------------------------------------------------------------- radar block

def radar():
    n = RADAR
    p = canvas(n, n)
    g = p.g
    c = n / 2
    d = g.dist(c, c)
    ang = g.angle(c, c)
    t = np.cos(ang - math.radians(225))           # light from the top left
    disc = d <= 31.6
    face = d <= 27.4
    ring = disc & ~face
    p.put(ring, RIM_MID)
    p.put(ring & (t > 0.35), RIM_HI)
    p.put(ring & (t < -0.45), RIM_LO)
    p.put(ring & (np.abs(d - 29.5) < 0.6) & (t > -0.2), mix(RIM_MID, LILAC, 0.35))
    p.put(disc & ~shrink(disc), OUT)
    p.put(face & ~shrink(face), OUT)
    inner = shrink(face)
    p.put(inner, hx('#14121b'), 222)
    p.put(inner & (d < 15.5), hx('#17151f'), 222)
    p.put(inner & (d < 6.5), hx('#1a1823'), 222)
    p.put(inner & (d > 24.5), hx('#0f0d15'), 230)
    # range rings and a faint crosshair
    for r, dashes in ((9.0, 12), (18.0, 24)):
        band = inner & (np.abs(d - r) < 0.5)
        on = np.mod(ang / (2 * math.pi) * dashes, 1.0) < 0.5
        p.put(band & on, hx('#2a2638'), 236)
    for k in range(4):
        a = math.radians(k * 90)
        for r in np.arange(6.0, 25.0, 1.0):
            x, y = int(c + math.cos(a) * r), int(c + math.sin(a) * r)
            if k == 2:
                x -= 1
            if k == 3:
                y -= 1
            if inner[min(n - 1, max(0, y)), min(n - 1, max(0, x))] and int(r) % 2 == 0:
                p.at(x, y, hx('#24212f'), 236)
    # four lilac studs on the diagonals, a forward notch at the top
    for k in range(4):
        a = math.radians(45 + 90 * k)
        sx, sy = int(round(c - 0.5 + math.cos(a) * 29.5)), int(round(c - 0.5 + math.sin(a) * 29.5))
        diamond(p, sx, sy, 1, LAV_DIM, LAV)
    # bearing ticks on the inner rim at the other three quarters
    for x0, y0, x1, y1 in ((61, 31, 59, 31), (61, 32, 59, 32), (31, 61, 31, 59), (32, 61, 32, 59),
                           (2, 31, 4, 31), (2, 32, 4, 32)):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                p.at(x, y, LAV_DIM)
    for y, half in ((0, 0), (1, 1), (2, 2), (3, 1), (4, 0)):
        for x in range(32 - 1 - half, 32 + half):
            p.at(x, y, LAV if y < 3 else LAV_DIM)
        p.at(32 - 2 - half, y, OUT)
        p.at(32 + half, y, OUT)
    # player arrow, forward is up
    arrow = ['...o...',
             '..oho..',
             '.ohhlo.',
             '.ohhlo.',
             'ohhhllo',
             'ohooolo',
             'oo...oo']
    pal = {'o': OUT, 'h': (255, 255, 255), 'l': hx('#c9c1e0')}
    p.sprite(arrow, pal, 29, 28)
    pin(p)
    return p.image()


MARK_ART = {
    'quest': ['...o...', '..oho..', '.ohllo.', 'ohllmmo', '.olmmo.', '..omo..', '...o...'],
    'quest_up': ['...o...', '..oho..', '.ohllo.', 'ohllmmo', 'ooooooo', '.......', '.......'],
    'quest_down': ['.......', '.......', 'ooooooo', 'ohllmmo', '.olmmo.', '..omo..', '...o...'],
    'npc': ['.ooo.', 'ohllo', 'ollmo', 'olmmo', '.ooo.'],
    'party': ['.ooo.', 'ohllo', 'ollmo', 'olmmo', '.ooo.'],
    'boss': ['.ooooo.', 'ohllllo', 'ololomo', 'olllmmo', '.olomo.', '.ommmo.', '..ooo..'],
    'north': ['.ooooo.', 'oh...ho', 'ohh..ho', 'oh.h.ho', 'oh..hho', 'oh...ho', '.ooooo.'],
}
MARK_RAMPS = {'quest': GOLD, 'quest_up': GOLD, 'quest_down': GOLD, 'npc': ((40, 34, 30), (90, 80, 70),
              (170, 160, 140), (220, 210, 190), (245, 238, 220), (255, 252, 240)), 'party': TEAL, 'boss': CRIMSON,
              'north': ((10, 9, 14), (40, 36, 50), (93, 87, 116), (157, 134, 207), (207, 200, 230), (240, 236, 255))}


def marker(name):
    art = MARK_ART[name]
    rmp = MARK_RAMPS[name]
    pal = {'o': mix(rmp[0], INK, 0.3), 'y': mix(rmp[0], INK, 0.3), 'd': rmp[2], 'm': rmp[3], 'l': rmp[4], 'h': rmp[5],
           '.': None}
    if name == 'north':
        pal = {'o': OUT, 'h': LAV, 'd': LAV_DIM}
        # dark disc behind the letter
        p = canvas(7, 7)
        fill(p, 1, 1, 5, 5, hx('#1b1824'))
        p.sprite(art, pal)
        pin(p)
        return p.image()
    p = canvas(len(art[0]), len(art))
    p.sprite(art, {k: v for k, v in pal.items() if v is not None})
    pin(p)
    return p.image()


def loc_cap(right):
    p = canvas(8, LOC_H)
    body = hx('#1b1823')
    for y in range(LOC_H):
        for x in range(8):
            edge_x = 7 - x if right else x
            if y in (0, LOC_H - 1):
                if edge_x >= 2:
                    p.at(x, y, OUT)
            elif y in (1, LOC_H - 2):
                if edge_x == 1:
                    p.at(x, y, OUT)
                elif edge_x >= 2:
                    p.at(x, y, LAV_DIM)
            else:
                if edge_x == 0:
                    p.at(x, y, OUT)
                elif edge_x == 1:
                    p.at(x, y, LAV_DIM)
                else:
                    p.at(x, y, body, BODY_A)
    diamond(p, 3 if not right else 4, LOC_H // 2, 2, LAV_DIM)
    diamond(p, 3 if not right else 4, LOC_H // 2, 1, LAV, (255, 255, 255))
    pin(p)
    return p.image()


def chip_cap(right):
    p = canvas(4, CHIP_H)
    body = hx('#1b1823')
    for y in range(CHIP_H):
        for x in range(4):
            e = 3 - x if right else x
            if y in (0, CHIP_H - 1):
                if e >= 1:
                    p.at(x, y, OUT)
            elif e == 0:
                p.at(x, y, OUT)
            elif y in (1, CHIP_H - 2) or e == 1:
                p.at(x, y, LAV_FAINT)
            else:
                p.at(x, y, body, BODY_A)
    pin(p)
    return p.image()


def chip_mid(width):
    p = canvas(64, CHIP_H)
    body = hx('#1b1823')
    for x in range(width):
        p.at(x, 0, OUT)
        p.at(x, 1, LAV_FAINT)
        for y in range(2, CHIP_H - 2):
            p.at(x, y, body, BODY_A)
        p.at(x, CHIP_H - 2, LAV_FAINT)
        p.at(x, CHIP_H - 1, OUT)
    return p.image()


def loc_mid(width):
    p = canvas(64, LOC_H)
    body = hx('#1b1823')
    for x in range(width):
        p.at(x, 0, OUT)
        p.at(x, 1, LAV_DIM)
        for y in range(2, LOC_H - 2):
            p.at(x, y, body, BODY_A)
        p.at(x, LOC_H - 2, LAV_DIM)
        p.at(x, LOC_H - 1, OUT)
    return p.image()


# ----------------------------------------------------------------------------- shader

def palette():
    src = open(JAVA, encoding='utf-8').read()
    block = src[src.index('palette:start'):src.index('palette:end')]
    return [int(h, 16) for h in re.findall(r'0x([0-9A-Fa-f]{6})', block)]


SHADER_JSON = {
    "vertex": "rendertype_text",
    "fragment": "rendertype_text",
    "samplers": [{"name": "Sampler0"}, {"name": "Sampler2"}],
    "uniforms": [
        {"name": "ModelViewMat", "type": "matrix4x4", "count": 16,
         "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
        {"name": "ProjMat", "type": "matrix4x4", "count": 16,
         "values": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},
        {"name": "ColorModulator", "type": "float", "count": 4, "values": [1.0, 1.0, 1.0, 1.0]},
        {"name": "FogStart", "type": "float", "count": 1, "values": [0.0]},
        {"name": "FogEnd", "type": "float", "count": 1, "values": [1.0]},
        {"name": "FogColor", "type": "float", "count": 4, "values": [0.0, 0.0, 0.0, 0.0]},
        {"name": "FogShape", "type": "int", "count": 1, "values": [0]},
        {"name": "ScreenSize", "type": "float", "count": 2, "values": [1.0, 1.0]}
    ]
}


def shader(pal):
    entries = ',\n    '.join('vec3(%.4f, %.4f, %.4f)' % ((c >> 16 & 255) / 255, (c >> 8 & 255) / 255, (c & 255) / 255)
                            for c in pal)
    return f'''#version 150

#moj_import <fog.glsl>

// Vanilla rendertype_text plus the Aetherion HUD (AetherionItems/tools/gen_carrier.py).
// The HUD is the title of the first boss bar (text y = 3). Its glyphs are coloured (76, 4 * code, b):
//   code  0..39  status frame, top left; y += 2 * code; b = palette index
//   code 40..63  radar block, top right; state = (code - 40) / 8 pushes it under the vanilla effect icons
//                (top at 3 / 27 / 52 GUI px), part = (code - 40) % 8: 0 disc, 1 marker (y = b - 64),
//                2..7 a label row at y = 66 + 4 * (part - 2).
// The HUD is drawn at eff screen px per art px: the GUI scale, capped by the screen size, so a huge GUI
// scale does not blow it up. Their drop shadows (19, code, b / 4) are dropped. Only vertices at GUI y 8..100
// are looked at: the title's glyphs reach from y 10 (top) to y 91 (the tallest frame plate). Everything else is vanilla.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec2 ScreenSize;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

const int PAL_N = {len(pal)};
const vec3 PALETTE[PAL_N] = vec3[](
    {entries}
);

const float MARGIN = 3.0;
const float RADAR = {float(RADAR):.1f};

void main() {{
    vec3 pos = Position;
    vec4 col = Color;
    bool drop = false;
    if (ProjMat[3][3] == 1.0 && ProjMat[2][3] == 0.0 && Position.y > 8.0 && Position.y < 100.0) {{
        ivec3 c = ivec3(round(Color.rgb * 255.0));
        if (c.r == 76 && (c.g & 3) == 0) {{
            int code = c.g >> 2;
            float guiW = 2.0 / ProjMat[0][0];
            bool sized = ScreenSize.x > 0.5;
            float gui = sized ? max(1.0, floor(ScreenSize.x / guiW + 0.5)) : 1.0;
            float eff = sized ? clamp(floor(min(ScreenSize.y / 320.0, ScreenSize.x / 520.0)), 1.0, gui) : gui;
            float k = eff / gui;
            float startX = floor(ceil(guiW - 0.01) * 0.5);
            vec2 local = vec2(Position.x - startX, Position.y - 10.0);
            col.rgb = PALETTE[min(c.b, PAL_N - 1)];
            if (code < 40) {{
                local.y += float(code) * 2.0;
                pos.xy = (vec2(MARGIN) + local) * k;
            }} else {{
                int part = (code - 40) - ((code - 40) / 8) * 8;
                int state = (code - 40) / 8;
                float top = state == 0 ? 3.0 : (state == 1 ? 27.0 : 52.0);
                if (part == 1) {{
                    local.y += float(c.b) - 64.0;
                    col.rgb = vec3(1.0);
                }} else if (part >= 2) {{
                    local.y += 66.0 + 4.0 * float(part - 2);
                }}
                float left = floor(guiW * gui / eff + 0.001) - MARGIN - RADAR;
                pos.x = (left + local.x) * k;
                pos.y = top + local.y * k;
            }}
        }} else if (c.r == 19 && c.g < 64) {{
            drop = true;
        }}
    }}
    gl_Position = drop ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = col * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}}
'''


POSITION_TEX_VSH = '''#version 150

// Vanilla position_tex plus the GUI y of each vertex, so position_tex.fsh can hide the HUD carrier's bar
// (AetherionItems/tools/gen_carrier.py).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float guiY;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    guiY = (ProjMat[3][3] == 1.0 && ProjMat[2][3] == 0.0) ? Position.y : -1000.0;
}
'''

POSITION_TEX_FSH = '''#version 150

// Vanilla position_tex, except: the first boss bar is the Aetherion HUD carrier (BLUE, empty). Its
// background sprite is marked with alpha 253 and sits at GUI y 12..17, so those texels are dropped there.
// A real BLUE bar further down the stack keeps its background.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in float guiY;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    if (guiY > 11.5 && guiY < 17.5 && abs(color.a * 255.0 - 253.0) < 0.5) {
        discard;
    }
    fragColor = color * ColorModulator;
}
'''


# ----------------------------------------------------------------------------- font json

def spaces():
    adv = {}
    for i in range(8):
        adv[chr(NEG_SPACE + i)] = -(1 << i)
        adv[chr(POS_SPACE + i)] = 1 << i
    return {'type': 'space', 'advances': adv}


def rows_of(cps, cols):
    out_rows = []
    for i in range(0, len(cps), cols):
        out_rows.append(''.join(chr(c) for c in cps[i:i + cols]))
    return out_rows


def main():
    tex = ('assets', 'aetherion', 'textures', 'font', 'carrier')
    ref = 'aetherion:font/carrier/'
    prov = [spaces()]

    def bitmap(name, im, cp, ascent=0):
        im.save(out(*tex, name + '.png'))
        prov.append({'type': 'bitmap', 'file': ref + name + '.png', 'ascent': ascent, 'height': im.height,
                     'chars': [chr(cp)]})

    for rows in range(4):
        bitmap('plate_%d' % rows, plate(rows), CP_PLATE + rows)
    bitmap('crest', crest(), CP_CREST)
    for i, kind in enumerate(FRAMES):
        bitmap('frame_' + kind, bar_frame(kind), CP_FRAME + i)
    for i, name in enumerate(ICONS):
        im = icon(name)
        im.save(out(*tex, 'icon_' + name + '.png'))
        for phase, base in enumerate(CP_ICON):
            prov.append({'type': 'bitmap', 'file': ref + 'icon_' + name + '.png', 'ascent': -phase, 'height': 7,
                         'chars': [chr(base + i)]})
    for kind, base in CP_FILL.items():
        for b in range(7):
            bitmap('fill_%s_%d' % (kind, 1 << b), fill_strip(kind, 1 << b), base + b, ascent=-1)
    bitmap('radar', radar(), CP_RADAR)
    for i, name in enumerate(MARKERS):
        bitmap('mark_' + name, marker(name), CP_MARK + i)
    bitmap('loc_left', loc_cap(False), CP_LOC, ascent=-2)
    bitmap('loc_right', loc_cap(True), CP_LOC + 1, ascent=-2)
    for b in range(7):
        bitmap('loc_mid_%d' % (1 << b), loc_mid(1 << b), CP_LOC + 2 + b, ascent=-2)
    bitmap('chip_left', chip_cap(False), CP_CHIP, ascent=-2)
    bitmap('chip_right', chip_cap(True), CP_CHIP + 1, ascent=-2)
    for b in range(7):
        bitmap('chip_mid_%d' % (1 << b), chip_mid(1 << b), CP_CHIP + 2 + b, ascent=-2)

    small = dict(SMALL)
    big = dict(BIG)
    big.update(BIG_EXTRA)
    for name, font, core_h, bases in (('small', small, 5, CP_SMALL), ('big', big, 7, CP_BIG)):
        for top, base in enumerate(bases):
            im, cw, ch = text_sheet(font, core_h, top)
            im.save(out(*tex, '%s_%d.png' % (name, top)))
            cps = [base + (c - 0x20) if (chr(c) in font and chr(c) != ' ') else 0 for c in range(0x20, 0x80)]
            prov.append({'type': 'bitmap', 'file': ref + '%s_%d.png' % (name, top), 'ascent': 0, 'height': ch,
                         'chars': rows_of(cps, 16)})

    with open(out('assets', 'aetherion', 'font', 'carrier.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'providers': prov}, f, indent=1)

    core = ('assets', 'minecraft', 'shaders', 'core')
    with open(out(*core, 'rendertype_text.vsh'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(shader(palette()))
    with open(out(*core, 'rendertype_text.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(SHADER_JSON, f, indent=4)
    with open(out(*core, 'position_tex.vsh'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(POSITION_TEX_VSH)
    with open(out(*core, 'position_tex.fsh'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(POSITION_TEX_FSH)

    bar = out('assets', 'minecraft', 'textures', 'gui', 'sprites', 'boss_bar', 'blue_background.png')
    im = Image.open(bar).convert('RGBA')
    a = np.asarray(im).copy()
    a[..., 3] = np.where(a[..., 3] > 0, 253, 0)
    Image.fromarray(a, 'RGBA').save(bar)
    print('carrier font: %d providers' % len(prov))


if __name__ == '__main__':
    main()
