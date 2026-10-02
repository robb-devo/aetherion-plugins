"""Emulates the Java placement check: Storage Hut / Quarry Housing on each starter's marked pad (all 4 rotations),
plus spawn safety. Mirrors StateRotator.rotateXZ and StructureService.validate."""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from aeprops.core import bname
from aeg.starters import STARTERS
from aeg.structures import STRUCTURES

REPLACEABLE = {"short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "snow", "moss_carpet", "vine",
               "glow_lichen", "sweet_berry_bush", "brown_mushroom", "red_mushroom", "hanging_roots", "nether_sprouts",
               "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
               "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "oak_sapling", "birch_sapling",
               "spruce_sapling"}
NONSOLID = {"water", "lava"} | REPLACEABLE

def rot(x, z, k):
    for _ in range(k % 4):
        x, z = -z, x
    return x, z

def build(name):
    fn = STARTERS.get(name) or STRUCTURES.get(name)
    b = fn(); b.resolve(); return b

def check(island, struct, cx, cz):
    out = []
    for k in range(4):
        blocked = []; floor = solid = 0
        for (lx, ly, lz), st in struct.blocks.items():
            rx, rz = rot(lx, lz, k)
            wx, wz = cx + rx, cz + rz
            here = island.blocks.get((wx, ly, wz))
            if ly == 0:
                floor += 1
                if here and bname(here).split(":")[1] not in NONSOLID:
                    solid += 1
                continue
            if here is not None and bname(here).split(":")[1] not in REPLACEABLE:
                blocked.append(((wx, ly, wz), bname(here)))
        ok = not blocked and solid >= floor * 0.6
        out.append((k, ok, len(blocked), blocked[:2], f"{solid}/{floor}"))
    return out

pads = {"starter_grove": {"st_storage_hut": (7, 4)},
        "starter_quarry": {"st_storage_hut": (5, 7), "st_quarry_housing": (7, -1)},
        "starter_tide": {"st_storage_hut": (7, -7)}}
spawns = {"starter_grove": (0, 12), "starter_quarry": (0, 12), "starter_tide": (0, -12), "starter_guild": (0, 19)}
bad = 0
for name, want in pads.items():
    isl = build(name)
    for sname, (cx, cz) in want.items():
        st = build(sname)
        for k, ok, nb, sample, fl in check(isl, st, cx, cz):
            print(f"{name:15s} {sname:18s} rot{k}: {'OK ' if ok else 'BAD'} blocked={nb} floor={fl} {sample}")
            bad += not ok
for name, (sx, sz) in spawns.items():
    isl = build(name)
    ground = isl.blocks.get((sx, 0, sz)); feet = isl.blocks.get((sx, 1, sz)); head = isl.blocks.get((sx, 2, sz))
    ok = ground is not None and bname(ground).split(":")[1] not in NONSOLID and \
        (feet is None or bname(feet).split(":")[1] in REPLACEABLE) and (head is None or bname(head).split(":")[1] in REPLACEABLE)
    print(f"spawn {name:15s} ground={ground and bname(ground)} feet={feet and bname(feet)} -> {'OK' if ok else 'BAD'}")
    bad += not ok
# guild project sites vs final shapes: only other-structure overlap is checked in Java, report blocks for info
print("problems:", bad)
