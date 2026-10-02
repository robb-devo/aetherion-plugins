"""Post-processing: fence / wall / pane connection states, floating plant cleanup, light sanity."""
from __future__ import annotations

import re

import numpy as np

from world import World, X0, Y0, Z0, NX, NY, NZ

DIRS = (("north", 0, -1), ("south", 0, 1), ("east", 1, 0), ("west", -1, 0))

NONFULL = ("air", "water", "lava", "_slab", "_stairs", "_fence", "_wall", "pane", "iron_bars", "_door", "_trapdoor",
           "lantern", "chain", "_sign", "torch", "leaves", "short_grass", "tall_grass", "fern", "flower", "poppy",
           "dandelion", "tulip", "orchid", "allium", "bluet", "daisy", "cornflower", "lily", "rose_bush", "peony",
           "lilac", "sunflower", "bush", "mushroom[", "brown_mushroom", "red_mushroom", "sapling", "rail", "carpet",
           "pressure_plate", "button", "ladder", "vine", "roots", "dripstone[", "pointed_dripstone", "snow[", "bell",
           "campfire", "lectern", "anvil", "grindstone", "cauldron", "composter", "hopper", "chest", "pot",
           "candle", "skull", "head", "scaffolding", "lightning_rod", "end_rod", "petals", "farmland", "dirt_path",
           "wheat", "carrots", "potatoes", "beetroots", "cluster", "amethyst_bud", "glass_pane", "bars", "cactus",
           "sweet_berry", "dead_bush", "hanging_roots", "cobweb", "fence_gate", "stonecutter", "enchanting",
           "brewing", "bed", "banner", "frame", "flower_pot", "potted", "sea_pickle", "lily_pad", "kelp", "seagrass")


def parse(state):
    m = re.match(r"([^\[]+)(?:\[(.*)\])?$", state)
    base = m.group(1)
    props = {}
    if m.group(2):
        for kv in m.group(2).split(","):
            k, v = kv.split("=")
            props[k] = v
    return base, props


def compose(base, props):
    if not props:
        return base
    return base + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def connect(W: World):
    pal = W.pal
    kind = np.zeros(len(pal), dtype=np.int8)  # 1 fence 2 wall 3 pane/bars
    full = np.zeros(len(pal), dtype=bool)
    gate = np.zeros(len(pal), dtype=bool)
    for i, s in enumerate(pal):
        b = s.split("[")[0]
        if b.endswith("_fence"):
            kind[i] = 1
        elif b.endswith("_wall") and "sign" not in b and "banner" not in b:
            kind[i] = 2
        elif b.endswith("glass_pane") or b == "minecraft:iron_bars":
            kind[i] = 3
        if b.endswith("_fence_gate"):
            gate[i] = True
        full[i] = not any(t in s for t in NONFULL) and s != "minecraft:air"
    A = W.a
    A0 = A.copy()
    K = kind[A0]
    pts = np.argwhere(K > 0)
    cache = {}
    n = 0
    for ix, iy, iz in pts:
        cur = int(A0[ix, iy, iz])
        k = kind[cur]
        base, props = parse(pal[cur])
        conns = {}
        for name, dx, dz in DIRS:
            jx, jz = ix + dx, iz + dz
            if not (0 <= jx < NX and 0 <= jz < NZ):
                conns[name] = False
                continue
            nb = int(A0[jx, iy, jz])
            nk = kind[nb]
            if k == 1:
                c = nk == 1 or gate[nb] or full[nb]
            elif k == 2:
                c = nk in (2, 3) or gate[nb] or full[nb]
            else:
                c = nk in (2, 3) or full[nb]
            conns[name] = bool(c)
        if k == 2:
            above = int(A0[ix, iy + 1, iz]) if iy + 1 < NY else 0
            tall = full[above] or kind[above] == 2
            for name in ("north", "south", "east", "west"):
                props[name] = ("tall" if tall else "low") if conns[name] else "none"
            straight = (conns["north"] and conns["south"] and not conns["east"] and not conns["west"]) or \
                       (conns["east"] and conns["west"] and not conns["north"] and not conns["south"])
            props["up"] = "false" if (straight and above == 0) else "true"
        else:
            for name in ("north", "south", "east", "west"):
                props[name] = "true" if conns[name] else "false"
        ns = compose(base, props)
        key = ns
        if key not in cache:
            cache[key] = W.bid(ns)
        A[ix, iy, iz] = cache[key]
        n += 1
    return n


def cleanup(W: World):
    """Remove plants that float (no valid ground) and snow layers on non-solid."""
    pal = W.pal
    plant = np.zeros(len(pal), dtype=bool)
    ground = np.zeros(len(pal), dtype=bool)
    for i, s in enumerate(pal):
        b = s.split("[")[0]
        if any(t in b for t in ("short_grass", "tall_grass", "fern", "poppy", "dandelion", "tulip", "orchid", "allium",
                                "bluet", "daisy", "cornflower", "lily_of", "rose_bush", "peony", "lilac", "petals",
                                "brown_mushroom", "red_mushroom", "dead_bush", "sweet_berry")) and "block" not in b:
            if "half=upper" not in s:
                plant[i] = True
        if any(t in b for t in ("grass_block", "dirt", "podzol", "moss_block", "coarse_dirt", "rooted_dirt",
                                "mud", "farmland", "sand", "gravel")):
            ground[i] = True
    A = W.a
    P = plant[A]
    pts = np.argwhere(P)
    removed = 0
    for ix, iy, iz in pts:
        below = int(A[ix, iy - 1, iz]) if iy > 0 else 0
        if not ground[below]:
            A[ix, iy, iz] = 0
            s = pal[int(A[ix, iy + 1, iz])] if iy + 1 < NY else ""
            if "half=upper" in s:
                A[ix, iy + 1, iz] = 0
            removed += 1
    # orphan upper halves
    for i, s in enumerate(pal):
        if "half=upper" in s and "door" not in s:
            for ix, iy, iz in np.argwhere(A == i):
                lo = pal[int(A[ix, iy - 1, iz])]
                if "half=lower" not in lo:
                    A[ix, iy, iz] = 0
                    removed += 1
    return removed


def run(W: World):
    r = cleanup(W)
    n = connect(W)
    print(f"  post: connections {n}, removed floating plants {r}")
