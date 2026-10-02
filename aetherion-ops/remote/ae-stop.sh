#!/bin/bash
# Graceful stop via "stop" (LOCKED countdown 10→8→6→4→2), then escalate.
# Usage:
#   ae-stop.sh <alias>           # stop + wait for exit
#   ae-stop.sh <alias> now       # emergency: stop now / force kill
set -euo pipefail
. /root/aetherion-ops/ae-lib.sh

key="${1:-mmor}"
mode="${2:-}"
dir=$(ae_resolve "$key")

clear_locks() {
  # Paper/Crafty leave session.lock after hard kills — blocks next start.
  find "$dir" -maxdepth 3 -type f -name 'session.lock' -delete 2>/dev/null || true
}

pid=$(ae_pid "$dir" || true)
if [[ -z "${pid:-}" ]]; then
  clear_locks
  echo "already down dir=$dir"
  exit 0
fi

echo "stopping pid=$pid dir=$dir mode=${mode:-graceful}"

# Prefer Crafty / stdin "stop" so LOCKED ShutdownCountdown runs.
if [[ -f /root/aetherion-ops/ae-crafty-action.py ]]; then
  python3 /root/aetherion-ops/ae-crafty-action.py stop "$key" ${mode:+"$mode"} || true
fi

# Countdown ~10s + Paper flush; give it room. Emergency is short.
wait_secs=55
[[ "$mode" == "now" || "$mode" == "force" ]] && wait_secs=8

for _ in $(seq 1 "$wait_secs"); do
  pid=$(ae_pid "$dir" || true)
  [[ -z "${pid:-}" ]] && { clear_locks; echo "stopped"; exit 0; }
  sleep 1
done

pid=$(ae_pid "$dir" || true)
if [[ -z "${pid:-}" ]]; then
  clear_locks
  echo "stopped"
  exit 0
fi

# Still up: SIGTERM (not -9 yet), then short wait
echo "SIGTERM pid=$pid"
kill -15 "$pid" 2>/dev/null || true
for _ in $(seq 1 15); do
  pid=$(ae_pid "$dir" || true)
  [[ -z "${pid:-}" ]] && { clear_locks; echo "stopped"; exit 0; }
  sleep 1
done

pid=$(ae_pid "$dir" || true)
if [[ -n "${pid:-}" ]]; then
  echo "SIGKILL pid=$pid"
  kill -9 "$pid" 2>/dev/null || true
  sleep 1
fi
clear_locks
echo "stopped"
