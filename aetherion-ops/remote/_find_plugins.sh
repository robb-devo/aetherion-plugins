#!/bin/bash
set -euo pipefail
ROOT=/var/opt/minecraft/crafty/servers
for d in "$ROOT"/*; do
  [ -d "$d" ] || continue
  id=$(basename "$d")
  port=$(grep -E '^server-port=' "$d/server.properties" 2>/dev/null | cut -d= -f2 || echo '?')
  motd=$(grep -E '^motd=' "$d/server.properties" 2>/dev/null | cut -d= -f2- | head -c 80 || echo '?')
  echo "=== $id port=$port motd=$motd ==="
  ls -la "$d/plugins"/BossEngine*.jar "$d/plugins"/AetherionDungeons*.jar 2>/dev/null || echo "(no BE/AD jars)"
  if [ -f "$d/plugins/BossEngine/bosses/dungeon_aetherion.yml" ]; then
    echo "HAS dungeon_aetherion.yml"
    head -n 5 "$d/plugins/BossEngine/bosses/dungeon_aetherion.yml"
  fi
done
