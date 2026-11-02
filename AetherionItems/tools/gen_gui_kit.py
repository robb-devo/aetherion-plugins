#!/usr/bin/env python3
"""Aetherion book-page kit: every chest menu can wear the Manager's book page without a page of its own.

    python gen_gui_kit.py <pack_root> [preview_dir]     e.g. python gen_gui_kit.py ../resourcepack

gen_gui_chrome.py draws one finished 256 px page per Manager menu. This draws the same page in parts, as glyphs of
the font ``aetherion:kit`` (referenced from every ``minecraft:default``, so legacy titles reach them), and
de.aetherion.core.api.ChromePage lays them out in the inventory title:

    frame    the menu frame in a theme's accent, rows 1..6 (panel, player-inventory wells, divider)
    page     the parchment over the container rows, rows 1..6
    marks    what sits under one slot, per container row: a ledger pocket (16 looks, one per set of
             neighbouring pockets, so a block of pockets gets one ink border), a seal, a small seal (button)
    plate    the title plate in a theme's accent, in parts: left end (4 wing lengths), right end (4), middle
             fills 1..64 px wide; the label on it is drawn with the caps letters below
    letters  the plate's 5x7 caps (gen_gui_chrome.FONT plus digits and punctuation)

Codepoints, advances and THEMES order are mirrored in ChromePage.java; change both together.
Coordinates are GUI coordinates as in gen_gui_chrome.py (title at x 8, y 6; glyph top = 13 - ascent).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_gui_chrome as gc  # noqa: E402
from gen_gui_chrome import (BEV_LO, INK, INK_SOFT, OUT, PAGE, PAGE_DARK, Canvas, geometry, hx, mix)  # noqa: E402

# key, accent, sigil. Order = ChromePage.Theme.
THEMES = [
    ('aether', '#9d86cf', 'star'),
    ('pets', '#e083b3', 'paw'),
    ('casino', '#e2c766', 'coin'),
    ('roulette', '#d0504a', 'crown'),
    ('guild', '#e0b24c', 'starfive'),
    ('island', '#6cc070', 'leaf'),
    ('trade', '#d6a24c', 'coin'),
    ('dungeon', '#cc5c66', 'skull'),
    ('quest', '#c9ad7e', 'scroll'),
    ('farm', '#a9c46c', 'leaf'),
    ('fish', '#5cc2d6', 'gem'),
    ('forage', '#62c08a', 'leaf'),
    ('mine', '#93a7bf', 'anvil'),
    ('forge', '#e0a35c', 'anvil'),
    ('beta', '#7ea4e8', 'bolt'),
    ('storage', '#d48a52', 'chest'),
]

FRAME = 0xE100   # + 8 * theme + rows
PAGE_CP = 0xE180  # + rows
MARK = 0xE200    # + 32 * row + k: k 0..15 pocket (bit 1 top, 2 bottom, 4 left, 8 right = a pocket there), 16 seal,
#                  17 button
PLATE = 0xE300   # + 16 * theme + k: 0..3 left end (wing 3 = full .. 0 = none), 4..7 right end, 8..14 fill 1..64 px
LETTER = 0xE500  # + index in LETTERS

WINGS = (0, 10, 17, 23)    # longest wing step k per wing level; 23 adds the bead at 26
FILLS = (1, 2, 4, 8, 16, 32, 64)
PAD, GAP, SIGIL_W = 6, 4, 7

# Digits and punctuation for the plate, in the 5x7 caps' style.
EXTRA = {
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
    '-': ["...", "...", "...", "###", "...", "...", "..."],
    '+': [".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."],
    '!': ["#", "#", "#", "#", "#", ".", "#"],
    '?': [".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."],
    '.': [".", ".", ".", ".", ".", ".", "#"],
    ',': ["..", "..", "..", "..", "..", ".#", "#."],
    ':': [".", ".", "#", ".", ".", "#", "."],
    "'": ["#", "#", ".", ".", ".", ".", "."],
    '/': ["....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."],
    '&': [".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#"],
    '#': [".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#."],
    '(': [".#", "#.", "#.", "#.", "#.", "#.", ".#"],
    ')': ["#.", ".#", ".#", ".#", ".#", ".#", "#."],
    '%': ["##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##"],
    '*': [".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", "....."],
}
GLYPHS = {k: v for k, v in gc.FONT.items() if k != ' '}
GLYPHS.update(EXTRA)
LETTERS = ''.join(sorted(GLYPHS))
LETTER_INK = hx('#f4f0fb')
LETTER_SHADOW = hx('#16131f')


def theme(key, accent, sigil):
    return gc.Theme(key, '', accent, sigil, tint=0.05)


# ----------------------------------------------------------------------------- frame + page

def frame(th, rows):
    g = geometry(rows)
    cv = Canvas()
    gc.draw_panel(cv, th, g)
    gc.draw_wells(cv, th, 7, g['inv_top'], 9, 3, brackets=(True, True, False, False))
    gc.draw_wells(cv, th, 7, g['bar_top'], 9, 1, brackets=(False, False, True, True))
    gc.draw_divider(cv, th, g)
    gc.draw_hotbar_seam(cv, th, g)
    pin(cv)
    return cv.im


def page(rows):
    cv = Canvas()
    gc.draw_page(cv, geometry(rows))
    pin(cv)
    return cv.im


def pin(cv):
    """1-alpha texel in image column 255: the client measures the glyph 256 wide, advance 257."""
    cv.put(255 - gc.OX, 120, INK, 1)


# ----------------------------------------------------------------------------- marks

class Local:
    """A 20x20 glyph whose texel (0, 0) is GUI (6 + 18c, 16 + 18r) for any slot: draws in slot-(0, 0) GUI space."""

    def __init__(self, w=20, h=20):
        self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.im.load()

    def put(self, gx, gy, c, a=255):
        x, y = gx - 6, gy - 16
        if 0 <= x < self.im.width and 0 <= y < self.im.height:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.put(x, y, c)


def pocket(mask):
    """gen_gui_chrome.draw_cells for slot (0, 0), with the ink border only toward sides without a pocket."""
    cv = Local()
    fill = mix(PAGE, PAGE_DARK, 0.42)
    groove = mix(PAGE_DARK, INK_SOFT, 0.55)
    shade = mix(fill, INK_SOFT, 0.22)
    lit = mix(PAGE, (255, 255, 255), 0.30)
    x, y = 7, 17
    for yy in range(y, y + 18):
        for xx in range(x, x + 18):
            cv.put(xx, yy, gc._paper(fill, xx, yy, 4))
    cv.hline(x, x + 17, y, groove)
    cv.vline(x, y, y + 17, groove)
    cv.hline(x + 1, x + 16, y + 1, shade)
    cv.vline(x + 1, y + 1, y + 16, shade)
    cv.hline(x + 1, x + 17, y + 17, lit)
    cv.vline(x + 17, y + 1, y + 17, lit)
    if not mask & 1:
        cv.hline(x - 1, x + 18, y - 1, INK_SOFT)
    if not mask & 2:
        cv.hline(x - 1, x + 18, y + 18, INK_SOFT)
    if not mask & 4:
        cv.vline(x - 1, y - 1, y + 18, INK_SOFT)
    if not mask & 8:
        cv.vline(x + 18, y - 1, y + 18, INK_SOFT)
    return mark_pin(cv)


def seal(inner, ring, outer):
    cv = Local()
    gc.draw_seal(cv, 0, 0, inner, ring, outer)
    return mark_pin(cv)


def mark_pin(cv):
    # Every mark measures 20 wide (advance 21) whatever it draws; texel (19, 19) is never drawn by a pocket's
    # right border when a pocket sits right of it, and the neighbour paints over it anyway.
    if cv.px[19, 19][3] == 0:
        cv.px[19, 19] = (INK[0], INK[1], INK[2], 1)
    return cv.im


def marks_row():
    looks = [pocket(m) for m in range(16)] + [seal(8.2, 9.1, 9.9), seal(7.4, 8.2, 8.95)]
    out = Image.new('RGBA', (20 * len(looks), 20), (0, 0, 0, 0))
    for i, im in enumerate(looks):
        out.alpha_composite(im, (20 * i, 0))
    return out


# ----------------------------------------------------------------------------- plate

class Strip:
    """A wide canvas for one plate, GUI x relative to the plate's left border x0 = 0, y -8..15."""
    X0 = 64

    def __init__(self, width=320):
        self.im = Image.new('RGBA', (width, 24), (0, 0, 0, 0))
        self.px = self.im.load()

    def put(self, x, y, c, a=255):
        x, y = x + self.X0, y + 8
        if 0 <= x < self.im.width and 0 <= y < 24:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.put(x, y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            self.hline(x0, x1, y, c)

    def bitmap(self, rows, x, y, c):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '#':
                    self.put(x + i, y + j, c)

    def bitmap_mirror(self, rows, x, y, c):
        self.bitmap([r[::-1] for r in rows], x, y, c)


def draw_plate(cv, th, tw, wing):
    """gen_gui_chrome.draw_plate for a label tw px wide with x0 = 0 and wings up to step ``wing``, no lettering."""
    sig = gc.SIGILS[th.sigil]
    w = SIGIL_W + GAP + tw + GAP + SIGIL_W + 2 * PAD + 2
    x0, x1 = 0, w - 1
    y0, y1 = -8, 10
    border = th.plate_border
    border_hi = mix(border, (255, 255, 255), 0.30)
    border_lo = mix(border, (0, 0, 0), 0.35)
    tail = mix(th.acc_deep, th.acc, 0.25)
    for side in (-1, 1):
        ex = x0 if side < 0 else x1
        for k in range(1, 7):
            x = ex + side * k
            for y in range(-5, 4):
                if k >= 5 and -2 <= y <= 0 and (k == 6 or y == -1):
                    continue
                if y in (-5, 3) or k == 6 or (k == 5 and y in (-3, 1)):
                    c = th.t(OUT)
                elif y == -4:
                    c = th.acc_dim
                elif y == 2:
                    c = mix(tail, (0, 0, 0), 0.35)
                else:
                    c = tail
                cv.put(x, y, c)
    cv.hline(x0 + 1, x1 + 1, y1 + 1, th.t(BEV_LO))
    cv.vline(x1 + 1, y0 + 2, y1 + 1, th.t(BEV_LO))
    for side in (-1, 1):
        base = x1 + 1 if side > 0 else x0 - 1
        for k in range(7, wing + 1):
            x = base + side * k
            c = th.acc if k <= 10 else (th.acc_dim if k <= 17 else th.acc_faint)
            cv.put(x, 1, c)
            if k <= 9:
                cv.put(x, 2, th.acc_deep)
        if wing >= 23:
            bead = base + side * 26
            cv.put(bead, 1, th.acc_dim)
            cv.put(bead, 0, th.acc_faint)
            cv.put(bead, 2, th.acc_faint)
    cv.rect(x0, y0, x1, y1, th.t(OUT))
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, border)
    cv.hline(x0 + 1, x1 - 1, y0 + 1, border_hi)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, border_hi)
    cv.hline(x0 + 1, x1 - 1, y1 - 1, border_lo)
    cv.vline(x1 - 1, y0 + 1, y1 - 1, border_lo)
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, th.plate_lo)
    ya, yb = y0 + 3, y1 - 3
    for y in range(ya, yb + 1):
        t = (y - ya) / max(1, yb - ya)
        if y == ya:
            c = th.plate_hi
        elif t < 0.5:
            c = mix(th.plate_hi, th.plate, min(1.0, 0.45 + t * 1.6))
        else:
            c = mix(th.plate, mix(th.plate, (0, 0, 0), 0.22), (t - 0.5) / 0.5)
        cv.hline(x0 + 3, x1 - 3, y, c)
    for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.put(x, y, (0, 0, 0), 0)
    for (x, y) in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        cv.put(x, y, th.t(OUT))
    for (x, y) in ((x0 + 2, y0 + 2), (x1 - 2, y0 + 2), (x0 + 2, y1 - 2), (x1 - 2, y1 - 2)):
        cv.put(x, y, border)
    for x in (x0 + 1, x1 - 1):
        cv.put(x, 1, th.acc_hi)
    tx, ty = x0 + 1 + PAD + 1, y0 + 6
    rx = x1 - 1 - PAD - SIGIL_W
    cv.bitmap(sig, tx, ty + 1, th.ink_sh)
    cv.bitmap_mirror(sig, rx, ty + 1, th.ink_sh)
    cv.bitmap(sig, tx, ty, th.sigil_c)
    cv.bitmap_mirror(sig, rx, ty, th.sigil_c)
    return x1


# Plate pieces relative to the label: the label starts at LABEL_DX from x0; the left end runs from x0 - reach(wing)
# to the label, the right end from the label's end to x1 + reach(wing).
LABEL_DX = 1 + PAD + 1 + SIGIL_W + GAP     # 19
RIGHT_W0 = 35 - LABEL_DX                   # x1 - (label end) + 1 = 16: right end up to and incl. x1


def reach(wing):
    return 27 if wing >= 23 else (wing + 1 if wing >= 7 else 6)


def plate_pieces(th):
    """[(image, width)] in PLATE order: 4 left ends, 4 right ends, the fills."""
    tw = 10
    pieces = []
    strips = []
    for wing in WINGS:
        s = Strip()
        draw_plate(s, th, tw, wing)
        strips.append(s)
    for wing, s in zip(WINGS, strips):
        left = -reach(wing)
        pieces.append(crop(s, left, LABEL_DX - 1))
    for wing, s in zip(WINGS, strips):
        start = LABEL_DX + tw
        pieces.append(crop(s, start, start + RIGHT_W0 + reach(wing)))
    column = crop(strips[0], LABEL_DX + 2, LABEL_DX + 2)[0]
    for n in FILLS:
        im = Image.new('RGBA', (n, 24), (0, 0, 0, 0))
        for i in range(n):
            im.paste(column, (i, 0))
        pieces.append((im, n))
    return pieces


def crop(s, gx0, gx1):
    """Columns gx0..gx1 (relative to x0) of a strip, with the right-most column pinned (width = gx1 - gx0 + 1)."""
    im = s.im.crop((gx0 + Strip.X0, 0, gx1 + Strip.X0 + 1, 24))
    px = im.load()
    if px[im.width - 1, 23][3] == 0:
        px[im.width - 1, 23] = (0, 0, 0, 1)
    return im, im.width


def plate_sheet(th):
    pieces = plate_pieces(th)
    out = Image.new('RGBA', (64 * len(pieces), 24), (0, 0, 0, 0))
    for i, (im, _w) in enumerate(pieces):
        out.alpha_composite(im, (64 * i, 0))
    return out, [w for _im, w in pieces]


# ----------------------------------------------------------------------------- letters

def letters_sheet():
    out = Image.new('RGBA', (6 * len(LETTERS), 16), (0, 0, 0, 0))
    px = out.load()
    for i, ch in enumerate(LETTERS):
        for c, dy in ((LETTER_SHADOW, 1), (LETTER_INK, 0)):
            for j, row in enumerate(GLYPHS[ch]):
                for k, on in enumerate(row):
                    if on == '#':
                        px[6 * i + k, j + dy] = (c[0], c[1], c[2], 255)
    return out


# ----------------------------------------------------------------------------- font

def main(root, preview=None):
    tex = os.path.join(root, 'assets/aetherion/textures/font/kit')
    os.makedirs(tex, exist_ok=True)
    for name in os.listdir(tex):
        os.remove(os.path.join(tex, name))
    providers = []

    def add(name, im, ascent, height, chars):
        im.save(os.path.join(tex, name + '.png'), optimize=True)
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/kit/%s.png' % name, 'ascent': ascent,
                          'height': height, 'chars': chars})

    widths = None
    for t, (key, accent, sigil) in enumerate(THEMES):
        th = theme(key, accent, sigil)
        for rows in range(1, 7):
            add('frame_%s_%d' % (key, rows), frame(th, rows), 23, 256, [chr(FRAME + 8 * t + rows)])
        sheet, widths = plate_sheet(th)
        add('plate_%s' % key, sheet, 21, 24, [''.join(chr(PLATE + 16 * t + k) for k in range(len(widths)))])
    for rows in range(1, 7):
        add('page_%d' % rows, page(rows), 23, 256, [chr(PAGE_CP + rows)])
    row = marks_row()
    for r in range(6):
        add('marks_%d' % r, row, -3 - 18 * r, 20, [''.join(chr(MARK + 32 * r + k) for k in range(18))])
    add('letters', letters_sheet(), 15, 16, [''.join(chr(LETTER + i) for i in range(len(LETTERS)))])
    font_dir = os.path.join(root, 'assets/aetherion/font')
    os.makedirs(font_dir, exist_ok=True)
    with open(os.path.join(font_dir, 'kit.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'providers': providers}, f, ensure_ascii=True, indent=1)
        f.write('\n')
    letter_w = ''.join(str(len(GLYPHS[ch][0])) for ch in LETTERS)
    print('kit: %d themes, %d providers' % (len(THEMES), len(providers)))
    print('ChromePage.LETTERS = "%s"' % LETTERS.encode('unicode_escape').decode())
    print('ChromePage.LETTER_WIDTHS = "%s"' % letter_w)
    print('ChromePage plate widths = %s' % widths)
    if preview:
        previews(preview)


def previews(out_dir):
    """A few composed pages, laid out as ChromePage does, for checking the art."""
    os.makedirs(out_dir, exist_ok=True)
    samples = [
        ('pets', 6, 'PET DETAILS', {13: 'seal', 29: 'seal', 33: 'seal', 45: 'button'}),
        ('casino', 6, 'SLOTS', {**{s: 'pocket' for s in range(9, 45) if 1 <= s % 9 <= 7},
                                **{45: 'button', 47: 'button', 49: 'button', 51: 'seal', 53: 'button'}}),
        ('guild', 3, 'GUILD BANK', {10: 'seal', 13: 'seal', 16: 'seal', 22: 'button'}),
        ('trade', 6, 'FENCE', {**{s: 'pocket' for s in range(0, 45)}, 49: 'button'}),
    ]
    for key, rows, label, marks in samples:
        t = [k for k, _a, _s in THEMES].index(key)
        th = theme(*THEMES[t])
        im = Image.new('RGBA', (256, 256), (40, 40, 40, 255))
        im.alpha_composite(frame(th, rows))
        im.alpha_composite(page(rows))
        row = marks_row()
        for slot in sorted(marks):
            r, c = divmod(slot, 9)
            kind = marks[slot]
            if kind == 'pocket':
                m = 0
                for bit, (dr, dc) in ((1, (-1, 0)), (2, (1, 0)), (4, (0, -1)), (8, (0, 1))):
                    rr, cc = r + dr, c + dc
                    if 0 <= cc < 9 and marks.get(rr * 9 + cc) == 'pocket':
                        m |= bit
                k = m
            else:
                k = 16 if kind == 'seal' else 17
            glyph = row.crop((20 * k, 0, 20 * k + 20, 20))
            im.alpha_composite(glyph, (6 + 18 * c + gc.OX, 16 + 18 * r + gc.OY))
        sheet, widths = plate_sheet(th)
        tw = sum(len(GLYPHS[ch][0]) + 1 if ch != ' ' else 4 for ch in label) - 1
        x0 = 88 - (tw + 36) // 2
        wing = 3
        lx = x0 + LABEL_DX
        left_w = widths[wing]
        im.alpha_composite(sheet.crop((64 * wing, 0, 64 * wing + left_w, 24)), (lx - left_w + gc.OX, -8 + gc.OY))
        x = lx
        rest = tw
        for i in reversed(range(len(FILLS))):
            while rest >= FILLS[i]:
                im.alpha_composite(sheet.crop((64 * (8 + i), 0, 64 * (8 + i) + FILLS[i], 24)), (x + gc.OX, -8 + gc.OY))
                x += FILLS[i]
                rest -= FILLS[i]
        right_w = widths[4 + wing]
        im.alpha_composite(sheet.crop((64 * (4 + wing), 0, 64 * (4 + wing) + right_w, 24)), (x + gc.OX, -8 + gc.OY))
        letters = letters_sheet()
        x = lx
        for ch in label:
            if ch == ' ':
                x += 4
                continue
            i = LETTERS.index(ch)
            w = len(GLYPHS[ch][0])
            im.alpha_composite(letters.crop((6 * i, 0, 6 * i + w, 16)), (x + gc.OX, -2 + gc.OY))
            x += w + 1
        im.resize((768, 768), Image.NEAREST).save(os.path.join(out_dir, 'kit_%s.png' % key))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'out', sys.argv[2] if len(sys.argv) > 2 else None)
