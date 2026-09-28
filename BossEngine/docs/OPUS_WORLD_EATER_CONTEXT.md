# World Eater — Opus context pack (Cursor prep)

**Branch:** `feature/world-eater-boss`  
**Mode:** Cloud Agent implements. Cursor prepared references + this brief only.  
**Quality floor:** Hollow Sun + Seraphine (Hanging Saint). **Target: exceed both.**

Companion tech pack (engine facts): `OPUS_BOSS_CONTEXT.md`  
Creative / process brief: `OPUS_WORLD_EATER_PROMPT.md`

---

## What this project is

A full authored encounter — not “another YAML boss”:

```
arrival → short approach → gate + PRE-BOSS → gate opens → main arena
  → portal/ritual arrival → MAIN BOSS → arena change / final phase
  → death cinematic → custom loot chest
```

Loose fantasy seed: **World Eater** (void / dimensional corruption / world-tearing mage-entity).  
Seeds only — invent a stronger identity if you find one.

---

## Architecture you must reuse

| Piece | Path / role |
|--------|-------------|
| Plugin | `BossEngine` |
| Templates | `BossEngine/src/main/resources/bosses/<id>.yml` |
| Runtime | `BossManager` → `BossInstance` (tick, phases, leash, death handoff) |
| Spectacle | Java `*Director` with `isMine()` claiming a template id |
| Hits | `BossHits.hurt` (scripted % HP — not vanilla punches) |
| Geometry | `BlockDisplay` / `ItemDisplay` / `TextDisplay`, `setPersistent(false)`, JOML transforms |
| Safe boom | `FakeDestruction` (look without grief) |
| Loot payout | `LootService` / `LootFactory` — wire a custom prop like Reliquary / Music Box |
| Spawn API | `/boss spawn <id>`, `/boss give`, vials/anchors via `BossSpawnItemService` |
| Core tags | `BossKeys` + AetherionCore boss PDC |

**Showcase registration pattern:** add director field on `BossInstance`; hook `onBind` / `tick` / `beginDeath` / `abort` / `isDying` beside Hollow Sun / Hanging Saint. YAML stub for HP gates + leash; combat identity lives in Java.

---

## Quality-bar references (READ — do not “polish” into World Eater)

### Hollow Sun (`hollow_sun`)
- `instance/HollowSunDirector.java` (+ `HollowBloodstorm.java`)
- `resources/bosses/hollow_sun.yml`
- Loot prop: `loot/HollowReliquary.java` + `listener/ReliquaryListener.java`
- Bar: AI-off director body, BlockDisplay rig, authored phases/transitions, supernova-class death, session-less display loot chest

### Seraphine / Hanging Saint (`hanging_saint`)
- `instance/saint/HangingSaintDirector.java` + package (`SaintBody`, `SaintFx`, `SaintStage`, `Skeleton`, `MusicBox`, …)
- `resources/bosses/hanging_saint.yml`
- Loot prop: `loot/SeraphineMusicBox.java` + `listener/SeraphineMusicBoxListener.java`
- Stage helper: `/boss stage build|remove` → `SaintStage`
- Bar: multi-piece body, stage dress, musical identity, Music Box ending

### Ashen line (secondary bar)
- `AshenSheathDirector`, `AshenSovereignDirector`, `AshesEliteDirector`
- YAMLs: `ashen_sheath.yml`, `ashen_chainwarden.yml`, `cinder_herald.yml`
- Dedicated Multiverse world on live: **`ashen_void`** (do not trash; pattern for own world)

### Engine helpers
- `fx/TierPhaseShow.java`, `fx/CombatTheatrics.java`, `fx/FakeDestruction.java`
- `combat/BossHits.java`, `instance/TransitionSpectacles.java`

**Do not modify** Hollow / Seraphine / Ashen directors “for cleanup” while building World Eater. Extend patterns; don’t rewrite the bar.

---

## Worlds / Multiverse (live MMO-R facts)

Plugin: **Multiverse-Core** (`plugins/Multiverse-Core/`).  
Config: `worlds.yml`, `anchors.yml`.

Existing dedicated worlds (examples of the pattern — **not** your stage):
- `ashen_void` — Ashen showcase void
- `bloodstone` — arena world
- `aether_test`, `aether_veins`, `aether_islands`, `aether_guilds`, …

Main play world is Multiverse key `overworld` / folder `world`.

**Preferred for World Eater:** brand-new **empty VOID** world (e.g. `world_eater` / `aether_world_eater`), registered via Multiverse later.  
Build the approach + gate + arena **inside that world**, not in production overworld.

Cursor / ops will Multiverse-import when Robbi asks. **Do not deploy the world to production from Cloud.**

DEV entry ideas (you invent the clean path):
- `/boss spawn <pre_boss_id>` / `/boss spawn <main_id>` at authored anchors
- spawn vial / altar item like Seraphine core/anchor
- optional admin teleport to world spawn / gate

---

## Custom loot chests (study first)

| Prop | Boss | Contract |
|------|------|----------|
| Hollow Reliquary | `hollow_sun` | No chest block; Interaction hitbox; display-only; claim shares; auto-expire payout; `clearAll` on disable |
| Seraphine Music Box | `hanging_saint` | Identity-matched ending prop; theme + open choreography |

World Eater chest should feel like the **ending of THIS fight**. Reuse the “session-less display prop + LootService bundles” approach unless you have a stronger clean design. Do not invent a second global chest framework without need.

Explore-chest props in Quests are a different system (overworld POIs) — optional craft taste only.

---

## Items / weapons

Optional dedicated weapon/armor **after** the encounter lands.  
**LOCKED — never touch:** Blossom Blade (`ashen_katana` / AshenKatana*), Gravwell Cleaver.  
Do not divert the pass into a gear factory.

---

## Constraints / pitfalls

1. **Cleanup is mandatory** — every display, temp block, portal, arena mutation: abort / death / disconnect / unload / plugin disable. Prefer `setPersistent(false)` + explicit remove + restore maps for real blocks.
2. **Arena destruction** is encouraged **with restore**. Don’t permanently corrupt the void world unless designed + documented.
3. **Performance** — rare showcase; spectacle wins, but no unbounded entity spam. Hollow/Seraphine discipline.
4. **Don’t dual-drive** vanilla AI + director teleports.
5. **Live YAMLs** in `plugins/BossEngine/bosses/` are not force-overwritten on boot — jar resources are defaults.
6. **Paper 1.21.1** — vanilla blocks/API; JOML transforms for displays.
7. **LOCKED systems** elsewhere (ranks/TAB, boosters/anvil, shutdown countdown, Core game-loop growth) — out of scope.
8. Pre-boss ≠ mini-main-boss. Own language; death **is** the gate transition.
9. Do not rewrite BossEngine broadly; add director + YAML + helpers locally.

---

## Suggested file layout (you may rename)

```
BossEngine/src/main/resources/bosses/world_eater.yml          # main
BossEngine/src/main/resources/bosses/world_eater_warden.yml   # pre-boss (example id)
BossEngine/src/main/java/.../instance/worldeater/             # directors, arena, portal, chest
```

Document Multiverse world name + paste/schem paths in your finish note.

---

## Expected completion state

- Pre-boss + gate transition authored  
- Main arena in dedicated void world (schem/world files + how to load)  
- Main boss arrival + full fight exceeding Hollow/Seraphine bar  
- Arena transformation / destruction with restore  
- Death cinematic as a highlight  
- Custom ending loot chest  
- DEV spawn path  
- Compile BossEngine  
- Commit + push branch  
- Short finish doc: ids, world name, test steps, cleanup notes, residual risks  

**No production deploy** unless Robbi asks later.
