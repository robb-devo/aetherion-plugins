# Aetherion Hetzner / Crafty — facts for agents

## Truth

- Live servers are on **Hetzner**, not Desktop Crafty.
- Desktop path `C:\Users\Robbi\Desktop\Minecraft-Network\...` is a **stale mirror**. Never deploy there.
- SSH alias: **`aetherion-hetzner`** → `root@135.181.18.162` with `~/.ssh/aetherion_ed25519`
- Local LAN Host **`aetherion`** (`192.168.178.117`) is unrelated — do not use it for Crafty ops (it hangs / times out).

## Paths

| Role | Value |
|------|--------|
| Ops local | `C:\Users\Robbi\IdeaProjects\aetherion-ops` |
| Ops remote | `/root/aetherion-ops` |
| Crafty root | `/var/opt/minecraft/crafty` |
| Servers | `/var/opt/minecraft/crafty/servers/<uuid>` |
| MMO-R uuid | `a28d676a-03ef-40f1-9ac7-7a21c2ef6383` |
| Panel | `https://135.181.18.162:8443` |

## Ports

Proxy 25565 · Hub 25566 · MMO-R 25567 · MMO-D 25568 · MMO-C 25569

## Commands (preferred)

From Windows (agent should run these, not reinvent SSH):

```powershell
cd C:\Users\Robbi\IdeaProjects\aetherion-ops\bin
.\ae.ps1 status
.\ae.ps1 deploy-items              # build + upload + remapper clear, NO restart
.\ae.ps1 deploy-items -Restart     # same + stop/start + wait Done
.\ae.ps1 start mmor
.\ae.ps1 stop mmor                 # graceful (LOCKED countdown)
.\ae.ps1 stop mmor -Now            # emergency only
.\ae.ps1 restart mmor
```

Remote one-liners (same scripts):

```bash
ssh aetherion-hetzner 'bash /root/aetherion-ops/ae-status.sh'
```

## Rules

1. Never invent temp bash + scp one-offs when `ae.ps1` covers it.
2. Default deploy = **JAR only**. Restart only when user asks / `-Restart`.
3. Prefer `stop` over SIGKILL so ShutdownCountdown (LOCKED) runs. Use `-Now` only if user wants emergency.
4. After Items deploy, remapper clear is mandatory (script does it).
5. Do not put passwords into chat, skills, or this repo. Panel creds live on the server (`default-creds.txt`). Local ACCESS notes stay in Temp only.
