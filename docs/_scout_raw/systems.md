# Factual scout — Pets / Boosters / Quests / Guilds

No redesign. Paths relative to worktree root.

---

## 1) Pets (Aethermobs)

### Canonical files
| Role | Path |
|------|------|
| Pet IDs + core stats + spawn | `Aethermobs/.../pet/PetFactory.java` |
| Registration | `Aethermobs/.../AetherMobs.java` (~L170–596) |
| Soft caps / rarity fractions | `Aethermobs/.../model/PetBalance.java` |
| Level/XP | `Aethermobs/.../pet/PetInstance.java` |
| Stat injection | `Aethermobs/.../pet/PetStatProvider.java` |
| Skills / combat | `Aethermobs/.../pet/skill/PetSkill.java` (+ `PetSkillManager.java`) |
| Catch spheres | `Aethermobs/.../pet/CatchSphereRegistry.java` |
| Rarity roll weights (factory constants) | `PetFactory`: COMMON=30, UNCOMMON=56, RARE=10.5, EPIC=2.8, LEGENDARY=0.28, MYTHIC=0.004 |

### Levels / upgrades
- Max level: **100** (`MAX_LEVEL`); **Aethered dragons: 200** (`AETHERED_MAX_LEVEL`)
- XP curve: `36 + 16L + L²/2` per level (`PetInstance.getRequiredExperience`)
- Level scale: **+1%/level** (`PetBalance.PER_LEVEL = 0.01`) → L100 ≈ ×1.99, then **dampen** over soft cap (`OVER_CAP = 0.18`)
- Shiny: **×1.25** (`SHINY_MULTIPLIER`); base shiny **1/4096**, Charm **1/256** within 48 blocks (`AccessoryItems`)
- Mythic dragon → Aethered: core **×1.25**, bonuses **×1.20** (`ascendToAethered`)
- Signature multipliers: `aetherion` **1.45**, `*_dragon` **1.20**, `hacker` **1.10**

### Soft caps (L100 target = 12% stack slice)
`FORTUNE 132`, `MINING_POWER 58`, `HARVEST_SPREAD 144`, `FISHING_CATCH 168`, `FISHING_SPEED 31`, `DAMAGE 38`, `DEFENSE 50`, `HEALTH 67`, `SPREAD 10`, `ATTACK_SPREAD 22`, `SPEED 12`, `PET_CATCH_RATE 18`, `CRIT_CHANCE 9`, `CRIT_DAMAGE 59`, `UNDEAD_DAMAGE 38`, `UNDEAD_RESIST 20`

Rarity fractions of L1 target: Common 0.18–0.32 → Mythic/Aethered 1.10–1.35.

Bonus stat count by rarity: C/U=1, R/E=2, L/M=3, Aethered=4.

### Registered pet IDs (79) — id → core capability
**Surface/farm:** `wolf` ATTACK_SPREAD, `pig` HARVEST_SPREAD, `cow` HEALTH, `farm_rabbit` HARVEST_SPREAD, `horse` SPEED, `sack_of_potatoes` HEALTH  

**Cave:** `bat` MINING_POWER, `cave_spider` CRIT_CHANCE, `creeper` DAMAGE, `zombie` UNDEAD_DAMAGE, `skeleton` CRIT_DAMAGE, `mining_dragon` MINING_POWER, `forest_dragon` SPREAD  

**Aquatic:** `squid` SPREAD, `glow_squid` MINING_POWER, `axolotl` HEALTH, `guardian` DAMAGE, `dolphin` SPEED, `cod` FISHING_SPEED, `salmon` SPEED, `pufferfish` DEFENSE, `tropical_fish` PET_CATCH_RATE, `water_dragon` FISHING_SPEED, `nature_dragon` HARVEST_SPREAD  

**Nether/sky/other:** `wither` UNDEAD_RESIST, `fire_dragon` DAMAGE, `blaze` CRIT_CHANCE, `slime_minion` HEALTH, `ghast` ATTACK_SPREAD, `hawk` CRIT_CHANCE, `bee` FORTUNE, `pigeon` SPREAD, `lightning_dragon` CRIT_DAMAGE, `ocelot` FISHING_CATCH, `parrot` SPREAD, `turtle` DEFENSE, `panda` HEALTH, `goat` SPEED, `llama` SPREAD, snow pack (`fox` SPEED, `rabbit` FORTUNE, `polar_bear` DEFENSE, `yeti` HEALTH, `snowflake` CRIT_CHANCE, `ice_dragon` DAMAGE), desert (`camel` HEALTH, `armadillo` DEFENSE), biome fillers (`frog`…`lush_oracle`), `allay` FORTUNE  

**Special:**  
- `aetherion` DAMAGE — spawnWeight **0**, Mythic only  
- `hacker` — **shopExclusive**, **randomCoreStat**, Rare/Epic only  
- Dungeon % auras (literal min–max, **not** PetBalance bands):  
  - `dungeon_zombie` DAMAGE aura 2.5–7%  
  - `dungeon_skeleton` BOW-only DAMAGE 3.4–9.4%  
  - `dungeon_dragon` ALL aura Mythic 4.2–6.8%  

Legacy save id `poison_dragon` → `mining_dragon`.

### Combat / skill impact (mandatory vs decorative)
| Impact | Flag | Notes |
|--------|------|-------|
| Flat ItemCapability via `PetStatProvider.getStat` | **Mandatory** for equipped non-dungeon pets | Feeds mining/farm/fish/combat formulas |
| Dungeon % via `getMultiplier` | **Mandatory in dungeons only** | DAMAGE / BOW / ALL |
| Active skills (beams, dragon charges, creeper burst, hijack, etc.) | **Mandatory combat** when that pet is equipped | Tuned in `PetSkill` (e.g. guardian 5–10 dmg, dragons L1–L100 charge bands, Allay boss-drop +10–55%) |
| Passive potions (Speed, NV, Jump, Resistance…) | **Soft power** | QoL + combat edge |
| Lookout / pet-sense glow | **Decorative / QoL** | No damage |
| Farm compact (`FARM_*`, `PIG_TRUFFLE`) | **Progression economy** | Compact chance ~1–5.5% |
| Fishing wait reduction | **Progression** | Ocelot / Cod |

Catch spheres (catch % by rarity C→M):  
`common` 45/28/12/4/1.2/0.3 · `rare` 60/42/24/10/3.5/0.8 · `epic` 75/58/40/22/10/2 · `legendary` 88/72/55/35/18/5 · `beta` flat 90% infinite.

**Overall:** Pets are **optional power**, not a hard gate — except tutorial quest `pocket_zoo` forces catch + equip once.

---

## 2) Boosters (AetherionItems)

### Canonical files
| Role | Path |
|------|------|
| Factories / IDs | `AetherionItems/.../item/BoosterItems.java` |
| Types | `.../model/BoosterType.java` |
| Values | `.../model/BoosterStats.java` |
| Cap | `.../core/BoosterLimits.java` → `BalanceTargets.BOOSTER_MAX_TOTAL = 14` |
| Apply | `.../model/BoosterApplier.java` |
| Socket GUI | `BoosterSocketMenu` + `BoosterSockets.COUNT = 14` |

### Item IDs → effect
| ID | Type | Stat | Common → Mythic flat |
|----|------|------|----------------------|
| `coal_booster` | CORE | Mining Power, Fortune, Damage, Defense | 0.8 → 3.0 |
| `iron_booster` | CORE | same | 1.2 → 4.8 |
| `gold_booster` | CORE | same | 2.0 → 7.0 |
| `diamond_booster` | CORE | same | 2.8 → 9.0 |
| `emerald_booster` | SPECIAL | Spread | 3 → 15 |
| `redstone_booster` | SPECIAL | Attack Spread | 3 → 15 |
| `lapis_booster` | SPECIAL | Health | 3 → 15 |
| `glowstone_booster` | SPECIAL | Speed (boots) | 0.8 → 2.8 |
| `wheat_booster` | SPECIAL | Pet Catch Rate | 0.8 → 3.5 |
| `carrot_booster` | SPECIAL | Harvest Spread | 3 → 15 |
| `oak_booster` | SPECIAL | Crit Damage | 3 → 15 |
| `birch_booster` | SPECIAL | Crit Chance | 0.4 → 1.8 |

CMD: coal 3001 … birch 3012 (carrot 3013). Lore always shows rarity table (LOCKED: stats stay in lore).

### Stackability — factual conflict
- **Current code:** `BoosterItems.applyBoosterData` → `meta.setMaxStackSize(1)`  
- **Locked rule:** stackable, `BoosterItems.MAX_STACK = 64` — **constant not present** in this tree; items are created as stack-size **1**  
- Socket cap: **14 total** per item (core + special share one pool)

### Usefulness
| Role | Flag |
|------|------|
| Core flats on tools/weapons/armor | **Mandatory midgame power** once Temper teaches them |
| Specials (spread/crit/health/speed/catch/harvest) | **Strong optional** — gated by `ItemProfile.allowsSpecialBooster` |
| Tutorial `lesson_boost` | **Mandatory unlock** — grants ANVIL + 1 Emerald Booster on accept |

---

## 3) Quests (AetherionQuests)

### Canonical
- Registry: `AetherionQuests/.../quest/QuestRegistry.java`
- Soft gates: `.../util/QuestStoryGate.java`
- XP helper: `.../reward/QuestAetherXp.java` (100 XP ≈ 1 account level)
- Unlocks on accept: `QuestManager` (~L676–692)
- Config.yml is **Talk UX / NPC life only** — not quest data

### Mandatory tutorial spine
**Done when all complete:** `lesson_boost`, `lesson_manager`, `farm_hand`, `pocket_zoo`  

**Tutorial quest set:**  
`welcome_aboard`, `gather_wood`, `forge_coal`, `first_shift`, `farm_hand`, `a_good_catch`, `dock_pass`, `pocket_zoo`, `lesson_steel`, `lesson_boost`, `lesson_manager`

Order soft-gate: Harbour → Mine → Temper → Ledger → Fields → graduation.

| Quest ID | Objective | Key rewards / unlocks | Flag |
|----------|-----------|----------------------|------|
| `welcome_aboard` | TALK lumberjack | waypoint only | Mandatory start |
| `gather_wood` | DELIVER 10 OAK_LOG | Starter Gear No Axe, 60c, 80 XP; axe on accept | Mandatory |
| `forge_coal` | DELIVER 20 COAL | Emerald Booster, `spawn:mines`, 60c, 70 XP | Mandatory path |
| `first_shift` | MINE 32 ANY_ORE | 80c, 90 XP | Mandatory path |
| `farm_hand` | HARVEST 48 WHEAT | 50c, 50 XP | **Graduation gate** |
| `pocket_zoo` | CATCH + pet menu + equip | 16 Rare spheres, PETS unlock, 16 common spheres on accept | **Graduation gate** |
| `lesson_boost` | APPLY_BOOSTER ×1 | ANVIL unlock + Emerald Booster | **Graduation gate** |
| `lesson_manager` | AETHER_SKILL ×1 | SKILLS unlock | **Graduation gate** |
| `lesson_steel` | KILL 10 BORDERLANDS | coins/XP | Tutorial-adjacent |
| `a_simple_craft` | (craft) | WORKBENCH unlock on accept | Soft mandatory for crafting |
| `a_good_catch` / `dock_pass` | fish / dock | small coins/XP | Tutorial set |
| `border_rites` | BORDERLANDS_RITE | coins/XP | Optional lesson |
| `lesson_bones` | DELIVER 10 BONE | coins/XP | Optional |

### Boss / world chains (optional power + spawn unlocks)
T1 `registerBossHunt` → Random Booster ×2 + coins + XP + Spawn:  
`those_sounds` (hollow_lurker), `poultry_problem`, `troll_toll`, `ink_contract`, `ash_and_arrows`, `walking_mountain`, `the_veil`, `closed_road`  

T2 `registerT2BossHunt` → Compacted Diamond Block ×2:  
`open_ticket`, `lost_and_found`, `overtime`, `denied_claim`, `bounced_check`  

### Side / economy quests (decorative for story, useful for boosters)
`sidewalk_survey`, `gravel_ambition`, `pantry_run`, `pig_in_a_poke`, `coal_audit`, `stump_census`, `canopy_sample`, `scale_sample`, `bovine_brief`, `guild_brick` (+ Random Boosters / Catch Spheres)

**Also decorative:** Surveyor “Hidden Blueprints” **retired**; Eldervale welcome / forage pad guides = flavor (blueprint/skill gated elsewhere).

---

## 4) Guilds / Islands / Quarry (AetherionGuilds)

### Unlock gates (Items → Guilds)
`ProgressionService.ISLAND_LEVEL = 20`, `GUILD_LEVEL = 75`  
Foreshadow: config `island-foreshadow-level: 15`, `guild-foreshadow-level: 65`  
Files: `AetherionItems/.../progress/ProgressionService.java`, `AetherionGuilds/.../util/AetherionItemsAccess.java`, `.../island/UnlockService.java`

### Island tiers
`IslandTiers.java` — MAX **5**  
Upgrade coins: L1→2 **5k**, 2→3 **20k**, 3→4 **60k**, 4→5 **150k** (cobble/cores fields = 0)  
Perks: build radius +8/tier, belt cap `160×tier`, +1 copy of each machine type  
Bank: `9×tier` slots (+9 if ≥8 members), coin cap `25_000×tier×members`

### Starters
`StarterLayout.java`: personal `grove` / `quarry` / `tide`; guild `guild_harbour`  
**Mandatory to play islands** after Lv20 claim; **decorative** which flavor you pick (can't swap).

### Structures (`StructureType.java`)
| ID | Cost | Role | Flag |
|----|------|------|------|
| `storage_hut` | 2_000c (first free lore) | SINK 5M cap | **Core economy** |
| `workshop` | 1_500c | UTILITY, max 1 | **Mandatory** to unlock mills/forges/belts |
| `depot` | 500c | SINK 250k | Optional sink |
| `mill` | 3_000c + `quarry_compressor` | Raw→Compressed 128:1, 4096/s | **Core mill chain** |
| `forge` | 8_000c + `quarry_compactor` | →Compacted | **Core compact chain** |
| `quarry_housing` | auto | SOURCE housing | Visual+chute |
| `project` | — | DECOR | Guild projects |

Structure upgrades (`StructureUpgrades.java`): Hut→Loft (32 compressed + 15k) →Warehouse (4 compacted + 1 core + 60k); Depot/Mill/Forge T2 costs listed in file.

### Quarry (`QuarryType.java`)
- Levels **1–7**, tick curve `{0,1,2,4,8,16,48,128} × rate`  
- Upgrade: L1–3 compressed (8/32/64), L4 16 compacted, L5 48 compacted +1 core, L6 64 compacted +3 cores  
- `CORE_ID = quarry_core`  
- IDs: `cobble_quarry`, `coal_quarry`, `raw_iron_quarry`, … logs, crops, mob drops, `cod_quarry` (full enum in file)  
- Interval: config `minion-interval-seconds: 10`  
**Mandatory for island AFK income**; decorative flavor per resource type.

### Land / belts / projects
- Personal parcel: `2500 × (owned−8)`; guild `10000` from bank (`config.yml`)  
- Belt tile: **5 coins**; free starter kit **16** tiles; max run **64**; belt cap from island tier  
- Guild projects (`GuildProjectType`):  
  - `guild_hall` stages: 25k/60k/150k + mats/compressed/compacted  
  - `harbour_beacon`: 15k/40k/90k + mats  
  **Decorative prestige** for harbour look; consumes quarry output → soft progression sink

### Progression contribution
| System | Contribution |
|--------|----------------|
| Personal island @20 | Private production / storage / belts |
| Guild @75 | Shared bank, harbour, projects, more quarries |
| Quarry → Mill → Forge | Compressed/compacted items (quest `guild_brick`, structure upgrades, cores) |
| Island tier coins | Space + machine count — **not** combat power |

---

## Cross-system flags (one-liners)

| System | Gate? | Power? |
|--------|-------|--------|
| Pets | Soft (tutorial catch once) | Real stats/skills; ~12% soft-capped slice |
| Boosters | Soft (Temper unlocks anvil) | Real flats; 14 sockets; stackability **code≠lock** |
| Quests | Hard tutorial spine; bosses optional | XP, spawn unlocks, boosters, gear |
| Islands/Guilds | Hard Lv20 / Lv75 | Economy/production, not combat stats |

All values above are as-coded in this worktree; no redesign proposed.