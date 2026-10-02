#!/bin/bash
set -euo pipefail
HUB=/var/opt/minecraft/crafty/servers/f3708762-f89b-4302-9d09-22071e139e9e
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282

echo "=== BossEngine jars everywhere ==="
find /var/opt/minecraft/crafty/servers -name 'BossEngine*.jar*' 2>/dev/null | while read f; do ls -la "$f"; done
echo "=== /tmp jars ==="
ls -la /tmp/BossEngine* /tmp/*.jar 2>/dev/null | head -30 || true
echo "=== remapper hub ==="
ls -la "$HUB/plugins/.paper-remapped"/BossEngine* 2>/dev/null || true
echo "=== deploy-bak hub ==="
find "$HUB/plugins/_deploy-bak" -type f 2>/dev/null | head -40
echo "=== crafty backups ==="
find /var/opt/minecraft/crafty -iname '*backup*' -type d 2>/dev/null | head -20
echo "=== MMO-R playerdata ==="
ls -la "$MMOR/world/playerdata/"
echo "=== HUB playerdata ==="
ls -la "$HUB/world/playerdata/"
echo "=== Core plugin data dirs ==="
ls -la "$HUB/plugins/AetherionCore/" 2>/dev/null || true
ls -la "$MMOR/plugins/AetherionCore/" 2>/dev/null || true
# look for inventory snapshots
find "$HUB/plugins" "$MMOR/plugins" -iname '*inv*' 2>/dev/null | head -40
