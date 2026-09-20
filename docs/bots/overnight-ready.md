# Overnight-ready StressBots (Peter)

Code-only pack for a **2–3 hour** live load + economy run. No deploy from this laptop — copy the jars/config onto **mmo-r**, then start bots there.

Bots join offline like players (`QaMine01`…), wear quirky nicknames, and grind currently playable loops: mine, forage, **farm**, catch, fish, combat (Borderlands lite — **no dungeon instances**), AH/Bazaar, quests, roam, jump pads.

## Before you start

1. Build/install `AetherionCore`, `AetherionItems`, `AetherionStressBots` (this PR).
2. On **mmo-r**, set live `plugins/AetherionStressBots/config.yml`:
   - `testbots.enabled: true`
   - Copy new keys (`roles.farm`, `prefixes.farm`, `caps.farm`, fish/farm anchors). Jar defaults **do not** overwrite an existing file.
3. Runner:
   ```bash
   cd AetherionStressBots/runner   # or /opt/aetherion-stress-bots/runner
   cp config.example.json config.json   # first time only
   # velocitySecret = proxies.velocity.secret  (local only, gitignored)
   npm install
   npm start -- --listen
   ```
4. Confirm Dev menu → Testbots header shows **runner: up**. If down: runner is not listening on `127.0.0.1:18765`.

## Recommended 2–3h mix (~32 bots, cap 40)

Dev menu **or** commands (op / `aetherion.dev`):

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

Counts clamp to `caps.*` (farm 6, others 8) and `max-total: 40`. Leave a few slots for yourself.

CLI equivalent (if you skip Dev menu):

```
node src/index.js --mine 5 --forage 4 --farm 3 --catch 3 --fish 3 --combat 4 --trade 4 --quest 3 --roam 2 --pad 1
```

## During the run

- Watch TPS on Dev menu Testbots header (also deaths / stuck / wallet net).
- Every 30–60 min: `/botreport` (chat + written book). Same text is the knackpoint dump.
- If a role sits on `stuck`/`void`: Stop that role, `/stressbots start <role> N` again. Copy live anchors if they still have the old death pads (see plugin README).
- Trade bots restock coal/cobble/logs/wheat and top up coins if the purse drops below 400. Catch restocks spheres. Farm re-equips a hoe.

## Stop

```
/stressbots stopall
```

or Dev menu **Stop all**. Then Ctrl+C the runner if you are done for the night.

## Read `/botreport`

| Block | What to look for |
|-------|------------------|
| `tps=` | Sustained < 18 is a knackpoint |
| `Activity mix` | Should not be all `idle`/`stuck` |
| `Deaths` / `stuck` | Combat deaths on Borderlands are expected; void/stuck on mine/forage/farm/pad is not |
| `Economy sources/sinks` | Starter grants vs AH/Bazaar buy-confirm. Empty AH books → `ah buy fail (empty)` until someone lists |
| Per-bot `recent:` | `dig` / `harvest` / `ah list` / `quest accept` / `pad land` means the loop is alive |

Activities: idle, pathing, mining, foraging, **farming**, catching, roaming, combat, fishing, ah, bazaar, quest_dialog, minigame, pad_hop, void, recovering, stuck.

## What is in / out of scope

**In:** movement that looks like a player (no void walks, less spin), AH list/buy/collect + Bazaar sell/buy, skills XP from real work, pets, quest accept, jump pads, farm harvest on Eldervale farm island (`-600 90 427`), combat on the Borderlands pad.

**Out:** Monkey/Amethyst/Foraging-entity work, dungeon instance entry, live deploy from this machine, `/skills setlevel` cheats.

## If farm/fish islands are empty

Farm/fish bots wander the paste origin. Paste schematics first (`/aetherpaste farming` / `fishing`) if those islands are not on mmo-r yet. Mine/forage/catch/trade/quest/pad still run on the older pads.
