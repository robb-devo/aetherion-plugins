#!/usr/bin/env python3
"""
Aetherion player HUD generator (Minecraft 1.21.1, pack_format 34).

    python gen_hud.py <pack_root>        e.g. python gen_hud.py ../resourcepack

Writes:
    assets/minecraft/textures/gui/sprites/hud/{heart/*,food_*,armor_*}.png   transparent 9x9
    assets/aetherion/font/hud.json                                          font aetherion:hud
    assets/aetherion/textures/font/hud/{frames,icons,fill,text}.png

Geometry (decompiled 1.21.1 Gui / BitmapProvider):
    ActionBar text is drawn at y = guiHeight - 72, centred on guiWidth / 2.
    Bitmap glyph top = y + 7 - ascent.  Hearts row = guiHeight - 39, armor row = - 49.
    The HUD string has net advance 0, so its pen starts on the screen centre and every glyph
    lands on an absolute GUI pixel at any GUI scale or window size.

Rows (frame top, relative to guiHeight):  ROW0 = -41 (heart row), ROW1 = -53, ROW2 = -65.
Frame ascent = -65 - top, inner (fill / text / icon, frame top + 2) ascent = frame ascent - 2.

Java mirror: de.aetherion.items.combat.HudGlyphs (codepoints, widths, geometry).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_gui_chrome import BODY, OUT, TRAY, hx, mix  # noqa: E402  one art language with the Manager chrome

# ----------------------------------------------------------------------------- geometry

FRAME_W, FRAME_H = 81, 11
DEF_W = 45
WELL = 67                 # fill steps 1..67
INNER = 7                 # well / text / icon height
ROW_TOPS = (-41, -53, -65)
ROW_BASES = (0xE100, 0xE200, 0xE300)
FRAME_OFF, ICON_OFF, FILL_OFF, TEXT_OFF = 0x00, 0x10, 0x20, 0x80

FRAMES = ('hp', 'cd', 'cd_deny', 'cd_ready', 'def')
ICONS = ('heart', 'heart_poison', 'heart_wither', 'heart_frozen', 'heart_gold',
         'hourglass', 'check', 'cross', 'shield', 'star')

NEG_SPACE = 0xF801        # -1, -2, -4 ... -128
POS_SPACE = 0xF821        # +1, +2, +4 ... +128

# ----------------------------------------------------------------------------- palette

CRIMSON = hx('#d0343c')
BRONZE = hx('#d9a441')
DENY = hx('#ff5a4a')
READY = hx('#62d977')
TEAL = hx('#52baa9')
WELL_BG = hx('#120f17')
INK_OUT = hx('#0d0b12')

# ----------------------------------------------------------------------------- pixel font (3x5 core)

FONT = {
    '0': ["###", "#.#", "#.#", "#.#", "###"],
    '1': [".#.", "##.", ".#.", ".#.", "###"],
    '2': ["##.", "..#", ".#.", "#..", "###"],
    '3': ["###", "..#", ".##", "..#", "###"],
    '4': ["#.#", "#.#", "###", "..#", "..#"],
    '5': ["###", "#..", "##.", "..#", "##."],
    '6': [".##", "#..", "###", "#.#", "###"],
    '7': ["###", "..#", ".#.", ".#.", ".#."],
    '8': ["###", "#.#", "###", "#.#", "###"],
    '9': ["###", "#.#", "###", "..#", "##."],
    'A': [".#.", "#.#", "###", "#.#", "#.#"],
    'B': ["##.", "#.#", "##.", "#.#", "##."],
    'C': [".##", "#..", "#..", "#..", ".##"],
    'D': ["##.", "#.#", "#.#", "#.#", "##."],
    'E': ["###", "#..", "##.", "#..", "###"],
    'F': ["###", "#..", "##.", "#..", "#.."],
    'G': [".##", "#..", "#.#", "#.#", ".##"],
    'H': ["#.#", "#.#", "###", "#.#", "#.#"],
    'I': ["###", ".#.", ".#.", ".#.", "###"],
    'J': ["..#", "..#", "..#", "#.#", ".#."],
    'K': ["#.#", "#.#", "##.", "#.#", "#.#"],
    'L': ["#..", "#..", "#..", "#..", "###"],
    'M': ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    'N': ["#..#", "##.#", "#.##", "#..#", "#..#"],
    'O': [".#.", "#.#", "#.#", "#.#", ".#."],
    'P': ["##.", "#.#", "##.", "#..", "#.."],
    'Q': [".#.", "#.#", "#.#", "##.", ".##"],
    'R': ["##.", "#.#", "##.", "#.#", "#.#"],
    'S': [".##", "#..", ".#.", "..#", "##."],
    'T': ["###", ".#.", ".#.", ".#.", ".#."],
    'U': ["#.#", "#.#", "#.#", "#.#", "###"],
    'V': ["#.#", "#.#", "#.#", "#.#", ".#."],
    'W': ["#...#", "#...#", "#.#.#", "##.##", "#...#"],
    'X': ["#.#", "#.#", ".#.", "#.#", "#.#"],
    'Y': ["#.#", "#.#", ".#.", ".#.", ".#."],
    'Z': ["###", "..#", ".#.", "#..", "###"],
    's': ["...", ".##", "#..", "..#", "##."],
    'm': [".....", "##.#.", "#.#.#", "#.#.#", "#.#.#"],
    'k': ["#..", "#.#", "##.", "#.#", "#.#"],
    '.': [".", ".", ".", ".", "#"],
    ',': [".", ".", ".", "#", "#"],
    ':': [".", "#", ".", "#", "."],
    '!': ["#", "#", "#", ".", "#"],
    "'": ["#", "#", ".", ".", "."],
    '?': ["##.", "..#", ".#.", "...", ".#."],
    '/': ["..#", "..#", ".#.", "#..", "#.."],
    '+': ["...", ".#.", "###", ".#.", "..."],
    '-': ["...", "...", "###", "...", "..."],
    '%': ["#.#", "..#", ".#.", "#..", "#.#"],
    '(': [".#", "#.", "#.", "#.", ".#"],
    ')': ["#.", ".#", ".#", ".#", "#."],
}

# ----------------------------------------------------------------------------- icons (7x7, painted)

ICON_ART = {
    # '#' body, '+' highlight, '-' shade
    'heart': [".##.##.", "#+####-", "#+#####", "######-", ".####-.", "..##-..", "...#..."],
    'hourglass': ["#######", ".#...#.", "..#+#..", "...#...", "..#.#..", ".#+++#.", "#######"],
    'check': ["......#", ".....##", "#...##.", "##.##..", ".###...", "..#....", "......."],
    'cross': ["##...##", ".##.##.", "..###..", "...#...", "..###..", ".##.##.", "##...##"],
    'shield': ["#######", "#+++++#", "#+#-#+#", "#+---+#", ".#+-+#.", "..#+#..", "...#..."],
    'star': ["...#...", "..#+#..", "##+++##", ".#+++#.", "..#-#..", ".##.##.", ".#...#."],
}
ICON_COLOURS = {
    'heart': (hx('#d8333b'), hx('#ff8a8f'), hx('#8e1b22')),
    'heart_poison': (hx('#5fae2e'), hx('#b4ec7c'), hx('#386b17')),
    'heart_wither': (hx('#3a3540'), hx('#77707f'), hx('#1c1920')),
    'heart_frozen': (hx('#7cc8ec'), hx('#dff5ff'), hx('#3f86ad')),
    'heart_gold': (hx('#e8b83c'), hx('#fff0a8'), hx('#a1741a')),
    'hourglass': (hx('#c9ad7e'), hx('#f2cf63'), hx('#7e6a4b')),
    'check': (hx('#62d977'), hx('#c8ffd2'), hx('#2f8a40')),
    'cross': (hx('#ff5a4a'), hx('#ffb0a6'), hx('#a3271c')),
    'shield': (hx('#9fe3d8'), hx('#3f9c8d'), hx('#24564f')),
    'star': (hx('#e2c766'), hx('#fff3b0'), hx('#9c7f2a')),
}


def icon_art(name):
    return ICON_ART['heart'] if name.startswith('heart') else ICON_ART[name]


# ----------------------------------------------------------------------------- drawing helpers


class Cell:
    def __init__(self, w, h):
        self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.im.load()
        self.w, self.h = w, h

    def put(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def rect(self, x0, y0, x1, y1, c, a=255):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c, a)


def box(cv, x0, y0, x1, y1, ring, body):
    """Outline + bevelled accent ring (lit top/left, shaded bottom/right) + body, clipped corners."""
    cv.rect(x0, y0, x1, y1, OUT)
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.put(x, y, (0, 0, 0), 0)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, ring)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, mix(ring, (255, 255, 255), 0.22))
    cv.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 1, mix(ring, (255, 255, 255), 0.14))
    cv.rect(x0 + 1, y1 - 1, x1 - 1, y1 - 1, mix(ring, OUT, 0.40))
    cv.rect(x1 - 1, y0 + 1, x1 - 1, y1 - 1, mix(ring, OUT, 0.30))
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, body)


def ticks(cv, x0, y0, x1, y1, c):
    """Tooltip-style brackets: the clipped corner reads as a bright accent chevron."""
    for (x, y), (dx, dy) in (((x0, y0), (1, 1)), ((x1, y0), (-1, 1)), ((x0, y1), (1, -1)), ((x1, y1), (-1, -1))):
        cv.put(x + dx, y, c)
        cv.put(x, y + dy, c)


def socket(cv, x0, ring, bg):
    cv.rect(x0 + 1, 1, x0 + 9, 9, ring)
    cv.rect(x0 + 1, 1, x0 + 9, 1, mix(ring, (255, 255, 255), 0.22))
    cv.rect(x0 + 1, 9, x0 + 9, 9, mix(ring, OUT, 0.40))
    cv.rect(x0 + 2, 2, x0 + 8, 8, bg)
    # lit bowl: brighter toward the top-left, a rim of shadow at the bottom
    cv.rect(x0 + 2, 2, x0 + 7, 3, mix(bg, (255, 255, 255), 0.08))
    cv.rect(x0 + 2, 8, x0 + 8, 8, mix(bg, OUT, 0.30))
    for x, y in ((x0 + 2, 2), (x0 + 8, 2), (x0 + 2, 8), (x0 + 8, 8)):
        cv.put(x, y, ring)


def frame(kind):
    accent = {'hp': CRIMSON, 'cd': BRONZE, 'cd_deny': DENY, 'cd_ready': READY, 'def': TEAL}[kind]
    ring = mix(TRAY, accent, 0.50)
    hi = mix(accent, (255, 255, 255), 0.40)
    well = mix(WELL_BG, accent, 0.10)
    socket_bg = mix(BODY, accent, 0.18)
    cv = Cell(FRAME_W, FRAME_H)
    if kind == 'def':
        # Shield socket + a 31px number plate (digits from x 12).
        box(cv, 0, 0, DEF_W - 1, FRAME_H - 1, ring, well)
        socket(cv, 0, ring, socket_bg)
        cv.rect(10, 0, 10, FRAME_H - 1, OUT)
        cv.rect(11, 2, DEF_W - 3, 2, mix(well, OUT, 0.55))
        ticks(cv, 0, 0, DEF_W - 1, FRAME_H - 1, hi)
        return cv.im
    box(cv, 0, 0, FRAME_W - 1, FRAME_H - 1, ring, well)
    if kind == 'hp':
        sx, wx0 = 0, 12                     # socket left, well x 12..78
    else:
        sx, wx0 = FRAME_W - 11, 2           # socket right, well x 2..68
    socket(cv, sx, ring, socket_bg)
    cv.rect(sx + (10 if kind == 'hp' else 0), 0, sx + (10 if kind == 'hp' else 0), FRAME_H - 1, OUT)
    # Inner lip under the ring so the well reads as recessed, and quarter marks on its floor.
    cv.rect(wx0, 2, wx0 + WELL - 1, 2, mix(well, OUT, 0.55))
    cv.rect(wx0, 8, wx0 + WELL - 1, 8, mix(well, accent, 0.12))
    for q in (1, 2, 3):
        x = wx0 + round(WELL * q / 4) - 1
        cv.put(x, 8, mix(well, accent, 0.45))
        cv.put(x, 7, mix(well, accent, 0.22))
    ticks(cv, 0, 0, FRAME_W - 1, FRAME_H - 1, hi)
    return cv.im


# Grey ramp, tinted per state by the text colour (glyph colour multiplies the texture).
# GIGA: glossy top, deeper foot, a faint seam every 8px so the bar reads as segments.
FILL_ROWS = (255, 242, 222, 206, 190, 168, 126)


def fill(k):
    cv = Cell(WELL, INNER)
    for x in range(k):
        edge = k >= 3 and x == k - 1
        seam = x % 8 == 7 and not edge
        for y, v in enumerate(FILL_ROWS):
            if edge:
                c = min(255, v + 40)
            elif seam and 1 <= y <= 5:
                c = int(v * 0.84)
            else:
                c = v
            cv.put(x, y, (c, c, c))
    return cv.im


def text_glyph(ch):
    rows = FONT[ch]
    w = len(rows[0])
    cv = Cell(7, INNER)
    core = {(x + 1, y + 1) for y, r in enumerate(rows) for x, c in enumerate(r) if c == '#'}
    # Solid drop shadow down-right, faint rim up-left: readable on any fill without a black slab
    # hiding the bar under the numbers.
    for (x, y) in core:
        for dx, dy in ((1, 1), (1, 0), (0, 1)):
            if (x + dx, y + dy) not in core:
                cv.put(x + dx, y + dy, INK_OUT, 220)
    for (x, y) in core:
        for dx, dy in ((-1, 0), (0, -1), (-1, -1), (1, -1), (-1, 1)):
            p = (x + dx, y + dy)
            if p not in core and cv.px[p[0], p[1]][3] == 0:
                cv.put(p[0], p[1], INK_OUT, 110)
    for (x, y) in core:
        cv.put(x, y, (255, 255, 255))
    # Bitmap advance = rightmost opaque column + 1, so the right outline column must exist.
    assert any(cv.px[w + 1, y][3] for y in range(INNER)), ch
    return cv.im


def icon(name):
    body, hi, lo = ICON_COLOURS[name]
    cv = Cell(7, INNER)
    for y, r in enumerate(icon_art(name)):
        for x, c in enumerate(r):
            colour = {'#': body, '+': hi, '-': lo}.get(c)
            if colour:
                cv.put(x, y, colour)
    assert any(cv.px[6, y][3] for y in range(INNER)), name
    return cv.im


def sheet(cells, cols, cw, ch):
    rows = (len(cells) + cols - 1) // cols
    im = Image.new('RGBA', (cols * cw, rows * ch), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        if c is not None:
            im.paste(c, ((i % cols) * cw, (i // cols) * ch))
    return im


def char_rows(cps, cols):
    out = []
    for i in range(0, len(cps), cols):
        row = cps[i:i + cols]
        row += [0] * (cols - len(row))
        out.append(''.join(chr(c) for c in row))
    return out


# ----------------------------------------------------------------------------- hidden vanilla sprites

HEARTS = ['container', 'container_blinking', 'container_hardcore', 'container_hardcore_blinking'] + [
    f'{kind}{hc}{part}{blink}'
    for kind in ('', 'absorbing_', 'frozen_', 'poisoned_', 'withered_')
    for hc in ('', 'hardcore_') for part in ('full', 'half') for blink in ('', '_blinking')
]
HIDE = (['heart/' + h for h in HEARTS]
        + ['food_empty', 'food_empty_hunger', 'food_full', 'food_full_hunger', 'food_half', 'food_half_hunger']
        + ['armor_empty', 'armor_half', 'armor_full'])


# ----------------------------------------------------------------------------- main


def main(root):
    hud_dir = os.path.join(root, 'assets/minecraft/textures/gui/sprites/hud')
    blank = Image.new('RGBA', (9, 9), (0, 0, 0, 0))
    for name in HIDE:
        path = os.path.join(hud_dir, name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        blank.save(path, optimize=True)

    tex = os.path.join(root, 'assets/aetherion/textures/font/hud')
    os.makedirs(tex, exist_ok=True)
    sheet([frame(k) for k in FRAMES], len(FRAMES), FRAME_W, FRAME_H).save(os.path.join(tex, 'frames.png'), optimize=True)
    sheet([icon(n) for n in ICONS], len(ICONS), 7, INNER).save(os.path.join(tex, 'icons.png'), optimize=True)
    sheet([fill(k) for k in range(1, WELL + 1)], 8, WELL, INNER).save(os.path.join(tex, 'fill.png'), optimize=True)
    text_cells = [text_glyph(chr(c)) if chr(c) in FONT else None for c in range(0x20, 0x80)]
    sheet(text_cells, 16, 7, INNER).save(os.path.join(tex, 'text.png'), optimize=True)

    providers = [{
        'type': 'space',
        'advances': {**{chr(NEG_SPACE + i): -(1 << i) for i in range(8)},
                     **{chr(POS_SPACE + i): 1 << i for i in range(8)}},
    }]
    for top, base in zip(ROW_TOPS, ROW_BASES):
        frame_ascent = -65 - top
        inner_ascent = frame_ascent - 2
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/hud/frames.png', 'ascent': frame_ascent,
                          'height': FRAME_H, 'chars': char_rows([base + FRAME_OFF + i for i in range(len(FRAMES))], len(FRAMES))})
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/hud/icons.png', 'ascent': inner_ascent,
                          'height': INNER, 'chars': char_rows([base + ICON_OFF + i for i in range(len(ICONS))], len(ICONS))})
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/hud/fill.png', 'ascent': inner_ascent,
                          'height': INNER, 'chars': char_rows([base + FILL_OFF + k for k in range(WELL)], 8)})
        text_cps = [base + TEXT_OFF + (c - 0x20) if chr(c) in FONT else 0 for c in range(0x20, 0x80)]
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/hud/text.png', 'ascent': inner_ascent,
                          'height': INNER, 'chars': char_rows(text_cps, 16)})
    font_dir = os.path.join(root, 'assets/aetherion/font')
    os.makedirs(font_dir, exist_ok=True)
    with open(os.path.join(font_dir, 'hud.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'providers': providers}, f, indent=2, ensure_ascii=True)
        f.write('\n')

    odd = {ch: len(rows[0]) for ch, rows in FONT.items() if len(rows[0]) != 3}
    print('HudGlyphs text core widths != 3:', ' '.join(f'{k}={v}' for k, v in sorted(odd.items())))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'out')
