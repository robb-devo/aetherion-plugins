# Opus prompt — Absolute Limit Item (single showcase)

Copy everything below the line into the Cloud workspace chat.

---

You are creating **one single Custom Item** for Aetherion — an intentionally absurd, ultra-late-game **spectacle showcase**.

## First read

1. `BossEngine/docs/OPUS_ABSOLUTE_ITEM_HANDOFF.md`  
2. Skim (do **not** rewrite) these **quality references**:
   - Boss craft: `BossEngine/.../instance/saint/SaintFx.java` (+ glance at MusicBox / HollowReliquary if useful)
   - Item craft: `DeepsongLeviathan`, `VesperBellBasilica`, `CataclysmRodNova`, `MeteorMaceCrash`, `WorldSplitterRift` under `AetherionItems/.../listener/`
   - Wiring pattern: `TestPrototypeAbilities` (`cast…` + busy flags + cleanup)
3. **Read-only LOCKED:** `AshenKatanaListener` / Gravwell — polish bar only; never edit.

Do **not** ingest the whole monorepo.

## Mission

Build **exactly one item** (one id, one activation fantasy, one ability sequence).

- Not a set. Not armor. Not a quest. Not a boss. Not a new progression system.  
- **No balancing.** Damage/cooldown/economy can be placeholder absurd. Robbi tunes later.  
- The bar: when used once, the reaction should be **“What the FUCK was that?”**  
- This must feel like **more than Minecraft** for a few seconds — while remaining implementable on **Paper 1.21.1** with our Display/FX craft.

Existing spectacular items and Seraphine are the **current standard**. You must **beat** that standard as a **single button-press fantasy**.

Use them as **technical + quality reference**. **Do not reproduce** them. No marionette-theatre clone. No “second Absolute Nova.” No “another meteor with more particles.”

## What carries the show (required taste)

Prefer:

- Animation, geometry, timing, motion  
- Sound design, spatial impact, brightness/contrast  
- Display entities / authored shapes  
- Optional world interaction **if** it serves the fantasy (restore or fake it)  
- Wind-up, silence, aftermath, clean teardown  

Particles are **garnish**, not the body.

Hard avoid:

- Giant vanilla explosion + particle carpet as the identity  
- Screen spam / ten systems mashed together  
- Random Display spam without choreography  
- “Big number” as the joke  

## Creative freedom (maximal)

You invent:

- Name, look, materials/CMD if needed  
- Activation input  
- Fantasy (black hole, collapsing star, singularity, seismic cataclysm, cosmic ray, matter erasure, spacetime shear, **or something better you invent after reading the stack**)  
- Color language, geometry, sound bed, beat structure  

Orientation only (you may discard):

activation → tension → strange signal → escalate → impact → “what happened?” beat → aftermath → clean end  

## Absolute-limit framing

Ask: *What is the most impressive single custom item we can actually ship with this Paper stack and our Display/FX patterns?*

- Reuse patterns (session `cast`, transform-interpolated displays, SaintFx-style anchor motion when it fits).  
- Add **dedicated** helper classes for this item if needed.  
- Do **not** rebuild the plugin framework.  
- If you hit a client limit, **route around it** creatively (fewer better pieces, fake geometry, sound-led beats) — don’t quit into a particle bomb.

## Performance

This item may be rarer and heavier than normal uniques. Still:

- Bound entity counts; always cleanup (quit/death/disable/overlap)  
- No pointless per-tick work after the sequence ends  
- Busy-flag so it doesn’t stack into a server melt  

## Process

1. Understand references above.  
2. Pick a concept that would **not** be confused with Deepsong / Vesper / Cataclysm / Meteor / Splitter / Seraphine.  
3. Implement in **AetherionItems** (factory + listener/session + register + easy give path).  
4. Compile; fix obvious issues.  
5. No deploy / no production server work.  
6. Finish with a short doc: name, concept, beat sheet, tech used, reused vs new files. Mention what you **consciously did not** copy.

## Hard locks / non-goals

- Do not edit Blossom Blade or Gravwell Cleaver.  
- Do not touch boosters/sockets/anvil, ranks/TAB, shutdown countdown.  
- Do not add armor sets, quests, bosses, loot tables that imply progression design.  
- Do not grow AetherionCore.

## Tone

Absurd. Precise. Authored. One identity.

Ship the item that makes every previous spectacle look like a warm-up — **through craft**, not particle count.

---
