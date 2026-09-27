# Early Game Handoff — technical context for Claude Opus

**Phase:** 1 — first 1–2 hours only.  
**Read with:** `OPUS_EARLY_GAME_AUDIT.md`, then implement from `OPUS_EARLY_GAME_PROMPT.md`.  
**Do not** dump the whole monorepo into context. Prefer the files below.

---

## Server / plugin map (relevant)

| Plugin | Role in hour 1–2 |
|--------|------------------|
| **AetherionHub** | Harbour spawn, `/spawns`, discover unlocks, starter-hint, launch pads |
| **AetherionQuests** | Tutorial spine, Living NPCs, dialog, hints, trails, explore chests |
| **AetherionItems** | Join welcome, starter tools, skills, boosters lesson, Manager, damage numbers, `/guide` |
| **AetherionCore** | Shared keys / thin APIs only — **do not grow a game loop here** |
| Farming / Mining / Foraging / Fishing | Activity loops after tools exist |
| **BossEngine** | **Reference quality only** this phase — do not redesign bosses |

Paths (Windows):

- `C:\Users\Robbi\IdeaProjects\AetherionHub\`
- `C:\Users\Robbi\IdeaProjects\AetherionQuests\`
- `C:\Users\Robbi\IdeaProjects\AetherionItems\`
- `C:\Users\Robbi\IdeaProjects\BossEngine\` (docs + FX reference)

---

## Architecture decisions that matter

1. **Quests are code-registered**, not YAML tables — `QuestRegistry.registerAll`.  
2. **Tutorial completion** is `QuestStoryGate.tutorialDone` = completed  
   `lesson_boost` + `lesson_manager` + `farm_hand` + `pocket_zoo`.  
3. **Soft story gates ≠ skill gates** — `QuestStoryGate` vs `QuestSkillGate`.  
4. **Harbour funnel** is velocity shove + hints (`HarbourOnboardingGate`), not hard teleports (except water rescue to harbour).  
5. **Dialog rhythm** is centralized: `DialogPace.LINE_GAP_TICKS` (40).  
6. **Living NPCs** = FancyNpcs + `LivingNpcProfile` / `LivingNpcService` / atmosphere hooks.  
7. **Starter tools** go through `StarterGearReward` → `CustomItem` + `StarterSetBalance` revision PDC.  
8. **AetherionCore stays thin** — no boss AI / quest ticks / loot logic in Core.

---

## Early-game journey (do not reorder)

```
Join (lang + welcome + FIND EGON)
  → Egon pier (cage until talk)
  → welcome_aboard → lumberjack
  → gather_wood → Egon turn-in → starter kit
  → Quartermaster / forge_coal → mines
  → Foreman / first_shift
  → Temper + Ledger + Fields (farm_hand, pocket_zoo)
  → Ledger stamps tutorialDone → world opens
```

---

## Files Opus should actually open

### Must understand (read first)

| File | Why |
|------|-----|
| `AetherionQuests/.../util/QuestStoryGate.java` | Tutorial NPC set, done condition, tip spine |
| `AetherionQuests/.../quest/QuestRegistry.java` | Quest IDs, rewards, service NPCs |
| `AetherionQuests/.../listener/HarbourOnboardingGate.java` | Pier / market soft walls |
| `AetherionQuests/.../dialog/DialogManager.java` | Spoken lines, accept flow |
| `AetherionQuests/.../dialog/DialogPace.java` | Speech timing |
| `AetherionQuests/.../npc/LivingNpcProfile.java` | Looks / chat colors |
| `AetherionQuests/.../npc/LivingNpcAtmosphere.java` | **Pattern** for ambient (Lark, Liquidator) |
| `AetherionQuests/.../ui/TutorialQuestTrail.java` | Harbour dust path corners |
| `AetherionQuests/.../ui/QuestHint.java` | Soft next-step bossbar |
| `AetherionQuests/.../feedback/QuestFeedback.java` | Accept / complete FX |
| `AetherionQuests/.../reward/StarterGearReward.java` | Kit grant path |
| `AetherionQuests/.../lang/lang` + `resources/lang/de.yml` | Player-facing copy |
| `AetherionItems/.../listener/JoinWelcomeListener.java` | First title |
| `AetherionHub/.../resources/config.yml` | `starter-hint`, spawn defs |
| `AetherionHub/.../listener/SpawnDiscoverListener.java` | Walk-in unlock announce |
| `AetherionQuests/.../chest/ExploreChestService.java` | Explore open path |

### Optional reference (quality bar — read-only)

| File | Why |
|------|-----|
| `BossEngine/.../loot/HollowReliquary.java` | Careful claim / finale pacing |
| `BossEngine/.../loot/SeraphineMusicBox.java` | Spectacle prop + per-player claim |
| `BossEngine/.../fx/CombatTheatrics.java` | Timing / telegraph taste |
| `BossEngine/.../instance/saint/SaintFx.java` | Display push helpers (**do not copy the failed TRS decompose**; use `setTransformationMatrix`) |
| `AetherionItems/.../combat/DamageNumbers.java` | Combat feedback already shipped |

### Likely touch targets for Phase-1 polish

- Extend `LivingNpcAtmosphere` (or sibling) for Egon / Forager / Foreman / Ledger  
- Enhance `StarterGearReward` grant moment  
- Upgrade `TutorialQuestTrail` visuals  
- Thin presence helper used by `DialogManager` / `LivingNpcProfile.say`  
- Polish `JoinWelcomeListener` + Hub `starter-hint` into one harbour beat  
- Optional: discover / explore-chest FX  
- UTF-8 copy fixes where hints are garbled  

---

## Existing helpers / APIs

- `QuestManager` — track / complete / enforce single quest  
- `QuestHint.show` / `remember` / `clearPending`  
- `QuestFeedback.playAccept` / `playComplete`  
- `LangPack` / `PlayerLang` — localized strings  
- `HubService` unlock / spawn resolve  
- `CustomItem.createSimplePickaxe` / `createSimpleAxe` / starter set  
- `GuideAdvice` — tutorial tips for `/guide` + Discord  

Prefer **extending** these over inventing parallel quest/HUD systems.

---

## Constraints (hard)

### Never change unless Robbi asks
- Blossom Blade / Gravwell Cleaver abilities & VFX  
- Booster stackability, lore-stat rule, socket anvil GUI  
- Rank / Admin UUID / TAB dyes  
- Shutdown countdown  
- Hanging Saint fight + Seraphine loot / music box  
- `QuestStoryGate.tutorialDone` quest set / wholesale quest ID rename  
- Harbour soft-wall **intent** (coords only if Robbi supplies new points)  
- Grow `AetherionCore` into gameplay  

### DisplayEntity note
If using BlockDisplay / ItemDisplay matrices: use **`setTransformationMatrix(m)`**.  
Do **not** reintroduce manual `getScale`/`getNormalizedRotation` decompose (broke Seraphine Hand/strings).

### Process
- Default: push to **`claude/early-game-harbour`**; Robbi deploys  
- Deploy to mmor/Crafty only if this environment already has a working path — do not invent credentials  
- No “while I’m here” mid/late-game systems  
- No full-server balance pass  

---

## Suggested creative freedom

Names, ambient motifs, kit-ceremony fantasy, trail look, arrival soundscape — **yes**, as long as they feel like **Anker Harbour / orientation**, not a random neon lobby and not a second Seraphine.

Tone already in the world: dry harbour humor (Egon, Vex, Ledger). Match that voice; don’t paste epic boss monologue onto the pier.

---

## Definition of done (for Opus build later)

Cold account, first hour:

1. Arrival feels intentional (not generic plugin title).  
2. Path to Egon → Forager → kit is obvious and *pretty*.  
3. Kit grant feels like a moment.  
4. Spine NPCs feel slightly alive.  
5. Quest graph and gates still behave as today.  
6. No locked systems touched.  

Then **STOP** — commit/push if that’s the Cloud contract; leave deploy to Robbi.
