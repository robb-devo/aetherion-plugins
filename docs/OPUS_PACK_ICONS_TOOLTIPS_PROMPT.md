# Opus Prompt — Pack Icons + Tooltip Polish (world items, not Manager chrome)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Effort:** **Max**. Parallel-safe with Manager UI Elevate (do **not** edit `gui_aetherion_*.png` / Manager font glyphs).  
**Pack roots (keep twins aligned):**  
- `C:\Users\Robbi\IdeaProjects\aetherion-texturepack`  
- `…/mining-eldervale-progression-65660c/AetherionItems/resourcepack/`  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md). Ship pack zip / sync both trees. Plugin jar only if OptiFine/CMD wiring must change. Prefer **pack-only**. No commit unless asked.

---

## Mission

Raise **held / inventory item presentation** and **tooltip rarity chrome** to the same premium bar as the Manager GUIs — so the world (hotbar, drops, Lore) matches the menus. Not a full item retexture of every ID; an authored **pass on what players see every minute**.

---

## Audit first (then pick the highest-traffic set)

1. Tooltip rarity frames: `assets/aetherion/textures/gui/sprites/tooltip/` (common→mythic) — elevate family, keep readable.
2. Hotbar-critical icons: starter kit tools, Nether Star / Manager opener if custom, skill-relevant picks/axes/hoes, common boosters (visual only — **stack/rules LOCKED**).
3. Mid-game “screenshot” items staff notice (Verdant Eye / Starcore-class drops, cleaver/pet-adjacent if already custom) — polish inconsistency, don’t invent new CMDs for vanilla.
4. Font/glyph hygiene for **non-Manager** pack text if broken (do not restyle BarChrome frames unless a clear pack bug).
5. Optional micro: still/flow water textures if the official 1.21.1 pack is actually missing/wrong (don’t chase Via/newer-client ghosts).

## Style bar

- Clean fantasy pixel, Aetherion charcoal + lilac/aether accents
- Icons readable at 16×16 and GUI scale 2–4
- Same hand across a family (tools / charms / eyes) — no random style clash
- Lore stays code-owned; you don’t rewrite balance text

## LOCKED

Manager chest overlays (`gui_aetherion_*`) · TalkUx · Anvil sockets / booster mechanics · ranks · Blossom Blade / Gravwell combat feel & models · signature ability VFX · ShutdownCountdown · tutorial quests

## Allowed

- PNG/properties under item + tooltip paths in **both** pack trees
- `cit` / `optifine` / `items` JSON only as already used by the pack
- Tiny Java **only** if a custom model data / key is missing for an icon that already exists in code — prefer pack-only

## Forbidden

- Redesigning Manager GUIs (Session A)
- Mass renaming item ids, economy changes, recipe rewrites
- Exile map / worldedit art

## Done when

Hotbar + opened tooltip on common→mythic gear feels **intentionally Aetherion**, not vanilla-mixed. Document which ids got art. Ship pack so live `:8088` (or current pack host) can pick it up — note the path for Robb.

## End note (≤15 lines)

Ids touched · tooltip frames yes/no · pack ship steps · anything needing Robb’s eyes in-game.
