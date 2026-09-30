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
- Wildlife HP labels, fishing lures, boss FX

Zone herds (`zone_spawn`) stay with their anchor and are capped. They are not ambient leaks.

## Why Capital held and Mine / Forage did not

Capital sits in chunks that stay loaded, and players stand on one layer, so the ambient cap (4 nearby) and marker lookup both hit.

Mine and Forage Isle (and Eldervale’s wide disk) unload the marker chunk while people are still in the area. Respawn timers treated “unloaded” as “missing” and stacked persistent nametags (mob-zone stands, isle-guide hologram, crypt hologram). Ambient animals default to persistent, so remove-when-far-away never fired. Underground players did not count animals on the surface above them, so the nearby cap never stopped new spawns. Those herds came back within hours of a wipe because the spawn path was still running.

Wiping a region does not fix that. The spawn path does.
