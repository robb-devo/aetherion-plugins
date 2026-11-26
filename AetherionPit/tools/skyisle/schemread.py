"""Read a Sponge schematic (.schem v2/v3, gzip NBT) into (array[x,y,z] of palette ids, palette list, block entities)."""
import gzip
import struct

import numpy as np


def _read(buf, pos, t):
    if t == 1:
        return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if t == 2:
        return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if t == 3:
        return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if t == 4:
        return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if t == 5:
        return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if t == 6:
        return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if t == 7:
        n = struct.unpack_from(">i", buf, pos)[0]
        pos += 4
        return bytes(buf[pos:pos + n]), pos + n
    if t == 8:
        n = struct.unpack_from(">H", buf, pos)[0]
        pos += 2
        return buf[pos:pos + n].decode("utf-8", "replace"), pos + n
    if t == 9:
        et, n = struct.unpack_from(">bi", buf, pos)
        pos += 5
        out = []
        for _ in range(n):
            v, pos = _read(buf, pos, et)
            out.append(v)
        return out, pos
    if t == 10:
        d = {}
        while True:
            tt = buf[pos]
            pos += 1
            if tt == 0:
                return d, pos
            n = struct.unpack_from(">H", buf, pos)[0]
            pos += 2
            k = buf[pos:pos + n].decode("utf-8", "replace")
            pos += n
            d[k], pos = _read(buf, pos, tt)
    if t == 11:
        n = struct.unpack_from(">i", buf, pos)[0]
        pos += 4
        return list(struct.unpack_from(">%di" % n, buf, pos)), pos + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", buf, pos)[0]
        pos += 4
        return list(struct.unpack_from(">%dq" % n, buf, pos)), pos + 8 * n
    raise ValueError("tag %d" % t)


def read_nbt(path):
    buf = gzip.open(path).read()
    t = buf[0]
    n = struct.unpack_from(">H", buf, 1)[0]
    name = buf[3:3 + n].decode()
    v, _ = _read(buf, 3 + n, t)
    return name, v


def varints(data):
    out, val, shift = [], 0, 0
    for b in data:
        val |= (b & 0x7F) << shift
        if b & 0x80:
            shift += 7
        else:
            out.append(val)
            val, shift = 0, 0
    return out


def load_schem(path):
    name, root = read_nbt(path)
    if "Schematic" in root and isinstance(root["Schematic"], dict):
        root = root["Schematic"]
    ver = root.get("Version")
    W, H, L = root["Width"] & 0xFFFF, root["Height"] & 0xFFFF, root["Length"] & 0xFFFF
    if ver == 3:
        blocks = root["Blocks"]
        pal, data, bes = blocks["Palette"], blocks["Data"], blocks.get("BlockEntities", [])
    elif "Palette" in root:
        pal, data, bes = root["Palette"], root["BlockData"], root.get("BlockEntities", root.get("TileEntities", []))
    else:
        raise ValueError("not a Sponge schematic (keys: %s)" % list(root)[:12])
    inv = [None] * (max(pal.values()) + 1)
    for s, i in pal.items():
        inv[i] = s
    ids = varints(data)
    arr = np.array(ids, dtype=np.int32).reshape(H, L, W)  # index = x + z*W + y*W*L
    arr = np.transpose(arr, (2, 0, 1))  # -> x, y, z
    return arr, inv, bes, root
