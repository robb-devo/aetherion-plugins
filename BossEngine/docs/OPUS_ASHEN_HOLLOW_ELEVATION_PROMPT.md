# Opus prompt — Elevate Ashen Sovereign + Hollow Sun to Seraphine level

> **SUPERSEDED for the next Opus session.**  
> Robbi split the work: **Hollow Sun first** → use  
> `OPUS_HOLLOW_SUN_ELEVATION_PROMPT.md` + `OPUS_HOLLOW_SUN_ELEVATION_HANDOFF.md`.  
> Sovereign/dragon pass comes later. Kept below for archive.

---

You are elevating **two existing Aetherion showcase bosses** to match (or beat) the craft bar set by **Seraphine, the Hanging Saint**.

## Scope (hard)

Work on **exactly these two**:

1. **Aetherion, Sovereign of Ash** — the Floor-3 **dragon** boss  
   - Template id: `dungeon_aetherion`  
   - Director: `AshenSovereignDirector`  
   - Arenas (permanent, already built): F3 **`aedun_f3_ashes`** on MMO-D (~266.5, 96, −70.5); showcase/preview often **`ashen_void`** on MMO-R (Bloodstone remap)

2. **The Hollow Sun**  
   - Template id: `hollow_sun`  
   - Director: `HollowSunDirector` (+ `HollowBloodstorm` Collapse addon)  
   - Arena: **`bloodstone`** on MMO-R (`hollow_sun_home` ≈ 0.5, 63, 115.5)

Do **not** redesign Seraphine. Do **not** touch Ashen Sheath, Chainwarden, Cinder Herald, world-raid `aetherion.yml`, Absolute Limit Item work, or other bosses unless you must read them as craft reference.

## First read (do not ingest the whole monorepo)

1. `BossEngine/docs/OPUS_ASHEN_HOLLOW_ELEVATION_HANDOFF.md`
2. Skim quality bar (Seraphine — **read, don’t clone theatre/marionette language**):
   - `instance/saint/HangingSaintDirector.java`
   - `instance/saint/SaintFx.java` (fixed anchor + matrix interpolation)
   - Glance: `SaintBody`, `HandBody`, `SaintStage`, `StageDressing`
3. The two targets (deep read):
   - `instance/AshenSovereignDirector.java` + `resources/bosses/dungeon_aetherion.yml`
   - `instance/HollowSunDirector.java` + `instance/HollowBloodstorm.java` + `resources/bosses/hollow_sun.yml`
4. Shared tools: `BossHits`, `FakeDestruction`, `BossInstance` director hooks

## Why this pass exists (Robbi’s playtest notes)

Both fights are still **very good**. Next to Seraphine they feel:

- Sometimes **confusing / chaotic / hard to read**
- **Too much** overlapping FX
- **Particle-heavy** where Seraphine is **authored animation + geometry + timing + silence**
- Arena participation underused relative to the new permanent stages

Mission: keep the **core fantasy and phase spine** that already works, but lift choreography, readability, arena participation, and thematic punch to **Seraphine-class** (or beyond).

## Quality law (non-negotiable)

Prefer, in order:

1. Animation / authored motion  
2. Timing / silence / beats  
3. Custom geometry (BlockDisplay / ItemDisplay)  
4. Movement language  
5. Sound design  
6. Impact feedback  
7. Environmental / arena interaction  
8. Particles as **garnish only**

Hard avoid:

- Particle carpets as the identity of a move  
- Stacking five systems at once so the tell is unreadable  
- “Another nova / another meteor / another rift” without a clearer idea  
- Cloning Seraphine’s Hand / threads / theatre / skull audience  

Readable fights > denser fights. One clear tell language per boss, consistent across phases.

## Creative freedom (maximal within the two bosses)

You may:

- Rewrite / replace moves, telegraphs, transitions, intros, deaths inside the two directors  
- Add dedicated helper classes for these bosses (like Seraphine’s `saint/` package or Hollow’s `HollowBloodstorm`)  
- **Go wild with arena effects** during the fight:
  - Temporary destruction, cracks, collapsing pillars, ashfall burying the floor, solar scorch, warped geometry  
  - Fake or real block edits **only if you restore** (or use FakeDestruction / displays so the permanent arena survives cleanly after abort/death/despawn)  
- Improve the arenas’ *fight-time* set dressing if you see a better Throne / Bloodstone staging  
- Invent signature moments that make players say *what the fuck was that*

You invent the elevation path. Orientation only:

wind-up → clear telegraph → impact → silence/aftermath → next beat

## Thematic targets (Robbi)

### Sovereign of Ash (dragon)

- Endgame **fire + ash + prison-throne** identity must stay / get sharper  
- He is a sky that burned out; the Throne was built as his cage  
- Arena may react: chains, pylons, ash spears, cracked throne floor, sky darkening — temporary, then reset  
- Keep the readable tell spine if it still serves (ember ground = danger, soul blue = safety, green heart = opening) — refine, don’t muddy it  

### Hollow Sun

- Must **feel like a sun / stellar core**, not “generic cool knight + purple void”  
- Fantasy: fed by / wearing / becoming the **core of a star**; late fight should read as **stellar collapse** (implosion, silence, fall, remnant)  
- Bloodstone arena should feel forged / scorched by that star  
- Collapse phase and signature transition are the place to go nuclear on craft — still readable  

## Process

1. Internalize Seraphine craft principles (anchor motion, dramaturgy, cleanup).  
2. Audit each target fight for noise, unreadable overlaps, particle dependence, weak thematic moments.  
3. Elevate **both** bosses (not one demo and leave the other).  
4. Compile; fix obvious issues.  
5. **No deploy / no production server work.**  
6. Finish with a short doc per boss: what changed, beat sheet, arena interactions, what you consciously cut (esp. particle spam), restore/cleanup path.

## Hard locks / non-goals

- Do **not** edit Seraphine’s fight directors / Hand / stage (read-only quality bar).  
- Do **not** edit Blossom Blade or Gravwell Cleaver.  
- Do **not** touch boosters/sockets/anvil, ranks/TAB, shutdown countdown.  
- Do **not** grow AetherionCore.  
- Do **not** redesign BossEngine framework; local director elevation only.  
- Balance / loot chances can stay placeholders; choreography first.

## Tone

Authored. Readable. Thematic. Arena as co-star. Absurd impact when earned.

Ship two fights that make Seraphine feel like a peer — not a lonely god-tier outlier.

---
