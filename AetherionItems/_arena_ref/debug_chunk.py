from pathlib import Path
import zlib
import scan_floor as sf

payload = sf.load_chunk(2, 2)
print("payload", len(payload) if payload else None)
root = sf.root_compound(payload)
print("root keys", root.keys())
for k,v in root.items():
    print(k, v[0] if isinstance(v, tuple) else type(v))
secs = root.get("sections")
print("sections type", secs[0] if secs else None, "n", len(secs[2]) if secs else None)
if secs:
    for sec in secs[2][:3]:
        print(" sec keys", sec.keys())
        print("  Y", sec.get("Y"))
        bs = sec.get("block_states")
        if bs:
            print("  bs keys", bs[1].keys())
            pal = bs[1].get("palette")
            if pal:
                print("  palette n", len(pal[2]), "first", sf.palette_block_name(pal[2][0]) if pal[2] else None)
# try block at many Y
for y in range(0, 100, 5):
    b = sf.block_at(40, y, 40)
    if b and b != "minecraft:air":
        print("hit", y, b)
