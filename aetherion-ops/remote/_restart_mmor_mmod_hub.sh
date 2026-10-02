#!/bin/bash
set -euo pipefail
ROOT=/var/opt/minecraft/crafty/servers

restart_one() {
  local dir="$1" flags="$2" label="$3"
  echo "=== RESTART $label ==="
  for p in $(pgrep -f 'java.*(paper|velocity)\.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [ "$cwd" = "$dir" ]; then
      echo "kill $p"
      kill -9 "$p" 2>/dev/null || true
    fi
  done
  sleep 2
  MARK=$(date -Is)
  printf '\n=== START %s ===\n' "$MARK" >> "$dir/logs/latest.log"
  jar=paper.jar
  [ -f "$dir/velocity.jar" ] && jar=velocity.jar
  sudo -u crafty bash -c "cd '$dir' && nohup java $flags -jar '$jar' nogui >> logs/latest.log 2>&1 &"
  for i in $(seq 1 90); do
    if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$dir/logs/latest.log"; then
      echo "OK $label"
      grep -E 'Enabling (BossEngine|AetherionDungeons)|Done \(' "$dir/logs/latest.log" | tail -n 6
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT $label"
  return 1
}

restart_one "$ROOT/ca61edb7-7939-4aa7-86fa-99af1390a282" "-Xms2G -Xmx8G" "MMO-D"
restart_one "$ROOT/a28d676a-03ef-40f1-9ac7-7a21c2ef6383" "-Xms2G -Xmx8G" "MMO-R"
restart_one "$ROOT/f3708762-f89b-4302-9d09-22071e139e9e" "-Xms1G -Xmx4G" "HUB"
echo ALL_RESTART_OK
