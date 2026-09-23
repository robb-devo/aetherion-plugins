# AetherionCore

Shared library plugin. Loads first. Does not tick the game.

**In here:** PDC keys used by more than one plugin, entity identity (`isBoss` / `isPet` / …), scripted/raw hit flags, soft-dep service interfaces under `de.aetherion.core.api` (`AetherServices`, `PartyAccess`, `BossSpawnAccess`, `ItemFactoryAccess`, `PlaytimeAccess`), the shared `VoidChunkGenerator`, FancyNpcs reflection glue (`FancyNpcFacade`), `/wipe` (Crafty path layout in `config.yml`), and wipe-safe `/playtime`.

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

## Playtime

`/playtime` is total online time. It survives world/map resets and `/wipe beta`. The XP-phial / booster timer in AetherionItems is a different counter; `/fullplaytimereset` does not touch it.

### Drop the jar

Build from the repo root:

```bash
mvn -B -DskipTests package
```

Copy `AetherionCore/target/AetherionCore-1.0.0.jar` over `plugins/AetherionCore.jar` on **every** Paper backend (Hub, mmo-r, mmo-d, and any other backend that runs Core). Restart those backends. There is no second plugin to install.

On a Crafty host the file lands in `<crafty>/shared/playtime/<uuid>.yml` when `…/crafty/shared` already exists or `wipe.shared-root` is set. Every backend then shares one total. A server without that folder uses `plugins/AetherionCore/playtime/` instead (still outside the world folders, still kept by `/wipe beta`).

Optional keys in `plugins/AetherionCore/config.yml` (missing keys use the defaults):

```yaml
playtime:
  directory: ""          # absolute path, or empty for the rule above
  flush-seconds: 60      # join/quit also flush; minimum 5
```

Online time starts at join, is written at least every `flush-seconds`, and is written again on quit and on plugin disable. Joining a backend reads that file again, so Hub and the MMO servers add to one total as long as they share the directory. Offline players keep the last saved total. A crash can drop at most one flush interval. The player should be online on one backend at a time (Velocity already does this).

### Permissions

| Permission | Default | Command |
|------------|---------|---------|
| *(none)* | everyone | `/playtime` — your own total |
| `aetherion.playtime.others` | op | `/playtime <player>` |
| `aetherion.playtime.reset.self` | op | `/fullplaytimereset` — yourself |
| `aetherion.playtime.reset.others` | op | `/fullplaytimereset <player>` |

Messages follow the client locale: German (`de*`) or English. With no argument, `/fullplaytimereset` resets the player who ran it. Another player requires `aetherion.playtime.reset.others`.

LuckPerms examples:

```
lp group admin permission set aetherion.playtime.others true
lp group admin permission set aetherion.playtime.reset.self true
lp group admin permission set aetherion.playtime.reset.others true
```

### Read API

Other plugins (and a later launcher) can read seconds without HTTP:

```java
PlaytimeAccess playtime = AetherServices.playtime();
long seconds = playtime == null ? 0L : playtime.seconds(uuid);
```

`reset(uuid)` is the same full reset as `/fullplaytimereset`. `name(uuid)` is the last name seen with that total.
