from pathlib import Path
import re
import shutil

cfg = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/AetherionHub/config.yml")
bak = Path(str(cfg) + ".bak-pre-bloodstone")
text = cfg.read_text(encoding="utf-8", errors="replace")

def spawn_keys(t):
    m = re.search(r"(?m)^spawns:\n(.*?)(?=\n[a-zA-Z0-9_-]+:|\Z)", t, re.S)
    if not m:
        return [], ""
    block = m.group(1)
    keys = re.findall(r"(?m)^  ([a-z0-9_-]+):", block)
    return keys, block

keys, block = spawn_keys(text)
print("LIVE spawn count:", len(keys))
for k in keys:
    sub = re.search(rf"(?m)^  {re.escape(k)}:\n((?:    .*\n)*)", block)
    has_loc = bool(sub and "location:" in sub.group(1))
    print(f"  {k}: loc={'YES' if has_loc else 'NO'}")

print("--- bloodstone present?", "bloodstone:" in text)
if bak.exists():
    bk, bb = spawn_keys(bak.read_text(encoding="utf-8", errors="replace"))
    print("BAK spawn count:", len(bk))
    live_set = set(keys)
    bak_set = set(bk)
    print("MISSING vs bak:", sorted(bak_set - live_set))
    print("EXTRA vs bak:", sorted(live_set - bak_set))
else:
    print("NO bak-pre-bloodstone")

# also list other bak files
hub = cfg.parent
for p in sorted(hub.glob("config.yml.bak*")):
    kk, _ = spawn_keys(p.read_text(encoding="utf-8", errors="replace"))
    print(f"  {p.name}: {len(kk)} spawns")
