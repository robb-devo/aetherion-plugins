# Opus prompt — Seraphine loot (armor / weapon / music-box chest)

Copy everything below the line into the Cloud workspace chat.

---

You are finishing **Seraphine, the Hanging Saint** for Aetherion.

## First read

1. `BossEngine/docs/OPUS_SERAPHINE_LOOT_CONTEXT.md`  
2. Skim (do not rewrite): `HollowReliquary`, `BossManager.payIntoReliquary` / `payoutDeath`, `LootService.grantToChest`, `AetherionItemHook`, and one signature-weapon listener pattern in AetherionItems (**read-only** on Blossom Blade / Gravwell Cleaver).

Branch: **`claude/boss-hanging-saint`** (points 1–3 already pushed).

## Goal

Implement **only** the open handoff points:

### 4. Seraphine drops (her gear — must match the boss)
These are **Seraphine’s boss rewards**, not generic legendaries. Armor + weapon must feel like they came off **her** fight and stage:

- Visual / naming language: porcelain doll, gold kintsugi seams, sewing needles, golden threads, marionette / music-box / theatre / Gilded Proscenium
- Look at her body + Hand + stage dressing for color and material cues — then invent the set in that world
- Full armor set + signature weapon in **AetherionItems** (`CustomItem` factories + profiles + ability listener if the weapon has one)
- Weapon ability fantasy should echo the fight (threads, needles, stop-motion, curtain/hand, etc.) — spectacle-grade, balance later
- Wire loot ids so BossEngine can resolve them (`AetherionItemHook` / reflective `create…` naming)
- Fill `hanging_saint.yml` loot table with those ids (placeholders OK for chances/amounts)

Do **not** ship random neon / void / sun gear that could belong to another boss.

### 5. Music-box loot chest
- BossEngine spectacle prop: music box on golden threads from the clouds, ballerina figure, per-player claim, dismantle finale
- Same loot contract as Hollow Reliquary (`grantToChest` → place → Interaction → expire payout)
- Hooks in `BossManager` (hanging_saint path), listener, `onDisable` cleanup
- Temporary stage teardown must wait until the chest is finished

## Hard scope

- Do **not** polish the fight again (spot / Act III / Hand are done)
- Do **not** touch locked signature weapons’ abilities
- Do **not** deploy or restart servers
- Do **not** invent unrelated systems

Creative freedom on names, looks, and ability fantasy — quality bar is Seraphine / Hollow Reliquary, not a generic chest.

## Deliverable

Commit + push to the branch. Then **STOP**.

Tell me: item ids, chest class name, files/hooks touched, brief fantasy of armor/weapon/box — nothing more.
