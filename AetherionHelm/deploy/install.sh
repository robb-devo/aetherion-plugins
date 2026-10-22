#!/bin/bash
# Aetherion Helm: installs or updates the panel on the Hetzner host. Run as root from the staging folder that
# deploy-helm.ps1 uploads (release files + this script). Safe to run again: it updates in place and keeps
# helm.json, the database, accounts and the agent token.
#
#   bash install.sh [--host panel.donnernet.de] [--cf-token-file FILE] [--tunnel]
#
#   --cf-token-file  token of a tunnel created in the Cloudflare dashboard (service points at http://127.0.0.1:8787)
#   --tunnel         set the tunnel up from here instead: prints a Cloudflare login link, then creates the tunnel
#                    and the DNS record for --host. Only the panel subdomain is touched, nothing else on the domain.
#
# Accounts: an optional accounts.txt next to this script, one "username|display|role|mcName|password" per line,
# is read once and deleted. Result lines go to result.txt for the deploy script.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
OPS=/root/aetherion-ops
APP=/opt/aetherion-helm
HOST=panel.donnernet.de
CF_TOKEN_FILE=""
TUNNEL=0
IDS=(proxy hub mmor mmod mmoc mmoi)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --cf-token-file) CF_TOKEN_FILE="$2"; shift 2 ;;
    --tunnel) TUNNEL=1; shift ;;
    *) echo "unbekannte Option: $1"; exit 2 ;;
  esac
done

say() { printf '\n== %s\n' "$*"; }
die() { printf 'STOP: %s\n' "$*" >&2; exit 1; }
RESULT="$HERE/result.txt"
: > "$RESULT"

# ------------------------------------------------------------------------------------------------ checks
say "Prüfe Voraussetzungen"
[[ $EUID -eq 0 ]] || die "bitte als root ausführen"
[[ -f $OPS/ae-lib.sh ]] || die "$OPS/ae-lib.sh fehlt"
. "$OPS/ae-lib.sh"
for f in helm-gateway.jar AetherionHelm.jar AetherionHelmProxy.jar helm-web.zip helm-ctl aetherion-helm.service; do
  [[ -f $HERE/$f ]] || die "$f fehlt im Staging-Ordner"
done
JAVA=$(command -v java) || die "java ist nicht installiert"
JMAJOR=$("$JAVA" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
(( JMAJOR >= 21 )) || die "java auf dem PATH ist Version $JMAJOR, gebraucht wird 21 oder neuer"
command -v python3 >/dev/null || die "python3 fehlt"
command -v sudo >/dev/null || die "sudo fehlt"
echo "ok: java $JMAJOR ($JAVA), python3, sudo"

# ------------------------------------------------------------------------------------------------ user + files
say "Installiere das Panel nach $APP"
id helm >/dev/null 2>&1 || useradd --system --home-dir "$APP" --no-create-home --shell /usr/sbin/nologin helm
install -d -m 755 "$APP" "$APP/bin"
install -d -m 700 -o helm -g helm "$APP/data"
[[ -f $APP/helm-gateway.jar ]] && cp -p "$APP/helm-gateway.jar" "$APP/helm-gateway.jar.bak"
install -m 644 "$HERE/helm-gateway.jar" "$APP/helm-gateway.jar"
install -m 755 -o root -g root "$HERE/helm-ctl" "$APP/bin/helm-ctl"

SUDOERS=/etc/sudoers.d/aetherion-helm
echo "helm ALL=(root) NOPASSWD: $APP/bin/helm-ctl" > "$SUDOERS.tmp"
chmod 440 "$SUDOERS.tmp"
visudo -cf "$SUDOERS.tmp" >/dev/null || die "sudoers-Regel ist ungültig"
mv "$SUDOERS.tmp" "$SUDOERS"

rm -rf "$APP/web.new"
python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$HERE/helm-web.zip" "$APP/web.new"
[[ -f $HERE/aetherion-helm.apk ]] && cp "$HERE/aetherion-helm.apk" "$APP/web.new/aetherion-helm.apk"
chmod -R a+rX "$APP/web.new"
[[ -d $APP/web ]] && mv "$APP/web" "$APP/web.old"
mv "$APP/web.new" "$APP/web"
rm -rf "$APP/web.old"
echo "ok: Gateway, Web-App$( [[ -f $HERE/aetherion-helm.apk ]] && echo ', APK'), helm-ctl, sudo-Regel"

# ------------------------------------------------------------------------------------------------ helm.json
say "Konfiguration"
SERVERS_TSV="$HERE/servers.tsv"
: > "$SERVERS_TSV"
for id in "${IDS[@]}"; do
  dir=$(ae_resolve "$id" 2>/dev/null) || { echo "warn: $id nicht gefunden, übersprungen"; continue; }
  [[ -d $dir ]] || { echo "warn: $dir fehlt, $id übersprungen"; continue; }
  if [[ $id == proxy ]]; then
    kind=velocity
    port=$(sed -n 's/^bind *= *"[^:]*:\([0-9]*\)".*/\1/p' "$dir/velocity.toml" 2>/dev/null | head -1)
  else
    kind=paper
    port=$(sed -n 's/^server-port=\([0-9]*\).*/\1/p' "$dir/server.properties" 2>/dev/null | head -1)
  fi
  xmx=$(ae_java_flags "$id" | sed -n 's/.*-Xmx\([0-9]*[GgMm]\).*/\1/p')
  printf '%s\t%s\t%s\t%s\t%s\n' "$id" "$kind" "${port:-0}" "${xmx:-}" "$dir" >> "$SERVERS_TSV"
done
python3 - "$APP/helm.json" "$SERVERS_TSV" "$HOST" <<'PY'
import json, os, secrets, sys
path, tsv, host = sys.argv[1:4]
names = {"proxy": ("Proxy", "Velocity-Proxy"), "hub": ("Hub", "Lobby"), "mmor": ("MMO-R", "Hauptwelt"),
         "mmod": ("MMO-D", "Dungeons"), "mmoc": ("MMO-C", "Exile & Story"), "mmoi": ("MMO-I", "Inseln")}
cfg = json.load(open(path)) if os.path.exists(path) else {}
cfg.setdefault("bind", "127.0.0.1")
cfg.setdefault("port", 8787)
cfg.setdefault("agentPort", 8788)
if len(cfg.get("agentToken", "")) < 24:
    cfg["agentToken"] = secrets.token_urlsafe(32)
cfg["publicOrigin"] = "https://" + host
cfg.setdefault("dataDir", "/opt/aetherion-helm/data")
cfg.setdefault("webDir", "/opt/aetherion-helm/web")
cfg.setdefault("timezone", "Europe/Berlin")
old = {s.get("id"): s for s in cfg.get("servers", [])}
servers = []
for line in open(tsv):
    sid, kind, port, xmx, d = line.rstrip("\n").split("\t")
    name, role = names.get(sid, (sid, ""))
    s = {"id": sid, "name": name, "role": role, "kind": kind, "port": int(port or 0), "xmx": xmx, "dir": d}
    # names and roles changed by hand in helm.json stay
    for k in ("name", "role"):
        if old.get(sid, {}).get(k):
            s[k] = old[sid][k]
    servers.append(s)
cfg["servers"] = servers
tmp = path + ".tmp"
with open(tmp, "w") as f:
    json.dump(cfg, f, indent=2, ensure_ascii=False)
os.replace(tmp, path)
PY
chown root:helm "$APP/helm.json"
chmod 640 "$APP/helm.json"
TOKEN=$(python3 -c "import json; print(json.load(open('$APP/helm.json'))['agentToken'])")
awk -F'\t' '{ printf "   %-6s %-9s Port %-6s Xmx %s\n", $1, $2, $3, $4 }' "$SERVERS_TSV"

# ------------------------------------------------------------------------------------------------ agents
say "Agent-Plugins in die Server legen (wirken nach dem nächsten Neustart)"
while IFS=$'\t' read -r id kind port xmx dir; do
  plugins="$dir/plugins"
  [[ -d $plugins ]] || { echo "warn: $plugins fehlt, $id übersprungen"; continue; }
  owner=$(stat -c '%U:%G' "$plugins")
  if [[ $kind == velocity ]]; then
    jar=AetherionHelmProxy.jar; pattern='AetherionHelmProxy*.jar'; cfgdir="$plugins/aetherionhelm"; cfgfile="$cfgdir/config.properties"
  else
    jar=AetherionHelm.jar; pattern='AetherionHelm-*.jar'; cfgdir="$plugins/AetherionHelm"; cfgfile="$cfgdir/config.yml"
  fi
  # exactly one jar per plugin per server
  find "$plugins" -maxdepth 1 -name "$pattern" -delete
  [[ $kind == paper ]] && rm -f "$plugins/AetherionHelm.jar"
  install -m 644 -o "${owner%%:*}" -g "${owner##*:}" "$HERE/$jar" "$plugins/$jar"
  install -d -o "${owner%%:*}" -g "${owner##*:}" "$cfgdir"
  python3 - "$cfgfile" "$kind" "$TOKEN" "$id" <<'PY'
import os, re, sys
path, kind, token, sid = sys.argv[1:5]
if kind == "velocity":
    keys = {"gateway": "ws://127.0.0.1:8788/agent", "token": token, "server": sid}
    text = open(path).read() if os.path.exists(path) else "# Aetherion Helm agent (proxy). Written by install.sh.\n"
    for k, v in keys.items():
        if k == "gateway" and re.search(r"(?m)^gateway=", text):
            continue
        line = f"{k}={v}"
        text, n = re.subn(rf"(?m)^{k}=.*$", line, text)
        if not n:
            text += ("" if text.endswith("\n") else "\n") + line + "\n"
else:
    if os.path.exists(path):
        text = open(path).read()
    else:
        text = ("# Aetherion Helm agent. Written by install.sh; only the token is secret.\n"
                "gateway: ws://127.0.0.1:8788/agent\ntoken: ''\nserver: ''\n"
                "mute-blocked-commands: [msg, tell, w, whisper, r, reply, me, pm, dm, mail, m, t]\n"
                "report-cooldown-seconds: 60\n")
    for k, v in {"token": token, "server": sid}.items():
        line = f"{k}: '{v}'"
        text, n = re.subn(rf"(?m)^{k}:.*$", line, text)
        if not n:
            text += line + "\n"
with open(path, "w") as f:
    f.write(text)
PY
  chown "$owner" "$cfgfile"
  chmod 640 "$cfgfile"
  echo "   $id: $jar"
done < "$SERVERS_TSV"

# ------------------------------------------------------------------------------------------------ service
say "Dienst starten"
sed "s#/usr/bin/env java#$JAVA#" "$HERE/aetherion-helm.service" > /etc/systemd/system/aetherion-helm.service
systemctl daemon-reload
systemctl enable aetherion-helm >/dev/null 2>&1
systemctl restart aetherion-helm
for _ in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:8787/api/health >/dev/null 2>&1; then break; fi
  sleep 0.5
done
curl -fsS http://127.0.0.1:8787/api/health >/dev/null 2>&1 || { journalctl -u aetherion-helm -n 40 --no-pager; die "das Panel antwortet nicht"; }
echo "ok: Panel läuft auf 127.0.0.1:8787 (nur lokal, nach außen über den Tunnel)"
echo "HEALTH ok" >> "$RESULT"

# ------------------------------------------------------------------------------------------------ accounts
if [[ -f $HERE/accounts.txt ]]; then
  say "Konten"
  while IFS='|' read -r user display role mc pw; do
    [[ -z $user ]] && continue
    out=$(printf '%s\n' "$pw" | sudo -u helm "$JAVA" -jar "$APP/helm-gateway.jar" --config "$APP/helm.json" user add "$user" "$display" "$role" "$mc" 2>&1 | tail -1) || true
    case "$out" in
      created*) echo "   $user: angelegt"; echo "ACCOUNT created $user" >> "$RESULT" ;;
      exists*) echo "   $user: gibt es schon, bleibt wie es ist"; echo "ACCOUNT exists $user" >> "$RESULT" ;;
      *) echo "   $user: Fehler: $out"; echo "ACCOUNT failed $user" >> "$RESULT" ;;
    esac
  done < "$HERE/accounts.txt"
  shred -u "$HERE/accounts.txt" 2>/dev/null || rm -f "$HERE/accounts.txt"
fi

# ------------------------------------------------------------------------------------------------ cloudflare
install_cloudflared() {
  command -v cloudflared >/dev/null && return 0
  echo "installiere cloudflared (offizielles Paket von Cloudflare)"
  install -d -m 755 /usr/share/keyrings
  curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg -o /usr/share/keyrings/cloudflare-main.gpg
  echo 'deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared any main' > /etc/apt/sources.list.d/cloudflared.list
  apt-get update -qq && apt-get install -y -qq cloudflared
}

# A cloudflared service this installer did not set up belongs to something else (maybe the main site): never touch
# it. The panel then gets a public hostname on that tunnel instead, which works because it runs on this host too.
OURS="$APP/.cloudflared-ours"
foreign_tunnel() {
  [[ -f $OURS ]] && return 1
  systemctl cat cloudflared >/dev/null 2>&1
}
if [[ -n $CF_TOKEN_FILE || $TUNNEL == 1 ]] && foreign_tunnel; then
  say "Cloudflare-Tunnel"
  echo "Auf dem Server läuft schon ein Cloudflare-Tunnel, der nicht vom Panel stammt. Er bleibt unangetastet."
  echo "Im Cloudflare-Dashboard bei diesem Tunnel einen Public Hostname anlegen: $HOST -> http://localhost:8787"
  echo "TUNNEL foreign" >> "$RESULT"
  [[ -n $CF_TOKEN_FILE ]] && { shred -u "$CF_TOKEN_FILE" 2>/dev/null || rm -f "$CF_TOKEN_FILE"; }
elif [[ -n $CF_TOKEN_FILE ]]; then
  say "Cloudflare-Tunnel (Token aus dem Dashboard)"
  install_cloudflared
  if [[ -f $OURS ]]; then cloudflared service uninstall >/dev/null 2>&1 || true; fi
  cloudflared service install "$(tr -d ' \r\n' < "$CF_TOKEN_FILE")"
  shred -u "$CF_TOKEN_FILE" 2>/dev/null || rm -f "$CF_TOKEN_FILE"
  touch "$OURS"
  echo "TUNNEL token" >> "$RESULT"
elif [[ $TUNNEL == 1 ]]; then
  say "Cloudflare-Tunnel für $HOST"
  install_cloudflared
  if [[ ! -f /root/.cloudflared/cert.pem ]]; then
    echo "Gleich erscheint ein Link. Öffne ihn, melde dich bei Cloudflare an und wähle die Domain ${HOST#*.}."
    cloudflared tunnel login
  fi
  if ! cloudflared tunnel list 2>/dev/null | grep -qw aetherion-helm; then
    cloudflared tunnel create aetherion-helm
  fi
  TID=$(cloudflared tunnel list -o json 2>/dev/null | python3 -c "import json,sys; print(next(t['id'] for t in json.load(sys.stdin) if t['name']=='aetherion-helm'))")
  install -d -m 755 /etc/cloudflared
  install -m 600 "/root/.cloudflared/$TID.json" "/etc/cloudflared/$TID.json"
  cat > /etc/cloudflared/config.yml <<EOF
# Aetherion Helm: only this hostname goes through the tunnel, to the panel on loopback.
tunnel: $TID
credentials-file: /etc/cloudflared/$TID.json
ingress:
  - hostname: $HOST
    service: http://127.0.0.1:8787
  - service: http_status:404
EOF
  # only creates the record for this one subdomain; it refuses if the name is already taken
  cloudflared tunnel route dns aetherion-helm "$HOST" || echo "warn: DNS-Eintrag für $HOST gibt es schon; prüfe in Cloudflare, dass er auf den Tunnel zeigt"
  if [[ -f $OURS ]]; then
    systemctl restart cloudflared
  else
    cloudflared --config /etc/cloudflared/config.yml service install
    touch "$OURS"
  fi
  echo "TUNNEL login" >> "$RESULT"
fi

if [[ -f $OURS ]] && systemctl is-active cloudflared >/dev/null 2>&1; then
  echo "ok: Cloudflare-Tunnel läuft"
  echo "TUNNEL active" >> "$RESULT"
fi

say "Fertig"
echo "Die Agent-Plugins laden beim nächsten Neustart der Server."
