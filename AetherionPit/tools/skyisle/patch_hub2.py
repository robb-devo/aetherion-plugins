"""Round 2: patch the hub's YAMLs for the smaller elven island (only the named keys change).

  python3 patch_hub2.py <hub server dir> <hub2.json>
"""
import json
import re
import sys

from patch_hub import replace_block, loc, box


def floaters(info):
    a = info["gate_aetherion"]
    p = info["gate_pit"]
    b = info["prop_modpack"]
    rows = [
        dict(type="block", material="AMETHYST_BLOCK", x=-12.5, y=70.6, z=-5.5, scale=0.7, bob=0.3, spin=90, tilt=35),
        dict(type="block", material="AMETHYST_BLOCK", x=-12.5, y=70.6, z=5.5, scale=0.7, bob=0.3, spin=-90, tilt=35),
        dict(type="item", material="NETHERITE_SWORD", x=p["label"][0] + 2.0, y=p["label"][1] + 1.6, z=0.5,
             scale=1.4, bob=0.2, spin=90, tilt=0),
        dict(type="item", material="ENCHANTED_BOOK", x=b["label"][0], y=b["label"][1] - 0.8, z=b["label"][2],
             scale=0.6, bob=0.15, spin=90, tilt=0),
        dict(type="block", material="LANTERN", x=33.5, y=67.0, z=9.5, scale=0.5, bob=0.25, spin=30, tilt=0),
        dict(type="block", material="LANTERN", x=37.5, y=67.6, z=14.5, scale=0.5, bob=0.25, spin=-30, tilt=0),
        dict(type="block", material="LANTERN", x=31.5, y=67.3, z=15.5, scale=0.5, bob=0.25, spin=30, tilt=0),
        dict(type="item", material="AMETHYST_SHARD", x=a["label"][0] - 1.0, y=a["label"][1] + 1.4, z=a["label"][2],
             scale=1.0, bob=0.2, spin=90, tilt=0),
    ]
    out = "floaters:\n"
    for r in rows:
        out += "- {%s}\n" % ", ".join("%s: %s" % (k, v) for k, v in r.items())
    return out


def patch_pit(path, info):
    t = open(path, encoding="utf-8").read()
    sx, sy, sz, syaw, spitch = info["spawn"]
    t = replace_block(t, "spawn", loc("spawn", sx, sy, sz, syaw, spitch))
    x0, z0, x1, z1 = info["island_box"]
    t = replace_block(t, "safe-zone", "safe-zone:\n  world: world\n  min-x: %s\n  max-x: %s\n  min-z: %s\n  max-z: %s\n"
                      % (float(x0 - 9), float(x1 + 9), float(z0 - 9), float(z1 + 9)))
    t = replace_block(t, "welcome-board", loc("welcome-board", 18.5, 66.3, -5.5, 90.0))
    px, py, pz, pyaw = info["pit_entrance"]
    t = replace_block(t, "pit-entrance", loc("pit-entrance", px, py, pz, pyaw))
    t = replace_block(t, "pit-board", loc("pit-board", px - 2.0, py + 2.5, pz - 7.0, pyaw))
    t = replace_block(t, "npcs-enabled", "npcs-enabled: false\n")
    portals = "portals:\n  enabled: true\n"
    for key in ("aetherion", "pit", "home"):
        portals += box(key, info["gate_" + key])
    t = replace_block(t, "portals", portals)
    props = "props:\n"
    d = dict(info["prop_discord"])
    d["label"] = [d["label"][0], d["label"][1] + 0.7, d["label"][2]]
    props += box("discord", d) + "    blocks: [BELL]\n"
    m = dict(info["prop_modpack"])
    m["label"] = [m["label"][0], m["label"][1] + 0.6, m["label"][2]]
    props += box("modpack", m) + "    blocks: [LECTERN, BOOKSHELF]\n"
    props += box("shop", {"min": [8, 64, 20], "max": [46, 90, 46], "label": [27.5, 68.6, 18.5]})
    props += ("    blocks: [BARREL, SMITHING_TABLE, LECTERN, ENCHANTING_TABLE, BREWING_STAND, CHEST, CRAFTING_TABLE,"
              " GRINDSTONE, ANVIL, CARTOGRAPHY_TABLE, FLETCHING_TABLE, LOOM]\n")
    t = replace_block(t, "props", props)
    t = replace_block(t, "void-catch", "void-catch:\n  enabled: true\n  y: 0.0\n")
    dk = info["duck"]
    t = replace_block(t, "duck", "duck:\n  enabled: true\n" + box("x", dk).split("\n", 1)[1].replace("    ", "  "))
    t = replace_block(t, "floaters", floaters(info))
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
