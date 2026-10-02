#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
MMOD=/var/opt/minecraft/crafty/servers/ca61edb7-7939-4aa7-86fa-99af1390a282
AD=/tmp/AetherionDungeons-1.0.0.jar
BE=/tmp/BossEngine-1.0.0.jar
YML=/tmp/dungeon_aetherion.yml

[[ -f "$AD" && -f "$BE" && -f "$YML" ]] || { echo missing uploads; ls -la /tmp/AetherionDungeons* /tmp/BossEngine* /tmp/dungeon_aetherion.yml; exit 1; }

# clear pending hub→mmo transfer payloads
rm -f /var/opt/minecraft/crafty/shared/transfer/*
echo "transfer cleared"

install -o crafty -g crafty -m 664 "$AD" "$MMOR/plugins/AetherionDungeons.jar"
install -o crafty -g crafty -m 664 "$AD" "$MMOD/plugins/AetherionDungeons.jar"
rm -f "$MMOR/plugins/.paper-remapped"/AetherionDungeons*.jar "$MMOD/plugins/.paper-remapped"/AetherionDungeons*.jar 2>/dev/null || true

# keep Opus BossEngine live on MMO-R / MMO-D only
install -o crafty -g crafty -m 664 "$BE" "$MMOR/plugins/BossEngine-1.0.0.jar"
install -o crafty -g crafty -m 664 "$BE" "$MMOD/plugins/BossEngine.jar"
rm -f "$MMOR/plugins/.paper-remapped"/BossEngine*.jar "$MMOD/plugins/.paper-remapped"/BossEngine*.jar 2>/dev/null || true
install -o crafty -g crafty -m 664 "$YML" "$MMOR/plugins/BossEngine/bosses/dungeon_aetherion.yml"
install -o crafty -g crafty -m 664 "$YML" "$MMOD/plugins/BossEngine/bosses/dungeon_aetherion.yml"

echo "DEPLOYED MMOR+MMOD only"
ls -la "$MMOR/plugins/AetherionDungeons.jar" "$MMOR/plugins/BossEngine-1.0.0.jar"
ls -la "$MMOD/plugins/AetherionDungeons.jar" "$MMOD/plugins/BossEngine.jar"
head -n 2 "$MMOD/plugins/BossEngine/bosses/dungeon_aetherion.yml"

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
      grep -E 'Enabling (BossEngine|AetherionDungeons)|Done \(|Inventory transfer|Leaving inventory' "$dir/logs/latest.log" | tail -n 8
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
