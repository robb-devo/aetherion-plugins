# AETHERION — AUDIO / SOUNDTRACK BIBLE

**Date:** 2026-10-03  
**Worktree:** `mining-eldervale-progression-65660c` (branch context: `ship/live-hardening`)  
**Role:** Technical scout / audio planner — **no implementation**  
**Audience:** Claude Opus (audio direction + soundtrack plan) + Robb

---

## 0. How to read this

This bible describes **what the repository actually has today**, plus a **small, coherent soundtrack plan** that fits Aetherion’s identity.

Rules used while scouting:

- Do not invent systems that do not exist.
- Prefer building on existing playback hooks.
- Consistency > AAA production value.
- Vanilla client first (Paper / 1.21.x / resource pack — no OptiFine/Iris required).

**Map caveat (important):**  
Code + live Origin systems still describe the **old ~1000×1000 OriginBuilds hub** (`AetherionHub` `origin.yml`).  
The **new Main Hub map** is **Ankerhold** (`Claude outputs/HUB_ANKERHOLD.md`, `ankerhold_hub.schem`) — compact ~380×361, harbour-city floating island. Ankerhold is designed to preserve the same *functions* (Egon, pads, districts) with new *form*. Audio zones below list **canonical district identities**; Ankerhold is the target geography for new hub music.

---

## 1. What Aetherion is (audio-relevant identity)

Aetherion is a Paper/Velocity Minecraft MMO whose fantasy is:

**Minecraft-as-foundation** (chop, mine, farm, fish, walk, talk)  
+ **authored places** (harbour tutorial, skill isles, Borderlands, dungeons, boss arenas)  
+ **connected progression** (skills, craft ladders, islands/guilds, pets/boosters)

**Tone:** dry harbour humor, earned power, practical fantasy. Not casino neon, not pure anime lobby, not sterile corporate spawn.

**Primary loops that need sound identity:**

1. Harbour orientation → kit → Miss Ledger → skill roads  
2. Skill isles (Mining / Foraging / Farming / Fishing)  
3. Combat spine (Vex → Borderlands rites → bosses)  
4. Dungeons (instance power)  
5. Boss spectacles (loot spikes)  
6. Personal / guild islands + quarry machines (mid/late social economy)

**Central sentence for audio:**  
Players should feel they live in a **worked fantasy harbour-city world**. Music and ambience reinforce *place* and *activity*, not a generic epic trailer.

---

## 2. Current systems snapshot (non-audio, but presentation-critical)

| Domain | Owning plugins / notes |
|---|---|
| Hub / Origin discovery | `AetherionHub` — districts, landmarks, bells, glowcaps, updrafts, glides, ambience |
| Tutorial / TalkUx | `AetherionQuests` — Harbour Hour, LivingNpc cast, **TalkUx speech bubbles LOCKED** |
| Skills gather | `AetherionMining`, `AetherionForaging`, `AetherionFarming`, `AetherionFishing` (+ Items craft ladders) |
| Combat / gear | `AetherionItems`, Borderlands rites, Colosseum |
| Bosses | `BossEngine` — authored fight directors + note-block “scores” |
| Dungeons | `AetherionDungeons` — instances, gates, loot FX |
| Pets | `Aethermobs` |
| Islands / guilds / quarry | `AetherionGuilds` |
| Resourcepack | `AetherionItems/resourcepack` (models/items/fonts) + Helios pack under BossEngine |

---

## 3. Current audio infrastructure (inventory)

### 3.1 Verdict

**There is almost no soundtrack system.**  
What exists is:

1. **Vanilla Bukkit `Sound` SFX** sprayed across plugins (UI, combat, skills, hubs).  
2. **District soundscape palettes** in Hub Origin (`OriginAmbience`) — ambient SFX only, not music loops.  
3. **Procedural note-block “music”** for Seraphine / Helios-style encounters (`MusicBox`, `Requiem`).  
4. **One custom sound namespace** for Helios (`helios:*` in a resourcepack `sounds.json`).  
5. **No project-wide `.ogg` library** checked into this worktree for hub/world music.  
6. **No general music manager** (no fade, no crossfade, no priority stack, no biome music override service).

**Implication for Opus:** build a **small soundtrack + playback contract**, then wire it through **existing event hooks**. Do not replace Origin ambience, Seraphine MusicBox, or Helios custom SFX.

---

### 3.2 Hub — `OriginAmbience` (KEEP / EXTEND)

**Where:** `AetherionHub/.../origin/OriginAmbience.java`  
**Config:** district `soundscape:` keys in `origin.yml`  
**Trigger:** Origin isle tick (~every 60 ticks for samples; emitters faster)

**What it does:**

- Per-district **day/night palettes** of vanilla sounds (harbour gulls/water/rigging, capital chatter, mountain wind/chimes, farm animals, wastes bones, etc.).
- Soft particles (fireflies, petals, motes) tied to palettes.
- Point emitters (forge clank, lighthouse beam, coins, braziers…).
- Skips players who disabled ambience or are mid-`OriginFlight`.

**Soundscape ids today:** `harbour`, `capital`, `mountain`, `ridge`, `wilds`, `meadow`, `arena`, `crags`, `farm`, `forest`, `wastes`.

**Reusable?** Yes — strongest existing “world feels alive” layer.  
**Preserve?** Absolutely.  
**Incomplete?** No music beds; Ankerhold re-point of `origin.yml` still pending when map ships.  
**Opus build-on?** Yes — new music should **layer under / beside** this, not replace it. Prefer ducking/pausing music only during scripted flights / opening cinematic if needed.

---

### 3.3 Hub — scripted travel SFX

| Mechanism | File | Sound role |
|---|---|---|
| Island slime pads | `IslandLaunchPads` | Slime jump + firework launch; movement lock during arc |
| Origin flights / glides | `OriginFlight` | Scripted path velocity; ambience muted while flying |
| Updrafts / bells / discovery | Origin discovery systems | Bell rings, discover cards (mostly titles + coins) |

**Preserve.** Opening cinematic should reuse the **flight-lock / cleanup patterns**, not invent a permanent freeze.

---

### 3.4 Quests — Harbour arrival + NPC presence (KEEP)

**`HarbourArrival`** (`AetherionQuests/.../ui/HarbourArrival.java`):

- First-join beat after language pick (~5.5s authored titles).
- Place card “ANKER HARBOUR” + foghorn (didgeridoo) + water SFX.
- FIND EGON card (Hub starter-hint owns shown-once flag).
- Bells + green flare from Egon’s location.
- Soft chat call from Egon.

**`LivingNpcAtmosphere`:**

- Spine ambient loops for Egon / Forager / Foreman / Ledger (quiet particles + rare nearby sounds).
- Lark pet presence; Liquidator rune motes.

**TalkUx:** speech bubbles — **LOCKED shell**. Audio may accompany, not replace.

**Preserve.** Opening cinematic must **integrate with** HarbourArrival (sequence / shown-once), not fight it.

---

### 3.5 Skills — SFX only (no music beds)

Examples (pattern everywhere):

- Fishing: bobber splash, pickup, rare toast-complete, Eldermaw elder-guardian cues (`AetherionFishing`).
- Foraging: ritual enchant/wood, toast complete (`GroveRitualService`).
- Farming: portal fill, isle cast villager cues.
- Mining: critters / ore events (vanilla combat/block sounds).

**Reusable:** event hooks yes; music no.  
**Opus:** add **short beds / stingers**, keep existing SFX.

---

### 3.6 BossEngine — authored SFX + procedural scores (KEEP)

**Major approach today:**

- Vanilla `world.playSound` / encounter `score()` for ability telegraphs.
- **Seraphine `MusicBox`**: note-block theme array drives fight rhythm + post-fight loot music box (`SeraphineMusicBox`).
- **Helios Requiem**: note-block pad / heartbeat-style scoring in code.
- **Custom Helios sounds** declared in:

```text
BossEngine/resourcepack-helios/assets/helios/sounds.json
```

Events: `heartbeat`, `star_hum`, `seismic_whine`, `seismic_boom`, `lance_charge`, `portal_open`, `supernova`, `requiem_pad`.

**Preserve.** Do not flatten boss identity into one generic boss track. Prefer **per-important-boss motifs** (or shared Ashen/Helios family motifs) built the same way: resourcepack `.ogg` **or** note-block themes where that already works.

---

### 3.7 Dungeons — SFX only

Gate / clear / loot / vault shutter sounds in `AetherionDungeons` (`DungeonGateFx`, `DungeonLootFx`, `InstanceManager`).  
No exploration music loop found.

---

### 3.8 Items / UI / weapons

- UI clicks, toast completes, levelup stingers across menus.
- Signature weapon FX (Blossom Blade / Gravwell Cleaver) — **LOCKED combat feel**; do not retune.
- Seraphine gear listener mirrors MusicBox theme for set abilities.

**Main Items resourcepack:** models/textures/fonts — **no `sounds.json` music library** in this worktree.

---

### 3.9 Playback APIs available

| API | Use |
|---|---|
| `Player.playSound(Location, Sound, category, vol, pitch)` | Per-player vanilla |
| `World.playSound(...)` | Spatial broadcast |
| `SoundCategory` (AMBIENT / PLAYERS / MASTER / RECORDS / …) | Mixing |
| ResourcePack custom events (`namespace:id`) | True music beds / unique stings |
| Note-block pitch sequences | Lightweight motifs without assets |

**Missing (do not overengineer unless needed):** crossfade mixer, server-side music bus, client-mod shaders for audio viz.

---

## 4. Audio content map — what needs music / identity

Priority scale: **P0 must exist**, **P1 strong**, **P2 nice**, **P3 later**.

### 4.1 World / Hub (Ankerhold districts)

| Place | Need | Priority | Notes |
|---|---|---|---|
| Anker Harbour / ship spawn | Calm harbour bed + water/gull ambience | **P0** | Opening identity |
| Capital / Ledger’s Court | Soft civic bed (fountain, people) | **P0** | Social home |
| Ore Ridge / mine mouth | Industrial / stone bed | P1 | Mining road |
| Whisperwood / lumber | Wood / leaf bed | P1 | Forage road |
| Clucksworth Fields | Pastoral bed | P1 | Farm road |
| Mount Skyreach | Wind / chime / height | P1 | Aspiration + dungeon threshold |
| Borderlands / Fallen Anchor | Sparse, uneasy bed | **P0** | Danger edge contrast |
| Colosseum fragment | Crowd / iron tension | P2 | Can share arena motif |
| Opening cinematic | Dedicated short cue | **P0** | See Document B |

### 4.2 Skill isles

| Isle | Need | Priority |
|---|---|---|
| Mining Eldervale | Cave/industrial loop | P1 |
| Forage Isle | Canopy / wood loop | P1 |
| Farm Isle | Pastoral (can share Fields) | P2 |
| Fishing Eldervale / lake | Waterbed + rare deep sting | P1 |

### 4.3 Progression moments

| Moment | Need | Priority |
|---|---|---|
| First join / harbour arrival | Stinger or short bed under existing titles | **P0** |
| Quest discovery / important talk | Tiny chime (don’t spam) | P2 |
| Area / district discovery | Soft discover sting (can share motif) | P1 |
| Ledger graduation / roads | Short hopeful sting | **P0** |
| Rare finds / toast completes | Keep vanilla toast; optional motif layer | P2 |
| Pad launch / travel | Whoosh sting (already have SFX) | P2 |

### 4.4 Bosses — prioritize important ones

**Spectacle / identity bosses (music worth it):**

| Boss | Intro sting | Combat bed | Phase cue | Ability cue | Death cue | Arena ambience |
|---|---|---|---|---|---|---|
| **Seraphine, Hanging Saint** | Optional | **Already has MusicBox theme** | Tempo change (exists) | Rhythm-tied (exists) | Wind-down (exists) | Soft chapel |
| **Helios / Herald / Requiem family** | Yes | Yes (extend Helios pack) | Yes | Keep custom SFX | Yes | Solar hum |
| **Nihil, World Eater** (+ Unbroken) | Yes | Dark sparse bed | Strong | Keep SFX | Strong | Void dread |
| **Hollow Sun** | Yes | Hollow solar bed | Yes | Keep SFX | Yes | Arena hum |
| **Ashen Sovereign / Sheath / Chainwarden** | Family motif | Ashen combat bed | Optional | Keep weapon LOCKED SFX | Yes | Ash wind |
| **EGGQUELIZER** | Comic sting | Optional light bed | Optional | Keep SFX | Comic hit | — |

**Lower priority / SFX-only OK for now:** Sparky, Baron von Wurm, Sir Balthazar, Insolvent Wither, Lobby Cleaner, Pathwarden, dungeon prototypes, test_* bosses, McNugget, Squidward, Bridge Troll, etc. — keep ability SFX; add music only if a fight becomes a promo pillar.

### 4.5 Dungeons

| Category | Need | Priority |
|---|---|---|
| Entry / gate | Short portal sting | P1 |
| Exploration | One dark loop reusable across floors | P1 |
| Combat wave | Tighter loop or intensity variant | P2 |
| Boss room | Use boss music if present | P1 |
| Clear / loot | Keep toast + vault SFX; optional win sting | P1 |
| Fail / wipe | Soft fail sting | P3 |

### 4.6 Opening cinematic

Dedicated **10–15s non-looping** cue. See Document B. This is the **identity motif seed**.

---

## 5. Practical soundtrack plan (small coherent set)

### 5.1 Motif strategy (reuse, don’t bloat)

| Motif | Uses |
|---|---|
| **Aetherion Harbour Motif** (4–8 bars) | Opening cinematic, harbour bed tag, graduation sting, rare world moments |
| **Capital Motif** (variant of harbour, warmer) | Capital bed, Ledger desk energy |
| **Edge Motif** (minor / sparse) | Borderlands, dungeon explore tag |
| **Saint Motif** | Already exists as Seraphine MusicBox — keep exclusive |
| **Helios Motif** | Helios family + pack pads — keep exclusive |

### 5.2 Track table

| TRACK / SOUND | PURPOSE | LOCATION / EVENT | STYLE / MOOD | LENGTH | LOOP? | IMPORTANCE | REUSE |
|---|---|---|---|---|---|---|---|
| `aetherion.open_orbit` | Opening flyover | First-time harbour cinematic | Calm, hopeful, harbour air | 12–16s | No | **P0** | Seeds Harbour Motif |
| `aetherion.harbour_bed` | Hub home | Anker Harbour | Soft strings/woodwinds + water space | 60–90s | Yes | **P0** | Shares motif |
| `aetherion.capital_bed` | Civic hub | Capital terrace | Warmer, plaza, light bells | 60–90s | Yes | **P0** | Motif variant |
| `aetherion.borderlands_bed` | Danger edge | Borderlands / Scar / Anchor | Sparse, dry, uneasy | 60–90s | Yes | **P0** | Edge Motif |
| `aetherion.skyreach_bed` | Height / wonder | Mount Skyreach | Wind, chimes, thin choir | 60–90s | Yes | P1 | Soft motif lift |
| `aetherion.ridge_bed` | Mining road | Ore Ridge / Mining isle | Stone, low pulse, forge distant | 60–90s | Yes | P1 | Share Mining |
| `aetherion.wood_bed` | Wood road | Whisperwood / Forage | Leaves, soft guitar/pluck | 60–90s | Yes | P1 | Share Forage |
| `aetherion.fields_bed` | Farm road | Fields / Farm Isle | Pastoral, chickens-ok vibe without silliness | 60–90s | Yes | P2 | Share Farm |
| `aetherion.water_bed` | Fishing | Harbour pier / Fishing isle | Water, soft pulse | 60–90s | Yes | P1 | Harbour cousin |
| `aetherion.discover_sting` | Discovery | District / landmark / vista | 1–2s chime | 1–2s | No | P1 | Motif fragment |
| `aetherion.graduate_sting` | Roads unlock | Miss Ledger stamp | Hopeful 3–4s | 3–4s | No | **P0** | Motif cadence |
| `aetherion.travel_whoosh` | Pad / glide | Launch pads | Soft whoosh | 1–2s | No | P2 | Optional over existing SFX |
| `aetherion.dungeon_explore` | Instance roam | Dungeons | Dark stone loop | 60–90s | Yes | P1 | Edge cousin |
| `aetherion.dungeon_clear` | Floor clear | Dungeon win | Bright short | 3–5s | No | P1 | Motif |
| `helios.*` (existing) | Helios fight | Helios family | Solar / seismic | varies | Mix | **Keep** | Exclusive |
| Seraphine MusicBox (existing) | Seraphine fight + loot box | Hanging Saint | Lullaby note-block | bars | Yes (fight) | **Keep** | Exclusive |
| `boss.nihil_bed` | World Eater | Nihil fight | Void sparse | 90–120s | Yes | P1 | Unique |
| `boss.hollow_sun_bed` | Hollow Sun | Hollow Sun | Hollow solar | 90–120s | Yes | P1 | Unique / Helios cousin |
| `boss.ashen_bed` | Ashen fights | Ashen family | Ash, blade air | 90–120s | Yes | P1 | Family |

**Hard cap recommendation for v1 ship:** **~12–16 assets** (beds + stings + opening). Not 40 tracks.

---

## 6. Non-musical sound layers (world alive)

Keep / extend vanilla-SFX layers rather than replacing with music:

| Layer | Source today | Guidance |
|---|---|---|
| Harbour water / gulls / rigging | `OriginAmbience` harbour palette | Keep; densify carefully on Ankerhold pier |
| Capital chatter / pages | capital palette | Keep |
| Farm animals / till | farm palette | Keep |
| Borderlands bones / wind | wastes palette | Keep; don’t drown with music |
| Forge emitters | Origin emitters | Keep |
| Lighthouse beam | Origin night emitter | Keep |
| NPC spine presence | `LivingNpcAtmosphere` | Keep quiet |
| Machinery (guild quarry) | Guilds FX | Soft industrial when near machines |
| UI feedback | menus everywhere | Standardize volumes later; don’t redesign |
| Teleport / pad | LaunchPads + network handoff | Keep existing; optional whoosh layer |
| Boss ambience | directors | Prefer existing score systems |

---

## 7. Audio style guide (Aetherion language)

### 7.1 Character

- **Minecraft-native first:** wood, water, wind, bells, iron, distant work.
- **Aetherion second:** harbour practical fantasy, floating-island air, earned wonder.
- **Not:** Hans Zimmer trailer brass walls, generic medieval RPG choir spam, EDM lobby, anime battle intros for early game.

### 7.2 Instrumentation direction

| Context | Lean toward |
|---|---|
| Early hub / opening | Soft strings or woodwinds, light harp/pluck, distant horn/foghorn texture, water noise bed |
| Capital | Warmer; soft bells / fountain space |
| Gather isles | Acoustic / environmental; low drums only if mining |
| Borderlands / dungeons | Sparse drones, dry percussion, silence as a tool |
| Boss spectacles | More aggressive — but still readable; leave space for ability SFX |

### 7.3 Mixing rules

- Music beds: **quiet**. Player must still hear footsteps, TalkUx, ability telegraphs.
- Prefer `SoundCategory.RECORDS` or `MUSIC` for beds; keep ambience on `AMBIENT`.
- One bed at a time per player. Crossfade ≤1–2s if you implement fades; hard cut is OK for v1 if clean.
- Boss music may duck hub beds to zero on encounter start.

### 7.4 How much vanilla Minecraft identity stays

**A lot.** Vanilla SFX remain the body. Custom music is the **clothing**.  
If music is missing, the game must still feel fine (it already does via Origin ambience + SFX).

---

## 8. ResourcePack integration notes

**Recommended path for real music:**

1. Add `assets/aetherion/sounds.json` (or extend an existing Aetherion pack ship pipeline Robb already uses for Items).
2. Put looping `.ogg` under `assets/aetherion/sounds/music/...`.
3. Play via `player.playSound(..., "aetherion:harbour_bed", SoundCategory.RECORDS, vol, 1f)`.
4. Helios stays in `helios:` namespace — do not merge namespaces casually.

**Note-block motifs** remain valid for bosses that already use them (Seraphine), and for ultra-light stings if asset pipeline is slow.

**Items pack today** is models-heavy — music can live in a dedicated music section of the same pack or a slim music pack, as long as clients get it.

---

## 9. What should NOT be overengineered

- No giant “AudioEngine” plugin with playlist graphs.
- No client-mod audio (Iris/Sodium/OptiFine) dependency.
- No unique full OST per every boss / every district on day one.
- No replacing OriginAmbience palettes with silence + music.
- No rewriting TalkUx to be “audio dialog.”
- No retuning LOCKED weapon / booster / shutdown systems for “better mix.”
- No dynamic adaptive AI composer. Static loops + stings are enough.

---

## 10. Suggested build order for Opus / audio work

1. **Harbour Motif + `open_orbit`** (feeds cinematic).  
2. **Harbour / Capital / Borderlands beds.**  
3. **Graduate + discover stings.**  
4. **Skill isle beds** (reuse where possible).  
5. **Dungeon explore + clear.**  
6. **Nihil / Hollow Sun / Ashen beds** only after hub identity exists.  
7. Leave Seraphine + Helios alone unless extending their existing vocabularies.

---

## 11. Source anchors (read these, don’t rediscover)

- `docs/AETHERION_MAIN_HUB_WORLD_BIBLE.md`
- `docs/MAIN_ISLAND_ORIGIN.md` / `AetherionHub/.../origin.yml` (old map systems still in code)
- `Claude outputs/HUB_ANKERHOLD.md` + `ankerhold_anchors.yml` (**new map truth**)
- `docs/RED_THREAD.md` (harbour spine)
- `AetherionHub/.../OriginAmbience.java`
- `AetherionQuests/.../HarbourArrival.java`
- `BossEngine/.../saint/MusicBox.java`
- `BossEngine/resourcepack-helios/assets/helios/sounds.json`

---

*End of Document A*
