#!/bin/bash
set -euo pipefail
HUB=/var/opt/minecraft/crafty/servers/f3708762-f89b-4302-9d09-22071e139e9e
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383

echo "=== Core configs ==="
echo "--- HUB ---"
cat "$HUB/plugins/AetherionCore/config.yml"
echo "--- HUB bak ---"
cat "$HUB/plugins/AetherionCore/config.yml.bak-pre-hubinvfix" 2>/dev/null || true
echo "--- MMOR ---"
cat "$MMOR/plugins/AetherionCore/config.yml"

echo "=== find 503522 BossEngine ==="
find /var/opt/minecraft /tmp /root -name 'BossEngine*.jar' -size 503522c 2>/dev/null || true
find /var/opt/minecraft /tmp -name 'BossEngine*.jar' 2>/dev/null | while read f; do
  s=$(stat -c%s "$f")
  echo "$s $f"
done | sort -n | uniq

echo "=== Robbi UUID playerdata detail ==="
# common robbi uuid from earlier
for id in c46afc0c-488b-430f-83a7-98f8a5df3ac5 af0ee4e4-f377-419d-a39b-60689d8a163a a1f60b78-f3aa-4686-91e1-f374f7a27786; do
  echo "-- $id --"
  ls -la "$MMOR/world/playerdata/$id"* 2>/dev/null || true
  ls -la "$HUB/world/playerdata/$id"* 2>/dev/null || true
done

echo "=== online players / latest log snippets ==="
tail -n 30 "$HUB/logs/latest.log" | grep -iE 'join|left|lost|invent|sync|hub' || true
tail -n 30 "$MMOR/logs/latest.log" | grep -iE 'join|left|lost|invent|sync|hub' || true

echo "=== crafty backup list ==="
ls -la /var/opt/minecraft/crafty/crafty-4/backups 2>/dev/null | head -20
