"""
Ashen Void builder — 1:1 Bloodstone layout with End-themed universal block remap.

Works ONLY under _ashen_void_build. Does NOT touch live bloodstone / BossEngine.
Uses Amulet universal_minecraft blocks (concrete+color, wall+material, etc.).
"""
from __future__ import annotations

import shutil
import sys
import traceback
from collections import Counter
from pathlib import Path

import numpy as np
from amulet import load_level
from amulet.api.block import Block
from amulet.api.errors import ChunkDoesNotExist, ChunkLoadError
from amulet_nbt import StringTag

SRC = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_live\bloodstone")
TAR = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_readonly_copy.tar")
DST = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\ashen_void")
LOG = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\build_log.txt")

NS = "universal_minecraft"

# Warm / bloodstone colors → End palette
COLOR_MAP = {
    "red": "purple",
    "orange": "magenta",
    "yellow": "magenta",
    "pink": "magenta",
    "light_gray": "purple",
    "gray": "black",
    "white": "purple",
    "brown": "black",
    "lime": "cyan",
}

# Structural materials → End equivalents (preserve stairs/slab/wall shape props)
MATERIAL_MAP = {
    "deepslate_tile": "end_stone_brick",
    "cobbled_deepslate": "end_stone_brick",
    "polished_deepslate": "purpur",
    "deepslate_brick": "end_stone_brick",
    "red_nether_brick": "purpur",
    "nether_brick": "purpur",
    "blackstone": "end_stone_brick",
    "polished_blackstone": "purpur",
    "polished_blackstone_brick": "purpur",
    "mangrove": "purpur",
    "crimson": "purpur",
    "warped": "purpur",
    "andesite": "end_stone_brick",
    "stone_brick": "end_stone_brick",
    "stone": "end_stone_brick",
    "cobblestone": "end_stone_brick",
    "mossy_cobblestone": "end_stone_brick",
    "mossy_stone_brick": "end_stone_brick",
    "basalt": "purpur",
    "smooth_basalt": "end_stone_brick",
}

# Simple base_name → replacement (no props to preserve, or discard props)
SIMPLE_REMAP = {
    "lava": "air",
    "fire": "air",
    "soul_fire": "air",
    "netherrack": "end_stone",
    "crimson_nylium": "end_stone",
    "warped_nylium": "end_stone",
    "magma_block": "end_stone",
    "soul_sand": "end_stone",
    "soul_soil": "end_stone",
    "basalt": "end_stone_bricks",
    "smooth_basalt": "end_stone",
    "polished_basalt": "purpur_pillar",
    "nether_wart_block": "purpur_block",
    "warped_wart_block": "purpur_block",
    "nether_bricks": "purpur_block",
    "cracked_nether_bricks": "purpur_block",
    "chiseled_nether_bricks": "purpur_pillar",
    "red_nether_bricks": "purpur_block",
    "gilded_blackstone": "crying_obsidian",
    "blackstone": "end_stone_bricks",
    "polished_blackstone": "purpur_block",
    "polished_blackstone_bricks": "purpur_block",
    "chiseled_polished_blackstone": "purpur_pillar",
    "glowstone": "purpur_block",
    "shroomlight": "ochre_froglight",
    "redstone_block": "amethyst_block",
    "coral_block": "end_stone",
    "polished_deepslate": "end_stone_bricks",
    "cobbled_deepslate": "end_stone_bricks",
    "deepslate": "end_stone",
    "deepslate_bricks": "end_stone_bricks",
    "deepslate_tiles": "end_stone_bricks",
    "chiseled_deepslate": "purpur_pillar",
    "andesite": "end_stone",
    "nether_sprouts": "air",
    "weeping_vines": "air",
    "weeping_vines_plant": "air",
    "nether_wart": "air",
    "crimson_fungus": "chorus_flower",
    "warped_fungus": "chorus_flower",
    "crimson_roots": "chorus_plant",
    "warped_roots": "chorus_plant",
    "twisting_vines": "chorus_plant",
    "twisting_vines_plant": "chorus_plant",
    "campfire": "air",
    "soul_campfire": "air",
}

COLORED_NAMES = {
    "concrete",
    "concrete_powder",
    "wool",
    "carpet",
    "stained_glass",
    "stained_glass_pane",
    "stained_terracotta",
    "glazed_terracotta",
    "candle",
    "bed",
    "banner",
    "wall_banner",
    "shulker_box",
}

SHAPE_NAMES = {"wall", "slab", "stairs"}


def log(msg: str, fh) -> None:
    print(msg, flush=True)
    fh.write(msg + "\n")
    fh.flush()


def tag_str(props: dict, key: str) -> str | None:
    if key not in props:
        return None
    v = props[key]
    return getattr(v, "py_str", None) or str(v)


def copy_props(props: dict) -> dict:
    return dict(props) if props else {}


def remap_block(block: Block) -> Block | None:
    """Return a new universal Block, or None if unchanged."""
    name = block.base_name
    props = block.properties or {}

    if name in SIMPLE_REMAP:
        dest = SIMPLE_REMAP[name]
        if dest == "air":
            return Block(NS, "air")
        return Block(NS, dest)

    if name in COLORED_NAMES:
        c = tag_str(props, "color")
        if c in COLOR_MAP:
            new_props = copy_props(props)
            new_props["color"] = StringTag(COLOR_MAP[c])
            return Block(NS, name, new_props)
        return None

    if name in SHAPE_NAMES:
        m = tag_str(props, "material")
        if m in MATERIAL_MAP:
            new_mat = MATERIAL_MAP[m]
            # Vanilla has no purpur_wall — walls must use end_stone_brick
            if name == "wall" and new_mat == "purpur":
                new_mat = "end_stone_brick"
            new_props = copy_props(props)
            new_props["material"] = StringTag(new_mat)
            return Block(NS, name, new_props)
        return None

    # Wood / planks / fence with nether-ish or mangrove material
    if name in ("wood", "planks", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate"):
        m = tag_str(props, "material")
        if m in ("mangrove", "crimson", "warped", "nether_brick"):
            if name == "wood":
                return Block(NS, "purpur_pillar")
            if name == "planks":
                return Block(NS, "purpur_block")
            if name in ("fence", "fence_gate"):
                # Keep as fence with a translateable material (iron_bars is not universal)
                new_props = copy_props(props)
                new_props["material"] = StringTag("crimson")  # will still look odd; prefer wall-like
                # Better: convert fence posts to end_stone_brick walls (up=true)
                if name == "fence":
                    return Block(
                        NS,
                        "wall",
                        {
                            "material": StringTag("end_stone_brick"),
                            "east": StringTag("none"),
                            "north": StringTag("none"),
                            "south": StringTag("none"),
                            "west": StringTag("none"),
                            "up": StringTag("true"),
                        },
                    )
                return Block(NS, "purpur_block")
            if name == "door":
                new_props = {k: v for k, v in copy_props(props).items() if k != "material"}
                return Block(NS, "iron_door", new_props)
            if name == "trapdoor":
                new_props = {k: v for k, v in copy_props(props).items() if k != "material"}
                return Block(NS, "iron_trapdoor", new_props)
            return Block(NS, "purpur_block")
        return None

    # Stem / hyphae style if stored as base names
    if name in (
        "crimson_stem",
        "crimson_hyphae",
        "stripped_crimson_stem",
        "stripped_crimson_hyphae",
        "warped_stem",
        "warped_hyphae",
        "stripped_warped_stem",
        "stripped_warped_hyphae",
    ):
        return Block(NS, "purpur_pillar")
    if name in ("crimson_planks", "warped_planks"):
        return Block(NS, "purpur_block")

    return None


def block_unchanged(old: Block, new: Block) -> bool:
    if old.base_name != new.base_name:
        return False
    return dict(old.properties or {}) == dict(new.properties or {})


def ensure_source(logf) -> Path:
    if SRC.exists() and (SRC / "level.dat").exists():
        log(f"SRC ok: {SRC}", logf)
        return SRC
    if TAR.exists():
        log(f"SRC missing; extracting {TAR}", logf)
        extract_dir = SRC.parent
        extract_dir.mkdir(parents=True, exist_ok=True)
        shutil.unpack_archive(str(TAR), str(extract_dir))
        if not SRC.exists():
            # tar may unpack with different top folder
            raise FileNotFoundError(f"After extract, SRC still missing: {SRC}")
        return SRC
    raise FileNotFoundError(f"No source at {SRC} and no tar at {TAR}")


def fresh_copy(src: Path, logf) -> None:
    if DST.exists():
        log(f"Removing old {DST}", logf)
        shutil.rmtree(DST)
    log(f"Copying {src} -> {DST}", logf)
    shutil.copytree(src, DST)
    for name in ("uid.dat", "session.lock"):
        p = DST / name
        if p.exists():
            p.unlink()


def remap_chunk(chunk) -> tuple[int, int]:
    """Remap one chunk via palette LUT. Returns (palette_entries_changed, cells_changed)."""
    pal = chunk.block_palette
    n = len(pal)
    index_map: dict[int, int] = {}
    entries_changed = 0

    for i in range(n):
        b = pal[i]
        nb = remap_block(b)
        if nb is None or block_unchanged(b, nb):
            index_map[i] = i
            continue
        try:
            ni = pal.get_add_block(nb)
        except Exception:
            # Fallback: try without properties
            try:
                ni = pal.get_add_block(Block(NS, nb.base_name))
            except Exception:
                index_map[i] = i
                continue
        index_map[i] = ni
        if ni != i:
            entries_changed += 1

    if entries_changed == 0:
        return 0, 0

    max_idx = max(max(index_map.keys()), max(index_map.values()))
    lut = np.arange(max_idx + 1, dtype=np.uint32)
    for o, nidx in index_map.items():
        lut[o] = nidx

    cells = 0
    for sy in list(chunk.blocks.sections):
        arr = np.array(chunk.blocks.get_section(sy), copy=True)
        if arr.size == 0:
            continue
        need = int(arr.max()) + 1
        if need > len(lut):
            lut = np.concatenate(
                [lut, np.arange(len(lut), need, dtype=np.uint32)]
            )
        new_arr = lut[arr]
        diff = int(np.sum(new_arr != arr))
        if diff:
            chunk.blocks.add_section(sy, new_arr)
            cells += diff

    if cells:
        chunk.changed = True
    return entries_changed, cells


def sample_arena(level, dim: str) -> Counter:
    counts: Counter = Counter()
    for x in range(-40, 40):
        for z in range(70, 160):
            for y in range(40, 90):
                try:
                    b = level.get_block(x, y, z, dim)
                except Exception:
                    continue
                counts[f"{b.namespace}:{b.base_name}"] += 1
    return counts


def main() -> int:
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with LOG.open("w", encoding="utf-8") as logf:
        try:
            src = ensure_source(logf)
            fresh_copy(src, logf)

            log("Loading level…", logf)
            level = load_level(str(DST))
            dim = "minecraft:overworld"

            # Level name
            try:
                data = level.level_wrapper.root_tag.compound["Data"]
                old_name = data["LevelName"]
                data["LevelName"] = StringTag("Ashen Void")
                log(f"LevelName: {old_name} -> Ashen Void", logf)
            except Exception as e:
                log(f"WARN LevelName set failed: {e}", logf)

            coords = list(level.all_chunk_coords(dim))
            log(f"total chunks {len(coords)}", logf)

            hot = 0
            changed_cells = 0
            changed_entries = 0

            for i, (cx, cz) in enumerate(coords):
                try:
                    chunk = level.get_chunk(cx, cz, dim)
                except (ChunkLoadError, ChunkDoesNotExist):
                    continue
                ent, cells = remap_chunk(chunk)
                if cells:
                    hot += 1
                    changed_cells += cells
                    changed_entries += ent
                    level.put_chunk(chunk, dim)
                if (i + 1) % 200 == 0:
                    log(
                        f"  scanned {i+1}/{len(coords)} hot={hot} changed={changed_cells}",
                        logf,
                    )

            log(
                f"hot chunks: {hot}  palette_entries_touched: {changed_entries}  "
                f"changed_cells: {changed_cells}",
                logf,
            )

            log("Saving…", logf)
            level.save()

            log("Sampling arena (-40..40, y40..90, z70..160)…", logf)
            counts = sample_arena(level, dim)
            log("top-20 blocks near arena:", logf)
            for name, n in counts.most_common(20):
                log(f"  {n:6d}  {name}", logf)

            # Sanity checks
            bad = []
            for name in (
                "universal_minecraft:lava",
                "universal_minecraft:netherrack",
                "universal_minecraft:magma_block",
                "universal_minecraft:fire",
            ):
                if counts.get(name, 0) > 0:
                    bad.append(f"{name}={counts[name]}")
            if bad:
                log(f"WARN still present near arena: {', '.join(bad)}", logf)
            else:
                log("OK: no lava/netherrack/magma/fire near arena", logf)

            good = [
                n
                for n, _ in counts.most_common(20)
                if any(
                    x in n
                    for x in (
                        "end_stone",
                        "purpur",
                        "amethyst",
                        "obsidian",
                        "concrete",
                    )
                )
            ]
            log(f"End-ish names in top-20: {good}", logf)

            level.close()
            log(f"DONE changed={changed_cells} world={DST}", logf)
            return 0
        except Exception:
            log("FATAL:\n" + traceback.format_exc(), logf)
            return 1


if __name__ == "__main__":
    raise SystemExit(main())
