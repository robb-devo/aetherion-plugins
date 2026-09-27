# Opus prompt — Elevate Hollow Sun past Seraphine (solo pass)

Copy everything below the line into the Cloud workspace chat.
Also attach / read `BossEngine/docs/OPUS_HOLLOW_SUN_ELEVATION_HANDOFF.md`.

---

You are elevating **one** existing Aetherion showcase boss: **The Hollow Sun**.

## Scope (hard)

**Only** `hollow_sun`.

| | |
|--|--|
| Display | **The Hollow Sun** |
| Template id | `hollow_sun` |
| Director | `HollowSunDirector` (+ `HollowBloodstorm` — rewrite/replace/keep as needed) |
| Arena | Permanent **Bloodstone** forge on MMO-R (`bloodstone`, spawner `hollow_sun_home` ≈ **0.5, 63, 115.5**) |

Do **not** touch:

- `dungeon_aetherion` / `AshenSovereignDirector` (later pass — out of scope now)
- Seraphine fight code (quality bar only — read, don’t edit)
- Ashen Sheath, elites, world-raid `aetherion.yml`
- Absolute Limit item (Terminus), locked weapons, ranks/TAB, boosters, AetherionCore
- Deploy / live server

## First read (don’t ingest the whole monorepo)

1. `BossEngine/docs/OPUS_HOLLOW_SUN_ELEVATION_HANDOFF.md` (this brief)
2. Quality bar — **read only**, do **not** clone marionette / Hand / theatre language:
   - `instance/saint/HangingSaintDirector.java`
   - `instance/saint/SaintFx.java` (fixed anchor + matrix interpolation)
   - Glance: `SaintBody`, `HandBody`, `SaintStage`, `StageDressing`
3. Deep target:
   - `instance/HollowSunDirector.java`
   - `instance/HollowBloodstorm.java`
   - `resources/bosses/hollow_sun.yml`
4. Shared tools: `BossHits`, `FakeDestruction`, `BossInstance` director hooks  
   Optional primer: `docs/OPUS_BOSS_CONTEXT.md` (engine facts; **bar is Seraphine**, not old Hollow)

## Why this pass

Hollow Sun is already strong. Next to Seraphine it can feel: noisy, overlapping FX, particle-heavy, thematically “cool knight / purple void” instead of a **star**.

Mission: keep a clear phase spine, but push craft, readability, arena co-starring, and **stellar identity** so this fight sits at Seraphine’s level — and if you can, **past** it.

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
- Five systems firing at once so tells are unreadable  
- Cloning Seraphine’s Hand / threads / skull audience / proscenium  
- “Another nova / meteor / rift” with no clearer stellar idea  

Readable fights > denser fights. One clear tell language, consistent across phases.  
The bar should keep rising beat → beat → phase → phase — peaking in Collapse / death, not dumping everything in phase 1.

## Theme (required)

Hollow Sun must feel like a being **fed by / wearing / becoming the core of a star**.

- Bloodstone = forge scorched by that star  
- Early fight can still read as armored star-knight if the **core** is the visual heart  
- Mid → late: swelling, heat, instability, **stellar collapse** (implosion, silence, fall, remnant)  
- Players should never confuse this with “generic purple void boss”

## Arena freedom (go wild — with restore)

The permanent Bloodstone arena is a **co-star**. You may:

- Set pieces burning, cracking, collapsing, melting  
- Lava rising / channels opening / forge reacting  
- Temporary destruction, warped geometry, solar scorch on the floor  
- FakeDestruction + displays **or** real block edits **only if fully restored** on abort / death / despawn / disable  

Never leave Bloodstone broken after the instance ends. Prefer spectacular temporary violence over permanent grief.

## Creative bar vs Seraphine

Seraphine is the current peak (authored geometry, dramaturgy, silence, arena as character).

Your job: make Hollow Sun a **peer**, then push **past** her on *this* fantasy — stellar scale, forge arena, collapse — through craft, not particle count. Robbi wants the “holy shit” ceiling to keep climbing.

## Process

1. Internalize Seraphine craft principles (anchor motion, dramaturgy, cleanup).  
2. Audit Hollow Sun for noise, unreadable overlaps, particle dependence, weak sun identity.  
3. Elevate **only** this boss (director + helpers + YAML transition lengths if needed).  
4. Compile; fix obvious issues.  
5. **No deploy.**  
6. Short finish doc: beat sheet, arena interactions, what you cut, restore/cleanup path, what you consciously did not copy from Seraphine.

## Tone

Authored. Readable. Stellar. Arena as co-star. Absurd impact when earned. Escalation that never plateaus early.

Ship the Hollow Sun fight that makes Seraphine look like a warm-up — **through craft**.

---
