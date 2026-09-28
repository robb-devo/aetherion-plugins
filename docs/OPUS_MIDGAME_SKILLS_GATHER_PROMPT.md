# Opus prompt — Midgame Skills + Gathering Minigames

**Repo:** `robb-devo/aetherion-plugins`  
**Branch:** `feature/midgame-skills-gather`  
**Local worktree (Robbi):** `IdeaProjects/_wt_midgame_skills`

Copy **everything below the line** into the Cloud Agent chat.

---

You are doing a **complex midgame pass** on Aetherion (Paper **1.21.1** monorepo).

## CRITICAL — read before you do anything

**Do NOT compile. Do NOT run Maven. Do NOT `mvn package` / `mvn install` / `mvn compile` at the start (or as a gate to begin work).**

Why: `repo.papermc.io` is blocked in this Cloud sandbox. The last two Cloud sessions burned time and failed early on Maven/Paper download errors. That is expected. It is **not** your problem to fix.

How you work instead:

1. Open the files listed below and start implementing.
2. If you want a sanity check, use **syntax / type-check only** against whatever JDK sources are already cached — optional, not required.
3. Robbi builds and deploys locally after you push. Your job is **code + commits + push + finish notes**.
4. Do **not** block on “BUILD SUCCESS”. Do **not** spend the budget debugging Maven mirrors.

Also: **Do not deploy or restart production.**

---

Two linked jobs. Both matter. Spend the budget on depth and feel, not on new plugins or dungeon work.

## Job A — Skill system overhaul (`AetherionItems`)
## Job B — Gathering minigame polish (`AetherionFishing` + `AetherionForaging` + `AetherionFarming`)

Quality floor: Early Game Full Pass tone + World Eater “this is ours” bar — but for **progression UI and loop feel**, not bosses.

First read (short):

1. `docs/OPUS_MIDGAME_SKILLS_GATHER_CONTEXT.md` (this branch)
2. Then only the hot files listed there / below

---

## You are NOT here to

- Run Maven / full compiles / Paper dependency installs (see CRITICAL above)
- Touch **dungeons** (`AetherionDungeons`) in any way — sync/perf is a known mess; out of scope
- Build or polish bosses (`BossEngine` directors, World Eater, Hollow, Seraphine, Ashen, …)
- Touch **LOCKED** systems:
  - Signature weapons: **Blossom Blade** (`ashen_katana` / AshenKatana*) and **Gravwell Cleaver** (`gravwell_cleaver`) — abilities, VFX, combat feel, tuning
  - Boosters / Borderlands vials / Custom Anvil (`BoosterSocketMenu`, stack rules)
  - Rank / Admin / OP separation / Dev Menu ranks / `player-ranks.yml` / special-rank dyes (Monkey, Citrus, MVP++)
  - Shutdown countdown in Core
- Grow `AetherionCore` into skill or minigame ownership (thin shared API only if truly duplicated)
- Replace TAB / rewrite the permanent player UI (already polished)
- Casino rewrite (optional tiny consistency only if you already touch shared HUD helpers — not a focus)
- Deploy, restart, or “fix on production”
- Drive-by refactors of unrelated plugins

## You ARE here to

### A) Skills that feel like a real midgame identity

Existing spine (keep architecture, raise the product):

| Piece | Path |
|---|---|
| Skills enum + bonuses | `AetherionItems/src/main/java/de/aetherion/items/skill/AetherSkill.java` |
| Progression / rarity / curves | `…/skill/SkillProgression.java` |
| Flavor lines by tier | `…/skill/SkillFlavor.java` |
| Service / unlocks / loadout | `…/skill/SkillService.java` |
| GUI | `…/skill/SkillMenu.java` |
| Account level / XP bar | `…/skill/AetherionLevel.java`, `AetherionXpBarSync.java` |
| Tool caps | `…/item/SkillToolCaps.java` |
| Command | `…/command/SkillCommand.java` |

Goals (creative license — pick what earns its keep):

1. **Menu** — `/skills` should feel premium: hierarchy, rarity presence, clear equipped vs pool, unlock path readable without a wiki. No generic “skyblock skill GUI”.
2. **Identity** — each equipped skill should announce itself when it matters (subtle proc feedback, rarity beats, level-up moments). Don’t spam ActionBar every hit.
3. **Progression clarity** — players should feel early → mid → late without reading constants. Tune curves only if the *feel* is wrong; document why. Don’t nuke Wave-2 intent (early modest, late strong).
4. **Loadout fantasy** — seven slots, first free, Legacy coins unlock rest — keep the economy hook; make unlock/equip moments land.
5. **Gathering skills** — if combat skills outshine gather skills in UI/feedback, close that gap so Mining/Farming/Foraging/Fishing skills feel first-class in the same menu.
6. **Technical quality** — no broken click maps, no duplicate listeners, no XP double-grant, no HUD fighting QuestBars/boss bars.

### B) Gathering loops that feel intentional (minigame polish)

| Loop | Plugin | Hot files |
|---|---|---|
| Fishing cast / strike | `AetherionFishing` | `FishingController`, `CastSession`, `StrikeBar`, `FishingHud`, `LureSchool`, encounter tiers |
| Foraging chop / fell | `AetherionForaging` | `ForagingStrike`, `FellPulse`, `ForagingHud`, `ForagingFx`, habitats/rites if they serve the loop |
| Farming events | `AetherionFarming` | `BirdScareEvent`, `ScarecrowEvent`, crop listener; isle ambience only if it serves the event |

Goals:

1. **Same quality bar across three loops** — telegraph → interact → succeed/fail → reward beat.
2. **Readable HUD** — bossbar / actionbar / particles must not fight each other or TAB. Use `QuestBars.suppress` while an event owns the bar.
3. **Juice with restraint** — success earned, failure readable. No boss-scale display spam.
4. **Performance** — light ticks, no leaks on quit/world change/disable.
5. **Midgame relevance** — after Open Roads / harbour, these loops should feel like growth, not tutorial leftovers.
6. **Optional light expansion** — one new beat or variant per loop is OK; don’t invent a fourth gathering plugin.

Mining veins (`AetherionMining`) — **light** pass only if sharing HUD/feedback patterns; no full veins redesign.

---

## Creative freedom

Invent presentation, copy, timing, VFX vocabulary, menu layout, and small systemic hooks **inside** Skills + these three gather plugins.

Stay Aetherion: harbour / work / dry humor / earned power — not generic MMO skill trees, not casino neon, not dungeon spectacle.

If two ideas conflict: **clarity > spectacle > novelty**.

---

## Process

1. Read the CRITICAL block. Skip Maven.
2. Skim context + hot files; don’t ingest the whole monorepo.
3. Stay on branch `feature/midgame-skills-gather`.
4. Implement A and B (order is yours).
5. Commit focused; **push** the branch.
6. Finish note:
   - what changed (skills / fish / forage / farm)
   - how Robbi tests in-game after local `mvn` + deploy
   - residual risks
   - anything left for a small polish pass

## Done means

- Skills menu + equip/level moments feel like a product
- Fishing, Foraging, Farming events feel like one family of loops
- No dungeon/boss/LOCKED regressions
- Branch pushed with finish notes
- **You did not require a successful Maven build**
- **No production deploy**

Go make the midgame feel like Aetherion owns it.
