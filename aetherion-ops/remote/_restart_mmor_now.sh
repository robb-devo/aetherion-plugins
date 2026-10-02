#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [ "$cwd" = "$MMOR" ]; then
    echo "kill $p"
    kill -9 "$p" 2>/dev/null || true
  fi
done
sleep 2
printf '\n=== START %s ===\n' "$(date -Is)" >> "$MMOR/logs/latest.log"
sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
for i in $(seq 1 90); do
  if tail -n 100 "$MMOR/logs/latest.log" | grep -q 'Done ('; then
    echo OK
    grep -E 'Done \(|Enabling BossEngine|Error occurred while enabling BossEngine|Enabling AetherionItems' "$MMOR/logs/latest.log" | tail -n 8
    exit 0
  fi
  sleep 2
done
echo TIMEOUT
exit 1
