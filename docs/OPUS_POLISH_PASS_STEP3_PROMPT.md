# Opus prompt — Polish Pass STEP 2-of-3 (Early Boss Rituals)

**Local agent.** Run **after STEP 1 (textures)** ships or Robbi explicitly defers textures.  
(Pets/habitats is the *third* pass — see `OPUS_POLISH_PASS_STEP2_PROMPT.md`.)

Copy everything below the line into the Opus chat.

---

You are a **local** Claude Opus agent executing **Aetherion Polish Pass — Early Boss Summoning / Rituals only**.

## First read (in order)

1. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A3, B, C (STEP 3 / rituals), D, F (rituals)
2. Optional philosophy only: `BossEngine/docs/OPUS_BOSS_PROMPT.md` or `docs/OPUS_BOSS_CONTEXT.md` — geometry/timing/sound before particles. Do **not** build a showcase boss.
3. Then open **only**:
   - `AetherionItems/.../world/BorderlandsRiteService.java`
   - `AetherionItems/.../world/ColosseumEscortService.java`
   - `AetherionItems/.../world/ColosseumGateService.java` (read — prefer not edit)
   - T1 YAMLs: `BossEngine/src/main/resources/bosses/{hollow_lurker,mcnugget,bridge_troll,skuldugery,pathwarden}.yml`
   - `BossEngine/.../fx/TierPhaseShow.java`, `CombatTheatrics.java` (read — KEEP)
   - `BossEngine/.../instance/SignatureDirector.java` (read — KEEP)
   - Optional skim: `BossEngine/.../instance/saint/` (Seraphine intro — reference only)

## You are NOT here to

- Rewrite Seraphine, Hollow Sun, World Eater, Ashen Sovereign, or Cinder Herald
- Clone Seraphine-length intros into Borderlands
- Change vial stack size (LOCKED 64), altar coords, powder contract, drop chances, or vial→boss map
- Redesign T1 combat kits, HP, loot, or Pathwarden Gravwell drop chance
- Add full new directors for T1
- Touch boosters, ranks, signature weapon combat, quest graph, textures STEP 1, pets

## You ARE here to

Raise early summon presentation to the **same craft language** as showcase encounters — smaller scale.

LESS: particle spam, generic explosions, “particles → boss pops”  
MORE: deliberate beats, readable anticipation, light environmental reaction, clean manifestation

### Scope

**P0 — Essential**
1. **Restage Colosseum summon FX** in `ColosseumEscortService` (`igniteSummon`, countdown FX, arrival in `spawnBoss`) to Borderlands-quality beats. Keep 10s bossbar, unlock/taught flow, Proctor demo path.

**P1 — Recommended**
2. Soft per-boss arrival motif after Borderlands `spawnBoss` for the four T1 ids (1–2 beats: timing + sound + optional few displays). Sync with invulnerable spawn ticks. Cleanup on abort.
3. Near-field soften of idle altar landmark beam; keep far readability. Preserve `ritualActiveNearAltar` pause.

**P2 — Optional**
4. Tint Borderlands stage rings with spirit/vial color if helpers exist.
5. Trim continuous `PARTICLE_AURA` in T1 YAMLs (rarer/subtler) — do not gut attack telegraphs.
6. Light Pathwarden pad-arrival beat only (not kit rewrite).

## Branch

`claude/polish-step2-rituals` (name reflects impact order: second pass). Commit. Push if possible. **STOP.**

## Deliverable

- Ritual polish shipped
- Report: what changed, files, what stayed, playtest notes for Borderlands + Colosseum

Do not start pets/habitats.
