"""Sponge Schematic v3 writer (WorldEdit 7.3 / FAWE), anchors.yml writer."""
from __future__ import annotations

import gzip
import json
import time

import nbtlib
import numpy as np
from nbtlib import Compound, Int, Short, Long, String, IntArray, ByteArray, List, Byte

from world import World, X0, Y0, Z0, NX, NY, NZ

DATA_VERSION = 3955  # 1.21.1

BE_IDS = {
    "chest": "minecraft:chest", "trapped_chest": "minecraft:trapped_chest", "barrel": "minecraft:barrel",
    "furnace": "minecraft:furnace", "blast_furnace": "minecraft:blast_furnace", "smoker": "minecraft:smoker",
    "campfire": "minecraft:campfire", "soul_campfire": "minecraft:campfire", "bell": "minecraft:bell",
    "lectern": "minecraft:lectern", "chiseled_bookshelf": "minecraft:chiseled_bookshelf",
    "skeleton_skull": "minecraft:skull", "skeleton_wall_skull": "minecraft:skull", "decorated_pot": "minecraft:decorated_pot",
}


def be_id(state: str):
    b = state.split("[")[0].replace("minecraft:", "")
    if b in BE_IDS:
        return BE_IDS[b]
    if b.endswith("_hanging_sign") or b.endswith("_wall_hanging_sign"):
        return "minecraft:hanging_sign"
    if b.endswith("_sign"):
        return "minecraft:sign"
    return None


def sign_data(lines):
    def msg(t):
        return String(json.dumps({"text": t}) if t else '""')
    empty = Compound({"messages": List[String]([String('""')] * 4), "color": String("black"),
                      "has_glowing_text": Byte(0)})
    front = Compound({"messages": List[String]([msg(t) for t in lines[:4]]), "color": String("black"),
                      "has_glowing_text": Byte(0)})
    return {"front_text": front, "back_text": empty, "is_waxed": Byte(1)}


def varint_bytes(flat: np.ndarray) -> np.ndarray:
    v = flat.astype(np.int64)
    assert v.max() < (1 << 21)
    n1 = v < 0x80
    n2 = (v >= 0x80) & (v < 0x4000)
    n3 = v >= 0x4000
    lens = np.where(n1, 1, np.where(n2, 2, 3))
    out = np.zeros(int(lens.sum()), dtype=np.uint8)
    pos = np.concatenate([[0], np.cumsum(lens)[:-1]])
    out[pos[n1]] = v[n1]
    out[pos[n2]] = (v[n2] & 0x7F) | 0x80
    out[pos[n2] + 1] = v[n2] >> 7
    out[pos[n3]] = (v[n3] & 0x7F) | 0x80
    out[pos[n3] + 1] = ((v[n3] >> 7) & 0x7F) | 0x80
    out[pos[n3] + 2] = v[n3] >> 14
    return out


def write_schem(W: World, path, origin=(0, 63, 0), name="ankerhold_hub"):
    A = W.a
    # crop to used bounds
    used = np.argwhere(A.any(axis=(1, 2)))
    xs = np.nonzero(A.any(axis=(1, 2)))[0]
    ys = np.nonzero(A.any(axis=(0, 2)))[0]
    zs = np.nonzero(A.any(axis=(0, 1)))[0]
    x0, x1 = xs.min(), xs.max()
    y0, y1 = ys.min(), ys.max()
    z0, z1 = zs.min(), zs.max()
    sub = A[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
    Wd, Ht, Ln = sub.shape[0], sub.shape[1], sub.shape[2]
    # palette reorder by frequency (common blocks -> 1-byte varints)
    counts = np.bincount(sub.ravel(), minlength=len(W.pal))
    order = np.argsort(-counts)
    order = [i for i in order if counts[i] > 0]
    remap = np.zeros(len(W.pal), dtype=np.int64)
    pal = {}
    for new, old in enumerate(order):
        remap[old] = new
        pal[W.pal[old]] = Int(new)
    flat = remap[np.transpose(sub, (1, 2, 0)).ravel()]  # y, z, x  -> index = x + z*W + y*W*L
    data = varint_bytes(flat)
    # block entities
    bes = []
    be_pal = [i for i, s in enumerate(W.pal) if be_id(s) and counts[i] > 0]
    for i in be_pal:
        bid = be_id(W.pal[i])
        for (ix, iy, iz) in np.argwhere(sub == i):
            rel = [int(ix), int(iy), int(iz)]
            data_c = {"id": String(bid), "x": Int(rel[0]), "y": Int(rel[1]), "z": Int(rel[2])}
            wx, wy, wz = int(ix + x0 + X0), int(iy + y0 + Y0), int(iz + z0 + Z0)
            if bid in ("minecraft:sign", "minecraft:hanging_sign"):
                data_c.update(sign_data(W.signs.get((wx, wy, wz), ["", "", "", ""])))
            if bid in ("minecraft:barrel", "minecraft:chest"):
                data_c["Items"] = List[Compound]([])
            bes.append(Compound({"Id": String(bid), "Pos": IntArray(rel), "Data": Compound(data_c)}))
    wmin = (int(x0 + X0), int(y0 + Y0), int(z0 + Z0))
    offset = [wmin[0] - origin[0], wmin[1] - origin[1], wmin[2] - origin[2]]
    schem = Compound({
        "Version": Int(3),
        "DataVersion": Int(DATA_VERSION),
        "Metadata": Compound({
            "Date": Long(int(time.time() * 1000)),
            "Name": String(name),
            "Author": String("Aetherion hub (Claude)"),
            "WorldEdit": Compound({"Version": String("7.3.9"), "EditingPlatform": String("enginehub:bukkit"),
                                   "Origin": IntArray(list(origin))}),
        }),
        "Width": Short(Wd), "Height": Short(Ht), "Length": Short(Ln),
        "Offset": IntArray(offset),
        "Blocks": Compound({"Palette": Compound(pal), "Data": ByteArray(data.view(np.int8)),
                            "BlockEntities": List[Compound](bes)}),
    })
    f = nbtlib.File({"Schematic": schem}, gzipped=True)
    f.save(path)
    info = dict(width=int(Wd), height=int(Ht), length=int(Ln), min=wmin,
                max=(int(x1 + X0), int(y1 + Y0), int(z1 + Z0)), palette=len(pal), block_entities=len(bes),
                solid=int((sub != 0).sum()), offset=offset, origin=list(origin))
    return info


def read_back(path):
    f = nbtlib.load(path)
    s = f["Schematic"]
    Wd, Ht, Ln = int(s["Width"]), int(s["Height"]), int(s["Length"])
    pal = {int(v): k for k, v in s["Blocks"]["Palette"].items()}
    raw = np.array(s["Blocks"]["Data"], dtype=np.int8).view(np.uint8)
    # decode varints
    vals = []
    i = 0
    out = np.zeros(Wd * Ht * Ln, dtype=np.int64)
    # vectorised decode
    cont = (raw & 0x80) != 0
    starts = np.concatenate([[0], np.nonzero(~cont)[0][:-1] + 1])
    ends = np.nonzero(~cont)[0]
    lens = ends - starts + 1
    v = (raw[starts] & 0x7F).astype(np.int64)
    m2 = lens >= 2
    v[m2] |= (raw[starts[m2] + 1] & 0x7F).astype(np.int64) << 7
    m3 = lens >= 3
    v[m3] |= (raw[starts[m3] + 2] & 0x7F).astype(np.int64) << 14
    assert len(v) == Wd * Ht * Ln, (len(v), Wd * Ht * Ln)
    arr = v.reshape(Ht, Ln, Wd)
    return dict(W=Wd, H=Ht, L=Ln, palette=pal, arr=arr, offset=list(s["Offset"]),
                origin=list(s["Metadata"]["WorldEdit"]["Origin"]), be=len(s["Blocks"]["BlockEntities"]),
                version=int(s["Version"]), dv=int(s["DataVersion"]))


def write_anchors(W: World, path, info, extra=None):
    import yaml  # noqa
    data = dict(meta=dict(schematic=info, paste_note="Stand at origin and //paste, or //paste -o to restore design coords"))
    if extra:
        data.update(extra)
    groups = {}
    for k, v in sorted(W.anchors.items()):
        g, _, name = k.partition(".")
        groups.setdefault(g, {})[name] = v
    data["anchors"] = groups
    with open(path, "w") as fh:
        yaml.safe_dump(data, fh, sort_keys=False, allow_unicode=True, width=140)
