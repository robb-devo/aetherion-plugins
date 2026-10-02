#!/bin/bash
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
echo "=== last 60 lines ==="
tail -n 60 "$MMOR/logs/latest.log"
echo "=== log size / mtime ==="
ls -la "$MMOR/logs/latest.log"
echo "=== java cmd ==="
tr '\0' ' ' < /proc/1164648/cmdline; echo
ps -o etime,pid,cmd -p 1164648
