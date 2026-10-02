#!/bin/bash
# One-shot: persist bloodstone pads, deploy jar, graceful restart MMO-R.
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
PLUG="$MMOR/plugins"

# --- persist Hollow Sun boss home (already live; rewrite to be sure) ---
python3 - <<'PY'
from pathlib import Path
import re
sp = Path("/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/BossEngine/spawners.yml")
text = sp.read_text(encoding="utf-8")
block = """  hollow_sun_home:
    enabled: true
    boss: hollow_sun
    type: STATIONARY
    world: bloodstone
    x: 0.5
    y: 63.0
    z: 115.5
    yaw: 0.0
    pitch: 0.0
    interval: 5m
    max-instances: 1
    leash-radius: 36
    leash-action: TELEPORT
"""
pat = re.compile(r"(?m)^  hollow_sun_home:\n(?:    .*\n)*")
if pat.search(text):
    text = pat.sub(block + ("\n" if not block.endswith("\n") else ""), text, count=1)
else:
    text = text.rstrip() + "\n\n" + block
sp.write_text(text, encoding="utf-8")
print("boss home locked")
PY
chown crafty:crafty "$PLUG/BossEngine/spawners.yml"

# --- Essentials warp (was y=200 unsafe) → arena player pad ---
mkdir -p "$PLUG/Essentials/warps"
cat > "$PLUG/Essentials/warps/bloodstone.yml" <<'EOF'
world: bloodstone
x: 0.5
y: 68.0
z: 0.5
yaw: 0.0
pitch: 0.0
name: bloodstone
EOF
chown crafty:crafty "$PLUG/Essentials/warps/bloodstone.yml"

# --- Items snapshot file (also created by plugin on boot if missing) ---
mkdir -p "$PLUG/AetherionItems"
cat > "$PLUG/AetherionItems/bloodstone-arena.yml" <<'EOF'
notes: Player pad + Hollow Sun boss home. NPC later. /bloodstonearena
player:
  world: bloodstone
  x: 0.5
  y: 68.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
boss:
  world: bloodstone
  x: 0.5
  y: 63.0
  z: 115.5
  yaw: 0.0
  pitch: 0.0
EOF
chown -R crafty:crafty "$PLUG/AetherionItems"

# --- JAR ---
install -o crafty -g crafty -m 664 /tmp/AetherionItems-1.0.0.jar "$PLUG/AetherionItems-1.0.0.jar"
rm -f "$PLUG/.paper-remapped"/AetherionItems*.jar 2>/dev/null || true
echo "jar installed"

# --- graceful stop (SIGTERM → Paper shutdown; prefer over SIGKILL) ---
pid=""
for p in $(pgrep -f 'java.*(paper|velocity)\.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [ "$cwd" = "$MMOR" ]; then pid=$p; break; fi
done
if [ -n "$pid" ]; then
  echo "SIGTERM pid=$pid (graceful)"
  kill -TERM "$pid" || true
  for i in $(seq 1 90); do
    if ! kill -0 "$pid" 2>/dev/null; then echo "stopped"; break; fi
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "still up — SIGKILL"
    kill -9 "$pid" || true
    sleep 2
  fi
else
  echo "already down"
fi

# --- start ---
printf '\n=== START %s ===\n' "$(date -Is)" >> "$MMOR/logs/latest.log"
sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
sleep 2
for i in $(seq 1 90); do
  if tail -n 80 "$MMOR/logs/latest.log" | grep -q 'Done ('; then
    echo OK
    tail -n 40 "$MMOR/logs/latest.log" | grep -E 'Done \(|Enabling AetherionItems|Error occurred while enabling AetherionItems|bloodstone' || true
    exit 0
  fi
  sleep 2
done
echo TIMEOUT
tail -n 50 "$MMOR/logs/latest.log"
exit 1
