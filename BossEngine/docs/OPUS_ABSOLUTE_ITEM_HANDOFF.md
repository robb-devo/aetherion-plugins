# Absolute Limit Item — Technical handoff (for Opus)

**Cursor does not implement this.** Analysis + prompt only.  
**Stack:** Paper 1.21.1 · AetherionItems + BossEngine spectacle craft  
**Date:** 2026-09-27

---

## A) CURRENT SPECTACLE LANDSCAPE

### Quality bar (boss) — read, don’t copy

| System | Where | Why it matters |
|--------|-------|----------------|
| **SaintFx** | `BossEngine/.../instance/saint/SaintFx.java` | Fixed **anchor**; displays **never teleport**; all motion = `setTransformationMatrix` + interpolation. Client-smooth “stage” craft. |
| **HangingSaintDirector** + saint props | `BossEngine/.../instance/saint/` | Multi-act dramaturgy, Hand, threads, titles, audience radius, sound beds |
| **SeraphineMusicBox** | `BossEngine/.../loot/SeraphineMusicBox.java` | Prop-as-spectacle; Matrix4f parts; lift / fly bars; claim + teardown |
| **HollowReliquary** | `BossEngine/.../loot/HollowReliquary.java` | Session-owned BlockDisplays, sparks, claim UX |

**Instruction for Opus:** these are the **quality ceiling we already hit on bosses**. The new **item** must feel *at least as authored*, ideally **more shocking as a single button-press fantasy** — without cloning Seraphine’s marionette/theatre language.

### Quality bar (player items) — current best showcases

Wired mainly via `TestPrototypeAbilities` → dedicated `*.cast(...)` sessions:

| Class | ~size | Fantasy / craft notes |
|-------|------:|------------------------|
| `DeepsongLeviathan` | ~95 KB | Stone sea / breach / spout — world-scale geometry |
| `VesperBellBasilica` | ~84 KB | Architecture + hymn + rapture/shatter arc |
| `CataclysmRodNova` | ~62 KB | Shells, rings, rays, polar bolts, seismic pillars |
| `HollowSunSetListener` | ~59 KB | Set spectacle (armor path — reference only; **don’t ship a set**) |
| `MeteorMaceCrash` | ~55 KB | Fissure → eruption → meteor → crater |
| `StormcallerTempest` | ~42 KB | Storm cloud / strokes / scars over time |
| `WorldSplitterRift` | ~36 KB | Reality rift; debris + brightness; pull into void |
| `CycloneRodTempest` | ~34 KB | Funnel / orbit / fling |
| `CascadeTorrent` | ~29 KB | Chained bolt hops |
| `JudgmentVerdict` | ~27 KB | Seal / swords / pillar |
| `AshenKatanaListener` | ~28 KB | **LOCKED** Blossom Blade — petal Block/ItemDisplays, crest timing (**read-only**) |
| `SeraphineGearListener` | ~40 KB | Her loot abilities — echo of boss language |
| `GravwellCleaverListener` | small | **LOCKED** — read-only; gravity fantasy exists but keep hands off combat |

**Pattern to reuse:** one **session object**, `cast(plugin, player, …, onDone)`, tick choreography, **hard cleanup** of every Display, no leaks on quit/death/disable.

### Shared techniques already proven

- `BlockDisplay` / `ItemDisplay` / occasional `TextDisplay`
- `Transformation` **or** JOML `Matrix4f` + `setTransformationMatrix`
- `setInterpolationDuration` / delay; `setTeleportDuration` for debris that must move in world space
- `Display.Brightness`, glow `Color`, block materials as “meshes”
- Layered `Sound` with pitch ramps; titles/ActionBar sparingly
- Particles as **accent** (FLASH, DUST, block crack) — not the body of the show
- Velocity / brief freeze / knockback for body feedback (true camera FOV control is very limited)

### Where to implement the new item

| Piece | Plugin | Notes |
|-------|--------|-------|
| Item factory + id + lore shell | `AetherionItems` `CustomItem` + profile | One weapon/tool/catalyst — **not** armor set |
| Ability session | New class under `items/listener/` (or `items/spectacle/`) | Mirror `WorldSplitterRift` / `CataclysmRodNova` / `VesperBellBasilica` shape |
| Trigger wiring | Listener + register in `AetherionItems.java` | Right-click / swap / sneak-click — pick one clear activation |
| Optional Dev give | Dev Menu / TestGear | So Robbi can summon without loot tables |
| BossEngine | **Do not** add a boss | May **read** SaintFx patterns; don’t depend on BossEngine at runtime unless already hooked |

### Technical limits (Paper / client)

- Display entity **count** and **view range** — dozens of large interpolating displays can hitch; prefer fewer, bigger, better-timed pieces  
- No real cinematic camera API — use motion, sound, titles, darkness, border/vignette tricks carefully  
- World mutation (blocks) must **restore** or use fake geometry (displays) like World Splitter / Deepsong  
- Main-thread only for Bukkit entities; keep tick work bounded; cancel on quit  
- Don’t fight other spectacles (busy flags like `TestPrototypeAbilities` already uses)

### LOCKED (never modify)

- Blossom Blade (`AshenKatana*`, `ashen_katana`)  
- Gravwell Cleaver (`GravwellCleaver*`)  
- Boosters / sockets / anvil, ranks/TAB, shutdown countdown  
- Seraphine fight directors / Hand / stage (loot listeners OK to **read**)

### Explicitly out of scope

- Item sets, armor, quests, bosses, economy, drop rates, balance numbers  
- Rewriting the item framework or Core  
- Deploy / production server work  

---

## B) What “surpass everything” means here

Surpass **Deepsong / Vesper / Cataclysm / Meteor / World Splitter / Saint stage craft** by:

- Stronger **idea identity** (one fantasy, unmistakable)  
- Better **dramaturgy** (wind-up → signal → escalate → impact → silence → aftermath → clean end)  
- Geometry + timing + sound carrying the show  
- **Not** by particle count, damage number, or gluing five existing casts together  

If Opus’s concept could be mistaken for “another nova / another meteor / another rift,” it is not good enough — invent further.
