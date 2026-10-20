"""Anti-aliased art helpers for the HD HUD glyphs (radar, panel plates, icons, markers).

Shapes are signed-distance fields sampled on a texel grid and turned into coverage with a one-texel
ramp, so edges are smooth without blurring. Every function works in texel units on numpy arrays and
returns float coverage 0..1; ``Canvas`` composites colour layers over each other (premultiplied "over").

Minecraft samples font textures with nearest filtering, so HD glyphs keep their detail only where it is
at least two texels wide: at GUI scale 2 a 4x glyph is minified to every other texel. Strokes below that
are avoided on purpose.
"""
import math

import numpy as np
from PIL import Image


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.rgb = np.zeros((h, w, 3), dtype=np.float64)   # premultiplied
        self.a = np.zeros((h, w), dtype=np.float64)
        ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
        # texel centres
        self.x = xs + 0.5
        self.y = ys + 0.5

    def paint(self, coverage, colour, alpha=1.0):
        """Composite ``colour`` (rgb tuple or HxWx3 array, 0..255) with ``coverage * alpha`` over the canvas."""
        a = np.clip(coverage * alpha, 0.0, 1.0)
        c = np.asarray(colour, dtype=np.float64) / 255.0
        if c.ndim == 1:
            c = np.broadcast_to(c, (self.h, self.w, 3))
        self.rgb = c * a[..., None] + self.rgb * (1.0 - a[..., None])
        self.a = a + self.a * (1.0 - a)

    def erase(self, coverage):
        keep = 1.0 - np.clip(coverage, 0.0, 1.0)
        self.rgb *= keep[..., None]
        self.a *= keep

    def image(self):
        a = np.clip(self.a, 0.0, 1.0)
        safe = np.where(a > 1e-6, a, 1.0)
        rgb = np.clip(self.rgb / safe[..., None], 0.0, 1.0)
        out = np.dstack([rgb * 255.0, a * 255.0]).round().astype(np.uint8)
        return Image.fromarray(out, 'RGBA')


# ----------------------------------------------------------------------------- coverage from distance

def fill(sd, soft=1.0):
    """Inside (sd < 0) -> 1, with a ``soft`` texel ramp across the edge."""
    return np.clip(0.5 - sd / soft, 0.0, 1.0)


def band(sd, half, soft=1.0):
    """Stroke of half-width ``half`` along the zero line of ``sd``."""
    return fill(np.abs(sd) - half, soft)


# ----------------------------------------------------------------------------- distance fields

def sd_circle(cv, cx, cy, r):
    return np.hypot(cv.x - cx, cv.y - cy) - r


def sd_box(cv, x0, y0, x1, y1, r=0.0):
    """Rounded box from (x0, y0) to (x1, y1) with corner radius ``r``."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx_, hy_ = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    qx = np.abs(cv.x - cx) - hx_
    qy = np.abs(cv.y - cy) - hy_
    out = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    return out + np.minimum(np.maximum(qx, qy), 0) - r


def sd_segment(cv, ax, ay, bx, by):
    px, py = cv.x - ax, cv.y - ay
    ex, ey = bx - ax, by - ay
    t = np.clip((px * ex + py * ey) / (ex * ex + ey * ey), 0.0, 1.0)
    return np.hypot(px - ex * t, py - ey * t)


def sd_polygon(cv, pts):
    """Exact signed distance to a simple polygon (negative inside)."""
    n = len(pts)
    d = np.full(cv.x.shape, np.inf)
    sign = np.ones(cv.x.shape)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[i - 1]
        d = np.minimum(d, sd_segment(cv, ax, ay, bx, by))
        c1 = cv.y >= ay
        c2 = cv.y < by
        c3 = (bx - ax) * (cv.y - ay) > (by - ay) * (cv.x - ax)
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        sign = np.where(flip, -sign, sign)
    return d * sign


def angle(cv, cx, cy):
    """Screen angle from +x, clockwise (y grows downward), 0..2pi."""
    return np.mod(np.arctan2(cv.y - cy, cv.x - cx), 2 * math.pi)


# ----------------------------------------------------------------------------- colour fields

def lerp(a, b, t):
    a = np.asarray(a, dtype=np.float64)
    b = np.asarray(b, dtype=np.float64)
    t = np.asarray(t, dtype=np.float64)
    if t.ndim:
        t = t[..., None]
    return a + (b - a) * t


def linear(cv, x0, y0, x1, y1):
    """0 at (x0, y0) .. 1 at (x1, y1), clamped."""
    ex, ey = x1 - x0, y1 - y0
    return np.clip(((cv.x - x0) * ex + (cv.y - y0) * ey) / (ex * ex + ey * ey), 0.0, 1.0)


def noise(cv, seed, scale=1.0):
    """Deterministic per-texel value noise in -1..1 (cheap grain for metal and stone)."""
    rng = np.random.default_rng(seed)
    base = rng.uniform(-1.0, 1.0, size=(cv.h // max(1, int(scale)) + 2, cv.w // max(1, int(scale)) + 2))
    img = Image.fromarray(((base + 1) * 127.5).astype(np.uint8), 'L').resize((cv.w, cv.h), Image.BILINEAR)
    return np.asarray(img, dtype=np.float64) / 127.5 - 1.0


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
