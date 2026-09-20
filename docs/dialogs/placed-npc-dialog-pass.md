# Placed NPC dialog pass

Morning read for Peter. **Only NPCs that are actually placed live** (AetherionQuests `npcs.yml` + FancyNpcs `ae_forage_grove_guide` / `ae_dungeon_keeper` in `world`).

Unplaced registry NPCs (Amethyst guide, gossip stalls, boss-quest cast, `dungeon_gate` Threshold, etc.) were **not** in scope. `mod_smoker` (editor test: “Hello there.” / “stink”) skipped.

## Call sheet

| NPC id | Name | Rec | Issue | Proposed (gist) |
|---|---|---|---|---|
| `egon` | Egon | **trim** | Wordy (“kit man”, sparkle-trail lecture) | Harbour kit. Oak — you fetch it. Forager + chop minigame. Arrow is the compass. |
| `lumberjack` | Forager | **trim** | Already clear | Egon needs oak. Again. Green CHOP. Ten logs to the pier — not my desk. |
| `quartermaster` | Quartermaster | **trim** | Intro long; return visit dumped `/spawns` lore | Forge is hungry. 20 coal, Ore Ridge. Turn-in: `/mines` + Foreman. |
| `craftsman` | Craftsman | **trim** | “Hey. Craftsman.” filler | Crafting unlocked. Recipe Book. Mining Pickaxe recipe. Bring it back. |
| `foreman` | Shaft Foreman | **trim** | Extra clause | Cave behind me. 32 ore. Then Temper. |
| `booster_tutor` | Temper | **trim** | DE 3 lines vs EN 2 | Boosters fuse on gear. Anvil: gear left, booster right. Fuse this Emerald. |
| `ledger` | Miss Ledger | **trim** | Official / long | Skills. Stamp orientation shut. Equip any one skill. Come back. |
| `farmer` | Farmer | **rewrite** (one fact) | DE said Linksklick; live scare is **either click** | Wheat 48. Birds land → **Klick**. Lark after. |
| `lark` | Lark | **trim** | “Hey.” + extra “again” | Hunt wild animals, not the parrot. Sphere, right-click, catch one, then equip. |
| `vex` | Sergeant Vex | **trim** | “Lesson steel: passed. Form still terrible.” try-hard | Ten hostiles. Move. Turn-in: less of a liability. Rite Warden optional. |
| `fisher` | Tackle | **rewrite** (DE) | DE still mentioned **Dock Pass** (removed) | Cast, REEL green, five fish. Egon first. |
| `fishmonger` | Fishmonger | **keep** shop; **trim** unused intro | Click opens shop, no speech. DE had Dock Pass. | Dead intro: Shop. Rods. Armor. Coins welcome. |
| `rite_keeper` | Rite Warden | **trim** | Sightseeing essay; rates must stay | T1 bosses. Vial %s + beacon altar. Powder, thunder, kill once. |
| `arena_proctor` | Proctor | **rewrite** | Cryptic (“people got careful”, “exist too hard”) | Borderlands = practice. Crypt = real. Don't shake it. Hazard pay is a myth. |
| `farm_isle_guide` | Harrow | **rewrite** (DE) | DE was leftover English | Portal → Farm Isle. Farming 10. Same portal back. |
| `forage_pad_guide` | Twig | **trim** | Fine | Slime pad → Forage Isle. Jump. Canopy Clerk wants samples. |
| `eldervale_welcome` | Maren | **keep** (tiny trim) | Already short | Welcome. Mining island. Don't fall off. |
| `eldervale_upgrade` | Forgehand | **trim** | Live click ≠ intro tree | Tool + Upgrade Stone. Desk opens. Ritual: into the frame / still warm. |
| `root_cellar` | Root Cellar | **rewrite** | One long sentence | Compacted crops in. Refined pantry goods out. |
| `merchant` | Merchant | **trim** | Repeated “chests” | World chests spawn everywhere. Rarer = better. Talking unlocks them. |
| `isle_clerk` | Deed | **trim** | Fine | Island at Level 20. Nether Star → Island tab. I don't do paperwork. |
| `bar_whisper` | Bar Whisper | **trim** | Fine | Bottom bar = Aetherion Level. Keep playing. It notices. |
| `vince` | Lucky Vince | **rewrite** | “Hungry? The glass is.” cryptic; EN/DE/casino desync | Four rotating one-liners. Glass is hungrier than you. |
| `liquidator` | Crystal Liquidator | **trim** | Name-repeat | Crystal desk. Buy, sell, melt. Shop or level rewards. |
| `surveyor` | Surveyor | **trim** | “Hey —” | Trolls drop blueprint pages. Bring a page. Stamp it here. |
| `canopy_clerk` | Canopy Clerk | **rewrite** | Forced crypticism (“cover story”, “receipts”) | Isle wood, compressed. Oak + Birch + Spruce. Fat payout. Chop. |
| `ae_forage_grove_guide` | Miss Canopy | **trim** | “Easy mode.” | Hey. Short tour of the isle? Click a topic. |
| `ae_dungeon_keeper` | Dungeon Keeper | **trim** | “still hungers” | The void's hungry. Pick a floor. |
| `mod_smoker` | editor test | **keep** (skip) | Not player-facing | Leave “Hello there.” / “stink”. |

Tone: short, knackig, kid-clear, light item-lore humor. No try-hard crypticism. Gates/hooks unchanged.

## Language (live)

- **English in Java is the default** (`DialogManager`, `NpcListener`, `QuestStoryGate`).
- **`lang/de.yml` overlays** when the player chose German.
- Hard-coded `LivingNpcProfile.say` for these NPCs now goes through `LangPack` (turn-in / escort / Vince / Surveyor / Forgehand / Root Cellar previously stayed English forever).

---

## Before → after

### Egon (`egon`) — **trim**

**Where:** `DialogManager` `egon_intro` + completed; wood turn-in in `NpcListener`.

| | Before | After |
|---|---|---|
| EN | Harbour kit man. I need oak — you're fetching it. / Forager is up the hill… Bring ten oak logs back here. / Yellow arrow… You'll also see a short sparkle trail… | Egon. Harbour kit. I need oak — you fetch it. / Forager up the hill lends the axe and shows the chop minigame. Ten oak logs, back here. / Yellow arrow up top is your compass. Sparkle trail on the ground for this job. |
| DE | Egon. Hafen-Ausrüster. Ich brauche Eiche — du holst sie. | Egon. Hafen-Kit. Ich brauche Eiche — du holst sie. |

Turn-in kept: “Logs received. Kit stays yours — don't lose it.” → Quartermaster next.

### Forager (`lumberjack`) — **trim**

**Where:** `lumberjack_intro`; onboarding / demo / replay in `NpcListener`.

| | After |
|---|---|
| EN | Egon needs oak. Again. / Simple Axe. Left-click a glowing trunk — that's the chop bar. / When it hits CHOP (green), left-click again. Miss it, the tree resets. / Ten oak logs → Egon on the pier. Not my desk. |
| DE | Egon braucht Eiche. Wieder. / … nicht bei mir. |

### Quartermaster (`quartermaster`) — **trim**

**Where:** intro in `DialogManager`; first complete in `NpcListener.briefQuartermasterSpawns` (live turn-in, not the old 4-line teleport lecture).

| | After |
|---|---|
| EN intro | Forge is hungry. Twenty coal from the surface. / Ore Ridge — hill past the little market. Open coal veins. Mine twenty, walk them back. / Yellow arrow up top tracks the job. |
| EN turn-in | Coal logged. Thanks. / Mines teleport's open — Manager → Teleports, or type `/mines`. / Next: Shaft Foreman at the Mines. |
| EN return | Coal's filed. Mines teleport stays open — `/mines`. / Foreman still lives at the Mines if you need him. |

### Craftsman (`craftsman`) — **trim**

**Where:** `craftsmanIntroLines`; gift line in `NpcListener`.

| | After |
|---|---|
| EN | Crafting's unlocked. Recipe Book lives in the Manager. / Nether Star (hotbar 9) → green book. Every blueprint's there. / Craft a Mining Pickaxe: Simple Pickaxe in the middle, coal around it. Bring that pick back. |
| Gift | There it is — Mining Pickaxe. Good work. |

### Shaft Foreman (`foreman`) — **trim**

**Where:** `foremanIntroLines` (backup unlock if Craftsman skipped); escort in `NpcListener`.

| | After |
|---|---|
| EN | Shabby Mine — cave behind me. Dig coal, copper, iron. Break 32 ore blocks, then come back. / Mining pickaxe and some armor help. Recipe Book in the Manager if you still need them. |
| Turn-in | That's a shift. Head toward the hub — stop at Temper (Booster Tutor) on the way. |

### Temper (`booster_tutor`) — **trim**

**Where:** intro; `briefTemperNext`. DE had 3 lines vs EN 2.

| | After |
|---|---|
| EN | Temper. Boosters fuse onto gear — mining power, fortune, damage. / Manager → Anvil: gear left, booster right. Here's an Emerald — fuse it once. Recipes stay in the Recipe Book. |

### Miss Ledger (`ledger`) — **trim**

**Where:** intro; blocked; `briefLedgerNext`; `openLedgerHelpDesk`. Redirects now in `de.yml` too.

| | After |
|---|---|
| EN intro | Miss Ledger. Skills — and I stamp orientation shut when you're done. / Hotbar 9 — Nether Star → Manager → Skills (it blinks). Equip any one skill. / Come back when it's equipped. |
| Blocked | You're early. Skills stay locked until the mine shift is done. / Shaft Foreman at the Mines. Finish his shift — then come back. / Then I unlock the Manager Skills tab. Not before. |
| Graduation | Well done. Orientation's filed — map's yours. / One habit: always watch the yellow arrow up top. |

### Farmer (`farmer`) — **rewrite** (click fact)

**Where:** `farmerIntroLines`; `briefFarmerNext`. Live bird scare accepts **left or right click**.

| | After |
|---|---|
| EN | Wheat from these fields. Birds off the crops. / Break wheat until you have 48. Birds land? Click them — bossbar counts shoos. / Lark is in the same area for pets after this… |
| DE | … Vögel landen? **Klick** — … (was Linksklick) |

### Lark (`lark`) — **trim**

**Where:** intro; mid-quest equip lesson; complete briefing.

| | After |
|---|---|
| EN | Fields are hunting ground. Wild animals — not my parrot. / Hold a Catch Sphere, look at a critter, right-click to throw. Catch one, then talk to me. / After that I'll show you how to equip it. One step at a time. |
| Equip | Nice catch. Pocket's squealing. / Now equip it: Manager → Pets (it'll blink)… |

### Sergeant Vex (`vex`) — **trim**

**Where:** intro; `briefVexNext`.

| | After |
|---|---|
| EN intro | Sergeant Vex. Gate to the Borderlands — wasteland past the wall. / First lesson: ten hostiles. Kill them. Husk, stray, crawler — I don't care which. / Struggling? Combat set from the Recipe Book, boosters on the anvil. Soft gear. Harder hits. Move. |
| Turn-in | Ten down. Steel filed. You're less of a liability. / Soft tip — harden your gear… / Optional next — Rite Warden. |

### Tackle (`fisher`) — **rewrite** (DE)

**Where:** intro; Egon-gate; complete. Dock Pass is gone; shop is always open.

| | After |
|---|---|
| EN | Cast into water. Wait for the bite. REEL when the bar turns green. / Miss the window, the fish leaves. Catch five, then talk to me. |
| DE | Leine ins Wasser… Fang fünf… (Dock-Pass-Satz weg) |
| Gate | Hold up. Finish with Egon first — then we talk fishing. |

### Fishmonger (`fishmonger`) — **keep** shop open, **trim** unused intro

**Live:** click opens the shop. No spoken intro. Polished the dead tree anyway and removed Dock Pass from DE: “Shop. Rods. Armor. Coins welcome.”

### Rite Warden (`rite_keeper`) — **trim**

Drop rates stay. Cut sightseeing.

| | After |
|---|---|
| EN | Rite Warden. T1 bosses. Not a walking tour. / Kill hostiles for Spirit vials: 5% T1 · 10% Sturdy · 15% elites. The beacon pillar is the altar. / Right-click the light-gray powder with a vial. Ten seconds. Thunder. Then it walks. Kill it once — rite passed. |
| Complete | Rite logged. Altar stays. Keep the vials coming. / You're free. Waste, desks, whatever. No leash from me. |

### Proctor (`arena_proctor`) — **rewrite**

**Where:** `arena_proctor_intro` / `_vial` / `_self` / `_done`; escort `say`.

| | After |
|---|---|
| Intro | Proctor. This ring used to mean something. / Borderlands vials are the starter kit. Crypt vials are the real ones. / Bring me a Crypt vial. I'll study it. Carefully. Probably. |
| Vial | There it is. Stronger than the Borderlands stuff. / Come on — we look at the ring… / Don't shake it. Don't sniff it. |
| Walk-off | Nope. Hazard pay's a myth. That's a you problem. Bye. |

### Harrow (`farm_isle_guide`) — **rewrite** (DE)

DE was leftover English.

| | After |
|---|---|
| EN | Portal goes to the Farm Isle — shared fields off the hub farm. / Farming 10 to enter. Same portal brings you back. |
| DE | Portal geht zur Farm Isle — geteilte Felder neben der Hub-Farm. / Farming 10 zum Betreten. Dasselbe Portal bringt dich zurück. |

### Twig (`forage_pad_guide`) — **trim**

- EN: “Slime pad → Forage Isle. Jump. Don't overthink it.” / Trees drop what they look like. / Canopy Clerk wants compressed samples. Follow the arrow.
- DE added (was missing).

### Maren (`eldervale_welcome`) — **keep** (tiny trim)

“Welcome to Eldervale. / Mining island. Deep rock. Don't fall off.”

### Forgehand (`eldervale_upgrade`) — **trim**

**Live click** is `NpcListener` (one line + GUI), not the DialogManager intro. Ritual lines in `BlueprintForgeRitual`.

| | After |
|---|---|
| Click | Forge is hot. Blueprinted tool + Upgrade Stone. I'll open the desk. |
| Intro (if played) | Blueprint forge. Tool plus Upgrade Stone. That's the whole trick. / Tier II, III, IV — stones get expensive. Fast. |
| Ritual | Into the frame — keep an eye on it. / Done — Tier X. Hot off the anvil. / Caught you — Tier X. Still warm. |

### Root Cellar (`root_cellar`) — **rewrite**

No `dialogId`. First visit only.

| | After |
|---|---|
| EN | Millstone Pantry. Compacted crops go in. Refined pantry goods come out. |
| DE | Mühlstein-Vorrat. Compacted Crops rein. Refined Pantry-Waren raus. |

### Merchant (`merchant`) — **trim**

| | After |
|---|---|
| EN | World chests. Not just this crate — they spawn all over. / Rarer chest, better loot. Exploring pays. / Talking to me unlocks them. Sample's beside me anytime. |

### Deed (`isle_clerk`) — **trim**

| | After |
|---|---|
| EN | Personal island: Aetherion Level 20. / Nether Star, slot 9 → Island tab. Claim it there. I don't do paperwork. |

### Bar Whisper (`bar_whisper`) — **trim**

| | After |
|---|---|
| EN | Bottom of your screen: Aetherion Level. / Stats, unlocks, new areas — a lot of this world opens through that bar. / Keep playing. It notices. |

### Lucky Vince (`vince`) — **rewrite**

**Live:** `NpcListener` picks one rotating line. DialogManager was 1 line, DE had 4, casino plugin had a duplicate set. All three now share:

1. Casino's behind me. Pick a machine. Don't cry on the felt.
2. Slots. Roulette. Coins in. Dignity stays in your pocket.
3. I don't deal. I commentate. Machines do the dirty work.
4. The glass is hungrier than you. Feed it anyway.

Casino big-win taunts (machine razz) left alone.

### Crystal Liquidator (`liquidator`) — **trim**

Click: two lines, then GUI.

| | After |
|---|---|
| EN | Crystal desk. Buy, sell, or melt mats into Aether Crystals. / Crystals come from the official shop — or as level rewards. |

### Surveyor (`surveyor`) — **trim**

Tutorial-blocked uses generic orientation lines. Post-tutorial hunt speech is the live copy.

| | After |
|---|---|
| Hunt | Trolls crawl the veins now. Hunt them — they drop blueprint pages. / Bring a page. I'll stamp it into a tool here. |
| Page | Got a page. Desk's yours — stamp it into a tool. |

### Canopy Clerk (`canopy_clerk`) — **rewrite**

Dropped “Isle wood tells the truth / harbour oak is a cover story / canopy keeps receipts.”

| | After |
|---|---|
| EN | I want isle wood — compressed. Not harbour leftovers. / Foraging 5. Then one Compressed Oak, one Birch, one Spruce. / Three samples. Fat payout. Then go chop. |

---

## FancyNpcs (world)

### Miss Canopy (`ae_forage_grove_guide`) — **trim**

**Where:** `IsleGuideNpc.talk` + briefing GUI.

| | After |
|---|---|
| Chat | Hey. Short tour of the isle? |
| GUI | “Click a topic.” (dropped “Easy mode.”) |

Topic cards left as-is — kid-clear how-to, not NPC chatter.

### Dungeon Keeper (`ae_dungeon_keeper`) — **trim**

**Where:** `DungeonListener.openKeeperMenu`.

| | Before | After |
|---|---|---|
| EN | The void still hungers. Choose a floor. | The void's hungry. Pick a floor. |

---

## Skipped

- `mod_smoker` editor NPC.
- Unplaced registry (Amethyst mines guide, Threshold, stalls, boss quest NPCs).
- Foraging entity spam, Monkey ranks, StressBots.
- Quest logic, gates, drop rates, GUI wiring.
- Casino big-win taunts.

## Files (implementation commit)

- `AetherionQuests/.../dialog/DialogManager.java`
- `AetherionQuests/.../listener/NpcListener.java`
- `AetherionQuests/.../util/QuestStoryGate.java`
- `AetherionQuests/src/main/resources/lang/de.yml`
- `AetherionForaging/.../IsleGuideNpc.java` + `IsleGuideBriefingGUI.java`
- `AetherionDungeons/.../DungeonListener.java`
- `AetherionItems/.../BlueprintForgeRitual.java` + `CasinoService.java` (Vince flavor sync)
