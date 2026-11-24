#!/usr/bin/env python3
"""The Aether Journal's hotbar book, one cover per stage (32x32, item/generated on BOOK).

    python gen_journal_cover.py [preview.png]

gen_gui_icons.py writes them with the menu icons (COVERS, CMD 7900 + stage on BOOK). The three covers share one
silhouette, so the book reads as the same book growing with the player:

  0 Worn Journal    faded tan leather, scuffs, yellowed pages, a twine marker, no metal and no rune
  1 Bound Journal   brown leather, brass corners and studs, a strap with a buckle, the A pressed into the leather
  2 Aether Journal  the old Aetherion Manager book (blue, gold, the A rune), now with the rune breathing light
                    and the spark on the corner twinkling (an 8-frame animation, interpolated)
"""
import math
import sys

from PIL import Image

# The Aetherion Manager book (32x32), one letter per colour. '.' is clear.
PALETTE = {
    'a': (0x2a, 0x3a, 0x8a), 'b': (0x19, 0x20, 0x6f), 'c': (0x0d, 0x02, 0x55), 'd': (0x53, 0x6f, 0xb0),
    'e': (0x87, 0x83, 0x8a), 'f': (0x1f, 0x1b, 0x25), 'g': (0x0c, 0x06, 0x34), 'h': (0xed, 0xca, 0x74),
    'i': (0x8f, 0xd9, 0xec), 'j': (0xb8, 0xb3, 0xbb), 'k': (0x3e, 0x24, 0x0a), 'l': (0x87, 0xa4, 0xd6),
    'm': (0xbe, 0xee, 0xf8), 'n': (0x19, 0x09, 0x2d), 'o': (0xb5, 0xef, 0xf5), 'p': (0xe2, 0xb1, 0x3c),
    'q': (0x20, 0x12, 0x0f), 'r': (0x2a, 0x18, 0x47), 's': (0x70, 0x5a, 0x9f), 't': (0x46, 0x30, 0x71),
    'u': (0xac, 0x75, 0x01), 'v': (0x9d, 0x86, 0xcf), 'w': (0xf9, 0xe1, 0xa2), 'x': (0x0b, 0x08, 0x1f),
    'y': (0xbc, 0xa8, 0xe4), 'z': (0xff, 0xff, 0xff),
}
BASE = (
    "................................",
    "................................",
    "..........................o.....",
    ".....kkggggggggggggggggkkozo....",
    "....kwhcdllllllllddddddhpqo.....",
    "....khccadddddddaaaaaaabpeef....",
    "....gcccadddddddaaaaaaaacejf....",
    "....gcccaddddddaaaaaaaaabeef....",
    "....gcccadddddambaaaaaaabejf....",
    "....gcccadddddmambaaaaaabeef....",
    "....gcccaddddambmbaaaaaabejf....",
    "....gcccadddambacmbaaaaabeef....",
    "....gcccaddaaiiiiibaaaaabejf....",
    "....gcccaddaibbbbbibaaaabeef....",
    "....gcccadaaibaaaaibaaaacejf....",
    "....gcccaaaibaaaaabibaaaceef....",
    "....gcccbaaibaaaaaaibaabcejf....",
    "....gcccbaabaaaaaaabaabbceef....",
    "....gcccbaaaaaahbaaaaabbcejf....",
    "....gcccbahbhbabaahbhcbbceef....",
    "....gcccbabhbaaaaabhcbbbcejf....",
    "....gcccbaabaaahbaacbbbbceef....",
    "....gcccbaaaaaabaaabbbbbcejf....",
    "....gcccbaaaaaaaaabbbbbbceef....",
    "....gcccbaaaaaaabbbbbbbbcejf....",
    "....khccbaaaaaaabbbbbccbheef....",
    "....kpuccbbbbbbbcccccsthuejf....",
    ".....qqxeeeeeeeeeeeeevseejef....",
    "........ffffffffffffrytnfff.....",
    "....................rvtn........",
    ".....................rsn........",
    "......................n.........",
)
SIZE = 32
RUNE = (9, 7, 21, 22)          # x0, y0, x1, y1 (inclusive): the A and the studs under it
SPARK = 'oz'
FRAMES = 8


def hexc(value):
    value = value.lstrip('#')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def in_rune(x, y):
    return RUNE[0] <= x <= RUNE[2] and RUNE[1] <= y <= RUNE[3]


def paint(recolour, fn=None):
    """The base art with each letter recoloured ({letter: '#rrggbb' or None = clear}); fn(x, y, ch) may override."""
    im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(BASE):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            colour = fn(x, y, ch) if fn else None
            if colour is None:
                colour = recolour.get(ch, PALETTE[ch]) if ch in recolour else PALETTE[ch]
                if colour is None:
                    continue
                if isinstance(colour, str):
                    colour = hexc(colour)
            elif colour == 'clear':
                continue
            elif isinstance(colour, str):
                colour = hexc(colour)
            px[x, y] = colour + (255,)
    return im


def _noise(x, y, seed):
    """Deterministic 0..1 per pixel."""
    v = math.sin(x * 12.9898 + y * 78.233 + seed * 37.719) * 43758.5453
    return v - math.floor(v)


def _rune_shadow(x, y):
    """A 'b' pixel that belongs to the A or its studs (next to a rune, stud or rune-shadow pixel), not cover shade."""
    if not in_rune(x, y) or BASE[y][x] != 'b':
        return False
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if BASE[y + dy][x + dx] in 'imh':
            return True
    return False


def worn():
    leather = {'a': '#8b6b47', 'b': '#715537', 'c': '#5c422a', 'd': '#97774f', 'l': '#a88a63', 'g': '#2e2116',
               'k': '#2e2116', 'q': '#2e2116', 'x': '#2e2116',
               'h': '#a88a63', 'p': '#8b6b47', 'u': '#5c422a', 'w': '#b39470',
               'e': '#b8a27a', 'j': '#d6c49c', 'f': '#2a1d12',
               'n': '#2e2116', 'r': '#5e4a33', 't': '#7a6246', 's': '#8f7656', 'v': '#a88d69', 'y': '#bfa37c',
               'o': None, 'z': None}
    scuffs = {(18, 8): '#a88a63', (19, 9): '#a88a63', (9, 22): '#a1835c', (9, 23): '#a1835c', (10, 23): '#a1835c',
              (15, 12): '#715537', (16, 13): '#715537', (16, 14): '#715537', (12, 18): '#a1835c'}

    def fn(x, y, ch):
        if (x, y) in scuffs:
            return scuffs[(x, y)]
        if in_rune(x, y) and (ch in 'imh' or _rune_shadow(x, y)):
            # no rune yet: the leather where the A will be pressed, same tone as around it
            return leather['d'] if BASE[y][x - 1] == 'd' or BASE[y - 1][x] == 'd' else leather['a']
        return None
    return paint(leather, fn)


def bound():
    leather = {'a': '#6b3a1f', 'b': '#4f2915', 'c': '#3a1d0f', 'd': '#8a4f2a', 'l': '#a8683c', 'g': '#1e0f08',
               'k': '#3e240a', 'q': '#1e0f08', 'x': '#1e0f08',
               'h': '#d4ac5c', 'p': '#a98038', 'u': '#6e4f1c', 'w': '#f0d999',
               'e': '#cdbb94', 'j': '#e7dcc0', 'f': '#2a1d12',
               'n': '#2a0a0a', 'r': '#5a1414', 't': '#7d1f1f', 's': '#a33030', 'v': '#c84a4a', 'y': '#e27a6a',
               'o': None, 'z': None}

    def fn(x, y, ch):
        if in_rune(x, y) and ch in 'im':
            return '#9a5c33' if ch == 'm' else '#8a4f2a'   # the A pressed into the leather, catching the light
        # a strap across the fore-edge with a brass buckle
        if 14 <= y <= 16 and 20 <= x <= 27 and ch != '.':
            if y == 14 or y == 16:
                return '#2a140a'
            if 21 <= x <= 23:
                return '#f0d999' if x == 21 else '#d4ac5c'
            return '#5a2e16'
        return None
    im = paint(leather, fn)
    px = im.load()
    for y in (14, 15, 16):              # the buckle's frame and tongue
        px[20, y] = hexc('#a98038') + (255,)
        px[24, y] = hexc('#a98038') + (255,)
    px[22, 15] = hexc('#3a1d0f') + (255,)
    return im


def aether(frame):
    """The Manager book; the rune breathes light and the corner spark twinkles."""
    t = frame / FRAMES
    glow = 0.5 - 0.5 * math.cos(2 * math.pi * t)      # 0 → 1 → 0 over the loop

    def mix(c1, c2, k):
        c1, c2 = hexc(c1), hexc(c2)
        return '#%02x%02x%02x' % tuple(round(a + (b - a) * k) for a, b in zip(c1, c2))

    spark = (1.0, 1.0, 0.6, 0.0, 0.0, 0.0, 0.4, 0.8)[frame]

    def fn(x, y, ch):
        if ch == 'i' and in_rune(x, y):
            return mix('#6fc3dc', '#c6f6ff', glow)
        if ch == 'm' and in_rune(x, y):
            return mix('#a6e6f6', '#ffffff', glow)
        if ch == 'b' and in_rune(x, y) and glow > 0.55:
            return mix('#19206f', '#2f4aa0', (glow - 0.55) / 0.45)   # light spills into the stroke's shadow
        if ch == 'o':
            return 'clear' if spark < 0.5 else ('#b5eff5' if spark < 0.9 else '#d9fbff')
        if ch == 'z':
            return 'clear' if spark <= 0.0 else ('#ffffff' if spark >= 0.6 else '#b5eff5')
        return None
    return paint({}, fn)


def covers():
    """(name, image, animated): image is 32x32, or a vertical strip of FRAMES for the animated one."""
    strip = Image.new('RGBA', (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.alpha_composite(aether(f), (0, SIZE * f))
    return [('journal_cover_worn', worn(), False),
            ('journal_cover_bound', bound(), False),
            ('journal_cover_aether', strip, True)]


ANIMATION = {'animation': {'frametime': 5, 'interpolate': True}}


def preview(path, scale=8):
    items = covers()
    im = Image.new('RGBA', (len(items) * (SIZE + 8) * scale, (SIZE + 8) * scale), (0xe3, 0xd1, 0xa4, 255))
    for k, (_name, art, animated) in enumerate(items):
        one = art.crop((0, 0, SIZE, SIZE)) if animated else art
        im.alpha_composite(one.resize((SIZE * scale, SIZE * scale), Image.NEAREST), (k * (SIZE + 8) * scale + 4 * scale,
                                                                                    4 * scale))
    im.save(path)


if __name__ == '__main__':
    preview(sys.argv[1] if len(sys.argv) > 1 else 'journal_covers.png')
