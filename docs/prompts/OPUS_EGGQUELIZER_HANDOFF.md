# EGGQUELIZER — OPUS HANDOFF

**You implement this boss.** Build it. Make it absurd and elite.

**You do NOT:** scan the monorepo, write READMEs/ship folders/manifests, redesign BossEngine, balance HP/loot, touch unrelated plugins, or “improve” Seraphine / Hollow Sun / World Eater / Helios / signature weapons.

**Worktree:** current Cursor workspace root (Aetherion monorepo). Boss lives in **BossEngine only**.

---

## 1. Encounter vision

**EGGQUELIZER** is a giant egg on tiny legs with a huge front-facing speaker for a face. It runs an arena-wide speaker network like a living PA system.

Joke = the egg.  
Respect = the fight.

Player feeling: *“Why the fuck is this egg boss this well made?”*

Physical. Rhythmic. Readable. Personality through tilt / wobble / silence / bass — not chat spam. Arena speakers are **gameplay**, not deco.

Inspiration vibe (Mouthpiece / Borderlands speaker hazards) = fuel only. Do **not** copy another game. This is an Aetherion original.

---

## 2. Visual design

Multi-part **display-entity** body (BlockDisplay / ItemDisplay hierarchy). Not a reskinned zombie with particles.

**Parts**
- Large egg shell (primary mass / silhouette)
- Tiny legs (unstable balance, never normal mob gait)
- Huge front speaker (THE face — grille / cone that breathes)
- Optional side amps, cables, small EQ bars / antenna
- Strong silhouette readable from 30+ blocks

**Personality language (no face)**
- Where the speaker points
- Tilt / lean / smug aim
- Squash–stretch / spring / recoil on bass
- Vibration intensity
- Distortion when damaged
- Pauses and silence

Walk like an egg: wobble, tip, recover — never vanilla pathfinding choreography while the director owns the body.

---

## 3. Arena design

Dedicated **EggStage** built at spawn (Seraphine `SaintStage` pattern: claim, build, strike/clear on abort).

**~12–20 major speakers** as real fight objects:
- Wall / floor / tower / elevated
- Visible cables / conduits between a few of them (feedback path readability)
- Each speaker is a display rig + cached reference (no per-tick world scans)

Player learns the network by **looking**, not a GUI.

---

## 4. Speaker state system (mandatory)

Every arena speaker: **IDLE → CHARGING → PRIMED → FIRING → COOLDOWN**

Readable at distance:
- Charge: hum + light + vibration amp
- Primed: hard telegraph (cone flash / dust puff / pitch hold)
- Fire: one clear physical pulse (not particle carpet)
- Cooldown: sag / crackle / dim

If the player cannot answer “which speaker is about to fire?”, the state machine failed.

---

## 5. Mechanics (seeds — improve freely)

### Bass Drop
Front speaker aims → charge → horizontal/physical bass pulse (shockwave / ground ripple / knockback). Jumpable or sidestep-readable. Feels like air hit you.

### Left / Right Channel
Speaker banks alternate. Spatial pressure. Escalating tempo later. Egg tracks player with speaker between banks.

### Subwoofer
Floor speakers → vertical launch pulse. Optional soft play with traversal if it feels good; **do not** force Skyreaver into the fight.

### Feedback (signature)
Activation travels the network: A → B → C → D…  
Arena becomes an instrument. Sequence must be learnable first, then nastier when cracked.

### Overclock (setpiece)
Egg freezes → vibrates → front speaker overfills → speakers wake in a rising pattern → **BASS OVERLOAD**.  
Spectacle with **counterplay** (safe lanes / timing windows / jump beats) — not “stand in unavoidable nuke.”

### Distortion phase
Shell cracks, internal glow, mangled cone, audio warble, unstable wobble. Patterns denser but still readable. More pulses / messier feedback — never unreadable spam.

### Final escalation (optional if stronger)
Shell fails open; speaker becomes glowing core; network syncs. Only if it elevates the fight — skip if Overclock + Distortion already peak.

**Freedom:** replace / merge / invent attacks if the result is stronger. Keep sound + physical speakers as the spine.

---

## 6. Animation / choreography

- Own the body (`ownsBody()`): AI off; director drives pose via transformations + interpolation
- Displays stay near an anchor; move with **Transformation**, not teleport spam (SaintFx / HeliosStage pattern)
- Attack beats: aim → hold → fire → recoil → settle
- Fake-outs: aim left, fire right bank; long silence then drop
- Damage reaction: flinch squash, cone dent, brief static

---

## 7. Audio direction (primary system)

Bass, sub, charge hum, crackle, feedback scream, **silence as weapon**.  
Positional sounds from the **firing speaker**, not only the egg.  
Contrast > volume spam. Vanilla / existing sounds OK; custom RP only if Eggquelizer truly needs it (keep tiny).

---

## 8. Death sequence (authored)

Not a generic boom.

1. Final charge / all speakers wake  
2. Peak tension  
3. **Hard silence**  
4. Speakers die one by one  
5. Egg loses power, tips over like a dead appliance  
6. Tiny final pop / soft gag  

Comedic and polished. Matches the personality.

Wire `beginDeath()` → hold alive through cinematic → return finished so engine pays loot/despawn. Placeholders for HP/loot OK.

---

## 9. Technical implementation guidance

### Path (required)
**BossInstance Director** (Seraphine / Hollow Sun style) — not Helios `BossScript`, not World Eater void world, unless you invent a strong reason (you shouldn’t).

### Create
- `BossEngine/src/main/resources/bosses/eggquelizer.yml` — spectacle stub: phases with `skills: []`, transition invuln/freeze; placeholder HP
- `BossEngine/src/main/java/de/aetherion/bossengine/instance/eggquelizer/`
  - `EggquelizerDirector.java` — Act/Move FSM, bind/tick/death/abort/clear
  - `EggBody.java` (or equivalent) — egg + legs + face speaker rig
  - `EggStage.java` — arena speakers + cables; build/strike
  - `EggFx.java` — spawn helpers, `push(Display, Matrix4f, interp)`, audience radius
  - `EggMath.java` — small helpers if needed
  - Optional: `EggSpeaker.java` state machine per installation

### Edit (minimal wiring only)
- `BossInstance.java` — field + `onBind` / `tick` / `isDying` / `beginDeath` / `abortCinematic` / `clear` / `blocksDamage` / `ownsBody`/`ownsTransition` as needed (mirror `HangingSaintDirector`)
- `BossEngine.java` — `saveResourceIfMissing("bosses/eggquelizer.yml")`
- Optional: `items.yml` + `BossSpawnItemService.ensureDefaultItems` for a test vial (`eggquelizer_core` or similar)
- Optional: custom death prop only if you want a gag drop-chest — else default loot path

### Reuse
- `BossHits` for scripted damage (% max HP)
- `BossKeys` tagging
- Display: `setPersistent(false)` + explicit remove on `abort`/`clear`
- Cleanup: idempotent `clear()`; no orphan displays
- Study (read, don’t gut): `instance/saint/*` (`HangingSaintDirector`, `SaintStage`, `SaintFx`, `Skeleton`), optionally `HollowSunDirector` for single-class FSM clarity

### Spawn for testing
`/boss spawn eggquelizer` after jar load. DevMenu Items is out of scope unless you only add the BossEngine spawn vial.

### Performance
Cached speaker list. Scheduled choreography. Bounded displays. No per-tick arena entity scans. Interpolation over spawn storms. No griefy block edits unless temp stage blocks with full strike restore (SaintStage air-only claim style).

---

## 10. Hard constraints

**ONLY Eggquelizer.**

Do **NOT** modify:
- AetherionCore, Items, Quests, Dungeons, Hub Origin, Guilds, Skills, ranks, boosters, anvil, TalkUx
- Existing bosses/directors (Seraphine, Sovereign, Hollow Sun, World Eater, Helios, …)
- Locked signature weapons
- BossEngine architecture refactors

If something outside this list is truly required:

`EGGQUELIZER DEPENDENCY: <one line>`

…and stop — do not silently patch it.

No HP/loot balancing essays. Placeholders fine.  
No ship folder / README / manifest theater. Code + yaml + needed assets only.  
Compile BossEngine if you can; **do not deploy** unless Robb asks.

---

## 11. Freedom

You may improve, replace, combine, or invent mechanics if the fight gets **substantially** better — as long as:
- it’s still Eggquelizer (egg + face-speaker + arena speaker network + sound choreography)
- readability and physicality stay first
- scope stays BossEngine Eggquelizer

Quality bar: sit beside Aetherion’s best authored encounters. Absurd concept. Dead-serious craft.

**Go.**
