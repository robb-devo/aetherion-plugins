#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
install -o crafty -g crafty -m 664 /tmp/AetherionItems-1.0.0.jar "$MMOR/plugins/AetherionItems-1.0.0.jar"
rm -f "$MMOR/plugins/.paper-remapped"/AetherionItems*.jar 2>/dev/null || true
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [[ "$cwd" == "$MMOR" ]]; then echo "KILL $p"; kill -9 "$p" || true; fi
done
sleep 2
rm -f "$MMOR/world/session.lock" 2>/dev/null || true
printf '\n=== START %s ===\n' "$(date -Is)" >> "$MMOR/logs/latest.log"
sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
for i in $(seq 1 40); do
  if tail -n 20 "$MMOR/logs/latest.log" | grep -q 'Done ('; then
    echo OK; tail -n 15 "$MMOR/logs/latest.log" | grep -E 'Done \(|Enabling AetherionItems|Failed'
    exit 0
  fi
  sleep 2
done
echo TIMEOUT; exit 1
