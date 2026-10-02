#!/bin/bash
# Install a plugin JAR already uploaded to /tmp into a server plugins/ dir.
# Clears Paper remapper cache for that plugin stem.
#
# Usage:
#   ae-deploy-jar.sh <alias> <tmp-jar> [dest-name]
# Example:
#   ae-deploy-jar.sh mmor /tmp/AetherionItems-1.0.0.jar AetherionItems-1.0.0.jar
set -euo pipefail
. /root/aetherion-ops/ae-lib.sh

key="${1:?server}"
src="${2:?/tmp/plugin.jar}"
dest_name="${3:-$(basename "$src")}"
dir=$(ae_resolve "$key")
plug="$dir/plugins"

[[ -f "$src" ]] || { echo "missing $src"; exit 1; }
mkdir -p "$plug"

install -o crafty -g crafty -m 664 "$src" "$plug/$dest_name"

# Keep common aliases in sync if they already exist
stem="${dest_name%.jar}"
stem="${stem%-1.*}"
stem="${stem%%-[0-9]*}"
# For AetherionItems-1.0.0.jar -> also refresh AetherionItems.jar / -1.1.jar if present
base="${dest_name%%-[0-9]*}"
base="${base%.jar}"
if [[ "$dest_name" == AetherionItems* ]]; then
  for alt in AetherionItems.jar AetherionItems-1.1.jar; do
    if [[ -e "$plug/$alt" ]]; then
      install -o crafty -g crafty -m 664 "$src" "$plug/$alt"
    fi
  done
  rm -f "$plug/.paper-remapped"/AetherionItems*.jar 2>/dev/null || true
else
  rm -f "$plug/.paper-remapped/${dest_name}" "$plug/.paper-remapped/${base}"*.jar 2>/dev/null || true
fi

echo "DEPLOYED"
ls -la "$plug/$dest_name"
sha256sum "$plug/$dest_name"
echo "remapper:"
ls -la "$plug/.paper-remapped"/${base}* 2>/dev/null || echo "(cleared)"
