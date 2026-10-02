#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282

deploy_jar() {
  local dir="$1" src="$2" dest="$3" stem="$4"
  install -o crafty -g crafty -m 664 "$src" "$dir/plugins/$dest"
  local base="${dest%.jar}"; base="${base%-1.0.0}"
  for alt in "${base}.jar" "${base}-1.0.0.jar"; do
    if [[ "$alt" != "$dest" && -e "$dir/plugins/$alt" ]]; then
      install -o crafty -g crafty -m 664 "$src" "$dir/plugins/$alt"
    fi
  done
  rm -f "$dir/plugins/.paper-remapped"/${stem}*.jar 2>/dev/null || true
  echo "OK $dir -> $dest ($(stat -c%s "$dir/plugins/$dest") bytes)"
}

for f in /tmp/BossEngine-1.0.0.jar /tmp/AetherionItems-1.0.0.jar /tmp/AetherionQuests-1.0.0.jar; do
  [[ -f "$f" ]] || { echo missing "$f"; exit 1; }
done

deploy_jar "$MMOR" /tmp/BossEngine-1.0.0.jar "BossEngine-1.0.0.jar" "BossEngine"
deploy_jar "$MMOD" /tmp/BossEngine-1.0.0.jar "BossEngine.jar" "BossEngine"
deploy_jar "$MMOR" /tmp/AetherionItems-1.0.0.jar "AetherionItems-1.0.0.jar" "AetherionItems"
if ls "$MMOD/plugins"/AetherionItems*.jar >/dev/null 2>&1; then
  dest=$(ls "$MMOD/plugins"/AetherionItems*.jar | head -1 | xargs -n1 basename)
  deploy_jar "$MMOD" /tmp/AetherionItems-1.0.0.jar "$dest" "AetherionItems"
fi
for dir in "$MMOR" "$MMOD"; do
  if ls "$dir/plugins"/AetherionQuests*.jar >/dev/null 2>&1; then
    dest=$(ls "$dir/plugins"/AetherionQuests*.jar | head -1 | xargs -n1 basename)
    deploy_jar "$dir" /tmp/AetherionQuests-1.0.0.jar "$dest" "AetherionQuests"
  else
    echo "SKIP Quests on $dir"
  fi
done

# Force Hollow Sun HP 120k (+ loot without bottles)
if [[ -f /tmp/bosses_hollow_sun.yml ]]; then
  for dir in "$MMOR" "$MMOD"; do
    mkdir -p "$dir/plugins/BossEngine/bosses"
    install -o crafty -g crafty -m 664 /tmp/bosses_hollow_sun.yml "$dir/plugins/BossEngine/bosses/hollow_sun.yml"
    echo "YML hollow_sun -> $dir"
  done
fi

echo DEPLOY_OK

restart_one() {
  local dir="$1" flags="$2" label="$3"
  echo "=== RESTART $label ==="
  for p in $(pgrep -f 'java.*paper.jar' || true); do
    cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
    if [ "$cwd" = "$dir" ]; then
      echo "kill $p"; kill -9 "$p" 2>/dev/null || true
    fi
  done
  sleep 2
  MARK=$(date -Is)
  printf '\n=== START %s ===\n' "$MARK" >> "$dir/logs/latest.log"
  sudo -u crafty bash -c "cd '$dir' && nohup java $flags -jar paper.jar nogui >> logs/latest.log 2>&1 &"
  for i in $(seq 1 90); do
    if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$dir/logs/latest.log"; then
      echo "OK $label"
      grep -E 'Enabling (BossEngine|AetherionItems|AetherionQuests)|Done \(' "$dir/logs/latest.log" | tail -n 8
      return 0
    fi
    sleep 2
  done
  echo "TIMEOUT $label"; return 1
}

restart_one "$MMOD" "-Xms2G -Xmx8G" "MMO-D"
restart_one "$MMOR" "-Xms2G -Xmx8G" "MMO-R"
echo ALL_OK_NO_HUB
