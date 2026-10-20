"""Generates the combat screen-glyph font (aetherion:combat) for the combat kit.

Each glyph is a 128x128 RGBA PNG shown through the title slot (client draws titles at 4x),
so at height 64 it spans roughly the middle third of the screen. Re-run after edits:

    python AetherionItems/resourcepack/tools/gen_combat_glyphs.py

Pixel art in the pack's style (AetherionItems/tools/px_art.py): hard edges, hand-picked ramps, outlines
tinted toward each glyph's own hue, light from the top left. Glows are stepped alpha bands, never blur.

Writes assets/aetherion/textures/font/combat/*.png and assets/aetherion/font/combat.json.
Chars U+E900..U+E906 are reserved for this font only (see CombatGlyphs.java).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools"))
from px_art import (AMBER, ASH, CRIMSON, EMBER, GOLD, SKY, STEEL, TEAL, VIOLET, WHITE, Px, edge, grow,  # noqa: E402
                    rim, shrink)

ROOT = os.path.join(HERE, "..", "assets", "aetherion")
TEX = os.path.join(ROOT, "textures", "font", "combat")
S = 128
C = S / 2


def halo(p, mask, colour, steps=((3, 70), (6, 34))):
    """Stepped glow: rings of fixed alpha around ``mask``, drawn under what is already there."""
    grown = mask
    done = mask.copy()
    for width, alpha in steps:
        for _ in range(width):
            grown = grow(grown, True)
        band = grown & ~done & ~p.solid()
        p.put(band, colour, alpha)
        done |= grown


def facet_ray(p, angle, length, half, rmp):
    """A four-sided ray from the centre: the half facing the light is lighter."""
    g = p.g
    ux, uy = math.cos(angle), math.sin(angle)
    vx, vy = -uy, ux
    base = 9.0
    tip = (C + ux * length, C + uy * length)
    left = (C + ux * base + vx * half, C + uy * base + vy * half)
    right = (C + ux * base - vx * half, C + uy * base - vy * half)
    root = (C, C)
    a = g.poly([root, left, tip])
    b = g.poly([root, tip, right])
    lit_a = (vx * -1 + vy * -1) > 0       # which side of the axis faces the top-left light
    return a, b, (a if lit_a else b), (b if lit_a else a)


def parry():
    p = Px(S, S)
    g = p.g
    shape = g.empty()
    lit, dark = g.empty(), g.empty()
    for k in range(8):
        ang = math.radians(-90 + 45 * k)
        length = 60 if k % 2 == 0 else 34
        half = 7.5 if k % 2 == 0 else 5.0
        a, b, l, d = facet_ray(p, ang, length, half, GOLD)
        shape |= a | b
        lit |= l
        dark |= d
    core = g.disc(C, C, 13.5)
    shape |= core
    halo(p, shape, GOLD[4])
    p.put(rim(shape), GOLD[0])
    p.put(dark & ~core, GOLD[3])
    p.put(lit & ~core, GOLD[5])
    p.put(edge(dark, 1, 1) & ~core, GOLD[2])
    p.put(core, GOLD[5])
    p.put(g.disc(C, C, 9.5), WHITE)
    p.put(g.disc(C - 3, C - 3, 3.0), WHITE)
    # sparks
    for sx, sy in ((26, 30), (100, 24), (106, 92), (22, 98), (88, 46), (40, 84)):
        p.put(g.rect(sx, sy, sx + 2, sy + 2), WHITE)
        p.put(rim(g.rect(sx, sy, sx + 2, sy + 2)) & ~p.solid(), GOLD[3])
    return p.image()


def dodge():
    p = Px(S, S)
    g = p.g
    streaks = ((18, 30, 96, 4), (30, 42, 110, 3), (8, 52, 78, 3), (24, 74, 104, 4), (12, 86, 88, 3), (34, 98, 116, 3))
    for x0, y, x1, th in streaks:
        body = g.rect(x0, y, x1, y + th)
        p.put(rim(body) & ~p.solid(), SKY[0])
        p.put(body, SKY[3])
        p.put(body & (g.y < y + 1), SKY[4])
        p.put(body & (g.x >= x1 - 10), SKY[5])
        p.put(body & (g.x >= x1 - 4), WHITE)
        # the tail breaks up into dashes
        tail = body & (g.x < x0 + (x1 - x0) * 0.35)
        gaps = tail & (np.mod(g.x.astype(int) - x0, 7) >= 5)
        p.erase(gaps)
        p.put(tail & ~gaps, SKY[2])
    # chevrons pointing the way of the dodge
    for cx in (96, 108):
        for i in range(9):
            for t in range(3):
                p.at(cx + i // 2 + t, 58 + i, WHITE if t < 2 else SKY[3])
                p.at(cx + i // 2 + t, 76 - i, WHITE if t < 2 else SKY[3])
    return p.image()


def winded():
    """A drained stamina ring, cracked, with two drops of sweat."""
    p = Px(S, S)
    g = p.g
    d = g.dist(C, C + 4)
    ang = g.angle(C, C + 4)
    ring = (d <= 40.5) & (d > 31.5)
    cracks = g.empty()
    for a0 in (20, 110, 200, 290):
        cracks |= np.abs(np.angle(np.exp(1j * (ang - math.radians(a0))))) < 0.07
    ring &= ~cracks
    t = np.cos(ang - math.radians(225))
    p.put(rim(ring), ASH[0])
    p.put(ring, ASH[2])
    p.put(ring & (t > 0.35), ASH[3])
    p.put(ring & (d > 39.0) & (t > 0.5), ASH[4])
    p.put(ring & (d < 33.0) & (t < -0.2), ASH[1])
    # the last sliver of stamina, amber, at the bottom
    sliver = ring & (ang > math.radians(70)) & (ang < math.radians(108))
    p.put(sliver, AMBER[3])
    p.put(sliver & (d > 38.5), AMBER[4])
    p.put(sliver & (d < 33.5), AMBER[2])
    # sweat drops
    drop = ['....oo....',
            '...ohlo...',
            '..ohlmmo..',
            '.ohlmmmso.',
            'ohllmmmsso',
            'ohlmmmmsso',
            'olmmmmmsso',
            '.ommmmsso.',
            '..osssso..',
            '...oooo...']
    pal = {'o': SKY[0], 'h': WHITE, 'l': SKY[4], 'm': SKY[3], 's': SKY[2]}
    p.sprite(drop, pal, 86, 18)
    p.sprite(drop, pal, 100, 40)
    return p.image()


def peril():
    """The perilous-attack mark: the kanji for danger on a red seal."""
    p = Px(S, S)
    g = p.g
    d = g.dist(C, C)
    t = np.cos(g.angle(C, C) - math.radians(225))
    seal = d <= 47.5
    halo(p, seal, CRIMSON[3], ((3, 90), (5, 40)))
    p.put(seal, CRIMSON[0])
    ring = (d <= 46.5) & (d > 40.5)
    p.put(ring, CRIMSON[3])
    p.put(ring & (t > 0.3), CRIMSON[4])
    p.put(ring & (d > 45.0) & (t > 0.55), CRIMSON[5])
    p.put(ring & (t < -0.45), CRIMSON[2])
    face = d <= 40.5
    p.put(face & ~shrink(face), CRIMSON[0])
    p.put(shrink(face), CRIMSON[1])
    p.put(shrink(face) & (d > 36.5) & (t < -0.3), CRIMSON[2])
    mask = kanji_mask("危", 58)
    if mask is not None:
        p.put(rim(mask, True) | rim(grow(mask), False) & ~mask, CRIMSON[0])
        p.put(mask, CRIMSON[4])
        p.put(mask & (edge(mask, -1, 0) | edge(mask, 0, -1)), CRIMSON[5])
        p.put(mask & edge(mask, 1, 0) & edge(mask, 0, 1), CRIMSON[3])
    else:
        p.put(g.rect(58, 36, 70, 76), CRIMSON[4])
        p.put(g.rect(58, 82, 70, 94), CRIMSON[4])
    return p.image()


def kanji_mask(ch, size):
    for path in ("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "C:/Windows/Fonts/YuGothB.ttc",
                 "C:/Windows/Fonts/msyhbd.ttc", "C:/Windows/Fonts/simsun.ttc"):
        if os.path.exists(path):
            font = ImageFont.truetype(path, size)
            break
    else:
        return None
    im = Image.new("L", (S, S), 0)
    ImageDraw.Draw(im).text((C, C + 1), ch, font=font, anchor="mm", fill=255, stroke_width=1, stroke_fill=255)
    return np.asarray(im) >= 128


def slash(rmp, mirror=False):
    """A crescent cut from the bottom left to the top right: the lens between two circles through the
    same two end points, so it is thickest in the middle and runs out to sharp points."""
    p = Px(S, S)
    g = p.g
    n = 1 / math.sqrt(2)
    o1, r1 = C + 51.9 * n, 90.0        # cutting edge
    o2, r2 = C + 107.2 * n, 130.0      # back of the blade trail
    outer = g.disc(o1, o1, r1)
    inner = g.disc(o2, o2, r2)
    cres = outer & ~inner
    along = (g.x - g.y + S) / (2 * S)            # 0 at bottom-left .. 1 at top-right
    halo(p, cres, rmp[4], ((2, 60), (3, 26)))
    p.put(rim(cres) & ~p.solid(), rmp[0])
    p.put(cres, rmp[3])
    edge_out = cres & ~shrink(cres) & ~grow(inner)
    p.put(cres & grow(inner), rmp[2])
    p.put(cres & ~shrink(shrink(cres)) & ~grow(inner), rmp[4])
    p.put(edge_out, rmp[5])
    p.put(edge_out & (along > 0.32) & (along < 0.72), WHITE)
    for sx, sy in ((25, 29), (13, 55), (49, 13)):
        p.put(g.rect(sx, sy, sx + 2, sy + 2), rmp[5])
    im = p.image()
    return im.transpose(Image.FLIP_LEFT_RIGHT) if mirror else im


def guard():
    """A raised guard: five steel plates in an arc above the centre, a teal line of light under them."""
    p = Px(S, S)
    g = p.g
    cy = C + 18
    d = g.dist(C, cy)
    ang = np.degrees(g.angle(C, cy))
    arc = (d <= 56.5) & (d > 44.5) & (ang > 200) & (ang < 340)
    plates = arc.copy()
    for a0 in (229, 256, 284, 311):
        plates &= ~(np.abs(ang - a0) < 1.4)
    t = np.cos(np.radians(ang) - math.radians(225))
    halo(p, plates, TEAL[4], ((2, 60), (3, 26)))
    p.put(rim(plates) & ~p.solid(), STEEL[0])
    p.put(plates, STEEL[3])
    p.put(plates & (d > 54.5), STEEL[5])
    p.put(plates & (d > 52.0) & (d <= 54.5), STEEL[4])
    p.put(plates & (d < 47.0), STEEL[2])
    p.put(plates & ~shrink(plates) & (t < -0.6), STEEL[1])
    glow = (np.abs(d - 41.0) < 1.2) & (ang > 208) & (ang < 332)
    p.put(glow, TEAL[4])
    p.put(glow & (np.abs(d - 41.0) < 0.5) & (ang > 240) & (ang < 300), TEAL[5])
    return p.image()


GLYPHS = [
    ("\ue900", "parry", parry),
    ("\ue901", "dodge", dodge),
    ("\ue902", "winded", winded),
    ("\ue903", "peril", peril),
    ("\ue904", "finisher", lambda: slash(EMBER)),
    ("\ue905", "riposte", lambda: slash(VIOLET, mirror=True)),
    ("\ue906", "guard", guard),
]


def main():
    os.makedirs(TEX, exist_ok=True)
    providers = []
    for char, name, fn in GLYPHS:
        fn().save(os.path.join(TEX, name + ".png"))
        providers.append({"type": "bitmap", "file": "aetherion:font/combat/%s.png" % name,
                          "ascent": 32, "height": 64, "chars": [char]})
    with open(os.path.join(ROOT, "font", "combat.json"), "w", encoding="utf-8") as f:
        json.dump({"providers": providers}, f, indent=2)
    print("wrote", len(GLYPHS), "glyphs")


if __name__ == "__main__":
    main()
