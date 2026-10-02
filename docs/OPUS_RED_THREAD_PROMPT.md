# Opus EXTRA — Soft red thread (tutorial + skill islands)

**Budget:** ~47% session. Scope = early-game red thread only. Do **not** expand into Borderlands content, Hub Origin, NPC life polish, TalkUx shell, weapons, boosters, ranks, or jar deploys unless Robbi says so.

**Scan rule (IMPORTANT):** Do **not** crawl the whole monorepo. Open the allowlist first. Grep only with the keywords below. You do **not** need a full-repo world scan — the spine already lives in Quests. If a detail is missing, ask Robbi.

---

## Goal

Make early game feel like there is a **soft red thread**: a suggested path through the real steps of the game that feels logical and calm — not random, not hectic, not a forced railroad.

Players must still be free to wander and skip. The thread is a gift, not a wall.

### Creative mandate (Robbi)

- If the current tutorial / spine is **already good** → polish lightly and stop.
- If you can **improve** pacing, order, or clarity → do it.
- If you can **replace or elevate** a beat, or **add** a quest / handoff / tip / soft gate that is legitimately better → **go for it**.
- You do **not** have to rebuild everything. Prefer the smallest change that elevates the feel.
- Additive ship mindset: don’t silently delete working quests/NPC ids; migrate or supersede cleanly and document it.

Especially raise:

1. **Tutorial pacing & order** — may reorder, split, merge, or add beats inside Quests if it feels smarter.
2. **Skill islands** — Farm / Fish / Forage must become *findable* via breadcrumbs from places players already visit (Harbour, Forager, Fields, Miss Ledger). Stumbling over them alone is not enough.
3. **Combat readiness before Vex** — don’t dump the player into “go fight” without a gear/armor cue. Soft gate or clear copy is fine; hard lock only if you document why.

---

## Design law

- Soft suggested order. Hard walls only when intentional and documented in the ship notes.
- Exploration / skip must still work.
- One clear “next” tip at a time — no tip spam.
- Don’t overload minute one with every system; introduce skills when they make sense (Ledger skills unlock is a natural hinge; earlier one-liner crumbs are OK).
- EN + DE (`lang/de.yml`) stay in sync for every player-facing string you touch.
- TalkUx bubble/chips shell stays. Content lines may change.

---

## Product spine (default — improve freely)

Use this as the default story shape. You may elevate it if you have a better flow:

1. **Anker Harbour / Egon** → Forager (oak) → Egon  
2. Quartermaster (coal) → Shaft Foreman (mine)  
3. **Temper** (booster) → **Miss Ledger** (Skills)  
4. Fields: Farmer + Lark → Ledger closes orientation  
5. **After skills / graduation:** clear soft pointers to Forage pad (`forage_pad_guide` / Twig), Farm Isle (`farm_isle_guide` / Harrow), Fishing (Tackle / Fishmonger — earlier harbour crumb, not only at the dock end of the world)  
6. **Vex** after a gear/readiness beat — **no Borderlands content** in this EXTRA

---

## Allowlist — read these (mostly only these)

### Must-read

| Path | Why |
|---|---|
| `AetherionQuests/src/main/java/de/aetherion/quests/util/QuestStoryGate.java` | Soft spine, guideTips, redirects, tutorialDone |
| `AetherionQuests/src/main/java/de/aetherion/quests/npc/CastBook.java` | Voice / next-stop / graduation / isle lines |
| `AetherionQuests/src/main/resources/lang/de.yml` | DE for touched keys |
| `docs/npc/NPC_DIALOGUE_OVERHAUL.md` | Cast map + talk rules |
| `docs/dialogs/DIALOG_VOICE_DE.md` | DE conventions (if present) |

### May-read when changing flow

| Path | Why |
|---|---|
| `AetherionQuests/.../listener/NpcListener.java` | spine NPC handlers (`egon`, `lumberjack`, `ledger`, `vex`, `farmer`, `fisher`, `lark`, `forage_pad_guide`, `farm_isle_guide`, `quartermaster`, `foreman`, `booster_tutor`, …) |
| `AetherionQuests/.../dialog/DialogManager.java` | accept / complete / graduation / gates |
| `AetherionQuests/.../ui/TutorialQuestTrail.java` | trail / find-Egon |
| `AetherionQuests/.../manager/QuestManager.java` | gates, rewards, handoffs |
| `AetherionQuests/.../quest/QuestRegistry.java` + NPC registry quest defs | add/reorder tutorial quests if elevating |
| `docs/FORAGING_ELDERVALE.md` / `FARMING_ELDERVALE.md` / `FISHING_ELDERVALE.md` | skim where pads/isles live — **do not rewrite isle gameplay plugins** |

### Grep keywords (then stop)

`guideTips`, `redirectToTutorial`, `tutorialDone`, `TUTORIAL_`, `lesson_manager`, `lesson_boost`, `forage_pad_guide`, `farm_isle_guide`, `vex_intro`, `graduate`, `welcome_aboard`, `gather_wood`, `a_good_catch`, `dock_pass`, `farm_hand`, `pocket_zoo`

---

## Hard deny-list

- TalkUx UX shell (layout / chips / linger / input) — LOCKED  
- `LivingNpcLife`, Origin Hub cast, Borderlands / Rite / vials  
- Blossom Blade / Gravwell combat, Booster stack/lore, Anvil sockets, Ranks, ShutdownCountdown  
- Farming / Fishing / Mining / Foraging **gameplay systems** (nodes, pads physics, GUIs) — breadcrumbs + Quests wiring only  
- Deploy / restart unless Robbi asks  

---

## Allowed change types

1. Dialog / CastBook / LangPack (EN + DE).  
2. `QuestStoryGate` tips, redirects, tutorial membership, soft gates.  
3. Tutorial quest defs / handoffs / order — **add, split, merge, replace** if it elevates; document before→after.  
4. Soft Vex readiness gate or stronger copy.  
5. Post-Ledger / graduation crumbs to skill islands.  
6. Tiny Quests-only helpers. Prefer **zero** Core; Core only if unavoidable and tiny.

---

## Deliverable

1. Quests work (Core only if unavoidable).  
2. Ship folder `_red_thread_ship/` with:
   - file list + why  
   - before→after spine (bullets)  
   - what you kept vs elevated vs added  
   - in-game test plan (fresh / mid-tutorial / post-Ledger)  
3. Compile `AetherionQuests` (+ Core if touched).  
4. Do **not** deploy.

---

## Test checklist (Robbi)

- [ ] Fresh join feels calm; Egon → Forager still works with bubbles  
- [ ] One sensible next tip at a time during tutorial  
- [ ] Skills / islands get real pointers (Forage, Farm Isle, Fishing) without forcing  
- [ ] Vex has a readiness cue before “go fight”  
- [ ] DE matches EN on touched keys  
- [ ] Exploring / skipping still works (new hard walls documented)

---

## Starter line (paste into a **new** Opus chat)

> Read **only** `docs/OPUS_RED_THREAD_PROMPT.md` and its allowlist — do not rescan the monorepo. Soft red thread EXTRA: elevate early-game tutorial + skill-isle discovery. Soft, not forced. You may polish, improve, add, or replace tutorial beats if it genuinely elevates; you don’t have to rebuild everything. TalkUx shell / Life / Origin / Borderlands / weapons / boosters LOCKED. Ship `_red_thread_ship/`, compile Quests, do not deploy.
