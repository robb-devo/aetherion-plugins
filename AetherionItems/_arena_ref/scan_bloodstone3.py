from pathlib import Path
import struct
import zlib
import gzip

region_dir = Path(__file__).resolve().parent / "bloodstone_world" / "Bloodstone Dungeon World" / "region"
print("files", list(region_dir.glob("*")))
for fpath in sorted(region_dir.glob("r.*.*.mca")):
    data = fpath.read_bytes()
    print(fpath.name, "size", len(data))
    nonempty = 0
    for i in range(1024):
        loc = int.from_bytes(data[i * 4 : i * 4 + 3], "big")
        if loc == 0:
            continue
        nonempty += 1
        if nonempty > 3:
            continue
        offset = (loc >> 8) * 4096
        length = int.from_bytes(data[offset : offset + 4], "big")
        comp = data[offset + 4]
        payload = data[offset + 5 : offset + 4 + length]
        print("  chunk", i, "len", length, "comp", comp, "off", offset)
        try:
            if comp == 2:
                raw = zlib.decompress(payload)
            elif comp == 1:
                raw = gzip.decompress(payload)
            elif comp == 3:
                raw = payload  # uncompressed
            else:
                print("   unknown comp")
                continue
            print("   decompressed", len(raw), "head", raw[:32].hex(), "ascii", raw[:80])
            # tag type first byte should be 10 (compound) for root
        except Exception as e:
            print("   fail", e)
    print("  nonempty entries", nonempty)
