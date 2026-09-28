# Helios Requiem: Polish Pass 2

Branch `claude/helios-polish-pass2` on top of the live `_wt_helios_polish` tree
(`claude/helios-polish` @ b18fc46 + the live-only patches that were uncommitted there; commit 1 records
them). Config: `helios.yml` **v4** (old copy is kept as `helios.yml.v3.bak`). Boss YAMLs unchanged (v3).

## A · Act I, the Herald
* **New move: Starfall** (`/helios attack starfall|starfall2`, from 90 % HP): he crouches, leaps into
  the star's light and hangs there hunting one player with an amber floor ring (slower than a walk, so
  you drag it but can't lose it) and a white hairline. Half a beat before the dive the ring goes white and
  locks; he lands like a meteor: crater, cracks, and a flat shockwave rolling out (jump it). One beat on
  his knee afterwards. Below 50 % he dives twice, enraged three times, each dive on someone else.
* **Pacing:** 84 BPM (was 78), moves chain on the next beat, flurries of 3 (4 below half), Opening 4 beats
  (was 6) and it opens as soon as his own move is done (a running cage keeps going; no idle dead zone).
  He may run one hazard alongside his own move (two below half). Blink chains 3x when enraged.
* **Blink:** vanishes in place (no smear), a white streak shows his path, he appears wound-up one step
  short and lunges through the cut, follow-through held, then guard. Local shake on the hit.
* **Hitching fixed:** the body is pushed every tick with one tick of interpolation overlap (was every
  second tick, interp == cadence: a late packet froze him). Blade swarm blades were pushed twice per tick
  with two different interpolations: fixed. Mirror shuffle / reveal are jump cuts instead of slides.
  During the mirror the real one moves exactly like the clones (only the heartbeat gives it away).
* Intro: the four blades falling out of the corona were invisible (hidden with the body): now visible.
  Title is English ("THE HERALD").

## B · Intro orbit debris ("comets")
* The star's rotation was `clock * spinRate`: every spin change (e.g. 0.4 → 1 at intro tick 85) made the
  whole star, corona and belt jump. Now an accumulated phase, and spin changes glide.
* Star pushed on odd ticks (bodies on even) with 3-tick interpolation for a 2-tick cadence (overlap
  absorbs network jitter). Same overlap for Helios' rig, debris field, singularity, falling sectors and
  the supernova's 2-tick pieces.

## C · The signature beam (Constellation)
Summon → constellation → charge → relay unchanged, then:
* **HUNT** (8 beats): the floor beam locks onto a player and chases them. It has weight (limited
  acceleration, top speed ramps from a walk to just under a sprint), so a sprint plus a hard turn shakes
  it. Every 2 beats it picks the next player (ping + magenta hairline + action bar). Touch-down ring,
  scorch, cracks, molten trail; the whole chain surges on every beat, the mask stares at the touch-down.
  Prism side beams rake their lanes like walls, then fade.
* **TEAR:** the scar glows white for one beat (fuses + cracks + rising tone), then erupts along its
  length oldest-first: pillars of light, and every third point tears a hole through the floor. Holes grow
  back after 9 s unless that sector was destroyed for good (burned / shattered / seam).
* The Requiem keeps the short form (no hunt) so its music stays in time.
* Portal rims no longer jump when the spin rate changes (accumulated spin).

## D · Arena expansion
Not done: temporary floor outside the layout collides with the supernova rebuild (`restore` +
`revertTemps` would punch holes) and with rescue / island logic. The Tear gives the reshaping instead.

## E · Singularity
* **Herding:** Helios hangs just outside the party (between them and the rim) and swoops round every
  4 beats: hitting it means walking away from the pull, escaping it means walking into the pull.
* **New move: Gravity Tether** (`/helios attack tether`, singularity only): a violet hairline from the
  core marks the player farthest from the hole (collar closes on the beat, a note only they hear), then a
  chain of dark light binds them for 3 beats: extra pull (`helios.tether.pull`, sprint outward still
  just holds), one burn halfway, Helios holding the other end. Two players below 15 % / enraged.
* Portals weigh less during the singularity (2), tether 4.

## F · Death (supernova)
Structure unchanged, pushed further:
* **Unstable star:** seven accelerating convulsions during the swell: shells lurch out and snap back,
  rays flare, a crash of metal and glass (anvil, mace, chain, glass, later sonic boom + bell), camera
  jolts; the last three strobe the sky. The point trembles harder and harder.
* **Over-dimensional detonation:** after the main blast a second wall (anvil destroy, shield break, glass
  in three pitches, golem death, door break, explosion), then a roar from beyond (wither spawn, dragon
  growl, thunder, sonic boom), the sky cracking into huge planes of light, aftershocks rolling away.

## G · The Star Vault and the stairs
* ~2 s after the detonation the pulsar's point swells and the **Star Vault** is born out of the dead
  star: plates fly out and lock together round a white-hot seed, two halos spin up. It sinks over the
  pit to float above the centre.
* Beat by beat the star builds the way up: first a dais of light under the vault (gold, smooth quartz,
  quartz brick), then five steps at a time on **four staircases (N/E/S/W)** until they touch the Crown.
  Each step is star-matter flying out of the vault and becoming real stone on the beat, one note higher.
* When the loot arrives the lid lifts and each capsule rises out of the vault and settles on the dais.
  Loot wiring, claim rules, expiry and offline delivery are unchanged (`StarseedReliquary`).
* Cleanup: the vault clears its blocks and displays on every exit (victory close, abort, leave,
  disable); blocks sit only in the pit (no layout cells), so the arena restore never touches them; a
  crash is caught by the slot wipe.

## Config (helios.yml v4)
`tempo.herald 84` · `herald.pacing.opening-beats 4` · `herald.starfall.power 60` ·
`herald.starfall.wave.power 30` · `helios.portals.hunt-beats 8` · `hunt-speed-start 0.16` ·
`hunt-speed-end 0.27` · `hunt-accel 0.024` · `hunt.power 36` · `tear.power 44` ·
`tear-regrow-seconds 9` · `helios.tether.power 14` · `helios.tether.pull 0.03`.

## Playtest notes
* `/helios start <you>` → watch the intro belt (no lurch at the heartbeat 4 ignition), blades falling.
* Herald: `/helios attack starfall2`, `blink3`, `swarm`; `/helios hp 51` mirror (shuffles are cuts).
* Act II: `/helios attack portals` (hunt + tear), `/helios hp 24` then `/helios attack tether`.
* Death: `/helios hp 5` → Requiem → kill; listen for the convulsions and the second/third blast, then
  the vault birth, the dais + four stairs, capsules on the dais.
* Tuning knobs if it's too much: `hunt-speed-end`, `hunt-beats`, `tear.power`, `tether.pull`,
  `starfall.power`, `herald.pacing.opening-beats`, `tempo.herald`.
