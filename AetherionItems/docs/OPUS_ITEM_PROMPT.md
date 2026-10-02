# Opus prompt — Aetherion flagship spectacle items (×2)

Copy everything below the line into a new Claude Opus session.

---

You are designing and creating **two extremely high-quality Minecraft player items** for **Aetherion**.

These are not “nice Test Arena toys.” They must hit the same emotional / craft bar as **Seraphine, the Hanging Saint** and the peak item cinematic **Cataclysm Rod** — work so dense and authored that experienced Minecraft players briefly forget they’re in Minecraft.

## First read

Read and internalize:

`docs/OPUS_ITEM_CONTEXT.md`

That file is the technical environment inside **AetherionItems**. Fit the existing TestGear / spectacle-class pattern. Do **not** scan the whole codebase. Do **not** redesign the plugin.

Then focus almost exclusively on the **two ability spectacles**.

## Creative goal

Create **two** player-cast spectacles that push Minecraft as far as is technically reasonable.

The bar is **not** a Hypixel ability with big particles. The bar is:

> When this fires, people lean back and think *what the fuck was that — that can’t still be Minecraft.*

Same ambition as the Hanging Saint boss fight (authored BlockDisplay bodies, timing, theatre, silence, punchlines) — compressed into a self-contained **4–12 second** player ability.

Prefer authored animation and custom geometry over particle spam.

Prioritize:

1. Animation / transformation timing  
2. Spatial composition (floor language, verticality, architecture made of displays)  
3. Anticipation → peak → hush / aftermath  
4. Sound layered to beats  
5. Impact feedback  
6. Particles only as accents  

Study for **craft only** (do not clone fantasy or beat structure):

- Boss: Hanging Saint / Hollow Sun directors — density, authored motion, set pieces  
- Item: `CataclysmRodNova` — multi-act punchline, silence, delayed floor catastrophe  

## Existing items — replace the *fantasy*, don’t remix the files

There are already sandbox prototypes in this space. Robbi has cast them and they are **not good enough**:

| Id | Class | Why it exists | Your job |
|----|--------|---------------|----------|
| `rune_sigil` | `RuneSigilRite` | Azure circle → pillar → heal | **Outclass this entirely.** New identity, new language, much denser. |
| `world_splitter` | `WorldSplitterRift` | Reality peel / void seam | **Outclass this entirely.** New identity. Flagship “holy shit” moment. |

You may:

- invent **brand-new item ids** and leave the old prototypes untouched, **or**
- **replace** those ids/classes with your superior implementations if that keeps wiring simpler  

Either way: the *result* must feel like a different tier of game. Do not ship a prettier azure seal or a slightly bigger rift.

## Item 1 — Living sanctum / circle (seed)

Robbi’s seed — interpret and escalate hard:

> Something you cast once. Around you, a **runebound floor / sanctum / living seal** appears — insanely animated. Allies inside might heal faster and hit harder, **and/or** it culminates in an absolute skyward judgment, **and/or** something stranger and more theatrical. It must feel architectural and alive, not a flat particle ring.

Full creative freedom on identity, materials, color language, ally buffs, enemy punishment, beam / collapse / dawn / whatever. Just make the circle **boss-grade**.

## Item 2 — Server flagship (complete freedom)

The **poster ability** of the server.

When someone casts it, bystanders should stop moving and watch.

Complete freedom: explosion, sky answer, ground answer, time-stop, silence, dual punchline, geometry that shouldn’t exist in Minecraft — whatever. Weapon or relic. Must still be a clean cast (start → spectacle → end → cleanup), not an endless boss loop.

No balance. No “fair PvP.” Visual / experiential impact only. Over-the-top is mandatory.

## Hard scope

Focus **ONLY** on these two items and their presentation.

Do **NOT** spend effort on:

- balance / economy / recipes / loot  
- permissions / deploy / restart  
- writeups beyond a tiny completion summary  
- locked signature weapons (**Blossom Blade / ashen_katana**, **Gravwell Cleaver**)  
- boosters / anvil / ranks  
- refactoring unrelated systems  

## Implementation

Real AetherionItems code in the proven showcase pattern:

- Spectacle class(es) under `de.aetherion.items.listener` with `cast(...)` + tick timeline + `shutdown()`  
- `CustomItem.create…()` + `ItemProfile` + `TestGear.all()`  
- Busy-guard + cast in `TestPrototypeAbilities`  
- Right-click + CD in `TestGearListener`  
- `shutdown()` from `AetherionItems.onDisable`

Use:

- `BlockDisplay` / `ItemDisplay` + transformations + interpolation  
- `ScriptedHits.run` for authored damage  
- `setPersistent(false)` + explicit cleanup  
- soft `MAX_LIVE` display budget  
- **no real world block destruction**

Spend almost all effort on design + presentation. Wire minimally so Test Gear can cast them.

## Most important

Do not play it safe.

Do not make “good Test Arena rods.”

Do not remix Cataclysm’s nova or the old rune/rift prototypes.

Try to create something that makes an experienced Minecraft player genuinely think:

**“This shouldn’t be possible in Minecraft.”**

Push animation, geometry, timing and sound as far as the engine allows while keeping each cast readable and self-contained.

## Deliverable / handoff

When both items are done, leave a **clean code deliverable** another engineer can pick up 1:1:

- Clear spectacle classes + minimal wiring  
- If GitHub access exists: commit/push to a branch  
- Do **not** deploy or restart live servers  

Then **STOP**.

Tell me they’re complete, where the files/branch are, and a **brief** summary of both identities and signature beats — nothing more.
