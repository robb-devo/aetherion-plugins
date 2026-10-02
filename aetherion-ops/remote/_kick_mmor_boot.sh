#!/bin/bash
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
echo "=== java? ==="
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  echo "pid=$p cwd=$cwd"
done
echo "=== last log ==="
tail -n 30 "$MMOR/logs/latest.log"
# if down, start now
up=0
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [[ "$cwd" == "$MMOR" ]]; then up=1; fi
done
if [[ "$up" == "0" ]]; then
  echo "STARTING"
  MARK=$(date -Is)
  printf '\n=== START %s ===\n' "$MARK" >> "$MMOR/logs/latest.log"
  sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
  for i in $(seq 1 60); do
    if tail -n 40 "$MMOR/logs/latest.log" | grep -q 'Done ('; then
      echo OK
      tail -n 40 "$MMOR/logs/latest.log" | grep -E 'Done \(|ashen_void|Enabling AetherionItems'
      exit 0
    fi
    sleep 2
  done
  echo TIMEOUT; tail -n 40 "$MMOR/logs/latest.log"; exit 1
else
  echo ALREADY_UP
fi
