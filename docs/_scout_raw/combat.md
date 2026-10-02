Factual scout only. Sources are YML templates + dungeon loot bridges. No redesign.

---

## 1. BossEngine bosses

**Root:** `BossEngine/src/main/resources/bosses/*.yml` (37 templates)

### Live / content bosses

| ID | Max HP | Attack (base) | Phases (id @ HP%) | Known loot (item-id / notes) | File |
|---|---:|---:|---|---|---|
| `mcnugget` | 3000 | 30 | cluck@100 → nugget_panic@50 | `compressed_coal`, `random_booster` | `mcnugget.yml` |
| `hollow_lurker` | 3000 | 32 | lurking@100 → frenzy@50 | `warped_blade` (0.40/0.16/0.06), `random_booster`, DIAMOND_BLOCK×32 | `hollow_lurker.yml` |
| `bridge_troll` | 3000 | 34 | stomp@100 → storm@50 | `bridged_axe` (0.12), `random_booster`, GOLD_BLOCK | `bridge_troll.yml` |
| `skuldugery` | 3000 | 30 | volley@100 → inferno@50 | `skuldugery_shortbow` (0.15), `catch_sphere_epic`, `random_booster` | `skuldugery.yml` |
| `ashen_sheath` | 5200 | 38 | quiet_steel@100 → falling_blossoms@66 → ashen_moon@33 | `ashen_katana` (killer 0.22; rank 0.14/0.06/0.03), `random_booster` | `ashen_sheath.yml` |
| `dungeon_sentinel` | 5800 | 72 | hold@100 → rupture@50 | **Empty** (dungeon chests pay) | `dungeon_sentinel.yml` |
| `squidward` | 11000 | 48 | ink@100 → inkstorm@66 → boil@33 | `squids_boot` (0.12), `random_booster`, INK_SAC | `squidward.yml` |
| `world_eater_unbroken` | 12000 | 0* | the_floor@100 → one_arm@50 | `random_booster` (0.25), CRYING_OBSIDIAN | `world_eater_unbroken.yml` |
| `cinder_herald` | 12000 | 30 | herald@100 → ashfall@50 | `random_booster` (0.10) | `cinder_herald.yml` |
| `ashen_chainwarden` | 16000 | 42 | warden@100 → keys@50 | `random_booster` (0.10) | `ashen_chainwarden.yml` |
| `helios_herald` | 26000 | 0* | herald@100 → mirror@50 | **Empty** (comment: Act II pays) | `helios_herald.yml` |
| `aether_colossus` | 38000 | 74 | awakened@100 → enraged@50 → last_stand@20 | `combat_sword` (1.0), `combat_chestplate` (0.35), NETHER_STAR, DIAMOND | `aether_colossus.yml` |
| `world_eater` | 45000 | 0* | hunger@100 → starless@70 → unmade@40 → ouroboros@15 | `worldbite`, `random_worldhide_armor`, `random_booster`, NETHER_STAR | `world_eater.yml` |
| `pathwarden` | 52000 | 520 | gate@100 (single) | `gravwell_cleaver` (0.10/0.04/0.02), `random_booster` | `pathwarden.yml` |
| `helios_requiem` | 52000 | 0* | korona@100 → zerfall@60 → singularitaet@25 → requiem@6 | `helios_solstice`, `random_helios_armor`, `random_booster`, NETHER_STAR | `helios_requiem.yml` |
| `eggquelizer` | 60000 | 0* | soundcheck@100 → headliner@60 → distortion@30 | Vanilla only (EGG, NOTE_BLOCK, XP bottles); comment: placeholders | `eggquelizer.yml` |
| `dungeon_frostbound` | 90000 | 200 | packed@100 → blizzard@55 → hearth@22 | **Empty** (dungeon chests pay) | `dungeon_frostbound.yml` |
| `dungeon_aetherion` | 115000 | 240 | challenger@100 → stormborn@75 → tempest@50 → singularity@25 | **Empty** (dungeon chests pay) | `dungeon_aetherion.yml` |
| `sir_balthazar` | 115000 | 142 | lecture@100 → hotfix@65 → rollback@30 | `staff_of_technical_difficulties` (0.34/0.18), `random_booster`, NETHER_STAR | `sir_balthazar.yml` |
| `hanging_saint` | 120000 | 0* | overture@100 → the_hand@66 → unstrung@33 | `seraphine_needle`, `random_seraphine_armor`, `random_booster` | `hanging_saint.yml` |
| `hollow_sun` | 120000 | 34 | main_sequence@100 → red_giant@66 → collapse@33 | `random_booster` ranks, NETHER_STAR, GLOWSTONE_DUST, CRYING_OBSIDIAN | `hollow_sun.yml` |
| `sparky` | 120000 | 148 | warm@100 → melt@75 → grid@50 → meltdown@28 | `thermal_core` (0.34/0.18), `random_booster` | `sparky.yml` |
| `lobby_cleaner` | 125000 | 154 | sweep@100 → overtime@55 → shredder@22 | `void_vacuum_charm` (0.34/0.18), `random_booster` | `lobby_cleaner.yml` |
| `baron_von_wurm` | 128000 | 160 | nibble@100 → quarry@60 → feeding@28 | `pickaxe_core_of_the_burrower` (0.34/0.18), `random_booster` | `baron_von_wurm.yml` |
| `insolvent_wither` | 145000 | 168 | audit@100 → foreclosure@55 → chapter_eleven@22 | `insolvent_ledger` (0.34/0.18), `random_booster`, NETHER_STAR | `insolvent_wither.yml` |
| `aetherion` | 200000 | 92 | challenger@100 → stormborn@75 → tempest@50 → singularity@25 | `aetherion_void_stick` (0.10), `random_aetherion_armor` (1.0/0.70/0.15) | `aetherion.yml` |

\* `attack-damage: 0` in attributes — damage comes from skills, not melee attr.

### Sandbox / test arena (explicit “no live drops”)

| ID | Max HP | Phases | Loot | File |
|---|---:|---|---|---|
| `test_petjury` | 31000 | spectacle@100 → enraged@40 | empty | `test_petjury.yml` |
| `test_afterimage` | 32000 | spectacle@100 → enraged@40 | empty | `test_afterimage.yml` |
| `test_softlock` | 33000 | spectacle@100 → enraged@40 | empty | `test_softlock.yml` |
| `test_broker` / `test_parity` | 34000 | spectacle@100 → enraged@40 | empty | matching yml |
| `test_nullspace` | 35000 | spectacle@100 → enraged@40 | empty | `test_nullspace.yml` |
| `test_echo` / `test_heartbeat` | 36000 | spectacle@100 → enraged@35–40 | empty | matching yml |
| `test_gravity` | 37000 | spectacle@100 → enraged@35 | empty | `test_gravity.yml` |
| `test_curator` | 38000 | spectacle@100 → enraged@35 | empty | `test_curator.yml` |
| `test_quiet` | 40000 | spectacle@100 → enraged@40 | empty | `test_quiet.yml` |
| `test_loadbearing` | 52000 | sealed@100 → critical@40 | empty | `test_loadbearing.yml` |

### Rough HP bands (data-only; not designer labels)

| Band (max-health) | Bosses that fall in it |
|---|---|
| ~3k–6k | island entry + `dungeon_sentinel`, `ashen_sheath` |
| ~11k–16k | `squidward`, Unbroken, F3 elites (`cinder_herald`, `ashen_chainwarden`) |
| ~26k–60k | Helios Herald/Requiem, Colossus, World Eater, Pathwarden, Eggquelizer |
| ~90k–145k | dungeon F2/F3 bosses + high island set bosses (Sparky, Seraphine, etc.) |
| 200k | open-world `aetherion` |

No other tier taxonomy exists in the YML beyond HP and dungeon floor wiring.

---

## 2. AetherionDungeons floors / gear / rewards

### Floor map

| Floor | Theme (UI) | Floor ID | Boss template | Elites | Key files |
|---|---|---|---|---|---|
| **1** | Prison / Warden's Prison | `prototype_1` | `dungeon_sentinel` (5800 HP) | — | `DungeonMenu.java`, `BossEngineBridge.java`, `structures/floor1/pool.yml`, `config.yml` (`floor1-pool`) |
| **2** | Frostbound | `prototype_2` | `dungeon_frostbound` (90000 HP) | — | same + `EndlessEncounter` / schematic path |
| **3** | Throne of Ashes / Aetherion | `prototype_3` | `dungeon_aetherion` (115000 HP) | `ashen_chainwarden` @ ~40% clear, `cinder_herald` @ ~70% clear | `AshesEncounter.java`, `BossEngineBridge.java` |

Wiring: `BossEngineBridge.templateForFloor(floor)` → F1 Sentinel / F2 Frostbound / F3 Aetherion.

### Floor trash HP (solo baseline; party scales ×1.0 + 0.40 per extra)

From `PrototypeDungeonBuilder.java` (`hp(f1,f2,f3)`):

| Mob tag | F1 HP / dmg | F2 HP / dmg | F3 HP / dmg |
|---|---|---|---|
| zombie (`dungeon_zombie`) | 750 / 46 | 12000 / 420 | 8200 / 330 |
| skeleton/stray | 480 / 40 | 9000 / 340 | 5800 / 265 |
| husk brute | 1200 / 64 | 24000 / 580 | 13500 / 400 |
| wither skeleton “warden” | 3400 / 78 | 42000 / 680 | 21000 / 460 |
| enderman (F3 theme) | 1900 / 125 | 1900 / 125 | 8200 / 330 |

Ashes F3 trash (world copy, separate): PiglinBrute HP 40, WitherSkeleton HP 28 — `AshesEncounter.java`.

### Gear / rewards (chests, not boss YML)

Sources: `ItemLootBridge.java`, `DungeonLootFx.java`, `DungeonGearTier.java`, `DungeonCore.java`.

| Floor | Combat caches | Victory chest | Core ID | Armor / weapons from chests |
|---|---|---|---|---|
| **1** | vestige + weapon schematic (r≥44 path), boosters, compressed/compacted | same + cores + boss relics | `dungeon_core` | `dungeon_vestige_{helmet,chestplate,leggings,boots}`; `dungeon_weapon_schematic` (Floor I) |
| **2** | cores / mats / boosters — **no vestige/schematic** | cores + mats; boss relic roll | `dungeon_core_2` | gear upgrades via cores (T2 in Items: `dungeon_t2_*` / `dungeon_relic_t2_*`) |
| **3** | same pattern as F2 | + **3%** `random_aetherion_armor`; cores; relics | `dungeon_core_3` | T3 relics (`dungeon_relic_t3_*`); tiny myth set chance |

Victory shared rolls (all floors, after F3 myth check): boss relic pool `warped_blade` / `aetherblade` / `bridged_axe` at r&lt;8; core at r&lt;22.

Explicit floor→tier enum: `AetherionItems/.../DungeonGearTier.java` — T1 floor=1 (1.00×), T2 floor=2 (1.60×), T3 floor=3 (2.25×).

Builder comment (intent, not a drop table): F1 = entry fight; F2 needs dungeon kit; F3 Aetherion set “not a souvenir” — `PrototypeDungeonBuilder.java` ~85–87.

---

## 3. Aethermobs

**No combat-enemy templates present.** Plugin is pets / catch / habitats / skill dragons.

| Finding | Evidence |
|---|---|
| Only resource YML | `Aethermobs/src/main/resources/plugin.yml` |
| Content surface | `PetDefinition`, `PetRegistry`, `PetSpawnManager`, catch spheres, habitat zones |
| “Dragon” listeners | Farming/Fishing/Foraging/Ascension pet dragons — not BossEngine enemies |

No HP/phase/loot enemy roster to list.

---

## Key path index

| Area | Paths |
|---|---|
| Boss templates | `BossEngine/src/main/resources/bosses/` |
| Dungeon↔boss | `AetherionDungeons/.../bridge/BossEngineBridge.java` |
| Dungeon loot rolls | `.../bridge/ItemLootBridge.java`, `.../instance/DungeonLootFx.java` |
| Floor build / trash stats | `.../instance/PrototypeDungeonBuilder.java` |
| F3 ashes | `.../instance/AshesEncounter.java` |
| Floor UI | `.../menu/DungeonMenu.java`, `DungeonGuideGUI.java` |
| F1 room pool | `.../resources/structures/floor1/pool.yml` |
| Gear tiers / cores | `AetherionItems/.../dungeon/DungeonGearTier.java`, `.../item/DungeonCore.java` |