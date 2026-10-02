from amulet import load_level
from collections import Counter
from pathlib import Path

p = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\ashen_void")
level = load_level(str(p))
dim = "minecraft:overworld"
counts = Counter()
# Sample around known Hollow Sun arena (0, 63, 115)
for x in range(-40, 40):
    for z in range(70, 160):
        for y in range(40, 90):
            try:
                b = level.get_block(x, y, z, dim)
            except Exception:
                continue
            counts[f"{b.namespace}:{b.base_name}"] += 1
print("top blocks near arena:")
for name, n in counts.most_common(40):
    print(f"  {n:6d}  {name}")
level.close()
