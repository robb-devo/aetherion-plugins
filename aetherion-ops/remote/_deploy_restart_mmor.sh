#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
cp /tmp/BossEngine-1.0.0.jar "$MMOR/plugins/BossEngine-1.0.0.jar"
rm -f "$MMOR/plugins/BossEngine.jar" "$MMOR/plugins/.paper-remapped"/BossEngine*.jar 2>/dev/null || true
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [ "$cwd" = "$MMOR" ]; then
    echo "kill $p"
    kill -9 "$p" 2>/dev/null || true
  fi
done
sleep 2
MARK=$(date -Is)
printf '\n=== START %s ===\n' "$MARK" >> "$MMOR/logs/latest.log"
sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
for i in $(seq 1 60); do
  if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$MMOR/logs/latest.log"; then
    echo RESTART_OK
    grep -E 'Enabling BossEngine|Done \(' "$MMOR/logs/latest.log" | tail -n 4
    ls -la "$MMOR/plugins/BossEngine-1.0.0.jar"
    exit 0
  fi
  sleep 2
done
echo TIMEOUT
exit 1
