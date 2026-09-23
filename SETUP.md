# Aetherion — Server Setup

Laptop / Homie-Wochenende. Soft-deps optional, aber empfohlen.

## Load order (hard)

1. **AetherionCore** (`load: STARTUP`) — shared keys, wipe paths, FancyNpcs facade
2. **AetherionItems** — economy, combat, recipes, party, skills
3. **BossEngine** — bosses / spawners
4. Everything else (any order after the three above)

Suggested folders drop: `plugins/AetherionCore.jar` first, then Items, BossEngine, then the rest.

Hub / Pit backends also need **AetherionCore** (Pit `depend`s on it for `FancyNpcFacade`).

## Wipe (Crafty)

AetherionCore `config.yml` → `wipe.*` (defaults = current production):

- `wipe.shared-root` empty = derive `…/crafty/servers/<id>` → `…/crafty/shared` (prod: `/var/opt/minecraft/crafty/shared`)
- `wipe.servers: []` = flag/peer-wipe every folder under `crafty/servers/`
- `wipe.dry-run: false` = real deletes. `true` only logs.

See `AetherionCore/README.md` and the comments in Core `config.yml`.

## Restart countdown (Crafty)

`aenet restart [seconds] [reason]` on the console (no leading slash). Default **10** seconds. Chat shows even seconds only (`10`, `8`, `6`, `4`, `2`).

- Manual: `aenet restart 10` — countdown only.
- With a reason: `aenet restart 10 Patch Ashen-Katana-Restore` — one English opening line, then the countdown.

The plugin calls `Bukkit.shutdown()` when the countdown ends. Send it before Crafty Stop / SIGTERM. Details and the host wrapper: `AetherionCore/README.md`.

## Soft depends (install when you use the feature)

| Plugin | Soft-deps |
|--------|-----------|
| AetherionItems | PlaceholderAPI, LuckPerms, WorldGuard, Hub, BossEngine, Quests, Dungeons, Guilds, AetherMobs, DiscordSRV |
| AetherionQuests | PlaceholderAPI, FancyNpcs, BossEngine, Hub, AetherMobs |
| AetherionDungeons | Items, BossEngine, Quests, WorldEdit |
| AetherionGuilds | Items, PlaceholderAPI |
| AetherionMining / Farming / Foraging | WorldGuard hard; Items soft. Farming also WorldEdit + AetherMobs. Foraging also WorldEdit + FancyNpcs. |
| BossEngine | Items, AetherMobs, Quests, WorldGuard (+ DeluxeHub / MythicMobs / WorldGuardExtraFlagsPlus for spawn-protect load order) |
| AetherionPit | WorldEdit/FAWE, FancyNpcs, PlaceholderAPI, DiscordSRV (hard-depend AetherionCore) |

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

## Test bots (Wave 1)

QA Mineflayer bots (mine / forage / catch / roam / combat / fish / trade / quest / pad) live in **AetherionStressBots**. They only **start from Dev menu / commands** when `plugins/AetherionStressBots/config.yml` has `testbots.enabled: true`.

1. On the host: `cd AetherionStressBots/runner && cp config.example.json config.json` — set `velocitySecret` locally, never commit it. After island-safety updates, merge `testbots.safety` + new role anchors into the live plugin YAML (jar defaults do not overwrite) and matching `anchors` into `runner/config.json`.
2. `npm start -- --listen` (HTTP control on `127.0.0.1:18765`).
3. In-game: `/dev` → page 2 → **Testbots**, or `/stressbots start mine 3` / `/botreport`.

Details, role limits, and later-wave gaps: [AetherionStressBots/README.md](AetherionStressBots/README.md).

