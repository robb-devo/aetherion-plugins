# Gather vs Items — Ownership

## Rule of thumb

- **Items plugin:** item IDs, stats, recipes, boosters, skill XP payouts, codex tracking.  
- **Gather plugins:** worlds, zones, regen, entry/exit, WorldGuard hooks, minigames, scare events.

## Table

| Concern | Owner | Notes |
|---------|-------|-------|
| The Veins world, `/mines`, reset timer, exit locations | **AetherionMining** | Persist exits; flush on disable |
| Ore/log “is this a tracked block?” helpers used by Items combat/codex | **AetherionItems** `HarvestRules` (helpers) | May call Mining for world name |
| `openMine(world)` / veins world name | **AetherionMining** (source of truth) | Items `HarvestRules.openMine` delegates to Mining config |
| Pickaxe/armor/hoe item definitions | **AetherionItems** | `CustomItem` / `FarmingItems` / mining gear factories |
| Crop regen, bird scare | **AetherionFarming** | |
| Foraging tree rules / WG | **AetherionForaging** | |
| Fishing minigame / lure | **AetherionFishing** | |
| Fortune / skill XP on harvest | **AetherionItems** skills/progress | Gather plugins fire events; Items awards |

## Mining pilot (done checklist)

- [x] Ownership documented here + Mining README  
- [x] Veins exit locations persisted  
- [x] Mining `onDisable` flushes veins metadata  
- [x] Items keeps item factories; Mining keeps world loop  
- [ ] Future: move remaining mine-only listeners out of Items when touched  

## Do not

- Add new gather world logic under `AetherionItems/.../listener`  
- Add new pickaxe stats under AetherionMining  
