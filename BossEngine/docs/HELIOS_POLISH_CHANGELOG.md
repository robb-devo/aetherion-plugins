# Helios Requiem: Polish Pass 1 (after the MMO-R playtest)

Branch: `claude/helios-polish`. It is `claude/confident-bell-u0mcjv` (Helios) merged with
`feature/world-eater-boss` (World Eater, Seraphine, Hollow Sun, Ashen …). `claude/helios-live-deploy`
was not on the remote, so the live-only patches were rebuilt here from the spec (see "Live patches").

## Deploy (MMO-R)

Jars come from CI: **Actions → "Helios jars" → latest run on `claude/helios-polish` → artifact `helios-jars`**
(contains `BossEngine-1.0.0.jar` and `AetherionItems-1.0.0.jar`).

```powershell
cd C:\Users\Robbi\IdeaProjects\aetherion-ops\bin
.\ae.ps1 deploy-jar mmor -Jar <dl>\BossEngine-1.0.0.jar     -DestName "BossEngine-1.0.0.jar"     -NoRestart
.\ae.ps1 deploy-jar mmor -Jar <dl>\AetherionItems-1.0.0.jar -DestName "AetherionItems-1.0.0.jar" -NoRestart
.\ae.ps1 restart mmor
```

Check `logs/latest.log` for:
* `[Helios] Updated helios.yml to v3 (old copy: helios.yml.v0.bak)` (same for `bosses/helios_*.yml`)
* `[Helios] Ready: world 'helios_requiem'`

The config files are now **versioned**: live copies older than the jar are backed up to `.bak` and
replaced, so the new balance actually reaches the server. Custom edits: copy them from the `.bak` back
into the new file.

Test: `/helios start <you>`, then phases `/helios hp 51 | 59 | 39 | 24 | 5`, and `/aetherionitems helioskit`
for the gear.

## What changed

### Language
Player-facing text is English only (titles, bars, action bars, chat, phase names, loot). `Lang.pick` is gone.
Internal phase ids (`korona`, `zerfall`, `singularitaet`) are unchanged so live YAML keeps working; players never see them.

### Live patches (rebuilt)
* Death: respawn **on the arena floor in survival** with 4 s grace (Resistance V, no Helios hits). No echo by
  default (`echo.enabled: false`). Wipe only if nobody is standing for 8 s (never on a death screen).
* Quest boss bar + quest hints hushed every second inside the instance (World Eater pattern), restored on exit.
* Herald pays no loot; Helios pays through the capsules. Sky packets only on change.

### Pacing (was: too fast, too strong, no windows)
| | before | now |
|---|---|---|
| Tempo Herald / Corona / Decay / Singularity / Requiem (BPM) | 90 / 100 / 108 / 80 / 120 | 78 / 88 / 96 / 72 / 112 |
| Moves per flurry | 3, rest 3-4 beats | 2 (3 below 50 % / 60 %), then an **Opening** |
| Opening (new) | none | 6 beats, boss starts nothing, **x1.35 damage**; gold floor ring shrinks as the timer |
| Concurrent attacks Herald / Helios | up to 2 / 3 | 1 until 50 % / 60 %, then 2 |
| Blink-Strike tell (wind-up / mark) | 1 / 1.5 beats | 1.5 / 2 beats |
| Move cooldowns | 6-40 beats | 8-44 beats (Herald), 14-30 (Helios) |
| Damage (power) | blink 70, swarm 55, cage 35, platform 80, lance 32; portals 45, plasma 50, flares 65, meteors 70, cage 40, seismic 90 | 55, 42, 26, 60, 24; 36, 34, 50, 52, 30, 62 |

In an Opening the Herald drops to one knee (core guttering). Helios sinks its heart onto the Crown at
sword height, opens its rings and slows down.

### Waves and knockback
* Every Helios knockback goes through one rule: scaled to 40 %, capped at 0.4 horizontal / 0.3 up,
  and **no horizontal shove at all if it would carry you over an edge** (edge grip).
* Plasma rings: 2-3 per cast (was 3-5), cooldown 16 beats (was 8). Solar wind: push 0.5 (was 1.15),
  edge grip at the brink, cooldown 30 beats. Seismic cooldown 22 beats.
* Void rescue: the star **catches you**. A gold beam lifts you above the nearest solid ground and you
  float down (slow falling), 3 s grace, hit 35 (was 70).

### Black hole
* Pull 0.045 (was 0.085), inward speed capped at 0.24 (a sprint outward wins), sneaking x0.35.
* **It breathes with the bar:** full pull on beats 1-2, 30 % on 3-4. A bass note plus a thicker horizon
  ring mark the haul, so you run on the slack.
* Horizon 6 → 2.5 (was 7.5 → 3), horizon hit 30 every 0.8 s. The core no longer crushes you to 1 HP:
  one hit, then it throws you out with 2 s grace.
* Vision/pitch toned down (vignette max 45 %, slowness only within 6 blocks, pitch -28 % max).
* Islands are torn away every 14 s (was 9), at least 4 stay.

### The glass block over players' heads
That was the old white-flash prop: an inverted, head-riding glass cube that rendered as a plain block on
real clients. **Removed.** Flashes are now a night-vision pulse (the night arena floods with light), with an
optional resource-pack glyph (`resourcepack.flash-glyph`).

### Supernova (rewritten, `requiem/Supernova.java`)
1. **Last breath**: every light in the arena goes out, the stars fade, the heart stutters slower.
2. **Collapse**: rings, mask, accretion disk and orbiting rubble spiral into the heart while light streams in from the rim.
3. **The point**: two seconds of total silence, one heartbeat.
4. **Swell**: four nested shells turning against each other, 16 corona rays, the whole floor turning to
   light ring by ring outward (real blocks, restored), a rising chord, the rumble.
5. **Detonation**: night turns to noon in one frame. Shock rings on three levels plus two upright ones,
   a sphere of light plates, ejecta, rays out to ~90 blocks, ender-dragon death under it. Everyone floats up (slow falling).
6. **Nebula + pulsar**: coloured veils drift out, a pulsar sweeps two beams, dawn.
7. **Giving back**: the arena rebuilds from light, the glow draws back inward, the lights return, the capsules land.

**Glitch fix:** slow motion used to stretch interpolation while poses kept arriving every tick (the client
restarts from the previous target, so pieces snapped). Interpolation now always matches push cadence, and
swallowed disk rocks teleport instead of flying across the screen.

### Portal beams: Constellation (rewritten)
The chain works as before (entry/exit pairs, cyan/magenta, optional prism), now staged as a signature move:
1. **Summon**: each portal ignites on its own half-beat as a rising arpeggio: spark, rim drawn round, face spins in, shards orbit.
2. **Constellation**: pair threads carry **light-points flowing from entry to exit**, the whole beam path is
   traced the same way, and amber floor lanes with **arrowheads** show where and which way the rake goes.
3. **Charge**: the heart flares and the feed beam swells.
4. **Relay**: the light hops through the chain one segment per half-beat, each hop a step higher in pitch,
   with a portal pulse and a local shake.
5. **Rake, overload, implode**: the floor beams sweep, leaving flash rings, cracks and molten scars;
   an overload flash; the beams snap; every portal collapses on a falling chord.

It only hurts once a segment is lit. Standing near a live beam tints your screen edges as a warning.

### Arena destruction
After the Corona burns, its rubble keeps circling just outside the rim (more after the Course
shatters), some of it glowing. At the death it all falls into the heart. Footing is unchanged.

## Loot: Dawnbearer set + Solstice

| id | item | CMD |
|---|---|---|
| `helios_crown` | Dawnbearer Crown | 4201 |
| `helios_heartplate` | Dawnbearer Heartplate | 4202 |
| `helios_orbit_greaves` | Dawnbearer Orbit Greaves | 4203 |
| `helios_dawn_treads` | Dawnbearer Treads | 4204 |
| `helios_solstice` | Solstice (sword) | 4205 |
| `random_helios_armor` | one random Dawnbearer piece (loot id) | |

* Stats: one step over Worldhide/Worldbite (`BossGearBalance`). Mythic, unbreakable, gold/silence trim.
* **Second Dawn** (full set): below 30 % HP you go supernova: heal 30 %, Absorption II 8 s, a gold ring
  that burns and throws back enemies within 6 blocks (45 dmg). Cooldown 120 s.
* **Constellation** (Solstice, right-click, 10 s): a cyan portal at your hand, its magenta twin 6 blocks
  above your aim (24 range); light-points flow between them, then a lance of sunlight falls onto the
  target (70 dmg, radius 2.8, burns). Never hits players, pets or minions.
* Drops: rank 1 Solstice 15 % + one armor piece guaranteed, rank 2 7 % / 75 %, rank 3 3 % / 40 %,
  every damager 6 % for an armor piece. Test kit: `/aetherionitems helioskit`.
* Textures: CMD 4201-4205 are reserved; without pack art the items look like netherite with a gold trim.

## Open / not verified
* **Not deployed by me**: this cloud environment has no SSH key for Hetzner and blocks port 22.
  The jars are in the CI artifact; deploy with the commands above.
* Not playtested: timings, Openings and the supernova need eyes in game (`/helios debug watch` for load;
  the supernova peaks around 250 displays).
* Merge: this branch is not your local `claude/helios-live-deploy`. If that tree has extra fixes (e.g.
  early-boss polish), merge `claude/helios-polish` into it instead of deploying this tree alone.
