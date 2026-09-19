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
| `mine` | `QaMine01…` | TP to **Eldervale interior pads**, leash + dig ores/stone | Mining I |
| `forage` | `QaForage01…` | TP to **Forage Isle grove/interior**, chop logs (and leaves if idle) | Kindling I axe |
| `catch` | `QaCatch01…` | Stay on solid habitat pads, throw catch spheres (no void chase) | Catcher I + common spheres |
| `roam` | `QaRoam01…` | Local hops on Origin slime pads + plugin pad-hop; flees husks/skeletons | Combat I |

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

Plugin YAML anchors (Eldervale / Forage Isle / Origin pads) should stay in sync with `runner/config.json` mine/forage/catch/roam sections.

## Island safety / tuning

Skyblock pads are small. Wave 1 defaults now **refuse jump-pad lips, canopy-edge catch spots, and the borderlands husk waypoint**.

| Live death loop (2026-09-19) | What changed |
|------------------------------|--------------|
| catch `500.5 80 -200.5` void-after-TP | Removed. Catch sits on grove / return platform / Eldervale interior |
| forage `479.5 74 -240.5` (Canopy-Kalle) | Removed jump-pad lip. Grove `560.5 91 -200.5` + return platform + paste center |
| roam `220.5 58 160.5` husk/skeleton | Removed. Roam stays on Origin slime pads; plugin pad-hops, runner does not walk the void |
| death → world spawn `298 63 -400` → die again | Respawn location is the role pad immediately; void/fall damage cancelled; Y-floor watchdog TPs back |

Copy `testbots.safety` + the new `roles.*.anchors` into the **live** `plugins/AetherionStressBots/config.yml` (jar defaults do not overwrite an existing file), then `/stressbots reload`. Merge the matching `anchors` / `waypoints` into `runner/config.json` and restart `--listen`.

| Key | Safe starting point | Notes |
|-----|---------------------|-------|
| `scatter-radius` | 3–5 | Random disk, **solid-ground samples only**. 12–16 walks bots off pad lips |
| `leash-radius` | 12–16 | Plugin TPs back if farther from every role pad |
| `void-floor-y` | 40 | Runner holds + plugin TPs. Raise only if a real pad is lower |
| `idle-reanchor-ticks` | 240 | Idle mine/forage/catch hop to another pad (~12s) |
| `pad-hop-ticks` | 1600 | Roam plugin-teleports between Origin pads (~80s). `0` disables |

`/botreport` activity now includes `void`, `recovering`, and `stuck` besides idle/pathing/mining/foraging/catching/roaming.

**Do not** add far cross-island roam waypoints expecting the runner to walk them. The runner only hops `maxHop` (12) blocks; distant pads are plugin teleports.

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
