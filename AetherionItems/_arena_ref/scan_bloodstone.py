from pathlib import Path
import struct
import zlib
import gzip

region_dir = Path(__file__).resolve().parent / "bloodstone_world" / "Bloodstone Dungeon World" / "region"

hits = []
for cx in range(-16, 16):
    for cz in range(-16, 16):
        rx, rz = cx >> 5, cz >> 5
        fpath = region_dir / f"r.{rx}.{rz}.mca"
        if not fpath.exists():
            continue
        data = fpath.read_bytes()
        lx, lz = cx & 31, cz & 31
        idx = 4 * (lx + lz * 32)
        loc = int.from_bytes(data[idx : idx + 3], "big")
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

        keys = [
            b"minecraft:polished_blackstone",
            b"minecraft:blackstone",
            b"minecraft:nether_bricks",
            b"minecraft:basalt",
            b"minecraft:magma_block",
            b"minecraft:deepslate",
            b"minecraft:crimson",
            b"minecraft:red_nether",
            b"minecraft:chiseled",
            b"minecraft:gilded_blackstone",
        ]
        score = sum(payload.count(k) for k in keys)
        if score < 3:
            continue
        ys = []
        needle = b"\x01\x00\x01Y"
        i = 0
        while True:
            j = payload.find(needle, i)
            if j < 0:
                break
            ys.append(struct.unpack("b", payload[j + 4 : j + 5])[0])
            i = j + 5
        hits.append((score, cx * 16 + 8, cz * 16 + 8, sorted(set(ys)), cx, cz))

hits.sort(reverse=True)
print("top chunks:")
for h in hits[:30]:
    print(h)
print("total", len(hits))
