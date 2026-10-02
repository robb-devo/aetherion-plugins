from pathlib import Path
import struct
import zlib
import gzip
from collections import Counter

region_dir = Path(__file__).resolve().parent / "bloodstone_world" / "Bloodstone Dungeon World" / "region"
names = Counter()
chunk_scores = []


def load_chunk(data: bytes, i: int):
    offset_sectors = int.from_bytes(data[i * 4 : i * 4 + 3], "big")
    sector_count = data[i * 4 + 3]
    if offset_sectors == 0 or sector_count == 0:
        return None
    offset = offset_sectors * 4096
    if offset + 5 > len(data):
        return None
    length = int.from_bytes(data[offset : offset + 4], "big")
    if length <= 1 or offset + 4 + length > len(data):
        return None
    comp = data[offset + 4]
    payload = data[offset + 5 : offset + 4 + length]
    if comp == 2:
        return zlib.decompress(payload)
    if comp == 1:
        return gzip.decompress(payload)
    if comp == 3:
        return payload
    return None


for fpath in sorted(region_dir.glob("r.*.*.mca")):
    data = fpath.read_bytes()
    parts = fpath.stem.split(".")
    rx, rz = int(parts[1]), int(parts[2])
    for i in range(1024):
        try:
            payload = load_chunk(data, i)
        except Exception:
            continue
        if not payload:
            continue
        lx, lz = i % 32, i // 32
        cx, cz = rx * 32 + lx, rz * 32 + lz
        found = []
        pos = 0
        while True:
            j = payload.find(b"minecraft:", pos)
            if j < 0:
                break
            end = j
            while end < len(payload) and 32 <= payload[end] < 127:
                end += 1
            s = payload[j:end].decode("ascii", "ignore")
            if 11 < len(s) < 48:
                names[s] += 1
                found.append(s)
            pos = j + 10
        # Heightmap WORLD_SURFACE search: name then TAG_Long_Array
        surface_y = None
        needle = b"WORLD_SURFACE"
        k = payload.find(needle)
        if k >= 0:
            # after name, expect 0c (TAG_Long_Array) already before name in named tag...
            # Named tag: type, name_len, name, payload
            # Find preceding type byte for this name
            name_len = len(needle)
            # look back for type 12 (0x0C) long array
            # pattern: 0C 00 0D WORLD_SURFACE then int length then longs
            start = k - 3
            if start >= 0 and payload[start] == 0x0C:
                arr_len = int.from_bytes(payload[k + name_len : k + name_len + 4], "big")
                if 0 < arr_len <= 256:
                    # sample center index 136? 16x16 = 256, index 8+8*16=136
                    longs = []
                    p = k + name_len + 4
                    for _ in range(min(arr_len, 37)):
                        longs.append(int.from_bytes(payload[p : p + 8], "big", signed=True))
                        p += 8
                    # 1.16+ heightmaps pack 9-bit values in longs depending on world height
                    # For rough estimate take bits - modern is often 9 bits for -64..320
                    # Simpler: use max of decoded center cells if we can
                    surface_y = "hm_present"
        score = len(found)
        if score:
            chunk_scores.append((score, cx * 16 + 8, cz * 16 + 8, sorted(set(found))[:15], surface_y, cx, cz))

print("top names:")
for n, c in names.most_common(50):
    print(f"{c:6} {n}")
print("\nrichest chunks (block name hits):")
for row in sorted(chunk_scores, reverse=True)[:25]:
    print(row)
print("chunks with names", len(chunk_scores))
