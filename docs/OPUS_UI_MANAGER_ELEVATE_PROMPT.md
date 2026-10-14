# Opus Prompt — Manager UI Elevate (chest chrome + leftover wiring)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Effort:** **Max**. Parallel-safe with the Pack World session (different files).  
**Tree:** `mining-eldervale-progression` + live pack twin `C:\Users\Robbi\IdeaProjects\aetherion-texturepack`.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md). Ship pack to both trees. Deploy Items (+ Hub/Aethermobs only if you wire their titles) when green; restart MMOR once. No commit unless asked.

---

## Mission

Make every **Manager-family chest UI** feel like one premium Aetherion product: same chrome language, no legacy plain titles left on player-facing Manager paths, art crisp at GUI scale 2–4.

---

## Already shipped (do not redo from scratch)

- Manager glyph family + `ManagerChrome` + `GUI_NOTES.md` (see `AetherionItems/resourcepack/GUI_NOTES.md`)
- Hub / Shop / Market / Skills / Codex / Journal / Stats / Storage / Loadouts / Spawns / Pets overlays exist
- BarChrome bossbars = **other session / already live** — out of scope here

## Gaps to close

1. **Legacy titles still plain:** Aetherlex, Pet Details (and any Manager-opened submenu still on `§8…` without overlay). Wire via `ManagerChrome` + matching glyph if missing.
2. **Recipes glyph** (`gui_aetherion_recipes` / `\uE01B`) — confirm wired end-to-end in `RecipeBookGUI`; fix if not.
3. **Art elevate pass** on the whole Manager family: cleaner fantasy pixel, slot wells readable, accent hues per category (notes already list accents). Prefer `tools/gen_gui_chrome.py` so both packs stay generated, not hand-drifted one-offs.
4. **QA walk:** `/manager` → every tab + one deep page each. Screenshot checklist in end note.

## LOCKED

TalkUx shell · Booster Socket / Custom Anvil layout & mechanics · ranks · Blossom/Gravwell · ShutdownCountdown · DevMenu redesign · gameplay formulas.

## Allowed

- Pack PNGs + `font/default.json` PUA glyphs (both pack trees)
- Minimal Java: titles / `ManagerChrome` constants / holder title strings only — **no** slot layout rewrites unless a title literally cannot attach
- Short update to `GUI_NOTES.md`

## Forbidden

- Item/weapon retextures (that’s Session B)
- Iris/shaders, world map art, tutorial quest rewrite
- Touching onboarding quest logic

## Done when

Opening Manager and clicking through feels like **one authored UI**. No plain vanilla chest titles on Manager paths players hit during onboarding (Skills, Codex, Storage, Pets, Recipes, Journal).

## End note (≤15 lines)

What you rewired, glyphs added, pack ship path, jars restarted.
