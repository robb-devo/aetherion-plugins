"""Re-parse every .schem in ../schematics and check it against the 1.21.1 block registry.

Checks: Sponge v3 layout (same tag types FAWE writes), DataVersion, palette states are valid and
fully specified, block data length, block-entity positions sit on matching blocks.
"""
import glob
import os
import sys

import nbtlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from aeprops.core import normalize, bname  # noqa: E402

EXPECT = {"Version": "Int", "DataVersion": "Int", "Metadata": "Compound", "Width": "Short", "Height": "Short",
          "Length": "Short", "Offset": "IntArray", "Blocks": "Compound"}


def varints(buf):
    out, i = [], 0
    while i < len(buf):
        v = s = 0
        while True:
            b = buf[i]
            i += 1
            v |= (b & 0x7F) << s
            if not b & 0x80:
                break
            s += 7
        out.append(v)
    return out


def check(path):
    problems = []
    s = nbtlib.load(path)["Schematic"]
    for k, t in EXPECT.items():
        if type(s.get(k)).__name__ != t:
            problems.append(f"{k} should be {t}")
    W, H, L = int(s["Width"]), int(s["Height"]), int(s["Length"])
    pal = {int(v): str(k) for k, v in s["Blocks"]["Palette"].items()}
    for st in pal.values():
        if st != "minecraft:air" and normalize(st) != st:
            problems.append("not canonical: " + st)
    ids = varints(bytes(int(x) & 0xFF for x in s["Blocks"]["Data"]))
    if len(ids) != W * H * L:
        problems.append("block data length mismatch")
    for be in s["Blocks"]["BlockEntities"]:
        x, y, z = (int(v) for v in be["Pos"])
        st = pal[ids[x + z * W + y * W * L]]
        kind = str(be["Id"]).split(":")[1].replace("hanging_sign", "sign")
        if kind not in bname(st) and not (kind == "bed" and bname(st).endswith("_bed")):
            problems.append(f"block entity {kind} sits on {st}")
    blocks = sum(1 for i in ids if pal[i] != "minecraft:air")
    return (W, H, L), int(s["DataVersion"]), blocks, problems


if __name__ == "__main__":
    bad = 0
    for f in sorted(glob.glob(os.path.join(HERE, "..", "schematics", "*.schem"))):
        size, dv, blocks, problems = check(f)
        bad += bool(problems)
        print(f"{os.path.basename(f):34s} {size[0]}x{size[1]}x{size[2]}  dv={dv}  blocks={blocks}  "
              f"{'OK' if not problems else problems}")
    print("ALL OK" if not bad else f"{bad} file(s) with problems")
