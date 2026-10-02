#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383

# 1) MV misc.spawn true (Skuldugery arrows)
sed -i 's/\r$//' /root/aetherion-ops/_fix_ashen_void_misc_spawn.sh
bash /root/aetherion-ops/_fix_ashen_void_misc_spawn.sh

# 2) Items jar
install -o crafty -g crafty -m 664 /tmp/AetherionItems-1.0.0.jar "$MMOR/plugins/AetherionItems-1.0.0.jar"
rm -f "$MMOR/plugins/.paper-remapped"/AetherionItems*.jar 2>/dev/null || true
echo "jar $(stat -c%s "$MMOR/plugins/AetherionItems-1.0.0.jar")"

# 3) Pad config — no void-y soft floor
mkdir -p "$MMOR/plugins/AetherionItems"
cat > "$MMOR/plugins/AetherionItems/ashen-void-arena.yml" <<'EOF'
notes: Ashen Void preview — End remap of Bloodstone. /ashenvoid
player:
  world: ashen_void
  x: 0.5
  y: 68.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
EOF
chown -R crafty:crafty "$MMOR/plugins/AetherionItems"

# 4) Stop MMO-R (java), swap world, start
pid=""
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  if [[ "$cwd" == "$MMOR" ]] && tr '\0' ' ' < "/proc/$p/cmdline" | grep -q 'paper.jar'; then
    pid=$p; break
  fi
done
if [[ -n "$pid" ]]; then
  echo "SIGTERM MMO-R $pid"
  kill -TERM "$pid" || true
  for i in $(seq 1 90); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "SIGKILL $pid"; kill -9 "$pid" || true; sleep 2
  fi
fi

cd "$MMOR"
rm -rf ashen_void.bak 2>/dev/null || true
if [[ -d ashen_void ]]; then mv ashen_void ashen_void.bak; fi
tar -xzf /tmp/ashen_void_world.tar.gz
rm -f ashen_void/session.lock ashen_void/uid.dat 2>/dev/null || true
chown -R crafty:crafty ashen_void
echo "world $(du -sh ashen_void | awk '{print $1}')"

MARK=$(date -Is)
printf '\n=== START %s ===\n' "$MARK" >> "$MMOR/logs/latest.log"
sudo -u crafty bash -c "cd '$MMOR' && nohup java -Xms2G -Xmx8G -jar paper.jar nogui >> logs/latest.log 2>&1 &"
for i in $(seq 1 100); do
  if awk -v m="$MARK" 'index($0,m){f=1} f && /Done \(/ {found=1; exit} END{exit !found}' "$MMOR/logs/latest.log"; then
    echo OK_MMOR
    awk -v m="$MARK" 'index($0,m){f=1} f' "$MMOR/logs/latest.log" | grep -E 'ashen_void|Ashen Void|Enabling AetherionItems|Done \(|Error occurred while enabling AetherionItems' | tail -n 20
    exit 0
  fi
  sleep 2
done
echo TIMEOUT
tail -n 40 "$MMOR/logs/latest.log"
exit 1
