from pathlib import Path
from amulet import load_level

schem = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\BloodstoneDungeon.schem")
print("trying schem", schem)
try:
    level = load_level(str(schem))
    print("loaded schem", level.level_wrapper.platform, level.level_wrapper.version)
    print("dims", list(level.dimensions))
    level.close()
except Exception as e:
    print("schem fail", type(e), e)

# try creating minimal level.dat for region world
world = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_src\Bloodstone Dungeon World")
print("regions", list((world/"region").glob("*.mca")))

# inspect one region with anvil
import anvil
region = anvil.Region.from_file(str(world/"region"/"r.0.0.mca"))
# find a populated chunk
found=False
for x in range(32):
    for z in range(32):
        try:
            chunk = region.get_chunk(x, z)
        except Exception:
            continue
        print("chunk", x, z, "version?", getattr(chunk, 'version', None))
        # sample blocks
        try:
            b = chunk.get_block(0, 64, 0)
            print(" block", b)
        except Exception as e:
            print(" get_block err", e)
        found=True
        break
    if found:
        break
if not found:
    print("no chunks in r.0.0")
