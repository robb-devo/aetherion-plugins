# Opus Prompt B — BossBar Polish (Run 2)

**Owner:** Robb (A3therion). Call him Robb.  
**Target:** Paper / Minecraft **1.21.1**, resource pack **`pack_format: 34`**.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md).  
**Prerequisite:** Run A ([`OPUS_HUD_POLISH_A_PLAYER_PROMPT.md`](OPUS_HUD_POLISH_A_PLAYER_PROMPT.md)) should be landed first so ActionBar glyph math / pack `default.json` extensions do not collide mid-flight. If Run A is not merged yet, extend fonts **additively** and avoid fighting ActionBar PUA ranges.

---

## Goal

Every **top BossBar type** gets a **recognizable look**: own frame / texture language and own icon.  
Bosse = **epic**. Hints = **quiet**. Gameplay / lease logic unchanged — presentation + optional send throttle only.

---

## Architecture you must respect

### Coordinator (do not redesign)

`AetherionCore/src/main/java/de/aetherion/core/api/QuestBars.java`

| API | Role |
|-----|------|
| `suppress(player)` / `unsuppress` | Legacy — bosses, dungeons, pet catch |
| `suppress(player, owner)` / `release(id, owner)` | Named leases (e.g. `"fishing"`, `"fell"`) |
| `suppressAll(player, owner)` | Also hides soft **hint** bar (`hintsHidden`) |

Quest/hint UI is implemented in Quests; other plugins only lease. **Do not** invent a second coordinator.

### Quest / Hint owners

| Class | Path | Color / style today | Title pattern |
|-------|------|---------------------|---------------|
| Quest progress | `AetherionQuests/.../ui/QuestProgressDisplay.java` | RED/YELLOW/GREEN by thirds; READY GREEN; specials PURPLE; `SOLID` | `§f{quest} • {obj}` / `Return to {npc}` |
| Soft hint | `AetherionQuests/.../ui/QuestHint.java` | YELLOW (GREEN arrive, WHITE init); `SOLID` | `Hint • {label} • {arrow dist}` |

### Type catalog (Bukkit / Adventure BossBar — no KeyedBossBar)

Paint these as **distinct visual families**. Same family may share a frame; different families must not look identical.

#### 1) Boss — epic

| Site | Path | Notes |
|------|------|--------|
| Default bosses | `BossEngine/.../hud/BossBarHud.java` | `{name} • {hp}/{max}`; YELLOW→RED@≤50%; `SEGMENTED_20`; near 32 / recent hit |
| Helios dual bars | `BossEngine/.../helios/encounter/HeliosBars.java` | Main NOTCHED_20 + movement PROGRESS; script-driven colors |
| Helios scripts | `HeliosScript` / `HeraldScript` via `enc.bars()` | Herald YELLOW; Helios RED; phase titles |
| Borderlands / Colosseum summon | `AetherionItems/.../BorderlandsRiteService.java`, `ColosseumEscortService.java` | Countdown RED/YELLOW PROGRESS |

Listener cleanup: `BossEngine/.../listener/BossHudListener.java`.

#### 2) Quest — clear objective

`QuestProgressDisplay` (above). Suppressible while other HUDs own the top.

#### 3) Hint — discreet

`QuestHint` (above). Hidden while `QuestBars.hintsHidden` / `suppressAll`.

#### 4) Event — loud but temporary

| Site | Path |
|------|------|
| Farm bird scare | `AetherionFarming/.../BirdScareEvent.java` |
| Scarecrow | `AetherionFarming/.../ScarecrowEvent.java` |
| Farm isle events | `AetherionFarming/.../isle/IsleEvents.java` |
| Mine events | `AetherionMining/.../isle/MineEvents.java` |
| Lake events | `AetherionFishing/.../isle/LakeEvents.java` |
| Grove events | `AetherionForaging/.../isle/GroveEvents.java` |

Typical: NOTCHED_20 or PROGRESS; countdown / tally in title.

#### 5) Gather mini-game — rhythmic

| Site | Path |
|------|------|
| Fishing cast | `AetherionFishing/.../FishingHud.java` + `StrikeBar.java` |
| Foraging fell | `AetherionForaging/.../ForagingHud.java` |
| Harvest rhythm | `AetherionFarming/.../isle/HarvestRhythm.java` |
| Mining rhythm | `AetherionMining/.../isle/StrikeRhythm.java` |
| Amethyst resonance | `AetherionMining/.../isle/AmethystMine.java` |
| Grove compass | `AetherionForaging/.../isle/GroveCompass.java` |

Titles often embed unicode strike windows (`[──██▓██──]`, `◆`). Restyle carefully — timing readability > pretty frames.

#### 6) Island / Guild

`AetherionGuilds/.../island/IslandBar.java` — tour / visit / warn / goal / status; uses `QuestBars.suppressAll`.

#### 7) Dungeon

| Site | Path |
|------|------|
| Room clear | `AetherionDungeons/.../instance/DungeonProgressHud.java` | PURPLE `SEGMENTED_10` |
| Endless / Ashes gates | `EndlessEncounter`, `AshesEncounter` state bars | RED→BLUE/PURPLE |
| Hook | `AetherionDungeons/.../instance/QuestHudHook.java` | thin QuestBars wrapper |

#### 8) Other

| Site | Path |
|------|------|
| Pet catch timing | `Aethermobs/.../CatchTimingSession.java` | PURPLE→hot→perfect; ~36t |
| Millstone / Blueprint forge | `MillstoneRitual`, `BlueprintForgeRitual` | YELLOW countdown |
| NPC editor input | `AetherionQuests/.../editor/NpcEditor.java` | staff-only; low priority |

**Gnomon / Hub:** no BossBar creators found — ignore unless you discover new ones.

### Not BossBars (leave alone unless Run A left a hook)

- Player HP ActionBar — Run A
- Dungeon nametag HP — `PrototypeDungeonBuilder.refreshDungeonNameplate`
- Damage numbers TextDisplay — `DamageNumbers`
- Pit XP bar mapping — `PitXpBarHud`

---

## Resource pack constraints (same as Run A)

- **1.21.1 / pack_format 34 only.** Vanilla boss bar textures live under the **1.21.1** GUI atlas paths — **verify** (`textures/gui/sprites/boss_bar/...` vs legacy `bars.png`). Do not assume 1.21.4.
- Write to `AetherionItems/resourcepack/`; keep Manager chrome + Run A HUD glyphs.
- **No Iris-required pack.** Core shaders only if verified for 1.21.1 and auto-applied via server pack.
- Vanilla `BarColor` / `BarStyle` are coarse (PINK/BLUE/RED/…, SOLID/SEGMENTED_*). True “custom frame per type” usually needs:
  - **Title prefix glyphs** (font bitmap frame + icon) per family, and/or
  - **Multiple boss_bar sprite overrides** if 1.21.1 exposes per-color sprites you can remap, and/or
  - Careful color assignment so families map to distinct bar colors + matching title chrome.

Document which approach you pick and what is impossible on 1.21.1 without client mods.

---

## Requirements

1. **Families:** at least distinct looks for **Boss / Quest / Hint / Event / Gather / Island / Dungeon** (Event may share energy with Gather if icons differ).
2. **Boss = epic** (ornate frame, strong icon). **Hint = quiet** (thin, low contrast, small icon).
3. **Throttle:** where bars update every tick with identical title/progress, skip resend (esp. IslandBar 1 Hz is fine; fishing strike may need per-tick — throttle only when unchanged).
4. **Leases:** do not break `QuestBars` suppress/release; fishing/fell/bird-scare/island/boss still hand off cleanly.
5. **GUI Scale 2/3/Auto** + Sodium/Iris notes.
6. **LOCKED:** TalkUx, signature weapon combat, boosters, anvil, ranks, shutdown. Helios fight logic / camera — bar *look* OK, encounter scripts stay.

---

## Expected deliverable

1. File list (plugins + pack).
2. Short “family → glyph/color/sprite” map.
3. Test plan: quest track → fishing cast → fell → island visit → boss spawn → dungeon clear → quest hint alone → pet catch — each bar visually typed; leases don’t stack wrongly.
4. Open questions (e.g. Helios dual bars, unicode strike readability).

**Self-score honestly.** Prefer readable typed frames over unreadable ornament.
