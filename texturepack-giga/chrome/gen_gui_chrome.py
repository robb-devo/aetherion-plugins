#!/usr/bin/env python3
"""
Aetherion Manager GUI chrome generator.

Every overlay is a 256x256 RGBA font bitmap. Coordinates below are *GUI coordinates*
(0,0 = top-left of the vanilla container background). The bitmap places the GUI origin at
image pixel (OX, OY):

    title x = 8, \\uE005 advance = -48  ->  glyph left  = 8 - 48           = -40  -> OX = 40
    title y = 6, glyph top = y + 7 - ascent                                 -> ascent = 13 + OY

Vanilla ChestMenu geometry (rows = 1..6):
    imageHeight    = 114 + 18*rows
    container slot = (8 + 18c, 18 + 18r)            item top-left, frame at -1
    player inv     = (8 + 18c, 103 + 18r + (rows-4)*18)
    hotbar         = (8 + 18c, 161 + (rows-4)*18)
    "Inventory"    = (8, imageHeight - 94)
"""
import colorsys
import json
import os
import sys

from PIL import Image

OX, OY = 40, 10
ASCENT = 13 + OY
CANVAS = 256

# ----------------------------------------------------------------------------- colour


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def hsl(h, s, l):
    r, g, b = colorsys.hls_to_rgb(h / 360.0, l, s)
    return (round(r * 255), round(g * 255), round(b * 255))


# Neutral stone, a hair of lilac in every tone so the family reads as one.
OUT = hx('#0a090e')       # outermost outline
BEV_HI = hx('#45415a')    # bevel light (top/left)
BEV_LO = hx('#110f17')    # bevel shadow (bottom/right)
BODY = hx('#211f2a')      # panel body
TRAY = hx('#14121a')      # rim around slot grids
WELL = hx('#2c2a37')      # slot interior
WELL_SH = hx('#16141d')   # slot top/left
WELL_HI = hx('#3f3c4e')   # slot bottom/right
WELL_MID = hx('#2a2834')


class Theme:
    def __init__(self, key, label, accent, sigil, plate=None, ink=None, tint=0.07):
        self.key = key
        self.label = label
        self.acc = hx(accent) if isinstance(accent, str) else accent
        self.sigil = sigil
        self.tint = tint
        a = self.acc
        self.acc_hi = mix(a, (255, 255, 255), 0.45)
        self.acc_dim = mix(BODY, a, 0.42)
        self.acc_faint = mix(BODY, a, 0.22)
        self.acc_deep = mix(OUT, a, 0.30)
        # plate
        if plate:
            self.plate = hx(plate)
            self.plate_hi = mix(self.plate, (255, 255, 255), 0.28)
            self.plate_lo = mix(self.plate, (0, 0, 0), 0.30)
            self.ink = hx(ink)
            self.ink_sh = mix(self.plate, (0, 0, 0), 0.12)
            self.plate_border = mix(a, (0, 0, 0), 0.35)
            self.sigil_c = mix(a, (0, 0, 0), 0.45)
        else:
            self.plate = mix(hx('#1b1823'), a, 0.20)
            self.plate_hi = mix(self.plate, a, 0.30)
            self.plate_lo = mix(self.plate, (0, 0, 0), 0.35)
            self.ink = mix((250, 248, 255), a, 0.16)
            self.ink_sh = mix(OUT, a, 0.18)
            self.plate_border = a
            self.sigil_c = mix(a, (255, 255, 255), 0.18)

    def t(self, c):
        """Tint a neutral tone toward the accent (very lightly)."""
        return mix(c, self.acc, self.tint)


# ----------------------------------------------------------------------------- pixel font (5x7 caps)

FONT = {
    'A': [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    'B': ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    'C': [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    'D': ["###..", "#..#.", "#...#", "#...#", "#...#", "#..#.", "###.."],
    'E': ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    'F': ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    'G': [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"],
    'H': ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    'I': ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
    'J': ["..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."],
    'K': ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    'L': ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    'M': ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    'N': ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    'O': [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    'P': ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    'Q': [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
    'R': ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    'S': [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    'T': ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    'U': ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    'V': ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    'W': ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    'X': ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    'Y': ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
    'Z': ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
    ' ': ["...", "...", "...", "...", "...", "...", "..."],
    '·': ["...", "...", ".#.", "###", ".#.", "...", "..."],
}


def text_width(s):
    return sum(len(FONT[ch][0]) for ch in s) + (len(s) - 1)


# ----------------------------------------------------------------------------- sigils (7x7)

SIGILS = {
    'star': ["...#...", "...#...", "..###..", "###.###", "..###..", "...#...", "...#..."],
    'gem': [".#####.", "##.#.##", "#######", ".#####.", "..###..", "...#...", "......."],
    'coin': ["..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."],
    'crown': [".......", "#..#..#", "##.#.##", "#######", "#######", ".......", "#######"],
    'bolt': ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
    'book': ["##...##", "#.#.#.#", "#..#..#", "#..#..#", "#..#..#", "##.#.##", "...#..."],
    'claw': ["#.#.#..", "#.#.#..", ".#.#.#.", ".#.#.#.", ".#.#.#.", "..#.#.#", "..#.#.#"],
    'leaf': ["....###", "..#####", ".####.#", ".###.##", ".##.###", ".#.###.", "#......"],
    'paw': ["..#.#..", "..#.#..", "#.....#", "#.###.#", ".#####.", ".#####.", "..###.."],
    'skull': [".#####.", "#######", "#..#..#", "#######", ".##.##.", ".#####.", ".#.#.#."],
    'eye': [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."],
    'starfive': ["...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."],
    'bars': [".....#.", ".....#.", "...#.#.", "...#.#.", ".#.#.#.", ".#.#.#.", "#######"],
    'chest': [".#####.", "#.....#", "#######", "#..#..#", "#.....#", "#.....#", "#######"],
    'armor': ["##...##", "###.###", "#######", ".#####.", ".#####.", ".#####.", ".##.##."],
    'compass': ["...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."],
    'scroll': [".######", "#.#...#", ".##.#.#", "..#...#", "..#.#.#", "..#...#", "..#####"],
}


# ----------------------------------------------------------------------------- drawing


class Canvas:
    def __init__(self):
        self.im = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
        self.px = self.im.load()

    def put(self, gx, gy, c, a=255):
        x, y = gx + OX, gy + OY
        if 0 <= x < CANVAS and 0 <= y < CANVAS:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def get(self, gx, gy):
        return self.px[gx + OX, gy + OY]

    def rect(self, x0, y0, x1, y1, c):  # inclusive
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.put(x, y, c)

    def bitmap(self, rows, x, y, c):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '#':
                    self.put(x + i, y + j, c)

    def bitmap_mirror(self, rows, x, y, c):
        self.bitmap([r[::-1] for r in rows], x, y, c)

    def text(self, s, x, y, c):
        for ch in s:
            g = FONT[ch]
            self.bitmap(g, x, y, c)
            x += len(g[0]) + 1


def geometry(rows):
    i = (rows - 4) * 18
    h = 114 + 18 * rows
    return {
        'h': h,
        'cont_top': 17,                       # first container frame row
        'cont_bot': 17 + 18 * rows - 1,       # last container frame row
        'inv_top': 102 + i,                   # player inv frame top
        'inv_bot': 102 + i + 54 - 1,
        'bar_top': 160 + i,                   # hotbar frame top
        'bar_bot': 160 + i + 18 - 1,
        'label_y': h - 94,
    }


# GIGA elevate: metal rim, recessed wells, bracketed trays, banner plate. Geometry is unchanged.
RIM_HI = hx('#6e6888')
RIM_MID = hx('#433e58')
RIM_LO = hx('#16131f')


def draw_wells(cv, th, x0, y0, cols, rows_, brackets=(True, True, True, True)):
    # tray rim
    cv.rect(x0 - 1, y0 - 1, x0 + 18 * cols, y0 + 18 * rows_, th.t(TRAY))
    well = mix(th.t(WELL), th.t(OUT), 0.10)
    lip_sh = mix(th.t(WELL_SH), th.t(OUT), 0.35)
    lip_hi = mix(mix(th.t(WELL_HI), th.acc, 0.30), (255, 255, 255), 0.06)
    sh2 = mix(well, lip_sh, 0.55)
    hi2 = mix(well, lip_hi, 0.30)
    for r in range(rows_):
        for c in range(cols):
            x, y = x0 + 18 * c, y0 + 18 * r
            cv.rect(x, y, x + 17, y + 17, well)
            # two-step inner shadow under the top/left lip, soft light on the far lip
            cv.hline(x + 1, x + 16, y + 1, sh2)
            cv.vline(x + 1, y + 1, y + 16, sh2)
            cv.hline(x + 2, x + 16, y + 16, hi2)
            cv.vline(x + 16, y + 2, y + 16, hi2)
            cv.hline(x, x + 17, y, lip_sh)
            cv.vline(x, y, y + 17, lip_sh)
            cv.hline(x + 1, x + 17, y + 17, lip_hi)
            cv.vline(x + 17, y + 1, y + 17, lip_hi)
            cv.put(x + 17, y, th.t(WELL_MID))
            cv.put(x, y + 17, th.t(WELL_MID))
    # accent brackets hugging the tray corners (outside the rim)
    xa, ya, xb, yb = x0 - 2, y0 - 2, x0 + 18 * cols + 1, y0 + 18 * rows_ + 1
    for on, (cx, cy, dx, dy) in zip(brackets, ((xa, ya, 1, 1), (xb, ya, -1, 1), (xa, yb, 1, -1), (xb, yb, -1, -1))):
        if not on:
            continue
        cv.put(cx, cy, th.acc_dim)
        for k in (1, 2):
            cv.put(cx + dx * k, cy, th.acc_faint)
            cv.put(cx, cy + dy * k, th.acc_faint)


def draw_corner(cv, th, x, y, dx, dy):
    """Corner ornament on the inner ring at (x, y); dx/dy point inward: bracket arms + a gem."""
    for k in range(2, 9):
        c = th.acc if k <= 5 else th.acc_dim
        cv.put(x + dx * k, y, c)
        cv.put(x, y + dy * k, c)
    for k in range(2, 6):
        cv.put(x + dx * k, y + dy, th.acc_deep)
        cv.put(x + dx, y + dy * k, th.acc_deep)
    # curl at the end of each arm
    cv.put(x + dx * 9, y + dy, th.acc_dim)
    cv.put(x + dx, y + dy * 9, th.acc_dim)
    cv.put(x + dx * 2, y + dy * 2, th.acc_dim)
    cv.put(x, y, th.acc_hi)
    cv.put(x + dx, y, th.acc)
    cv.put(x, y + dy, th.acc)
    cv.put(x + dx, y + dy, th.acc_hi)


def draw_diamond(cv, cx, cy, r, fill, edge=None):
    for dy in range(-r, r + 1):
        w = r - abs(dy)
        for dx in range(-w, w + 1):
            on_edge = abs(dx) == w
            cv.put(cx + dx, cy + dy, edge if (edge and on_edge) else fill)


def draw_gem(cv, th, cx, cy, r):
    """Faceted accent gem: dark edge, lit top-left facet, bright pip."""
    draw_diamond(cv, cx, cy, r, th.acc_dim, th.t(OUT))
    for dy in range(-r + 1, r):
        w = r - 1 - abs(dy)
        for dx in range(-w, w + 1):
            cv.put(cx + dx, cy + dy, th.acc if dx + dy < 0 else th.acc_dim)
    cv.put(cx, cy, th.acc_hi)
    if r > 1:
        cv.put(cx - 1, cy - 1, mix(th.acc_hi, (255, 255, 255), 0.5))


def draw_panel(cv, th, g):
    x0, y0, x1, y1 = -2, -2, 177, g['h'] + 1
    cv.rect(x0, y0, x1, y1, th.t(OUT))
    # metal rim: lit top/left, shaded bottom/right
    rim_hi = mix(th.t(RIM_HI), th.acc, 0.45)
    rim_mid = mix(th.t(RIM_MID), th.acc, 0.22)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, rim_mid)
    cv.hline(x0 + 1, x1 - 1, y0 + 1, rim_hi)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, rim_hi)
    cv.hline(x0 + 1, x1 - 1, y1 - 1, th.t(RIM_LO))
    cv.vline(x1 - 1, y0 + 1, y1 - 1, th.t(RIM_LO))
    # groove + body, soft top light and a darker foot
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, th.t(BEV_LO))
    body = th.t(BODY)
    cv.rect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, body)
    for k, t in ((0, 0.24), (1, 0.12), (2, 0.05)):
        cv.hline(x0 + 3, x1 - 3, y0 + 3 + k, mix(body, th.t(BEV_HI), t))
    for k, t in ((0, 0.30), (1, 0.14)):
        cv.hline(x0 + 3, x1 - 3, y1 - 3 - k, mix(body, th.t(OUT), t))
    # header band: the strip the title plate sits on, lit from above, closed by an accent rule
    for y in range(2, 14):
        t = (y - 2) / 11.0
        c = mix(mix(body, th.acc, 0.16), body, t)
        cv.hline(2, 173, y, c)
    cv.hline(2, 173, 14, mix(body, th.acc, 0.30))
    cv.hline(2, 173, 15, mix(body, th.t(OUT), 0.35))
    # rivets on the rim
    for x in range(x0 + 12, x1 - 8, 24):
        cv.put(x, y1 - 1, rim_mid)
    for y in range(y0 + 14, y1 - 8, 24):
        cv.put(x0 + 1, y, mix(rim_hi, (255, 255, 255), 0.35))
        cv.put(x1 - 1, y, rim_mid)
    # chamfered outer corners (2px)
    for (cx, cy, dx, dy) in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        cv.put(cx, cy, (0, 0, 0), 0)
        cv.put(cx + dx, cy, (0, 0, 0), 0)
        cv.put(cx, cy + dy, (0, 0, 0), 0)
        cv.put(cx + dx, cy + dy, th.t(OUT))

    # inner accent ring (tooltip language)
    rx0, ry0, rx1, ry1 = 1, 1, 174, g['h'] - 2
    ring = mix(th.acc_faint, th.acc_dim, 0.5)
    cv.hline(rx0, rx1, ry0, ring)
    cv.hline(rx0, rx1, ry1, ring)
    cv.vline(rx0, ry0, ry1, ring)
    cv.vline(rx1, ry0, ry1, ring)
    draw_corner(cv, th, rx0, ry0, 1, 1)
    draw_corner(cv, th, rx1, ry0, -1, 1)
    draw_corner(cv, th, rx0, ry1, 1, -1)
    draw_corner(cv, th, rx1, ry1, -1, -1)

    # bottom keystone gem with little wings
    cx = 87
    for k in range(4, 9):
        cv.put(cx - k, ry1, th.acc_dim if k < 7 else th.acc_faint)
        cv.put(cx + k, ry1, th.acc_dim if k < 7 else th.acc_faint)
    draw_gem(cv, th, cx, ry1, 3)


def draw_divider(cv, th, g):
    """Rune line between the container and the player inventory."""
    top = g['cont_bot'] + 2       # first free row under the container tray rim
    bot = g['inv_top'] - 2        # last free row above the inventory tray rim
    cy = (top + bot) // 2
    cx = 87
    # leave the "Inventory" label zone (x 8..58) clean
    x_left, x_right = 64, 110
    for x in range(x_left, x_right + 1):
        d = abs(x - cx)
        if d <= 4:
            continue
        c = th.acc if d < 9 else (th.acc_dim if d < 17 else th.acc_faint)
        cv.put(x, cy, c)
        if d < 13:
            cv.put(x, cy + 1, th.acc_deep)
    for side in (-1, 1):
        sx = cx + side * 14
        cv.put(sx, cy - 1, th.acc_dim)
        cv.put(sx, cy + 1, th.acc_dim)
        cv.put(sx, cy, th.acc_hi)
        cv.put(cx + side * 5, cy - 1, th.acc_dim)
        cv.put(cx + side * 6, cy - 2, th.acc_faint)
    cv.put(x_left - 2, cy, th.acc_faint)
    cv.put(x_right + 2, cy, th.acc_faint)
    # side studs on the ring at the divider height
    for sx in (1, 174):
        cv.put(sx, cy, th.acc)
        cv.put(sx, cy - 1, th.acc_dim)
        cv.put(sx, cy + 1, th.acc_dim)
    draw_gem(cv, th, cx, cy, 3)


def draw_hotbar_seam(cv, th, g):
    y = (g['inv_bot'] + g['bar_top']) // 2  # in the 4px gap
    for x in (3, 4, 171, 172):
        cv.put(x, y, th.acc_faint)
    cv.put(87, y, th.acc_faint)


def draw_plate(cv, th):
    label = th.label
    tw = text_width(label)
    sig = SIGILS[th.sigil]
    sw = len(sig[0])
    gap = 4
    pad = 6
    inner_w = sw + gap + tw + gap + sw
    w = inner_w + 2 * pad + 2  # + borders
    cx = 88
    x0 = cx - w // 2
    x1 = x0 + w - 1
    y0, y1 = -8, 10
    border = th.plate_border
    border_hi = mix(border, (255, 255, 255), 0.30)
    border_lo = mix(border, (0, 0, 0), 0.35)
    tail = mix(th.acc_deep, th.acc, 0.25)
    # swallowtail ribbons behind the plate ends
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
    # shadow under plate (1px, onto panel)
    cv.hline(x0 + 1, x1 + 1, y1 + 1, th.t(BEV_LO))
    cv.vline(x1 + 1, y0 + 2, y1 + 1, th.t(BEV_LO))
    # wings: filigree that grows out past the ribbons along the inner ring
    for side in (-1, 1):
        base = x1 + 1 if side > 0 else x0 - 1
        for k in range(7, 24):
            x = base + side * k
            if not (8 <= x <= 167):
                break
            c = th.acc if k <= 10 else (th.acc_dim if k <= 17 else th.acc_faint)
            cv.put(x, 1, c)
            if k <= 9:
                cv.put(x, 2, th.acc_deep)
        bead = base + side * 26
        if 8 <= bead <= 167:
            cv.put(bead, 1, th.acc_dim)
            cv.put(bead, 0, th.acc_faint)
            cv.put(bead, 2, th.acc_faint)
    # outline + bevelled accent border
    cv.rect(x0, y0, x1, y1, th.t(OUT))
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, border)
    cv.hline(x0 + 1, x1 - 1, y0 + 1, border_hi)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, border_hi)
    cv.hline(x0 + 1, x1 - 1, y1 - 1, border_lo)
    cv.vline(x1 - 1, y0 + 1, y1 - 1, border_lo)
    # inset gap (double-line frame)
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, th.plate_lo)
    # fill: lit top, deeper foot
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
    # chamfered corners
    for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.put(x, y, (0, 0, 0), 0)
    for (x, y) in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        cv.put(x, y, th.t(OUT))
    for (x, y) in ((x0 + 2, y0 + 2), (x1 - 2, y0 + 2), (x0 + 2, y1 - 2), (x1 - 2, y1 - 2)):
        cv.put(x, y, border)
    # pips on the side borders
    for x in (x0 + 1, x1 - 1):
        cv.put(x, 1, th.acc_hi)

    tx = x0 + 1 + pad + 1
    ty = y0 + 6
    # sigils with a drop shadow, like the lettering
    cv.bitmap(sig, tx, ty + 1, th.ink_sh)
    cv.bitmap_mirror(sig, x1 - 1 - pad - sw, ty + 1, th.ink_sh)
    cv.bitmap(sig, tx, ty, th.sigil_c)
    cv.bitmap_mirror(sig, x1 - 1 - pad - sw, ty, th.sigil_c)
    # text with drop shadow
    lx = tx + sw + gap
    cv.text(label, lx, ty + 1, th.ink_sh)
    cv.text(label, lx, ty, th.ink)
    return x0, x1


def render(th, rows):
    g = geometry(rows)
    cv = Canvas()
    draw_panel(cv, th, g)
    draw_wells(cv, th, 7, g['cont_top'], 9, rows)
    draw_wells(cv, th, 7, g['inv_top'], 9, 3, brackets=(True, True, False, False))
    draw_wells(cv, th, 7, g['bar_top'], 9, 1, brackets=(False, False, True, True))
    draw_divider(cv, th, g)
    draw_hotbar_seam(cv, th, g)
    draw_plate(cv, th)
    return cv.im


# ----------------------------------------------------------------------------- catalogue

LILAC = '#9d86cf'
THEMES = [
    # key,                label,              accent,     sigil,      rows, glyph
    ('manager',          'AETHERION MANAGER', LILAC,     'star',     6, ''),
    ('shop',             'AETHER SHOP',       '#5cc2d6', 'gem',      5, ''),
    ('shop_boosts',      'SHOP · BOOSTS',     '#5cc2d6', 'gem',      5, ''),
    ('shop_materials',   'SHOP · MATERIALS',  '#5cc2d6', 'gem',      5, ''),
    ('shop_exclusive',   'SHOP · EXCLUSIVE',  '#5cc2d6', 'gem',      5, ''),
    ('bazaar',           'BAZAAR',            '#d6a24c', 'coin',     6, ''),
    ('auction',          'AUCTION HOUSE',     '#e2c766', 'crown',    6, ''),
    ('market_list',      'LIST ITEM',         '#d6a24c', 'coin',     1, ''),
    ('market_confirm',   'CONFIRM PURCHASE',  '#d6a24c', 'coin',     3, ''),
    ('skills',           'SKILLS',            '#7ea4e8', 'bolt',     6, ''),
    ('skills_overview',  'SKILLS · OVERVIEW', '#7ea4e8', 'bolt',     6, ''),
    ('skills_presets',   'SKILL PRESETS',     '#7ea4e8', 'bolt',     3, ''),
    ('recipes',          'RECIPE BOOK',       '#c99560', 'book',     6, ''),
    ('bestiary',         'BESTIARY',          '#cc5c66', 'claw',     6, ''),
    ('collection',       'COLLECTION',        '#62c08a', 'leaf',     6, ''),
    ('codex',            'AETHERION CODEX',   '#b294e6', 'eye',      6, ''),
    ('milestones',       'MILESTONES',        '#b294e6', 'starfive', 6, ''),
    ('journal',          'BOSS JOURNAL',      '#c9ad7e', 'skull',    6, ''),
    ('stats',            'STATS',             '#52baa9', 'bars',     6, ''),
    ('storage',          'STORAGE',           '#d48a52', 'chest',    6, ''),
    ('loadouts',         'LOADOUTS',          '#93a7bf', 'armor',    6, ''),
    ('spawns',           'SPAWNS',            '#a9c46c', 'compass',  6, ''),
    ('pets',             'PETS',              '#e083b3', 'paw',      6, ''),
]

PARCHMENT = {'journal'}


def make_theme(key, label, accent, sigil):
    if key in PARCHMENT:
        # Journal: parchment plate with ink lettering, warm sepia trim.
        return Theme(key, label, accent, sigil, plate='#d8c49a', ink='#3b2a1a', tint=0.08)
    tint = 0.07 if key in ('storage', 'recipes') else 0.05
    return Theme(key, label, accent, sigil, tint=tint)


def main(out_root):
    tex_dir = os.path.join(out_root, 'assets/minecraft/textures/font')
    os.makedirs(tex_dir, exist_ok=True)
    catalogue = []
    for key, label, accent, sigil, rows, glyph in THEMES:
        th = make_theme(key, label, accent, sigil)
        im = render(th, rows)
        name = 'gui_aetherion_manager.png' if key == 'manager' else f'gui_aetherion_{key}.png'
        im.save(os.path.join(tex_dir, name), optimize=True)
        catalogue.append({'key': key, 'label': label, 'rows': rows, 'glyph': glyph, 'file': name})
    with open(os.path.join(out_root, 'catalogue.json'), 'w', encoding='utf-8') as f:
        json.dump({'ox': OX, 'oy': OY, 'ascent': ASCENT, 'items': catalogue}, f, indent=2, ensure_ascii=False)
    return catalogue


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'out')
