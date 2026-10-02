# AETHERION OPUS CONTEXT PACK

> Prepared for a future Claude Opus 5.5 session. **Information only.**  
> Do **not** treat this as a redesign brief. Preserve contracts unless Robbi explicitly overrides.  
> Live server / deploy / restart / commit are out of scope for the original gather task.

---

## Project / Architecture

Aetherion is a multi-plugin Paper network. For the tasks below, three repos matter:

| Repo | Path | Role |
|------|------|------|
| **BossEngine** | `C:\Users\Robbi\IdeaProjects\BossEngine` | Boss templates (YAML), spawn items/spawners, per-boss **Directors**, combat tick loop, death cinematics |
| **AetherionDungeons** | `C:\Users\Robbi\IdeaProjects\AetherionDungeons` | Dungeon instances, floors, Ashes F3 encounter, **victory/combat chests** (`DungeonLootFx`) |
| **AetherionItems** | `C:\Users\Robbi\IdeaProjects\AetherionItems` | Items, Dev Menu, Hollow Sun **armor** (stats/lore only — no set listener yet), Bloodstone arena TP command, dungeon gear consumers |

Soft coupling via `de.aetherion.core.api.AetherServices` (`registerBosses`, `registerDungeons`, etc.).

**Network note:** Dungeon hub/instances typically run on **MMO-D** (`mmo-d`). Hollow Sun / Bloodstone arena lives on **MMO-R**. Do not conflate the two worlds.

**BossEngine boot pattern** (`BossEngine` main):
1. `saveResourceIfMissing` for `bosses/*.yml`, `items.yml`, `spawners.yml`
2. `TemplateManager.reload()` — load every `plugins/BossEngine/bosses/<id>.yml`
3. `BossSpawnItemService.reload()` — admin spawn tools from `items.yml`
4. `BossManager.start()` — tick loop
5. `SpawnerManager` — TIMER / COMMAND / ITEM / STATIONARY spawners
6. Soft API: `AetherServices.registerBosses(...)`

**There is no Java “register boss id” API.** Drop YAML → template loads. **Director binding is hard-coded** on `BossInstance` (each director’s `isMine()` gates on template id).

---

## Relevant Files

### BossEngine — Hollow Sun / directors
- `BossEngine\src\main\java\de\aetherion\bossengine\instance\HollowSunDirector.java` — **sole combat/VFX director** (~4489 lines)
- `BossEngine\src\main\java\de\aetherion\bossengine\instance\BossInstance.java` — owns `HollowSunDirector`; routes bind/tick/death/transition
- `BossEngine\src\main\java\de\aetherion\bossengine\BossManager.java` — `spawn`, tick, `finishDragonKill` / loot on death end
- `BossEngine\src\main\java\de\aetherion\bossengine\TemplateManager.java` — YAML load
- `BossEngine\src\main\resources\bosses\hollow_sun.yml` — template (HP/phases/dialog; combat skills empty — director owns combat)
- `BossEngine\src\main\resources\items.yml` — `hollow_sun_anchor` (`SET_SPAWN`), `hollow_sun_core` (`SUMMON`)
- `BossEngine\src\main\resources\spawners.yml` — may omit hollow_sun; live MMO-R uses `hollow_sun_home` (STATIONARY, world `bloodstone`)

### BossEngine — Dungeon Aetherion (F3)
- `BossEngine\src\main\resources\bosses\dungeon_aetherion.yml` — Floor 3 boss template (`ENDER_DRAGON`, 115k HP)
- `BossEngine\src\main\resources\bosses\aetherion.yml` — **world raid** twin (separate id; do not merge)
- Shared dragon theatrics: `DragonDirector`, `CombatTheatrics` (cases include `"dungeon_aetherion"`), `TransitionSpectacles`

### AetherionDungeons
- `...\AetherionDungeons.java` — plugin entry, `/dungeon`, registers `DungeonAccessImpl`
- `...\instance\InstanceManager.java` — `enterPrototype`, `onBossKilled`, `tryLootChest`
- `...\instance\AshesEncounter.java` — Floor 3 Throne of Ashes
- `...\instance\DungeonLootFx.java` — **all dungeon chest place/open/VFX**
- `...\instance\DungeonWarmPool.java` — `ASHES_BASE = "aedun_f3_ashes"`
- `...\bridge\BossEngineBridge.java` — `AETHERION = "dungeon_aetherion"`, spawn/despawn helpers
- `...\bridge\ItemLootBridge.java` — item factory bridge for chest rolls
- `...\listener\DungeonListener.java` — chest click → loot; boss death routing

### AetherionItems (peripheral to Opus visual tasks)
- `...\world\BloodstoneArena.java` + `BloodstoneArenaCommand.java` — `/bloodstonearena`
- `...\item\CustomItem.java` — `createHollowSun*` armor (lore effects **not implemented** — no `HollowSunSetListener`)
- `_arena_ref\hollow_sun.yml`, `_arena_ref\hollow_sun_armor_concept.md`, `_arena_ref\items.yml`
- Locked systems rule: `IdeaProjects\.cursor\rules\locked-systems.mdc` (signature weapons, boosters, ranks, shutdown countdown)

---

## Hollow Sun

### Identity
- **Template id:** `hollow_sun`
- **Display:** `&6&lThe Hollow Sun`
- **Entity:** `WITHER_SKELETON` (body mostly hidden; **visuals are BlockDisplay rig**)
- **Arena:** Multiverse world `bloodstone` (MMO-R). Boss home snapshot: `0.5, 63.0, 115.5`. Player pad: `0.5, 68.0, 0.5` (`BloodstoneArena` / Essentials warp).
- **Entry:** `/boss spawn hollow_sun`, spawn items, or STATIONARY spawner `hollow_sun_home`

### Architecture pattern (quality bar)
| Concern | Pattern |
|---------|---------|
| Combat | **One fat Director** — not YAML skills for moves |
| Forms | `Form { MAIN, GIANT, COLLAPSE }` |
| Moves | `Move { SWIPE, LANCE, SLAM, STARCALL, CORONA, FLARE, PROMINENCE, WELL, FOLD, NOVA }` |
| Cinematics | `Cinematic { NONE, IGNITION, STARFALL, HOLD }` — phase transitions |
| Visuals | **`BlockDisplay` primary**; ArmorStand husks on death; **no ItemDisplay** in HollowSunDirector |
| FX list | `List<Display> fx` — “cleared as one list so nothing can leak” |
| Rig | `coreA`, `coreB`, `shell`, crown rays, mace — `spawnRig` / `syncRig` / `clearRig` |
| Arena | `measureArena()` → `skyRise` feeds death ascend height |
| Cleanup | `abort`, `clearCombatFx`, `clearRig`, `clearHusks`, `revertAllScorch`, `fizzleHazards` |

### Lifecycle (hooks on `HollowSunDirector`)
1. `onBind()` — clear, hide body, intro or spawn rig  
2. Intro: `INTRO_TICKS=62`, impact at `INTRO_IMPACT=34` — `beginIntro` / `tickIntro`  
3. Combat `tick()` — move AI + ambient + `syncRig`  
4. Phase gates from YAML (66% → Red Giant / `IGNITION_BASE=100`; 33% → Collapse / `STARFALL_BASE=240`) via `beginTransition` / `tickTransition` / `finishTransition`  
5. Death: HP 0 → `BossInstance` → `beginDeath()` → each tick `tickDeath()` until returns `true` → manager payout/loot  

### YAML role
`hollow_sun.yml` supplies attributes, phase **HP %**, transition duration/invuln, ON_PHASE/ON_DEATH dialog. **Combat skills arrays are empty** — director owns all fight choreography.

### Live balance note (as of gather)
Live / `_arena_ref` may show `max-health: 120000`, `attack-damage: 40` after a recent Robbi tune. Treat YAML on disk as source of truth for the next spawn.

### Armor (Items) — separate from boss VFX
Items exist (`hollow_sun_helmet`…`boots`, CMD 2341–2344). Lore describes Solar Sight / Caged Star / Swell / Starfall / set bonuses. **No `HollowSunSetListener` registered** — procs do not run. Do not “fix” armor unless tasked.

---

## Hollow Sun Death Sequence

**File:** `HollowSunDirector.java`  
**Methods:** `beginDeath()` → per-tick `tick()` → **`tickDeath()`** → end clears via `clearRig()` / `clearCombatFx()` / `clearHusks()` / `revertAllScorch()`  
**Constant:** `DEATH_TICKS = 186` (~9.3s at 20 TPS)

### Beat map (`deathTick` incremented at start of `tickDeath`)

| Ticks | What |
|------|------|
| **0** (`beginDeath`) | Clear combat FX; invuln; snapshot armor → `deathArmor[]`; focus; sounds `BLOCK_BEACON_DEACTIVATE`, `BLOCK_RESPAWN_ANCHOR_DEPLETE`, `ENTITY_WARDEN_HEARTBEAT`; shout |
| **1–39** | Body shake; `coreBoost` pulse; directional **`Particle.END_ROD`** leaks; charge sounds; @20 beacon power |
| **40** | Armor burst: `FLASH` + END_ROD burst; strip equipment; **`spawnHusks()`** (ArmorStands); crown → `Shard`s; **`dropMace()`**; **`applyDeathMaterials()`**; `coreOverride = chest` |
| **41–71** | Core hovers + bob; husks fall (`tickHusks`); heartbeats / amethyst chimes |
| **72–139** | **↑ RISING STAR (the moment to extend)** — see below |
| **140** | Climax flash: `FLASH` + 40 `FIREWORK`; twinkle / bell / toast; title + shout “returned to the sky” |
| **141–185** | Residual pulse (`rigScale` ~0.35 ± sine); END_ROD drip; @172 husk smoke + `clearHusks()` |
| **≥186** | `entity.remove()`; **`clearCombatFx()` + `clearRig()` + `clearHusks()` + `revertAllScorch()`**; return `true` |

### Exact rising / shrinking / disappearing object

**Not a single particle.** The “star” is the **rig core trio of `BlockDisplay`s**:

| Field | Death materials (`applyDeathMaterials`) |
|-------|----------------------------------------|
| `coreA` | `PEARLESCENT_FROGLIGHT` |
| `coreB` | `OCHRE_FROGLIGHT` |
| `shell` | `WHITE_STAINED_GLASS` + solar glow |

**Ascend + shrink** (`tickDeath` branch `deathTick` 72–139):

```text
u = easeInOut((deathTick - 72) / 68.0)
ascend = min(26.0, skyRise + 6.0)
coreOverride = chest + (0, ascend * u, 0)
rigScale = 1.0 - 0.65 * u     // → ~0.35 at end of rise
```

Trails: `Particle.END_ROD` every tick; `Particle.FIREWORK` every 2 ticks; rising `BLOCK_NOTE_BLOCK_CHIME` pitch.

**Scale application:** `syncRig(...)` multiplies core/shell transforms by `rigScale` (`Math.max(0.02, (base + …) * rigScale)`).

**Disappearance:**
1. Visual “gone” feeling peaks at **tick 140** flash (core already tiny).
2. **Hard remove:** `clearRig()` at **tick ≥ 186** discards `coreA` / `coreB` / `shell` / mace / crown via `discardRig`.

**Opus supernova hook (read-only guidance):** Extend **after** the rise shrink (≈ tick 140) and/or **before** `clearRig()` at 186 — without rewriting combat phases. Preserve `clearCombatFx` / `clearRig` contract so displays never leak.

---

## Dungeon Chest System

**Single implementation:** `AetherionDungeons\...\instance\DungeonLootFx.java`

| Concern | Detail |
|---------|--------|
| Block | Vanilla `Material.CHEST` |
| Tags (PDC) | `aetheriondungeons:loot_chest`, `loot_floor`, `loot_victory`, `loot_vestige` |
| Spinner | `ItemDisplay` entity tag `aether_loot_spin` (GUI transform, idle item cycle) |
| Label | `TextDisplay` tag `aether_loot_label` |
| Place API | `placeCombat`, `placeVictory`, `placeVictoryAt`, `placeGuaranteedVestige` → private `place(...)` |
| Open | `DungeonListener` → `InstanceManager.tryLootChest` → `DungeonLootFx.tryLoot` |
| Claim model | Per-player; busy while spin; “one item each” |
| Open VFX | ~22–27 tick spin + click sounds; `burst(...)` particles by rarity |
| Custom chest model | **None** — no dedicated chest CMD in Aetherion pack |
| Rewards | `ItemLootBridge` — cores / boosters / mats / F3 myth ~3% `random_aetherion_armor` |

**F3 victory chest placement:** `AshesEncounter.placeVictoryChest` → `DungeonLootFx.placeVictoryAt` near boss (`BOSS_X+4`, `BOSS_Y`, `BOSS_Z`, floor 3). BossEngine loot on `dungeon_aetherion` is intentionally empty — **chest is the reward**.

**Safe visual extension:** change/augment displays inside `DungeonLootFx.place` / spin / `burst` while keeping PDC keys + claim/busy logic.

---

## Existing Dungeon Architecture

| Floor | Name | Session id | Build | Boss template id |
|-------|------|------------|-------|------------------|
| 1 | Prison | `prototype_1` | Procedural / Lerfing | `dungeon_sentinel` |
| 2 | Frostbound | `prototype_endless_test` | Endless schem XL | `dungeon_frostbound` |
| 3 | Throne of Ashes | `prototype_ashes_3` | Preinstalled world `aedun_f3_ashes` | `dungeon_aetherion` |

**Entry:** `InstanceManager.enterPrototype(Player, bossOnly, floor)` — floor ≥ 3 → Ashes path.  
**Constraint:** typically **one active dungeon run**.  
**Boss bridge:** `BossEngineBridge.spawn(..., AETHERION)` / `despawnInWorld`.  
**Warm pool:** `DungeonWarmPool` clones `aedun_f3_ashes` base.

Despawn / recycle: `AshesEncounter.resetForReuse` → `BossEngineBridge.despawnInWorld`.

---

## Dungeon 3 / Floor 3 Current Concept

### Preserve as identity (even if visuals are redesigned later)
- **Name:** Floor 3 · **Throne of Ashes**
- **Boss:** **Aetherion** (`dungeon_aetherion`) — *“The set is not a souvenir.”*
- **Session id:** `prototype_ashes_3` (`AshesEncounter.FLOOR_ID`)
- **World folder:** `aedun_f3_ashes` (must be installed on dungeon server or entry fails)
- **Flow:** trash clear (~75%) → red glass gate drop → spawn Aetherion in open arena
- **Coords (from `AshesEncounter`):** spawn `(0,100,0)`; gate `x=194,y=96–102,z=-74..-68`; arena `(206–288)×(−111..−31)`; boss center ~`(247, 98, −71)`
- **Split:** world-raid `aetherion.yml` ≠ dungeon `dungeon_aetherion.yml` — do not merge balance/visuals casually
- **Reward fantasy:** victory chest; rare real Aetherion set (CMD **2311–2314**); Core III economy via Items

### Implemented vs unfinished

| Exists | Missing / conceptual |
|--------|----------------------|
| Full encounter Java + boss YAML (115k dragon, phases challenger→stormborn→tempest→singularity, dialog) | Map art not in git — external world install |
| `DragonDirector` / shared dragon theatrics | `model-engine-id: ""` — vanilla dragon look |
| Victory chest + myth roll | No custom chest furniture model |
| Menus / DevMenu `dungeon:floor3` | Procedural `prototype_3` leftover is **not** the live F3 path |

### Explicit note for Opus
**The future Floor 3 boss may completely redesign combat/visuals.** Preserve ids, encounter contract (clear → gate → arena → chest), and reward economy unless Robbi says otherwise. Treat current dragon as the **shipping baseline**, not a sacred VFX bar — **Hollow Sun is the VFX quality reference**.

---

## Existing Boss/VFX/Animation Utilities

### Hollow Sun (gold standard)
- `BlockDisplay` + `Transformation` + eased `place(...)`
- Phase material swaps (`applyRigMaterials` / `applyDeathMaterials`)
- Hazard records: Meteor, Arc, Lance, Shockwave, Well, Afterglow, Shard, Husk, Scorch
- Particle language: `END_ROD`, `FIREWORK`, `FLASH`, `FLAME`, `SONIC_BOOM`, `DUST`, portals, lava set, etc.
- Sound language: beacon / respawn-anchor / warden heartbeat / amethyst / note chime / firework / bell
- Telegraphs: ground rings, rising tells, crimson disc (Starfall), flare beams
- Cleanup discipline: one `fx` list + explicit clear on abort/death

### Dungeon / other directors (lower bar for F3 today)
- `DragonDirector` — dragon body theatrics
- `CombatTheatrics`, `TransitionSpectacles` — YAML-driven phase shapes (`FIRE_SPIRAL`, `LIGHTNING_STORM`, `BLACK_HOLE`)
- `SignatureDirector` — excludes dungeon_aetherion from signature weapon-style treatment
- Other directors exist (`AshenSheathDirector`, `PathwardenDirector`, `SparkyDirector`, …) — study only if needed

### Items-side display craft (reference only; LOCKED weapons nearby)
High-end **ItemDisplay** work lives in Items listeners (Echo Blade, Judgment, Bridged Axe, etc.). Hollow Sun itself uses **BlockDisplay**. Prefer matching the boss’s existing medium unless Robbi asks otherwise. **Never touch** Blossom Blade / Gravwell Cleaver combat systems (`AshenKatana*`, `GravwellCleaver*`).

---

## Existing Assets / Models / Resource Pack Integration

| Asset | Location / note |
|-------|-----------------|
| Aetherion armor CMD 2311–2314 | Items `CustomItem` + texturepack CIT |
| Hollow Sun armor CMD 2341–2344 | Items + concept doc; trims gold/SPIRE/RIB/SILENCE/FLOW until custom models |
| Dungeon chest block | Vanilla only |
| Hollow Sun boss | No ModelEngine; BlockDisplay froglight / glass / concrete / magma palette |
| Dungeon Aetherion | `model-engine-id: ""` |
| Texture pack extract | `C:\Users\Robbi\IdeaProjects\Aetherion_texturepack_extract\` (CIT / model overrides) |
| Bloodstone world ref | `AetherionItems\_arena_ref\` scans/schems — **not** Throne of Ashes |

---

## Technical Constraints

1. **Director hard-coding:** new showcase boss VFX usually means a dedicated Director class wired in `BossInstance`, not only YAML.
2. **Display leaks:** any new displays must join `fx` (or equivalent) and clear on abort/death.
3. **Dungeon map external:** F3 world must remain named `aedun_f3_ashes` unless install pipeline changes.
4. **One-run dungeon concurrency** assumption in `InstanceManager`.
5. **MMO-R vs MMO-D:** Hollow Sun Bloodstone ≠ dungeon server worlds.
6. **Chest claim contract:** PDC keys + per-player claim + busy spin — keep unless redesigning loot UX.
7. **Double loot:** do not fill BossEngine loot drops for `dungeon_aetherion` without removing chest rewards.
8. **LOCKED systems** (Items): signature weapons, boosters stack/lore, ranks, custom anvil GUI, shutdown countdown — see `locked-systems.mdc`.
9. **Armor set procs** for Hollow Sun are unimplemented — out of scope unless tasked.

---

## Safe Extension Points

| Goal | Where |
|------|--------|
| Hollow Sun death **Supernova** (visual only) | `HollowSunDirector.tickDeath` around ticks **140–186**; optionally keep rise 72–139; always still call `clearRig()` |
| New death materials / particles | `applyDeathMaterials`, particle/sound calls inside `tickDeath` |
| Dungeon chest look | `DungeonLootFx.place` / spinner ItemDisplay / `burst` + optional resource-pack CMD item |
| F3 boss visual redesign | Prefer new/extended Director **or** YAML `model-engine-id` + `DragonDirector`/theatrics; keep id `dungeon_aetherion` |
| F3 arena entrance FX | `AshesEncounter.openBossDoor` / `spawnAetherion` hooks |
| Admin TP to Hollow Sun arena | Already: `/bloodstonearena` (Items) |

---

## Important APIs / Classes

| Class | Why |
|-------|-----|
| `HollowSunDirector` | Hollow Sun everything |
| `BossInstance` / `BossManager` | Spawn, tick, death payout |
| `TemplateManager` / `YamlTemplateLoader` | Boss YAML |
| `BossSpawnItemService` | Anchor/core items |
| `AshesEncounter` | F3 flow + coords |
| `BossEngineBridge` | Dungeon ↔ BossEngine |
| `DungeonLootFx` / `ItemLootBridge` | Chests + rewards |
| `InstanceManager` / `DungeonSession` | Run lifecycle |
| `DungeonAccessImpl` / `AetherServices` | Cross-plugin start from Items DevMenu |
| `BloodstoneArena` | Locked pads for Hollow Sun |

---

## What Can Be Freely Changed

*(When Opus is later tasked — not now)*

- Purely visual extension of Hollow Sun **death climax** (supernova) if cleanup stays intact
- `DungeonLootFx` visuals (block/display/particles/sounds) without breaking claim keys
- F3 boss **presentation** (ModelEngine, director FX, telegraphs) while keeping `dungeon_aetherion` id and Ashes encounter flow
- Resource-pack models/CMD for new chest or boss cosmetics
- YAML dialog / transition shape cosmetics on `dungeon_aetherion.yml`

---

## What Must NOT Be Changed

- Locked Items systems (signature weapons Blossom Blade / Gravwell Cleaver, boosters, ranks, anvil GUI, shutdown countdown) unless Robbi explicitly requests
- Accidental merge of world-raid `aetherion` with dungeon `dungeon_aetherion`
- Hollow Sun **combat feel** / phase timings / move tuning unless Robbi asks (death visual extension is the intended carve-out for the next task)
- Bloodstone / Hollow Sun vs Throne of Ashes world identity mix-up
- Chest PDC claim protocol without a deliberate loot redesign
- Deploy/restart/commit as part of “context only” work

---

## Recommended Entry Points for Opus

1. **Hollow Sun Supernova (death visual):**  
   Open `HollowSunDirector.tickDeath` — focus **ticks 72–140 (rise/shrink)** and **140–186 (climax → `clearRig`)**. Instrument new displays into `fx`; never skip end clears.

2. **Dungeon chest visual upgrade:**  
   Open `DungeonLootFx.place` + idle spin runnable + `burst`. Keep `tryLoot` claim/busy. Optional pack CMD for a furniture item used as `ItemDisplay`.

3. **Floor 3 boss visual redesign:**  
   Start from `AshesEncounter` + `dungeon_aetherion.yml` + `DragonDirector`/`CombatTheatrics`. If Hollow-Sun-tier spectacle is required, add a dedicated Director patterned on `HollowSunDirector` (BlockDisplay rig, phase cinematics, strict cleanup), **new file**, wire in `BossInstance` behind `isMine()` for `dungeon_aetherion` only. Preserve Ashes gate/arena/chest contract.

4. **Quality reference checklist (copy Hollow Sun habits):**  
   director-owned combat or cinematic beats · telegraph language · `fx` list · material phases · sound motifs · abort/death clears · no leaked displays.

---

## Quick Path Index (absolute)

```
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\instance\HollowSunDirector.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\hollow_sun.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\dungeon_aetherion.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\aetherion.yml
C:\Users\Robbi\IdeaProjects\AetherionDungeons\src\main\java\de\aetherion\dungeons\instance\AshesEncounter.java
C:\Users\Robbi\IdeaProjects\AetherionDungeons\src\main\java\de\aetherion\dungeons\instance\DungeonLootFx.java
C:\Users\Robbi\IdeaProjects\AetherionDungeons\src\main\java\de\aetherion\dungeons\bridge\BossEngineBridge.java
C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\java\de\aetherion\items\world\BloodstoneArena.java
C:\Users\Robbi\IdeaProjects\AetherionItems\_arena_ref\hollow_sun_armor_concept.md
C:\Users\Robbi\IdeaProjects\.cursor\rules\locked-systems.mdc
```

---

*End of context pack. No gameplay was modified to produce this document.*
