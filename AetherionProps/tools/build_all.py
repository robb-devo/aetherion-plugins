"""Build every registered prop: bake neighbour states, check support, export .schem + previews."""
import os
import sys
import json
import time
import importlib

from PIL import Image

from aeprops.core import normalize
from aeprops.render import Renderer, unresolved_states
from aeprops.style import check_support

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("AE_OUT", os.path.dirname(HERE))  # the AetherionProps folder
os.makedirs(os.path.join(OUT, "schematics"), exist_ok=True)
os.makedirs(os.path.join(OUT, "previews", "front"), exist_ok=True)
sys.path.insert(0, HERE)

import pieces  # noqa: E402  (registers everything)


def with_context(b, ctx):
    """Render-only surroundings: a grass plane at y=0, or a water plane at the piece's waterline.
    Blocks that would be buried (below ground / below the sea bed) are hidden."""
    if not ctx:
        return dict(b.blocks)
    (x1, _, z1), (x2, _, z2) = b.bounds()
    m = 3
    if isinstance(ctx, (list, tuple)) and ctx[0] == "water":
        lvl = ctx[1]
        bed = lvl - 3
        blocks = {p: s for p, s in b.blocks.items() if p[1] > bed}
        for x in range(x1 - m, x2 + m + 1):
            for z in range(z1 - m, z2 + m + 1):
                for y in range(bed + 1, lvl + 1):
                    if (x, y, z) not in b.blocks:
                        blocks[(x, y, z)] = "minecraft:water[level=0]"
                blocks[(x, bed, z)] = "minecraft:sand"
        return blocks
    if isinstance(ctx, (list, tuple)) and ctx[0] == "gully":
        # cutaway: banks only behind the bridge's front face, channel water running out toward the viewer
        hw, depth = ctx[1], ctx[2]
        blocks = {}
        for p_, s_ in b.blocks.items():
            if p_[1] < 0 and abs(p_[0]) > hw and p_[2] < z2:
                continue
            blocks[p_] = s_
        for x in range(x1 - m, x2 + m + 1):
            for z in range(z1 - m, z2 + m + 1):
                if abs(x) >= hw and z <= z2:
                    if (x, 0, z) not in b.blocks:
                        blocks[(x, 0, z)] = "minecraft:grass_block[snowy=false]"
                    if abs(x) == hw:
                        for y in range(-depth, 0):
                            if (x, y, z) not in b.blocks:
                                blocks[(x, y, z)] = "minecraft:stone"
                elif abs(x) < hw:
                    if (x, -depth - 1, z) not in b.blocks:
                        blocks[(x, -depth - 1, z)] = "minecraft:gravel"
                    if (x, -depth, z) not in b.blocks:
                        blocks[(x, -depth, z)] = "minecraft:water[level=0]"
        return blocks
    if isinstance(ctx, (list, tuple)) and ctx[0] == "cliff":
        lip = ctx[1]
        blocks = {p: s for p, s in b.blocks.items() if p[1] >= 0 or p[2] >= lip}
        for x in range(x1 - m, x2 + m + 1):
            for z in range(z1 - m, lip):
                if (x, 0, z) not in b.blocks:
                    blocks[(x, 0, z)] = "minecraft:grass_block[snowy=false]"
                for y in range(-7, 0):
                    if z == lip - 1 and (x, y, z) not in b.blocks:
                        blocks[(x, y, z)] = "minecraft:stone" if (x + y) % 3 else "minecraft:andesite"
        return blocks
    blocks = {p: s for p, s in b.blocks.items() if p[1] >= 0}
    for x in range(x1 - m, x2 + m + 1):
        for z in range(z1 - m, z2 + m + 1):
            if (x, 0, z) not in b.blocks:
                blocks[(x, 0, z)] = "minecraft:grass_block[snowy=false]"
    return blocks


def preview(b, u, title, ctx="grass"):
    r = Renderer(u)
    blocks = with_context(b, ctx)
    front = r.render(blocks, title=title + "  — front (SE)")
    back = r.render(blocks, rot=2, title="back (NW)")
    h = max(front.height, back.height)
    img = Image.new("RGB", (front.width + back.width, h), (236, 238, 232))
    img.paste(front, (0, 0))
    img.paste(back, (front.width, 0))
    return img, front


def run(names=None, u=None):
    meta = {}
    mp = os.path.join(HERE, "meta.json")
    if os.path.exists(mp):
        meta = json.load(open(mp))
    for name, (fn, info) in pieces.PIECES.items():
        if names and name not in names:
            continue
        t = time.time()
        b = fn()
        b.name = name
        b.resolve()
        warns = check_support(b)
        bad = unresolved_states(set(b.blocks.values()))
        size, offset, npal = b.save_schem(os.path.join(OUT, "schematics", name + ".schem"))
        W, H, L = size
        uu = u or (20 if max(W, L) <= 10 else 16 if max(W, L) <= 16 else 12 if max(W, L) <= 26 else 9)
        img, front = preview(b, uu, f"{name}  {W}x{H}x{L}", ctx=b.meta.pop("context", "grass"))
        img.save(os.path.join(OUT, "previews", name + ".png"))
        front.save(os.path.join(OUT, "previews", "front", name + ".png"))
        (mn, mx) = b.bounds()
        below = -mn[1] if mn[1] < 0 else 0
        meta[name] = dict(info, size=[W, H, L], offset=list(offset), blocks=len(b.blocks),
                          palette=npal, below_ground=below, anchor=list(b.anchor),
                          block_entities=len(b.block_entities), **b.meta)
        print(f"{name:34s} {W:3d}x{H:3d}x{L:3d} blocks={len(b.blocks):6d} pal={npal:3d} "
              f"below={below} offset={offset} {time.time() - t:.1f}s")
        for w in warns[:20]:
            print("   WARN", w)
        if len(warns) > 20:
            print(f"   ... {len(warns) - 20} more warnings")
        for s in bad:
            print("   NO MODEL", s)
    json.dump(meta, open(mp, "w"), indent=1)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    u = None
    for a in sys.argv[1:]:
        if a.startswith("--u="):
            u = int(a[4:])
    run(args or None, u)
