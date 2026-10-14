# Aetherion Manager GUI chrome

One art language for the whole Manager family: dark stone panel, a thin accent ring with
tooltip-style corner brackets, an engraved slot grid, a quiet rune divider above the player
inventory, and a banner title plate (sigil · NAME · sigil) that sits on the top edge.
Each category changes only its accent hue and sigil. The Boss Journal uses a parchment plate.

Both pack trees ship identical files:
`aetherion-texturepack/` and `AetherionItems/resourcepack/`.

## How it renders

A title is `` (space, advance −48) followed by one 256×256 bitmap glyph.
All glyphs use `"ascent": 23, "height": 256`, and the text colour must be **WHITE**.

| What                                          | Value                                    |
|-----------------------------------------------|------------------------------------------|
| Vanilla title position                        | (8, 6) from the container's top-left     |
| Glyph left = 8 − 48                           | −40 → GUI x 0 = bitmap x **40**          |
| Glyph top = 6 + 7 − ascent                    | −10 → GUI y 0 = bitmap y **10**          |
| Panel bleed                                   | 2 px past the vanilla background on every side |
| Plate                                         | GUI y −8 … 10 (sits above the panel)     |

One ascent works for every chest size because the title is always drawn at (8, 6) from the
container's top-left, whatever its height. The art follows vanilla `ChestMenu` geometry:
container slots `(8+18c, 18+18r)`, player inventory `103+18r+(rows−4)·18`, hotbar
`161+(rows−4)·18`. The faint vanilla "Inventory" label lands on the clear left half of the divider.

> The old `` art put the GUI origin at bitmap (46, 17), but `ascent 24` puts it at
> (40, 11), so the old wells were drawn 6 px right of and 6 px below the real slots.
> The new art and `ascent 23` are built from the same numbers, so the two agree.
> If something is off in game, change `ascent` on every glyph. Only change `` for a
> horizontal error (that also moves the hub).

## Glyph map

| Glyph    | File                                  | Rows | Used by |
|----------|---------------------------------------|------|---------|
| `` | gui_aetherion_manager.png             | 6 | `AetherionManagerGUI.TITLE` (unchanged Java) |
| `` | gui_aetherion_shop.png                | 5 | `ShardShopMenu` root |
| `` | gui_aetherion_shop_boosts.png         | 5 | `ShardShopMenu` BOOSTS |
| `` | gui_aetherion_shop_materials.png      | 5 | `ShardShopMenu` MATERIALS |
| `` | gui_aetherion_shop_exclusive.png      | 5 | `ShardShopMenu` EXCLUSIVE |
| `` | gui_aetherion_bazaar.png              | 6 | `MarketService` browse (Bazaar) |
| `` | gui_aetherion_auction.png             | 6 | `MarketService` browse (Auction) |
| `` | gui_aetherion_market_list.png         | 1 | `MarketService` price picker (9-slot) |
| `` | gui_aetherion_market_confirm.png      | 3 | `MarketService` confirm purchase |
| `` | gui_aetherion_skills.png              | 6 | `SkillMenu` |
| `` | gui_aetherion_skills_overview.png     | 6 | `SkillOverviewGUI.Holder` |
| `` | gui_aetherion_skills_presets.png      | 3 | `SkillPresetsGUI.Holder` |
| `` | gui_aetherion_recipes.png             | 6 | **not wired**: `RecipeBookGUI` (see below) |
| `` | gui_aetherion_bestiary.png            | 6 | `CodexBrowser` (Bestiary), `CodexDetailGUI` (Bestiary cards) |
| `` | gui_aetherion_collection.png          | 6 | `CodexBrowser` (Collection), `CodexDetailGUI` (Collection cards) |
| `` | gui_aetherion_codex.png               | 6 | `CodexHubGUI.Holder`, detail fallback |
| `` | gui_aetherion_milestones.png          | 6 | `CodexMilestonesGUI.Holder` |
| `` | gui_aetherion_journal.png             | 6 | `DungeonJournalGUI.Holder` (Boss Journal) |
| `` | gui_aetherion_stats.png               | 6 | `StatsOverviewGUI` |
| `` | gui_aetherion_storage.png             | 6 | `StorageGUI` + `StorageHolder` (page in holder) |
| `` | gui_aetherion_loadouts.png            | 6 | `LoadoutGUI` + `LoadoutHolder` |
| `` | gui_aetherion_spawns.png              | 6 | `AetherionHub` `SpawnMenu` |
| `` | gui_aetherion_pets.png                | 6 | `Aethermobs` `PetMenu` collection (Holder) |

Kept: `` (Wanderer badge), `` (−48 shift), `` (space_neg).
Java constants live in `de.aetherion.items.util.ManagerChrome`. The old `String TITLE`
constants are kept, so nothing that references them breaks.

## Accents

manager lilac · shop aether-cyan · bazaar/market amber · auction gold · skills periwinkle ·
recipes bronze · bestiary crimson · collection jade · codex/milestones violet ·
journal parchment · stats teal · storage copper · loadouts steel · spawns moss · pets rose.

## Wired / remaining

- **Storage / Loadouts / Pets / Recipes**: wired via `InventoryHolder` + `ManagerChrome.*`
  (page on `StorageHolder`). Pack glyphs already on Hetzner `:8088`.
- **Not chest UIs / out of scope**: Craft (vanilla workbench), Island and Guild (chat),
  Booster Sockets / Anvil (LOCKED), DevMenu. Aetherlex / Pet Details still use legacy titles.

## What overlay titles lose

Live title text that used to show extra info is gone: the Bazaar/Auction page counter (`· 1/5`),
the Codex category or entry name, and the Bazaar/Auction name on the price picker. The menus
already show that info in their header and nav items.

## Regenerating

`tools/gen_gui_chrome.py` (in the Items repo) is parametric: the theme table, row counts,
accent hex values and sigils sit at the bottom. Run `python gen_gui_chrome.py <out_dir>` and
copy `assets/` into both packs.
