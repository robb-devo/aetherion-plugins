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


def draw_wells(cv, th, x0, y0, cols, rows_):
    # tray rim
    cv.rect(x0 - 1, y0 - 1, x0 + 18 * cols, y0 + 18 * rows_, th.t(TRAY))
    for r in range(rows_):
        for c in range(cols):
            x, y = x0 + 18 * c, y0 + 18 * r
            cv.rect(x, y, x + 17, y + 17, th.t(WELL))
            cv.hline(x, x + 17, y, th.t(WELL_SH))
            cv.vline(x, y, y + 17, th.t(WELL_SH))
            cv.hline(x + 1, x + 17, y + 17, th.t(WELL_HI))
            cv.vline(x + 17, y + 1, y + 17, th.t(WELL_HI))
            cv.put(x + 17, y, th.t(WELL_MID))
            cv.put(x, y + 17, th.t(WELL_MID))


def draw_corner(cv, th, x, y, dx, dy):
    """Tooltip-family corner bracket at ring corner (x,y); dx/dy point inward."""
    for k in range(1, 5):
        cv.put(x + dx * k, y, th.acc)
        cv.put(x, y + dy * k, th.acc)
    cv.put(x, y, th.acc_hi)
    cv.put(x + dx, y + dy, th.acc_dim)  # soft inner fleck


def draw_diamond(cv, cx, cy, r, fill, edge=None):
    for dy in range(-r, r + 1):
        w = r - abs(dy)
        for dx in range(-w, w + 1):
            on_edge = abs(dx) == w
            cv.put(cx + dx, cy + dy, edge if (edge and on_edge) else fill)


def draw_panel(cv, th, g):
    x0, y0, x1, y1 = -2, -2, 177, g['h'] + 1
    cv.rect(x0, y0, x1, y1, th.t(OUT))
    # bevel
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, th.t(BEV_LO))
    cv.hline(x0 + 1, x1 - 2, y0 + 1, th.t(BEV_HI))
    cv.vline(x0 + 1, y0 + 1, y1 - 2, th.t(BEV_HI))
    # body
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, th.t(BODY))
    # very soft top light: the first two body rows a touch brighter
    light = mix(th.t(BODY), th.t(BEV_HI), 0.22)
    cv.hline(x0 + 2, x1 - 2, y0 + 2, light)
    # rounded outer corners (1px chamfer)
    for (cx, cy) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.put(cx, cy, (0, 0, 0), 0)

    # inner accent ring (tooltip language)
    rx0, ry0, rx1, ry1 = 1, 1, 174, g['h'] - 2
    cv.hline(rx0, rx1, ry0, th.acc_faint)
    cv.hline(rx0, rx1, ry1, th.acc_faint)
    cv.vline(rx0, ry0, ry1, th.acc_faint)
    cv.vline(rx1, ry0, ry1, th.acc_faint)
    draw_corner(cv, th, rx0, ry0, 1, 1)
    draw_corner(cv, th, rx1, ry0, -1, 1)
    draw_corner(cv, th, rx0, ry1, 1, -1)
    draw_corner(cv, th, rx1, ry1, -1, -1)

    # bottom keystone
    cx = 87
    draw_diamond(cv, cx, ry1, 2, th.acc_dim, th.acc)
    cv.put(cx, ry1, th.acc_hi)


def draw_divider(cv, th, g):
    """Quiet rune line between the container and the player inventory."""
    top = g['cont_bot'] + 2       # first free row under the container tray rim
    bot = g['inv_top'] - 2        # last free row above the inventory tray rim
    cy = (top + bot) // 2
    cx = 87
    # leave the "Inventory" label zone (x 8..58) clean
    x_left, x_right = 66, 108
    for x in range(x_left, x_right + 1):
        d = abs(x - cx)
        if d <= 3:
            continue
        # fade toward the ends: dim -> faint
        c = th.acc_dim if d < 12 else th.acc_faint
        cv.put(x, cy, c)
    cv.put(x_left - 2, cy, th.acc_faint)
    cv.put(x_right + 2, cy, th.acc_faint)
    draw_diamond(cv, cx, cy, 3, th.acc_deep, th.acc)
    cv.put(cx, cy, th.acc_hi)


def draw_hotbar_seam(cv, th, g):
    y = (g['inv_bot'] + g['bar_top']) // 2  # in the 4px gap
    # two tiny ticks either side, nothing more
    for x in (3, 4, 171, 172):
        cv.put(x, y, th.acc_faint)


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
    # shadow under plate (1px, onto panel)
    cv.hline(x0 + 1, x1 + 1, y1 + 1, th.t(BEV_LO))
    cv.vline(x1 + 1, y0 + 2, y1 + 1, th.t(BEV_LO))
    # wings: filigree that grows out of the plate along the inner ring
    for side in (-1, 1):
        base = x1 + 1 if side > 0 else x0 - 1
        for k in range(1, 22):
            x = base + side * k
            if not (8 <= x <= 167):
                break
            c = th.acc if k <= 5 else (th.acc_dim if k <= 13 else th.acc_faint)
            cv.put(x, 1, c)
            if k <= 3:
                cv.put(x, 0, th.acc_dim)
                cv.put(x, 2, th.acc_dim)
        # end bead (only when there is room before the corner bracket)
        bead = base + side * 24
        if 8 <= bead <= 167:
            cv.put(bead, 1, th.acc_dim)
    # outline
    cv.rect(x0, y0, x1, y1, th.t(OUT))
    # accent border
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, th.plate_border)
    # inset gap (double-line frame)
    cv.rect(x0 + 2, y0 + 2, x1 - 2, y1 - 2, th.plate_lo)
    # fill
    cv.rect(x0 + 3, y0 + 3, x1 - 3, y1 - 3, th.plate)
    cv.hline(x0 + 3, x1 - 3, y0 + 3, th.plate_hi)
    # chamfered corners
    for (x, y) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        cv.put(x, y, (0, 0, 0), 0)
    for (x, y) in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        cv.put(x, y, th.t(OUT))
    for (x, y) in ((x0 + 2, y0 + 2), (x1 - 2, y0 + 2), (x0 + 2, y1 - 2), (x1 - 2, y1 - 2)):
        cv.put(x, y, th.plate_border)

    tx = x0 + 1 + pad + 1
    ty = y0 + 6
    # sigils
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
    draw_wells(cv, th, 7, g['inv_top'], 9, 3)
    draw_wells(cv, th, 7, g['bar_top'], 9, 1)
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
