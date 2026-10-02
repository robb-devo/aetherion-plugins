# Aetherion — Player Islands / Guilds / Quarry Overhaul  
## Opus Implementation & Design Brief (Scout Handoff · Robbi-amended)

**Audience:** Claude Opus (implementer) + Robbi (scope owner).  
**Scout date:** 2026-09-30 · amended same night after Robbi feedback.  
**Stance:** This is a **highlight system**. Build the real skeleton with depth — not a timid Phase-1 stub. Fine polish later. **Balancing is irrelevant right now.**

---

## 0. One-line brief

Evolve the **already-working** personal islands, guild islands, and quarry minions in **AetherionGuilds** into a **highlight Aetherion feature**: nicer unlock/announce, 2–3 authored starter islands you expand like SkyBlock, placeable structures, and a **Satisfactory-lite quarry + conveyor production loop** that already has real depth — without full factory-sim bloat, without killing TPS, without gutting what works.

Emotional target:

> “Oh shit, I unlocked my island.” / “I picked this starter and I’m growing it.” / “That quarry is feeding my belts.” / “Our guild built that.”

Not:

> “I clicked Upgrade and a number went up.”

---

## 1. Non-negotiable rules

1. **Existing systems work.** Preserve them. Extend beside them. Do not delete personal/guild/quarry/friend/bank foundations.
2. **Additive ship.** Never deploy a thinner Guilds jar.
3. **Highlight energy, not feature bloat.** Depth through **physical world + production chain**, not 50 menus / currencies.
4. **Performance still matters.** Visuals cheap; sim ≠ entity spam. Inactive islands quiet.
5. **World-fit.** Fantasy Aetherion — harbour / mine / forest / fishing vibe. No sci-fi chrome factories.
6. **Balancing = 0 priority.** Pick simple numbers (rates, costs, caps, expand prices) and ship. If a value feels fine, **it’s approved**. Do not design elaborate economy logic. Do not nerf “because Hypixel”. Crazy quarry output for now is **fine**.
7. **Locked systems stay locked:** TalkUx shell, special ranks/`player-ranks.yml`, signature weapons, boosters/anvil sockets, shutdown countdown. NPC/dialog **content** for unlock moments OK.
8. On real conflicts:

```
LOCKED/EXISTING SYSTEM CONFLICT:
[system]
[what conflicts]
[why it matters]
[recommended solution]
```

---

## 2. What exists today (accurate — preserve)

### 2.1 Ownership map

| System | Plugin | World | Model / service |
|--------|--------|-------|-----------------|
| **Personal Island** | `AetherionGuilds` | `aether_islands` | `PersonalIsland` + `PersonalIslandService` |
| **Guild + Guild Island** | `AetherionGuilds` | `aether_guilds` | `Guild` + `GuildService` + `IslandService` |
| **Quarries** | `AetherionGuilds` (+ Items craft) | both | `QuarryMinion` + `MinionService` + `QuarryMenu` |
| Unlock gates | Items → Core `ProgressAccess` | — | Island **Lv 20**, Guild **Lv 75** (gates may stay; **presentation** must upgrade) |
| Skill isles / Origin Hub | other plugins | other worlds | **UNRELATED** |

Commands: `/island`, `/guild`, `/friend`.

### 2.2 Personal Island today

- Biome pick PLAINS/FOREST/DESERT → procedural `IslandBuilder` platform (small), levels 1–5 expand radius.
- Persistence: `personal_islands.yml` + world blocks.
- Owner build, friend visit-only, flight, void rescue.
- **No** authored starter layouts, **no** Storage Hut system, unlock is Manager/level-gate flat.

### 2.3 Guild Island today

- Shared plot, bank, ranks, shared quarries, same builder, no biome pick.
- **No** highlight unlock ceremony, **no** guild projects / staged builds.
- Collective = bank + quarries + island level decor only.

### 2.4 Quarries today

Hypixel-style **minions**: ArmorStand + virtual catch-up production + GUI. Mill/Forge = enum on the minion. **No** physical belts, **no** external processors, **no** visible ore path.

Keep the **sim backbone** (catch-up ints, place on island, types, craft items) — evolve presentation + logistics hard.

### 2.5 Reuse infra

- Hub `PropCatalog` / `PropWand` / `FaweIslandPaste` — authored schems + async paste
- Millstone BlockDisplay, ExploreChest / StarterKit ItemDisplay motion — machine + cargo visuals
- Do **not** confuse skill-isle worlds with personal/guild plots

---

## 3. KEEP / IMPROVE / NEW / DO NOT TOUCH

### KEEP

- Dual worlds + plot spacing model
- Shared builder/tier plumbing (extend / fork layouts — don’t delete)
- Rank ACL, friend visit rules, void rescue, personal flight
- Quarry place/collect/upgrade/craft ladder as **data backbone**
- Catch-up AFK math (cheap) — still authoritative sim
- Guild bank
- Additive YAML / side files for new state
- Level gates can remain numerically; make the **moment** better

### IMPROVE (required this pass)

- **Unlock / announce** for personal **and** guild island — highlight, not a quiet Manager unlock
- **Starter identity** — 2–3 designed starting islands (not one tiny flat pad)
- **Expandable space** — SkyBlock-style buy/expand more build room (evolve `IslandTiers` / costs; numbers = pick and go)
- Quarry feel: from floating stand → **machine in the world**
- Personal storage story (Storage Hut physical)

### NEW (build now — this is the highlight)

1. **2–3 starter island templates** (authored layouts; slightly larger than current default platform)
2. **Highlight unlock flow** (personal + guild) — ceremony / announce / first-arrive beat
3. **Placeable curated structures** — Storage Hut, Workshop, depot, conveyor bits, quarry housings…
4. **Satisfactory-lite production** — physical quarry → conveyor → processor/depot/storage with **real depth already** (not “architecture only”)
5. **Guild projects v1** (can be thinner than personal production, but present — visible shared build)
6. Simple expand-plot / buy-space loop

### DO NOT TOUCH

- TalkUx shell, ranks wipe rules, signature weapons, boosters, shutdown countdown
- Skill-isle / Origin as “the island”
- Silent wipes of bank/friends/existing island data
- Full Satisfactory clone (no power grid spreadsheet, no 40 machine types day one)
- Unrestricted free-build editor / real hopper megafactories
- Endless balance spreadsheets

---

## 4. Design north star

### This is a highlight system

Players should remember unlocking it. Starter choice should feel like picking a home. Production should look and play like a lightweight factory fantasy on an Aetherion isle — readable belts, loud machines, growing footprint.

### Starter islands (2–3)

Design **two or three** distinct starters. Examples (invent better if you want):

- **Camp / Grove** — cozy forest/plains starter, soft storage + workshop pads  
- **Quarry Outpost** — rockier, obvious first quarry pad + ore heap energy  
- **Tide Dock** — stilt/harbour-leaning starter (still void-island safe)

Rules:

- Each is an authored layout (schem and/or richer `IslandBuilder` template) — **not** three identical circles with different dirt.
- **Slightly larger** than today’s default platform (today ~radius 8 at L1). Don’t go mega-hub sized.
- Player **picks one** at claim (replaces or sits beside biome pick — your call; biome can become template flavor).
- Afterwards: **expand / buy more space** like SkyBlock (island level / plot radius / bought chunks of build room). Existing upgrade cost table can be rewritten with **simple placeholder numbers**.

Guild side: nicer unlock/announce + optionally a stronger default guild plot layout (one solid guild starter is enough if 3 personal templates already stretch scope).

### Unlock / announce (personal + guild)

Make claiming feel like an event:

- Clear foreshadow before unlock (Manager hint, NPC line, level toast — content OK)
- Claim moment: title/subtitle/sound/particle beat + first teleport onto the chosen starter
- Guild create / first `/guild home`: collective “we have a place” beat — not silent TP

Do **not** rewrite TalkUx. Chest GUI + chat + titles are enough if NPC bubble work would fight the lock.

### Player moments that must exist in v1

- I unlocked this and it felt big.
- I chose starter A vs B vs C.
- I bought more space.
- I placed a Storage Hut / Workshop.
- My quarry is a physical thing.
- Belts move stuff toward storage/processing (visually + abstract sim).
- I can extend the chain a bit (quarry → belt → processor or depot → storage).
- Guild has at least one “we built this” project beat.

### Balancing

**Ignore.** Fat quarry output OK. Upgrade costs = whatever simple ints feel shippable. Caps generous. Tune later.

---

## 5. Architecture (fit existing code)

### 5.1 Home

**`AetherionGuilds`**. Soft-use Hub paste/schem patterns. Items for crafts/unlock gates. Core stays thin.

### 5.2 Sim vs presentation

```
SIM (authoritative)     →  catch-up production, buffers, belt transfer ticks,
                           structure unlock flags, expand level
PRESENTATION (cull)     →  quarry housings, ItemDisplay cargo, ghosts, stage pastes
                           only near players / active islands
```

### 5.3 Persistence (additive)

| File | Purpose |
|------|---------|
| extend `personal_islands.yml` | starterTemplate id, expand level fields |
| `island_structures.yml` | placed structures |
| `island_logistics.yml` | belt segments, machine links |
| `guild_projects.yml` | guild staged projects |
| extend `guilds.yml` lightly | or side file for guild starter/announce flags |

Rebuildable visuals from YAML recipes — don’t rely on world blocks alone through clears/wipes.

### 5.4 Starter templates + expand

- Registry: `IslandStarter` id → schem/block recipe + spawn + reserved pads (quarry/storage/workshop hints)
- Claim UI: pick starter (2–3 cards)
- Expand: pay simple cost → grow build radius / platform ring (evolve `IslandTiers`; rewrite numbers freely)
- Protect starter footprint + placed structures from expand stomps

### 5.5 Placeable structures

Ghost → valid pad → confirm → paste. Curated catalog only.

Minimum v1 set:

- Storage Hut (physical sections; upgrade same anchors)
- Workshop
- Quarry housing (per family or generic+)
- Depot / input chest
- Conveyor straight + corner (+ ramp if easy)
- One processor machine body (mill/crusher/smelter — pick simple)

### 5.6 Satisfactory-lite quarries + logistics (**build depth now**)

Not full Satisfactory. Not GUI-only minion cosplay either.

**v1 depth that must ship:**

1. **Physical quarry** — housing on place; migrate existing minions; interact still works  
2. **Output port** — abstract buffer feeding logistics  
3. **Placeable conveyors** — graph quarry → … → depot/processor/storage  
4. **At least one external processor node** (promote Mill/Forge off “flag only” toward a placeable machine; old install path can remain as shortcut)  
5. **Storage Hut / depot endpoint** that receives from belts  
6. **Visible cargo** on belts when players nearby (hard-capped ItemDisplays)  
7. **Room to grow** — `MachineNode` interface so splitters/smelters can land later without rewrite  

**Still forbidden as primary:** vanilla hopper megafactories, item-entity rivers, per-tick pathfinding, thousands of displays.

**Rates:** make quarries feel juicy. Simple curve. Approved by default.

### 5.7 Guild projects

- Mayor+ starts; members contribute (Footman included)
- Visible stages on guild plot
- Additive guild menu slot
- Can be fewer pieces than personal logistics — but must feel real

---

## 6. Constraints & risks (short)

| Risk | Move |
|------|------|
| TPS death from belts | Caps, near-player only, abstract transfer, pause inactive |
| `IslandBuilder.clear/upgrade` stomps | Protect lists / reserved footprints |
| Wipe list misses new YAMLs | Register new files in BetaWipe deliberately; never touch ranks |
| Scope explosion into full Satisfactory | Hard stop at: quarry + belts + 1 processor + depot/storage + expand + starters + unlock beat + thin guild project |
| Skill-isle confusion | Don’t edit Farming/Mining/Foraging/Fishing isle packages |

---

## 7. Implementation shape (one highlight ship, not timid stubs)

Prefer **one ambitious ship** (or two tight ships if quota forces a split) covering:

### Ship A — Island identity (must)

- 2–3 starter templates (+ slightly larger base)
- Expand / buy space
- Highlight unlock/announce (personal + guild)
- Structure framework + Storage Hut + Workshop placeable

### Ship B — Production highlight (must — same pass if possible)

- Physical quarries
- Conveyors + depot + one processor
- Belt visuals + abstract transfer
- Juicy simple rates
- Thin guild project v1

If quota splits: finish Ship A, pause, continue Ship B — **do not** ship identity without a production path plan in the same brief README.

**Compile** `AetherionGuilds` (± Items if new crafts). **Do not deploy** unless Robbi asks. Hash-guarded `_island_quarry_highlight_ship/` + honest README.

---

## 8. Key files

**Guilds:** `AetherionGuilds.java`, `PersonalIslandService`, `IslandService`, `GuildService`, `MinionService`, `FriendService`, `IslandBuilder`, `PersonalIsland`, `Guild`, `IslandTiers`, `QuarryMinion`, `QuarryType`, `GuildRank`, `GuildListener`, menus (`IslandMenu`, `BiomeSelectMenu`, `GuildMenu`, `QuarryMenu`, `BankMenu`), `config.yml`

**Items/Core:** `ProgressionService` (20/75), `QuarryItems`, `CompressedResource`, `RecipeRegistry`, Manager GUI, `ProgressAccess`, `BetaWipe`, `VoidChunkGenerator`

**Reuse:** Hub `PropCatalog` / `PropWandListener` / `FaweIslandPaste`; Millstone cabinet; ExploreChest / StarterKit display motion

---

## 9. Opus working instructions

1. Read **this whole brief**. Full focus on the system.
2. Grep allowlisted island/guild/quarry/paste paths — no monorepo tourism.
3. Preserve working behavior; add highlight layers.
4. **Don’t balance.** Pick numbers. Ship.
5. Satisfactory-lite with real depth — not a fake README architecture.
6. Not full Satisfactory.
7. Ship folder + compile Guilds; no deploy unless asked.
8. Quota empty → wait → continue.
9. Conflicts → `LOCKED/EXISTING SYSTEM CONFLICT` and stop that thread.

---

## 10. Opus chat paste (full highlight pass)

```
Read `docs/OPUS_ISLANDS_QUARRY_OVERHAUL_BRIEF.md` end-to-end.

Build the Island / Guild / Quarry HIGHLIGHT system in AetherionGuilds:

- Nicer unlock/announce for personal AND guild islands
- 2–3 authored starter islands (slightly larger than current); player picks one; expandable / buy-more-space like SkyBlock
- Placeable curated structures (Storage Hut, Workshop, depot, …)
- Satisfactory-lite quarries NOW with real depth: physical quarry → conveyors → processor/depot/storage (visible cargo, abstract sim). Not full Satisfactory.
- Thin guild project v1 with visible stages
- Balancing is IRRELEVANT — juicy simple numbers, don’t overthink
- Preserve existing island/guild/quarry/bank/friend foundations (additive)
- No TalkUx shell / ranks / weapons / boosters changes
- Performance: cull visuals, no hopper megafactories
- Ship `_island_quarry_highlight_ship/`, compile Guilds (± Items if needed), do NOT deploy
- Full focus on the system; fine polish later
```

---

## 11. Success definition

A player who unlocks an island feels a **highlight**, picks a **real starter**, grows space, places buildings, and watches a **juicy quarry chain** move resources through the world. A guild feels a place they’re building together. Server stays alive. Foundations weren’t deleted. Balance pass is explicitly later.
