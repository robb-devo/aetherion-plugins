# AetherionItems — package map

When adding code, put it in the matching package. Do not grow `item/CustomItem` further — extract domain factories (see `BoosterItems`, `FarmingItems`, …).

| Package | Owns |
|---------|------|
| `item` | Item factories (`CustomItem` facade + domain classes) |
| `economy` | Coins, shards, market, traders |
| `combat` | Hit flags facades, gear set bonuses |
| `recipe` | Recipe registry / unlocks / book |
| `progress` / `skill` | Account progression, skills, loadouts |
| `social` | Party |
| `shop` | Shard shop, XP booster consumables |
| `world` | Animal/mob zones, areas, world maps |
| `listener` | Bukkit listeners for Items-owned systems |
| `codex` | Bestiary / collections |
| `rank` | Rank badges / LuckPerms sync |

Cross-plugin: register on `de.aetherion.core.api.AetherServices` instead of new reflection bridges.
