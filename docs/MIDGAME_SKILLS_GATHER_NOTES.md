# Midgame Skills + Gathering — finish notes

**Branch:** `feature/midgame-skills-gather` · **Base:** `ddcdc7c`
**Built in Cloud?** No. Parse-checked only (javac parser, no Paper jars). Robbi compiles locally.

```
mvn -pl AetherionCore,AetherionItems,AetherionFishing,AetherionForaging,AetherionFarming -am package
```

> **Deploy all five jars together.** `AetherionCore` gained `QuestBars.suppress(player, owner)` /
> `QuestBars.release(uuid, owner)`. New Fishing/Foraging/Farming jars on an old Core jar =
> `NoSuchMethodError` the first time a bar opens.

Out of scope and untouched: Dungeons, BossEngine, Blossom Blade / Gravwell Cleaver, boosters /
vials / anvil, ranks / admin / OP / dev-menu ranks, shutdown countdown, TAB, casino.

---

## The loop grammar (why the three loops now feel like one family)

| Beat | Fishing | Foraging (fell) | Farming (birds) |
|---|---|---|---|
| Telegraph | bar fills as the biter closes in → `ready…` + tick, wake trail | trunk glows, `ready` + creak two cells before the window | `Birds circling…` bar, wisps wheel down, wing beats |
| Interact | strike bar, gold **perfect** cell reads `NOW` | fell bar, gold **perfect** cell reads `NOW` | click birds; **Bold Crow** hops once |
| Result | `On the line.` / `Perfect reel.` · miss says early/late + streak lost | `Clean fell.` / `Perfect fell.` · miss says early/late/too slow + streak lost + cooldown | `Field clear` / `Clean sweep` · fail says how many were pecking |
| Reward | action bar: result · `✦streak` · focus Fishing skill bar | tally after the canopy: result · `✦streak` · `12/14 spruce` · focus Foraging skill bar | title + boost; reward line with focus Farming skill bar |
| Growth | streak 5 = hot water (+1 lure fish, +1 green cell) | best Foraging Lv 50 = +1 cell; streak 5 = +1 cell | Farm Isle flock; boost +5s per Farming rarity tier |

Shared vocabulary: `Label  [──██▓██──]  WORD`, green window, gold middle cell, `◆/◇` marker,
`✦N` streak tag (yellow, gold from 5). Streaks break on a miss and cool off after 2 idle minutes.

HUD: each loop takes a **named QuestBars lease** (`fishing`, `fell`, `bird-scare`). The quest bar only
returns when every lease is released; legacy unsuppress (bosses/dungeons/pet catch) is ignored while a
lease is held. Before this, casting a rod inside a bird event re-showed the quest bar over it.

---

## What changed

### Skills (AetherionItems)
- **`/skills` menu rebuilt** (`SkillMenu`): header row = Loadout Readout (live totals of equipped
  effects), Your Standing (level/rank/next rank), Next Slot (lifetime-coin progress bar), Field Guide
  (rules, rarity ladder, curve stages). Loadout row states: Empty (item frame) / **Next** (iron
  trapdoor + progress) / Locked. **Rarity strip** under each slot in the skill's rarity color.
  Category icons: what they level from, best skill, how their stats touch the loop. Pool lore:
  equipped state, stage, level bar, next rarity + XP to go, every value with `→` preview at the
  next rarity, gathering "in the loop" line. Frame panes tint to the open category. Clicks
  re-render in place (no reopen flicker) and every click has a sound. Default page = first
  equipped skill's category.
- **Moments** (`SkillService`): level-ups coalesce per tick into **one** chat line (`▲ Rock Whisper 12 ·
  Quiet Pride 8`) + one headline: **Mastered** › **new rarity** (title, toast, rarity-dust ring) ›
  **stage** (Journeyman at 50, Master at 75). **Slot unlocks** from lifetime coins now announce
  (title + clickable `[Open Skills]` + next mark); existing players are measured silently first
  (`seenSlots` in `skills.yml`, `-1` = unmeasured).
- **Proc identity**: ledger compact procs are credited by chance share (a Voided pick at 100% stays
  quiet) — soft bundle click every time, named chat line max once a minute. Boss Grudge: one line on
  the first hit of a new named boss (not in dungeons, not set minions). Blood Tax coin line tagged.
  Cave Sense: one action-bar line when the dark starts paying (2 min cooldown).
- **Progression clarity** (`SkillProgression`): `Stage` Apprentice 1–49 / Journeyman 50–74 /
  Master 75–99 / Mastered 100, `nextRarityLevel`, `xpUntil`, `levelFill`, `miniBar`.
  **Curve numbers unchanged** — Wave-2 intent (early modest, late strong) felt right; the problem was
  that nobody could see it. Doc comment corrected (rarity-inclusive values).
- **Gathering API** for the loops: `focusSkill`, `progressLine`, `loopCredit`, `grantGatherBonus`,
  `coloredName`, `coinSlots`, `nextSlotCost`, `lifetimeCoins`.
- `/skills <category>` jumps to a page (tab-completes). Admin `/skills xp <player> <skill> <amount>`
  feeds one skill through the normal level-up path (for testing moments). Fixed copy: "Next rarity at Lv. 10" (it's 20),
  "All six skill slots" (seven), `setlevel <1-60>` (1–100).

### Fishing (AetherionFishing)
- `StrikeBar`: gold perfect cell, `NOW`, streak tag, approach `ready…` title.
- `FishingController`: perfect reel (+4 Fishing XP, chime + wax sparkle), catch streak (+1 XP per two,
  cap +3), hot water at 5, approach telegraph from real biter distance, reward beat one tick after
  Items pays the catch, one-time "equip a Fishing skill" tip, readable misses. Re-casting no longer
  wipes the last reward line.
- `FishingEncounterListener`: encounters surface after ~1.2s of boiling water instead of popping in;
  kill beat credits the Fishing skill.
- New: `CatchStreak`, `FishingSkills` (NoClassDefFoundError-safe bridge to Items).

### Foraging (AetherionForaging)
- `ForagingStrike` / `FellPulse`: same bar shape, perfect cell, `ready` tell (direction-aware),
  early/late/too-slow reasons (direction-aware — the marker bounces).
- `ForagingListener`: perfect fell (+12 Foraging XP), fell streak (+2/step, cap +8), window +1 at best
  Foraging Lv 50 and +1 on a hot streak, tally after the canopy lands (wood paid vs cap + type),
  Canopy Cleaver fells tally too (no timing bonus), miss explained on the action bar (chat only on
  the first miss per boot), world change cancels the bar without a miss, shutdown releases jobs,
  expired miss locks / cleaver cooldowns pruned alongside idle tree jobs.
- `ForagingFx`, `ForagingHud`: new beats, named lease. Tutorial demo (`ForagerChopDemo`) untouched —
  old 5-arg `striking` and `success(player, at)` kept.
- New: `FellStreak`, `ForagingSkills`.

### Farming (AetherionFarming)
- `BirdScareEvent` rebuilt around Telegraph → Interact → Result → Reward (see table).
- **New beat: Bold Crow** — gray named parrot; first shoo makes it hop elsewhere on the field, second
  shoo sends it off. Off-hand double events no longer count as two hits.
- **Clean sweep** (clear with ≥50% timer left): +30s boost, +6 XP.
- **Farm Isle flock** (midgame): 3–5 birds, always a Bold Crow, 14s timer, 75s base boost.
  Toggle: `bird-scare.farm-island.enabled` (default true, documented in `config.yml`).
- Peck ambience (crumbs + sound) shows the threat without a debuff; bar turns red for the last 4s.
- Helpers get +12 Farming XP (+6 sweep) and a reward line with their Farming bar; boost +5s per
  Farming rarity tier; 10s-left heads-up before it fades.
- Hygiene: spawn/expire tasks stored + cancelled, host quitting no longer ends it for everyone
  (empty fields end quietly after 2s), per-tick nearby cache, visibility sync every 10 ticks,
  bars every 2.
- New: `FarmingSkills`.

### Core
- `QuestBars`: owner-keyed leases (see HUD above). Old API kept and behaves the same unless a lease
  is held.

---

## How to test in-game (after local `mvn` + deploy of all five jars)

**Skills**
1. `/skills` — header row (spyglass / star / tripwire hook / book), loadout row, colored strip,
   categories, pool. Hover every header item. Click categories (page-turn sound, frame tint).
2. Equip / unequip from pool and from loadout (chain / leather sounds, chat line, strip updates,
   pool jumps to the unequipped skill's page). Fill all unlocked slots → pool says "No free slot".
3. New admin helper runs the real level-up path: `/skills setlevel <you> rock_whisper 19`, then
   `/skills xp <you> rock_whisper 500` → one `▲` line + **Uncommon** title + dust ring.
   `setlevel … 49` + `xp … 2000` → Journeyman line. `setlevel … 99` + `xp … 13000` → Mastered.
4. `/skills wipe <you>`, equip a Utility + a Mining skill, mine ore → when both level on the same
   block they share **one** `▲` line (no more one line per skill).
5. Slot unlock: `/skills reset <you>` then `/skills unlock <you>` → "SLOTS 2–7 OPEN" title +
   `[Open Skills]` click works. (Real path: earn past 5,000 lifetime coins on a fresh account.)
6. Pack Rat / Timber Tax equipped, farm a while → soft bundle click on compacts, named line ≤1/min.
7. Boss Grudge vs an overworld boss → exactly one line on first hit. Blood Tax kill → coin line tag.
8. Cave Sense underground → one action-bar line, then silence for 2 minutes.
9. `/skills fishing` opens straight on Fishing; tab-complete shows categories.

**Fishing**
1. Cast: wait bar → approach bar fills, turns yellow `ready…` with a tick → `Bite!` subtitle mentions gold.
2. Reel on gold (`NOW`) → `Perfect reel.` + chime; reel on plain green → `On the line.`
3. One tick later the action bar shows the focus Fishing skill bar; no Fishing skill → one tip in chat.
4. Five clean catches → "Hot water" line, `✦5` gold tag, next strike has one more green cell.
5. Miss early / late / timeout → reason + "streak ✦N lost".
6. Catch until an encounter triggers → "line's still pulling", bubbles ~1.2s, then the mob.
7. While fishing, stand in a bird event → both bars on screen, quest bar never pops back until both end.

**Foraging**
1. Chop a marked trunk with an axe → trunk glow + knock; creak right before the green each pass.
2. Hit gold → `Perfect fell.` + strip sound; after the tree is down, tally `12/14 oak │ Woodwise …`.
3. Miss → action bar reason + cooldown; chat explanation only the first time.
4. `/skills setlevel <you> woodwise 50` → next fell bar is one cell wider. Streak 5 → another cell.
5. Teleport away mid-bar → bar disappears, no miss lock on that tree.
6. Forager tutorial demo still plays (bar shape now shows the gold cell; no streak tag).

**Farming**
1. Harbour farm (or lower `bird-scare.interval-ticks` to 1200 for testing): `Birds circling…` bar,
   wisps descend, birds land.
2. Clear quickly → `Clean sweep`, boost seconds in title; slowly → `Field clear`.
3. Bold Crow: first click hops it (`The crow disagrees`), second removes it.
4. Let it time out → "The birds left · N still pecking"; no debuff.
5. Farm Isle (Farming 10 portal) → larger flock, `Farm Isle` label, always a Bold Crow.
6. Host logs out mid-event → event continues for others; everyone walks away → ends quietly.

---

## Residual risks

- **Not compiled in Cloud.** Parse-clean; API names checked by reading existing usages. Most likely
  compile nits if any: `World#spawn(Location, Class<? extends LivingEntity>, Consumer)` in
  `BirdScareEvent` (same pattern as fishing's 4-arg spawn), `Particle.DUST` + `DustOptions` ring,
  `Sound.BLOCK_AMETHYST_BLOCK_RESONATE` / `ITEM_BUNDLE_INSERT` / `ITEM_AXE_STRIP`.
- **Jar set:** Core must ship with the other four (new `QuestBars` methods).
- **XP pace:** skilled fishing now earns up to ~+90% Fishing XP per catch (perfect + max streak), fell
  ~+40%. Constants live at the top of `FishingController` / `ForagingListener` / `BirdScareEvent`.
- **Perfect cell is one cell (≈100 ms).** High-ping players will hit it less; it is bonus-only, never
  required.
- **Legacy unsuppress is ignored while a lease is held.** Correct for overlaps; only a problem if a
  gather plugin ever fails to release (all three release on quit, world change, disable).
- **Farm Isle bird events** depend on mature crops within 18 blocks of a survival player in the isle
  world. `bird-scare.farm-island.enabled: false` turns it off.
- **Level-up lines arrive one tick later** (coalescing). They still land after quest reward blocks.
- `ScarecrowEvent.java` is listed in the context pack but does not exist on this branch; the Bold Crow
  fills the "one new beat" slot for farming.

## Left for a small polish pass

- Scarecrow trophy: on a Clean sweep, a temporary scarecrow (armor stand) on the field for the boost
  duration that also keeps the next flock off that field.
- Persist catch/fell streaks across relog (currently memory-only by design).
- Mining veins: reuse the `loopCredit` action-bar tail on vein completion (no HUD there yet).
- Per-skill "first equip" flavor line (one-time) and a `/skills` sort toggle (level vs. enum order).
- Consider a `QuestBars` quit sweep in Core if a future HUD owner forgets to release.
