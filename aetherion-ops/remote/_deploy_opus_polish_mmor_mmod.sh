#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282

BE=/tmp/BossEngine-1.0.0.jar
AD=/tmp/AetherionDungeons-1.0.0.jar
AI=/tmp/AetherionItems-1.0.0.jar

[[ -f "$BE" && -f "$AD" && -f "$AI" ]] || { echo missing jars; ls -la /tmp/BossEngine* /tmp/AetherionDungeons* /tmp/AetherionItems* 2>/dev/null; exit 1; }

rm -f /var/opt/minecraft/crafty/shared/transfer/* 2>/dev/null || true

deploy_jar() {
  local dir="$1" src="$2" dest="$3" stem="$4"
  install -o crafty -g crafty -m 664 "$src" "$dir/plugins/$dest"
  # sync sibling alias if present
  local base="${dest%.jar}"
  base="${base%-1.0.0}"
  for alt in "${base}.jar" "${base}-1.0.0.jar"; do
    if [[ "$alt" != "$dest" && -e "$dir/plugins/$alt" ]]; then
      install -o crafty -g crafty -m 664 "$src" "$dir/plugins/$alt"
    fi
  done
  rm -f "$dir/plugins/.paper-remapped"/${stem}*.jar 2>/dev/null || true
  echo "OK $dir -> $dest ($(stat -c%s "$dir/plugins/$dest") bytes)"
}

# BossEngine
deploy_jar "$MMOR" "$BE" "BossEngine-1.0.0.jar" "BossEngine"
deploy_jar "$MMOD" "$BE" "BossEngine.jar" "BossEngine"

# AetherionDungeons
deploy_jar "$MMOR" "$AD" "AetherionDungeons.jar" "AetherionDungeons"
deploy_jar "$MMOD" "$AD" "AetherionDungeons.jar" "AetherionDungeons"

# AetherionItems
deploy_jar "$MMOR" "$AI" "AetherionItems-1.0.0.jar" "AetherionItems"
# MMO-D may or may not have Items — only if present
if ls "$MMOD/plugins"/AetherionItems*.jar >/dev/null 2>&1; then
  dest=$(ls "$MMOD/plugins"/AetherionItems*.jar | head -1 | xargs -n1 basename)
  deploy_jar "$MMOD" "$AI" "$dest" "AetherionItems"
else
  echo "SKIP Items on MMO-D (no jar present)"
fi

# Force-install new boss YAMLs (plugin won't overwrite existing)
for yml in ashen_chainwarden.yml cinder_herald.yml dungeon_aetherion.yml; do
  src="/tmp/bosses_$yml"
  if [[ -f "$src" ]]; then
    for dir in "$MMOR" "$MMOD"; do
      mkdir -p "$dir/plugins/BossEngine/bosses"
      install -o crafty -g crafty -m 664 "$src" "$dir/plugins/BossEngine/bosses/$yml"
      echo "YML $dir/plugins/BossEngine/bosses/$yml"
    done
  fi
done

# Force items.yml so new spawn items exist without relying solely on ensureItem
if [[ -f /tmp/bossengine_items.yml ]]; then
  for dir in "$MMOR" "$MMOD"; do
    install -o crafty -g crafty -m 664 /tmp/bossengine_items.yml "$dir/plugins/BossEngine/items.yml"
    echo "items.yml -> $dir"
  done
fi

echo DEPLOY_OK

restart_one() {
  local dir="$1" flags="$2" label="$3"
  echo "=== RESTART $label ==="
  for p in $(pgrep -f 'java.*paper.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [ "$cwd" = "$dir" ]; then
      echo "kill $p"
      kill -9 "$p" 2>/dev/null || true
    fi
  done
  sleep 2
  MARK=$(date -Is)
  printf '\n=== START %s ===\n' "$MARK" >> "$dir/logs/latest.log"
  sudo -u crafty bash -c "cd '$dir' && nohup java $flags -jar paper.jar nogui >> logs/latest.log 2>&1 &"
  for i in $(seq 1 90); do
    if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$dir/logs/latest.log"; then
      echo "OK $label"
      grep -E 'Enabling (BossEngine|AetherionDungeons|AetherionItems)|Done \(|Error occurred while enabling' "$dir/logs/latest.log" | tail -n 10
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT $label"
  return 1
}

restart_one "$MMOD" "-Xms2G -Xmx8G" "MMO-D"
restart_one "$MMOR" "-Xms2G -Xmx8G" "MMO-R"
echo ALL_OK_NO_HUB
