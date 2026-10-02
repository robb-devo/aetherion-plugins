# Player UI — Handoff for Claude Opus

Read **`OPUS_PLAYER_UI_AUDIT.md`** first, then this file, then open only the files below.

## Goal

Make the **permanent sidebar + TAB list** feel more premium and intentional — same systems, sharper presentation — **without** a UI framework rewrite.

## Must understand

### AetherionItems (data + paint)
- `src/main/java/de/aetherion/items/rank/RankBadgeService.java`
- `src/main/java/de/aetherion/items/rank/TabScoreboardRebind.java`
- `src/main/java/de/aetherion/items/rank/CelestialDye.java` / `CitrusDye.java` / `RainbowDye.java` (**read-only** unless a bug blocks polish)
- `src/main/java/de/aetherion/items/placeholder/CoinPlaceholderExpansion.java`
- `src/main/java/de/aetherion/items/skill/AetherionLevel.java`
- `src/main/java/de/aetherion/items/skill/SkillService.java` — only `tabSlotLine` / `tabExtraLine` if considering skill lines (prefer **not**)

### AetherionQuests (optional quest crumb)
- `src/main/java/de/aetherion/quests/placeholder/QuestPlaceholderExpansion.java`
- Skim `QuestCompass` / `QuestHint` so ActionBar and sidebar don’t duplicate loudly

### Live TAB (presentation)
On the deploy target (or a synced copy), not necessarily in git:
- `plugins/TAB/config.yml` — `scoreboard`, `header-footer`, `scoreboard-teams`, refresh intervals
- `plugins/TAB/groups.yml`
- `plugins/TAB/animations.yml` — especially `Aetherion` and rank animations

Treat live TAB YAML as first-class deliverable if you change presentation.

## Likely touch targets

| Priority | Target | Why |
|----------|--------|-----|
| High | `TAB/config.yml` scoreboard lines + header/footer | What players see; fastest quality win |
| High | New/adjusted PAPI keys only if needed for conditional lines | Keep logic in Items/Quests, layout in TAB |
| Med | Placeholder refresh intervals | Perf + snappiness |
| Med | Optional second scoreboard with `display-condition` | Only with a crisp condition |
| Low | Tiny helpers in `CoinPlaceholderExpansion` / quest expansion | e.g. blank-safe combined lines |
| Avoid | Rank dye algorithms, Admin UUID, Dev Menu ranks | LOCKED |

## Do not ingest whole monorepo

Ignore BossEngine fight directors, item ability listeners, farming rituals, etc., except when a placeholder already exposes their data.

## Locked / conflict rules

If a change would alter special-rank dyes, Admin-only rules, OP≠Admin, or Dev-Menu-only assignment → **stop**, report `LOCKED RANK SYSTEM CONFLICT`, wait.

If a change would replace TAB with a custom scoreboard stack → **stop**, report scope conflict.

## Success bar

A wiped early-game player should feel:
- Clear who they are (rank / level) without clutter  
- Clear where they are (area)  
- Clear economy pulse (coins)  
- Optional: what quest wants next — one line max  
- TAB header/footer that feels like **Aetherion mmor**, not a generic lobby paste  

Someone with Monkey/Citrus/Admin must look **at least as good as today**.

## Out of scope

- Inventory / manager GUIs  
- Item lore polish  
- BossBar as permanent HUD  
- Guild island TAB layout redesign (leave unless broken)  
- Gameplay / economy / progression numbers  
- Production deploy unless the workspace already has an approved path (prefer branch + notes)

## Deliverables when done

1. Code/config diffs (TAB YAML + any small PAPI helpers)  
2. Short note: **changed** vs **consciously unchanged**  
3. How to verify in-game (with/without ultra rank; area change; optional active quest)
