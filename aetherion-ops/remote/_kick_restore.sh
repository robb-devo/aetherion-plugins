#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
ROBBI=c46afc0c-488b-430f-83a7-98f8a5df3ac5

# Confirm restore still intact
ls -la "$MMOR/world/playerdata/${ROBBI}.dat"

# Kick via server stdin if crafty fifo exists, else try screen, else write kick to a one-shot
# Paper accepts commands via /tmp if we have rcon — check
if [ -f "$MMOR/server.properties" ]; then
  grep -E '^enable-rcon|^rcon' "$MMOR/server.properties" || true
fi

# Prefer: send kick through crafty console if possible
if command -v python3 >/dev/null && [ -f /root/aetherion-ops/ae-crafty-action.py ]; then
  python3 /root/aetherion-ops/ae-crafty-action.py cmd mmor "kick A3therion Inventory restore — please rejoin MMO-R" 2>&1 || true
fi

# Fallback: use named pipe / screen — many crafty installs use stdin via API only
# Hard fallback: if player file mtime updates after restore, warn
sleep 1
size=$(stat -c%s "$MMOR/world/playerdata/${ROBBI}.dat")
mtime=$(stat -c%y "$MMOR/world/playerdata/${ROBBI}.dat")
echo "playerdata size=$size mtime=$mtime"
if [ "$size" != "11467" ]; then
  echo "WARN: size drifted — re-applying restore"
  cp -a "$MMOR/world/playerdata/${ROBBI}.dat_old" "$MMOR/world/playerdata/${ROBBI}.dat"
  chown crafty:crafty "$MMOR/world/playerdata/${ROBBI}.dat"
  chmod 600 "$MMOR/world/playerdata/${ROBBI}.dat"
fi

# Also clear any NEW transfer files again
rm -f /var/opt/minecraft/crafty/shared/transfer/*
echo "transfer cleared again"
ls -la /var/opt/minecraft/crafty/shared/transfer/

# Show last join lines
grep -E 'A3therion|transfer snapshot|inventory' "$MMOR/logs/latest.log" | tail -n 20
