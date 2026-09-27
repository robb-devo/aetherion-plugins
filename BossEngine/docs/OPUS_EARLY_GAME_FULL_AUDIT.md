# Early Game Full Pass — Audit (Cursor)

**Date:** 2026-09-27  
**Scope:** JOIN → orientation → post-tutorial early hours → soft mid-game opening  
**Baseline:** Harbour Hour Phase-1 already authored on `claude/early-game-harbour` ([PR #65](https://github.com/robb-devo/aetherion-plugins/pull/65)) — arrival, kit ceremony, pier ambience, trail craft. **This pass must not stop at the pier.**  
**Mode:** analysis only (this file). Implementation = Opus via companion prompt.

---

## Journey map (as coded)

```
Join (lang) → harbour arrival / FIND EGON
  → welcome_aboard → gather_wood (market wall lifts)
  → forge_coal → first_shift
  → lesson_boost (Temper) → lesson_manager (Ledger)
  → farm_hand + pocket_zoo (Fields)
  → Miss Ledger stamps tutorialDone
  → soft world open (no hard chain)
```

**`tutorialDone` =** `lesson_boost` + `lesson_manager` + `farm_hand` + `pocket_zoo`  
File: `AetherionQuests/.../util/QuestStoryGate.java`

**Tutorial-allowed but not required for stamp:** `lesson_steel` (Vex), `a_good_catch`, `dock_pass`, …

**After stamp (soft, not a spine):**
1. `lesson_steel` if skipped — Sergeant Vex / Borderlands  
2. `border_rites` — Rite Warden  
3. Craftsman / Recipe Book / `a_simple_craft` (quest gated until stamp; NPC talkable earlier)  
4. Surveyor / blueprint hunt tips  
5. Side loops: `open_up`, `first_hunt`, `sidewalk_survey`, `those_sounds`, `lesson_bones`, skill audits…

`guideTips()` returns **empty** when `tutorialDone` → `/guide` falls through to generic mid-game advice. That is the emotional cliff.

---

## What’s already strong (LEAVE ALONE unless tiny glue)

| Area | Why leave |
|------|-----------|
| Harbour funnel (`HarbourOnboardingGate`) | Intentional pier cage / market soft-wall |
| Soft tutorial redirects (`QuestStoryGate.redirectToTutorial`) | Clear “go here next” |
| QuestAcceptGUI + QuestFeedback | Accept/complete already have craft |
| UnlockToast / ProgressionService unlocks | Manager flags already celebrated |
| Organic spawn discover (`SpawnDiscoverListener`) | Title + unlock exists — polish OK, don’t replace |
| DialogPace + LivingNpcProfile.say | Speech rhythm + color |
| Temper / Ledger / Vex / Rite dialogs | Already teach systems in text |
| Explore chest open FX (once unlocked) | Premium enough |
| Harbour Hour on early-game branch | Arrival, kit ceremony, pier ambience, trail — **don’t redo; extend past pier** |
| LOCKED | Signature weapons, boosters/sockets/anvil, ranks/TAB, shutdown, Seraphine fight |

---

## A) MUST IMPROVE

### A1. Post-graduation cliff (“and now?”)
After Ledger stamp, trail/guide spine evaporates. Soft Vex hint is thin; `/guide` becomes dungeon/gear flavored.  
**Impact:** best-authored hour dumps into checklist emptiness.  
**Evidence:** `QuestStoryGate.guideTips` empty when done; `GuideAdvice`; `NpcListener.openLedgerHelpDesk`.

### A2. Guidance dies after harbour wood
`TutorialQuestTrail` (even after Harbour Hour chevrons) is built around Egon↔Forager. QM → Mine → Temper → Ledger → Fields has bossbar hints but no path craft.  
**Impact:** orientation feels premium only on the pier.

### A3. Living world uneven after pier
Atmosphere historically Lark + Liquidator; Harbour Hour adds pier spine. Mines / Fields / Capital / Vex gate still risk feeling like static FancyNPCs.  
**Impact:** “harbour is a game; rest is plugins.”

### A4. Early UI empty density
- **Recipe Book:** 54-slot panes with few unlocked recipes → barren shop feel (`RecipeBookGUI` / `RecipeBookLayout`).  
- **Manager:** many locked tabs visible as “nope glass” before unlocks (`AetherionManagerGUI`).  
**Impact:** first system UIs feel unfinished.

### A5. Craft / Recipe onboarding confusion
Craftsman can unlock workbench mid-tutorial; `a_simple_craft` accept often waits for stamp; visit can auto-complete quietly.  
**Impact:** “quest never offered / already done.”

---

## B) HIGH-VALUE POLISH

### B1. First combat as a taught moment
`lesson_steel` / Vex / Borderlands. DamageNumbers already show — never framed. No “this is how Aetherion hits feel” beat.  
**Not** a combat rewrite.

### B2. Post-tutorial `/guide` + soft spine
Vex → Rite/gear → Craftsman/Surveyor → Merchant/chests → skill loops. Fill empty `guideTips` without forcing one quest.

### B3. Atmosphere pass beyond pier
Foreman, Farmer, Ledger desk, Vex / Borderlands gate — extend `LivingNpcAtmosphere` pattern.

### B4. Compact / progressive Recipe Book (and Manager first-open)
Collapse empty rows, “unlocked strip,” or progressive chrome until content exists. Same craft philosophy as TAB polish: information hierarchy, not more glass.

### B5. Unlock / discover / milestone presentation
Area unlock, recipe unlock, Ledger graduation, first Borderlands step — make “the world noticed you” consistent (titles, short FX, NPC line). Prefer extending UnlockToast / SpawnDiscover / QuestFeedback.

### B6. Exploration invitation (light)
Explore chests + discover already exist. Add subtle POI readability and one or two honest secrets — **not** loot carpet.

### B7. Environment pieces (optional but high if done well)
Few modular landmarks (mine mouth, workshop, overlook, well, small ruin) that sell early zones — placeable via existing FAWE / `/aetherpaste` / schem workflow. See prompt § World-building. **No new regions.**

---

## C) OPTIONAL / NICE TO HAVE

- Codex / StatsOverview density for brand-new players  
- Loadout empty-state copy  
- Renaming misleading helpers (e.g. “escort” that isn’t a walk)  
- Tiny world mood shifts after milestones (light/sound only)  
- First-crit toast once  
- Merchant/chest timing clarity while still in tutorial  

---

## Risks / regressions

| Risk | Mitigation |
|------|------------|
| Touching `tutorialDone` set / quest IDs | Soft UX only; don’t reshuffle spine |
| Harbour funnel coords | Feel OK; don’t gut walls |
| Kit ceremony double-grant if re-hooked | Guard with existing starter-kit flags |
| Recipe Book craft path break | Keep `RecipeBookCrafter` contract |
| Manager unlock gating | Don’t unlock everything early |
| World edits without restore/schem discipline | Prefer schems + `/aetherpaste`; document coords |
| Particle spam | Geometry/timing > carpets; match harbour tone |
| Scope creep into mid/late bosses | Explicit out of scope |

---

## Cursor vs Opus

| Cursor (this agent / Robbi local) | Opus |
|-----------------------------------|------|
| Audit + prompts + context docs | Creative implementation |
| Merge PR #65 / deploy jars | Don’t need production deploy |
| Ops, TAB live configs, wipe/OP | Content + UX craft |
| Keep LOCKED systems honest | Extend patterns, don’t invent parallel frameworks |

**Opus owns:** post-cliff spine presentation, UI density, ambience beyond pier, combat teach framing, milestone juice, optional few environment pieces.

**Cursor owns beforehand:** ensure Harbour Hour branch is the base Opus starts from; deploy when Robbi asks.
