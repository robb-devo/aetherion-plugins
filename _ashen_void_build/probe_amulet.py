from amulet import load_level
from pathlib import Path

p = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_src\Bloodstone Dungeon World")
level = load_level(str(p))
print("platform", level.level_wrapper.platform, level.level_wrapper.version)
dims = list(level.dimensions)
print("dims", dims)
dim = dims[0]
coords = list(level.all_chunk_coords(dim))
print("chunks", len(coords), coords[:8])
cx, cz = coords[0]
chunk = level.get_chunk(cx, cz, dim)
print("chunk", type(chunk))
print([x for x in dir(chunk) if "block" in x.lower()][:30])
b = level.get_block(0, 64, 0, dim)
print("block", b, getattr(b, "namespaced_name", None), getattr(b, "base_name", None), getattr(b, "namespace", None))
# bounds
print("bounds", level.bounds(dim) if hasattr(level, "bounds") else "n/a")
level.close()
