# Aetherion Helm

Staff panel for the network: live state of every server and the host, players with inventory and ender chest,
console, punishments, reports, chat, audit log. Web app (PWA) plus an Android app; German UI.

| Part | What it is | Build |
|---|---|---|
| `gateway/` | Java 21 service: login with mandatory 2FA, roles, audit log, history, host monitor, alerts, REST + live socket, serves the web app | `mvn -f gateway/pom.xml package` → `helm-gateway.jar` |
| `agent-paper/` | Paper plugin on every backend: TPS/MSPT, players, inventories, console, moderation, lag radar, `/report` | `mvn -f agent-paper/pom.xml package` |
| `agent-velocity/` | Velocity plugin on the proxy: network player list, proxy bans, send/kick/broadcast, backend pings (Java 25) | `mvn -f agent-velocity/pom.xml package` |
| `web/` | React + Vite app, liquid-glass design, light and dark; `android/` is the Capacitor shell that opens the live panel | `npm ci && npm run build` |
| `deploy/` | `install.sh` (runs on the host), `helm-ctl` (the only root command the gateway may run), systemd unit, `deploy-helm.ps1` | — |

Agents connect to the gateway over loopback with the shared token from `helm.json`; nothing is exposed except the panel
itself, which goes out through a Cloudflare tunnel. Power actions go through the existing ops scripts (graceful stop with
the countdown, stop markers for the watchdog).

## Release and deploy

Every push to `main` or `claude/aetherion-helm` that touches this folder runs `.github/workflows/aetherion-helm.yml`. It
builds the jars, the web app and the APK and replaces the GitHub release `aetherion-helm`.

From Robb's PC: `powershell -ExecutionPolicy Bypass -File deploy-helm.ps1 [-CloudflareToken <token>]`. It downloads the
release, uploads it to a staging folder on the host and runs `install.sh`, which is safe to run again (keeps `helm.json`,
the database, accounts and the agent token). New accounts get generated passwords, shown once. After the install, one
restart of every server loads the agents.

The APK is signed with the key from the secrets `HELM_KEYSTORE_B64`, `HELM_KEYSTORE_PASSWORD`, `HELM_KEY_ALIAS`,
`HELM_KEY_PASSWORD`; without them each build gets a throwaway key. UI changes need no new APK.

## Local work

- `npm run dev` in `web/` (expects a gateway on the same origin), `npm run build:demo` builds a click-through preview with
  sample data (`src/lib/demo.ts`, left out of the real build).
- `java -jar helm-gateway.jar --config helm.json user add|passwd|reset-2fa|list` manages accounts (password on stdin).
