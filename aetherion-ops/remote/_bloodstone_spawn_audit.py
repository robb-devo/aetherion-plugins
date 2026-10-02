from pathlib import Path
import re

mmor = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383")

# Hub config bloodstone
hub = (mmor / "plugins/AetherionHub/config.yml").read_text(encoding="utf-8", errors="replace")
print("=== hub bloodstone ===")
m = re.search(r"(?m)^  bloodstone:\n(?:    .*\n)*", hub)
print(m.group(0) if m else "MISSING")

# MV spawn
mv = (mmor / "plugins/Multiverse-Core/worlds.yml").read_text(encoding="utf-8", errors="replace")
m = re.search(r"(?m)^minecraft:bloodstone:\n(?:  .*\n)*", mv)
print("=== mv bloodstone block head ===")
if m:
    block = m.group(0)
    sm = re.search(r"(?m)^  spawn-location:\n(?:    .*\n)*", block)
    print(sm.group(0) if sm else "no spawn-location")

# hollow sun spawner
sp = (mmor / "plugins/BossEngine/spawners.yml").read_text(encoding="utf-8", errors="replace")
m = re.search(r"(?m)^  hollow_sun_home:\n(?:    .*\n)*", sp)
print("=== hollow_sun_home ===")
print(m.group(0) if m else "MISSING")

# recent log mentions of bloodstone coords
log = (mmor / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
for line in log.splitlines():
    if "bloodstone" in line.lower() and any(x in line.lower() for x in ("spawn", "set", "anchor", "teleport", "location")):
        print("LOG", line[-200:])
