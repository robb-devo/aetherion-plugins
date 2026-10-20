"""Pixel-art helpers for the HUD, panel, radar, menu emblems and combat glyphs.

Everything is drawn the way the pack's items are: hard edges, no anti-aliasing, a few hand-picked colours
per material, dark outlines tinted toward the material's own hue (never black) and light from the top
left. Shapes are boolean masks sampled at texel centres; ``Px`` paints solid colours into them.

Ramps run dark to light: ``(outline, dark, shade, mid, light, highlight)``. The metal ramps copy the
structure of the charm medallions (``charm_*`` in the pack), which are the house style.

Minecraft samples font textures with nearest filtering, so a glyph drawn at an integer number of texels
per GUI pixel stays sharp; every HUD glyph uses 2 texels per GUI pixel, like the pack's 32 px items.
"""
import math

import numpy as np
from PIL import Image


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hexes):
    return tuple(hx(h) for h in hexes)


# outline, dark, shade, mid, light, highlight
GOLD = ramp('#3a1a12', '#7a3a1c', '#b4692a', '#e0a23c', '#f6d470', '#fff6cf')
COPPER = ramp('#3b1a10', '#7a3420', '#b45a2e', '#df8a4a', '#f5b47a', '#ffe0c2')
STEEL = ramp('#1a1828', '#383a52', '#5d6684', '#8f9cb8', '#c6d3e3', '#f4f8ff')
SLATE = ramp('#0e0a16', '#17121f', '#1f1a2a', '#2a2338', '#3a3150', '#54476e')
VIOLET = ramp('#1a0630', '#3c0e66', '#6a1eaa', '#9a52e0', '#c79cff', '#f1e2ff')
CRIMSON = ramp('#1e0208', '#480812', '#7e1020', '#c0283a', '#f2645a', '#ffc2a8')
EMBER = ramp('#2e0b12', '#6b1d16', '#b2421a', '#ec7a26', '#ffb648', '#fff0a6')
TEAL = ramp('#06181e', '#0d3a40', '#14685f', '#22a08a', '#5fdcb8', '#cffff0')
EMERALD = ramp('#0b2014', '#164a26', '#237a30', '#45ad3c', '#8fdc5e', '#e4ffb4')
SKY = ramp('#0c1430', '#173a74', '#2662b4', '#3f98e6', '#86d0ff', '#e2f6ff')
AMBER = ramp('#2a1406', '#6a3a0a', '#a8660e', '#e09a1a', '#ffd04a', '#fff3b0')
BONE = ramp('#2a1a18', '#5a4030', '#8c6c4c', '#c2a274', '#e8d3a2', '#fff7de')
ROSE = ramp('#2a0618', '#5e1236', '#9c2458', '#d8457a', '#ff86a8', '#ffd8e2')
ASH = ramp('#141218', '#2c2a34', '#4a4856', '#706e7e', '#a19fae', '#d8d6e2')

WHITE = (255, 255, 255)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# ----------------------------------------------------------------------------- masks

class Grid:
    """Texel-centre coordinates for a w x h sprite."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
        self.x = xs + 0.5
        self.y = ys + 0.5

    def empty(self):
        return np.zeros((self.h, self.w), dtype=bool)

    def disc(self, cx, cy, r):
        return (self.x - cx) ** 2 + (self.y - cy) ** 2 <= r * r

    def ellipse(self, cx, cy, rx, ry):
        return ((self.x - cx) / rx) ** 2 + ((self.y - cy) / ry) ** 2 <= 1.0

    def ring(self, cx, cy, r_out, r_in):
        return self.disc(cx, cy, r_out) & ~self.disc(cx, cy, r_in)

    def rect(self, x0, y0, x1, y1):
        """Texels x0..x1-1, y0..y1-1 (integer texel bounds)."""
        m = self.empty()
        m[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = True
        return m

    def poly(self, pts):
        """Texels whose centre lies inside the polygon (even-odd)."""
        inside = self.empty()
        n = len(pts)
        for i in range(n):
            ax, ay = pts[i]
            bx, by = pts[i - 1]
            if ay == by:
                continue
            cond = (self.y >= min(ay, by)) & (self.y < max(ay, by))
            xint = ax + (self.y - ay) * (bx - ax) / (by - ay)
            inside ^= cond & (self.x < xint)
        return inside

    def angle(self, cx, cy):
        """Screen angle from +x, clockwise (y grows down), 0..2pi."""
        return np.mod(np.arctan2(self.y - cy, self.x - cx), 2 * math.pi)

    def dist(self, cx, cy):
        return np.hypot(self.x - cx, self.y - cy)

    def line(self, x0, y0, x1, y1, thick=1):
        """A Bresenham line between texel coordinates; ``thick`` 2 adds the texel to the right/below."""
        m = self.empty()
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            for ox in range(thick):
                for oy in range(thick):
                    if 0 <= x0 + ox < self.w and 0 <= y0 + oy < self.h:
                        m[y0 + oy, x0 + ox] = True
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy
        return m


def shift(mask, dx, dy):
    """mask moved by (dx, dy); texels shifted in from outside are False."""
    out = np.zeros_like(mask)
    h, w = mask.shape
    ys = slice(max(0, dy), min(h, h + dy))
    yd = slice(max(0, -dy), min(h, h - dy))
    xs = slice(max(0, dx), min(w, w + dx))
    xd = slice(max(0, -dx), min(w, w - dx))
    out[ys, xs] = mask[yd, xd]
    return out


def grow(mask, diag=False):
    out = mask | shift(mask, 1, 0) | shift(mask, -1, 0) | shift(mask, 0, 1) | shift(mask, 0, -1)
    if diag:
        out |= shift(mask, 1, 1) | shift(mask, -1, 1) | shift(mask, 1, -1) | shift(mask, -1, -1)
    return out


def shrink(mask):
    return mask & shift(mask, 1, 0) & shift(mask, -1, 0) & shift(mask, 0, 1) & shift(mask, 0, -1)


def rim(mask, diag=False):
    """The one-texel line just outside ``mask``."""
    return grow(mask, diag) & ~mask


def edge(mask, dx, dy):
    """Texels of ``mask`` whose neighbour at (dx, dy) is outside it."""
    return mask & ~shift(mask, -dx, -dy)


def clean(mask):
    """Drops single texels that touch nothing orthogonally (stray stair steps)."""
    n = shift(mask, 1, 0) | shift(mask, -1, 0) | shift(mask, 0, 1) | shift(mask, 0, -1)
    return mask & n


# ----------------------------------------------------------------------------- canvas

class Px:
    def __init__(self, w, h):
        self.g = Grid(w, h)
        self.w, self.h = w, h
        self.px = np.zeros((h, w, 4), dtype=np.uint8)

    def put(self, mask, colour, alpha=255):
        self.px[mask, 0:3] = colour
        self.px[mask, 3] = alpha

    def at(self, x, y, colour, alpha=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y, x, 0:3] = colour
            self.px[y, x, 3] = alpha

    def erase(self, mask):
        self.px[mask] = 0

    def solid(self):
        return self.px[..., 3] > 0

    def outline(self, mask, colour, diag=False, alpha=255):
        self.put(rim(mask, diag) & ~self.solid(), colour, alpha)

    def shade(self, mask, rmp, light=(-1, -1), soft=False):
        """Flat-shaded fill: mid tone, light texels on the edges facing the light, highlight on the lit
        corner, shade then dark on the far edges. ``soft`` skips the dark band (small or thin shapes)."""
        lx, ly = light
        self.put(mask, rmp[3])
        far = edge(mask, -lx, 0) | edge(mask, 0, -ly)
        self.put(far, rmp[2])
        if not soft:
            self.put(edge(mask, -lx, 0) & edge(mask, 0, -ly), rmp[1])
        lit = edge(mask, lx, 0) | edge(mask, 0, ly)
        self.put(lit & ~far, rmp[4])
        self.put(edge(mask, lx, 0) & edge(mask, 0, ly) & ~far, rmp[5])

    def sprite(self, rows, palette, x0=0, y0=0):
        """Stamps an ASCII sprite; '.' is transparent, other characters index ``palette``."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != '.' and ch != ' ':
                    col = palette[ch]
                    if len(col) == 4:
                        self.at(x0 + x, y0 + y, col[:3], col[3])
                    else:
                        self.at(x0 + x, y0 + y, col)

    def pin_advance(self):
        """Minecraft measures a glyph's advance up to its last non-transparent column. A texel with alpha
        1/255 in that column fixes the advance and is never drawn (the text shader discards alpha < 0.1)."""
        if self.px[:, -1, 3].max() == 0:
            self.px[self.h // 2, -1] = (0, 0, 0, 1)

    def image(self):
        return Image.fromarray(self.px, 'RGBA')


def from_rows(rows, palette):
    h, w = len(rows), max(len(r) for r in rows)
    p = Px(w, h)
    p.sprite(rows, palette)
    return p


def upscale(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


# ----------------------------------------------------------------------------- Minecraft font metrics

def mc_advance(im, height):
    """Advance Minecraft gives a one-glyph bitmap: round(actual width * height / texture height) + 1."""
    alpha = np.asarray(im)[..., 3]
    cols = np.where(alpha.max(axis=0) > 0)[0]
    actual = 0 if len(cols) == 0 else int(cols[-1]) + 1
    return int(0.5 + actual * (height / im.height)) + 1


def check_advance(name, im, height, expected):
    got = mc_advance(im, height)
    if got != expected:
        raise SystemExit('%s: Minecraft advance %d, Java expects %d' % (name, got, expected))
