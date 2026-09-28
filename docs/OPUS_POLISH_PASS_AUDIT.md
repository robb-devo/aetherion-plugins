# Aetherion Polish Pass — Audit (Textures → Pets/Habitats → Early Rituals)

**Role of this doc:** Scout / architect / prep for Claude Opus.  
**Not an implementation pass.** Do not treat this as permission to rewrite systems.

**Quality principle:** Showcase encounters (Seraphine, Hollow Sun, World Eater) are **quality references**, not templates to copy. Prefer *small, deliberate, polished* over *huge, complicated, expensive*.

**Date context:** Scouted from monorepo `robb-devo/aetherion-plugins` working tree (main + large local WIP). Opus should work from GitHub `main` + this doc set unless Robbi pins a branch.

---

## A. CURRENT STATE

### A1. Textures / progression equipment

**Dual pack reality (critical):**

| Pack | Path | Git | Role |
|------|------|-----|------|
| Plugin pack | `AetherionItems/resourcepack/` | **Tracked** (~36 PNGs) | What Opus can ship via repo |
| Client extract | `Aetherion_texturepack_extract/` | **Gitignored** | Fuller live client pack (CIT Resewn armor, many 256× icons) |
| Zip | `Aetherion_texturepack.zip` | **Gitignored** | Manual client delivery |

Both packs: `pack_format: 34` (1.21.x).

**Registration pipeline (solid — do not redesign):**
1. `CustomItem` / `ProgressionItems` factories build stacks
2. PDC id via `ItemKeys`
3. Stats via `ItemManager.applyItemData`
4. Look via hard-coded `meta.setCustomModelData(N)` on vanilla base materials
5. Armor polish via `ArmorAppearance` (trims/dyes; `simple_*` intentionally untrimmed)
6. Lore chrome via `ItemPresentation` / `ItemFlavor`
7. Capability map via `ItemProfile`
8. Client: CMD overrides on `models/item/<base>.json` → custom model → texture; worn armor usually needs CIT Resewn

**Two early ladders (do not confuse):**

| Ladder | Owner | Player sees when | Visual status |
|--------|-------|------------------|---------------|
| Starter / skill sets | `CustomItem` | Hour-1 craft/kit + Mining/Combat T1–T5 | Partial: simple tools 256² in plugin pack; beginner_pickaxe **1254² outlier**; simple armor 32²; combat/mining T1 icons 64² in plugin pack |
| Compressed/Compacted | `ProgressionItems` CMD **2701–2716** (+ voided **2799**) | Mid-early economy line | Almost no pack assets — vanilla-looking except stub `compacted_diamond_sword` |

**Hour-1 starter CMD map:**

| ID | Material | CMD |
|----|----------|-----|
| `beginner_pickaxe` | DIAMOND_PICKAXE | 1001 |
| `simple_pickaxe` | WOODEN_PICKAXE | 1002 |
| `simple_axe` | WOODEN_AXE | 1003 |
| `simple_sword` | WOODEN_SWORD | 1004 |
| `simple_hoe` | WOODEN_HOE | 1005 |
| `simple_helmet`…`boots` | LEATHER_* | 2001–2004 |

Combat T1–T5 swords / Mining T1–T5 picks already have stronger art in the **ignored extract** (often 256×). Plugin pack only has lean T1 stubs at 64× for combat/mining icons.

**Recent early-game work** (`_early_game_patches`, `_wt_early_game`) polished harbour / kit ceremony / NPCs — **not item textures**.

### A2. Pets + habitats

**Ownership:** Catchable pets = **Aethermobs**. Painted biotopes = **AetherionItems** (`pet-habitats.yml` runtime). Ambient wildlife / forage TAB habitats are **parallel systems** — do not conflate.

| Layer | Plugin | Player-facing? |
|-------|--------|----------------|
| Wild pets | Aethermobs (`PetFactory` → `PetRegistry`, ~74 code-registered pets) | Yes — catchable ItemDisplay fauna |
| Painted disks | Items `PetHabitatZoneService` → Aethermobs `PetHabitatZones` | Staff markers only today |
| Block auto-detect | `PetHabitat.presentAt` (biomes ignored on purpose) | Indirect via spawn mix |
| Forage habitats | AetherionForaging | TAB / weather on Forage Isle |
| Ambient wildlife | Items `WildlifeLooks` + animal/mob zones | Vanilla entities, not pets |

**Spawn:** `PetSpawnManager` ~1.2s tick, caps (120 global / 4 chunk), habitat-weighted, seeks hard-matching column within 36 blocks, `PetWalkSurface` canopy-safe.

**Organic verdict:** Spawn + terrain already feel living. **Discovery UX is the gap** — no player enter toast (unlike `CryptDiscoverListener` / Hub discover). Staff see ArmorStand markers + rare `HAPPY_VILLAGER`.

**Runtime data:** `plugins/AetherionItems/pet-habitats.yml` is **not in repo**. Live paint coverage must be audited on the server before changing auto-detect.

### A3. Early boss summoning / rituals

**What “early” means here:**

| Tier | Bosses | Arrival | Gate |
|------|--------|---------|------|
| T1 Borderlands | `hollow_lurker`, `mcnugget`, `bridge_troll`, `skuldugery` | Spirit vial → powder altar → 10s rite → spawn | `border_rites` / Vex loop |
| T2 Colosseum | `pathwarden` | Crypt spirit pad / Proctor demo → 10s → spawn | `ColosseumGateService` |
| Not early | Cinder Herald, Ashen*, Seraphine, Hollow Sun, World Eater | Dungeon / directors / sites | Mid–late |

**Borderlands rite** (`BorderlandsRiteService`): already staged (quiet seal → tighten → column → soft arrival). Comments explicitly reject particle dumps. Ambient magenta→cyan landmark beam while idle. Vials **stack 64 — LOCKED**.

**Colosseum summon** (`ColosseumEscortService`): same 10s bar, but FX are explosion / dense soul-fire carpets / tall spark columns — clearest “particles → boss pops” path.

**After spawn:** T1 YAML `ON_SPAWN` is SOUND+DIALOG only. Phase polish (`TierPhaseShow`, `CombatTheatrics`) and deaths (`SignatureDirector`) are already ahead of entrance polish. Continuous `PARTICLE_AURA` in T1 YAMLs is noisy (Skuldugery especially).

**Showcase refs (read-only):** Seraphine `instance/saint/*` act machine; Ashen Sovereign multi-beat intro; Hollow Sun / World Eater largely in worktrees — learn *geometry/timing/sound first, particles last*.

---

## B. KEEP / DO NOT TOUCH

### Locked systems (workspace rules)
- Boosters (lore-in-tooltip, stack 64, IDs/models)
- Borderlands vials stackable + altar/rite **contracts** (coords, powder, vial→boss map, drop chances) — presentation FX only unless Robbi expands scope
- Custom Anvil / `BoosterSocketMenu` / 14 sockets
- Rank / Admin / TAB dyes / `player-ranks.yml`
- Signature weapons: Blossom Blade (`ashen_katana`) + Gravwell Cleaver — abilities/VFX/tuning
- Shutdown countdown

### Architecture leave-alone
- `QuestManager`, NPC tooling, harbour early-game quest graph / `QuestStoryGate`
- `BossEngine` core lifecycle / showcase directors (Seraphine, Sovereign, Hollow Sun, World Eater)
- `CustomItem` / `ItemManager` / `ItemProfile` registration model (texture-only preferred)
- Existing CMD numbers and item PDC ids
- `PetHabitat` scan thresholds, spawn caps/weights, `PetWalkSurface`, catch timing, farm/village/shore staying **auto**
- Combat/Mining T5 polished extract icons (don’t regress)
- Proctor spill narrative beat; Borderlands 10s countdown **structure**
- `TierPhaseShow` / themed `CombatTheatrics` / `SignatureDirector` deaths for T1 four
- Pathwarden **combat** kit (entrance FX only)

### Explicit non-goals
- Recreating every texture “more detailed”
- Cloning Seraphine intro length into Borderlands
- New pet species / habitat kinds for polish theater
- Rewriting YAML combat balance / loot tables for ritual polish

---

## C. POLISH CANDIDATES

### STEP 1 — Textures / progression equipment

| ID | Change | Priority | Essential? |
|----|--------|----------|------------|
| T1.1 | Fix `beginner_pickaxe.png` 1254² → power-of-two (prefer 64 or 256 NN) | P0 | **Essential** |
| T1.2 | Unify `simple_*` tool+armor icons to one deliberate resolution (recommend **64×64** inventory readability; tools may stay 256 if already authored well) | P0 | **Essential** |
| T1.3 | Decide **canonical pack**: ship polish into tracked `AetherionItems/resourcepack`; document that Robbi syncs gitignored extract / live client separately | P0 | **Essential** (process) |
| T1.4 | Author ProgressionItems CMD **2701–2716** models+textures+base overrides (biggest empty shelf) | P1 | Recommended |
| T1.5 | Raise combat/mining **armor** icons toward tool quality (plugin pack currently 64; extract often still 16 for armor) | P2 | Optional |
| T1.6 | Wire missing base JSON (e.g. `golden_sword.json` for copper/midas line) without CMD reassignment | P1 | Recommended with T1.4 |

**Per-change detail (T1.1–T1.2):**
- **Files:** `AetherionItems/resourcepack/assets/minecraft/textures/item/{beginner_pickaxe,simple_*}.png` (+ matching `models/item/*.json` if needed)
- **Current:** Mixed 32/64/256/1254; inventory silhouette inconsistent
- **Desired:** Cohesive early kit that reads at Minecraft UI scale; tiers look intentionally different from vanilla wood/leather
- **Reason:** Hour-1 identity; first impression of Aetherion craft
- **Tech:** Keep CMD 1001–1005 / 2001–2004; pack_format 34; no component/`item_model` migration
- **Deps:** Client must reload pack; CIT worn armor remains extract-side unless Robbi un-ignores CIT paths
- **Regression:** Low if ids/CMD/materials unchanged; medium if wrong material override file
- **Risk:** Over-detailing 64→256 noise — prefer readable silhouettes

**Per-change detail (T1.4):**
- **Files:** `ProgressionItems.java` (CMD constants — **read only**); new textures/models under resourcepack; extend `models/item/<material>.json` overrides
- **Current:** Almost all progression line looks vanilla
- **Desired:** Each compressed/compacted piece has a distinct readable icon matching rarity color language
- **Reason:** Economy progression should *look* like progression
- **Tech:** CMD 2701–2716 already assigned — add art only; watch per-material CMD collisions
- **Deps:** Recipes/shops already reference ids
- **Regression:** Medium (wrong override steals another item’s look on same material)
- **Essential?** Recommended — largest visual gap after starter kit

### STEP 2 — Pets / habitats

| ID | Change | Priority | Essential? |
|----|--------|----------|------------|
| T2.1 | First-enter habitat discovery feedback (title/actionbar), patterned on `CryptDiscoverListener` | P0 | **Essential** |
| T2.2 | Optional soft one-shot enter FX per biotope (not per-tick) | P1 | Recommended |
| T2.3 | Audit live `pet-habitats.yml` coverage on Origin before any auto-detect tuning | P0 | **Essential** (ops/preflight) |
| T2.4 | Document / lightly align Eldervale forage TAB vs pet `ELDERVALE` paint | P2 | Optional |
| T2.5 | Density review only if playtest reports crowded-but-uncatchable biotopes | P2 | Optional — evidence-gated |

**Do not “polish” without evidence:** spawn weights, flee feel, canopy logic, catch minigame.

**Per-change detail (T2.1):**
- **Files:** New small listener in Aethermobs or Items (prefer Aethermobs ownership of pet UX); mirror `CryptDiscoverListener` persistence pattern; kinds from `PetHabitat` / `PetHabitatKind`
- **Current:** Players may not know they entered a biotope
- **Desired:** First enter of a dominant habitat → short discover beat; hysteresis so border walking doesn’t spam
- **Reason:** Habitats become *places*, not invisible spawn modifiers
- **Tech:** Persist discovered set; debounce edge thrash; staff markers stay as-is
- **Deps:** Painted zones file on live server; Hub quiet bubbles must remain
- **Regression:** Medium (actionbar spam / hub tutorial clutter if radius wrong)
- **Essential?** Yes for STEP 2 intent

### STEP 3 — Early boss rituals

| ID | Change | Priority | Essential? |
|----|--------|----------|------------|
| T3.1 | Restage Colosseum summon FX to Borderlands-quality beats | P0 | **Essential** |
| T3.2 | Soft per-boss arrival motif after Borderlands spawn (1–2 beats, not Seraphine-scale) | P1 | Recommended |
| T3.3 | Near-field soften of Borderlands altar landmark beam (keep far readability) | P1 | Recommended |
| T3.4 | Tint Borderlands stage rings with `SpiritBoss.color()` | P2 | Optional |
| T3.5 | Trim T1 YAML `PARTICLE_AURA` density | P2 | Optional |
| T3.6 | Pathwarden post-pad entrance beat only | P2 | Optional |

**Per-change detail (T3.1):**
- **Files:** `AetherionItems/.../world/ColosseumEscortService.java` (`igniteSummon`, countdown tick, `spawnBoss`)
- **Current:** FLASH + EXPLOSION + 55 SOUL_FIRE + tall END_ROD column; per-second soul carpets; arrival EXPLOSION_EMITTER + 60 SOUL_FIRE
- **Desired:** 3–4 authored beats matching Borderlands staging language; one clean manifestation
- **Reason:** Same game as showcase without showcase complexity
- **Tech:** Keep 10s bar, gate/taught flags, pad coords, refund paths
- **Deps:** `ColosseumGateService`, Proctor demo flow, `BossSpawnAccess`
- **Regression:** Low–medium if countdown/unlock untouched
- **Essential?** Yes

**Per-change detail (T3.2):**
- **Files:** `BorderlandsRiteService.spawnBoss` (+ tiny helpers); optional Pathwarden hook
- **Current:** Generic soft wither + FLASH + YAML ambient
- **Desired:** Identity beat per T1 (sink / stomp / flutter / draw) via timing+sound+few displays
- **Tech:** Sync with `invulnerable-spawn-ticks`; cleanup displays on abort
- **Regression:** Medium if combat start delayed unfairly
- **Essential?** Recommended

---

## D. RECOMMENDED ORDER

**Keep the proposed order: 1 Textures → 2 Pets/Habitats → 3 Rituals.**

**Why not reorder:**
- No hard code dependency between the three (different plugins/asset surfaces).
- Textures are pure client art + pack overrides — lowest regression into game logic; establishes “authored” visual language for everything else.
- Habitat discover UX benefits from a settled visual bar but does not require new item art.
- Ritual polish is Items/BossEngine FX — best done after texture noise is out of the way so Opus sessions stay single-focus.

**Exception / preflight only:** Before STEP 2 implementation, **ops-audit live `pet-habitats.yml`** (not a reorder — a gate).

**Opus session slicing:** One STEP per Opus run. Do not ask Opus to do all three in one cloud session.

---

## E. OPUS IMPLEMENTATION BRIEF

See companion files:

- `docs/OPUS_POLISH_PASS_PROMPT.md` — copy-paste prompt (**STEP 1 first**)
- `docs/OPUS_POLISH_PASS_STEP2_PROMPT.md` — pets/habitats (after STEP 1 ships)
- `docs/OPUS_POLISH_PASS_STEP3_PROMPT.md` — early rituals (after STEP 2)

Handoff summary for humans: this audit + prompts.

---

## F. VALIDATION PLAN

### STEP 1 — Textures
1. Build/reload resource pack on a 1.21.x client with pack_format 34.
2. `/give` or Dev Menu: `beginner_pickaxe`, all `simple_*` tools+armor — check inventory + hotbar silhouette at default GUI scale.
3. Confirm CMD still resolves (no vanilla wood/leather fallback).
4. If ProgressionItems art shipped: give 2701–2716 samples; verify no look collision with combat/mining/foraging CMD on same materials.
5. Boosters + Gravwell Cleaver + Blossom Blade icons unchanged.
6. Diff pack: no accidental deletion of combat/mining T1 stubs.

### STEP 2 — Pets/habitats
1. Enter a painted biotope first time → discover feedback once; leave and re-enter → no spam.
2. Walk habitat borders → no actionbar thrash.
3. Hub NPC quiet radii still pet-free.
4. Catch flow + collection menu still work.
5. Farm/village/shore still auto (no forced paint kinds).
6. TPS: habitat scan cache still ~stable under multi-player.

### STEP 3 — Rituals
1. Borderlands: vial rite still 10s; drops/stack 64; altar powder contract unchanged; refund on spawn fail.
2. Colosseum: Proctor demo + self-pad still unlock/`markTaught` correctly; FX readable, not carpet-spam.
3. Fight each T1 once: phase shows + signature deaths still fire; arrival beat doesn’t softlock AI.
4. Pathwarden pad summon still works; Gravwell loot chance untouched.
5. Particle budget: standing at altar + during rite should feel calmer than pre-pass Colosseum.

### Regression watchlist (all steps)
- Quest harbour funnel / `border_rites`
- `ItemProfile` / recipes
- Locked boosters, anvil sockets, ranks, signature weapons
- Showcase boss directors untouched
- Gitignored extract pack: Robbi must manually sync client pack if extract is still what players load

---

## File map (quick index)

### Textures
- `AetherionItems/resourcepack/` (+ `pack.mcmeta`)
- `AetherionItems/.../item/CustomItem.java`, `ProgressionItems.java`, `ArmorAppearance.java`, `ItemManager.java`, `ItemProfile.java`
- Gitignored: `Aetherion_texturepack_extract/` (CIT + richer icons)

### Pets
- `Aethermobs/.../pet/{PetFactory,PetRegistry,PetSpawnManager,PetHabitat,PetHabitatZones,PetEntity,PetWalkSurface}.java`
- `AetherionItems/.../world/{PetHabitatZoneService,PetHabitatKind,CryptDiscoverListener}.java`
- Runtime: `plugins/AetherionItems/pet-habitats.yml`

### Rituals
- `AetherionItems/.../world/{BorderlandsRiteService,ColosseumEscortService,ColosseumGateService}.java`
- `BossEngine/src/main/resources/bosses/{hollow_lurker,mcnugget,bridge_troll,skuldugery,pathwarden}.yml`
- `BossEngine/.../fx/{TierPhaseShow,CombatTheatrics}.java`, `instance/SignatureDirector.java`
- Showcase refs: `BossEngine/.../instance/saint/*`, `AshenSovereignDirector.java`, docs `OPUS_BOSS_*.md`

---

## Repo readiness note (scout)

At audit time, local `main` had a **large dirty WIP** (~180+ modified tracked files + many untracked worktrees/patches) unrelated to this polish brief (harbour early-game, midgame, World Eater trees, etc.).

**For Opus:** Prefer a clean branch from **pushed `origin/main` + these docs**, not an accidental dump of mixed WIP.

**For Robbi:** Triage WIP separately (harbour branch already exists in `_wt_early_game` pattern). Do not require Opus to inherit an unclean mega-diff.
