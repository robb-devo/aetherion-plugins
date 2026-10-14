# Transfer gap scan — recipes (2026-10-07)

## Smoking gun

Inventory transfer looked clean, but **recipe unlock state never entered the v5/v6 blob**.

`NetworkPlayerDataSync` exported `recipe_unlocks.yml` under `players.<uuid>`.
`RecipeUnlockService` actually stores:

- `crafted.<uuid>`
- `obtained.<uuid>`
- `full-unlock` (UUID list)

Export found nothing → arrival JVM had empty unlock RAM → compression / predecessor gates failed even with mats in bags.

## Fix shipped (this pass)

1. Core + Dungeons `NetworkPlayerDataSync.YAML_KEYS`: `crafted` + `obtained` (removed dead `areas.yml` key).
2. `ProgressAccessImpl.reloadAfterImport`: after overlay, `discoverFor` + `syncOwnedRecipes` (fixes late apply after join-time discover).

## Closed in beta polish pass (2026-10-10)

| Gap | Fix |
|---|---|
| `full-unlock` membership | `member.full-unlock` flag merged into recipe yaml payload |
| `colosseum-unlock.yml` | `member.unlocked` / `member.taught` + `ColosseumGateService.applyTransferFlags` |
| Hub spawn unlocks | blob `hub` = `AetherionHub/players/<uuid>.yml` (+ flush/invalidate) |
| Mid-forge pending | `blueprint-forge-pending.yml` `pending.<uuid>` in YAML_KEYS |

## Still open (smaller)

| Gap | Notes |
|---|---|
| Isle profiles | forage/farm/fish/mine player yml local to backend |

Ranks stay synced (LOCKED) via `player-ranks.yml`.
