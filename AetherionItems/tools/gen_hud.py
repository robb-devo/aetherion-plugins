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
from px_art import hx  # noqa: E402
import gen_hud_art as art  # noqa: E402  frames, fills and icons: pixel art at 2 texels per GUI px

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
    s = art.S
    sheet([art.frame(k, FRAME_W, FRAME_H, DEF_W, WELL, 12, FRAME_W - 11) for k in FRAMES], len(FRAMES),
          FRAME_W * s, FRAME_H * s).save(os.path.join(tex, 'frames.png'), optimize=True)
    sheet([art.icon(n) for n in ICONS], len(ICONS), 7 * s, INNER * s).save(
        os.path.join(tex, 'icons.png'), optimize=True)
    sheet([art.fill_strip(k, WELL, INNER) for k in range(1, WELL + 1)], 8, WELL * s, INNER * s).save(
        os.path.join(tex, 'fill.png'), optimize=True)
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
