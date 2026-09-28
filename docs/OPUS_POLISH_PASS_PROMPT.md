# Opus prompt — Polish Pass STEP 1 (Textures / Progression Equipment)

Copy everything below the line into the Cloud workspace chat.

---

You are executing **Aetherion Polish Pass — STEP 1 only: Textures / Progression Equipment**.

## First read (in order)

1. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A1, B, C (STEP 1 rows), D, F (STEP 1)
2. Then open **only** these:
   - `AetherionItems/resourcepack/pack.mcmeta`
   - `AetherionItems/resourcepack/assets/minecraft/textures/item/` (list + inspect)
   - `AetherionItems/resourcepack/assets/minecraft/models/item/` for starter/progression bases
   - `AetherionItems/src/main/java/de/aetherion/items/item/CustomItem.java` (CMD for beginner/simple only — read)
   - `AetherionItems/src/main/java/de/aetherion/items/item/ProgressionItems.java` (CMD 2701–2716 — read)
   - `AetherionItems/src/main/java/de/aetherion/items/item/ArmorAppearance.java` (read)

Do **not** ingest the entire monorepo.

## You are NOT here to

- Touch pets, habitats, boss rituals, quests, NPCs, or showcase bosses
- Reassign CustomModelData numbers or item PDC ids
- Redesign `CustomItem` / `ItemManager` / recipes / stats
- Touch locked systems: boosters, Blossom Blade / Gravwell Cleaver, ranks, anvil sockets, Borderlands vial contracts
- “Make everything more detailed” or upscale all art to 256 blindly
- Depend on `Aetherion_texturepack_extract/` (it is **gitignored** — not available to you as source of truth)
- Deploy/restart production

## You ARE here to

Make early progression equipment look **deliberately authored, cohesive, readable, and polished** at normal Minecraft inventory scale.

### Scope (do this)

**P0 — Essential**
1. Replace `beginner_pickaxe.png` (currently 1254×1254) with a proper power-of-two texture (prefer **64×64** or **256×256** nearest-neighbor). Keep CMD **1001** / diamond_pickaxe override.
2. Unify `simple_pickaxe`, `simple_axe`, `simple_sword`, `simple_hoe`, and `simple_*` armor icons into one coherent visual family. Prefer **64×64** for inventory readability unless existing 256 tool art is already strong and you only need armor to match the same language. Keep CMDs **1002–1005** / **2001–2004**.
3. Ensure matching `models/item/*.json` still point at the updated textures. Do not break override predicates.

**P1 — Recommended if quality stays high**
4. Author missing ProgressionItems art for CMD **2701–2716** (and wire base-material JSON overrides). Do **not** change Java CMD values — only add pack assets + overrides. Skip `2799` voided unless trivial.
5. Add missing base overrides where needed (e.g. `golden_sword.json` for copper/midas line) without colliding with other CMD users on that material.

**P2 — Optional**
6. Light polish on combat/mining T1 armor icons already in the plugin pack (`combat_*.png`, `mining_*.png` at 64) only if time remains — do not regress swords/picks.

### Quality bar

- Readable silhouette at default GUI scale
- Cohesive early tier identity (starter kit should feel like one set)
- Progression line should look meaningfully different from plain vanilla
- Minecraft visual language: clean pixels, controlled detail — **not** noisy over-detail
- Preserve assets that are already good; rewrite only what fails the bar

### Hard technical rules

- `pack_format: 34` stays
- Classic CustomModelData overrides only (no wholesale 1.21.4+ `item_model` migration)
- Same Material the factory uses must host the CMD predicate
- Never touch booster textures/models or signature weapon abilities/ids
- Prefer extending existing model JSON override lists over new parallel systems
- Commit art + JSON only unless a one-line path fix is required in Java (should be rare)

## Branch

Work on a new branch: **`claude/polish-step1-textures`** from current `main`.  
Commit + push implementation to that branch. Then **STOP**.

## Deliverable

- STEP 1 textures/models shipped
- Commit + push to `claude/polish-step1-textures`
- Brief report:
  - What changed player-facing
  - Exact files touched
  - What you deliberately left alone
  - Whether ProgressionItems 2701–2716 were completed or deferred
  - Anything Robbi must sync to the gitignored live client extract

Nothing more. Do not start STEP 2 or STEP 3.
