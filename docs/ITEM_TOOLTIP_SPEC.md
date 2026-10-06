# Aetherion Item Tooltip Spec

The law for every item with an Aetherion id. `LoreLayout` enforces it on every create and every refresh,
so factories only supply facts (stats, ability text, usage). Nothing outside this shape ships.

## Layout

Fixed order. Empty sections vanish. Exactly one blank line between sections, never two.

```text
Gravwell Cleaver                  ← name: rarity color, no ✦ decorations (GOD items: rainbow)
Sword                             ← type: §8, the real slot ("Mining Pickaxe", "Core Booster")

⚔ Damage: +80                     ← stats: fixed order, zeros hidden
✧ Crit Chance: +13%
⚡ Throw: 15s                      ← properties: item-only numbers, right under stats

The gate keeps a toll.            ← flavor: §7§o, one line (two for flagships)
Torn from the Pathwarden.         ← origin: §8§o, optional

✦ Event Horizon                   ← ability: §6 ✦ header, block stays together
Right-click: open a gravity well, ← §7 text, §f numbers, wraps at 46 visible chars
pull hostiles in, then detonate.
Players & pets ignored. CD 5s.    ← §8 meta line
✦ Full Set: Second Dawn           ← set bonus: same shape

Right-click to open.              ← use: §e verb + §7 rest (non-gear items)

Boosters 3/14                     ← §8 header, §f count
● Redstone x3                     ← applied only, booster color
Fits: Coal · Iron · Gold          ← §8 remaining types, max 2 lines, gone when full

Hoe Level: 12/50 + bar            ← progress / dungeon: only real state, max 3 lines

Value: 120 coins each             ← value: once (`ValueLore`; `AH value: N coins` when it cannot be sold)
LEGENDARY                         ← rarity: §l rarity color, always the last line
```

## Stat order

`⚔ Damage · ⚔ Attack Spread · ✧ Crit Chance · ✧ Crit Damage · ☠ Undead Damage · ❤ Health · 🛡 Defense ·
☠ Undead Resist · ✦ Speed · ⛏ Mining Power · 💎 Fortune · ✦ Spread · 🌾 Harvest · 🎣 Fish Speed ·
🐟 Fish Catch · ☘ Catch Rate`

Line shape `§7<icon> <Label>: §f+<value>`. `%` on Crit Chance, Crit Damage, Undead Damage/Resist, Speed,
Catch Rate. A stat at 0 is not printed. Dungeon gear may append its in-dungeon value as ` §8(+x)`.

## Numbers

Integers plain (`+12`). Decimals: at most 2, trailing zeros cut (`+1.5`, `+0.35`). Thousands with `,`
(`12,400`). Durations `8s`, `15m`.

## Color language (complete list)

| Meaning | Code |
|---|---|
| Name, rarity footer | rarity color |
| Type, origin, notes, separators | `§8` |
| Stat labels, descriptions | `§7` |
| Values and numbers | `§f` |
| Ability / set header | `§6✦` |
| Input verbs (Right-click, Sneak) | `§e` |
| Flavor | `§7§o` |
| Heal / harm accents inside text | `§a` / `§c`, sparingly |

Booster emblems keep their booster color. Nothing else gets a color.

## Flavor

One line, aim ≤ 40 characters (hard cap: two wrapped lines). Ends with punctuation. Funny, grim or
heartbreaking when the item earns it. Never vague, never a thesaurus, never the same joke twice.
Priority: curated line in `ItemFlavor` > the item's own quip from its factory > generic set line.

## Always cut

Rarity headers (`✦ RARE`), type banners (`Aetherion Mining Set`, `⛏ Mining Tool II`, `Starter Tool`),
"Off-hand accessory" echoes, zero stats, duplicate stat lines, empty booster chips by name, two blank
lines in a row, rarity text anywhere but the last line.

## Refresh

Owned items rebuild from their factory lore on every refresh (`LoreTemplates`), then stats, boosters,
tool levels, cores and dungeon state are re-applied from the item's own data. Old shredded lore heals.
The rebuild always runs in English; the language layer below is the last step.

## Languages (English + German)

English is canonical in code. German is a last, reversible pass over the finished English layout, so
`LoreLayout`, every factory and every mutator keep working on English only.

**Bridge.** Core owns `LangAccess` (`AetherServices.registerLang / lang() / clearLang / german(player)`).
Quests registers its player-language store on enable and clears it on disable. Items only reads the bridge.
Quests missing or offline → everyone gets English, no errors. Items never imports Quests.

**Data.** `AetherionItems/src/main/resources/lang/de.yml`, loaded by `LoreLocale`:

| Section | What | Lookup |
|---|---|---|
| `glossary:` | `stat`, `slot` (exact type lines), `word` (fallback word map), `rarity`, `booster`, `chrome` (`boosters`, `fits`) | `LoreGlossary`, the one central glossary |
| `names:` | `{en, de}` plain display names, resource words, hole words | exact name |
| `name_patterns:` | `{en, de}` with `{0}` `{1}` holes (`Upgrade Stone {0}`) | after exact names |
| `text:` | `{en, de}` coloured prose (flavor, origin, ability, use, notes), keyed by the English text; up to 4 wrapped lines joined with one space | exact, longest group first |
| `patterns:` | `{en, de}` for lines with numbers or names (`Value: {0} coins each`); decimal separators swap in the holes | longest literal first |
| `books:`, `authors:` | pages per find id, author names for readable story finds | `LoreLocale.localizeBook` |

The key layout is English-text-keyed lists (not `flavor.<id>` keys): one German line serves every item that
shares the English line, and the same table runs backwards (German → English) so mutators can read it.

**Rules.**
- Machine lines (stat rows, type line, Boosters header/rows, Fits, rarity footer, booster value rows) come
  from the glossary only, so one tooltip never mixes English and German labels.
- Missing entry → that line (or that key) stays English. Nothing else changes.
- Unchanged in German: ids, CMD, materials, numbers, colour codes, glyphs, rarity colours, combat values.
- Decimals use `,` in German only where the English has `.`; thousands `1,200` → `1.200`.
- German voice: informal "du", same joke/beat count as the English line, at most ~25 % longer
  (the layout re-wraps at 46 visible characters).
- Proper nouns stay English (Aetherion, Eldervale, Pathwarden, Hollow Lurker, Registry, Warden's Prison, …).

**State.** An item showing German carries PDC `lore_lang = de`. `lore_cache` always holds the English lore.
Every mutator reads `LoreLocale.canonicalLore / loreOrEmpty` and writes `LoreLocale.writeLore`, so a German
item survives level-ups, boosters, cores and value refreshes.

**Heal trigger (exact).** Items are re-presented in the holder's language
1. when the player changes language (`PlayerLang.set` → `AetherServices.notifyLangChanged` →
   `LoreLocaleListener`: the whole inventory, EN→DE and DE→EN),
2. on join (the existing 1 s `GearTooltip` refresh),
3. one tick after pickup, hotbar switch, inventory click or inventory close, for items whose tag does not
   match the player's language.
Old pre-pass items have no tag: they count as English, rebuild from template and then follow 1–3.

**Known limits.** Shop/trader icons that append their own chrome and auction/market items (shown in the
seller's language) are outside this pass.
