"""The places of Morningbell. prepare(t) shapes the ground under them (before it is painted), build(w, t)
raises them, after_flora(w, t, forest) adds what needs to know where the trees ended up.

Every building follows a real type (see the concept): the belfry is a Nordic free-standing timber bell tower,
the charcoal camp a Kohlenmeiler with its Koete, the quarry has a treadwheel crane, the smithy is a hammer forge.
"""
import math

import numpy as np

import layout as L
import site_bell
import site_camp
import site_falls
import site_quarry
import site_forge
import site_village
import site_yard
import site_harbour
import site_flat
import site_light
import site_farm


def clear_disc(t, cx, cz, r):
    """No trees or scatter inside this circle."""
    zz, xx = np.ogrid[L.Z0:L.Z0 + L.SZ, L.X0:L.X0 + L.SX]
    t.keep_clear |= (xx - cx) ** 2 + (zz - cz) ** 2 <= r * r


def clear_box(t, x1, z1, x2, z2, margin=1):
    t.keep_clear[max(z1 - margin - L.Z0, 0):z2 + margin + 1 - L.Z0, max(x1 - margin - L.X0, 0):x2 + margin + 1 - L.X0] = True


def level_box(t, x1, z1, x2, z2, y, blend=3):
    """Flat ground at height y under a footprint, eased into its surroundings."""
    H = t.H
    for z in range(z1 - blend, z2 + blend + 1):
        for x in range(x1 - blend, x2 + blend + 1):
            if not t.inb(x, z):
                continue
            zi, xi = z - L.Z0, x - L.X0
            if not t.land[zi, xi] or t.stream[zi, xi]:
                continue
            dx = max(x1 - x, 0, x - x2)
            dz = max(z1 - z, 0, z - z2)
            d = max(dx, dz)
            if d == 0:
                H[zi, xi] = y
            else:
                k = d / float(blend + 1)
                H[zi, xi] = int(round(y * (1 - k) + int(H[zi, xi]) * k))


SITES = [site_bell, site_camp, site_falls, site_quarry, site_forge, site_village, site_yard, site_harbour, site_flat, site_light,
         site_farm]


def prepare(t):
    for s in SITES:
        s.prepare(t)


def build(w, t):
    for s in SITES:
        s.build(w, t)


def after_flora(w, t, forest):
    for s in SITES:
        if hasattr(s, "after_flora"):
            s.after_flora(w, t, forest)
