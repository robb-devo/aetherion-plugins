# Entity lifecycle

Two kinds of entities. Do not treat them the same.

## Persistent / intentional

Saved with the chunk. One canonical instance. Never bulk-removed by area.

- Quest NPCs, dungeon NPCs, FancyNpcs, traders, the Foreman
- Bosses and their minions while an encounter is running
- Jump pads and their single label (`IslandLaunchPads`)
- Forest / configured custom entities and editor NPCs
- The **one** marker for each area, animal zone, mob zone, pet habitat, crypt hologram, and the Forage Isle guide label

Markers are restamped only when their anchor chunk is already loaded and no entity with that owner id exists. `Bukkit.getEntity` is null for an unloaded chunk; spawning in that case used to stack a new persistent copy on the saved one.

## Temporary / runtime

Not saved (`setPersistent(false)`). Removed when the use ends: area leave, logout, disconnect, death, teleport, restart, or chunk unload.

- Wild pets and equipped pets (`ItemDisplay` + nameplate). The tracker calls `remove()` when the body is gone so the nameplate does not linger.
- Ambient wildlife (`ambient_runtime`, or an older titled animal that was already flagged remove-when-far-away and is not a zone herd). Capped near the player. Same vertical layer as the player — underground mining does not fill the surface.
- Wildlife HP labels (`wildlife_label` TextDisplays). Non-persistent. Removed on entity remove (not only death), on chunk load orphan sweep, and on a worldwide periodic sweep — never only within 48 blocks of a player.

## Area idle (cleanup, not nerf)

When **no real players** are within a zone radius (+ buffer):

- **Mob zones** (Borderlands / Eldervale / generic): do not seed; `despawnTagged` removes persistent tagged hostiles and their HP labels. Seed targets (`SURFACE_MIN`/`MAX`, crypt, Eldervale densites) are unchanged when players return.
- **Animal zones**: same idle despawn; `TARGET` unchanged while players are present.
- **Pet habitats**: stop marker ensure ticks (avoids unload→respawn stacks). Habitat caps unchanged.
- **Forage Isle guide hologram**: stop ensure ticks when nobody is near the isle; never spawn while the guide chunk is unloaded.

## Why Capital held and Mine / Forage did not

Capital sits in chunks that stay loaded, and players stand on one layer, so the ambient cap and marker lookup both hit.

Mine and Forage Isle (and Eldervale’s wide disk) unload the marker chunk while people are still in the area. Respawn timers treated “unloaded” as “missing” and stacked persistent nametags. Ambient animals defaulted to persistent, so remove-when-far-away never fired. HP labels orphaned on despawn-without-death and only got cleaned near players, so far loaded chunks piled TextDisplays.

Wiping a region does not fix that. The spawn / teardown path does.
