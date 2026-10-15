# GIGA pack — merge notes (2026-10-16)

**Zip:** `texturepack-giga/Aetherion_texturepack_GIGA_2026-10-16.zip` (pack_format 34, 2285 files, 2.1 MB).
SHA1 `564244250aac88fa811546034c58b13d93a48c91`: put this on the pack host / `resource-pack-sha1` when you swap the zip.
It was built from the live `IdeaProjects/Aetherion_texturepack.zip` (SHA1 `0c5cf112…`), which was only read, never changed.
To rebuild: `python texturepack-giga/src/build.py` (reads the live zip and the 1.21.1 client jar).

**No plugin changes.** Every new override fills a CMD that Java already sets. The validator resolves every (material, CMD) the way the client does:

- 0 errors.
- LOCKED Blossom Blade (3304) and Gravwell Cleaver (3301) are byte-identical.
- All 65 default-font glyphs and all 399 HUD glyphs keep their advance, height and ascent.

## New art: 285 textures (234 replace live art, 51 are new), all 32×32 in one hand

**P0 weapons**

| Item | Material / CMD |
|---|---|
| Worldbite | |
| Solstice | `netherite_sword` 4205 (new) |
| Severance | `iron_sword` 3340 (new) |
| Aetherblade | |
| Stormcaller Maul | |
| Staff of Technical Difficulties | `blaze_rod` 2501 (new) |
| Warped Blade | |
| Void Stick | |
| Ashen / Rotten Cleaver | `iron_axe` 1019 / 1020 (new) |
| Webweave Fang | `stone_sword` 1021 (new) |
| Hunter Crossbow | `bow` 2205 (new) + crossbow frames |
| 15 Warden's Prison dungeon weapons | 2811–2853 (new) |
| Weapon relics | `ender_eye` 2792, `nether_star` 2793 |
| Schematics | `paper` 2795, 2796 |
| Combat Sword T1–T5 | |
| 5 early bows | |

**P1 armour**

| Set | Material / CMD |
|---|---|
| Hollow Sun | `netherite_*` 2341–2344 (new) |
| Dawnbearer | 4201–4204 (new) |
| Seraphine | `leather_*` 3341–3344 (new) |
| Worldhide | |
| Aetherion | |
| Ironhide | |
| Squid's Boot | `leather_boots` 2204 (new) |
| Sealed dungeon relic armour | `leather_*` 2800–2803 / 2810–2813 (new) |

Ladder sets, each family keeping its own motif: Combat / Mining T1–T5, Farming / Fishing / Foraging T1–T5, Catcher T1–T3, Diving, Simple.

**P2 charms and trinkets**
- **From run1:** 18 skill charms, plus Shiny / Forge / Estate and the Void Vacuum Charm, as 32px medallions. Two motifs were repainted: Farming now shows a wheat ear, Utility/Wayfinder a compass rose.
- They are now on real CMD models, so vanilla clients see them; before, they were CIT-only and showed as raw seeds and bowls. The CIT copies use the same art.
- Also new: Dungeon Cores I–III (`heart_of_the_sea` / `ender_eye` / `nether_star` 2501–2503, run1 crystals), Thermal Core, Burrower Core, Insolvent Ledger, Recipe Book (`book` 3008), and the Manager tome.

**P5 tools:** Mining Pickaxe, Foraging Axe, Farming Hoe and Fishing Rod T1–T5, plus the beginner and simple tools.

**Polish round** (every weapon or tool still on old art):
- Hollow Longbow, Skuldugery Shortbow, Bridged Axe, Voided 455, Resonance Scythe, Obsidian Maul.
- Mender Staff, Ember Rod, Frost Shard, Copper Rod, Splinter Glaive, Bone Knife, Venom Dagger.
- Canopy Cleaver, Vein Siphon, Bounty Hoe, Tide Latch, Wild Sight, Catcher Gaff ×3.
- The compacted / compressed gear, and Copper Sword.
- Compressed Gold Sword and Compacted Emerald Scythe now draw their own textures. Before, they shared them with the material icons.
- The CIT `tool_*` copies follow the new art.

Still on live art: boosters, catch spheres, upgrade stones, blueprints, and LOCKED Blossom Blade / Gravwell.

## Live bugs fixed on the way
- `golden_sword` overrides were reversed, so Copper Sword, Compressed Gold Sword and Compacted Midas Dagger all showed **Venom Dagger**.
- `blaze_rod` overrides were reversed, so **Ember Rod showed Mender Staff**.
- `lightning_rod.json` pointed at a texture 1.21.1 doesn't have, so vanilla lightning rods showed the missing-texture checker.
- Items that showed the wrong icon:

  | Item | Showed before |
  |---|---|
  | Solstice | Worldbite |
  | Severance | Iron Combat Sword |
  | Hollow Sun set | Aetherion set |
  | Dawnbearer set | Worldhide set |
  | Seraphine set | Foraging T5 |
  | Dungeon weapons | Neighbouring icons |
  | Charms, cores, trinkets | Vanilla items |
- Leather icons (68 models) moved to `layer1` over `aetherion_blank`, so the dye no longer tints them.
- Vanilla trim overrides are restored on the netherite and leather base models.
- A `_vanilla` sentinel now follows every new CMD.

## GUI / HUD: yes
- **23 `gui_aetherion_*.png`** (Manager family): same OX/OY, ascent and slot geometry. New: accent metal rim, header band, recessed wells, ornate divider, banner plate.
- **`hud/frames.png` and `hud/fill.png`** (segmented fill), plus **14 `boss_bar/*` sprites** (10 segments with gloss).
- **Font JSONs** are byte-identical to live.
- **Items twin:** `texturepack-giga/twin-sync.patch` applies with `git apply` from the mining-eldervale root, and `--check` passes. It carries those 37 PNGs and the new `gen_gui_chrome.py`, `gen_hud.py` and `gen_bars.py`, so regenerating keeps this art. This session can't write into that worktree, so the patch is not applied yet.

## F3+T test first
1. Hotbar: the Manager tome plus a Combat Sword or Mining Pickaxe tier should look crisp at GUI scale 2 and 3.
2. Manager menu, one 6-row tab and the 1-row price picker: the plate and wells must sit on the slots.
3. Solstice (`netherite_sword` 4205) and Ember Rod show their own icons now.
4. Hollow Sun and Seraphine in the inventory. Farming armour shows its art colours instead of the dye tint.
5. A charm on a client without the CIT mod.
6. A boss bar, and the HP and cooldown bars.

## Risks
- Leather icons now show their art colours, not the dye colour. That was intended; check that no icon relied on the tint.
- The 256px painted charm CITs are replaced by the run1 32px art everywhere.
- An unknown CMD directly after a new entry now shows the vanilla item instead of borrowing a neighbour's icon.
- Not touched: boosters, catch spheres, upgrade stones, blueprints and worn CIT armour layers.
