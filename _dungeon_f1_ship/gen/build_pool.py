"""Build the Floor 1 room pool: generated rooms + adapted Lerfing prison pieces.

Outputs (into OUT):
  structures/floor1/<id>.nbt
  structures/floor1/pool.yml
  previews/<id>.png   (top-down, y=1..3 composite)
"""
import os
import sys
from collections import deque

import nbtlib
from PIL import Image, ImageDraw

from kit import Room, AIRISH
import rooms

SRC = os.environ.get("LERFING_SRC", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "AetherionDungeons",
                   "lerfing-template", "Lerfing Dungeon Template", "generated", "minecraft", "structures"))
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
STRUCT = os.path.join(OUT, "structures", "floor1")
PREV = os.path.join(OUT, "previews")
os.makedirs(STRUCT, exist_ok=True)
os.makedirs(PREV, exist_ok=True)

SIDES = ["MINZ", "MAXX", "MAXZ", "MINX"]  # clockwise order: north, east, south, west

# prison pieces we keep (near-duplicates dropped: 2≈1, 6≈5, 10≈9, 17≈16, 19≈18)
PRISON = {
    1: "Holding Cells", 3: "Guard Ring", 4: "Watch Post", 5: "Stair Cells", 7: "Split Cells",
    8: "Crossroads", 9: "Long Cells", 11: "Warden Steps", 14: "Side Chambers", 16: "Storeroom",
    18: "Barracks", 20: "Cell Gallery",
}

PASSABLE_EXTRA = {"jigsaw"}
COLORS = {
    "stone_bricks": (122, 122, 122), "mossy_stone_bricks": (104, 121, 90), "cracked_stone_bricks": (110, 110, 110),
    "chiseled_stone_bricks": (140, 140, 140), "polished_andesite": (150, 152, 150), "andesite": (136, 136, 136),
    "iron_bars": (90, 90, 110), "spruce_planks": (114, 84, 48), "stripped_spruce_log": (140, 104, 64),
    "spruce_stairs": (114, 84, 48), "spruce_slab": (114, 84, 48), "spruce_fence": (100, 74, 42), "barrel": (140, 100, 60),
    "bookshelf": (120, 80, 40), "hay_block": (200, 170, 40), "cobweb": (230, 230, 230), "lantern": (255, 200, 90),
    "soul_lantern": (90, 200, 220), "chain": (60, 60, 70), "water": (50, 90, 200), "moss_carpet": (90, 130, 50),
    "candle": (240, 220, 160), "anvil": (60, 60, 60), "grindstone": (100, 100, 100), "cobblestone": (115, 115, 115),
    "gravel": (130, 125, 120), "rubble": (100, 100, 100),
}


def load_piece(n):
    f = nbtlib.load(os.path.join(SRC, f"test{n}.nbt"))
    pal = f["palette"]
    sx, sy, sz = [int(v) for v in f["size"]]
    blocks = {}
    for b in f["blocks"]:
        x, y, z = [int(v) for v in b["pos"]]
        st = pal[int(b["state"])]
        name = str(st["Name"]).split(":")[1]
        props = {k: str(v) for k, v in st.get("Properties", {}).items()}
        blocks[(x, y, z)] = (name, props)
    return (sx, sy, sz), blocks


def piece_room(n):
    (sx, sy, sz), blocks = load_piece(n)
    r = Room(f"f1_prison_{n:02d}", sx, sy, 1000 + n)
    r.title = PRISON[n]
    for pos, (name, props) in blocks.items():
        if name == "jigsaw":
            name, props = "air", {}
        if name in ("bedrock", "barrier"):
            # Lerfing markers: floor stays floor, anything above is a blocker the old builder scrubbed away.
            name, props = ("stone_bricks", {}) if pos[1] == 0 else ("air", {})
        r.b[pos] = (name, props)
    return r


def passable(r, x, y, z):
    name = r.get(x, y, z)
    return name in AIRISH or name in PASSABLE_EXTRA


def reach(r):
    """Largest connected walkable region (4-neighbourhood) — the room's main floor."""
    S = r.S
    def walk(x, z):
        return 0 <= x < S and 0 <= z < S and passable(r, x, 1, z) and passable(r, x, 2, z) \
            and not passable(r, x, 0, z) and r.get(x, 0, z) not in ("water", "lava")
    best = set()
    seen_all = set()
    for sx in range(S):
        for sz in range(S):
            if (sx, sz) in seen_all or not walk(sx, sz):
                continue
            comp = {(sx, sz)}
            q = deque([(sx, sz)])
            while q:
                x, z = q.popleft()
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, nz = x + dx, z + dz
                    if (nx, nz) not in comp and walk(nx, nz):
                        comp.add((nx, nz))
                        q.append((nx, nz))
            seen_all |= comp
            if len(comp) > len(best):
                best = comp
    return best


def doors(r):
    """A side is a door when punching the builder's 5x5 mouth there connects to the centre region."""
    S, m = r.S, r.mid
    region = reach(r)
    out = []
    for side in SIDES:
        if side == "MINZ":
            cells = [(m + d, k) for d in range(-2, 3) for k in range(0, 3)]
        elif side == "MAXZ":
            cells = [(m + d, S - 1 - k) for d in range(-2, 3) for k in range(0, 3)]
        elif side == "MINX":
            cells = [(k, m + d) for d in range(-2, 3) for k in range(0, 3)]
        else:
            cells = [(S - 1 - k, m + d) for d in range(-2, 3) for k in range(0, 3)]
        # mouth punch reaches 2 blocks in; the region must touch the punched box or its inner edge
        touch = False
        for (x, z) in cells:
            for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, z + dz) in region:
                    touch = True
        if touch:
            out.append(side)
    return out, len(region)


def preview(r, path):
    S = r.S
    scale = 8
    img = Image.new("RGB", (S * scale, S * scale), (20, 20, 24))
    d = ImageDraw.Draw(img)
    for x in range(S):
        for z in range(S):
            col = None
            for y in (1, 2, 3):
                name = r.get(x, y, z)
                if name not in ("air", "cave_air"):
                    if name in COLORS and name not in ("stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks", "cobblestone", "andesite", "polished_andesite", "gravel"):
                        col = COLORS[name]
                    elif "stairs" in name or "slab" in name:
                        col = (180, 160, 120)
                    elif name.endswith("_wall") or name.endswith("_fence"):
                        col = (200, 200, 170)
                    else:
                        col = (215, 215, 215)
                    if y > 1:
                        col = tuple(int(c * 0.8) for c in col)
                    break
            if col is None:
                fl = r.get(x, 0, z)
                col = (50, 90, 200) if fl == "water" else (58, 60, 64) if fl != "moss_block" else (60, 80, 50)
            d.rectangle([x * scale, z * scale, x * scale + scale - 1, z * scale + scale - 1], fill=col)
    for (x, z) in r.pads:
        d.ellipse([x * scale + 1, z * scale + 1, x * scale + scale - 2, z * scale + scale - 2], fill=(220, 50, 50))
    if r.loot:
        x, z = r.loot
        d.rectangle([x * scale, z * scale, x * scale + scale - 1, z * scale + scale - 1], outline=(255, 215, 0), width=3)
    img.save(path)


def main():
    entries = []
    for builder, size_class, weight in rooms.DESIGNS:
        r = builder(abs(hash(builder.__name__)) % 100000)
        door_list, region = doors(r)
        count, pal = r.to_nbt(os.path.join(STRUCT, r.name + ".nbt"))
        preview(r, os.path.join(PREV, r.name + ".png"))
        entries.append((r, size_class, weight, door_list, region))
        print(f"{r.name:24s} {size_class:6s} S={r.S} H={r.H} blocks={count} pal={pal} doors={door_list} region={region} pads={len(r.pads)} loot={r.loot}")
    for n, title in PRISON.items():
        r = piece_room(n)
        door_list, region = doors(r)
        r.auto_pads(10, 4)
        r.auto_loot()
        count, pal = r.to_nbt(os.path.join(STRUCT, r.name + ".nbt"))
        preview(r, os.path.join(PREV, r.name + ".png"))
        entries.append((r, "SMALL", 2, door_list, region))
        print(f"{r.name:24s} SMALL  S={r.S} H={r.H} blocks={count} pal={pal} doors={door_list} region={region} pads={len(r.pads)} loot={r.loot}")

    lines = [
        "# Floor 1 room pool (Warden's Prison). Generated by gen/build_pool.py.",
        "# Coordinates are template-local (x east, z south, floor at y=0).",
        "# doors: sides whose centre mouth reaches the room's main floor (MINZ=north, MAXX=east, MAXZ=south, MINX=west).",
        "# Drop extra <id>.nbt files into plugins/AetherionDungeons/structures/floor1/ and add an entry here to extend the pool.",
        "version: 1",
        "templates:",
    ]
    for r, size_class, weight, door_list, region in entries:
        if size_class != "LOBBY" and size_class != "BOSS" and len(door_list) == 0:
            print("SKIP (no doors)", r.name)
            continue
        lines.append(f"  {r.name}:")
        lines.append(f"    file: {r.name}.nbt")
        lines.append(f"    title: \"{r.title}\"")
        lines.append(f"    class: {size_class}")
        lines.append(f"    size: {r.S}")
        lines.append(f"    height: {r.H}")
        lines.append(f"    weight: {weight}")
        lines.append(f"    doors: [{', '.join(door_list)}]")
        lx, lz = r.loot if r.loot else (r.mid, r.mid)
        lines.append(f"    loot: [{lx}, {lz}]")
        lines.append("    pads: [" + ", ".join(f"[{x}, {z}]" for x, z in r.pads) + "]")
    with open(os.path.join(STRUCT, "pool.yml"), "w", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print("templates:", len(entries))


if __name__ == "__main__":
    main()
