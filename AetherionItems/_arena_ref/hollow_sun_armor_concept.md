# The Hollow Sun: boss armor set (concept only)

Design doc for a future implementer. Nothing here is implemented yet. It is based on `_arena_ref/hollow_sun.yml`, `BossEngine/.../HollowSunDirector.java` (palette, tells, sound language) and the existing Mythic boss armor format in `CustomItem.createAetherionArmor` (Aetherion set, CMD 2311–2314, `BossGearBalance.stamp`).

---

## 1. Set name and fantasy

**Set: Remnant of the Hollow Sun** (short name: *Hollow Sun Plate*)

The Hollow Sun was a star that fell into the Bloodstone forge and put on its war-plate. When it dies, the armor bursts apart and the core goes back up to the sky. These are the plates that stayed behind. They are still warm, and something inside them still keeps time like a star. Wearing the set takes you through the star's life story: the steady gold burn of the Main Sequence, the swollen fury of the Red Giant, and at the end, the Collapse into a black star that refuses to die quietly.

---

## 2. The four pieces

All four are **MYTHIC** (same tier as the Aetherion set), unbreakable, vanilla attributes hidden, stamped with `BossGearBalance.stamp(meta)`.

| Slot | Display name | Base material | CMD (proposal) | Phase motif |
|---|---|---|---|---|
| Helmet | `§6✦✦✦ §eCorona Visor` | `NETHERITE_HELMET` | 2341 | Main Sequence: the crown of light |
| Chest | `§6✦✦✦ §6Hollow Heart Cuirass` | `NETHERITE_CHESTPLATE` | 2342 | The caged star core |
| Legs | `§6✦✦✦ §cRed Giant Greaves` | `NETHERITE_LEGGINGS` | 2343 | Red Giant: swelling heat |
| Boots | `§6✦✦✦ §5Starfall Sabatons` | `NETHERITE_BOOTS` | 2344 | Collapse / Starfall |

Notes:
- The CMD range 2341–2344 is a **proposal**. Check `resourcepack/` and `CustomItem` for collisions before using it.
- Until custom models exist, use vanilla **Armor Trims** for the look: trim material `GOLD`; patterns `SPIRE` (helmet), `RIB` (chest), `SNOUT` or `SILENCE` (legs), `FLOW` (boots). Netherite plus gold trim reads as "black plate, burning seams", which matches the boss.
- Suggested item ids: `hollow_sun_helmet`, `hollow_sun_chestplate`, `hollow_sun_leggings`, `hollow_sun_boots`.

---

## 3. Per-piece stats (ballpark)

These use the Aetherion `ItemStats` fields and the same lore order as `createAetherionArmor`. The Aetherion Mythic set is the reference point: helmet 70 / 110 / 22 / 22 / 6 / 42 / 2. The Hollow Sun set is tuned **tankier with higher crit damage** and slightly less raw Damage and Attack Spread. It trades wide cleave for heavy single hits, like a star-knight's mace.

| Piece | 🛡 Defense | ❤ Health | ⚔ Damage | ⚔ Attack Spread | ✧ Crit Chance | ✧ Crit Damage | ✦ Speed |
|---|---|---|---|---|---|---|---|
| Corona Visor | 80 | 120 | 18 | 12 | 8% | 48% | 2% |
| Hollow Heart Cuirass | 160 | 210 | 34 | 20 | 10% | 75% | 2% |
| Red Giant Greaves | 120 | 160 | 28 | 16 | 8% | 60% | 3% |
| Starfall Sabatons | 80 | 115 | 18 | 12 | 6% | 45% | 5% |
| **Full set** | **440** | **605** | **98** | **60** | **32%** | **228%** | **12%** |

For comparison, the full Aetherion set is 385 / 545 / 118 / 105 / 32% / 209% / 14%. Final numbers should go through `BossGearBalance` like every other boss set.

---

## 4. Per-piece effects

Each piece gets one identity effect. No stacking procs and no constant particle spam.

### Corona Visor: *Solar Sight*
- **Passive:** your **crits** mark the target **Sunmarked** for 4s. A thin gold ring sits at its feet. Sunmarked targets take **+6%** damage from you. One mark per target; re-critting only refreshes the timer.
- It reads like the boss's gold ground tell ("something lands here") turned against the enemy.

### Hollow Heart Cuirass: *Caged Star*
- **Passive, core heat:** dealing damage fills a hidden **Heat** meter (0–100). At 100 the chest core glows white on the next hit, and that hit adds a **Corona burst**: a 4-block ring dealing 60% of the hit as scripted damage (`ScriptedHits`). Heat then resets.
- Heat decays when you stop fighting (−10 per second after 3s idle), so it cannot be pre-charged.
- Action bar: `§6☀ Heat §f73%` only while above 50%. Otherwise silent.

### Red Giant Greaves: *Swell*
- **Passive:** while you are **below 50% HP**, you swell: **+10% Damage** and **+8% Defense**, and your melee hits leave a 1.5s **Flare** burn on the target (a small tick, well under Meteor Mace's pool).
- Cosmetic: a subtle `GENERIC_SCALE +0.06` while swollen, if the scale attribute is safe to touch at that point.

### Starfall Sabatons: *Starfall*
- **Active:** **double-tap sneak in mid-air** (at least 3 blocks above the ground) to fall like the boss's Starfall. You plunge straight down, cancel fall damage, and the landing makes a crimson-disc shockwave (radius 4, knockback plus about 1.5× weapon damage, `ScriptedHits`).
- **Cooldown 18s.** A crimson disc tell appears on the ground for 6 ticks before you land, the same tell language as the boss.

---

## 5. Set bonuses (phase fantasy)

### 2 pieces: **Main Sequence**, *Steady Burn*
- Every **5th melee hit** on the same target calls a **Starcall lance**: a gold spear of light drops onto it (small `BlockDisplay` beam, 3 ticks) for **35%** of the hit.
- The gold, steady rhythm is the boss's first phase, "a knight who fights clean".

### 3 pieces: **Red Giant**, *Burn Brighter*
- When you take **big damage** (one hit over 20% max HP) or fall below 35% HP: **Red Giant** for **8s**, once per **45s**.
  - Two **flare beams** rotate slowly around you at waist height (particle lines, radius 3.5). Enemies they cross take a small burn tick every 10 ticks.
  - **+15% Damage** during the window. Your Swell (Greaves) also stays active above 50% HP.
- Line on trigger (action bar): `§c§lRED GIANT §7— §fI can burn brighter.`

### 4 pieces: **Collapse**, *Last Light*
- **Once per 12 minutes**, a hit that would kill you **collapses** you instead:
  1. You fold into a **black star** for 1.5s: invulnerable, slowed, with a black and violet core and inward particles. Nearby enemies (6 blocks) are **pulled toward you** (the boss's gravity-well feel).
  2. **Nova:** a white strobe, then a violet shockwave. Enemies in 6 blocks take a large scripted hit (about 3× weapon damage, capped for bosses) and are knocked back.
  3. You come back at **35% HP** with **3s of Resistance II**. The armor pieces "burst" outward as displays and snap back onto you.
- If the Aetherion set is ever wearable alongside this one, it would overlap the Aetherion set's "survive death once". It can't be, since both are full sets, but keep the cooldowns on separate keys anyway.
- Chat line on trigger: `§5✦ Last Light §7— §dthe star refused to go out.`

---

## 6. Lore lines

Format mirrors `createAetherionArmor`: the rarity line, then stats, then effects, then a flavor footer.

**Corona Visor**
```
§7✦ §5MYTHIC
(stats…)

§e☀ Solar Sight
§7Crits §eSunmark §7the target for §f4s§7.
§7Sunmarked foes take §e+6% §7damage from you.

§6Remnant of the Hollow Sun §8(§f1§8/§f4§8)
§8It still remembers how to shine.
```

**Hollow Heart Cuirass**
```
§7✦ §5MYTHIC
(stats…)

§6☀ Caged Star
§7Dealing damage builds §6Heat§7.
§7At §f100§7, your next hit bursts in a
§6Corona §7for §f60% §7of its damage.

§6Remnant of the Hollow Sun §8(§f2§8/§f4§8)
§8The plate is hollow. The heart is not.
```

**Red Giant Greaves**
```
§7✦ §5MYTHIC
(stats…)

§c☀ Swell
§7Below §f50% §7HP: §c+10% §7Damage, §c+8% §7Defense,
§7and your hits leave a brief §cFlare§7.

§6Remnant of the Hollow Sun §8(§f3§8/§f4§8)
§8I burned for ten thousand years.
```

**Starfall Sabatons**
```
§7✦ §5MYTHIC
(stats…)

§5☀ Starfall §8(Double-sneak in the air)
§7Plunge down and land in a crimson shockwave.
§8Cooldown: §f18s

§6Remnant of the Hollow Sun §8(§f4§8/§f4§8)
§8Every star falls.
```

**Set block** (on every piece, after the piece effect):
```
§6Set: Remnant of the Hollow Sun
§e2 · Main Sequence §7— every §f5th §7hit calls a §eStarcall§7.
§c3 · Red Giant §7— heavy blows make you §cburn brighter§7.
§54 · Collapse §7— cheat death once as a §5black star§7,
§7    then §dNova§7. §8(12m)
```

---

## 7. VFX / SFX identity notes

Reuse the **HollowSunDirector palette** so the set reads as the boss's own plate:

| Name | RGB | Use |
|---|---|---|
| GOLD | 255, 196, 64 | Main Sequence, Sunmark rings, Starcall lances |
| SOLAR | 255, 236, 160 | Soft highlights, Heat glow |
| HOT | 255, 255, 255 | "Fires now" strobe (Corona burst, Nova) |
| FLARE | 255, 64, 24 | Red Giant beams, Swell burn |
| CRIMSON | 205, 18, 40 | Starfall landing disc |
| VIOLET | 150, 70, 255 | Collapse shockwave |
| VOID | 70, 22, 120 | Black-star core, gravity inflow |
| PHOTON | 236, 226, 255 | Nova afterglow |

**Idle (full set):** very low key. Every 2–3 seconds a single gold `DUST` mote rises from the chest seam, plus one `END_ROD` flicker near the helmet (the corona). No ambient sound. It should never be noisy in a crowd.

**Tell language (same as the boss):** gold, flare-red or violet ground marks where something lands; a white strobe means it fires now. Starfall shows a crimson disc before impact. Nova strobes white before the violet ring.

**Sounds** (all already used in `HollowSunDirector` / this repo):
- Sunmark: `BLOCK_AMETHYST_BLOCK_CHIME` (quiet, high pitch).
- Corona burst: `BLOCK_BEACON_POWER_SELECT` + `ENTITY_BLAZE_SHOOT` (low).
- Starcall lance: `ITEM_TRIDENT_THROW` (pitch 0.8) + `BLOCK_BEACON_ACTIVATE` (quiet).
- Red Giant trigger: `BLOCK_RESPAWN_ANCHOR_CHARGE` + `BLOCK_FIRE_AMBIENT`; the beams hum with `BLOCK_BEACON_AMBIENT` at a rising pitch.
- Starfall landing: `ITEM_MACE_SMASH_GROUND_HEAVY` + `ENTITY_GENERIC_EXPLODE` (pitch 0.6).
- Last Light collapse: `BLOCK_BEACON_DEACTIVATE` + `ENTITY_WARDEN_HEARTBEAT` (pitch 0.5), then half a second of **silence**. The boss's Collapse transition uses silence as its signature. Then Nova: `ENTITY_WARDEN_SONIC_BOOM` + `BLOCK_RESPAWN_ANCHOR_DEPLETE`.
- Armor re-forming after the Nova: `ITEM_ARMOR_EQUIP_NETHERITE` stepping up in pitch across the 4 pieces, the same as the boss's arrival assembly.

**Display-entity moments** (implement like `JudgmentVerdict` / `StormcallerTempest`: `LIVE` list, `shutdown()` hooked into `AetherionItems.onDisable`, capped live count):
- Starcall lance: 1 glowing `BlockDisplay` beam (white core, gold glow), 3 ticks, then shrinks.
- Last Light: 4 small netherite `ItemDisplay` plates burst outward from the player and ease back in over 10 ticks, around a black concrete core with a violet glow.

---

## 8. Drop / acquisition (concept)

The current `hollow_sun.yml` loot table gives boosters, glowstone, crying obsidian, a Nether Star and XP. The set plugs in **alongside** that table, without replacing it. Booster loot stays exactly as it is (Boosters are a LOCKED system).

- **Hollow Sun Plate Fragment** (new material item): 1–3 for every participant who passes the 5% threshold, scaled by damage. Suggested id: `hollow_sun_fragment`, material `GOLD_NUGGET` with custom model data.
- **Direct piece drop:**
  - Rank 1: 12% for a random piece.
  - Ranks 2–3: 6%.
  - Killer bonus: +3%.
- **Forge path:** 4 pieces × (about 24 fragments + 1 `NETHER_STAR` for the chest) at the forge or station, so the set can be finished without pure RNG. About 8–12 clears for a full set.
- **Flavor:** the fragments are the plates that "burst" in the death cinematic. The core goes back to the sky; the armor stays with whoever was standing closest when the star went out.

---

### Implementation guardrails for whoever builds this

- Do not change Boosters, Borderlands vials, the Custom Anvil GUI (`BoosterSocketMenu` / `BoosterSockets`), the rank system or the shutdown countdown.
- Whether Dungeon Cores can be infused into this set is an open design question. Ask Robbi first; do not copy `DungeonCore.AETHERION_CORE_HINT` by default.
- All scripted damage goes through `ScriptedHits.run(...)`, and targets are filtered with the same rules as `TestPrototypeAbilities.isCombatTarget`: no players, pets, NPCs or set minions.
