# Opus Prompt — GIGA Texture Pack Elevate (one Max session, pack-only)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Effort:** **Max / Ultrathink OK**. This session exists to **burn balance on art quality**.  
**Output:** a shippable pack zip + clear merge notes. **No plugin logic. No deploy to MMOR unless Robb asks.**  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md) — don’t edit gameplay jars for this run.

---

## Mission

Take the **live Aetherion resource pack** (already works with the shader) and run one **behindert-heftigen** elevate pass so weapons, armors, uniques, charms, Manager GUIs, and HUD chrome look like a top-tier paid MMO pack — same fantasy-pixel DNA, not a random style reset.

---

## Pack truth (start here)

| Asset | Path |
|-------|------|
| **Live / served pack** | `C:\Users\Robbi\IdeaProjects\Aetherion_texturepack.zip` (+ extract twin if present: `Aetherion_texturepack_extract\`, `aetherion-texturepack\`) |
| **Items twin** | `…\mining-eldervale-progression-65660c\AetherionItems\resourcepack\` — keep glyphs/CIT in sync when you change shared assets |
| **Untested Opus pack (charms looked great — mine it)** | `C:\Users\Robbi\IdeaProjects\aetherion-story-p1\opus_texturepack_briefing\Aetherion_texturepack_32x32_run1.zip` (+ `style_reference\`, `opus_prompt.md`, CSVs) |
| **HUD / bar experiments** | `Aetherion_texturepack_HUDBAR.zip`, `Aetherion_texturepack_HUD_READY.zip` — steal what elevates; don’t regress BarChrome math |
| Format | Paper **1.21.1**, `pack_format` **34**, CustomModelData (not ItemsAdder) |

**Method:** work in a **new output folder / zip**. Don’t destroy the live zip until Robb merges. Prefer additive: replace weak PNGs, keep CMDs/ids.

---

## Priority ladder (do in this order)

### P0 — Showcase & boss weapons (non-LOCKED)
- Boss drops, dungeon uniques, midgame showcase blades/staves that still look vanilla or muddy.
- Match rarity: Common quiet → Mythic loud but readable at 16×16 / GUI scale 2–4.
- **LOCKED models (do not redesign CMD/model files):** Blossom Blade (`ashen_katana`) and Gravwell Cleaver (`gravwell_cleaver`) — combat feel/ids/CMD stay. Optional: leave as-is or only micro-polish pixels **without** new CMD/shape. Prefer spending tokens on **other** boss/showcase weapons.

### P1 — Armor sets
- Full set identity (helm/chest/legs/boots read as one family).
- CIT / leather tint / CMD layers as the live pack already uses — don’t invent a second armor system.

### P2 — Charms / accessories / eyes / cores
- **Pull the good charm art** from the untested Opus `32x32_run1` pack into the live tree where better.
- Keep stackable booster **identity** readable (LOCKED: boosters stay stackable; don’t change materials/ids).

### P3 — Manager GUI chrome + fonts
- Elevate `gui_aetherion_*.png` family (see `AetherionItems/resourcepack/GUI_NOTES.md` / `gen_gui_chrome.py`).
- One language across hub tabs; slot wells stay item-readable.
- Do **not** break `BarChrome` / shift glyphs / TextWidth contracts.

### P4 — Player HUD / health / bars
- Elevate HP / defense / action-bar related pack art if present; keep send-throttling and glyph advances valid.
- Bossbar frames already live via Core BarChrome — elevate **art**, don’t rewrite Java.

### P5 — Tools / rods / picks / filler icons
- Only after P0–P2 look trailer-ready. Hotbar literacy > random flair.

---

## Style bar

- Clean fantasy pixel, Aetherion charcoal + lilac/aether accents (existing vibe).
- Readable at hotbar distance. No muddy brown soup. No Hypixel clutter spam.
- Sets and weapon families share a hand. Charms from the Opus run1 pack that already slap → **keep/adapt**, don’t redraw worse.

## LOCKED (gameplay / product)

- TalkUx shell · booster **stack/rules** / Anvil socket **layout** · ranks / `player-ranks.yml` · ShutdownCountdown · Borderlands vials  
- **Blossom Blade / Gravwell Cleaver** — no new CMD, no model swap, no combat/VFX changes  
- No Items.java balance, recipes, or quest rewrites in this session  

## Deliverables

1. `Aetherion_texturepack_GIGA_<date>.zip` (pack_format 34) ready to drop on the pack host.  
2. Short `MERGE_NOTES.md`: what you took from run1 charms, which weapon/armor ids got new art, GUI/HUD files touched, anything Robb must F3+T test first.  
3. Optional before/after PNG sheet for 8–12 hero items (boss weapon + armor set + charm).  

## Done when

Robb can swap the zip, F3+T, and the **hotbar + Manager + one boss weapon + one armor set** feel like a different product tier — without broken CMDs or missing icons.

## End note (≤20 lines)

Zip path · hero ids elevated · charms imported from run1? · GUI/HUD yes/no · risks.
