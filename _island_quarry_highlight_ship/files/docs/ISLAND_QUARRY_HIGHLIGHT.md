# Island · Quarry · Guild highlight (AetherionGuilds)

Personal islands, guild islands and quarries grew into one highlight system. Everything lives in
**AetherionGuilds**; Items, Core, TalkUx, ranks, weapons, boosters/anvil and the shutdown countdown are untouched.

> "Oh, I unlocked my island." · "I picked this starter and I'm growing it." · "That quarry is feeding my belts." · "Our guild built that."

## The loop

1. **Foreshadow** (Level 15 via PlaceholderAPI `%aetherion_level%`): a gull drops a note, `/island` opens the three
   starters as a locked preview.
2. **Unlock** (Level 20, gate unchanged in Items): title, chime, totem burst, clickable *Choose your starter*.
3. **Claim**: pick **Grove Camp**, **Quarry Outpost** or **Tide Dock** (authored ~31×31 isles, not dirt circles).
   "The island rises…" while it pastes, then you drift down onto it (slow falling), fanfare, a 3-line *first steps* card.
4. **Build**: blueprints with a particle ghost (green = valid pad, red = blocked cells). Right-click builds, left-click
   rotates. Storage Hut (first one free), Workshop, Depot, Mill, Forge. Each starter has a marked Hut Site.
5. **Quarries are physical**: every quarry gets a timber headframe (pasted only into empty space). Its chute faces you.
6. **Belts**: the Rail Layer lays rails, straight or L-shaped, chaining from the end. A belt that runs into a hut,
   depot, mill or forge feeds it. Mill: raw → compressed. Forge: compressed → compacted. Chains extend:
   quarry → mill → forge → hut.
7. **Expand**: a 9×9 land map of 16×16 parcels. Buy land touching yours; new ground rises there in your island's look
   (or build rights only, for sky bridges). Island Tier (coins only now) still adds radius, belt capacity and
   one more of each machine.
8. **Guilds**: founding raises the **Guild Harbour** (plaza, well, banner poles, project board, two reserved sites)
   and drops the founder onto it: *"We have a place."* Every member's first arrival gets its own beat.
   **Projects**: Mayor+ starts the Guild Hall or the Harbour Beacon; everyone chips in (pack, guild Storage Huts,
   coins, guild bank). Each paid stage visibly rises on site with a guild-wide title.

## Architecture

| Piece | Class | Notes |
|---|---|---|
| Templates | `template.TemplateLibrary`, `Template`, `NbtIn`, `StateRotator` | Sponge v2/v3 reader, rotation on state strings (no newer API). Bundled in the jar, extracted once to `plugins/AetherionGuilds/templates/`; that folder wins, so re-author with WorldEdit and `/island admin reload-templates`. |
| Paste | `template.PasteService` | Tick-spread (config `paste-blocks-per-tick`), bottom-up, no physics. Modes: skip-air, only-replaceable (housings), clear-matching (deconstruct, stage transitions). |
| Islands | `island.HostService`, `IslandHost` | One view over personal + guild islands (permissions, money: personal = your coins, guild = guild bank). |
| Starters | `island.StarterLayout` | Spawn, pads, land look. Numbers mirror the generator. |
| Land | `island.LandService` | Parcels 16×16, ring ≤ 4 (edge ±71 < wipe radius 80). Build zone = classic tier square ∪ parcels. |
| Unlock beats | `island.UnlockService` | Seen-state in `personal_islands.yml → unlock.*`. |
| Structures | `structure.StructureService`, `StructureType`, `PlacedStructure` | `island_structures.yml`. Protection is exact: a block is protected only while it still is the template's block. |
| Build modes | `structure.PlacementService` | Blueprint ghost + Rail Layer. No tool items. |
| Logistics | `logistics.LogisticsService`, `Belt`, `Res`, `CargoVisuals` | `island_logistics.yml`. Routes resolved on change; flow is arithmetic (same for online/offline). |
| Projects | `project.GuildProjectService`, `GuildProjectType` | `guild_projects.yml`. |
| Menus | `menu.StarterSelectMenu`, `BuildMenu`, `LandMenu`, `StorageMenu`, `MachineMenu`, `GuildProjectMenu` | One holder type (`MenuKit.Holder`), one click route. |

### Simulation (authoritative) vs presentation (culled)

- **Routes** are computed once per change (belt laid/removed, structure placed/removed): each producer follows its
  belt to the first machine it runs into, or a dead end.
- **Flow step** (1 s for islands someone stands on, 60 s catch-up for everything else, and on shutdown): quarries
  catch up with the existing math, their storage drains into the route target; processors convert (speed-limited,
  output capped = back-pressure) and push on; sinks fill to capacity. A quarry with nowhere to send stops at its cap
  exactly like before.
- **Visuals** exist only while someone is on the island: cargo ItemDisplays glide tile to tile with client-side
  teleport interpolation (≤ 36 per island, ≤ 240 total, non-persistent), a turning millstone, a bucket on the
  headframe chain, forge sparks, hut puffs.
- Rails keep their shapes (physics cancelled on belt rails only). Belts, their support blocks and structure blocks are
  protected from breaking, pistons and explosions.

## Data (all additive; rebuildable from YAML)

- `personal_islands.yml`: `starter`, `parcels`, top-level `unlock.*`.
- `guilds.yml`: `starter`, `parcels`.
- `island_structures.yml`: structures per host, buffers/storage.
- `island_logistics.yml`: belt tiles per host (`/island admin rebuild` re-places rails from it).
- `guild_projects.yml`: renown, completed, visited, active project + paid.

Existing islands keep their classic pads (no starter = legacy path, nothing re-pasted). Existing quarries are linked as
**frameless housings** on first boot (belts may start next to them); *Raise Housing* in the quarry menu builds the
headframe on demand, only into empty space.

## Numbers (balancing irrelevant, simple)

Island Tier: 5k / 20k / 60k / 150k coins. Land: 2,500 × (parcels − 8) personal, 10,000 × … guild.
Hut 2,000 (first free), Workshop 1,500, Depot 500, Mill 3,000 + Quarry Mill item, Forge 8,000 + Quarry Forge item,
belts 5/tile. Hut holds 5M raw-eq, Depot 250k, machines 4,096 raw-eq/s. Projects: see `GuildProjectType`.
