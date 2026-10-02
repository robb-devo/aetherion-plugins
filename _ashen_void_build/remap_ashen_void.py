#!/usr/bin/env python3
"""
Ashen Void builder — COPY of Bloodstone Dungeon with End-themed block remap.
Does NOT touch the live bloodstone world. Layout stays 1:1; only block IDs change.
Lava / magma become air (void pits). A companion plugin kills + respawns at world spawn.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

from amulet import load_level, load_format
from amulet.api.block import Block
from amulet.api.errors import ChunkLoadError, ChunkDoesNotExist
from amulet.api.selection import SelectionBox, SelectionGroup

SRC = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\bloodstone_src\Bloodstone Dungeon World")
DST = Path(r"C:\Users\Robbi\IdeaProjects\_ashen_void_build\ashen_void")

# Universal block -> End palette. Anything not listed stays as-is (blackstone/obsidian already fit).
REMAP: dict[str, str] = {
    # nether stone / crust
    "minecraft:netherrack": "minecraft:end_stone",
    "minecraft:crimson_nylium": "minecraft:end_stone",
    "minecraft:warped_nylium": "minecraft:end_stone",
    "minecraft:basalt": "minecraft:end_stone_bricks",
    "minecraft:smooth_basalt": "minecraft:end_stone",
    "minecraft:polished_basalt": "minecraft:purpur_pillar",
    "minecraft:magma_block": "minecraft:obsidian",
    "minecraft:soul_sand": "minecraft:end_stone",
    "minecraft:soul_soil": "minecraft:end_stone",
    "minecraft:glowstone": "minecraft:purpur_block",
    "minecraft:shroomlight": "minecraft:end_rod",
    # nether bricks
    "minecraft:nether_bricks": "minecraft:purpur_block",
    "minecraft:cracked_nether_bricks": "minecraft:purpur_block",
    "minecraft:chiseled_nether_bricks": "minecraft:purpur_pillar",
    "minecraft:red_nether_bricks": "minecraft:purpur_block",
    "minecraft:nether_brick_slab": "minecraft:purpur_slab",
    "minecraft:nether_brick_stairs": "minecraft:purpur_stairs",
    "minecraft:nether_brick_wall": "minecraft:purpur_pillar",
    "minecraft:nether_brick_fence": "minecraft:iron_bars",
    "minecraft:red_nether_brick_slab": "minecraft:purpur_slab",
    "minecraft:red_nether_brick_stairs": "minecraft:purpur_stairs",
    "minecraft:red_nether_brick_wall": "minecraft:purpur_pillar",
    # blackstone family → keep dark, slight end tint via polished / bricks
    "minecraft:gilded_blackstone": "minecraft:crying_obsidian",
    # crimson / warped wood → chorus / end materials
    "minecraft:crimson_stem": "minecraft:purpur_pillar",
    "minecraft:crimson_hyphae": "minecraft:purpur_pillar",
    "minecraft:stripped_crimson_stem": "minecraft:purpur_pillar",
    "minecraft:stripped_crimson_hyphae": "minecraft:purpur_pillar",
    "minecraft:crimson_planks": "minecraft:purpur_block",
    "minecraft:crimson_slab": "minecraft:purpur_slab",
    "minecraft:crimson_stairs": "minecraft:purpur_stairs",
    "minecraft:crimson_fence": "minecraft:iron_bars",
    "minecraft:crimson_fence_gate": "minecraft:purpur_block",
    "minecraft:crimson_door": "minecraft:iron_door",
    "minecraft:crimson_trapdoor": "minecraft:iron_trapdoor",
    "minecraft:crimson_button": "minecraft:polished_blackstone_button",
    "minecraft:crimson_pressure_plate": "minecraft:polished_blackstone_pressure_plate",
    "minecraft:crimson_sign": "minecraft:oak_sign",
    "minecraft:crimson_wall_sign": "minecraft:oak_wall_sign",
    "minecraft:crimson_hanging_sign": "minecraft:oak_hanging_sign",
    "minecraft:warped_stem": "minecraft:purpur_pillar",
    "minecraft:warped_hyphae": "minecraft:purpur_pillar",
    "minecraft:stripped_warped_stem": "minecraft:purpur_pillar",
    "minecraft:stripped_warped_hyphae": "minecraft:purpur_pillar",
    "minecraft:warped_planks": "minecraft:purpur_block",
    "minecraft:warped_slab": "minecraft:purpur_slab",
    "minecraft:warped_stairs": "minecraft:purpur_stairs",
    "minecraft:warped_fence": "minecraft:iron_bars",
    "minecraft:warped_fence_gate": "minecraft:purpur_block",
    "minecraft:warped_door": "minecraft:iron_door",
    "minecraft:warped_trapdoor": "minecraft:iron_trapdoor",
    # flora
    "minecraft:crimson_fungus": "minecraft:chorus_flower",
    "minecraft:warped_fungus": "minecraft:chorus_flower",
    "minecraft:crimson_roots": "minecraft:chorus_plant",
    "minecraft:warped_roots": "minecraft:chorus_plant",
    "minecraft:nether_sprouts": "minecraft:air",
    "minecraft:weeping_vines": "minecraft:air",
    "minecraft:weeping_vines_plant": "minecraft:air",
    "minecraft:twisting_vines": "minecraft:chorus_plant",
    "minecraft:twisting_vines_plant": "minecraft:chorus_plant",
    "minecraft:nether_wart": "minecraft:air",
    "minecraft:nether_wart_block": "minecraft:purpur_block",
    "minecraft:warped_wart_block": "minecraft:purpur_block",
    # fire / lava → void
    "minecraft:lava": "minecraft:air",
    "minecraft:fire": "minecraft:air",
    "minecraft:soul_fire": "minecraft:air",
    "minecraft:lava_cauldron": "minecraft:cauldron",
    # red / orange accents → purple / black
    "minecraft:red_concrete": "minecraft:purple_concrete",
    "minecraft:orange_concrete": "minecraft:purple_concrete",
    "minecraft:red_concrete_powder": "minecraft:purple_concrete_powder",
    "minecraft:orange_concrete_powder": "minecraft:purple_concrete_powder",
    "minecraft:red_terracotta": "minecraft:purple_terracotta",
    "minecraft:orange_terracotta": "minecraft:purple_terracotta",
    "minecraft:red_stained_glass": "minecraft:purple_stained_glass",
    "minecraft:orange_stained_glass": "minecraft:purple_stained_glass",
    "minecraft:red_stained_glass_pane": "minecraft:purple_stained_glass_pane",
    "minecraft:orange_stained_glass_pane": "minecraft:purple_stained_glass_pane",
    "minecraft:red_wool": "minecraft:purple_wool",
    "minecraft:orange_wool": "minecraft:purple_wool",
    "minecraft:red_carpet": "minecraft:purple_carpet",
    "minecraft:orange_carpet": "minecraft:purple_carpet",
    "minecraft:redstone_block": "minecraft:amethyst_block",
    "minecraft:redstone_lamp": "minecraft:sea_lantern",
    "minecraft:jack_o_lantern": "minecraft:sea_lantern",
    "minecraft:lantern": "minecraft:soul_lantern",
    "minecraft:campfire": "minecraft:soul_campfire",
    "minecraft:torch": "minecraft:end_rod",
    "minecraft:wall_torch": "minecraft:end_rod",
    "minecraft:soul_torch": "minecraft:end_rod",
    "minecraft:soul_wall_torch": "minecraft:end_rod",
    # quartz accents stay / slight end
    "minecraft:quartz_block": "minecraft:purpur_block",
    "minecraft:smooth_quartz": "minecraft:purpur_block",
    "minecraft:quartz_pillar": "minecraft:purpur_pillar",
    "minecraft:quartz_stairs": "minecraft:purpur_stairs",
    "minecraft:quartz_slab": "minecraft:purpur_slab",
    # blood / red mushrooms
    "minecraft:red_mushroom": "minecraft:chorus_flower",
    "minecraft:red_mushroom_block": "minecraft:purpur_block",
    "minecraft:mushroom_stem": "minecraft:purpur_pillar",
}


def block_key(block: Block) -> str:
    ns = block.namespace or "minecraft"
    return f"{ns}:{block.base_name}"


def remap_block(block: Block, platform: str, version: tuple) -> Block:
    key = block_key(block)
    target = REMAP.get(key)
    if not target:
        return block
    # Keep facing/axis/etc when the dest supports them — amulet will drop invalid props.
    props = dict(block.properties) if block.properties else {}
    try:
        return Block.from_string_blockstate(f"{target}{''}")  # type: ignore
    except Exception:
        pass
    # Fallback: construct without properties
    ns, name = target.split(":", 1)
    try:
        return Block(ns, name, props)
    except Exception:
        return Block(ns, name, {})


def main() -> int:
    if not SRC.exists():
        print("SRC missing", SRC, file=sys.stderr)
        return 1
    if DST.exists():
        shutil.rmtree(DST)
    shutil.copytree(SRC, DST)
    print("Copied world ->", DST)

    level = load_level(str(DST))
    dim = "minecraft:overworld"
    changed = 0
    scanned = 0
    try:
        chunk_coords = list(level.all_chunk_coords(dim))
        print(f"Chunks: {len(chunk_coords)}")
        for cx, cz in chunk_coords:
            try:
                chunk = level.get_chunk(cx, cz, dim)
            except (ChunkLoadError, ChunkDoesNotExist):
                continue
            # iterate block palette via chunk.blocks
            # amulet UniversalChunk: sections
            for x in range(16):
                for z in range(16):
                    # Y range from chunk bounds
                    for y in range(-64, 320):
                        try:
                            block, _ = chunk.get_block(x, y, z) if hasattr(chunk, "get_block") else (None, None)
                        except Exception:
                            # Universal chunk API
                            try:
                                block = level.get_block(cx * 16 + x, y, cz * 16 + z, dim)
                            except Exception:
                                continue
                            scanned += 1
                            new_block = remap_block(block, level.level_wrapper.platform, level.level_wrapper.version)
                            if new_block is block or block_key(new_block) == block_key(block):
                                continue
                            level.set_block(cx * 16 + x, y, cz * 16 + z, dim, new_block)
                            changed += 1
                            continue
                        if block is None:
                            continue
                        scanned += 1
                        new_block = remap_block(block, level.level_wrapper.platform, level.level_wrapper.version)
                        if block_key(new_block) == block_key(block):
                            continue
                        # set via level for consistency
                        level.set_block(cx * 16 + x, y, cz * 16 + z, dim, new_block)
                        changed += 1
            print(f"  chunk {cx},{cz} done (changed so far {changed})")
        level.save()
        print(f"DONE scanned={scanned} changed={changed}")
    finally:
        level.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
