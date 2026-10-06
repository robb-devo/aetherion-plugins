# Opus Prompt — Story Package 1

**Superseded for the mega-chat:** use `docs/STORY_OPUS_CHAT_PROMPT.md` (dialog overhaul + story NPCs + items + lullaby). Keep this file as the narrower technical spine reference via `STORY_P1_PLAN.md`.

---

Copy everything below the line into a fresh Opus chat in this worktree (legacy P1-only).

---

You are implementing **Aetherion Story Package 1** in the repo at the workspace root. Read these first, in order:

1. `docs/STORY_BIBLE.md` — canon (tone, names, quests 1–9, finds, NPCs)
2. `docs/STORY_P1_PLAN.md` — exact P1 scope, architecture, acceptance checklist
3. `docs/STORY_INVENTORY.md` — what already exists
4. `docs/dialogs/DIALOG_VOICE_DE.md` — DE overlay rules

Owner is **Robb**. Call him Robb in chat.

## Mission

Implement Package 1 **code + content** as specified in `STORY_P1_PLAN.md`:

- Story stage API (Core interface, Quests implementation from `lullaby_*` state)
- Server-wide story flag store (API + persistence; no hymn playback)
- Chronicle tab under Manager Journal hub (Boss Journal stays)
- Lore item factories + DevMenu give row (additive)
- BossDeathEvent fundstück listener (no boss YAML loot edits)
- 5 new NPCs: `ysolde`, `berit`, `nell`, `hesper`, `ferryman` — LivingNpc + dialogs + CastBook + DE — **spawnable via `/questnpc`, not auto-placed**
- Quests `lullaby_old_tale` … `lullaby_silent_road` (bible 1–9)
- Additive Act I / Act II dialog variants on FREE NPCs only
- Completing `lullaby_silent_road` sets per-player `veil_quay_unlocked`

## Hard bans

- Do **not** deploy, restart, or commit
- Do **not** touch TalkUx shell architecture
- Do **not** touch boosters, anvil sockets, ranks/wipes, ShutdownCountdown, Blossom Blade, Gravwell Cleaver, Borderlands vials
- Do **not** edit Farmer / Harlow / Rite Warden / Proctor / isle-cast dialogs
- Do **not** thin or replace existing quests, DevMenu pages, or boss YAML loot
- Do **not** build exile world, Act III bosses, finale, bell events, or fade VFX
- Do **not** auto-place the 5 story NPCs in the world

## Implementation rules

- **Additive ship:** union of menus/factories; never overwrite a richer page with a thinner one
- **Core stays thin:** `StoryAccess` + `AetherServices` register/clear only; stage math lives in Quests
- **English canonical** for boss/key quotes; DE in `lang/de.yml` under `cast.*` / talk keys, informal du, dry, **same sentence/beat count**
- Match existing patterns:
  - Quests: `QuestRegistry`, `QuestManager`, `ObjectiveType` TALK/DELIVER/KILL, `QuestStoryGate.tutorialDone`
  - NPCs: `QuestNPCRegistry`, `LivingNpcProfile`, `DialogManager`, `CastBook`, `ideaNpcIds()` / extras anchors
  - Manager: `AetherionManagerGUI.JOURNAL_SLOT` (23) — hub → Boss Journal + Chronicle
  - Boss death: `de.aetherion.bossengine.event.BossDeathEvent` — mutate `lootByPlayer`
  - Progress: `AetherServices.progress()` / `ProgressAccess` for AL soft gates
- Resolve live npcIds by grepping (Hollis = `town_crier`, Sister Aurel / bellkeeper, Deed / isle clerk). Do not invent duplicates.
- Soft AL gates for quests 5–9; do not break island (20) / guild (75) systems
- Keep lines short for TalkUx bubbles (~70 chars / beat)

## Quest IDs (exact)

```
lullaby_old_tale
lullaby_gear
lullaby_insignia
lullaby_short_toll
lullaby_old_seal
lullaby_letters
lullaby_third_anchor
lullaby_forgotten
lullaby_silent_road
```

Fundstück boss → quest mapping and item names are in `STORY_P1_PLAN.md` §4 / bible Boss table.

## Done criteria

1. Code compiles for touched modules (at least Quests + Items + Core if Core changed)
2. Acceptance checklist in `STORY_P1_PLAN.md` is honestly marked done / deferred with reason
3. Short summary for Robb: files touched, how to place NPCs, how to test quests 1–9 with DevMenu items
4. No deploy / restart / commit

## Process

- Scout before inventing APIs that already exist
- Prefer one focused PR-sized change set; no drive-by refactors
- If a LOCKED system would need changing: stop, write `LOCKED … SYSTEM CONFLICT: …`, wait
- If Ankerhold coords are unknown: leave NPCs unplaced (anchors only)

Start by reading the four docs, then implement.
