"""Under and around Ankerhold: the two remaining mooring chains, the Keel balcony (secret), rescue footprint."""
from __future__ import annotations

import math

import numpy as np

import structures as S
from common import gy, npc, YAW
from d_border import link_chain
from world import World, stairs, slab, log, fence, lantern, chain, X0, Z0, Y0, NX, NZ


def mount_plate(W, x, y, z, r=4.5):
    for dx in range(-5, 6):
        for dz in range(-5, 6):
            d = math.hypot(dx, dz)
            if d <= r:
                W.set(x + dx, y, z + dz, "polished_blackstone_bricks" if d > 1.5 else "chiseled_polished_blackstone")
                if d > r - 1:
                    W.set(x + dx, y - 1, z + dz, "polished_blackstone_bricks")
    for dy in range(1, 3):
        W.set(x, y - dy, z, "polished_blackstone")


def hanging_chain(W, T, x, z, depth, label):
    ix, iz = x - X0, z - Z0
    b = int(T.bot[ix, iz])
    # find actual lowest solid under the column
    y = b
    while y > Y0 and W.get(x, y - 1, z) != 0:
        y -= 1
    mount_plate(W, x, y - 1, z)
    link_chain(W, [(x, y - 3, z), (x + 0.6, y - 3 - depth * 0.5, z + 0.4), (x + 1.2, y - 3 - depth, z + 0.8)],
               link_len=7, link_w=4.2)
    W.anchor("landmark." + label, x + 0.5, y - 3, z + 0.5, note="Mooring chain hanging from the underside")
    return y


def keel(W, T, rng):
    """Stair from the south quay down through the rock to a balcony under the lip."""
    ex, ez = 126, 33
    y0 = gy(T, ex, ez)
    # entrance hatch hut
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for yy in range(y0 + 1, y0 + 4):
            W.set(ex + dx, yy, ez + dz, log("spruce_log"))
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            W.set(ex + dx, y0 + 4, ez + dz, slab("spruce", "bottom"))
    W.set(ex, y0 + 3, ez, lantern(True))
    # descend east
    x, y = ex, y0
    W.set(x, y, ez, 0)
    steps = 0
    while y > 50:
        x += 1
        y -= 1
        for dz in (-1, 0, 1):
            W.set(x, y, ez + dz, stairs("stone_brick", "west"))
            for k in range(1, 4):
                W.set(x, y + k, ez + dz, 0)
            W.set(x, y - 1, ez + dz, "stone_bricks")
        if steps % 4 == 0:
            W.set(x, y + 3, ez - 1, lantern(True))
        steps += 1
    # corridor east until we break out of the rock
    while True:
        x += 1
        solid_above = any(W.get(x, yy, ez) != 0 for yy in range(y + 1, y + 8))
        for dz in (-1, 0, 1):
            W.set(x, y, ez + dz, "stone_bricks")
            for k in range(1, 4):
                W.set(x, y + k, ez + dz, 0)
        if x > 175:
            break
        if not solid_above and W.get(x + 1, y + 1, ez) == 0 and W.get(x + 2, y + 1, ez) == 0:
            break
    # balcony
    bx = x
    for dx in range(0, 5):
        for dz in range(-3, 4):
            W.set(bx + dx, y, ez + dz, "spruce_planks")
            if dx == 4 or abs(dz) == 3:
                W.set(bx + dx, y + 1, ez + dz, fence("spruce"))
        W.set(bx + dx, y - 1, ez, log("spruce_log", "x"))
    for k in range(1, 6):
        W.set(bx + 4 - k, y - 1 - k, ez - 3, log("spruce_log", "x"))
        W.set(bx + 4 - k, y - 1 - k, ez + 3, log("spruce_log", "x"))
    W.set(bx + 1, y + 1, ez - 2, lantern(False))
    S.bench(W, bx + 1, y, ez + 2, "north", 2)
    W.anchor("vista.the_keel", bx + 2.5, y + 1, ez + 0.5,
             note="The Keel — balcony under the harbour lip; the Harbour Chain hangs beside it (secret vista)")
    W.anchor("secret.keel_hatch", ex + 0.5, y0 + 1, ez + 0.5, note="Keel hatch on the south quay")
    return bx, y, ez


def build(W: World, T, rng):
    bx, by, bz = keel(W, T, rng)
    # Harbour chain a few blocks south-east of the Keel balcony (on solid underside)
    best = None
    for x in range(bx - 14, bx + 2):
        for z in range(bz + 6, bz + 20):
            ix, iz = x - X0, z - Z0
            if 0 <= ix < NX and 0 <= iz < NZ and T.island[ix, iz] and T.edge[ix, iz] >= 5:
                d = abs(x - bx) + abs(z - bz - 10)
                if best is None or d < best[0]:
                    best = (d, x, z)
    if best:
        hanging_chain(W, T, best[1], best[2], 44, "harbour_chain")
    # Capital keel chain (deepest point under the capital)
    hanging_chain(W, T, -6, 6, 26, "capital_chain")
    # rescue footprint + world spawn
    W.anchor("rescue.footprint", 0, -60, 0, min=[-196, -60, -196], max=[196, 200, 196],
             note="Edge rescue box (Origin rescue.floor-y stays -60; footprint shrinks to the new hub)")


def plan(P, T):
    pass
