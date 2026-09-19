# Aetherion

Paper plugin suite for the Aetherion MMO: custom items/economy, bosses, quests, gather worlds, dungeons, and a Hub pit.

## Servers

Velocity proxy in front of Crafty Paper backends:

| Backend | Role |
|---------|------|
| **Hub** | Lobby + Pit (safe spawn, PvP outside the red line). Runs **AetherionPit**. |
| **mmo-r** | RPG / capital (Items, AetherionHub, Quests, gather, Guilds, …). |
| **mmo-d** | Dungeon hub + instances. |
| **mmo-c** | Extra dungeon/Ashes world (Multiverse mark). |

**Hub needs AetherionCore.** Phase 5 made Pit `depend` on Core (`FancyNpcFacade`). Drop `AetherionCore.jar` on Hub, not only mmo-r / mmo-d.

Crafty layout: `/var/opt/minecraft/crafty/` (`servers/<id>/` backends, `shared/` for transfer + wipe).

## Docs

- [SETUP.md](SETUP.md) — load order, soft-deps, smoke test, testbots
- [ARCHITECTURE.md](ARCHITECTURE.md) — who owns what
- [docs/OWNERSHIP.md](docs/OWNERSHIP.md) — gather vs Items
- [docs/DUPING_CHECKLIST.md](docs/DUPING_CHECKLIST.md) — economy / transfer checks
- [AetherionStressBots/README.md](AetherionStressBots/README.md) — QA/stress Mineflayer bots (Wave 1 + combat/fish/trade/quest/pad)

## Build

```bash
mvn -B -DskipTests package
```

Jars land in each module `target/*.jar`. Install order: see [SETUP.md](SETUP.md) (Core first).
