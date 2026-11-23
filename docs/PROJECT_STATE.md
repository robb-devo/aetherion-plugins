# Aetherion — Project state (read this first)

Snapshot: **2026-11-22** (live table below, handoff folder, beta work list); older sections keep their dates.
One page so a new session does not have to rescan the repo. Update the dated sections when they change.
Open work: the **beta work list** A-01..A-72 (Claude Doc "Aetherion: Beta-Arbeitsliste", copy in the project files
and in Robb's handoff folder, see "Where the truth lives"). Work it in A-number order, one item at a time.
Code health and cleanup plan: [CODE_AUDIT_2026-11-10.md](CODE_AUDIT_2026-11-10.md). Older beta list:
[BETA_AUDIT_2026-10-19.md](BETA_AUDIT_2026-10-19.md).
Ops, hosting and access details are deliberately **not** in this repo; they live in
the private `aetherion-ops/` folder on Robb's PC (`docs/OPUS_SESSION_BIBLE.md`,
`docs/LIVE_STATE_*.md`, `docs/BETA_AUDIT_OPS_*.md`).

## What this is

Minecraft MMORPG network "Aetherion" on Paper 1.21.1 behind a Velocity proxy.
16 Maven modules in the root reactor (one plugin each), SamuraiBoss and the Velocity plugin AetherionProxy with their
own poms, and AetherionHelm (staff panel, own builds): ~506k lines of Java, 8 unit test classes.
CI (`.github/workflows/compile.yml`) compiles the reactor, AetherionProxy and SamuraiBoss and runs the Core, Farming and
Fishing tests. The AetherionItems tests do not compile yet (see "Known gaps").

| Module | Role | Java LOC |
|---|---|---|
| AetherionCore | Shared API, network transfer, persistence, shutdown, exile content | 21k |
| AetherionItems | Items, combat, HUD, economy UI, recipes, resource pack source | 146k |
| AetherionQuests | Quests, onboarding acts, TalkUx, Codex | 55k |
| BossEngine | Boss framework + 43 boss YAMLs | 86k |
| Aethermobs | Mobs, pets | 37k |
| AetherionGuilds | Guilds, private/guild islands | 29k |
| AetherionDungeons | Dungeon gate (mmo-r) + instances (mmo-d) | 17k |
| AetherionHub | Hub pads, spawns, warps | 18k |
| SamuraiBoss | Standalone boss | 16k |
| AetherionMining / Foraging / Farming / Fishing | Gathering isles (Eldervale) | 20k / 16k / 11k / 9k |
| AetherionGnomon | Adaptive boss encounter. A test boss to check a concept, not part of the story or the game (Robb, 11-10): no work goes into it | 9k |
| AetherionBeta / Pit / StressBots | Beta tooling, Pit minigame, QA load bots | 3k / 2k / 4k |
| AetherionProxy | Velocity plugin: sends the resource pack once per session (own build, JDK 25) | 0.3k |

Backends: **hub** (lobby + Pit), **mmo-r** (main world, capital, isles, bosses, market),
**mmo-d** (dungeons only), **mmo-c** (exile/story), **mmo-i** (private + guild islands), and since late October
**mmo-m** (Titan mine test server, world `jawcamp`). On 11-02 mmo-m was behind `main` on Core, Items and Quests, had no
Helm agent and lacked the `aetherion-armor` datapack the other backends carry; nothing there is open to players yet.

## Where the truth lives

- **GitHub `main` = live code.** Every jar on the servers is built from it.
- Build source: a clean clone of GitHub `main` at `IdeaProjects/_deploy/aetherion-plugins` (fast-forward it,
  build, deploy). Work happens on a branch in a worktree beside it, merged by PR before any deploy. The old ship
  worktree `mining-eldervale-progression-65660c` stopped on 10-21 and is not a source any more.
- Helm (staff panel, `AetherionHelm/`) is on `main` since PR #98; its jars come from the `aetherion-helm` release.
- The `IdeaProjects/` root checkout (branch `claude/polish-pets`) is **stale (09-28)** with
  ~6000 dirty files. Do not build or commit from it.
- Agent prompts and briefs live in `docs/prompts/`; they are history, not specs.
- **Handoff folder** (since 2026-11-21): `Desktop\Aetherion-Handoff` on Robb's PC is the shared interface between all
  sessions (cloud and PC). Its `HANDOFF.md` is a log, newest on top: every session adds 3 to 5 lines after its work
  (date, what was done, where it lives, what is open) and puts copies of what it produced (plans, lists, reports)
  beside it. A cloud thread that cannot reach the PC parks its lines in the project files under `handoff/` and moves
  them over on its next PC access.
- Live jar hashes, deploy times and backups are logged per deploy in `aetherion-ops/docs/LIVE_STATE_*.md` on Robb's PC
  (newest on top); the table below is the summary.

## Live plugin builds (sha256 prefix, 2026-11-23 22:55 CET)

Summary of the deploy log (LIVE_STATE). Deploy round A-04 to A-61 (2026-11-23 19:09-19:11 CET) put `main` `f069314`
(PRs #159 to #187) on every backend. The round of 21:21-21:22 CET added `6f592a1` (#189: A-58, A-54/A-55, A-59,
A-13: Core, Items, Quests, BossEngine, SamuraiBoss), and Quests followed with `6b2c6f9` (#190) at 22:55 CET. **`main` equals live**;
the one exception is the hub server's own Hub jar.

| Plugin | Live build | Backends |
|---|---|---|
| Core | `8709799c` | all six |
| Items | `2299eb85` | mmo-r, mmo-d, mmo-c, mmo-i, mmo-m (economy.yml patched to v4 on start, `economy.yml.v3.bak` kept) |
| Quests | `d03b742d` | every mmo backend (live configs untouched; mmo-i had paged talk + cards switched on before) |
| BossEngine | `50cbbc16` | hub, mmo-r, mmo-d, mmo-c (no LuckPerms player group grants `*` or `helios.*`) |
| AetherMobs | `68472a44` | every mmo backend |
| Mining | `7646c17a` | mmo-r, mmo-c, mmo-m |
| Farming / Fishing / Foraging | `065a45f1` / `4761955f` / `d3949dac` | mmo-r, mmo-c |
| Guilds | `25b1b93c` | mmo-i only (parked on mmo-r and mmo-d) |
| Hub | `0fb9f70c` | mmo-r; **hub keeps `d1a097e7` (10-05)** |
| SamuraiBoss | `16210290` | mmo-r (`arena.bell-challenge: true` patched live; no dojo is built there right now, see below) |
| Dungeons, Beta, Gnomon, Pit | `main` of 11-16 (unchanged since) | as below |

Which backend loads which plugin: Core on all six (hub, mmo-r, mmo-d, mmo-c, mmo-i, mmo-m); Items, Quests,
AetherMobs on every mmo backend; BossEngine on hub, mmo-r, mmo-d, mmo-c; Mining on mmo-r, mmo-c, mmo-m; Foraging,
Farming, Fishing on mmo-r and mmo-c; Dungeons and Beta on mmo-r and mmo-d; Guilds on mmo-i only (parked on mmo-r and mmo-d);
Gnomon and SamuraiBoss on mmo-r; Pit on hub; Helm agent on all, `AetherionHelmProxy` on the proxy.

Backups of the replaced jars and configs: `backups/beta-a04-a61-20261123-190456` and
`backups/beta-a54-a59-20261123-211821` (with `quests-190` for the #190 swap; rollback scripts inside and in the matching staging folder). Nothing merged is
waiting for a deploy.

Live look of 2026-11-23 (things the repo does not show):
- mmo-r has **no Samurai dojo** (both were removed with `/samurai remove` on 10-31; boot says "0 Dojo(s)"). The temple
  bell starts fights only after staff run `/samurai build` + confirm somewhere.
- The live `plugins/BossEngine/bosses/dungeon_aetherion.yml` on mmo-c has real rank loot (rank 1: an Aetherion armor
  piece for sure, chances on Void Stick, Terminus, Portal Gun, Void Vacuum Charm, Ashen Katana; ranks 2 and 3 lower;
  95,000 XP). The repo copy's loot section is empty (it is only written when missing). Quests' `FinaleSpoils` adds
  coins only, so nothing is paid twice.
- On the hub, `/hub` comes from AetherionPit (aliases lobby, spawn), `/spawn` from AetherionHub (alias hub), `/lobby`
  from DeluxeHub (for A-63).

Deploys swap jars by plugin name only where a server already runs that plugin, back the old ones up and
restart each backend once (graceful RCON stop). The host also runs a standalone vanilla server (own port,
not behind the proxy). Crafty and the Mineflayer stress-bot service were switched off on 10-25: Crafty
auto-started a second, idle Velocity on its restart of 10-24.

The proxy runs **AetherionProxy** `44947554` (Velocity 4, built on JDK 25, see "Resource pack").

The bold cell is drift on purpose: a newer Hub would switch on Origin systems (props, chests, NPCs) in the
lobby. Hub and Fishing left mmo-d on 10-20 (jars in `plugins.bak-pre-mmod-cleanup-*`, data folders kept);
there Dungeons registers `/spawn` and `/hub` itself and sends players back to the main world. StressBots left mmo-r and mmo-d on 10-19 (jar in the ship backup, data folder kept).
Old Farming left mmo-d on 10-19 (it piled ~283k ambient mobs into one chunk).

## Resource pack (2026-10-21)

**One pack for the whole network, sent once by the proxy.** `Aetherion_texturepack.zip` sha1 `52784940`.
The Velocity plugin `AetherionProxy` offers it when a player first reaches a backend (required, hash read
from the served zip); no backend advertises a pack any more (`resource-pack=` empty everywhere), so moving
between hub, mmo-r, mmo-d, mmo-c and mmo-i never reloads resources. The hub has the full pack too: vanilla
hearts are hidden network-wide, and the Pit shows HP as text on the ActionBar.

Pack swap: build with `AetherionItems/tools/build_served_pack.py <live.zip> <out.zip> <1.21.1 client jar>`
(the live zip plus exactly the HUD/GUI paths the generators own; never copy the repo pack's item models or
textures, they are stale) and upload it over the served zip. That is all: the proxy re-reads the zip's hash
every minute and sends a changed pack to everyone online. No `server.properties` edits, no restarts.

HUD (10-21): status frame top left and radar top right ride an invisible BLUE boss bar
(`de.aetherion.items.hud.HudCarrier`), not the ActionBar, so they no longer fade on lag or vanish when
something else writes to the ActionBar. Art, font and shaders: `tools/gen_carrier.py`; the text shader also
caps the HUD's size on large GUI scales. Manager and menu icons: `tools/gen_gui_icons.py` (16 px objects in
each menu's accent colour; locked tabs keep their picture, greyed with a padlock).

## Robb's list (2026-10-21, cloud session)

One PR, deployed with `deploy-robb-list.ps1` from Robb's PC (build of `main`, served pack, jars by plugin name only where a
server already runs them, AetherMobs on mmo-r only, one restart per backend, backups under `backups/robb-list-<time>`).
- Pack: HUD frame + radar on clients newer than 1.21.1 (`newer_clients.py`), rank and level badges (font `aetherion:badges`,
  U+EA00..EAFB, referenced from every `minecraft:default`), every Manager page as a book page (`gen_gui_chrome.py` PAGES,
  page glyphs E000..E02C added to the default fonts by `build_served_pack.py`).
- Items: Owner rank for Robb (group key still `admin`), pink Co-Owner bound to Shuminite's UUID (`bound.coowner` in
  `player-ranks.yml`), BookPage captions on Shop, Codex front page and Recipe Book index.
- Guilds: `/insel` warps to an existing island and never claims again (records restored from factory saves).
- Core: raid menu for moderators (`/raidban`, aliases `raidmenu`, `antiraid`; heads of the whole network in join order,
  click = barrier, confirm = IP ban on every backend with the bot-raid message); Ferryman always answers on the quay and
  explains what the crossing still needs; `/exile repairquay [apply]`;
  one-time exile void trim (`plugins/AetherionCore/exile-void-trim.armed` on mmo-c, needs
  `worlds.aetherion_exile.generator: AetherionCore` in bukkit.yml).
- BossEngine: `/boss stage sweep <world> <x1> <z1> <x2> <z2> [apply]` for Seraphine stage leftovers; Helios returns
  people to the Star Ring; Quests: boss kill credit 128 blocks, Lark's pet catch is part of tutorial Act II.
- AetherMobs: pet bodies polished (cow and mooshroom patches, upright blaze rods, framed hacker terminal, 12-spike
  guardian, glow squid teal, dolphin slate, cat tail, butterfly wing borders).

## Aetherion Helm, staff panel (live since 2026-10-22)

`AetherionHelm/`: own Maven builds outside the root reactor plus a web app, see its README. Gateway (Javalin, SQLite,
login with mandatory 2FA, roles, audit log, host monitor) on the Hetzner host behind a Cloudflare tunnel on a panel
subdomain; Paper agent `AetherionHelm` on every backend and Velocity agent `AetherionHelmProxy` on the proxy talk to it
over loopback. React PWA in liquid-glass style; the Android app is a Capacitor shell that opens the live panel.
`.github/workflows/aetherion-helm.yml` publishes everything as the release `aetherion-helm`; `deploy-helm.ps1` installs
it from Robb's PC. Accounts: Robb (Owner) and Shuminite (Co-Owner, everything except managing staff accounts).

## Exile, ferry, islands, routing (2026-10-22, cloud session, PR #99)

Deployed with `deploy-exile-isles.ps1` (Core, BossEngine, Hub jar on mmo-r; islands moved by `ae-islands-migrate.py`).
- Veil Quay: the PR #99 turn was refused live (the recorded origin 600 51 322 was wrong); the turn really ran on
  2026-10-22 23:07 with PR #103's locator, see below.
- Exile void: the trim deletes every terrain chunk (bedrock or half-generated). Its premise was WRONG: the map's core
  stands on old terrain chunks, so the 10-23 run cut it out (repaired the same night, see below). Never re-arm
  `exile-void-trim.armed` as the code stands.
- Islands live on mmo-i only: mmo-r's Guilds jar parked, data moved; `/island` `/insel` `/guild` `/friend` on mmo-r, mmo-c,
  mmo-d cross to mmo-i (IslandLink), from the hub via mmo-r.
- Routing: `network.routed-commands.everywhere: true` arms travel commands on every backend but mmo-r; built-in labels
  always count, `/harbor` `/hafen` replay as `/harbour`; hub hops to mmo-r only for labels it does not answer itself.

## Exile void, islands on mmo-i, /exile from anywhere (2026-10-23, cloud session, PR #106 + #107)

Deployed 03:08 server time with `deploy-exile-void-isles.ps1` (main `6ed0008`: Core everywhere, Items, BossEngine,
Quests, Guilds on mmo-i; one restart of all five). The tutorial on mmo-r and mmo-i was already the newest code.
- Islands: mmo-r's data won. mmo-i's old island data is in `<mmo-i>/_retired/pre-island-move-<time>` (6 islands that
  only existed there, incl. david679964, can be restored one by one). Guilds jars on mmo-r and mmo-d are parked in
  `plugins/_disabled-network`; the old island worlds there bounce players to spawn (`network.islands.old-worlds`).
- `/exile go` crosses from the main world when the ferry gate passes (since A-52: Aetherion Level 75 and the Pathwarden
  beaten once, flag `pathwarden_down`; the story is no longer the lock; at the quay it boards the ferry); the hub routes
  it through mmo-r. The arrival barge hangs on a per-rider cable; the white screen holds until the line chunks are loaded.
- Exile void: the plugin trim cut the map's core (229 built chunks). At 03:24 a one-off script restored the 21 region
  files from `aetherion_exile.void-backup-20261023-030928`, then deleted only the 2998 terrain chunks outside the box
  around the map (blocks x -272..351, z -368..463, built chunks + 2). Inside the box the old terrain stays. Also gone:
  a small wooden hut (chunks 26..28 / 11..12) and cocoa far out. Copies: `aetherion_exile.trimmed-20261023-032447`
  (after the first trim) and `aetherion_exile.retrim-20261023-032447` (before the second).

## Harbour and ferry (2026-10-22, cloud session, PR #103 + #104)

Deployed with `deploy-harbour-ferry.ps1` (Core, Quests, Items, BossEngine, Hub; one restart of all five) and
`deploy-harbour-ferry-2.ps1` (Hub on mmo-r, quay turn, underside fill; one mmo-r restart).
- Veil Quay: `/exile turnquay [apply]` (ExileQuayTurn) finds the islet by voting on the schematic's rare blocks around the
  recorded origin, then turns it around the deck's middle. Live: found at 620 51 322 (97 % in place), now origin
  562 51 322, `exile.quay.facing: east`, bell in place, no causeway recorded. The jump pad reaches the quay once the
  gangway is solid at the recorded origin.
- Ferry: fog from the first step on deck, gantry and cable with slings (CABLE_TAG displays, purged by UUID), the
  Ferryman walks each passenger aboard (ExileFerryEscort: a per-player FancyNpcs copy, the real one hidden for them).
- Anker Harbour: displays are tracked by UUID and swept (stale ones survived in resting chunks); the boatmen and the crane's
  lighterman are FancyNpcs (OriginHand) rowing with oar displays; the Halcyon berths at a sky pier (`courier.sky-pier: auto`).
  `/origin dev underside [apply]` fills thin seabed over open air (9466 blocks placed live).
- mmo-r's `latest.log` is not cut at a restart and holds most lines twice: deploy scripts must find answers by content
  (lines after the last start line), never by counting lines.

## 10-25 rounds (PR #118, #119)

- Exile arrival: `CineCam` attaches the lens once its chunk has reached the client and `rebind()`s it before
  the white peels, so every rider opens on the same frame (before, the camera packet beat the entity and the
  player floated wherever they had joined). Every way aboard the King's Ferry (talk, bell, sneak, `/exile go`)
  has the Ferryman walk ahead; the shot starts a second after both are on the deck.
- The old Gate Warden (Closed Road) is retired; the Colosseum Proctor is the only Pathwarden quest.
- Onboarding: spine NPCs say at most three lines, task first (EN + DE). Chopping opens with the Forager's axe
  (the demo no longer blocks). Spheres bounce off NPC companions (Lark's parrot) unspent. Vex credits the kit
  on accept and posts the drill keys as keybind components. Temper's first booster is a pick of three
  (`tutorial/BoosterPick`). Mining starts at the Shaft Foreman, then the Surveyor's first blueprint tool; the
  Mining pad reads Quests' story flag `mine_road_open` instead of Ledger's stamp (veterans keep it via
  `MiningAccess.knowsIsle`). The Farm Isle road starts at the Farmer.

## Exile boss altars, harbour gaps (2026-11-02)

- Exile boss sites take a **vial** instead of a story talk (`exile/ExileVials`, key `aetherion:exile_vial`, staff:
  `/exile vial <boss|all> [player] [amount]`; players get them from the Keeper of The Last Bell since A-53, see below). Sites: Caedmon's sword
  (Hollow Sun), the music box (Seraphine), the star ring (Helios), the Maw (Nihil & the Unbroken), the Great Doors
  (Aetherion; the four lullaby seals and Orrin no longer gate anything). No vial: one action-bar line. A refused
  spawn or portal gives the vial back.
- Exile loop (A-53): the Keeper of The Last Bell (Quests `story/ExileBrewer`) brews a boss vial from isle finds plus
  coins (`exile-loop.vial-finds` 2, `exile-loop.vial-coins` 5000 in the Quests config): Hollow Sun = Crystal Finds,
  Seraphine = Prize Crops, Helios = Heartwood, Nihil = Trophy Fish. Each of the four bosses leaves an Echo
  (`aetherion:exile_echo`) with everyone who hit it; one Echo of each brews the Aetherion vial. Set pieces drop without
  doubles per player (BossEngine `loot/SetPieceLedger`, round mask `aetherion:set_round_<set>` on the player).
- Caedmon's sword is a display prop (`exile/HollowSword`): the vial makes it climb into the sky and flare into the
  Hollow Sun's falling star; it comes back down a few seconds after the fight. The old block sword stood on the
  boss's spawn column (its pommel became the arena floor); `/exile tidy apply` removes it and the water curtain
  the void trim left west of the island (x -160, rows z -190 / 195).
- Seraphine spawns at -20.5 220 69.5, so her own stage hangs in open air over the Little Theatre (inside the
  hall it never fit and built itself on the roof). People falling from it are lowered back into the hall.
- Anker Harbour: `/origin dev underside gaps [apply]` fills the air layer between the seabed and the rock under it
  (bay columns only, natural blocks only).
- Gnomon and SamuraiBoss run on mmo-r only (Gnomon's mmo-c copy parked in `plugins/_disabled-network`).

## Cleanup round 1 (2026-11-10, cloud session, not deployed yet)

First round from [CODE_AUDIT_2026-11-10.md](CODE_AUDIT_2026-11-10.md). No behaviour change for players.
- Core: `ExileVoidTrim` and its `onLoad` hook are gone, with the three one-off scripts under `tools/deploy/`. A leftover
  `exile-void-trim.armed` file now does nothing.
- Every state file that was written with a plain `save` goes through `AtomicYaml` (temp file, then rename), 63 places in
  Items, Quests, Hub, Mining, AetherMobs, Guilds, Dungeons, BossEngine, Farming, Fishing, Foraging, Pit, Beta and Core:
  codex, recipe and blueprint unlocks, XP boosts, pets, hub players, friends, mob zones, spawners and more. A kill in
  the middle of a save can no longer leave a cut-off file. Not converted: the one-off exile tools, three NPC position
  files and the two dev arena configs.
- Items: `/bloodstonearena` and `/ashenvoid` need `aetherion.dev` or op for the teleport too (they dropped any player
  into the boss arenas). Side features (casino cabinets, millstone, root cellar, market stall, ambient props, Colosseum
  gate and escort, crypt discovery, wildlife looks) start through `startSafely`: a failure is logged as
  `[Startup] ... failed to start` and the rest of the plugin keeps starting.
- Repo: binary assets are marked in `.gitattributes` (the `*.nbm` CRLF trap), CI compiles SamuraiBoss and runs the
  Core, Farming and Fishing tests, the stale `helios-jars` workflow is gone.
- Deploy: nearly every plugin changed, so this is one round on all backends. Between 11-09 and the arrival of Robb's
  new PC nothing can be deployed (the ssh key lived on the old one), so the PR stays unmerged until then.

## Cleanup round 2: gear stats (2026-11-10, cloud session, not deployed yet)

Audit item C1. Same PR as round 1. No balance change: every number comes out as before.
- `de.aetherion.items.manager.EquippedGear` keeps, per player and equipped slot, what was read off the item (id,
  stats per capability for overworld and dungeon, any other item-only fact). An entry is dropped as soon as the item
  in the slot no longer equals the one it was read from, so a swap, an upgrade or a new booster counts on the very
  next read. There is no timer and no event to forget; an unchanged item costs one comparison instead of a meta copy.
- `ActiveEquipmentStats.getStat` sums the six slots from there. Providers that depend on place or time (skills, pets,
  isles, events) are still evaluated live.
- The set checks on the stat path read ids from there too: `UndeadCombat.wearingFullSet` (gear set bonuses),
  `DungeonArmor.wornPieces` and `DungeonGearProgress.wornLevelMultiplier` (dungeon callings), the coal ring in
  `ProgressionEffects`, the HUD's held item, and the two 2-tick set loops (`AetherionSetListener`,
  `HollowSunSetListener`, `DungeonCore.wornAetherionSetTier`).
- Before: around 80 item meta copies every 2 ticks for a player in full gear who is standing still (counted from the
  code). Not measured yet; take a spark profile on mmo-r after the deploy.
- New code that asks "what is this player wearing" should use `EquippedGear.itemId` or `EquippedGear.fact` instead of
  `itemManager.getItemId(player.getInventory()...)`. Slower set listeners (diving, healer, World Eater, Helios) still
  read the items directly.
- Deploy: Items only (mmo-r, mmo-d, mmo-c, mmo-i).

## Cleanup round 2: transfer and save timers (2026-11-10, cloud session, not deployed yet)

Audit items B2 (step 1) and B3 (first part). Same PR.
- Transfer: the Dungeons copy of `NetworkPlayerDataSync` is gone. Dungeons' `TransferSnapshotStore` uses
  `de.aetherion.core.network.NetworkPlayerDataSync`, so the list of files and keys a character carries, and how
  they are merged on arrival, exists once. The two copies only differed in comments and in when a storage warning
  is logged. Dungeons calls nothing in Core that the live Core does not already have. Its two own steps (staging
  files before login, repairing pre-v6 keys) live in `bridge/PendingSnapshotFiles`.
- Still open on the transfer (needs a build and test hops on every server pair, see the audit, B2): Dungeons'
  `TransferSnapshotStore` and Core's `CharacterStore` are two implementations of the same v6 character, and Core
  still starts the v5 `TransferSnapshotStore`.
- Save timers: the eight shared files in Items (coins, skills, progress, codex, recipes, blueprints, shards, XP
  boosts) each still save once a minute, but `core/SaveSlot` spreads their first run over 49 seconds so they no
  longer all write in the same tick. Still open (audit, B3): writing off the main thread and saving only the
  leaving player on quit.
- Deploy: Dungeons (mmo-r, mmo-d) and Items (mmo-r, mmo-d, mmo-c, mmo-i).

## Cleanup round 3: one character host (2026-11-14, cloud session, not deployed yet)

Audit item B2, step 2. Branch on top of the round 1 and 2 PR.
- The v6 character exists once: Core's `network/CharacterStore` (pack, claim, apply, outbox, quarantine, ledger)
  and `network/CharacterHost` (hop, quit, shutdown, join wait, redirect, guest). Where the two backend families
  differ on purpose, `CharacterStore.Policy` says so: `plain()` for mmo-c / mmo-i / mmo-m (travel as SURVIVAL,
  never merge leftovers, park legacy files), `gate(role)` for mmo-r / mmo-d (role as `from-server`, leftover
  adoption, legacy v4/v5 apply), exactly as the two copies behaved before.
- Dungeons builds that host in `bridge/RemoteServerBridge` from its `remote-transfer` config (same keys, same
  folder, same server name) and registers it as the network transport. `HubPortalBridge` calls `host.arrive` on
  join and keeps the gate's arrival teleports and pending-floor entry. Dungeons' `TransferSnapshotStore`,
  `TransferLedger`, `TransferGuard`, `CharacterSync`, `PendingSnapshotFiles` and `DungeonCharacterTransport` are
  deleted (about 2,000 lines).
- Small visible changes: the gate says "Crossing to the Dungeon Hub" instead of the server id, and a few error
  lines use Core's wording. `/aetransfer` on mmo-r / mmo-d shows "v6 via Dungeon Gate" and the guest flag.
- Still open: Core still starts the v5 `TransferSnapshotStore` (B2 step 3, after checking live that no v5 files
  are left).
- Deploy: Core (all five backends) and Dungeons (mmo-r, mmo-d) together; a new Dungeons jar does not run on an
  old Core. Test hops on every server pair before calling it done.

## Cleanup round 4: chunk loads, travel, leftovers (2026-11-15, cloud session, not deployed yet)

Audit items C3, A3 (one file missed), D6 and a new find. No balance or content change.
- Timers no longer load chunks. `Location.getChunk()` and `Block.getChunk()` load the chunk, so checks written as
  `getChunk().isLoaded()` were always true and pulled the chunk in on every run: animal zones, pet habitats, the
  shabby mine guard and world maps in Items (every 5-10 s, on every backend with Items), the forage isle guide (every
  4 s) and every merchant chest in Quests (twice a second). They now ask `world.isChunkLoaded`; the Items and
  Foraging ones also wait for the chunk's entities (`ZoneMarkers.ready`), so nothing is re-spawned next to a
  marker or display that is still loading. Content in chunks nobody is near now simply waits until someone comes.
  Startup restores and removal paths still load on purpose (one-off).
- Hub: spawn travel (`HubService.teleport`, used by the spawn menu and `/spawn goto`) and glowcap travel use
  `teleportAsync`; sound, message, compass and slow falling run on arrival. Origin profiles are saved through
  `AtomicYaml`.
- Removed eight unused classes (Quests `OpenRoadsGUI`, `QuestNPCManager`, `MiningQuestListener`, `QuestConfig`;
  Items `CraftCurve`; AetherMobs `PetEquipmentChangeEvent`; BossEngine `BossEngineConstants`, `SpawnTriggerFactory`).
  `TabScoreboardRebind` stays (rank system, has a test).
- Deploy: Items (mmo-r, mmo-d, mmo-c, mmo-i), Quests (mmo-r), Hub (mmo-r), Foraging (mmo-r), AetherMobs and
  BossEngine only for the deleted classes (can ride with the next deploy of those).

## The Hush, showcase boss (2026-11-14, cloud session, NOT live)

New instanced boss in BossEngine (`bossengine/hush/`, `bosses/the_hush.yml`, `hush.yml`), built without a
server to test on: it compiles, it has never been fought. Design, entry, admin commands and what is missing:
`BossEngine/docs/THE_HUSH.md`. `/hush enter` needs `hush.enter` (op by default) until the boss has a place in
the world. Its void world `aetherion_hush` is only created on the backend where somebody first enters.

## Private island as its own game (2026-11-21, cloud session, PR #155 + #156, Guilds only)

Plan: doc "Private Insel: Onboarding-Konzept", tab "Umsetzungsplan" (v2, four packages). P1 (PR #155) live on mmo-i
since 2026-11-21 17:03 CET. PR #156 adds P2-P4, deployed to mmo-i with one restart (see LIVE_STATE).
- Stages 0-3 on personal islands (`island/Stages.java`): First Shift, Werkhof, Foreman's Hall, Grand Hub. The Hub
  tier and Island Tier follow the stage (no coin upgrades there); guild islands keep the Hub-tier ladder.
- Hand press at the Hub (128 Cobble -> 1 Compressed), stage-0 ramp (`stages.ramp-multiplier`, default 10).
- Dock (`island/DockService.java`, `menu/DockMenu.java`, Role.EXPORT): phase contract = stage gate, a daily, two
  regular contracts, a Guild Order when the owner's guild has its Guild Hall (coins to the guild bank), surplus
  sold for Dock Marks (1 per 512 raw-equivalent, stage 1+). Dock Marks buy cosmetics only. Airship is cosmetic;
  rewards are booked at ship start. Saved in each island file, section `dock`.
- Look and feel: build thunk, own tops for Splitter/Sorter/Overflow, island sound bed (`ambience.enabled`),
  five-second landing sweep on a new island (`landing-camera.enabled`, spectator camera, game mode restored on
  quit/next join), first-pick cards in the Build Tool.
- Guild: Harbour Beacon gives members' own quarries +10 %; project board "Give from my island"; guild joining from
  Level 20 in Guilds (`/guild` on mmo-i), founding still Level 75. Core IslandLink and the Items Manager tile still
  gate `/guild` at 75 on the other backends (follow-up needs a Core + Items deploy).
- Not done: building hologram snapping, Southwood broker board, account XP and booster rewards.

Compact buildings (2026-11-22, PR #157, Guilds only): every new building is a display-prop body on a small barrier
shell (`structure/Props.java` `prop_v2_*` templates, bodies in `logistics/CompactBodies.java`, no resource pack).
Mill, Forge, Depot and Storage Hut 2x2, Hub 4x2 (real lectern at local 0,1,1), Dock 3x2 (you walk on its deck).
Anchor = back-right cell, so the chute stays at (0,1,1) and belts start at (0,1,2). Older 3x3/5x5 props keep their
templates and bodies (props keep their template on upgrade); effects, tags and reach use `PlacedStructure.centerX/Z`.
Looks: calcite smock windmill (sails, fantail and sack hoist at tier 2), brick smithy (chimney, blast furnace over
the chute, anvil yard, copper hood at tier 2), half-timbered store that grows a storey per tier (Warehouse: brick
base, slate roof, crane), market-hall Hub (copper roof whose patina grows per tier, clock turret, turning gem, gold
at the Grand Hub), raised quay Dock (mooring mast, crow's nest, crane, contract board). The Build Tool has a Quarry
part (hologram = the rig, the tour hands the first quarry through it) and centres compact footprints under the
crosshair.

Compact quarries and conveyor polish (2026-11-22, Guilds only, follow-up to PR #157): new quarry housings use
`prop_v2_quarry` (2x2, minion on the anchor cell, chute (0,1,1), port (0,1,2)); bodies in `logistics/QuarryBodies.java`,
one look per trade and four tiers (`PropBodies.quarryTier`: timber / iron / copper / crowned): mine headframe with
winding house, farm barn + silo (crops, paddock or coop by product), grove saw shed under its tree, fishery hut on
stilts by a pond, mob keep with a grinder pit. The slim rig got the same lattice tower. Old 3x3 housings keep their
template and body. The Build Tool hologram shows the carried quarry's trade and level. Conveyors
(`logistics/BeltBodies.java` look, `BeltVisuals` motion): timber rails with copper caps, iron posts per joint, drums
with bearings, end walls, chamfered bends, inlet hood with chain curtain; chevron cleats run at the cargo's pace
(`BeltBodies.TICKS_PER_TILE`, also `CargoVisuals`) in one long interpolation per loop and dive into the drums; gold
marker cleats glow while the line is live (the old text chevrons are gone).

Island tour in flow order (2026-11-22, branch `claude/island-chain-onboarding`, Guilds only, after Robb's test):
- `island/IslandGuide.java`: Hub, then Corin's starter kit (Cobble Quarry into the hand, the Mill unlocked through
  `FactoryProgress.unlock`, `guide.kit-belt-tiles` free belt tiles, default 32; the first hut is always free), then
  quarry, Mill in front of its chute, belt into the Mill (he tops the Mill's hopper up to 128 once, so the first
  block comes at once), hut behind the Mill, belt into the hut, the "First chain running" card, then the Hub
  lectern: the Dock unlock costs 1 Compressed Cobble (was 4), the First Shipment 2 (was 4). Every piece is placed
  once; nothing is pre-built and there are no marked spots. The hand press stays at the lectern but is out of the tour.
- Corin never walks: he greets where the owner lands and takes a fixed post beside the Hub once it stands
  (a puff, he appears there). Too far for the bubble, his lines go to chat with clickable chips.
- Saves: `island_guide.yml` has `version: 2`; older files (hut-first tour) keep DOCK/SHIP, every other tour step
  restarts at HUB and `advance()` skips what the island already shows. Old quarry→hut belts get a "Pull up old
  belt" chip in the Mill-feeding step.
- Build Tool board (`structure/PartBoard.java`, shown by `PlacementService.showBoard` and `beltSpec`): a small sign
  on a post beside the hologram, never in it (dark panel in a copper frame, the part's name on a copper tab, a copper
  arrow toward the hologram, real items for In and Out, the cost, and the tour's hint via
  `IslandGuide.boardHint`). It stays while the part is held, turns to the player and grows with distance. It replaces
  the old first-pick notes. Belts get one too, beside the start chute.
- Machine tags: Mill/Forge show "128 Cobblestone → 1 Compressed" and "▸ 37/128 in the hopper"; a quarry without a
  belt shows its rate and "belt its chute". Personal factory goals: the first chain means quarry → Mill → hut
  (`mill_line` and the old `press` goal are guild-only or gone), bar total counts only the island's own ladder.

## Paged talk and screen cards (2026-11-22, cloud session, Quests + Guilds, mmo-i first)

Robb's ask: far less text. Two switches in the Quests config, both off by default, so a server only changes when
its live config turns them on (first mmo-i; the rest of the network after Robb's OK).
- `paged-talk.enabled` (`talk/TalkUx.java`): a bubble line waits for the player's click (left/right-click the NPC
  or its bubble, or left-click while looking at them); a click while typing shows it at once; a breathing ▾ means
  more is coming; reply chips come once the last line is read. NPC lines no longer go to chat: one grey log line
  per conversation (hover = transcript, click = book of the last 12 talks, `/aetalk log`), "had more to say · N
  unread" when the player walks off. Callers hand paged lines over at once (`DialogManager.DialogRun`,
  `LivingNpcProfile.say`, `TalkAccessImpl.say`, Corin's `IslandGuide.pump`); follow-ups that must wait use
  `DialogPace.whenFree`. A LOW-priority FancyNpcs bridge (`LivingNpcInteractListener.registerPagedTalk`) turns a
  click on the talking NPC into "next line" and cancels it, so no click handler restarts the talk.
- `hud-cards.*` (`talk/HudCards.java`): a small toast at the top (step done, what's next) and a big animated card
  above the middle (quest done, island milestone: rows tick in, real item icons, "+250" counts up), then one summary
  chat line. Display entities at the player's eye with billboard CENTER, so the camera-space translation keeps them
  in front however the head turns; only the owner sees them; probed every other tick and pulled closer (same size
  on screen) in front of walls and the talking NPC. Big cards hold paged lines back; a left-click sends one away.
- Card look (second pass, same evening, Robb: "aktuell einfach nur ne box"): the big card is a crest with the
  moment's item turning in it, a fan of rays behind it, a ribbon with the header across a framed plate with a point;
  rows with an icon and "+N" become reward slots whose number counts up, other rows a list with icons. The toast is
  a slim plate with a check (or gem) badge on its left edge and a bar that runs down while it stays. Every shape is a
  `" "` text display (its background quad scaled and turned about the view axis). Crest item: the card's emblem
  (`Cards.big(..., emblem)`, quest complete = nether star), else the first block among the row icons.
- Quest moments (`feedback/QuestFeedback.java`, `QuestManager.cardRewards`): objective done and "ready" are toasts,
  quest complete is the big card with the rewards as rows instead of reward chat lines.
- Other plugins: `de.aetherion.quests.api.Cards` (plain types; false = cards off, say it the old way). Guilds reaches
  it only through `island/IslandUi` (soft hook, provided dependency on AetherionQuests): Corin's tour steps get
  toasts, first chain / Hub founded / Compressed Cobble / stage-ups get big cards, ship, factory goals, upgrades and
  first-pick part notes are toasts, the hand press and "built" lines go to the action bar.

## Aether Journal, phase 1 (2026-11-23, cloud session, PR #192, switch off by default)

Robb's ask: the Manager is too overloaded; make it one clean book that grows with the player. Backup of the old
Manager: branch `backup/manager-pre-journal-2026-11-23` (= main `8326dea`). Concept: the Claude Doc linked in the PR.
- Switch `journal.enabled` in the Items config (default false, read on start). Off = the Nether Star Manager, every
  text unchanged. On = slot 9 is a book (Worn / Bound / Aether Journal, same `ItemKeys.manager()` key),
  `AetherionManager.open` opens `menu/JournalMenu.java`, `/journal` is an alias of `/aetherion`.
- The page is one inventory title: `core/api/JournalPage.java` (frame, spread, ribbons, gold line, plate, line texts
  in the fonts `aetherion:jrn_line_1..4`). Art: `tools/gen_journal_art.py` via `gen_gui_kit.py`, kit glyphs
  0xE400..0xE443; codepoints and the Chapter order are mirrored there.
- Chapters: Today, Character (Stats, Skills, Pets, Storage, Loadouts), Journey, Atlas, Workshop, Market, Codex,
  Company, Settings. Visibility follows the Manager gates (`AetherionManagerGUI.gates`); tile clicks reuse
  `AetherionManagerListener.handleManagerClick` via `runTile`. PDC `journal_seen` marks chapters already opened.
- Texts: `core/api/JournalText.t` rewrites "Nether Star → Skills" into "Journal › Character › Skills" while the
  switch is on. Sinks: `LangPack`, `TalkText.fill`, `DialogueRuntime.fill`, `UnlockToast`, `ProgressionService.hint`;
  other Manager strings are wrapped where they are sent. New Manager-pointing texts: write them as today, the
  rewrite follows, and keep JournalText.TILES in step with the chapters in JournalMenu.
- Deploy: Core on every backend that gets Items, Quests, Hub, Farming or Guilds (they call JournalText), served pack
  with the journal art, then patch `journal: enabled: true` into the live Items config, mmo-i first.

## Stacked entities: crypt hologram, farm isle, trolls, fishing (2026-11-22, cloud session)

mmo-d lagged (MSPT spikes to 1.4 s with nobody online): `CryptHologramService` (Items) re-spawned its saved
text display every 10 s whenever the spot's chunk was unloaded, so 293k displays sat in one chunk of `world`
(311 MB, saved every 10 s). The crypt holo belongs on mmo-r only (Borderlands, behind the altar); the yml had been
copied to every backend. Cleaned live on 11-22 (backup `crypt-holo-cleanup-20261122-152432` in the ops backups).
Rule for every keep-alive loop: never spawn into a chunk whose entities are not loaded (`ZoneMarkers.ready`), and
prefer non-persistent displays that are put up on chunk load. Fixed in the same PR:
- Crypt holo: non-persistent, spawned only while its chunk is loaded, saved leftovers removed on `EntitiesLoadEvent`.
- `FarmIslandAmbience` (Farming): tops up animals and pets only while players are there and the area is loaded
  (same pattern as the ~283k mobs on mmo-d in October).
- Ore trolls (Items) and fishing encounter mobs (Fishing): untracked ones coming back with a chunk are removed.
Known, left as is: Dungeon Keeper / Guide villager fallback can add one villager per restart if FancyNpcs is not ready.

## Content state (verified against live 2026-10-19)

Live and committed: onboarding v2 (Acts I–VII incl. skill-isle lessons), exile crossing,
explore-chest scatter, pet bodies, Saitama main island (parts 1–3 + harbour), Codex/Skills
overhaul, GIGA texture pack, German overlay, player HUD + boss bars, HUD frame + radar on the boss-bar carrier
(10-21, radar quest marker shows above/below), Manager GUI icons (redrawn 10-21) and invisible fillers,
combat system (guard, cadence, mobility, stamina HUD), SamuraiBoss.

Known gaps:
- AetherMobs: the status check of 11-02 found every backend on the `main` build (the 10-04 build on mmo-c/mmo-i is gone).
- 18 newer Quests lines lack German (`validate_de_overlay.py`: `ui.roads.*`,
  `dialogs.amethyst_mines_guide_intro`).
- Veins reset fixed 10-20 (PR #71): the schematic is pasted in place on FAWE's async queue (~1 s, no
  main-thread stall). mmo-c has `veins.daily-reset: false`. The Eldervale and Forage isle pastes moved to
  the same queue (PR #73, Mining `d55c57f6` / Foraging `8d58797b` on mmo-r and mmo-c). Exile, Hub, Pit and
  Guilds already paste async; Dungeons endless stays synchronous until mmo-d has FAWE.
- ServerWaypoint 2.8.4 on mmo-r fails to enable (its data file has the wrong format); third-party.
- `AetherionItems` test sources do not compile, so local builds need `-Dmaven.test.skip=true`.
  `CelestialDyeTest` asserts an unshipped rank change (Monkey/Citrus/Beta/MVP++ no longer LuckPerms
  parents, which would drop Monkey's content kit); see closed PR #74. Needs a product decision.
- Quests on `main` drops two unused `de.yml` keys (PR #72); live Quests still carries them until the
  next Quests deploy. No behaviour difference.

## Uncommitted work in other worktrees

| What | Worktree |
|---|---|
| Pet bodies WIP | `aethermobs-pokemon-pets-bdadef` (40 dirty) |
| Dialog voice | `dialog-voice-german-localization-651358` (13 dirty) |
| Website | `aetherion-website` (49 dirty) |

29 worktrees exist, 14 with uncommitted files. All branches were mirrored to
`origin/backup/2026-10-02/*` on 10-02.

## Website (live on donnernet.de since 2026-11-19, PR #148, main `55ebc808`)

`website/` holds the new donnernet.de: one continuous 3D camera take over one in-game day, EN and DE. Start with `website/README.md`.
The home page is a server page, not a game: hero, one statement, six frames with a line each, join. Depth lives in the wiki.
The page opens on the main island (MMO-R world, `world/region`, baked 2026-11-19): Anker Harbour at dawn, the Capital, the fields
and Southwood, the whole island from above, then the starter islands off the west coast, the prison under the clouds, the Exile city
once at night (late game, never the face), and back to the harbour. `island.wasm` is not in git (purchased map, OriginBuilds); the
deploy copies it in. Bake steps in `website/README.md`.
Deployed 2026-11-19 21:04 from main: `dist/` plus `island.wasm` copied over `/var/www/donnernet.de` (launcher kept), backup of the
old site in `/root/aetherion-ops/backups/www-donnernet-20261119-210454.tar.gz`. The build values live in the deploy clone's
`website/.env.production.local`, never in this repo. Robb cleared the OriginBuilds licence for the site on 11-19.
Still open:
- Impressum and Datenschutz: put aside by Robb on 11-19; a German site with accounts usually needs them.
- The boss list on the page and in the wiki.
- Real-device performance (tested on desktop GPU and a software renderer; phones fall back to stills when too slow).
