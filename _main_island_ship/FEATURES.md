# Main Island / Origin — what changed (one page)

**The map is the live island, 1:1.** The OriginBuilds world file proves it three ways:
- Its world spawn is Ledger's plaza.
- Its only slime pad is exactly `origin_to_mining`.
- All 21 on-island Quests NPCs stand on its ground.

Every Origin coordinate was measured from the region files and validated offline (standable, reachable, in the right district): **BAD 0**.

**13 districts, y-aware:**

| District | District | District |
|---|---|---|
| Mount Skyreach (above the city) | Ore Ridge | Anker Harbour |
| Seawatch Point | The Capital | Whisperwood Wilds |
| Bloomfield | The Colosseum | Glowcap Crags |
| Clucksworth | Southwood | The Borderlands |
| Eastwood | | |

- Each district has a card and a first-visit reward.
- **39 landmarks** each have a blurb and pay once.
- Titles: **Wayfarer** (every district) and **Origin Cartographer** (every landmark).
- Tutorial players stay in **quiet mode** until the Capital camp unlocks, so the Harbour Hour keeps the stage.

**Getting around a 180-block-tall island:**
- **Glowcap waystones.** The map's 6 giant glowing mushrooms, plus 2 sprouts planted from displays at the harbour and Eastwood. Walk up to attune; right-click to travel between the ones you know. All eight earn the **Glowcap Circle**.
- **Updrafts.** Skyreach (y 64 → 158, behind the Mountain Gate) and the Crags (81 → 145).
- **Scripted skyways and glides:**
  - the two **unused arch pads** of the map, brought back: SE and SW, 622 blocks to the plaza in 12 s
  - the **Summit Glide** from the cliff ring
  - the **Crag Glide** into Clucksworth
- How they fly: Chaikin-smoothed, terrain-checked, latency-proof. If something blocks the path, the wind lets go with slow falling.
- **Pad flair.** Light columns, a walk-up preview (destination, sealed or open, new), a card on take-off and "Back on Origin" on landing.
- Riding everything earns **Skyrider**.
- **Edge rescue** (the wind puts you back) and fall grace.
- The **wayfinder**: `/origin go <anything>` shows an arrow with distance and a ▲/▼ height hint.

**Discovery toys:**
- **Vistas.** Five high places where floating per-player labels name every district on the horizon, with its distance. All five earn **Stargazer**.
- **The Seven Bells.** The map's real bells, one hymn note each; the high tower bell is rung with an arrow. They toll at dawn and dusk. All seven earn **Bellwright** and the full hymn.
- **The wishing fountain.** Sneak and right-click Fountain Square's water: a coin, a fortune, and rarely the Fountain's Favour.

**Island life:**
- **District soundscapes** by day and night, fireflies, petals and ash.
- **40 emitters** on real features:
  - forges, kitchens and brewers
  - the fountain, the mill and the library
  - Vince's coins, gulls, surf and bones
  - braziers, starbells and glowcap spores
  - mast lights and a **rotating lighthouse beam**
- **Six island moments** every 22 min, picked by time of day:
  - **Lantern Festival**: rising lanterns and harmless fireworks
  - **Aurora**
  - **Starfall**: sneak and look up to wish
  - **Petal Storm**
  - **Harbour Fog**: with the lighthouse horn
  - **Rainbow**: after rain

**The Origin townsfolk** are five Hub-owned villagers, not Quests NPCs. Right-click opens a board (GUI):

| Who | Board |
|---|---|
| Orla Vane | Journal, tour, "Where next?", fountain |
| Cobb Kettleby | Skyway ledger |
| Sister Aurel | Bells and hymn |
| Fen Glowmoor | Glowcaps |
| Stellan Voss | Vistas, wishes, sky |

They are the **8-stop first-visit tour**: cast → fountain → updraft → summit → glide → bells → glowcap. It pays once.

**Origin Journal (`/origin`)** shows districts, landmarks, glowcaps, bells, vistas, skyways, townsfolk, titles, the current moment, and per-player settings for ambience and particles.

**New camps:** **Skyreach Summit** (`/summit`, `/skyreach`) and **Whisperwood Shrine** (`/whisperwood`, `/wilds`). Both are organic walk-in unlocks and seeded once. Locked gotos and menu entries say how far away the camp is and in which direction.

**DEV:** `/origin dev` covers moments, bell tolls, townsfolk anchors and presets, seeding camps, district-aware **softlight with exact undo**, TPs, riding each updraft and glide, and completing or resetting a profile. Items DEV → WORLDS → **Origin Island** (empty slot 39) opens it.

**Union:** the main checkout's Hub (prop wand + 18 schems, `wipePlayer`, `bloodstone`) is merged in. The Items Full Player Wipe now also clears the Origin journal. Surface diff vs both Hub jars: **MISSING 0**.

**Locked and untouched:**
- Quests (LivingNpc, TalkUx/TalkText, CastBook, skins, DialogManager, `/npc`, fonts), including the Quests Origin cast
- Codex/Skills
- Foraging, Mining, Farming, Fishing
- Anvil sockets, ranks, Blossom Blade, Gravwell Cleaver / HorizonFx, ShutdownCountdown, Borderlands vials
- The DevMenu layout: one tile in an empty slot

**FPS:**
- One cadenced router and no runtime block scans.
- Particles only near players, and never for players who turned them off.
- All entities are non-persistent, tagged, purged on start and rebuilt on chunk load.
- Labels are per-player and time out.
