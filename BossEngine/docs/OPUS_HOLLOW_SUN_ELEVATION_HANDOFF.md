# Hollow Sun elevation — Technical handoff (for Opus)

**Cursor does not implement this.** Map + brief only.  
**Stack:** Paper 1.21.1 · BossEngine  
**Date:** 2026-09-27  
**Scope this pass:** **`hollow_sun` only.** Ashen Sovereign / dragon = later. Seraphine = quality bar only.

---

## A) IDENTITY

| | |
|--|--|
| Display | **The Hollow Sun** |
| Template id | `hollow_sun` |
| Director | `HollowSunDirector` |
| Collapse addon | `HollowBloodstorm` (modular helper — rewrite/replace OK) |
| Hitbox body | Invisible wither skeleton + netherite armor silhouette; displays carry identity (star core, mace, etc.) |
| Spawn | `/boss spawn hollow_sun` · items `hollow_sun_anchor` / `hollow_sun_core` |
| Loot prop | `loot/HollowReliquary.java` — don’t casually break payout wiring |

**Out of scope this pass:** `dungeon_aetherion`, `AshenSovereignDirector`, Seraphine edit, Terminus item, locked weapons, Core, deploy.

---

## B) WHERE EVERYTHING LIVES

| Path | Role |
|------|------|
| `resources/bosses/hollow_sun.yml` | HP gates, phases, leash ~40; combat mostly empty |
| `instance/HollowSunDirector.java` | Main fight (~5k lines today) |
| `instance/HollowBloodstorm.java` | Collapse storm cells |
| `instance/BossInstance.java` | Director hooks (`onBind` / `tick` / `beginDeath` / `abort`) |
| `manager/BossManager.java` | Spawn; Hollow → Reliquary payout path |
| `combat/BossHits.java` | Scripted %maxHP hits |
| `fx/FakeDestruction.java` | Impact + debris without permanent grief |
| `docs/OPUS_BOSS_CONTEXT.md` | Engine primer (bar is now Seraphine) |

### Seraphine quality bar (read only)

| Path | Why |
|------|-----|
| `instance/saint/HangingSaintDirector.java` | Multi-act dramaturgy |
| `instance/saint/SaintFx.java` | Fixed anchor; matrix interpolation; no teleport spam |
| `SaintBody`, `HandBody`, `Skeleton`, `ThreadLine`, `SaintProps` | Authored geometry |
| `SaintStage`, `StageDressing` | Arena-as-character pattern |
| `SaintMath` | Easing / windows |

**Lesson:** geometry + timing + silence > particle carpets; ruthless cleanup; one readable idea per beat.

---

## C) ARENA — BLOODSTONE

| | |
|--|--|
| World | `bloodstone` (MMO-R) |
| Spawner | `hollow_sun_home` ≈ **0.5, 63, 115.5** |
| Nature | Permanent player-built forge — **not** SaintStage-built |
| Leash | ~40 (YAML); director samples floor edges → `arenaRadius` |

**Allowed during fight (must restore / fake):**

- Burning / cracking / collapsing set pieces  
- Lava rising, forge channels, melt scars  
- Temporary destruction, solar scorch, warped floor language  
- `FakeDestruction` + BlockDisplays preferred; real edits only with full restore on abort/death/despawn/disable  

Never leave Bloodstone permanently wrecked.

**Do not conflate** with F3 `aedun_f3_ashes` or showcase `ashen_void` (dragon arenas — irrelevant this pass).

---

## D) CURRENT PHASE SPINE (evolve, don’t necessarily delete)

| Phase | HP | Current idea |
|-------|-----|----------------|
| `main_sequence` | 100% | Star-knight: swipe, lance, slam, starcall, corona |
| `red_giant` | 66% | Swells/hovers; flare beams, lava prominences |
| Signature transition | 66→33 | Swell → black star → silence → Starfall |
| `collapse` | 33% | Wells, folds, shockwaves; ~15% Nova; Bloodstorm after Starfall |
| Death | — | Armor burst; core returns to sky as a star |

**Moves today:** `SWIPE, LANCE, SLAM, STARCALL, CORONA, FLARE, PROMINENCE, WELL, FOLD, NOVA`

**Tell language today:** gold / flare-red / violet ground = danger; white strobe = fire; green = opening; chevrons = beam direction. Refine — don’t muddy.

**Robbi thematic push:** louder **sun / stellar core / collapse**; quieter generic knight+void.

---

## E) WHAT “PAST SERAPHINE” MEANS HERE

1. Stronger **stellar identity** (unmistakable)  
2. Clearer **escalation** — each phase raises the ceiling; Collapse/death is the peak  
3. Better **dramaturgy** (wind-up → signal → hit → hush → aftermath)  
4. Geometry + animation + sound carrying the show  
5. **Bloodstone as co-star** (temporary hell, then clean)  
6. **Not** more particles, bigger numbers, or gluing five casts  

If a beat is still `FLAME + LAVA + EXPLOSION_EMITTER` carpet, replace with authored motion/geometry.

---

## F) DELIVERABLE

- Elevated `HollowSunDirector` (+ helpers as needed)  
- `HollowBloodstorm` rewritten/replaced/integrated only if it serves the sun fantasy  
- YAML transition lengths only if timelines need it  
- Short writeup: beats, arena interactions, cuts, restore path, what you didn’t copy from Seraphine  
- Compile clean; **no deploy**; **no Ashen Sovereign work this pass**
