#!/bin/bash
# Fix ashen_void Multiverse misc.spawn so arrows/projectiles can exist (was false → Skuldugery broken).
set -euo pipefail
MV=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/Multiverse-Core/worlds.yml
python3 - <<'PY'
from pathlib import Path
p = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/Multiverse-Core/worlds.yml")
text = p.read_text(encoding="utf-8")
key = "minecraft:ashen_void:"
idx = text.find(key)
if idx < 0:
    print("ashen_void missing")
    raise SystemExit(1)
# next world key after this one
rest = text[idx + len(key):]
next_idx = rest.find("\nminecraft:")
block = rest if next_idx < 0 else rest[:next_idx]
fixed = block.replace("    misc:\n      spawn: false", "    misc:\n      spawn: true", 1)
if fixed == block:
    # also try already-true
    if "misc:\n      spawn: true" in block or "misc:\n      spawn: 'true'" in block:
        print("misc.spawn already true")
    else:
        print("WARN could not locate misc.spawn in ashen_void block")
        print(block[block.find("spawning"):block.find("spawning")+500] if "spawning" in block else "no spawning")
else:
    text = text[:idx + len(key)] + fixed + ("" if next_idx < 0 else rest[next_idx:])
    p.write_text(text, encoding="utf-8")
    print("misc.spawn set true for ashen_void")
PY
chown crafty:crafty "$MV"
echo MV_OK
