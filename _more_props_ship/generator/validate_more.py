"""Strict re-check of the second-pass schematics in $AE_OUT/schematics (or argv[1]).

Per file: Sponge v3 layout + DataVersion 3955 (validate.check), every palette state canonical and in the
1.21.1 registry, block entities on matching blocks, re-built piece has no support warnings, the file on disk
matches a fresh build block-for-block, the anchor (paste origin) sits inside the footprint, and nothing
below a declared waterline is left dry when it could hold water.
"""
import glob
import os
import sys

import nbtlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pieces  # noqa: E402
from validate import check, varints  # noqa: E402
from aeprops.style import check_support  # noqa: E402
from aeprops.core import registry  # noqa: E402
from build_more import NEW  # noqa: E402

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.environ.get("AE_OUT", os.path.dirname(HERE)), "schematics")
bad = 0
for name in NEW:
    path = os.path.join(out, name + ".schem")
    probs = []
    if not os.path.isfile(path):
        print(f"{name:32s} MISSING")
        bad += 1
        continue
    size, dv, nblocks, p = check(path)
    probs += p
    if dv != 3955:
        probs.append(f"DataVersion {dv}")
    s = nbtlib.load(path)["Schematic"]
    W, H, L = size
    off = [int(v) for v in s["Offset"]]
    # origin (anchor) must fall inside the region
    ax, ay, az = -off[0], -off[1], -off[2]
    if not (0 <= ax < W and 0 <= ay < H and 0 <= az < L):
        probs.append(f"anchor outside region {(ax, ay, az)}")
    # fresh build equality
    fn, info = pieces.PIECES[name]
    b = fn(); b.name = name; b.resolve()
    warns = check_support(b)
    if warns:
        probs.append(f"{len(warns)} support warnings e.g. {warns[0]}")
    pal = {int(v): str(k) for k, v in s["Blocks"]["Palette"].items()}
    ids = varints(bytes(int(x) & 0xFF for x in s["Blocks"]["Data"]))
    (mx, my, mz), _ = b.bounds()
    disk = {}
    for i, pid in enumerate(ids):
        st = pal[pid]
        if st == "minecraft:air":
            continue
        x = i % W; z = (i // W) % L; y = i // (W * L)
        disk[(x + mx, y + my, z + mz)] = st
    if disk != b.blocks:
        probs.append("schem differs from a fresh build")
    wl = b.meta.get("context")
    if isinstance(wl, tuple) and wl[0] == "water" and name not in ("ae_prop_channel_marker",):
        reg = registry()
        dry = [p_ for p_, st in b.blocks.items() if p_[1] < wl[1] and "waterlogged" in reg[st.split("[")[0]][0]
               and "waterlogged=false" in st]
        if dry:
            probs.append(f"{len(dry)} dry waterloggables under the waterline")
    print(f"{name:32s} {W:3d}x{H:3d}x{L:3d} blocks={nblocks:5d} anchor={(ax, ay, az)} "
          f"{'OK' if not probs else probs}")
    bad += bool(probs)
print(f"{len(NEW)} pieces, " + ("ALL OK" if not bad else f"{bad} with problems"))
sys.exit(1 if bad else 0)
