# Aetherion — Architecture (1 page)

## Who owns what

| Plugin | Owns | Does **not** own |
|--------|------|------------------|
| **AetherionCore** | PDC keys, entity identity helpers, hit flags, cross-plugin service interfaces (`de.aetherion.core.api`) | Game ticks, combat math, loot, quests |
| **AetherionItems** | Custom items/recipes, economy (coins/shards/market), skills/loadouts, party, codex, combat stats | World regen, dungeon instances, boss AI |
| **BossEngine** | Boss templates (YAML), phases/skills, spawners | Player economy, quests |
| **AetherionQuests** | Quest state, NPCs, compass/markers, rewards hooks | Item definitions |
| **AetherionHub** | Unlockable spawns, `/spawn` `/hub` | Islands, guilds |
| **AetherionDungeons** | Temp instances, warm pool, dungeon flow | Boss AI (uses BossEngine), item defs (uses Items) |
| **AetherMobs** | Pets, catch, collection, Aetherlex | Boss templates |
| **AetherionGuilds** | Guilds, friends, personal islands, quarry minions | Hub spawns |
| **Mining / Farming / Foraging / Fishing** | World rules, zones, regen, minigames | Item stats / recipes (live in Items) |

## Cross-plugin rules

1. Shared “is this a pet/boss?” → Core keys / `AetherEntities`.  
2. Soft features → register on `AetherServices` (Core) or use typed APIs (`BossEngineAPI`, `AetherionHubAPI`). Prefer that over new reflection bridges.  
3. New listener under Items only if it is items/economy/combat/social — otherwise put it in the owning plugin.  
4. Content-as-data: BossEngine YAML first; avoid hardcoding new bosses in Java.

## Feature freeze guardrails

- **OK anytime:** balance numbers, boss/loot YAML, typos, small UX.  
- **Wait for structure gates:** new plugins, new economy systems, new reflection bridges, bloating `CustomItem`.
