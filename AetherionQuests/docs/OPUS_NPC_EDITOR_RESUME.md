# Opus resume — NPC Content Studio (pick up mid-rework)

Copy everything below the line into a **new Cloud Agent / Cloud Opus** chat on this workspace.
Do **not** start from the original rework prompt — that work is already half done on disk.

---

You are **resuming** an interrupted rework of Aetherion’s in-game NPC / quest content editor.

## Mission

Finish the Content Studio so it **compiles** and feels professional / self-explanatory. Continue the architecture already started — do **not** throw it away and redesign from zero unless something is fundamentally broken.

## Scope (unchanged, hard)

**Only** `AetherionQuests` → `de.aetherion.quests.editor` (+ `.editor.gui`).

Do **not** touch: story `npcs.yml` / `/questnpc` / Harbour Hour, story `QuestRegistry` casually, Items combat/Terminus/locked weapons/boosters, ranks/TAB, BossEngine, Core growth, deploy.

Keep: `/npc` (+ aliases), `aetherion.npc.editor`, Dev Menu entry, `editor-npcs.yml` compatibility, Fancy prefix `ae_editor_`, dialogue actions (`CLOSE`, `PAGE`, `RUN_*`, `OFFER_QUEST`, `START_QUEST`, `TURN_IN_QUEST`), command blocklist.

## Exact state when the previous agent died

Work is **already on the working tree** (local, uncommitted). Roughly 10:31–10:45 local time on 2026-09-27.

### Already built / heavily rewritten (KEEP and finish wiring)

| Piece | Status |
|-------|--------|
| `gui/Menu.java` | New studio screen framework (slot actions, in-place refresh) |
| `gui/Frame.java` | Shared double-chest layout: tabs Overview / Look / Dialogue / Quest + bottom bar (Back/Undo/Close/Help) |
| `gui/EditorItems.java` | Rewritten helpers (old `title(player,key,fallback)` API removed) |
| `TextInput.java` | Chat/anvil-style input helper |
| `NpcTemplates.java` | Create templates |
| `NpcCheck.java` | Validation issues for NPCs |
| `QuestCatalog.java` | Quest picking helper |
| `EditHistory.java` | Undo |
| `NpcEditor.java`, `NpcEditorCommand`, `NpcEditorListener` | Rewired to open **new** menus |
| `CustomNpc`, `CustomNpcStorage`, `CustomNpcService` | Expanded |
| `DialogueRuntime`, `DialogueAction`, `EditorSessions` | Expanded |

### Missing (this is why compile fails — build these next)

Referenced everywhere but **files do not exist**:

- `gui/HomeMenu.java` — studio home / all NPCs
- `gui/OverviewMenu.java` — NPC overview tab
- `gui/LookMenu.java` — appearance tab

`Frame` also routes tabs to these (+ Dialogue / Quest screens). Implement whatever Dialogue/Quest workspace screens the new `NpcEditor` / `Frame` expect if they are also missing.

### Leftover old GUIs (broken vs new API — migrate or delete)

Still present from the old editor and now incompatible (`EditorItems.title(Player,…)`, `persistQuiet`, old `EditorSessions` APIs, etc.):

- `MainMenu`, `EditMenu`, `ListMenu`, `AppearanceMenu`, `DialogueMenu`, `QuestLinkMenu`, `ConfirmMenu`, `HelpMenu` (old shapes)

`mvn -DskipTests compile` currently fails with **hundreds of errors**. Goal: green compile.

## What to do (order)

1. Read `Frame.java`, `Menu.java`, `NpcEditor.java`, `NpcEditorCommand.java` — learn the intended IA and which `*.open(player, …)` methods are required.  
2. Implement the missing menus (`HomeMenu`, `OverviewMenu`, `LookMenu`, plus Dialogue/Quest/Confirm/Help in the **new** `Menu`/`Frame` style).  
3. Remove or fully migrate old Listener-based menus so nothing still calls dead APIs.  
4. `mvn -DskipTests compile` until clean.  
5. Smoke-check mentally: `/npc` → Home → create/edit → tabs Overview/Look/Dialogue/Quest → save; wand still works; story NPCs untouched.  
6. **No deploy.** Short finish note: screen map + what was left incomplete when you started + storage compat.

## UX bar (same as original brief)

Professional Content Studio. Double chest OK. Scan in 2 seconds. One job per screen. Breadcrumbs. Confirm deletes. Empty states that teach. Chat only when needed. Human labels. Never edit story NPCs.

## Tone

Resume, don’t restart. Finish the studio.

---
