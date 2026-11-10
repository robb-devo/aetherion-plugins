# Aetherion — Project state (read this first)

Snapshot: **2026-11-10** (module table, CI, cleanup rounds 1 and 2); the live build table is still the 10-25 one
and older sections keep their dates. Cleanup rounds 1 and 2 are in PR #146 (branch `claude/project-thread-knvhf4`),
not merged and not deployed; the handover with next steps is at the top of the audit below. One page so a new session does not have to rescan the repo. Update the dated
sections when they change.
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

## Live plugin builds (sha1 prefix, 2026-10-25 12:00)

This table is the 10-25 snapshot. Every backend has been redeployed since (last: PR #143 and #144 on 11-02), so the
sha1 values are out of date and need a refresh from the box. Which backend loads which plugin still holds.
One build per plugin on every backend that loads it, all from `main` `c5eb105` (PR #119) or earlier `main`:

| Plugin | sha1 | Backends |
|---|---|---|
| Core | `9b556b78` | hub, mmo-r, mmo-d, mmo-c, mmo-i |
| Items | `ebfac21b` | mmo-r, mmo-d, mmo-c, mmo-i |
| Quests | `d2663e0b` | mmo-r, mmo-d, mmo-c, mmo-i |
| AetherMobs | `baba6df2` | mmo-r, mmo-d, mmo-c, mmo-i |
| BossEngine | `c47cba94` | hub, mmo-r, mmo-d, mmo-c |
| Foraging / Mining | `a8c46420` / `f2faa59c` | mmo-r, mmo-c |
| Farming / Fishing | `919d9515` / `c55d270e` | mmo-r, mmo-c |
| Gnomon | `684013f0` | mmo-r (mmo-c parked 11-02) |
| Dungeons / Beta | `07c2c939` / `ed9d1621` | mmo-r, mmo-d |
| Guilds | `5e72839d` | mmo-i (parked on mmo-r and mmo-d) |
| Hub | `7a3fa285` | mmo-r; **hub keeps `d1a097e7` (10-05)** |
| SamuraiBoss / Pit | `b6f736f0` / `7e494d00` | mmo-r / hub |
| Helm agent | `2da97e46` | all five (proxy: `AetherionHelmProxy`) |

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
- `/exile go` crosses from the main world when the story gate passes (at the quay it boards the ferry); the hub routes
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
  `/exile vial <boss|all> [player] [amount]`; how players earn them is not built yet). Sites: Caedmon's sword
  (Hollow Sun), the music box (Seraphine), the star ring (Helios), the Maw (Nihil & the Unbroken), the Great Doors
  (Aetherion; the four lullaby seals and Orrin no longer gate anything). No vial: one action-bar line. A refused
  spawn or portal gives the vial back.
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
- `CombatTeach` (tutorial combat beat) is still a stub.
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
