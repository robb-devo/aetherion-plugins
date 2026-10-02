# AETHERION — FULL PROGRESSION / STATS / ECONOMY SCOUT

**Date:** 2026-10-01  
**Worktree:** `mining-eldervale-progression-65660c`  
**Mode:** Analysis only — no redesign, no value changes, no architecture rewrites.  
**Source of truth:** Current repository code + YAML as coded.

Supporting raw scout dumps: `docs/_scout_raw/{stats,skills,items,combat,systems}.md`

---

## SECTION A — CURRENT PROGRESSION OVERVIEW

Aetherion is a **multi-spine MMO** whose systems are implemented but **not yet unified into one curve**.

### What actually exists (verified)

| Spine | Owner plugin | Player-facing role |
|---|---|---|
| Account level (1–5000) | AetherionItems | TAB titles, +1 DMG/+1 HP every 5 levels, island/guild gates |
| Equipped skills (1–100 each) | AetherionItems | Flat stat bonuses × effect curve; category XP from play |
| Craft ladders T1–T5 | AetherionItems | Combat / Mining / Farm / Forage / Fish sets |
| Material progression items | AetherionItems | Compressed/compacted bridge gear |
| Boss / signature / set gear | Items + BossEngine loot | Spectacle power spikes |
| Dungeon floors 1–3 | AetherionDungeons + Items | Vestiges, cores, dungeon-scaled gear |
| Pets | Aethermobs | Soft-capped ~12% stack slice + combat skills |
| Boosters (14 sockets) | AetherionItems | Flat pads on gear (LOCKED design) |
| Compression economy | Items + Guilds mill/forge | 128→compressed→128→compacted |
| Quests | AetherionQuests | Tutorial spine + boss hunts + side jobs |
| Islands / Guilds / Quarry | AetherionGuilds | Production sinks @ account 20 / 75 |
| Boss spectacles | BossEngine | HP bands ~3k → 200k; loot uneven |

### What does **not** exist as coded combat stats

- No Strength / Luck / Mana / Ability Power enums  
- Combat power = `DAMAGE` (+ crit / undead / boss flags)  
- AetherionCore does **not** own stats (thin: keys, progress unlock API, raw-hit flag)

### Structural verdict

The game already has **many meaningful parts**. The progression problem is less “missing systems” and more:

1. **Parallel ladders** that don’t force each other (you can ignore whole spines).  
2. **Boss loot** that often ends as souvenirs rather than recipe sinks.  
3. **Dungeon gear** that is powerful inside dungeons (×1.0+) but **crippled overworld** (×0.38).  
4. **Comfort bands** in `BalanceTargets` document an intended endgame stack, but live content (boss HP, signature weapons, god kits) sits beside that band without one authored journey.

---

## SECTION B — PLAYER STAT SYSTEM

### Canonical identifiers (`ItemCapability`)

Path: `AetherionItems/.../model/ItemCapability.java`

| ID | Display role | Calculated from | Consumed by |
|---|---|---|---|
| `DAMAGE` | Weapon/armor damage | Gear + skills + pets + boosters + account milestones + codex + sets | `DamageListener` replaces vanilla when >0 |
| `DEFENSE` | Mitigation | Same sources | `damage *= 100/(100 + def*0.85)` |
| `HEALTH` | Bonus max HP | Same | `HealthListener`: max = 20 + HEALTH |
| `CRIT_CHANCE` / `CRIT_DAMAGE` | Crit | Gear/skills/pets/boosters/sets | Outgoing: `× (1 + CD/100)` if roll |
| `UNDEAD_DAMAGE` / `UNDEAD_RESIST` | Undead niche | Gear/skills/pets/sets | Outgoing / incoming caps 80% / 70% |
| `ATTACK_SPREAD` | Extra melee targets | Gear/skills/pets/boosters | Floor(AS/100) + remainder % |
| `MINING_POWER` / `FORTUNE` / `SPREAD` | Mining | Gear/skills/pets/boosters/masteries | Mining listeners / isle |
| `HARVEST_SPREAD` | Farming | Same | Farm harvest |
| `FISHING_SPEED` / `FISHING_CATCH` | Fishing | Same | Fishing loot/wait |
| `SPEED` | Move % | Gear/pets/boosters only (**skills never contribute**) | Attribute ADD_SCALAR /100 |
| `PET_CATCH_RATE` | Catch | Gear/pets/boosters | Aethermobs catch |

### Aggregation order (`ActiveEquipmentStats.getStat`)

1. Sum allowed stats from mainhand + offhand + armor  
2. Apply dungeon/core mults on the item  
3. Add all `StatProvider` flats (skills, pets, codex, sets, isles…)  
4. Multiply by product of provider multipliers (pets % auras, Hollow Sun, etc.)

### Meaningful impact (current)

| Stat | Impact | Notes |
|---|---|---|
| DAMAGE | **High** | Full rewrite of melee when present |
| DEFENSE | **High early–mid**, diminishing late | Soft asymptotic formula |
| HEALTH | **High** | Direct max HP |
| CRIT | **Medium–high** | Depends on gear CD% |
| Mining/Farm/Fish stats | **High in domain** | Domain-isolated |
| SPEED | **Medium QoL** | Also account ≥3 world_pace +0.36 |
| UNDEAD_* | **Niche** | Borderlands / undead packs |
| ATTACK_SPREAD | **High when stacked** | Can dominate trash clears |

### Redundancy / overlap

- Fortune appears on mining/farm/fish/skills/pets/codex — intentional but crowded  
- Combat T5 sword vs signature weapons vs dungeon set vs pet slice — multiple “main” damage spines  
- Account milestone HP/DMG is tiny vs gear after midgame

---

## SECTION C — PLAYER POWER CURVE

**Honest caveat:** There is **no single authored “hour N” power profile** in code. Below is a **reconstructed approximate** curve from ladders + gates + loot, not a claim that live players follow it cleanly.

### EARLY (~tutorial → T1/T2)

| Axis | Typical |
|---|---|
| Account | ~1–25 |
| Skills | 1–20 equipped; slots 1–2 |
| Gear | Starter / Combat T1–T2 / domain T1 |
| Weapon dmg | ~14–22 (craft) |
| Armor | Low tens DEF/HP |
| Pet | Tutorial catch; low stats |
| Boosters | Lesson unlocks anvil + 1 emerald |
| Enemies | Borderlands + island bosses ~3k HP |
| Boss tier | T1 hunts (Lurker, McNugget, Troll, …) |
| Dungeon | Optional F1 entry |

### MID (~T3 craft + isles + F1)

| Axis | Typical |
|---|---|
| Account | ~20–75 (island at 20) |
| Skills | 40–60; isle-gated skills matter |
| Gear | Combat/Mining T3; compressed/compacted bridge items |
| Weapon dmg | ~36–64 craft; some boss drops |
| Pet | Leveled rare/epic possible |
| Boosters | Multiple sockets filling |
| Boss | Mid island / Helios Herald band ~12k–52k |
| Dungeon | F1 farmable; F2 needs dungeon kit |

### LATE (~T4–T5 + F2/F3 + set bosses)

| Axis | Typical |
|---|---|
| Account | ~75–250+ (guild at 75) |
| Skills | 75–100 Master curve (~2.8–6.5×) |
| Gear | T5 ladders + boss sets + dungeon cores |
| Comfort band (doc) | ~320 DMG / 560 HP / 420 DEF full stack |
| Boss | Sparky / Seraphine / Hollow Sun / Insolvent ~120k–145k |
| Dungeon | F2–F3 with dungeon gear (overworld still ×0.38) |

### SUPER LATE / ENDGAME

| Axis | Typical |
|---|---|
| Account | Soft grind to 5000 possible |
| Signature / myth | Helios, Worldbite, Aetherblade, Aetherion set |
| Open-world `aetherion` | **200k HP** |
| God kits | Command-only — **outside** normal progression |
| Gaps | No clean “you need Mining+Foraging+Pet+Booster prep” gate for pinnacles |

### Gaps (important)

- **No enforced midgame build diversity** — one combat ladder + one pet is enough for much content.  
- **Dungeon ↔ overworld split** breaks a continuous power story.  
- **Boss HP jumps** (3k → 90k F2) are steeper than craft T1→T5 alone.  
- Hourly timeline in the brief is a **design aspiration**, not measured play data.

---

## SECTION D — SKILL CURVES

### Shared

| Constant | Value |
|---|---|
| Max skill level | 100 |
| Effect mult | ~1.0 → ~6.5× (with rarity) |
| XP to 100 | ~346,768 category XP (if always equipped) |
| Loadout | 7 slots; coin unlocks to 1.25M lifetime |
| Account feed | skill XP ÷ 40 → Aetherion XP |

### Categories (60 skills)

Combat 11 · Mining 12 · Foraging 10 · Farming 8 · Fishing 7 · Utility 8 · Dungeon 4

### Domain XP (base)

| Domain | Base grant |
|---|---|
| Mining ores | 8–18 by material |
| Farm crop | 4 |
| Forage wood | 4 |
| Fish catch | 8 (+ isle bonuses) |

### Parallel mastery systems (separate from skill Lv)

Ore Mastery · Crop Mastery · Grove Mastery · Angler Log — real bonuses, island-weighted.

### Role outside own activity

| Skill family | Outside own loop? |
|---|---|
| Combat | Feeds account XP + kill economy (Blood Tax) |
| Gathering | Account XP + compact ledger skills + quests |
| Utility | Half XP from all grants; economy helpers |
| Dungeon | **Only while in dungeon** |

**Verdict:** Skills matter for **stats and account level**, but a player can ignore 3 of 4 gathering categories forever and still combat-progress.

---

## SECTION E — WEAPON / ARMOR / TOOL PROGRESSION

### Craft ladders (BalanceTargets REV6)

| Ladder | T1 → T5 highlight |
|---|---|
| Combat sword | Dmg 14 → **88** |
| Combat chest | Def/HP 8/12 → **58/78** |
| Mining pick | MP/Fort 14/24 → **130/250** |
| Farm hoe | Fort/Harvest → **210/260** |
| Forage axe | → **96/180** |
| Fish rod | Catch → **320** |

### Material bridge (`ProgressionItems`)

Compressed oak / stone / iron / diamond tools; midas dagger; emerald scythe; voided_455 (MP 145 / Fort 270).  
**Organic cross-skill potential exists here** (timber axe needs wood economy; diamond pick needs mining compression).

### Boss / signature (selected)

| ID | Approx weapon power | Notes |
|---|---|---|
| warped_blade | 48 dmg | Early boss |
| ashen_katana (Blossom) | 86 | **LOCKED** |
| gravwell_cleaver | 80 | **LOCKED** |
| worldbite / helios_solstice / aetherblade | 108–114 | High spectacle |
| seraphine_needle | 84 | Hanging Saint |

### Dungeon gear

- F1: vestige + weapon schematic → identify sets (Turnkey / Escapist / Riot Warden / Chaplain / Sculkbound)  
- Cores I–III scale in-dungeon  
- **Overworld mult 0.38** — intentional split, progression disconnect

### Dead / asymmetric

- `diving_*` factories + economy, **no recipes**  
- Hunter crossbow stub  
- God/God2 kits command-only  
- Some `createRandom*Armor` helpers unused in loot

---

## SECTION F — RECIPE / CRAFTING MAP

- **~330 recipes**, all Java (`RecipeRegistry`) — no recipe YAML.  
- Stations: vanilla craft + Booster Anvil (LOCKED) + Blueprint Forge + Pocket Forge + Millstone + Guild mill/forge.  
- Unlock patterns: predecessor tier, material obtained, island level, blueprint.

### Compression chain

| Step | Ratio | Sell formula |
|---|---|---|
| Raw → compressed | 128:1 | unit × 128 × 1.4 |
| Compressed → compacted | 128:1 | compressed × 128 × 1.5 |
| Compacted → refined (crops) | millstone | ×4 |

### Cross-system recipe opportunities (flag only — not implement)

| Result family | Possible organic dependency |
|---|---|
| High mining pick | Compressed wood / forage adhesive already partially true on timber tools |
| Combat T4–T5 | Already uses compacted gold/diamond/emerald — mining-heavy |
| Armor mid | Could want forage leather equivalents — **not currently required** |
| Boss weapons | Mostly drop-only — **weak recipe sink today** |
| Dungeon identify | Vestige → set piece (exists); little world-mat fusion |

---

## SECTION G — RESOURCE / ECONOMY MAP

### Currencies

| Currency | Role |
|---|---|
| Coins | Primary; traders, slots, island upgrades, shops |
| Crystals | Liquidator / Aether Shop |
| Shards | Skill seals / milestones / shop |
| Lifetime coins | Skill slot unlock thresholds |

### Sell truth

| Channel | Rate |
|---|---|
| Resource trader | 100% listed |
| Gear trader buyback | 32% |
| Silas (boss fence) | **8×** listed |
| Bazaar | 50–300% |
| Liquidator | 40k listed value / crystal |

### Bottlenecks / dead resources (observed)

- Compressed stacks are both **useful** (recipes) and **easy AFK** (quarry) → coin inflation risk  
- Some compressed keys disabled (crimson/warped stems)  
- Boss fence 8× can dominate early coin curve  
- Crystal buy 20k / sell 7.5k — intentional friction

---

## SECTION H — ENEMY PROGRESSION

### Overworld / Borderlands

Wildlife combat via Items listeners; armor penetration on some looks. Not a full bestiary HP table in Items.

### Dungeon trash (`PrototypeDungeonBuilder`)

| Floor | Example brute HP | Notes |
|---|---:|---|
| F1 | ~1.2k–3.4k | Entry |
| F2 | ~12k–42k | Huge jump |
| F3 | Mixed; Ashes world-copy mobs much lower vanilla HP | Two combat dialects |

### Aethermobs

**No combat enemy roster** — pets only.

---

## SECTION I — BOSS TIERS / BOSS PROGRESSION

### Data-supported HP bands (not designer labels)

| Band | HP | Examples |
|---|---|---|
| Entry | 3k–6k | McNugget, Lurker, Troll, Skuldugery, Ashen Sheath, F1 Sentinel |
| Mid | 11k–38k | Squidward, Unbroken, F3 elites, Colossus, Helios Herald |
| High | 45k–60k | World Eater, Pathwarden, Helios Requiem, Eggquelizer |
| Spectacle | 90k–145k | F2 Frostbound, F3 Aetherion, Sparky, Seraphine, Hollow Sun, Baron, Insolvent |
| Pinnacle open | 200k | `aetherion` |

### Loot quality uneven

- Strong unique drops: Blossom, Gravwell, Worldbite, Helios, Seraphine, set armors  
- Weak/placeholder: Eggquelizer vanilla eggs; several elites `random_booster` only  
- Dungeon bosses: **empty YML loot** — chests pay instead

### Can pinnacles require multi-system prep today?

**Partially.** Gear + skills + pets + boosters all add damage, but **nothing hard-gates** “must have Fishing 40 + Pet X + Diamond boosters” for Seraphine/Helios. Execution/mechanics carry more than build diversity.

---

## SECTION J — DUNGEON PROGRESSION

| Floor | Boss HP | Gear story |
|---|---:|---|
| 1 | 5.8k | Vestige → Floor I sets; cores |
| 2 | 90k | Cores II; no vestige path |
| 3 | 115k | Cores III; tiny Aetherion armor chance |

**Disconnect:** F1→F2 HP ~15× while craft sword T1→T5 is ~6×. Dungeon-native gear is the intended bridge **inside** instances, but feels like a separate game overworld.

---

## SECTION K — PET SYSTEM

- 79 registered pets; rarity weights heavily common/uncommon  
- Level 100 (dragons Aethered 200); +1%/level with soft-cap dampening  
- Soft-cap targets ≈ **12%** of comfortable full stack  
- Combat skills (beams, charges) are real  
- Tutorial forces catch once; afterward **optional power**

---

## SECTION L — BOOSTER SYSTEM

| Type | Effect |
|---|---|
| Coal/Iron/Gold/Diamond | Flat MP/Fortune/Damage/Defense |
| Emerald/Redstone/Lapis/… | Spread / Attack Spread / Health / Speed / Catch / Harvest / Crit |

- Cap **14** sockets (LOCKED GUI)  
- Values sized as midgame pads, not second sets  
- **Code note:** items currently `setMaxStackSize(1)` while LOCKED rule wants stack 64 — **document conflict**, do not “fix” in this scout pass unless Robb orders

---

## SECTION M — QUEST / REWARD PROGRESSION

### Hard tutorial spine

Harbour → Mine → Temper → Ledger → Fields → graduation (`lesson_boost`, `lesson_manager`, `farm_hand`, `pocket_zoo`).

Unlocks: WORKBENCH, ANVIL, SKILLS, PETS, spawn waypoints.

### Optional

- T1 boss hunts → spawn unlocks + boosters  
- T2 boss hunts → compacted diamond blocks  
- Side audits for gathering/coins  

**Missing bridges:** Few quests fuse “bring forage + mining mats for combat craft.” Boss hunts rarely teach preparation beyond “go kill.”

---

## SECTION N — ISLAND / GUILD / QUARRY PROGRESSION

| Gate | Account level |
|---|---:|
| Personal island | 20 |
| Guild | 75 |

- Island tiers 1–5 (5k→150k coins)  
- Workshop unlocks mill/forge; quarry L1–7  
- Belts / storage / guild projects = **economy & prestige**, not combat stats  
- Contributes compressed/compacted into wider economy — **valuable sink when recipes demand them**

---

## SECTION O — CROSS-SYSTEM DEPENDENCY MAP

```
Skills (equipped)
  ├─► Flat stats ──► Combat / Gather formulas
  └─► Skill XP ÷40 ──► Account level ──► Island(20) / Guild(75) / titles / +HP+DMG

Gather loops (Mine/Farm/Forage/Fish)
  ├─► Skill XP + Masteries + Isle bonuses
  ├─► Raw mats ──► Compress/Compact (craft OR island mill/forge OR quarry)
  └─► Compact ledger skills ──► Economy

Craft ladders T1–T5
  └─► Primary overworld power spine

Boss kills
  ├─► Unique weapons/sets (high spike)
  ├─► Spawn unlocks (quests)
  └─► Often weak recipe sink

Dungeons
  ├─► Vestige/cores (in-instance power)
  └─► ×0.38 overworld (quarantined)

Pets / Boosters
  └─► Soft pads on the same ItemCapability bus

Quests
  └─► Tutorial unlocks + optional hunts (not full curriculum)
```

**Integration score (subjective, data-backed):** Gathering↔Economy **strong**. Combat↔Boss loot **medium**. Combat↔Gathering **weak**. Dungeon↔Overworld **intentionally fragmented**. Islands↔Combat **indirect via mats only**.

---

## SECTION P — DEAD / WEAK SYSTEMS

- Diving armor craft path missing  
- Hunter crossbow stub  
- Eggquelizer loot placeholders  
- Several boss elites: booster-only loot  
- Unused random-armor helper factories  
- God kits outside player journey  
- Skills unequipped = zero loop XP (loadout skill, not account skill)  
- SPEED skill bonuses nowhere (explicit)  
- Surveyor Hidden Blueprints retired  

---

## SECTION Q — OVERPOWERED / BROKEN CURVE AREAS

- F1→F2 dungeon trash/boss HP cliff  
- Silas **8×** fence vs trader 100%  
- Signature / myth weapons near or above T5 craft without shared recipe tax  
- Account level soft-cap 5000 with flat 100 XP — infinite dilute grind  
- Skill effect curve 6.5× at 100 can outweigh mid tiers if stacked with T5  
- Dungeon gear overworld cripple can feel like “two characters”  
- Quarry AFK compression vs recipe demand balance unclear (needs play economy telemetry)

---

## SECTION R — MISSING CONNECTIONS

1. Boss uniques → crafting components / upgrade reagents  
2. Foraging / Fishing mats in combat armor recipes (beyond emerald/gold)  
3. Pet or booster **preparation** as soft checks for pinnacle bosses  
4. Quest curriculum between T1 and T2 bosses  
5. Dungeon rewards that matter in overworld without breaking dungeon identity  
6. Clear “why leave your main skill island” besides coins  
7. Unified midgame banner: “you are Mid when X” (not coded)

---

## SECTION S — HIGH-VALUE DESIGN OPPORTUNITIES

1. **Keep BalanceTargets as the craft spine** — tune connections into it, don’t replace.  
2. **Boss loot sinks** — convert souvenirs into upgrade keys / recipe catalysts.  
3. **Organic cross-skill recipes** at T3–T5 only (avoid early busywork).  
4. **Pinnacle prep model** — soft requirements (skills/pets/boosters) + hard execution.  
5. **Dungeon identity** — keep instance scaling; add rare overworld-transferable rewards carefully.  
6. **Economy sinks** — island upgrades + recipes absorbing quarry output.  
7. **Dead item cleanup list** for a later additive pass (diving recipes, stubs).  
8. **Do not add** new currencies/stats/stations unless a connection fails without them.

---

## SECTION T — OPUS HANDOFF

See dedicated block below (also intended as paste target for a later Opus session).

---

# AETHERION — PROGRESSION / STATS OPUS HANDOFF

## Current state (one paragraph)

Aetherion already ships a complete **stat bus** (`ItemCapability` + `ActiveEquipmentStats`), **five craft ladders**, **skill loadouts to 100**, **pets**, **14-socket boosters**, **128-compression economy**, **islands/guilds**, **3 dungeon floors**, and a **large BossEngine roster**. Power is real and systems are deep in isolation. What is missing is a **single coherent journey** where gathering, crafting, pets, boosters, quests, dungeons, and bosses **reinforce each other** instead of sitting as parallel menus.

## Major strengths

- Clear aggregation model and combat formulas  
- Documented comfortable stack bands (`BalanceTargets`)  
- Skill effect curve with intentional late wall  
- Compression economy with mill/forge on islands  
- Strong spectacle bosses with unique identity  
- Tutorial spine that unlocks systems in order  

## Major problems

- Parallel progression (combat can ignore gathering)  
- Boss loot often lacks downstream craft purpose  
- Dungeon↔overworld power split  
- HP cliffs vs craft curve  
- Uneven boss loot quality  
- No authored multi-system pinnacle prep  

## Current progression curve (compressed)

Tutorial unlocks → T1/T2 craft + T1 bosses (3k) → Island@20 + mid craft/isles → Guild@75 + T4/T5 + F2/F3 + spectacle bosses → optional myth/signature / open `aetherion` 200k. **Not hourly-calibrated.**

## Desired progression philosophy (for Opus redesign)

1. Every meaningful stat/item has a reason.  
2. Noticeable steps; controlled inflation.  
3. Midgame = build choices; late = system mastery.  
4. Endgame combines multiple Aetherion parts **organically**.  
5. Hardest bosses ≠ pure gear checks.  
6. Cross-skill deps where they tell a story — not busywork.  
7. Depth through **connection**, not new systems.  
8. Player should feel: “I couldn’t get here on one activity alone.”

## Important dependencies to preserve

- `ItemCapability` / `ActiveEquipmentStats` pipeline  
- LOCKED: boosters stackability intent + socket GUI; signature weapons combat feel; Talk UX; ranks/wipes rules; shutdown countdown  
- Additive ship rule — do not thin live menus  
- Dungeon overworld mult may stay if compensated with other bridges  

## Recipe / stat / boss / skill / economy levers (Opus freedom)

- **Allowed:** retune ladder numbers, recipe graphs, boss HP/loot sinks, skill bonuses, pet soft-caps, quest rewards, island costs — where it improves connection.  
- **Allowed:** small missing links (e.g. diving recipes, boss catalyst items).  
- **Not allowed blindly:** new currencies, new primary stats, new crafting UIs, new progression plugins.  
- **Must not:** rewrite LOCKED combat for Blossom/Gravwell; strip Talk UX; break booster socket model; overwrite richer live content.

## Creative freedom zones

- Mid/late recipe interdependence design  
- Boss loot → craft catalyst loops  
- Soft pinnacle preparation checklist  
- Making F2/F3 and overworld feel like one MMO with two modes  
- Dead-item resolution (additive)  
- Quest bridges after tutorial  

## Must remain unchanged without explicit Robb order

- LOCKED systems (boosters model intent, anvil GUI, ranks, signature weapons, Talk UX, shutdown)  
- Core stays thin  
- Additive ship discipline  

## Suggested Opus first deliverable (later session)

1. Proposed **single power curve** (Early/Mid/Late/End) with concrete gear+skill+pet snapshots.  
2. **Recipe delta list** (only changes that create organic cross-skill).  
3. **Boss loot sink table** (what each unique becomes).  
4. **Dungeon bridge plan** (keep instance identity, fix overworld orphan feeling).  
5. Explicit **non-goals** list (no new currency, etc.).

---

*End of scout. Implementation deferred until Opus design pass.*
