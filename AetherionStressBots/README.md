# Aetherion Stress / Test Bots

Mineflayer clients that log in **through the proxy like real players** and play the whole network: the hub's Pit, every
job on the main world (quarries, Fell bar trees, strike-bar fishing, catch timing, farms, AH/Bazaar, quest NPCs, jump
pads, menus, slots, parties), dungeons on mmo-d, the exile on mmo-c and private islands on mmo-i. They walk between
servers on the players' own roads (`/aetherion`, `/dhub`, `/exile go`, `/island` …), and `/botreport` on any
backend shows which roads worked, how long each menu took to open, what got done and what broke.

They have real names: German puns per job (`Kumpel_Kalle` digs, `Fischers_Fritz` fishes, `Kerker_Kurt` runs
dungeons, `Insel_Ingo` lives on his island) and the **Testbot_** family (`Testbot_Toni`, `Testbot_Tina` …), players
who tour everything. `names.style: "qa"` brings back `QaMine01`-style names.

Status (2026-11-18): plugin on mmo-r, mmo-d, mmo-c and mmo-i once `deploy-stressbots.ps1` has run; the proxy door
ships in AetherionProxy. `testbots.enabled` defaults to `false`.

## Pieces

| Piece | What |
|-------|------|
| Plugin `AetherionStressBots` (every backend but the hub) | Kits bots, sends them to role spots, keeps them out of the void, the hidden `/testbot` helper, Dev-menu bridge, `/botreport` |
| Proxy door in `AetherionProxy` | Lets a bot log in offline when its handshake carries the secret token (loopback only); everyone else logs in online as before |
| Runner `runner/` (Node) | The Mineflayer players, the roads between servers, the network telemetry, a localhost HTTP control plane |

### How a bot gets in

`connect.mode: "proxy"` (default): the bot connects to the proxy with `<token>.testbots` as the host it typed. The
proxy's `plugins/aetherion-proxy/testbots.properties` (`enabled`, `token`, `loopback-only`) has the same token; a
match from 127.0.0.1 is let in offline, so the backends see an offline (version 3) UUID and know it is a bot. The
proxy pack is accepted like a player would. Logins are spaced 3.5 s apart (Velocity allows one per address every 3 s).

`connect.mode: "direct"`, or no token set: straight into MMO-R with forged Velocity forwarding (`velocitySecret`), the
old setup. Pit bots need the proxy (the Pit is on the hub).

### The `/testbot` helper

Only bots can run it (the permission is attached on join to offline-UUID players; everyone else gets "Unknown
command"). It answers with one plain `[tb] …` line the runner reads:

| Call | Answer |
|------|--------|
| `hello <role> <slot> [setup] [session]` | Which server and world this is; `setup` clears, kits and sends the bot to its role spot |
| `where` | Server, world, position |
| `stage <spot>` | Teleport to a live Hub spawn (`farm`, `capital` …) or a role's spot (`mine`, `fish` …) |
| `near <hostile\|npc\|villager\|animal\|item\|chest\|name:text> [radius]` | The closest one, server side (a dungeon's next mob, a loot cache, the Dungeon Keeper) |
| `quest` | Where the bot's open quest points |
| `goto <server>` | Core's travel pipeline: the fallback when a player road did not move the bot |

The hub has no plugin, so a `hello` that nobody answers means "on the hub".

## Roles

### Main-world jobs (they walk from the hub to MMO-R, get kitted and stay)

| Role | Names | Where | Does |
|------|-------|-------|------|
| `mine` | `Kumpel_Kalle`, `Stollen_Steffi` … | Mining Eldervale quarry floors | Digs ores, then stone in reach. Own void floor (`-40`) |
| `forage` | `Axel_Schweiss`, `Holzhacker_Hans` … | Forage Eldervale lodges | Clicks a log, clicks again while the **Fell** bar is green |
| `catch` | `Kugel_Kai`, `Netz_Norbert` … | Elderwood Vale | Throws a sphere, locks on **✦ Now** |
| `roam` | `Wander_Willi`, `Bummel_Berta` … | Origin slime pads | Local hops + plugin pad-hop; flees hostiles |
| `combat` | `Haudrauf_Harald`, `Kloppe_Klaus` … | Hub spawn `borderlands` | Fights hostiles inside the leash |
| `fish` | `Fischers_Fritz`, `Hering_Horst` … | Fishing Eldervale shores | Casts, reels only on green (gold = perfect) |
| `trade` | `Feilsch_Fred`, `Taler_Theo` … | `harbour` / `capital` | Lists on AH and Bazaar, buys other bots' listings |
| `quest` | `Quassel_Quentin`, `Plauder_Paula` … | Quest NPC spots | Talks to NPCs, answers TalkUx dialogs, accepts quests |
| `pad` | `Hupf_Hubert`, `Flummi_Flo` … | Island jump pads | Rides the arcs |
| `farm` | `Bauer_Bernd`, `Ruebe_Rudi` … | `/farm` (else the `farm` Hub spawn) | Harvests ripe crops with the farming hoe |
| `menus` | `Klick_Klaus`, `Menue_Manni` … | anywhere | Opens every player menu (`/aetherion`, `/skills`, `/collection`, `/codex`, `/bestiary`, `/pets`, `/bazaar`, `/ah`, `/shardshop`, `/trades`, `/atrecipes`, `/spawns`, `/gamble`), times it, pages forward on arrows only |
| `casino` | `Zocker_Zoe`, `Jackpot_Jens` … | `/gamble slots` | A few default-bet spins per visit, spin button only |
| `social` | `Kumpel_Kurti`, `Freundin_Frida` … | MMO-R | Parties of four with other bots: invite, accept, `/party` chat, leave, disband, new mix every 3 min |

### Network roles

| Role | Names | Does |
|------|-------|------|
| `general` | `Testbot_Toni`, `Testbot_Tina` … | **Tours everything**: picks a step (any job above, menus, slots, a dungeon run, the exile, an island, the Pit), plays it 1–12 minutes, cleans up and picks the next. Weights in `general.weights` (0 turns a step off) |
| `pit` | `Pit_Bulli`, `Knock_Knut` … | Hub Pit: `/pit enter`, fights **only other bots of this runner**, jump crits and strafes, walks back in after a death |
| `dungeon` | `Kerker_Kurt`, `Gruft_Gerda` … | `/dhub`, parties of four (lowest slot leads and opens Floor 1 at the Dungeon Keeper), tell the Gate Warden "ready", fight room to room, loot the cache, leave. **Yields to people**: never starts while a person is on mmo-d, leaves a run when one shows up, 8 min per run at most (Dungeons runs one instance at a time) |
| `exile` | `Verbannt_Bernd`, `Exil_Erwin` … | `/exile go` (the story gate decides; a bot that has not earned the ferry crosses on the `/testbot goto` fallback and the report says so), sneaks past the arrival cinematic, explores, fights, talks to people and answers their dialogs, `/exile return` |
| `island` | `Insel_Ingo`, `Kokos_Klaus` … | `/island`, `/island create` (random starter), lives there: walks, builds a little and takes it down again, opens `/island menu`, befriends other bots and visits their islands |

Island and tour bots are raised once to account level 20 (islands need it): `testbots.unlocks.account-level` and
`account-level-roles`. Every other role earns its XP by playing.

Every bot gets a mixed-tier kit from its slot, equipped role skills (XP only lands on equipped skills), a booster on
the main tool, sometimes a pet, and coins **topped up** to `starter-coins` (trade, tour) or `pocket-coins`. Coins are
never added on top, so respawns and reconnects do not inflate the economy. Names are stable per slot: the same slot
comes back with the same coins, skills and island.

## Safety

1. `testbots.enabled: true` in `plugins/AetherionStressBots/config.yml` before starting from the Dev menu (default **false**).
2. The runner's control server stays on `127.0.0.1`. The proxy door only opens for loopback connections with the token.
3. `runner/config.json` holds only what is local: `velocitySecret`, `connect.proxy.token`, host/port, control token.
   Everything else comes from `config.example.json` (merged key by key, `config.json` wins). It is gitignored; the
   token also lives in the proxy's `testbots.properties`. Never commit either.
4. Bots never fight, invite, befriend or buy from people: Pit targets, party and friend accepts are this runner's own
   bots by exact name; trade buys only bot listings (`trade.buyFromPlayers: true` turns that off).
5. Dungeon bots yield to people (above). Menus bots only press page arrows; casino bots only the spin lever.
6. **Stop all** from the Dev menu or `/stressbots stopall`.

The main-world jobs keep the old leash and void floor (plugin and runner). Network roles walk wherever their job takes
them: the plugin only catches them below the world (`min height − 8`) and puts them back on the last ground they
stood on, and their respawns are left to the server (dungeon, island, exile and Pit place their own).

## Dev menu and commands

`/dev` → page 2 → **Testbots**: count per role, Start / Stop, role lists, **Stop all**, `/botreport`.

```
/stressbots start <role> [count]     roles: mine forage catch roam combat fish trade quest pad
                                            general farm menus casino social pit dungeon exile island
/stressbots stop <role|all>
/stressbots stopall · list · setup · reload
/botreport
```

Permission: op, `aetherion.stressbots.admin`, or `aetherion.dev`. Counts clamp to `testbots.max-total` (40),
`testbots.caps.<role>` and the server's `max-players`; the runner has its own `caps` and `maxTotal`.

`/botreport` on any backend: the bots on that server (role, position, activity, deaths, errors, recent actions, TPS,
work per role), then **Network** from the runner:

```
Bots online: mmo-r 14 · mmo-d 4 · hub 3 · mmo-i 2 (runner up 37 min)
Roads: hub→mmo-r 21 ok avg 2.1s max 4.0s | mmo-r→mmo-c 3 ok 2 fallback avg 6.2s (/exile go did not move) | …
Menus: /bazaar 310ms (max 820) · /skills 140ms (max 410) · /gamble never opened 2 missed …
Done: dungeon rooms cleared 18 · islands claimed 2 · pit kills 11 · casino spins 9 · tour dungeon 4 …
Who is where: general@mmo-d (dungeon) · pit@hub (pit) · island@mmo-i (island) …
Problem: Kerker_Kurt: dungeon hub: no Dungeon Keeper found ×3
```

## Runner

```bash
cd AetherionStressBots/runner
cp config.example.json config.json   # keep only the local keys; set connect.proxy.token and velocitySecret
npm install
npm start -- --listen                # Dev-menu daemon (0 bots until a role is started)

node src/index.js --general 6 --pit 4 --dungeon 4 --exile 2 --island 2
node src/index.js --mine 4 --forage 3 --fish 2 --farm 2 --menus 2 --casino 1 --social 4
node src/index.js --help

npm test                             # names, helper lines, roads, telemetry, parties, windows, tour, login target …
```

Control HTTP on `127.0.0.1:18765`: `/health`, `/status` (every bot with server, step, uptime, kicks, stats),
`/report.txt` (the Network lines above), `POST /desired`, `/stop`, `/stop-all`. Optional `control.token` must match
the plugin's `testbots.runner.token`.

### How the bots read the server

Menu titles are pack glyphs, so nothing is matched by title: windows are told apart by their slots (market browse,
price picker, confirm page, quest offer, language picker, Dungeon Keeper, slots table, island starter pick). Text is
1.21 NBT and decoded with prismarine-chat. Timing bars are boss bars (`Fishing [..] REEL/NOW`, `Fell [..] CHOP/NOW`,
catch `✦ Now`). A watchdog closes a stray menu after 7 s like Escape. Loops never overlap their own ticks and can be
stopped and started again (the tour switches jobs that way). A bot that keeps getting kicked backs off (8 s, 16 s …
2 min) and gives up after `reconnect.maxFails` short sessions; logging out on purpose (the only road back to the hub)
does not count.

### Spots

Spots per role come from YAML `anchors`/`waypoints`, live Hub spawns in `hub-spawns`, and quest NPC spots
(`use-quest-npcs`). A spot without solid ground is skipped. The plugin **patches** a live `config.yml` on start
(`testbots.config-version`, now 3): new keys and roles are added, tuned values stay.

### Limits

- Exile story steps and full quest trees are best-effort; the report says when a road needed the fallback.
- Dungeons run one instance at a time: with people on mmo-d the dungeon bots wait.
- Islands need `/island create` to work for level 20 accounts; a locked starter pick shows up as a problem.
- Not automated: anvil booster fusion, buying in shops, guild management.

## History

| Wave | Scope |
|------|--------|
| **1** | Foundation: role interface, registry, report DTO, Dev menu, `/botreport`, mine / forage / catch / roam |
| **2** | Combat, fish, trade/AH, quest NPC click, jump pads; player-like skills/kits/pets |
| **3** | Language dismiss, AH/Bazaar GUI list/buy, quest accept clicks, fishing strike/reel, pad arcs, spawn unlocks |
| **4 (2026-11-17)** | Today's server: NBT text, glyph menus by contents, Fell / strike / catch bars, TalkUx dialogs, live-map spots, bots-only trading, reconnect backoff, work counters, live config patch |
| **5 (2026-11-18)** | The whole network: proxy logins, German names and the Testbot_ family, `/testbot` helper on every backend, roads between servers, Pit, farm, menus, casino, parties, dungeons, exile, islands, the tour, network `/botreport` |
