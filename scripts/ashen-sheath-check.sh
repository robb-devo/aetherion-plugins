#!/usr/bin/env bash
# Ashen Sheath drop-in check for Robb.
# Does not restart the live server. Restart Crafty yourself after the jars are in place.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BOSS_JAR="${1:-$ROOT/BossEngine/target/BossEngine-1.0.0.jar}"
ITEMS_JAR="${2:-$ROOT/AetherionItems/target/AetherionItems-1.0.0.jar}"

need() {
  if [[ ! -f "$1" ]]; then
    echo "missing: $1" >&2
    exit 1
  fi
}

need "$BOSS_JAR"
need "$ITEMS_JAR"

echo "== sha256 =="
sha256sum "$BOSS_JAR" "$ITEMS_JAR"

echo
echo "== BossEngine contents =="
jar tf "$BOSS_JAR" | grep -E 'AshenSheathDirector|bosses/ashen_sheath.yml|items.yml' || {
  echo "Ashen Sheath classes or templates missing from BossEngine jar" >&2
  exit 1
}

echo
echo "== AetherionItems contents =="
jar tf "$ITEMS_JAR" | grep -E 'AshenKatanaListener|createAshenKatana' || true
if ! jar tf "$ITEMS_JAR" | grep -q 'AshenKatanaListener.class'; then
  echo "AshenKatanaListener missing from AetherionItems jar" >&2
  exit 1
fi

echo
echo "Jars look intact. Live server was not restarted."
echo
echo "BossEngine only writes bosses/ashen_sheath.yml when that file is missing."
echo "If the live data folder already has the old template, replace it with the"
echo "copy inside BossEngine.jar (shape: CHERRY_TORNADO) before the restart."
echo "items.yml on live already has ashen_sheath_anchor and ashen_sheath_core."
echo
echo "After you SCP and restart Crafty yourself:"
echo "  /boss spawn ashen_sheath"
echo "  DEV menu → Weapons → Special Weapons → Ashen Katana"
echo "  Right-click the katana: dash, hover leaf tornado, slam shockwave."
echo "  Phase 3 (ashen moon) should spin real cherry-leaf blocks, then remove them."
