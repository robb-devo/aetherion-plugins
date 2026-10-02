"""Trees, undergrowth, flowers and boulders — after all structures are placed."""
from __future__ import annotations

import math
import random

import numpy as np
from scipy import ndimage

import structures as S
from terrain import (D_CAP, D_HARB, D_WHIS, D_RIDGE, D_FIELD, D_MTN, D_BORD, D_COLO, D_LIGHT, D_DEBRIS, noise)
from world import World, X0, Z0, Y0, NX, NZ, double_plant

GROUNDS = ("minecraft:grass_block[snowy=false]", "minecraft:podzol[snowy=false]", "minecraft:podzol",
           "minecraft:moss_block", "minecraft:coarse_dirt")


def run(W: World, T, seed=21):
    rng = random.Random(seed)
    nrng = np.random.default_rng(seed)
    top = T.top
    occ = ndimage.binary_dilation(T.occ, iterations=1)
    grass_id = W.bid("minecraft:grass_block[snowy=false]")
    pod_id = W.bid("minecraft:podzol")
    moss_id = W.bid("minecraft:moss_block")
    coarse_id = W.bid("minecraft:coarse_dirt")
    A = W.a
    ok_ground = {grass_id, pod_id, moss_id, coarse_id}
    dense = noise(28, seed + 3, 2)  # clumping

    def ground_ok(x, z, clear=10):
        ix, iz = x - X0, z - Z0
        if not (2 <= ix < NX - 2 and 2 <= iz < NZ - 2):
            return None
        y = int(top[ix, iz])
        if y < -100:
            return None
        gid = int(A[ix, y - Y0, iz])
        if gid not in ok_ground:
            return None
        if A[ix - 1:ix + 2, y + 1 - Y0:y + 1 + clear - Y0, iz - 1:iz + 2].any():
            return None
        return y

    cells = np.argwhere(T.island_all & ~occ)
    nrng.shuffle(cells)
    placed_trees = np.zeros((NX, NZ), dtype=bool)
    nt = 0
    for ix, iz in cells:
        d = int(T.dmap[ix, iz])
        x, z = ix + X0, iz + Z0
        cl = (dense[ix, iz] + 1) / 2  # 0..1
        slope = T.slope[ix, iz]
        if slope > 1.6:
            continue
        if d == D_WHIS:
            p = 0.020 + 0.05 * cl
        elif d == D_MTN:
            y = top[ix, iz]
            if y > 124:
                continue
            p = (0.008 + 0.04 * cl) * (1.0 if y < 112 else 0.5)
        elif d == D_FIELD:
            p = 0.009 * (cl > 0.45) + 0.002
        elif d == D_HARB:
            p = 0.004 * cl
        elif d == D_RIDGE:
            p = 0.016 * cl + 0.002
        elif d == D_CAP:
            p = 0.010
        elif d in (D_COLO, D_DEBRIS, D_LIGHT):
            p = 0.01
        else:
            continue
        if rng.random() > p:
            continue
        if placed_trees[max(ix - 3, 0):ix + 4, max(iz - 3, 0):iz + 4].any():
            continue
        y = ground_ok(x, z)
        if y is None:
            continue
        if d == D_WHIS:
            r = rng.random()
            if r < 0.45:
                S.oak_tree(W, x, y, z, rng, h=rng.randint(5, 9), rad=rng.choice([2.4, 2.8, 3.2]))
            elif r < 0.8:
                S.birch_tree(W, x, y, z, rng)
            else:
                S.spruce_tree(W, x, y, z, rng)
        elif d == D_MTN:
            S.spruce_tree(W, x, y, z, rng, h=rng.randint(7, 15))
        elif d == D_CAP:
            if rng.random() < 0.5:
                S.cherry_tree(W, x, y, z, rng)
            else:
                S.oak_tree(W, x, y, z, rng, h=rng.randint(4, 6), rad=2.2)
        elif d == D_FIELD:
            S.oak_tree(W, x, y, z, rng, h=rng.randint(5, 7), rad=3.0)
        elif d == D_RIDGE:
            if rng.random() < 0.6:
                S.spruce_tree(W, x, y, z, rng, h=rng.randint(6, 10))
            else:
                S.oak_tree(W, x, y, z, rng)
        else:
            if rng.random() < 0.5:
                S.bush(W, x, y, z, rng, "azalea")
            else:
                S.oak_tree(W, x, y, z, rng, h=4, rad=2.0)
        placed_trees[ix, iz] = True
        nt += 1
    # ground cover
    flowers = ["poppy", "dandelion", "cornflower", "oxeye_daisy", "azure_bluet", "allium", "red_tulip", "white_tulip",
               "pink_tulip", "orange_tulip", "lily_of_the_valley"]
    tall = ["tall_grass", "large_fern", "lilac", "rose_bush", "peony"]
    nf = 0
    for ix, iz in cells:
        d = int(T.dmap[ix, iz])
        if d in (D_BORD,):
            continue
        x, z = ix + X0, iz + Z0
        y = int(top[ix, iz])
        if y < -100:
            continue
        gid = int(A[ix, y - Y0, iz])
        if gid not in (grass_id, pod_id, moss_id):
            continue
        if A[ix, y + 1 - Y0, iz] != 0:
            continue
        r = rng.random()
        cl = (dense[ix, iz] + 1) / 2
        if d == D_WHIS:
            if r < 0.30:
                W.set(x, y + 1, z, rng.choice(["minecraft:fern", "minecraft:short_grass", "minecraft:short_grass"]))
            elif r < 0.34 and A[ix, y + 2 - Y0, iz] == 0:
                lo, hi = double_plant(rng.choice(["large_fern", "tall_grass"]))
                W.set(x, y + 1, z, lo)
                W.set(x, y + 2, z, hi)
            elif r < 0.36:
                S.bush(W, x, y, z, rng, rng.choice(["azalea", "flowering_azalea"]))
            elif r < 0.37:
                W.set(x, y + 1, z, rng.choice(["minecraft:brown_mushroom", "minecraft:red_mushroom"]))
            elif r < 0.385:
                W.set(x, y + 1, z, "minecraft:lily_of_the_valley")
        elif d in (D_FIELD, D_HARB, D_RIDGE, D_COLO, D_LIGHT, D_DEBRIS, D_MTN):
            gp = 0.30 if d != D_MTN else 0.18
            if r < gp:
                W.set(x, y + 1, z, "minecraft:short_grass")
            elif r < gp + 0.05 * (0.4 + cl) and d != D_MTN:
                W.set(x, y + 1, z, "minecraft:" + flowers[int((dense[ix, iz] * 7 + 7) * 1.3 + rng.random() * 2) % len(flowers)])
                nf += 1
            elif r < gp + 0.08 and A[ix, y + 2 - Y0, iz] == 0:
                lo, hi = double_plant(rng.choice(tall[:2] if d == D_MTN else tall))
                W.set(x, y + 1, z, lo)
                W.set(x, y + 2, z, hi)
            elif r < gp + 0.084 and d == D_MTN:
                W.set(x, y + 1, z, "minecraft:sweet_berry_bush[age=3]")
        elif d == D_CAP:
            if T.plateau[ix, iz]:
                if r < 0.25:
                    W.set(x, y + 1, z, "minecraft:short_grass")
                elif r < 0.30:
                    W.set(x, y + 1, z, "minecraft:" + rng.choice(flowers))
    # boulders
    for ix, iz in cells[: len(cells) // 2]:
        if rng.random() > 0.0025:
            continue
        d = int(T.dmap[ix, iz])
        if d in (D_CAP,):
            continue
        x, z = ix + X0, iz + Z0
        y = ground_ok(x, z)
        if y is None:
            continue
        m = rng.choice(["mossy_cobblestone", "cobblestone", "andesite", "stone"])
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if rng.random() < 0.7:
                    W.set(x + dx, y + 1, z + dz, m if rng.random() < 0.7 else "mossy_cobblestone")
        if rng.random() < 0.5:
            W.set(x, y + 2, z, m)
    print("  vegetation: trees %d, flowers %d" % (nt, nf))
