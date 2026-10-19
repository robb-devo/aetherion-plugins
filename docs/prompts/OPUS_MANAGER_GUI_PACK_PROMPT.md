# Opus prompt — Aetherion Manager GUI (clean fantasy pixel)

Copy everything below the line into a **new** Opus chat.
Effort: **Extra** (not Max — stay on GUI chrome only).
Scope is tiny. Do not wander into item icons, bosses, maps, shaders.

---

```text
# AETHERION MANAGER GUI — elevate + unify (pack only + tiny title wiring)

## Pack truth
Live pack root: `C:\Users\Robbi\IdeaProjects\aetherion-texturepack`
Manager overlay already exists in Items pack twin:
`C:\Users\Robbi\IdeaProjects\AetherionItems\resourcepack\`

Canonical overlay today:
- `assets/minecraft/textures/font/gui_aetherion_manager.png` (256×256 RGBA)
- Font: `assets/minecraft/font/default.json`
  - `\uE005` = space advance −48
  - `\uE004` = manager bitmap (ascent 24, height 256)
- Hub Java: `AetherionManagerGUI.TITLE = Component.text("\uE005\uE004")` (54-slot chest)

Also keep tooltip rarity frames under `assets/aetherion/textures/gui/sprites/tooltip/` (common→mythic) — same visual family, do not delete.

Deliver into **both** pack trees (texturepack + Items/resourcepack) so they stay aligned. Or ship one zip + apply notes.

## Mission (ONLY this)
1. Redesign `gui_aetherion_manager.png` into **extremely clean fantasy pixel** — still readable Minecraft chest GUI, not busy Hypixel spam.
2. Roll that same chrome language across **every Manager category** (same family, tiny accent diffs OK: skills/recipes/storage/etc.).
3. Wire matching inventory titles (Adventure Component + new PUA glyphs) so every Manager submenu uses an overlay — not plain `§8…` chest titles.

## Categories to cover (Manager hub tabs → their GUIs)
Hub +: Shop, Bazaar/Market, Auction, Skills, Recipe Book (+ list/detail if same size), Bestiary, Collection, Pets, Journal hub / Boss Journal / Chronicle, Stats, Storage, Loadouts, Spawn (if chest UI), Craft entry if chest, Guild menu if opened from Manager.
**Do NOT redesign** Booster Socket / Custom Anvil layout (LOCKED). Optional: matching outer frame only if it doesn’t touch socket slots.

54-slot overlays share one template; 45/27-slot need shorter panels that still match the language.

## Style bar
- Clean fantasy pixel: dark stone / soft aether-lilac accents (palette already leans charcoal + lilac title plate)
- Crisp bevels, quiet ornament (thin runes / corner filigree / soft inner glow) — **not** noisy borders
- Slot wells must stay item-readable
- Title plate readable at GUI scale 2–4
- One shared language; accents = hue/motif only (e.g. Skills cooler, Storage warmer, Journal parchment-ish) — still obviously Aetherion Manager

## Allowed work
- New PNGs under `textures/font/gui_aetherion_*.png`
- Extend `font/default.json` with `\uE010+` (keep `\uE004/\uE005/\uE001` working)
- Minimal Java: replace Manager-family inventory titles with `Component.text("\uE005\uE0xx")` like the hub — **no** layout/slot/logic changes
- Short `GUI_NOTES.md` (glyph map + which class uses which)

## Forbidden
- Item/weapon/armor retextures, CMD changes
- DevMenu, TalkUx, ranks, boosters/anvil socket mechanics, Gravwell/Blossom
- Full UI framework rewrite, new plugins, deploy, long research essays
- Touching live servers

## Done when
Opening `/manager` (or Manager key) and clicking through categories feels like one authored UI family — hub was the prototype, everything else matches, looks premium and clean.

Go make the chrome expensive. Stay in this lane.
```
