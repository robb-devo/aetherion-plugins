#!/bin/bash
set -euo pipefail
HUB=/var/opt/minecraft/crafty/servers/f3708762-f89b-4302-9d09-22071e139e9e
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
SHARED=/var/opt/minecraft/crafty/shared
OLD_BE=/tmp/BossEngine.jar   # 590038 = pre-opus Hollow Sun build from earlier today
ROBBI=c46afc0c-488b-430f-83a7-98f8a5df3ac5

echo "=== transfer dir before ==="
ls -la "$SHARED/transfer/" || true

# 1) Quarantine any pending transfers (esp. hub→* after our mess)
mkdir -p /tmp/transfer-quarantine-20260926
if [ -d "$SHARED/transfer" ]; then
  cp -a "$SHARED/transfer/." /tmp/transfer-quarantine-20260926/ 2>/dev/null || true
  # remove active transfer payloads so empty hub inv cannot apply on next join
  find "$SHARED/transfer" -type f -delete
  echo "cleared shared/transfer (quarantined to /tmp/transfer-quarantine-20260926)"
fi

# 2) Restore Hub BossEngine to pre-opus jar
if [ ! -f "$OLD_BE" ] || [ "$(stat -c%s "$OLD_BE")" -lt 500000 ]; then
  echo "FATAL: missing pre-opus BossEngine at $OLD_BE"
  ls -la /tmp/BossEngine* || true
  exit 1
fi
install -o crafty -g crafty -m 664 "$OLD_BE" "$HUB/plugins/BossEngine.jar"
rm -f "$HUB/plugins/.paper-remapped"/BossEngine*.jar 2>/dev/null || true
echo "Hub BossEngine restored: $(stat -c%s "$HUB/plugins/BossEngine.jar") bytes"

# 3) Restore dungeon_aetherion.yml from that jar (not Opus rewrite)
tmpdir=$(mktemp -d)
cd "$tmpdir"
jar xf "$OLD_BE" bosses/dungeon_aetherion.yml 2>/dev/null || unzip -qo "$OLD_BE" bosses/dungeon_aetherion.yml
if [ -f bosses/dungeon_aetherion.yml ]; then
  install -o crafty -g crafty -m 664 bosses/dungeon_aetherion.yml \
    "$HUB/plugins/BossEngine/bosses/dungeon_aetherion.yml"
  echo "Hub dungeon_aetherion.yml restored from pre-opus jar:"
  head -n 4 "$HUB/plugins/BossEngine/bosses/dungeon_aetherion.yml"
else
  echo "WARN: could not extract dungeon_aetherion.yml from old jar"
fi
rm -rf "$tmpdir"

# 4) Ensure Hub Core keeps hub-inv isolation
python3 - <<'PY'
from pathlib import Path
p = Path("/var/opt/minecraft/crafty/servers/f3708762-f89b-4302-9d09-22071e139e9e/plugins/AetherionCore/config.yml")
text = p.read_text()
if "transfer-inventory-from-hub:" not in text:
    text += "\nnetwork:\n  this-server: hub\n  transfer-inventory-from-hub: false\n  ignore-inventory-from: []\n"
    p.write_text(text)
    print("Hub Core: appended network isolation")
else:
    # force false
    import re
    text2 = re.sub(r"(transfer-inventory-from-hub:\s*)\w+", r"\1false", text)
    if "this-server:" not in text2:
        text2 = text2.replace("network:", "network:\n  this-server: hub", 1)
    p.write_text(text2)
    print("Hub Core: transfer-inventory-from-hub forced false")
print(p.read_text()[-400:])
PY

# 5) Confirm Robbi MMO-R playerdata still healthy (do NOT overwrite with hub empty)
#    (MMO-R Core/config left alone — defaults already omit hub inventory)
ls -la "$MMOR/world/playerdata/${ROBBI}.dat" "$MMOR/world/playerdata/${ROBBI}.dat_old"
echo "MMO-R playerdata size=$(stat -c%s "$MMOR/world/playerdata/${ROBBI}.dat") (should stay ~11k, NOT hub ~3k)"

# 7) Restart ONLY Hub
echo "=== RESTART HUB ONLY ==="
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [ "$cwd" = "$HUB" ]; then
    echo "kill hub $p"
    kill -9 "$p" 2>/dev/null || true
  fi
done
sleep 2
MARK=$(date -Is)
printf '\n=== START %s ===\n' "$MARK" >> "$HUB/logs/latest.log"
sudo -u crafty bash -c "cd '$HUB' && nohup java -Xms1G -Xmx4G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
for i in $(seq 1 60); do
  if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$HUB/logs/latest.log"; then
    echo HUB_OK
    grep -E 'Enabling BossEngine|Done \(' "$HUB/logs/latest.log" | tail -n 4
    ls -la "$HUB/plugins/BossEngine.jar"
    exit 0
  fi
  sleep 2
done
echo HUB_TIMEOUT
exit 1
