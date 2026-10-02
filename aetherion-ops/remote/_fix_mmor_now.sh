#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383

# Kill ALL java with this cwd
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [[ "$cwd" == "$MMOR" ]]; then
    echo "KILL $p"
    kill -9 "$p" || true
  fi
done
sleep 2
# Clear stale locks
rm -f "$MMOR/world/session.lock" "$MMOR/ashen_void/session.lock" "$MMOR/bloodstone/session.lock" 2>/dev/null || true

# Ensure jar + world present
install -o crafty -g crafty -m 664 /tmp/AetherionItems-1.0.0.jar "$MMOR/plugins/AetherionItems-1.0.0.jar" 2>/dev/null || true
rm -f "$MMOR/plugins/.paper-remapped"/AetherionItems*.jar 2>/dev/null || true
if [[ ! -d "$MMOR/ashen_void" ]] || [[ ! -f "$MMOR/ashen_void/level.dat" ]]; then
  cd "$MMOR"
  tar -xzf /tmp/ashen_void_world.tar.gz
  chown -R crafty:crafty ashen_void
fi
rm -f "$MMOR/ashen_void/session.lock" "$MMOR/ashen_void/uid.dat" 2>/dev/null || true
chown -R crafty:crafty "$MMOR/ashen_void"

# MV misc already fixed earlier — re-run cheap
bash /root/aetherion-ops/_fix_ashen_void_misc_spawn.sh || true

printf '\n=== START %s ===\n' "$(date -Is)" >> "$MMOR/logs/latest.log"
sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
for i in $(seq 1 45); do
  if tail -n 25 "$MMOR/logs/latest.log" | grep -q 'Done ('; then
    echo OK
    tail -n 30 "$MMOR/logs/latest.log" | grep -E 'Done \(|ashen_void|Enabling AetherionItems|Failed to start'
    exit 0
  fi
  if tail -n 15 "$MMOR/logs/latest.log" | grep -q 'Failed to start'; then
    echo FAIL; tail -n 20 "$MMOR/logs/latest.log"; exit 1
  fi
  sleep 2
done
echo TIMEOUT; tail -n 30 "$MMOR/logs/latest.log"; exit 1
