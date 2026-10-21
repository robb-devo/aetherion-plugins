"""World VFX art (called by gen_panel.py): slash, ring, rune and telegraph glyphs, white, tinted in game.

The HUD art (status frame, radar, markers) lives in gen_carrier.py.
"""
import math

import numpy as np

from px_art import Px, grow, upscale

# ----------------------------------------------------------------------------- world VFX (white, tinted in game)

def vfx(kind):
    """64-texel pixel art, doubled to the 128 texels the font expects (same glyph metrics as before)."""
    n = 64
    p = Px(n, n)
    g = p.g
    c = n / 2
    d = g.dist(c, c)
    ang = g.angle(c, c)
    hi, mid, lo = (255, 255, 255), (214, 214, 222), (160, 160, 172)
    if kind == 'slash':
        outer = g.disc(c, c, 30.4)
        inner = g.disc(c, c + 11.5, 29.0)
        cres = outer & ~inner & (g.y < c + 6)
        p.put(cres, mid, 230)
        p.put(cres & ~shift_down(cres), hi)
        p.put(cres & ~grow(~cres & outer & (g.y < c + 6)) & (np.abs(g.x - c) < 18), hi)
        tips = cres & (np.abs(g.x - c) > 21)
        p.put(tips, lo, 150)
    elif kind == 'ring':
        band = (d <= 29.6) & (d > 26.4)
        p.put(band, mid, 235)
        p.put(band & (d > 28.6), hi)
        p.put(band & (d <= 27.4), lo, 200)
    elif kind == 'rune':
        p.put((d <= 30.6) & (d > 29.0), hi)
        p.put((d <= 23.4) & (d > 22.2), mid, 235)
        for k in range(24):
            a0 = 2 * math.pi * k / 24
            on = np.abs(np.angle(np.exp(1j * (ang - a0)))) < (0.06 if k % 3 else 0.11)
            p.put(on & (d < 28.6) & (d > 24.4), hi if k % 3 == 0 else mid, 235)
        for k in range(6):
            a = math.radians(k * 60 - 90)
            b = math.radians(k * 60 + 30)
            p.put(g.line(c - 0.5 + math.cos(a) * 21.5, c - 0.5 + math.sin(a) * 21.5,
                         c - 0.5 + math.cos(b + math.radians(60)) * 21.5, c - 0.5 + math.sin(b + math.radians(60)) * 21.5),
                  mid, 235)
        p.put(d < 2.6, hi)
    elif kind == 'tele_edge':
        band = (d <= 30.6) & (d > 28.2)
        on = np.mod(ang / (2 * math.pi) * 32, 1.0) < 0.58
        p.put(band & on, hi)
        p.put(band & ~on, lo, 110)
    elif kind == 'tele_fill':
        disc = d <= 30.4
        p.put(disc, mid, 70)
        p.put(disc & (d > 20.0), mid, 100)
        p.put(disc & (d > 26.0), hi, 140)
    return upscale(p.image(), 2)


def shift_down(mask):
    from px_art import shift
    return shift(mask, 0, 1)
