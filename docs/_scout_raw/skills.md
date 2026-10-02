# Skill progression scout (factual)

Spine lives in **AetherionItems**. Mining / Farming / Foraging / Fishing only bridge into it. All skills start available at Lv.1; you equip them into slots. No per-skill unlock ladder.

---

## Shared spine (AetherionItems)

| Piece | Path | Key constants |
|---|---|---|
| Skill catalog | `AetherionItems/.../skill/AetherSkill.java` | 60 skills, 7 categories |
| Level/XP/effect curve | `.../skill/SkillProgression.java` | `MAX_LEVEL=100`, `RARITY_EVERY=20` |
| Runtime + XP grants | `.../skill/SkillService.java` | `SLOT_COUNT=7`, `COIN_UNLOCK`, `grant*` |
| Account level | `.../skill/AetherionLevel.java` | `MAX_LEVEL=5000`, `XP_PER_LEVEL=100`, `SKILL_XP_DIVISOR=40` |
| Rarity seals | `.../skill/SkillSeals.java` + `.../codex/CodexTiers.java` | seals at Uncommon→Mythic |
| Tool item leveling | `.../item/SkillToolCaps.java` | T1→50 … T5→150 |

### Level range & XP curve (every `AetherSkill`)

- **1–100** (`SkillProgression.MAX_LEVEL`)
- Stages: Apprentice 1–49 · Journeyman 50–74 · Master 75–99 · Mastered 100
- Rarity every 20: Common &lt;20 · Uncommon 20 · Rare 40 · Epic 60 · Legendary 80 · Mythic 100
- `xpToNext(level) = 32 + 11*L + L²/2` (+70× after 50, +110× after 75)

| Level | XP to next | Cumulative XP | Effect mult (w/ rarity) |
|---|---:|---:|---:|
| 1 | 43 | 0 | ~1.00× |
| 25 | 619 | ~6.5k | ~1.33× |
| 50 | 1,902 | ~35k | ~1.66× |
| 75 | 5,599 | ~125k | ~2.84× |
| 100 | — | ~347k | ~6.50× |

Effect formula: `1 + (L-1)×0.0102` to 50, then `+0.044`/lvl to 75, then `+0.140`/lvl, plus `+0.08` per rarity tier.

Compact skills (Pack Rat / Timber Tax / Seed Ledger / Fish Ledger): **0.6% → 2.8%** linear by level.

### How XP is applied

`SkillService.grant(category, amount)` credits **every equipped skill of that category at full amount**, and equipped **Utility at half**. Unequipped skills get nothing from loop XP.

### Loadout / slots (not skill unlocks)

- 7 slots; slot 1 free
- Lifetime coins: `0 · 5k · 25k · 80k · 200k · 500k · 1.25M` (`COIN_UNLOCK`)
- Presets unlock at account Lv **1 / 10 / 25**

### Account level (fed by skills)

- Skill XP pool ÷ 40 → Aetherion XP; flat 100 XP/level to 5000
- Stat milestones: +1 Damage / +1 Health every 5 account levels
- Shard claim every 650 account levels (`SHARD_MILESTONE`)
- Rank titles at 25,50,75,100,150,200,250,350,500,750,1250,2500,5000

### Skill seals (claim rewards)

| Rarity | Level | Coins | Shards |
|---|---|---:|---:|
| Uncommon | 20 | 2,500 | 10 |
| Rare | 40 | 10,000 | 25 |
| Epic | 60 | 22,500 | 60 |
| Legendary | 80 | 40,000 | 150 |
| Mythic | 100 | 62,500 | 400 |

### Category inventory

| Cat | Count | Examples (early / mid / late flavor) |
|---|---:|---|
| Combat | 11 | Heavy Hands · Boss Grudge · Last Word |
| Mining | 12 | Rock Whisper · Bedrock Born · Depth Gauge |
| Foraging | 10 | Woodwise · Grove Born · Heart Hunter |
| Farming | 8 | Crop Gossip · Soil Sense · Market Day |
| Fishing | 7 | Bite Me · Lake Sense · Tide Reader |
| Utility | 8 | Quick Hands · Pinch Penny · Golden Hour |
| Dungeon | 4 | Chamber Pace · Relic Appetite · Floor Grudge |

---

## Mining

**Bridge:** `AetherionMining/.../isle/MineSkills.java`  
**Isle:** Mining Eldervale (`mine-isle` in `AetherionMining/src/main/resources/config.yml`, world paste ~22,90,580)

### Skills (12) — bonuses / flags

| Skill | Bonus / flag |
|---|---|
| Rock Whisper | +12 Mining Power |
| Extra Pocket | +22 Fortune |
| Pack Rat | compact ore (`PACK_RAT`) |
| Spread Sheet | +8 Spread |
| Quarry Manners | +10 Power, +13.5 Fortune |
| Cave Sense | +10 Power in dark (`CAVE_SENSE`) |
| Bedrock Born | +16 Fortune +6 Spread **isle only** |
| Work Song | Strike Rhythm faster (`WORK_SONG`) +6 Fortune |
| Geode Nose | Crystal Finds (`GEODE_NOSE`) |
| Union Card | Foreman Contracts pay more |
| Seam Reader | Seam Chains / shimmer |
| Depth Gauge | depth bonuses ×1.5 |

### XP sources

**Base (Items):** `SkillService.grantFromBlock` via `HarvestListener`

| Material class | XP |
|---|---:|
| Stone/cobble/deepslate/etc. | 3 |
| Ores | 8 |
| Amethyst cluster | 12 |
| Full mineral blocks | 14 |
| Diamond/emerald/debris/netherite | 18 |

**Isle bonuses (`MineSkills.bonus` → `grantGatherBonus(MINING)`):** rhythm ticks (+1), seams (+6), depth bands, crystal finds, ore mastery (`40×tier²`), foreman contracts, forge (80×/100×), compass discoveries (20/150), critters (6–250), assay (up to 400), amethyst (60), hazards (20), events (8–10+).

### Parallel systems (affect mining, separate from skill Lv)

- **Ore Mastery** (`OreMastery.java`): tiers I–VII; thresholds 150→100k × ore scale; +5 Fortune/tier; +3 Power @IV, +8 @VII; isle/veins count double
- **Strike Rhythm, Crystal Finds, Foreman Contracts, Depth bands**

### Tools / gear

- Pickaxes T1–T5: `mining_pickaxe`…`_5` — craft ladder in `RecipeRegistry` (predecessor + compressed mats). **No** `*PickaxeProgress` item-leveling class (unlike hoe/axe/rod)
- Mining armor T1–T5 same category
- Charms: Survey Token/Charm/Relic (`AccessoryItems.Charm.MINING`)
- Vein Siphon (blueprint unlock)

### Unlocks / gates

- Amethyst Violet Deep: mining skill ≥ **20** (config `amethyst-mine.violet-deep-level`)
- Quest example: Coal Audit needs Mining category **8** (`QuestRegistry`)
- Personal island claim: **Aetherion Lv 20** (`ProgressionService.ISLAND_LEVEL`) — account, not mining skill

### Cross-system

Skills feed account XP; mining Codex blocks; pet gather share on block XP; compact upgrades via Pack Rat; Cave Sense used by hazards; Union Card scales contract coins.

---

## Farming

**Bridge:** `AetherionFarming/.../FarmingSkills.java`  
**Isle:** Farm Isle (`AetherionFarming/.../isle/FarmIsle.java`)

### Skills (8)

| Skill | Effect |
|---|---|
| Crop Gossip | +22 Fortune, +8 Harvest Spread |
| Wide Furrow | +24 Harvest, +10 Fortune |
| Seed Ledger | crop compact |
| Soil Sense | +18 Fortune +12 Harvest **isle only** |
| Row Rhythm | Harvest Rhythm (`ROW_RHYTHM`) +6 Fortune |
| Blue Ribbon | Prize Crops |
| Bird Law | crow scare / Golden Hour |
| Market Day | Harvest Orders pay more |

### XP sources

**Base:** `grantFromFarm` — `CropHarvestListener` pays **4 XP per harvested crop** (same amount granted to hoe progress). Vanilla block path also `grantFromBlock` → farming **4** for crops.

**Isle bonuses:** rhythm (+1), prize crops (40), crop mastery (`50×tier²`), harvest orders, discoveries, bakehouse (5), bird helper XP, bloom events, food XP on isle.

### Parallel: Crop Mastery

`CropMastery.java` — I–VI, thresholds 250 → 100k; +6 Fortune/tier; isle harvests weight 2.

### Tools

- Hoes T1–T5 + armor: craft ladders
- **Hoe item levels:** `FarmingHoeProgress` + `SkillToolCaps` (T1 max 50 … T5 max 150)
- Charms: Furrow Token/Charm/Relic

### Cross-system

SOIL_SENSE isle-gated in `SkillService` bonus path; Market Day / Bird Law / Blue Ribbon / Row Rhythm drive isle loops; feeds account XP + Codex.

---

## Foraging

**Bridges:** `ForagingSkills.java` (hub/fell) + `isle/ForageBridge.java` (Eldervale flags)  
**Isle:** Foraging Eldervale

### Skills (10)

| Skill | Effect |
|---|---|
| Light Foot | flavor (no Speed) |
| Woodwise | +18 Fortune |
| Timber Tax | oak compact |
| Green Thumb | +11 Fortune |
| Grove Born | wood cap / mastery pace **isle** |
| Sap Sense | Crown Finds |
| Steady Hands | CHOP window +1, shorter miss CD |
| Deadfall Dancer | widowmaker mitigation |
| Board Rates | Lumber Board pay |
| Heart Hunter | heartwood rate |

### XP sources

**Base:** `grantFromBlock` → foraging **4** per wood/plank (`Tag.LOGS`/`PLANKS`); also from `ForagingListener`.

**Bonuses:** perfect fells / streaks (`ForagingSkills.bonus`), Grove Mastery (`paid×40`), Crown Finds (`6×grade`), Lumber Board (`coins/8`), Forest Ledger, compass (20–30), events (5–40+), critters (12), Woodwright marks (30×).

### Parallel: Grove Mastery

`GroveMastery.java` — I–VII, base fells `{10,40,120,300,700,1500,3000}×wood.scale`; coins 150→12k; +1 wood cap/tier; +0.25% heartwood/tier; IV = wider CHOP; VII = Crown Finds ×1.5.

### Tools

- Axes T1–T5 + armor ladders
- **Axe item levels:** `ForagingAxeProgress` + `SkillToolCaps`
- Charms: Grove Token/Charm/Relic

### Skill-level gates

- `ForagingStrike.WIDE_WINDOW_LEVEL = 50` — wider CHOP when best foraging skill ≥ 50
- Quests: Foraging 5 / 8 (`canopySample`, `stumpCensus`)

### Cross-system

Isle flags only when equipped; Grove Born can double-count mastery fells; Codex wood; pet share on wood XP.

---

## Fishing

**Bridge:** `AetherionFishing/.../FishingSkills.java`  
**Isle:** Fishing Eldervale  
**Loot/weights:** `AetherionItems/.../listener/FishingLootPool.java`

### Skills (7)

| Skill | Effect |
|---|---|
| Bite Me | +30 Fish Catch, +18 Fortune |
| Short Cast | +8 Speed, +15 Catch, +10 Fortune |
| Fish Ledger | cod compact |
| Lake Sense | +20 Catch +8 Speed **isle only** |
| Steady Line | gold cell 2-wide; boiling streak can survive miss |
| Tall Tales | rare/legend weight & kg |
| Tide Reader | shoals faster + wake arrow |

### XP sources

**Base:** `grantFromFish(8)` on catch (`FishingListener`); sea encounters `4 + lootQuality×2`.

**Bonuses:** perfect reel **+4**, streak up to **+3** (`FishingController`); TheLine land XP; first-species XP; Angler Log; lake events; compass discoveries.

### Loot gates (skill level = highest fishing skill)

| Gate | Level / condition |
|---|---|
| Soft puffer weight bump | &lt;8 vs ≥8 |
| Soft prismarine bump | &lt;15 vs ≥15 |
| Compressed fish (rod T2 path) | fishing ≥ **18** or Catch ≥ 80 |
| Compacted fish (rod T3 path) | fishing ≥ **40** and Catch ≥ 160 |
| Rod T3+ / T4+ | always allow compressed / compacted respectively |

Weights scale with `SkillProgression.effectMultiplier(fishingLevel)`.

### Parallel: Angler Log

`AnglerLog.java` — ranks 0–10; points thresholds `{0,3,8,15,24,35,48,63,80,100,120}`; permanent **+3 Catch / +2 Speed per rank on isle**.

### Tools

- Rods T1–T5 + armor ladders
- **Rod item levels:** `FishingRodProgress` + `SkillToolCaps`
- Catcher Gaff: `CatcherGaffProgress` (same caps)
- Charms: Tide Token/Charm/Relic

### Unlocks

- Quest: Scale Sample needs Fishing **10**
- Lake Sense / Steady Line / Tall Tales / Tide Reader are isle-oriented flags

---

## Tools summary (`SkillToolCaps`)

Independent of skill level — per-item XP on the tool:

| Tier | Max tool level |
|---|---:|
| T1 | 50 |
| T2 | 75 |
| T3 | 100 |
| T4 | 125 |
| T5 | 150 |

`xpToNext = 14 + 7×L` (+5/lvl after 40, +18/lvl after 60). Gain mult 1.0 / 1.5 (@60) / 2.0 (@80).

Applies to: **hoe, axe, rod, gaff**. Mining pickaxes are tiered crafts without that progress class.

Recipe unlocks for T2–T5 are **crafted predecessor + materials**, not skill-level gates (`RecipeRegistry` ladders).

---

## Cross-system map

| System | How skills touch it |
|---|---|
| Account / TAB ranks | Skill XP → Aetherion level → titles/stats/shards |
| Quests | `QuestSkillGate` — category/skill/account mins (examples: Mining 8, Foraging 5/8, Fishing 10, Account 5/20) |
| Codex | Collection ladders + skill seals |
| Pets | `sharePetGather` on mining/wood/farm/fish base XP |
| Compact economy | Pack Rat / Timber Tax / Seed Ledger / Fish Ledger |
| Dungeons | Relic Appetite / Floor Grudge / Stone Blood (equipped) |
| Combat | kill XP → Combat skills; Blood Tax / Life Absorb / Boss Grudge flags |
| Charms | per-category accessory line (`AccessoryItems`) |
| Personal island | Account Lv 20, not gathering skill |

---

## Sample early / mid / late (gathering)

| Stage | Typical | What opens |
|---|---|---|
| Early (~1–20) | Equip Rock Whisper / Crop Gossip / Woodwise / Bite Me; slot 1–2 | Base loop XP; Common→Uncommon seal @20; soft fishing weight shifts |
| Mid (~40–60) | Isle flags (Bedrock Born, Soil Sense, Grove Born, Lake Sense); Rare/Epic seals | Compressed fish path (~18); foraging wide window @50; Journeyman curve @50; mastery tiers climbing |
| Late (~75–100) | Master curve; Mythic seal; Depth Gauge / Heart Hunter / Tall Tales stacked | Compacted fish (~40+); Ore/Crop/Grove mastery VII; tool T4–T5 + high item levels |

---

## Canonical file shortlist

```
AetherionItems/.../skill/AetherSkill.java
AetherionItems/.../skill/SkillProgression.java
AetherionItems/.../skill/SkillService.java
AetherionItems/.../skill/AetherionLevel.java
AetherionItems/.../skill/SkillSeals.java
AetherionItems/.../codex/CodexTiers.java
AetherionItems/.../item/SkillToolCaps.java
AetherionItems/.../listener/FishingLootPool.java
AetherionItems/.../item/{FarmingHoe,ForagingAxe,FishingRod,CatcherGaff}Progress.java
AetherionMining/.../isle/MineSkills.java (+ OreMastery, StrikeRhythm, …)
AetherionFarming/.../FarmingSkills.java (+ CropMastery, HarvestRhythm, …)
AetherionForaging/.../ForagingSkills.java + isle/ForageBridge.java (+ GroveMastery)
AetherionFishing/.../FishingSkills.java (+ AnglerLog, TheLine, FishingController)
AetherionQuests/.../util/QuestSkillGate.java
```

No redesign — this is the current wiring as coded.