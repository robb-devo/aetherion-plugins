"""Find solid floor Y at a few XZ points by decoding 1.18+ chunk sections (palette + longs)."""
from pathlib import Path
import zlib
import gzip
import struct

region_dir = Path(__file__).resolve().parent / "bloodstone_world" / "Bloodstone Dungeon World" / "region"

AIRISH = {
    "minecraft:air",
    "minecraft:cave_air",
    "minecraft:void_air",
    "minecraft:lava",
    "minecraft:water",
    "minecraft:light",
}


def load_chunk(cx, cz):
    rx, rz = cx >> 5, cz >> 5
    fpath = region_dir / f"r.{rx}.{rz}.mca"
    data = fpath.read_bytes()
    lx, lz = cx & 31, cz & 31
    i = lx + lz * 32
    offset_sectors = int.from_bytes(data[i * 4 : i * 4 + 3], "big")
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


def read_string(buf, p):
    ln = int.from_bytes(buf[p : p + 2], "big")
    p += 2
    s = buf[p : p + ln].decode("utf-8", "ignore")
    return s, p + ln


def skip_tag(buf, p, tag_type):
    if tag_type == 0:
        return p
    if tag_type == 1:
        return p + 1
    if tag_type == 2:
        return p + 2
    if tag_type == 3:
        return p + 4
    if tag_type == 4:
        return p + 8
    if tag_type == 5:
        return p + 4
    if tag_type == 6:
        return p + 8
    if tag_type == 7:  # byte array
        n = int.from_bytes(buf[p : p + 4], "big", signed=True)
        return p + 4 + n
    if tag_type == 8:
        _, p = read_string(buf, p)
        return p
    if tag_type == 9:  # list
        itype = buf[p]
        n = int.from_bytes(buf[p + 1 : p + 5], "big", signed=True)
        p += 5
        for _ in range(max(n, 0)):
            p = skip_tag(buf, p, itype)
        return p
    if tag_type == 10:  # compound
        while True:
            t = buf[p]
            p += 1
            if t == 0:
                return p
            _, p = read_string(buf, p)
            p = skip_tag(buf, p, t)
    if tag_type == 11:  # int array
        n = int.from_bytes(buf[p : p + 4], "big", signed=True)
        return p + 4 + 4 * n
    if tag_type == 12:  # long array
        n = int.from_bytes(buf[p : p + 4], "big", signed=True)
        return p + 4 + 8 * n
    raise ValueError(f"bad tag {tag_type} at {p}")


def parse_compound(buf, p):
    out = {}
    while True:
        t = buf[p]
        p += 1
        if t == 0:
            return out, p
        name, p = read_string(buf, p)
        if t == 1:
            out[name] = ("byte", buf[p])
            p += 1
        elif t == 2:
            out[name] = ("short", int.from_bytes(buf[p : p + 2], "big", signed=True))
            p += 2
        elif t == 3:
            out[name] = ("int", int.from_bytes(buf[p : p + 4], "big", signed=True))
            p += 4
        elif t == 4:
            out[name] = ("long", int.from_bytes(buf[p : p + 8], "big", signed=True))
            p += 8
        elif t == 8:
            s, p = read_string(buf, p)
            out[name] = ("string", s)
        elif t == 9:
            itype = buf[p]
            n = int.from_bytes(buf[p + 1 : p + 5], "big", signed=True)
            p += 5
            items = []
            for _ in range(max(n, 0)):
                if itype == 10:
                    c, p = parse_compound(buf, p)
                    items.append(c)
                else:
                    # store raw skip for non-compound lists we care about less
                    start = p
                    p = skip_tag(buf, p, itype)
                    items.append(("raw", itype, start))
            out[name] = ("list", itype, items)
        elif t == 10:
            c, p = parse_compound(buf, p)
            out[name] = ("compound", c)
        elif t == 12:
            n = int.from_bytes(buf[p : p + 4], "big", signed=True)
            p += 4
            longs = [int.from_bytes(buf[p + i * 8 : p + (i + 1) * 8], "big", signed=True) for i in range(n)]
            p += 8 * n
            out[name] = ("long_array", longs)
        else:
            p = skip_tag(buf, p, t)
            out[name] = ("skipped", t)


def root_compound(payload: bytes):
    # root is unnamed compound: type 10, name empty, then compound
    assert payload[0] == 10
    p = 1
    name, p = read_string(payload, p)
    data, p = parse_compound(payload, p)
    return data


def palette_block_name(entry: dict) -> str:
    n = entry.get("Name")
    if n and n[0] == "string":
        return n[1]
    return "minecraft:air"


def section_get(section: dict, lx, ly, lz):
    bs = section.get("block_states")
    if not bs or bs[0] != "compound":
        # empty section?
        return "minecraft:air"
    bsc = bs[1]
    pal = bsc.get("palette")
    if not pal or pal[0] != "list":
        return "minecraft:air"
    palette = [palette_block_name(e) for e in pal[2]]
    if len(palette) == 1:
        return palette[0]
    data = bsc.get("data")
    if not data or data[0] != "long_array":
        return palette[0]
    longs = data[1]
    bits = max(4, (len(palette) - 1).bit_length())
    index = ly * 256 + lz * 16 + lx
    # values packed without crossing long boundaries
    per = 64 // bits
    li = index // per
    lo = (index % per) * bits
    if li >= len(longs):
        return "minecraft:air"
    v = (longs[li] >> lo) & ((1 << bits) - 1)
    if v >= len(palette):
        return "minecraft:air"
    return palette[v]


def block_at(x, y, z):
    cx, cz = x >> 4, z >> 4
    payload = load_chunk(cx, cz)
    if not payload:
        return None
    root = root_compound(payload)
    sections = root.get("sections")
    if not sections or sections[0] != "list":
        return None
    sy = y >> 4
    lx, ly, lz = x & 15, y & 15, z & 15
    for sec in sections[2]:
        ytag = sec.get("Y")
        if not ytag:
            continue
        if ytag[1] != sy and (ytag[1] if isinstance(ytag[1], int) else -999) != sy:
            # byte may be unsigned in parse - we stored as byte 0-255
            sec_y = ytag[1] if ytag[0] != "byte" else struct.unpack("b", bytes([ytag[1]]))[0]
        else:
            sec_y = ytag[1]
        if ytag[0] == "byte":
            sec_y = struct.unpack("b", bytes([ytag[1] & 0xFF]))[0]
        if sec_y != sy:
            continue
        return section_get(sec, lx, ly, lz)
    return "minecraft:air"


def find_floor(x, z, y_from=120, y_to=0):
    for y in range(y_from, y_to, -1):
        b = block_at(x, y, z)
        above = block_at(x, y + 1, z)
        above2 = block_at(x, y + 2, z)
        if b is None:
            return None
        if b not in AIRISH and above in AIRISH and above2 in AIRISH:
            return y + 1, b
    return None


points = [
    (0, 0),
    (16, 0),
    (24, 0),
    (32, 0),
    (0, 16),
    (0, 24),
    (0, 32),
    (20, 20),
    (-20, 20),
    (8, 8),
    (40, 40),
    (0, 40),
    (40, 0),
]
for x, z in points:
    try:
        hit = find_floor(x, z)
        print(f"{x},{z} -> {hit}")
    except Exception as e:
        print(f"{x},{z} ERR {e}")
