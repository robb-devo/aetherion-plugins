# Aetherion Stress / Test Bots

Mineflayer clients that join **mmo-r** like real players and play the real loops: dig quarries, fell trees on the Fell bar, reel on the strike bar, lock catch timing, trade on AH/Bazaar, talk to quest NPCs, ride jump pads. They start from the **Dev menu** or **`/stressbots start`**. `/botreport` shows what each bot is doing and how much work the fleet got done. Phase 1 `StressM*` mining still works; `StressC*` still kits as a combat alias.

Status (2026-11-17): the plugin is not installed on mmo-r/mmo-d (removed 2026-10-19 for the beta) and the runner service is off. `testbots.enabled` defaults to `false`.

## Plugin or commands?

**Both.**

| Piece | What |
|-------|------|
| Plugin `AetherionStressBots` | Kits bots, sends them to role spots, keeps them safe, Dev-menu bridge, `/botreport` |
| Runner `runner/` (Node) | Mineflayer clients + localhost HTTP control plane |

Bots connect **offline to MMO-R** `127.0.0.1:25567` with Velocity modern-forwarding HMAC (not through the public proxy).

## Safety

1. Set `testbots.enabled: true` in `plugins/AetherionStressBots/config.yml` **before** Dev-menu start (default is **false**).
2. Keep the runner control server on `127.0.0.1` only.
3. `runner/config.json` holds only what is local: **`velocitySecret`**, host/port, control token. Everything else comes from `config.example.json` in the same checkout (merged key by key, `config.json` wins). The file is gitignored. Do not commit secrets.
4. **Stop all** from Dev menu or `/stressbots stopall`.
5. Trade bots only buy **other bots'** listings (sellers named `Qa…` / `Stress…`), so a test never spends bot coins on real players' items. `trade.buyFromPlayers: true` in the runner config turns that off.

## Roles

| Role | Names | Where | Does |
|------|-------|-------|------|
| `mine` | `QaMine01…` | Mining Eldervale quarry floors: Terraces, North Pit, Long Cut, Sunder Cut, West Hollow | Digs ores, then stone already in reach. Own void floor (`-40`) because the floors sit at Y 35–77 |
| `forage` | `QaForage01…` | Forage Eldervale: Landing, Streamside Hut, Bell Lodge, Ridge Watchtower, Frostpine Lodge | Clicks a log, then clicks again while the **Fell** bar is green. Skips trees on miss cooldown. Chop is unlocked on setup |
| `catch` | `QaCatch01…` | Elderwood Vale ground | Throws a sphere, looks up and left-clicks once while the catch bar shows **✦ Now** |
| `roam` | `QaRoam01…` | Origin slime pads | Local hops + plugin pad-hop; flees hostiles |
| `combat` | `QaCombat01…` | Live Hub spawn `borderlands` (fallback `220.5 58 160.5`) | Equips the sword, attacks hostiles inside the leash |
| `fish` | `QaFish01…` | Fishing Eldervale shores: Lakewatcher pier, Bait Shack, plaza landing, Harbourmaster's Hall | Casts at open water, waits out "Fishing • …", reels only while the strike bar is green (gold = perfect) |
| `trade` | `QaTrade01…` | Live Hub spawns `harbour` / `capital` | Lists gear on the AH and resources on the Bazaar at the suggested price, buys another bot's listing through the confirm page |
| `quest` | `QaQuest01…` | Quest NPC spots from AetherionQuests (follows NPCs when they move) | Walks to an unlisted NPC, right-clicks, answers the TalkUx dialog through its chat buttons (`/aetalk pick …`), accepts quests most of the time |
| `pad` | `QaPad01…` | Island jump pads | Walks onto a pad, rides the arc, picks another pad |

Every bot gets a mixed-tier kit from its login index (`QaMine01` = T1, `02` = T2, …), equipped role skills (XP only lands on equipped skills; bots never `/skills setlevel`), a booster on the main tool, sometimes a pet, and coins **topped up** to `starter-coins` (trade) or `pocket-coins` (everyone else). Coins are never added on top, so respawns and reconnects do not inflate the economy.

Timing skill is stable per bot (0.55–0.95): good bots wait for the gold/perfect cell, weak ones take the first green and now and then miss, so perfects, streaks and misses all show up in isle telemetry.

## How the bots read the server

Aetherion menu titles are pack glyphs, so nothing is matched by title:

- **Runner:** windows are told apart by their slots (market browse = writable book "Sell" in 49, price picker = four "coins (%)" buttons, confirm = lime/red wool on 11/15, quest offer = concrete, language = English/Deutsch). Titles, item names, lore, boss bars and chat are 1.21 NBT text components and are decoded with prismarine-chat.
- **Plugin:** menus are told apart by their `InventoryHolder` (MarketService browse/price/confirm, Quests LangMenu and quest GUIs).
- **Timing bars** are boss bars: `Fishing [..] REEL/NOW`, `Fell [..] CHOP/NOW` (plus `READY · LMB` preview and `… other trees free` cooldown), catch `Hold it` → `✦ Now` → `Locked`.
- A watchdog closes a stray menu after 7 s, like a player pressing Escape (trade and quest bots keep their own menus longer).
- Each loop runs one tick at a time (no overlapping ticks), and a bot that keeps getting kicked backs off (8 s, 16 s, 32 s … 2 min) and gives up after `reconnect.maxFails` short sessions in a row.

## Dev menu

1. `/dev` / `/adev` / `/devmenu` (op or `aetherion.dev`)
2. Next page (page 2) → **Testbots**
3. Per role: set count (left/right ±1, shift ±5) → **Start** / **Stop**. **More roles** pages combat / fish / trade / quest / pad.
4. Click the role icon for a list (nickname, xyz, held item, activity)
5. **Stop all** · **/botreport** book+chat

Login names stay `QaMine01…` (Velocity). In chat, tab list, death messages, Dev menu, `/botreport`, and **Collection** top-3 they wear a quirky nickname (`Pickel-Ute`, `Ast-Anni`, `Kugel-Kai`, …). Edit `testbots.nicknames` in config.

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

Runner calls (start/stop) run off the main thread and answer in chat. The runner's health is cached and refreshed in the background, so the Dev menu and `/botreport` never wait on HTTP.

`/botreport` dumps online bots (role, position, activity, deaths, errors, recent actions, TPS) and **work done** per bot and per role: `blocks`, `chops`, `casts`, `fish`, `throws`, `locks`, `hits`, `kills`, `talks`, `menus`, `recovers`.

## Host / runner

```bash
cd AetherionStressBots/runner
cp config.example.json config.json
# keep only host/port/velocitySecret/control in config.json; set velocitySecret locally

npm install
# Dev-menu daemon (0 bots until you start a role)
npm start -- --listen

# or CLI
node src/index.js --mine 5 --forage 3 --catch 2 --roam 5
node src/index.js --combat 3 --fish 2 --trade 2 --quest 2 --pad 2
node src/index.js --mining 5    # Phase 1 StressM

npm test                        # helper tests (window kinds, bars, dialog, trading, config merge)
```

Control HTTP: `http://127.0.0.1:18765` (`/health`, `/status`, `/desired`, `/stop`, `/stop-all`). `/status` lists every bot with uptime, reconnects, kicks, last kick reason and its stats (fells, reels, locks, buys, …). Optional `control.token` must match plugin `testbots.runner.token`.

The plugin's YAML decides **where** bots go. The runner keeps home where the plugin put the bot; a runner anchor only counts when the bot stands on it.

## Spots and safety

Spots come from three places, in this order per role: YAML `anchors`/`waypoints`, live Hub spawns listed in `hub-spawns`, and quest NPC spots when `use-quest-npcs: true`. A spot with no solid ground under it is skipped for the next one, never used as a void drop.

The plugin remembers where it last put each bot (setup, respawn, recover) and measures the leash from there. Landing on another configured pad makes that pad home. Fall damage is cancelled without a teleport (quarry drops, pad arcs); void damage and dropping under the role's floor teleport the bot home.

Live config: the plugin **patches** an existing `config.yml` on start (`testbots.config-version`). Role anchors that still equal what an older jar shipped move to the current spots, and new keys are added. Anchors you tuned stay as they are. Nothing else is overwritten.

| Key | Safe starting point | Notes |
|-----|---------------------|-------|
| `scatter-radius` | 3–5 | Random disk, solid-ground samples within 3 blocks of the spot's height only |
| `leash-radius` | 12–20 | Plugin TPs back if farther from home and from every role pad |
| `void-floor-y` | 40 (mine −40) | Global under `testbots.safety`, per role under `roles.<role>`. Runner uses `voidFloorY` the same way |
| `idle-reanchor-ticks` | 240 | Idle bots move to another spot (~12 s) |
| `pad-hop-ticks` | 1600 | Roam plugin-teleports between Origin pads (~80 s). `0` disables |
| `hub-spawns` | `[harbour, capital]` | Live `/hubadmin set` spawns as spots (trade, combat) |
| `use-quest-npcs` / `quest-npc-radius` | true / 1500 | Quest bots stand by saved NPC spots in their world |
| `gatherStuckMs` / `digStuckMs` | 18 s / 28 s | Runner: freeze window while pathing to a block / breaking |

`/botreport` activity: idle / pathing / mining / foraging / catching / roaming / fighting / fishing / trading / ah / bazaar / quest_dialog / minigame / pad_hop / browsing / void / recovering / stuck.

**Do not** add far cross-island roam/pad waypoints expecting the runner to walk them. The runner only hops `maxHop` blocks; distant pads are plugin teleports.

### Limits

- Quest bots run dialogs and accept offers; full quest completion trees are still best-effort.
- Catch spots are ground in the Vale; if no animals walk there, the catch bots wander and idle-reanchor.
- Combat bots can still die on the Borderlands waste; respawn puts them back home.
- Not automated: anvil booster fusion, every spawn-unlock edge case.

## Layout

- Plugin sources: `AetherionStressBots/`
- Runner on server: `/opt/aetherion-stress-bots/runner` (typical)
- Config: `runner/config.json` (local, gitignored) + `plugins/AetherionStressBots/config.yml`

`max-players` on MMO-R was raised to **60** for headroom.

## History

| Wave | Scope |
|------|--------|
| **1** | Foundation: role interface, registry, report DTO, Dev menu, `/botreport`, mine / forage / catch / roam |
| **2** | Forage stuck/leash fix; combat (QaCombat), fish, trade/AH, quest NPC click, jump-pad hops; player-like skills/kits/pets; ~40 mixed cap |
| **3** | Language dismiss, AH/Bazaar GUI list/buy, quest accept clicks, fishing strike/reel, pad-arc hops, spawn unlocks |
| **4 (2026-11)** | Bots play today's server: NBT text, glyph menus by contents/holder, Fell / strike / catch timing bars, TalkUx dialogs, quarry and isle spots from the live map, bots-only trading, reconnect backoff, work counters, no main-thread HTTP, live config patch |
