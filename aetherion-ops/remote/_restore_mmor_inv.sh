#!/bin/bash
set -euo pipefail
MMOR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/world/playerdata
ROBBI=c46afc0c-488b-430f-83a7-98f8a5df3ac5

cd "$MMOR"
echo "=== BEFORE ==="
ls -la "${ROBBI}".dat "${ROBBI}".dat_old

# If player online on MMO-R, kick first so restore sticks
MMOR_DIR=/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383
# try kick via screen/fifo if any — best effort: if java cwd is mmor, we can't easily kick without rcon
# Check if playerdata is locked / newer than restore candidate

GOOD="${ROBBI}.dat_old"
WIPED="${ROBBI}.dat"
good_size=$(stat -c%s "$GOOD")
wiped_size=$(stat -c%s "$WIPED")
echo "good=$good_size wiped=$wiped_size"

if [ "$good_size" -gt "$wiped_size" ]; then
  cp -a "$WIPED" "${ROBBI}.dat.bak-wiped-$(date +%Y%m%d-%H%M%S)"
  cp -a "$GOOD" "$WIPED"
  chown crafty:crafty "$WIPED"
  chmod 600 "$WIPED"
  echo "RESTORED ${ROBBI}.dat from dat_old ($good_size bytes)"
else
  echo "SKIP: dat_old not larger than dat — manual check needed"
fi

echo "=== AFTER ==="
ls -la "${ROBBI}".dat* | head -20

# Also scan other players: if dat shrunk vs dat_old after 15:28, restore
echo "=== other shrinks since restart window ==="
for f in *.dat; do
  [ -f "${f}_old" ] || continue
  s=$(stat -c%s "$f")
  o=$(stat -c%s "${f}_old")
  mt=$(stat -c%Y "$f")
  # after 15:28 UTC-ish = epoch roughly — use mtime string
  mts=$(stat -c%y "$f")
  if [ "$o" -gt "$s" ] && [[ "$mts" > "2026-09-26 15:28" ]]; then
    echo "CANDIDATE $f now=$s old=$o mtime=$mts"
    cp -a "$f" "${f}.bak-wiped-$(date +%Y%m%d-%H%M%S)"
    cp -a "${f}_old" "$f"
    chown crafty:crafty "$f"
    chmod 600 "$f"
    echo "  restored $f from _old"
  fi
done

echo DONE_RESTORE
