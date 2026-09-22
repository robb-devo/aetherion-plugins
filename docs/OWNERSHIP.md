# Gather vs Items — Ownership

## Rule of thumb

- **Items plugin:** item IDs, stats, recipes, boosters, skill XP payouts, codex tracking.  
- **Gather plugins:** worlds, zones, regen, entry/exit, WorldGuard hooks, minigames, scare events.

## Table

| Concern | Owner | Notes |
|---------|-------|-------|
| Amethyst Area (`aether_veins`), dig zones, spawn protect, 24h dig snapshot reset | **AetherionMining** | Hub BreadBuilds untouched; see `docs/AMETHYST_AREA.md` |
| Soft light (`LIGHT` blocks) for hub / veins | **AetherionHub** `SoftLightPass` | `/hubadmin softlight` / `/deepmines softlight` |
| Ore seal / regen / WorldGuard mining allow | **AetherionMining** `MiningListener` | Sole class named `MiningListener`. Respawn table: `MiningRespawnTimes` via `MiningAccess` |
| Ore/log “is this a tracked block?” helpers used by Items combat/codex | **AetherionItems** `HarvestRules` (helpers) | `openMine` delegates to `AetherServices.mining()` |
| `openMine(world)` / veins world name | **AetherionMining** (source of truth) | `MiningAccess.isVeinsWorld` / `veinsWorldName` |
| Fortune / skill XP / break-speed / tool gates on harvest | **AetherionItems** `HarvestListener` | Exposed as `AetherServices.harvest()` (`HarvestAccess`) |
| Pickaxe/armor/hoe item definitions | **AetherionItems** | `CustomItem` / `FarmingItems` / mining gear factories |
| Crop regen, bird scare | **AetherionFarming** | `FarmingListener` + `BirdScareEvent` |
| Crop fortune / HARVEST_SPREAD / hoe XP | **AetherionItems** `CropHarvestListener` | Stats hooks only; does not replant |
| Millstone / Root Cellar stations | **AetherionItems** | Item recipes / GUIs, not crop regen |
| Foraging tree rules / WG | **AetherionForaging** | Wood fortune payout still `HarvestAccess.payWood` |
| Fishing minigame / lure | **AetherionFishing** | `FishingController` |
| Fishing loot pool / rod progress / catch XP | **AetherionItems** `FishingListener` + `FishingLootPool` | Items skips wait/lure when Fishing is loaded |

## Mining (done checklist)

- [x] Ownership documented here + Mining README  
- [x] Veins exit locations persisted  
- [x] Mining `onDisable` flushes veins metadata  
- [x] Items keeps item factories; Mining keeps world loop  
- [x] Two `MiningListener` classes resolved: Mining owns world/WG/regen; Items harvest renamed to `HarvestListener`  
- [x] Vacuum seal + respawn seconds go through `MiningAccess` (no Items reflection into Mining)  
- [ ] Ore Troll + Shabby Mine area guard stay in Items (blueprint drops / hub `AreaService`, not The Veins world)  
- [ ] Vein Siphon / Emerald Spread stay in Items (item abilities); they call Mining for seal/respawn  

## Farming / Fishing (this cut)

- [x] Crop regen already lives in AetherionFarming; Items `CropHarvestListener` documented as stats-only  
- [x] Fishing minigame stays in AetherionFishing; Items loot/rod-progress documented; wait-clamp runs only if Fishing is missing  

## Do not

- Add new gather world logic under `AetherionItems/.../listener`  
- Add new pickaxe stats under AetherionMining  
- Name a second `MiningListener` in Items  
