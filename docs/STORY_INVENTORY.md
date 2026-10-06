# AETHERION — Story Inventory (READ-ONLY facts)

Date: 2026-10-03  
Audience: external story bible author (knows progression curve; not live map/tech).  
Rules: facts only · every claim sourced · `[UNSICHER]` when not verified · no design ideas.

**Live** = placed/reachable on server. **Code-only** = exists in jar/DevMenu/docs only.  
If placement on *this* host is unknown → `[UNSICHER — Robb fragen]`.

---

## 0. Engine answers (BossEngine)

| Question | Answer | Source |
|---|---|---|
| Fight ends by **ITEM use** (not damage)? | **No** on live bosses. Items **spawn** bosses (`SpawnCause.ITEM`, anchors/cores, pinnacle key). Closest: sandbox `test_loadbearing` (right-click pillars unseals core, then damage-kill). | `BossEngine/.../BossItemUseListener.java`; `SandboxDirector.java`; `SkillTrigger` has no `ON_ITEM` |
| Boss **invulnerable + countdown**? | **Yes.** YAML `transition: { duration-ticks, invulnerable: true }` → `PhaseTransition` / `BossInstance`. Helios Requiem: silence + chart (invuln) + damage window (`helios.requiem.window-seconds`). Helios enrage bar: SUPERNOVA countdown. | `PhaseTransition.java`; `BossInstance.java`; `helios/Requiem.java`; `HeliosEncounter.java` |
| Multi-form A dies → B? | **Not** generic YAML. **Yes** via wrappers: Herald → `helios_requiem`; Unbroken death → site OPEN → island footfall spawns `world_eater`. Same fight can `rebindBody`. | `HeliosEncounter.heraldFallen()`; `WorldEaterSite`; `BossInstance.rebindBody` |
| Clean hooks if missing | ITEM-end → listener → `BossManager.onDeath` / director. Invuln+timer → YAML `transition` or director `blocksDamage()` + tick (Helios pattern). A→B → encounter owner (not template YAML). | `BossManager`; `BossScripts.register` |

---

## 1. Bosse

HP = YAML `attributes.max-health`. Texts: YAML `message:` or director Java (path if long).

### 1.1 Spectacle / special directors

| id | Display | Live/code | Spawn / arena | HP | Phases | Text (verbatim or path) | Drops | Quest |
|---|---|---|---|---|---|---|---|---|
| `helios_herald` | The Herald | code-only instance | `/helios enter` → world `helios_requiem` | 26000 | herald→mirror | UI/scripts: `HeliosEncounter`, `HeraldScript` e.g. `"&6✦ &eThe dying star calls. &7The arena is forming…"` | none (Act II) | — |
| `helios_requiem` | Helios | same | after Herald dies | 52000 | korona→zerfall→singularitaet→requiem | `"&c&lHELIOS"` / `"&7Requiem of a dying star"`; `"&f&oSilence."`; `"&6&lThe heart lies open!"`; `"&f&lSUPERNOVA"` — full: `HeliosScript`/`Requiem`/`Supernova` | spectacle_star, helios_solstice, random_helios_armor, boosters | — |
| `hanging_saint` | Seraphine, the Hanging Saint | [UNSICHER — Robb fragen] place | director builds Gilded Proscenium at spawn | 120000 | overture→the_hand→unstrung | `"&f&lSeraphine"` / `"&6the Hanging Saint"`; `"&7&oThe Hand descends."`; `"&4&oUnstrung."`; `"&f&oCurtain."` — `HangingSaintDirector` | seraphine_needle, random_seraphine_armor → MusicBox | — |
| `world_eater` | Nihil, the World Eater | [UNSICHER] world `world_eater` | after Unbroken; island footfall | 45000 | hunger→starless→unmade→ouroboros | `"&5&lNIHIL"` / `"&7the World Eater"`; `"&f&lWORLD SAVED"` / `"&7Nihil ate itself…"` — `WorldEaterDirector` | spectacle_star, worldbite, random_worldhide_armor | — |
| `world_eater_unbroken` | The Unbroken | same site | plaza before Nihil | 12000 | the_floor→one_arm | `"&7&lTHE UNBROKEN"` — `UnbrokenDirector` | boosters / crying obsidian | — |
| `hollow_sun` | The Hollow Sun | [UNSICHER] | `/boss spawn` / `hollow_sun_anchor`; Bloodstone comment | 120000 | main_sequence→red_giant→collapse | YAML: `"I burned for ten thousand years. I can burn brighter."` / `"Every star falls. Fall with me."` / `"The Hollow Sun has returned to the sky."` + director intros | spectacle_star, random_hollow_sun_armor | — |
| `eggquelizer` | EGGQUELIZER | code-only / event | EggStage at spawn | 60000 | soundcheck→headliner→distortion | `"&f&lEGGQUELIZER"` / `"&7check one, two."` — `EggquelizerDirector` | placeholder eggs/XP | — |
| `aetherion` | ✦✦✦ Aetherion | open pinnacle | `aetherion_summons` @ spawner `aetherion_home` | 200000 | challenger→stormborn→tempest→singularity | YAML e.g. `"Oh a challenger... its been a while."` / `"Entertain me and SHOW ME WHAT U GOT"` — `DragonDirector` | void stick, random_aetherion_armor | **not** `the_veil` |
| `dungeon_aetherion` | ✦✦✦ Aetherion | F3 live path | F3 Throne of Ashes after clearance | 115000 | same phase ids | Sovereign shouts e.g. `"Kneel, or burn standing."` — `AshenSovereignDirector` ([UNSICHER] if YAML fireballs still fire beside director) | dungeon chest (YAML loot empty) | `the_veil` |

**Helios world:** `helios.yml` → `world.name: helios_requiem`, arena-y 160, slot spacing 1024. Arena **procedural** (`Arena`/`ArenaBuilder`) — no `.schem` in BossEngine.

**World Eater site:** `config.yml` `world-eater.world: world_eater`; layout `SiteLayout.java` (code build via `/boss worldeater build`).

### 1.2 Aetherion old vs new fight

| | Open `aetherion` | Dungeon `dungeon_aetherion` |
|---|---|---|
| HP | 200000 | 115000 |
| AI | `DragonDirector` | **excluded**; `AshenSovereignDirector` |
| Feel | YAML fireballs + phase transitions | Ashen Wake, Cinderfall, Talon, Skyfire, Last Sky, Throne Eclipse… |
| Spawn | Pinnacle key at `aetherion_home` (coords **[UNSICHER]** — runtime spawners store) | F3 after ≥75% trash + elites + gate drop |
| Quest | pinnacle design | `the_veil` → kill `dungeon_aetherion` |

**F3 new arena (Throne of Ashes)** — `AetherionDungeons/.../AshesEncounter.java`, `DungeonWarmPool.ASHES_BASE`:

| Field | Value |
|---|---|
| Floor id | `prototype_ashes_3` |
| World | `aedun_f3_ashes` (warm base; claims may clone) |
| Player spawn | 0.5, 100, 0.5 |
| Gate | x=194, y=96–102, z=-74..-68 |
| Arena box | x 206–288, z -111..-31 |
| Boss spawn | **266.5, 96, -70.5** |
| Schematic | **None in BossEngine.** Warm prep: runtime `structures/ashes/f3-prep.yml`. Built map / Multiverse. |
| Flow | Clear trash → Chainwarden @~40% / Cinder Herald @~70% → door → `dungeon_aetherion` → legendary chest |

### 1.3 Midgame / dungeon elites / quest bosses

| id | Display | HP | Phases (names) | Spawn | Loot highlights | Quest |
|---|---|---|---|---|---|---|
| `mcnugget` | McNugget | 3000 | cluck, nugget_panic | anchor/core | compressed_coal, booster | `poultry_problem` |
| `bridge_troll` | Bridge Troll | 3000 | stomp, storm | anchor/core | bridged_axe | `troll_toll` |
| `hollow_lurker` | Hollow Lurker | 3000 | lurking, frenzy | cave | warped_blade | — |
| `skuldugery` | Skuldugery | 3000 | volley, inferno | anchor | shortbow, catch_sphere_epic | `ash_and_arrows` |
| `squidward` | Squidward | 11000 | ink, inkstorm, boil | water | squids_boot | `ink_contract` |
| `aether_colossus` | Aether Colossus | 38000 | awakened, enraged, last_stand | core | compacted_*, spectacle_star | `walking_mountain` |
| `pathwarden` | Pathwarden | 52000 | gate | anchor | gravwell_cleaver | — |
| `ashen_sheath` | The Ashen Sheath | 5200 | quiet_steel, falling_blossoms, ashen_moon | [UNSICHER] anchor coverage | ashen_katana (Blossom Blade) | — |
| `ashen_chainwarden` | The Chainwarden | 16000 | warden, keys | F3 elite | booster | — |
| `cinder_herald` | Cinder Herald | 12000 | herald, ashfall | F3 elite | booster | — |
| `dungeon_sentinel` | Prototype Sentinel | 5800 | hold, rupture | F1 | chest | — |
| `dungeon_frostbound` | The Frostbound | 90000 | packed, blizzard, hearth | F2 | chest | — |
| `sir_balthazar` | Sir Balthazar | 115000 | lecture, hotfix, rollback | T2 | staff_of_technical_difficulties | `open_ticket` |
| `lobby_cleaner` | The Lobby Cleaner | 125000 | sweep, overtime, shredder | T2 | void_vacuum_charm | `lost_and_found` |
| `sparky` | Sparky | 120000 | warm, melt, grid, meltdown | T2 | thermal_core | `overtime` |
| `baron_von_wurm` | Baron von Wurm | 128000 | nibble, quarry, feeding | T2 | pickaxe_core_of_the_burrower | `denied_claim` |
| `insolvent_wither` | The Insolvent Wither | 145000 | audit, foreclosure, chapter_eleven | T2 | insolvent_ledger | `bounced_check` |

Sandbox: `test_*` (12) + `test_loadbearing` — Dev Test Arena; no story loot. YAML folder: `BossEngine/src/main/resources/bosses/` (38 files).

Quest ↔ boss table: `AetherionQuests/.../QuestRegistry.java` (rows above). No Quests hits for Helios / Seraphine / Nihil / Hollow Sun / Eggquelizer.

---

## 2. BossEngine capabilities

### Skill types (`SkillRegistry.registerDefaults`)

`SOUND` · `PARTICLE_AURA` · `POTION` · `MINION_SPAWN` · `ARROW_SHOT` · `EGG_SHOT` · `LEAP` · `SLAM_LEAP` · `INK_SHOT` · `BOILING_WATER` · `DRAGON_FIREBALL` · `DIALOG` · `ACTION_BAR` · `INVERT_CONTROLS` · `CHARM_DRAIN` · `VACUUM_PULL` · `TELEPORT_PRANK` · `OVERHEAT` · `BLAST_CORES` · `BURROW_STRIKE` · `TAX_COLLECTOR` · `SPECTACLE` · `RING_BURST` · `METEOR_RAIN`

Triggers (`SkillTrigger`): `ON_SPAWN` · `ON_PHASE` · `ON_TIMER` · `ON_DAMAGE` · `ON_DEATH`.

### Phase logic

Phases by descending `health-percent`. Optional `transition` → invuln + freeze AI + VFX for `duration-ticks`. Missing transition → guard (~50 ticks). **BossScript** templates (Helios): phase applied immediately; script owns interludes via `blocksDamage()`.

### Directors by template id (`BossInstance`)

| Director | ids |
|---|---|
| `DragonDirector` | EnderDragon except `dungeon_aetherion` |
| `AshenSovereignDirector` | `dungeon_aetherion` |
| `HollowSunDirector` | `hollow_sun` |
| `HangingSaintDirector` | `hanging_saint` |
| `EggquelizerDirector` | `eggquelizer` |
| `UnbrokenDirector` / `WorldEaterDirector` | unbroken / eater |
| `AshenSheathDirector` | `ashen_sheath` |
| Sparky / Frostbound / Pathwarden / AshesElite / Sandbox / Signature | matching / `test_*` / death themes |
| `BossScripts` | `helios_herald`, `helios_requiem` only |

### Cutscenes / worlds

No standalone Cutscene API. Pattern = director ticks + titles + `blocksDamage`. Worlds: `helios_requiem`, `world_eater`, `aedun_f3_ashes`; Seraphine/Egg = temporary stages at spawn.

---

## 3. NPCs

`npcs.yml` is **runtime** (`plugins/AetherionQuests/npcs.yml`), not jar resources. Coords from ops snapshot `aetherion-ops/tmp-quest-npcs.yml` ([UNSICHER] if live drifted). Names/roles: `QuestNPCRegistry`, `LivingNpcProfile`, voice `CastBook`.

### 3.1 Placed Living cast (snapshot)

| id | Display | Loc (approx) | Role | Tone (1 line) | Quests | Barks | Lines |
|---|---|---|---|---|---|---|---|
| `egon` | Egon the Equipper | harbour 300,63,-382 | Equipper | Dry pier vet | `welcome_aboard`, turn-in `gather_wood` | greet/idle/banter | FREE content; TalkUx shell **LOCKED** |
| `lumberjack` | Lumberjack / Forager | 349,66,-327 | Timber | Soft hug-axeman | `gather_wood` | yes | FREE |
| `quartermaster` | Quartermaster | 170,64,-360 | Supply | Ledger/crate pedant | `forge_coal` | yes | FREE |
| `craftsman` | Craftsman | 218,64,-387 | Forge | “Hm.” gift | flavor (`a_simple_craft` exists) | yes | FREE |
| `foreman` | Shaft Foreman | 30,55,190 | Shaft | Dusty mine boss | `first_shift` | yes | FREE |
| `booster_tutor` | Temper | 53,60,113 | Boosters | Flammable tutor | `lesson_boost` | yes | FREE |
| `ledger` | Miss Ledger | -1,58,0 | Lesson Clerk | Stamp / help desk | `lesson_manager` | yes | FREE (≤5 chips) |
| `farmer` | Farmer | -214,61,175 | Fields | Bird-paranoid | `farm_hand` | yes | **LOCKED** Farmer→Harlow polish |
| `lark` | Lark | -225,58,149 | Pets | Parrot-adjacent | `pocket_zoo` | yes | FREE |
| `fisher` | Tackle | 278,64,-418 | Dock | Patient sea tip | `a_good_catch` | yes | FREE |
| `fishmonger` | Fishmonger | 278,63,-370 | Stall | Shop bark | shop | yes | FREE |
| `vex` | Sergeant Vex | 161,58,73 | Drill | Barking drill | `lesson_steel` | yes | FREE |
| `rite_keeper` | Rite Warden | 251,65,173 | Border | Ominous waste | `border_rites` | quiet DE | **LOCKED** Hub/Borderlands |
| `arena_proctor` | Proctor | -299,58,-101 | Arena | Clipboard escort | flavor + Colosseum | quiet DE | **LOCKED** Hub pass |
| `farm_isle_guide` | Harlow (code) / Harrow (live yml) | -256,61,185 | Farm pad | Isle pitch | flavor | yes | **LOCKED** Harlow polish |
| `surveyor` | Surveyor | -78,50,266 | Blueprints | Troll-curious | flavor | yes | FREE |
| `vince` | Lucky Vince | -6,60,74 | Casino | House floor | flavor | yes | FREE |
| `bar_whisper` | Bar Whisper | 195,64,-367 | Aether Meter | Whispery XP tip | flavor | yes | FREE |
| `eldervale_welcome` | Maren | 56,90,490 | Eldervale Host | Thin-air host | flavor | yes | FREE |
| `eldervale_upgrade` | Forgehand | 53,90,500 | Blueprint Forge | Heat/forge GUI | flavor | yes | FREE |
| `merchant` | Merchant | 250,64,-354 | Coin Desk | Chest salesman | flavor | yes | FREE |
| `isle_clerk` | Deed | 28,58,51 | Island Deed | Anti-paperwork | flavor | yes | FREE |
| `forage_pad_guide` | Twig | 365,65,-308 | Forage Pad | Bouncey guide | flavor | yes | FREE |
| `liquidator` | Crystal Liquidator | -24,58,33 | Crystals | Numbers | flavor | yes | FREE |
| `canopy_clerk` | Canopy Clerk | 567,90,-204 | Samples | Wood clerk | `canopy_sample` | yes | FREE |
| `root_cellar` | Root Cellar | -294,107,522 | Mill | Pantry GUI | flavor | yes | FREE |
| `amethyst_mines_guide` | Crystal Guide | 108,37,589 | [UNSICHER] | placed yml | **no** registry entry this build | — | [UNSICHER] orphan |

Idea extras (unplaced default): `town_crier` Hollis, `street_sweeper` Bram, `lamp_lighter` Wick — `/questnpc extras` · **FREE**.

Unplaced quest givers (place via anchors/DevMenu): boss hunt NPCs (`chicken_keeper`…`repo_agent`), `hunter`, `collector`, `miner`, `dungeon_gate` (Threshold), stalls, etc. — **FREE** content unless noted; TalkUx only if Living+FancyNPC.

### 3.2 Origin townsfolk (Hub — not Quests TalkUx)

| id | Display | Role | Preset loc (`origin.yml`) | Barks | Status |
|---|---|---|---|---|---|
| `guide` | Orla Vane | Origin Journal | 10.5 58 6.5 | 5 | Hub bubble; FREE content [UNSICHER vs Ankerhold swap] |
| `keeper` | Cobb Kettleby | Skyway Ledger | 5.5 67 -199.5 | 5 | same |
| `bellkeeper` | Sister Aurel | Seven Bells | -160.5 61 7.5 | 5 | same |
| `tender` | Fen Glowmoor | Glowcap Tender | -86.5 60 140.5 | 5 | same |
| `stargazer` | Stellan Voss | Summit Stargazer | -7.5 198 -271.5 | 5 | same |

Source: `OriginRole.java`, `origin.yml` → `cast.presets`.

### 3.3 Cross locks

| Thing | Status | Source |
|---|---|---|
| TalkUx shell | **LOCKED** | user rules; `docs/npc/NPC_DIALOGUE_OVERHAUL.md` |
| Ankerhold map re-point | **LOCKED** other track | `docs/PROGRESSION_IMPL_SESSION.md` |
| OpeningOrbit | **LOCKED** | `OpeningOrbit.java`, `opening_orbit.yml` |
| Farmer→Harlow polish | **LOCKED** | `docs/POLISH_BRIEFING.md` |
| Rite Warden / Proctor DE ambient | **CLAIMED** Hub pass | `docs/dialogs/DIALOG_VOICE_DE.md` |
| Isle casts (Farm/Fish/Mine/Forage), Miss Canopy, Dungeon Keeper | out of Quests overhaul | `NPC_DIALOGUE_OVERHAUL.md` |

---

## 4. Quest system

### Objective types (`ObjectiveType`)

| Type | Example | Source |
|---|---|---|
| `TALK` | `welcome_aboard` → lumberjack | `QuestRegistry` |
| `DELIVER` | `forge_coal` → COAL×20 | same |
| `MINE` | `first_shift` → ANY_ORE×32 | same |
| `KILL` | `lesson_steel` → BORDERLANDS×10; boss ids | same |
| `CRAFT` | `a_simple_craft` → mining_pickaxe | same |
| `HARVEST` | `farm_hand` → WHEAT×48 | same |
| `FISH` | `a_good_catch` → ANY×5 | same |
| `USE` | boosters / Skills / Borderlands rite / merchant | same |
| `CATCH` | `pocket_zoo` → ANY | same |
| `BREAK` | enum+listener; **no** QuestRegistry use | [UNSICHER] editor-only |

### Soft spine / gates

Harbour → QM → Mine → Temper → Ledger → Fields → Ledger stamp (`QuestStoryGate`).

| Gate | Rule | Source |
|---|---|---|
| Tutorial NPC allowlist | First-hour cast until tutorial done | `TUTORIAL_NPCS` |
| Tutorial done | `lesson_boost`+`lesson_manager`+`farm_hand`+`pocket_zoo` | `TUTORIAL_DONE_QUESTS` |
| Ledger accept | needs `first_shift` | `ledgerUnlocked` |
| Rite accept | full tutorial | `riteKeeperUnlocked` |
| Harbour onboarding | pier closed until Egon oak | `HarbourOnboardingGate` |
| Skill/AL | `requireCategory` / `requireAccountLevel` e.g. Mining8, Foraging5, AL20 `guild_brick` | `QuestSkillGate` |

### Rewards / flags

Rewards (string kinds): coins, XP, named items/kits, `spawn:*` unlocks — `QuestManager`.

| Store | Server-wide? |
|---|---|
| `players/<uuid>.yml` quest states + starter bits | **no** |
| `npc-memory.yml` | **no** |
| Items `ProgressionService.Flag` (WORKBENCH, ANVIL, SKILLS, PETS, TRADER…) | **no** |
| Server-wide “boss X beaten” quest flag | **none found** in Quests |

Dialog branching: completed variants, starter flags, `CastBook.returning`, gate redirects, Proctor vial+taught, session `askedTopics`.

### Barks / TalkUx limits

| Layer | Behavior | Source |
|---|---|---|
| Greet | ≤6.5 blocks; cooldown | `LivingNpcAtmosphere` |
| Idle bark | 4–14 blocks | `CastBook.idle` |
| Banter | 12 scripts; pair CD | `CastBook.banter` |
| Max distance | 10 | `talk-ux.max-distance` |
| Reply timeout | 40s | config |
| Linger | 90 ticks | config |
| Typewriter | 1.5 cpt, read-gap 36 | config |
| Topics shown | ≤3 + Bye | `DialogManager` |
| Ledger chips | ≤5 | comments / `SkillRoads` |

---

## 5. Sprache & Dialog

| Fact | Source |
|---|---|
| EN in Java = source of truth | `LangPack` javadoc |
| DE only `AetherionQuests/.../lang/de.yml` | sole lang yml |
| Player `/sprache` · `/language` → `players/<uuid>.yml` `language` | `LanguageCommand` |
| DE missing → EN fallback; **`cast.*` missing → silence** (no EN bark leak) | `LangPack` / `DIALOG_VOICE_DE.md` |
| Shared bark language = audience majority; ties EN | Atmosphere |

### `DIALOG_VOICE_DE.md` (bullets)

- Content/localization on existing pipeline; pacing/gates/ids unchanged.
- DE overlay: intros, completed, returns, chips, topics, greets, idle, 12 banter, quest cards, TalkUx UI strings.
- TalkUx: **string-only** (~23 literals); shell LOCKED.
- Rite/Proctor DE ambient **CLAIMED** elsewhere.
- Style: informal *du*, dry, match EN beat count; proper nouns stay EN.

### `NPC_DIALOGUE_OVERHAUL.md` (bullets)

- Scope: placed Quests NPCs only (not isle casts).
- Bubble + typewriter + chips + quest card; chest = Details/classic/fallback.
- 39 original skins → FancyNpcs MineSkin.
- Life: work beats, patrols, greetings, banter; `npc-memory.yml`.
- Prepared: CastBook, DialogManager variants, LangPack, validator `docs/dialogs/tools/validate_de_overlay.py`.

---

## 6. Welt & Karte

### Two hub truths

| Layer | Status | Source |
|---|---|---|
| Live Origin overlay | Still coded ~1000×1000 `world` | `AetherionHub/.../origin.yml`, `docs/MAIN_ISLAND_ORIGIN.md` |
| Ankerhold (new hub) | Design ~380×361; plugin re-point may lag | `docs/AETHERION_OPENING_CINEMATIC_HANDOFF.md` |
| `ankerhold_anchors.yml` / `HUB_ANKERHOLD.md` | **Outside worktree:** `C:\Users\Robbi\IdeaProjects\Claude outputs\` | handoff / audio bible |
| OpeningOrbit | Code live; default server list **`mmo-c` only** | `OpeningOrbit.java` L140–147 |

### Ankerhold named places (design)

| Area | Places | Source |
|---|---|---|
| Harbour | Anker Harbour, Spill / Spill Gate, Long Haul, Seawatch Light, Guild Slips | `HUB_ANKERHOLD.md`, anchors |
| Capital | Ledger's Court, Tallybell, Registry, Archive, Lucky Knot | same |
| Farm SW | Clucksworth, windmill, orchard, Harrow's Gate | same |
| South | Ore Ridge, Shaft No.1, Surveyor's Ledge, Temper | same |
| NE | Whisperwood, Forage Skyway | same |
| West | Vex's Gate, Fallen Anchor, The Scar, burnt quarter, Rite Circle, Colosseum fragment | Fallen Anchor = “torn third mooring” |
| North | Mountain Gate, Skyreach, dungeon Threshold | same |
| Bells | 5 (Harbour, Tallybell, Vex, Field Chapel, Wayside) — ≠ Origin’s 7 | `HUB_ANKERHOLD.md` |
| Empty hooks | Guild Slips, Archive, Threshold frame, burnt quarter | same |

### Live Origin (`origin.yml`)

13 districts (Mount Skyreach, Ore Ridge, Anker Harbour, Capital, Whisperwood, Bloomfield, Colosseum, Glowcap Crags, Clucksworth, Southwood, Borderlands, Eastwood, Seawatch…). Landmarks: Ledger's Court, Fountain Square, Spiral Scar, Ribcage, skyways, 7 bells, glowcaps, vistas.

### Skill islands

| Isle | Plugin | Places / cast | Source |
|---|---|---|---|
| Mining Eldervale | `AetherionMining` | 25 districts; Old Wick, Otto, Nan, Ilse, Brann, Hollis | `docs/MINING_ELDERVALE.md` |
| Foraging Eldervale | `AetherionForaging` | 7 forests + landmarks; Pell, Tamsin, Juniper + Miss Canopy | `docs/FORAGING_ELDERVALE.md` |
| Farming Eldervale | `AetherionFarming` | 15 plots; Elsie, Hattie, Bram, Gus, Wren, Pip, Marta, Tobias | `IsleRole.java`, `docs/FARMING_ELDERVALE.md` |
| Fishing Eldervale | `AetherionFishing` | 12 waters; Maren, Tilly, Odile, Old Finn | `docs/FISHING_ELDERVALE.md` |

Hub goto: `/mining` `/forage` `/farm` `/fishing` — mostly same `world` at offsets (`docs/AETHERION_MAIN_HUB_WORLD_BIBLE.md`).

### Dungeon floors

| Floor | Theme | Boss UI | Backend | Source |
|---|---|---|---|---|
| F1 | Warden's Prison | Prototype Sentinel | instances **mmo-d** | `DungeonGuideGUI`, `FloorOnePool` |
| F2 | Frostbound | Frostbound | same | same |
| F3 | Throne of Ashes | Aetherion | same; return **mmo-r** | `AshesEncounter`, `ServerNames` |

### Velocity servers (`ServerNames.java`)

| Name | Role | Port |
|---|---|---|
| `hub` | HUB | 25566 |
| `mmo-r` | MAIN | 25567 |
| `mmo-d` | DUNGEON | 25568 |
| `mmo-c` | COLOSSEUM (+ default opening-orbit host) | 25569 |

Shared transfer: `/var/opt/minecraft/crafty/shared/transfer` (default).

### Unused / sealed

SE/SW slime pads → skyways (`MAIN_ISLAND_ORIGIN.md`). Pad sealed until blueprint stamp (`OriginHooks.padSealed`). Opening orbit off on mmo-r by default. Unused dungeon NBTs noted in `OPUS_DUNGEON_F1_ELEVATE_PROMPT.md`. Softlight excludes Borderlands (`origin.yml`).

---

## 7. Events & Ambiente

### Origin moments (`OriginEvents`)

| Moment | When | Duration | Control | Source |
|---|---|---|---|---|
| Harbour Fog / Petal Storm | DAWN/DAY | 120/60s | — | `OriginEvents.pick()` |
| Lantern Festival | DUSK (/NIGHT) | 90s | — | same |
| Aurora / Starfall / Lantern | NIGHT RNG | 120/90/90s | Starfall: sneak+look up | same |
| Rainbow | After rain (day) | 60s | WeatherChangeEvent | same |
| Schedule | every `events.interval-minutes` (22) if anyone on isle | cosmetic | `events.enabled`; `/origin` force | `origin.yml` |
| Mute | `OriginProfile.ambience` / `.particles` | settings | not quest-flag | `OriginProfile` |
| Quiet until capital spawn | titles suppressed | `quiet-until-spawn` | `origin.yml` |

Dawn/dusk bells: 3 / 5 strokes — `OriginBells.watchClock` + Sister Aurel bark.

### Sound / music

Almost no soundtrack manager (`docs/AETHERION_AUDIO_SOUNDTRACK_BIBLE.md`). `OriginAmbience` district palettes. Seraphine `MusicBox` note-block theme + loot box. Helios Requiem notes + `helios:*` pack sounds. Opening cue `aetherion:open_orbit` (may fall back to notes).

### NPC presentation tools

| Capability | Fact | Source |
|---|---|---|
| Skin change | Yes — FancyNPC / LivingNpcSkins | `LivingNpcSkins`, `LookMenu` |
| Temporary despawn | Yes — spawn service / API / sneak-despawn | `QuestNPCSpawnService` |
| Transparent body | Villager path forces `setInvisible(false)`. Talk UI can hide panels. **No** story transparency API found | `QuestNPCAppearance` |
| FancyNPC hide for cinematic | [UNSICHER] | — |

---

## 8. Items für Lore

| Kind | What exists | Source |
|---|---|---|
| Codex ledgers | Collection · Bestiary · Journal | `CodexBook`, `CodexMenus` |
| Collection tabs | ALL, Ores, Stone, Wood, Dirt, Crops, Fishing, Nether, Other | `CodexCatalog.COLLECTION_TABS` |
| Bestiary tabs | ALL, Hostile, Nether, End, Animals, Water, Sea, Dungeon, Bosses | `BESTIARY_TABS` |
| Written book shipped | Beta Checklist (meta) | `AetherionBeta/.../BetaBook.java` |
| Lecterns | Guild factory desk — mechanical | `AetherionGuilds` |
| Story gear lore | Hollow Sun / Helios / Seraphine / Worldhide / Summons lines | `CustomItem.java` |
| Chronik Codex tab | **does not exist** today; Collection/Bestiary/Journal only | `CodexCatalog` |
| Signs/schematic prose | [UNSICHER] not readable from Java scout | — |

Lore-items (books/letters/quest items with pages): **possible** via Items factories + books; no large authored world-book corpus in code.

---

## 9. Progression (kurz)

| Gate | Rule | Soft/hard | Source |
|---|---|---|---|
| Island | Account level ≥ **20** | hard | `ProgressionService.ISLAND_LEVEL` |
| Guild | Account level ≥ **75** | hard | `GUILD_LEVEL` |
| Skills / Manager | Flag `SKILLS` | flag | `ProgressionService.Flag` |
| Trader | Flag `TRADER` | flag | same |
| Workbench / Recipes | Flag `WORKBENCH` | flag | same |
| Anvil | Flag `ANVIL` | flag | same |
| Pets | Flag `PETS` | flag | same |
| Dungeon F1–F3 | Early / Late(AL75+T4 rec.) / End(T5 rec.) | **soft UI** — no hard AL check found in Dungeons | `DungeonGuideGUI` |
| Aetherion Summons | Craft mythic `aetherion_summons`; right-click at Veil; consumed; party 4; `PINNACLE_KEY` | craft+site | `CustomItem.createAetherionSummons`, `BossItemUseListener` |

---

## 10. Bestehende Lore (verbatim excerpts)

| Text | Source |
|---|---|
| Bell hymn = pitches `{0.749, 0.841, 0.944, 1.122, 1.26, 1.498, 1.682}` as NOTE_BLOCK_BELL/CHIME — not lyrics | `OriginBells.HYMN` |
| “Seven bells, seven notes. The old hymn only works if you ring them all.” | `OriginRole.BELLKEEPER` |
| “The bells of Origin ring in the morning.” / “…toll for dusk. The lanterns are lit.” | `OriginBells.toll` |
| Fallen Anchor = torn third mooring (ring top ~y 133) | `Claude outputs/ankerhold_anchors.yml` `fallen_anchor` |
| Spill = pier head over lagoon waterfall | `OPENING_ORBIT.md`, anchors `the_spill` |
| “I burned for ten thousand years. I can burn brighter.” / “Every star falls. Fall with me.” / “The Hollow Sun has returned to the sky.” | `hollow_sun.yml` |
| Hollow gear: “It still remembers how to shine.” / “The plate is hollow. The heart is not.” | `CustomItem` |
| Helios: “The dying star, an armillary sphere around a porcelain-gold mask.” | `helios_requiem.yml` header |
| Helios gear: “Seven rays. One of them still warm.” · Solstice “The Herald's fourth blade. It came back.” | `CustomItem` |
| “Nihil, the World Eater – the Last Seed” | `world_eater.yml` |
| Worldhide: “Sixteen worlds, stitched shut.” · Worldbite: “Its last fang. Still hungry.” | `CustomItem` |
| Seraphine titles “Seraphine” / “the Hanging Saint”; Needle: “She cut her own strings with this.” | `HangingSaintDirector`, `CustomItem` |
| Summons: “Every isle, a spectacle, the third floor and the bank, pressed into one crystal.” · “Right-click at the Veil… Aetherion answers.” | `createAetherionSummons` |
| Orla: “Thirteen districts, one island…” | `OriginRole.GUIDE` |
| Fishing species e.g. “Lake royalty. Takes a tax on every bite.” | `Species.java` |

Full director corpora: paths in §1 (too large to paste).

---

## 11. Grenzen

| Limit | Rule | Source |
|---|---|---|
| Boosters | Stackable 64; lore stats; no ID/model/stat rewrite | `.cursor/rules/locked-systems.mdc` |
| Borderlands vials | Stackable; no rite change unless asked | same |
| Custom Anvil / 14 sockets | LOCKED | same |
| Ranks | OP≠Admin; Admin=Robb UUID; `player-ranks.yml` wipe-exempt | same |
| Signature weapons | Blossom Blade (`ashen_katana`) + Gravwell Cleaver — no ability/VFX polish | same + user rules |
| ShutdownCountdown | 10→8→6→4→2 / 2s | same |
| TalkUx shell | LOCKED | user rules |
| Additive ship | Never overwrite richer live menus with thinner trees | `additive-ship-never-overwrite.mdc` |
| Core thinness | Core = keys/identity; game loops in owning plugins | `BossEngine/.cursor/rules/aetherion-plugins.mdc` |
| Ownership | Gather worlds in gather plugins; payouts/stats/recipes in Items | `docs/OWNERSHIP.md` |
| Perf | Display budgets (Forage guard, Hub caps, Helios/World Eater); no hologram forests | Hub bible; Eldervale docs |
| Deploy | Restart only servers that load the jar; no Done-waits | `deploy-restart-discipline.mdc` |

---

## 12. Offene Fragen an Robb

1. Is **Ankerhold** the live hub now, or still the old OriginBuilds map on `mmo-r`?
2. When should **opening orbit** leave `mmo-c`-only?
3. Should `origin.yml` be re-pointed to Ankerhold (5 bells vs 7) before story work?
4. Dungeon F2 “Account 75+” — hard gate later, or stay soft recommend?
5. One cosmology bible for Fallen Anchor / Spill / Hollow Sun / Helios / Nihil / Aetherion, or keep parallel flavor?
6. Authored written books / Archive lecterns wanted, or dialogue + item lore only?
7. Should Origin moments ever be quest-flag gated?
8. NPC tools: skin+despawn enough, or need real transparency/hide without despawn?
9. Forage/display leak still a ship blocker before hub ambience expansion?
10. Story beats that must live on Ankerhold vs personal island / guild voids only?
11. Confirm Admin/rank LOCKED stays absolute even for fictional “Admin” titles.
12. Is `mmo-c` Colosseum-only long-term, or also Ankerhold preview host?
13. Open-world `aetherion_home` exact world/xyz on live?
14. Does `dungeon_aetherion` YAML combat still run beside `AshenSovereignDirector`?
15. Live `farm_isle_guide` name Harrow vs code Harlow — which is canonical?
16. `amethyst_mines_guide` orphan — keep, wire, or remove?

---

## Path index

| Path | Role |
|---|---|
| `BossEngine/src/main/resources/bosses/` | Boss YAML |
| `BossEngine/src/main/java/de/aetherion/bossengine/` | Directors, skills, Helios, World Eater |
| `AetherionDungeons/.../AshesEncounter.java` | F3 arena |
| `AetherionQuests/.../QuestRegistry.java`, `CastBook`, `DialogManager`, `lang/de.yml` | Quests/NPCs/lang |
| `AetherionHub/.../origin.yml`, `OriginRole`, `OriginEvents`, `OriginBells` | Hub Origin |
| `Claude outputs/ankerhold_anchors.yml`, `HUB_ANKERHOLD.md` | Ankerhold design (outside worktree) |
| `AetherionItems/.../ProgressionService.java`, `CustomItem.java`, `CodexCatalog` | Progression / lore items / Codex |
| `AetherionCore/.../ServerNames.java` | Velocity names |
| `docs/dialogs/DIALOG_VOICE_DE.md`, `docs/npc/NPC_DIALOGUE_OVERHAUL.md` | Dialog process |
| `.cursor/rules/locked-systems.mdc` | LOCKED |

---

*End of inventory. No code or config was changed for this document.*
