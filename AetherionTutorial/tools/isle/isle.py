"""Builds Morningbell.

  python isle.py <out dir> [--no-region] [--stage N]

Writes <out>/dump (raw array for previews), <out>/region (Anvil files) and <out>/isle.json (points for the plugin).
Stages (for quick previews): 1 ground, 2 + ways, 3 + sites, 4 + plants.
"""
import json
import os
import sys
import time

import layout as L
import terrain
import paths
from world import World


def build(stage=99):
    w = World(L.X0, L.Z0, L.SX, L.SZ, L.Y0, L.SY)
    clock = time.time()
    t = terrain.shape()
    if stage >= 2:
        paths.carve(t)
    if stage >= 3:
        import sites
        sites.prepare(t)
    raised = terrain.seal_water(t)
    terrain.paint(w, t)
    terrain.dress_stream(w, t)
    if stage >= 2:
        paths.pave(w, t)
    print("ground %.1fs (%d bank columns raised, %d sills, %d films)" % (time.time() - clock, raised, len(t.sills),
                                                                         len(t.films)))
    forest = None
    if stage >= 3:
        clock = time.time()
        sites.build(w, t)
        n = len(paths.lamps(w, t))
        print("sites %.1fs, %d lanterns along the route" % (time.time() - clock, n))
    if stage >= 4:
        import flora
        clock = time.time()
        forest = flora.build(w, t)
        if stage >= 3:
            sites.after_flora(w, t, forest)
        cut = flora.trim_canopy_off_buildings(w, forest)
        print("plants %.1fs (%d trees, %d leaves trimmed off buildings)" % (time.time() - clock, len(forest.trees), cut))
    clock = time.time()
    w.finalize()
    import water
    bad = water.leaks(w)
    print("finalize %.1fs, water that could start to run: %d" % (time.time() - clock, len(bad)))
    for item in bad[:12]:
        print("   ", item)
    return w, t


def datapack(folder):
    """The data pack that defines the island's biome. It belongs into <world>/datapacks/ before the first start."""
    ns, name = L.BIOME.split(":")
    target = os.path.join(folder, "data", ns, "worldgen", "biome")
    os.makedirs(target, exist_ok=True)
    with open(os.path.join(folder, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 48, "description": "Morningbell: the tutorial isle"}}, f, indent=1)
    biome = {
        "has_precipitation": False, "temperature": 0.7, "downfall": 0.4,
        "effects": dict(L.BIOME_LOOK, mood_sound={"sound": "minecraft:ambient.cave", "tick_delay": 6000,
                                                  "block_search_extent": 8, "offset": 2.0}),
        "spawners": {}, "spawn_costs": {}, "carvers": {}, "features": [],
    }
    with open(os.path.join(target, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(biome, f, indent=1)


if __name__ == "__main__":
    out = sys.argv[1]
    stage = 99
    if "--stage" in sys.argv:
        stage = int(sys.argv[sys.argv.index("--stage") + 1])
    w, t = build(stage)
    w.dump(os.path.join(out, "dump"))
    with open(os.path.join(out, "isle.json"), "w", encoding="utf-8") as f:
        json.dump({"marks": w.marks, "sea": L.SEA}, f, indent=1)
    if "--no-region" not in sys.argv:
        clock = time.time()
        w.save(os.path.join(out, "region"), ocean_biome=L.BIOME)
        datapack(os.path.join(out, "datapacks", "morningbell"))
        print("region %.1fs" % (time.time() - clock))
