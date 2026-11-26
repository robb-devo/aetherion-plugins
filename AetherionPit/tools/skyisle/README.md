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

## Round 2 (same night, after Robb's feedback)

Smaller island around three free-to-use builds by SkyblockSquad from Planet Minecraft ("free to use anywhere"):
Elven Shrine, Elven Tower, Elven House (downloaded from the authors' Google Drive mirrors linked on the PMC pages).
Only two portals (Aetherion at the end of the shrine bridge, The Pit at the east edge, both normal size).
Discord = the bell, Modpack = the spell book at the tower, Pit-Shop = barrels etc. in the elven house.
Floating displays (crystals, sword, book, lanterns) are animated by AetherionPit (`floaters`).

- `gen_hub2.py <outdir> <dir with shrine.bin tower.bin house.bin>` builds `hub_base`, one template per build
  (kept at the schematic's DataVersion so vanilla upgrades states and block entities), `hub_overlay`, and
  `hub2.json` with the `place template` commands (tower `counterclockwise_90`, house `180`).
- `patch_hub2.py <hub dir> <hub2.json>` patches AetherionPit (spawn, safe zone, boards, portals, props, duck,
  floaters) and the WorldGuard `hub_safe` box.

## Round 3: the polish pass (2026-11-26 afternoon)

Same island and builds, everything around them redone after a real in-game walkthrough (local hub clone + client).
Builds: "Elven Shrine", "Elven Tower" and "Elven House" by SkyblockSquad (Planet Minecraft, "free to use anywhere").

- `gen_hub3.py <outdir> <dir with the three .bin schematics>` (writes `hub3.json`): plaza with benches, bell arch
  (Discord), moon gate for Aetherion on the shrine bridge, pointed Pit gate with soul-fire braziers, shop counter,
  the Sky Path jump and run around the tower, seven duckling spots, the Pit arena with its entrance tunnel, light
  blocks for every lamp. Trees are vanilla features: the builder runs the `features` commands after the templates.
- `patch_hub3.py <hub dir> <hub3.json>`: AetherionPit keys (spawn, safe zone, boards, portals, props, duck, floaters,
  decor, parkour, ducklings) and the WorldGuard `hub_safe` box.
- Props in the served pack: `AetherionItems/tools/gen_hub_props.py` (scroll parts, lamp post, signpost, duckling,
  crystal) on `minecraft:disc_fragment_5` CustomModelData 8101-8109, mirrored in `HubModels`.
- AetherionPit: `HubScrolls` (unrolling scrolls instead of text boxes), `HubDecor` (lamps, signpost, rowing boat),
  `HubParkour` (Sky Path: timer, checkpoints, best times in parkour.yml), `HubSecrets` (ducklings, ducklings.yml),
  books for the bell and the spell book, `/pit hubtest <player> <discord|modpack>` for console tests.
