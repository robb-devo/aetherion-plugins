# Early Game Full Pass — Opus context pack

Compact reference. Read with `OPUS_EARLY_GAME_FULL_AUDIT.md` + implement from `OPUS_EARLY_GAME_FULL_PROMPT.md`.

**Git baseline:** work on / branch from **`claude/early-game-harbour`** (Harbour Hour already landed there). Do not reinvent pier arrival/kit ceremony/trail — **extend past the pier and past `tutorialDone`.**

---

## Plugins that matter

| Plugin | Early role |
|--------|------------|
| AetherionHub | Spawns, discover unlocks, starter-hint, `/aetherpaste` FAWE island paste |
| AetherionQuests | Spine, Living NPCs, dialog, hints, trails, explore chests, story gates |
| AetherionItems | Welcome, starter tools, Manager, Recipe Book, DamageNumbers, `/guide`, UnlockToast |
| Farming / Mining / Foraging / Fishing | Post-tool activity loops |
| BossEngine | Quality bar only — no boss redesign this pass |
| AetherionCore | Thin keys/APIs only |

---

## Spine facts

- Quests are **code-registered** (`QuestRegistry`), not YAML tables.  
- Soft story ≠ skill gates (`QuestStoryGate` vs `QuestSkillGate`).  
- `tutorialDone` = `lesson_boost` + `lesson_manager` + `farm_hand` + `pocket_zoo`.  
- Harbour funnel = soft shove/hints, not hard teleports (except water rescue).  
- `DialogPace.LINE_GAP_TICKS` = single speech rhythm.  
- Starter tools: `StarterGearReward` (+ `StarterKitCeremony` on early-game branch).  
- Display matrices: `setTransformationMatrix` (no TRS decompose).

---

## Must-open files

### Gates / journey
- `AetherionQuests/.../util/QuestStoryGate.java`
- `AetherionQuests/.../quest/QuestRegistry.java` (early + post IDs)
- `AetherionQuests/.../listener/HarbourOnboardingGate.java`
- `AetherionQuests/.../manager/QuestManager.java` (start gates ~tutorial)
- `AetherionQuests/.../listener/NpcListener.java` (`openLedgerHelpDesk`, Craftsman visit)

### Presence / feedback
- `.../npc/LivingNpcAtmosphere.java`, `LivingNpcProfile.java`, `LivingNpcService.java`
- `.../ui/TutorialQuestTrail.java`, `QuestHint.java`, `QuestFeedback.java`
- `.../dialog/DialogManager.java`, `DialogPace.java`
- `.../reward/StarterGearReward.java` (+ `StarterKitCeremony` if on branch)
- `.../ui/HarbourArrival.java` (if on branch)
- `.../chest/ExploreChestService.java`
- `AetherionHub/.../listener/SpawnDiscoverListener.java`
- `AetherionItems/.../progress/UnlockToast.java`, `ProgressionService.java`
- `AetherionItems/.../guide/GuideAdvice.java` (or equivalent `/guide` path)

### UI
- `AetherionItems/.../recipe/GUI/RecipeBookGUI.java`, `RecipeBookLayout.java`, `RecipeBookListener.java`
- `AetherionItems/.../menu/AetherionManagerGUI.java`, `StatsOverviewGUI.java`
- `AetherionItems/.../codex/CodexGui.java` (or CodexService GUI)
- `AetherionQuests/.../ui/QuestAcceptGUI.java`
- `AetherionItems/.../combat/DamageNumbers.java`

### Combat teach
- Vex / `lesson_steel` dialogs in `DialogManager`
- Borderlands soft entry (Hub unlock `borderlands`)

### World / place tools
- `AetherionHub/.../command/AetherPasteCommand.java`
- `AetherionHub/.../island/FaweIslandPaste.java`
- WorldEdit/FAWE schematics folder on server (existing island schems)

### Quality bar (read-only)
- SaintFx / Seraphine MusicBox / Hollow Reliquary — craft taste, not scale
- Harbour Hour result on early-game branch — tone reference for “smaller but authored”

---

## Constraints

Never without Robbi ask: Blossom Blade / Gravwell, boosters/sockets/anvil, ranks/TAB, shutdown countdown, Seraphine fight redesign, tutorialDone ID set reshuffle, Core game-loop growth, new legendary weapons, new dungeon regions.

Prefer extending existing managers over parallel systems.
