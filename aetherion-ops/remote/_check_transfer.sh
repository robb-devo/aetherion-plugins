#!/bin/bash
set -euo pipefail
SHARED=/var/opt/minecraft/crafty/shared
HUB=/var/opt/minecraft/crafty/servers/f3708762-f89b-4302-9d09-22071e139e9e
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282

echo "=== shared transfer ==="
ls -la "$SHARED" 2>/dev/null || echo no-shared
find "$SHARED" -type f 2>/dev/null | head -50
echo "=== Dungeons configs role ==="
for s in "$HUB" "$MMOR" "$MMOD"; do
  echo "-- $(basename "$s") --"
  grep -nE 'role|hub|server' "$s/plugins/AetherionDungeons/config.yml" 2>/dev/null | head -20 || echo "(no config)"
done
echo "=== wipe flags ==="
find /var/opt/minecraft/crafty -name '*wipe*' 2>/dev/null | head -30
ls -la "$SHARED"/ 2>/dev/null
