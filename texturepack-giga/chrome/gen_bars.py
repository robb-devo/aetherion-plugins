#!/usr/bin/env python3
"""
Aetherion boss bar chrome (Minecraft 1.21.1, pack_format 34).

    python gen_bars.py <pack_root>        e.g. python gen_bars.py ../resourcepack

Writes:
    assets/minecraft/textures/gui/sprites/boss_bar/<colour>_{background,progress}.png   restyled, 182x5
    assets/aetherion/textures/font/bar/{frames,icons,icons_boss,icons_hint}.png
    assets/minecraft/font/default.json                                                  bar providers merged in

Geometry (decompiled 1.21.1 BossHealthOverlay):
    bar   x = guiWidth/2 - 91, y = 12 + 19n, 182 x 5, drawn first;
    title x = guiWidth/2 - width(title)/2, y = bar y - 9, drawn after the bar (with shadow).
    Bitmap glyph top = title y + 7 - ascent  ->  a pixel dy rows below the bar top needs ascent -2 - dy.
The title starts with a net-zero run of space glyphs + frame + icon, so the frame lands on the bar at any
GUI scale. Frames only touch row dy -1 above the bar inside the text span (text descender shadow row), the
taller caps sit left / right of the 182px bar.

PINK is the "ghost" colour: a faint hairline instead of a bar, reserved for quiet hints.

Java mirror: de.aetherion.core.api.BarChrome (codepoints, cap widths, icon sockets).
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_gui_chrome import OUT, hx, mix  # noqa: E402  one art language with the Manager chrome

BAR_W = 182
TOP, BOTTOM = -5, 10            # frame cell rows (dy), shared by every frame
FRAME_H = BOTTOM - TOP + 1
FRAME_ASCENT = -2 - TOP
FRAME_CELL_W = 224

FRAME_BASE = 0xE900
ICON_BASE, BOSS_ICON_BASE, HINT_ICON_BASE = 0xE920, 0xE940, 0xE950
NEG_SPACE, POS_SPACE = 0xE980, 0xE990    # -/+ 1, 2, 4 ... 256

WHITE = (255, 255, 255)
INK = hx('#0d0b12')
WELL_BG = hx('#120f17')


class Cv:
    def __init__(self, w, h, ox=0, oy=0):
        self.im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.px = self.im.load()
        self.w, self.h, self.ox, self.oy = w, h, ox, oy

    def put(self, x, y, c, a=255):
        x, y = x + self.ox, y + self.oy
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (c[0], c[1], c[2], a)

    def get(self, x, y):
        x, y = x + self.ox, y + self.oy
        return self.px[x, y] if 0 <= x < self.w and 0 <= y < self.h else (0, 0, 0, 0)

    def hline(self, x0, x1, y, c, a=255):
        for x in range(x0, x1 + 1):
            self.put(x, y, c, a)

    def vline(self, x, y0, y1, c, a=255):
        for y in range(y0, y1 + 1):
            self.put(x, y, c, a)

    def rect(self, x0, y0, x1, y1, c, a=255):
        for y in range(y0, y1 + 1):
            self.hline(x0, x1, y, c, a)

    def shape(self, inside, x0, y0, x1, y1, ring, body, hi=None):
        """Outline (INK) + accent ring + body for any boolean shape; hi = top-left ring highlight."""
        pts = {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1) if inside(x, y)}

        def depth(p):
            for d in range(0, 8):
                for dx in range(-d, d + 1):
                    for dy in range(-d, d + 1):
                        if max(abs(dx), abs(dy)) == d and (p[0] + dx, p[1] + dy) not in pts:
                            return d
            return 8
        for p in pts:
            d = depth(p)
            if d == 1:
                self.put(*p, INK)
            elif d == 2:
                lit = hi is not None and (p[0] - x0) + (p[1] - y0) < (x1 - x0 + y1 - y0) / 2
                self.put(*p, hi if lit else ring)
            else:
                self.put(*p, body)


# ----------------------------------------------------------------------------- bar sprites

BAR_HUES = {
    'red': hx('#c9372e'),
    'yellow': hx('#e0ac3a'),
    'green': hx('#4bb352'),
    'blue': hx('#3b92d8'),
    'purple': hx('#9256d0'),
    'white': hx('#d4d0dc'),
}
GHOST = hx('#d9c79a')


def bar_sprite(base, progress):
    """GIGA: glossy fill and a recessed well, both cut into ten segments (a tick every 18px)."""
    cv = Cv(BAR_W, 5)
    if progress:
        rows = (mix(OUT, base, 0.40), mix(base, WHITE, 0.50), mix(base, WHITE, 0.14), mix(base, OUT, 0.26),
                mix(OUT, base, 0.40))
    else:
        well = mix(WELL_BG, base, 0.20)
        rows = (mix(OUT, base, 0.16), mix(well, OUT, 0.40), well, mix(well, base, 0.08), mix(OUT, base, 0.16))
    for y, c in enumerate(rows):
        cv.hline(0, BAR_W - 1, y, c)
    for x in range(17, BAR_W - 2, 18):
        for y in (1, 2, 3):
            cv.put(x, y, mix(rows[y], OUT, 0.30 if progress else 0.45))
        if progress:
            cv.put(x + 1, 1, mix(rows[1], WHITE, 0.35))
    for x in (0, BAR_W - 1):
        cv.put(x, 0, (0, 0, 0), 0)
        cv.put(x, 4, (0, 0, 0), 0)
    return cv.im


def ghost_sprite(progress):
    cv = Cv(BAR_W, 5)
    if progress:
        cv.hline(2, BAR_W - 3, 2, GHOST, 150)
        cv.hline(8, BAR_W - 9, 1, GHOST, 22)
        cv.hline(8, BAR_W - 9, 3, GHOST, 22)
    else:
        cv.hline(2, BAR_W - 3, 2, GHOST, 40)
    return cv.im


# ----------------------------------------------------------------------------- frames
# Frame canvas coordinates are bar coordinates: x 0..181 / dy 0..4 is the bar itself.

FAMILIES = ('boss', 'phase', 'quest', 'event', 'gather', 'island', 'dungeon', 'ritual')
# (left cap, right cap) in px outside the bar; Java mirrors these.
CAPS = {
    'boss': (20, 20), 'phase': (3, 3), 'quest': (13, 4), 'event': (15, 7),
    'gather': (13, 8), 'island': (14, 5), 'dungeon': (13, 5), 'ritual': (15, 7),
}

GOLD = hx('#e2b13c')
CRIMSON = hx('#a3262c')
BRONZE = hx('#c08a4a')
PARCH = hx('#3a2d22')
AMBER = hx('#f08a2c')
TEAL = hx('#45c2b0')
LEAF = hx('#7cc46a')
SKY = hx('#2f4a3a')
STONE = hx('#8b8796')
VOID = hx('#22162e')
ARCANE = hx('#b07ae8')


def frame_canvas(fam):
    l, r = CAPS[fam]
    return Cv(FRAME_CELL_W, FRAME_H, ox=l, oy=-TOP)


def rails(cv, top, bottom, x0=-1, x1=BAR_W, ends=True):
    """1px rails hugging the bar: dy -1 / dy 5, optional side posts at x -1 / 182."""
    if top:
        for x in range(x0, x1 + 1):
            cv.put(x, -1, top(x) if callable(top) else top)
    if bottom:
        for x in range(x0, x1 + 1):
            cv.put(x, 5, bottom(x) if callable(bottom) else bottom)
    if ends:
        for y in range(0, 5):
            cv.put(-1, y, bottom if not callable(bottom) else bottom(-1))
            cv.put(BAR_W, y, bottom if not callable(bottom) else bottom(BAR_W))


def circle(cx, cy, r):
    return lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def frame_boss():
    cv = frame_canvas('boss')
    gold_hi, gold_lo = mix(GOLD, WHITE, 0.45), mix(GOLD, OUT, 0.45)
    rails(cv, gold_hi, gold_lo)
    # Pendant studs on the bottom rail at the quarters.
    for q in (1, 2, 3):
        x = round(BAR_W * q / 4) - 1
        cv.put(x, 6, GOLD)
        cv.put(x, 7, gold_lo)
        cv.put(x - 1, 6, gold_lo)
        cv.put(x + 1, 6, gold_lo)
    for cx, side in ((-10, -1), (BAR_W + 9, 1)):
        # Wing spikes out of the medallion.
        tip = cx + side * 10
        for i, (y0, y1) in enumerate(((2, 2), (1, 3), (1, 3))):
            cv.vline(tip - side * i, y0, y1, INK if i == 0 else gold_lo)
        cv.put(tip - side * 2, 2, GOLD)
        cv.shape(circle(cx, 2, 7.6), cx - 8, -6, cx + 8, 10, GOLD, mix(CRIMSON, OUT, 0.55), gold_hi)
        # Four studs on the ring (compass points).
        for dx, dy in ((0, -6), (0, 6), (-6, 0), (6, 0)):
            cv.put(cx + dx, 2 + dy, gold_hi)
    # Right medallion: a cut ruby instead of an icon.
    cx = BAR_W + 9
    gem = {(0, -3): 2, (-1, -2): 1, (0, -2): 2, (1, -2): 0, (-2, -1): 1, (-1, -1): 2, (0, -1): 2, (1, -1): 0,
           (2, -1): 0, (-3, 0): 1, (-2, 0): 2, (-1, 0): 2, (0, 0): 0, (1, 0): 0, (2, 0): 0, (3, 0): 3,
           (-2, 1): 0, (-1, 1): 0, (0, 1): 0, (1, 1): 3, (2, 1): 3, (-1, 2): 0, (0, 2): 3, (1, 2): 3, (0, 3): 3}
    shades = (hx('#d8323c'), hx('#ff9aa0'), hx('#ff5560'), hx('#7a1218'))
    for (dx, dy), s in gem.items():
        cv.put(cx + dx, 2 + dy, shades[s])
    return cv.im


def frame_phase():
    cv = frame_canvas('phase')
    line = mix(GOLD, OUT, 0.30)
    rails(cv, mix(GOLD, OUT, 0.15), line)
    for x, inward in ((-3, 1), (BAR_W + 2, -1)):
        cv.vline(x, -1, 5, mix(GOLD, WHITE, 0.30))
        cv.put(x + inward, -1, mix(GOLD, WHITE, 0.30))
        cv.put(x + inward, 5, mix(GOLD, WHITE, 0.30))
    return cv.im


def frame_quest():
    cv = frame_canvas('quest')
    rails(cv, mix(BRONZE, WHITE, 0.25), mix(BRONZE, OUT, 0.35))
    rr = lambda x, y: not ((x in (-13, -3)) and (y in (-3, 7)))
    cv.shape(rr, -13, -3, -3, 7, BRONZE, mix(PARCH, OUT, 0.20), mix(BRONZE, WHITE, 0.40))
    # End knob: a rounded rod end.
    cv.shape(lambda x, y: not (x == BAR_W + 3 and y in (-1, 5)), BAR_W, -1, BAR_W + 3, 5, BRONZE,
             mix(BRONZE, OUT, 0.25), mix(BRONZE, WHITE, 0.40))
    return cv.im


def frame_event():
    cv = frame_canvas('event')
    hot, dark = mix(AMBER, WHITE, 0.30), mix(AMBER, OUT, 0.55)
    # Hazard dashes: three lit, one dark, staggered top / bottom.
    rails(cv, lambda x: dark if (x + 1) % 4 == 0 else hot, lambda x: dark if (x + 3) % 4 == 0 else AMBER)
    banner = lambda x, y: y <= 7 or abs(x + 9) <= 9 - y
    cv.shape(banner, -15, -4, -3, 10, AMBER, mix(hx('#3b1d0c'), OUT, 0.25), mix(AMBER, WHITE, 0.45))
    # Right: a pennant streaming off the bar.
    pennant = lambda x, y: x - BAR_W <= 6 - abs(y - 2) * 2 + 0
    cv.shape(pennant, BAR_W, -1, BAR_W + 6, 5, AMBER, AMBER, hot)
    return cv.im


def frame_gather():
    cv = frame_canvas('gather')
    rails(cv, mix(TEAL, WHITE, 0.30), mix(TEAL, OUT, 0.40))
    cv.shape(circle(-8, 2, 5.6), -14, -4, -2, 8, TEAL, mix(hx('#10302c'), OUT, 0.20), mix(TEAL, WHITE, 0.45))
    cv.shape(circle(BAR_W + 4, 2, 3.6), BAR_W, -2, BAR_W + 8, 6, TEAL, mix(TEAL, WHITE, 0.35))
    return cv.im


def frame_island():
    cv = frame_canvas('island')
    rails(cv, None, mix(LEAF, OUT, 0.25), ends=False)
    # Octagon badge: calm, and no roof poking up into a long status line.
    octagon = lambda x, y: abs(x + 8) + abs(y - 2) <= 8
    cv.shape(octagon, -14, -3, -2, 7, LEAF, mix(SKY, OUT, 0.20), mix(LEAF, WHITE, 0.40))
    cv.shape(lambda x, y: not (x == BAR_W + 4 and y in (0, 4)), BAR_W, 0, BAR_W + 4, 4, LEAF,
             mix(LEAF, OUT, 0.20), mix(LEAF, WHITE, 0.40))
    return cv.im


def frame_dungeon():
    cv = frame_canvas('dungeon')
    stone_hi, stone_lo = mix(STONE, WHITE, 0.25), mix(STONE, OUT, 0.45)
    stud = lambda base: (lambda x: mix(STONE, WHITE, 0.55) if x % 13 == 6 else base)
    rails(cv, stud(stone_hi), stud(stone_lo))
    arch = lambda x, y: -13 <= x <= -3 and (y >= 0 or (x + 8) ** 2 + y ** 2 <= 5.5 ** 2)
    cv.shape(arch, -13, -6, -3, 7, STONE, mix(VOID, OUT, 0.10), stone_hi)
    cv.shape(lambda x, y: True, BAR_W, -2, BAR_W + 4, 6, STONE, stone_lo, stone_hi)
    cv.put(BAR_W + 2, 2, mix(STONE, WHITE, 0.55))
    return cv.im


def frame_ritual():
    cv = frame_canvas('ritual')
    dot = lambda base, alt: (lambda x: base if x % 2 == 0 else alt)
    rails(cv, dot(mix(ARCANE, WHITE, 0.30), mix(ARCANE, OUT, 0.55)), dot(ARCANE, mix(ARCANE, OUT, 0.60)))
    cv.shape(circle(-9, 2, 6.6), -16, -5, -2, 9, ARCANE, mix(hx('#1c1030'), OUT, 0.10), mix(ARCANE, WHITE, 0.45))
    for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0)):
        cv.put(-9 + dx, 2 + dy, GOLD)
    rune = lambda x, y: abs(x - (BAR_W + 3)) + abs(y - 2) <= 3
    cv.shape(rune, BAR_W, -1, BAR_W + 6, 5, ARCANE, GOLD)
    return cv.im


FRAME_ART = {
    'boss': frame_boss, 'phase': frame_phase, 'quest': frame_quest, 'event': frame_event,
    'gather': frame_gather, 'island': frame_island, 'dungeon': frame_dungeon, 'ritual': frame_ritual,
}

# ----------------------------------------------------------------------------- icons

C = hx
ICONS7 = [
    # name, art, {char: colour}
    ('quest', ['-#####-', '.#+o+#.', '.#+o+#.', '.#+o+#.', '.#+++#.', '.#+o+#.', '-#####-'],
     {'#': C('#c9a46a'), '+': C('#f3e3bd'), 'o': C('#5a2e16'), '-': C('#8a6a3c')}),
    ('quest_ready', ['......#', '.....##', '#...##.', '##.##..', '.###...', '..#....', '.......'],
     {'#': C('#62d977')}),
    ('quest_special', ['...#...', '..#+#..', '##+++##', '.#+++#.', '..#-#..', '.##.##.', '.#...#.'],
     {'#': C('#c87ae8'), '+': C('#f4d8ff'), '-': C('#7a3aa0')}),
    ('wheat', ['...+...', '.+.#.+.', '..+#+..', '.+.#.+.', '..+#+..', '...#...', '...#...'],
     {'#': C('#b98a2e'), '+': C('#f2cf63')}),
    ('pick', ['.+###-.', '#.....#', '...o...', '...o...', '...o...', '...o...', '...o...'],
     {'#': C('#b9c2cc'), '+': C('#ffffff'), '-': C('#6b7480'), 'o': C('#9a6a3a')}),
    ('fish', ['.......', '.###..#', '#o++###', '#####-.', '.---..#', '.......', '.......'],
     {'#': C('#5ab4e0'), '+': C('#c8ecff'), '-': C('#2f6f99'), 'o': C('#0d0b12')}),
    ('leaf', ['....###', '..##++#', '.#++#+#', '.#+#+-.', '.##--..', '.#.....', '#......'],
     {'#': C('#4fae4a'), '+': C('#a8e48a'), '-': C('#2c6e2a')}),
    ('bird', ['.......', '.......', '##...##', '.+#.#+.', '..-#-..', '...o...', '.......'],
     {'#': C('#3a3540'), '+': C('#8a8494'), '-': C('#1c1920'), 'o': C('#f2cf63')}),
    # A float reads at 7px where a thin hook doesn't.
    ('bobber', ['...o...', '..###..', '.#+##-.', '.-----.', '.=====.', '..===..', '...o...'],
     {'#': C('#e0443a'), '+': C('#ff9a8a'), '-': C('#0d0b12'), '=': C('#f4f2f8'), 'o': C('#8a8494')}),
    ('axe', ['.##.o..', '#++#o..', '#+++o..', '#++#o..', '.##.o..', '....o..', '....o..'],
     {'#': C('#9aa3ad'), '+': C('#e6edf2'), 'o': C('#9a6a3a')}),
    ('crystal', ['...#...', '..#+#..', '.#++-#.', '#++#--#', '.#+--#.', '..#-#..', '...#...'],
     {'#': C('#a35ad8'), '+': C('#e6c4ff'), '-': C('#5e2a8a')}),
    ('compass', ['..###..', '.#.+.#.', '#..+..#', '#..o..#', '#..-..#', '.#...#.', '..###..'],
     {'#': C('#c9a46a'), '+': C('#ff5a4a'), '-': C('#d8d4e0'), 'o': C('#ffffff')}),
    ('paw', ['..#.#..', '#.#.#.#', '#.....#', '..###..', '.#+++#.', '.#+++#.', '..###..'],
     {'#': C('#c87ae8'), '+': C('#f4d8ff')}),
    ('house', ['...#...', '..###..', '.#####.', '#######', '.++o++.', '.++o++.', '.+++++.'],
     {'#': C('#c2553a'), '+': C('#efe2c4'), 'o': C('#5a2e16')}),
    ('flag', ['o###...', 'o+++##.', 'o++++#.', 'o###...', 'o......', 'o......', 'o......'],
     {'#': C('#d9a441'), '+': C('#fff0a8'), 'o': C('#8a8494')}),
    ('star', ['...#...', '..#+#..', '##+++##', '.#+++#.', '..#-#..', '.##.##.', '.#...#.'],
     {'#': C('#e2c766'), '+': C('#fff3b0'), '-': C('#9c7f2a')}),
    ('warn', ['...#...', '..#+#..', '..#o#..', '.#+o+#.', '.#+++#.', '#++o++#', '#######'],
     {'#': C('#ff5a4a'), '+': C('#ffd9a0'), 'o': C('#0d0b12')}),
    ('arrow', ['...#...', '...##..', '#####+.', '#+++++#', '#####-.', '...##..', '...#...'],
     {'#': C('#52baf0'), '+': C('#d8f3ff'), '-': C('#2a6f99')}),
    ('eye', ['.......', '..###..', '.#+++#.', '#++o++#', '.#+++#.', '..###..', '.......'],
     {'#': C('#9aa3ad'), '+': C('#eef2f6'), 'o': C('#3b92d8')}),
    ('gate', ['.#####.', '#o#o#o#', '#o#o#o#', '#######', '#o#o#o#', '#o#o#o#', '#.#.#.#'],
     {'#': C('#9a96a6'), 'o': C('#1a1022')}),
    ('gate_open', ['.#####.', '#+++++#', '#+-+-+#', '#+++++#', '#+-+-+#', '#+++++#', '#+++++#'],
     {'#': C('#9a96a6'), '+': C('#a76ae8'), '-': C('#e6c4ff')}),
    ('chamber', ['.#####.', '#+++++#', '#oo+oo#', '#oo+oo#', '.#+-+#.', '..#+#..', '..#-#..'],
     {'#': C('#d8d4e0'), '+': C('#f4f2f8'), 'o': C('#1a1022'), '-': C('#8a8494')}),
    ('mill', ['..###..', '.#+-+#.', '#+-+-+#', '#-+o+-#', '#+-+-+#', '.#+-+#.', '..###..'],
     {'#': C('#8b8796'), '+': C('#c9c5d2'), '-': C('#5a5664'), 'o': C('#1a1022')}),
    ('anvil', ['.......', '#######', '.#+++##', '..###..', '..###..', '.#####.', '#######'],
     {'#': C('#5a5664'), '+': C('#c9c5d2')}),
    ('hourglass', ['#######', '.#...#.', '..#+#..', '...#...', '..#.#..', '.#+++#.', '#######'],
     {'#': C('#c9ad7e'), '+': C('#f2cf63')}),
]
ICONS9 = [
    ('skull', ['..#####..', '.#+++++#.', '#+++++++#', '#+oo+oo+#', '#+oo+oo+#', '.#++o++#.', '..#+++#..',
               '..#-#-#..', '...#.#...'],
     {'#': C('#d8d4e0'), '+': C('#f4f2f8'), 'o': C('#7a1218'), '-': C('#8a8494')}),
    ('sun', ['....+....', '.+..#..+.', '..##+##..', '..#+++#..', '+#+++++#+', '..#+++#..', '..##-##..',
             '.+..#..+.', '....+....'],
     {'#': C('#f0a830'), '+': C('#fff0a8'), '-': C('#b8661a')}),
    ('crown', ['.........', '#...#...#', '##.###.##', '#+##+##+#', '#+++++++#', '#+a+a+a+#', '#+++++++#',
               '#########', '.........'],
     {'#': C('#c99a2e'), '+': C('#f2cf63'), 'a': C('#ff5560')}),
    ('sigil', ['...###...', '.##+++##.', '#+.....+#', '#..###..#', '#.#+o+#.#', '#..###..#', '#+.....+#',
               '.##+++##.', '...###...'],
     {'#': C('#d0343c'), '+': C('#ff9a8a'), 'o': C('#fff0a8')}),
]
ICONS5 = [
    ('hint', ['..#..', '.#+#.', '#+++#', '.#+#.', '..#..'], {'#': C('#b89a5a'), '+': C('#e8d6a8')}, 190),
    ('hint_here', ['.###.', '#+++#', '#+++#', '#+++#', '.###.'], {'#': C('#4fae4a'), '+': C('#a8e48a')}, 210),
    ('hint_away', ['..#..', '.#.#.', '#...#', '.#.#.', '..#..'], {'#': C('#8a8494')}, 170),
]


def icon(art, colours, size, alpha=255):
    cv = Cv(size, size)
    for y, row in enumerate(art):
        assert len(row) == size, (art, row)
        for x, ch in enumerate(row):
            if ch in colours:
                cv.put(x, y, colours[ch], alpha)
    return mark_advance(cv.im)


def mark_advance(im):
    """Bitmap advance = rightmost non-transparent column + 1: an alpha-1 pixel pins it to the cell width."""
    px = im.load()
    w = im.width
    if not any(px[w - 1, y][3] for y in range(im.height)):
        px[w - 1, 0] = (0, 0, 0, 1)
    return im


# ----------------------------------------------------------------------------- default.json merge

OWN_FILES = 'aetherion:font/bar/'


def provider_line(p):
    return json.dumps(p, ensure_ascii=True)


def merge_default(path, providers):
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    kept = []
    for p in data['providers']:
        if p.get('file', '').startswith(OWN_FILES):
            continue
        if p.get('type') == 'space' and any(ord(k) >= NEG_SPACE and ord(k) < POS_SPACE + 16 for k in p.get('advances', {})):
            continue
        kept.append(p)
    out = kept + providers
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('{\n  "providers": [\n')
        f.write(',\n'.join('    ' + provider_line(p) for p in out))
        f.write('\n  ]\n}\n')


def chars(cps):
    return [''.join(chr(c) for c in cps)]


def main(root):
    sprites = os.path.join(root, 'assets/minecraft/textures/gui/sprites/boss_bar')
    os.makedirs(sprites, exist_ok=True)
    for name, base in BAR_HUES.items():
        bar_sprite(base, False).save(os.path.join(sprites, name + '_background.png'), optimize=True)
        bar_sprite(base, True).save(os.path.join(sprites, name + '_progress.png'), optimize=True)
    ghost_sprite(False).save(os.path.join(sprites, 'pink_background.png'), optimize=True)
    ghost_sprite(True).save(os.path.join(sprites, 'pink_progress.png'), optimize=True)

    tex = os.path.join(root, 'assets/aetherion/textures/font/bar')
    os.makedirs(tex, exist_ok=True)
    frames = Image.new('RGBA', (FRAME_CELL_W, FRAME_H * len(FAMILIES)), (0, 0, 0, 0))
    for i, fam in enumerate(FAMILIES):
        im = FRAME_ART[fam]()
        l, r = CAPS[fam]
        width = l + BAR_W + r
        px = im.load()
        assert any(px[width - 1, y][3] for y in range(FRAME_H)), fam + ': right cap must end at the cap width'
        assert not any(px[x, y][3] for x in range(width, FRAME_CELL_W) for y in range(FRAME_H)), fam + ': art past cap'
        frames.paste(im, (0, i * FRAME_H))
    frames.save(os.path.join(tex, 'frames.png'), optimize=True)

    def sheet(items, size, name):
        im = Image.new('RGBA', (size * len(items), size), (0, 0, 0, 0))
        for i, it in enumerate(items):
            im.paste(icon(it[1], it[2], size, it[3] if len(it) > 3 else 255), (i * size, 0))
        im.save(os.path.join(tex, name), optimize=True)

    sheet(ICONS7, 7, 'icons.png')
    sheet(ICONS9, 9, 'icons_boss.png')
    sheet(ICONS5, 5, 'icons_hint.png')

    providers = [
        {'type': 'space', 'advances': {**{chr(NEG_SPACE + i): -(1 << i) for i in range(9)},
                                       **{chr(POS_SPACE + i): 1 << i for i in range(9)}}},
        {'type': 'bitmap', 'file': OWN_FILES + 'frames.png', 'ascent': FRAME_ASCENT, 'height': FRAME_H,
         'chars': [chr(FRAME_BASE + i) for i in range(len(FAMILIES))]},
        # Icons centre on the bar's middle row (dy 2): 9px from dy -2, 7px from dy -1, 5px from dy 0.
        {'type': 'bitmap', 'file': OWN_FILES + 'icons_boss.png', 'ascent': 0, 'height': 9,
         'chars': chars(BOSS_ICON_BASE + i for i in range(len(ICONS9)))},
        {'type': 'bitmap', 'file': OWN_FILES + 'icons.png', 'ascent': -1, 'height': 7,
         'chars': chars(ICON_BASE + i for i in range(len(ICONS7)))},
        {'type': 'bitmap', 'file': OWN_FILES + 'icons_hint.png', 'ascent': -2, 'height': 5,
         'chars': chars(HINT_ICON_BASE + i for i in range(len(ICONS5)))},
    ]
    merge_default(os.path.join(root, 'assets/minecraft/font/default.json'), providers)

    print('frames', ' '.join(f'{f}={CAPS[f][0]}+{BAR_W}+{CAPS[f][1]}' for f in FAMILIES))
    print('icons7', ' '.join(n for n, *_ in ICONS7))
    print('icons9', ' '.join(n for n, *_ in ICONS9))
    print('icons5', ' '.join(n for n, *_ in ICONS5))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'out')
