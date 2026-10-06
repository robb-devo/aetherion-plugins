# P5: Boss HP re-band (plan; no numbers yet)

Status, 2026-10-03: **no fight-log telemetry exists yet.** The P0 logger ships with the next BossEngine jar, and nothing has been deployed. Per Robb's rule, **no HP numbers change in this phase.** This file is the procedure, plus a tool that turns the log into suggestions once there is a week of data.

Design source: `docs/PROGRESSION_DESIGN_OPUS.md` §3.3 (targets), §3.4 L5/L5b, §5 (boss table).

## 1. What the log records

`plugins/BossEngine/fight-log.csv` gets one row per paid boss death. The writer is BossEngine `telemetry/BossFightLog`, added in P0 and extended in P5. Sandbox / Test Arena kills and non-finale script acts are not logged.

| Column | Meaning |
|---|---|
| `fight_seconds` | first player hit → death |
| `party_size` / `party_5pct` | players with any damage / with ≥5% share |
| `top_share`, `top_dps`, `party_dps`, `total_damage` | damage spread |
| `boss_max_hp` | **live** HP (after raid scale) |
| `base_hp` (P5) | the YAML `max-health` |
| `raid_players` (P5) | players counted near the boss at spawn |

When the header changes, the writer moves the old file aside to `fight-log.<n>.csv` so columns never mix.

## 2. A correction to the design (important for L5b)

Design C-note: "BossEngine YAML bosses have no per-player HP scaling." **That's not quite true.**

- `BossManager.applyWorldRaidScale` → `BossInstance.applyRaidScale` gives every **open-world** YAML boss **+35% HP and +10% damage per extra player within the leash radius at spawn** (up to +8 players).
- It is skipped for dungeon worlds and `dungeon_sentinel`.

So L5b (add `scaling.health-per-extra-player`) is mostly there already: the rate is global (0.35) and counted once, at spawn. Decide after the data:

- (a) keep 0.35 globally;
- (b) make it per-template in YAML, defaulting to 0.35 so nothing changes;
- (c) re-count at first hit instead of at spawn. Players often arrive after the spawn, so the boss scales for fewer people than actually fight.

The `raid_players` vs `party_size` columns will show how often (c) matters.

## 3. Procedure (after ~1 week of live data)

1. Copy `plugins/BossEngine/fight-log.csv` from the server.
2. `python docs/tools/fight_log_report.py fight-log.csv --md p5-report.md --min-fights 5`
3. For each boss, the tool takes the median per-player DPS, multiplies it by the band's target party and target TTK, then removes the raid scale to get a suggested YAML base HP. Observed TTK already includes uptime.
4. Bands and targets (design §3.3):

   | Band | Target TTK | Party | Home bosses |
   |---|---|---|---|
   | Entry | 40–75 s | 1 | McNugget, Lurker, Troll, Skuldugery, Ashen Sheath (D5) — **keep, report only** |
   | Mid | 60–120 s | 1 | Squidward, Colossus, Pathwarden, F1 Sentinel, Unbroken |
   | High | 2–3 min | 2 | World Eater, Helios Herald/Requiem, T2 hunts, F2 Frostbound |
   | Spectacle | 3–5 min | 3 | Seraphine, Hollow Sun, Sparky, Baron, Insolvent, Balthazar, Lobby Cleaner, Eggquelizer, F3 Aetherion |
   | Pinnacle | 5–8 min | 4 | `aetherion` |

5. Sanity-check against the design's model guesses, which are not targets: Mid low end ×2, High ×3, Spectacle ×5, Pinnacle ×8. If the data says much more or much less, trust the data, but check that the fights came from kits of the matching stage. P4 levers must be live first, or the End-kit DPS will overstate the needed HP.
6. Apply in YAML only. **Remember the live copies:** `plugins/BossEngine/bosses/*.yml` are only extracted when missing (`TemplateManager`). Copy the edited files to the server, or delete them so they re-extract.
7. Re-run the report after another week. Expect one more pass at most.

## 4. Order of operations

P0 telemetry → P4 levers live (crit cap, Grudge, milestone) → collect one week → run the tool → HP pass → second week → small correction. Changing HP before P4 is live would tune against the End blow-up that P4 removes.

## 5. Not in P5

- Mob HP inside dungeons (different system; `scaleHealth` per party).
- Helios encounter (`helios.yml` has its own `health-per-extra-player: 0.45`).
- D5 (`ashen_sheath` 5.2k vs a T5-class drop): the report shows its Entry-band numbers so Robb can decide.
