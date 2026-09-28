# Opus prompt — Polish Pass STEP 3 (Early Boss Rituals)

Copy everything below the line into the Cloud workspace chat **only after STEP 2 is merged or explicitly deferred by Robbi**.

---

You are executing **Aetherion Polish Pass — STEP 3 only: Early Boss Summoning / Rituals**.

## First read (in order)

1. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A3, B, C (STEP 3), D, F (STEP 3)
2. `BossEngine/docs/OPUS_BOSS_PROMPT.md` — quality **philosophy only** (geometry/timing/sound before particles). Do **not** build a showcase boss.
3. Then open **only**:
   - `AetherionItems/.../world/BorderlandsRiteService.java`
   - `AetherionItems/.../world/ColosseumEscortService.java`
   - `AetherionItems/.../world/ColosseumGateService.java` (read — prefer not edit)
   - T1 YAMLs: `BossEngine/src/main/resources/bosses/{hollow_lurker,mcnugget,bridge_troll,skuldugery,pathwarden}.yml`
   - `BossEngine/.../fx/TierPhaseShow.java`, `CombatTheatrics.java` (read — KEEP)
   - `BossEngine/.../instance/SignatureDirector.java` (read — KEEP)
   - Optional skim (reference only): `BossEngine/.../instance/saint/` Seraphine intro beats

## You are NOT here to

- Rewrite Seraphine, Hollow Sun, World Eater, Ashen Sovereign, or Cinder Herald
- Clone Seraphine-length intros into Borderlands
- Change vial stack size (LOCKED 64), altar coords, powder contract, drop chances, or vial→boss map
- Redesign T1 combat kits, HP, loot, or Pathwarden Gravwell drop chance
- Add full new directors for T1 (overkill)
- Touch boosters, ranks, signature weapon combat, quest graph

## You ARE here to

Raise early summon presentation to the **same craft language** as showcase encounters — smaller scale.

LESS: particle spam, generic explosions, “particles → boss pops”  
MORE: deliberate beats, readable anticipation, light environmental reaction, clean manifestation

### Scope

**P0 — Essential**
1. **Restage Colosseum summon FX** in `ColosseumEscortService` (`igniteSummon`, per-second countdown FX, arrival in `spawnBoss`) to match Borderlands staging discipline: few beats, readable, one clean manifestation. Keep 10s bossbar, unlock/taught flow, Proctor demo path.

**P1 — Recommended**
2. **Soft per-boss arrival motif** after Borderlands `spawnBoss` for the four T1 ids (1–2 beats each: timing + sound + optional few BlockDisplays). Sync with invulnerable spawn ticks. Cleanup on abort.
3. **Near-field soften** of idle altar landmark beam in `tickAltarBeams` while keeping far-distance readability. Rite already pauses beam via `ritualActiveNearAltar` — preserve that.

**P2 — Optional**
4. Tint Borderlands stage rings using existing spirit/vial color helpers if present (`SpiritBoss.color()` or equivalent).
5. Trim continuous `PARTICLE_AURA` in T1 YAMLs (rarer / subtler) — do not gut attack telegraphs.
6. Light Pathwarden pad-arrival beat only (not kit rewrite).

### Quality bar

Borderlands rite structure is already the right shape — **refine Colosseum toward it**, then add identity on arrival.

Showcase teaches: act beats, anticipation, identity language, displays over particle carpets, cleanup.  
Showcase does **not** mean: multi-act machines, custom arenas, sky rewrites, MusicBox.

### Hard technical rules

- FX / presentation only unless a YAML aura number change is in P2 scope
- Preserve vial refund on `BossSpawnAccess` failure
- Preserve trash clear + boss bubble behavior
- Any new displays: `persistent=false` + remove on abort/fail
- Prefer Items-side ritual FX over new BossEngine directors

## Branch

Work on **`claude/polish-step3-rituals`**.  
Commit + push. Then **STOP**.

## Deliverable

- Colosseum restage (+ any completed P1/P2)
- Commit + push
- Brief report: player-facing beats, files, what you left alone, playtest checklist for altar + pad

Nothing more.
