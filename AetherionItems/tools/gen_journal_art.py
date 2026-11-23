#!/usr/bin/env python3
"""Aether Journal art: the open book that replaces the Manager page, drawn as glyphs of the font ``aetherion:kit``.

gen_gui_kit.py calls ``add_glyphs`` while it writes kit.json, so these ride the same font (referenced from every
``minecraft:default``) and de.aetherion.core.api.JournalPage lays them out in the inventory title:

    spread     two parchment pages on a leather cover over all six container rows, the spine at GUI x 88,
               ruled lines under rows 1..4, a folded corner under Back (slot 45)
    ribbons    one silk bookmark per chapter (CHAPTERS, order = JournalPage.Chapter) in four looks:
               0 open, 1 the chapter you are in (longer, gold thread), 2 new (sparks), 3 next to unlock (pale)
    highlight  the one strong line of a page: a gold wash four slots wide, one glyph per row 1..4

``write_line_fonts`` derives aetherion:jrn_line_1..4 (vanilla text moved down onto rows 1..4) from the committed
mgr_caption_1.json, so no client jar is needed.

Codepoints, sizes and CHAPTERS are mirrored in JournalPage.java; change both together.
Coordinates are GUI coordinates as in gen_gui_chrome.py (title at x 8, y 6; glyph top = 13 - ascent).
"""
import json
import os

from PIL import Image

import gen_gui_chrome as gc
from gen_gui_chrome import INK, INK_SOFT, PAGE, PAGE_DARK, Canvas, hx, mix

SPREAD = 0xE400
RIBBON = 0xE410   # + 4 * chapter + look
HIGHLIGHT = 0xE440  # + row - 1

# key, silk, sigil. Order = JournalPage.Chapter.
CHAPTERS = [
    ('today', '#e0b24c', 'star'),
    ('character', '#9d86cf', 'armor'),
    ('journey', '#c94f4f', 'scroll'),
    ('atlas', '#5aa86a', 'compass'),
    ('workshop', '#d9823f', 'anvil'),
    ('market', '#4f9fc9', 'coin'),
    ('codex', '#a45fb0', 'eye'),
    ('company', '#d97aa6', 'starfive'),
    ('settings', '#8c939e', 'gear'),
]
SIGILS = dict(gc.SIGILS)
SIGILS['gear'] = [".#.#.#.", "#######", ".#...#.", "##.#.##", ".#...#.", "#######", ".#.#.#."]

LEATHER = hx('#4a2c1c')
LEATHER_HI = hx('#6b4129')
LEATHER_LO = hx('#2a170e')
GOLD = hx('#f4d27a')
GOLD_DEEP = hx('#c79a3a')
PAPER_EDGE = mix(PAGE_DARK, INK_SOFT, 0.25)
RULE = mix(PAGE_DARK, INK_SOFT, 0.32)

LEFT = (4, 86)
RIGHT = (89, 171)
TOP, BOTTOM = 26, 124
LINE_TOPS = (40, 58, 76, 94)  # GUI y of the text on rows 1..4 (JournalPage.LINE_FONTS)


# ----------------------------------------------------------------------------- spread

def _page(cv, x0, x1, spine_at_right):
    for y in range(TOP, BOTTOM + 1):
        for x in range(x0, x1 + 1):
            outer = (x - x0) if spine_at_right else (x1 - x)
            inner = (x1 - x) if spine_at_right else (x - x0)
            edge = min(outer, y - TOP, BOTTOM - y)
            t = 0.0 if edge >= 7 else (7 - edge) / 7.0 * 0.45
            t += max(0.0, (6 - inner) / 6.0) * 0.55   # the gutter falls into the spine
            cv.put(x, y, gc._paper(mix(PAGE, PAGE_DARK, min(1.0, t)), x, y))
    # ruled lines between rows 1..4, dotted toward the gutter
    for r in range(1, 5):
        y = 35 + 18 * r
        a, b = (x0 + 3, x1 - 3)
        for x in range(a, b + 1):
            gap = (x1 - x) if spine_at_right else (x - x0)
            if gap < 8 and x % 2:
                continue
            cv.put(x, y, RULE)
    # ink corner brackets at the outer top and bottom
    ox = x0 + 2 if spine_at_right else x1 - 2
    step = 1 if spine_at_right else -1
    for (cy, dy) in ((TOP + 2, 1), (BOTTOM - 2, -1)):
        for k in range(4):
            cv.put(ox + step * k, cy, INK_SOFT)
            cv.put(ox, cy + dy * k, INK_SOFT)


def spread():
    cv = Canvas()
    # the cover: leather one texel beyond the paper, a stitched seam
    for y in range(TOP - 2, BOTTOM + 3):
        for x in range(LEFT[0] - 2, RIGHT[1] + 3):
            cv.put(x, y, gc._paper(LEATHER, x, y, 6))
    for x in range(LEFT[0] - 2, RIGHT[1] + 3):
        cv.put(x, TOP - 2, LEATHER_HI)
        cv.put(x, BOTTOM + 2, LEATHER_LO)
    for y in range(TOP - 2, BOTTOM + 3):
        cv.put(LEFT[0] - 2, y, LEATHER_HI)
        cv.put(RIGHT[1] + 2, y, LEATHER_LO)
    # stacked page edges under the paper
    for y in range(TOP, BOTTOM + 2):
        cv.put(LEFT[0] - 1, y, PAPER_EDGE)
        cv.put(RIGHT[1] + 1, y, PAPER_EDGE)
    for x in range(LEFT[0] - 1, RIGHT[1] + 2):
        if x not in (87, 88):
            cv.put(x, BOTTOM + 1, PAPER_EDGE)
    _page(cv, LEFT[0], LEFT[1], True)
    _page(cv, RIGHT[0], RIGHT[1], False)
    # the spine: a dark seam, a gold bead at the head
    for y in range(TOP - 1, BOTTOM + 2):
        cv.put(87, y, LEATHER_LO)
        cv.put(88, y, LEATHER)
    gc.draw_diamond(cv, 87, TOP - 1, 1, GOLD, GOLD_DEEP)
    # the folded corner under Back (slot 45): the page turned up at the bottom left
    for k in range(10):
        for j in range(10 - k):
            x, y = LEFT[0] + j, BOTTOM - k
            cv.put(x, y, mix(PAGE_DARK, INK_SOFT, 0.12 + 0.02 * j) if j < 10 - k - 1 else INK_SOFT)
    for k in range(10):
        cv.put(LEFT[0] + k, BOTTOM - 9 + k, INK_SOFT)
    # a small fleuron at the foot of the right page
    cx, cy = (RIGHT[0] + RIGHT[1]) // 2, BOTTOM - 7
    gc.draw_diamond(cv, cx, cy, 1, INK_SOFT)
    for k in range(3, 12):
        if k % 2:
            cv.put(cx - k, cy, RULE)
            cv.put(cx + k, cy, RULE)
    cv.put(255 - gc.OX, 120, INK, 1)
    return cv.im


# ----------------------------------------------------------------------------- ribbons

class Ribbon:
    """A 20x28 glyph whose texel (0, 0) is GUI (6 + 18c, 12): the slot of column c spans local x 2..18."""

    def __init__(self):
        self.im = Image.new('RGBA', (20, 28), (0, 0, 0, 0))
        self.px = self.im.load()

    def put(self, x, y, c, a=255):
        if 0 <= x < 20 and 0 <= y < 28:
            self.px[x, y] = (c[0], c[1], c[2], a)


def ribbon(silk, sigil, look):
    rb = Ribbon()
    base = hx(silk)
    if look == 3:
        base = mix(base, hx('#b9ae98'), 0.6)
    top, bottom = (4, 20) if look == 3 else (0, 25 if look == 1 else 21)
    alpha = 150 if look == 3 else 255
    x0, x1 = 4, 15
    for y in range(top, bottom + 1):
        for x in range(x0, x1 + 1):
            if y > bottom - 3 and abs(x - 9.5) < (y - (bottom - 3)) + 0.5:   # the notch at the tail
                continue
            c = base
            if x in (x0, x1):
                c = mix(base, INK, 0.38)
            elif x == x0 + 2:
                c = mix(base, (255, 255, 255), 0.28)
            if y < top + 2:
                c = mix(c, INK, 0.25)   # the silk comes out from behind the cover
            rb.put(x, y, c, alpha)
    if look == 1:
        for y in range(top + 1, bottom - 2):
            rb.put(x0 + 1, y, GOLD)
            rb.put(x1 - 1, y, GOLD)
    glyph = SIGILS[sigil]
    ink = hx('#fff4dc') if look != 3 else mix(hx('#fff4dc'), base, 0.5)
    shadow = mix(base, INK, 0.55)
    sy = top + 5
    for j, row in enumerate(glyph):
        for i, ch in enumerate(row):
            if ch == '#':
                rb.put(6 + i, sy + j + 1, shadow, alpha)
    for j, row in enumerate(glyph):
        for i, ch in enumerate(row):
            if ch == '#':
                rb.put(6 + i, sy + j, ink, alpha)
    if look == 2:
        for (x, y) in ((17, 1), (18, 2), (17, 3), (16, 2), (2, 6), (1, 7), (2, 8), (3, 7)):
            rb.put(x, y, GOLD)
        rb.put(17, 2, (255, 255, 255))
        rb.put(2, 7, (255, 255, 255))
    if rb.px[19, 27][3] == 0:
        rb.px[19, 27] = (INK[0], INK[1], INK[2], 1)   # every ribbon measures 20 wide: advance 21
    return rb.im


# ----------------------------------------------------------------------------- highlight

def highlight():
    """76x20, texel (0, 0) = GUI (6 or 96, 17 + 18r): the four slots of one page line under a gold wash."""
    im = Image.new('RGBA', (76, 20), (0, 0, 0, 0))
    px = im.load()
    wash = mix(PAGE, hx('#f2cf6b'), 0.55)
    edge = mix(GOLD_DEEP, INK, 0.15)
    for y in range(1, 19):
        for x in range(1, 75):
            corner = (x in (1, 74)) and (y in (1, 18))
            if corner:
                continue
            border = x in (1, 74) or y in (1, 18)
            c = edge if border else mix(wash, PAGE, 0.25 * (y / 18.0))
            px[x, y] = (c[0], c[1], c[2], 255 if border else 215)
    for k in range(3):   # a pointer on the left edge
        for j in range(-2 + k, 3 - k):
            px[3 + k, 9 + j] = (edge[0], edge[1], edge[2], 255)
    px[75, 19] = (INK[0], INK[1], INK[2], 1)   # advance 77
    return im


# ----------------------------------------------------------------------------- kit hook + fonts

def add_glyphs(add):
    """Called by gen_gui_kit.main with its add(name, image, ascent, height, chars)."""
    add('journal_spread', spread(), 23, 256, [chr(SPREAD)])
    for n, (key, silk, sigil) in enumerate(CHAPTERS):
        sheet = Image.new('RGBA', (80, 28), (0, 0, 0, 0))
        for look in range(4):
            sheet.alpha_composite(ribbon(silk, sigil, look), (20 * look, 0))
        add('journal_ribbon_%s' % key, sheet, 1, 28, [''.join(chr(RIBBON + 4 * n + k) for k in range(4))])
    hi = highlight()
    for r in range(1, 5):
        add('journal_highlight_%d' % r, hi, 13 - (17 + 18 * r), 20, [chr(HIGHLIGHT + r - 1)])


def write_line_fonts(root):
    """aetherion:jrn_line_N = mgr_caption_1 (text top at GUI y 37) moved to LINE_TOPS[N-1]."""
    font_dir = os.path.join(root, 'assets/aetherion/font')
    with open(os.path.join(font_dir, 'mgr_caption_1.json'), encoding='utf-8') as f:
        caption = json.load(f)
    for n, top in enumerate(LINE_TOPS, start=1):
        providers = []
        for p in caption['providers']:
            q = dict(p)
            if q.get('type') == 'bitmap':
                q['ascent'] = p['ascent'] - (top - 37)
            providers.append(q)
        with open(os.path.join(font_dir, 'jrn_line_%d.json' % n), 'w', encoding='utf-8', newline='\n') as f:
            json.dump({'providers': providers}, f, ensure_ascii=True, separators=(',', ':'))


# ----------------------------------------------------------------------------- preview

def preview(path, frame_im, plate_draw=None, chapter=1, lines=(), strong=None, looks=None):
    """A composed page for checking the art: frame, spread, ribbons, the strong line, and line text in the plate caps
    (the game draws the vanilla font there)."""
    im = Image.new('RGBA', (256, 256), (34, 36, 40, 255))
    im.alpha_composite(frame_im)
    im.alpha_composite(spread())
    looks = looks or {}
    for n, (key, silk, sigil) in enumerate(CHAPTERS):
        look = looks.get(key, 1 if n == chapter else 0)
        if look is None:
            continue
        im.alpha_composite(ribbon(silk, sigil, look), (6 + 18 * n + gc.OX, 12 + gc.OY))
    if strong is not None:
        r, side = strong
        im.alpha_composite(highlight(), ((6 if side == 0 else 96) + gc.OX, 17 + 18 * r + gc.OY))
    if plate_draw:
        plate_draw(im)
    for (r, side, text, colour) in lines:
        x = (28 if side == 0 else 118) + gc.OX
        y = LINE_TOPS[r - 1] + gc.OY
        for ch in text.upper():
            if ch == ' ':
                x += 4
                continue
            rows = gc.FONT.get(ch)
            if not rows:
                continue
            for j, row in enumerate(rows):
                for i, on in enumerate(row):
                    if on == '#':
                        im.putpixel((x + i, y + j), hx(colour) + (255,))
            x += len(rows[0]) + 1
    im.resize((768, 768), Image.NEAREST).save(path)
