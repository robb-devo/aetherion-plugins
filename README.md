# Aetherion

Paper plugin suite for the Aetherion MMO — **full Java source** for every live game plugin.

**Repo (public):** https://github.com/robb-devo/aetherion-plugins  
Paste that link into another chat when you need plugin context; `main` is the current tree.

## Plugins (source roots)

| Module | Owns |
|--------|------|
| [AetherionCore](AetherionCore/) | Shared keys / identity only (no game loop) |
| [AetherionItems](AetherionItems/) | Items, economy, boosters, Codex/Skills, DevMenu, combat abilities, arenas |
| [BossEngine](BossEngine/) | Bosses (Helios, Seraphine/Saint, Hollow Sun, World Eater, …) |
| [Aethermobs](Aethermobs/) | Pets / wild mobs / catch |
| [AetherionQuests](AetherionQuests/) | Quests, NPC talk/cast, dialog |
| [AetherionHub](AetherionHub/) | Hub spawns, goto, spawn menu |
| [AetherionMining](AetherionMining/) | Mining isle / veins |
| [AetherionForaging](AetherionForaging/) | Foraging isle / groves |
| [AetherionFarming](AetherionFarming/) | Farming isle |
| [AetherionFishing](AetherionFishing/) | Fishing isle |
| [AetherionDungeons](AetherionDungeons/) | Dungeon instances |
| [AetherionGuilds](AetherionGuilds/) | Guilds |
| [AetherionPit](AetherionPit/) | Hub Pit PvP |
| [AetherionStressBots](AetherionStressBots/) | QA Mineflayer bots |
| [AetherionBeta](AetherionBeta/) | Beta helpers |

## Servers

Velocity proxy in front of Crafty Paper backends:

| Backend | Role |
|---------|------|
| **Hub** | Lobby + Pit (safe spawn, PvP outside the red line). Runs **AetherionPit**. |
| **mmo-r** | RPG / capital (Items, AetherionHub, Quests, gather, Guilds, …). |
| **mmo-d** | Dungeon hub + instances. |
| **mmo-c** | Extra dungeon/Ashes world (Multiverse mark). |

**Hub needs AetherionCore.** Pit `depend`s on Core (`FancyNpcFacade`). Drop `AetherionCore.jar` on Hub, not only mmo-r / mmo-d.

Crafty layout: `/var/opt/minecraft/crafty/` (`servers/<id>/` backends, `shared/` for transfer + wipe).

## Docs

- [SETUP.md](SETUP.md) — load order, soft-deps, smoke test, testbots
- [ARCHITECTURE.md](ARCHITECTURE.md) — who owns what
- [docs/OWNERSHIP.md](docs/OWNERSHIP.md) — gather vs Items
- [docs/DUPING_CHECKLIST.md](docs/DUPING_CHECKLIST.md) — economy / transfer checks
- [docs/FORAGING_ELDERVALE.md](docs/FORAGING_ELDERVALE.md) · [docs/MINING_ELDERVALE.md](docs/MINING_ELDERVALE.md) · [docs/CODEX_SKILLS_OVERHAUL.md](docs/CODEX_SKILLS_OVERHAUL.md)
- [docs/npc/NPC_DIALOGUE_OVERHAUL.md](docs/npc/NPC_DIALOGUE_OVERHAUL.md)
- [AetherionStressBots/README.md](AetherionStressBots/README.md) — QA/stress bots

## Build

```bash
mvn -B -DskipTests package
```

Jars land in each module `target/*.jar`. Install order: see [SETUP.md](SETUP.md) (Core first).

## Locked systems (do not casually change)

Boosters (stackable lore), Borderlands vials, Custom Anvil / 14 booster sockets, Rank/Admin UUID rules, Blossom Blade + Gravwell Cleaver combat, Shutdown countdown `10→8→6→4→2`. Details live in repo Cursor rules / code.
