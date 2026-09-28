# Opus prompt — Aetherion boss encounter

Copy everything below the line into a new Claude Opus session.

---

You are designing and creating an extremely high-quality Minecraft boss encounter for **Aetherion**.

## First read

Read and internalize:

`docs/OPUS_BOSS_CONTEXT.md`

That file is the technical environment. Use it so your design fits BossEngine (YAML template + Java director, BlockDisplay/ItemDisplay choreography, `BossHits`, tick lifecycle, cleanup). Do **not** scan the entire Aetherion codebase. Do **not** redesign BossEngine.

Then focus almost exclusively on the boss encounter itself.

## Creative goal

Create a boss encounter that pushes Minecraft as far as reasonably possible visually and mechanically — boss body, every phase, arena/environment interaction, intro and death. Go all-out.

The quality target is **not** a generic Minecraft MMO boss. Aim for a bar where even hardened Hypixel / Wynncraft-level Minecraft engineers would stop and say *holy shit*.

Think about the encounter quality and spectacle of extremely polished action games / FromSoftware-style bosses such as Malenia, Radahn, Pontiff Sulyvahn, Sister Friede, etc., **WITHOUT copying** their characters, attacks, visuals, names, or designs.

The boss should have:

- A strong visual identity
- Distinct movement language
- Deliberate attack choreography
- Strong anticipation before attacks
- Extremely satisfying impact and feedback
- High-quality custom animation
- Interesting phase changes
- Meaningful attack patterns
- Strong sound design
- **A dedicated custom arena built for this fight** (not a flat void pad and not “reuse some existing Aetherion map”)
- Arena hazards / set pieces that matter to the choreography
- Memorable signature moments
- A coherent beginning, escalation, climax and death
- Visual storytelling through the fight itself

## Dedicated arena (required)

Build a **fitting custom arena as part of the encounter**, not as an afterthought.

That means:

- Design the stage specifically for this boss’s identity, phases, and signature moves
- Use authored geometry (BlockDisplay / ItemDisplay props, set pieces, floor language, verticality, walls, pillars, pits, platforms — whatever the fight needs)
- Make the arena participate: telegraphs, stage beats, phase-change set-dressing, death/intro staging
- Keep it self-contained in the deliverable so another engineer can place/wire it on Aetherion later
- Prefer fake/safe destruction and display props over real world griefing

Do **not** assume an existing Aetherion arena. Invent this boss’s own stage.

## Visual direction

Prefer authored, intentional animation and custom geometry/display work over simply throwing huge amounts of particles everywhere.

Particles can support effects, but should **NOT** be the primary way to make the boss look impressive.

Prioritize:

1. Animation  
2. Timing  
3. Custom geometry / displays  
4. Movement  
5. Sound  
6. Impact feedback  
7. Environmental interaction  
8. Carefully used particles  

The goal should be:

> How can this feel like an actual boss from a high-budget action game while still being built inside Minecraft?

You have significant creative freedom.

You may invent:

- boss identity  
- appearance  
- lore  
- phases  
- mechanics  
- attacks  
- movement  
- **a custom arena / stage geometry**  
- arena interaction / hazards / set pieces  
- signature abilities  
- transitions  
- death sequence  
- sound design  
- visual language  

Do **NOT** unnecessarily constrain yourself to existing Aetherion bosses. Hollow Sun / Ashen Sovereign are quality references for craft, not templates to clone.

## Hard scope

Focus **ONLY** on the boss encounter and its presentation.

Do **NOT** spend effort on:

- balance  
- exact HP values  
- loot  
- economy  
- progression  
- permissions  
- commands  
- deployment  
- server management  
- writeups / design docs / changelogs (beyond a tiny completion summary)  
- unrelated Aetherion systems  
- refactoring the BossEngine  
- redesigning existing infrastructure  
- production server deploy / restart  
- full-network Aetherion integration (spawners, dungeons, loot tables, live YAML polish)

Balance and live-server wiring will be handled **separately later by another engineer**. Your job is the encounter itself.

The BossEngine context exists only so you understand available capabilities and do not design something incompatible with Minecraft / this plugin.

## Implementation

Implement the encounter as real BossEngine code (YAML stub + Java director + **custom arena/stage helpers/props**), in the same style as existing showcase directors.

Spend almost all effort on design + presentation of the fight.

Do **not** waste effort on:

- deployment to live servers  
- server restart  
- broad architectural refactors  
- unrelated cleanup  

If a small amount of technical adaptation is necessary to make the encounter compile/run in BossEngine, do it locally and minimally (YAML stub + director + `BossInstance` hooks).

Use:

- director-owned movement (`setAI(false)` when scripting the body)  
- `BlockDisplay` / `ItemDisplay` with transformations and interpolation  
- `BossHits.hurt` for scripted player damage  
- `setPersistent(false)` + explicit cleanup on abort/death  
- phase YAML for HP gates / transition windows; Java for the fight  

## Most important

Do not play it safe.

Do not make another generic MMO boss.

Do not simply combine particles, explosions and existing abilities.

Try to create something that makes an experienced Minecraft player genuinely stop and think:

**“What the fuck was that?”**

Push Minecraft’s animation, geometry, timing, sound and visual capabilities as far as is technically reasonable while keeping the encounter readable and playable.

## Deliverable / handoff

When the encounter is done, leave a **clean code deliverable** another engineer can pick up 1:1:

- Prefer a clear folder (e.g. under `BossEngine` with the new director, YAML, arena/stage helpers, and any dedicated props).
- If this session has GitHub access, commit/push that deliverable to a branch — that is the handoff package.
- Do **not** integrate into live Aetherion ops, spawners, dungeons, or production configs.

Then **STOP**.

Do not continue into unrelated polish, deployment, balancing notes, or server operations.

Tell me the boss encounter is complete, where the files/branch are, and give a **brief** summary of what was created (identity, phases, arena, signature moments) — nothing more.
