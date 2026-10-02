# AETHERION OPUS CONTEXT PACK
## Floor 3 Mini-Bosses · Light Polish (Pathwarden / Skuldugery / Hollow Lurker / McNugget) · Hollow Sun Armor Effects

> Prepared for a future Claude Opus session. **Information only.**  
> Do **not** implement, deploy, balance numbers, or restart servers from this pack alone.  
> Robbi will hand you the creative brief separately. This document is the technical runway so you can cook immediately.

---

## Mission Snapshot (what Robbi will ask)

Three jobs in one creative pass. Priority: **design / presentation quality**, not balance tuning.

| # | Job | Quality bar | Scope |
|---|-----|-------------|-------|
| 1 | **Two new mini-bosses** themed for Floor 3 · Throne of Ashes | Below Hollow Sun / Sovereign of Ash (those are endboss). Readable, memorable, themed trash-gate elites | BossEngine YAML + preferably small Directors; wire into Floor 3 flow if needed |
| 2 | **Light polish** on Pathwarden, Skuldugery, Hollow Lurker, McNugget | Lift presentation toward current Aetherion feel (telegraphs, VFX, sound, death beats) — not full redesigns | Existing YAML + PathwardenDirector / CombatTheatrics / skills |
| 3 | **Hollow Sun armor effects** | Lore + concept already exist; effects currently **do not run** (no listener) | New `HollowSunSetListener` (or equivalent) in AetherionItems, register like `AetherionSetListener` |

**Out of scope unless Robbi says otherwise:** balance retunes, deploy/restart, loot economy redesign, Hub server, signature weapons, boosters, ranks, Hollow Sun / Sovereign combat redesign.

---

## Project Map

| Repo | Path | Role for this task |
|------|------|--------------------|
| **BossEngine** | `C:\Users\Robbi\IdeaProjects\BossEngine` | Boss YAML, Directors, skills, spawn items, spawners |
| **AetherionDungeons** | `C:\Users\Robbi\IdeaProjects\AetherionDungeons` | Floor 3 Ashes encounter, trash clear → gate → endboss, chests |
| **AetherionItems** | `C:\Users\Robbi\IdeaProjects\AetherionItems` | Hollow Sun armor items + set listeners |
| **AetherionCore** | `C:\Users\Robbi\IdeaProjects\AetherionCore` | `ScriptedHits`, `AetherServices`, shared keys |

Soft coupling via `de.aetherion.core.api.AetherServices` (`registerBosses`, `registerDungeons`, …).

**Network:** Floor 3 runs on **MMO-D**. Hollow Sun / Bloodstone is **MMO-R**. Do not conflate.

---

## Floor 3 · Throne of Ashes (where mini-bosses live)

### Identity
- **Session id:** `prototype_ashes_3` (`AshesEncounter.FLOOR_ID`)
- **World base:** `aedun_f3_ashes` (`DungeonWarmPool.ASHES_BASE = aedun_f3_ashes`)
- **Endboss template:** `dungeon_aetherion` (AshenSovereignDirector) — **do not redesign**
- **Flow today:** trash clear (~75%) → red glass gate drops → spawn Aetherion at locked Throne core

### Locked Throne core (Robbi placed Anchor)
```
BOSS_X = 266
BOSS_Y = 96
BOSS_Z = -71
→ spawn / chest at 266.5, 96, -70.5 (aedun_f3_ashes)
```
Live spawner on MMO-D: `dungeon_aetherion_home` STATIONARY at those coords.

### Arena / gate coords (`AshesEncounter`)
| Thing | Coords |
|-------|--------|
| Player entrance | `(0, 100, 0)` |
| Gate (red glass) | `x=194`, `y=96–102`, `z=-74..-68` |
| Arena AABB | `x 206–288`, `z -111..-31` |
| Trash tag | `ashes_trash` (WitherSkeleton / PiglinBrute) |

### Key files
```
AetherionDungeons\...\instance\AshesEncounter.java
AetherionDungeons\...\bridge\BossEngineBridge.java
AetherionDungeons\...\instance\DungeonWarmPool.java
AetherionDungeons\...\instance\DungeonLootFx.java
AetherionDungeons\...\instance\DungeonChestProps.java
```

### BossEngineBridge dungeon ids
```java
SENTINEL = "dungeon_sentinel"
FROSTBOUND = "dungeon_frostbound"
AETHERION = "dungeon_aetherion"
DUNGEON_BOSSES = { sentinel, frostbound, aetherion }
```
**New Floor 3 mini-boss template ids** should be added here if Ashes should spawn them via the bridge (plus YAML + Director wiring).

### How trash works today
- Prep spots from warm-pool / `structures/ashes/f3-prep.yml`
- `staggerSpawn` → Wither Skeletons + Piglin Brutes tagged `ashes_trash`
- Clear ratio `0.75` of planned mobs → `openBossDoor` → `spawnAetherion`
- **No mid-boss slots yet.** Mini-bosses likely plug in as: gate elites before endboss, or staged spawns during clear / after gate. Creative choice — preserve clear → gate → endboss contract unless Robbi asks to change it.

---

## BossEngine Architecture (how to add / polish bosses)

### Boot
1. `saveResourceIfMissing` for `bosses/*.yml`, `items.yml`, `spawners.yml`
2. `TemplateManager.reload()` — loads every `plugins/BossEngine/bosses/<id>.yml`
3. `BossSpawnItemService.reload()` — spawn tools from `items.yml` (+ `ensureItem` for missing entries)
4. `BossManager.start()` — tick loop
5. Soft API: `AetherServices.registerBosses(...)`

**There is no Java “register boss id” API.** Drop YAML → template loads. **Director binding is hard-coded** on `BossInstance` (`isMine()` per director).

### Two presentation layers
| Layer | Used by | Notes |
|-------|---------|-------|
| **YAML skills** (`ARROW_SHOT`, `EGG_SHOT`, `RING_BURST`, …) + `CombatTheatrics` / phase transitions | Skuldugery, Hollow Lurker, McNugget (mostly) | Fast polish path: better telegraphs, particles, sounds, dialog, death skills |
| **Dedicated Director** | Pathwarden, Hollow Sun, Ashen Sovereign, Frostbound, Sparky, … | Wire in `BossInstance` (bind / tick / death / abort / transition). Strict `fx` cleanup |

### Quality references (do not copy combat)
| Boss | File | Role |
|------|------|------|
| Hollow Sun | `HollowSunDirector.java` (~4.8k lines) | Gold-standard VFX language, telegraphs, death supernova |
| Sovereign of Ash | `AshenSovereignDirector.java` (~4k lines) | Floor 3 endboss — **leave alone** |
| Pathwarden | `PathwardenDirector.java` (~907 lines) | Mid-tier Director example (blades, spin, beam, death) |

### Spawn items pattern
```
/boss give <id>_anchor   # SET_SPAWN
/boss give <id>_core     # SUMMON
```
Add to `src/main/resources/items.yml` **and** `BossSpawnItemService.ensureItem(...)` so live `plugins/BossEngine/items.yml` gains entries on reload.

Examples already live: `hollow_sun_anchor/core`, `dungeon_aetherion_anchor/core`, `pathwarden_*`, `skuldugery_*`, `hollow_lurker_*`, `mcnugget_*`.

### Cleanup discipline (mandatory)
Any displays / particles / temp entities must join an `fx` (or equivalent) list and clear on abort / death / despawn. Interrupted abilities must not leak.

---

## Existing Bosses to Polish (light touch)

### Pathwarden (`pathwarden`)
| | |
|--|--|
| YAML | `bosses/pathwarden.yml` |
| Entity | `GIANT`, 52k HP, attack 520 |
| Director | `PathwardenDirector` — **owns combat** (skills empty in YAML) |
| Fantasy | Path gate; blades, judgment line, spin, beam, death |
| Spawn | `/boss give pathwarden_anchor` / `pathwarden_core` |
| Polish angle | Telegraph clarity, VFX density, death beat, sound motifs — keep identity |

### Skuldugery (`skuldugery`)
| | |
|--|--|
| YAML | `bosses/skuldugery.yml` |
| Entity | `SKELETON` scale 1.75, 3k HP |
| Combat | YAML: `ARROW_SHOT` → phase `inferno` (50%) with sphere transition + lava |
| Fantasy | Dodge-heavy T1 rite / tutorial |
| Polish angle | Arrow tells, inferno transition spectacle, death sting — YAML/`CombatTheatrics` first |

### Hollow Lurker (`hollow_lurker`)
| | |
|--|--|
| YAML | `bosses/hollow_lurker.yml` |
| Entity | `ZOMBIE` scale 1.4, 3k HP |
| Combat | YAML: `RING_BURST` soul rings → frenzy 50% |
| Special | Extra handling in `BossInstance` (`isHollowLurker()` — several branches) |
| Polish angle | Soul-ring telegraphs, frenzy pop, shell-break fantasy |

### McNugget (`mcnugget`)
| | |
|--|--|
| YAML | `bosses/mcnugget.yml` |
| Entity | `CHICKEN` scale 2.8, 3k HP |
| Combat | YAML: `EGG_SHOT` → panic 50% (chicken rain dialog) |
| Polish angle | Comedy-readable telegraphs, panic climax, death gag — keep charm |

### Skill registry (for YAML polish)
`BossEngine\...\skill\SkillRegistry.java` — includes `ARROW_SHOT`, `EGG_SHOT`, `RING_BURST`, plus many others. `CombatTheatrics` for shared slam/ring/meteor FX helpers.

---

## Floor 3 Mini-Bosses (new) — technical guidance

Creative freedom on **what** they are (must theme to Throne of Ashes / ash / ember / chained sky / throne prison). Technical expectations:

1. New `bosses/<id>.yml` (unique ids — do not reuse `dungeon_aetherion` / world-raid `aetherion`)
2. Prefer **small dedicated Directors** if they need readable telegraphs; otherwise strong YAML + theatrics is OK for mini scale
3. Wire Directors in `BossInstance` behind `isMine()` — copy Pathwarden / Sparky scale, not Hollow Sun scale
4. Spawn items: `<id>_anchor` + `<id>_core`
5. Optional: add to `BossEngineBridge` + call from `AshesEncounter` at a clear beat (e.g. mid-clear elites, or post-gate pre-Aetherion). **Do not replace** Sovereign spawn
6. HP / damage: mini-boss — clearly below Pathwarden (52k) and far below Sovereign (115k). Exact numbers are Robbi’s call later; ship sensible placeholders
7. Cleanup + no permanent residue

**Theme anchors already in Floor 3:** ash, ember lanes, throne chains, soul wards, purple/royal dust, “sky that was burned out.” Mini-bosses should feel like **Throne servants / jailers / ash-spawn**, not second endbosses.

---

## Hollow Sun Armor Effects (implement)

### Status
| Piece | Item exists | Lore / set text | Effects code |
|-------|-------------|-----------------|--------------|
| All 4 pieces | Yes — `CustomItem.createHollowSun*` | Yes | **No** — no `HollowSunSetListener` |

### Item ids / CMD
| Id | Display | CMD |
|----|---------|-----|
| `hollow_sun_helmet` | `§6✦✦✦ §eCorona Visor` | 2341 |
| `hollow_sun_chestplate` | `§6✦✦✦ §6Hollow Heart Cuirass` | 2342 |
| `hollow_sun_leggings` | `§6✦✦✦ §cRed Giant Greaves` | 2343 |
| `hollow_sun_boots` | `§6✦✦✦ §5Starfall Sabatons` | 2344 |

Also: `BossGearBalance`, `ArmorAppearance` (gold trims SPIRE/RIB/SILENCE/FLOW), `ItemProfile`, Dev Menu Special Sets page (`hollow_sun` give).

### Authoritative design (implement this)
Full detail: `AetherionItems\_arena_ref\hollow_sun_armor_concept.md`

**Per piece**
- **Visor — Solar Sight:** crits Sunmark 4s; marked take +6% from you; gold ground ring tell
- **Cuirass — Caged Star:** Heat 0–100 from dealing damage; at 100 next hit Corona burst (60% of hit via `ScriptedHits`); idle decay; action bar only above 50%
- **Greaves — Swell:** below 50% HP → +10% dmg / +8% def + brief Flare; optional subtle scale
- **Boots — Starfall:** double-sneak mid-air (≥3 blocks up) → plunge + crimson disc telegraph + shockwave; **18s CD**

**Set**
- **2pc Main Sequence:** every 5th melee hit on same target → Starcall lance (35% of hit)
- **3pc Red Giant:** big hit or ≤35% HP → 8s Red Giant (flare beams +15% dmg), **45s CD**
- **4pc Collapse:** once / **12m** fatal → black star 1.5s (pull) → Nova → return at 35% HP + Res II; chat line `§5✦ Last Light…`

### Pattern to copy
```
AetherionItems\...\listener\AetherionSetListener.java
```
Registered in `AetherionItems.java` (~line 278):
```java
registerEvents(new AetherionSetListener(this, itemManager), this);
```
Use `ItemManager` piece-id checks (`hollow_sun_*`), `ScriptedHits.run(...)` for scripted damage, cleanup on quit/disable. Separate cooldown keys from Aetherion set’s death-save.

### Stats already stamped
Match `createHollowSunArmor` / concept table — **do not retune** unless asked. Effects only.

---

## LOCKED Systems (do not touch)

From `IdeaProjects\.cursor\rules\locked-systems.mdc`:

- **Signature weapons:** Blossom Blade (`ashen_katana`) / Gravwell Cleaver — combat/VFX/feel
- **Boosters:** lore/tooltip + stackable 64
- **Borderlands vials:** stackable; rite/altar unless asked
- **Custom Anvil GUI:** BoosterSocketMenu / 14 sockets
- **Ranks:** Admin = Robb UUID only; Dev Menu only assignment; `player-ranks.yml`; special dyes
- **Shutdown countdown:** 10→8→6→4→2 every 2s

Also leave alone unless asked:
- AshenSovereignDirector combat / phases (death was already polished)
- HollowSunDirector combat / death supernova
- Hub inventory transfer / network sync

---

## Recommended Opus Entry Order

1. Read this pack + `hollow_sun_armor_concept.md` + skim `AetherionSetListener`
2. Skim `AshesEncounter` + `BossEngineBridge` for Floor 3 hook points
3. Skim `PathwardenDirector` + the three YAML bosses for polish targets
4. Implement **Hollow Sun set listener** (clearest contract)
5. Polish the four existing bosses (presentation)
6. Design + implement **two Floor 3 mini-bosses** + optional Ashes wiring + spawn items
7. Local `mvn -DskipTests package` on touched repos — **Composer/Robbi handle deploy**

---

## Absolute Path Index

```
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\instance\BossInstance.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\instance\PathwardenDirector.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\instance\HollowSunDirector.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\instance\AshenSovereignDirector.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\pathwarden.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\skuldugery.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\hollow_lurker.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\mcnugget.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\bosses\dungeon_aetherion.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\resources\items.yml
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\item\BossSpawnItemService.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\skill\SkillRegistry.java
C:\Users\Robbi\IdeaProjects\BossEngine\src\main\java\de\aetherion\bossengine\fx\CombatTheatrics.java
C:\Users\Robbi\IdeaProjects\AetherionDungeons\src\main\java\de\aetherion\dungeons\instance\AshesEncounter.java
C:\Users\Robbi\IdeaProjects\AetherionDungeons\src\main\java\de\aetherion\dungeons\bridge\BossEngineBridge.java
C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\java\de\aetherion\items\item\CustomItem.java
C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\java\de\aetherion\items\listener\AetherionSetListener.java
C:\Users\Robbi\IdeaProjects\AetherionItems\src\main\java\de\aetherion\items\AetherionItems.java
C:\Users\Robbi\IdeaProjects\AetherionItems\_arena_ref\hollow_sun_armor_concept.md
C:\Users\Robbi\IdeaProjects\AetherionCore\src\main\java\de\aetherion\core\combat\ScriptedHits.java
C:\Users\Robbi\IdeaProjects\.cursor\rules\locked-systems.mdc
```

---

## Explicit Non-Goals for the Opus Build Pass

- No deploy / restart / commit
- No balance pass on Sovereign / Hollow Sun bosses
- No Hub↔MMO inventory sync work
- No changing Throne Anchor coords / Sovereign spawn core unless Robbi asks
- No “minimal stub” mini-bosses — they should feel designed, just not endboss-scale

---

*End of context pack. No gameplay was modified to produce this document.*
