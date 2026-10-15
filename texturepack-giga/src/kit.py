"""Diagonal-weapon kit in uv space.

u = x + y (across the weapon), v = x - y (along it, growing toward the top-right tip).
uv space is the screen turned 45 degrees and scaled by sqrt(2): circles stay circles, a
hammer head is a plain uv box. Only pixels with u and v of equal parity exist.
The default axis u = 31 runs from (2, 29) up to (29, 2).
"""
import math

AXIS = 31


def at(v, axis=AXIS):
    """Screen point (float) on the axis at along-coordinate v."""
    return (axis + v) / 2.0, (axis - v) / 2.0


def uv_set(ic, u, v, key, adj=0):
    if (u + v) % 2 == 0:
        ic.set((u + v) // 2, (u - v) // 2, key, adj)


def uv_bump(ic, u, v, d):
    if (u + v) % 2 == 0:
        ic.bump((u + v) // 2, (u - v) // 2, d)


def uv_get(ic, u, v):
    if (u + v) % 2:
        return None
    return ic.get((u + v) // 2, (u - v) // 2)


def uv_fill(ic, key, pred, adj=0, only=None):
    """Fill every pixel whose (u, v) satisfies pred. `only`: restrict to pixels holding that key."""
    for y in range(ic.h):
        for x in range(ic.w):
            if only is not None and ic.key[y][x] != only:
                continue
            if pred(x + y, x - y):
                ic.set(x, y, key, adj if only is None else ic.adj[y][x] + adj)


def uv_shade(ic, key, fn):
    """Add fn(u, v) to the shade of every pixel of `key`."""
    for y in range(ic.h):
        for x in range(ic.w):
            if ic.key[y][x] == key:
                ic.adj[y][x] += fn(x + y, x - y)


def bands(ic, key, table, axis=AXIS):
    """Diagonal colour bands along the weapon: table[u - axis] = shade offset."""
    uv_shade(ic, key, lambda u, v: table.get(u - axis, 0))


def blade(ic, key, v0, vtip, wl, wr, k=2.0, axis=AXIS, adj=0):
    """Straight blade: u in [axis-wl, axis+wr], v from v0 to a tip losing k per u-line off axis."""
    uv_fill(ic, key, lambda u, v: axis - wl <= u <= axis + wr and v0 <= v <= vtip - k * abs(u - axis), adj)


def shaped_blade(ic, key, v0, v1, half, axis=AXIS, adj=0):
    """Blade with a width profile: half(v) -> (left, right) half-widths in u (floats)."""
    def pred(u, v):
        if not (v0 <= v <= v1):
            return False
        l, r = half(v)
        return axis - l <= u <= axis + r
    uv_fill(ic, key, pred, adj)


def haft(ic, key, v0, v1, w=1, axis=AXIS, adj=0):
    """Shaft along the axis, u in [axis-w, axis+w]."""
    uv_fill(ic, key, lambda u, v: axis - w <= u <= axis + w and v0 <= v <= v1, adj)


def box(ic, key, u0, u1, v0, v1, adj=0, chamfer=0):
    """uv box (a 45-degree rotated rectangle on screen); chamfer clips its corners."""
    def pred(u, v):
        if not (u0 <= u <= u1 and v0 <= v <= v1):
            return False
        if chamfer:
            du = min(u - u0, u1 - u)
            dv = min(v - v0, v1 - v)
            return du + dv >= chamfer
        return True
    uv_fill(ic, key, pred, adj)


def disc_uv(ic, key, cu, cv, r, adj=0):
    uv_fill(ic, key, lambda u, v: (u - cu) ** 2 + (v - cv) ** 2 <= r * r, adj)


def ring_uv(ic, key, cu, cv, r0, r1, adj=0, arc=None):
    """Annulus r0 < d <= r1; arc=(deg0, deg1) keeps part of it (0 deg = +u, 90 = +v)."""
    def pred(u, v):
        d = math.hypot(u - cu, v - cv)
        if not (r0 < d <= r1):
            return False
        if arc:
            a = math.degrees(math.atan2(v - cv, u - cu)) % 360
            a0, a1 = arc
            return (a0 <= a <= a1) if a0 <= a1 else (a >= a0 or a <= a1)
        return True
    uv_fill(ic, key, pred, adj)


def wrap(ic, key, period=4, dark=(0, 1), d=-1):
    """Grip wrap: darken every few steps along the weapon."""
    uv_shade(ic, key, lambda u, v: d if (v % period) in dark else 0)


def pommel(ic, key, v, r=1.9, axis=AXIS):
    x, y = at(v, axis)
    ic.disc(x, y, r, key)


def gem_plus(ic, key, x, y):
    for p in ((x, y - 1), (x - 1, y), (x, y), (x + 1, y), (x, y + 1)):
        ic.set(*p, key)


def gem_diamond(ic, key, x, y, r=2):
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if abs(dx) + abs(dy) <= r:
                ic.set(x + dx, y + dy, key)
