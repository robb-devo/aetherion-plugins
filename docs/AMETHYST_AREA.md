# Amethyst Area (aether_veins) — admin

Finished BreadBuilds hub stays **1:1**. Dig volume is one solid cube flush against the schematic outer surface (no clearance air gap). Never delete the world folder to “reset”.

## Layout

| Piece | Behavior |
| --- | --- |
| Hub (center schematic) | Untouched. Skipped by non-air footprint + enclosed-air mask. |
| Dig volume | One solid cube (`dig-half-extent` × depth/height) with four themed quadrants inside. |
| Flush | Dig stone fills exterior air right up to schematic solids — zero air ring. |
| Ore regen | **None** while playing (mined = gone). |
| Daily reset | Restores dig snapshot (identical). Hub skipped. |
| Soft light | Transparent `LIGHT` via Hub `SoftLightPass`. |

## Rules

| Action | Rule |
| --- | --- |
| Break | Allowed everywhere **except** ~20 spawn protect (`VeinsListener` + WG `veins_spawn_protect`). |
| Place | **Denied everywhere** in `aether_veins` (plugin + WG `__global__` `BLOCK_PLACE=DENY`). Creative+admin exempt. |
| NPCs | **None** in Amethyst. Foreman never auto-restored into `aether_veins`; leftovers purged on enable (+ delayed sweeps). `/deepmines npc` admin-only elsewhere. |
| Vanilla mobs | Off world-wide (`DO_MOB_SPAWNING=false`, spawn flags off, WG `MOB_SPAWNING=DENY`, creature-spawn cancel). |
| Pets | Custom spawns OK in dark digs; blocked on open-sky island surface. |

Config: `plugins/AetherionMining/config.yml` → `veins.*`

Defaults: spawn `8.5 / 18 / 8.5`, `spawn-protect-radius: 20`, `dig-half-extent: 180`, `dig-depth: 48`, `dig-height: 28`, `reset-hours: 24`, `dig-seed: 20260922`.

## Re-paint (closes air gap / rebuilds dig flush)

```
/deepmines zones force
```

Batched fill — watch console progress. Schematic voxels are never overwritten.

Check / first paint:

```
/deepmines zones
```

## Soft light

Auto after paint when `veins.softlight-on-paint: true`.

```
/deepmines softlight [radius]
/hubadmin softlight veins
```

## 24h reset / force restore

```
/deepmines reset
/deepmines time
```

Restores `plugins/AetherionMining/veins-dig-snapshot.bin.gz`. Does **not** unload/delete `aether_veins`.

## Do not

- Do not delete `aether_veins` or restore Deep Veins prototype.
- Do not use old `dig-hub-clearance` air-gap thinking — footprint mask only.
- Do not touch WildlifeLooks / MobZone idle / entity lifecycle freeze.
