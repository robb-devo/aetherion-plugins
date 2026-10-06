# AETHERION — Progression Implementation Session Pack

Date: 2026-10-03  
For: new Opus / agent session (background while Robb playtests)  
Worktree: `C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`  
Branch: `ship/live-hardening` (dirty — other live work exists; stay in progression files only)

---

## What this session is

Implement the **already-written progression design**, phase by phase.  
Do **not** re-scout the monorepo. Do **not** invent a new curve.

Robb continues playtesting polish (TalkUx, Ankerhold, Farmer/Harlow, etc.).  
Progression work runs **in parallel in the repo** — **no jar deploy / no Crafty restart** unless Robb explicitly says so.

---

## Folders / access you need

Grant / open this worktree root (not only `IdeaProjects` main):

```text
C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c
```

Must be able to read **and write**:

| Path | Why |
|------|-----|
| `docs/PROGRESSION_DESIGN_OPUS.md` | **Source of truth** for the design |
| `docs/AETHERION_PROGRESSION_SCOUT.md` | Context only — prefer Design where they disagree |
| `docs/_scout_raw/*` | Optional raw dumps |
| `AetherionItems/**` | Recipes, economy, boosters, BalanceTargets, ActiveEquipmentStats |
| `BossEngine/**` | Boss YAML loot, hooks, telemetry |
| `AetherionDungeons/**` | Relic drops, overworld mult, floor labels |
| `.cursor/rules/` or `C:\Users\Robbi\IdeaProjects\.cursor\rules\` | LOCKED + additive-ship rules |

Previous cloud Opus could not write under `.claude/…`. **This session must use the worktree above** (local / Desktop with folder attached).

Do **not** need for P0: Hub map build, Ankerhold cinematic, Foraging chop polish.

---

## Read first (in order)

1. **This file** (`docs/PROGRESSION_IMPL_SESSION.md`)
2. `docs/PROGRESSION_DESIGN_OPUS.md` — full design
3. `docs/AETHERION_PROGRESSION_SCOUT.md` — context only
4. Locked rules:
   - `locked-systems.mdc` (boosters, anvil, ranks, Blossom/Gravwell, TalkUx, shutdown)
   - `additive-ship-never-overwrite.mdc`
   - Core stays thin (`BossEngine/.cursor/rules/aetherion-plugins.mdc` if present)

---

## Robb decisions (LOCKED IN — do not re-open)

| ID | Decision |
|----|----------|
| **D1** | Accept boosters as the main mid+ damage spine. **Do NOT** retune booster values / sockets / stacks. Re-document `BalanceTargets` (L1). Curve levers = L2/L3/L4 + later HP from telemetry. |
| **D2** | Hollow Sun → (a) normalise one step above Helios + drop from `hollow_sun`. |
| **D3** | Taper account milestones after L250 (L4). |
| **D4** | Crit soft-cap at 50% (L2) — OK. |
| **D5** | `ashen_sheath` HP — leave for now (telemetry). |
| **D6** | Guild breadth gate — skip for now; ship Signatures/recipes first. |

---

## Phase order (STOP after each — wait for Robb “GO Pn”)

| Phase | Scope |
|-------|--------|
| **P0** | Data fixes (LOW): Colossus loot fix; `the_veil` id/desc; `NETHER_STAR` → `spectacle_star`; INFUSABLE extend (Worldhide/Helios/Seraphine); BossDeathEvent telemetry (`fight-log.csv`: fight seconds, party size, top DPS). Also: `AetherionItemHook` resolve `compressed_*` via `CompressedResource.fromId` (McNugget coal drop bug). |
| **P1** | Signature ids stamped via ItemFactoryAccess + `economy.yml` + Codex |
| **P2** | T4/T5 recipe deltas only (+ helpers); diving orphan recipe; recipe-book lore; economy recalc for changed results |
| **P3** | Dungeon bridge: restore T2/T3 relic drops; tiered overworld mult (F1 ×0.38, T2 ×0.60, T3 ×0.70); floor stage labels |
| **P4** | Curve levers L2+L3+L4 (arena-testable; **no** boss HP yet) |
| **P5** | Boss HP re-band **ONLY** after telemetry exists |
| **P6** | Pinnacle key + optional teaching quests |

**P0 status (2026-10-03):** Done and **applied into the worktree** via `_progression_p0_ship/apply-p0.ps1`. Notes: that folder’s `P0_NOTES.md`.

**Robb (later):** Continue **P1 → P6** in order. Take your time. Prefer writing under `_progression_pN_ship\` + apply script if `.claude\` remains unwritable; otherwise write the worktree directly. Still **no deploy/restart** unless Robb says so. After each phase: short report (files / playtest / risks), then continue to the next unless he says stop.

---

## Pre-flight before touching stats math (§3.4 / P4)

Worktree may have local `ActiveEquipmentStats` / `HealthListener` / `CustomItem` edits.  
Diff them vs `main` before P4. Safety-only diffs (provider snapshot / UNKNOWN PDC) do **not** invalidate booster math — keep them if present.

---

## Hard rules

- Implement from the design doc. Do not invent a new curve.
- One phase at a time with a short report after each — but **do not wait for GO between P1–P6** unless Robb says stop (he authorized continuous work 2026-10-03).
- **No jar deploy / no live Crafty restart** unless Robb says so.
- Additive ship: never thin DevMenu / factories / live give-lists.
- Honour LOCKED: boosters intent + Anvil GUI, Blossom/Gravwell combat feel, TalkUx shell, ranks/wipes, shutdown countdown, Borderlands vials.
- Do not commit / push unless Robb asks.
- Do not touch Ankerhold / OpeningOrbit / TalkUx timing / Farmer-Harlow polish (other track).
- If `.claude\` is unwritable: ship packs under `C:\Users\Robbi\IdeaProjects\_progression_pN_ship\` + `apply-pN.ps1` (same pattern as P0).

---

## Parallel tracks (ignore unless asked)

Robb’s playtest polish backlog (separate session): TalkUx uniform timing, Farmer→Harlow, recipe-book Combat/Mining layout, Borderlands-only hostiles, skill-isle guide NPCs, Tackle→fishing waypoint.  
**Not your job in this progression session.**

---

## Suggested first message reply shape

1. Confirm P0 already applied in worktree  
2. Start **P1** now, then P2→P6 without waiting  
3. After each phase: files / playtest / risks, then continue  
4. P5 still needs telemetry rows before HP re-band — if none yet, stub the phase plan and skip HP numbers until data exists
