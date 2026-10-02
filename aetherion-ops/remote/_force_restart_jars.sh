#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282

kill_java() {
  local dir="$1" label="$2"
  local pid=""
  for p in $(pgrep -f 'java.*paper.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [[ "$cwd" == "$dir" ]]; then
      # prefer real java, not bash wrappers
      if tr '\0' ' ' < "/proc/$p/cmdline" | grep -q 'paper.jar'; then
        pid=$p
        break
      fi
    fi
  done
  if [[ -z "$pid" ]]; then
    echo "$label already down"
    return 0
  fi
  echo "SIGTERM $label java pid=$pid"
  kill -TERM "$pid" || true
  for i in $(seq 1 90); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$label stopped after ${i}s"
      return 0
    fi
    sleep 1
  done
  echo "$label still up — SIGKILL (deploy stuck)"
  kill -9 "$pid" || true
  sleep 2
}

kill_java "$MMOR" "MMO-R"
kill_java "$MMOD" "MMO-D"
# Also reap any leftover children
sleep 2

start_one() {
  local dir="$1" label="$2"
  for p in $(pgrep -f 'java.*paper.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [[ "$cwd" == "$dir" ]] && tr '\0' ' ' < "/proc/$p/cmdline" | grep -q 'paper.jar'; then
      echo "$label still running pid=$p"
      return 1
    fi
  done
  MARK=$(date -Is)
  printf '\n=== START %s ===\n' "$MARK" >> "$dir/logs/latest.log"
  sudo -u crafty bash -c "cd '$dir' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
  for i in $(seq 1 100); do
    if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$dir/logs/latest.log"; then
      echo "OK $label"
      awk -v m="$MARK" 'index($0,m){f=1} f' "$dir/logs/latest.log" | grep -E 'Enabling (BossEngine|AetherionItems)|Done \(|ashen_void|Ashen Void|Error occurred' | tail -n 30
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT $label"
  tail -n 60 "$dir/logs/latest.log"
  return 1
}

start_one "$MMOD" "MMO-D" || true
start_one "$MMOR" "MMO-R"
ls -la "$MMOR/plugins/.paper-remapped/" | grep -E 'BossEngine|AetherionItems' || true
echo ALL_DONE
