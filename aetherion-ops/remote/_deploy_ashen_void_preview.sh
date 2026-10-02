#!/bin/bash
# Deploy Ashen Void preview world + BossEngine/Items uniqueness+regen jars. Does NOT touch live bloodstone.
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

for f in /tmp/BossEngine-1.0.0.jar /tmp/AetherionItems-1.0.0.jar /tmp/ashen_void_world.tar.gz; do
  [[ -f "$f" ]] || { echo missing "$f"; exit 1; }
done

deploy_jar "$MMOR" /tmp/BossEngine-1.0.0.jar "BossEngine-1.0.0.jar" "BossEngine"
deploy_jar "$MMOD" /tmp/BossEngine-1.0.0.jar "BossEngine.jar" "BossEngine"
deploy_jar "$MMOR" /tmp/AetherionItems-1.0.0.jar "AetherionItems-1.0.0.jar" "AetherionItems"
if ls "$MMOD/plugins"/AetherionItems*.jar >/dev/null 2>&1; then
  dest=$(ls "$MMOD/plugins"/AetherionItems*.jar | head -1 | xargs -n1 basename)
  deploy_jar "$MMOD" /tmp/AetherionItems-1.0.0.jar "$dest" "AetherionItems"
fi

# World: never touch bloodstone. Replace only ashen_void preview folder.
cd "$MMOR"
if [[ -d ashen_void ]]; then
  rm -rf ashen_void.bak 2>/dev/null || true
  mv ashen_void ashen_void.bak
fi
tar -xzf /tmp/ashen_void_world.tar.gz
# tar contains top-level ashen_void/
rm -f ashen_void/session.lock ashen_void/uid.dat 2>/dev/null || true
chown -R crafty:crafty ashen_void
echo "WORLD ashen_void installed ($(du -sh ashen_void | awk '{print $1}'))"

# Items pad defaults
mkdir -p "$MMOR/plugins/AetherionItems"
cat > "$MMOR/plugins/AetherionItems/ashen-void-arena.yml" <<'EOF'
notes: Ashen Void preview — End remap of Bloodstone. /ashenvoid
void-y: 40.0
player:
  world: ashen_void
  x: 0.5
  y: 68.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
EOF
chown -R crafty:crafty "$MMOR/plugins/AetherionItems"

# Essentials warp for quick TP
mkdir -p "$MMOR/plugins/Essentials/warps"
cat > "$MMOR/plugins/Essentials/warps/ashenvoid.yml" <<'EOF'
world: ashen_void
x: 0.5
y: 68.0
z: 0.5
yaw: 0.0
pitch: 0.0
name: ashenvoid
EOF
chown crafty:crafty "$MMOR/plugins/Essentials/warps/ashenvoid.yml"

echo DEPLOY_FILES_OK
