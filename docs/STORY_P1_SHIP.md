# Story Package 1 — ship note

"Das Schlaflied", Act I–II (quests 1–9), plus the dialog cleanup for the free harbour/capital cast.
Plan: `STORY_P1_PLAN.md`. Bible: `STORY_BIBLE.md`. Not deployed, not committed.

## What shipped

**1 · Dialog cleanup.** Egon, Bar Whisper, Deed (isle_clerk), Hollis (town_crier), Wick (lamp_lighter),
Vince, Surveyor, Miss Ledger, foreman idle, Sister Aurel + Stellan barks. EN in code, DE in `lang/de.yml`
(same beat count). Story-stage variants are additive:

| Who | Act I (legend) | Act II (doubt) from stage |
|---|---|---|
| Egon | "don't sail toward the Veil" | 4 · ferry with no rower |
| Hollis | intro `default`, "mind your children" | 5 · intro `quiet_news` |
| Deed | old seals, ignore them | 3 · intro `after_seal` |
| Wick | the lamp law | 4 · intros `night_queen` / `day_queen` |
| Vince / Surveyor / Bar Whisper | bet · troll chain · the bar remembers | 5 / 5 / 6 |
| Miss Ledger | — | 3 · "no records before the Silent Year" |
| Sister Aurel / Stellan (Hub) | Hub barks, polished | 2 / 3 · short toll · warm stars |

Each voice also gets one stage-aware small-talk topic (`cast.<npc>.story.*`). Secrets stay out of
barks (Maelis' whereabouts, Elin, Corvin and the bells). Tutorial GPS facts unchanged.

**2 · Five story NPCs.** `ysolde`, `berit`, `nell`, `hesper`, `ferryman`: LivingNpc profile, CastBook
voice (EN + DE), registry entry, `QuestNPCRegistry.storyNpcIds()`. Not auto-placed. Before the
tutorial is done they give a short "not yet" and point back to it.

**3 · Lore items + Chronicle.** `StoryLore` (9 finds, no stats, PDC `aetherion:lore_find` + `find_id`).
Manager → **Journal** now opens a hub: Boss Journal (unchanged) + Chronicle. DevMenu → Content →
**Lore & Chronicle** (additive tile, slot 23). Boss finds come from `BossDeathEvent` only while the
matching `lullaby_*` quest runs; no boss YAML touched.

**4 · Quests.** `lullaby_old_tale` … `lullaby_silent_road` (`story/LullabyQuests`). Stage 0–7 is
computed from completed Lullaby quests (`StoryState`), exposed to other plugins through the thin
Core `StoryAccess` (`AetherServices.story()`). `silent_road` sets the per-player flag
`veil_quay_unlocked`. `closed_road` and `border_rites` untouched.

| # | Quest | Giver → finish | Gate |
|---|---|---|---|
| 1 | old_tale | Hollis → talk Berit (gets *The Mad King* storybook) | tutorial |
| 2 | gear | Berit → kill Hollow Lurker → Ysolde | — |
| 3 | insignia | Ysolde → kill Skuldugery → Ysolde | — |
| 4 | short_toll | Ysolde → talk Sister Aurel (Hub) | — |
| 5 | old_seal | Deed → talk Ysolde | Lvl 20 |
| 6 | letters | Ysolde → 3 letters from world chests → Ysolde | Lvl 30 |
| 7 | third_anchor | Ysolde → talk Hesper | Lvl 40 |
| 8 | forgotten | Nell → talk Berit (she no longer knows you) | Lvl 50 |
| 9 | silent_road | Ysolde → kill Pathwarden → Ysolde (Sealed Writ) | Lvl 60 |

Side finds (once per player, while any Lullaby quest runs): McNugget → Tiny Paper Crown,
Bridge Troll → Anchor Chain Link. (The crown's item id is `lore_tiny_paper_coronet`: ids containing
"crown" get labelled as helmets.) After each step the next stop is named in the dialog and, when it
lives with someone else, in an action-bar tip.

## Test

1. `/questnpc story` → place Ysolde (Archive), Berit (cottage, Capital edge), Nell (harbour lane),
   Hesper (under the Fallen Anchor), Ferryman (Veil Quay). Hollis (idea NPC, same page) and Deed too.
2. Finish the tutorial, click Hollis → offer → Berit → storybook + Chronicle page.
3. DevMenu → Content → Lore & Chronicle: grab finds (gear, insignia, letters, writ) to fast-forward
   DELIVER steps; `/aquest complete <questId>` skips a step (pages, storybook and the Veil flag follow).
4. Switch to German: same flow, every beat German, no English fallbacks.
5. Sister Aurel: with `short_toll` running, click her → three lines, quest completes, board skipped once.

## Deferred

Story markers over the five NPCs · compass pin for Sister Aurel (Hub NPC) · cast skins for the
five · ferry ride / exile wiring · Act III, finale, bell events, fade VFX · Nachspiel.
