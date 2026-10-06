# Ankerhold opening orbit — "Welcome to Aetherion" (15 s)

**Status:** choreography authored and verified against `ankerhold_hub_v2.schem`. A preview is rendered. No plugin code has been written yet: per the handoff, that's the next task, in the plugin that owns first-join UX (Quests / `HarbourArrival`).
**Files:**
- `opening_orbit.yml`: keyframes, cue sheet, and 301 baked ticks the plugin can play directly.
- `ankerhold_opening_orbit_preview.mp4`: 15 s at 40 fps, rendered from the real schematic.
- `ankerhold_opening_orbit_storyboard.jpg`
- `orbit.py` / `render_preview.py`: re-tune and re-render.

![storyboard](ankerhold_opening_orbit_storyboard.jpg)

## The shot

| t | beat | what the player learns |
|---|---|---|
| 0.0 | Player's eye at the gangway, looking down the pier through the Spill Gate | "I arrived by ship" |
| 0.9–1.8 | Straight rise up the pier axis: the lagoon, the pier head, the town, and the **Tallybell centred under the mountain** | the harbour → capital axis |
| 3.4 | Climb out past **the Long Haul** and the sloop | sky-harbour, ships come and go |
| 5.6 | SE vantage: Ore Ridge, Clucksworth, Capital, and the Fallen Anchor on the far rim | mining south, farm south-west, danger west |
| 7.6 | E vantage: the harbour axis runs into the Tallybell, with the anchor behind it. One distant bell toll. | the city is the centre |
| 9.6 | NE vantage: Mount Skyreach, Whisperwood, Seawatch Light | that mountain matters; wood + Forage north-east |
| 11.4–13.5 | Swing down past the lighthouse and the sloop's sails, then drop onto the pier axis | coming home |
| 14.0 | **Through the Spill Gate** at eye height | the first walk, pre-played |
| 15.0 | Settle on the pier head ~6 blocks from Egon, who is just right of centre with the town and spire behind him. Hand-off. | "talk to him" |

It's a three-quarter arc around the east side plus a deliberate return, not a 360°. Readability won over geometry.

**Verified on the baked path:**
- **Clearance:** the closest block (any solid, incl. ships, clouds, gate, lamps) is **1.2 blocks** away.
- **Rotation rates:** max yaw rate **19°/s**, max pitch rate **12°/s**. There is no roll.
- **Speed:** eases in and out at both ends. Peak speed is ~95 b/s, but only on the outer arc, 250+ blocks from the island, where it reads as a slow drone orbit.
- **Hand-off cell:** `147.5 65 4.5` is standable (planks under, air at feet and head). The end yaw/pitch (`91.4 / 0.7`) equals the last frame, so the hand-off has no pop.

## Playing it in vanilla (implementation sketch for the plugin task)

- **Camera rig:** one non-persistent `ItemDisplay` (no item, tagged, `setPersistent(false)`) with `setTeleportDuration(1)`. Each tick, teleport it to `baked[k]` (x, y, z, yaw, pitch). Put the player in SPECTATOR and `setSpectatorTarget(rig)`. The client interpolates between ticks, so motion is smooth at any framerate. *First test:* confirm the rotation lerps as smoothly as the position. If it steps, raise `teleport_duration` to 2 and lead the samples by one tick.
- **Light:** `setPlayerTime(1000, false)` for the flight (morning, sun behind the arrival, so the town and Egon are front-lit). `resetPlayerTime()` at the end.
- **Text:** no titles during the flight. The shortened FIND EGON action bar comes at the hand-off.
- **Egon** (cue sheet in the yml): busywork at the kit crates from 12.9, turns to the arrival at 13.5, arm swing at 14.6. The green flare (`HarbourArrival.flareEgon`) starts at 14.0. This is all LivingNpc look-at / animation, with no fake props.
- **Sound:**
  - `aetherion:open_orbit` at tick 0. If the .ogg isn't ready, use a note-block motif.
  - The existing foghorn, low, at 0.2.
  - Soft wind for the climb.
  - **One Tallybell toll** at 7.6 as the spire crosses centre.
  - Spill spray particles at 13.8.
- **Order with HarbourArrival:** language pick → **orbit** → shortened FIND EGON. The flare and bells are reused; quest ids and order don't change.
- **Gate:** play on first join only, after Ankerhold is the active hub. Check a config flag or footprint, and never use old Origin coords. Set the shown-once flag on completion. Add an admin replay command.
- **Fail open, always.** On quit, kick, world change, transfer or plugin disable mid-flight:
  - stop spectating;
  - restore the gamemode;
  - teleport to `end` (or spawn.harbour if the tick is < 20);
  - remove the rig;
  - reset player time.

  Purge tagged rigs on enable.

**Playtest:** disconnect mid-flight, `/reload` mid-flight, second join (no replay), talk to Egon right after the hand-off, and two players arriving at once (each gets their own rig).

## Honest notes

- The preview renderer approximates the vanilla look; it isn't the client. Egon and the Fishmonger are **stand-in blocks in the preview only**. The real NPCs come from LivingNpc.
- The sloop's sails sweep past on the left at 12.5–13.2 s. That's deliberate (scale and parallax). If it feels too busy in-game, move keyframe 12.9 four blocks north.
- If the map changes, rerun `python orbit.py` (clearance + rates) before trusting the baked ticks.
