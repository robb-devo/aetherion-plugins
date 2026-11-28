"""Minimal NBT writer (big-endian, Java edition). Tags are (type, value) pairs built with the helpers below."""
import struct


class Tag:
    __slots__ = ("t", "v")

    def __init__(self, t, v):
        self.t = t
        self.v = v


def Byte(v): return Tag(1, int(v))
def Short(v): return Tag(2, int(v))
def Int(v): return Tag(3, int(v))
def Long(v): return Tag(4, int(v))
def Float(v): return Tag(5, float(v))
def Double(v): return Tag(6, float(v))
def ByteArray(v): return Tag(7, bytes(v))
def String(v): return Tag(8, str(v))
def List(t, items): return Tag(9, (t, list(items)))
def Compound(d): return Tag(10, d)
def IntArray(v): return Tag(11, list(v))
def LongArray(v): return Tag(12, v)  # numpy int64 array (big-endian written) or list of ints


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
    elif t == 7:
        out.append(struct.pack(">i", len(v)))
        out.append(v)
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
    elif t == 12:
        try:
            import numpy as np
            arr = np.asarray(v, dtype=">i8")
            out.append(struct.pack(">i", arr.size))
            out.append(arr.tobytes())
        except ImportError:  # pragma: no cover
            out.append(struct.pack(">i", len(v)))
            out.append(struct.pack(">%dq" % len(v), *v))
    else:
        raise ValueError(t)


def dumps(root):
    """Serialise a root compound (unnamed)."""
    out = [struct.pack(">bH", 10, 0)]
    _payload(out, root)
    return b"".join(out)


def parse_state(s):
    """'oak_stairs[facing=east,half=top]' -> ('minecraft:oak_stairs', {'facing': 'east', 'half': 'top'})"""
    if "[" in s:
        name, rest = s.split("[", 1)
        props = dict(p.split("=", 1) for p in rest.rstrip("]").split(",") if p)
    else:
        name, props = s, {}
    if ":" not in name:
        name = "minecraft:" + name
    return name, props


def state_tag(s):
    name, props = parse_state(s)
    d = {"Name": String(name)}
    if props:
        d["Properties"] = Compound({k: String(v) for k, v in sorted(props.items())})
    return Compound(d)
