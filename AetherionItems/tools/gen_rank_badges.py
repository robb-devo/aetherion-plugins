#!/usr/bin/env python3
"""Rank badges: pill-shaped glyphs for the cosmetic ranks and the account level, in chat, the tab list and
above heads (de.aetherion.items.rank.RankGlyphs builds the strings).

Usage: python gen_rank_badges.py [preview_dir]

The glyphs live in the font ``aetherion:badges``; build_served_pack.py adds a ``reference`` to it into the
live ``minecraft:default`` font, because rank prefixes are legacy strings (LuckPerms, TAB, chat format) that
cannot pick a font. Codepoints (mirrored in RankGlyphs.java):

  U+EA00         space, advance -1 (joins the pieces of a level badge)
  U+EA10..EA15   owner, coowner, mvpplusplus, monkey, citrus, beta
  U+EA20 + 16 * tier + k   level badge of title tier 0..13 (AetherionLevel.titleTier):
                 k = 0 left cap with the tier's emblem, 1 right cap, 2..11 the digits 0..9

Art is drawn at 2 texels per GUI pixel (like the HUD), 18 texels high = 9 GUI px, ascent 8, so a badge sits
on the text line like a capital letter with half a pixel above and below. Pieces of a level badge have even
widths: advance = width / 2 + 1, and the -1 space between them closes the gap, so the pill is seamless.
Colours avoid red 76..79: the HUD text shader moves GUI glyphs coloured (76, 4n, b) (gen_carrier.py).

Writes into AetherionItems/resourcepack:
  assets/aetherion/font/badges.json
  assets/aetherion/textures/font/badges/*.png
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen_hud import FONT as SMALL  # noqa: E402  3x5 caps and digits
from px_art import AMBER, CRIMSON, EMERALD, GOLD, ROSE, SKY, STEEL, TEAL, VIOLET, hx, mix, ramp  # noqa: E402

PACK = os.path.join(HERE, '..', 'resourcepack')
TEX = ('assets', 'aetherion', 'textures', 'font', 'badges')
REF = 'aetherion:font/badges/'

S = 2                    # texels per GUI pixel
H = 18                   # texture height (9 GUI px)
ASCENT = 8
NEG = 0xEA00
COSMETIC = ['owner', 'coowner', 'mvpplusplus', 'monkey', 'citrus', 'beta']
COSMETIC_BASE = 0xEA10
LEVEL_BASE = 0xEA20

# Ramps run (outline, dark, shade, mid, light, highlight).
PINK = ramp('#3a0628', '#7d0f56', '#c41d8c', '#ff3fbf', '#ff8bdc', '#ffe2f6')
ICE = ramp('#06262c', '#0f5560', '#2a95a2', '#62d2dc', '#b2ffff', '#f0ffff')
CITRUS = ramp('#2c2006', '#6a4a08', '#c27a0c', '#ffb703', '#ffe14a', '#fff8c8')
LIME = ramp('#14240a', '#2c5410', '#4c8c18', '#78c828', '#b4f05a', '#efffc8')
YELLOW = ramp('#2a2406', '#62540c', '#a89014', '#e2c81e', '#fff064', '#fffbd2')
BLOOD = ramp('#1c0204', '#40060a', '#6c0c12', '#a01a1e', '#d84a3c', '#ffb0a0')
CYAN = ramp('#062028', '#0c4a5a', '#16808e', '#2ab8c4', '#7ee8ee', '#e0ffff')
MAGENTA = ramp('#2a0628', '#5e1058', '#9a2290', '#d040c0', '#f48ce4', '#ffe0fa')
ROYAL = ramp('#12062a', '#2c0c5e', '#4c1a98', '#7438d0', '#a878f4', '#ecdcff')

# title tier (AetherionLevel.titleTier) -> ramp, emblem. Tiers follow AetherionLevel.color().
TIERS = [
    (STEEL, 'dot'),        # 0 Adventurer   §f
    (LIME, 'sword'),       # 1 Veteran      §a
    (SKY, 'diamond'),      # 2 Champion     §9
    (AMBER, 'spark'),      # 3 Legend       §6
    (GOLD, 'spark'),       # 4 Legend 100+  §6§l
    (MAGENTA, 'star'),     # 5 Mythwright   §d
    (VIOLET, 'orb'),       # 6 Aetherborn   §5§l
    (CYAN, 'spark'),       # 7 Celestine    §b
    (YELLOW, 'crown'),     # 8 Sovereign    §e
    (GOLD, 'crown'),       # 9 Sovereign 500+
    (TEAL, 'gem'),         # 10 Ascendant   §3
    (CRIMSON, 'flame'),    # 11 Empyrean    §c§l
    (BLOOD, 'star'),       # 12 Eternal     §4§l
    (ROYAL, 'crown'),      # 13 Aetherion   §5§l
]

# 5x5 emblems (GUI px; drawn 2x2 texels each)
EMBLEMS = {
    'dot': [".....", "..#..", ".###.", "..#..", "....."],
    'sword': ["....#", "...#.", "#.#..", ".#...", "#.#.."],
    'diamond': ["..#..", ".###.", "#####", ".###.", "..#.."],
    'spark': ["..#..", "..#..", "##.##", "..#..", "..#.."],
    'star': ["..#..", ".###.", "#####", ".#.#.", "#...#"],
    'orb': [".###.", "##.##", "#.#.#", "##.##", ".###."],
    'crown': ["#.#.#", "#####", "#####", ".....", "#####"],
    'gem': [".###.", "#####", ".###.", "..#..", "....."],
    'flame': ["..#..", ".##..", ".###.", "#####", ".###."],
    'heart': [".#.#.", "#####", "#####", ".###.", "..#.."],
}


def canvas(w):
    return Image.new('RGBA', (w, H), (0, 0, 0, 0))


def put(im, x, y, c, a=255):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), tuple(c) + (a,))


def body_colour(r, y, x=None, w=None):
    """Fill colour of texel row y: a soft top-lit gradient."""
    stops = [r[4], r[3], r[3], r[2]]
    t = (y - 2) / (H - 5)
    t = min(max(t, 0.0), 1.0)
    if t < 0.33:
        return mix(stops[0], stops[1], t / 0.33)
    if t < 0.66:
        return stops[1]
    return mix(stops[2], stops[3], (t - 0.66) / 0.34)


def pill(im, r, x0, x1, left=True, right=True, fill=None):
    """Pill body over texel columns x0..x1-1; rounded ends where left/right."""
    for x in range(x0, x1):
        for y in range(H):
            edge_l = left and x - x0 < 2
            edge_r = right and x1 - 1 - x < 2
            corner = (y < 2 or y > H - 3) and (edge_l or edge_r)
            if corner:
                # round 2-texel corners: drop the very corner, outline the next one
                dx = (x - x0) if edge_l else (x1 - 1 - x)
                dy = y if y < 2 else H - 1 - y
                if dx + dy < 2:
                    continue
                if dx + dy == 2:
                    put(im, x, y, r[0])
                    continue
            if y == 0 or y == H - 1 or (left and x == x0) or (right and x == x1 - 1):
                put(im, x, y, r[0])
            elif y == 1:
                put(im, x, y, mix(r[4], r[5], 0.6))
            elif y == H - 2:
                put(im, x, y, r[1])
            else:
                put(im, x, y, fill(x, y) if fill else body_colour(r, y))


def stamp(im, rows, x, y, colour, shadow, scale=S):
    """Draws a 1-bit glyph at (x, y) texels, each glyph pixel scale x scale texels, with a 1-texel shadow."""
    for gy, row in enumerate(rows):
        for gx, ch in enumerate(row):
            if ch != '#':
                continue
            for sy in range(scale):
                for sx in range(scale):
                    put(im, x + gx * scale + sx + 1, y + gy * scale + sy + 1, shadow)
    for gy, row in enumerate(rows):
        for gx, ch in enumerate(row):
            if ch != '#':
                continue
            for sy in range(scale):
                for sx in range(scale):
                    put(im, x + gx * scale + sx, y + gy * scale + sy, colour)


def text_rows(text):
    return [SMALL[ch] for ch in text]


def text_width(text):
    """Width in GUI px of 3x5 text with 1 px between letters."""
    return sum(len(SMALL[ch][0]) for ch in text) + len(text) - 1


def draw_text(im, text, x, y, colour, shadow, colours=None):
    for i, ch in enumerate(text):
        c = colours[i] if colours else colour
        stamp(im, SMALL[ch], x, y, c, shadow)
        x += (len(SMALL[ch][0]) + 1) * S


TEXT_Y = 4  # texel row of the 3x5 text top (10 texels tall, rows 4..13)


def cosmetic(kind):
    if kind == 'owner':
        r, label, icon, ink = CRIMSON, 'OWNER', 'crown', GOLD[5]
        icon_c = GOLD[4]
    elif kind == 'coowner':
        r, label, icon, ink = PINK, 'CO-OWNER', 'heart', (255, 255, 255)
        icon_c = PINK[5]
    elif kind == 'mvpplusplus':
        r, label, icon, ink = GOLD, 'MVP++', None, (255, 255, 255)
        icon_c = None
    elif kind == 'monkey':
        r, label, icon, ink = ICE, 'MONKEY', None, ICE[0]
        icon_c = None
    elif kind == 'citrus':
        r, label, icon, ink = CITRUS, 'CITRUS', None, CITRUS[0]
        icon_c = None
    else:
        r, label, icon, ink = VIOLET, 'BETA', None, (255, 255, 255)
        icon_c = None
    pad = 3
    icon_w = (5 + 1) * S if icon else 0
    w = 1 + pad + icon_w + text_width(label) * S + pad + 1
    w += w % 2
    im = canvas(w)
    fill = None
    if kind == 'beta':
        hues = [hx(h) for h in ('#ff5a5a', '#ffb03a', '#ffe94a', '#5ee06a', '#4ab8ff', '#9a6aff', '#ff5ad8')]

        def fill(x, y):
            t = x / max(1, w - 1) * (len(hues) - 1)
            i = min(int(t), len(hues) - 2)
            base = mix(hues[i], hues[i + 1], t - i)
            return mix(base, (255, 255, 255), 0.18) if y < 6 else mix(base, (0, 0, 0), 0.12 if y > 12 else 0.0)
        r = ramp('#24103a', '#3c1e5c', '#6a3aa0', '#9a6ad0', '#d4bcff', '#ffffff')
    pill(im, r, 0, w, fill=fill)
    x = 1 + pad
    shadow = mix(r[1], r[0], 0.4)
    if icon:
        stamp(im, EMBLEMS[icon], x, TEXT_Y, icon_c, shadow)
        x += icon_w
    colours = None
    if kind == 'mvpplusplus':
        colours = [ink, ink, ink, CRIMSON[4], CRIMSON[4]]
    if kind in ('monkey', 'citrus'):
        shadow = mix(r[4], r[5], 0.5)
    draw_text(im, label, x, TEXT_Y, ink, shadow, colours)
    return im


def level_piece(tier, k):
    """k: 0 left cap (emblem), 1 right cap, 2..11 digit k-2."""
    r, emblem = TIERS[tier]
    ink = (255, 255, 255) if tier not in (0, 8) else r[0]
    shadow = mix(r[1], r[0], 0.4) if ink == (255, 255, 255) else mix(r[4], r[5], 0.5)
    if k == 0:
        w = 1 + 3 + 5 * S + 2
        w += w % 2
        im = canvas(w)
        pill(im, r, 0, w, left=True, right=False)
        stamp(im, EMBLEMS[emblem], 4, TEXT_Y, r[5] if ink == (255, 255, 255) else r[0], shadow)
        return im
    if k == 1:
        im = canvas(4)
        pill(im, r, 0, 4, left=False, right=True)
        return im
    digit = str(k - 2)
    rows = SMALL[digit]
    w = 4 * S
    im = canvas(w)
    pill(im, r, 0, w, left=False, right=False)
    off = (3 - len(rows[0])) * S // 2
    stamp(im, rows, off, TEXT_Y, ink, shadow)
    return im


def main(preview=None):
    tex = os.path.join(PACK, *TEX)
    os.makedirs(tex, exist_ok=True)
    providers = [{'type': 'space', 'advances': {chr(NEG): -1}}]
    images = {}
    for i, kind in enumerate(COSMETIC):
        im = cosmetic(kind)
        name = 'rank_%s.png' % kind
        im.save(os.path.join(tex, name))
        images[kind] = im
        providers.append({'type': 'bitmap', 'file': REF + name, 'ascent': ASCENT, 'height': H // S,
                          'chars': [chr(COSMETIC_BASE + i)]})
    for tier in range(len(TIERS)):
        for k in range(12):
            im = level_piece(tier, k)
            name = 'level_%02d_%02d.png' % (tier, k)
            im.save(os.path.join(tex, name))
            images[(tier, k)] = im
            providers.append({'type': 'bitmap', 'file': REF + name, 'ascent': ASCENT, 'height': H // S,
                              'chars': [chr(LEVEL_BASE + 16 * tier + k)]})
    font = os.path.join(PACK, 'assets', 'aetherion', 'font', 'badges.json')
    with open(font, 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'providers': providers}, f, indent=1)
    print('rank badges: %d glyphs' % (len(providers) - 1))
    if preview:
        write_preview(images, preview)


def write_preview(images, out_dir):
    """Mock-up at 4 screen px per GUI px on a chat-like background."""
    rows = [[images[k] for k in COSMETIC]]
    for tier in range(len(TIERS)):
        level = [1, 25, 50, 75, 100, 150, 200, 250, 350, 500, 750, 1250, 2500, 5000][tier]
        pieces = [images[(tier, 0)]] + [images[(tier, 2 + int(d))] for d in str(level)] + [images[(tier, 1)]]
        rows.append(pieces)
    width = max(sum(p.width for p in row) + 4 * len(row) for row in rows) + 8
    height = (H + 6) * len(rows) + 8
    sheet = Image.new('RGBA', (width, height), (24, 22, 30, 255))
    y = 4
    for i, row in enumerate(rows):
        x = 4
        for p in row:
            sheet.alpha_composite(p, (x, y))
            x += p.width + (4 if i == 0 else 0)
        y += H + 6
    sheet = sheet.resize((width * 2, height * 2), Image.NEAREST)
    os.makedirs(out_dir, exist_ok=True)
    sheet.save(os.path.join(out_dir, 'rank_badges_preview.png'))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
