# AetherionCore

Shared library plugin. Loads first. Does not tick the game.

**In here:** PDC keys used by more than one plugin, entity identity (`isBoss` / `isPet` / …), scripted/raw hit flags, and soft-dep service interfaces under `de.aetherion.core.api` (`AetherServices`, `PartyAccess`, `BossSpawnAccess`, `ItemFactoryAccess`).

Shared keys today: boss id/minion, pet, set minion + owner, dungeon mob/npc, quest npc, true damage, no-set-save, item id, charm suppress.

**Not in here:** combat math, boss AI, loot, quests, world ticks, wildlife.

When two plugins copy the same key or the same “is this a pet?” check, move that copy here. Leave game logic where it lives.

Other plugins: `depend: [AetherionCore]` and Maven `de.aetherion:AetherionCore:1.0.0` (provided). Register typed services on `AetherServices` instead of adding new reflection bridges.
