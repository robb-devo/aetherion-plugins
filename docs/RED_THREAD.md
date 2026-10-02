# Soft red thread — early game + skill isles (AetherionQuests)

A content and flow pass on the first hour, inside AetherionQuests only. The first-hour spine was already solid, so most of it is **kept**. This pass **elevates** the one weak moment, graduation (it only pointed at Vex), and **adds** crumbs so Forage, Farm and Fishing can be found from places players already walk through. Nothing new is locked. The only new gate is a one-time gear check at Vex, and players can talk straight past it.

TalkUx shell, Life, Origin, Borderlands content, weapons and boosters are untouched. Quest ids, NPC ids, rewards and quest state are unchanged, and no quest was added or removed.

## Spine: before → after

**Before**
- Egon → Forager (oak) → Egon (kit) → Quartermaster (coal) → [Craftsman] → Shaft Foreman → Temper → Miss Ledger (Skills) → Farmer + Lark → Miss Ledger stamps.
- The stamp's only pointer: *"Sergeant Vex at the Borderlands gate, if you're curious."* The compass went to Vex.
- Isles:
  - **Farm Isle:** one optional line from the Farmer.
  - **Forage pad:** a one-time auto-hint the first time a graduate walked through the harbour. It replaced whatever compass target they had.
  - **Fishing:** nothing pointed to Tackle. You found him by walking to the end of the pier.
- Vex: *"Ten hostiles… Now move."* The gear advice was one line in the middle.

**After**
- The same spine, with a crumb only where a player is standing anyway:
  - **Forager** (completed chatter): *"Free afternoon? Twig's slime pad is just past me. Forage Isle."*
  - **Egon** (after the coal run): *"Pier's quiet. Tackle fishes off the end, if you fancy a rod."*
  - **Miss Ledger** (fields brief): *"Back to me after both. Then I show you where skills really grow."* This sets up the payoff. Her "What's left for me?" topic says the same.
- **Graduation = the roads.** Ledger stamps and says *"Skills grow out there. Not at my desk."* Then reply chips appear:
  - **Forage Isle · Twig**
  - **Farm Isle · Harrow**
  - **Fishing · Tackle**
  - **Combat · Vex** (becomes **Bosses · Rite Warden** after Vex, and disappears after the Rite)
  - **I'll wander.**

  Picking a road gets one line from Ledger, one compass target and one action-bar tip. The compass starts on a sensible default (Tackle if his lesson is open, otherwise Twig), so it's never blank. *I'll wander* clears it.
- **Ledger desk:** a new first chip, **Where next?**, reopens the roads at any time. This is also how players who graduated before this ship get the roads.
- **Harbour pad crumb** (post-graduation, once):
  - no current road → it takes the compass, as before
  - already on a road or a quest → one chat line only
  - already met Twig or already pointed there → silent
- **Road hints end on arrival.** Reaching Twig or Harrow drops the bossbar. Tackle's hint hands over to the quest bar when you take his lesson.
- **Vex readiness:**
  - His intro now carries the checklist: *"Armour on, blade in hand, food in the bag. Boosters help."*
  - With in-world talk on, an under-geared player gets one **gear check** on the first click:
    - fewer than 2 armour pieces → *"No armour. Bold. Also stupid."* + Egon's kit
    - no booster yet → *"Blade: plain."* + Temper
  - Two chips follow: **I'll gear up.** / **Ready anyway.** (the second goes straight to his offer).
- **/guide tips** now have German text. The Temper tip had the location wrong ("at the harbour"); it now says "on the road to Capital". The last tip says Ledger "shows you the isles".

## Walls

- **New:** none.
- **Vex gear check (soft):**
  - plays once per player (`vex_gear_check`)
  - only when armour or the booster is missing
  - only with in-world talk on (classic mode and QA bots skip it)
  - clicking Vex again, or *Ready anyway*, always gets the offer
- **Unchanged:**
  - Ledger's Skills gate (Foreman first)
  - Tackle waits for Egon's wood
  - post-tutorial NPCs wait for the stamp
  - Rite Warden gate
  - Farm Isle portal (Farming 10, isle plugin)

## Kept / elevated / added

| | What |
|---|---|
| Kept | Spine order and every quest; Egon↔Forager bubbles, chop demo, kit ceremony; Temper → Ledger → Fields order; all gates; Farmer's Harrow peek; Vex after-lesson brief; chest desk behind *More topics…* |
| Elevated | Graduation (Vex-only → 4 roads + wander); harbour pad hint (no longer overrides your compass); Vex intro (checklist); /guide (DE, Temper fact) |
| Added | Forager → Twig and Egon → Tackle crumbs; Ledger payoff line + topic; the *Where next?* desk chip; Vex gear check; road hints that end on arrival |
| Moved | Ledger desk *Boosters* chip → *More topics…* (TalkUx shows 5 chips max; Temper teaches boosters) |

## Files

| File | Why |
|---|---|
| `ui/SkillRoads.java` (new) | Road picker, default road, point-at-one-road, forage flag |
| `listener/NpcListener.java` | Graduation → roads; desk *Where next?*; Vex gear check; Twig tip in DE + flag; Ledger payoff line |
| `ui/QuestHint.java` | `pendingNpc()`; road hints end on arrival; Vex / Rite / Tackle pins hand over to the quest bar |
| `util/QuestStoryGate.java` | `guideTips` in DE + Temper fact; `combatReadiness()` |
| `listener/ForageHarbourHintListener.java` | Respects the current road; DE; silent if Twig already met |
| `dialog/DialogManager.java` | Vex intro checklist; Egon after-coal → Tackle; Forager completed → Twig |
| `npc/CastBook.java` | Ledger *What's left for me?* topic |
| `resources/lang/de.yml` | +56 keys, 5 changed, 0 removed |

`ledger.hint_vex`, `hint_rite`, `hint_help`, `ui.tip.vex_soft` and `ledger.desk.boosters` are no longer called from the in-world path. They stay in `de.yml` for rollback.

## In-game test plan

**Fresh (`/aquest reset all` or an alt)**
1. Arrive. Egon's bubble → Forager → chop → Egon kit. Nothing new happens here.
2. Click the Forager again → the second line mentions Twig's pad. The compass doesn't move.
3. QM coal → click Egon → *"Tackle fishes off the end…"*. Tackle offers his lesson (not blocked).
4. `/guide` at each step shows exactly one "Next:". At Temper it says "on the road to Capital".

**Mid-tutorial**
5. Walk to Vex before Temper, wearing the kit:
   - *"Gear check. Armour: fine. Blade: plain."* + Temper line + chips
   - *I'll gear up.* → compass on Temper
   - *Ready anyway.* → normal Vex intro + offer
   - click again later → straight to the offer (the check is gone)
6. Same test with the armour taken off (alt) → the *"No armour"* variant.
7. `/npctalk classic` on an alt → Vex offers immediately, and the intro has the checklist line.
8. Ledger fields brief ends with *"Then I show you where skills really grow."*

**Post-Ledger (graduation)**
9. Fields done → Ledger stamps → *"Skills grow out there…"* → *"Wood, wheat, fish — or steel…"* → 5 chips. The bossbar already points at Tackle (or Twig if his lesson is done).
10. Pick *Farm Isle · Harrow* → Ledger line, bossbar → Harrow, green action bar. Walk to Harrow → the bossbar drops.
11. Click Ledger again → desk chips *Where next? · Aetherion Manager · Skills · Pets · More topics…*. *Where next?* → roads again. *I'll wander.* → bossbar cleared.
12. Pick *Fishing · Tackle* → take his lesson → the quest bar takes over (no double pin). After the catch, *Where next?* → Fishing line says *"Ask him where the big ones went."*
13. With the Twig road picked, walk through the harbour → no second harbour pad hint.
14. With Vex picked as your road, walk through the harbour → one chat crumb about the pad, and the compass stays on Vex.
15. Finish Vex → the desk road reads *Bosses · Rite Warden*.

**DE (`/sprache de`)**: repeat 5, 9, 11 and 13 → German lines, chips and action bars.

**Regression**
- QA/stress bots: Vex offers through the chest on the first click.
- The chest desk (*More topics…*) still lists Boosters.
- An existing graduate gets no graduation replay; they reach the roads through the desk.
