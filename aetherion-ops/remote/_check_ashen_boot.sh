#!/bin/bash
sleep 20
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
echo "=== log ==="
tail -n 120 "$MMOR/logs/latest.log" | grep -E 'Done \(|Enabling BossEngine|Enabling AetherionItems|ashen_void|Ashen Void|Error occurred|Loaded world' || true
echo "=== pids ==="
for p in $(pgrep -f 'java.*paper.jar' || true); do
  cwd=$(readlink -f "/proc/$p/cwd" 2>/dev/null || true)
  echo "pid=$p cwd=$cwd"
done
echo "=== remapper ==="
ls -la "$MMOR/plugins/.paper-remapped/" 2>/dev/null | grep -E 'BossEngine|AetherionItems' || echo none
echo DONE
