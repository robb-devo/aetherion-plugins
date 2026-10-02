#!/bin/bash
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
pid=""
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [ "$cwd" = "$MMOR" ]; then pid=$p; break; fi
done
echo "pid=$pid"
if [ -z "$pid" ]; then exit 1; fi
ls -l "/proc/$pid/fd/0"
# Paper often still reads from a pipe/pty even under nohup — try a few reload labels
for cmd in "boss reload" "bossengine reload" "be reload"; do
  if printf '%s\n' "$cmd" > "/proc/$pid/fd/0" 2>/dev/null; then
    echo "sent: $cmd"
  else
    echo "fail send: $cmd"
  fi
  sleep 1
done
tail -n 40 "$MMOR/logs/latest.log" | grep -iE 'reload|Unknown command|hollow_sun|BossEngine' || true
grep -n 'max-health\|attack-damage' "$MMOR/plugins/BossEngine/bosses/hollow_sun.yml" | head -5
