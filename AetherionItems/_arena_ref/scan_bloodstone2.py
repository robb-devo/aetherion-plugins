from pathlib import Path
import struct
import zlib
import gzip
from collections import Counter

region_dir = Path(__file__).resolve().parent / "bloodstone_world" / "Bloodstone Dungeon World" / "region"
names = Counter()
chunk_meta = []

for fpath in sorted(region_dir.glob("r.*.*.mca")):
    data = fpath.read_bytes()
    for i in range(1024):
        loc = int.from_bytes(data[i * 4 : i * 4 + 3], "big")
        if loc == 0:
            continue
        offset = (loc >> 8) * 4096
        length = int.from_bytes(data[offset : offset + 4], "big")
        if length <= 1 or offset + 4 + length > len(data):
            continue
        comp = data[offset + 4]
        payload = data[offset + 5 : offset + 4 + length]
        try:
            if comp == 2:
                payload = zlib.decompress(payload)
            elif comp == 1:
                payload = gzip.decompress(payload)
            else:
                continue
        except Exception:
            continue
        # extract minecraft: ids
        pos = 0
        found = []
        while True:
            j = payload.find(b"minecraft:", pos)
            if j < 0:
                break
            end = j
            while end < len(payload) and 32 <= payload[end] < 127:
                end += 1
            s = payload[j:end].decode("ascii", "ignore")
            if len(s) < 40:
                names[s] += 1
                found.append(s)
            pos = j + 10
        lx = i % 32
        lz = i // 32
        # region coords from filename
        parts = fpath.stem.split(".")
        rx, rz = int(parts[1]), int(parts[2])
        cx, cz = rx * 32 + lx, rz * 32 + lz
        if found:
            chunk_meta.append((len(found), cx * 16 + 8, cz * 16 + 8, sorted(set(found))[:12]))

print("top block name counts:")
for n, c in names.most_common(40):
    print(c, n)
print("\nrichest chunks:")
for row in sorted(chunk_meta, reverse=True)[:20]:
    print(row)
