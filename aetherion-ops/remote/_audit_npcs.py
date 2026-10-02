from pathlib import Path
import re

base = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins")
npc = base / "FancyNpcs" / "npcs.yml"
print("LIVE size", npc.stat().st_size)
for p in sorted((base / "FancyNpcs").glob("npcs.yml*")):
    print(p.name, p.stat().st_size)

def count(path: Path):
    t = path.read_text(encoding="utf-8", errors="replace")
    ids = re.findall(r"(?m)^  ([0-9a-fA-F-]{36}):", t)
    names = re.findall(r"(?m)^    name:\s*(.+)$", t)
    return ids, names, t

ids, names, text = count(npc)
print("LIVE uuids", len(ids), "names", names)

bak = Path(str(npc) + ".bak-pre-bloodstone-gate")
if bak.exists():
    bids, bnames, bt = count(bak)
    print("BAK uuids", len(bids), "names", bnames)
    print("MISSING", sorted(set(bids) - set(ids)))
    print("EXTRA", sorted(set(ids) - set(bids)))

# search other backups / older copies
cands = list(base.rglob("npcs.yml*")) + list(Path("/var/opt/minecraft/crafty").rglob("*npcs*.yml*"))
seen = set()
for p in cands:
    try:
        sz = p.stat().st_size
    except Exception:
        continue
    key = (str(p), sz)
    if key in seen:
        continue
    seen.add(key)
    if sz > 5000 or "FancyNpcs" in str(p) or "bak" in p.name.lower():
        print(f"CAND {sz:8d} {p}")

# BossEngine spawners summary
sp = (base / "BossEngine" / "spawners.yml").read_text(encoding="utf-8", errors="replace")
print("spawners SET_SPAWN homes enabled?")
cur = None
d = {}
homes = []
for line in sp.splitlines():
    m = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
    if m:
        if cur:
            homes.append((cur, d))
        cur = m.group(1)
        d = {}
        continue
    if cur and ":" in line and not line.strip().startswith("#"):
        k, v = line.strip().split(":", 1)
        d[k.strip()] = v.strip()
if cur:
    homes.append((cur, d))
for name, d in homes:
    print(f"  {name}: en={d.get('enabled')} {d.get('world')} {d.get('x')},{d.get('y')},{d.get('z')}")
