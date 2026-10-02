# Aetherion Items — Opus context pack (spectacle abilities)

Compact technical facts for designing **high-spectacle showcase weapons/items** inside `AetherionItems`. Inspected from live sources. Not an architecture thesis.

---

## What this plugin is

Paper plugin owning custom gear, combat stats, and **scripted ability cinematics**. Showcase items are **not** YAML skill configs — they are Java timeline classes driven by right-click, spawning `BlockDisplay` / `ItemDisplay` choreography for several seconds, then cleaning up.

| Piece | Role |
|--------|------|
| `CustomItem.create…()` | Builds the `ItemStack` (material, display name, lore, stats, PDC id) |
| `ItemProfile` enum | Registers the item id + which stats it carries |
| `TestGear` / Dev Menu | Sandbox give-list (`TestGear.all()`) so Robbi can pull them in-game |
| `TestGearListener` | Right-click / sneak wiring, cooldowns, busy-guards |
| `TestPrototypeAbilities` | Thin cast glue (`castX` → spectacle class) |
| `*Nova` / `*Tempest` / `*Rite` / `*Rift` | The actual multi-tick spectacle |

Live production uniques (Ashen Katana / Gravwell Cleaver / etc.) use their own listeners. **Showcase / Test Arena rods** intentionally live on the TestGear path so they stay sandboxed until Robbi promotes them.

---

## Quality bar (study these, do not clone)

| Class | Lines (approx) | Why it matters |
|--------|----------------|----------------|
| `CataclysmRodNova` | ~1300 | Current peak item cinematic: bolt → Absolute Nova → silence → delayed seismic floor punchline. Authored shells, seal tiles, pillars, ejecta. Timing is the craft. |
| `CycloneRodTempest` | ~730 | Sustained spatial composition (funnel, feeders, eye, fling). Clean display budget. |
| `JudgmentVerdict` / `MeteorMaceCrash` / `ResonanceScytheWave` | mid | Other authored timelines on the same stack |
| `RuneSigilRite` | ~770 | Prototype only — Robbi finds it **not good enough**. Treat as a floor to crush, not a template. |
| `WorldSplitterRift` | ~825 | Prototype only — same. Flagship bar is higher than this. |

**Boss-side craft reference (read for ambition only, do not depend on BossEngine):** Seraphine / Hanging Saint — custom skeleton of BlockDisplays, stop-motion language, theatre set. Item work should feel like *that* ambition compressed into a 4–12s player ability — “this shouldn’t still be Minecraft.”

`rune_sigil` / `world_splitter` may be replaced or abandoned. Do not ship a mild remix of either.

---

## Proven delivery pattern (copy this shape)

1. **Spectacle class** under `de.aetherion.items.listener`  
   - `static void cast(JavaPlugin, Player, double damage, Runnable done)`  
   - private timeline (`BukkitRunnable`, tick 0…TOTAL)  
   - spawn `BlockDisplay`s with `Transformation` + interpolation  
   - `setPersistent(false)` on every display  
   - `static void shutdown()` removes all live displays (called from `AetherionItems.onDisable`)  
   - global `MAX_LIVE` budget so stacked casts don’t melt the server

2. **Item factory** in `CustomItem`  
   - `setItemId(meta, "your_id")`  
   - `stampTestGear(meta, "your_id")`  
   - Legendary-looking lore; stats are placeholders (balance later)

3. **`ItemProfile` enum entry** with the same id string

4. **`TestGear.all()`** — add `custom.createYourItem()` so Dev Menu exposes it

5. **`TestPrototypeAbilities`** — busy set + `castYourItem(…)` calling the spectacle

6. **`TestGearListener`** — right-click branch on item id, cooldown map, `AbilityCooldownHud.arm(…)`

7. **`AetherionItems.onDisable`** — call `YourSpectacle.shutdown()`

Wire only what’s needed for the ability to cast from Test Gear. Do **not** invent economy recipes, loot tables, dungeon drops, or live progression.

---

## Hits / players / safety

- Scripted combat damage goes through **`ScriptedHits.run(() -> living.damage(…))`** so other listeners know it’s authored (same idea as BossEngine `BossHits`, different facade).
- Prefer hitting **hostile mobs / non-allied players**; never grief the caster’s allies unless the fantasy is a clear “zone buff” (then buff only).  
- **Never edit real world blocks** for spectacle. Use displays + `FakeDestruction`-style debris / falling visuals. Cataclysm and World Splitter both treat the world as sacred.
- Displays: `Display.Brightness`, JOML `Quaternionf` / `Vector3f`, `setInterpolationDuration` / `setInterpolationDelay`, teleport duration for smooth motion.
- Particles support timing; **they are not the show**. Geometry + timing + sound first (same rule as the boss brief).

---

## Cooldowns / casting

- Test Arena often runs CDs soft; still implement a busy-guard so double-click doesn’t stack two timelines on one player.
- Long cinematics (Cataclysm ~several seconds of phases) should feel authored: windup → peak → hush → punchline — not one big boom.
- Keep damage numbers absurd if needed; **visual impact >> balance**.

---

## Hard locks (do not touch)

| Locked | Why |
|--------|-----|
| **Blossom Blade** (`ashen_katana` / `AshenKatanaListener`) | Signature weapon — abilities/VFX/feel frozen |
| **Gravwell Cleaver** (`gravwell_cleaver` / `GravwellCleaverListener`) | Same |
| Booster socket / anvil GUI / booster stack rules | LOCKED server systems |
| Rank / Admin / OP identity | Unrelated |

Do not refactor combat core, CustomItem wholesale, or Dev Menu architecture. Add alongside.

---

## Limits

- Tick budget: tens–low hundreds of displays per cast is the proven range; Cap with `MAX_LIVE`.
- Always remove props on finish / cancel / plugin disable.
- No ModelEngine dependency required — displays are the craft language here.
- No BossEngine compile dependency for item spectacles.
- Handoff = code on a branch. No live deploy/restart unless asked.

---

## Minimal mental model

```
Right-click TestGear item
  → TestGearListener (id + CD + busy)
  → TestPrototypeAbilities.castX
  → YourSpectacle.cast(plugin, player, damage, done)
       tick timeline: spawn/animate BlockDisplays + sound + ScriptedHits
       finish: remove displays, run done
```
