"""Place END_PORTAL in ashen_void wherever bloodstone has lava."""
from __future__ import annotations

from pathlib import Path

from amulet import load_level
from amulet.api.block import Block

BLOOD = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_live\bloodstone")
ASHEN = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\ashen_void")
DIM = "minecraft:overworld"
PORTAL = Block("universal_minecraft", "end_portal", {})
VERSION = ("universal", (1, 0, 0))


def chunk_has_lava(level, cx: int, cz: int) -> bool:
    base_x, base_z = cx * 16, cz * 16
    for lx in range(0, 16, 2):
        for lz in range(0, 16, 2):
            for y in range(16, 112, 4):
                try:
                    if level.get_block(base_x + lx, y, base_z + lz, DIM).base_name == "lava":
                        return True
                except Exception:
                    continue
    return False


def main() -> int:
    src = load_level(str(BLOOD))
    dst = load_level(str(ASHEN))
    changed = 0
    try:
        coords = list(src.all_chunk_coords(DIM))
        hot = [(cx, cz) for cx, cz in coords if chunk_has_lava(src, cx, cz)]
        print(f"hot_chunks={len(hot)} / {len(coords)}", flush=True)
        for i, (cx, cz) in enumerate(hot):
            base_x, base_z = cx * 16, cz * 16
            before = changed
            for lx in range(16):
                for lz in range(16):
                    for y in range(0, 128):
                        try:
                            b = src.get_block(base_x + lx, y, base_z + lz, DIM)
                        except Exception:
                            continue
                        if b.base_name != "lava":
                            continue
                        dst.set_version_block(base_x + lx, y, base_z + lz, DIM, VERSION, PORTAL)
                        changed += 1
            print(f"  {i+1}/{len(hot)} chunk {cx},{cz} +{changed-before} total={changed}", flush=True)
            if (i + 1) % 3 == 0:
                dst.save()
        dst.save()
        print(f"DONE end_portal_blocks={changed}", flush=True)
    finally:
        src.close()
        dst.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
