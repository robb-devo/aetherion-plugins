# Aetherion Stress Bots

Mineflayer load test: **Borderlands combat** + **Shabby Mine mining**.

## Plugin or commands?

**Both.**

| Piece | What |
|-------|------|
| Plugin `AetherionStressBots` | Auto-kits `StressC*` / `StressM*` with custom gear and TPs them into the zones |
| Commands (host) | `systemctl` / `npm start -- --combat N --mining N` to launch Mineflayer clients |

Bots connect **offline to MMO-R** `127.0.0.1:25567` with Velocity modern-forwarding HMAC (not through the public proxy).

## In-game

```
/stressbots list
/stressbots setup
/stressbots reload
```

## Host controls

```bash
# start default 5+5
systemctl start aetherion-stress-bots

# stop
systemctl stop aetherion-stress-bots

# scale (edit unit or run manually)
cd /opt/aetherion-stress-bots/runner
node src/index.js --combat 15 --mining 10
```

Names: `StressC01…` / `StressM01…`

## Layout

- Plugin sources: `AetherionStressBots/`
- Runner on server: `/opt/aetherion-stress-bots/runner`
- Config: `runner/config.json` (local, gitignored) + `plugins/AetherionStressBots/config.yml`

Copy the example runner config, then set the **live Velocity forwarding secret only on the server / local runner env**. Do not commit a real `velocitySecret`.

```bash
cp runner/config.example.json runner/config.json
# edit runner/config.json → velocitySecret = the server's proxies.velocity.secret
```

`max-players` on MMO-R was raised to **60** for headroom.
