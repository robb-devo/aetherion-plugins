# Opus prompt — Polish Pass STEP 1 (Textures / All skill ladders T1–T5)

**Local agent.** Workspace = worktree `_wt_polish_skill_gear` on branch `claude/polish-skill-gear-t1-t5`.  
Copy everything below the line into the Opus chat.

---

You are a **local** Claude Opus agent executing **Aetherion Polish Pass — STEP 1 only: Textures / Progression Equipment for ALL skill ladders T1–T5**.

You are already in the correct worktree. Branch: `claude/polish-skill-gear-t1-t5`. Do not switch branches or touch `main` WIP.

## First read (in order)

1. `_tmp_polish/START_HERE.md` — kickoff + pack junctions
2. `docs/OPUS_POLISH_PASS_README.md` — impact order (you are STEP 1 only)
3. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A1, B, C (STEP 1), D, F (STEP 1)
4. Then open **only** these:
   - `AetherionItems/resourcepack/pack.mcmeta`
   - `AetherionItems/resourcepack/assets/minecraft/textures/item/` (list + inspect)
   - `AetherionItems/resourcepack/assets/minecraft/models/item/`
   - `AetherionItems/src/main/java/de/aetherion/items/item/CustomItem.java` (CMD beginner/simple — read)
   - `AetherionItems/src/main/java/de/aetherion/items/item/ProgressionItems.java` (CMD 2701–2716 — read)
   - `AetherionItems/src/main/java/de/aetherion/items/item/ArmorAppearance.java` (read)
   - Farming / Foraging / Fishing item factories (find + read CMD/material wiring only):
     - `FarmingItems` (or equivalent hoe/tool factory)
     - `ForagingItems` (or equivalent axe/tool factory)
     - `FishingItems` (or equivalent rod/tool factory)
5. **Reference only (copy overrides/art from, do not treat as write root):**
   - `_tmp_polish/pack_live/` → live Desktop `Aetherion TexturePack` (models/item overrides, existing art)
   - `_tmp_polish/pack_extract/` → full T1–T5 art extract (reference)

Do **not** ingest the entire monorepo. Ignore unrelated dirty WIP on other worktrees/`main`.

## You are NOT here to

- Touch pets, habitats, boss rituals, quests, NPCs, or showcase bosses
- Reassign CustomModelData numbers or item PDC ids
- Redesign factories / `ItemManager` / recipes / stats
- Touch locked systems: boosters, Blossom Blade / Gravwell Cleaver, ranks, anvil sockets, Borderlands vials
- “Make everything more detailed” or upscale all art to 256 blindly
- Depend on `_tmp_polish/pack_extract/` as the only source of truth
- Deploy/restart production

## You ARE here to

Make **Combat, Mining, Farming, Foraging, and Fishing** progression gear T1–T5 look **deliberately authored, cohesive, readable, and polished** at normal Minecraft inventory scale.

### Scope

**P0 — Essential**
1. **Port missing base overrides** into tracked `AetherionItems/resourcepack/assets/minecraft/models/item/` (adapt from `_tmp_polish/pack_live/`), e.g. `chainmail_*.json`, `stone_sword.json`, `stone_pickaxe.json`, plus any base materials Farming/Foraging/Fishing T1 CMDs need. Without base overrides, CMDs never apply from the repo pack.
2. Fix broken source art (e.g. `beginner_pickaxe.png` non–power-of-two) → prefer **64×64** or **256×256** nearest-neighbor. Keep existing CMDs.
3. Unify early `simple_*` / beginner tools + armor into one visual family. Prefer **64×64** for inventory readability unless a ladder already uses consistent 256. Keep existing CMDs.
4. Ensure model JSON overrides still point at updated textures for **all five ladders**.

**P1 — Recommended if quality stays high**
5. Author / wire Progression + skill-ladder art for Combat/Mining/Farming/Foraging/Fishing **T1–T5** (textures + model overrides). Do not change Java CMD values or PDC ids. Skip one-off sentinel CMDs unless trivial.
6. Fix leather `_vanilla` sentinel CMD collisions in `leather_*.json` if you touch those files.

**P2 — Optional**
7. Light polish on existing ladder textures that are already present (e.g. `combat_*.png` / `mining_*.png`) — do not regress.

### Quality bar

- Readable silhouette at default GUI scale
- Cohesive early-tier identity per ladder, still recognizably Aetherion
- Progression line should look different from plain vanilla
- Minecraft visual language: clean pixels — not noisy over-detail
- Preserve assets that are already good

### Hard technical rules

- `pack_format: 34` stays
- Classic CustomModelData overrides only
- Same Material the factory uses must host the CMD predicate
- Never touch booster textures or signature weapon abilities/ids
- **Write only** under `AetherionItems/resourcepack/` (+ rare one-line path fix if a factory points at a missing model path)

## Branch / deliverable

Stay on `claude/polish-skill-gear-t1-t5`. Commit implementation. Push if remotes work; otherwise leave commits local. **STOP.**

Report: player-facing changes per ladder (Combat / Mining / Farming / Foraging / Fishing), files touched, left alone, what Robbi must sync to Desktop pack (`_tmp_polish/pack_live`).

Nothing more. Do not start rituals or pets.
