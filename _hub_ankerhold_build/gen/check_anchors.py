"""Verify every standing anchor (spawns, NPC docks, vistas, landings) is standable: solid below, air at feet+head."""
import math
import numpy as np
from post import NONFULL

def standable(W, x, y, z):
    bx, by, bz = math.floor(x), math.floor(y), math.floor(z)
    below = W.getn(bx, by - 1, bz)
    feet = W.getn(bx, by, bz)
    head = W.getn(bx, by + 1, bz)
    passable = lambda s: s == "minecraft:air" or any(t in s for t in ("short_grass", "fern", "petals", "flower", "poppy",
                         "dandelion", "tulip", "daisy", "bluet", "allium", "cornflower", "lily_of", "snow[layers=1",
                         "rail", "pressure_plate", "carpet", "dead_bush", "mushroom[", "brown_mushroom", "red_mushroom"))
    bb = below.split("[")[0]
    solid_below = bb != "minecraft:air" and bb != "minecraft:water" and not any(t in bb for t in ("short_grass", "fern", "flower", "_fence", "lantern"))
    return solid_below and passable(feet) and passable(head), (below, feet, head)

KINDS = ("spawn.", "npc.", "vista.", "pad.", "waystone.", "updraft.", "glide.", "portal.dungeon", "gate.", "secret.")

PLANTS = ("short_grass", "fern", "petals", "poppy", "dandelion", "tulip", "daisy", "bluet", "allium", "cornflower",
          "lily_of", "lilac", "rose_bush", "peony", "tall_grass", "large_fern", "dead_bush", "mushroom", "sweet_berry")


def clear_docks(W):
    """Keep NPC docks and spawn points free of plants (feet/head + 1 ring)."""
    for k, v in W.anchors.items():
        if not k.startswith(("npc.", "spawn.")):
            continue
        bx, by, bz = math.floor(v["x"]), math.floor(v["y"]), math.floor(v["z"])
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (0, 1):
                    s = W.getn(bx + dx, by + dy, bz + dz)
                    if any(p in s for p in PLANTS) and "block" not in s:
                        W.set(bx + dx, by + dy, bz + dz, 0)


def normalise(W):
    """Docks and camps snap to block centres (x.5 / z.5)."""
    for k, v in W.anchors.items():
        if k.startswith(("npc.", "spawn.", "world.")):
            v["x"] = math.floor(v["x"]) + 0.5
            v["z"] = math.floor(v["z"]) + 0.5


def run(W):
    normalise(W)
    clear_docks(W)
    bad = []
    n = 0
    for k, v in W.anchors.items():
        if not k.startswith(KINDS) or k.startswith("landmark.harbour_chain") or k.startswith("landmark.capital_chain"):
            continue
        if k.startswith("pad.") and "land" not in k:
            continue  # pad centres sit on slime; checked separately
        if k.startswith("portal.farm_isle") or k == "landmark.fallen_anchor" or k.startswith("landmark.colosseum_bridge"):
            pass
        n += 1
        ok, info = standable(W, v["x"], v["y"], v["z"])
        if not ok:
            # try to auto-fix by searching y +-3
            fixed = None
            for dy in (1, -1, 2, -2, 3, -3):
                if standable(W, v["x"], v["y"] + dy, v["z"])[0]:
                    fixed = dy
                    break
            bad.append((k, (v["x"], v["y"], v["z"]), info, fixed))
            if fixed is not None:
                v["y"] = v["y"] + fixed
    return n, bad

if __name__ == "__main__":
    from build import run as build
    W, T, P = build()
    n, bad = run(W)
    print("checked", n, "bad", len(bad))
    for b in bad:
        print("  ", b)
