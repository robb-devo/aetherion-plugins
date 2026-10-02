# NPC Studio — editor rework (finish doc)

**Plugin:** AetherionQuests · package `de.aetherion.quests.editor` (+ `.gui`) only
**Entry:** `/npc` (`/aethernpc`, `/npceditor`), perm `aetherion.npc.editor`, Dev Menu → NPC Editor (still `performCommand("npc")`)
**Status:** compiles clean; not deployed; model logic verified with an offline harness (44 checks); **not yet clicked through in-game**.

---

## Screen map

```
/npc · wand right-click air · Dev Menu
        │
        ▼
 HOME — every NPC as a head card, nearest first            + New NPC ─► TEMPLATE PICK ─► type name (chat)
        │  click card (shift-click = teleport)                            (Talker · Quest giver · Guide · Service · Blank)
        ▼
 NPC WORKSPACE — 4 tabs, one click apart, green marker under the open tab
 ┌────────────┬──────────────┬──────────────────────┬───────────────────────────┐
 │ Overview   │ Look         │ Dialogue             │ Quest                     │
 │ ▶ Preview  │ Name         │ page cards: what the │ Main quest (picker)       │
 │ Health ✔/⚠ │ Subtitle     │ NPC says, the replies│ ⚡ Set up as quest giver   │
 │ Where + tp │ Skin · Arms  │ and where they lead  │ Stage timeline ①②③④      │
 │ Move/Face  │ 10 outfits   │ + New page           │ Quest replies list        │
 │ Dup/Delete │              │                      │                           │
 └────────────┴──────────────┴──────────┬───────────┴─────────────┬─────────────┘
                                        ▼                         ▼
                                PAGE — NPC says (lines)     PAGE PICKER / QUEST PICKER
                                     + Player replies        (browse instead of typing ids)
                                        │
                                        ▼
                                REPLY — button text + "what happens" grid
                                (Conversation · Quest · Command) + its one setting
```

Every screen: title is a breadcrumb (`Bob › Dialogue › Greeting`), bottom bar is always
`◀ Back · ↶ Undo · ✖ Close · ? Help` (help = hover tips for that screen, click = full guide).

## What got simpler

| Before | Now |
|---|---|
| Main menu → List → Edit menu (3 hops) | Home *is* the list; nearest NPC first, one click to open |
| Edit menu: 9 unrelated buttons in a row | 4 tabs grouped by job (Overview / Look / Dialogue / Quest) |
| Page ids typed in chat; typo = silent new page | Page **picker** + "+ New page"; renaming rewires every link |
| Quest ids typed in chat | Quest **picker** with objectives/rewards, search, tutorial quests hidden |
| Reply action = click-to-cycle through 7 enum names | Labelled grid ("Offer a quest", "Continue talking"…) with descriptions |
| Pick action, then separately type its target | Picking an action asks for its one missing setting right away |
| Only shift-click-remove for lines; no edit/reorder | Click = edit (chat pre-filled via **[✎ Edit current text]**), shift = reorder, Q = delete |
| One line per chat prompt | Dictation mode: type N lines in a row, `done` to finish |
| Delete page: no confirm, leaves dead links | Confirm screen; linked replies become "End conversation" |
| Sneak-click NPC with wand = delete | Sneak-click = talk to it for real; delete lives in Overview with confirm |
| No preview without dropping the wand | ▶ Preview everywhere (safe: commands/quests only described) |
| Mistakes are permanent | ↶ Undo per NPC (40 steps, incl. deleted NPCs) |
| Broken wiring found by players | Health check on Overview/Home cards, click jumps to the fix |

Chat is only used for real text: names, subtitle, skin username, lines, reply text, commands, search.
Pending input shows a boss bar, times out after 5 min, and opening any studio screen cancels it.
A typed `/command` during a command prompt is captured as the answer instead of being run.

## Quest connection — can / can't

**Can (new):**
- **Main quest** per NPC, picked from the list (hover shows objectives/rewards/requirements).
- **Stage pages:** where the chat starts when the player's main quest is *In progress*, *Ready to turn in*
  or *Completed* (Not started = first page). Right-click a stage to preview as that stage.
- **⚡ Set up as quest giver:** adds an Offer reply + In progress / Ready (with Turn in) / Completed pages and
  wires the stages. Non-destructive, idempotent, undo-able. The *Quest giver* template does this at creation.
- Offer / Start / Turn-in replies now tell players why nothing happened (already on it, not done yet, …).

**Can't (still code-only):** creating or editing quests themselves (objectives, rewards, waypoints, gates).
The studio only links existing `QuestRegistry` quests and never registers, mutates or saves quests or player
progress. No per-reply visibility conditions (e.g. show "Turn in" only when ready) — stage pages cover that case.
Editor-isolated quest drafts were deliberately not attempted in this pass.

## Storage compatibility

Still `plugins/AetherionQuests/editor-npcs.yml`, same keys, same action names — **old files load unchanged**
(verified against a pre-studio entry). New keys are optional and only written when used:

| Key | Meaning |
|---|---|
| `quest-pages.{active,ready,completed}` | stage → page id |
| `created-by`, `edited-by`, `edited-at` | shown on Home cards ("Last edit: Robbi, 5 min ago") |
| `subtitle: ''` | now means "no subtitle" (missing key still defaults to `Guide`, as before) |

Also: empty `lines` are no longer padded with `…` on load; saves go through a temp file + atomic move;
the first save after each start copies the live file to `editor-npcs.yml.bak`.
Rolling back to the old jar still loads the file, but its writer would drop the new keys on the next save.

Unchanged: Fancy name `ae_editor_<id>`, `setSaveToFile(false)`, ids `mod_<slug>` with story-id collision
checks, reload-before-write, hologram tags, wand PDC key (old wands keep working).

## Safety notes

- Story NPCs never appear (only `ae_editor_*` are handled). Tutorial quests are hidden in the picker and flagged if linked.
- Command blocklist kept and tightened: namespaced forms (`minecraft:execute`), `//` WorldEdit, `sudo`, `rl`,
  `save-off`, and the studio's own commands. Existing replies using those commands will now be blocked.
- Preview never runs commands or quest actions; real testing = sneak-right-click with the wand (or no wand).

## Please check in-game (couldn't be tested here)

1. `/npc` and Dev Menu open Home; wand right-click air / NPC / sneak-NPC.
2. Create each template; Quest giver → picker → stages lit; Service → reply screen asks for a command.
3. Chat input: **[✎ Edit current text]** fills the chat box; `cancel`, `done`, boss bar, timeout.
4. Q-to-delete, shift-reorder, in-place refresh (cursor shouldn't jump on same-screen edits).
5. Undo after delete-NPC (chat button + Home undo). Tooltips: no "Attack Damage" lines on tool icons.
6. Real talk with stage pages: not started / active / ready / completed.
7. German clients: editor titles are English now (the old `ui.npc_*` keys in `lang/de.yml` are unused).
