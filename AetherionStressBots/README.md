# Aetherion Stress / Test Bots

Mineflayer clients that join **mmo-r** like real players. Wave 1 + Wave 2 QA roles start from the **Dev menu** and **`/stressbots start`**. `/botreport` dumps activity. Phase 1 `StressM*` mining still works; `StressC*` still kits as a combat alias.

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
| `mine` | `QaMine01…` | TP to **Eldervale interior pads**, leash + dig ores/stone | Mixed T1–T4 mining + mining skills |
| `forage` | `QaForage01…` | TP to **Forage Isle grove/interior**, chop logs (and leaves if idle) | Mixed Kindling–Canopy axe + foraging skills |
| `catch` | `QaCatch01…` | Stay on solid habitat pads, throw catch spheres + timing clicks | Mixed catcher + spheres + varied pets |
| `roam` | `QaRoam01…` | Local hops on Origin slime pads + plugin pad-hop; flees husks/skeletons | Mixed combat kit |

## Wave 2 roles

| Role | Names | Does | Kit |
|------|-------|------|-----|
| `combat` | `QaCombat01…` | Leashed wander/attack near the combat pad (default Borderlands). **Velocity offline auth uses `QaCombat` like other Qa* names.** Set `prefixes.combat: StressC` only if you still want the Phase 1 names; `StressC*` still auto-kits as an alias. | Mixed combat T1–T4 + combat skills |
| `fish` | `QaFish01…` | Walk to water near forage/origin pads, cast and reel on the green strike bar | Mixed fishing kit + fishing skills |
| `trade` | `QaTrade01…` | `/ah` list+buy and `/bazaar` sell+buy via real GUIs. Provisioner unlocks **TRADER** + starter coins. | Mixed combat kit + surplus mats |
| `quest` | `QaQuest01…` | Walk to known FancyNPC pads (Maren / Twig / Eldervale), click Quest Offer accept | Mixed combat kit |
| `pad` | `QaPad01…` | Walk onto island jump pads, ride the arc, hop a different pad. `pad-flight-ms` covers mid-hop. | Mixed combat kit |

### Residual risks / limits

- **AH / Bazaar:** Trade bots get the **TRADER** flag + starter coins and click list/buy slots (AH list 49 / price 2 / confirm 11). Empty books still fail.
- **Quests:** Right-click NPCs, then click Quest Offer accept (slot 11) or chat `/aetherionquest accept <id>`. Full completion trees are still best-effort.
- **Fishing:** Casts and reels when the green strike bar / Bite! fires (`qaMinigame.biteUntil`). Water is scanned near grove/origin pads.
- **Combat:** Default pad is Borderlands `220.5 58 160.5` (hostiles). That zone killed **roam** bots; combat bots are geared for it but can still die if they walk off the waste. `StressC*` still matches as an alias.
- **Pads:** Real jump-pad centers including Forage lip `479.5 74 -240.5`. Mid-arc is covered by `pad-flight-ms` + leash 72 so the watchdog does not yank the hop.
- **Forage stuck (fixed):** Safety used to mark `activity=stuck` after ~10s of no movement and cancel the dig path while the bot was approaching a log. Dig/path-to-block now uses a longer freeze window, ignores still-closing distance to the target, and **retargets / wanderOnIsland** instead of staying stuck. Forage leash is 20; logs outside that disk are not chosen (`searchLeashBonus` only expands search within the island).
- **Skills:** XP only accrues on **equipped** loadout skills. Provisioner unlocks 3 slots and equips role skills. Bots do **not** `/skills setlevel`. There are no skill hotkeys — they peek `/skills` like a player opening the GUI.
- **Boosters:** Applied on the main-hand tool at provision (`BoosterApplier`). Runner also clicks leftover booster items / confirm GUIs when they appear.
- **Pets:** Catch bots throw spheres and click the timing window. Other roles vary: follow (equip), collection-only, wild spawn beside them, or none. Follow needs AetherMobs online.
- **Language:** `Language / Sprache` is forced to English on join (plugin + runner click slot 11).
- Still not automated: every spawn-unlock edge case, anvil booster fusion, full quest completion trees.

### Player-like mix (same PR)

Every role now kits mixed T1–T4 (catcher T1–T3) from the login index (`QaMine01` = T1, `02` = T2, …), equips skills so mining/foraging/fishing/combat actually levels, and runs a shared fidget/inventory/`/skills`/`/pets` loop. Goal is economy + stat + skill telemetry under **~40 concurrent** bots.

### Caps

`testbots.max-total: 40`, `max-per-start: 40`, per-role **8**. A mixed fleet of five roles × 8 fills the 40 cap; nine roles cannot all sit at 8 at once. Live `max-players` (60) is the other clamp. Copy these keys into the live `config.yml` — jar defaults do not overwrite an existing file.

### Catch limits (wave 1)

Bots **throw spheres** at nearby living entities. They do **not** play the catch timing minigame, so pets may flee or never complete. Habitat coordinates are YAML — paint real pet disks in DEV if the defaults miss live animals.

## Dev menu

1. `/dev` / `/adev` / `/devmenu` (op or `aetherion.dev`)
2. Next page (page 2) → **Testbots**
3. Per role: set count (left/right ±1, shift ±5) → **Start** / **Stop**. **More roles** pages combat / fish / trade / quest / pad.
4. Click the role icon for a list (nickname, xyz, held item, activity)
5. **Stop all** · **/botreport** book+chat

Login names stay `QaMine01…` (Velocity still has to recognize the role prefix). In chat, tab list, death messages, Dev menu, `/botreport`, and **Collection** top-3 they wear an ordinary Minecraft-style name (`MapleReed`, `IronWade`, `NovaKite`, …), different per role so two bots do not share a label. Edit `testbots.nicknames` in config. There is no Mysteries API — skipped.

Counts clamp to `testbots.max-total` (40), `testbots.caps.<role>` (8), `max-per-start` (40), and the server `max-players`.

## Commands

```
/stressbots start <mine|forage|catch|roam|combat|fish|trade|quest|pad> [count]
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
node src/index.js --combat 3 --fish 2 --trade 2 --quest 2 --pad 2
node src/index.js --mining 5    # Phase 1 StressM
```

Control HTTP: `http://127.0.0.1:18765` (`/health`, `/desired`, `/stop`, `/stop-all`, `/status`). Optional `control.token` must match plugin `testbots.runner.token`.

## Live dashboard

Open **`http://127.0.0.1:18765/`** on the machine running the runner (same bind as the control server, default localhost only). From a laptop, tunnel it:

```bash
ssh -L 18765:127.0.0.1:18765 <host>
```

Then open `http://127.0.0.1:18765/`. If `control.token` is set, use `http://127.0.0.1:18765/?token=<token>` so the page can call `/status`.

In game, `/stressbots dashboard` (alias `link`) posts a **clickable** chat URL. Set `testbots.dashboard.public-url` to the address players can actually open (a reverse proxy, or `http://HOST:18765` if that port is reachable). Each command asks the runner for a new session token (`testbots.dashboard.session-hours`, default 12) and appends `?token=`. The permanent `testbots.runner.token` is not put in chat. The runner still binds `127.0.0.1` until `control.bind` is changed or a proxy forwards the public URL. The page can start, stop, and set a role count as well as show the fleet. The page polls every 2s: name, role, personality, activity, goal, location, health, estimated coins, inventory, uptime, deaths, last error, and fleet economy totals.

## How a bot decides

Each login name gets a stable personality (patience, greed, caution, sociability, clumsiness, focus) plus a combat style (`aggressive`, `cautious`, `strafe`, `burst`, `balanced`) and an economy style (`saver`, `spender`, `flipper`, `casual`). The role loop still does the work the kit was built for. A slow mind tick (about every 3s, staggered) can linger, eat, retreat, look at someone nearby, say one short line, or walk off to list a resource stack on the Bazaar. Trade bots pick Auction House / Bazaar actions from purse, stock, and style instead of a fixed rotate, and they sometimes underprice, overprice, or close the GUI. Chat is capped fleet-wide so a full set of bots does not spam. Island leash, void hold, and plugin teleports are unchanged.

Plugin YAML anchors should stay in sync with `runner/config.json` mine/forage/catch/roam/combat/fish/trade/quest/pad sections.

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
| `leash-radius` | 12–20 | Plugin TPs back if farther from every role pad. Forage default **20** so nearby logs count |
| `void-floor-y` | 40 | Runner holds + plugin TPs. Raise only if a real pad is lower |
| `idle-reanchor-ticks` | 240 | Idle mine/forage/catch hop to another pad (~12s) |
| `pad-hop-ticks` | 1600 | Roam **and pad** plugin-teleport between Origin pads (~80s). `0` disables |
| `gatherStuckMs` / `digStuckMs` | 18s / 28s | Runner: freeze window while pathing-to-block / breaking. Do not treat digging as stuck |

`/botreport` activity: idle / pathing / mining / foraging / catching / roaming / fighting / fishing / trading / questing / hopping / browsing / void / recovering / stuck.

**Do not** add far cross-island roam/pad waypoints expecting the runner to walk them. The runner only hops `maxHop` blocks; distant pads are plugin teleports.

## In-game (legacy)

```
/stressbots list
/stressbots setup
/stressbots reload
```

Names: `QaCombat01…` (Wave 2) and `StressC01…` (alias) / `StressM01…` still auto-kit on join.

## Layout

- Plugin sources: `AetherionStressBots/`
- Runner on server: `/opt/aetherion-stress-bots/runner` (typical)
- Config: `runner/config.json` (local, gitignored) + `plugins/AetherionStressBots/config.yml`

`max-players` on MMO-R was raised to **60** for headroom.

## Staged roadmap

| Wave | Scope |
|------|--------|
| **1** | Foundation: role interface, registry, report DTO, Dev menu, `/botreport`, mine / forage / catch / roam |
| **2 (on main)** | Forage stuck/leash fix; combat (QaCombat), fish, trade/AH, quest NPC click, jump-pad hops; player-like skills/kits/pets; ~40 mixed cap |
| **3 (this)** | Language dismiss, real AH/Bazaar GUI list/buy, quest accept clicks, fishing strike/reel, pad-arc hops, spawn unlocks |
