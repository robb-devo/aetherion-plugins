"""Contact sheets: several views of a dumped world in one image.

  python shots.py <dump dir> <out.png> [--noon] shot ...

A shot is  name:FROM>TO[,fov]  or  name:x,y,z,yaw,pitch[,fov].
FROM and TO are points:  x,y,z  (exact)  or  x,z  (standing on the ground there: eye height)  or
x,z,+h  (h blocks above the ground there).  Example:  road:27,64>26,96   overlook:-3,-27,+3>26,96,+8,40
--noon lights the scene from high up (no long shadows), to judge shapes in what would be shade.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

import render

SOFT = ("_leaves", "_log", "_wood", "_fence", "_carpet", "_sapling", "_banner", "_wool")
PLANTS = {"air", "short_grass", "tall_grass", "fern", "large_fern", "dandelion", "poppy", "cornflower",
          "oxeye_daisy", "azure_bluet", "allium", "red_tulip", "white_tulip", "sugar_cane", "wheat", "carrots",
          "potatoes", "beetroots", "lantern", "moss_carpet", "brown_mushroom", "red_mushroom", "sweet_berry_bush",
          "dead_bush", "seagrass", "lily_pad", "cobweb", "chain", "campfire"}


class Ground:
    """Looks up the walking surface of a dumped world."""

    def __init__(self, dump):
        self.meta = json.load(open(os.path.join(dump, "meta.json"), encoding="utf-8"))
        m = self.meta
        self.blocks = np.fromfile(os.path.join(dump, "blocks.u16"), dtype=np.uint16).reshape(m["sy"], m["sz"], m["sx"])
        skip = []
        for i, s in enumerate(m["palette"]):
            n = s.split("[", 1)[0].replace("minecraft:", "")
            if n in PLANTS or n.endswith(SOFT):
                skip.append(i)
        self.skip = np.array(skip, dtype=np.uint16)

    def at(self, x, z):
        m = self.meta
        xi, zi = int(math.floor(x)) - m["x0"], int(math.floor(z)) - m["z0"]
        if not (0 <= xi < m["sx"] and 0 <= zi < m["sz"]):
            return 63
        col = self.blocks[:, zi, xi]
        solid = np.nonzero(~np.isin(col, self.skip))[0]
        return m["y0"] + int(solid[-1]) + 1 if solid.size else 63


def point(text, ground, eye=1.62):
    parts = text.split(",")
    if len(parts) >= 3 and not parts[2].startswith("+"):
        return [float(parts[0]), float(parts[1]), float(parts[2])], parts[3:]
    x, z = float(parts[0]), float(parts[1])
    if len(parts) >= 3 and parts[2].startswith("+"):
        return [x, ground.at(x, z) + float(parts[2][1:]), z], parts[3:]
    return [x, ground.at(x, z) + eye, z], parts[2:]


def parse(arg, ground):
    name, rest = arg.split(":", 1)
    if ">" in rest:
        a, b = rest.split(">", 1)
        e, _ = point(a, ground)
        v, extra = point(b, ground, eye=1.2)
        dx, dy, dz = v[0] - e[0], v[1] - e[1], v[2] - e[2]
        yaw = math.degrees(math.atan2(-dx, dz))
        pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        return (name, e[0], e[1], e[2], yaw, pitch, float(extra[0]) if extra else 78.0)
    v = [float(p) for p in rest.split(",")]
    return (name, v[0], v[1], v[2], v[3], v[4], v[5] if len(v) > 5 else 78.0)


def sheet(dump, out, shots, cols=2, w=800, h=450, ss=2, sun=None):
    rows = (len(shots) + cols - 1) // cols
    img = Image.new("RGB", (cols * w, rows * h), (20, 20, 20))
    for i, (name, x, y, z, yaw, pitch, fov) in enumerate(shots):
        tmp = out + ".%d.png" % i
        if sun:
            render.cast(dump, tmp, 0, (x, y, z), yaw, pitch, fov, w, h, ss, sun=sun)
        else:
            render.persp(dump, tmp, x, y, z, yaw, pitch, w, h, fov, ss)
        tile = Image.open(tmp)
        img.paste(tile, ((i % cols) * w, (i // cols) * h))
        tile.close()
        os.remove(tmp)
        d = ImageDraw.Draw(img)
        d.text(((i % cols) * w + 8, (i // cols) * h + 6), name, fill=(255, 255, 255), stroke_width=2,
               stroke_fill=(0, 0, 0))
    img.save(out)


if __name__ == "__main__":
    args = [a for a in sys.argv[3:] if a != "--noon"]
    ground = Ground(sys.argv[1])
    sheet(sys.argv[1], sys.argv[2], [parse(a, ground) for a in args],
          sun=(-60.0, -72.0) if "--noon" in sys.argv else None)
