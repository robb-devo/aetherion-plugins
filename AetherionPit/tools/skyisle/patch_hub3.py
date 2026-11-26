"""Round 3: patch the hub's AetherionPit config (and the WorldGuard hub_safe box) from hub3.json.

  python3 patch_hub3.py <hub server dir> <hub3.json>

Only the named top-level keys of AetherionPit/config.yml are replaced; everything else stays as it is.
"""
import json
import re
import sys

from patch_hub import replace_block, loc, box


def flow(key, value):
    """a top-level key with a JSON flow value (JSON is valid YAML)"""
    if isinstance(value, list):
        out = key + ":\n"
        for item in value:
            out += "- " + json.dumps(item, ensure_ascii=False) + "\n"
        return out
    return key + ": " + json.dumps(value, ensure_ascii=False) + "\n"


def floaters(info):
    p = info["gate_pit"]
    b = info["prop_modpack"]
    rows = [
        {"type": "model", "code": 8106, "x": -13.5, "y": 71.2, "z": -4.5, "scale": 1.25, "bob": 0.3, "spin": 45, "tilt": 0},
        {"type": "model", "code": 8106, "x": -13.5, "y": 71.2, "z": 3.5, "scale": 1.25, "bob": 0.3, "spin": -45, "tilt": 0},
        {"type": "item", "material": "NETHERITE_SWORD", "x": p["label"][0] + 2.0, "y": p["label"][1] + 1.8, "z": 0.5,
         "scale": 1.4, "bob": 0.2, "spin": 90, "tilt": 0},
        {"type": "item", "material": "ENCHANTED_BOOK", "x": 8.5, "y": 66.7, "z": -17.5, "scale": 0.6, "bob": 0.15,
         "spin": 90, "tilt": 0},
        {"type": "block", "material": "LANTERN", "x": 33.5, "y": 67.0, "z": 9.5, "scale": 0.5, "bob": 0.25, "spin": 30, "tilt": 0},
        {"type": "block", "material": "LANTERN", "x": 37.5, "y": 67.6, "z": 14.5, "scale": 0.5, "bob": 0.25, "spin": -30, "tilt": 0},
        {"type": "block", "material": "LANTERN", "x": 31.5, "y": 67.3, "z": 15.5, "scale": 0.5, "bob": 0.25, "spin": 30, "tilt": 0},
    ]
    return flow("floaters", rows)


def patch_pit(path, info):
    t = open(path, encoding="utf-8").read()
    sx, sy, sz, syaw, spitch = info["spawn"]
    t = replace_block(t, "spawn", loc("spawn", sx, sy, sz, syaw, spitch))
    x0, z0, x1, z1 = info["island_box"]
    t = replace_block(t, "safe-zone", "safe-zone:\n  world: world\n  min-x: %s\n  max-x: %s\n  min-z: %s\n  max-z: %s\n"
                      % (float(x0 - 9), float(x1 + 9), float(z0 - 9), float(z1 + 9)))
    wx, wy, wz = info["welcome"]
    t = replace_block(t, "welcome-board", loc("welcome-board", wx, wy, wz, 90.0))
    px, py, pz, pyaw = info["pit_entrance"]
    t = replace_block(t, "pit-entrance", loc("pit-entrance", px, py, pz, pyaw))
    bx, by, bz, byaw = info["pit_board"]
    t = replace_block(t, "pit-board", loc("pit-board", bx, by, bz, byaw))
    t = replace_block(t, "npcs-enabled", "npcs-enabled: false\n")
    portals = "portals:\n  enabled: true\n"
    for key in ("aetherion", "pit", "home"):
        portals += box(key, info["gate_" + key])
    t = replace_block(t, "portals", portals)
    props = "props:\n"
    props += box("discord", info["prop_discord"]) + "    blocks: [BELL]\n"
    props += box("modpack", info["prop_modpack"]) + "    blocks: [LECTERN, BOOKSHELF]\n"
    stall = info.get("shop_stall", {"label": [27.5, 69.4, 18.5]})
    props += box("shop", {"min": [8, 64, 18], "max": [46, 90, 46], "label": stall["label"]})
    props += ("    blocks: [BARREL, SMITHING_TABLE, LECTERN, ENCHANTING_TABLE, BREWING_STAND, CHEST, CRAFTING_TABLE,"
              " GRINDSTONE, ANVIL, CARTOGRAPHY_TABLE, FLETCHING_TABLE, LOOM]\n")
    t = replace_block(t, "props", props)
    t = replace_block(t, "void-catch", "void-catch:\n  enabled: true\n  y: 0.0\n")
    dk = info["duck"]
    t = replace_block(t, "duck", "duck:\n  enabled: true\n" + box("x", dk).split("\n", 1)[1].replace("    ", "  "))
    t = replace_block(t, "floaters", floaters(info))
    t = replace_block(t, "decor", flow("decor", info["decor"]))
    t = replace_block(t, "parkour", flow("parkour", info["parkour"]))
    if "ducklings" in info:
        t = replace_block(t, "ducklings", flow("ducklings", info["ducklings"]))
    open(path, "w", encoding="utf-8").write(t)


if __name__ == "__main__":
    H, info_path = sys.argv[1], sys.argv[2]
    info = json.load(open(info_path))
    patch_pit(H + "/plugins/AetherionPit/config.yml", info)
    x0, z0, x1, z1 = info["island_box"]
    f = H + "/plugins/WorldGuard/worlds/world/regions.yml"
    t = open(f, encoding="utf-8").read()
    i = t.index("hub_safe:")
    head, tail = t[:i], t[i:]
    tail = re.sub(r"min: \{x: -?\d+, y: -64, z: -?\d+\}", "min: {x: %d, y: -64, z: %d}" % (x0 - 9, z0 - 9), tail, count=1)
    tail = re.sub(r"max: \{x: -?\d+, y: 320, z: -?\d+\}", "max: {x: %d, y: 320, z: %d}" % (x1 + 9, z1 + 9), tail, count=1)
    open(f, "w", encoding="utf-8").write(head + tail)
    print("patched")
