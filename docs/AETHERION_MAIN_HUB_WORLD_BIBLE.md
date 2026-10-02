# AETHERION — MAIN HUB WORLD BIBLE
## FOR CLAUDE OPUS

**Date:** 2026-10-01  
**Worktree:** `mining-eldervale-progression-65660c`  
**Author role:** Researcher / World-Bible writer (not Creative Director)  
**Opus role:** Creative Director / Builder of a **new** Main Hub  
**Mode:** Discovery + briefing only. **No Hub build, no schematics, no deploy, no code changes** in this document's production.

**Target for Opus build (later):** floating-island Main Hub, **≈300×300 to 400×400** (hard max **400×400**). Compact, dense, explorable — not a lobby, not another 1000×1000.

**Related reading (do not re-derive from scratch):**
- `docs/MAIN_ISLAND_ORIGIN.md` — current Origin Hub overlay on the live map
- `docs/AETHERION_PROGRESSION_SCOUT.md` — systems / economy / combat spines
- `docs/RED_THREAD.md` — early-game quest roads
- `docs/FORAGING_ELDERVALE.md`, `docs/MINING_ELDERVALE.md`, `docs/FARMING_ELDERVALE.md`
- `docs/OWNERSHIP.md` — gather vs Items ownership
- `docs/npc/NPC_DIALOGUE_OVERHAUL.md` — Talk UX contract

---

## 1. Executive Summary

Aetherion is a Paper/Velocity Minecraft MMO whose power comes from **authored physical places** (harbour tutorial, skill islands, Borderlands rites, dungeon floors, boss arenas) wired to **real progression systems** (skills, craft ladders, gather masteries, islands/guilds, dungeons, pets/boosters).

The **current Main Hub** is a pasted ~1000×1000 commercial skyblock RPG spawn (`world`), deepened by **AetherionHub Origin** (districts, waystones, bells, updrafts, townsfolk boards) and inhabited by a separate **AetherionQuests LivingNpc cast** (Harbour Hour, TalkUx speech bubbles). It works — but the footprint is now considered **too large for the density it earns**.

**You (Opus) will invent a new floating-island Hub** that:
- preserves **function** (spawn camps, pads, quest NPC roles, travel hooks, discovery),
- reinvents **form** (layout, silhouette, districts, landmarks, architecture),
- stays **300–400 blocks across**,
- feels like a **real place** players walk and rediscover — not a portal wall / NPC wall / hologram lobby.

This bible gives identity, constraints, and integration facts. It does **not** prescribe exact block placement.

---

## 2. What Aetherion Is

Aetherion is **Minecraft-as-foundation**: chopping, mining, farming, fishing, walking, looking, talking to NPCs in the world. Menus exist (Manager, Spawn menu, boards, shops) but the fantasy is that **places teach you** before UIs do.

**Primary loops players actually play:**
1. **Harbour orientation** → kit, first gather, first craft, Skills unlock (Miss Ledger).
2. **Skill roads** → Forage / Farm / Fish isles + Combat (Vex → Borderlands rites → bosses).
3. **Craft ladders T1–T5** + gather masteries + compression economy.
4. **Account level** gates personal island (20) and guilds (75).
5. **Dungeons** (instance power, vestiges/cores) and **Boss spectacles** (loot spikes).
6. **Pets / Boosters** as soft pads on the same `ItemCapability` bus (booster design **LOCKED**).

**What makes it not a generic Skyblock lobby:**
- Authored NPC dialogue with **in-world speech bubbles** (TalkUx — **LOCKED shell**).
- Skill islands with their own weather, districts, boards, and physical travel (slime pads / portals).
- Borderlands vial rites; Colosseum / boss camps; dungeon floors as authored prisons, not random caves.
- Origin discovery layer (districts, glowcaps, bells, vistas) — proof that Hub exploration is already a design goal.

---

## 3. Aetherion's Core Identity

### What it is trying to feel like

From code + docs (not equal-weight buzzwords):

| Theme | Evidence | Hub implication |
|---|---|---|
| **Physical world first** | Pads, walk-in spawn unlocks, glowcap attune, updrafts, TalkUx at NPC feet | Navigation by sight and path, not only menus |
| **Authored encounters** | Harbour Hour, rites, dungeon rooms, Origin moments | Small stories in space; not empty decoration |
| **Progression you can point at** | Skill roads, stamped blueprint gate on pads, camps | Departure points that *mean* something |
| **Alive NPCs** | LivingNpc life, bark cooldowns, boards, cast roles | Inhabited, not mannequin wallpaper |
| **Systems connected** | Gather → compress → craft; Ledger → roads; Vex → Borderlands | Hub as crossroads of those connections |
| **Spectacle with purpose** | Bosses, dungeon F1 set pieces, lantern festivals | Landmark drama OK if it serves orientation or return visits |
| **"Wait, I can do THAT?"** | Updrafts, glides, waystones, fountain wishes, Starfall | Compact Hub still needs vertical + secret toys |

**Central sentence:** Aetherion wants players to feel they live in a **worked fantasy harbour-city-island world** where skills grow **out there**, and the Hub is the **home dock** — not the entire game.

**Tone:** dry harbour humor, earned power, practical fantasy — not casino neon, not pure anime lobby, not sterile corporate spawn.

---

## 4. Current Player Experience

### First join → early
1. Land at **Anker Harbour** (default unlocked spawn).
2. Hint: find **Egon** (green glow on pier) → orientation + kit.
3. Short gather/craft chain (Forager / Quartermaster / Foreman / Temper) → **Miss Ledger** unlocks Skills.
4. Ledger **stamps** graduation → **roads** chips (Forage Twig / Farm Harrow / Fishing Tackle / Combat Vex).
5. Slime pads to Mining Eldervale / Forage Isle (often **require stamped blueprint**).

### Mid
- Skill islands, craft ladder climb, Borderlands / Colosseum, optional bosses.
- Personal island @20; guilds @75; quarry / mill / forge as economy sinks.
- Dungeons as parallel power track (strong inside, ×0.38 overworld).

### Late / returning
- Spawn menu camps, `/spawn`, glowcaps, wayfinder `/origin go`.
- Social: Capital plaza, harbour docks, guild harbour fantasy.
- Fast access to Manager, shops, departure pads — without replaying tutorial.

### What the Hub should communicate by time

| Window | Should feel | Should NOT |
|---|---|---|
| **30 s** | Beautiful arrival; one obvious next person (Egon / pier energy) | Dump all systems; portal wall |
| **2 min** | "This is a place"; path toward first NPC; distant silhouette | Unlock every camp |
| **5 min** | First conversation + first walk; hint of Capital / mountain | Full map tour forced |
| **15 min** | Orientation spine underway; one curiosity pull (isle pad, mountain, bell) | Endgame dungeon lobby |

**Obvious:** harbour / arrival, who to talk to, where the city sits.  
**Discoverable:** districts, bells, vistas, side paths, ambient NPCs.  
**Mysterious:** Borderlands edge, high peaks, dungeon gate energy, future content hooks.  
**Visual > explained:** pad light columns, pier → hill → city silhouette, mountain gate.

---

## 5. Main World Context

- **Primary Hub world name:** `world` (mmo-r / Capital backend in network terms).
- **Network:** Velocity; dungeon role often `mmo-d` (`AetherionDungeons` `role: hub|dungeon`). Hub portal configs exist but may be disabled on a given deploy — assume Hub must still **work standalone** with pads + `/server`-style transfers as configured.
- **Skill content** mostly lives **on the same `world`** as pasted Eldervale islands (Mining / Forage / Farm / Fishing schematics at large offsets) **or** as separate instance worlds for dungeons/guilds.
- Player **personal / guild islands** are separate void worlds (Guilds plugin) — Hub only unlocks and teleports toward them; it does not host the full island sim.

The Hub is the **social + orientation spine**. Skill islands and dungeons are **elsewhere** (same world far away, or other servers/worlds).

---

## 6. Existing Main Hub (reference — not a layout to copy)

### Source map
- OriginBuilds **"1000×1000 Skyblock RPG Spawn"** pasted **1:1** into `world`.
- Measured footprint (docs): **x ≈ −526…458**, **z ≈ −616…364**; ground ~y 57–64; peak **Skyreach ~y 236**; underside rock roots.
- World spawn **`0 57 0`** ≈ Ledger plaza (Capital).

### Major districts (Origin overlay)
Mount Skyreach · Ore Ridge · Anker Harbour · Seawatch Point · The Capital · Whisperwood Wilds · Bloomfield · The Colosseum · Glowcap Crags · Clucksworth Farmlands · Southwood · The Borderlands · Eastwood.

### Functional layers on that map
| Layer | Plugin | Role |
|---|---|---|
| Spawn camps + `/spawns` menu | Hub | Unlockable teleports (harbour default; capital/farm/borderlands/ore_ridge walk-in discover) |
| Goto commands | Hub | `/harbour` `/capital` `/mining` `/forage` `/fishing` `/farm` … |
| Island slime pads | Hub `IslandLaunchPads` | Physical arcs to Mining / Forage (and returns) |
| Origin discovery | Hub `origin.*` | Districts, 39 landmarks, 8 glowcaps, 5 vistas, 7 bells, updrafts, glides, moments, fountain |
| Origin townsfolk (5) | Hub | Boards (journal / skyways / bells / glowcaps / vistas) — **not** TalkUx |
| Quest LivingNpcs | Quests | Tutorial + world cast with TalkUx bubbles |
| Softlight / props | Hub | DEV lighting + prop wand schems |
| FAWE paste anchors | Hub `aether-paste` | Farming / Fishing isle schematic origins |

### Strengths of current Hub
- Clear **harbour → capital → borderlands** fantasy.
- Real **vertical play** (updrafts, glides, summit).
- Discovery systems already prove compact exploration can pay (coins + titles).
- Pads feel **physical** (jump, arc, land) — Aetherion signature travel.

### Weaknesses / scale problems
- **Too much empty / low-density terrain** for how often players actually walk it.
- Tutorial path is a **long walk** across a map sized for spectacle, not for first 15 minutes.
- Dual NPC systems (Origin boards vs Quests talk) can confuse if co-located poorly (docs keep ≥~14 blocks separation).
- Some areas feel like **map leftovers** (unused slime pads reused as skyways — clever, but shows map-first thinking).
- Performance risk if ambience/displays/NPCs stack (known forage hologram leaks elsewhere — Hub must stay light).

### Critical distinction
**FUNCTIONAL REQUIREMENTS** (must survive somehow): spawn IDs, pad IDs, quest NPC roles + TalkUx, goto/discovery hooks, stamped pad gate, ledger/roads flow.  
**CURRENT IMPLEMENTATION** (free to scrap): exact 13 districts, Skyreach coords, glowcap mushroom props, commercial spawn architecture, 1000×1000 silhouette.

---

## 7. Important Systems (Hub connection matrix)

| System | Why it matters | Hub needs | Outside Hub |
|---|---|---|---|
| **Tutorial / Harbour Hour** | First identity + kit | Arrival + Egon (or successor role) + pier energy; Origin **quiet mode** until Capital unlock | — |
| **Quests / TalkUx** | Spine + bosses | Space for LivingNpcs; bubble UX | Content lines OK to move with NPCs |
| **Skills / Manager** | Power spine | Capital / Ledger presence; Manager access nearby | Skill XP happens on islands |
| **Mining Eldervale** | Mid gather fantasy | Pad / departure landmark (`/mining`) | Isle itself (same `world` paste) |
| **Forage Isle** | Wood loop | Pad + Twig crumb (`/forage`) | Isle (perf issues known — don't copy heavy display habits) |
| **Farm / Fishing isles** | Gather roads | Farm: portal + `/farm`/`/farmisle`; Fish: goto `/fishing` (no jar slime pad) | Isles in `world` |
| **Personal island** | L20 home | Deed / `isle_clerk` affordance | Void world `aether_islands` |
| **Guilds / Quarry** | L75 social economy | Guild fantasy hint; not full machine UI | Void world `aether_guilds` |
| **Dungeons** | Instance progression | Gate NPC / portal **hint** or transfer point | Instances on dungeon backend |
| **Bosses / Borderlands / Colosseum** | Spectacle combat | Edge district energy; Vex / Proctor roles | Arenas / rites |
| **Economy / shops** | Coins sink/source | A few merchants / boards — not mall sprawl | AH/bazaar if menu-led |
| **Crafting** | Temper / ladders | Early forge presence on tutorial path | Stations can be portable later |
| **Pets / Boosters** | Soft power | Optional hint near gear NPCs | Systems live in Items (**LOCKED** models) |
| **Codex / Collections** | Long-term | Optional archive vibe | Mostly UI |
| **Storage** | QoL | Optional bank feel | Often menu |

**Do not give every system a dedicated palace.** Prefer **one landmark that implies a family of systems**.

---

## 8. Progression Context (for Hub storytelling)

Hub should make these spines **legible at a glance**, not teach their formulas:

1. **Harbour work** → Skills desk → **Roads out**.
2. **Gather isles** as visible destinations (pads / distant island silhouettes).
3. **Danger edge** (Borderlands / Colosseum) opposite the safe harbour.
4. **City / Capital** as rules, skills, social density.
5. **High place** as aspiration / vista (returning players love overlooks).
6. **Dungeon / adventure gate** as a later silhouette — curious, not tutorial-blocking.

Account level / island / guild gates belong as **NPC dialogue and distant harbour banners**, not as Hub-minigames.

---

## 9. NPC / Character Context

### Quests LivingNpc cast (TalkUx — keep shell)
Established early-game roles (**ids** matter more than display names):
`egon` · `lumberjack` · `quartermaster` · `foreman` · `craftsman` · `booster_tutor` (Temper) · `ledger` · `farmer` · `lark` · `fisher` (Tackle) · `vex` · `rite_keeper` · `forage_pad_guide` (Twig) · `farm_isle_guide` · `eldervale_welcome` · `dungeon_gate` (Threshold) · `isle_clerk` (Deed) · `arena_proctor` · shops/flavour (`fishmonger`, `vince`, `dockhand`, …).

These NPCs **carry quest state**. Moving them requires updating Quests placement data (`npcs.yml` / living cast) — not "just build a cool spot and hope".

### Origin townsfolk (Hub boards — no TalkUx)
Orla Vane · Cobb Kettleby · Sister Aurel · Fen Glowmoor · Stellan Voss — journal / skyways / bells / glowcaps / vistas.

**Integration note:** Origin cast is optional flavour for a new Hub. Quest cast is **mandatory for tutorial continuity** unless Robb explicitly rewrites Harbour Hour.

### Ambient life
Workers, travelers, merchants, animals-as-ambience — good. **NPC every 10 blocks** — bad. Prefer few memorable faces + sparse ambient.

---

## 10. Quest / Story Context

- **Harbour Hour** owns first minutes: Egon → work → Ledger → stamp → roads.
- Hub must support **compass / wayfinder** targets without explaining the whole cosmology.
- Borderlands / Rite / bosses are **post-graduation curiosity**.
- Lore should be **environmental**: ships, bells, scars, leftovers — not exposition dumps.
- Future questlines need **empty hooks** (closed door, unfinished quay, sealed gate) without dead content spam.

**Visible:** pier, first NPC, capital desk, departure pads.  
**Hinted:** isles, Borderlands, dungeon gate.  
**Hidden:** endgame, guild machine depth, balance cliffs (not Hub's job).

---

## 11. Skill / Island / Guild Context

- **Mining / Forage / Farm / Fishing Eldervale** are full content products with their own docs, weather, boards, DEV hubs.
- Hub connects via **pads, gotos, NPC crumbs, Spawn menu unlocks** — not by rebuilding the isles inside the Hub.
- **Guilds:** "raise a harbour" fantasy — a Hub dock / registry vibe is enough.
- **Quarries / belts / mills:** live on guild islands — Hub only foreshadows.

---

## 12. Dungeon / Boss Context

- Dungeons: floor pools, instances, transfer ledger — **spectacle elsewhere**.
- Hub needs a **legible adventure departure** (NPC `dungeon_gate`, portal, or ferry) without becoming a dungeon hub mall.
- Bosses / Colosseum / Borderlands: **edge of map** energy; Proctor / Vex roles.
- Do **not** paste boss arenas into the Hub.

---

## 13. Economy / Crafting Context

- Coins via `AetherServices.coins()`; Origin rewards already use that.
- Early **Temper / craftsman** on the road to Capital is part of Harbour Hour geography.
- Shops / liquidators / AH can be **small physical stalls + menus** — avoid a second city of shop plots.
- Compression / mill / forge fantasy belongs more to **islands** than Hub.

---

## 14. Visual Identity

### DNA across recent work
- **Minecraft-native first**, custom models/CMD where it elevates (items, some props).
- Eldervale isles: district identity, soft particle weather, boards, physical benches — **readable biomes**.
- Dungeon F1: authored prison set pieces, heavy silhouette, intentional gloom.
- Origin Hub: glowcaps, bells, fountain, lighthouse beam, lantern festivals — **romantic harbour-fantasy**.
- NPCs: TalkUx bubble + chips; LivingNpc life movement (separate from Origin boards).

### Architecture / materials (descriptive, not a palette mandate)
Harbour wood + rope + boats; Capital stone + bells + plaza water; mountain stone + height; Borderlands ash/scar; farm hay/meadow. Fantasy **grounded** — not crystal-castle spam.

### Custom vs vanilla
Use custom assets for **identity props** (pads, waystones, key monuments). Fill with good vanilla composition. Resource pack progression gear is mid-polish (see polish audit) — Hub shouldn't depend on unfinished item textures.

---

## 15. Floating-Island Direction

The new Hub **should be a floating / sky island**.

Thematic fit: current map is already a skyblock spawn with underside roots; Origin leans into **height, updrafts, glides, lighthouse, aurora**.

Give Opus freedom on:
- island silhouette + underside
- cliff edges, waterfalls into void/cloud
- satellite fragments + bridges
- arrival from below/side/portal vs "always was here"
- how distant skill-isle silhouettes read in the skybox

**Constraint:** edges must handle falls (current: rescue below y −60). Keep a **safe fail** for new players.

---

## 16. Exploration Philosophy

In 300–400 blocks, exploration comes from **occlusion, elevation, and path choice**, not distance.

Fits Aetherion:
- Harbour quay → hill switchback → city terrace
- Mountain gate you see before you can reach
- Side alleys to a bell / shrine / overlook
- Underside / cave mouth / pier underside secret
- Bridge to a small fragment with one NPC or vista
- Rooftops / scaffolding as optional routes

Does **not** fit: endless forest filler, mirrored plaza wings, maze for maze's sake.

---

## 17. Spatial Design Requirements

**Dense, not crowded.**

| Space type | Role |
|---|---|
| **Functional** | Pier, first NPCs, Ledger desk, pads, spawn points |
| **Discovery** | Bells/waystones/secrets/vistas — optional rewards |
| **Empty / breath** | Short lawns, cliff air, water — framing, not deserts |

Avoid giant empty plazas **and** visual noise (particle blizzard, NPC carpet, hologram forest).

Hard max **400×400**. Prefer feeling bigger via **height stack** (harbour / mid / high) over spreading.

---

## 18. Landmark Philosophy

Players should navigate by **"toward that"**:
- Memorable **arrival** (pier / gate / ship)
- **Central identity** (capital heart / tower / fountain — Opus invents)
- **Secondary** (mountain, border scar, pad towers, lighthouse)
- **Distant goals** visible from arrival within the 300–400 footprint

Do not prescribe exact landmarks unless reusing **functional** ones (Egon pier, Ledger desk, pads, Vex gate). Silhouettes are Opus's.

---

## 19. Player Flow

### New player
`JOIN → ARRIVAL (harbour energy) → ORIENT (see city / mountain) → FIRST NPC (Egon) → FIRST QUEST → FIRST WALK → FIRST DEPARTURE (pad / road) → RETURN`

### Returning player
`JOIN → selected spawn OR /spawn → quick read of landmarks → Manager / pads / social plaza → leave`

Both must work. Tutorial must not block veterans' sightlines to pads and Capital.

---

## 20. Discovery Philosophy

Prefer:
- Walk-in unlocks (already Hub DNA)
- Attune / touch / stand-on (glowcaps, vistas)
- Environmental readability (light columns on pads, pier direction)

Avoid depending on:
- Giant floating text walls
- Arrow particle highways
- NPC spam explaining menus
- GUI-only orientation for the first minutes

TalkUx bubbles at NPCs are **allowed and desired** for quest NPCs — that is diegetic, not hologram spam.

---

## 21. Technical Infrastructure

### World / spawn
- Hub plugin: `AetherionHub` — `HubService`, `SpawnCommand`, `SpawnMenu`, `SpawnGotoCommand`, `SpawnDiscoverListener`, `PlayerHubData`.
- Config: `plugins/AetherionHub/config.yml` — `spawns.*`, `island-pads.*`, `aether-paste.*`, join/respawn flags.
- Origin overlay: `origin.yml`, `origin-players/`, `origin-cast.yml`.

### Building / paste
- Hub: FAWE `/aetherpaste`, prop wand schems, Island paste helpers.
- Guilds: `PasteService` / NBT templates for guild structures.
- Dungeons: structure NBT pool paste.
- Foraging: `ForageIslePaste`.
- Opus Hub build will likely ship as **world folder / schematic** then re-hook coords in YAML — plan for **config relocation**, not hardcoded magic in Java.

### NPC
- Quests: LivingNpc + TalkUx + CastBook + skins pipeline.
- Hub Origin: villager boards + PDC `aetherionhub:origin_npc`.
- FancyNPC / editor paths exist for mods — don't break LivingNpc ids.

### Visual
- BlockDisplay / ItemDisplay / TextDisplay spawned **per plugin** (no shared Core `DisplayEntities` helper in this tree).
- Patterns: pad labels, Origin waystones, TalkUx bubbles, isle critters, dungeon chest props (avoid importing dungeon **1-tick** prop tickers to Hub).
- Historical forage hologram leak fix (`ForageDisplayGuard` / `49cd2a7`) did not land in Eldervale ship — **Hub must cap, tag, non-persist, and purge displays**.

### Building the new Hub map
- Prefer **FAWE/WE schems** (Hub `/aetherpaste`, prop wand) or AmbientProp dressing — not Guilds tick-paste and not Dungeon Structure NBT for capital terrain.

### Safety / protection
- Hub has **no WorldGuard dependency**; WG is used by Mining/Farming/Foraging/Items/BossEngine. New Hub still needs clear build/break rules (WG or plugin guards) before public play.

### Persistence
- Hub spawn unlocks + Origin journal are per-player YAML.
- Quest progress is Quests-owned.
- Wipes must respect **LOCKED** `player-ranks.yml` (special ranks).

### Network
- `AetherionDungeons` remote-transfer / character sync for mmo-r ↔ mmo-d.
- Hub portal blocks may be enabled/disabled per env — keep goto + pads as **primary** Hub travel.

---

## 22. Integration Requirements (painful if ignored)

When the new Hub lands, expect to **re-point**:

| Hook | Today | Integration need |
|---|---|---|
| `spawns.*.location` via `/hubadmin set` | Live coords on old map | Re-set every camp |
| `island-pads` AABB + targets | Hardcoded in `config.yml` | Rebuild pad volumes + arcs |
| Quest NPC positions | `npcs.yml` / living cast | Move cast with TalkUx intact |
| Goto commands | Hub teleports | Update destinations |
| Discover radii | SpawnDiscoverListener | Re-tune for compact map |
| Origin.yml anchors | Districts/landmarks/… | Either rebuild Origin content for new map **or** slim/replace Origin layer (Robb call) |
| FAWE isle paste origins | Farming/Fishing paste XYZ | May stay far away in `world` or move |
| First-join hint | Harbour Egon | Keep semantic target |
| Stamped blueprint pad gate | IslandLaunchPads | Preserve rule |
| Dungeon / hub portals | Dungeons config | Re-place if used |
| Softlight / ambience emitters | Origin | Don't port 40 emitters blindly — budget |

**World name:** staying on `world` minimizes plugin churn; a new world name forces spawn/world checks across Hub/Quests/Items area services.

**Assumption to challenge carefully:** many systems assume Hub content is in `world` near spawn 0,0. Moving spawn is fine; splitting Hub to another world is a larger contract change.

---

## 23. Performance Constraints

Known traps in Aetherion:
- Unbounded TextDisplays / holograms (forage/hub history).
- 1-tick routers that do too much work every tick.
- Global particle moments without per-player opt-out.
- Too many always-loaded chunk-heavy decorative entities.
- Dungeon-style chest prop tick-all displays — don't import that pattern to Hub.

**Guardrails for a busy Hub:**
- Prefer **non-persistent**, tagged displays; purge on start.
- Cadence ambient ticks (Origin already uses 1/2/4/10/20/60 pattern — good model).
- Particles **near players only**; respect ambience off.
- NPC count: dozens max for Hub proper, not hundreds.
- No per-player hologram forests visible to everyone.
- Keep pathfinding AI off for decorative villagers.

---

## 24. MUST SURVIVE

- **Harbour-first tutorial identity** (Egon → work → Ledger → stamp → roads), or an explicit Robb-approved rewrite of that spine.
- **TalkUx in-world bubble + chips** for living quest NPCs (**LOCKED**).
- **Spawn system**: `/spawn` `/hub` `/spawns`, unlockable camps, walk-in discover pattern.
- **Goto surface** (commands + menu) for major destinations players already know by name.
- **Island launch pads** as physical travel (Mining / Forage at minimum) + stamped gate behaviour.
- **Miss Ledger / Skills unlock** as Capital function.
- **Vex / Borderlands / danger edge** as post-tutorial combat road.
- **Departure to skill isles** as visible fantasy.
- **HubAccess / wipe hooks** compatibility with Items full wipe (Origin journal wipe pattern).
- **Additive ship mentality** — don't delete richer live Hub commands/props when replacing the map (union of lineages).
- **LOCKED systems untouched:** booster socket model intent, ranks/wipes rules, signature weapons combat, shutdown countdown, TalkUx shell.

---

## 25. CAN BE REINVENTED

- Entire terrain, silhouette, architecture, district names, landmark art.
- Origin-specific glowcaps / seven bells / exact 13 districts / commercial spawn props.
- Exact pad coordinates and skyway paths (keep the *idea* of authored flight if desired).
- Origin townsfolk boards vs folding some of that into Quests/Hub differently (with care).
- Fountain / festivals / aurora — keep spirit or replace with new Hub-scale moments.
- Visual style within Aetherion DNA (harbour-fantasy floating island).
- How Capital vs Harbour share the compact footprint (stacked terraces vs split lobes).

---

## 26. DO NOT BRING FORWARD

- **1000×1000 empty skirts** and filler forests that exist only because the map was big.
- Portal-wall / NPC-wall lobby tropes.
- Dual unexplained tutorial greeters stacked on the same block.
- Hologram instruction spam as primary UX.
- Pasting Eldervale / dungeon interiors into the Hub "so players see content".
- Legacy unused slime pads without a new authored purpose.
- Assuming every Origin discovery collectible must return 1:1.
- Performance-heavy ambient stacks "because the old Hub had 40 emitters".

---

## 27. Creative Freedom

Opus **should invent**:
layout · architecture · terrain · island silhouette · districts · landmarks · paths · verticality · bridges · interiors · secrets · environmental storytelling · ambient life · atmosphere · decorative systems · small Hub-specific interactions (within performance budget)

Opus **should not invent** replacements for LOCKED UX/systems, and should not silently delete Harbour Hour / pad / spawn contracts without Robb's go.

---

## 28. Negative Design Rules

- No generic Minecraft lobby.
- No giant sterile symmetrical spawn disk.
- No portal wall / NPC wall.
- No hologram spam as navigation.
- No second 1000×1000.
- No meaningless buildings with no path, NPC, or view payoff.
- No decorative filler terrain.
- No "every system gets a cathedral".
- No over-designed first 30 seconds that blocks walking.
- No importing dungeon tick/display patterns that tank FPS.
- No breaking TalkUx by replacing quest NPCs with board-only villagers for the tutorial spine.

---

## 29. Final Creative Brief (to Claude Opus)

You do **not** need to rediscover Aetherion from scratch. This bible + the linked docs are your context pack.

**Your job:** physically express Aetherion's identity in a **new floating-island Main Hub**.

**Size:** about **300×300 to 400×400**. Hard maximum **400×400**.  
Large enough to explore. Compact enough that every area matters.

**Feel:** a **real place**, not a server lobby.

Players should want to:
LOOK AROUND · WALK · FOLLOW A PATH · CLIMB · SEE BEHIND · NOTICE A DISTANT LANDMARK · WONDER · RETURN AND NOTICE SOMETHING NEW.

**Preserve function, reinvent form.**  
Harbour Hour and travel hooks must still make sense after your map exists. Exact old coordinates and the commercial 1000×1000 layout do **not**.

**Quality bar here means:** authored movement (pads, height), readable interaction, NPC presence with TalkUx for quest cast, environmental storytelling, deliberate effects, cohesive early-game flow, density with breath — the same bar Origin and Eldervale aimed at, without their scale mistakes.

When you design, leave clear **integration seams** (spawn IDs, pad volumes, NPC docks, Capital desk, danger edge, isle departures) so wiring YAML/commands afterward is mechanical — not a redesign of the game.

Build the world Aetherion has been growing into:  
**compact · high-signal · harbour-rooted · sky-island · worth walking.**

---

## Appendix A — Current spawn IDs (config surface)

From `HubService.ORIGIN_SPAWN_IDS` / Hub `config.yml` (names are contracts; coords are not):  
`harbour` (default) · `ore_ridge` · `mines` · `capital` · `forage_isle` · `farm` · `farm_isle` · `borderlands` · `colosseum` · `eldervale` · `fishing_eldervale` · Origin camps `summit` / `whisperwood` · optional `bloodstone` (recognised, never auto-created).

**Retired (do not revive blindly):** `veil`, `ruins`, `spawn`, `lurker_camp`, `royal_palace`, `ticket_hall`, `trash_chute`, `guild_quarry`, `worm_tunnels`, `collections`.

Gotos: `/harbour` `/oreridge` `/mines` `/capital` `/forage` `/farm` `/farmisle` `/borderlands` `/colosseum` `/eldervale` `/mining` `/fishing` `/summit` `/whisperwood` …

## Appendix B — Current pad / portal contracts

**Slime pads** (`island-pads`): `origin_to_mining` · `mining_to_origin` · `origin_to_forage` · `forage_to_origin`.  
- Mining pad typically **requires stamped Surveyor blueprint**; forage pads often open.  
- **No jar-shipped farm/fish slime pads** — Fishing uses goto/landing; Farming uses **nether-style portals** (`FarmPortal*`, often gated Farming 10).

**Dungeon hub portal** (Mountain Gate vicinity, may be config-disabled): default `hub-portal` ≈ `world −1 67 −204` r=12 → `mmo-d`; return land ≈ `−1 67 −198`.

**FAWE isle pastes in same `world`:** Farming ~`−600 90 427`; Fishing ~`−585 90 −649`; Mining Eldervale south of mine pad (see Mining `eldervale.paste`).

## Appendix C — LOCKED reminders for Hub work

- TalkUx shell  
- Booster sockets / stack intent + Anvil GUI  
- Special ranks / wipe exclusions  
- Blossom Blade / Gravwell Cleaver combat  
- Shutdown countdown  

## Appendix D — Next-patch note (unrelated but tracked)

Foraging isle still lags live despite Eldervale ship; `ForageDisplayGuard` (`49cd2a7`, branch `cursor/forage-hologram-leak-470a`) never merged into Eldervale jar — must ride a later patch. Do **not** expand Hub displays into the same failure mode.

---

*End of World Bible. No implementation performed.*
