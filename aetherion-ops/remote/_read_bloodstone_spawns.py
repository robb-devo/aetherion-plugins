import os
from pathlib import Path
import gzip

mmor = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383")

# java pid
for p in Path("/proc").iterdir():
    if not p.name.isdigit():
        continue
    try:
        if os.readlink(p / "cwd") == str(mmor):
            cmd = (p / "cmdline").read_bytes()
            if b"java" in cmd:
                print("pid", p.name)
                break
    except Exception:
        pass

# level.dat spawn via naive TAG_Int after SpawnX\0
raw = (mmor / "bloodstone" / "level.dat").read_bytes()
try:
    data = gzip.decompress(raw)
except Exception:
    data = raw

def find_int(tag: bytes):
    idx = data.find(tag + b"\x00")
    if idx < 0:
        return None
    # after name null comes value for named tag inside compound - for SpawnX it's TAG_Int (3) then 4 bytes BE
    # structure when inside compound: type(1) + name_len(2) + name + payload
    # We're searching for name bytes which appear after type+len. So after name\0 is payload.
    start = idx + len(tag) + 1
    return int.from_bytes(data[start:start+4], "big", signed=True)

print("level SpawnX/Y/Z", find_int(b"SpawnX"), find_int(b"SpawnY"), find_int(b"SpawnZ"))

# MV file
import re
mv = (mmor / "plugins/Multiverse-Core/worlds.yml").read_text(encoding="utf-8", errors="replace")
m = re.search(r"minecraft:bloodstone:.*?spawn-location:\n((?:    .*\n)+)", mv, re.S)
print("mv spawn block:\n", m.group(1) if m else None)

sp = (mmor / "plugins/BossEngine/spawners.yml").read_text(encoding="utf-8", errors="replace")
m = re.search(r"(?m)^  hollow_sun_home:\n(?:    .*\n)*", sp)
print("boss home:\n", m.group(0) if m else None)
