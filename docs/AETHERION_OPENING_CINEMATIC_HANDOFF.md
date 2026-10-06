# AETHERION — OPUS OPENING CINEMATIC HANDOFF

**Date:** 2026-10-03  
**For:** Claude Opus  
**From:** Technical scout (Composer) — repository inspection only, **no implementation in this pass**  
**Companion:** `docs/AETHERION_AUDIO_SOUNDTRACK_BIBLE.md`

---

## Purpose

Build **ONE** polished Aetherion opening sequence:

> a short, calm “Welcome to Aetherion” flyover of the Main Hub  
> ~**10–15 seconds**  
> ending at **Egon** on the pier  

Not a lore trailer. Not a cinematic framework. Not a redesign of the map.

---

## 1. Aetherion identity (experience target)

Aetherion is Minecraft-as-foundation with authored places:

- Harbour practical fantasy (ships, docks, work)
- Floating-island home dock
- Skills grow **out there**; hub is the crossroads
- Dry humour, earned power — not casino lobby

The opening should leave the player with a **mental map**, not a cutscene hangover:

> Harbour here → Capital there → Farm that way → Mining that way → Borderlands / Anchor that way → that mountain matters.

Tone: **calm confidence**. “Someone cared about the first 15 seconds.”

---

## 2. Map source of truth — ANKERHOLD (new Main Hub)

**Do not design the cinematic for the old 1000×1000 OriginBuilds map.**

**Current authored hub map:** Ankerhold  
Canonical docs / assets:

| Asset | Path |
|---|---|
| Design writeup | `C:\Users\Robbi\IdeaProjects\Claude outputs\HUB_ANKERHOLD.md` |
| Anchors (machine-readable) | `...\Claude outputs\ankerhold_anchors.yml` (also `_hub_ankerhold_build\`) |
| Schematic | `ankerhold_hub.schem` (Opus elevate may produce `ankerhold_hub_v2.schem`) |
| Previews | `HUB_ANKERHOLD_map.png`, `HUB_ANKERHOLD_views.jpg` |

### Footprint

- **≈380 × 361** blocks (`x −188..191`, `z −181..179`)
- Vertical bands: harbour **y≈64–70**, Capital **y≈78**, Skyreach **y≈125–159**
- Design origin paste: centre ≈ `0 63 0` with `//paste -o`
- Fantasy: island once chained to the world; **Fallen Anchor** on the west rim = Borderlands landmark

### Critical anchors for the cinematic (design feet coords)

| Role | Coords (x y z) | Notes |
|---|---|---|
| **World spawn / ship gangway** | `166.5 65 8.5`, yaw **90** | Arrival pier beside sky-sloop |
| **Egon** | `141.5 65 3.5`, yaw **-90** | Pier head; green glow target; ~25 blocks from spawn toward pier head |
| **Fishmonger** (beside Egon) | `141.5 65 13.5` | Bickers with Egon — useful for “alive pier” |
| **Capital / Ledger’s Court** | centre ~`0 78 0` band; Ledger `-25.5 79 -4.5` | Walled terrace, Tallybell, fountain |
| **Tallybell** (landmark) | `16.5 118 -24.5` | Climbable copper spire — skyline read |
| **Farm / Clucksworth** | camp `-85.5 75 72.5`; Harrow portal `-135.5 75 140.5` | SW fields + windmill hill |
| **Ore Ridge / Mining pad** | ridge camp `90.5 72 80.5`; pad ~`40..44, 72, 174..176` | South — Mining departure |
| **Whisperwood / Forage pad** | camp `90.5 74 -61.5`; Twig pad ~`139..143, 73, -95..-91` | NE wood / Forage departure |
| **Borderlands / Fallen Anchor** | Vex `-83.5 72 -15.5`; west wastes | Danger edge + giant tilted anchor |
| **Colosseum fragment** | ~`-136.5 74 -142.5` | Separate floating fragment SW/W |
| **Mount Skyreach / Mountain Gate** | gate/threshold ~`-9.5 89 -94.5`; summit `-49.5 159 -115.5` | North aspiration + dungeon threshold |
| **Seawatch Light** | vista `168.5 106 -36.5` | Harbour lighthouse — great silhouette |
| **The Spill** | pier head over lagoon waterfall | Motion + water read under pier |

### Sightlines that matter

From a modest altitude over the **east harbour**, a slow orbit should naturally reveal:

1. **Pier + sky-sloop + Spill** (start)
2. **Capital wall + Tallybell** (centre)
3. **Fields / windmill** (SW)
4. **Ore Ridge headframe / Mining pad direction** (S)
5. **Fallen Anchor / Borderlands** (W) — the “oh” landmark
6. **Skyreach stair / mountain** (N)
7. Return along the pier to **Egon**

If a mathematically perfect 360° hurts readability, **prefer a three-quarter orbit + deliberate return**. Readability > geometry.

### Integration status (important)

Ankerhold is the **map design truth**. Plugin re-point (`origin.yml`, `npcs.yml`, spawn camps) may still lag behind paste.  
Cinematic choreography should be authored in **Ankerhold design coordinates**. If the live world is still the old map when you implement, **gate playback** until Ankerhold is the active hub (or key off a config flag / world footprint). Do not hard-code old Origin coords.

---

## 3. Desired experience (10–15s)

1. Player is at **ship / harbour spawn** (`166.5 65 8.5`).
2. Temporary cinematic control (no free walk).
3. Camera **lifts smoothly** away from the player.
4. Camera performs a **calm orbit / flyover** that teaches the island silhouette and districts.
5. Landmarks stay **readable** (not blurry speed porn).
6. Camera **returns** toward the pier / ship.
7. Settles near **Egon** (pier head).
8. **Egon does something visible** (not idle statue):
   - working kit / clipboard / looking toward player
   - turning to face arrival
   - short harbour busywork (crate / rope / gear) — FancyNPC/LivingNpc-capable motion preferred over fake props if possible
9. Camera settles; **control returns**.
10. Player is ready for existing onboarding (talk to Egon).

Emotional result: **“Here is your world.”**  
Not: **“Here is a 15-second movie.”**

---

## 4. Suggested timing beats (adapt to map)

These are **beats**, not a lock:

| Time | Beat |
|---|---|
| 0.0–2.0s | Soft lift from gangway; harbour water + ship in frame |
| 2.0–8.0s | Main overview orbit — Capital, Fields, Ridge, Anchor, Mountain as readable beats |
| 8.0–11.0s | Finish reveal; begin return toward pier |
| 11.0–14.0s | Descend to Egon; frame pier head + green cue |
| 14.0–15.0s | Settle; handoff to player |

Total may sit **12–15s**. Prefer slightly shorter if motion feels floaty.

### Camera craft notes

- Smooth accel / decel (ease-in/out). No teleport pops.
- Altitude high enough to read the **floating silhouette** and **Fallen Anchor**, low enough that districts stay legible.
- Avoid nausea: no hard rolls, no rapid pitch flips, no stuttering tick snaps.
- Player body: hidden or locked in place at spawn until end (your choice), but **must restore** cleanly.
- Final shot: Egon roughly mid-frame or rule-of-thirds; player ends standing where talking to him is natural (near pier head / green glow path).

---

## 5. Sound / music requirements

Pair with Document A.

**Minimum for this sequence:**

- One dedicated cue: `aetherion.open_orbit` (~12–16s, **non-looping**).
- Calm harbour identity (soft motif that can later tag harbour bed / graduation).
- Optional: very light water / wind underlay using **existing vanilla SFX** (HarbourArrival already uses foghorn + water).
- Do **not** blast boss music or toast-complete spam.

**Mixing:** quiet enough that the island “reads” visually first.

If custom `.ogg` is not ready, a temporary vanilla note-block motif is acceptable for prototype — but the **authored target** is a short music cue in the resourcepack.

---

## 6. Visual presentation (vanilla client constraints)

### What is realistic WITHOUT client mods

Paper + vanilla client + resourcepack can support:

- Smooth teleport / velocity-scripted camera paths (see Hub `OriginFlight`, `IslandLaunchPads` flight lock)
- Spectator mode / spectate entity patterns (if you choose — must restore)
- Titles / action bars (use sparingly; **do not** dump tutorial text over the flyover)
- Particles (soft mist, water spray near Spill, distant ember in Borderlands — subtle)
- Time-of-day / weather set for the beat (restore after)
- Display entities / TextDisplays (avoid permanent leftovers)
- Resourcepack models already in Items pack (don’t invent a shader pack)

### What is NOT available without mods

- Real screen shaders (Iris / OptiFine / Sodium shader packs)
- Custom post-process fog beyond vanilla fog + particles + time/weather tricks
- True cinematic FOV animation is limited; do not depend on client FOV mods

**Prefer:** composition, altitude, timing, light particles, music.  
**Avoid:** requiring OptiFine for the opening to “count.”

---

## 7. Technical infrastructure to reuse (proportional engineering)

Inspect / reuse — do **not** invent a giant cinematic engine unless truly necessary.

### Strong reuse candidates

| Piece | Where | Why |
|---|---|---|
| Scripted path + per-tick assert | `AetherionHub` `OriginFlight` | Smooth path following, stuck detection, quit cleanup |
| Movement lock / restore | `IslandLaunchPads` `beginFlightLock` / `endFlightLock` | Walk speed 0 + attribute multiply; quit-safe patterns |
| First-join sequencing | `HarbourArrival`, `PlayerJoinListener`, `LangMenu` | Language pick → arrival beat; shown-once habits |
| Egon locate / flare | `HarbourArrival.flareEgon`, `NpcPresence`, `EgonHintParticles` | Green identity already exists |
| LivingNpc presence | `LivingNpcAtmosphere` + LivingNpc service | Egon “busywork” / facing |
| Per-player sound | Bukkit `playSound` + future `aetherion:` events | Music sting |

### Keep engineering proportional

This is **one** 15s sequence. Acceptable shapes:

- A small `OpeningOrbit` (or similar) class with: start → tick path → end → cleanup
- Configured waypoints in YAML (Ankerhold coords)
- First-join flag persisted (PDC / existing quest/hub profile — pick what already stores “seen starter hint”)

Unacceptable shapes:

- Generic multi-scene director for all future cutscenes “while we’re here”
- Rewriting Origin / TalkUx / Harbour Hour

---

## 8. Onboarding integration (do not break Harbour Hour)

**Existing first minutes (KEEP):**

1. Language pick (quests)  
2. `HarbourArrival.play` — ANKER HARBOUR card + foghorn + FIND EGON + bells/flare + Egon chat call  
3. Green glow trail to Egon  
4. TalkUx conversation → kit spine (`docs/RED_THREAD.md`)

**Recommended integration (Opus decides exact order):**

- Play **orbit first** (or orbit as the “place card” replacement), then hand into a **shortened** FIND EGON beat — **or**
- Play orbit **between** language pick and FIND EGON, then reuse flare/bells

Hard requirements:

- Must **not** duplicate on every join
- Must **not** permanently skip / break Egon’s quest start
- Must cooperate with Hub `sendStarterHint` shown-once flag
- Quiet Origin discovery (`quiet-until-spawn: capital`) must remain respected after handoff
- Returning players who already finished arrival must **not** be forced through the orbit again (admin replay command optional)

---

## 9. Safety / cleanup (MUST)

The cinematic MUST:

- Never permanently freeze the player
- Restore walk speed, flight flags, gamemode, potion effects you added
- Handle disconnect mid-orbit (land safely at spawn or Egon; clear tasks)
- Handle world change / server transfer mid-orbit
- Survive reload/restart without orphan tasks / spectator leftovers
- Spawn **no permanent** entities (temp camera stands / displays must die)
- Not leave players invisible / locked / under map
- Not break ship/spawn location
- Not break Egon interactions / TalkUx
- Not break resourcepack identity
- Not interfere with tutorial quest ids / gates

If anything fails: **fail open to gameplay** at harbour spawn with control restored.

---

## 10. Quality bar

The player finishes with:

1. Smoothness  
2. Readable mental map of Ankerhold  
3. Calm confidence  
4. Good composition (ship → city → danger anchor → mountain → Egon)  
5. Seamless handoff to “talk to Egon”  
6. Sound that feels intentional  
7. **No technical jank**

If it feels like “AI dumped a camera path,” it is not done.

---

## 11. Creative freedom

**OPUS FREEDOM:**  
Within the constraints above, you have full creative freedom over camera choreography, timing, sound integration, subtle visual effects, Egon’s behavior, and presentation. Prefer the simplest implementation that achieves a genuinely polished result.

You may choose:

- Spectator vs locked-survival camera
- Exact orbit shape (full 360 vs three-quarter)
- Whether player body is hidden
- How Egon’s “doing something” is staged
- Whether titles appear at all during the orbit (default: **minimal / none** during flight)

---

## 12. DO NOT TOUCH

Unless a **minimal** integration change is genuinely required (document it explicitly):

- Unrelated gameplay systems
- Progression formulas / economy
- Boss systems / dungeon logic
- LOCKED systems (TalkUx shell, boosters, anvil sockets, ranks, ShutdownCountdown, signature weapons Blossom Blade / Gravwell Cleaver)
- Existing Harbour Hour quest order / quest ids
- Map layout redesign (Ankerhold art is Opus map work — cinematic consumes it, does not rebuild it)
- Item / resourcepack visual identity beyond adding music assets if needed
- “Also restart all servers” / live paste of the map unless Robb asks

---

## 13. Suggested deliverables when Opus implements (later task)

1. Tiny cinematic module (Hub or Quests — pick the owner that already owns first-join UX; Quests currently owns `HarbourArrival`)  
2. Waypoint YAML for Ankerhold orbit  
3. First-join flag + cleanup  
4. Optional resourcepack sound `aetherion:open_orbit`  
5. Short playtest notes: disconnect mid-flight, reload, second join, talk-to-Egon after

**This handoff document itself is scout-only. Do not implement during the scout task.**

---

## 14. Quick reference — player mental map after 15s

| Seen | Meaning |
|---|---|
| East pier + sloop + Spill | Home arrival |
| Capital terrace + Tallybell | City / rules / Ledger |
| SW fields + windmill | Farming road |
| South ridge / headframe | Mining road |
| West Fallen Anchor / Scar | Danger / Borderlands |
| North mountain / gate | Aspiration / dungeons later |
| Egon at pier head | Your first person |

---

*End of Document B*
