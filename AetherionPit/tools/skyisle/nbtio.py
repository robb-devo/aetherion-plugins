"""Tiny NBT writer + vanilla structure-template (.nbt) export for the sky-isle hub."""
import gzip
import struct


class Tag:
    __slots__ = ("t", "v")

    def __init__(self, t, v):
        self.t = t
        self.v = v


def Byte(v): return Tag(1, v)
def Short(v): return Tag(2, v)
def Int(v): return Tag(3, v)
def Long(v): return Tag(4, v)
def Float(v): return Tag(5, v)
def Double(v): return Tag(6, v)
def String(v): return Tag(8, v)
def IntArray(v): return Tag(11, v)
def List(t, items): return Tag(9, (t, items))
def Compound(d): return Tag(10, d)


def _payload(out, tag):
    t, v = tag.t, tag.v
    if t == 1:
        out.append(struct.pack(">b", v))
    elif t == 2:
        out.append(struct.pack(">h", v))
    elif t == 3:
        out.append(struct.pack(">i", v))
    elif t == 4:
        out.append(struct.pack(">q", v))
    elif t == 5:
        out.append(struct.pack(">f", v))
    elif t == 6:
        out.append(struct.pack(">d", v))
    elif t == 8:
        b = v.encode("utf-8")
        out.append(struct.pack(">H", len(b)))
        out.append(b)
    elif t == 9:
        et, items = v
        out.append(struct.pack(">bi", et if items else 0, len(items)))
        for it in items:
            _payload(out, it)
    elif t == 10:
        for k, it in v.items():
            kb = k.encode("utf-8")
            out.append(struct.pack(">bH", it.t, len(kb)))
            out.append(kb)
            _payload(out, it)
        out.append(b"\x00")
    elif t == 11:
        out.append(struct.pack(">i", len(v)))
        out.append(struct.pack(">%di" % len(v), *v))
    else:
        raise ValueError(t)


def write_gz(path, root):
    out = [struct.pack(">bH", 10, 0)]
    _payload(out, root)
    with gzip.open(path, "wb", compresslevel=6) as f:
        f.write(b"".join(out))


def parse_state(s):
    """'minecraft:oak_stairs[facing=east,half=top]' -> (name, {props})"""
    if "[" in s:
        name, rest = s.split("[", 1)
        props = dict(p.split("=") for p in rest.rstrip("]").split(",") if p)
    else:
        name, props = s, {}
    if ":" not in name:
        name = "minecraft:" + name
    return name, props


def write_structure(path, size, palette, blocks, block_nbt, data_version=3955):
    """palette: list of state strings; blocks: iterable of (x,y,z,pidx); block_nbt: {(x,y,z): Compound}."""
    pal = []
    for s in palette:
        name, props = parse_state(s)
        d = {"Name": String(name)}
        if props:
            d["Properties"] = Compound({k: String(v) for k, v in props.items()})
        pal.append(Compound(d))
    items = []
    for x, y, z, p in blocks:
        d = {"pos": List(3, [Int(int(x)), Int(int(y)), Int(int(z))]), "state": Int(int(p))}
        nb = block_nbt.get((int(x), int(y), int(z)))
        if nb is not None:
            d["nbt"] = nb
        items.append(Compound(d))
    root = Compound({
        "DataVersion": Int(data_version),
        "size": List(3, [Int(size[0]), Int(size[1]), Int(size[2])]),
        "palette": List(10, pal),
        "blocks": List(10, items),
        "entities": List(10, []),
    })
    write_gz(path, root)
