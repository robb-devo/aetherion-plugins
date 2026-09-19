# Aetherion — Balance- & Progressions-Audit (Read-First)

**Stand:** 19. September 2026 · Branch `cursor/balance-audit-2b34`  
**Scope:** alle Quellen für Stats und Ressourcen. **Keine Zahlen geändert.** Compile unberührt.  
**Methode:** Rezepte, `CompressedResource`, `EconomyCurve`/`economy.yml`, `BalanceTargets` REV 5, Skill-Kurven, Boss-YAML, Dungeon-Loot, Pet-Tabellen, Quarry/Minions, Shops.  
**Skyblock-Maßstab:** Enchanted ≈ 160→1 (hier 128 = 2×64), Super-Compact ≈ gleiche Ratio nochmal, Tiers ×~1.6–2.0, Unique-Drops selten und an den richtigen Boss gebunden.

---

## Applied in PR (`cursor/balance-patch-878f`)

Gewicht-5 / high-priority Soll from this audit, plus fishing / pickaxe / skill-proc feedback. Item IDs unchanged.

| # | Change | After |
|---|--------|-------|
| A / F1 | Compact chain one language | **128 raw → Compressed**, **128 Compressed → Compacted**. Craft 4×32. Lore „128 Compressed“. Pocket Forge 128. Quarry `COMPACT_UNIT` 128×128. Economy already 128 → Craft→Trader **1.5×**. |
| B / F2 | McNugget aetherblade | Rank loot: 8/5/3 Compressed Coal + booster. Silas aetherblade now gated on **Aetherion**. |
| C / F3 | Catcher T3 + Common Sphere ≈ 63% Mythic | Sphere Mythic **2% → 0.3%**. Catch gear **multiplies** sphere rate (cap +80%), not +flat. T3 recipe **8 Compacted Feather**. T3+Common Mythic ≈ **0.5%**. |
| D / F4 | Listing printers | `catcher_*_3` ~910k → ~457k (honest T2+8 Compacted Feather×1.65). `charm_estate` 1.4M → **19**. `vein_siphon` 10.6M → **180**. 32% buyback is no longer a printer. |
| E / F7–F8 | Compacted-named gear vs T-ladder | Stone Pick **16/28/2** (≥ T1). Iron Pick **46/76/6** (1 stone + 3 Comp. Iron). Diamond Pick **80/130/12** (1 iron + 2 Compac. Diamond). Chest **4** Compac. Diamond, Def 54 / HP 74. Sword **3** Compac. Diamond + scrap (T4 stats kept). |
| F | Early Compacted fish | Crates **0%** below rod T4 (or T3 + L40 + 160 Catch). Catch-upgrade Compressed needs rod T3+. Base/vanilla/T1 rod + L1: no Compacted crate, no catch-upgrade. Base crate 4.5% → 1.2% gated. |
| G | T5 pick 3× T4 sink | T5 = **1 T4 + 1 Compacted Diamond + 3 Compacted Redstone**. Listed 3.66M → 2.03M. |
| H | Skill procs | Compact **1.2–5.5% → 0.6–2.8%**. Compacted-of-proc **6/10% → 3/5%**. Spread Sheet **1.5 → 8**. Wide Furrow **14 → 22**. |
| extra | Diamond ore gate | Required MP **95 → 80** so T4 pick (88) mines diamond alone. |

Not in this PR: Hollow Lurker, F1 relic pool / F2 HP, Blood Tax / Account softcap, pet spawn weights, wildlife T3, shard shop, Pathwarden, quarry type gaps.

---

## A) Kurzfazit

### Was schon gut wirkt

- **Skill-Leitern T1–T5** (`BalanceTargets` REV 5) sind intern stimmig: primäre Stats grob ×1.80 pro Stufe, Crit langsam, Crit-Damage steiler. Combat/Mining/Farming/Foraging/Fishing folgen derselben Kurve.
- **Rezept-Form** der Leitern ist Skyblock-nah: T1 Vanilla-Form, T2 Eisen/Karotte/Lachs-Wrap, T3 Compressed-Mix, T4 Compacted-Mix, T5 Dual-Wrap (4 Ecken + 4 Kanten Compacted). Vorgänger-Gates (`CraftedPredecessorRequirement`) verhindern das Überspringen.
- **Foraging-Holzleiter** ist die sauberste Ressourcen-Story: Oak → Birch+Spruce → Jungle+Acacia compressed → Dark Oak+Mangrove compacted → Cherry+Bamboo compacted (`RecipeRegistry` Kommentar Zeile 1017).
- **Skill-Compact-Procs** sind bewusst flach (1,2 % → 5,5 %) — das ist der richtige Ton: Compact bleibt Helfer, nicht Drucker.
- **Account-Level** ist einfach (100 XP = 1 Level) und als Langzeit-Leiter lesbar. Titel-Meilensteine und +1 Damage/+1 Health alle 5 Level sind nachvollziehbar.
- **Millstone** (1 Compacted Farm-Crop → 1 Refined, 8 s, ×4 Listenwert) ist eine klare, organische Farm-Senke.
- **Pocket Forge / Quarry-Prozessor** als QoL sind die richtige Idee — sobald die Ratios eine Sprache sprechen.

### Was kaputt wirkt („bastet“)

1. **Compact-Kette spricht vier Sprachen** (16 / 64 / 128). Lore, Craft, Pocket Forge, Quarry und Economy widersprechen sich. Das ist der größte strukturelle Bruch und der Grund, warum Midgame sich unehrlich anfühlt.
2. **Tutorial-Boss droppt Endgame-Waffe:** McNugget (3 000 HP) hat 8 % `aetherblade` (155 Dmg, gelistet 4,5 Mio.) — stärker als Combat-T5-Schwert (130 Dmg).
3. **Catcher-T3 + Catch-Rate stapelt Mythics kaputt:** T3-Set + Common Sphere ≈ 63 % Mythic-Catch. Rezept kostet 8 Compressed Feather.
4. **`economy.yml` crafted-Werte** für Catcher-T3, Estate-Charm und Vein Siphon sind 100–200× über den Zutaten. Gear-Buyback 32 % macht daraus **Coin-Drucker**.
5. **Material-Sidegrades** (Compressed Stone Pick, Compacted Iron/Diamond Pick, Compacted Diamond Chest) kosten Compacted-Mengen und verlieren gegen die billigere Skill-Leiter.
6. **Dungeon F1→F2** ist eine HP-Klippe (5 800 → 90 000), Overworld-Uniques sind oft besser als Dungeon-Rewards.

---

## B) Ressourcen-Familien & Compact/Compress-Ketten

### B1) Die Ratio-Wahrheit (ein Material, fünf Quellen)

| Quelle | Raw → Compressed | Compressed → Compacted | Raw für 1 Compacted | Datei |
|--------|------------------|------------------------|---------------------|--------|
| Craft | 2 Slots × 64 = **128** | 4 Slots × 16 = **64** | **8 192** | `RecipeRegistry.registerResourceCompression()` |
| Lore | „2 stacks“ = **128** | „**16** Compressed“ | 2 048 (falsch) | `CompressedResource.compressed()` / `.compacted()` |
| Pocket Forge | **128** | **64** | 8 192 | `PocketForgeListener` `VANILLA_PER_COMPRESSED` / `COMPRESSED_PER_COMPACTED` |
| Quarry | `COMPRESS_UNIT` **128** | `COMPACT_UNIT` 128×64 | 8 192 | `QuarryMinion` |
| Economy | `STACK_RAW` **128** × 1,4 | **128** × 1,5 | **16 384** (Preis) | `EconomyCurve`, `economy.yml` |

**Beleg Craft Compacted:** `sixteenCompressed.setAmount(16)` + Shape `"CC","CC"` + `CraftingMatcher` verlangt `actual.getAmount() >= required.getAmount()` pro Slot → **64 Compressed**, nicht 16.

**Beleg Economy:** `compacted_cobblestone: 34368` = `round(179 × 128 × 1,5)`. Craft-Zutatenwert ist `64 × 179 = 11 456`. **Craft→Trader = genau 3,0×** (nicht der dokumentierte 1,5×-Aufschlag).

Skyblock-Soll: eine Ratio, überall. Vorschlag: **128→1 und 128→1** (Economy schon so) *oder* **128→1 und 64→1** (Craft/Forge/Quarry schon so) — dann Economy und Lore nachziehen. Lore „16 Compressed“ ist objektiv falsch zum Live-Craft.

### B2) Familien — Early / Mid / Late

Einheitliche Listenwerte (Vanilla-Unit → Compressed → Compacted), soweit in `economy.yml` gelistet. Safety-Net in `ItemValueService` füllt fehlende IDs über `EconomyCurve`.

| Familie | Early (raw) | Mid (compressed) | Late (compacted / refined) | Notes |
|---------|-------------|------------------|----------------------------|--------|
| **Stein** | Cobble 1 | 179 | 34 368 | T1 Combat-Zutat; Hammer-Sidegrade |
| **Kohle** | 2 | 358 | 68 736 | T1 Mining-Pick (8 Coal + Simple); T3 Mining-Mix |
| **Kupfer** | 1 | 179 | 34 368 | T1 Mining-Rüstung; Copper Sword |
| **Eisen** | Ingot 3 / Raw 2 | 538 | 103 296 | T2 Combat/Mining-Wrap; Compacted Iron Pick |
| **Gold** | Ingot 4 / Raw 3 | 717 | 137 664 | T4 Combat (4 Compacted Gold + 4 Bone) |
| **Redstone** | 1 | 179 | 34 368 | T5 Mining-Kanten; Redstone Boots |
| **Lapis** | 2 | 358 | 68 736 | Charms Utility; Lapis Pendant |
| **Diamant** | 8 | 1 434 | 275 328 | T5 Combat-Ecken; Diamond-Sidegrades |
| **Smaragd** | 6 | 1 075 | 206 400 | T5 Combat-Kanten; Crown / Scythe |
| **Eiche** | 1 | 179 | 34 368 | T1 Foraging; Oak Chestplate |
| **Birke / Fichte** | 1 (vanilla.yml) | 179 | 34 368 | T2 Foraging (roh, nicht compressed) |
| **Dschungel / Akazie** | nicht in `vanilla:` | Safety-Net ≈179 | ≈34 368 | T3 Foraging compressed |
| **Dark Oak / Mangrove** | Safety-Net | ≈179 | ≈34 368 | T4 Foraging compacted |
| **Kirsche / Bambus** | Safety-Net | ≈179 | ≈34 368 | T5 Foraging compacted |
| **Weizen** | 1 | 179 | 34 368 / refined 137 472 | T1 Farming; Millstone |
| **Karotte** | 1 | 179 | 34 368 / refined 137 472 | T2 Farming (8 roh!); T4 Mix |
| **Kartoffel** | 1 | 179 | 34 368 / refined 137 472 | T3 Farming compressed |
| **Zuckerrohr** | 1 | 179 | 34 368 | T4 Farming |
| **Nether Wart** | 2 | 358 | 68 736 | T5 Farming |
| **Knochen / Verrottet** | 1 | 179 | 34 368 | T3 Combat compressed |
| **Leder / Feder / Faden** | 1–2 | 179–358 | 34 368–68 736 | Catcher / Säcke |
| **Schießpulver** | 2 | 358 | 68 736 | Charm Combat T3 |
| **Kabeljau** | 1 | 179 | 34 368 | T1 Fishing |
| **Lachs** | 2 | 358 | 68 736 | T2 Fishing; T4 Mix |
| **Kugelfisch** | 3 | 538 | 103 296 | T3 Fishing compressed |
| **Prismarín** | 2 | 358 | 68 736 | T4/T5 Fishing |

**Outlier-Listen (nicht in `economy.yml` resources):** `jungle_log`, `acacia_log`, `dark_oak_log`, `mangrove_log`, `cherry_log`, `bamboo_block`. Werden zur Laufzeit berechnet — funktional ok, aber T4/T5-Foraging-Zutaten sind in der YAML unsichtbar.

**Nether-Hölzer** (`CRIMSON_STEM`, `WARPED_STEM`): `contentEnabled=false` — korrekt geparkt.

### B3) Harvest / Mining / Fishing / Foraging — Yields & XP

| Aktion | Basis-Yield | Fortune | Skill-XP | Datei |
|--------|-------------|---------|----------|--------|
| Crop | 1 | +Fortune/100 extra | 4 / Crop | `CropHarvestListener`, `SkillService.grantFromBlock` |
| Log | 1 (Isle: typed wood; Harbour force Oak) | Fortune | 4 / Log | `AetherionForaging` + Skills |
| Stein | 1 | Fortune | 3 | `SkillService.miningXp` |
| Erz | 1 (Kupfer-Vanilla extra) | Fortune | 8 | dito |
| Amethyst | 4 Shards | Fortune | 12 | `MiningListener` |
| Dense Block (Coal/Iron/…) | 12–14 | Fortune | 14 | `HarvestRules.fullBlockBaseAmount` |
| Diamond/Emerald/Debris-Block | 12 / 12 / 10 | Fortune | 18 | dito |
| Fish catch | 1 + Fortune | + Fish Catch encounter 12–18 % | 8 | `FishingListener`, `config.yml` |
| Isle Heartwood | — | 3 % / bezahlter Log | — | `AetherionForaging` `isle-heartwood.chance` |
| Farm-Insel | — | Level 10 | Regen 18 s | `AetherionFarming` config |

**Mining-Power-Gates** (`HarvestRules.requiredPower`):

| Block | MP | T1-Pick 14 | T3-Pick 48 + T3-Set 110 = 158 | T4-Pick 88 allein | T5-Pick 160 + T5-Set |
|-------|----|------------|-------------------------------|-------------------|----------------------|
| Coal | 8 | ja | ja | ja | ja |
| Copper | 14 | knapp | ja | ja | ja |
| Iron | 24 | **nein allein** | ja | ja | ja |
| Gold | 42 | nein | ja | ja | ja |
| Diamond | 95 | nein | ja (nur mit Rüstung) | **nein allein (88&lt;95)** | ja |
| Emerald | 140 | nein | knapp ja | nein allein | ja |
| Debris | 250 | nein | nein | ja mit T4-Set (287) | ja |

T4-Pickaxe kann **Diamant-Erz nicht allein**. T5-Rezept braucht Compacted Diamond. Gangbar über T3/T4-Rüstungs-MP, Skills, Quarry, Dungeon-Drops oder Shard-Shop — aber die Pick-allein-Kurve lügt.

**Zeitordnung Compacted Diamond** (Rezept 8 192 raw, nicht Economy-16 384):

- T3-Mining-Set Fortune ≈ 262 → ≈3,6 Items/Erz → ≈2 270 Diamond-Erze → grob **60–90 min** Ader-Mining.
- T5-Set Fortune ≈ 860 → ≈9,6/Erz → grob **15–20 min**.
- Diamond-Quarry L1 (`rate` 0,20, `perTick` min. 1 / 10 s): 6/min → **≈23 h AFK** für 1 Compacted. L7 (26/10 s): **≈52 min**.
- Quarry-Rezept: 8 Compressed Diamond + Core = **1 024 Diamanten + 4 Shards**, bevor AFK lohnt.

### B4) Wildlife / Mob-Drops

`WildlifeLooks`: T2 Sturdy 16 % Spawn, T3 Brute, T4 Crypt.

| Tier | Compressed-Chance | Compacted-Chance | Extra-Yield |
|------|-------------------|------------------|-------------|
| T2 Sturdy | **4 %** | — | 2–4 Staples |
| T3 Brute | **28 %** | — | 4–7 |
| T4 Crypt | **8 %** | **1,5 %** | 3–5 |

T3 droppt Compressed **häufiger als T4**. T4-Compacted 1,5 % ist selten — ok. T3-Brute ist die Compact-Farm (Leder/Knochen/Schießpulver). 64 Compressed ≈ 229 Brute-Kills für 1 Compacted Leder — neben Mining die zweite Compact-Quelle.

### B5) Booster-Rezepte (inkonsistente Rohkosten)

| Booster | Rezept | Roh-Äquivalent |
|---------|--------|----------------|
| Coal / Iron / Gold / Diamond / Emerald / Redstone / Lapis | 9× Block | 81 Units |
| Wheat | 9× Hay | 81 Wheat |
| Glowstone | 9× Glowstone | 9 (nicht 81) |
| Oak / Birch | 9× Log | **9 Logs** |
| Carrot | 9×32 Carrot | **288** |

Oak-Booster ist 9× billiger als Wheat/Coal-Muster. Carrot-Booster ist 3,5× teurer.

---

## C) Combat / Stats-Quellen

### C1) Skill-Leiter (Budget REV 5, ohne Booster)

`BalanceTargets`: Tool ≈ 60 %, Rüstungsset ≈ 40 %, Charm T3 ≈ ein Mid-Rüstungsteil, 7 Skills @ L100 ≈ 15–25 % eines T5-Sets, Common-Pet ≈ 10–20 % eines T1-Teils, Mythic-Pet &lt; ein T4-Teil.

**Combat-Schwert** Dmg / AS / CC / CD:

| Tier | Stats | Rezept (Kern) | Gelistet |
|------|-------|---------------|----------|
| T1 | 14 / 3 / 5 / 42 | 2 Cobble + Stick | 4 |
| T2 | 22 / 5 / 6 / 40 | 8 Iron + T1 | 36 |
| T3 | 40 / 9 / 9 / 58 | 4 Comp. Bone + 4 Comp. Flesh + T2 | 2 129 |
| T4 | 72 / 14 / 13 / 82 | 4 Compac. Gold + 4 Compac. Bone + T3 | 1 138 924 |
| T5 | 130 / 22 / 18 / 110 | 4 Compac. Diamond + 4 Compac. Emerald + T4 | 5 825 088 |

Volle T5-Brust: Def 95 / HP 130 / AS 22 / Dmg 24 / CC 16 / CD 105. Zutaten ≈ 4×275 328 + 4×206 400 + T4 ≈ 3,1 Mio. → Margin 1,90 passt zur Liste.

**Mining-Pick** MP / Fortune / Spread: 14/24/2 → 26/44/4 → 48/80/7 → 88/145/13 → 160/260/22.

**Farming-Hoe** Fort / Harvest: 18/28 → 34/52 → 62/95 → 110/170 → 200/300.

**Foraging-Axt** MP / Fort / Spread: 10/18/2 → 18/34/4 → 34/62/7 → 62/110/13 → 110/200/22.

**Fishing-Rod** Fort / Speed / Catch: 18/4/32 → 34/8/58 → 62/14/105 → 110/24/190 → 200/38/340.

Kurve selbst: **gut**. Kosten-Sprung T3→T4 (Compressed→Compacted) ist der gewollte Skyblock-Wall.

### C2) Material-Progression (Sidegrades) — oft Falle

| Item | Stats | Rezeptkosten (Craft-Ratio) | Vergleich Leiter |
|------|-------|----------------------------|------------------|
| `compressed_stone_pickaxe` | MP 12 / Fort 16 | 3 Comp. Cobble = 384 Cobble | **schwächer als T1-Pick** (14/24), teurer als 11 Coal |
| `compacted_iron_pickaxe` | MP 32 / Fort 40 | 3 Compac. Iron = 24 576 Iron | T3-Pick 48/80 ist stärker und billiger (Compressed) |
| `compacted_diamond_pickaxe` | MP 55 / Fort 70 | 3 Compac. Diamond + 1 Cobble | T4-Pick 88/145 stärker; Kupfer+Kohle Compacted billiger |
| `compacted_diamond_chestplate` | Def 58 / HP 32 + 10 % Reflect | **8** Compac. Diamond = 65 536 Diamanten | T5-Brust braucht nur 4 Diamond + 4 Emerald Compacted, ist viel stärker |
| `compacted_diamond_sword` | 72 / 18 / 14 / 85 + 25 % Boss | 1 Compac. Diamond + Netherite Scrap | ≈ T4-Schwert für **1** statt 8 Compacted — **Steal** |
| `copper_sword` | 18 Dmg / 35 AS | 2 Comp. Copper | besser als T1-Schwert (14), Early-OK |
| `compressed_gold_sword` | 28 Dmg + Kill-Coins | 2 Comp. Gold | unter T3 (40), Utility |
| `compacted_midas_dagger` | 36 Dmg, **10 Coins/Hit** | 3 Compac. Gold + Gold Sword | unter T3, teure Senke |
| `compacted_timber_axe` | MP 16 / Fort 18 | 2 Compac. Oak = 16 384 Oak | schlechter als Foraging-T2 (18/34), 1000× teurer |
| `voided_455` | MP 220 / Fort 255 / Spread 24 | nicht craftbar | über T5-Pick (160/260/22) bei MP |

Pattern: Die T1–T5-Leiter ist die echte Progression. Compacted-Named-Gear sieht „höher“ aus und ist oft schlechter.

### C3) Skills

`SkillProgression`: Max 100. XP(n) = 30 + 12n + 2n²/3, ab 50 extra (n−49)×45.

| Ziel | Summe Skill-XP | Farming (4/crop) | Mining-Stein (3) | Mining-Erz (8) | Fish (8) |
|------|----------------|------------------|------------------|----------------|----------|
| 10 | 996 | 249 | 332 | 125 | 125 |
| 25 | 7 576 | 1 894 | 2 525 | 947 | 947 |
| 50 | 43 098 | 10 775 | 14 366 | 5 387 | 5 387 |
| 100 | **338 601** | 84 650 | 112 867 | 42 325 | 42 325 |

Effekt-Mult: 1,0 @1 · 2,46 @50 · 3,28 @60 · **6,28 @100**. Compact-Chance 1,2–5,5 %.

7 Slots (`SLOT_COUNT=7`), Slot 1 immer frei. Account-XP = Skill-XP / 40. Ein Skill auf 100 ≈ 8 465 Account-XP ≈ **85 Account-Level**. Sieben maxed Skills ≈ 600 Level, wenn alle Slots parallel fressen.

`BalanceTargets` „7 Skills @100 ≈ 15–25 % T5-Set“: Heavy Hands Basis 5,5 × 6,28 ≈ **35 Dmg** — das ist 27 % eines T5-Schwerts allein. Sieben Combat-Skills + Account-Meilensteine sprengen das Budget. Account +1 Dmg/HP je 5 Level: @100 = +20, @500 = +100, @2500 = +500, Cap 5000 = **+1000**. Langfristig **Account &gt; Gear**.

`Blood Tax` @100: ~185 % der Mob-Max-HP als Coins. Brute ~750 HP → ~1 387 Coins/Kill. Mit Gold Sword (24 % HP) und Golden Hour stapelbar.

### C4) Pets — Spawn, Catch, Combat

**Rarity-Gewichte** (`PetFactory`): Common 30 · **Uncommon 56** · Rare 10,5 · Epic 2,8 · Legendary 0,28 · Mythic 0,004.  
Uncommon ist häufiger als Common — Catch-Feeling „alles ist Uncommon“.

**Sphären** (`CatchSphereRegistry`), Chance in %:

| Sphere | Common | Unc | Rare | Epic | Leg | Myth | Rezept |
|--------|--------|-----|------|------|-----|------|--------|
| common | 45 | 28 | 15 | 8 | 4 | **2** | 4 Coal + Planks |
| rare | 60 | 45 | 32 | 18 | 10 | 5 | 4 Gold + Planks |
| epic | 75 | 62 | 50 | 38 | 22 | 12 | 4 Diamond + Planks |
| legendary | 88 | 78 | 68 | 55 | 40 | 22 | Root Cellar |
| beta | 90 flat | | | | | | Dev |

Catch-Rate vom Gear **addiert** sich (`PetCatchListener.getCatchChance`, Cap 100).

Catcher-Set Catch-Rate (`CatcherItems`): T1 2+3+2+2+8 = **17** · T2 5+7+5+5+14 = **36** · T3 9+12+9+9+22 = **61**.

T3-Rezept: 8 Compressed Feather (1 024 Federn) um T2. **Common Sphere + T3-Set = 2+61 = 63 % Mythic.** Emerald Crown +25 → 88 %. Lucky Streak + Utility-Charm können 100 % erreichen.  
T3 ist `registerPlusUpgrade` (8 Surround) mit **Compressed** Feather, Rarity LEGENDARY — Legendary-Pet-Gear für Early/Mid-Farm.

**Combat-Kurve** (`PetStats`): ×1,01/Level, Softcap 48, darüber ×0,28.

| Pet | Core Common | Core Leg / Myth | L1 nach Softcap | L100 grob |
|-----|-------------|-----------------|-----------------|-----------|
| Wolf | 2–4 | 30–46 | 2–46 | unter T4-Teil |
| Pig (Fortune) | 4–8 | 38,5–56 | bis 48 | ~T3-Hoe-Fortune |
| Sack | 6–12 | 63–91 Myth | 52–60 | stark, noch Gear-Raum |
| Guardian | — | 90–135 Myth | ~62–72 | an T4-Brust |
| **Aetherion** | — | **225–450 Myth** | **~98** | **~160** |

BalanceTargets: „Mythic pet &lt; ein T4-Teil“. Aetherion-Pet sprengt das bewusst (Raid-Pet). Der Rest der Kurve ist ok — **Catch-Rate nicht**.

Erste Catch: +90 Account-XP. Gaff-XP skaliert mit Rarity.

---

## D) Bosse & Dungeons

### D1) Overworld / Raid (Live-YAML, Test-Arena ausgeschlossen)

| Boss | HP | Atk | Phasen-Idee | Signature (Rank 1) | Gelistet | Kommentar YAML |
|------|----|-----|-------------|--------------------|----------|----------------|
| **McNugget** | 3 000 | 30 | 2 (Panic 50 %) | **aetherblade 8 %** | 4 500 000 | „T1 rite / tutorial“ |
| Hollow Lurker | 3 000 | — | 1 | warped_blade **40 %** + **32 Diamond Blocks** | 1 800 000 | 3k HP, T3-Waffe + 288 Diamanten |
| Skuldugery | 3 000 | — | — | shortbow 15 % | 2 800 000 | |
| Bridge Troll | 3 000 | — | — | bridged_axe 12 % | 2 400 000 | |
| Squidward | 11 000 | — | — | squid boot 12 % | 1 600 000 | |
| Aether Colossus | 38 000 | — | — | guaranteed + 35 % | — | |
| **Pathwarden** | 52 000 | **520** | Director | gravwell 10 % | 2 400 000 | „T2–T3 · Solo T3 ~7 min“ |
| Dungeon Sentinel | 5 800 | 72 | Slam | Dungeon-Loot | — | F1 |
| Frostbound | 90 000 | 200 | — | Dungeon-Loot | — | F2 |
| Dungeon Aetherion | 115 000 | 240 | — | Dungeon-Loot | — | F3 |
| Sir Balthazar | 115 000 | — | Adds | 34 % unique | — | |
| Sparky | 120 000 | — | — | 34 % | — | |
| Lobby Cleaner | 125 000 | — | — | 34 % | — | |
| Baron von Wurm | 128 000 | — | Adds | 34 % | — | |
| Insolvent Wither | 145 000 | — | Adds | 34 % | — | |
| **Aetherion** (Welt) | 200 000 | 92 | Dragon | Rank1 **100 % Armor** + 10 % Void Stick | 6–9 Mio. | „T4–T5 party 4–5 min“ |

Alle Live-Bosse: `equipment.drop-chance: 0.0` — Loot nur über `loot:`-Tabellen. Gut.

**McNugget-Math:** T1-Schwert 14 Dmg → ~215 Hits, mit Crit/AS grob 2–4 min Solo. Reward kann die **gesamte Combat-Leiter überspringen**. `BossGearBalance.aetherblade` = 155/18/20/140 vs T5-Schwert 130/22/18/110.

**Hollow Lurker:** 32 Diamond Blocks guaranteed Killer-Bonus = 288 Diamanten = 2,25 Compressed. 3 000-HP-Mob als Diamond-Quelle.

**Pathwarden 520 Atk:** Kommentar sagt BossHits softcappt ~72 % Bar. T2-Set-HP ≈ 68+20 Basis ≈ 90. Ohne Softcap One-Shot. Softcap muss in Live-Tests gehalten werden — YAML allein sieht T2-unspielbar aus.

**Aetherion 200k:** T5-Set ~200+ Dmg + Crit + Party → 4–5 min wie dokumentiert. **Belohnung passt zur Hürde.** Das Tutorial-Unique nicht.

Silas Markup: Uniques ×8 Listenwert nach erstem Kill (`FenceService.MARKUP = 8`). Aetherblade bei Silas ≈ 36 Mio. Coins — ok als Skip, wenn der Drop selten wäre. Ist er nicht.

### D2) Dungeons

Floors: F1 Sentinel 5 800 · F2 Frostbound 90 000 (**×15,5**) · F3 Aetherion 115 000.  
Loot (`DungeonLootFx.rollCombat` / `rollVictory`, d100):

**Combat-Kisten**

| | F1 | F2/F3 |
|--|----|-------|
| Compacted | 2 % | 2 % |
| Compressed | 16 % | 16 % + 16 % extra |
| Booster | 26 % | 22 % |
| Schematic / Vestige | 22 % / 34 % | — |
| Core | — | 18 % + 24 % |

**Victory**

| | F1 | F2 | F3 |
|--|----|----|-----|
| Aetherion-Armor | — | — | **3 %** |
| Relic (`aetherblade` / `warped_blade` / `bridged_axe`) | **8 %** | 8 % | 8 % |
| Core | 14 % | 14 %+24 % | 14 %+24 % |
| Compacted | 2 % | 2 % | 2 % |

`ItemLootBridge.BOSS_ITEMS` = dieselben drei Uniques wie McNugget/Lurker/Troll. F1-Victory kann Aetherblade droppen. Cores: 350k / 750k / 1,5 Mio. gelistet. Vestige-Teile 50–85k.

**Schwierigkeit vs Reward:** F1 ist leichter als mehrere Overworld-3k-Bosse, droppt aber dieselben Relics seltener. F2 ist eine Mauer ohne proportionalen Unique-Sprung (nur Cores). F3-Mythic-Armor 3 % ist ok-selten; Welt-Aetherion gibt Rank-1-Armor guaranteed.

### D3) Midgame-Item vs Zeit (Größenordnung)

| Ziel | Grober Aufwand | Power |
|------|----------------|-------|
| Combat T3-Set | Comp. Bone+Flesh: ~8×128×5 Teile ≈ 5k Mob-Drops oder Brute-Farm | 40-Dmg-Schwert — fühlt sich verdient an |
| Combat T4-Set | 20 Compacted Gold+Bone = 20×8192 raw | 72 Dmg — Wall, Skyblock-ok |
| Combat T5-Set | 20 Compacted Diamond+Emerald ≈ 20×1 h T3-Mining oder Quarry-Tage | 130 Dmg — hart, aber Leiter-intern fair |
| Compacted Diamond Sword | 1 Compacted Diamond (~1 h) | **T4-Dmg + Boss 25 %** — zu billig |
| Compacted Diamond Chest | 8 Compacted Diamond (~8 h) | T4-Def, schlechte HP — zu teuer |
| Catcher T3-Set | 5×8×128 = 5 120 Federn | **63 % Mythic-Catch** — zu billig |
| Aetherblade | 1 uns McNugget, 8 % | über T5 — zu billig |
| T5 Farming-Hoe | 4 Compac. Wart + 4 Compac. Wheat + T4 | 200/300 Fortune/Harvest — Leiter-ok, Wart-Quelle dünn |

---

## E) Economy (Coins / Shards)

### E1) Quellen

| Quelle | Formel / Zahl | Datei |
|--------|---------------|--------|
| Resource-Trader | 100 % Listenwert Compressed/Compacted/Refined | `ItemValueService`, `economy.yml` Kommentar |
| Craft→Trader Compacted | 3,0× Zutaten (wegen 64-vs-128) | siehe B1 |
| Gear-Buyback | **32 % Listenwert** | `ItemValueService.gearBuyback` |
| Blood Tax | 22 %→185 % Mob-HP | `SkillService.bloodTaxCurve` |
| Compressed Gold Sword | 24 % Mob-HP | `ProgressionEffects` |
| Emerald Scythe Proc | 25 Coins + Stun | dito |
| Bazaar | 50–300 % | `economy.yml` Kommentar |
| Silas | Listenwert × **8**, nach Boss-Kill | `FenceService.MARKUP` |
| Account-Shards | **650** alle 25 Level | `AetherionLevel.SHARD_MILESTONE` |
| Crystal Liquidator (Mats→Shard) | 40 000 Listenwert / Crystal | `LiquidatorService.MAT_VALUE_PER_CRYSTAL` |
| Dungeon / Boss / Quarry / Wildlife | siehe D / B | |
| Catcher-T3 Buyback | 32 % × 910 775 ≈ **291k** bei ~1,4k Craft | `economy.yml` `catcher_*_3` |
| Estate-Charm Buyback | 32 % × 1 408 396 ≈ **451k** bei ~12 Vanilla | `charm_estate` Rezept `SL/BG` |
| Vein Siphon Buyback | 32 % × 10 614 988 ≈ **3,4 Mio.** bei T1-Pick+Iron+Redstone | `vein_siphon` |

Die letzten drei sind **keine Senken**, sondern Drucker, solange der Gear-Trader sie annimmt (`isGear` = Aetherion-ID, nicht Compressed).

### E2) Senken

| Senke | Zahl | Note |
|-------|------|------|
| Midas Dagger | 10 Coins/Hit (5 mit Pinch Penny) | echte Senke, Waffe zu schwach |
| Crystal kaufen | **20 000** Coins / Crystal | |
| Crystal verkaufen | **7 500** Coins | 62,5 % Spread |
| Shard-Shop Compacted Diamond | 220 Crystals = 4,4 Mio. Coins | 16× Listenwert 275k — Skip, kein Deal |
| Shard-Shop 8× Comp. Diamond | 45 Crystals | **teurer pro Compressed** als 1 Compacted (220/64 = 3,4 vs 45/8 = 5,6) |
| Shard-Shop Refined Wheat | 80 Crystals | vs 137k Liste |
| Shard-Shop Estate | 350 Crystals | vs 12-Coin-Rezept / 1,4-Mio-Liste |
| Hacker-Pet | 450 / 850 Crystals | Shop-only, ok |
| Quarry Compressor Craft | 8 Compacted Cobble + Core | Liste **450** vs Zutaten ~275k — Listing-Müll, funktionale Senke |
| Casino | variabel | `CasinoService` |
| T4/T5 Craft | Millionen Listenwert | echte Progressionssenke |

### E3) Listing-Bugs (Generator, nicht Ratio)

| ID | Liste | Plausible Zutaten | Faktor |
|----|-------|-------------------|--------|
| `catcher_*_3` | ~910 775 | T2 + 8× Comp. Feather (179) ≈ 3,5k | **~260×** — Rechnung sieht aus wie 8× `compacted_coal` (68 736) × 1,65 |
| `charm_estate` | 1 408 396 | Spyglass+Leder+Knochen+Gold ≈ 12 | **~100 000×** |
| `vein_siphon` | 10 614 988 | T1-Pick + 4 Iron + 4 Redstone ≈ 50 | **~200 000×** |
| `charm_forge` | 704 847 | 1 Compac. Cobble + Furnace + Comp. Iron/Oak ≈ 35k | ~20× |
| `blueprint_upgrade_stone_4` | 2 973 983 | härter als Stone 3 | **&lt; Stone 3** (3 180 058) — invertiert |
| `quarry_compressor` / `compactor` | 450 / 1 400 | 8 Compac. Cobble / 8 Compac. Diamond + Vorgänger | 600–1500× zu niedrig |

`economy.yml` `items: {}` — alles über `crafted`/`resources`/`drops` + Safety-Net.

---

## F) Priorisierte Unbalance-Liste

Gruppe nach einem Patch, nicht einzeln anfassen. **F1–F4, F7–F8, F12 (diamond MP) plus fishing/skill-proc feedback: siehe Applied in PR oben.** F5–F6, F9–F11, F13–F20 still open.

| # | Gewicht | Ist | Soll-Vorschlag (Skyblock-glatt) | Datei / Symbol |
|---|---------|-----|----------------------------------|----------------|
| 1 | **5** | Compacted: Lore 16 / Craft+Forge+Quarry 64 / Economy 128 | Eine Ratio. Empfehlung: **128 raw→1 Comp, 128 Comp→1 Compacted** (Economy schon so). Craft-Shape 8×16 oder 4×32. Lore-Text anpassen. Pocket Forge + `QuarryMinion.COMPACT_UNIT` mitziehen. | `RecipeRegistry.registerResourceCompression`, `CompressedResource` Lore, `PocketForgeListener`, `QuarryMinion`, `EconomyCurve` |
| 2 | **5** | McNugget 8/4/2 % `aetherblade` (155 Dmg) | Unique entfernen oder auf &lt;1 % / T4-Boss verschieben. McNugget: Booster + 4–8 Compressed + 1–2k Coins. Aetherblade nur Aetherion/F3. | `bosses/mcnugget.yml` `rank-loot`; `BossGearBalance.aetherblade` |
| 3 | **5** | Catcher T3 +61 Catch, Common Sphere Mythic 2 % | Catch-Rate **multiplikativ** oder Softcap (z. B. +Catch = +% der Basis, Cap +15). T3-Rezept auf **Compacted Feather** oder 8 Compacted. Sphere-Mythic 2 % → 0,2–0,4 %. | `CatcherItems`, `PetCatchListener`, `CatchSphereRegistry`, `RecipeRegistry` catcher_3 |
| 4 | **5** | Catcher-T3 / Estate / Vein Siphon Listenwerte 100–200× Zutaten; Buyback 32 % | `economy.yml` crafted neu aus echten Zutaten × Margin. Catcher T3 ≈ 3,5k×1,65 ≈ **6k**. Estate ≈ 15–50. Siphon ≈ 80–200 (Blueprint-Prestige max. 5–20k). Buyback auf 32 % **nur** nach Recalc. | `economy.yml` `crafted`; Generator hinter `ItemValueService` |
| 5 | **4** | Hollow Lurker 40 % warped_blade + 32 Diamond Blocks @ 3k HP | Blade 8–12 %; Killer-Bonus 4–8 Diamond **Blocks** oder 1 Compressed Diamond. | `bosses/hollow_lurker.yml` |
| 6 | **4** | F1 Victory 8 % Relic-Pool inkl. Aetherblade; F1→F2 HP ×15,5 | F1-Relics: Vestige/Schematic/Core T1 only. Relic-Pool ab F3 oder Welt-Raid. F2-HP Richtung 35–50k **oder** F2-Rewards (garantierter Core T2 + 4–8 Compressed). | `DungeonLootFx`, `ItemLootBridge.BOSS_ITEMS`, `dungeon_frostbound.yml` |
| 7 | **4** | Compacted Diamond Chest 8 Compacted für Def 58; Sword 1 Compacted für T4-Dmg | Chest: 3–4 Compacted, Stats näher T4-Brust (HP 70+). Sword: 3 Compacted + Scrap **oder** Dmg 48–55 (zwischen T3/T4). | `RecipeRegistry.registerMaterialProgression`, `ProgressionItems` |
| 8 | **4** | Material-Picks schwächer als Leiter bei höherem Compact-Cost | Stone Pick ≤ Simple/T1-Kosten oder Stats ≥ T1. Iron Pick = T3-Äquivalent oder Rezept auf Compressed. Diamond Pick ≈ T4-1, Rezept 2 Compacted nicht 3+Cobble. | `ProgressionItems`, Rezepte |
| 9 | **4** | Blood Tax @100 = 185 % HP-Coins; Account +1/5 bis +1000 | Blood Tax Decke ~40–60 % HP. Account-Stat Softcap (z. B. +1/5 bis 100, dann +1/25). Skill-Mult @100 Richtung 3,5–4,0× statt 6,3× **oder** Slot-Budget 3 Combat-Skills. | `SkillService.bloodTaxCurve`, `AetherionLevel`, `SkillProgression` |
| 10 | **3** | Uncommon-Spawn-Gewicht 56 &gt; Common 30 | Common 55 / Unc 30 / Rare 10 / Epic 3 / Leg 0,4 / Myth 0,05 (Summe ≈100). | `PetFactory` Konstanten |
| 11 | **3** | T3 Brute Compressed 28 % &gt; T4 8 % | T2 3 % / T3 8 % / T4 12 % + Compacted 2–3 %. | `WildlifeLooks` T2/T3/T4_COMPRESSED |
| 12 | **3** | T4-Pick 88 MP &lt; Diamond 95 | T4-Pick 100–110 **oder** Diamond-Erz 80. T5-Rezept darf nicht hinter einem Solo-Pick-Gate liegen. | `BalanceTargets.MINING_PICK`, `HarvestRules.requiredPower` |
| 13 | **3** | Farming T2 = 8 Karotten; Oak-Booster = 9 Logs | T2 Farming: 8 Compressed Wheat **oder** 32 Karotten/Slot. Boosters: 9 Compressed **oder** 9 Blocks einheitlich. Carrot-Booster 9×8 statt 9×32. | `RecipeRegistry` farming_2, booster-Blöcke |
| 14 | **3** | Shard-Shop: Compacted Diamond shard-effizienter als Compressed | 8 Comp. Diamond ≈ 25–30 Crystals; 1 Compacted ≈ 180–200 **oder** 8×64-Äquivalent. Estate aus Shop oder an Compacted-Zutat binden. | `ShardShopMenu.materialOffers` |
| 15 | **3** | Pathwarden 520 Atk; Stone 4 Liste &lt; Stone 3 | Atk 80–120 (T3-Party). Stone 4 Liste ≥ Stone 3 (Netherite-Block + Debris). | `pathwarden.yml`, `economy.yml` blueprint_upgrade_stone_* |
| 16 | **3** | Quarry-Typen fehlen vs. Rezepte (Salmon, Puffer, Prismarine, Cane, Wart) | Entweder `QuarryType`-Cases oder `hasQuarry=false` + Rezept raus. L7 Diamond `perTick` min. 1 bei rate 0,2 ist ok; L1 Diamond = 1/10s ist großzügig (Soll: 0,2 → selten, nicht `max(1,…)`). | `QuarryType`, `CompressedResource.hasQuarry`, `perTick` |
| 17 | **2** | Timber Axe / Oak Chest vs. Foraging-T2 | Timber Axe Stats ≥ T3-Axt oder Rezept 2 Compressed nicht 2 Compacted. | `ProgressionItems`, Rezepte |
| 18 | **2** | Midas 10 Coins/Hit bei 36 Dmg | Dmg 48–55 **oder** Fee 2–3 Coins. Sonst niemand craftet ihn. | `ProgressionItems.createCompactedMidasDagger` |
| 19 | **2** | Crimson/Warped im Enum, nicht im Flow | Lassen bis Nether-Content; nicht in Menüs zeigen (schon `contentEnabled=false`). | `CompressedResource` |
| 20 | **1** | God/God2-Items 15 Mio. / 30 Mio. Liste | Dev/Admin — ignorieren oder aus Spieler-AH sperren. | `economy.yml` drops |

---

## G) Was solo testbar ist vs. was Multiplayer braucht

### Solo (ein Account, eine Welt, Dev-Menu)

- Compact-Kette: 128 Cobble craften → 1 Compressed; 64 Compressed → 1 Compacted. Lore-Text „16“ vs. Verbrauch 64. Pocket Forge Offhand, gleiche Zahlen.
- Craft→Trader: 64 Compressed Cobble (Wert 11 456) → 1 Compacted verkaufen (34 368).
- Catcher T3 craften (8 Comp. Feather), Listenwert / Buyback gegen Gear-Trader.
- Estate Charm und Vein Siphon: Craft-Kosten vs. Buyback.
- McNugget `/boss give mcnugget_core`: Kill-Zeit mit T1-Set, Loot-Tabelle (Rank-1 8 % — ~12 Kills für Erwartungswert 1 Blade).
- Mining-Gates: T4-Pick allein an Diamond-Erz; T3-Vollset an Diamond; T4-Vollset an Debris.
- Skill-XP: 10 Wheat → +4 Farming auf ausgerüsteten Skills; Level-1→2 braucht 42 XP ≈ 11 Crops.
- Catch: Common Sphere auf Common-Pet vs. auf Mythic-Pet mit/ohne Catcher-T3 (Dev-Pet).
- Millstone: 1 Compacted Wheat → 1 Refined (137 472 vs. 34 368).
- Shard-Shop-Preise vs. Liquidator (20k/Crystal).
- Pathwarden-Schaden mit T2-Set (Softcap ja/nein).
- Dungeon F1 Solo: Sentinel 5,8k, Kisten-Mix. F2 Solo nur mit T4/T5 oder Dungeon-Set — Erwartung: Brick.

### Braucht Multiplayer / Netzwerk

- Dungeon **mmo-r → mmo-d** Transfer, Party-Chest-Claims, `damage-based` Rank-Loot (3 Ranks).
- Aetherion-Raid 200k / Insolvent 145k als 3er-Party (YAML-Ziel 4–5 min).
- Bazaar 50–300 % und AH (zwei Spieler, Listing-Duping: `docs/DUPING_CHECKLIST.md`).
- Guild-Quarry-Ticks, Insel-Cap, Core-Shard aus Weltboss bei mehreren Damagern.
- Blood-Tax-Farm in shared Wildlife (Brute-Dichte, Chunk-Load).
- Catch-Kontest / Pet-Despawn wenn zwei Spieler dieselbe Entity hitten.
- Silas nach „ein Kill auf dem Account“ vs. Party-Credit.

### Nicht in diesem Audit (Lücken)

- Quest-Rewards (AetherionQuests) — Haken, keine Item-Defs.
- Casino-RTP im Detail.
- Booster-Socket-Math live (±20–35 % laut BalanceTargets — nicht gegen jedes Item gegengerechnet).
- Pit-Shop enchanted gear (Level 5 Gate) — Nebenwelt.
- Live-MP auf T4-Pick nach Skills/Pets (Cave Sense, Quarry Manners können die 7-MP-Lücke schließen).

---

## Anhang — Datenquellen (Inventar)

| Thema | Ort |
|-------|-----|
| Rezepte / Gates | `AetherionItems/.../recipe/RecipeRegistry.java`, `CraftingMatcher.java`, `RecipeUnlockService.java` |
| Compact-Items | `economy/CompressedResource.java`, `EconomyCurve.java`, `resources/economy.yml` |
| Item-Stats Leiter | `item/BalanceTargets.java`, `item/StarterSetBalance.java` |
| Sidegrades | `item/ProgressionItems.java` |
| Catcher | `item/CatcherItems.java` |
| Boss-Waffen | `item/BossGearBalance.java` |
| Skills | `skill/SkillProgression.java`, `skill/AetherSkill.java`, `skill/SkillService.java`, `skill/AetherionLevel.java` |
| Harvest | `mining/HarvestRules.java`, `listener/MiningListener.java`, `listener/CropHarvestListener.java` |
| Gather-Welten | `AetherionMining/Farming/Foraging/Fishing` `config.yml` |
| Wildlife | `world/WildlifeLooks.java` |
| Quarry | `AetherionGuilds/.../QuarryMinion.java`, `QuarryType.java` |
| Pets | `Aethermobs/.../PetFactory.java`, `CatchSphereRegistry.java`, `PetStats.java`, `PetCatchListener.java` |
| Bosse | `BossEngine/src/main/resources/bosses/*.yml` |
| Dungeon | `AetherionDungeons/.../DungeonLootFx.java`, `ItemLootBridge.java` |
| Shops | `shop/ShardShopMenu.java`, `economy/LiquidatorService.java`, `economy/FenceService.java` |
| Ownership | `ARCHITECTURE.md`, `docs/OWNERSHIP.md` |

**Nächster sinnvoller Patch:** Punkt F1 allein (Ratio vereinheitlichen + Lore). Danach F2–F4 (McNugget, Catch, Listings). Nicht alles auf einmal — die Leiter T1–T5 soll stehen bleiben.
