#!/bin/bash
# Start a Crafty server as user crafty. Does NOT stop anything.
# Usage: ae-start.sh <alias|uuid|name> [--wait]
set -euo pipefail
. /root/aetherion-ops/ae-lib.sh

key="${1:-mmor}"
wait_flag="${2:-}"
dir=$(ae_resolve "$key")
jar=$(ae_jar_for "$dir")
flags=$(ae_java_flags "$key")

if pid=$(ae_pid "$dir"); then
  echo "already running pid=$pid dir=$dir"
  exit 0
fi

# Stale locks from a previous hard kill block Paper world load.
find "$dir" -maxdepth 3 -type f -name 'session.lock' -delete 2>/dev/null || true

ae_mark_log "$dir" "START"
# shellcheck disable=SC2086
sudo -u crafty bash -c "cd '$dir' && nohup java $flags -jar '$jar' nogui >> logs/latest.log 2>&1 &"
sleep 2
pid=$(ae_pid "$dir" || true)
echo "started dir=$dir jar=$jar flags='$flags' pid=${pid:-?}"

if [[ -z "${pid:-}" ]]; then
  echo "WARN: java pid not found after start — check $dir/logs/latest.log" >&2
  tail -n 30 "$dir/logs/latest.log" 2>/dev/null || true
  exit 1
fi

if [[ "$wait_flag" == "--wait" ]]; then
  ae_wait_done "$dir" 180
fi
