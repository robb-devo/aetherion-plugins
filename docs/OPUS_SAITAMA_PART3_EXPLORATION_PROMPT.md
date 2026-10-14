# SAITAMA Part 3 — Exploration layer (FRESH CHAT ONLY)

**Robb:** Paste into a **brand-new** Opus Extra/Max session. Do **not** continue the bloated Part 1+2 cloud chat — context tax killed that run.

Workspace:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Zips (if cloud can’t deep-read Java): `_opus_src.zip` + `_opus_map.zip` in workspace root — recreate if missing:

```powershell
cd C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c
Compress-Archive -Force -Path AetherionHub\src,AetherionQuests\src,AetherionItems\src,AetherionCore\src -DestinationPath _opus_src.zip
Copy-Item "$HOME\Downloads\OriginBuilds - Skyblock RPG Spawn.zip" _opus_map.zip
```

Deliver: **one `saitama-part3.patch`** (git apply clean). No `_ship/`. No deploy. Robb’s other agent applies.

---

```text
# AETHERION — SAITAMA PART 3 / RUN 2: EXPLORATION LAYER ONLY

Fresh Opus Extra/Max. Owner: **Robb**. Call him Robb.

Workspace:
C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c

## WHY THIS CHAT EXISTS

Parts 1+2 already shipped in a previous cloud session that burned quota on context bloat.
**You do NOT redo harbour wow, airship, NPC seed, errands, patrols, dialog polish, or crane.**
Those are DONE on the live tree (or applied via part1+part2 patches). Your job is the **missing half**: long-term exploration so the 1000² stays fun for countless hours.

If you waste this session re-tuning Tobin’s tune or moving the crane again, you failed.

Hygiene: docs/OPUS_PROMPT_HYGIENE.md. Edit source (or deliver apply-clean patch if `.claude` path blocks writes). NO deploy. TalkUx shell LOCKED. Ankerhold OUT. Result > limit — but **stay on Part 3 scope**. Self-grade: honest 110% on THIS pillar or keep going / hand off exact gaps.

---

## ALREADY DONE (DO NOT REBUILD — only extend if a Part-3 feature requires a hook)

### Part 1 (live / part1 patch)
- `OriginBay` — Halcyon airship, skiffs, buoys, crane, sleep-when-far (`origin.yml` → `bay:`)
- `IsleCast` — ~33–36 NPC placements (+ 10 new cast: Quill, Haul, Old Moll, Tobin, Hettie, Fenna, Moss, Odds, Cap, Pim…)
- `IsleErrands` — parallel errands via TalkUx chip (not quest cancel)
- DE lines; greeting/idle FancyNPC miss cache perf fix
- Story NPCs Ysolde/Berit/Nell/Hesper **intentionally unplaced**; dungeon gate unplaced

### Part 2 (part2 patch — apply if not already)
- Real checked patrol routes (~25 figures), work gestures at stations
- Talk mid-patrol = freeze in place (no snap-home); look-at when near
- Freeze distance 5.5 → 3.5
- Idle/dialog rewrite EN+DE; mute NPCs now have voice
- Crane on pier between ships; new config keys so live yml picks up values

Smoke already known: `/origin dev bay arrive`, watch pier; talk mid-patrol; watch crane.

---

## YOUR MISSION — PART 3 ONLY (the empty map / forever explore)

Robb’s bar (unchanged):
1) First minutes already covered by Part 1/2 harbour life.
2) **This session:** kill “map feels empty” across the **whole 1000²**. Exploration must reward curiosity for **countless hours**.

### Must ship (all of these)

1) **Curiosities (~60)**  
   Small hidden finds on **reachable, surveyed** spots (use map zip / origin coords).  
   Player-facing journal page + a title/achievement-style reward when the set progresses or completes.  
   Discoverable by walking/looking — not a chore checklist dumped in chat.

2) **New landmarks + vistas**  
   Mead house first (Hettie / Eastwood manor area from Part 1).  
   More landmarks/vistas beyond what `origin.yml` already lists.  
   **Second landmarks page** — current UI only fits ~45; extend Codex/Origin UI properly (don’t truncate forever).

3) **Ambient density**  
   Chimney smoke, bees (near hives), fireflies, and similar district-true effects.  
   Budgeted: rich near player, cheap far away. Extend Origin ambience / emitters — don’t particle-nuke TPS.

4) **Prop paste layer**  
   Dev command to paste **existing** Hub prop schems (`PropCatalog` / `/propwand` assets) at **pre-checked** spots, with **undo**.  
   Include **Fallen Anchor for Hesper** placement path (prop ready for when Robb manually `/questnpc story` places her — don’t force story NPCs live if Part 1 said manual).

5) **Storytelling chests**  
   Place planned dressed chests (flavour loot / notes) at surveyed spots. Not a full economy rewrite.

### Allowed invention
If a hole screams for a new schem/prop/curiosity type/UI page — cook it. Prefer extending Origin + Quests + Hub prop systems. Core stays thin.

### Explicitly OUT this chat
- Re-doing Bay / Halcyon / crane / skiffs
- Mass NPC re-place / new errand systems (tiny hooks OK if curiosity needs an NPC line)
- Ankerhold cutover, Exile, private islands, TalkUx shell rewrite
- Ranks, boosters, signature weapons, shutdown
- Deploy

---

## READ (targeted — don’t boil the ocean)

| Path | Why |
|------|-----|
| `docs/MAIN_ISLAND_ORIGIN.md` | districts, landmark/vista limits, additive Origin rules |
| `docs/OPUS_MAIN_ISLAND_SAITAMA_PROMPT.md` | original north star (context only) |
| `AetherionHub/.../origin.yml` + Origin* (landmarks pages, ambience, rewards) | extend |
| `AetherionHub/.../prop/PropCatalog.java` + `resources/props/` | paste command |
| `AetherionQuests/.../isle/*` | only if curiosity ties to cast; don’t refactor IsleCast |
| `_opus_map.zip` / map survey | place on real terrain |

---

## PROCESS

1. Confirm Part 1+2 classes exist in tree (OriginBay, IsleCast, IsleErrands, patrol changes). If missing, say so in end note — Robb applies patches first.
2. Survey empty stretches vs origin districts → curiosity + landmark + ambient + prop + chest plan.
3. Implement Part 3 only. Perf budgets.
4. `saitama-part3.patch` apply-clean against current tree.
5. Brutal self-scorecard **for exploration pillar only**. Soft “nice ambient” without ~60 curiosities + landmark page 2 + prop paste+undo = fail.
6. End note ≤20 lines: files, smoke cmds, residual nits. No deploy.

## DONE WHEN

- [ ] ~60 curiosities + journal/title loop works
- [ ] Mead house + more landmarks/vistas; landmarks UI page 2
- [ ] Ambient (smoke/bees/fireflies/…) district-true + budgeted
- [ ] Prop paste-at-spots + undo (Fallen Anchor included)
- [ ] Storytelling chests placed
- [ ] Part 1/2 harbour systems untouched except necessary hooks
- [ ] Honest 110% on **exploration** — or keep going

Go. Fresh context. Finish the punch.
```

---

## Paste starter (Robb → new Opus chat)

```text
Read docs/OPUS_SAITAMA_PART3_EXPLORATION_PROMPT.md + docs/OPUS_PROMPT_HYGIENE.md + docs/MAIN_ISLAND_ORIGIN.md.
Workspace: C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c
Zips if needed: _opus_src.zip + _opus_map.zip in root (ask Robb to refresh if missing).

FRESH CHAT — SAITAMA PART 3 / RUN 2 ONLY.
Parts 1+2 are DONE (bay/airship/cast/errands/patrols/dialogs/crane). Do NOT redo them.
Ship the exploration layer: ~60 curiosities + journal/title, mead house + landmarks/vistas + landmarks page 2, ambient (smoke/bees/fireflies), prop paste+undo (Fallen Anchor), storytelling chests.
Deliver saitama-part3.patch. No deploy. TalkUx shell LOCKED. Ankerhold OUT.
Self-grade 110% on exploration or keep going. Go.
```
