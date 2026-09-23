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

## Restart countdown

`aenet restart [seconds] [reason]` (permission `aetherion.core.admin`) broadcasts a chat countdown and then calls `Bukkit.shutdown()`. Default duration is **10 seconds**. Crafty stdin uses the bare command (no leading slash). In-game, `/aenet` hits the same executor.

Chat ticks every 2 seconds, and only on even remaining seconds. A 10 second restart shows `10`, `8`, `6`, `4`, `2`, then the backend stops. An odd duration still announces only the even remainders (15 → 14, 12, …, 2).

The reason is optional:

- `aenet restart 10` — countdown only. Nothing invents a patch name.
- `aenet restart 10 Patch Ashen-Katana-Restore` — one English line, then the same countdown: `Patch Ashen-Katana-Restore goes live — server will reset. Expected back in about 1 minute.`

Paper's disable hook is too late to wait 10 seconds, and a Crafty Stop / SIGTERM cannot be delayed inside the JVM. Send the command **before** the process is killed. Host wrapper (install on the box, do not commit it into Crafty itself):

```bash
sudo install -m 0755 ops/aetherion-restart-mmo /usr/local/bin/aetherion-restart-mmo
```

Optional `/etc/aetherion/restart.env` picks how the script reaches the console (`AETHERION_RCON_*`, `AETHERION_SCREEN`, `AETHERION_TMUX`, or `AETHERION_STDIN`). The script only sends the countdown. The plugin stops the backend when the countdown ends. Do not SIGTERM the process until that happens.

Same command works on every Velocity backend that has AetherionCore, not only mmo-r. The script name is the mmo-r wrapper; point `restart.env` at whichever server you are stopping.
