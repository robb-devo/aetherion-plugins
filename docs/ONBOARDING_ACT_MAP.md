# Onboarding — act map (2026-10-15)

**What Aetherion is (the audit, in one breath):** one big hand-built island ringed by skill isles you reach by launch
pad. Every activity is a minigame that feeds a skill and one Aetherion Level. The Nether Star Manager is the whole
toolkit: skill loadout, Anvil boosters, Codex, Teleports, pets, recipes. NPCs talk in-world (bubble + reply chips).

**Old spine (killed):** wood → 20 coal → 32 ore → booster → skill → 48 wheat → pet → stamp. Five vanilla chores over
four regions, and the loadout and Codex were never taught.

| Act | Beat | NPC · quest id | Teaches | Done when |
|---|---|---|---|---|
| **I · Anker Harbour** — *arrive* | 1 | Egon · `welcome_aboard` | TalkUx: bubble, reply chips, the yellow bar | talk to the Forager |
| | 2 | Forager → Egon · `gather_wood` | the **Fell bar** (green window, NOW, streak): gathering here is timing | 10 oak to Egon |
| | 3 | Egon (kit ceremony) | gear has stats; Nether Star = Manager; **Teleports** (Capital unlocked as reward) | — |
| **II · The Capital** — *build* | 4 | Miss Ledger · `lesson_manager` | the **skill loadout** (glowing star, blinking Skills) + the **Codex** (this tracks your life, tiers pay coins, coins open slots); Lv 5 = auto pickup | 1 skill equipped + Codex opened |
| | 5 | Temper · `lesson_boost` | **boosters + Anvil sockets**; Mining Power / Fortune / Crystal Find / Dungeon Core in one line; *socketed boosters stay until you pull them* | 1 booster fused (Mines teleport as reward) |
| **III · The Roads** — *go* | 6 | Sgt. Vex · **`lesson_combat`** (new) | **COMBAT STUB** — "how you fight here" placeholder, armour on + blade up | gear check passes |
| | 7 | Miss Ledger · stamp | `/capital` home, walk-in camps (Borderlands), **pads = the roads** | stamp → road picker → pad |

`tutorialDone` = `gather_wood + lesson_manager + lesson_boost + lesson_combat`, **or** the old four
(`lesson_boost, lesson_manager, farm_hand, pocket_zoo`), **or** already stamped. Nobody who was done before stops being done.

**Moved off the spine (still live, open after the stamp):** Quartermaster coal, Foreman shift (copy is now *gather*),
Farmer wheat, Lark pets, Tackle fishing. During orientation their NPCs defer to the spine, so a side quest can't abort
the spine quest.

**Combat slot (Sat):** quest `lesson_combat`, NPC `vex` (Borderlands gate 161 58 73), code
`AetherionQuests/.../tutorial/CombatTeach.java`. Today's objective is `COMBAT_READY` (gear check).
Every `TODO(combat-sat)` marks where the drill goes: telegraph / dodge / guard objectives fed by
`QuestProgressHook.noteUsed(player, "COMBAT_…")` from the Items combat system. Config `onboarding.combat-teach.live`
flips the stub lines to the real drill.

---

## Acts IV–VII · the skill isles (one act per isle, any order)

**Audit (cold path, stamp → road → isle):** the road picker offered Forage / Farm / Fish / *Steel* — no Mine road, and
the Mining pad said *sealed*. Forage went via Twig (a 3-question desk), Farm via Harlow, whose "Farming 10" gate is a
lie (the slime pad has no gate), Fish via Tackle's harbour chore (5 fish) *before* the isle. On landing every isle did
the same thing: one grey chat line and an arrow to a desk. Miss Canopy opened a chest of tooltip text. Nobody ever
had to *do* the isle's loop, so nothing was learned.

**Now:** Ledger's picker = the four isles (✓ once done). Each road points straight at its pad. Each isle runs its own
lesson, owned by the isle plugin, taught by its lead NPC in the bubble. The lesson only moves when the player *does*
the loop. Same five beats everywhere, isle-native content:

| Beat | What happens |
|---|---|
| Land | Title card (isle + what it is) · **lesson bar** (quest frame, isle icon) with arrow + metres to the teacher |
| Hook | Teacher, bubble, 2 lines: what the isle is + the job. Chips: *Show me.* / *the why* (stakes in one line) |
| Do | The isle's real loop. The bar counts live; a miss is shown, never a fail. |
| Find | The isle's signature find is armed for the next action (guaranteed). Lost? It re-arms. |
| Close | Back at the teacher: the skill in one sentence, reward, *next* + *way home* chips. Then a plain desk forever. |

During the lesson the isle's other cast defer to the teacher (one line + arrow). Players who already worked the isle
before (legacy rule per isle below) never see it. Finishing sends `ISLE_LESSON_<ISLE>` to Quests (road ✓).

| Act | Isle · teacher | Do (completes only on the loop) | Find | Close: one sentence · next |
|---|---|---|---|---|
| **IV** | Forage · **Miss Canopy** | **3 isle fells in a row** on the green; a miss resets the count | Crown Shaker armed → next fell drops a **Crown Find**, catch it | "Green, green, green — the forest pays the streak." · Pell's Lumber Board |
| **V** | Farm · **Elsie** (Warden) | **Harvest Rhythm to Steady** (20 in time, bar drains when idle) | next harvest pops a **Prize Crop**, click it before it wilts | "A rhythm, not a chore." · Hattie's orders · Farm Isle teleport unlocked |
| **VI** | Fish · **Maren** (Harbourmaster) | **3 catches in a row** = Warm water; a lost fish resets | next bite is **Something heavy!** (a rare, narrow window, re-forced until landed) | "Patience, then one honest pull." · Odile's Log |
| **VII** | Mine · **Old Wick** (Lampwarden) | reads your **⛏ Mining Power** vs coal 8 / copper 14 / iron 24 (spare Mining Pickaxe ⛏14 if you're short) → **Strike Rhythm to Steady Pick** | next ore strike grows a **geode**, crack it | "Power opens the rock, rhythm empties it." · Otto's contracts |

Legacy skip: Forage ≥ 5 isle fells · Farm ≥ 4 plots or any order/prize · Fish ≥ 5 isle catches · Mine ≥ 4 districts or
any contract/Forge rep. Mining pad: open for anyone Ledger has stamped (the blueprint still opens it too).
