# Opus — Island highlight: final knackpunkt pass (GO HARD)

**New Opus chat · Extra · no artificial token budget.**  
If quota empties: wait a few minutes → continue. Do not shrink the work.

**Hygiene:** Edit source directly. Code + templates/recipes only.  
**No** `_ship/`, Manifest, Apply-Script, README novel, TEST_SHEET.  
**No deploy** (Robbi/Cursor deploys). Short summary when done.

Robbi playtested. Core island/quarry highlight is already **excellent** (blueprint hologram, land buy, quarry housing, scale, conveyors-as-sim). **Do not gut or “rewrite for fun” what works.**

This pass = the **last knackpunkte**. On these, **ausrasten** — polish hard, make it feel premium Aetherion. Everywhere else: hands off.

---

## Scope (only these)

### 1) True void around islands (must-fix)

Personal/guild islands must live in **real void**. Right now a few empty chunks, then normal overworld terrain continues. Wrong.

- Every player expands their floating isle in nothing — not a hole punched in a real world.
- Fix generator registration for **existing** worlds (load path often skips re-applying `VoidChunkGenerator`).
- Persist void gen for `aether_islands` / `aether_guilds`.
- Clean already-generated junk terrain outside owned parcels / safe plot radius without deleting islands or bought land.
- Same check for guild world.

### 2) First-time interactive guide (must — FancyNPC + bubbles)

Chat tips are not enough. Add a **FancyNPC** on personal starters (spawn / works yard) that:

- Interactive walkthrough on first claim / first talk
- Guides: Storage Hut (or pad) → places a **free Cobble Quarry** with the player → short belt into storage/depot → done
- After that, player upgrades/expands alone
- Skippable if they walk away; never soft-lock building

**Bubbles = required standard.** Use the existing in-world TalkUx speech bubble (typewriter over NPC, reply chips, per-listener). Robbi wants bubbles as the default for NPC talk — especially this tutorial NPC. **Do not** make chat-only / chest-only the main path.

**TalkUx shell LOCKED** — wire content through the existing bubble UX. Do not rewrite, remove, or “simplify away” TalkUx.

This guide is **high priority**. Make it feel clear and Aetherion-quality.

Guild harbour can stay without this NPC this pass (optional board one-liner OK).

### 2b) Island bossbar (optional — if it earns its keep)

On **personal or guild island** worlds: suppress active **quest / hint bossbars** — the island owns that top bar.

If useful, show a calm **island/guild bossbar** instead (short status: tier, production, “talk to guide”, next tip). Not spam, not a second quest tracker. If a replacement bar doesn’t help: only clear quest/hint bars while on the isle.

### 3) Structure upgrades (1–2 tiers) using existing economy

Storage Hut and other placeables should be upgradeable **once or twice** (not a 10-tier spreadsheet).

- Look at **existing server items/resources** already in Aetherion (compressed/compacted chain, quarry cores, coins, materials already used by island/guild costs) — **reuse**, don’t invent currencies.
- Recipes / costs in the same spirit as current Stage-1 / multi-build costs.
- Upgrades should feel physical where it matters (same building grows / interior capacity / machine rate) — not “number goes up in a void menu.”
- Mill / Forge / Depot / Hut are the obvious candidates; Workshop only if it earns an upgrade.

### 4) Island UI overhaul (clean, one hub)

Right now `/island` and `/island build` feel like **two stiff, different systems**. Overhaul so:

- `/is` / `/island` is the **one home** — navigate everything from there (build, land, production, expand, guide links, etc.)
- Submenus clean and readable — not every double-chest slot filled, but **high-end presentation**
- Less confusion, less stiffness, clear labels, sensible icons, obvious next actions after first claim
- Keep power-user paths if useful (`/island build` can remain as shortcut into the same UI)

Make the menu layer match how good the world systems already feel.

### 5) Conveyors look Satisfactory (presentation)

Sim/cargo already slap. Visuals are still boring iron plates.

- Belts must **read as conveyors** (frame, rollers, direction, subtle motion) while staying cheap + culled.
- No hopper megafactory, no item-entity rivers, no TPS death.
- Keep place/remove flow and abstract transfer.

---

## Explicitly leave alone

- Land parcel buy / raise ground (Robbi likes it — keep)
- Blueprint hologram + build-tool-in-hand model (unless UI wiring needs a tiny hook)
- Quarry housing look / scale from polish 1 (5×5 hut, 3×3 machines, ~41 starters) unless an upgrade pass needs a higher-tier variant
- Guild projects (unless a one-line board text for the guide)
- Balancing obsession — pick simple costs from existing items and ship
- Ranks, weapons, boosters, TalkUx shell, skill isles, Hub Origin rewrite, unrelated plugins

---

## Ambition

On the knackpunkte above: **go hard**. Premium feel. Bubble guide that actually teaches. Void that feels like SkyBlock void. Menus you’d show off. Belts you’d screenshot. Upgrades that make Storage/machines grow with the island. Bossbar that serves the isle, not quest spam.

Not: rewrite the whole Guilds plugin. Not: new skill trees. Not: feature bloat outside this list.

---

## Done when

1. Flying off a personal island = void, not overworld after a few chunks.  
2. FancyNPC first-run guide works **with TalkUx bubbles** (free cobble quarry + short belt teach).  
3. Hut / key machines have 1–2 meaningful upgrades using existing Aetherion resources.  
4. `/island` UI is one clean navigable hub (build nested inside).  
5. Belts look like conveyors.  
6. On island/guild worlds: no quest/hint bossbar stealing the top; optional calm island bossbar if useful.  
7. Compile Guilds (± Items if new recipes) if possible without m2-begging; else skip compile, one-line note. No deploy.

## Starter line

> Read **only** `docs/OPUS_ISLAND_POLISH2_PROMPT.md`. Final knackpunkt pass — void fix, FancyNPC guide **with TalkUx bubbles**, 1–2 structure upgrades (existing items), `/island` UI hub overhaul, Satisfactory conveyors, island bossbar (suppress quest/hint bars on isle; optional island status bar). Ausrasten on these only. Don’t gut what already slaps. Edit source directly. No ship/README/manifest. No deploy.
