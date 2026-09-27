# Early Game Audit — Aetherion (Phase 1)

**Scope:** first **1–2 hours** of a brand-new player.  
**Mode:** technical scout only — **no implementation in this pass.**  
**Date context:** post–Hanging Saint polish (fight LEAVE ALONE).

---

## Current State

### Player spine (orientation)

Canonical soft order (from `QuestStoryGate` + harbour funnel):

1. **Join** → language picker (Quests) + generic welcome title (Items) + Hub “FIND EGON” starter hint  
2. **Anker Harbour pier** → soft cage until Egon is spoken to (`HarbourOnboardingGate`)  
3. **Egon** → `welcome_aboard` → talk to **Forager / lumberjack**  
4. **Forager** → axe + chop → `gather_wood` turn-in at Egon → **Starter Gear** (pickaxe etc.)  
5. Soft market wall lifts after timber turn-in  
6. **Quartermaster** → `forge_coal` (Ore Ridge coal) → mines unlock reward  
7. **Shaft Foreman** → `first_shift` (mine ores)  
8. **Temper** (`lesson_boost`) + **Miss Ledger** (`lesson_manager`) + **Fields** (`farm_hand`, `pocket_zoo`)  
9. **Miss Ledger stamps tutorial done** → world opens (Vex / Borderlands / boss contracts beyond)

Supporting systems already in place:

| Layer | What exists |
|--------|-------------|
| Hub | `/spawn`, `/spawns`, discover unlocks, homestead markers, launch pads |
| Quests | Code-registered quests, Living FancyNPCs, dialog pacing, hints, compass, trails |
| Items | Starter gear + flavor, skills, boosters, Manager GUI, `/guide` |
| World skills | Farming / Mining / Foraging / Fishing plugins |
| Combat ceiling | High-end weapons + BossEngine spectacles (reference quality, not early content) |

### First-hour cast (tutorial NPCs)

`egon`, `lumberjack`, `quartermaster`, `foreman`, `craftsman`, `fisher` / `fishmonger`, `farmer`, `lark`, `ledger`, `vex`, `booster_tutor`, plus harbour flavor (stalls, bar whisper, merchant, liquidator…).

### Combat in hour 1–2

- Soft: farm animals (`first_hunt`) — optional / parallel-ish  
- Real PvE teach: **Sergeant Vex** → Borderlands kills (`lesson_steel`) — after orientation closes  
- Damage numbers + ability spectacle exist in Items / BossEngine but are **not** introduced as a curated first-fight lesson

---

## Strong Areas

- **Harbour funnel** — pier cage + water rescue + market soft-wall is intentional design, not accidental softlock  
- **QuestStoryGate** — clear “orientation not done → world stays closed” rules; Ledger graduation is a real beat  
- **LivingNpcProfile** — per-NPC colors, leather kits, skins, nametag language  
- **DialogPace** — single speech-rhythm knob (`LINE_GAP_TICKS = 40`)  
- **QuestHint + TutorialQuestTrail + compass** — player rarely has *zero* next-step signal when systems work  
- **Spawn discover** — walking into Ore Ridge / Capital / Farm / Borderlands unlocks teleports with popup  
- **StarterSetBalance** — starter tools stay revisable without wiping extras  
- **ItemFlavor** dry humor on starter items — already “character,” not placeholder lore  
- **Explore chests** — exploration reward hook already exists  
- **GuideAdvice / DiscordGuideHook / `/guide`** — tutorial-aware tips bridge in-game ↔ Discord  
- **Endgame combat / Seraphine / Hollow Reliquary** — quality bar to *aspire toward*, not rewrite

---

## Leave Alone

Mark these **LEAVE ALONE** unless Robbi explicitly reopens them:

### Locked / production-sacred
- Signature weapons: **Blossom Blade** / **Gravwell Cleaver** (abilities, VFX, feel)  
- **Boosters** (lore stats, stack 64, IDs/models) + **BoosterSocketMenu** / custom Anvil GUI  
- **Rank / Admin / TAB dye** system  
- **ShutdownCountdown** (10→2)  
- **Hanging Saint** fight + Seraphine music box / Mended Saint loot (just QA’d)

### Early-game structure that already works
- **Quest ID spine** and `QuestStoryGate.tutorialDone` completion set (`lesson_boost`, `lesson_manager`, `farm_hand`, `pocket_zoo`) — do **not** reshuffle the whole graph for novelty  
- **HarbourOnboardingGate** soft-wall coordinates / intent (polish *feel* of hints OK; don’t gut the funnel)  
- **Starter gear factory path** via `StarterGearReward` + `CustomItem` simple tools  
- **Organic spawn discover** IDs (`ore_ridge`, `farm`, `capital`, `borderlands`, …)  
- High-end BossEngine directors / CombatTheatrics used as **reference only**

---

## Early Game Problems

### 1. First impression is “plugin title,” not “world”
`JoinWelcomeListener` = gold **AETHERION** + “welcome” + amethyst chime. Fine as a stub; it does not match harbour identity (pier, Egon glow, sea). Hub starter-hint helps, but join + hint don’t feel like one authored moment.

### 2. NPC presence is uneven
Only **Lark** (AetherMobs pet) and **Liquidator** (rune motes) get ambient systems. Tutorial anchors (Egon, Forager, Foreman, Ledger) are mostly static FancyNPCs + chat. Personality lives in text; the *body* of the world rarely reacts.

### 3. Dialog is paced chat, not presence
`DialogManager` + `DialogPace` are solid infrastructure. Presentation still reads as clickable chat lines. Little look-at, prop reaction, or spatial feedback when a line lands. Comment heritage still frames “Egon / Quartermaster” as the original full paths — risk of uneven coverage for later tutorial NPCs.

### 4. Kit handoff is transactional
Egon’s starter kit / axe grants are functional messages (`Received: Simple Axe`). No “equipment ceremony” beat. Compare to Seraphine music-box claim or Hollow Reliquary — early game has nothing at that *care* level.

### 5. Path guidance is dust-only
`TutorialQuestTrail` = yellow dust along hardcoded harbour corners. Works. Does not feel like the same craft as BlockDisplay bosses.

### 6. Combat teaching gap
Hour-1 combat is cows / later Borderlands trash. No curated “this is how Aetherion hits feel” beat before signature weapons. `DamageNumbers` exist but aren’t framed as a lesson. Jump from farm → Borderlands → bosses is steep narratively.

### 7. Encoding / copy fragility
Some live hint strings show mojibake/`§` corruption in sources (e.g. onboarding shove hints). That makes an otherwise smart funnel feel broken/cheap.

### 8. “What now?” after Ledger stamp
Orientation close is clear; the **emotional** handoff into free exploration / Vex / skills loop is thinner than the funnel that preceded it. Risk: player finishes tutorial and feels dumped into a checklist world.

### 9. Explore / world reaction underplayed
Explore chests + discover unlocks exist but open/discover moments can still feel like vanilla + title. Missed chance for “the world noticed me.”

---

## High Potential

Where Opus can create a **large perceived quality jump** without rewriting the economy or combat endgame:

1. **Harbour Arrival vignette** — join/first-harbour as one authored beat (sound, light, Egon attractor)  
2. **Egon kit ceremony** — BlockDisplay / item-display handoff when starter gear is granted  
3. **Living tutorial anchors** — ambient loops for 3–5 spine NPCs (Egon / Forager / Foreman / Ledger)  
4. **Trail / waypoint craft** — upgrade dust trail to small Display breadcrumbs (harbour path only)  
5. **Dialog presence layer** — shared helper: glance, soft particles, prop twitch on line ticks (reuse `DialogPace`)  
6. **Discover & chest open FX** — shared “world reacted” package for spawn unlock + explore chest  
7. **First combat lesson beat** — *one* small taught fight or gated Borderlands intro using existing mobs + DamageNumbers framing — **no new legendary weapons**

---

## Low Risk Opportunities

Isolated, reviewable, hard to brick production:

| Opportunity | Why low risk |
|-------------|--------------|
| Fix garbled onboarding/hint copy (UTF-8) | Text-only |
| Enrich `JoinWelcome` / Hub `starter-hint` into one harbour-toned moment | Config + small listener |
| Kit grant VFX in `StarterGearReward` only | Single reward path |
| Ambient FX for Egon (extend `LivingNpcAtmosphere`) | Pattern already exists |
| Explore chest open FX | Service already owns open |
| Discover announce polish in `SpawnDiscoverListener` | Already has title/sound hook |
| `/guide` tip copy pass for hour-1 | Lang / GuideAdvice |

---

## Experimental Opportunities

Worth considering if Opus has leftover creative budget — still Phase 1 only:

- Pier ** gull / rope / lantern** Display dressing that reacts when Egon speaks  
- Soft **echo** of player footsteps on pier planks (sound only)  
- “Orientation stamp” prop at Ledger desk when tutorial completes  
- Tiny **harbour weather / tide light** shift once gather_wood is done (mood unlock)  
- First **crit** teaching toast the first time `DamageNumbers` rolls gold  

Avoid: new skill trees, new currencies, mid-game islands, boss rewrites, weapon balance sweeps.

---

## Suggested Scope (one Opus project)

**Name:** *Harbour Hour — presence without rewriting the spine*

**In scope (pick a coherent bundle, not all at once):**

A. Arrival + Egon attractor  
B. Kit ceremony on starter gear grant  
C. Ambient for Egon + Forager (+ optional Foreman/Ledger)  
D. Display trail for Egon↔Forager path only  
E. Shared dialog presence helper wired into existing `DialogManager` / `LivingNpcProfile.say`  
F. Copy/encoding cleanup for onboarding hints  

**Out of scope:**

- Quest graph redesign  
- New bosses / signature weapons  
- Booster / rank / anvil systems  
- Seraphine / Hollow Reliquary  
- Full Mid/Late game  
- Deploy / server admin  

**Success test:** a cold join → Egon → wood → kit feels *authored*; player says “this place cares” before they ever see a world boss.

---

## TOP EARLY-GAME OPPORTUNITIES

| # | What | Why | Files / systems | Opus could make | Impact | Complexity | Risk |
|---|------|-----|-----------------|-----------------|--------|------------|------|
| 1 | Harbour arrival vignette | First 10s set the quality bar | `JoinWelcomeListener`, Hub `starter-hint`, harbour spawn | One pier-toned join beat + Egon green attractor | Very high | Low–Med | Low |
| 2 | Egon kit ceremony | Biggest early reward currently feels like `/give` | `StarterGearReward`, Egon turn-in in `NpcListener` / Dialog | Display handoff + sound motif when kit lands | Very high | Med | Low |
| 3 | Tutorial NPC atmosphere | Spine NPCs feel dead vs Lark/Liquidator | `LivingNpcAtmosphere`, profiles | Ambient loops for Egon/Forager/Foreman/Ledger | High | Med | Low |
| 4 | Trail → Display breadcrumbs | Guidance works but looks “plugin dust” | `TutorialQuestTrail` | Thin gold/oak Display crumbs along existing corners | High | Med | Low |
| 5 | Dialog presence helper | Text is good; body language missing | `DialogManager`, `DialogPace`, `LivingNpcProfile` | Shared glance/FX on line ticks | High | Med | Med (touch many dialogs carefully) |
| 6 | Discover + chest “world noticed you” | Unlocks/chests under-sold | `SpawnDiscoverListener`, `ExploreChestService` | Small shared FX kit | Med–High | Low–Med | Low |
| 7 | First combat teach beat | Combat identity arrives too late | Vex/`lesson_steel`, `DamageNumbers`, Borderlands | Framing + one taught skirmish — **no new legendaries** | High | Med–High | Med |
| 8 | Onboarding copy UTF-8 pass | Garbled hints trash funnel trust | `HarbourOnboardingGate`, `lang/de.yml`, Hub config | Clean DE/EN strings only | Med | Low | Very low |

---

## LEAVE ALONE (summary)

Hanging Saint + Seraphine loot · signature weapons · boosters/sockets/anvil · ranks/TAB · shutdown countdown · quest spine / tutorialDone set · harbour soft-wall intent · StarterSetBalance · organic spawn IDs · endgame BossEngine spectacles (reference only).

---

## FUTURE — MID GAME

*(Do not work now.)* Skill ladders depth · island loops · blueprint desk identity · mid boss contracts · economy loops · “after Ledger” motivation spine.

## FUTURE — LATE GAME

*(Do not work now.)* T2/T3 spectacle integration · dungeon vestige path · endgame retention · final polish pass across systems.

---

**STOP.** Analysis complete. Implement only via a separate Opus Phase-1 build using the handoff + prompt.
