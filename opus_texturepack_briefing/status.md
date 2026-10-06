# Status — Aetherion texture pack briefing

Date: 2026-10-03
Canonical live pack: `C:\Users\Robbi\IdeaProjects\Aetherion_texturepack.zip`
SHA1: `446a98ab072ed1844572883aa9ca5b61f1c28744` (matches live served pack; URL omitted)
pack_format: **34** (MC 1.21.x / Paper)
Item system: **AetherionItems** (PDC item id + CustomModelData). Not ItemsAdder/Nexo.

## Counts
| Slice | Count |
|-------|------:|
| Pack item PNGs | 333 |
| Already 32x32 | 2 |
| Need rework/downscale | 331 |
| Pack armor-ish | 132 |
| Pack non-armor (Run 1) | 201 |
| Java id+CMD pairs | 112 |
| … texture found | 94 |
| … texture FEHLT | 18 |
| … armor | 53 |
| … non-armor | 59 |
| Model CMD overrides | 255 |

## Resolution histogram
- 64x64: 159
- 16x16: 152
- 256x256: 19
- 32x32: 2
- 48x48: 1

## Pipeline
1. Java sets item_id + CustomModelData on vanilla Material
2. Pack models/item/<material>.json CMD → model → textures/item/*.png
3. Worn armor often CIT (Run 2). Inventory icons are still item PNGs.

## Priority
- Run 1: non-armor → 32x32 first-party quality
- Run 2: armor sets + layers later

## Oddities
- Mostly 64x64 icons (too heavy)
- Variant suffixes _2/_3/t1-t5
- Signature weapons: texture OK, combat code LOCKED
- Boosters: texture OK, stack/stats LOCKED
