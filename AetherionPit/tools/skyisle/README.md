# Sky-isle hub (2026-11-26)

The hub world since 2026-11-26: a floating cherry-blossom island in a void world, star gates (end gateway
surfaces) instead of NPCs, a Pit arena island to the east, islets, clouds and a giant rubber duck.

- `gen_skyisle.py <outdir>` builds everything into `skyisle.nbt` (vanilla structure template), `skyisle.npz`
  (for the preview) and `skyisle.json` (corner + the coordinates AetherionPit needs).
- `render.py` renders a textured preview from the npz (vanilla textures from a 1.21.1 client jar).
- `patch_hub.py <hub dir> <skyisle.json>` patches only the named keys: AetherionPit (spawn, safe zone, boards,
  `npcs-enabled`, `portals`, `void-catch`, `duck`), AetherionHub (join/respawn at selected spawn off, island pads
  off), WorldGuard `hub`/`hub_safe` boxes, FancyNpcs `npcs: {}`.

Building the world: a Paper 1.21.1 server with the hub's void generator settings, structure copied to
`world/generated/minecraft/structures/`, `forceload add -103 -90 180 91`, `place template minecraft:skyisle -103 -7 -90`,
gamerules (no daylight cycle, time 11500, randomTickSpeed 0, no mob spawning, no fire tick), a few minutes for the
waterfalls and lava falls, then stop and pack the world folder.
