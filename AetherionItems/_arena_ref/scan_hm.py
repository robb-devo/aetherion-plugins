from pathlib import Path
import zlib
import gzip
import struct

region_dir = Path(__file__).resolve().parent / "bloodstone_world" / "Bloodstone Dungeon World" / "region"


def load_chunk(cx, cz):
    rx, rz = cx >> 5, cz >> 5
    fpath = region_dir / f"r.{rx}.{rz}.mca"
    data = fpath.read_bytes()
    lx, lz = cx & 31, cz & 31
    i = lx + lz * 32
    offset_sectors = int.from_bytes(data[i * 4 : i * 4 + 3], "big")
    sector_count = data[i * 4 + 3]
    if offset_sectors == 0:
        return None
    offset = offset_sectors * 4096
    length = int.from_bytes(data[offset : offset + 4], "big")
    comp = data[offset + 4]
    payload = data[offset + 5 : offset + 4 + length]
    if comp == 2:
        return zlib.decompress(payload)
    if comp == 1:
        return gzip.decompress(payload)
    return payload


def decode_heightmap(payload: bytes, name=b"WORLD_SURFACE"):
    # TAG_Long_Array named: 0x0C + nameLen(u16) + name + length(i32) + longs
    needle = bytes([0x0C, 0x00, len(name)]) + name
    k = payload.find(needle)
    if k < 0:
        return None
    p = k + len(needle)
    n = int.from_bytes(payload[p : p + 4], "big")
    p += 4
    longs = [int.from_bytes(payload[p + i * 8 : p + (i + 1) * 8], "big") for i in range(n)]
    # bits per entry = ceil(log2(worldHeight+1)); for -64..320 height=384 -> 9 bits
    bits = 9
    mask = (1 << bits) - 1
    vals = []
    bit = 0
    for _ in range(256):
        # read bits from longs stream (Minecraft packs from LSB of each long)
        idx = bit // 64
        off = bit % 64
        if idx >= len(longs):
            break
        v = (longs[idx] >> off) & mask
        if off + bits > 64 and idx + 1 < len(longs):
            # values do NOT span longs in modern MC - each long holds floor(64/bits) entries
            pass
        vals.append(v)
        # actually in 1.16+ heightmaps: entries do not cross long boundaries
        vals.pop()
        break
    # proper decode: per-long packing
    per_long = 64 // bits
    vals = []
    for lg in longs:
        for i in range(per_long):
            if len(vals) >= 256:
                break
            vals.append((lg >> (i * bits)) & mask)
        if len(vals) >= 256:
            break
    # convert to absolute Y: value is height above min_y? For WORLD_SURFACE it's block Y+1 in some versions
    # In 1.18+: heightmap stores Y - min_y + 1? Actually stores absolute block Y for surface in older;
    # 1.18 WORLD_SURFACE: the value is the Y coordinate of the highest non-air + 1, offset by min_y
    # Spec: "The height is the lowest Y that is open to the sky" stored as (y - minY)
    # For overworld minY=-64, so abs_y = val + minY ... or val + minY - 1 for standing?
    return vals


for cx, cz in [(0, 0), (0, 1), (0, 2), (1, 1), (-1, 1), (2, 2), (-3, 2)]:
    payload = load_chunk(cx, cz)
    if not payload:
        print(cx, cz, "missing")
        continue
    vals = decode_heightmap(payload)
    if not vals:
        # try MOTION_BLOCKING
        vals = decode_heightmap(payload, b"MOTION_BLOCKING")
    if not vals:
        print(cx, cz, "no hm", "has_surface", b"WORLD_SURFACE" in payload)
        continue
    # center of chunk local 8,8 -> index 8+8*16=136
    center = vals[136]
    # also sample a few
    samples = [vals[i + j * 16] for i in (0, 8, 15) for j in (0, 8, 15)]
    min_y = -64
    abs_center = center + min_y  # if stored as offset from minY
    abs_center2 = center - 1  # if absolute+1 old style
    print(
        f"chunk {cx},{cz} center_raw={center} as_offsetY={abs_center} as_absMinus1={center-1} samples_raw={samples[:5]} min={min(vals)} max={max(vals)}"
    )
