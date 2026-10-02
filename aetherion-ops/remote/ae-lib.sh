#!/bin/bash
# Shared paths for Aetherion Crafty ops (Hetzner).
# Source this: . /root/aetherion-ops/ae-lib.sh

AE_CRAFTY_ROOT=/var/opt/minecraft/crafty
AE_SERVERS_ROOT="$AE_CRAFTY_ROOT/servers"
AE_CRAFTY_DB="$AE_CRAFTY_ROOT/crafty-4/app/config/db/crafty.sqlite"
AE_JAVA_MMOR="-Xms2G -Xmx8G"
AE_JAVA_HUB="-Xms1G -Xmx4G"
AE_JAVA_PROXY="-Xms1G -Xmx2G"
AE_JAVA_DEFAULT="-Xms2G -Xmx8G"

# Canonical MMO-R (live play / boss testing)
AE_MMOR_ID=a28d676a-03ef-40f1-9ac7-7a21c2ef6383

ae_resolve() {
  # Usage: ae_resolve <alias|uuid|name>  -> echoes absolute server dir
  local key="$1"
  case "${key,,}" in
    mmor|mmo-r|mmo_r) echo "$AE_SERVERS_ROOT/$AE_MMOR_ID"; return 0 ;;
  esac
  if [[ -d "$AE_SERVERS_ROOT/$key" ]]; then
    echo "$AE_SERVERS_ROOT/$key"
    return 0
  fi
  if [[ -f "$AE_CRAFTY_DB" ]]; then
    local id
    id=$(sqlite3 "$AE_CRAFTY_DB" "SELECT server_id FROM servers WHERE lower(server_name)=lower('$key') LIMIT 1;" 2>/dev/null || true)
    if [[ -n "$id" && -d "$AE_SERVERS_ROOT/$id" ]]; then
      echo "$AE_SERVERS_ROOT/$id"
      return 0
    fi
  fi
  echo "UNKNOWN_SERVER:$key" >&2
  return 1
}

ae_pid() {
  local dir="$1"
  local p cwd
  for p in $(pgrep -f 'java.*(paper|velocity)\.jar' 2>/dev/null || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [[ "$cwd" == "$dir" ]]; then
      echo "$p"
      return 0
    fi
  done
  return 1
}

ae_java_flags() {
  local name="${1,,}"
  case "$name" in
    mmor|mmo-r|mmo_r) echo "$AE_JAVA_MMOR" ;;
    hub) echo "$AE_JAVA_HUB" ;;
    proxy) echo "$AE_JAVA_PROXY" ;;
    *) echo "$AE_JAVA_DEFAULT" ;;
  esac
}

ae_jar_for() {
  local dir="$1"
  if [[ -f "$dir/velocity.jar" ]]; then echo velocity.jar; return; fi
  if [[ -f "$dir/paper.jar" ]]; then echo paper.jar; return; fi
  echo paper.jar
}

ae_mark_log() {
  local dir="$1" msg="${2:-START}"
  mkdir -p "$dir/logs"
  printf '\n=== %s %s ===\n' "$msg" "$(date -Is)" >> "$dir/logs/latest.log"
}

ae_wait_done() {
  # Wait for a NEW "Done (" after the last START marker. Timeout seconds optional.
  local dir="$1"
  local timeout="${2:-180}"
  local log="$dir/logs/latest.log"
  local start_line=0 end=$((SECONDS + timeout))
  if [[ -f "$log" ]]; then
    start_line=$(wc -l < "$log" | tr -d ' ')
  fi
  while (( SECONDS < end )); do
    if [[ -f "$log" ]]; then
      if tail -n +"$((start_line + 1))" "$log" 2>/dev/null | grep -q 'Done ('; then
        echo OK
        tail -n +"$((start_line + 1))" "$log" | grep -E 'Done \(|Enabling Aetherion|Error occurred while enabling Aetherion' | tail -n 20 || true
        return 0
      fi
    fi
    sleep 1
  done
  echo TIMEOUT
  tail -n 40 "$log" 2>/dev/null || true
  return 1
}
