# Foraging Eldervale — the deep pass

Foraging Eldervale turns the Forage Isle (OrioniX "Foraging Eldervale Island", 500×500) into a skill island on the same tier as Farming, Fishing and Mining Eldervale. It covers seven forests you can feel, a long mastery game, finds worth chasing, a board, a bench and a ledger. On top of that come isle events, critters, updrafts up a 170-block-tall island, and a small cast who live on it.

All of it lives in **AetherionForaging** (`de.aetherion.foraging.isle`). **AetherionItems** gets six skills and one DEV tile. Core, Hub and Quests are unchanged.

---

## 1. Gate 0 — what was on disk and live (2026-09-29)

| Thing | Finding |
|---|---|
| Schematic | `Downloads/ForagingEldervaleIsland/…/Schem/Foraging-Eldervale-Island.schem` · SHA-256 `1a99d4e1…af51e66`, identical to live `plugins/FastAsyncWorldEdit/schematics/`. Sponge v3, 561×384×512, Offset (−391, −123, −137). |
| World file | same folder, `World/Foraging Eldervale Island World.zip` (unused — the isle is pasted into `world`). |
| Paste | `/forageadmin isle paste` → origin **677 90 −116, rotate-y 180** ⇒ **world = (1068 − x, y − 33, 21 − z)**. Matches the configured light footprint (x 508–1068, z −490–21) exactly. |
| Live Foraging | **Two jars** in MMO-R `plugins/`: `AetherionForaging-1.0.0.jar` (= `_wt_midgame_skills` build, has FellStreak + ForagingSkills) and `AetherionForaging.jar` (= main checkout's staged WIP build, has the look-preview `LookCache`). Paper logs *Ambiguous plugin name* on every boot. |
| Live Core / Hub / Items | byte-identical to builds from `.claude/worktrees/mining-eldervale-progression-65660c` (its working tree = e1ac96f + uncommitted Mining ship + Items WIP). That is the base for this pass. |
| Union | Foraging here = midgame lineage **plus** the staged WIP (look-preview bar, any-stem chop, newcomer hints), 3-way merged. Class surface vs **both** live jars: **MISSING 0**. |
| Bug found | Foraging cancels `BlockBreakEvent` to pay wood itself; the Codex listener runs `ignoreCancelled = true` and collapse logs fire no event, so **chopped wood never reached the Codex**. Fixed: every paid log is filed (`ForageBridge.codexWood`). |

---

## 2. The map

### 2.1 Districts (baked, y-aware)

`forage-districts.bin` is an 8×8×8-cell grid (71×32×65, ~7 KB gzip) baked offline from the schematic. Each cell holds the forest whose logs and leaves dominate it, with empty cells filled by nearest forest (vertical steps cost double) and smoothed twice. A lookup is one array read — no block scans, no chunk access — which is what finally lets **TAB, weather and loot know the forest** while the old material-sniffing habitat sense stays **off** for FPS.

The grid is y-aware because the build stacks forests: the **Blossom Shelf** (cherry, y≈200–237) sits directly over the **Gloamwood Hollow** (dark oak, y≈84–115).

| # | Forest (`Grove`) | Wood | Tier (world y) | Weather table | Crown Find | Heart |
|---|---|---|---|---|---|---|
| 1 | **Frostpine Ridge** | spruce | 105–150, snowy ridge (SE) | snow | Frostcone | Frostpine Lodge |
| 2 | **Blossom Shelf** | cherry | 199–237, sky shelf (NW) | flower | Petal Silk | Blossom Pagoda |
| 3 | **Sunscar Mesa** | acacia | 147–192, red mesa + ponds (NE) | savanna | Sunresin | Sunscar Huts |
| 4 | **Elderwood Vale** | oak + birch | 86–110, streams (SW, the Landing) | plains | Elder Acorn | Streamside Hut |
| 5 | **Gloamwood Hollow** | dark oak | 84–115, under the shelf | dark_thicket | Gloamcap | Emberlit Hollow |
| 6 | **Brinefall Mangroves** | mangrove | 124–232, waterfall lakes (E) | swamp | Brine Pearl | Brinefall Lakes |
| 7 | **Canopy Crown** | jungle (+bamboo) | 201–293, the island's roof (N) | jungle | Canopy Amber | Canopy Boardwalk |

The grid was cross-checked in Java against the Python bake (3,011 points, 0 mismatches). If the isle is re-pasted elsewhere with the same rotation, the plugin shifts the grid by the difference. A different rotation turns the grid off (places still work) and logs why.

### 2.2 Places (landmarks) — `forage-isle.yml → landmarks`

Found by clustering built blocks (planks, stairs, doors, lanterns, barrels…) in the schem. Anchors are measured standing spots (solid floor + two free blocks). A place with `grove:` overrides the grid inside its box; the Pagoda is Blossom even though ornamental oaks stand around it.

| id | Name | Forest | Anchor |
|---|---|---|---|
| landing | The Landing | Elderwood | 559.5 90 −207.5 |
| emberlit_hollow | Emberlit Hollow (lantern hamlet under the shelf — the "warm grotto") | Gloamwood | 634.5 98 −255.5 |
| blossom_pagoda | Blossom Pagoda | Blossom | 693.5 200 −243.5 |
| petal_market | Petal Market | Blossom | 732.5 199 −318.5 |
| bell_lodge | Bell Lodge (the only bell on the island) | Elderwood | 763.5 92 −44.5 |
| streamside_hut | Streamside Hut | Elderwood | 698.5 91 −132.5 |
| frostpine_lodge | Frostpine Lodge | Frostpine | 846.5 113 −161.5 |
| ridge_watchtower | Ridge Watchtower | Frostpine | 766.5 112 −146.5 |
| sunscar_huts | Sunscar Huts | Sunscar | 857.5 151 −347.5 |
| redrock_camp | Redrock Camp | Sunscar | 907.5 151 −270.5 |
| brinefall_stilts | Brinefall Stilt House | Brinefall | 921.5 113 −186.5 |
| brinefall_lakes | Brinefall Lakes | Brinefall | 1015.5 146 −149.5 |
| canopy_boardwalk | Canopy Boardwalk | Crown | 778.5 199 −317.5 |
| mossgate_ruin | Mossgate Ruin | Crown | 718.5 199 −389.5 |
| crown_lookout | Crown Lookout (highest standing spot, y 258) | Crown | 721.5 258 −263.5 |

### 2.3 Updrafts — `forage-isle.yml → updrafts`

Each shaft was searched in the schem: the air above `from` is clear all the way to `to`'s height, so a rider never passes through leaves.

| id | Rise | from → to |
|---|---|---|
| landing_updraft | 107 | 607.5 92 −215.5 → 612.5 199 −220.5 (Vale → Blossom Shelf, next to the Landing) |
| hollow_chimney | 98 | 598.5 101 −237.5 → 599.5 199 −238.5 (Hollow → Shelf) |
| crown_draft | 101 | 676.5 98 −201.5 → 680.5 199 −205.5 (→ Canopy Crown) |
| crown_step | 61 | 720.5 129 −188.5 → 723.5 190 −192.5 |
| ridge_gust | 28 | 841.5 113 −171.5 → 834.5 141 −177.5 (Ridge → Mesa) |
| frost_chimney | 41 | 909.5 100 −76.5 → 902.5 141 −82.5 |
| brinefall_spout | 49 | 970.5 97 −117.5 → 970.5 146 −124.5 (→ the lakes) |
| brine_cliff | 104 | 981.5 93 −226.5 → 978.5 197 −222.5 |

### 2.4 The Grove cast (FancyNPC, isle-local)

| Role | Name | Job | Spot |
|---|---|---|---|
| board_clerk | **Pell Ardwin** | Lumber Board | Landing stall 564.5 90 −180.5 |
| woodwright | **Tamsin Holt** | Grove Marks, consumables | Bell Lodge 763.5 92 −44.5 |
| archivist | **Juniper Quell** | Forest Ledger, Cabinet, sales | Blossom Pagoda 693.5 200 −243.5 |

Miss Canopy (the existing isle guide) stays as she is; talking to her is now step 1 of the tour. FancyNPC names are `ae_forage_cast_*` (the Quests Living-NPC stack only resolves `ae_living_*` / `ae_editor_*`). **No borrowed skins**: an empty `skin:` means the default Steve/Alex. Put only original textures there. They are placed once at the measured spots (`cast.auto-place`). Move each with its DEV anchor. Name plates are non-persistent TextDisplays, re-ensured every 5 s, so they never duplicate across restarts.

---

## 3. The loop

```
fell ─ Fell Pulse (timing bar · streak · look-preview) ─ unchanged at heart
     ─ Titan fells (≥40 logs: two clean cuts)   ─ district from the grid / place
     ─ Grove Mastery (per wood, I–VII)          ─ Crown Finds (catch it as it falls)
     ─ District extras (weather-tilted)         ─ Widowmaker → Deadfall
     ─ Lumber Board progress · Warden Standing  ─ isle records (largest fell per forest)
payout ─ the chop listener asks back: CHOP window · marker speed · miss cooldown ·
         wood cap · heartwood chance · Codex filing
around ─ updrafts · fall-catch · compass & tour · cast · bench · ledger · journal · events · critters
```

Off the isle footprint nothing changes: harbour trees, personal/guild islands and dungeons behave exactly as before. Every hook returns the old value there.

### 3.1 Fell feel

- **Titans** (isle trees with ≥ `tuning.titan-logs` = 40 logs): the first clean cut **notches** the trunk ("The Titan groans — one more cut"). The bar returns 8 ticks later, one cell tighter and 15% quicker. A miss keeps the notch. A Titan pays wood cap ×1.6, counts double for mastery, adds +10% Crown Find chance, and is an order target.
- **Marker speed** is now a real parameter (`FellPulse` gets a fractional speed; the old constructor is unchanged). Keen Edge slows it; the time budget stretches with it, so the pass count stays the same.
- **Look-preview** uses the same window rule as the live chop (skills, streak, isle bonuses), so the green you preview is the green you get.
- After the tally, a second beat shows **Grove Mastery** progress for that wood.

### 3.2 Grove Mastery — `GroveMastery`

Isle fells per wood (oak, birch, spruce, cherry, acacia, dark oak, mangrove, jungle, bamboo). The tiers sit at 10 / 40 / 120 / 300 / 700 / 1500 / 3000 fells × wood scale (jungle 0.7, cherry and mangrove 0.8, acacia and dark oak 0.85). Each tier is paid when you cross it: 150 → 12,000 coins and tier×40 XP. Permanent, for that wood only:
- +1 wood cap per tier
- +0.25% heartwood per tier
- IV: CHOP window +1
- VII: Crown Finds ×1.5 and the "Warden of …" card

Standing: +1 at IV, +3 at VII.

### 3.3 Crown Finds — `CrownFinds`

When a crown lets go, a glowing find (a single ItemDisplay) drifts from the top of the crown to the stump. **Catching it mid-air** adds 10% weight; picking it up after it lands is fine too, and it fades 8 s after landing. The finder has it alone for 4 s, then anyone may take it.
- Chance: base 7%, +3% for a Perfect, +10% for a Titan, then +1.2% per Canopy Eye tier. Multiplied by: Sap Sense ×(1+0.5·scale), mastery VII ×1.5, Blossom Storm ×3, Tailwind ×2, weather (below), Sap Lure ×3. Capped at 90%. A Crown Shaker charge guarantees one.
- Grades: Rough 70 / Fine 23 / Pristine 6.5 / Heartsong 0.5 — luck shifts weight upward.
- One find per forest, grades × weight → value (Rough ×1, Fine ×3, Pristine ×10, Heartsong ×40 of base 50–90).
- Heartsong catches are announced isle-wide. The heaviest of each kind is an isle record.
- Finds can't be placed, eaten or thrown (item guard).

### 3.4 District extras — `DistrictLoot`

About one roll in five per fell (`tuning.extras-chance` 0.22), small stacks of vanilla goods:
- Frostpine: sweet berries, snowballs, spruce saplings, fern
- Blossom: pink petals, honeycomb, cherry saplings, sticks
- Sunscar: sticks, acacia saplings, gold nuggets, dead bush
- Elderwood: apples, oak and birch saplings, sticks
- Gloamwood: red and brown mushrooms, glow berries, dark oak saplings
- Brinefall: propagules, clay, lily pads, slime balls
- Canopy Crown: cocoa, melon, bamboo, jungle saplings

**Weather tilts it**: snow ×2 on the Ridge; rain or drizzle ×1.5 in the Mangroves, the Crown and the Vale; fog ×1.5 in the Hollow; clear ×1.25 on the Mesa; wind ×1.5 on the Shelf. The same idea applies to Crown Finds (×1.2–1.5). Weather stays player-local and **visuals stay off** — it's TAB and loot only, as before.

### 3.5 Widowmaker → Deadfall — `Widowmaker`

After a fell of ≥14 logs, 12% of the time (×1.6 for jungle, dark oak and mangrove; ×1.5 for Titans) a loose limb lets go above the feller:
- **The tell:** bark dust and a groan for 26 ticks (+10 with Deadfall Dancer).
- **Stay put:** a BlockDisplay limb lands on you for 3 hearts. It never kills: damage is capped to leave 1 heart, halved by Deadfall Dancer, and reduced by Wind Step.
- **Step out:** it's a **Deadfall** — 2–4 logs of that wood outside the cap (×2 with Deadfall Dancer), and your streak never notices.

### 3.6 Lumber Board — `LumberBoard` (Pell)

Three orders per forager:
- **Hand-ins:** typed logs (plain vanilla stacks only), or a graded find.
- **Fill while you work:** fells in a named forest, Perfect fells, a Titan, Crown Finds caught, critters caught, deadfalls.

Pay is well above wood value: coins + coins/8 XP + 1–3 Standing, +8% per Standing level, and Board Rates adds up to +60%. A filled slot restocks after 2:30. The board rerolls free every 30 minutes, or for 250 coins. Slot 1 is always something you can do from the Landing with an axe.

### 3.7 Woodwright — `Woodwright` (Tamsin)

**Grove Marks** live on the forager, not the axe. Five lines, tiers I–V:

| Line | Effect |
|---|---|
| Keen Edge | marker +5 / 10 / 10 / 20 / 20% slower; III and V widen the CHOP window by 1 each |
| Deep Roots | +1 wood cap per tier on every isle tree |
| Canopy Eye | Crown Finds +1.2% per tier, better grades |
| Sure Grip | miss cooldown −12% per tier; V = **Second Wind** (one miss every 3 min keeps the streak) |
| Wind Step | ½ fall damage on the isle · limbs −20% · slow-fall after updrafts · limbs −40% · **the canopy always catches you** |

Cost per tier:
- **Wood:** 64 / 96 / 128 / 192 / 256 plain logs, each line walking you through different forests.
- **Heartwood:** 0 / 1 / 2 / 3 / 5, any kind.
- **Finds:** a Rough+ find at III, Fine+ at IV, Pristine+ at V.
- **Coins:** 500 → 25,000.

Warden Standing gates the top tiers: III needs Sprout, IV Grovehand, V Elder. Each mark tier bought gives +1 Standing.

Consumables:
- **Sap Lure** (the isle's bait; `bait.enabled`): 32 acacia logs + 150 coins. Hang it on a trunk; for 3 minutes, fells within 6 blocks roll Crown Finds ×3 and extras ×2.
- **Crown Shaker:** 1 heartwood + 400 coins. The next fell guarantees a find.
- **Heartwood Incense:** 2 finds + 600 coins. Heartwood ×2 for 10 minutes.

### 3.8 Forest Ledger — `ForestLedger` (Juniper)

- **Collections** read the real **Codex** counts per wood. Milestones at 100 / 500 / 2k / 6k / 15k / 40k logs pay 200 → 30,000 coins, claimed at Juniper. Standing is paid from milestone III.
- **Collector rank:** every 6 claimed milestones → +0.2% heartwood on the isle.
- **Find Cabinet:** 7 finds × 4 grades, filled on first catch.
  - A full **row** (one find, every grade) → 3,000 coins + 2 Standing + **+1 wood cap** on that forest's wood.
  - A full **column** (one grade, every find) → 2k / 6k / 20k / 60k coins + Standing.
- **Sales:** Juniper buys finds. Left-click keeps your best of each kind; shift-right sells all.
- **Record board:** a live TextDisplay beside Juniper shows the largest fell per forest and the heaviest find per kind. It only exists while someone is within 40 blocks.

### 3.9 Warden Standing — `WardenStanding`

Sapling → Seedling → Sprout → Bough → Grovehand → Woodwarden → Elder → Canopy Keeper → Heartwood, at 0 / 3 / 8 / 15 / 25 / 40 / 60 / 85 / 120 points. It is earned from orders, marks, events, mastery, collections, the cabinet, Grove Walker, Cartographer and the tour. It is never spent; there is no second currency.

### 3.10 Isle events — `GroveEvents` (one shared boss bar, telegraphed)

Every 7–11 minutes (`events.interval-*`), and only while someone is on the isle:
- **Golden Sap** — the isle's designed **hotspot** (`hotspots.enabled`). One forest, preferring one with players in it, runs rich for 2:30: wood cap ×1.5, heartwood ×2, +8 XP per fell.
- **Windfall** — a gale drops limbs around every forager every ~5 s. Strike a limb 3× for 3–5 logs outside any cap. The crew goal is 10 × foragers (min 12). If it's made, everyone who helped gets 400 + 20/limb coins, +1 Standing and **Tailwind** (Haste I, Crown Finds ×2 for 1:00).
- **Blossom Storm** — petals on the wind (client-side particles), Crown Finds ×3 and better grades for 2:00.
- **Bark Blight** — bark beetles crawl out near foragers and nip (never lethal). Squash them for 15 coins each. The crew goal is 8 × foragers (min 10); success pays 500 + 20/beetle coins, +1 Standing and Tailwind.

### 3.11 Critters — `GroveCritters` (display entities + Interaction hitbox, capped)

- **Canopy Squirrel** (Vale / Crown, daytime): 9% chance after a fell. It bolts from the crown with a find in its cheeks; one hit and it drops it.
- **Frost Moth** (Ridge, at night or in snow): catch it for **Frostlit** (CHOP window +1 for 1:00).
- **Glowcap Wisp** (Hollow, in fog or at night): drifts off through the trunks. Follow it; where it fades it leaves a Gloamcap.
- **Bark Beetle** (Bark Blight only).

Cap: 14 global (`critters.global-cap`) and 2 per player. Nothing is a mob, so no spawn guards trip and nothing is farmable by spawner.

### 3.12 Getting around — `Traversal`, `GroveCompass`

- **Updrafts:** step onto a vent (a cloud puff when someone is near). You rise up the measured column, then glide onto the ledge. Sneak to let go. There are 8 fall-damage-free seconds after a ride, and slow-fall on arrival (longer with Wind Step III).
- **Fall-catch:** on the isle a fall never kills. A lethal fall leaves you on 1 heart and winded; Wind Step V removes the damage entirely.
- **Edge rescue:** drop off the island (below y 70 inside the footprint columns) and you're put back on your last safe footing. This only applies to someone who stood on the isle in the last 30 s, so nobody is ever pulled up from below.
- **Compass:**
  - The first entry into a forest shows its card and pays 250 coins + 30 XP. Later entries get a quiet action-bar line.
  - Places pay 150 once.
  - All 7 forests make you a **Grove Walker** (+1,500 coins, +2 Standing); every place makes you the **Cartographer** (+2,500 coins, +2 Standing).
  - `/grove go <forest|place|updraft|cast>` points an arrow with a **height hint** (▲/▼) and never overwrites a tally on screen.
- **Tour** (auto-offered on the first visit): Miss Canopy → Pell → Tamsin → Juniper, and the last leg is the Landing Updraft. It pays 750 coins + 1 Standing.

---

## 4. AetherionItems (small, guarded)

- `AetherSkill`: six **FORAGING** skills plus flags. They are flag-driven, with no stat bonuses, so `SkillService` isn't touched. Foraging reads the flags by name, so an older Items jar just means "no Eldervale skills".
  - `GROVE_BORN` — isle wood cap +2–4, and sometimes counts a fell twice for mastery.
  - `SAP_SENSE` — Crown Finds ×(1+0.5·scale), better grades.
  - `STEADY_HANDS` — CHOP window +1 on the isle, miss cooldown −25% per scale (max −50%).
  - `DEADFALL_DANCER` — longer limb tell, half the hurt, double deadfall.
  - `BOARD_RATES` — Board coins +15% per scale (max +60%).
  - `HEART_HUNTER` — heartwood ×(1+0.5·scale) on the isle.
- `SkillFlavor` / `SkillMenu`: flavour and effect lines, and the Foraging loop hint mentions Eldervale.
- `DevHubs`: **WORLDS slot 38** (the empty slot beside Mining Island) gets **Forage Island**. Nothing moved.
- `DevMenu`: one additive branch — `forageisle:open` runs `/grove dev`. The Forage Island DEV hub itself lives in AetherionForaging. Content-kit accounts are stopped by DevMenu's existing allow-list; they can still use `/grove dev` (with `aetherion.dev.content`).

Hub is unchanged — `/forage` and `/forageisle` already teleport to `forage_isle`. Core is unchanged.

---

## 5. Commands, permissions, config

- `/grove` (aliases `canopy`, `forestjournal`) — the Journal (forests, mastery, marks, cabinet, board, records, places, tour, active effects).
  - Subcommands: `tour` · `go <id>` · `stop` · `where` · `places` · `mastery` · `cabinet`.
  - `board` / `bench` / `ledger` open when you stand by the keeper; anyone else is pointed there. Admins can open them anywhere.
- `/grove dev [action]` — DEV hub (`aetherion.forage.admin` or `aetherion.dev.content`). Actions look like `group:verb[:arg]`:
  - Travel and layout: `tp:<id>`, `updraft:ride|from|to|setfrom|setto|remove:<id>`, `place:move:<id>`
  - Cast: `cast:placeall|removeall|anchor:<role>|here:<role>|talk:<role>`
  - Events and critters: `event:<kind>|stop`, `critter:<kind>`
  - Items: `find:all|drop|<kind>[:grade]`, `tonic[:id]`, `heartwood`, `logs`
  - Profile: `profile:standing|marks|mastery|cabinet|discover|shaker|reset:confirm`
  - Info: `where`, `status`, `reload`
- `forage-isle.yml` (new, data folder): landmarks, updrafts, cast spots, tuning, events, critters. Bundled defaults are merged **additively** — a moved anchor is never overwritten, and a removed updraft stays removed (`removed:` tombstones).
- `config.yml`: `hotspots` and `bait` were placeholders that nothing read. They now drive Golden Sap and the Sap Lure. On first start a one-time migration sets both to `true` and writes `hotspots.designed: grove-events-v1`. Set them `false` afterwards to opt out; the migration never runs again.
- `forage-isle-players.yml` (new): profiles and isle records. Serialised on the main thread, written off it (tmp + move) every minute when dirty, on quit and on disable.

## 6. FPS / TPS

- District, TAB and weather lookups: one array read plus ≤15 box checks.
- The habitat sense and weather visuals stay **off** exactly as before.
- Displays are one per falling find, critter, windfall limb and name plate — all non-persistent and capped.
- Particles are few and near players only. The Blossom Storm petals are sent per player.
- Timers: 1 tick (updraft riders, cheap), 2 ticks (finds, critters, only while any exist), 10 ticks (compass), 1 s (events, ambient critters), 5 s (name plates), 30 s (record board), 60 s (save).

## 7. Test sheet (in game)

1. Boot once. The log should show:
   - `Foraging Eldervale: grid loaded 71×32×65 · 15 places · 8 updrafts`
   - the hotspot/bait migration line
   - `Grove cast: … placed` ×3
2. Walk the Landing to check the tour: Miss Canopy points to Pell, then Tamsin, then Juniper. Ride the **Landing Updraft**.
3. Check TAB shows the forest or place name; `/grove where` says the same.
4. Fell trees: the tally is followed by a Grove Mastery beat. The Codex counts go up (the `/codex` wood pages).
5. Fell a jungle giant: "Notched!", then a tighter second bar, then a wood cap ×1.6.
6. `/grove dev find:drop:fine` — catch it mid-air (+10%). Try `critter:squirrel|frost_moth|wisp`.
7. `/grove dev event:windfall` — strike limbs, check the crew bar and the Tailwind payout. `event:golden_sap` should name a forest.
8. The bench: forge Keen Edge I (64 oak + 500c) and check the marker is slower on the isle. Craft and use each consumable.
9. The board: hand in a log order. Fill a Perfect order.
10. Fall off an edge → rescue. Jump from the Shelf → caught on 1 heart.

## 8. Deliberately left alone (parallel NPC-talk chat)

AetherionQuests entirely: LivingNpc\*, TalkUx / TalkText, CastBook, NpcMemory, LivingNpcLife / Skins, DialogManager, LivingNpcProfile skin auto-assign, the `/npc` editor, the Hub Origin cast, Borderlands Vex / Rite, the `forage_pad_guide` dialogue, celebrity skin lists and the MineSkin pipeline. Also the resource-pack dialog fonts, Anvil GUI, ranks, Blossom Blade / Gravwell Cleaver, ShutdownCountdown, and the Borderlands vials and rite. The DevMenu dashboard layout is unchanged (one tile in an empty slot).

## 9. Re-baking the map

`docs/tools/forage_grove_bake.py` (numpy; Pillow for `render`):
- `bake` regenerates `forage-districts.bin` (verified byte-identical to the shipped one).
- `anchors --points "x,y,z;…"` finds standing spots.
- `updrafts` lists clear shafts between tiers.

Re-bake with `--paste` / `--rotate` only if the isle is pasted with a different rotation.
