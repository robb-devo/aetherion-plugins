# Opus prompt — Polish Pass STEP 1 (Textures / Progression Equipment)

**Local agent.** Copy everything below the line into the Opus chat.

---

You are a **local** Claude Opus agent executing **Aetherion Polish Pass — STEP 1 only: Textures / Progression Equipment**.

## First read (in order)

1. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A1, B, C (STEP 1), D, F (STEP 1)
2. Then open **only** these:
   - `AetherionItems/resourcepack/pack.mcmeta`
   - `AetherionItems/resourcepack/assets/minecraft/textures/item/` (list + inspect)
   - `AetherionItems/resourcepack/assets/minecraft/models/item/`
   - `AetherionItems/src/main/java/de/aetherion/items/item/CustomItem.java` (CMD beginner/simple — read)
   - `AetherionItems/src/main/java/de/aetherion/items/item/ProgressionItems.java` (CMD 2701–2716 — read)
   - `AetherionItems/src/main/java/de/aetherion/items/item/ArmorAppearance.java` (read)
3. **Reference only (copy overrides/art from, do not treat as write root):**
   - `C:\Users\Robbi\Desktop\Aetherion TexturePack\assets\minecraft\models\item\` (has `chainmail_*.json`, `stone_sword.json`, `stone_pickaxe.json`)

Do **not** ingest the entire monorepo. Ignore unrelated dirty WIP.

## You are NOT here to

- Touch pets, habitats, boss rituals, quests, NPCs, or showcase bosses
- Reassign CustomModelData numbers or item PDC ids
- Redesign `CustomItem` / `ItemManager` / recipes / stats
- Touch locked systems: boosters, Blossom Blade / Gravwell Cleaver, ranks, anvil sockets, Borderlands vials
- “Make everything more detailed” or upscale all art to 256 blindly
- Depend on `Aetherion_texturepack_extract/` as the only source of truth
- Deploy/restart production

## You ARE here to

Make early progression equipment look **deliberately authored, cohesive, readable, and polished** at normal Minecraft inventory scale.

### Scope

**P0 — Essential**
1. **Port missing base overrides** into tracked `AetherionItems/resourcepack/assets/minecraft/models/item/`: `chainmail_helmet/chestplate/leggings/boots.json`, `stone_sword.json`, `stone_pickaxe.json` (adapt from Desktop TexturePack). Without these, Combat/Mining T1 CMDs never apply from the repo pack.
2. Replace `beginner_pickaxe.png` (1254×1254) with power-of-two art (prefer **64×64** or **256×256** nearest-neighbor). Keep CMD **1001**.
3. Unify `simple_*` tools + armor into one visual family. Prefer **64×64** for inventory readability unless tools stay 256 and armor is lifted to match. Keep CMDs **1002–1005** / **2001–2004**.
4. Ensure model JSON overrides still point at updated textures.

**P1 — Recommended if quality stays high**
5. Author ProgressionItems art for CMD **2701–2716** + material overrides (do not change Java CMD values). Skip `2799` unless trivial.
6. Fix leather `_vanilla` sentinel CMD collisions in `leather_*.json` if you touch those files.

**P2 — Optional**
7. Light polish on existing `combat_*.png` / `mining_*.png` (64) — do not regress.

### Quality bar

- Readable silhouette at default GUI scale
- Cohesive early tier identity
- Progression line should look different from plain vanilla
- Minecraft visual language: clean pixels — not noisy over-detail
- Preserve assets that are already good

### Hard technical rules

- `pack_format: 34` stays
- Classic CustomModelData overrides only
- Same Material the factory uses must host the CMD predicate
- Never touch booster textures or signature weapon abilities/ids
- **Write only** under `AetherionItems/resourcepack/` (+ rare one-line path fix if needed)

## Branch

`claude/polish-step1-textures` from current local `main` (or Robbi’s pointed HEAD).  
Commit implementation. Push if remotes work; otherwise leave commits local. **STOP.**

## Deliverable

- STEP 1 shipped
- Brief report: player-facing changes, files touched, left alone, whether 2701–2716 done, what Robbi must sync to Desktop pack

Nothing more. Do not start rituals or pets.
