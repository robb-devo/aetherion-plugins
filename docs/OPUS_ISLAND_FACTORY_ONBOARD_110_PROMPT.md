# Opus / Claude High — Island Factory Onboarding → Compacted Cobble (110%)

**Paste everything below the line into a new Claude chat.**  
Mode: **High** (~40–50% session). One product. No Extreme thrash.

Workspace:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Follow `docs/OPUS_PROMPT_HYGIENE.md`.  
**No deploy. No restart. No commit/push.** Edit source in-tree. Deliver patch(es) if the bridge cannot write under `.claude\`.

---

```text
# AETHERION — Island Factory: onboarding that CLICKS (90% → 110%)

You are elevating an **already strong** personal-island factory — not reinventing Satisfactory.

Robb’s verdict: the groundwork works, quarries slap, belts/sim are cool, Hollis already teaches hut → quarry → belt. What still fails: it does not feel **intertwined with the server**. New players do not leave thinking “oh — THAT’S how the factory works, and I want more.” Take the existing loop from “pretty good side feature” to **flagship teachable addiction**.

Budget: **High**, ~half a session. Finish the onboarding product. Optional schematic→prop side only if the main flow is done and tokens remain.

---

## North star (read twice)

1. **Onboarding must be brutal-clean and brutal-logical.** One path. One next step. Generous materials/unlocks during the teach. Zero homework menus.
2. End state of the guided arc: the player has run a line that produces **Compacted Cobblestone** (Quarry → … → Mill → Forge → storage) and **gets** why Hub unlocks / Build Tool / belts exist.
3. Do **not** rewrite Guilds from scratch. Do **not** invent a new factory plugin. Extend `IslandGuide` / `FactoryGoals` / Hub lectern / existing props.

---

## What already exists (use it)

| Piece | Where |
|---|---|
| Hollis first tour (hut → cobble quarry → belt) + TalkUx bubbles | `AetherionGuilds/.../island/IslandGuide.java` |
| Soft ladder after first chain (mill, forge, compacted, …) | `.../island/FactoryGoals.java` — already has `mill_line`, `forge_line` (“Compressed in, Compacted out”) |
| Funnel milestones | `.../island/FactoryProgress.java` |
| Hub / workshop lectern unlocks | `StructureService`, Hub menus, `HubRitual` |
| Build tool, belts, machines, quarry | `PlacementService`, `LogisticsService`, `QuarryMenu`, `PropBodies`, `BeltVisuals` |
| Templates (schems) | `TemplateLibrary` — `sm_workshop`, storage, mill, forge, quarry housing, … |

Hollis currently stops after the first belt. **That is the gap.** Extend the guided fantasy through Mill → Forge → compacted, without trapping builders.

---

## Product (mandatory) — Onboarding to Compacted Cobble

### A) Extend Hollis / guide after the first chain

After hut + quarry + belt (existing DONE):

- Continue (or seamlessly hand into) a **second act**: Hub founded → unlock Mill → put Mill in line → unlock Forge → put Forge in line → see Compacted Cobble land in storage.
- Generous: free/cheap first Mill + Forge unlocks **during the teach**, or pre-grant mats so the player is never stuck farming off-isle mid-tutorial. Robb wants generous.
- Every step still **detectable from island state** (like today) — doable in any order, skippable, resumable from `/island` / “What’s next?”.
- TalkUx **bubbles required** for guide talk. Shell is **LOCKED** — wire lines + chips only. No chat-only primary path. No TalkUx rewrite.
- Keep Hollis voice: dry harbour hand, not lecture bot.

### B) Make “what’s next?” impossible to miss

- Island bar / `/island` / Hollis “What’s next?” / Production tile must agree on the **same** next goal (`FactoryGoals.next`).
- When a goal completes: short celebration beat (sound/particles/title OK — restrained) + clear new next line.
- Empty / confused states die: if the player opens Hub mid-tour, the lectern highlights only what the tour needs.

### C) Logic & UX cleanliness (110% feel)

- One obvious Build Tool path for place/rotate/remove during the tour.
- No nested workshop circus to finish the teach.
- Labels in EN (DE only if island already has lang hooks).
- If `/island` still feels like two systems, tidy the **tour-relevant** screens only — do not boil the ocean on every submenu.

### D) Done when (spot-check)

1. Fresh personal island → talk Hollis → complete extended guide without leaving the isle for mats.
2. Storage shows **Compacted Cobble** (or clearly “compacted cobble” item your economy already uses).
3. Player can explain in one sentence: quarry makes raw → mill compresses → forge compacts → Hub unlocks the next machine.
4. Skip mid-tour → `/island` or Hollis resumes without soft-lock.
5. TalkUx bubbles still work; no TalkUx architecture edits.

---

## Side (only after Product is done) — fewer fat schematics, more identity props

Robb: a giant **Workshop / work-table schematic** where you only need one interaction block is nonsense. Prefer **custom prop bodies** (`PropBodies` / displays) with clear identity and light motion — not “god knows what” spam props.

Priority:
1. **Workshop / Hub desk** — shrink or replace fat schem with a readable prop + lectern/interact; keep unlock UX.
2. Mills / Forges / quarry housing — if still schem-first and the footprint is mostly dead air, lean prop; keep gameplay boxes/hit volumes correct.
3. **Storage Hut may stay schem-ish** — multiple belt approach points make a real footprint useful. Don’t rip storage for ideology.

Rules:
- No TPS death (no item-entity rivers, no hopper megafactory).
- Don’t invent a new art pipeline; use existing ItemDisplay / PropBodies patterns.
- Existing islands must not brick — migrate or dual-path carefully.
- This side is **optional polish**. If tokens are tight, ship Product only and list schematic leftovers.

---

## Explicitly leave alone

- Full Satisfactory redesign / one-building-tool philosophy rewrite (already partly there — don’t churn)
- Land buy / raise ground (liked)
- Quarry gameplay math unless a tutorial grant needs a one-line hook
- Guild harbour projects
- Skill isles, Harbour quest tutorial rewrite, Main island map, Exile map
- LOCKED: TalkUx shell, boosters×64, Anvil sockets, ranks/`player-ranks.yml`, Blossom Blade / Gravwell feel, ShutdownCountdown, Borderlands vials
- Texture-pack Manager chrome, Perf megapass, Farm animal dens (other agents)

---

## Process

1. Read this file + skim `IslandGuide`, `FactoryGoals`, `FactoryProgress`, Hub lectern open path, `PropBodies` (for the side).
2. Implement Product A–C end-to-end.
3. If budget left: Workshop schem → prop identity (side).
4. No Maven gate if Paper blocked — syntax-check OK; Robb builds.
5. End note: files touched, how to spot-check the compacted-cobble tour, residuals. **No deploy.**

## Ambition line

Not “add more menus.” Make the first 15 minutes on a personal island feel like **this is a pillar of Aetherion** — clean, logical, generous, addictive. 90 → 110.
```
