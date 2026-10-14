# AetherionCore

Shared library plugin. Loads first. Does not tick the game.

**In here:** PDC keys used by more than one plugin, entity identity (`isBoss` / `isPet` / …), scripted/raw hit flags, soft-dep service interfaces under `de.aetherion.core.api` (`AetherServices`, `PartyAccess`, `BossSpawnAccess`, `ItemFactoryAccess`), the shared `VoidChunkGenerator`, FancyNpcs reflection glue (`FancyNpcFacade`), and `/wipe` (Crafty path layout in `config.yml`).

Shared keys today: boss id/minion, pet, set minion + owner, dungeon mob/npc, quest npc, true damage, no-set-save, item id, charm suppress.

**Not in here:** combat math, boss AI, loot, quests, world ticks, wildlife.

When two plugins copy the same key or the same “is this a pet?” check, move that copy here. Leave game logic where it lives.

Other plugins: `depend: [AetherionCore]` and Maven `de.aetherion:AetherionCore:1.0.0` (provided). **AetherionPit** (Hub backend) also hard-depends on Core for `FancyNpcFacade` — drop Core.jar on Hub, not only mmo-r / mmo-d. Register typed services on `AetherServices` instead of adding new first-party reflection bridges. FancyNpcs stays external — call `FancyNpcFacade` instead of copying `Class.forName`.

## Wipe paths (`config.yml`)

`/wipe beta` and `NetworkWipeWatch` read `wipe.*`. Empty / omitted keys keep production behavior:

| Key | Default | Meaning |
|-----|---------|---------|
| `wipe.shared-root` | empty → derive `<crafty>/shared` | Shared transfer/progress + shared wipe flag. Production path is `/var/opt/minecraft/crafty/shared`. |
| `wipe.servers` | `[]` → every `crafty/servers/*` | Optional allowlist of Crafty folder names or absolute paths. |
| `wipe.dry-run` | `false` | Log intended flag writes / deletes; do not change files. Pending flags are left in place. |

Do not set `dry-run: true` on live backends unless you are probing paths.

## Network (hub · mmo-r · mmo-d · mmo-c · mmo-i)

`ServerNames` holds the fixed roles (mmo-i = 25570 islands). `NetworkRouter.travel(player, server, route)` is the
one way across: a route file (`shared/transfer/routes/`) says what to finish on arrival, a `CharacterTransport`
moves the character (v6, one owner, ledger). AetherionDungeons registers its pipeline on mmo-r / mmo-d;
`CharacterHost` is Core's port of it for backends without the Dungeons jar (mmo-c, mmo-i — never the hub).

| Class | Does |
|-------|------|
| `CharacterHost` / `CharacterStore` / `CharacterLedger` / `CharacterGuard` | v6 pack → freeze → Connect → rollback; apply once on join; quit / shutdown pack; crash redirect; guest sessions |
| `NetworkRouter` + `RouteBook` | travel + "finish this on arrival" (replay a command, silent hub warp) |
| `MainCommandRouter` | on mmo-c / mmo-i: `/capital`, hub warps, `/bazaar` `/ah` → mmo-r; `/dhub` → mmo-d |
| `IslandLink` | where Guilds does not run: `/island` `/guild` `/friend` → mmo-i (+ one "your island awaits" nudge on mmo-r) |
| `IslandsHost` | mmo-i only: land arrivals on their island, respawn there, void Isle Dock as main world |
| `/aetransfer [player]` | admin: holder, pending snapshot, route, hold state |

Config: `network.*` in `config.yml` (all keys optional; defaults = the fixed roles).
