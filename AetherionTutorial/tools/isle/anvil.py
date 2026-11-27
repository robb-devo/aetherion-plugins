"""Anvil region writer for Minecraft 1.21.1 (DataVersion 3955). Writes full chunks without light data
(isLightOn = 0, so the server lights them on load) and without heightmaps (primed on load)."""
import os
import struct
import zlib

import numpy as np

import nbt

DATA_VERSION = 3955
MIN_SECTION = -4
MAX_SECTION = 19


def pack_indices(idx, bits):
    """idx: 1-D array of palette indices. Entries never span two longs (1.16+ layout)."""
    per = 64 // bits
    n = idx.size
    longs = (n + per - 1) // per
    padded = np.zeros(longs * per, dtype=np.uint64)
    padded[:n] = idx
    padded = padded.reshape(longs, per)
    shifts = (np.arange(per, dtype=np.uint64) * np.uint64(bits))
    packed = np.bitwise_or.reduce(padded << shifts, axis=1)
    return packed.view(np.int64)


def section_tag(y, states, palette_strings, biome_ids, biome_names):
    """states: (16,16,16) array [y][z][x] of global palette ids, or None for an all-air section.
    biome_ids: (4,4,4) array [y][z][x] of ids into biome_names, or an int for a uniform section."""
    if states is None:
        block_states = nbt.Compound({"palette": nbt.List(10, [nbt.state_tag("minecraft:air")])})
    else:
        flat = states.reshape(-1)
        uniq, inverse = np.unique(flat, return_inverse=True)
        pal = nbt.List(10, [nbt.state_tag(palette_strings[int(u)]) for u in uniq])
        d = {"palette": pal}
        if uniq.size > 1:
            bits = max(4, int(uniq.size - 1).bit_length())
            d["data"] = nbt.LongArray(pack_indices(inverse.astype(np.uint64), bits))
        block_states = nbt.Compound(d)
    if isinstance(biome_ids, (int, np.integer)):
        biomes = nbt.Compound({"palette": nbt.List(8, [nbt.String(biome_names[int(biome_ids)])])})
    else:
        flat = biome_ids.reshape(-1)
        uniq, inverse = np.unique(flat, return_inverse=True)
        d = {"palette": nbt.List(8, [nbt.String(biome_names[int(u)]) for u in uniq])}
        if uniq.size > 1:
            bits = max(1, int(uniq.size - 1).bit_length())
            d["data"] = nbt.LongArray(pack_indices(inverse.astype(np.uint64), bits))
        biomes = nbt.Compound(d)
    return nbt.Compound({"Y": nbt.Byte(y), "block_states": block_states, "biomes": biomes})


def chunk_bytes(cx, cz, sections, block_entities):
    root = nbt.Compound({
        "DataVersion": nbt.Int(DATA_VERSION),
        "xPos": nbt.Int(cx),
        "yPos": nbt.Int(MIN_SECTION),
        "zPos": nbt.Int(cz),
        "Status": nbt.String("minecraft:full"),
        "LastUpdate": nbt.Long(0),
        "InhabitedTime": nbt.Long(0),
        "isLightOn": nbt.Byte(0),
        "sections": nbt.List(10, sections),
        "block_entities": nbt.List(10, block_entities),
        "block_ticks": nbt.List(10, []),
        "fluid_ticks": nbt.List(10, []),
        "Heightmaps": nbt.Compound({}),
        "structures": nbt.Compound({"References": nbt.Compound({}), "starts": nbt.Compound({})}),
    })
    return nbt.dumps(root)


class RegionWriter:
    """Collects chunks per region and writes r.X.Z.mca files."""

    def __init__(self, folder):
        self.folder = folder
        self.regions = {}

    def put(self, cx, cz, data):
        self.regions.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = zlib.compress(data, 6)

    def flush(self):
        os.makedirs(self.folder, exist_ok=True)
        for (rx, rz), chunks in self.regions.items():
            locations = bytearray(4096)
            timestamps = bytearray(4096)
            body = bytearray()
            sector = 2
            for (lx, lz), comp in sorted(chunks.items(), key=lambda kv: (kv[0][1], kv[0][0])):
                payload = struct.pack(">ib", len(comp) + 1, 2) + comp
                count = (len(payload) + 4095) // 4096
                if count > 255:
                    raise ValueError("chunk %d,%d too large" % (lx, lz))
                payload += b"\x00" * (count * 4096 - len(payload))
                i = 4 * (lx + lz * 32)
                locations[i:i + 3] = struct.pack(">i", sector)[1:]
                locations[i + 3] = count
                body += payload
                sector += count
            with open(os.path.join(self.folder, "r.%d.%d.mca" % (rx, rz)), "wb") as f:
                f.write(locations)
                f.write(timestamps)
                f.write(body)
        self.regions.clear()
