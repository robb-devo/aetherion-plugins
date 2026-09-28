# Seraphine loot handoff — Opus context (points 4–5)

Continue from branch **`claude/boss-hanging-saint`**. Points **1–3** (follow-spot, Act III pacing, falling Hand frame) are **done and pushed**. Your job is **only** points **4–5**: Seraphine drops (armor + weapon) and the music-box loot chest.

Do **not** rework the fight choreography. Do **not** deploy/restart live servers.

---

## Where you are

| Piece | Status |
|--------|--------|
| Fight + arena (`BossEngine/.../instance/saint/`) | Done |
| Follow-spot / matrix flicker fix / Act III denser / Hand frames her | Done (latest commit on this branch) |
| 4. Armor + weapon drops | Done: The Mended Saint set + Severance (`SeraphineGearListener`), ids in `hanging_saint.yml` |
| 5. Music-box loot chest | Done: `loot/SeraphineMusicBox` + `LootService.grantToChest`, stage held via `SaintStage.hold` |

Boss id: `hanging_saint`  
Display: Seraphine, the Hanging Saint  
YAML: `BossEngine/src/main/resources/bosses/hanging_saint.yml` (loot section still a stub — fill it)

---

## Point 4 — Drops (AetherionItems)

### Pattern

Boss loot IDs resolve through `BossEngine` → `AetherionItemHook.createById`:

1. Explicit switch cases, else  
2. `CustomItem.createDungeonFromId(id)`, else  
3. Reflective `CustomItem.createCamelCaseFromSnakeId()` — e.g. id `seraphine_needle` → method `createSeraphineNeedle()`

So the clean path is:

- Add `create…()` factories on `CustomItem` (armor pieces + weapon)
- Register `ItemProfile` entries
- Wire `AetherionItemHook` cases if reflection naming is awkward
- Add a **new** ability listener (like Blossom Blade’s pattern) — **do not edit** `AshenKatanaListener` / `GravwellCleaverListener` (LOCKED)
- Register listener in `AetherionItems.onEnable`
- Optional: Dev Menu / Test Gear exposure for playtest
- Fill `hanging_saint.yml` `loot:` with `source: AETHERION` + your `item-id`s (killer / rank / shared / per-damager as fits the fantasy)

### Theme lock — these are *her* drops

Armor + weapon are **Seraphine’s boss loot**. They must read as belonging to this encounter only:

| Cue from the fight | Carry into gear |
|--------------------|-----------------|
| Porcelain body, gold seams | Armor materials / lore / trim language |
| Giant sewing needles | Weapon silhouette or ability tell |
| Five golden threads / Hand Above | Ability VFX, particles, sound |
| Music box / theatre / Gilded Proscenium | Names, lore, set identity |
| Unstrung stop-motion Act III | Optional ability cadence / feel |

Invent names and exact pieces freely, but **do not** make generic “cool legendary” gear that could drop from Hollow Sun or a dungeon. If you removed the name “Seraphine,” a player should still guess these came from the hanging doll boss.

Spectacle-grade VFX on the weapon ability if it has one. Balance is **not** the goal; identity + look + feel are.

Study craft only:

- Blossom Blade / Gravwell Cleaver → how a signature weapon is stamped + listened (do not change them)
- Hollow Sun loot YAML → how boss tables reference Aetherion ids
- `instance/saint/*` → her visual language (read, don’t rewrite)

---

## Point 5 — Music-box loot chest (BossEngine)

### Contract to mirror: `HollowReliquary`

| Hook | What it does |
|------|----------------|
| `LootService.grantToChest(event)` | XP + recap now; returns `Map<UUID, List<ItemStack>>` bundles |
| `BossManager.payIntoReliquary` | For `hollow_sun` only today — delay, then `HollowReliquary.place(...)` |
| `HollowReliquary.place(plugin, anchor, bossName, bundles)` | Spectacle prop + `Interaction` claim |
| Per-player claim / expire → ground payout | Never lose loot |
| `HollowReliquary.clearAll()` | Called from `BossEngine.onDisable` |
| `ReliquaryListener` | Interaction clicks |

### Seraphine version (Opus plan — implement this)

Fantasy: a **music box** lowered from the clouds on **golden threads**, with a **ballerina** figure, per-player payout, dismantle finale.

Technical:

1. New class e.g. `SaintMusicBox` / `SeraphineMusicBox` under `BossEngine/.../loot/` (same package style as `HollowReliquary`)
2. Extend `BossManager.payIntoReliquary` (or sibling) so `hanging_saint` also uses `grantToChest` + delayed place on stage center (prefer stage/spawn focus Seraphine already owns — not a random ground block)
3. Hook `ReliquaryListener` (or dedicated listener) for the Interaction
4. `clearAll` / shutdown from `BossEngine.onDisable` beside HollowReliquary
5. **Stage teardown wait:** today `HangingSaintDirector` schedules `SaintStage.strike` after `STRIKE_DELAY_TICKS` (45s) when death finishes. Temporary stages must **not** strike while the music box is still live — gate strike on chest completion / expiry, or extend delay dynamically. Permanent stages (`/boss stage build`) only clear temp blocks; still don’t yank the box.

No real chest block / world grief — displays + Interaction only, like Hollow Reliquary.

---

## Locks

- Do **not** change Blossom Blade / Gravwell Cleaver combat
- Do **not** redesign Seraphine’s fight (points 1–3 are frozen unless a loot prop needs a tiny death-hook)
- Boosters / ranks / anvil sockets untouched
- No live deploy from Cloud

---

## Quality bar

Same shelf as Seraphine’s fight and Hollow Reliquary: authored BlockDisplays, timing, sound, punchline — not a vanilla chest with particles.

---

## Deliverable

Push to **`claude/boss-hanging-saint`** (or a clear follow-up branch from it). Then STOP. Brief summary of item ids + chest class + hooks touched — nothing more.
