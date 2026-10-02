#!/bin/bash
set -euo pipefail
. /root/aetherion-ops/ae-lib.sh

echo "=== Crafty servers ==="
if [[ -f "$AE_CRAFTY_DB" ]]; then
  sqlite3 -separator $'\t' "$AE_CRAFTY_DB" \
    "SELECT server_name, server_id, server_port, auto_start FROM servers ORDER BY server_name;" \
    | while IFS=$'\t' read -r name id port auto; do
        dir="$AE_SERVERS_ROOT/$id"
        pid=$(ae_pid "$dir" || true)
        printf '%-8s  port=%-5s  %s  pid=%s\n' "$name" "$port" "$id" "${pid:-down}"
      done
else
  for dir in "$AE_SERVERS_ROOT"/*; do
    [[ -d "$dir" ]] || continue
    id=$(basename "$dir")
    pid=$(ae_pid "$dir" || true)
    printf '%s  pid=%s\n' "$id" "${pid:-down}"
  done
fi

echo
echo "=== MMO-R plugins (Aetherion*) ==="
ls -la "$AE_SERVERS_ROOT/$AE_MMOR_ID/plugins"/Aetherion*.jar 2>/dev/null || echo "(none)"
echo
echo "=== remapper cache (Aetherion*) ==="
ls -la "$AE_SERVERS_ROOT/$AE_MMOR_ID/plugins/.paper-remapped"/Aetherion* 2>/dev/null || echo "(cleared / none)"
