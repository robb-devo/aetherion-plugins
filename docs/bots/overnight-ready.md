# Tonight: ~2h StressBots fleet on MMO-R

Operator runbook for Peter. **This machine does not deploy.** Copy jars + runner onto **mmo-r**, then run the commands below. Target mix: **32 bots**, ~2 hours, then `/botreport`.

Bots join offline (`QaMine01`…), wear nicknames, and grind playable loops: mine, forage, farm, catch, fish, combat (Borderlands lite — **no dungeon instances**), AH/Bazaar, quests, roam, jump pads.

## Blockers for a 2h fleet tonight

### Hard stop (fleet will not start / will not stay up)

| Check | Fail look | Fix |
|-------|-----------|-----|
| **Jars + runner both updated** | Old Mineflayer still running; farm/AH polish missing | Deploy **three jars** *and* the `AetherionStressBots/runner/` tree. Jars do **not** contain the Node runner. |
| `AetherionCore.jar` + `AetherionItems.jar` + `AetherionStressBots.jar` | StressBots red in log (`depend:`) | Copy all three; restart Paper. |
| `testbots.enabled: true` | `/stressbots start` → `Testbots disabled` | Edit live YAML, then `/stressbots reload`. Jar default is **false**. Merge-from-jar does **not** flip this. |
| Runner `--listen` on `127.0.0.1:18765` | `Runner not reachable` / Dev menu `runner: down` | Start Node **on the mmo-r host**. |
| `velocitySecret` = Paper `proxies.velocity.secret` | Bots never appear; Velocity HMAC fail | Set in **gitignored** `runner/config.json`. Must not be `CHANGE_ME`. |
| Paper listening `127.0.0.1:25567` | Runner: timeout waiting for backend | Start mmo-r first, then runner. |
| `max-players` ≥ 40 (live was **60**) | Starts clamp / kick | `server.properties` `max-players`. Mix is 32 + you. |
| Control bind localhost only | Plugin cannot reach runner | `testbots.runner.host/port` = `127.0.0.1` / `18765`. Token empty unless both sides set the same value. |

Missing YAML keys (`roles.farm`, prefixes, fish/farm anchors) are **filled in-memory from the jar** and in-memory from `config.example.json` on the runner. You still must set **`enabled: true`** and the Velocity secret. Live death-pad coordinates are **not** auto-replaced — see “legacy anchors” below.

### Soft (fleet runs; some roles look idle)

| Issue | What you see | Optional tonight |
|-------|--------------|------------------|
| Farm island not pasted | `QaFarm*` wander/hoe at `-600 90 427` | `/aetherpaste farming` (needs FAWE + schem) |
| Fish island not pasted | Fish still works on grove/origin water | `/aetherpaste fishing` |
| Empty AH / Bazaar books | `/botreport` `ah buy fail (empty)` until list+collect | Trade bots list; give it ~10 min |
| AetherMobs down | Catch throws; pets may not follow | Catch still counts as activity |
| AetherionQuests down | Quest click / accept no-ops | Other 9 roles still run |
| Legacy death pads still in live YAML | void/stuck on catch `500.5,-200.5`, forage lip `479.5,-240.5`, roam Borderlands | Copy new anchors from this PR’s `config.yml` / example JSON |

**Not a blocker:** dungeon instances (intentionally out). Monkey / Amethyst / Foraging-entity (out of scope).

## 1. Build (laptop / CI)

From repo root (Java 21). GitHub **Compile** on this PR already packages the same reactor:

```bash
mvn -pl AetherionCore,AetherionItems,AetherionStressBots -am package -DskipTests
```

Jars:

- `AetherionCore/target/AetherionCore-1.0.0.jar`
- `AetherionItems/target/AetherionItems-1.0.0.jar`
- `AetherionStressBots/target/AetherionStressBots-1.0.0.jar`

Runner tests (optional): `cd AetherionStressBots/runner && npm install && npm test`

## 2. Deploy onto mmo-r (host)

Set these once per shell. Crafty layout is typically `…/crafty/servers/<mmo-r-id>/`.

```bash
REPO=…/aetherion-plugins          # checkout of this PR
PLUGINS=…/plugins                 # mmo-r plugins folder
RUNNER=/opt/aetherion-stress-bots/runner   # or $REPO/AetherionStressBots/runner
```

**Jars** (match the names already in `plugins/`; restart Paper after):

```bash
ls "$PLUGINS"/Aetherion{Core,Items,StressBots}*.jar
cp "$REPO"/AetherionCore/target/AetherionCore-1.0.0.jar "$PLUGINS/AetherionCore.jar"
cp "$REPO"/AetherionItems/target/AetherionItems-1.0.0.jar "$PLUGINS/AetherionItems.jar"
cp "$REPO"/AetherionStressBots/target/AetherionStressBots-1.0.0.jar "$PLUGINS/AetherionStressBots.jar"
```

**Runner** (keep the live `config.json` with the Velocity secret):

```bash
# first time only, if the dir does not exist:
# mkdir -p "$RUNNER" && cp -a "$REPO"/AetherionStressBots/runner/. "$RUNNER"/
# cp "$RUNNER"/config.example.json "$RUNNER"/config.json
# $EDITOR "$RUNNER"/config.json   # velocitySecret

rsync -a --exclude config.json --exclude node_modules \
  "$REPO"/AetherionStressBots/runner/ "$RUNNER"/
cd "$RUNNER"
cp -n config.example.json config.json
# velocitySecret must equal Paper proxies.velocity.secret
grep -n velocitySecret config.json
npm install
```

**Plugin YAML** — jar defaults do not overwrite the file. Set enable (required). Restart or `/stressbots reload` after:

```yaml
testbots:
  enabled: true
```

In `plugins/AetherionStressBots/config.yml`. Confirm Paper `server.properties`:

```
max-players=60
```

Optional islands (farm/fish loops, not required for the other 8 roles):

```
/aetherpaste farming
/aetherpaste fishing
```

Restart **mmo-r** so the new jars load.

## 3. Start the runner (mmo-r host, after Paper is up)

Stop any old listener first:

```bash
ss -ltnp | grep 18765 || true
# kill the old node pid if one is bound
cd "$RUNNER"
npm start -- --listen
```

Expect: `control listening on http://127.0.0.1:18765` and `backend 127.0.0.1:25567 is up`.

Health:

```bash
curl -sS http://127.0.0.1:18765/health
# {"ok":true,"message":"fleet 0 bots"}
```

Leave this process running for the whole 2h. Do **not** also pass `--mine 5 …` on the same process if you will drive counts from the plugin — `--listen` stays at 0 until `/stressbots start`.

## 4. Start the 32-bot mix (in-game, op / `aetherion.dev`)

Sanity:

```
/stressbots reload
/stressbots list
```

`enabled=true`. Dev menu → page 2 → **Testbots** header: **runner: up**.

Exact start (32 total; farm cap 6, others 8, `max-total` 40):

```
/stressbots start mine 5
/stressbots start forage 4
/stressbots start farm 3
/stressbots start catch 3
/stressbots start fish 3
/stressbots start combat 4
/stressbots start trade 4
/stressbots start quest 3
/stressbots start roam 2
/stressbots start pad 1
```

Each line should answer `Starting N <role> bots (qa…01..)`. If a role refuses, fix that row (cap / runner / enabled) and re-run **that** line only.

Stop one role / restart it:

```
/stressbots stop farm
/stressbots start farm 3
```

## 5. During the ~2h

```
/botreport
```

Every 30–60 min (chat + written book). Same text is the knackpoint dump. Watch Dev-menu Testbots header (TPS, deaths, stuck, wallets).

If a role sits on `stuck`/`void`: `/stressbots stop <role>` then `/stressbots start <role> N`. Trade restocks mats/coins ~every 90s; catch restocks spheres; farm re-equips a hoe.

## 6. Stop

```
/stressbots stopall
```

or Dev menu **Stop all**. Confirm `/stressbots list` shows 0 online.

Then on the host: `Ctrl+C` the runner (or `kill` the node pid on `:18765`).

If stopall says runner unreachable, kick still happens next tick; **kill Node** or bots will reconnect.

## 7. Read `/botreport`

| Block | Knackpoint |
|-------|------------|
| `tps=` | Sustained **< 18** |
| `Activity mix` | Must not be all `idle`/`stuck` |
| `Deaths` / `stuck` | Combat deaths on Borderlands are expected; void/stuck on mine/forage/farm/pad is not |
| `Economy sources/sinks` | Starter grants vs AH/Bazaar buy-confirm. Empty AH → `ah buy fail (empty)` until listings exist |
| Per-bot `recent:` | `dig` / `harvest` / `ah list` / `quest accept` / `pad land` = loop alive |

Activities: idle, pathing, mining, foraging, **farming**, catching, roaming, combat, fishing, ah, bazaar, quest_dialog, minigame, pad_hop, void, recovering, stuck.

## Role cheat-sheet

| Command | Names | Where |
|---------|-------|--------|
| `/stressbots start mine 5` | `QaMine01…` | Eldervale interior |
| `/stressbots start forage 4` | `QaForage01…` | Forage Isle grove |
| `/stressbots start farm 3` | `QaFarm01…` | Farm island `-600 90 427` |
| `/stressbots start catch 3` | `QaCatch01…` | Habitat pads + spheres |
| `/stressbots start fish 3` | `QaFish01…` | Grove / origin / fish island `-585 90 -649` |
| `/stressbots start combat 4` | `QaCombat01…` | Borderlands `220.5 58 160.5` (no dungeons) |
| `/stressbots start trade 4` | `QaTrade01…` | Origin pads, `/ah` `/bazaar` |
| `/stressbots start quest 3` | `QaQuest01…` | Maren / Twig / Forgehand / Millstone |
| `/stressbots start roam 2` | `QaRoam01…` | Origin slime pads (plugin pad-hop) |
| `/stressbots start pad 1` | `QaPad01…` | Real jump-pad centers |

Also: `/stressbots stop <role\|all>`, `/stressbots stopall`, `/stressbots list`, `/stressbots setup`, `/stressbots reload`, `/botreport`.
