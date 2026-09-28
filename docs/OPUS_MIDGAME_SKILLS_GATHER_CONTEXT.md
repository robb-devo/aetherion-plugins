# Midgame Skills + Gathering — Opus context pack

**Repo:** `https://github.com/robb-devo/aetherion-plugins`  
**Branch:** `feature/midgame-skills-gather`  
**Base:** `origin/main` @ `ddcdc7c`  
**Mode:** Cloud Agent implements. Cursor prepared this branch + docs.  
**Build:** Robbi only. Cloud: **no Maven** (Paper repo 403 in sandbox).

## Why no Maven

Cloud sandboxes cannot reliably reach `repo.papermc.io`. Previous Opus sessions failed at “compile first”. Skip that. Write code, push, leave compile to Robbi (`mvn -pl AetherionItems,AetherionFishing,AetherionForaging,AetherionFarming -am package` locally).

## Scope

| Job | Plugins |
|---|---|
| A — Skill overhaul | `AetherionItems` (`skill/*`, `SkillToolCaps`, `SkillCommand`) |
| B — Gather minigames | `AetherionFishing`, `AetherionForaging`, `AetherionFarming` |

**Out:** Dungeons, BossEngine fights, LOCKED weapons/ranks/boosters/anvil, TAB rewrite, Core growth, deploy.

## Hot paths

### Skills
- `AetherionItems/src/main/java/de/aetherion/items/skill/AetherSkill.java`
- `SkillProgression.java`, `SkillFlavor.java`, `SkillService.java`, `SkillMenu.java`
- `AetherionLevel.java`, `AetherionXpBarSync.java`
- `item/SkillToolCaps.java`, `command/SkillCommand.java`

### Fishing
- `AetherionFishing/.../FishingController.java`, `CastSession.java`, `StrikeBar.java`, `FishingHud.java`, `LureSchool.java`, encounter tiers

### Foraging
- `AetherionForaging/.../ForagingStrike.java`, `FellPulse.java`, `ForagingHud.java`, `ForagingFx.java`

### Farming
- `AetherionFarming/.../BirdScareEvent.java`, `ScarecrowEvent.java`

### Shared HUD helper
- `AetherionCore/.../api/QuestBars.java` — suppress quest bar while a minigame owns the screen

## Tone

Harbour / work / dry humor. Midgame after Open Roads. Premium loops, not new spectacle bosses.

## Prompt

Paste `docs/OPUS_MIDGAME_SKILLS_GATHER_PROMPT.md` (everything below its horizontal rule) into the Cloud Agent.
