# Farming — Eldervale expansion

Branch `claude/farming-island-expansion-1d0764`. Makes the Eldervale Farm Isle (pasted in hub `world`,
footprint x −481…−107, z 420…782) the Farming destination.

## Base lineage (why the branch has two merges + an import)

| Live jar | Built from |
|---|---|
| AetherionItems (09-28 20:57) | `claude/helios-polish` (`_wt_helios_polish`) |
| AetherionCore / Farming-1.0.0 (09-28 12:53) | `feature/midgame-skills-gather` |
| AetherionFarming.jar (09-25) | **staged WIP in the main checkout** (featured crop, scarecrow, footprint, seeder) |

This branch = main + midgame merge + Helios merge + the Farming WIP import (commit `5a79cc3`) + the expansion.
Building Core + Items + Farming from here keeps every live feature of all three.

## Systems (AetherionFarming `isle` package unless noted)

| System | What it does | Config |
|---|---|---|
| Harvest Rhythm | Combo meter, tiers 20/50/100 pay +10/+25/+50 crop-only Fortune (+10/+25 Harvest), pentatonic note per swing, notched boss bar | `harvest-rhythm.enabled` |
| Prize Crops | 1/450 per hand harvest (20 s cooldown); giant glowing crop, finder's dibs 4 s, anyone 4–8 s, then wilts. Weighted item, 192–512 coins | `prize-crops.chance` |
| Crop Mastery | Lifetime per-crop ledger, tiers I–VI at 250/1k/4k/12k/35k/100k, +6 crop Fortune per tier, coins + XP; isle ×2 | `crop-mastery.isle-weight` |
| Plots & compass | 15 named areas, discovery title + 25 XP, Cartographer 2,500 coins, action-bar wayfinder | `farm-isle-plots` |
| Isle events | Bee Bloom (field: 50 % +1 crop, +2 XP, 90 s) / Harvest Moon (prizes ×4, 120 s), every 20 min with farmers on | `isle-events.*` |
| Harvest Orders | 3 per player, raw / compressed / prize, 2–2.6× trader value + XP, 3 min restock, free reroll 30 min | — |
| Oven House | 5 foods from crops (Loaf, Crumble, Hash, Cordial, Golden Pie), 10–15 min farming-only buffs | — |
| Cast | 8 villagers (Warden, Orders, Baker, Granary, Beekeeper, Pip, Marta, Tobias), holograms, barks, door presets | `isle-cast.presets`, data `isle-cast.yml` |

Player data: `plugins/AetherionFarming/isle-players.yml` (plots, mastery, prizes, orders, food, records).

Items: 5 Farming skills (Soil Sense isle-only stats, Row Rhythm, Blue Ribbon, Bird Law, Market Day),
`FarmIsleHook` (guarded FarmAccess calls), crop listener adds crop Fortune + harvest extras,
DEV `FarmIsleDevPages`. Core: `FarmAccess` default methods.

## DEV menu

`/devmenu` → page 1, slot 42 **Farming Island** (Portals moved inside as *Legacy Portal Tools*).

- **Teleports** — landing, every NPC spot, every plot.
- **NPC Cast** — left anchor · right preset · shift-left go · shift-right remove; *Place Whole Cast at Presets*.
- **Props & Tools** — scarecrow, hay wagon, cane tool, 4 district signs, millstones, FAWE prop list.
- **Events** — Bee Bloom, Harvest Moon, stop, bird scare here, scarecrow wave, featured reroll, prize pop, max rhythm.
- **Progression & Skills** — Farming skills Lv 1–100, Eldervale loadout, hoes T1–T5, all prizes, mastery 0/III/VI,
  plots all/reset, order reset, profile wipe (shift), foods, clear food.
- Hub row 3 opens every NPC board without walking.

## Test sheet

1. DEV → Farming Island → NPC Cast → *Place Whole Cast at Presets*. Walk the Teleports list; nudge any NPC with its anchor.
2. Teleport *Landing* in survival → "Harvest Hall" discovery title, welcome line, arrow to the Warden.
3. Harvest a row fast → Rhythm bar, notes climb; at 20/50/100 tier-up chord. Stop → drains, bass note.
4. Events → *Pop a Prize Crop* while looking at a crop → click it; second player can snatch after 4 s.
5. Talk to Hattie → deliver an order (Progression → give crops via creative), sell a prize.
6. Talk to Bram → bake a Loaf, eat it → Warden board shows the buff.
7. Progression → Mastery → III → Gus's ledger; harvest to cross a tier for the title.
8. Events → Bee Bloom inside a field → yellow bar, pollen, "Bee Bloom +1" action bar on harvests.
9. Progression → Eldervale loadout → `/skills farming` shows the five new skills with effect lines;
   Soil Sense stats only apply inside the footprint.
10. Bird scare on the isle with Bird Law → bold crows leave on one click, Golden Hour longer.

## Deploy (only when asked)

Build: `mvn -o -pl AetherionFarming -am package` → Core, Items, Farming jars. Ship **all three together**
(Items calls new `FarmAccess` methods; guarded, but the features need the new Core + Farming).

On MMO-R remove the stale `plugins/AetherionFarming.jar` (09-25) — with both it and
`AetherionFarming-1.0.0.jar` present Paper picks one ("Ambiguous plugin name").

Config: new keys are added by `copyDefaults`; no live key is changed. New files appear on first boot:
`isle-players.yml`, `isle-cast.yml`.
