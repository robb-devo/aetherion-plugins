"""Preview images of a dumped world (see World.dump) through the C ray caster (cast.c).

  python render.py <dump dir> <out.png> persp x y z yaw pitch [W H fov]
  python render.py <dump dir> <out.png> top [cx cz span] [W]
  python render.py <dump dir> <out.png> iso yaw pitch [cx cy cz span] [W H]

World coordinates and Minecraft angles (yaw 0 = south, 90 = west, -90 = east; pitch positive = down).
"""
import hashlib
import json
import os
import struct
import subprocess
import sys

import numpy as np
from PIL import Image

import blocktex

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.environ.get("MC_CLIENT_JAR", os.path.expandvars(
    r"%APPDATA%\ModrinthApp\meta\versions\1.21.1-0.19.5\1.21.1-0.19.5.jar"))
CAST = os.path.join(HERE, "cast.exe" if os.name == "nt" else "cast")
SUN_YAW, SUN_PITCH = -78.0, -30.0   # low in the east, a little south: morning


def tables(dump):
    meta = json.load(open(os.path.join(dump, "meta.json"), encoding="utf-8"))
    salt = str(int(os.path.getmtime(blocktex.__file__)))          # a change to the looks rebuilds the tables
    key = hashlib.sha1((json.dumps(meta["palette"]) + salt).encode()).hexdigest()[:16]
    tpath = os.path.join(dump, "table-%s.bin" % key)
    apath = os.path.join(dump, "atlas-%s.bin" % key)
    if not (os.path.isfile(tpath) and os.path.isfile(apath)):
        atlas, table, missing = blocktex.build_tables(JAR, meta["palette"])
        if missing:
            print("no look for:", ", ".join(missing))
        out = bytearray()
        for e in table:
            boxes = np.zeros((8, 6), np.float32)
            if e is None:
                out += struct.pack("<ii3ii3f3f", 0, 0, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1) + boxes.tobytes()
                continue
            b = e["boxes"][:8]
            boxes[:len(b)] = np.array(b, np.float32)
            tt = [c / 255.0 for c in e["ttop"]] if e["ttop"] else [-1, -1, -1]
            ts = [c / 255.0 for c in e["tside"]] if e["tside"] else [-1, -1, -1]
            out += struct.pack("<ii3ii3f3f", e["flags"], len(b), *e["tex"], e["axis"], *tt, *ts) + boxes.tobytes()
        open(tpath, "wb").write(out)
        atlas.tofile(apath)
    return meta, tpath, apath


def cast(dump, out_png, mode, cam, yaw, pitch, fov, W, H, ss=2, sun=(SUN_YAW, SUN_PITCH)):
    meta, tpath, apath = tables(dump)
    raw = out_png + ".rgb"
    local = (cam[0] - meta["x0"], cam[1] - meta["y0"], cam[2] - meta["z0"])
    args = [CAST, os.path.join(dump, "blocks.u16"), str(meta["sx"]), str(meta["sy"]), str(meta["sz"]), tpath, apath,
            raw, str(W), str(H), str(mode), *("%.3f" % c for c in local), str(yaw), str(pitch), str(fov),
            str(sun[0]), str(sun[1]), str(ss)]
    subprocess.run(args, check=True)
    img = np.fromfile(raw, dtype=np.uint8).reshape(H, W, 3)
    os.remove(raw)
    Image.fromarray(img, "RGB").save(out_png)
    return meta


def top(dump, out_png, cx=0.0, cz=0.0, span=None, W=1400, labels=True):
    meta = json.load(open(os.path.join(dump, "meta.json"), encoding="utf-8"))
    if span is None:
        span = float(max(meta["sx"], meta["sz"]))
    cast(dump, out_png, 1, (cx, meta["y0"] + meta["sy"] - 1, cz), 180, 89.99, span, W, W)
    if labels and meta.get("marks"):
        from PIL import ImageDraw
        im = Image.open(out_png)
        dr = ImageDraw.Draw(im)
        scale = W / span
        for key, m in meta["marks"].items():
            px = (m["x"] + 0.5 - cx) * scale + W / 2
            py = (m["z"] + 0.5 - cz) * scale + W / 2
            dr.ellipse([px - 4, py - 4, px + 4, py + 4], fill=(255, 220, 60), outline=(0, 0, 0))
            dr.text((px + 6, py - 6), key, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))
        im.save(out_png)


def iso(dump, out_png, yaw=-135.0, pitch=35.0, centre=None, span=None, W=1600, H=1000):
    meta = json.load(open(os.path.join(dump, "meta.json"), encoding="utf-8"))
    if centre is None:
        centre = (0.0, meta["y0"] + 44.0, 0.0)
    if span is None:
        span = float(max(meta["sx"], meta["sz"])) * 1.1
    cast(dump, out_png, 1, centre, yaw, pitch, span, W, H)


def persp(dump, out_png, x, y, z, yaw, pitch, W=1280, H=720, fov=78.0, ss=2):
    cast(dump, out_png, 0, (x, y, z), yaw, pitch, fov, W, H, ss)


if __name__ == "__main__":
    dump, out, kind = sys.argv[1], sys.argv[2], sys.argv[3]
    a = [float(v) for v in sys.argv[4:]]
    if kind == "persp":
        persp(dump, out, *a[:5], *(int(v) for v in a[5:7]), *a[7:8])
    elif kind == "top":
        top(dump, out, *a[:3], *(int(v) for v in a[3:4]))
    elif kind == "iso":
        iso(dump, out, a[0], a[1], tuple(a[2:5]) if len(a) >= 5 else None, a[5] if len(a) > 5 else None,
            *(int(v) for v in a[6:8]))
