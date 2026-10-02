"""On-foot reachability (2.5D BFS): step up <=1 (jump), drop <=3. Verifies the Harbour Hour loop and every dock."""
import json, math, pickle
from collections import deque
import numpy as np
from world import X0, Y0, Z0, NX, NY, NZ

PASS_HINT = ("short_grass", "fern", "petals", "poppy", "dandelion", "tulip", "daisy", "bluet", "allium", "cornflower",
             "lily_of", "lilac", "rose_bush", "peony", "tall_grass", "dead_bush", "mushroom", "snow[layers=1", "rail",
             "pressure_plate", "carpet", "wheat", "carrots", "potatoes", "beetroots", "sweet_berry", "hanging_roots",
             "sugar_cane", "lily_pad", "light[")


def masks(pal):
    passable = np.zeros(len(pal), bool)
    floor = np.zeros(len(pal), bool)
    for i, s in enumerate(pal):
        b = s.split("[")[0]
        if s == "minecraft:air" or any(h in s for h in PASS_HINT) and "block" not in b:
            passable[i] = True
            continue
        if b in ("minecraft:water", "minecraft:lava"):
            continue
        if any(t in b for t in ("_fence", "_wall", "pane", "iron_bars", "chain", "lantern", "campfire", "pointed_dripstone",
                                "ladder", "_sign", "bell", "lightning_rod", "scaffolding", "magma")):
            continue
        floor[i] = True
    return passable, floor


def run(A, pal, anchors, start="spawn.harbour"):
    passable, floor = masks(pal)
    P = passable[A]
    F = floor[A]
    S = np.zeros_like(P)
    S[:, 1:-1, :] = F[:, :-2, :] & P[:, 1:-1, :] & P[:, 2:, :]
    sx = anchors[start]
    s = (math.floor(sx["x"]) - X0, math.floor(sx["y"]) - Y0, math.floor(sx["z"]) - Z0)
    assert S[s], ("start not standable", s)
    seen = np.zeros_like(S)
    seen[s] = True
    q = deque([s])
    while q:
        x, y, z = q.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < NX and 0 <= nz < NZ):
                continue
            for dy in (0, 1, -1, -2, -3):
                ny = y + dy
                if not (1 <= ny < NY - 1):
                    continue
                if S[nx, ny, nz] and not seen[nx, ny, nz]:
                    if dy == 1 and not P[x, y + 2, z]:
                        continue  # no headroom to jump
                    if dy < 0 and not all(P[nx, y + k, nz] for k in (0, 1)):
                        continue
                    seen[nx, ny, nz] = True
                    q.append((nx, ny, nz))
                    break
    res = {}
    for k, v in anchors.items():
        if not k.startswith(("npc.", "spawn.", "vista.", "waystone.", "updraft.skyreach.floor", "glide.", "pad.",
                             "portal.dungeon_hub", "secret.")):
            continue
        if k.startswith("pad.") and "land" not in k and not k.endswith(("origin_to_mining", "origin_to_forage")):
            continue
        p = (math.floor(v["x"]) - X0, math.floor(v["y"]) - Y0, math.floor(v["z"]) - Z0)
        ok = False
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (0, 1, -1):
                    q2 = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if 0 <= q2[0] < NX and 0 <= q2[1] < NY and 0 <= q2[2] < NZ and seen[q2]:
                        ok = True
        res[k] = ok
    return res, int(seen.sum())


if __name__ == "__main__":
    A = np.load("out/world.npy")
    pal = json.load(open("out/palette.json"))
    anchors = pickle.load(open("out/meta.pkl", "rb"))["anchors"]
    res, n = run(A, pal, anchors)
    print("reachable standable cells:", n)
    bad = [k for k, ok in res.items() if not ok]
    print("checked", len(res), "unreachable:", bad)
