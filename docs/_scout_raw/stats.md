# Player stat / progression scout (factual)

**Scope:** AetherionItems owns stats/combat/XP. AetherionCore is thin (keys, progress unlock bridge, raw-hit flag) — no `StatType` / `ItemStats` there.  
**Not present as Aetherion combat stats:** Strength, Luck. Combat power = `DAMAGE`. “Luck-like” = `FORTUNE` / `PET_CATCH_RATE` (and skill `LUCKY_STREAK`).

---

## 1. Canonical stat model

### `ItemCapability` (enum — the “StatType”)
`AetherionItems/.../model/ItemCapability.java`

| Capability | Role |
|---|---|
| `MINING_POWER` | Mining power |
| `FORTUNE` | Fortune / drops |
| `DAMAGE` | Combat damage (replaces vanilla when &gt; 0) |
| `DEFENSE` | Incoming mitigation |
| `HEALTH` | Bonus max HP (added to vanilla 20) |
| `SPREAD` | Mining/foraging block spread |
| `ATTACK_SPREAD` | Extra combat targets |
| `SPEED` | Movement % (gear/pets/boosters; **not skills**) |
| `PET_CATCH_RATE` | Pet catch |
| `CRIT_CHANCE` | Crit roll % |
| `CRIT_DAMAGE` | Crit bonus % |
| `UNDEAD_DAMAGE` | % vs undead |
| `UNDEAD_RESIST` | % resist from undead (capped 70) |
| `HARVEST_SPREAD` | Farming harvest |
| `FISHING_SPEED` / `FISHING_CATCH` | Fishing |

No separate `StatType` / Strength / Luck enum.

### `ItemStats` (per-item values + booster counts)
`.../model/ItemStats.java` — fields mirror capabilities; booster counters for Coal/Iron/Gold/Diamond + Emerald/Redstone/Lapis/Glowstone/Wheat/Carrot/Oak/Birch.  
Key method: `upgradeFrom(oldActual, oldBase, newBase)` — core stats scale proportionally; specials keep extras; booster counts preserved.

### PDC keys (`ItemKeys`)
`.../core/ItemKeys.java` — namespace `aetherionitems`:  
`mining_power`, `fortune`, `damage`, `defense`, `health`, `spread`, `attack_spread`, `speed`, `catch_rate`, `crit_chance`, `crit_damage`, `undead_damage`, `undead_resist`, `harvest_spread`, `fishing_speed`, `fishing_catch` + booster count keys.  
`ItemKeys.forCapability(ItemCapability)` maps enum → key.

### `ItemProfile` + capability gating
`.../model/ItemProfile.java` — which capabilities an item type may expose. Aggregation only counts a profile’s allowed capability (UNKNOWN still uses written PDC).

---

## 2. Aggregation pipeline

### `ActiveEquipmentStats.getStat(Player, ItemCapability)`
`.../manager/ActiveEquipmentStats.java`

1. Sum gear slots: mainhand, **offhand** (unless charm-suppressed), helmet, chest, legs, boots  
2. Per item: `ItemManager.getStat` → profile gate → optional dungeon/core mults  
3. Add all registered `StatProvider.getStat`  
4. Multiply by product of all `StatProvider.getMultiplier` (only if `extra > 0`)

Dungeon item mults inside `getItemStat`:
- Infusable: `× DungeonCore.rarityStatMultiplier(rarity)` (EPIC 1.05, LEGENDARY 1.10, MYTHIC/AETHERED 1.16)
- Native dungeon gear **outside** dungeon: `× DungeonArmor.OVERWORLD_STAT_MULTIPLIER` (**0.38**)
- **In** dungeon: `× DungeonGearProgress.dungeonBonusMultiplier` (cores × gear level)

### `ItemManager.getStat(ItemStack, ItemCapability)`
Reads DOUBLE from PDC via `ItemKeys.forCapability`. Persists via `saveItemStats`.

### `StatProvider` interface
`.../manager/StatProvider.java` — `getStat`, default `getMultiplier` → 1.0

### Registered providers (Items + hooks)
| Class | Flat / mult |
|---|---|
| `SkillService` | Equipped skills + account milestone HP/DMG; **SPEED always 0** |
| `CodexPerks` | Collection Fortune; Bestiary Damage/Health |
| `GearSetBonuses` | Full-set undead/crit/def/harvest/spread/fish |
| `DungeonGearListener` | Calling full-set bonuses |
| `ProgressionEffects` | Compressed coal ring +10 SPEED in dark |
| `HollowSunSetListener` | Mult only: swell DMG×1.10 / DEF×1.08; Red Giant DMG×1.15 |
| `PetStatProvider` (AetherMobs) | Equipped pet scaled stats (+ catch skill extras); percent pets use `getMultiplier` |
| FarmIsle / MineIsle / FishIsle / Scarecrow / BirdScare | Domain/event providers (outside Items but same bus) |

UI mirror: `StatsOverviewGUI` — same `ActiveEquipmentStats` totals.

---

## 3. Health / Speed application

### `HealthListener`
`.../listener/HealthListener.java`

- **Base:** `VANILLA_HEALTH = 20.0`
- **Max HP:** `20 + ActiveEquipmentStats.getStat(HEALTH)` via `Attribute.MAX_HEALTH` / `GENERIC_MAX_HEALTH`
- Cancels satiated/regen regen; scaled natural regen ~every 1.6s: `amount = max/20`
- Action bar: current/max HP + Defense
- Methods: `updateHealth`, `refreshHealth`, `heal`, `updateSpeed`

### Speed
- Gear/pet SPEED → `AttributeModifier` ADD_SCALAR `speed/100` (`ItemKeys.key("movement_speed")`)
- Account level ≥ 3: fixed world pace `+0.36` ADD_SCALAR (`world_pace`) + jump `+0.055`
- Comment: skills never contribute SPEED
- Offhand `thermal_core`: attack speed `+0.42` ADD_SCALAR (unless charm suppressed)

---

## 4. Damage formulas

### Player → mob — `DamageListener.onEntityDamage`
`.../listener/DamageListener.java`

- Melee only (`ENTITY_ATTACK` / `SWEEP`); skips Ore Troll, scripted hits, bows/wands in hand
- If equipment `DAMAGE <= 0` → leave vanilla
- Else **replace** event damage with Aetherion damage:
  1. Sum `DAMAGE`
  2. Crit: if `random*100 < CRIT_CHANCE` → `damage *= 1 + CRIT_DAMAGE/100`
  3. Optional `XpBoosterService.damageMultiplier` (1.04 when flask active)
  4. Undead: `damage *= 1 + min(80, UNDEAD_DAMAGE)/100`
- **Attack Spread:** `floor(AS/100)` guaranteed extras + remainder % chance; radius 16; same damage; no recursion (`spreadProcessing`)
- Shortbow: damage from projectile PDC `ItemKeys.damage()`

Boss mult (separate, `ProgressionEffects.onHit`):  
`boss *= skills.bossBonus` (Boss Grudge +10%×skill mult; Floor Grudge +12%× in dungeon); compacted diamond sword ×1.25 vs bosses.

### Mob → player — `DamageListener.onIncomingDamage`
- Skip if `trueDamage` PDC or `IncomingHits.isRaw()` (Core) or VOID/KILL/SUICIDE/WORLD_BORDER/STARVATION
- `defense = getStat(DEFENSE) * xpBoost.defenseMultiplier` (1.03 if flask)
- Borderlands: `defense *= (1 - WildlifeLooks.armorPenetration(living))`
- **Formula:** `damage *= 100 / (100 + defense * 0.85)`
- Undead resist: `damage *= 1 - min(70, UNDEAD_RESIST)/100`

Comments cite ~Combat III (~280 def) ≈ 30% incoming; Combat V (~1120) ≈ 10%.

---

## 5. Boosters

### Types — `BoosterType`
Core: COAL, IRON, GOLD, DIAMOND → flat to Mining Power / Fortune / Damage / Defense (if profile has them)  
Special: EMERALD→Spread, REDSTONE→Attack Spread, LAPIS→Health, GLOWSTONE→Speed, WHEAT→Catch Rate, CARROT→Harvest, OAK→Crit Damage, BIRCH→Crit Chance

### Values — `BoosterStats.getCoreFlat` / `getSpecialStat`
Core flats by rarity (Coal Common 0.8 → Diamond Mythic 9.0). Specials flat by rarity (many Common 3 → Mythic 15; Glowstone/Wheat/Birch smaller).

### Apply — `BoosterApplier.apply` / `remove`
Mutates `ItemStats` + PDC + lore. Cap: `BoosterLimits.MAX_TOTAL` = `BalanceTargets.BOOSTER_MAX_TOTAL` = **14**.

Sockets: `BoosterSockets` + locked GUI `BoosterSocketMenu` (14 sockets).

---

## 6. Level / XP systems

### Account level — `AetherionLevel`
`.../skill/AetherionLevel.java`

| Constant | Value |
|---|---|
| `MAX_LEVEL` | 5000 |
| `XP_PER_LEVEL` | 100 (flat) |
| `SKILL_XP_DIVISOR` | 40 (skill XP → account XP) |
| `STAT_EVERY` | 5 → `milestoneStatBonus = level/5` **+1 Damage and +1 Health** |

Titles at marks 25…5000 (`title` / `rankGroup` / `color`).  
`SkillService.accountXp` = `bonusXp` (1:1 pets/quests/…) + `fromSkillXp(sum spent skill XP + current)`.

### Skill levels — `SkillProgression` + `SkillService` + `AetherSkill`
- Skills 1–100; XP curve `xpToNext`; effect curve `effectMultiplier` (~1.0@1 → ~6.5@100 with rarity)
- 7 loadout slots; unlock by lifetime coins `COIN_UNLOCK = {0, 5k, 25k, 80k, 200k, 500k, 1.25M}`
- Presets unlock at account 1 / 10 / 25
- Categories: COMBAT, MINING, FORAGING, FARMING, FISHING, UTILITY, DUNGEON
- Dungeon skills only while in dungeon; isle-gated flags (Soil/Lake/Bedrock)
- Persist: `plugins/AetherionItems/skills.yml`
- Combat XP grant path: equipped skills of matching category (utility half); scaled by `XpBoosterService.scale`

### Unlock flags (not combat stats) — `ProgressionService`
`progress.yml`: TRADER, WORKBENCH, ANVIL, SPAWN_UNLOCKER, SKILLS, PETS  
Island at account **20**, Guild at **75**.  
Core bridge: `ProgressAccess` (`AetherionCore/.../api/ProgressAccess.java`) — flush/reload/unlocks only.

### Codex perks — `CodexPerks`
Every 10 Collection levels → +1 Fortune; every 10 Bestiary → +1 Damage +2 Health. Toggle `codex-perks` in Items config.

### Dungeon gear levels — `DungeonGearProgress`
Max 100; `LEVELS_PER_CORE = 25`; `dungeonLevelMultiplier = 1 + 0.01*level`; cores `DungeonCore.dungeonStatMultiplier` tier1/2/3 → 1.08 / 1.22 / 1.40.

### Comfortable stack bands (design doc) — `BalanceTargets`
Combat Damage **320** · Health **560** · Defense **420** (endgame comfortable full stack). Stacking budget: set 40% / pad 12% / skills 18% / boosters 18% / pet 12%.

---

## 7. Multipliers / booster interactions (summary)

| Source | What it modifies |
|---|---|
| Core boosters | Flat onto item MP/Fortune/Damage/Defense |
| Special boosters | Flat onto special stats on item |
| Skills (equipped) | Flat via `skill.bonus × effectMultiplier` into aggregation |
| Account milestones | Flat +1 DMG/+1 HP per 5 levels |
| Codex | Flat Fortune / Damage / Health |
| Gear set / dungeon calling | Flat extras |
| Pet | Flat (or % mult for percent pets) |
| Hollow Sun | Mult on aggregated DAMAGE/DEFENSE |
| XP flask (`XpBoosterService`) | XP×1.05, outgoing dmg×1.04, defense×1.03, coins×1.05 |
| Dungeon native gear | ×0.38 overworld; in-dungeon cores+level |
| Crit | Multiplies outgoing after base DAMAGE sum |
| Undead / boss / weapons | Post-aggregation damage scales |

Aggregation order: **(gear flats + provider flats) × product(provider multipliers)**. Flask damage/defense apply in `DamageListener` on top of that for hits.

---

## 8. AetherionCore relevance

- `ProgressAccess` / `AetherServices.progress()` — unlocks & transfer sync, not stats  
- `de.aetherion.core.combat.IncomingHits` — raw/unmitigated incoming (bypasses defense)  
- `AetherEntities` — pet/boss identity used by damage/spread filters  
- No player Attribute/stat calculator in Core

---

## Key method index

`ActiveEquipmentStats.getStat` · `ItemManager.getStat` / `saveItemStats` · `BoosterApplier.apply` · `DamageListener.onEntityDamage` / `onIncomingDamage` · `HealthListener.updateHealth` / `updateSpeed` · `SkillService.getStat` / `accountLevel` / `accountXp` / `addXp` · `AetherionLevel.milestoneStatBonus` · `SkillProgression.effectMultiplier` · `XpBoosterService.damageMultiplier` / `defenseMultiplier` / `scale`