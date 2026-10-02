# Terminus — "Here Ends the World"

Item id `terminus` · `LIGHTNING_ROD` (a survey stake) · MYTHIC · Test Gear flagship.
Given via **Dev Menu → Test Arena → Test Gear**, top row, centre (slot 13). `createById("terminus")` works too.
Right-click, main hand. Cooldown 60 s (placeholder), no cooldown in the Test Arena. Only one Terminus can run at a time on the whole server: "The world already has an edge."

## Concept

The weapon is **the world border**: the real vanilla one that normally sits thirty million blocks away.

You set a brass survey benchmark where you look. The edge of the world then appears 50 blocks out on every side, turns red and closes in. It passes through everyone watching, and their screens go red because they are now outside the world. It herds every enemy onto one block. The world has to go somewhere, so it squeezes out of that block as a 29-block **core sample** of the ground (with a **command block** at the bottom of the world), with the crushed enemies stacked on top. Then the world lets go. The edge snaps back out in green, the core sample drops back into the ground, and the tower is left standing on the benchmark on nothing. It looks at you. It looks down. It falls.

It has one identity, told in the border's own colour code (blue at rest, red closing, green releasing) and in squares everywhere: the grid, the benchmark, the needle and the landing shock rings.

## Beat sheet (20 tps, measured from the click)

| t | Beat | What happens |
|---|------|--------------|
| 0.0 s | **Click** | Lodestone-lock click and a vault shutter. The copper benchmark drops 2.6 blocks and twists 45° into grid alignment. |
| 0.2 s | **Set** | It lands flush with a heavy-core thud and a small square shock ring. The action bar reads `edge of the world · 29,999,9xx blocks`. |
| 0.35–1.2 s | **Survey** | A centre cross races out 100 blocks (spyglass sound). The parallel lines fan out a pair at a time, each with a hi-hat tick: an 11×11 grid, 100 blocks on a side. |
| 1.4 s | **Horizon** | Every sound stops. A per-player virtual border pops up at 100 wide, faint and blue on the horizon. The outer grid lines and the benchmark glow blue: they are the border's footprint. Chat shows `[Name: Set the world border to 100.0 block(s) wide]` (vanilla translation keys). The HUD snaps to `edge of the world · 35 blocks`. |
| 1.4–2.5 s | **Tension** | A warden heartbeat every second. Each beat pulses a red vignette on every screen, driven by the border's warning distance. |
| 2.5 s | **It comes** | Border, grid edge and benchmark turn red. Chat: `Shrinking the world border to 1.0 block(s) wide over 3 second(s)`. Beacon power-down, a wind bed, a rising beacon hum. |
| 2.5–5.5 s | **Advance** | The walls creep, then rush (about 0.1 → 2 blocks/tick). The grid compresses with the space it measures. Enemies inside 36 blocks (up to 10) are shoved by the walls. Heartbeat speeds up from 16 to 5 ticks. The walls plough dust off the floor. |
| ~5.2 s | **Crossing** | As the wall passes each player: a riptide whoosh, a low glass tone and a hurt-camera shake. From then on they have full red screen edges and the HUD reads `you are 14 blocks outside the world`. A warden sonic charge builds toward the crush. |
| 5.5–5.7 s | **Crush** | 7 → 1.3 wide in 4 ticks (piston grab), with a clamp that pulls stragglers in. |
| 5.7 s | **Crunch** | Iron-door bang, anvil, heavy core, piston and grindstone, plus a camera shake. Survivors take a small hit and are stacked into a **tower** (a passenger chain on the benchmark, biggest at the bottom). |
| 5.75–6.5 s | **Extrude** | The core sample rams up the needle: 12 blocks on the first tick, overshoots to 30.4, settles at 29. Every stratum makes its own place sound as it breaks the surface, like a ratchet. The command block surfaces last with an orange glow and an 8-bit beep-boop. |
| 6.6–7.3 s | **Hold** | Dead silence. A red needle one block wide and as tall as the world, still tightening (1.15 → 1.0) so it stays red. |
| 7.3 s | **Release** | Green. The border lerps 1 → 220 in 0.8 s and sweeps out past everyone, clearing their screens (a lighter whoosh for each). End-portal chord, sonic boom and beacon select. The core drops back into the ground in 3 ticks. The grid springs back to 100 wide, then dissolves. Chat: `Growing the world border to 59999968.0 blocks wide over 1 seconds`. The HUD is back at 29,999,9xx. |
| 7.3–8.4 s | **"What happened?"** | The tower still stands on the benchmark, 29 blocks up, on nothing. At 7.65 s it turns to look at the caster; at 8.05 s it looks down, with one low bass note. |
| 8.45–9.9 s | **Fall** | The benchmark falls under vanilla-equivalent gravity with the tower riding it, and a slide whistle made of flute notes falls from 2.0 to 0.5 with it. |
| 9.9 s | **Land** | Heavy thud, a clink and a 7.5-block square shock ring. |
| 10.0–11.0 s | **Topple** | The tower comes apart from the top, one body every 2 ticks. Each hits the floor for the **main damage**, with a thud and its own square ring. |
| ~11.2 s | **Close** | The benchmark sinks and vanishes with the same lodestone click that opened the show. Everything is gone. |

With no enemies the show still runs in full: the benchmark rides the column, hangs, falls and clinks.

## Tech used

- **Per-player virtual `WorldBorder`** (`Bukkit.createWorldBorder()` + `Player#setWorldBorder`). The client draws it at full world height with additive blending, colours it by motion, and lerps it frame-smooth with `setSize(size, MILLISECONDS, ms)`. The ease-in advance is re-targeted every tick (100 ms lerp toward the schedule 2 ticks ahead), so the wall follows a custom curve and never goes stationary (blue) by accident.
- **Warning distance as a vignette dial.** Red screen edges pulse on the heartbeat for players inside the border. Anyone outside gets full red automatically. Warning time is 0, so the only tint is the one we script.
- **Crossing detection** on the server, from the same signed distance, gives per-player whoosh + `sendHurtAnimation` camera shake.
- **SaintFx-style displays.** Grid lines and core segments are spawned on one fixed anchor and moved only through interpolated transformations. The one exception is the benchmark, which is a teleport-interpolated `BlockDisplay` **vehicle**: enemies ride it in a passenger chain, so the tower moves as one piece in lockstep with the column (no 3-tick mob position lag).
- **Strata** are the real top blocks under the benchmark, then a fixed geology down to bedrock and the command block. Each stratum's emergence sound comes from its own `SoundGroup`.
- **Vanilla chat feel:** `chat.type.admin` + `commands.worldborder.set.*` translatables, so it renders in each player's language exactly like op feedback.
- `stopAllSounds` for the two silences, and `setRotation` on AI-paused riders for the look beat.

## Budget and safety

- Displays: 22 grid + 29 core + 1 benchmark + at most 14 decals × 4, so about 110 at the peak and usually far fewer. Up to 10 captives. Audience is everyone within 72 blocks.
- **One session server-wide** (static lock), plus the per-caster busy flag in `TestPrototypeAbilities`.
- `clearAll()` runs on finish, on caster quit/death/world change, on any exception, and from `TerminusEdge.shutdown()` in `onDisable`. It restores every viewer's real border (a viewer who changes world gets it back immediately), takes the tower apart from the top, sets anyone high up back on the floor, restores AI and silence to their *original* values, and removes every display.
- Bosses (`BOSS_ID`) are never moved. They only take the release hit if they're standing on the needle. Mobs that are riding something, or carrying anything other than a display label, are not captured.
- The task cancels itself when the show ends. Nothing ticks afterwards.
- **No blocks are touched.** No real border is touched.

Known limits: while a player is outside the virtual border (about 2 s), their client won't place or break blocks. The vignette needs Fancy graphics or better. Vanilla riding offsets sink some mobs slightly into whatever they stand on in the tower.

## Files

**New**
- `listener/TerminusEdge.java`: the session (timeline, grid, core sample, benchmark/tower, decals, sound, HUD, teardown).
- `listener/TerminusBorder.java`: lends and returns the virtual edge; lerp, vignette, crossings, restore.
- `docs/TERMINUS.md`: this file.

**Touched (wiring only)**
- `item/CustomItem.java`: `createTerminus()`.
- `model/ItemProfile.java`: `TERMINUS`.
- `listener/TestPrototypeAbilities.java`: `terminusBusy`, `isTerminusBusy`, `castTerminus`.
- `listener/TestGearListener.java`: right-click handler and cooldown. It cancels in both hands so the rod can never be placed.
- `menu/dev/TestGear.java`: `flagships()`. The 21 regular test-gear slots were already full.
- `menu/dev/DevMenu.java`: flagship slot 13 and click lookup.
- `AetherionItems.java`: `TerminusEdge.shutdown()` on disable.

Not touched: Blossom Blade, Gravwell Cleaver, boosters/sockets/anvil, ranks/TAB, shutdown countdown, AetherionCore, BossEngine.

## Consciously not copied

- **Deepsong's sky** (per-player time) and its terrain-as-water sea: the sky is left alone. The only "world" trick here is the border.
- **Gravwell / black holes:** there is no disk, sphere or purple inflow. The compression is square and orthogonal, and is shown by a grid.
- **World Splitter:** there is no seam, void plane or peeling terrain walls. These walls are the game's own edge, not displays.
- **Cataclysm / Meteor:** there is no nova shell, fireball, crater or explosion particle carpet. The two impacts are made of sound, camera shake, screen tint and motion.
- **Vesper / Judgment / Rune Sigil:** no architecture, seal or pillar of light. The column is a physical core sample, not light.
- **Seraphine:** no marionette, threads or theatre. The tower's look-at-you beat is cartoon timing, not puppetry.

## Tuning knobs (top of `TerminusEdge`)

- `START_SIZE` / `ADVANCE`: how far out the edge appears and how long it takes to close.
- `CAPTURE` / `TOTEM_CAP`: herd radius and tower height.
- `CORE`: core sample height. It must match `STRATA.length`.
- `CRUSH_SHARE`: the crunch hit as a fraction of the landing hit.
- Landing damage is `max(320, weaponDamage × 6 + 100)` in `TestPrototypeAbilities.castTerminus`.
- Cooldown is `1200L` in `TestGearListener`.
