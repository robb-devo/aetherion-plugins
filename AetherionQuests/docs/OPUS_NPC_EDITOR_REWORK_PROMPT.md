# Opus prompt — Rework NPC / Quest Content Editor (GUI only)

Copy everything below the line into the Cloud workspace chat.
Also attach / read `AetherionQuests/docs/OPUS_NPC_EDITOR_REWORK_HANDOFF.md`.

---

You are rebuilding Aetherion’s **in-game NPC / quest content editor** into a tool that feels **professional, clean, and self-explanatory**.

## Scope (hard)

**Only** the custom editor under `AetherionQuests` → `de.aetherion.quests.editor` (+ `.editor.gui`).

Do **not** touch:

- Story cast / Harbour Hour (`npcs.yml`, `/questnpc`, Egon/Twig/Canopy, `HarbourArrival`, `StarterKitCeremony`, living NPC atmosphere)
- Core quest engine rewrite (`QuestRegistry` story quests, player progress files) unless you add a **clearly separate** editor-authored quest path that cannot corrupt live story quests
- AetherionItems combat, Terminus, locked weapons, boosters/sockets/anvil
- Ranks / TAB / Admin identity / shutdown countdown
- BossEngine, Hollow Sun, Seraphine
- Deploy / live server
- Growing AetherionCore with game logic (keep using existing `FancyNpcFacade`)

Dev Menu entry (`/npc`) and permission `aetherion.npc.editor` must keep working.

## First read

1. `AetherionQuests/docs/OPUS_NPC_EDITOR_REWORK_HANDOFF.md`
2. Current editor package (deep):
   - `editor/NpcEditor.java`, `NpcEditorCommand`, `NpcEditorListener`
   - `CustomNpc`, `CustomNpcStorage`, `CustomNpcService`
   - `DialogueRuntime`, `DialogueAction`, `EditorQuestHook`, `EditorSessions`
   - All of `editor/gui/*`
3. Skim only as needed: FancyNPC facade in Core, Dev Menu wand give in Items

Do **not** ingest the whole monorepo.

## Why this pass

Robbi’s playtest feel: the current editor is **unübersichtlich and unnecessarily complicated**. Chat-prompt hopping + multi-nested dialogue menus make it feel like a tech demo, not a content tool.

Goal: anyone on the team (or a new mod) can open it and **make NPCs + quests/dialogue content without a tutorial**.

## Product vision

Build an in-game **Content Studio** for:

1. **Custom NPCs** — create, place, look, skin, duplicate, delete  
2. **Dialogue** — pages, lines, choices, actions — easy to scan and edit  
3. **Quest connection** — offer / start / turn-in / link to quests in a way that feels like “making a quest interaction,” not digging through obscure action enums  

Today the tool is mainly FancyNPC + dialogue + **link existing quest id** (there is no full visual quest designer). You may:

- Completely redesign every GUI and the navigation model  
- Replace chat-prompt UX with clearer flows (Anvil rename, sign input, confirm panels, double-chest workspaces — your call)  
- Expand toward **editor-authored quest drafts** *only if* they stay isolated from story `QuestRegistry` / `npcs.yml` and are safe to reload  
- If full quest authoring is too deep for one pass: still make the NPC + dialogue + quest-link path so good that creating a quest NPC feels obvious — document honestly what quest authoring still can’t do  

## GUI freedom (maximal)

You own the entire presentation:

- Single chest, double chest, multi-row layouts, tab rows, side panels via filler language — whatever reads best  
- Clear hierarchy: **Home → pick thing → edit thing → save**  
- Labels a human understands (“Dialogue”, “When clicked…”, “Give this quest”) — not internal enum names unless secondary  
- Destructive actions always confirm  
- Empty states that teach (“No pages yet — click to add”)  
- Help should be almost unnecessary; keep a short Help anyway  

Hard avoid:

- Nested menus that feel like a maze  
- Requiring `/help` or Discord to understand basics  
- Dumping every setting on one cryptic page with no grouping  
- Fancy particle/FX fluff in the editor itself  

## UX principles (non-negotiable)

1. **Scan in 2 seconds** — primary actions obvious  
2. **One job per screen**  
3. **Always know where you are** (title / breadcrumb item / back)  
4. **Preview when possible** (what the player will see)  
5. **Safe by default** — can’t wipe story NPCs; can’t easily brick YAML  
6. **Keyboard/chat only when necessary** (names, long dialogue lines) — minimize prompt ping-pong  

## Technical constraints (must preserve under the hood)

Unless you migrate carefully with backward compatibility:

| Keep | Why |
|------|-----|
| `plugins/AetherionQuests/editor-npcs.yml` as editor source of truth | Live content |
| FancyNPC prefix `ae_editor_<id>`, `setSaveToFile(false)` | Don’t fight Fancy’s own files |
| Separation from story `npcs.yml` / `/questnpc` | Hard wall |
| Choice actions: `CLOSE`, `PAGE`, `RUN_CONSOLE`, `RUN_PLAYER`, `OFFER_QUEST`, `START_QUEST`, `TURN_IN_QUEST` (extend only if runtime understands) | Player dialogue works |
| `aetherion.npc.editor` + `/npc` (+ aliases) | Dev Menu / Content Kit |
| Command blocklist for RUN_* | Safety |
| Concurrent-safe YAML (reload-before-write or equivalent) | Multi-mod edits |

You **may** replace all `editor.gui.*` classes, rewrite navigation, rename screens, change wand behavior, and refactor editor Java freely **inside the editor package**. Prefer migration that still loads existing `editor-npcs.yml`.

## Process

1. Audit current flow; list what’s confusing.  
2. Redesign IA (information architecture) on paper in your head — Home, NPC list, Edit hub, Dialogue workspace, Quest link, Appearance.  
3. Implement the rework in `AetherionQuests` editor package.  
4. Compile; fix obvious issues.  
5. **No deploy.**  
6. Finish with a short doc: new screen map, what got simpler, storage compatibility, what quest authoring can/can’t do after the pass.

## Tone

Clean. Professional. Obvious. Built for creators, not for the person who wrote the plugin.

Ship the editor Robbi opens and thinks: *finally — this is how it should have been.*

---
