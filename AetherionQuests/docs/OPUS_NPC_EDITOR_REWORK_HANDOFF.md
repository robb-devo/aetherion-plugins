# NPC / Quest Content Editor rework — Technical handoff (for Opus)

**Cursor does not implement this.** Map + brief only.  
**Plugin:** AetherionQuests · Paper 1.21.1  
**Date:** 2026-09-27  
**Scope:** Full GUI/UX rework of `de.aetherion.quests.editor` (+ gui). Nothing else.

---

## A) WHAT THIS TOOL IS TODAY

There is **no full visual quest designer**. The live tool is a **FancyNPC + multi-page dialogue editor** that can **link existing quests**.

| Robbi wants | Current reality |
|-------------|-----------------|
| In-game NPCs + quests | NPCs + dialogue + quest **link/offer/start/turn-in** against existing quest ids |
| Clean professional UX | Nested GUIs + lots of chat prompts; feels dense / unclear |
| Self-explanatory | HelpMenu exists; still needs tribal knowledge |

**Product goal for this pass:** Content Studio UX that makes creating custom NPCs + dialogue (+ quest interaction) obvious. Expand quest authoring only if safely isolated from story quests.

---

## B) ENTRY POINTS

| Entry | Notes |
|-------|--------|
| `/npc` | Main (`/aethernpc`, `/npceditor`) |
| Subcommands | `create/new`, `edit/nearby`, `list`, `delete/remove`, `move`, `duplicate/copy`, `wand`, `help` |
| Dev Menu (Items) | Blaze-rod **NPC Editor** → `performCommand("npc")` / wand give |
| Perm | `aetherion.npc.editor` |

**Not this editor:** `/aquest` (progress admin), `/questnpc` (story cast → `npcs.yml`).

---

## C) FILE MAP

Package: `AetherionQuests/src/main/java/de/aetherion/quests/editor/`

### Core

| Class | Role |
|-------|------|
| `NpcEditor` | Facade: create/edit/move/delete/wand; opens menus |
| `NpcEditorCommand` | `/npc` executor + tab |
| `NpcEditorListener` | Wand air-click + chat prompts |
| `CustomNpc` | Model: name, subtitle, skin, preset, loc, pages, linked quest |
| `CustomNpcStorage` | `editor-npcs.yml` load/save (reload-before-write) |
| `CustomNpcService` | FancyNPC spawn/move/refresh; prefix `ae_editor_`; holograms |
| `CustomNpcInteractListener` | Interact → edit (wand) or talk |
| `DialogueRuntime` | Player chat lines + choice GUI; `EditorQuestHook` |
| `DialogueAction` | `CLOSE`, `PAGE`, `RUN_CONSOLE`, `RUN_PLAYER`, `OFFER_QUEST`, `START_QUEST`, `TURN_IN_QUEST` |
| `EditorQuestHook` | Offer/start/turn-in extension (no quest authoring UI) |
| `EditorSessions` | Per-player prompt / page / choice state |
| `AppearancePreset` | Leather presets + default skins |

### GUI (safe to replace entirely)

| Class | Role today |
|-------|------------|
| `MainMenu` | Create / nearby / list / wand / help (27 slots) |
| `EditMenu` | Rename, subtitle, appearance, dialogue, quest link, move, look, duplicate, delete |
| `ListMenu` | Paginated editor NPCs |
| `AppearanceMenu` | Presets, slim, custom skin (chat) |
| `DialogueMenu` | Tree → page (≤7 lines/choices) → choice (text/action/target) |
| `QuestLinkMenu` | Pick existing quest id |
| `ConfirmMenu` | Delete confirm |
| `HelpMenu` | Commands / storage / perm |
| `EditorItems` | Shared buttons/titles |

### Related — **do not absorb into editor**

- Story: `NPCDataStorage` / `QuestNPC*` / `/questnpc` → `npcs.yml`
- Quests: `QuestRegistry` (Java), `QuestManager`, `players/<uuid>.yml`
- Player UIs: `QuestAcceptGUI`, `EgonBriefingGUI`, `DialogManager` (story)

---

## D) USER FLOW TODAY

1. `/npc` or Dev Menu → **Main**  
2. Create (chat name → spawn at feet → **Edit**) / Edit nearby / List / Wand / Help  
3. **Edit** → Appearance | Dialogue tree | Quest link | Move/Look/Duplicate/Delete  
4. **Dialogue:** Tree → Page → Choice (cycle action, chat for target text)  
5. Players without wand: click NPC → `DialogueRuntime`  
6. Wand + sneak-click → delete confirm  
7. Story NPCs are never editable here  

Pain (Robbi): too many layers, unclear labels, chat ping-pong, doesn’t feel like a content tool.

---

## E) STORAGE

| File | Role |
|------|------|
| `plugins/AetherionQuests/editor-npcs.yml` | Editor NPCs only; jar must not clobber live file |
| Schema | `npcs.<id>.{name,subtitle,skin,slim,preset,world,x,y,z,yaw,pitch,quest,start,pages.<pageId>.{lines,choices[{text,action,target}]}}` |
| Ids | Prefer `mod_<slug>`; avoid clash with story registry ids |
| Fancy name | `ae_editor_<id>` (`setSaveToFile(false)`) |
| Config | `editor-npc-visibility-distance` (default 48) |

**Hard wall:** never write editor data into `npcs.yml`; never mutate story spawn paths.

---

## F) WHAT GUI REWORK MAY REPLACE VS MUST KEEP

| Replace freely | Preserve (or migrate with compat) |
|----------------|-----------------------------------|
| All `editor.gui.*` layouts, titles, navigation | `editor-npcs.yml` keys / choice action names |
| Chat-prompt UX | `CustomNpc` essential fields, `START_PAGE` / greeting semantics |
| Wand feel, Help copy | Fancy prefix `ae_editor_`, hologram/PDC tags |
| Information architecture | `DialogueRuntime` offer/start/turn-in + command blocklist |
| Optional new screens (preview, quest binder) | Separation from story NPCs + id collision checks |
| | Perm + `/npc` aliases for Dev Menu |

---

## G) QUEST AUTHORING BOUNDARY

- **In scope to improve hard:** linking / offering / starting / turning in quests from dialogue choices with crystal-clear UI.  
- **Optional stretch:** editor-local quest drafts **isolated** from story `QuestRegistry` (own YAML, own ids, cannot overwrite Harbour/story quests).  
- **Out of scope:** rewriting all existing story quests into the editor; changing player progress schema casually; touching Harbour onboarding.

If you don’t ship full quest authoring, say so in the finish doc and still make the NPC+dialogue+link path excellent.

---

## H) LOCKED / NON-GOALS

- Story NPCs, Harbour Hour, starter kit ceremony  
- Blossom Blade / Gravwell, boosters, anvil, ranks/TAB, shutdown countdown  
- AetherionCore game-logic growth  
- Deploy / production  
- Unrelated Quests UI polish (sidebar, TAB — separate)

---

## I) DELIVERABLE

- Reworked editor UX in `AetherionQuests` editor package  
- Existing `editor-npcs.yml` still loads (or migrated with backup note)  
- `/npc` + Dev Menu still open the new studio  
- Compile clean; no deploy  
- Short finish doc: screen map, simplifications, storage compat, quest capabilities after the pass
