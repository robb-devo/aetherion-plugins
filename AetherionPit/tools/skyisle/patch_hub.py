"""Patch the hub's plugin YAMLs for the sky-isle world (only the named keys change, the rest stays).

  python3 patch_hub.py <hub server dir> <skyisle.json>
"""
import json
import sys


def replace_block(text, key, block):
    lines = text.split("\n")
    out, i, found = [], 0, False
    while i < len(lines):
        if lines[i].startswith(key + ":"):
            found = True
            out.extend(block.rstrip("\n").split("\n"))
            i += 1
            while i < len(lines) and (lines[i].startswith(" ") or lines[i].startswith("-")):
                i += 1
            continue
        out.append(lines[i])
        i += 1
    if not found:
        if out and out[-1] == "":
            out.pop()
        out.extend(block.rstrip("\n").split("\n"))
        out.append("")
    return "\n".join(out)


def loc(key, x, y, z, yaw=0.0, pitch=0.0):
    return "%s:\n  world: world\n  x: %s\n  y: %s\n  z: %s\n  yaw: %s\n  pitch: %s\n" % (
        key, float(x), float(y), float(z), float(yaw), float(pitch))


def box(name, g, indent="  "):
    return ("%s%s:\n%s  world: world\n%s  min: [%d, %d, %d]\n%s  max: [%d, %d, %d]\n%s  label: [%s, %s, %s]\n" % (
        indent, name, indent, indent, *g["min"], indent, *g["max"], indent, *g["label"]))


def patch_pit(path, info):
    t = open(path, encoding="utf-8").read()
    sx, sy, sz, syaw = info["spawn"]
    t = replace_block(t, "spawn", loc("spawn", sx, sy, sz, syaw))
    mx0, mz0, mx1, mz1 = info["main_box"]
    t = replace_block(t, "safe-zone", "safe-zone:\n  world: world\n  min-x: %s\n  max-x: %s\n  min-z: %s\n  max-z: %s\n"
                      % (float(mx0 - 9), float(mx1 + 8), float(mz0 - 10), float(mz1 + 10)))
    t = replace_block(t, "welcome-board", loc("welcome-board", 3.5, 66.6, -7.5, 90.0))
    px, py, pz, pyaw = info["pit_entrance"]
    t = replace_block(t, "pit-entrance", loc("pit-entrance", px, py, pz, pyaw))
    t = replace_block(t, "pit-board", loc("pit-board", px - 2.0, py + 2.5, pz - 7.0, pyaw))
    t = replace_block(t, "npcs-enabled", "npcs-enabled: false\n")
    portals = "portals:\n  enabled: true\n"
    for key in ("aetherion", "pit", "home", "discord", "modpack", "shop"):
        portals += box(key, info["gate_" + key])
    t = replace_block(t, "portals", portals)
    t = replace_block(t, "void-catch", "void-catch:\n  enabled: true\n  y: 0.0\n")
    d = info["duck"]
    t = replace_block(t, "duck", "duck:\n  enabled: true\n" + box("x", d).split("\n", 1)[1].replace("    ", "  "))
    open(path, "w", encoding="utf-8").write(t)


def patch_lines(path, pairs):
    t = open(path, encoding="utf-8").read()
    for a, b in pairs:
        if a not in t:
            print("WARN not found in", path, ":", a.strip())
        t = t.replace(a, b, 1)
    open(path, "w", encoding="utf-8").write(t)


if __name__ == "__main__":
    H, info_path = sys.argv[1], sys.argv[2]
    info = json.load(open(info_path))
    patch_pit(H + "/plugins/AetherionPit/config.yml", info)
    patch_lines(H + "/plugins/AetherionHub/config.yml", [
        ("\nrespawn-at-selected-spawn: true", "\nrespawn-at-selected-spawn: false"),
        ("\njoin-at-selected-spawn: true", "\njoin-at-selected-spawn: false"),
        ("island-pads:\n  enabled: true", "island-pads:\n  enabled: false"),
    ])
    mx0, mz0, mx1, mz1 = info["main_box"]
    patch_lines(H + "/plugins/WorldGuard/worlds/world/regions.yml", [
        ("min: {x: -91, y: 48, z: -84}", "min: {x: -110, y: -64, z: -110}"),
        ("max: {x: 83, y: 150, z: 83}", "max: {x: 200, y: 320, z: 110}"),
        ("min: {x: -78, y: -64, z: -60}", "min: {x: %d, y: -64, z: %d}" % (mx0 - 9, mz0 - 10)),
        ("max: {x: 42, y: 320, z: 60}", "max: {x: %d, y: 320, z: %d}" % (mx1 + 8, mz1 + 10)),
    ])
    open(H + "/plugins/FancyNpcs/npcs.yml", "w", encoding="utf-8").write("npcs: {}\n")
    print("patched")
