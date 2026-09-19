# AetherionCore

Shared library plugin. Loads first. Does not tick the game.

**In here:** PDC keys used by more than one plugin, entity identity (`isBoss` / `isPet` / …), scripted/raw hit flags, soft-dep service interfaces under `de.aetherion.core.api` (`AetherServices`, `PartyAccess`, `BossSpawnAccess`, `ItemFactoryAccess`), the shared `VoidChunkGenerator`, FancyNpcs reflection glue (`FancyNpcFacade`), and `/wipe` (Crafty path layout in `config.yml`).

Shared keys today: boss id/minion, pet, set minion + owner, dungeon mob/npc, quest npc, true damage, no-set-save, item id, charm suppress.

**Not in here:** combat math, boss AI, loot, quests, world ticks, wildlife.

When two plugins copy the same key or the same “is this a pet?” check, move that copy here. Leave game logic where it lives.

Other plugins: `depend: [AetherionCore]` and Maven `de.aetherion:AetherionCore:1.0.0` (provided). Register typed services on `AetherServices` instead of adding new first-party reflection bridges. FancyNpcs stays external — call `FancyNpcFacade` instead of copying `Class.forName`.

## Wipe paths (`config.yml`)

`/wipe beta` and `NetworkWipeWatch` read `wipe.*`. Empty / omitted keys keep production behavior:

| Key | Default | Meaning |
|-----|---------|---------|
| `wipe.shared-root` | empty → derive `<crafty>/shared` | Shared transfer/progress + shared wipe flag. Production path is `/var/opt/minecraft/crafty/shared`. |
| `wipe.servers` | `[]` → every `crafty/servers/*` | Optional allowlist of Crafty folder names or absolute paths. |
| `wipe.dry-run` | `false` | Log intended flag writes / deletes; do not change files. Pending flags are left in place. |

Do not set `dry-run: true` on live backends unless you are probing paths.
