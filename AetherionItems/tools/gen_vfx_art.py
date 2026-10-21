#!/usr/bin/env python3
"""Display-effect art for the pack font aetherion:vfx (DisplayVfx draws them on text displays).

    python gen_vfx_art.py            # writes ../resourcepack/assets/aetherion/{textures/font/vfx,font/vfx.json}

White art with stepped alpha (the text colour tints it), the same pixel language as the slash / ring / rune
glyphs drawn before. Every glyph is height 32, ascent 18: a text display draws it 0.8 blocks tall with its centre
0.1 blocks above the entity, whatever the image width (DisplayVfx counts on that).

Glyphs (char: what it is, image size):
    E500 slash  E501 ring  E502 rune  E503 tele_edge  E504 tele_fill    (hand-drawn earlier, kept as they are)
    E505 streak     comet / shooting star: round head on the right, tapering tail     512x64
    E506 burst      eight-point star flash with a halo                                 128x128
    E507 orb        soft glowing disc                                                  128x128
    E508 spark      four-point twinkle                                                 128x128
    E509 flame      flame tongue, outer body                                           128x128
    E50A flame_core flame tongue, hot core (drawn over E509 in a lighter tint)          128x128
    E50B pillar     column of light, fading upward                                     128x512
    E50C bolt       jagged lightning bolt                                              512x64
    E50D ribbon     aurora curtain, bright foot fading upward                          512x128
    E50E mote       small soft dot (embers, fireflies, glints)                         32x32
    E50F wave       thick shockwave band                                               128x128
    E510 beam       straight bar with a bright core                                    512x32
    E511 arc        upper half of a ring (rainbows, halos over a horizon)              256x128
"""
import json
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.normpath(os.path.join(HERE, '..', 'resourcepack', 'assets', 'aetherion'))
TEX = os.path.join(PACK, 'textures', 'font', 'vfx')
FONT = os.path.join(PACK, 'font', 'vfx.json')

# alpha steps: the hand-drawn glyphs use a few flat bands, not smooth ramps
STEPS = (255, 200, 140, 80, 36)


def band(t):
    """0..1 intensity -> a stepped alpha (0 below the last step)."""
    if t <= 0.0:
        return 0
    for i, a in enumerate(STEPS):
        if t >= 1.0 - (i + 1) / len(STEPS):
            return a
    return 0


def canvas(w, h):
    return Image.new('RGBA', (w, h), (255, 255, 255, 0))


def put(im, x, y, a):
    if 0 <= x < im.width and 0 <= y < im.height and a > 0:
        r, g, b, old = im.getpixel((x, y))
        if a > old:
            im.putpixel((x, y), (255, 255, 255, a))


def radial(im, cx, cy, r, core=0.35):
    """Soft disc: full inside core*r, stepping out to r."""
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if d <= core:
                put(im, x, y, 255)
            elif d <= 1.0:
                put(im, x, y, band(1.0 - (d - core) / (1.0 - core)))


def streak():
    im = canvas(512, 64)
    hx, cy = 476, 32
    # tail: thickness and brightness fall off toward the left
    for x in range(0, hx):
        t = x / hx                       # 0 at the far tail, 1 at the head
        half = 1.5 + 13.0 * t ** 1.6
        for y in range(64):
            d = abs(y + 0.5 - cy) / half
            if d <= 1.0:
                put(im, x, y, band(t ** 0.9 * (1.0 - d * 0.55)))
        put(im, x, cy - 1, band(min(1.0, t * 1.4)))      # bright spine
        put(im, x, cy, band(min(1.0, t * 1.4)))
    radial(im, hx, cy, 26, core=0.30)
    return im


def burst():
    im = canvas(128, 128)
    c = 64
    radial(im, c, c, 30, core=0.25)
    for k in range(8):
        ang = k * math.pi / 4
        length = 62 if k % 2 == 0 else 40
        width = 7.0 if k % 2 == 0 else 5.0
        for s in range(0, length):
            t = s / length
            w = width * (1.0 - t)
            px = c + math.cos(ang) * s
            py = c + math.sin(ang) * s
            nx, ny = -math.sin(ang), math.cos(ang)
            for o in range(-int(w) - 1, int(w) + 2):
                a = band((1.0 - t) * (1.0 - abs(o) / max(w, 0.6)))
                put(im, int(round(px + nx * o)), int(round(py + ny * o)), a)
    return im


def orb():
    im = canvas(128, 128)
    radial(im, 64, 64, 60, core=0.28)
    return im


def spark():
    im = canvas(128, 128)
    c = 64
    for axis in ((1, 0), (0, 1)):
        for s in range(-60, 61):
            t = 1.0 - abs(s) / 60.0
            w = 1 + 7 * t ** 2.2
            for o in range(-int(w) - 1, int(w) + 2):
                a = band(t * (1.0 - abs(o) / w))
                x = c + (s if axis[0] else o)
                y = c + (o if axis[0] else s)
                put(im, x, y, a)
    radial(im, c, c, 12, core=0.4)
    return im


def flame(core):
    """Teardrop pointing up; the core is a smaller, brighter tongue inside it."""
    im = canvas(128, 128)
    cx = 64
    base_y, tip_y = 118, 8 if not core else 40
    max_w = 46 if not core else 24
    for y in range(128):
        if y < tip_y or y > base_y:
            continue
        t = (y - tip_y) / (base_y - tip_y)          # 0 at the tip, 1 at the base
        w = max_w * t ** 1.35                        # a tongue: needle tip, widest low down
        if t > 0.8:
            w *= math.sqrt(max(0.0, 1.0 - ((t - 0.8) / 0.2) ** 2))   # round foot
        sway = math.sin(t * 5.0) * 3.0 * (1.0 - t)
        for x in range(128):
            d = abs(x + 0.5 - cx - sway) / max(w, 0.5)
            if d <= 1.0:
                put(im, x, y, band((1.0 - d) * (0.55 + 0.45 * t) + (0.25 if core else 0.0)))
    return im


def pillar():
    im = canvas(128, 512)
    cx = 64
    for y in range(512):
        t = 1.0 - y / 511.0            # 1 at the top, 0 at the foot
        fade = (1.0 - t) ** 0.7
        w = 30 + 14 * (1.0 - t) ** 3   # flares at the foot
        for x in range(128):
            d = abs(x + 0.5 - cx) / w
            if d <= 1.0:
                put(im, x, y, band(fade * (1.0 - d ** 1.5)))
    return im


def bolt():
    im = canvas(512, 64)
    import random
    rnd = random.Random(7)
    pts = [(0, 32)]
    x = 0
    while x < 512:
        x = min(512, x + rnd.randint(28, 52))
        pts.append((x, 32 + rnd.randint(-20, 20) if x < 512 else 32))
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            px = x0 + (x1 - x0) * i / n
            py = y0 + (y1 - y0) * i / n
            for o in range(-6, 7):
                put(im, int(px), int(py) + o, band(1.0 - abs(o) / 6.5))
    return im


def ribbon():
    im = canvas(512, 128)
    for x in range(512):
        foot = 96 + 10 * math.sin(x / 512 * math.pi * 3.0) + 4 * math.sin(x / 512 * math.pi * 11.0)
        stria = 0.75 + 0.25 * math.sin(x * 0.21) * math.sin(x * 0.047)
        for y in range(128):
            if y > foot + 3:
                continue
            t = y / max(foot, 1.0)          # 0 top .. 1 at the foot
            put(im, x, y, band(t ** 2.2 * stria))
    return im


def mote():
    im = canvas(32, 32)
    radial(im, 16, 16, 14, core=0.30)
    return im


def wave():
    im = canvas(128, 128)
    for y in range(128):
        for x in range(128):
            d = math.hypot(x + 0.5 - 64, y + 0.5 - 64)
            if 40 <= d <= 63:
                t = (d - 40) / 23.0
                put(im, x, y, band(math.sin(t * math.pi) ** 0.8 * (0.5 + 0.5 * t)))
    return im


def arc():
    im = canvas(256, 128)
    for y in range(128):
        for x in range(256):
            d = math.hypot(x + 0.5 - 128, 128 - (y + 0.5))
            if 112 <= d <= 126:
                put(im, x, y, band(math.sin((d - 112) / 14.0 * math.pi) ** 0.7))
    return im


def beam():
    im = canvas(512, 32)
    for x in range(512):
        for y in range(32):
            d = abs(y + 0.5 - 16) / 15.0
            put(im, x, y, band(1.0 - d ** 1.4))
    return im


NEW = [
    ('', 'streak', streak),
    ('', 'burst', burst),
    ('', 'orb', orb),
    ('', 'spark', spark),
    ('', 'flame', lambda: flame(False)),
    ('', 'flame_core', lambda: flame(True)),
    ('', 'pillar', pillar),
    ('', 'bolt', bolt),
    ('', 'ribbon', ribbon),
    ('', 'mote', mote),
    ('', 'wave', wave),
    ('', 'beam', beam),
    ('', 'arc', arc),
]
OLD = [('', 'slash'), ('', 'ring'), ('', 'rune'), ('', 'tele_edge'), ('', 'tele_fill')]


def main():
    os.makedirs(TEX, exist_ok=True)
    providers = []
    for ch, name in OLD:
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/vfx/%s.png' % name, 'ascent': 18, 'height': 32,
                          'chars': [ch]})
    for ch, name, draw in NEW:
        draw().save(os.path.join(TEX, name + '.png'), optimize=True)
        providers.append({'type': 'bitmap', 'file': 'aetherion:font/vfx/%s.png' % name, 'ascent': 18, 'height': 32,
                          'chars': [ch]})
    with open(FONT, 'w', encoding='utf-8') as f:
        json.dump({'providers': providers}, f, indent=1)
    print('vfx: %d glyphs' % len(providers))


if __name__ == '__main__':
    main()
