# AetherionGnomon — GNOMON, the surveyor that learns you

Dedicated encounter plugin. A 20-block tripod instrument that walks on three needle legs, hangs its heart from a chain,
and **builds a model of how you fight, then uses it against you, visibly**. Design bible: [DESIGN.md](DESIGN.md).

| | |
|---|---|
| Server | **mmo-r** (same backend as BossEngine) |
| Needs | `AetherionCore`, `BossEngine` (hard). Items/Quests optional (defense formula, German lines). |
| Owns | rig (displays), dial, adaptive mind, moves, all cleanup |
| BossEngine owns | HP pool, phase gates (`survey` 100 %, `verdict` 50 %), damage tracker, boss bar, loot, fight-log telemetry |
| Contract | `BossScript` registered for template `gnomon` (same path as Helios) |

## Build / install

```bash
mvn -B -DskipTests package          # from the repo root (module is in the parent pom)
```

Jar: `AetherionGnomon/target/AetherionGnomon-1.0.0.jar` → `plugins/` on mmo-r, after Core and BossEngine.
On first start it writes `plugins/BossEngine/bosses/gnomon.yml` (versioned; an older live file is backed up to
`gnomon.yml.v<old>.bak`). No other plugin's files are touched.

## Arena

Any **flat, open** floor, radius 24 (configurable). The dial is drawn with displays; the only world edits are the
Interdict walls (blackstone, placed only into air, journaled, restored on end / crash / `/gnomon cleanup`).

```
/gnomon setarena [radius]   stand in the middle of the floor
/gnomon start               or: /gnomon start here
```

Players join by walking onto the dial in **survival/adventure**. They get a private night sky and a private world
border (the seal). Leaving / dying / quitting gives both back. Creative/spectator = audience only (can't hit it,
can't be hit). Nobody on the dial for 45 s during the fight = it folds away (reset, no loot).

## Admin / tuning

| Command | |
|---|---|
| `/gnomon debug` | **mind HUD** on your action bar: posture, focus, reads (`*` noticed, `!` exploitable), last move |
| `/gnomon brain` | full dump: every player's reads + the last decisions with all move scores |
| `/gnomon force <MOVE>` | run a move now (spends the focus's best read if it has one) |
| `/gnomon hp <pct>` | jump HP (e.g. `49` to see the Phase II transition) |
| `/gnomon skip` | skip the intro |
| `/gnomon stop` · `status` · `cleanup` · `reload` | |

Every number lives in `plugins/AetherionGnomon/config.yml` (damage powers, telegraph ticks, fairness floors, memory
half-life, notice/exploit/forget thresholds, teaching window, focus rotation, pacing). Per-move weights can be
overridden under `moves.<ID>.base / cooldown / reads.<READ> / postures.<POSTURE>` without code.

**Background music:** when the fight starts, a note-block transcription of the Stalemate cover loops
(`SoundCategory.RECORDS` — client music slider). With `music.follow-players: true` every fighter hears it at
their feet across the whole dial. Tune via `music.enabled / volume / loop / track / follow-players`.
Tracks: `stalemate` (default, 20 t/s grid-locked, ~150 BPM, every 16th even) and `stalemate_174` (original
tempo, notes rounded to the nearest tick). Re-transcribe with `tools/nbtranscribe/run_all.py` (see its README).

## What to playtest (and what you should feel)

Go in alone first, survival, decent gear. Turn on `/gnomon debug` the second time, not the first.

1. **Intro (≈10 s).** The dial draws itself mark by mark, the floor rumbles, the tripod rises out of the ground and
   its legs unfold, the needle lights, the lens opens and *looks at you* (spyglass in your ear).
   → *"Okay… what is that."*
2. **Teaching (first ~35 s).** White iris. Long telegraphs. Every move is honest: shadows grow inside a chalk ring,
   turn crimson when they stop tracking, and hit when the fill touches the ring. Your path is drawn on the floor in
   glowing chalk. Hit the swinging plumb bob (the heart) — it recoils away from you.
3. **Get read.** Dodge every stomp **to the same side** 3–4 times. You'll hear a scratch and a page turn, your chalk
   trail flares, and you get: *"…left. Always left."* → *"Wait. It's reading me."*
4. **Get punished.** After teaching it turns red (PREY). Now a stomp's shadow appears where you *would* dodge, with a
   crimson chevron from you to it. Or the **feint**: a leg hovers over you, but the lens light sits on your dodge
   spot — then the real stab comes down there. **Rule: the eye never lies.** Watch the light on the floor, not the leg.
5. **Out-think it.** Once it's spending your left-read, dodge **right**. Glass cracks, the lens flinches, the heart
   sags gold: *"…you wanted me to see that."* Free damage window. Keep dodging right and it forgets left
   (*"…changed. Interesting."*) — then learns right. That loop is the whole fantasy.
6. **Greed.** Hit the gold heart after every Plumb Drop until the very last moment, several times. Eventually it
   drops, glows gold — but the lens stays bright red and stares at you. Swing anyway and the trap ring fires
   (*"…I saw you reach."*, jump it). Hold back and it staggers into a real window.
7. **Circle it / camp a corner / kite far.** Circling → the Hour Hand sweeps *against* you and walls rise across your
   route. Camping one slice → ink floods that slice. Staying far → Survey Lines split the floor through you.
8. **`/gnomon hp 49`** → *"I have read enough."* Every chalk line turns crimson, the dial turns, darkness, triple slam.
   Phase II: stickier memory, more predatory. **Recital**: it replays *your own recorded route* as a trail of
   eruptions — walk your habit again and you walk into your past.
9. **Kill it.** Lens cracks, legs buckle one by one, needle topples, the heart drops with a gong, the whole floor
   ledger lifts into the sky. Last line depends on how well it read you: *"…I knew you."* / *"…unmeasurable."*
10. **Then with 2+ players.** The lens rotates between you (*"It is watching you"* in your action bar when it picks
   you). Each of you is read separately; the party's habits (everyone under it, everyone circling) shape its mood.

Things to judge: does the walk feel heavy, do stomps land with weight, are telegraphs readable at night, does the
first "…left. Always left." land, can you *win the mind game on purpose*. Report in feel language; the numbers are
all in config.

## Cleanup guarantees

All displays: tag `ae_gnomon`, `persistent=false`, swept on enable / world load / `/gnomon cleanup`.
Walls: journal `plugins/AetherionGnomon/journal.yml`, restored on end, enable, cleanup (only if still ours).
Players: PDC mark `aetheriongnomon:in_fight` → sky / weather / border / hidden effects restored on leave, death,
respawn, quit, rejoin, plugin disable. BossEngine `/boss reload` or despawn → script `abort()` → full cleanup.

## Code map

```
GnomonPlugin        bootstrap, template sync, BossEngine script registration, start/abort
encounter/          Encounter (state machine, tells, openings, damage), GnomonScript (BossScript),
                    Party (members + private sky), Arena (the dial), Attack / Hazard contracts
ai/                 Brain (observe → evidence → reads → posture → focus → utility pick), Profile, Read,
                    Posture, MoveSpec/Moves (authored table), Tell, Decision      ← Bukkit-free, simulatable
rig/                Tripod (hub, 2-bone IK legs + gait, lens, needle), Leg, Pendulum (verlet heart)
attack/             Stomp, Feint, HourHand, PlumbDrop, FalseOpening, SurveyLine, Stride, Interdict, Recital, Breath
fx/                 Shapes (honest floor telegraphs), Ledger (chalk/ink route memory), Fx (sound, shake, words)
render/             Part / Parts (transformation-only block displays, dirty-checked)
world/TempBlocks    journaled temporary blocks
```
