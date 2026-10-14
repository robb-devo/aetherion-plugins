# Opus Prompt A — Player HUD Polish (Run 1)

**Owner:** Robb (A3therion). Call him Robb.  
**Target:** Paper / Minecraft **1.21.1**, resource pack **`pack_format: 34`**.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md) — edit real tree, no ship folders, no deploy unless asked.  
**Sibling run (later):** [`OPUS_HUD_POLISH_B_BOSSBARS_PROMPT.md`](OPUS_HUD_POLISH_B_BOSSBARS_PROMPT.md) — do **not** restyle bossbars in this run.

---

## Goal

Make the **always-on player HUD** look like a custom MMORPG, not Minecraft.

| Must | Detail |
|------|--------|
| Remove hunger UI | Vanilla food icons gone for every player (pack already forced). Food stays mechanically full (server already locks food=20). |
| Replace hearts | Vanilla hearts replaced / hidden; show **custom HP** that matches the real custom max HP (not “10 hearts = lie”). |
| Defense | Own clear readout, not squashed into HP. |
| Weapon cooldown | Clear, dedicated, preferably **filling / animated** glyph bar — not unicode ▮ jammed on the right. |
| Style | Own textures (+ font negative-space / core shaders if needed). Match Aetherion art language (dark stone, thin accent, tooltip-corner vibe — see Manager chrome notes). |
| Gameplay | **Unchanged** — no combat formula, CD duration, heal, or stat math changes. Presentation + send throttling only. |

---

## Ist-Zustand (do not re-scout — use this)

### Combat ActionBar (canonical)

| Class | Path |
|-------|------|
| Owner | `AetherionItems/src/main/java/de/aetherion/items/listener/HealthListener.java` |
| CD fragment | `AetherionItems/src/main/java/de/aetherion/items/combat/AbilityCooldownHud.java` |
| Pixel pad helper | `AetherionItems/src/main/java/de/aetherion/items/combat/HudWidth.java` |
| Stats source | `AetherionItems/src/main/java/de/aetherion/items/manager/ActiveEquipmentStats.java` (`HEALTH`, `DEFENSE`, …) |
| Register | `AetherionItems` → `registerProgressionAndSocial()` constructs `HealthListener` |

**Refresh today:** scheduler every **2 ticks** → `tickHealthDisplay` → `showHealthBar` → `player.sendActionBar(...)`.

**Current ActionBar layout** (`showHealthBar` ~L383–434):

```
❤ {paddedCurrent} / {max}                      🛡 {defense}  ·  {Label ▮▮▮▯▯▯▯▯ Ns | ✔ ready}
```

- HP: Adventure `Component`, red heart emoji, white current, dark gray `/`, red max.
- Defense: only if `DEFENSE > 0.05`; aqua `🛡` + white number; long space gap from HP.
- CD: `AbilityCooldownHud.fragment(...)` when `showing`; 8 unicode segments; bronze/gold colors; ready flash ~40t + chime; deny pulse ~10t.
- Centering: `HudWidth.balance(lanePx)` pads left so CD on the right does not drag HP/DEF.
- Also: `player.setCooldown(Material, ticks)` hotbar overlay (keep unless you intentionally replace with pack-only CD — do not break pearl/shield ownership tracking in `OVERLAY_OWNER`).

**Vanilla hearts today:** if max HP > 20 → `setHealthScaled(true)`, `setHealthScale(20)`, `sendHealthUpdate()` — **always 10 hearts**, fill = current/max. That is a lie visually; custom max is only in ActionBar text.

**Persistence / regen (DO NOT break):** PDC `stored_player_health`; custom regen cancels vanilla `SATIATED`/`REGEN`; regen still checks `foodLevel >= 18` (always true under HungerListener).

### Hunger (not hidden)

| Class | Path |
|-------|------|
| Lock | `AetherionItems/src/main/java/de/aetherion/items/listener/HungerListener.java` |

Food forced to **20** / sat 20 / exhaustion 0 (join, respawn, 40-tick timer, `FoodLevelChangeEvent` cancel). **Vanilla hunger icons stay visible and full.** There is **no** ProtocolLib / PacketEvents hunger rewrite in this monorepo.

### Competing ActionBars (keep HP winning)

These also send ActionBar; HealthListener’s 2-tick refresh is intentional so they cannot permanently eat the HP line:

- Hub: `AetherionHub/.../origin/OriginCompass.java`, `OriginText.java`
- Quests / exile / cast tips (transient)
- Gear one-shot ability flavor lines
- Fishing/catch feedback (transient)

Do not delete those systems. Your HUD must either (a) stay on a channel they do not fight, or (b) remain high-frequency / composited so HP/DEF/CD stay readable.

### What is NOT player HUD

- Boss / quest / gather / island **BossBars** → Run B.
- Manager chest GUI chrome (font bitmaps) → already shipped; **do not rebuild**.
- TalkUx speech bubble → **LOCKED** — do not touch.
- Blossom Blade / Gravwell Cleaver **abilities/VFX** → **LOCKED** — CD *display* may restyle; do not change arm times / combat feel.
- Boosters, Anvil socket GUI, ranks, ShutdownCountdown → **LOCKED**.

---

## Resource pack — what exists vs missing

### Write targets (both trees stay aligned)

1. **Tracked:** `AetherionItems/resourcepack/` (`pack.mcmeta` → **34**)
2. **Live folder (if present):** `C:\Users\Robbi\IdeaProjects\aetherion-texturepack\`
3. Reference live zip: `C:\Users\Robbi\IdeaProjects\Aetherion_texturepack.zip` (served as `http://play.donnernet.de:8088/Aetherion_texturepack.zip`)

Deliver new HUD assets into **tracked** `AetherionItems/resourcepack/` (and mirror to `aetherion-texturepack/` if you edit that tree). Cursor/Robb syncs the served zip later.

### Already shipped (reuse language, do not trash)

| Asset | Path / notes |
|-------|----------------|
| Manager chrome fonts | `assets/minecraft/font/default.json` — PUA `\uE004`, `\uE010`–`\uE025`, neg space `\uE005` (−48), `\uE007` |
| Chrome bitmaps | `assets/minecraft/textures/font/gui_aetherion_*.png` |
| Notes | `AetherionItems/resourcepack/GUI_NOTES.md`, `docs/OPUS_MANAGER_GUI_PACK_PROMPT.md` |
| Tooltip rarity frames (live pack) | `assets/aetherion/textures/gui/sprites/tooltip/` — **not** all in tracked; keep if present |
| Java chrome constants | `de.aetherion.items.util.ManagerChrome` |

### Missing for this goal (you add)

| Need | Status |
|------|--------|
| `assets/minecraft/textures/gui/**` heart/food/hotbar replacements | **Absent** |
| Custom HP / DEF / CD font glyphs (bitmap bars) | **Absent** |
| `assets/minecraft/shaders/core/**` (vanilla pack shaders) | **Absent everywhere** |
| Cooldown glyph in pack | **Absent** (docs once said “prefer if present”) |

### 1.21.1 / pack_format 34 — verify, do not guess

Minecraft **1.21.2+** changed atlas/sprite and some shader paths. This server is **1.21.1**.

You **must**:

1. Confirm exact texture paths for hearts / food / XP / armor icons in **1.21.1** jar or wiki for that version (often still `textures/gui/sprites/hud/...` or legacy `icons.png` depending on version — **look it up**).
2. If using **core shaders**, confirm `assets/minecraft/shaders/core/*.json` + `.vsh`/`.fsh` names and uniforms for **1.21.1** only. Do not copy 1.21.4 Iris/Sodium shader pack layouts.
3. Keep `pack.mcmeta` at **`"pack_format": 34`**.
4. Prefer techniques that work for **every** player on join (server resource-pack URL). **No Iris-required shader pack.** Optional Iris notes only as “known issues”.

### Suggested presentation approach (you may refine)

Practical stack used by many MMOs on vanilla packs:

1. **Hide** vanilla hearts + hunger (and maybe armor icons if unused) via empty/transparent HUD sprites **or** a tiny core shader that discards those layers.
2. **Draw** custom HP / DEF / CD with **bitmap font glyphs** on the ActionBar (or a dedicated font + negative-space positioning), driven by existing `HealthListener` / `AbilityCooldownHud` data.
3. Keep vanilla XP bar unless it collides; Pit uses XP bar in pit worlds (`AetherionPit/.../PitXpBarHud.java`) — do not break that.

If you choose a different stack, document why in the handoff. Unclear → open question, do not invent PacketEvents.

---

## Technical requirements

1. **Send throttle:** Stop blasting ActionBar every 2 ticks when nothing changed. Cache last painted signature (hp, max, def, cd fragment state) per player; send only on change (+ optional low-rate heartbeat ≤1 Hz if needed for safety). Keep responsiveness on damage/heal/CD arm.
2. **GUI Scale 2, 3, Auto** + common window sizes: HUD must sit cleanly. Document any scale that breaks.
3. **Sodium / Iris:** Test if available; document breakage. Must still work on vanilla renderer.
4. **Additive ship:** Do not delete Manager chrome glyphs or overwrite unrelated `default.json` providers — **extend** the font JSON.
5. **No ProtocolLib dependency** unless already a hard dep (it is not). Prefer pack + Adventure ActionBar.
6. **Hotbar material CD:** Either keep `setCooldown` as secondary cue or replace carefully without stealing unrelated item CDs (`AbilityCooldownHud.OVERLAY_OWNER`).

---

## LOCKED / do not touch

- TalkUx (`AetherionQuests` TalkUx + helpers)
- Signature weapons combat: AshenKatana / GravwellCleaver listeners & props (CD *label display* OK)
- Boosters stackable lore, BoosterSocketMenu / Anvil, ranks / `player-ranks.yml`, ShutdownCountdown
- Damage formulas in `DamageListener`, pet catch odds, quest logic

---

## Expected deliverable

1. Changed / new files under plugin(s) + `AetherionItems/resourcepack/` (list them).
2. Short explanation of the chosen technique (hide hearts/food + glyph HUD, etc.).
3. Test plan: GUI Scale 2/3/Auto · window resize · damage/heal · DEF 0 vs high · CD arm/ready/deny · held swap · OriginCompass / fishing tip competition · Sodium/Iris note.
4. Open questions + ideas for Run B / Run 2 (polish proportions, bossbars).

**Self-score honestly.** Prefer a coherent v1 over half-broken shader experiments.

---

## Smoke commands / in-game checks

- Join MMO-R with pack forced → no hunger icons, custom HP visible.
- Equip high-HP / high-DEF gear → numbers match ActionBar / custom bar, not “10 hearts”.
- Trigger any weapon ability CD (non-locked or test gear) → filling CD readable.
- Stand still near Origin compass / fishing → HP line still wins or composites cleanly.
