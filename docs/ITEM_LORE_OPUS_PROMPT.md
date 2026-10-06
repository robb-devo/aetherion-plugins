# Aetherion — Item Lore + Tooltip Design Mega-Chat

Workspace root (open this folder, nothing above it):

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Strongest model. Resources unlimited — quality is the only score. Stay in this root. **No deploy, no restart, no commit** unless Robb says go.

This chat is **item presentation only**. Do not do story quests, world build, or NPC dialog here.

## Goal

Make every Aetherion item tooltip so clean, simple, and effective that Robb laughs because it finally just *works*. Cleaner and more consistent than Hypixel — not random, not bloated, instantly readable.

Cover **all** Aetherion items: weapons, armor, tools, gather gear, boosters, cores, summons, lore/quest items, cosmetics, god items, dungeon drops — everything factories create. Not “a few swords.”

## Read / scout first

Core presentation pipeline (this is the spine — elevate it, don’t invent a parallel system):

- `AetherionItems/src/main/java/de/aetherion/items/item/LoreLayout.java`
- `AetherionItems/src/main/java/de/aetherion/items/item/ItemLore.java`
- `AetherionItems/src/main/java/de/aetherion/items/item/ItemPresentation.java`
- `AetherionItems/src/main/java/de/aetherion/items/item/ItemFlavor.java`
- `AetherionItems/src/main/java/de/aetherion/items/item/TooltipStyle.java`
- `AetherionItems/src/main/java/de/aetherion/items/item/CustomItem.java` (+ other `*Items.java` factories)
- `AetherionItems/src/main/java/de/aetherion/items/model/Rarity.java`

Optional texture side:

- Briefing pack folder: `opus_texturepack_briefing/` (prompt, CSVs, `style_reference/`, `current_pack.zip`)
- Tracked pack write target: `AetherionItems/resourcepack/`
- Live pack reference (outside repo, read-only if needed): `C:\Users\Robbi\IdeaProjects\Aetherion_texturepack.zip` (pack_format 34, CustomModelData)

## What “top tier” means here

1. **One layout for everything.** Same section order on every item. Same stat order every time a stat appears. No random rearrangements.
2. **Scan in under one second.** Name → type → stats → short flavor → extras/boosters → rarity/footer. If a line doesn’t help, cut it.
3. **Not bloated.** Kill duplicate lines, filler adjectives, triple-empty gaps, “mythical unknown origin” spam, redundant rarity headers.
4. **Flavor with kick.** Short. Sharp. Funny / serious / heartbreaking when it fits the item — never cryptic, never thesaurus flex.
5. **Stats readable.** Icons + labels + numbers aligned by convention. Same decimal style. Same `+` / `%` rules. Same color language.
6. **Identity stays.** Item ids, CMD, materials, actual stat *values*, and combat behavior stay. This is presentation + prose, not a silent rebalance.
7. **Better than Hypixel clutter.** Hypixel-clean means hierarchy and restraint — not more rainbow noise.

Propose a written **Tooltip Spec** in `docs/ITEM_TOOLTIP_SPEC.md` first (one page: section order, stat order, color rules, flavor length, rarity footer). Then implement against that spec across the pipeline + factories so nothing drifts.

## Suggested layout (starting point — refine if you can make it cleaner)

```text
Name (rarity-colored)
Type / slot label

⚔ Damage …
❤ Health …
… (fixed stat order — always the same)

Flavor — 1 short line, maybe 2 max for flagships

Ability / unique — only if real
Booster sockets / applied boosters — compact, never a novel
Level / req — if any
Rarity footer + value — once, bottom
```

Every weapon, armor piece, and tool must use that same bones. Gather items and lore items use the same bones with empty sections omitted (no blank graveyards).

## Locked (do not break)

- TalkUx shell
- Boosters: stay stackable (`MAX_STACK = 64`); booster **stat info stays in lore**; don’t rename ids / strip intended effects
- Custom Anvil / 14 sockets system
- Ranks / wipes / Admin identity
- Blossom Blade (`ashen_katana`) and Gravwell Cleaver: **no ability / VFX / combat changes**. Display name + lore presentation polish is OK if ids/CMD/models stay
- Borderlands vials stackable
- Additive ship: don’t delete Special Sets / Test Gear / T2 uniques / DevMenu pages while “cleaning”

## Texture pack (optional bonus)

If while doing lore you see a clear win (“this icon makes the tooltip hit harder”), you may improve art in:

- Prefer writing into `AetherionItems/resourcepack/` and/or delivering zips under `opus_texturepack_briefing/`
- Keep 32×32, pack_format 34, CustomModelData system
- Do **not** require a full pack rebuild to ship the lore pass — textures are bonus, lore is the mission
- Don’t burn the session walking the entire live zip; use `style_reference/` + targeted PNGs

## Process

1. Scout current layout + 15–20 representative items (starter, mid, mythic, booster, lore item, signature weapon, gather tool).
2. Write `docs/ITEM_TOOLTIP_SPEC.md` — the law.
3. Implement the law in `LoreLayout` / `ItemLore` / `ItemPresentation` / `ItemFlavor` so factories inherit unity.
4. Sweep factories / one-off lores that bypass the pipeline until everything matches.
5. Compile Items (and Core only if you truly must). Fix breaks.
6. End with: before/after examples (5 items), what stayed LOCKED, any texture bonuses, how Robb spot-checks in DevMenu.

## Done when

- One consistent tooltip design across Aetherion items
- Stats always in the same order
- Flavor is short and elite
- Bloat gone
- Spec doc exists
- Modules compile
- No deploy / restart / commit

If something LOCKED would need to change for the design: stop, say `LOCKED … SYSTEM CONFLICT`, wait.

Start now. Make Robb laugh because it’s stupidly clean.
