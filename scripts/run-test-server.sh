#!/usr/bin/env bash
#
# run-test-server.sh — spin up a local Paper 1.21.1 server with the freshly
# built Aetherion plugins for the SETUP.md smoke test.
#
# Usage:
#   mvn -B clean install            # build the plugin jars first
#   scripts/run-test-server.sh      # download Paper, deploy jars, start server
#
# Environment overrides:
#   SERVER_DIR       Where the server lives (default: /tmp/aetherion-server)
#   PAPER_VERSION    Minecraft/Paper version (default: 1.21.1)
#   SKIP_SOFT_DEPS   If set to 1, do not download WorldEdit/WorldGuard/PlaceholderAPI
#   MEMORY           JVM heap, e.g. 2G (default: 2G)
#
# The soft-dep server plugins (WorldEdit, WorldGuard, PlaceholderAPI) are
# required for AetherionMining/Farming/Foraging (hard depend on WorldGuard) and
# for AetherionDungeons/Pit to enable without class-loading errors. See SETUP.md.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${SERVER_DIR:-/tmp/aetherion-server}"
PAPER_VERSION="${PAPER_VERSION:-1.21.1}"
MEMORY="${MEMORY:-2G}"

mkdir -p "$SERVER_DIR/plugins"

# --- Paper server jar ---------------------------------------------------------
if [[ ! -f "$SERVER_DIR/paper.jar" ]]; then
  echo ">> Resolving latest Paper $PAPER_VERSION build..."
  build_json="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${PAPER_VERSION}/builds/latest")"
  paper_url="$(echo "$build_json" | python3 -c 'import sys,json;print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
  echo ">> Downloading $paper_url"
  curl -fsSL -o "$SERVER_DIR/paper.jar" "$paper_url"
fi

echo "eula=true" > "$SERVER_DIR/eula.txt"

# --- Soft-dep server plugins --------------------------------------------------
download_modrinth() {
  # $1 = modrinth project slug, $2 = output filename
  local slug="$1" out="$2"
  [[ -f "$SERVER_DIR/plugins/$out" ]] && return 0
  local url
  url="$(curl -fsSL "https://api.modrinth.com/v2/project/${slug}/version?loaders=%5B%22paper%22%2C%22bukkit%22%2C%22spigot%22%5D&game_versions=%5B%22${PAPER_VERSION}%22%5D" \
    | python3 -c 'import sys,json;v=json.load(sys.stdin);f=[x for x in v[0]["files"] if x.get("primary")][0];print(f["url"])' 2>/dev/null || true)"
  if [[ -n "$url" ]]; then
    echo ">> Downloading $slug from $url"
    curl -fsSL -o "$SERVER_DIR/plugins/$out" "$url" || echo "!! Failed to fetch $slug (optional, continuing)"
  else
    echo "!! Could not resolve $slug for $PAPER_VERSION (optional, continuing)"
  fi
}

if [[ "${SKIP_SOFT_DEPS:-0}" != "1" ]]; then
  download_modrinth worldedit worldedit.jar
  download_modrinth worldguard worldguard.jar
  download_modrinth placeholderapi placeholderapi.jar
fi

# --- Deploy freshly built Aetherion jars --------------------------------------
echo ">> Deploying Aetherion plugin jars from build output..."
shopt -s nullglob
jars=("$REPO_ROOT"/*/target/*-1.0.0.jar)
if [[ ${#jars[@]} -eq 0 ]]; then
  echo "!! No built jars found. Run 'mvn -B clean install' first." >&2
  exit 1
fi
# Remove stale Aetherion/Boss jars, keep third-party soft-deps.
find "$SERVER_DIR/plugins" -maxdepth 1 -name '*-1.0.0.jar' -delete
cp "${jars[@]}" "$SERVER_DIR/plugins/"
echo ">> Deployed ${#jars[@]} plugin jars."

# --- server.properties (flat world, offline, small view distance) -------------
if [[ ! -f "$SERVER_DIR/server.properties" ]]; then
  cat > "$SERVER_DIR/server.properties" <<'PROPS'
online-mode=false
level-type=minecraft:flat
spawn-protection=0
max-players=20
view-distance=6
server-port=25565
motd=Aetherion Test Server
PROPS
fi

echo ">> Starting Paper $PAPER_VERSION (heap $MEMORY). Type 'stop' to shut down."
cd "$SERVER_DIR"
exec java -Xms1G -Xmx"$MEMORY" -jar paper.jar --nogui
