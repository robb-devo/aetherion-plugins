# Opus — Item Lore Finish Pass (EN + DE, fully clean)

Workspace root (open this folder, nothing above it):

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Strongest model. Stay in this root.  
**No deploy. No restart. No commit.** Edit source directly. Follow `docs/OPUS_PROMPT_HYGIENE.md`.

This chat is **item presentation only**. No story quests, no world build, no NPC dialog, no Exile map.

---

## Mission

Close the item tooltip system for real — **English and German**, same bones, same kick.

1. Kill the **EN leftovers** from the Oct 4 lore ship.
2. Ship **full German** item lore (informal **du**, same layout law, same beat count).
3. Keep **English** elite — DE must not regress EN; both languages are first-class.
4. Finish the **three cleanup extras** below so nothing half-old survives in factories.

Done = Robb can:

- `/language en` → every DevMenu shelf tooltip is clean EN under `ITEM_TOOLTIP_SPEC`
- `/sprache de` → same items, clean DE, **zero English leak** in player-facing labels/flavor/ability/use/type/rarity words
- Old inventory items **heal** after a language flip (refresh path)

Layout spine stays: `docs/ITEM_TOOLTIP_SPEC.md` + `LoreLayout` / `ItemLore` / `LoreTemplates`. Elevate, don’t invent a parallel system.

---

## Read first (law + spine)

| File | Why |
|---|---|
| `docs/ITEM_TOOLTIP_SPEC.md` | Layout law (still says “English only for now” — replace with EN+DE rules) |
| `docs/ITEM_LORE_OPUS_PROMPT.md` | Original mega-pass intent |
| `docs/OPUS_PROMPT_HYGIENE.md` | No ship theater |
| `AetherionItems/.../item/LoreLayout.java` | Enforcer |
| `AetherionItems/.../item/ItemLore.java` | Pipeline |
| `AetherionItems/.../item/LoreTemplates.java` | Refresh heal |
| `AetherionItems/.../item/ItemFlavor.java` | Curated EN flavor |
| `AetherionItems/.../item/ItemPresentation.java` | Presentation helpers |
| `AetherionItems/.../item/ItemSlotLabel.java` | Type / slot labels |
| `AetherionItems/.../item/CustomItem.java` | Factories (wands, upgrade stones, hunter stub, old headers) |
| `AetherionItems/.../dungeon/DungeonArmor.java` | Assassin lore table |
| `AetherionItems/.../lore/StoryLore.java` + `StoryBooks.java` | Story finds / books |
| `AetherionQuests/.../lang/PlayerLang.java` + `LangPack.java` | `/language` · `/sprache` |
| `AetherionCore/.../api/AetherServices.java` | Thin register pattern (`StoryAccess`) |

---

## Part A — EN leftovers (first, small, compile-safe)

### A1 · Dungeon Assassin table too long
- `DungeonArmor.java` → `refreshAssassinLore` / `assassinLore`
- Compress to the law: short ability block, wrap ≤ 46 visible chars, no novel, no duplicate draw/fire spam
- Numbers / CD truth stay. Presentation only. **EN + DE** both short when Part B lands

### A2 · Upgrade-Stone wrap bug
- `CustomItem.createBlueprintUpgradeStone2/3/4` (+ any `BlueprintUpgrade` lore path)
- Fix mid-word / mid-thought wrap. Clean at 46-char wrap in **both** languages

### A3 · Wand duplicate Lightning / Fire / Frost lines
- `createCopperRod`, `createFrostShard`, `createEmberRod`, wooden rod, any other wand still hand-building old element lines **and** pipeline stats
- One source of truth — never duplicate damage
- Strip old rarity headers on those factories (see also Part C1)

### A4 · `hunter_crossbow` missing real item
- `createHunterCrossbow()` currently returns plain `Material.LEATHER` with **no** id
- `ItemSlotLabel` already has `"hunter_crossbow" → Bow`
- `ItemManager` treats id as legacy **bow** (keep that)
- Restore a real giveable BOW: id `hunter_crossbow`, pipeline lore, `createById` / DevMenu wired if missing
- No new combat system. Sane stats near sibling bows. **EN + DE** flavor/name

### A5 · Spec doc
- Update `ITEM_TOOLTIP_SPEC.md`: remove “English only for now”; add short **EN + DE** rules (bridge, glossary, refresh heal)

---

## Part B — Full bilingual lore (EN canonical, DE complete)

### B1 · Language bridge (thin Core — required)

Items **must not** depend on Quests. Quests owns `/language` + preference.

- Add `de.aetherion.core.api.LangAccess` (`german(Player)`, optional `code(Player)`)
- `AetherServices.registerLang` / `lang()` / `clearLang`
- Quests registers from `PlayerLang` on enable; clears on disable
- Items reads `AetherServices.lang()` when building/refreshing for a viewer
- Quests offline → English. No crash

### B2 · Both languages, first-class

| Surface | EN | DE (`/sprache`) |
|---|---|---|
| Display names | Canonical in code | Curated DE (prefer full coverage for player gear) |
| Type / slot | Yes | Yes |
| Stat labels | Yes | Yes — **one shared glossary** (Part C2) |
| Flavor / origin | `ItemFlavor` + factories | Same kick, **du**, same length budget |
| Ability / set / use | Yes | Yes — no EN words inside DE prose |
| Boosters / fits / progress / value | Yes | Yes |
| Rarity footer word | `LEGENDARY` … | `LEGENDÄR` … |
| Story finds / books | `StoryLore` / `StoryBooks` | Full DE parity |

Unchanged: ids, CMD, materials, numbers, icons, rarity **colors**, combat values, booster stack 64.

### B3 · String homes

- **EN** = canonical in code (`ItemFlavor`, factories, pipeline defaults)
- **DE** = `AetherionItems/src/main/resources/lang/de.yml`  
  keys like `flavor.<id>`, `name.<id>`, `ability.<id>`, `stat.damage`, `slot.sword`, `rarity.legendary`, …
- Missing DE key → English fallback for **that key only** (never blank lore)
- Refresh (`LoreTemplates` / owned rebuild) re-applies language for the viewer after `/sprache` / `/language`

### B4 · Coverage sweep (mandatory)

Every Aetherion factory path:

Weapons · armor · tools · gather · boosters · cores · summons · cosmetics · god/test · dungeon · schematics · story finds/books · upgrade stones / blueprints

DevMenu spot-check both languages: Starter, Progression, T1/T2, Dungeon, Boosters, Lore & Chronicle, gather tools.

### B5 · Tone

- EN: already the law — keep it sharp
- DE: informal **du**, dry, funny/grim when EN earns it, same flavor beat count, no thesaurus flex

---

## Part C — Three extras (mandatory in this same pass)

### C1 · Factory leftover header sweep
While touching factories, **kill** every leftover hand-authored rarity banner still sitting in lore lists, e.g.:

- `§7✦ §aCOMMON` / `§7✦ §7UNCOMMON` / `§7✦ §bRARE` / `§7✦ §6LEGENDARY` / similar
- Old type banners that the spec forbids (`Aetherion Mining Set`, duplicate slot echoes, etc.)

Rarity belongs **only** on the footer line (pipeline). Grep `CustomItem` + gather item factories + dungeon factories. Don’t leave half the shelf clean and half vintage.

### C2 · Shared DE glossary (hard)
One central table for:

- Stat labels (`Damage` → `Schaden`, `Crit Chance` → …, full stat-order list from the spec)
- Slot / type labels (`Sword`, `Mining Pickaxe`, `Core Booster`, …)
- Rarity footer words
- Common chrome (`Boosters`, `Fits`, `Sells for`, `Right-click`, …)

No mixed “Damage” next to “Schaden” on one tooltip. EN glossary stays the code defaults; DE maps 1:1.

### C3 · Refresh-heal verification
Explicitly verify (and fix if broken):

1. Give item on EN → flip `/sprache de` → lore becomes DE (or next refresh/open rebuilds DE)
2. Flip back to EN → lore becomes EN again
3. Old pre-pass items in inventory heal to the current law (no shredded double headers)

Document the exact trigger in the end note (join refresh, inventory open, `/aquest`-style — whatever you wire). Must work without relogging if the pipeline already refreshes on open; if not, hook the lightest existing refresh path.

---

## Locked (stop if conflict)

- TalkUx shell  
- Boosters stackable `MAX_STACK = 64`; booster stats stay in lore  
- Custom Anvil / 14 sockets  
- Ranks / wipes / Admin = Robb only  
- Blossom Blade (`ashen_katana`) + Gravwell Cleaver: **no ability / VFX / combat changes** — EN/DE lore presentation OK if ids/CMD/models stay  
- Borderlands vials stackable  
- Additive ship: do not delete Special Sets / Test Gear / T2 / DevMenu pages  

Conflict → `LOCKED … SYSTEM CONFLICT` and wait.

---

## Process

1. Part A (A1–A4)  
2. Part C1 while grepping factories (headers)  
3. Part B1 Core `LangAccess` + Quests register  
4. Part B2–B5 + C2 glossary + full `lang/de.yml`  
5. Part C3 refresh-heal  
6. Update `ITEM_TOOLTIP_SPEC.md` (EN + DE)  
7. Compile Items (+ Core/Quests if touched). `.m2` blocked → one line, stop  
8. End note for Robb: A-fixes, DE key layout, glossary location, refresh trigger, 8-item spot-check list for EN and DE. **No deploy.**

---

## Done when

- [ ] Assassin lore short (EN + DE)  
- [ ] Upgrade stones wrap clean (EN + DE)  
- [ ] Wands: no duplicate element/damage; no old rarity headers  
- [ ] `hunter_crossbow` real id’d bow again  
- [ ] Factory rarity-header leftovers gone (C1)  
- [ ] Shared DE glossary, no mixed labels (C2)  
- [ ] `/sprache` ↔ `/language` heals owned items (C3)  
- [ ] Full DE coverage across factories + story finds/books  
- [ ] EN still perfect  
- [ ] Spec doc = EN + DE law  
- [ ] Compile OK (or one-line block)  
- [ ] No deploy / restart / commit  

Start with Part A + C1. Then B + C2 + C3. Make it fucking clean — both languages.
