# Aetherion BossEngine — Opus context pack

Compact technical facts for designing a high-spectacle boss inside this engine. Inspected from live `BossEngine` sources. Not an architecture thesis.

---

## What BossEngine is

Paper plugin that turns **YAML boss templates** + optional **Java directors** into live encounters.

| Piece | Role |
|--------|------|
| `bosses/<id>.yml` | Template: entity type, attrs, phases, transitions, skills, leash, loot |
| `BossManager` | Spawn / tick / despawn / uniqueness (`max-instances`) |
| `BossInstance` | One fight: combat HP, phases, leash, shared FX hooks, director ownership |
| `*Director` | Per-boss scripted choreography (showcase path) |
| `SkillRegistry` | Declarative YAML skills (secondary for spectacle bosses) |

Entry points: `/boss spawn <id>`, spawn vials / altar items, dungeon bridges via `AetherServices` spawn access. Templates load from `plugins/BossEngine/bosses/` (jar resources as defaults; live YAMLs are not force-overwritten on boot).

---

## Lifecycle

```
SPAWNING → ALIVE ⇄ phase transition → ALIVE → (director death cinematic) → DEAD / DESPAWNING
```

1. **Spawn** — `BossManager.spawn(templateId, location, …)` creates a tagged `LivingEntity`, builds `BossInstance`, registers by instance UUID + entity UUID, fires spawn skills / director `onBind()`.
2. **Tick** — scheduler every `tick-interval-ticks` (default **1**). `BossInstance.tick()` runs polish → directors → transitions → slam/leash/skills.
3. **Phases** — health % gates from YAML (`health-percent`). Crossing a gate starts a `PhaseTransition` (duration, invuln, freeze-ai, optional explode, `TransitionShape`).
4. **Death** — combat HP hits 0; if a director claims the fight, `beginDeath()` runs a multi-tick cinematic while state stays alive until the director finishes; then despawn + loot.
5. **Cleanup** — abort directors, remove minions / displays / props, unregister. Leash breach uses `TELEPORT` (snap home) or hard reset (`LeashAction`).

States: `SPAWNING`, `ALIVE`, `DESPAWNING`, `DEAD`.

---

## Registration / identity

- Template `id` is the contract (e.g. `hollow_sun`, `dungeon_aetherion`).
- Entity tagged via `BossKeys.tagBoss(entity, templateId, instanceId)` + Aetherion Core boss PDC keys.
- **Showcase directors** claim with `isMine()` → `template.getId().equalsIgnoreCase("…")`. Pattern: add a director field on `BossInstance`, call `onBind` / `tick` / `beginDeath` / `abort` in the same places as Hollow Sun / Ashen Sovereign.
- YAML alone is enough for simple rite bosses; spectacle bosses leave `skills: []` on phases and script attacks in Java.

---

## Players

- Target pool: `world.getPlayers()` filtered by vulnerable (not CREATIVE / SPECTATOR; typically must be alive / valid).
- Helpers: `nearestVulnerablePlayer(range)`, `hazardFocus()` (stable arena focus point for AOEs during transitions).
- Raid scaling exists (`raidPlayers`, `scaleDamage`) — ignore for design; tune later.
- Scripted player damage: **`BossHits.hurt(player, source, power)`** — SkyBlock-style % of max HP, not vanilla heart ticks. Prefer this for choreographed hits.
- Boss HP is **internal combat HP** (can exceed vanilla’s ~1024 attr cap); the entity bar is visual sync.

---

## Movement

Two modes in production:

1. **Vanilla AI** — Mob pathing / target for lighter bosses + YAML skills (`LEAP`, `SLAM_LEAP`, etc.).
2. **Director-owned body** (preferred for spectacle) — `mob.setAI(false)`, zero unwanted velocity, authored `teleport` / `setVelocity` / look poses each tick. Hollow Sun explicitly: *“Vanilla AI never walks, targets, or punches.”*

Leash radius from YAML keeps the fight in the arena. Transitions may freeze AI and hold the body at `hazardFocus`.

---

## Attacks / phases / structure

**YAML skills** (`SkillTrigger`: `ON_SPAWN`, `ON_PHASE`, `ON_TIMER`, `ON_DAMAGE`, `ON_DEATH`): SOUND, PARTICLE_AURA, LEAP, SLAM_LEAP, METEOR_RAIN, RING_BURST, VACUUM_PULL, MINION_SPAWN, DIALOG, SPECTACLE, T2 burrow/blast cores, etc. Good for glue; weak as the whole fight.

**Director pattern** (quality bar): state machine of moves (windup → active → recovery), per-phase move tables, telegraphs before hits, dedicated intro + death timelines. Phase YAML still defines HP gates + transition duration/invuln; the director overrides motion/FX during those windows (`tickTransition`).

Shared built-ins on `BossInstance`: slam arm/telegraph, boiling zones, lightning storm, black hole, overheat, ink blobs, minion list.

---

## Animation / VFX / geometry

Preferred spectacle stack (already proven in-engine):

| Tool | Use |
|------|-----|
| **`BlockDisplay` / `ItemDisplay`** | Custom geometry, weapons, rings, suns, blades. `Transformation` (JOML `Vector3f` / `Quaternionf`), `setInterpolationDuration`, `setTeleportDuration`, `Display.Brightness`, `Billboard.FIXED`. **`setPersistent(false)`** so props die with the fight. |
| **`TierPhaseShow`** | Small authored BlockDisplay rigs for T1 phase cinematics (not particle spheres). |
| **`CombatTheatrics`** | Themed transition start/pulse/end + slam/meteor helpers. |
| **`TransitionSpectacles`** | Void tornado / spinning crystal beams (hazardous). |
| **`FakeDestruction`** | Explosion *look* + flying debris **without world edits**. |
| **Armor / equipment** | Vanilla entity as silhouette; displays carry the identity. |
| **Particles / dust** | Accents, telegraphs, trails — support, not the main show. |

`model-engine-id` exists on templates but showcase bosses here are built with **displays + directors**, not ModelEngine dependency for the fight logic.

Sound: standard Bukkit `Sound` / `SoundCategory` at locations; layered pitches/volumes per beat. YAML `SOUND` / `DIALOG` skills for simple lines.

---

## Arena / environment

- Spawn location + leash define the stage.
- `FakeDestruction` / boom packets for impact without griefing.
- FallingBlock debris that does not place.
- Optional crystals / area hazards during transitions.
- WorldGuard: BossEngine can spawn tagged entities in protected regions via a guarded spawn hook — still avoid real block destruction.
- Chunk unload: arena/dungeon bosses often `setPersistent(false)`; reclaim/orphan logic exists on boot — **clean up every display you spawn**.

---

## Useful reference classes (technical only)

| Class | Why look |
|--------|-----------|
| `HollowSunDirector` | Gold-standard director: AI off, BlockDisplay rig, phase-owned transitions, death cinematic |
| `AshenSovereignDirector` | Floor-3 dragon spectacle; intro/death/arena props |
| `AshenSheathDirector` | ItemDisplay weapon + BlockDisplay petals; cherry phase shape |
| `PathwardenDirector` | Stronger-than-T1 scripted moves on a simpler body |
| `BossInstance` | Tick order, transitions, leash, shared hazards |
| `BossHits` / `FakeDestruction` / `TierPhaseShow` | Hit feel + safe destruction + display rigs |

Quality bar in this codebase = Hollow Sun / Ashen One directors, **not** particle-spam YAML bosses.

---

## Limits / don’t fight the engine

- **Tick budget**: every display pose × every player-visible tick counts. Prefer tens of authored displays over hundreds of particles.
- **Always** remove props on abort/death/despawn (`setPersistent(false)` + explicit `remove()`).
- Don’t dual-drive AI and director teleports — pick one (spectacle → AI off).
- Don’t rely on vanilla melee damage for signature hits — use `BossHits`.
- Pets are tagged `ItemDisplay`s elsewhere; never treat random displays as bosses.
- Phase transitions can grant invulnerability / freeze-AI — design windups that respect that window.
- Keep loot/HP/balance placeholders; focus on choreography. Wire a new boss like existing directors: YAML stub + `isMine()` director + hooks in `BossInstance`.
- Do not refactor BossEngine broadly; adapt locally and minimally.

---

## Minimal YAML shape (spectacle stub)

```yaml
id: your_boss_id
display-name: "&cYour Boss"
entity-type: WITHER_SKELETON   # silhouette only
attributes: { max-health: 100000, movement-speed: 0.25, scale: 1.3, knockback-resistance: 1.0 }
options: { silent: true, persistent: true, remove-when-far-away: false }
conditions: { max-instances: 1, leash-radius: 40, leash-action: TELEPORT }
phases:
  - id: phase_a
    health-percent: 100
    skills: []
  - id: phase_b
    health-percent: 50
    transition: { duration-ticks: 80, invulnerable: true, freeze-ai: true }
    skills: []
```

Combat identity lives in the Java director matching `your_boss_id`.
