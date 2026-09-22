# Amethyst Area (aether_veins) — admin

Finished BreadBuilds hub stays **block-for-block 1:1**. Dig paint **never** writes schematic voxels.

## Critical paint rule

1. Any dig-volume column with non-air = hub column → **never queued** for dig fill/carve.
2. Dig jobs = air cells in exterior columns only (hub/frozen never iterated).
3. Log must show `schematicOverwrites=0` (not “attempt then block”).
4. Auto softlight off.

`loosenPriorDigFill` / clearance-gap / flood-into-rooms logic is **gone**.

## Layout

| Piece | Behavior |
| --- | --- |
| Hub schematic | Untouched. Frozen columns + frozen non-air. |
| Dig volume | Solid fill outside footprint; four themed quadrants. |
| Softlight | **Off** by default (`softlight-on-paint: false`) — LIGHT would alter schematic air. |

## Rules

| Action | Rule |
| --- | --- |
| Break | OK except ~20 spawn protect. |
| Place | Denied everywhere in `aether_veins`. |
| NPCs | None — Foreman never restored into Amethyst. |
| Vanilla mobs | Off. |

## After restoring a clean Zip world

1. Deploy new `AetherionMining.jar`.
2. Delete `plugins/AetherionMining/veins-dig-snapshot.bin.gz` if present (old snapshots may be corrupt).
3. Run:

```
/deepmines zones force
```

Confirm log: `schematicOverwrites=0`.

## Soft light (optional, dig-only care)

Do **not** softlight the hub. Prefer off. Manual dig softlight only if operators stay outside schematic columns.

## 24h reset

```
/deepmines reset
/deepmines time
```

Snapshot is dig-only (hub excluded).

## Do not

- Do not delete `aether_veins` to “fix” digs.
- Do not restore Deep Veins prototype.
- Do not touch WildlifeLooks / MobZone idle.
