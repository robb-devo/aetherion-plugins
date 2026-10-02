from amulet import load_level
from collections import Counter
from pathlib import Path

p = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_live\bloodstone")
level = load_level(str(p))
dim = "minecraft:overworld"
samples = {}
for x in range(-20, 20):
    for z in range(90, 140):
        for y in range(50, 80):
            try:
                b = level.get_block(x, y, z, dim)
            except Exception:
                continue
            name = f"{b.namespace}:{b.base_name}"
            if name in samples:
                continue
            if b.base_name in ("concrete", "concrete_powder", "wool", "carpet", "stained_glass_pane",
                               "stained_terracotta", "lava", "netherrack", "magma_block", "wall",
                               "slab", "stairs", "wood", "planks", "coral_block", "nether_wart_block"):
                samples[name] = dict(b.properties) if b.properties else {}
print("samples:")
for k, v in sorted(samples.items()):
    print(k, v)
level.close()
