#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282
MV="$MMOR/plugins/Multiverse-Core/worlds.yml"

send_stop() {
  local dir="$1" label="$2"
  local pid=""
  for p in $(pgrep -f 'java.*paper.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [[ "$cwd" == "$dir" ]]; then pid=$p; break; fi
  done
  if [[ -z "$pid" ]]; then
    echo "$label already down"
    return 0
  fi
  echo "SIGTERM $label pid=$pid (LOCKED countdown)"
  kill -TERM "$pid" || true
  for i in $(seq 1 75); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$label stopped"
      return 0
    fi
    sleep 1
  done
  echo "$label still up after 75s"
  return 1
}

if [[ -f "$MV" ]] && ! grep -q '^minecraft:ashen_void:' "$MV"; then
  python3 /root/aetherion-ops/_seed_ashen_void_mv.py
  chown crafty:crafty "$MV"
else
  echo "MV ashen_void already present or worlds.yml missing"
fi

send_stop "$MMOR" "MMO-R" || true
send_stop "$MMOD" "MMO-D" || true
sleep 2

start_one() {
  local dir="$1" label="$2"
  local pid=""
  for p in $(pgrep -f 'java.*paper.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [[ "$cwd" == "$dir" ]]; then pid=$p; break; fi
  done
  if [[ -n "$pid" ]]; then
    echo "$label still running pid=$pid — skip start"
    return 1
  fi
  MARK=$(date -Is)
  printf '\n=== START %s ===\n' "$MARK" >> "$dir/logs/latest.log"
  sudo -u crafty bash -c "cd '$dir' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
  for i in $(seq 1 90); do
    if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$dir/logs/latest.log"; then
      echo "OK $label"
      grep -E 'Enabling (BossEngine|AetherionItems)|Done \(|ashen_void|Ashen Void|Error occurred' "$dir/logs/latest.log" | tail -n 25
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT $label"
  tail -n 50 "$dir/logs/latest.log"
  return 1
}

start_one "$MMOD" "MMO-D" || true
start_one "$MMOR" "MMO-R"

ls -la "$MMOR/ashen_void/level.dat" "$MMOR/plugins/BossEngine-1.0.0.jar" "$MMOR/plugins/AetherionItems-1.0.0.jar"
grep -c 'ashen_void' "$MV" || true
echo ALL_DONE
