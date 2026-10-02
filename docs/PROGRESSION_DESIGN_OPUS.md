# AETHERION — Progression Design Pass (Opus)

Date: 2026-10-01
Input: `docs/AETHERION_PROGRESSION_SCOUT.md` + `docs/_scout_raw/*`, verified against code.
Mode: **design only.** No code or YAML was changed in this pass.
Rules honoured: `.cursor/rules/locked-systems.mdc`, `.cursor/rules/additive-ship-never-overwrite.mdc`, Core stays thin.

**Code base used for verification**

- GitHub `main` @ `1922f36`
- plus the local F1 dungeon patch (`_dungeon_f1_ship/patches/dungeon-f1-vs-1922f36.patch`)
- plus `_f1_rooms_gear` (DungeonArmor, DungeonCalling, DungeonGearProgress, DungeonIdentifyMenu, DungeonWeaponKind, ItemLootBridge, …)
- plus local `AetherionDungeons/.../instance/{DungeonLootFx, DungeonChestProps, AshesEncounter}.java`
- Not readable from the cloud session: the worktree's local edits to `ActiveEquipmentStats.java` (~+560 B), `HealthListener.java` and `CustomItem.java`. All numbers below assume `main`'s aggregation. **Re-check `ActiveEquipmentStats` before you implement §3.4.**

---

## 0. TL;DR

1. **The scout is mostly right, but 9 of its claims are wrong or incomplete** (§1). The three that change the design most:
   - **Silas is a coin sink.** He sells boss uniques at 8×. He does not buy them.
   - **The T2/T3 dungeon relic tier has no live drop source.** It exists only in the DevMenu.
   - **Boosters are the biggest damage source from Mid onward.** They scale with the *target item's* rarity, and on combat armour they add Damage *and* Defense.
2. **The curve is fine through Mid and explodes after it.** Effective hit vs bosses goes 78 → 393 → 1,792 → 3,897 (Early → Mid → Late → End). Today a Late kit kills a 120k spectacle boss solo in about 1½ minutes (model, §3.1).
3. **The spine of this design** is that gathering makes *Signatures*, combat makes *Trophies* and dungeons make *Cores*. From T4 up, every ladder needs one of the other two (§2).
4. **Recipe deltas touch T4/T5 only.** Each piece gets +1 neighbour Signature, and each T5 tool gets +1 Dungeon Core. T1–T3 stay single-domain, so nothing gets grindier early (§4).
5. **Boss loot:** add spectacle sets to the Dungeon Core infusion list, add one strong recipe use per boss where the story fits, and add one pinnacle key that needs every system (§5).
6. **Dungeon bridge:** restore the T2/T3 relic drops, give relic gear a milder overworld multiplier than F1, keep F1 at ×0.38 and label floors by stage (§6).
7. **Six decisions only Robb can make** are listed in §8. One of them is a `LOCKED BOOSTER SYSTEM CONFLICT`.

---

## 1. Scout corrections (verified in code)

| # | Scout says | Code says | Why it matters |
|---|-----------|-----------|----------------|
| C1 | "Silas (boss fence) 8× listed" is a **sell channel** that "can dominate the early coin curve" | `FenceService` **sells** boss uniques to players at `listed × 8` (`MARKUP = 8`). An item unlocks after ≥1 kill (`codex.bossKills > 0`). | It is a **coin sink** and a pity path. That is a strength, not a risk. |
| C2 | Account level 5000 at a flat 100 XP is an "infinite dilute grind" | Account XP = Σ(all 60 skills' XP) / 40 + bonus XP. One skill at 100 = 346,768 XP → 8,669 account XP. **All 60 skills at 100 ≈ L5201.** | Account level is a **breadth meter** with a natural ceiling, which is exactly what the philosophy wants. Keep it and say so in the UI. |
| C3 | Dungeon gear is "crippled overworld (×0.38)" | ×0.38 applies **only to native gear**: Floor-I attuned callings, T1 dungeon weapons and schematic weapons (`DungeonArmor.isNativeDungeonGear`). Relic-identified **T2/T3 gear has no penalty**. A T3 Maul does 105 Damage (T5 sword: 88). A T3 Tank set gives 180 Defense (T5 combat set: 172). | The split is narrower than reported, but see C4. |
| C4 | F2/F3 give "gear upgrades via cores (T2 … `dungeon_relic_t2_*`)" | **No drop source for `dungeon_relic_t2/_t3`.** `DungeonLootFx.rollCombat/rollVictory` never rolls them, and the dungeon boss YAMLs have `items: []`. BossEngine already maps `random_dungeon_relic_t2/_t3` (`AetherionItemHook`), but no YAML uses those ids. | A whole gear tier is unreachable. **This is the real cause of the "dungeon orphan" feeling.** |
| C5 | `createRandomWorldhide/Helios/SeraphineArmor`: "no loot hooks" | They are hooked through BossEngine's reflection fallback (`random_worldhide_armor` → `createRandomWorldhideArmor()`). `world_eater`, `helios_requiem` and `hanging_saint` drop them. | Not dead. |
| C6 | Hollow Sun: "loot uneven" | The **Hollow Sun set has no drop path** (DevMenu only). Set-alone it gives 440 Defense / 605 HP, which is more than the whole comfortable band, plus DMG ×1.10–1.15 / DEF ×1.08 multipliers. The boss drops boosters, a star, glowstone and crying obsidian. | It is an orphan, and an outlier if it ever ships. Decision D2. |
| C7 | Boosters are "midgame pads" with an 18% budget | Booster value = `BoosterStats(type, rarity of the item being boosted)`. Core boosters add to **every** core capability the item has, so on combat armour one booster gives Damage **and** Defense. `BalanceTargets`' own "typical load" on a T5 (Mythic) combat set = **+228 Damage / +138 Defense**. That is 71% of the 320 band, not 18%. Recipes are cheap: `diamond_booster` = 9 diamond blocks. | Boosters are the main damage spine from Mid on (Mid: 96 from boosters vs 57 from the set). They are LOCKED, so the curve has to be designed around them. |
| C8 | "Combat ↔ Gathering weak" | Combat T4/T5 already consumes compacted raw_gold/bone and diamond/emerald. Those materials are **fungible**, though: an L7 diamond quarry makes ≈26 diamonds/10 s (≈1 compacted per 1.75 h, AFK). | There is a coupling through *materials*, not through *playing*. Signatures (§4) fix that. |
| C9 | (not mentioned) | Skill XP goes to **every** equipped skill of the category, and account XP sums all skills. Seven equipped mining skills = 7× account XP from mining. | One activity can carry you to Guild (75). See optional lever L6. |

Smaller confirmations and notes:

- `the_veil` (description: "Challenge Aetherion **in the dungeon**") kills the template `aetherion` (200k, open world) and pays T1-hunt rewards. The dungeon boss is `dungeon_aetherion`. Either the id or the description is wrong.
- `walking_mountain` (Colossus, 38k) is registered with the T1 helper.
- `aether_colossus` (38k, Mid) drops **T1** `combat_sword` at 100% and T1 `combat_chestplate` at 35%. Those are souvenirs.
- `ashen_sheath` has 5.2k HP (Entry band) and drops Blossom Blade (86 Damage, T5 class, LOCKED). Its HP is a mismatch; see D5.
- `NETHER_STAR` drops from Colossus, World Eater, Helios Requiem, Hollow Sun, Insolvent and Balthazar. It is used in **zero** recipes.
- BossEngine YAML bosses have **no per-player HP scaling**. Only `helios.yml` has it (`health-per-extra-player: 0.45`).
- `DungeonCore.INFUSABLE` already lets boss uniques (warped_blade, gravwell_cleaver, aetherblade, T2 uniques, aetherion set …) take cores and level in dungeons. **That is a real Overworld → Dungeon bridge** the scout missed.
- `IsleHeartwood` says in its own Javadoc: *"hooks for rituals / quests / crafts later."* §4 uses that hook.

---

## 2. Design spine

> **Gathering earns Signatures. Combat earns Trophies. Dungeons earn Cores.
> From T4 up, every ladder needs one of the others.**

| Source | What it is (already in code) | Why it is the right currency for links |
|--------|------------------------------|----------------------------------------|
| **Signatures** (gathering) | Heartwood (`IsleHeartwood`, ~3% per paid log on the isle), Crystal Finds (`CrystalFinds`, 0.18% per hand strike), Prize Crops (`PrizeCrops`, 0.22% per hand harvest), Fish Trophies (`Trophies`, rare/legendary or ≥90% weight) | Isle-only and earned by hand. **Quarries cannot make them.** They are tradeable, so playing *or* trading both work. |
| **Trophies** (combat) | Boss uniques plus a new tagged `spectacle_star` (replaces the unused NETHER_STAR drops) | Boss loot gets a downstream purpose. |
| **Cores** (dungeons) | `dungeon_core` / `_2` / `_3` (F1–F3 chests) | Cores already infuse boss gear. Here they also become the T5-tool reagent, so dungeons feed every ladder. |

Rules:

1. **T1–T3 stay single-domain.** Early game is not touched.
2. **T4:** +1 *neighbour* Signature per piece.
3. **T5:** +1 *second-neighbour* Signature per piece. The T5 **tool** also takes +1 `dungeon_core`, and `combat_sword_5` takes +1 boss Trophy.
4. **The pinnacle key** needs one of each Signature, one Core III and one Spectacle Star.
5. **No new currency, stat, station or plugin.** New items are limited to `spectacle_star` and Items ids stamped onto the four existing Signature finds.
6. **Nothing is hard-gated behind a skill level.** A Signature can be bought or traded. The goal is: "I couldn't get here on *one* activity alone", not "I was forced to grind fishing".

---

## 3. Deliverable 1 — One power curve

### 3.1 What the code produces today (snapshots)

Model: `ActiveEquipmentStats` sum → crit `×(1 + CC·CD)` → boss multiplier (`ProgressionEffects` + `SkillService.bossBonus`). Combat focus. Pet values from `PetBalance`. Skill values = `bonus × SkillProgression.effectMultiplier`.

| Stage | Kit (all real ids / values) |
|-------|-----------------------------|
| **Early** (acct ~18) | Combat T2 set + `combat_sword_2` (RARE). 4× Gold booster on the sword. Heavy Hands L20, Mean Streak L15. Uncommon DMG pet L25. |
| **Mid** (acct ~60) | Combat T3 set (EPIC). `charm_combat_2`. Sword 8× Diamond, chest 4× Gold, other armour 2× Gold each. HH50, LW40, MS40, SI40, TS40. Rare pet L50. Boss Grudge L40. |
| **Late-entry** (acct ~120) | Combat T4 set (LEGENDARY). `charm_combat_3`. Typical load. Six combat skills ~60. Epic pet L80. Boss Grudge 60. |
| **Late** (acct ~250) | Combat T5 set (MYTHIC). `charm_combat_3`. Typical load (10 Diamond sword, 6 Diamond chest, 4 Gold per other piece). Six combat skills 75–80. Legendary pet L100. Boss Grudge 75. |
| **End** (acct ~500) | As Late, with skills at 100, a Mythic dragon pet and Boss Grudge 100. |

| Stage | Set | Boosters | Skills | Charm | Pet | Acct | **Damage** | CC / CD | crit × | boss × | **Eff. hit vs boss** | Defense (dmg taken) | HP |
|------|----:|----:|----:|----:|----:|----:|----:|----|----:|----:|----:|----|----:|
| Early | 33 | 14 | 8 | 0 | 10 | 3 | **68** | 18% / 82% | 1.15 | 1.00 | **78** | 38 (76%) | 91 |
| Mid | 57 | 96 | 17 | 26 | 17 | 12 | **224** | 34% / 152% | 1.52 | 1.16 | **393** | 124 (49%) | 148 |
| Late-entry | 93 | 186 | 24 | 42 | 27 | 24 | **396** | 49% / 210% | 2.02 | 1.22 | **974** | 237 (33%) | 236 |
| Late | 144 | 228 | 34 | 42 | 38 | 50 | **536** | 62% / 260% | 2.60 | 1.28 | **1,792** | 333 (26%) | 320 |
| End | 144 | 228 | 67 | 42 | 40 | 100 | **620** | 94% / 297% | 3.81 | 1.65 | **3,897** | 362 (25%) | 407 |

**Solo time-to-kill estimate.** Assumes 1.5 hits/s sustained (Aetherion damage ignores attack charge; vanilla i-frames cap it near 2/s) and damage uptime of 0.8 (Entry), 0.7 (Mid), 0.6 (High) and 0.45 (Spectacle/Pinnacle, because of scripted shield windows).

| Kit \ Boss band | Entry 3–6k | Mid 11–38k | High 45–60k | Spectacle 90–145k | Pinnacle 200k |
|---|---:|---:|---:|---:|---:|
| Early | 48 s | 5 min | 12 min | — | — |
| Mid | 10 s | 61 s | 2.5 min | 7.5 min | 12.5 min |
| Late | 2 s | 13 s | 32 s | **99 s** | 2.8 min |
| End | 1 s | 6 s | 15 s | **46 s** | **76 s** |

There is no YAML party scaling, so a party of N divides these times by about N.

### 3.2 Where the inflation comes from (ranked)

1. **Boosters at item rarity, double-dipping on armour** (C7). +228 Damage at Late. Locked.
2. **Crit near the cap.** At End, Mean Streak (32.5%) and Last Word (26%) @100 sit on top of 36% CC from sword, chest and charm, giving ~94% CC. CD reaches ~297%, for **×3.8**.
3. **Boss Grudge scales with `effectMultiplier`.** +65% vs bosses at L100. Floor Grudge adds another +78% inside dungeons, for **×2.43** on dungeon bosses.
4. **Account milestone** +1 DMG / +1 HP per 5 levels, uncapped. +100 at L500, **+1,000 at L5000**.
5. Boss HP bands were authored against the REV6 comfort band (320 Damage), which does not exist in play.

Points 1–3 multiply each other. That is why Mid→Late is ×4.6 while Early→Mid is ×5 but feels fine: Mid content was also tuned for it.

### 3.3 Proposed authored curve (targets)

| | **Early** | **Mid** | **Late** | **End** |
|---|---|---|---|---|
| Enter at | Tutorial graduation (`lesson_boost`, `lesson_manager`, `farm_hand`, `pocket_zoo`) | Account **20** (island claim) | Account **75** (guild) **and** first T4 craft (needs a Signature) | First T5 tool (needs a Signature + Core) or the first spectacle-set piece |
| Kit | T1–T2 ladders, 1–3 skills, first pet, boosters on T2 | T3 ladders + compacted bridge items, 4–6 skills at 40–60, rare/epic pet, charm T2, F1 calling set + Core I | T4→T5, charm T3, skills 60–80, legendary pet, Core II, first boss unique | Skills 100 in the main role + breadth (acct 300+), mythic pet, boss set, Core III |
| Target eff. hit vs boss | ~80 | ~400 | ~1,000 → 1,650 | **~2,400** (today 3,900) |
| "Home" bosses | Entry: spirit-vial T1s, McNugget, Troll, Lurker, Skuldugery | Mid: Squidward, Colossus, Pathwarden (Colosseum), F1 | High: World Eater, Helios, T2 hunts; F2 | Spectacle: Seraphine, Hollow Sun, Sparky, Baron, Insolvent; F3; Pinnacle `aetherion` |
| Target TTK at home | 40–75 s solo | 60–120 s solo | 2–3 min duo | Spectacle 3–5 min (party of 3), Pinnacle 5–8 min (party of 4) |

Stage steps: ×5 → ×2.5–4 → ×1.5. That gives noticeable early steps and a flattening tail.

### 3.4 Levers to land it (all outside LOCKED systems)

| ID | Lever | Where | Effect on eff. hit (Early / Mid / Late / End) |
|----|-------|-------|------|
| **L1** | Re-document the comfort band as **per-stat focus** and state the booster reality. REV7 band: Combat Damage ~540, Defense ~420, HP ~560. Doc comment only. | `BalanceTargets` Javadoc | 0 |
| **L2** | **Crit-chance soft cap:** CC above 50% counts half (`eff = 50 + (CC−50)/2`). | `DamageListener` crit roll (~L421) | 0 / 0 / −6% / −17% |
| **L3** | **Flatten the Grudge skills:** Boss Grudge `0.10 + 0.20·(L−1)/99` (10% → 30%); Floor Grudge `0.12 + 0.24·(L−1)/99`. | `SkillService.bossBonus` | 0 / +2% / −3% / −21% |
| **L4** | **Taper the account milestone:** +1/+1 per 5 levels up to L250, then per 25 levels. Gives +60 at L500 and +240 at L5000. | `AetherionLevel.milestoneStatBonus` | 0 / 0 / 0 / −6% |
| **L5** | **Re-band boss HP to TTK targets** (YAML only, *after* telemetry): Entry keep; Mid low end ×2 (Squidward 11k → ~25k); High ×3; Spectacle ×5; Pinnacle ×8. Formula: `HP = effHit_home × 1.5 × uptime × players × TTK`. | `BossEngine/.../bosses/*.yml` | — |
| **L5b** | Add `scaling.health-per-extra-player` to generic YAML templates, reusing the `helios.yml` pattern (0.45). | BossEngine template loader | — |
| **L6** *(optional)* | **Breadth gate for Guild:** account ≥75 **and** ≥2 skill categories with a skill ≥25. | `ProgressionService` guild check | — |
| **L7** *(UX)* | **Stage banner:** show "Stage: Early/Mid/Late/End" in `StatsOverviewGUI` and the TAB hover, using the §3.3 entry rules. | Items GUI | — |

Combined L2+L3+L4 give eff. hit **78 / 401 / 1,643 / 2,374**. Early to Late stays as it is, and End is cut by 39%.

**Telemetry first (prerequisite for L5).** `BossInstance.getDamageTracker()` already exists. On `BossDeathEvent`, log fight seconds, party size and top-DPS share into `BossJournal`/Codex. Tune HP from real data, not from this model.

---

## 4. Deliverable 2 — Recipe delta list

Only changes that create organic cross-skill dependencies. **T4/T5 only.**

### 4.1 Enabling change: Signature item ids

`CraftingMatcher` matches custom ingredients by **Aetherion item id**. Three of the four Signatures are only PDC-tagged by their gather plugin today, so stamp an Items id onto each **when it is created**. Keep the existing PDC (grade, carat, kg) for value and lore.

| Signature | Created by | Id to stamp | Notes |
|-----------|-----------|-------------|-------|
| Heartwood | `IsleHeartwood` (Items) | `isle_heartwood_<wood>` (exists) | Nine woods; recipes name one wood each |
| Crystal Find | `AetherionMining/isle/CrystalFinds` | `crystal_find` (grade/ore stay in PDC) | Any grade matches; Heartstone stays a collector item |
| Prize Crop | `AetherionFarming/isle/PrizeCrops` | `prize_crop` | Any crop/weight matches |
| Fish Trophy | `AetherionFishing/isle/Trophies` | `fish_trophy` | Any species matches; heads stay unplaceable |

Route the stamping through the Core `ItemFactoryAccess` (Items owns ids, per `OWNERSHIP.md`). Add the four ids to `economy.yml` (value = the current NPC buy price) and to the Codex collection.

### 4.2 Neighbour map

Each ladder touches **two** other domains across T4 → T5. Story first.

| Ladder | T4 needs (neighbour) | T5 needs (second neighbour) | T5 tool/weapon extra |
|--------|---------------------|-----------------------------|----------------------|
| **Combat** | Foraging: `isle_heartwood_dark_oak` (hilts, straps) | Fishing: `fish_trophy` (scale lining) | `combat_sword_5`: +1 `warped_blade` (§5) |
| **Mining** | Foraging: `isle_heartwood_oak` (hafts, pit props) | Farming: `prize_crop` (miners' rations) | `mining_pickaxe_5`: +1 `dungeon_core` |
| **Foraging** | Mining: `crystal_find` (whetstone edge) | Fishing: `fish_trophy` (isinglass glue for laminated hafts) | `foraging_axe_5`: +1 `dungeon_core` |
| **Farming** | Fishing: `fish_trophy` (fish-meal fertiliser) | Mining: `crystal_find` (mineral dust) | `farming_hoe_5`: +1 `dungeon_core` |
| **Fishing** | Farming: `prize_crop` (champion bait) | Foraging: `isle_heartwood_bamboo` (rod blank) | `fishing_rod_5`: +1 `dungeon_core` |

Demand per Signature: Heartwood ×3 ladders, Trophy ×3, Crystal ×2, Prize ×2. That is balanced enough for a bazaar to form.

### 4.3 Recipe changes

Two small helper variants in `RecipeRegistry`. The existing helpers stay for everything else.

- `registerLadderCrossSig(id, cat, result, rarity, center, first, second, sig)` — shape `"ASB", " C ", "B A"` (the +1 Signature sits in the empty top slot).
- `registerLadderPeakSig(id, cat, result, rarity, center, premium, support, sig[, extra])` — shape `"XS ", "SCS", "EP "` (`X` = Signature; `E` = optional extra: Core or Trophy).

| Recipe ids | Today | Proposed delta |
|-----------|-------|----------------|
| `combat_{helmet,chestplate,leggings,boots,sword}_4` | Cross: prev + 2× `compacted_raw_gold` + 2× `compacted_bone` | **+1 `isle_heartwood_dark_oak`** |
| `combat_{helmet,chestplate,leggings,boots}_5` | Peak: prev + 1× `compacted_diamond` + 3× `compacted_emerald` | **+1 `fish_trophy`** |
| `combat_sword_5` | same | **+1 `fish_trophy` +1 `warped_blade`** |
| `mining_{helmet,chestplate,leggings,boots,pickaxe}_4` | Cross: prev + `compacted_raw_copper` + `compacted_coal` | **+1 `isle_heartwood_oak`** |
| `mining_{…armour}_5` | Peak: prev + `compacted_diamond` + `compacted_redstone` | **+1 `prize_crop`** |
| `mining_pickaxe_5` | same | **+1 `prize_crop` +1 `dungeon_core`** |
| `mining_pickaxe_5_burrower` *(new alt)* | — | Center = `pickaxe_core_of_the_burrower` **instead of** `mining_pickaxe_4`, otherwise the same as `mining_pickaxe_5`. A boss drop as a *shortcut*, not a tax. |
| `foraging_*` T3 → T4 crafts *(cross)* | prev + `compacted_dark_oak_log` + `compacted_mangrove_log` | **+1 `crystal_find`** |
| `foraging_*` T4 → T5 crafts *(peak)* | prev + `compacted_cherry_log` + `compacted_bamboo_block` | **+1 `fish_trophy`**; axe also **+1 `dungeon_core`** |
| `farming_*` T3 → T4 crafts *(cross)* | prev + `compacted_sugar_cane` + `compacted_carrot`/`wheat` | **+1 `fish_trophy`** |
| `farming_*` T4 → T5 crafts *(peak)* | prev + `compacted_nether_wart` + `compacted_potato`/`wheat` | **+1 `crystal_find`**; hoe also **+1 `dungeon_core`** |
| `fishing_*` T3 → T4 crafts *(cross)* | prev + `compacted_prismarine_shard` + `compacted_salmon` | **+1 `prize_crop`** |
| `fishing_*` T4 → T5 crafts *(peak)* | prev + `compacted_prismarine_shard` + `compacted_pufferfish` | **+1 `isle_heartwood_bamboo`**; rod also **+1 `dungeon_core`** |
| `diving_{helmet,chestplate,leggings,boots}` *(new, fixes an orphan)* | factory + value, **no recipe** | Plus shape: `fishing_<piece>_3` + 2× `compressed_prismarine_shard` + 1× `compressed_pufferfish` + **1× `fish_trophy`**. Requirement: `CraftedPredecessorRequirement("fishing_<piece>_3")`. |

> The domain ladders (farming/foraging/fishing) register their ids through `farming.helmet(n)` etc., with T2 at `(1)` as the predecessor. Keep that indexing as it is. Their rarity ladder starts at UNCOMMON.

**Volume check.** Per ladder: T4 set = 5 Signatures, T5 set = 5 Signatures + 1 Core. At current rates (heartwood ~3% per paid log; crystal ~1 per ~550 strikes; prize ~1 per 450 hand harvests; trophies most common) that is roughly **1–2 h of neighbour-isle play per tier, or a trade.** Tune amounts after one week of Codex collection data.

**Housekeeping:** recompute `economy.yml` listed values for every changed result (`EconomyCurve`: ingredientSum × tier margin). Add the new ingredients to the recipe-book GUI lore ("needs a Signature from Eldervale").

### 4.4 Teaching quests (optional, `QuestRegistry`, after the tutorial)

One short quest per Signature, offered when the player first crafts any T3. Each asks for 1 Signature at the relevant isle NPC (Tamsin Holt / Brann Emberlock / Hattie / Odile), pays the T3 → T4 hint plus a small reward, and sets the compass to the neighbour isle. This fills the "quest curriculum between T1 and T2 bosses" gap without a new quest type. *Verify that `DELIVER` matches Aetherion item ids; if not, use a TALK objective plus a held-item check.*

---

## 5. Deliverable 3 — Boss loot sink table

Today's sinks (all exist): equip · `DungeonCore` infusion (INFUSABLE list) · Silas (buy at 8× after the first kill) · Gear Trader 32% · Liquidator.

| Boss (HP) | Unique(s) | Today | Proposed downstream use | Change type |
|-----------|-----------|-------|------------------------|-------------|
| `mcnugget` (3k) | `compressed_coal` | material | keep | — |
| `hollow_lurker` (3k) | `warped_blade` (40/16/6%), 32 diamond blocks | equip, infuse, Silas | **+ ingredient of `combat_sword_5`** (common drop, story: reforge the edge) | recipe |
| `bridge_troll` (3k) | `bridged_axe` 12% | equip, infuse, Silas | keep (weapon with lifesteal) | — |
| `skuldugery` (3k) | `skuldugery_shortbow` 15%, `catch_sphere_epic` | equip, infuse, Silas | keep | — |
| `squidward` (11k) | `squids_boot` 12% | equip, infuse, Silas | keep; HP → ~25k (L5) | YAML |
| `ashen_sheath` (5.2k) | `ashen_katana` **LOCKED** | equip | none; HP question → D5 | — |
| `pathwarden` (52k) | `gravwell_cleaver` **LOCKED** | equip, infuse (already listed), Silas | none | — |
| `aether_colossus` (38k) | **T1** `combat_sword` 100%, **T1** `combat_chestplate` 35%, NETHER_STAR | souvenir | Replace T1 pieces with **`compacted_bone` ×1 (100%)** and **`compacted_raw_gold` ×1 (35%)**, which feed combat T4. NETHER_STAR → `spectacle_star`. | YAML |
| `world_eater_unbroken` (12k) | boosters | Act I | keep | — |
| `world_eater` (45k) | `worldbite`, Worldhide set | equip | **+ add to `DungeonCore.INFUSABLE`** (`worldbite`, `worldhide_*`) | Items list |
| `helios_herald` (26k) / `helios_requiem` (52k) | `helios_solstice`, Helios set | equip | **+ INFUSABLE** (`helios_solstice`, `helios_*`); NETHER_STAR → `spectacle_star` | Items list, YAML |
| `hanging_saint` (120k) | `seraphine_needle`, Seraphine set | equip | **+ INFUSABLE** (`seraphine_*`) | Items list |
| `hollow_sun` (120k) | boosters, star, glowstone | souvenir-ish | Decision **D2**. Recommended: normalise the set to one step above Helios in `BossGearBalance` (~255 Def / ~335 HP set-alone) and add `random_hollow_sun_armor` rank-loot (1.0 / 0.7 / 0.35); star → `spectacle_star` | Items + YAML |
| `eggquelizer` (60k) | vanilla placeholders | — | Interim: `spectacle_star` ×1, `random_booster` ×2, `compacted_*` ×1. A real unique follows `OPUS_EGGQUELIZER_HANDOFF.md` | YAML |
| `sir_balthazar`, `lobby_cleaner`, `sparky`, `baron_von_wurm`, `insolvent_wither` (115–145k) | staff, vacuum charm, thermal core, burrower core, ledger (34% killer / 18% rank) | equip, infuse, Silas | Burrower core → **`mining_pickaxe_5_burrower`** shortcut (§4). The others stay equip/infuse/Silas. NETHER_STAR (Balthazar, Insolvent) → `spectacle_star` | recipe, YAML |
| `aetherion` (200k, open) | Aetherion set (1.0/0.7/0.15), void stick 10% | equip, infuse | **Pinnacle key** (below); fix `the_veil` | recipe, Quests |
| `dungeon_sentinel` / `_frostbound` / `_aetherion` | empty — chests pay | — | T2/T3 relic rolls (§6) | Dungeons |

### `spectacle_star` (the only new item)

A tagged Items item with no stats. Lore: "Pried from a spectacle. Something wants it back." It replaces every `NETHER_STAR` boss drop. Tag it rather than using vanilla stars, so a vanilla Wither can never bypass the system.

**Uses** (kept deliberately short):

1. Pinnacle key
2. Optional Hollow Sun set craft, if D2 picks "craft" over "drop"

### Pinnacle key — `aetherion_summons`

- **Output:** the existing BossEngine SUMMON item `aetherion_core`, today marked "Test-Item". Craft it through Items and hand off via `BossEngineAPI`/spawn item, or have BossEngine accept an Items id. Implementation picks one; no new station.
- **Recipe:**

  | Row | Slots |
  |-----|-------|
  | 1 | `isle_heartwood_cherry` · `spectacle_star` · `crystal_find` |
  | 2 | `fish_trophy` · `dungeon_core_3` · `prize_crop` |
  | 3 | `compacted_diamond` · `compacted_emerald` · `compacted_diamond` |

- **What it proves:** every isle, a spectacle kill, F3 and the economy, which is the handoff's "I couldn't get here on one activity alone".
- **Soft prep stays mechanical, not a gate.** Show a "Recommended" block in the BossJournal entry: Defense ≥ 300, HP ≥ 300, Boss Grudge equipped, Undead Resist for undead bosses, Allay pet for drop chance. All of these are existing stats and effects.

---

## 6. Deliverable 4 — Dungeon bridge plan

Goal: keep the instance identity, end the "two characters" feeling.

| # | Change | Where | Notes |
|---|--------|-------|-------|
| **B1** | **Restore the T2/T3 relic drops.** F2/F3 victory: `random_dungeon_relic_t<floor>` at r 58–70 (taken from the core band 58–82, which leaves cores at 8–22 + 70–82 = 26%). F2/F3 combat chests: r 62–66 (taken from the compressed band 62–78). F1 victory: **2%** `random_dungeon_relic_t2` as a preview at r 56–58 (taken from the booster band 40–58). | `DungeonLootFx.rollVictory/rollCombat` + `ItemLootBridge.rollRelic(tier)` (BossEngine already maps the ids) | Data-level change. F2/F3 bosses keep `items: []` because chests pay, which is the current design. |
| **B2** | **Tiered overworld multiplier.** F1 native stays **×0.38**. Relic-identified T2 → **×0.60**, T3 → **×0.70** overworld; full power plus cores plus level inside. Implemented by stamping `dungeonNative` with a tier and reading the multiplier from `DungeonGearTier`. | `DungeonArmor.OVERWORLD_STAT_MULTIPLIER` → per tier | Without B2, B1 would ship a T3 Assassin set (137 Damage set-alone) that outclasses the T5 overworld ladder. With B2 it is about 96 Damage and no Defense: a build choice, not a replacement. |
| **B3** | **Boss sets level in dungeons** (Overworld → Dungeon). Extend `INFUSABLE` with Worldhide, Helios and Seraphine (§5). | `DungeonCore.INFUSABLE` | Skill ladders stay out on purpose (existing comment). |
| **B4** | **Dungeons feed every ladder** (Dungeon → Overworld). `dungeon_core` becomes a T5-tool reagent (§4). Core III goes into the pinnacle key. | Recipes | Today Grade-I cores come only from F1 victory chests (r 8–22 = 14%). F2/F3 give grade II/III (combat 18%, victory 38%). Four T5 tools = four Grade-I cores. If that turns out too slow, accept `dungeon_core` **or** `dungeon_core_2` through a second recipe id. |
| **B5** | **Floors labelled by stage.** `DungeonMenu`/`DungeonGuideGUI` show "Recommended: F1 Early–Mid · F2 Late (Acct 75+, T4) · F3 End". F2 stays a cliff for Mid on purpose: zombie TTK ~24 s at Mid vs ~10 s at Late-entry. The label turns a wall into a goal. | Dungeons GUI | UX only. No HP change until telemetry. |
| **B6** | **Floor Grudge** follows L3, which avoids ×2.43 on dungeon bosses at L100. | `SkillService` | — |

Result: F1 = learn plus vestiges; F2 = T2 relics (a real Late sidegrade); F3 = T3 relics plus Core III plus the Aetherion set. Boss gear levels in dungeons, and every T5 tool and the pinnacle key need a Core.

---

## 7. Deliverable 5 — Explicit non-goals

- No new currency, primary stat, crafting station/UI or progression plugin.
- **No change to boosters** (values, stack size, sockets, recipes, lore) — LOCKED. See D1.
- No change to Blossom Blade or Gravwell Cleaver abilities, VFX or feel, or to their drop tables — LOCKED.
- No change to Talk UX, ranks/wipes, `voided_455` MVP++ pity, Borderlands vials/rites, or the shutdown countdown — LOCKED/rank-adjacent.
- No changes to T1–T3 recipes. Early game stays single-domain.
- No hard skill-level gates on recipes or bosses. Signatures can always be traded.
- No removal of DevMenu pages, factories or test gear (the Hollow Sun set stays in DevMenu whatever D2 decides).
- No boss HP retune before telemetry (L5 waits for data).
- Hunter crossbow stub: leave as it is until it has a design; do not delete (additive rule).
- AetherionCore gets no stat logic. Signature stamping goes through the existing `ItemFactoryAccess`.

---

## 8. Decisions for Robb

| ID | Question | Recommendation |
|----|----------|----------------|
| **D1** | `LOCKED BOOSTER SYSTEM CONFLICT:` `BalanceTargets` budgets boosters at 18%, but the LOCKED booster values give ~43% of Late Damage (C7). Accept that boosters are the main stat spine (re-document, L1), or unlock boosters for a retune? | **Accept and re-document.** Tune crit, Grudge and boss HP instead. |
| **D2** | Hollow Sun set: (a) normalise to one step above Helios and drop it from `hollow_sun`, (b) craft it from `spectacle_star` + Hollow Sun drops, or (c) declare it test gear only. | **(a)** |
| **D3** | Account milestone stats: taper after L250 (L4) or keep +1 per 5 levels to 5000 (+1,000)? | **Taper.** Breadth already pays through titles and gates. |
| **D4** | Crit soft cap at 50% (L2): OK, or protect crit builds? | **OK.** Only affects late builds. |
| **D5** | `ashen_sheath` sits at 5.2k (Entry band) but drops a T5-class LOCKED weapon. Raise its HP (weapon untouched), or keep it as an intentional early jackpot? | Keep for now. Decide after telemetry. |
| **D6** | Guild breadth gate (L6): account 75 **and** 2 categories ≥25? | Optional. Ship §4 first and check whether it is still needed. |

---

## 9. Suggested implementation order

| Phase | Content | Risk |
|-------|---------|------|
| **P0 — data fixes** | Colossus loot (§5); `the_veil` id/description fix; NETHER_STAR → `spectacle_star` (item + YAML); INFUSABLE extension; telemetry logging on `BossDeathEvent` | Low (YAML plus small Items changes) |
| **P1 — Signatures** | Stamp ids (§4.1); `economy.yml` values; Codex entries | Low/medium (touches 3 gather plugins via Core API) |
| **P2 — Recipes** | Two new ladder helpers; T4/T5 deltas; `mining_pickaxe_5_burrower`; diving recipes; recipe-book lore | Medium (recipe tree; diff the DevMenu/recipe book against live per the additive rule) |
| **P3 — Dungeon bridge** | B1 relic rolls; B2 tiered overworld multiplier; B5 labels | Medium |
| **P4 — Curve levers** | L2, L3, L4 (after D3/D4) | Low code, high feel. Test in the arena first. |
| **P5 — Boss HP re-band** | L5/L5b from one week of telemetry | Medium |
| **P6 — Pinnacle key + teaching quests** | `aetherion_summons`; §4.4 | Medium |

Acceptance check for every phase:

- `StatsOverviewGUI` numbers match §3.1 for the reference kits (DevMenu test gear).
- No live DevMenu page or give-list is lost (jar-surface diff, `docs/tools/jar-surface-diff.ps1`).
- The recipe book shows every changed recipe with the Signature hint.

*End of design pass.*
