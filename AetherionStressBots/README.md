# Aetherion Stress / Test Bots

Mineflayer clients that join **mmo-r** like real players. Wave 1 adds QA roles with **Dev-menu** start/stop and **`/botreport`**. Phase 1 combat/mining stress still works.

## Plugin or commands?

**Both.**

| Piece | What |
|-------|------|
| Plugin `AetherionStressBots` | Kits bots, TPs them to role anchors, Dev-menu bridge, `/botreport` |
| Runner `runner/` (Node) | Mineflayer clients + localhost HTTP control plane |

Bots connect **offline to MMO-R** `127.0.0.1:25567` with Velocity modern-forwarding HMAC (not through the public proxy).

## Safety

1. Set `testbots.enabled: true` in `plugins/AetherionStressBots/config.yml` **before** Dev-menu start (default is **false**).
2. Keep the runner control server on `127.0.0.1` only.
3. Copy `runner/config.example.json` → `runner/config.json` and set **`velocitySecret` only on the server**. That file is gitignored. Do not commit secrets.
4. **Stop all** from Dev menu or `/stressbots stopall`.

## Wave 1 roles

| Role | Names | Does | Kit |
|------|-------|------|-----|
| `mine` | `QaMine01…` | TP to **Eldervale mining isle**, path to ores, dig with a starter pick | Mining I |
| `forage` | `QaForage01…` | TP to **Forage Isle**, chop logs | Kindling I axe |
| `catch` | `QaCatch01…` | Roam pet-habitat anchors, throw catch spheres at nearby entities | Catcher I + common spheres |
| `roam` | `QaRoam01…` | Walk Origin pad / borderlands-approach waypoints, occasional jump/look/swing | Combat I |

**Not in wave 1:** Auction House / Bazaar, quest NPC dialogue, jump pads as pathing, spawn unlocks, equip-swap UI, full progression.

### Catch limits (wave 1)

Bots **throw spheres** at nearby living entities. They do **not** play the catch timing minigame, so pets may flee or never complete. Habitat coordinates are YAML — paint real pet disks in DEV if the defaults miss live animals.

## Dev menu

1. `/dev` / `/adev` / `/devmenu` (op or `aetherion.dev`)
2. Next page (page 2) → **Testbots**
3. Per role: set count (left/right ±1, shift ±5) → **Start** / **Stop**
4. Click the role icon for a list (nickname, xyz, held item, activity)
5. **Stop all** · **/botreport** book+chat

Login names stay `QaMine01…` (Velocity). In chat, tab list, death messages, Dev menu, `/botreport`, and **Collection** top-3 they wear a quirky nickname (`Pickel-Ute`, `Ast-Anni`, `Kugel-Kai`, `Flaneur-Franz`, …). Edit `testbots.nicknames` in config. There is no Mysteries API — skipped.

Counts clamp to `testbots.max-total`, `testbots.caps.<role>`, `max-per-start` (1–20 UI), and the server `max-players`.

## Commands

```
/stressbots start <mine|forage|catch|roam> [count]
/stressbots stop <role|all>
/stressbots stopall
/stressbots list
/stressbots setup
/stressbots reload
/botreport
```

Permission: op, `aetherion.stressbots.admin`, or `aetherion.dev`.

`/botreport` dumps online bots (role, position, deaths, errors, recent actions, TPS hint) to chat and a written book. Same text is useful for AI QA.

## Host / runner

```bash
cd AetherionStressBots/runner
cp config.example.json config.json
# set velocitySecret = proxies.velocity.secret  (local only)

npm install
# Dev-menu daemon (0 bots until you start a role)
npm start -- --listen

# or CLI
node src/index.js --mine 5 --forage 3 --catch 2 --roam 5
node src/index.js --combat 5 --mining 5    # Phase 1 StressC / StressM
```

Control HTTP: `http://127.0.0.1:18765` (`/health`, `/desired`, `/stop`, `/stop-all`). Optional `control.token` must match plugin `testbots.runner.token`.

Plugin YAML anchors (Eldervale / Forage Isle / Origin pads) should stay in sync with `runner/config.json` mine/forage/roam sections.

## In-game (legacy)

```
/stressbots list
/stressbots setup
/stressbots reload
```

Names: `StressC01…` / `StressM01…` still auto-kit on join.

## Layout

- Plugin sources: `AetherionStressBots/`
- Runner on server: `/opt/aetherion-stress-bots/runner` (typical)
- Config: `runner/config.json` (local, gitignored) + `plugins/AetherionStressBots/config.yml`

`max-players` on MMO-R was raised to **60** for headroom.

## Staged roadmap

| Wave | Scope |
|------|--------|
| **1 (this)** | Foundation: role interface, registry, report DTO, Dev menu, `/botreport`, mine / forage / catch / roam |
| Later | AH/Bazaar trading, quest NPC talks, jump pads as primary pathing, spawn unlocks, equip swapping, full progression play |
