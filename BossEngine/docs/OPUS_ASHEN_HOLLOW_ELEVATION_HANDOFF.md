# Ashen Sovereign + Hollow Sun elevation — Technical handoff (for Opus)

**Cursor does not implement this.** Map + brief only.  
**Stack:** Paper 1.21.1 · BossEngine directors  
**Date:** 2026-09-27  
**Scope:** elevate `dungeon_aetherion` + `hollow_sun` toward Seraphine craft. Seraphine = quality bar only.

---

## A) WHO IS WHO (names Robbi uses)

| Robbi says | Official display | Template id | Director |
|------------|------------------|-------------|----------|
| “Ashen / the dragon / Ashenworn” | **Aetherion · Sovereign of Ash** | `dungeon_aetherion` | `AshenSovereignDirector` |
| Hollow Sun | **The Hollow Sun** | `hollow_sun` | `HollowSunDirector` (+ `HollowBloodstorm`) |
| Seraphine / puppet / Hand boss | **Seraphine, the Hanging Saint** | `hanging_saint` | `HangingSaintDirector` + `instance/saint/*` |

**Not in scope:** `ashen_sheath` (Ashen Sheath / Blossom Blade drop), `aetherion.yml` (separate world-raid dragon), Floor-3 elites (`ashen_chainwarden`, `cinder_herald`).

Spawn / test items (from `BossSpawnItemService` / `items.yml`):

- Dragon: `dungeon_aetherion_anchor` (SET_SPAWN), `dungeon_aetherion_core` (SUMMON)  
- Hollow: `hollow_sun_anchor`, `hollow_sun_core`  
- Commands: `/boss spawn dungeon_aetherion`, `/boss spawn hollow_sun`

---

## B) WHERE EVERYTHING LIVES

### Engine glue

| Path | Role |
|------|------|
| `BossEngine/src/main/java/.../instance/BossInstance.java` | Owns director fields; `onBind` / `tick` / `beginDeath` / `abort` hooks |
| `BossEngine/src/main/java/.../manager/BossManager.java` | Spawn, tick, loot; `dungeon_aetherion` treated as arena boss (`setPersistent(false)`); Hollow Sun → `HollowReliquary` payout path; Seraphine → music box |
| `BossEngine/src/main/java/.../combat/BossHits.java` | Scripted %maxHP hits — prefer for choreographed damage |
| `BossEngine/src/main/java/.../fx/FakeDestruction.java` | Impact look + flying debris **without** permanent grief (or pair with restore if you edit blocks) |
| `BossEngine/docs/OPUS_BOSS_CONTEXT.md` | Older engine primer (still useful); **quality bar is now Seraphine**, not Hollow/Ashen |

### Sovereign of Ash (dragon)

| Path | Role |
|------|------|
| `resources/bosses/dungeon_aetherion.yml` | HP gates, phases, leash ~54; combat empty (`skills: []`) |
| `instance/AshenSovereignDirector.java` | **All** fight craft (~4.8k lines) |
| Arena | Permanent **Throne of Ashes**; live world commonly `ashen_void`. Director samples floor edges → `arenaRadius`. Soft-places Sovereign Reliquary on spawn anchor after death cinematic |

**Phase spine (YAML + director):**

| Phase | HP | Idea |
|-------|-----|------|
| `sovereign` | 100% | Air rule: Ashen Wake, Cinderfall, Talon Dive, Skyfire |
| `chained` | 66% | Transition: pylons + chains drag him down; ground kit (sweep, breath, eruption rings with gaps, lunge, lash); green heart openings |
| `unchained` | 33% | Transition “Last Sky”: snaps chains, drops a sun; soul wards = broken pylons; then faster meteor-dive Unchained |
| Death | — | Heart crack, crash, Throne Eclipse finale, chains him for good |

**Move enum (current):** `WAKE, CINDERFALL, TALON, VOLLEY, METEOR, LANDED, TAKEOFF, SWEEP, BREATH, ERUPTION, LUNGE, LASH`

**Body:** vanilla `ENDER_DRAGON` posed every tick; contact hits suppressed; all damage from telegraphed moves via `BossHits`.

**Tell language (documented in director — keep readable):** ember ground = fire lands; crimson ring = marked; white strobe = fires now; soul blue = safety; green heart = hit opening.

### Hollow Sun

| Path | Role |
|------|------|
| `resources/bosses/hollow_sun.yml` | HP gates, phases, leash ~40 |
| `instance/HollowSunDirector.java` | Main fight (~5k lines): AI off, invisible wither silhouette + armor + display mace/star core |
| `instance/HollowBloodstorm.java` | Collapse-phase storm cells (display geometry + strikes) — already a “helper package” pattern |
| Loot prop | `loot/HollowReliquary.java` (post-fight chest; don’t break payout wiring casually) |
| Arena | Permanent **Bloodstone** forge stage; director samples edges → `arenaRadius` |

**Phase spine:**

| Phase | HP | Idea |
|-------|-----|------|
| `main_sequence` | 100% | Star-knight: swipe, lance, slam, starcall, corona |
| `red_giant` | 66% | Swells/hovers; flare beams, lava prominences |
| `collapse` | 33% | Gravity wells, folds, shockwaves; ~15% Nova; Bloodstorm active after Starfall |
| Signature transition | 66→33 | Swell → black star → silence → Starfall (stand outside crimson disc) |
| Death | — | Armor burst; core returns to sky as a star |

**Move enum (current):** `SWIPE, LANCE, SLAM, STARCALL, CORONA, FLARE, PROMINENCE, WELL, FOLD, NOVA`

**Tell language:** gold / flare-red / violet ground = danger; white strobe = fire; green = opening; chevrons = beam direction.

### Seraphine (quality bar — read only)

| Path | Role |
|------|------|
| `resources/bosses/hanging_saint.yml` | HP gates only; fight is 100% Java |
| `instance/saint/HangingSaintDirector.java` | Multi-act dramaturgy |
| `instance/saint/SaintFx.java` | **Fixed anchor**; displays never teleport; motion = `setTransformationMatrix` + interpolation |
| `SaintBody`, `HandBody`, `Skeleton`, `ThreadLine`, `SaintProps` | Authored body/Hand geometry |
| `SaintStage`, `StageDressing` | Arena build / permanent stage / strike after fight |
| `SaintMath` | Easing / windows |
| Loot | `loot/SeraphineMusicBox.java` (+ listener) |

**Why Seraphine reads cleaner:** fewer simultaneous particle identities; geometry + timing + silence; arena is a character; moves have clear windups; cleanup is ruthless.

---

## C) ARENAS (Robbi already built them)

Treat permanent arenas as **given co-stars**, not blank pads. **No SaintStage builder** for these two — maps are permanent worlds; Seraphine’s `SaintStage` is craft reference only.

| Encounter | World | Server | Approx spawn / notes |
|-----------|-------|--------|----------------------|
| **Hollow Sun** | `bloodstone` | MMO-R | Spawner `hollow_sun_home` ≈ **0.5, 63, 115.5**. Permanent Bloodstone forge. Schematic lineage under `_ashen_void_build/` (BloodstoneDungeon). |
| **Sovereign showcase / preview** | often **`ashen_void`** | MMO-R | End-theme 1:1 remap of Bloodstone (`_ashen_void_build/build_ashen_void.py`). Warp ~**0.5, 68, 0.5**. Not code-built. |
| **Throne of Ashes (F3 dungeon)** | `aedun_f3_ashes` | MMO-D | Boss ≈ **266.5, 96, −70.5**; entrance `(0,100,0)`; gate `x=194`; arena AABB roughly `206–288 × −111..−31`. Warm-pooled via `DungeonWarmPool` / `AshesEncounter` (`prototype_ashes_3`). |
| Seraphine (ref) | wherever spawned | — | `SaintStage` builds/tears Proscenium |

**Do not conflate** `aedun_f3_ashes` (real F3) with `ashen_void` / `bloodstone` (MMO-R showcases). Dragon leash ~54; Hollow leash ~40. Director samples floor edges → `arenaRadius`.

Rules for arena violence:

- Prefer `FakeDestruction` + BlockDisplays over permanent grief  
- If you edit real blocks: snapshot + **full restore** on abort / death / despawn / plugin disable  
- Never leave the permanent arena broken after the instance ends  
- Spawner-managed dragon already `setPersistent(false)` so it doesn’t serialize into region files — keep that invariant  
- Related dungeon wiring (read if needed): `AetherionDungeons/.../AshesEncounter.java`, `DungeonWarmPool.java`

---

## D) WHAT “SERAPHINE LEVEL” MEANS HERE

Surpass current Ashen/Hollow *presentation* by:

1. **Readability** — one move readable at a glance; cut overlapping particle storms  
2. **Dramaturgy** — wind-up → signal → hit → hush → aftermath  
3. **Geometry + animation** carrying the show (SaintFx-style when it fits)  
4. **Thematic clarity** — ash-throne dragon vs stellar-core sun, unmistakable  
5. **Arena as co-star** — temporary destruction, reacting architecture, phase set dressing  
6. **Not** by stacking more particles, bigger damage numbers, or gluing five casts

If a beat still looks like “FLAME + LAVA + EXPLOSION_EMITTER carpet,” replace it with authored motion/geometry.

---

## E) KNOWN PAIN (from Robbi + code smell)

- Both directors already mix strong display craft with **heavy particle use** on many impacts — trim toward Seraphine’s ratio  
- Hollow’s fantasy can drift knight/void; Robbi wants **sun / core / collapse** louder  
- Ashen is already ash/fire-prison — sharpen spectacle + arena reaction, reduce chaos  
- Fights can feel “wirr / too much” mid-phase — prioritize fewer simultaneous hazards and clearer priority tells  
- `HollowBloodstorm` is a good modular pattern; don’t paste Stormcaller wholesale as identity

---

## F) OUT OF SCOPE / LOCKED

- Seraphine fight code (read-only)  
- Blossom Blade / Gravwell Cleaver  
- Boosters, sockets, anvil, ranks, TAB, shutdown countdown  
- AetherionCore growth  
- Deploy / live server  
- New bosses, armor sets, quests, economy redesign  

---

## G) DELIVERABLE SHAPE

- Elevated `AshenSovereignDirector` (+ helpers if needed)  
- Elevated `HollowSunDirector` / `HollowBloodstorm` (+ helpers if needed)  
- YAML phase gates only if timelines need new transition lengths  
- Short writeup: per-boss beat sheet, arena interactions, what was cut, restore/cleanup notes  
- Compile clean; no production deploy
