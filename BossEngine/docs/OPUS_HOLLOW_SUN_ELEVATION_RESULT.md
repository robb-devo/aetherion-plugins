# Hollow Sun elevation — result

**Date:** 2026-09-27 · **Scope:** `hollow_sun` only · **Build:** `mvn -o compile` clean · **Not deployed.**
Backup of the pre-pass files: `BossEngine/_backup/hollow-sun-elevation-2026-09-27/`.

## Files

| File | Change |
|------|--------|
| `instance/HollowSunDirector.java` | Elevated in place (spine kept). All dust telegraphs replaced; new rig identity, arena co-star, sky, hitstop, EJECTA. |
| `instance/HollowProps.java` | **New.** The floor language as display props on fixed anchors (glyph, lane, sector, ring wall, seams, slash, rays, ring, loop). |
| `instance/HollowAccretion.java` | **New.** Collapse accretion disk built from real Bloodstone floor plates. Replaces the Bloodstorm. |
| `instance/HollowSky.java` | **New.** Per-player sky time that follows the star; always released. |
| `instance/HollowBloodstorm.java` | **Removed** (backed up). Stormcaller weather, not a star. |
| `resources/bosses/hollow_sun.yml` | Header + Collapse name/dialog colors only. Transition lengths unchanged. |

## One tell language (all phases)

Every danger zone is a **sun glyph** on the floor: a glowing rim, rays, and an eight-point star fill whose points touch the rim **on the hit tick**.

- Gold in Main Sequence, flare-red in Red Giant.
- Blue-white with **inward** rays in Collapse: the star's light is falling in now.
- **White** rim = now. **Crimson** = never stand here (Starfall, well core). **Green ring at his feet** = he is spent; it shrinks as the window closes. **Glowing ridge** = shockwave, jump it.

All of it is block displays: one start pose, one end pose, client interpolation. Tells no longer vanish for players on reduced particle settings.

## Beat sheet

**Arrival.** The star streaks in; the fight's first sun glyph counts down the landing. Impact: seams race out from the crater, a ridge rolls to the rim, then the armor assembles.

**Main Sequence** (clean; the forge only scorches where he strikes):
- **Swipe.** Body turns (3 ticks), then the cone commits. The hit leaves a white-hot slash arc.
- **Lance.** A thin searching light tracks his aim, then a lock cue (crossbow + beacon) and the lane draws in full. The thrust burns its line into the floor.
- **Slam.** Mace climbs while he tracks; the cone commits 16 ticks out. Impact: seams crack forward, a ridge, the floor jolts under everyone near, 3-tick hitstop.
- **Starcall.** Each meteor is a sun glyph plus a comet-tail rod. No flame, lava or smoke carpet.
- **Corona.** He stands at the center of his own glyph. The core inhales (dims), then swells. Rays lance out, 2-tick hitstop, then the opening ring.

**Ignition** (100 ticks): the red giant's first glyph around him; the sky slides to dusk. The Bloodstone splits in nine glowing seams that stay hot into the phase. At 45 the photosphere blooms and closes around the knight. At 90 the blast.

**Red Giant** (he floats inside his own star; the pulsing envelope breathes like a variable star):
- **Flare.** Each beam carries a white leading-edge plate that flips the instant the sweep reverses; this replaces the dust chevrons. The beams burn a short wake into the floor.
- **Prominence.** A magnetic loop of plasma is drawn from the forge's rim toward you, segment by segment. When it touches down the plasma rides it in, landing on a glyph.
- **Ambient.** The floor under him boils (short client-side magma/shroomlight cells).

**Starfall** (240 ticks, the signature):

| Tick | Beat |
|------|------|
| 24–60 | Rises and swells into a red sun overhead; the sky deepens. |
| 80 | **Implosion.** The envelope crushes to a point in 3 ticks, the sky **cuts** to night, and every scorch on the floor goes dark at once. |
| 86 | `stopAllSounds`: real silence. |
| 104 | One bell. |
| 108 | The crimson, inward-rayed glyph. |
| 120–170 | The black star falls, trailing one stretched rod of light. |
| 170 | Impact: crater, cold seams to the rim, three jumpable ridges. **The floor plates tear loose and rise.** |
| 190+ | He stands; crown and mace re-form. |

**Collapse** (black core, blue-white photon shell, and the forge's own floor orbiting him in a tilted accretion disk):
- **Disk.** Plates heat ember → gold → white as they spiral in and are swallowed (the core kicks). He keeps tearing new plates up around himself.
- **Ejecta** (new). He points; the disk flings one plate per player (max 3) out to its rim and down onto a blue glyph. Damage, and debris made of that plate's own block type.
- **Event Horizon.** Pull radius = slow blue floor ring; kill core = fast crimson ring (counter-spinning). The disk is blue glass plus hot matter.
- **Fold.** An inward-rayed glyph slides after the target, plants, and counts down. Landing: cold seams, jolt, 4-tick hitstop.
- **Last Light** (≤15%). Integrity is a green floor ring eaten away segment by segment as players crack the core. The night **pales toward dusk** while it charges. Broken: the sky falls back to night. Detonated: one flash of noon.

**Death.** The plates fall back into their own holes one by one and the floor heals while he shakes apart. Then armor burst, the core climbs, fades, true silence, supernova (geometry kept, particle spray roughly halved). The sky flashes noon for 5 ticks, then night again, and releases at the end.

## Readability rules added

- **One family of ground hazards at a time.** While meteors, prominences or a thrown plate are in flight, he only swipes (the Nova may still start). No Starcall or Ejecta while a well is pulling.
- **Wind-up → signal → hit → hush.** The body tells first, the floor commits after (lance lock, slam commit, swipe commit, fold plant). Heavy hits get 2–4 ticks of hitstop.
- **"Spent" is honest.** While exposed he stands still in his green ring (hover-settles in Red Giant) and starts nothing new.

## Arena interactions and restore path

Nothing edits the world. There are three kinds of temporary change, each with a guaranteed restore:

| Kind | What | Restored by |
|------|------|-------------|
| Client block paints (`paintSurface`) | Craters, lance burns, flare wake, floor boil | Per-cell timer (`tickScorches`); all at once on implosion, abort and end of death (`revertAllScorch`) |
| Accretion holes (client paint to magma) | Torn floor plates | Plate reseat on death (`release` → per-hole restore); `clear()` on abort, rebind, despawn, disable and end of death. Crater paints never touch accretion-owned blocks, so a scorch revert can't "heal" a hole early. |
| Displays | Props, rig, plates | `props.clear()` in `clearCombatFx`; rig in `clearRig`; plates in `accretion.clear()`. All non-persistent and tagged `beam_fx`. |
| Sky | Per-player time | `sky.release()` on abort and end of death; players who walk out of 72 blocks are reset within a second. Overworld skies only. |

Engine paths: despawn, leash, disable → `BossInstance.despawnMinions()` → `abort()`. Death → `tickDeath()` end. Body rebuild → `onBind()` restores the current phase's state (envelope / night + fresh disk).

## Cut

- **HollowBloodstorm** entirely: wool thunderheads striking every 8 ticks per cell on top of wells, folds and waves.
- Every per-tick dust telegraph: rings, lanes, cones, arcs, sigils, spokes, flare guides and chevrons, the Starfall disc, the nova integrity arc. The dead helpers are deleted too.
- Violet/purple as Collapse identity: purple glass, amethyst crown, crying obsidian, `REVERSE_PORTAL`/`PORTAL` everywhere. Violet survives only in the supernova nebula, where it reads as a nebula.
- Particle carpets on impacts and flights: flame/lava/firework/end-rod bursts per hit, per-tick flame along beams and meteors, soul-fire trails. Particle call sites 151 → 104, and the remaining ones are mostly one-shot garnish.

## Consciously not copied from Seraphine

- No marionette, threads, Hand, skull audience, proscenium, stage building, follow-spots, music box score, or stop-motion.
- The floor telegraph is a **sun glyph** (star fill, rays that flip inward after collapse), not her gold theatre circle.
- The arena is **not built**: Bloodstone stays a real, player-built forge. We only overlay it and tear pieces of it up.
- The sky is driven by the **star's physical state** (swell → dusk, implosion → hard cut, supernova → flash). There's no dawn curtain call; the night ends with one more star in it.
- Borrowed only the craft principles: fixed-anchor interpolation, push-at-age-2, hitstop, floor bounce, ruthless cleanup.

## Verify in game (couldn't be tested here)

1. Glyph fill timing matches impacts (meteor, corona, fold, ejecta, Starfall).
2. The flare white edge sits on the leading side and flips at the reverse.
3. Plates tear, orbit, get flung and reseat. After death and after `/boss despawn`, every hole is real Bloodstone again.
4. Implosion silence and the sky cut. `bloodstone` must be an overworld-type world for the sky; otherwise it's a harmless no-op.
5. Display count at the Starfall impact (seams + three ridges + plates, roughly 250 briefly). Trim `RingWall` segment count in `tickWaves` if a client struggles.
6. Server data folder: `hollow_sun.yml` is only saved if missing, so the new Collapse name color needs a manual copy (optional). No timing changed.
