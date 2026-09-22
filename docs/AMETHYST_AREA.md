# Amethyst Area (aether_veins) — admin

Finished BreadBuilds hub stays **1:1**. Dig zones are painted around it; never delete the world folder to “reset”.

## Layout

| Piece | Behavior |
| --- | --- |
| Hub (center schematic) | Untouched by generators. Spawn protect ~20 blocks. |
| Four dig zones | NE Crystal, NW Lush, SE Forge, SW Cinder — tunnels + themed ores. |
| Ore regen | **None** while playing (mined = gone). |
| Daily reset | Re-paints dig zones from fixed `veins.dig-seed` (identical). Hub skipped. |
| Soft light | Transparent `LIGHT` blocks via Hub `SoftLightPass`. |

Config: `plugins/AetherionMining/config.yml` → `veins.*`

Defaults: spawn `8.5 / 18 / 8.5`, `spawn-protect-radius: 20`, `dig-hub-clearance: 64`, `reset-hours: 24`, `dig-seed: 20260922`.

Hub spawn `amethyst` (Dev Menu anchor / `/hubadmin set amethyst`) overrides `veins.spawn-*` when planted.

## First-time dig zones (hub safe)

On Mining enable, if `veins.yml` layout &lt; 5 or `dig-zones-ready` is false, dig zones paint once outside `dig-hub-clearance`. Hub blocks are never written.

Force re-paint (still skips hub):

```
/deepmines zones force
```

Check without force:

```
/deepmines zones
```

## Soft light (once or after reset)

Auto-runs after dig paint when `veins.softlight-on-paint: true`.

Manual:

```
/deepmines softlight [radius]
/hubadmin softlight veins
```

Safe to re-run — skips existing `LIGHT` blocks. Level defaults soft (10), not blinding.

## 24h reset / force restore

Scheduled by Mining (`veins.reset-hours`, default 24). Timer tick every few minutes.

Force now:

```
/deepmines reset
```

Restores dig zones from `plugins/AetherionMining/veins-dig-snapshot.bin.gz` (written on first/forced paint). Hub untouched. Players are pulled to hub spawn briefly. If the snapshot is missing, dig zones are re-painted from `veins.dig-seed` and a new snapshot is saved.

Time left:

```
/deepmines time
```

## Spawn protect

~20 blocks around hub spawn (plugin `VeinsListener` + WorldGuard region `veins_spawn_protect`). Creative + `aetherion.mines.admin` can still edit.

Outside protect: players may mine everything (including amethyst clusters/blocks) with normal drops. No seal/respawn in this world.

## Do not

- Do not run old Deep Veins world-delete rebuilds.
- Do not paste dig generation into the hub clearance.
- Do not touch WildlifeLooks / MobZone / pet entity lifecycle for this task.
