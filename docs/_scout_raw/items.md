# AetherionItems — Equipment / Crafting / Economy Scout

Base: `AetherionItems/`. No redesign — inventory only.

---

## 1. Authority map (paths)

| Concern | Path |
|--------|------|
| Main factories | `.../item/CustomItem.java` (~100 `create*`) |
| Ladder stats (live) | `.../item/BalanceTargets.java` (**REV 6**) + `StarterSetBalance.java` |
| Boss / set / signature stats | `.../item/BossGearBalance.java` |
| Midgame mat gear | `.../item/ProgressionItems.java` |
| Domain ladders | `FarmingItems.java`, `ForagingItems.java`, `FishingItems.java`, `CatcherItems.java`, `AccessoryItems.java` |
| Boosters (LOCKED stackable) | `.../item/BoosterItems.java` |
| Rarity | `.../model/Rarity.java` |
| Recipes (code-only) | `.../recipe/RecipeRegistry.java` (~228 literal + ~99 compression + 3 catch spheres) |
| Values | `src/main/resources/economy.yml` + `.../economy/ItemValueService.java` + `EconomyCurve.java` |
| Compression | `.../economy/CompressedResource.java` |
| Crystals / liquidator | `ShardService.java`, `LiquidatorService.java` |
| Shops | `shop/ShardShopMenu.java`, `GearTraderService`, `FishShopService`, `FenceService`, `TraderService`, `MarketService` |

**No recipe YAML/JSON.** Resources jar: `economy.yml`, `config.yml`, `plugin.yml` only.

---

## 2. Rarity tiers

`COMMON → UNCOMMON → RARE → EPIC → LEGENDARY → MYTHIC → AETHERED`  
(`AETHERED` = dragon ascension only.)

| Ladder T1 | T2 | T3 | T4 | T5 |
|-----------|----|----|----|-----|
| Combat / Mining | COMMON | RARE | EPIC | LEGENDARY | MYTHIC |
| Farm / Forage / Fish | **UNCOMMON** | RARE | EPIC | LEGENDARY | MYTHIC |

Dungeon gear tiers (`DungeonGearTier`): T1 RARE ×1.0, T2 EPIC ×1.6, T3 LEGENDARY ×2.25.

Unlisted custom gear AH fallback (`ItemValueService`): COMMON 2.5k → MYTHIC 2.2M.

---

## 3. Skill progression ladders (early→late)

Live stats from **`BalanceTargets`** at craft (`StarterSetBalance.ensureBase`). IDs: `{domain}_{piece}` then `_2`…`_5`.

### Combat — `combat_*` + `combat_sword`

| Tier | Sword Dmg/AS/CC/CD | Helm Def/HP/AS/Dmg | Chest Def/HP/AS/Dmg/CC/CD |
|------|--------------------:|-------------------:|--------------------------:|
| 1 | 14/3/5/42 | 4/8/2/1 | 8/12/3/2/4/25 |
| 2 | 22/5/6/46 | 7/14/3/2 | 14/22/5/4/6/36 |
| 3 | 36/8/8/62 | 12/24/5/4 | 24/38/8/8/8/52 |
| 4 | 56/12/11/80 | 20/36/8/7 | 38/56/12/14/11/72 |
| 5 | 88/18/15/102 | 34/46/12/10 | 58/78/18/22/14/94 |

Listed coins (sword): 4 → 16 → 1,061 → 569,456 → **2,781,570**.

Recipe mats: T2 iron+bone → T3 compressed bone/flesh → T4 compacted gold+bone → T5 compacted diamond+emerald.

### Mining — `mining_*` + `mining_pickaxe`

| Tier | Pick MP/Fort/Spread |
|------|--------------------:|
| 1–5 | 14/24/2 → 26/42/3 → 48/74/5 → 96/128/8 → **130/250/12** |

Bridge: `beginner_pickaxe` → `simple_pickaxe` → `mining_pickaxe`.

### Farming — display names T1–5

| Piece | Names |
|-------|--------|
| Cap | Furrow → Barnstorm → Haymaker → Threshlord → **Verdant Halo** |
| Hoe | Furrow Ledger → … → **The Verdant** (`farming_hoe_5`) |
| Hoe Fort/Harvest | 18/28 → … → **210/260** |

### Foraging

| Axe names | Kindling Hatchet → … → **The Worldroot** (`foraging_axe_5`) |
| Axe MP/Fort/Spread | 10/16/2 → … → **96/180/14** |

### Fishing

| Rod | Nibble → Ripple → Keelhaul → Abyssal → **The Leviathan** |
| Fort/FS/FC | 18/4/30 → … → **180/32/320** |

**Diving set** (`diving_helmet|chestplate|leggings|boots`): factories + `economy.yml` values — **no `RecipeRegistry` entries** (orphan craft path).

### Catcher T1–3 — `catcher_*` / `catcher_gaff[_2|_3]` (Aethermobs category)

### Charms T1–3 — `charm_{combat,mining,foraging,farming,fishing,utility}[_2|_3]`  
Plus `charm_shiny`, `charm_forge`, `charm_estate`. Pads from `BalanceTargets.CHARM_*`.

---

## 4. Material-progression bridge (`ProgressionItems`)

| ID | Role stats (hardcoded) |
|----|------------------------|
| `compressed_oak_chestplate` | Def 10, Fort 12, HP 8 |
| `compacted_timber_axe` | MP 32, Fort 50, Speed 5 |
| `compressed_stone_pickaxe` | MP 18, Fort 32, Spread 2 |
| `compacted_cobble_hammer` | MP 26, Fort 36, Spread 4 |
| `compacted_iron_pickaxe` | MP 48, Fort 74, Spread 5 |
| `compacted_diamond_pickaxe` | MP 96, Fort 128, Spread 8 |
| `voided_455` | MP **145**, Fort **270**, Spread 14 |
| `copper_sword` → `compressed_gold_sword` → `compacted_midas_dagger` → `compacted_diamond_sword` | Dmg 18→32→40→64 |
| `compacted_emerald_scythe` | Dmg 56, CC 12, CD 72 |
| `emerald_crown`, `lapis_pendant`, `compressed_coal_ring`, `redstone_infused_boots` | accessory/armor sidegrades |

All registered via `RecipeRegistry.registerMaterialProgression`.

---

## 5. Boss / special / signature (not recipe ladders)

Stats: `BossGearBalance.base(id)`.

| ID | Dmg/AS/CC/CD (weapons) |
|----|------------------------|
| `warped_blade` | 48/8/9/62 |
| `gravwell_cleaver` | **80/8/13/96** (LOCKED) |
| `ashen_katana` (Blossom Blade) | **86/14/15/105** (LOCKED) |
| `bridged_axe` | 80/12/13/90 |
| `aetherblade` | 100/16/16/115 |
| `worldbite` | 108/16/17/120 |
| `helios_solstice` | 114/16/18/126 |
| `seraphine_needle` | 84/10/14/100 |

Sets: Aetherion, Worldhide, Hollow Sun, Helios, Seraphine, Ironhide (+ mob: Rotten, Bone, Webweave, Healer).  
Dungeon: vestige / identify / cores I–III / relics T2–T3.

Blueprint tools: `vein_siphon`, `canopy_cleaver`, `bounty_hoe`, `wild_sight`, `tide_latch` (+ stones `_2/_3/_4`).

---

## 6. Crafting system

- **~330 runtime recipes**, all Java — no YAML defs.
- Categories: MINING, RESOURCES, COMBAT, ARMOR, FARMING, FORAGING, FISHING, BOOSTERS, CHARMS, AETHER_MOBS.
- Unlock: predecessor tiers, `MaterialObtainedRequirement`, island level (quarries), blueprint/hub gates.
- Crafting: vanilla 3×3 + recipe book; stations: Booster Socket `/av`, Blueprint Forge (Eldervale), Pocket Forge, Millstone, Root Cellar (offer crafts, not registry), guild quarry compressor/compactor.

**Boosters (12 + sack):** coal/iron/gold/diamond/emerald/redstone/lapis/glowstone/wheat/oak/birch/carrot — LOCKED stackable.

---

## 7. Resource processing

| Step | Ratio | Rarity | Value formula |
|------|------:|--------|---------------|
| Raw → compressed | **128:1** | UNCOMMON | `unit × 128 × 1.4` |
| Compressed → compacted | **128:1** | RARE | `compressed × 128 × 1.5` |
| Compacted → refined | 1:1 millstone | EPIC | `compacted × 4` (wheat/carrot/potato only) |
| Quarry placeables | — | — | listed **450** default |

~32 content keys (logs, ores, crops, drops…). `crimson_stem` / `warped_stem` disabled.

Example (cobble unit 1): compressed **179**, compacted **34,368**, quarry **450**.  
Diamond compressed **1,434**, compacted **275,328**.

---

## 8. Economy / shops / sinks

### Sell channels
| Channel | Rate |
|---------|------|
| Trader | **100%** listed (resources) |
| Gear Trader buyback | **32%** |
| Silas (boss fence) | **8×** listed |
| Bazaar | **50–300%** of base |
| Liquidator mats→crystal | **40,000** listed value / crystal |

### Crystal liquidator
Buy **20,000** coins / crystal · Sell **7,500** · Mat burn 40k value/crystal.

### Aether Shop (crystals)
Lesser Phial 120 · Blood Phial 500 · Estate charm 350 · refined wheat 80 · compacted iron 90 · compacted diamond 220 · 8× compressed diamond 45 · Hacker pet 450/850.

### Coin shops
Gear Trader: arrows 6, simple tools 12–22. Fishmonger: Nibble set ~14–28 + rod 24.

### Other sinks / gates
Skill slot lifetime-coin thresholds: 0 / 5k / 25k / 80k / 200k / 500k / **1.25M**. Casino bets 10–50k. Isle rerolls **250**. Mining Forge marks 2k–150k + mats.

### Notable listed drops (`economy.yml` `drops:`)
`dungeon_core` 350k · `gravwell_cleaver` 2.4M · `aetherblade` 4.5M · `god_pickaxe` 15M · `god2_pickaxe` 30M.

---

## 9. Obvious orphans / asymmetries

| Item | Issue |
|------|--------|
| `createHunterCrossbow` | Stub leather; no call sites |
| `diving_*` | Factory + economy; **no recipes** |
| `createRandomWorldhide/Helios/SeraphineArmor` | Defined; no loot hooks (Aetherion random is hooked) |
| `createRandomDungeonVestige` | Defined only |
| Signature Blossom/Gravwell | Intentional — no recipes |
| God / God2 kits | Command-only, not DevMenu weapons pages / recipes |
| Test arena gear | DevMenu/`TestGear` only |

---

## 10. Comfortable endgame bands (BalanceTargets doc)

Mining Fort **1100** / MP **480** · Farming Fort **1000** / Harvest **1200** · Foraging Fort **900** · Fishing Fort **900** / Catch **1400** · Combat Dmg **320** / HP **560** / Def **420** — budget split set 40% / charm 12% / skills 18% / boosters 18% / pet 12%.

---

**Takeaway for Robb:** One curve (`BalanceTargets` REV6) drives five craftable skill ladders; compression is **128→128**, not 9; sell truth is **`economy.yml` + `ItemValueService`**; recipes are entirely **`RecipeRegistry` Java**. Diving gear and a few random-* helpers are the clearest dead/asymmetric craft paths.