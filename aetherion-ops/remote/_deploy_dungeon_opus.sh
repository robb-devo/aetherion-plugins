#!/bin/bash
set -euo pipefail
. /root/aetherion-ops/ae-lib.sh 2>/dev/null || true

deploy_one() {
  local dir="$1" jar_src="$2" dest_name="$3"
  local plug="$dir/plugins"
  install -o crafty -g crafty -m 664 "$jar_src" "$plug/$dest_name"
  # sync sibling names if present
  local base
  base=$(echo "$dest_name" | sed -E 's/(-[0-9].*)?\.jar$//')
  for alt in "${base}.jar" "${base}-1.0.0.jar"; do
    if [[ "$alt" != "$dest_name" && -e "$plug/$alt" ]]; then
      install -o crafty -g crafty -m 664 "$jar_src" "$plug/$alt"
    fi
  done
  rm -f "$plug/.paper-remapped"/${base}*.jar 2>/dev/null || true
  echo "OK $dir -> $dest_name ($(stat -c%s "$plug/$dest_name") bytes)"
}

ROOT=/var/opt/minecraft/crafty/servers
MMOR=$ROOT/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=$ROOT/ca61edb7-7939-4aa7-86fa-99af1390a282
HUB=$ROOT/f3708762-f89b-4302-9d09-22071e139e9e

YML=/tmp/dungeon_aetherion.yml
BE=/tmp/BossEngine-1.0.0.jar
AD=/tmp/AetherionDungeons-1.0.0.jar

[[ -f "$BE" && -f "$AD" && -f "$YML" ]] || { echo "missing uploads"; ls -la /tmp/BossEngine* /tmp/AetherionDungeons* /tmp/dungeon_aetherion.yml; exit 1; }

# BossEngine
deploy_one "$MMOR" "$BE" "BossEngine-1.0.0.jar"
deploy_one "$MMOD" "$BE" "BossEngine.jar"
deploy_one "$HUB"  "$BE" "BossEngine.jar"

# AetherionDungeons (only where present)
deploy_one "$MMOR" "$AD" "AetherionDungeons.jar"
deploy_one "$MMOD" "$AD" "AetherionDungeons.jar"

# Force-overwrite boss YAML (plugin will not replace existing)
for dir in "$MMOR" "$MMOD" "$HUB"; do
  dest="$dir/plugins/BossEngine/bosses/dungeon_aetherion.yml"
  mkdir -p "$(dirname "$dest")"
  install -o crafty -g crafty -m 664 "$YML" "$dest"
  echo "YML $dest"
  head -n 3 "$dest"
done

echo DEPLOY_ALL_OK
