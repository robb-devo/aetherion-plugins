import gzip
from pathlib import Path
from nbtlib import File, Compound, Int, Short, Byte, String, Long, List, ByteArray, IntArray

schem_path = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\BloodstoneDungeon.schem")
raw = gzip.decompress(schem_path.read_bytes())
# nbtlib load from bytes
from io import BytesIO
nbt = File.parse(BytesIO(raw))
root = nbt
print("root keys", list(root.keys())[:30] if hasattr(root, 'keys') else type(root))
# sometimes wrapped
if '' in root:
    root = root['']
print("keys", list(root.keys()))
for k in list(root.keys())[:20]:
    v = root[k]
    print(k, type(v).__name__, (len(v) if hasattr(v,'__len__') and not isinstance(v,(str,bytes)) else v) if k not in ('BlockData','Palette','Blocks','Data') else f'len={len(v)}')
