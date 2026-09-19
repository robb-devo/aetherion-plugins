# Aetherion — Server Setup

Laptop / Homie-Wochenende. Soft-deps optional, aber empfohlen.

## Load order (hard)

1. **AetherionCore** (`load: STARTUP`) — shared keys only
2. **AetherionItems** — economy, combat, recipes, party, skills
3. **BossEngine** — bosses / spawners
4. Everything else (any order after the three above)

Suggested folders drop: `plugins/AetherionCore.jar` first, then Items, BossEngine, then the rest.

## Soft depends (install when you use the feature)

| Plugin | Soft-deps |
|--------|-----------|
| AetherionItems | PlaceholderAPI, LuckPerms, WorldGuard, Hub, BossEngine, Quests, Dungeons, Guilds, AetherMobs |
| AetherionQuests | TAB, PlaceholderAPI, FancyNpcs, FancyHolograms, Vault, Mining, Foraging, BossEngine, Hub, AetherMobs |
| AetherionDungeons | Items, Hub, BossEngine, Quests, WorldEdit |
| AetherionGuilds | Items, Hub, PlaceholderAPI |
| AetherionMining / Farming / Foraging | WorldGuard (+ Items soft) |
| BossEngine | Items, AetherMobs, Quests, WorldGuard |
| AetherMobs | PlaceholderAPI |

## Smoke test (every deploy)

1. Start server — no red errors for Aetherion\* / BossEngine  
2. Join → check coins / quests / pets load  
3. Quit → stop server  
4. Start again → same progress still there  

## Build

From workspace root (parent reactor):

```bash
mvn -q clean install
```

Jars land in each module `target/*.jar`. Install **AetherionCore** before anything that compiles against it if you build a single module alone.
