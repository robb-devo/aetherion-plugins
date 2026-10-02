# AETHERION — FULL TECHNICAL HEALTH REPORT

Audit date: 2026-10-02. Tree: `main` @ `1922f36` (2026-09-29, “Docs: short Cloud chat handoff for dialog/DE + locks.”). History is a full clone (not shallow): 93 commits. No production source, config, or asset was modified. Runtime work used an isolated Paper 1.21.1-133 server under `/tmp/paper-test` with the jars this tree built, plus WorldEdit 7.3.10-beta-01 and WorldGuard 7.0.12 from Modrinth. That server is one process with every module loaded. Production is a Velocity split (Hub / mmo-r / mmo-d / mmo-c). Findings that depend on “all plugins in one JVM” are marked as such.

Labels used on every finding: severity (CRITICAL / HIGH / MEDIUM / LOW / INFORMATIONAL), evidence (CONFIRMED / REPRODUCED / STRONGLY INDICATED / POSSIBLE / THEORETICAL), verification (RUNTIME VERIFIED / STATICALLY INFERRED / UNTESTED).

Logs: `/opt/cursor/artifacts/paper-boot-1.log`, `paper-boot-2-write.log`, `paper-boot-3-reload.log`, `maven-package.log`, `maven-tests.log`.

---

## 1. Executive Summary

The suite compiles. All 15 modules package on JDK 21. The two data tests that exist (Farming 11, Fishing 9) pass. A fresh Paper process enables Core, Items, Quests, Hub, BossEngine (36 templates), Guilds, Mining, Farming, Foraging, Fishing, Dungeons, Mobs, Beta, and StressBots. Coins written through `CoinService` survive a clean stop and a second boot (4242 coins for a probe UUID, reloaded as 4242). Auction purchase removal-before-debit, atomic YAML for coins, and the in-world `TalkUx` bubble are still in the tree and should be left alone. Blossom Blade (`ashen_katana`) and Gravwell Cleaver (`gravwell_cleaver`) listeners are still wired. The 14-socket anvil is still the anvil menu. Those locks were not rewritten on `main`.

What is actually wrong, with evidence:

- Any player can run `/kit1`, `/kit2`, `/pickaxe`, `/sword`, `/booster`, and `/aetherionitems …` and receive god-tier or booster items. `plugin.yml` sets no permission. `AetherionCommand` does not check one. **CRITICAL, CONFIRMED, STATICALLY INFERRED** (no joined client; the code path has no other gate).
- **AetherionPit does not enable** unless PlaceholderAPI is installed. `PitPlaceholders` extends `PlaceholderExpansion`, and `onEnable` calls `PitPlaceholders.tryRegister` unconditionally. Reproduced: `NoClassDefFoundError: me/clip/placeholderapi/expansion/PlaceholderExpansion` at `AetherionPit.java:48`. Hub is the server that runs Pit. **HIGH, REPRODUCED, RUNTIME VERIFIED.**
- First-time Farm Island paste scans the entire schematic on the server thread looking for nether portal blocks. Observed paste `263×235×276` in 3718 ms, then a Paper watchdog (“has not responded for 10 seconds”) inside `FarmIslandService.findPortalCenter`, and “Can’t keep up! … 89399ms or 1787 ticks behind” on that first boot. **HIGH, REPRODUCED, RUNTIME VERIFIED.** Later boots of the same world were ~12 s because `farm-island.pasted` was already true.
- The stated lock “OP ≠ Aetherion Admin” is not what `main` does. `RankBadgeService.extraFor` returns the admin rank when the player `isOp()` or has `group.admin` or `aetherion.rank.admin`, and `applyLuckPerms` then writes that group onto the LuckPerms user. This has been true since the initial commit `a193a5f`. The commits that restrict Admin to a stored Dev-menu row exist only on unmerged branches (`cursor/stored-devmenu-ranks-e54a`, `cursor/stop-lp-cosmetic-sync-4ceb`). **HIGH, CONFIRMED, STATICALLY INFERRED. Lock gap, not a later overwrite of a correct `main`.**
- Without LuckPerms, the delayed rank sync task throws `NoClassDefFoundError: net/luckperms/api/node/Node` from `RankBadgeService.syncGroups`. Reproduced on the test server. Production that always has LuckPerms will not hit this. The soft-depend is not actually soft. **MEDIUM, REPRODUCED, RUNTIME VERIFIED.**
- `player-ranks.yml` is rewritten from memory with `config.save(file)`, exceptions swallowed, not `AtomicYaml`, and not merged with disk. Transfer import writes individual keys into that same file. **HIGH if ranks are shared across backends, MEDIUM if each backend has its own file. CONFIRMED code, STATICALLY INFERRED, not crash-tested.**
- Dungeon transfer snapshots default to `/var/opt/minecraft/crafty/shared/transfer`. The test server logged `Could not create transfer snapshot dir`. **MEDIUM, REPRODUCED** as a warning. On the real Crafty host that directory is the documented path, so production may be fine. A host that cannot create it will fail transfers.
- BossEngine creates `helios_requiem` at enable (3 slots). Dungeons warm a void world and `aedun_xl_base` with zero players. Shutdown logged `Waiting 60s for chunk system to halt` for `aedun_warm_void_d32a867a`. **MEDIUM, REPRODUCED, RUNTIME VERIFIED** on this combined server. A dungeon backend that keeps the warm pool will pay this on every stop.
- The even-second restart countdown (`RestartCountdown`, chat on 10, 8, 6, 4, 2) is **not on `main`**. It exists only on unmerged `origin/cursor/aenet-restart-countdown-398b`. **INFORMATIONAL lock gap.** Do not treat `main` as if that class is present.
- `robb-devo/aetherion-texturepack` is not readable from this environment (`gh repo view` could not resolve it). Model/texture parity is **UNTESTED** against the pack. Static count: 114 `setCustomModelData` calls in `CustomItem.java`, 101 distinct integers, 11 integers used more than once.

Do not “clean up” `CustomItem`, `DevMenu`, scripted boss directors, `TalkUx`, booster sockets, or the two locked weapons. They are large because they are the game.

---

## 2. Current System Map

Parent reactor `de.aetherion:aetherion-parent:1.0.0`, Java 21, Paper API `1.21.1-R0.1-SNAPSHOT`. About 346,000 lines of Java, 1,075 files, 37 boss YAML templates under `BossEngine/src/main/resources/bosses/`.

| Module | Owns | State | Load / save / restart (what was verified) |
|--------|------|--------|-------------------------------------------|
| AetherionCore | PDC keys, `AetherServices`, `AtomicYaml`, `VoidChunkGenerator`, FancyNpcs facade, beta wipe, network wipe watch | Core `config.yml`, wipe flag file | `load: STARTUP`. No game loop. Enabled and disabled cleanly in the test server. |
| AetherionItems | Items, recipes, coins, shards, market, skills, codex, loadouts, storage, boosters, ranks, combat listeners, arenas, Dev menu | `coins.yml`, `shards.yml`, `skills.yml`, `player-ranks.yml`, `market.yml`, per-player storage/loadouts/sacks, many world YAMLs | Coins: load all on enable, dirty flag, save every 60 s, save on disable. Probe round-trip verified. Ranks: load all, save whole file on change. |
| BossEngine | YAML bosses + scripted directors (Helios, Hollow Sun, Ashen Sovereign, Hanging Saint, World Eater, Unbroken, Sandbox, Ashes Elite, Ashen Sheath, …) | Boss YAML in the jar; spawner state in plugin data | Enable creates `helios_requiem`. `onDisable` stops managers, reliquary, music box, World Eater site. 36 templates logged. |
| AetherionQuests | Quest state, dialog, living NPCs, `TalkUx` bubble, compass | Per-player YAML via `PlayerQuestStorage` (`AtomicYaml` when the config is a `YamlConfiguration`) | Enabled. FancyNpcs missing → editor NPCs not restored (expected on this VM). |
| AetherionHub | `/spawn`, `/hub`, spawn menu, `/aetherpaste` | Hub config | Enabled. Paste command is FAWE-oriented; FAWE was not installed. |
| AetherionDungeons | Temp instances, warm pool, transfer snapshots | `remote-transfer.shared-dir` | Enabled. Warm worlds created. Snapshot dir creation failed. |
| AetherionGuilds | Guilds, bank, private islands, quarry minions | `guilds.yml` via `AtomicYaml`, full rewrite from memory | Created `aether_guilds` and `aether_islands` at enable. Minions tick while the server runs (log line). |
| AetherionMining | Eldervale districts, Amethyst veins world | Mine profiles, dig snapshot | Created `aether_veins` (~66 MB on first gen). Dig paint reported 0 writes on an empty hub. Hard-depends WorldGuard. |
| AetherionFarming | Harvest, bird scare, Eldervale farm isle, separate farm-island world | `farm-island.pasted` in config | Pasted farm island on first boot; portal scan stalled the main thread. |
| AetherionForaging | Groves, isle weather, guide NPC hologram | Forage profiles, isle YAML | Enabled. FancyNpcs interact event missing. |
| AetherionFishing | Waters, shoals, Eldervale fishing | Angler profiles | Enabled. Data test passed against shipped `config.yml`. |
| Aethermobs | Pets, catch, collection | `pets/<uuid>.yml` | Enabled. Deprecated `EntityKnockbackEvent` warning. |
| AetherionPit | Hub Pit | Pit data | **Failed enable** without PlaceholderAPI. |
| AetherionBeta | Checklist | Beta store | Enabled. |
| AetherionStressBots | QA bots, `/botreport` | `config.yml` (`testbots.enabled: false` by default) | Enabled, did not connect. Effective cap is `min(max-total, server max-players)`. |

Public service registry (`de.aetherion.core.api.AetherServices`): coins, progress, quests, pets, bosses, dungeons, hub, party, harvest, mining, forage, fish, farm, item factory, quest bars, test bots. Callers are expected to null-check. Dungeon transfer (`NetworkPlayerDataSync`) does.

Lifecycle sketch for the paths that were actually read:

- **Coins.** Create: first `add`. Use: `ConcurrentHashMap` + CAS `take`. Save: merge into on-disk YAML under `saveLock`, write `*.tmp`, atomic rename (`AtomicYaml`). Restart: `load()` replaces memory from disk. Disconnect: quit flush exists (`PersistenceFlushListener`; not exercised with a player). Two servers writing one shared `coins.yml`: each holds the whole map; last save wins for keys the other JVM mutated after this JVM’s last load. **STRONGLY INDICATED, STATICALLY INFERRED.** Crash inside the 60 s dirty window loses those seconds by design (`docs/DUPING_CHECKLIST.md`).
- **Ranks.** Create/change: `setRank` / progression sync. Save: new YAML from memory, `config.save`, `IOException` ignored. No per-player merge. Restart: full load. An OP’s join calls `applyLuckPerms`, which adds the `admin` inheritance group.
- **Quests.** Per-player file, dirty set, atomic save. Quit unloads. Not round-tripped with a player.
- **Market.** Listing removed under `listingLock` before `coins.take`; failed take puts the listing back. Confirm clicks are per-player gated (`buying` set). **This matches the duping checklist.** Not spam-tested with two clients.
- **Dungeons.** `InstanceManager` builds a temp world, teleports the party, and has `purgeOrphanSessionsAndWorlds` every 60 s plus `shutdown()` on disable. Warm pool still creates worlds at startup with nobody online.
- **Bosses.** `BossEngine.onDisable` stops the manager, Helios, spawners, and loot displays. Phase logic lives in very large directors. No fight was run.
- **Guild bank.** `depositCoins` takes player coins then adds bank coins then `save()`. Main thread only, so two clicks in one JVM are ordered. No extra lock. Not safe if a future async caller appears. **THEORETICAL** for one server, **STRONGLY INDICATED** if two backends share `guilds.yml` (nothing in the transfer list treats guilds as shared).

---

## 3. Confirmed Bugs (only confirmed/reproduced)

### 3.1 Ungated item-grant commands

- Severity: **CRITICAL**. Evidence: **CONFIRMED**. Verification: **STATICALLY INFERRED** (no player client).
- `AetherionItems/src/main/resources/plugin.yml` registers `aetherionitems` with aliases `kit1`, `kit2`, `pickaxe`, `axe`, `sword`, `shortbow`, `booster`, armor pieces, and others. Only `devmenu` and `shardshop` have a `permission:` node.
- `AetherionCommand.onCommand` gives `createGodPickaxe/Axe/Sword` and god armor on `kit` / `kit1`, god-2 set on `kit2`, and individual tools and boosters from the alias or from `/aetherionitems <name>`, with no `hasPermission` / `isOp` check. Dev menu is the exception (`DevMenu.canUse`).
- Bukkit’s default for a command with no permission node is that every player may run it. LuckPerms cannot deny a permission that was never declared.
- Expected: kit and booster factories are dev-only. Actual: the source grants them to whoever can send the command.
- Repro (not executed here; no client): join a Paper server that has AetherionItems and no extra command blocker, run `/kit1`. Expected god kit in inventory.

### 3.2 AetherionPit does not start without PlaceholderAPI

- Severity: **HIGH** on the Hub backend. Evidence: **REPRODUCED**. Verification: **RUNTIME VERIFIED**.
- `plugin.yml` lists PlaceholderAPI under `softdepend`, not `depend`.
- `AetherionPit.onEnable` line 48 calls `PitPlaceholders.tryRegister(this)`. `PitPlaceholders` extends `PlaceholderExpansion`. Loading that class requires the PAPI jar even if the method would have no-op’d.
- Boot log: `Error occurred while enabling AetherionPit v1.0.0` / `NoClassDefFoundError: me/clip/placeholderapi/expansion/PlaceholderExpansion`, then `Disabling AetherionPit`.
- Same boot: Quests and Guilds check `isPluginEnabled("PlaceholderAPI")` before constructing their expansions, and they enabled. Pit is the one that does not.
- Hub without Pit means no Pit zone, shop, or hub NPC service from this plugin.

### 3.3 Rank sync task dies when LuckPerms is absent

- Severity: **MEDIUM** (production with LuckPerms will not throw; the soft-depend is still false). Evidence: **REPRODUCED**. Verification: **RUNTIME VERIFIED**.
- `RankBadgeService.apply` schedules `syncGroups` 40 ticks later. `LuckPermsSilent.available()` returns false when the plugin is missing, and the bytecode returns before LuckPerms calls. This JVM still threw `NoClassDefFoundError: net/luckperms/api/node/Node` at `RankBadgeService.syncGroups` line 317 (the `invokestatic LuckPermsSilent.syncGroups`). The scheduler logged `Task #86 for AetherionItems generated an exception`. Items stayed enabled. Rank painting that depended on that task did not finish.
- Root cause of the link error was not bisected past the stack. The practical fact is: missing LuckPerms is not a clean no-op.

### 3.4 Farm Island portal search stalls the server thread

- Severity: **HIGH** on first paste or force rebuild. Evidence: **REPRODUCED**. Verification: **RUNTIME VERIFIED**.
- `FarmIslandService.paste` logged `Farm island paste done in 3718ms (263x235x276)`.
- `findPortalCenter` then walks every block of that AABB with `world.getBlockAt` on the main thread (`FarmIslandService.java` around 203–227), called from `ensureIsland` during enable.
- Paper watchdog at 16:03:40, stack `FarmIslandService.findPortalCenter` ← `ensureIsland` ← `AetherionFarming` enable lambda, chunk wait in `aether_farm_island`. Same boot: `Can't keep up! Running 89399ms or 1787 ticks behind`.
- Second boot (flag already pasted) finished in 12.116 s. The stall is the scan, not steady-state tick cost.
- Block visits on that AABB: 263 × 235 × 276 = 17,058,180 `getBlockAt` calls. That count is arithmetic on the logged dimensions, not a profiler sample.

### 3.5 OP is treated as Aetherion Admin

- Severity: **HIGH** against the published lock. Evidence: **CONFIRMED**. Verification: **STATICALLY INFERRED**.
- `RankBadgeService.extraFor` (lines 289–298): online player who `isOp()` or has `group.admin` or `aetherion.rank.admin` gets `rankByGroup("admin")`.
- `applyTo` → `applyLuckPerms` → `LuckPermsSilent.paintUser` clears managed inheritance groups and adds the extra group. For an operator that extra group is `admin`. `syncGroups` also ensures the LuckPerms `admin` group has `aetherion.rank.admin`.
- `git blame` / `git log -S`: this `isOp()` branch has been on `main` since `a193a5f`. There is no `ROBB` UUID on `main`.
- Unmerged `b57c344` (“Stop auto-applying special ranks from permissions on join”) and `147c8bc` (“Keep the Admin cosmetic exclusive to Robb”) describe the lock and were never merged. Merging those branches wholesale is unsafe: they also carry Ashen Sheath, altar-outline, and katana commits.
- This is a lock that `main` does not implement. It is not a regression from a correct revision of `main`.

### 3.6 Transfer snapshot directory

- Severity: **MEDIUM**. Evidence: **REPRODUCED** (the warning and the config). Verification: **RUNTIME VERIFIED** for the warning; the failed save itself was not given a player to trigger.
- `AetherionDungeons/src/main/resources/config.yml` sets `remote-transfer.shared-dir` to `/var/opt/minecraft/crafty/shared/transfer`.
- Boot: `[AetherionDungeons] Could not create transfer snapshot dir: /var/opt/minecraft/crafty/shared/transfer`.
- `TransferSnapshotStore` keeps that `File` when `mkdirs` fails. Later `save` will fail to write. On the real Crafty layout this path is intentional (`SETUP.md`). Anywhere else, transfers do not work until the config is overridden. The jar default is not “derive relative to the server”.

---

## 4. Suspected Regressions

Nothing in `git log` on `main` shows a previously correct implementation of the rank lock or the restart countdown being deleted. Both were never merged. Calling them regressions of `main` would be inaccurate. They are unshipped lock work.

| Item | What `main` has | Where the other behavior lives | Label |
|------|-----------------|--------------------------------|-------|
| Admin cosmetic exclusive to a stored rank row, OP ignored | OP / `group.admin` paints Admin and is written to LuckPerms | `origin/cursor/stored-devmenu-ranks-e54a`, `origin/cursor/stop-lp-cosmetic-sync-4ceb` | **LOCKED GAP, not a main regression.** Do not merge the branch tip; it is tangled with boss and katana commits. |
| Restart chat on even seconds 10→8→6→4→2 | Class absent. `git merge-base --is-ancestor 6b8f2ea HEAD` is false | `origin/cursor/aenet-restart-countdown-398b` (`RestartCountdown.java`, tests included) | **LOCKED GAP.** |
| Forage/hub TextDisplay cap | `IsleGuideNpc.ensureHologram` removes nearby tagged displays, then spawns a **persistent** TextDisplay. If `Bukkit.getEntity` misses an unloaded display, `tickHologram` calls `ensureHologram` again | `49cd2a7` on `origin/cursor/forage-hologram-leak-470a`, never merged. `DisplayEntities` does not exist on `main` | **STRONGLY INDICATED leak, STATICALLY INFERRED.** Not reproduced (no long session, no FancyNpcs). |
| Altar outline display leak | `BorderlandsRiteService.ensureAltarOutline` reuses one `BlockDisplay`, scrubs orphans, `setPersistent(false)` | Older leak commits exist (`a769061`, `d382547`) and the current main method already reuses one display | **Not an open leak in the current method.** UNTESTED under a real Borderlands crowd. |
| Hub snapshot omitting inventory wipes destination gear | `TransferSnapshotStore` version 4 always writes `inventory-b64`. Apply clears inventory, then `setContents` whenever `decodeItems` returns non-null. A missing key becomes an empty list, which is non-null, so a snapshot **without** the key would leave the player empty after the clear | `2abbd28` on `origin/cursor/hub-transfer-inventory-omit-8bee` | **POSSIBLE** for a hand-edited or future hub writer. **Not** the current writer. |
| Booster item stack size 1 | `BoosterItems.applyBoosterData` `setMaxStackSize(1)` since `a193a5f` | README “stackable lore” is the stat-stacking lore (`BoosterStats`, 14 sockets), not item stack size | **Not a regression.** |
| Blossom Blade / Gravwell combat | Listeners and factories still present (`AshenKatanaListener`, `GravwellCleaverListener`, `CustomItem` ids) | Several `cursor/katana-*` and `cursor/ashen-katana-polish-011a` branches are unmerged | **Do not merge those branches** under a health fix. They change locked combat. `main` still has the pre-polish combat. |
| “Keep boosters on upgrade” | Commit `26f625d` is on `main` (“Ship Eldervale plugins sync: … + keep boosters on upgrade”) | — | Present. Not re-tested in game. |

Hot files since August (commit touch count, not a quality judgment): `SkillService` 11, `DevMenu` 8, `CustomItem` 7, `BossInstance` 6, `DialogManager` 5, `AetherionItems` main class 7. That is where an agent is most likely to clobber a working path. It is not evidence those files are wrong.

---

## 5. Performance Findings

### Observed (this VM, Paper 1.21.1-133, `-Xmx2G`, all modules, empty worlds)

- First boot `Done (134.702s)`. Dominated by world creation: overworld/nether/end, `helios_requiem`, `aether_guilds`, `aether_islands`, `aether_veins` (66 MB), `aether_test`, `aether_farm_island`, `aedun_warm_void_*`, `aedun_xl_base`.
- One watchdog and one 89 s “can’t keep up”, both during that first-boot farm scan and chunk gen. Not a steady-state TPS measurement. No spark profile was read.
- Second and third boots, worlds already present: `Done (12.116s)` and `Done (11.690s)`.
- Shutdown of the first boot: `Waiting 60s for chunk system to halt` for the warm void dungeon world. One observation.

### Static (not timed)

- `runTaskTimer` is used across essentially every feature plugin (boss directors, pet AI, zone services, isle events, quest markers, `TalkUx`). That is the game loop. It is not, by itself, a defect.
- `PlayerMoveEvent` handlers are few (farm portal, guild, pit, pet skill, hub pads, dungeon, hub portal, harbour gate, two arenas, portal gun). Not a “move event on every system” pile.
- `FarmIslandService.findPortalCenter` is O(schematic volume) on the main thread. **Observed.**
- `IsleGuideNpc` and forage ledger/cast spawn `TextDisplay`s. Scaling is O(respawns × chunks) if the reuse check misses. **THEORETICAL** until a long session counts entities.
- Boss directors (`HollowSunDirector`, `AshenSovereignDirector`, `HangingSaintDirector`, `WorldEaterDirector`, `BossInstance`) are large per-tick state machines. Cost scales with **active** bosses and nearby players, not with the YAML file size. No fight was run, so particle and entity counts are **UNTESTED**.
- Pet spawn (`PetSpawnManager`) and zone services tick while players are in range. Empty server cost was not isolated.
- Guild log line: quarries tick while the server runs, even with zero guilds. **LOW**, constant, not measured.
- Mining veins first-gen and Amethyst dig paint ran at startup and reported 0 writes on this empty world. The paint path is the one that can rewrite a hub if the mask is wrong (`VeinsDigZones` logs `BUG: dig paint attempted … hub writes` when that happens). It did not fire here.

No invented capacity numbers. See section 21.

---

## 6. Persistence Findings

**Verified**

- `CoinService` memory → `coins.yml` (`players.<uuid>`, `lifetime.<uuid>`) → process stop → new process `load()` → memory 4242. Probe: `AuditProbe` on `ServerLoadEvent`, UUID `00000000-0000-0000-0000-000000000777`, `add(4242)` + `save()`. Boot 2 log: `AUDIT wrote after=4242`. File contained both sections. Boot 3 log: `AUDIT coins before=4242` / `AUDIT roundtrip seen=4242`. **RUNTIME VERIFIED.**
- `AtomicYaml` is write-temp + `ATOMIC_MOVE`, with a non-atomic fallback, and `recoverTemp` promotes a temp file only when the live file is missing or empty. A crash during the rename window can leave a good temp beside a good live file; recovery will **not** replace a non-empty live file. That matches the class comment. **STATICALLY INFERRED.** Not kill-9 tested.

**Code-confirmed risks**

- Rank file: `RankBadgeService.save` builds a fresh `YamlConfiguration`, `config.save(file)`, catches `IOException` and ignores it. A disk-full save fails silently and the next successful save can still be a full overwrite. **HIGH** if this file is the network source of truth, **MEDIUM** otherwise. **CONFIRMED, STATICALLY INFERRED.**
- Coins `save()` loads the live file and overlays this JVM’s map. Two backends sharing one `coins.yml` can drop the other’s updates. The duping checklist already says a hard kill inside 60 s loses unsaved seconds, and that per-player coin files are not done. **STRONGLY INDICATED, STATICALLY INFERRED.**
- `TransferSnapshotStore.save` writes the snapshot, then `clearLivingInventory`. If the proxy move never happens, the source player is empty until some server applies `{uuid}.yml`. Documented in `docs/DUPING_CHECKLIST.md`. **CONFIRMED design, UNTESTED** with a real Velocity hop.
- Apply path clears inventory before restoring. A snapshot missing `inventory-b64` restores an empty array (see section 4). **POSSIBLE.**
- Quest files use `AtomicYaml` for `YamlConfiguration`. **Looks sound. UNTESTED** with a player.
- Guild `save()` is a full-file rewrite through `AtomicYaml`. Crash mid-write should not truncate. Concurrent writers still last-win. **STATICALLY INFERRED.**
- Market, shards, skills, codex, storage are flushed from `AetherionItems.onDisable` after GUIs are closed. Order matches the checklist comment. **Not exercised** with an open GUI.
- `NetworkPlayerDataSync.importAll` can `dispatchCommand` `tab scoreboard show main <name>` if TAB is present. Player name comes from the online player object, not from YAML text. **LOW** command injection surface. **STATICALLY INFERRED.**

**Not tested:** inventory, equipment, XP, skills, quests, pets, loadouts, storage, guild bank, island data, dungeon snapshots, ranks round-trip, crash `-9`, two players, full inventory, disconnect mid-quest.

---

## 7. Concurrency Findings

- Coin `take` is a CAS loop. Coin `add` is `ConcurrentHashMap.merge`. **Sound for one JVM.**
- Market `buy` holds `listingLock`, removes the listing, debits, and restores the listing if debit fails. Per-player `buying` set blocks double-clicks from the same player. Two buyers are serialized by the lock. **Matches the checklist. UNTESTED with two clients.**
- Guild bank mutations are not locked. The Bukkit main thread serializes them. **THEORETICAL** race if anything calls `depositCoins` off-thread. **Do not** move this to async without a lock.
- Rank `assigned` / `extras` are concurrent maps, but `save()` iterates them while building YAML with no lock against `setRank`. A concurrent `setRank` during `save` can throw or write a partial view. `setRank` is main-thread today. **THEORETICAL.**
- Dungeon `byOwner` / `byPlayer` / `byWorld` updates on the main thread around build failure do remove the maps and delete the world. **Not torture-tested.**
- Boss damage and loot shares were not executed. Duplicate-loot claims are checklist “manual QA”, still open.

---

## 8. Cross-Plugin/API Findings

- Enable order observed: Core (STARTUP) → Hub → Items → Mobs → Beta → Quests → Farming → BossEngine (hooks Items loot bridge on the next tick) → Guilds → Foraging → Mining → StressBots → Dungeons → Pit (failed) → Fishing. BossEngine’s `loadbefore: Multiverse-Core` does nothing here because Multiverse is absent. That hook is for production world_eater void gen. **UNTESTED** against real Multiverse.
- `AetherServices` is the intended seam. Dungeon transfer null-checks progress, pets, and quests. **Good.**
- Pit’s PAPI coupling is a hard class dependency hiding behind `softdepend` (section 3.2). Items’ coin placeholder is constructed only when PAPI is enabled. **That pattern is the one to copy.**
- LuckPerms is `softdepend` and is not safe to call (section 3.3).
- FancyNpcs is reflection (`FancyNpcFacade`). Foraging, Pit, and Quests logged missing interact events and continued. **RUNTIME VERIFIED** as a degraded but alive start. NPC click gameplay is **UNTESTED** and will not work without FancyNpcs.
- WorldGuard is a real `depend` for Farming, Foraging, and Mining. They enabled once the dist jars were present. The Maven `worldguard-bukkit` artifact (273 KB) is not a server plugin; the Modrinth dist jar is. **INFORMATIONAL** for anyone who copies the compile-scoped jar into `plugins/`.
- Items owns harvest/combat math; gather plugins own zones. `docs/OWNERSHIP.md` still matches that split at the level read for this audit. A line-by-line contract diff was not finished.

---

## 9. Dungeon Findings

- `InstanceManager` owns session maps, a 1 s room scan, a 60 s orphan purge, build failure cleanup (`deleteWorld`), and `shutdown()` from `AetherionDungeons.onDisable`. **STATICALLY INFERRED** as a real lifecycle, not a fire-and-forget.
- Warm pool runs with no players. Boot created `aedun_warm_void_d32a867a` and `aedun_xl_base`. Shutdown paid a 60 s chunk halt on the warm void world. **REPRODUCED.**
- Transfer v4 is claim-by-rename (`uuid.yml` → `uuid.claimed.yml`) so a second join should not apply twice. **STATICALLY INFERRED.** Not tested.
- Inventory is cleared on the source after a successful snapshot write. Residual empty-inventory if the hop fails: already in the duping checklist.
- Party disconnect, loot chest double-claim, reconnect, and two simultaneous instances: **UNTESTED.**
- Floor/room builders (`PrototypeDungeonBuilder`, `LerfingTestBuilder`, `EndlessSchemBuilder`) were not executed. Impossible connections and room collisions: **UNTESTED.**
- No evidence on `main` that dungeon code was gutted. The booster-wipe fix commit `83b7f85` is on an unmerged branch and also tried to replace anvil boosters; `main` already has `BoosterSockets` and a 14-socket menu, so that branch is not a clean patch.

---

## 10. Boss Findings

- 36 templates loaded. `helios_requiem` world is created at enable with 3 instance slots. **RUNTIME VERIFIED.**
- `onDisable` stops Helios, spawners, `BossManager`, `HollowReliquary`, `SeraphineMusicBox`, `WorldEaterBonusChest`, saint stage, and `WorldEaterSite`. **STATICALLY INFERRED** cleanup on the happy disable path. Not tested after a crash mid-fight.
- Scripted directors are the combat. They are large on purpose (Hollow Sun, Ashen Sovereign, Hanging Saint, World Eater, Unbroken, Sandbox, Ashes Elite, Ashen Sheath). **Do not simplify them in a health pass.**
- No spawn → phase → leave → return → death → cleanup run. Orphan displays, duplicate instances, and cross-boss interference: **UNTESTED.**
- Deprecated `EntityRemoveEvent` on `BossCombatListener.onBossBodyRemoved`. Paper says performance is affected. **LOW**, warning reproduced, impact not measured.
- Helios and Seraphine/World-Eater SFX on `MASTER` are recent commits on `main` (`bb5f6cb`, `cd908ae`). Not listened to.

---

## 11. Island/Guild/Quarry Findings

- Guilds enable creates two worlds (`aether_guilds`, `aether_islands`) and states that quarries tick for the whole uptime. **RUNTIME VERIFIED** creation. Tick cost **UNTESTED** (no minions placed).
- Bank deposit: permission check, cap, `takeCoins`, then `setBankCoins`, then atomic full save. Withdraw: decrement bank, then `addCoins`. A crash between those two lines loses or duplicates coins depending on direction. **POSSIBLE, STATICALLY INFERRED**, one main-thread window, not reproduced.
- Island ownership and permission leaks: **UNTESTED.** No player, no WorldGuard regions exercised beyond “config loaded”.
- Farming isle paste is a separate world from the Eldervale farm grid in `world` (`FarmIsleZones` comment). Both exist. The volume scan (section 3.4) is the farm-island world, not the Eldervale grid.
- Forage/mine/fish Eldervale content is data-heavy and the two unit tests lock fishing waters (12 waters, footprint) and farming data. **RUNTIME VERIFIED** that those tests pass. World coordinates assume a live paste the test server did not have, so gameplay on those grids is **UNTESTED**.

---

## 12. Quest/NPC/World Findings

- `TalkUx` is present and still described in source as a `TextDisplay` bubble, reply chips, and a quest card, per listener. **LOCKED SHELL INTACT. STATICALLY INFERRED.** No NPC was clicked.
- FancyNpcs absent: Quests logged editor NPCs will not restore; Foraging logged Pell/Tamsin/Juniper not placed and interact events missing; Pit logged hub NPC clicks dead (and then Pit disabled for the PAPI crash). **RUNTIME VERIFIED** degradation.
- `DialogManager` is ~large and still the dialog owner. German overlay: `lang/de.yml` exists. The handoff (`docs/CLOUD_CHAT_HANDOFF.md`) says an EN rewrite + DE overlay was in progress as of 2026-09-29 and `main` is that tip. Key-by-key EN/DE parity was **not** fully diffed. **UNTESTED** as a player.
- Quest rewards in `QuestManager` still call `customItem.createRandomBooster()` and vanilla item grants. A full “reward id with no factory” pass was **not** completed. **UNTESTED** beyond spot reads.
- Fresh-player walkthrough: **not done.** No Minecraft client in this VM.

---

## 13. Item/Combat Findings

- Blossom Blade: id `ashen_katana`, `AshenKatanaListener` (dash, rise, blossom-crown, slam — comment in source), `BossGearBalance` case, Dev menu factory, `CustomItem.createAshenKatana`. **Present. Combat feel UNTESTED. Do not edit.**
- Gravwell Cleaver: id `gravwell_cleaver`, `GravwellCleaverListener`, balance case, factory. **Present. UNTESTED. Do not edit.**
- Boosters: `BoosterSockets` stores 14 sockets; `BoosterSocketMenu` is the anvil UI; `BoosterLimits` / lore say 14 total; item stack size is 1 since the initial commit. Stat stacking is the lock called “stackable lore”. **Shell present. Socket behavior UNTESTED.**
- Borderlands spirit vials are water potions with PDC (`borderlandsSpirit`, boss id), not drinkable by lore. No `setMaxStackSize`, so they follow potion stacking (1). **STATICALLY INFERRED.** Whether they should stack as items is the lock phrase “Borderlands vials” without an explicit stack size in `main`. Do not change them in a drive-by.
- `CustomItem.java` is ~8,900 lines and the item factory. 114 custom-model-data assignments, 101 unique, 11 duplicated integers (examples: 2202 ×3, 2203 ×3, 1005 ×2). Duplicates can be intentional shared models. **INFORMATIONAL** until the texture pack is diffed.
- Stat math lives in `ItemStats`, `BossGearBalance`, `BalanceTargets`, equipment providers. `onDisable` clears `ActiveEquipmentStats` providers specifically to stop stacking across reloads. **Good. Formula correctness UNTESTED.**
- `/kit1` god items bypass progression entirely (section 3.1). That is the combat-economy integrity hole.

---

## 14. StressBot/QA Findings

- Default `testbots.enabled: false`. Boot log: `Testbots ready (enabled=false, runner=127.0.0.1:18765, max=5)`. `max=5` is `min(config 40, server max-players 5)` from `TestBotController.maxTotal`. **Not a product bug.** This test server set `max-players=5`.
- Roles in README and code: mine, forage, catch, roam, combat, fish, trade, quest, pad. They need a Node runner, Velocity modern forwarding secret, and offline-mode MMO-R. **Not started.** No secret was created or used.
- README contradicts itself: the Wave 2 catch row says bots throw spheres and click the timing window; the “Catch limits” section says they do **not** play the timing minigame. The second paragraph matches the older wave-1 limit. **INFORMATIONAL** doc drift. Which one the runner does was not executed.
- Explicitly not covered by the bots (README): full quest trees, anvil fusion, every spawn unlock, skill hotkeys. They will not, by themselves, prove persistence or the kit-command hole.
- `/botreport` reflects what the plugin’s tracker recorded. It cannot see systems the bots never touch. **STATICALLY INFERRED.**

---

## 15. Configuration Findings

- Dungeon `remote-transfer.shared-dir` is an absolute production path (section 3.6).
- StressBot `max-total: 40` is real; the log prints the clamped value. Jar defaults do not overwrite a live YAML (`SETUP.md`). **INFORMATIONAL.**
- Pit `softdepend` PlaceholderAPI does not match the class dependency.
- Items `softdepend` LuckPerms does not match the rank task’s class dependency.
- Many Items commands have no permission node (section 3.1). `aetherion.shardshop` defaults to true, which matches the permission message (“open to everyone”).
- CI (`.github/workflows/compile.yml`) runs `mvn -B -DskipTests compile` on push and PR. It does not run the Farming/Fishing tests. A green CI run does not mean those tests ran.
- `mvn -pl AetherionFarming test` without `-am` fails: sibling jars are not in Maven Central, and `mvn package` does not `install` them. `mvn -pl AetherionFarming,AetherionFishing -am test` succeeds. **Toolchain fact, not a compile error.**

A full “every YAML key vs every `get`” pass was not completed. Dead keys are **UNKNOWN** beyond the mismatches above.

---

## 16. Resourcepack/Asset Integration Findings

- `https://github.com/robb-devo/aetherion-texturepack` is not accessible here (`gh repo view` → repository not resolved). **No pack diff was done.**
- In this repo, item identity is PDC `ItemKeys.item()` plus `CustomModelData` set in `CustomItem` / `BoosterItems`. Without the pack, missing textures, stale model overrides, and unused PNGs are **UNTESTED**.
- Duplicate model-data integers exist (section 13). That is a static smell, not a proven missing texture.
- Talk UX fonts and dialog assets are called out in forage docs as pack-owned. **UNTESTED.**

---

## 17. Security/Integrity Findings

- **CRITICAL** ungated kit/booster/tool commands (section 3.1). This is item and progression integrity, not a theoretical exploit chain.
- **HIGH** OP → LuckPerms `admin` group write (section 3.5). If the live `admin` group has more than a prefix, de-op is not enough until the next `applyTo` removes it, and `applyTo` only removes it when `extraFor` stops returning admin. An operator who is de-opped but still has `group.admin` from this write keeps the cosmetic and the node. **STATICALLY INFERRED.**
- Shard give and skill XP admin commands check `aetherion.shards.admin` and `aetherion.skills.admin`. **Those paths are gated.** Arena teleports check op or `aetherion.devmenu`.
- Dev menu checks op or `aetherion.dev`. That is operator access, which is separate from the Admin cosmetic lock.
- Casino, bazaar, and AH are player-facing with no extra permission. That is normal if the economy checks hold. AH double-buy is guarded in code (section 7). **UNTESTED** live.
- Wipe (`BetaWipe`) deletes named progression files and vanilla playerdata under a layout derived from the server root. It runs when a flag file exists. Path traversal was not fully audited. **UNTESTED.** Do not run it against a real Crafty tree.
- No external scanning, no production hosts, no credential use.

---

## 18. AI Regression Risk Map

Future agents break this tree by rewriting a large file that already works, or by merging an old unmerged branch that is not a clean diff against today’s `main`.

| Area | Why it breaks | Preserve | Read before editing | After |
|------|---------------|----------|---------------------|--------|
| `AetherionCommand` / `plugin.yml` commands | Looks like “debug leftovers” | Player-facing commands that are intentionally public (`/ah`, `/skills`, `/bazaar`). Gate only the factories that grant gear | `AetherionCommand.java`, Items `plugin.yml` | A non-op player must not receive god kits. `/ah` and `/skills` must still open |
| `RankBadgeService` + `LuckPermsSilent` | OP vs Admin is a lock; LP calls crash if linked wrong | Progression rank from account level; do not invent a new rank ladder | Both classes, `player-ranks.yml` shape, unmerged rank commits as **reference not a merge** | OP does not gain `admin`. Missing LP does not throw. Existing stored extras still load |
| `BoosterSockets` / `BoosterSocketMenu` / `BoosterItems` | “Cleanup” changes stack size, socket count, or lore | 14 sockets, stat application while socketed, item ids | Those three, `ItemKeys.boosterSockets` | An item with 14 sockets still loads |
| `AshenKatanaListener`, `GravwellCleaverListener` | Polish branches already exist and must not be merged casually | Abilities, VFX, sounds, tuning | Do not open them except to avoid them | No diff |
| `TalkUx`, `DialogManager`, `LivingNpcService` | Dialogue rewrites delete the bubble | TextDisplay bubble, chips, quest card, per-player visibility | `TalkUx.java` header, `docs/CLOUD_CHAT_HANDOFF.md` | Bubble class still starts |
| `FarmIslandService.findPortalCenter` | Fixing the stall by deleting the island | Paste, exit location, `farm-island.pasted` | The scan only | Exit location still saved; main thread not walking 17M blocks |
| `TransferSnapshotStore` | “Simplify” drops claim-rename or clears inventory differently | v4 blobs, claim file, clear-after-save on the source | Store + `NetworkPlayerDataSync` | One apply per snapshot; coins overlay one UUID |
| `CoinService` + `AtomicYaml` | Per-player file “cleanup” is a format change the checklist forbids | `players.<uuid>` / `lifetime.<uuid>`, CAS take, 60 s flush | `docs/DUPING_CHECKLIST.md` | Round-trip like the probe |
| Boss directors | Size looks like a rewrite target | Phase order, loot, cleanup | The director you were asked to touch, not its neighbors | One boss fight still reaches death and removes displays |
| Unmerged `cursor/*` branches | `git diff main...branch` is huge because the branch is old | Merge **one** reviewed commit, or re-implement on current `main` | `git log origin/main..branch` | `git diff` against `main` is only the intended files |

---

## 19. DO NOT TOUCH — WORKING SYSTEMS

- `AtomicYaml` and `CoinService` CAS + dirty epoch + disable flush. Round-trip verified.
- Market `buy`: remove listing, then debit, restore on failed debit.
- `TalkUx` speech bubble architecture.
- `AshenKatanaListener` / `GravwellCleaverListener` and their item ids.
- `BoosterSocketMenu` 14-socket anvil and `BoosterSockets` PDC.
- `AetherServices` null-checked transfer flush (`NetworkPlayerDataSync.flushPlayer`).
- BossEngine disable order (managers, then loot displays, then World Eater site). Do not collapse directors.
- Farming and Fishing Eldervale data tests. They passed. Don’t “fix” numbers to satisfy taste.
- Gather vs Items ownership (zones in gather plugins, items and XP in Items).
- Core staying a library. The BossEngine cursor rule says Core must not grow a game loop. The restart countdown, if it is ever merged, is a small command clock, not a reason to move combat into Core.

---

## 20. Underused Existing Capabilities

Informational only. These exist in source and are easy to miss:

- `AetherServices` already has farm, fish, forage, mining, harvest, party, quest bars, test-bot, and boss-spawn access. New plugins should register there instead of new reflection bridges. FancyNpcs is the exception that still uses `FancyNpcFacade`.
- Dungeon warm pool and Helios multi-slot instancing already allocate worlds. The game does not need a second instance framework.
- `QuestBars` owner-keyed leases (`ec452f6` on history) exist so minigame HUDs do not unhide each other. New boss bars should use that lease.
- StressBot trade/quest/fish roles already click real GUIs. They are off unless `testbots.enabled` and the runner are up.
- `/aetherpaste` async island paste exists in Hub. It was not run (needs FAWE for the path the command prefers).
- Borderlands rite, crypt vials, and colosseum escort are a full altar loop in `BorderlandsRiteService` / `ColosseumEscortService`. Not played.
- Codex, bestiary, collections, milestones are commands (`/codex`, `/collection`, `/bestiary`). Not opened.

---

## 21. Scaling Risks

No player-capacity claim. Curves from the code and from one empty boot:

| Driver | What grows | What stayed flat in the empty boot |
|--------|------------|-------------------------------------|
| Plugin enable | Worlds: Helios, two guild worlds, veins, test arena, farm island, dungeon warm worlds. **Observed** even at 0 players | Coin map, rank map (empty files) |
| Players in combat | Boss director ticks, pet AI, damage listeners, `TalkUx` displays per conversation | YAML boss count (36) is load-time, not per player |
| Islands / quarries | Minion ticks for placed minions; guild save size | “Quarries tick while the server runs” is a constant scheduler even at 0 guilds |
| Dungeon instances | One world plus entities per instance; warm pool holds worlds **before** players arrive | — |
| Displays | Persistent TextDisplays and BlockDisplays that fail to reuse (forage hologram **indicated**, altar outline **reused**) | — |
| `coins.yml` | Full map load and full merge save. Grows with historical players, not online players | 60 s period is constant |
| Farm paste | O(volume) once per paste | Not per player |

5 vs 200 players does not change the enable-time world creation. It does change boss, pet, NPC, and display cost, which was not measured.

---

## 22. Recommended Priority Order

Technical order only.

1. Put a real permission on gear-granting commands and enforce it in `AetherionCommand`. Do not remove `/ah`, `/skills`, `/bazaar`, `/party`, `/guide`.
2. Make Pit’s PlaceholderAPI use the same “class not loaded unless plugin present” pattern as Quests, or move Pit to `depend`. Hub is down without it.
3. Stop `findPortalCenter` from walking the whole schematic on the main thread. Cache the portal coordinate at paste time in the edit session, or scan chunk snapshots off-thread and apply one location on the main thread.
4. Rank lock: stop deriving Admin from `isOp()` / `group.admin`, and stop writing that group in `paintUser`. Do this on current `main`. Do not merge the old rank branches. Keep progression ranks and stored `extras`.
5. Make the LuckPerms call fail closed without `NoClassDefFoundError` (isolate the API types so the class loads when LuckPerms is absent).
6. Save `player-ranks.yml` like coins: merge, `AtomicYaml`, do not swallow `IOException`.
7. Dungeon transfer dir: if `mkdirs` fails, log the error you already log and do not pretend snapshots work; consider a relative default for non-Crafty hosts without changing production’s absolute path when that directory exists.
8. Forage hologram: reuse tagged displays and do not spawn a new persistent one when the old one is merely unloaded. Re-implement on `main`; do not merge `forage-hologram-leak-470a` as a whole if the diff is wider than that.
9. Warm-pool shutdown: don’t block disable for 60 s on an idle void world. Confirm on a dungeon-only boot before changing pool policy.
10. Only then: two-client AH, guild bank, quest, and pet round-trips.

---

## 23. Unknowns / Areas Requiring Human Testing

- Any joined player: kits, quests, NPC bubble, combat feel, vials, anvil sockets, boosters on upgrade.
- Velocity hop: snapshot, inventory clear, apply once, coins overlay, failed connect.
- LuckPerms present: does `syncGroups` rewrite live group weights/prefixes (`writeGroupMeta` clears weight and prefix nodes on every rank group at startup)? **STATICALLY INFERRED** as a production side effect. Not run with LP installed.
- Multiverse load order vs `world_eater` generator.
- FancyNpcs click path, TAB, DiscordSRV.
- Texture pack vs model data.
- Two players on one listing, guild bank, dungeon loot.
- Long bot session, entity counts, memory.
- German overlay completeness.
- Whether production Pit always has PAPI (if it does, 3.2 is latent until PAPI is removed).
- Kill -9 inside the 60 s coin window (designed loss). File truncation was not tested; atomic rename makes truncation unlikely. **UNTESTED.**
- WorldGuard region flags were defaults (TNT permitted in new worlds). Not the live map.

---

## 24. Suggested Test Suite

Keep these small. Do not generate a second framework.

1. **Permission.** Paper + Items, no LP. Non-op fake or real player: `/kit1`, `/aetherionitems booster diamond`, `/pickaxe` must not give items. `/ah` and `/skills` must still open.
2. **Pit.** Boot Hub jars with and without PAPI. With: Pit enables. Without: Pit enables and skips placeholders.
3. **Ranks.** With LP: op a test UUID, join, deop, join. LuckPerms user must not retain `admin` from the op join. Stored `extras` in `player-ranks.yml` must survive restart. Missing LP: no exception in the log.
4. **Coins.** The probe already done: add, save, stop, start, balance matches. Add a second JVM test only if coins stay a shared file.
5. **Farm paste.** Time `ensureIsland` on a copy of the schematic. Main thread must not block for a full volume walk. Exit coordinates still written.
6. **Transfer.** Two local Paper instances, shared dir that exists. Snapshot, kill the destination before apply, confirm the source inventory policy is the one you intend, then apply once.
7. **Dungeon.** Start one instance, stop the server, assert no `Waiting 60s` on an idle warm world, or document that the wait is accepted.
8. **Quests/pets.** One player, accept a quest, catch or dev-give a pet, stop, start, state matches.
9. **Data tests.** Run `mvn -pl AetherionFarming,AetherionFishing -am test` in CI, not only `compile`.
10. **Do not** add tests that instantiate boss directors just to assert style.

---

## 25. FINAL VERDICT

This is a large, rapidly assembled Paper MMO that **builds, boots, and keeps coins** when the process is allowed to stop cleanly. The economy write path and the service registry are better than the size of the classes suggests. The boss and item files are big because the content is big. That is not the problem.

The problem is a handful of concrete holes and a set of locks that live in docs and unmerged branches rather than on `main`. The worst hole is that god-kit commands are public. The worst operational hole on Hub is Pit dying without PlaceholderAPI. The worst stall we actually watched is the farm-island portal scan on first paste. The Admin/OP lock is unimplemented on `main`, and the restart countdown the docs name is not in this tree at all.

It is not a rewrite candidate. It is a “fix these paths, then playtest the ones this VM could not join” candidate. Merging the pile of old `cursor/*` branches to “catch up” would change locked weapons and mix unrelated diffs. Re-apply the intended behavior on today’s `main`, in the files named in the handoff, and leave the directors and the bubble alone.

---

# AETHERION — ENGINEERING HANDOFF

### FIX NOW (confirmed, meaningful impact)

#### 1. Gear-grant commands are public

- Subsystem: AetherionItems commands.
- Files: `AetherionItems/src/main/resources/plugin.yml` (`aetherionitems` and aliases), `AetherionItems/src/main/java/de/aetherion/items/command/AetherionCommand.java`.
- Problem: `/kit1`, `/kit2`, tool aliases, and `/aetherionitems …` / `/aetherionitems booster …` add custom items with no permission check.
- Evidence: CONFIRMED, STATICALLY INFERRED. No `permission:` on those commands. `onCommand` returns after `addItem` of god sets.
- Reproduction: join and run `/kit1` on a server with only this suite. Not executed here (no client).
- Risk: any player mints endgame gear and boosters. Breaks economy and combat.
- Expected: only operators or an explicit dev permission receive those items.
- Direction: add a permission (the existing `aetherion.dev` node is the one Dev menu already uses) in `plugin.yml` **and** at the top of the grant branches. Keep `/ah`, `/bazaar`, `/skills`, `/codex`, `/party`, `/guide`, `/trades`, `/gamble` public if they are public today.
- Must not change: item ids, booster socket behavior, Blossom Blade, Gravwell Cleaver, recipes.

#### 2. Pit enable crashes without PlaceholderAPI

- Subsystem: AetherionPit.
- Files: `AetherionPit/src/main/java/de/aetherion/pit/AetherionPit.java` (line 48), `AetherionPit/src/main/java/de/aetherion/pit/placeholder/PitPlaceholders.java`, `plugin.yml` `softdepend`.
- Problem: `PitPlaceholders extends PlaceholderExpansion`, so the class cannot load without PAPI. The call is unconditional.
- Evidence: REPRODUCED, RUNTIME VERIFIED. Boot log `NoClassDefFoundError` then `Disabling AetherionPit`.
- Reproduction: start Paper with the built jars and without PlaceholderAPI.
- Risk: Hub Pit does not run.
- Expected: Pit enables; placeholders register only when PAPI is present.
- Direction: split the expansion class so `onEnable` does not reference it unless `isPluginEnabled("PlaceholderAPI")`, matching `AetherionQuests` / `AetherionGuilds`. Or declare a hard `depend` if Hub always has PAPI. The first option matches the current `softdepend`.
- Must not change: Pit zone rules, safe-box, shop prices.

#### 3. Farm Island portal scan blocks the main thread

- Subsystem: AetherionFarming island paste.
- Files: `AetherionFarming/src/main/java/de/aetherion/farming/island/FarmIslandService.java` (`paste`, `findPortalCenter`, `ensureIsland`).
- Problem: after paste, every block in the schematic AABB is read on the server thread.
- Evidence: REPRODUCED, RUNTIME VERIFIED. Log `263x235x276` in 3718 ms, then watchdog stack in `findPortalCenter`, then 89399 ms behind. Later boots were ~12 s once `farm-island.pasted` was true.
- Reproduction: delete `farm-island.pasted` (or use a fresh world) and boot with Farming + WorldEdit.
- Risk: multi-second to multi-ten-second stall on every fresh paste or force rebuild. Not a per-player leak.
- Expected: paste still records an exit location; the server keeps ticking.
- Direction: record portal blocks during the WorldEdit operation, or limit the scan to portal chunks, off the main thread, then set spawn on the main thread. Keep `farm-island.pasted` and the exit keys.
- Must not change: Eldervale farm rhythm, bird scare, crop tables, locked weapons.

#### 4. OP is written as LuckPerms admin

- Subsystem: ranks.
- Files: `AetherionItems/src/main/java/de/aetherion/items/rank/RankBadgeService.java` (`extraFor`, `applyTo`, `syncGroups`), `LuckPermsSilent.java` (`paintUser`, `syncGroups`).
- Problem: `isOp()` or `group.admin` or `aetherion.rank.admin` selects the admin rank, and `paintUser` adds that inheritance group.
- Evidence: CONFIRMED, STATICALLY INFERRED. Present since `a193a5f`. Unmerged fixes: `b57c344`, `147c8bc` (do not merge those branches).
- Reproduction: with LuckPerms, op a test account, join, inspect `lp user <name> info` for group `admin`. Not run here (LuckPerms not installed). The code path is direct.
- Risk: operator status and the Aetherion Admin cosmetic/group are the same. De-op does not stick if the group remains.
- Expected: Admin comes from the stored rank row the owner designated, not from OP. Progression ranks still follow account level.
- Direction: change `extraFor` so OP and permission nodes are not inputs. Persist admin only through `setRank` into `extras`. Keep `paintUser` from adding `admin` unless that stored row says so.
- Must not change: rank ladder weights, TAB/prefix format beyond the admin mix-up, MVP++ UUID list behavior unless the owner asks, Blossom Blade, boosters.

### FIX SOON

#### 5. LuckPerms absence throws out of the rank task

- Subsystem: ranks.
- Files: `LuckPermsSilent.java`, `RankBadgeService.syncGroups`.
- Problem: soft-depend still produced `NoClassDefFoundError: net/luckperms/api/node/Node` at line 317.
- Evidence: REPRODUCED, RUNTIME VERIFIED, test server without LuckPerms.
- Risk: rank sync task aborts on any backend that boots before LuckPerms or without it. Production with LP is unaffected until LP fails to load.
- Expected: missing LP logs once and skips.
- Direction: keep LuckPerms types in a class that is not initialized unless `isPluginEnabled` is true, and catch linkage errors. Do not shade LuckPerms into the jar.
- Must not change: group names, the admin lock from item 4.

#### 6. `player-ranks.yml` save is a silent full overwrite

- Subsystem: ranks persistence.
- Files: `RankBadgeService.save` / `load` / `overlayPlayerFromDisk`.
- Problem: new YAML, `config.save`, `IOException` ignored. Not atomic. Not merged with disk. Transfer (`NetworkPlayerDataSync.writeKeys`) edits the same file per key.
- Evidence: CONFIRMED, STATICALLY INFERRED.
- Risk: lost rank updates across backends or a failed save that looks successful.
- Expected: same durability as `coins.yml`.
- Direction: load-merge-atomic save, log IOException. Do not switch to per-player files unless the owner asks; the checklist is explicit that coin file format stays.
- Must not change: YAML keys `players`, `extras`, `forced` if live files use them.

#### 7. Persistent forage hologram can multiply when the tracked entity is not loaded

- Subsystem: AetherionForaging isle guide.
- Files: `AetherionForaging/src/main/java/de/aetherion/foraging/npc/IsleGuideNpc.java` (`ensureHologram`, `tickHologram`, `hologram`). `setPersistent(true)` at the spawn site.
- Problem: missing UUID is treated as “spawn another”. Nearby removal does not see an unloaded display.
- Evidence: STRONGLY INDICATED, STATICALLY INFERRED. Unmerged `49cd2a7` describes this exact failure. Not counted in a long session.
- Risk: TextDisplay growth on the forage isle / hub guide.
- Expected: one hologram per guide.
- Direction: reuse any loaded tagged display; spawn non-persistent FX; do not spawn a second persistent display in the same tick the chunk is cold.
- Must not change: guide dialogue, grove rules, locked TalkUx.

#### 8. Warm dungeon world holds shutdown for 60 s

- Subsystem: AetherionDungeons warm pool.
- Files: `AetherionDungeons` enable path and `InstanceManager` / `DungeonWarmPool` (world `aedun_warm_void_*` in the boot log).
- Problem: with zero players the pool created a void world; shutdown logged a 60 s chunk-system halt for it.
- Evidence: REPRODUCED once, RUNTIME VERIFIED, combined server.
- Risk: dungeon backend stop/restart stalls. Confirm it still happens on a dungeons-only boot before changing pool size.
- Expected: disable does not sit a minute on an idle warm world.
- Direction: unload warm worlds on disable without a blocking wait, or don’t keep them loaded across a quiet halt. Do not remove the pool if production relies on it for join latency; measure first.
- Must not change: loot, party rules, snapshot format.

#### 9. Transfer directory failure is only a warning

- Subsystem: dungeon network transfer.
- Files: `AetherionDungeons/src/main/resources/config.yml`, `TransferSnapshotStore` constructor.
- Problem: absolute Crafty path; `mkdirs` failure is logged and the bad path is kept.
- Evidence: REPRODUCED warning. Production path may exist.
- Risk: silent failed transfers off the Crafty layout.
- Expected: operator-visible hard failure, or a working fallback directory.
- Direction: if the configured dir cannot be created, log severe and skip transfer rather than clearing inventories later. Do not change the production path string if that directory exists in Crafty.
- Must not change: snapshot version 4 field names.

### MONITOR

- Shared `coins.yml` last-writer-wins across JVMs. Designed 60 s loss on `kill -9`. Checklist already says so.
- Market and guild bank under two real clients. Code is ordered; it is not proven.
- `LuckPermsSilent.writeGroupMeta` clears weight and prefix nodes on every managed group at startup whenever LuckPerms is installed. Can fight hand-edited LP prefixes. **STATICALLY INFERRED.** Watch the next boot that has LP.
- Helios world created at every BossEngine enable (3 slots). Cost is real (world in the boot log) and may be intentional.
- Paper’s deprecated-event warnings: `EntityKnockbackEvent` (pets), `EntityRemoveEvent` (boss body). Low, reproduced as log lines only.
- Duplicate custom model data integers. Harmless if the pack uses one model for several items.
- StressBot README catch-timing contradiction.
- CI does not run unit tests.
- First-boot world set when every jar is on one server. Production split may avoid some of this. Do not “fix” world creation without knowing which backend loads which jar.

### DO NOT TOUCH

- `TalkUx` bubble, chips, quest card.
- `AshenKatanaListener`, `GravwellCleaverListener`, their ids `ashen_katana` and `gravwell_cleaver`, VFX, sounds, tuning.
- `BoosterSocketMenu`, `BoosterSockets`, 14-socket lore, booster item ids.
- Borderlands / crypt spirit vial ids and altar behavior, except a proven bug with a repro.
- `CoinService` format and CAS `take`.
- Boss director phase logic (Helios, Hollow Sun, Saint, World Eater, Unbroken, Ashen Sovereign, Ashen Sheath, Sandbox).
- Core as a non-game-loop library.
- Unmerged katana polish branches. Leaving them unmerged is the correct state until the owner asks.

### INVESTIGATE LATER

- Full EN/DE dialog key parity (`lang/de.yml` vs Java keys). Handoff says this was in progress at `1922f36`.
- Texture pack vs the 114 model-data values. Repo not readable here.
- Quest reward ids with no factory. Spot-checked only.
- Dungeon room connectivity and loot double-claim. Needs two players.
- Pet persistence quit/rejoin. Checklist marks it manual QA.
- `BetaWipe` path scope before anyone runs a wipe from a non-Crafty layout.
- Whether `clearLivingInventory` after a failed Velocity connect is still the desired failure mode.
- Guild island permission leaks. No player session.
- Spark profile at 20 and 50 players. This audit has no such numbers and will not invent them.

---

## Verification ledger

| Check | Result |
|-------|--------|
| `mvn -B -DskipTests package` (Maven 3.8.7, JDK 21.0.10) | BUILD SUCCESS, 37.690 s wall |
| `mvn -pl AetherionFarming,AetherionFishing -am test` | 11 + 9 tests, 0 failures |
| `mvn -pl AetherionFarming test` without reactor deps | FAILURE: `AetherionCore`/`AetherionItems` not in remote repos. Expected. |
| Paper 1.21.1 build 133, all module jars, WorldEdit+WorldGuard dist | Boot 1: Done 134.702 s, Pit error, LP rank task error, farm watchdog, transfer dir warning, warm world, Helios world |
| Coin probe stop/start | 4242 written, 4242 reloaded |
| Joined player, Velocity, FancyNpcs, LuckPerms, PAPI, texture pack, StressBot runner | Not run |
| `aetherion-texturepack` | No access |
