# Opus prompt — Polish Pass STEP 2 (Pets / Habitats)

Copy everything below the line into the Cloud workspace chat **only after STEP 1 is merged or explicitly deferred by Robbi**.

---

You are executing **Aetherion Polish Pass — STEP 2 only: Pets + Habitats**.

## First read (in order)

1. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A2, B, C (STEP 2), D, F (STEP 2)
2. Then open **only**:
   - `Aethermobs/.../pet/PetHabitat.java`
   - `Aethermobs/.../pet/PetHabitatZones.java`
   - `Aethermobs/.../pet/PetSpawnManager.java` (read — prefer not edit)
   - `Aethermobs/.../pet/PetWalkSurface.java` (read — prefer not edit)
   - `AetherionItems/.../world/PetHabitatZoneService.java`
   - `AetherionItems/.../world/CryptDiscoverListener.java` (**pattern to mirror**)
   - `AetherionHub/.../listener/SpawnDiscoverListener.java` (secondary pattern)
   - `Aethermobs/.../pet/PetSpawnZones.java` (hub quiet bubbles — keep)

Do **not** ingest the entire monorepo. Do **not** rewrite pet registration (`PetFactory`).

## You are NOT here to

- Retune spawn weights, global caps, flee/bob feel, or catch timing “for fun”
- Expand paint kinds to FARM / VILLAGE / SHORE (product rule: stay auto)
- Merge forage habitats with pet habitats into one system
- Add new pets or combat skills
- Touch showcase bosses, textures STEP 1 scope, or Borderlands rites
- Touch locked systems (boosters, ranks, signature weapons, vials stack, anvil)
- Wipe or rewrite live `pet-habitats.yml` blindly (file is runtime-only)

## You ARE here to

Make habitats feel like **actual places** — not invisible spawn modifiers — with *small, deliberate* discovery polish.

### Scope

**P0 — Essential**
1. **Player first-enter habitat discovery** — short title and/or actionbar when entering a dominant pet biotope for the first time. Mirror `CryptDiscoverListener` persistence + one-shot feel. Debounce borders so walking the edge does not spam.
2. If live paint file content is unavailable in this environment, implement discovery against `PetHabitat.dominantAt` / painted+auto resolution already used by spawn, and document that Robbi should verify against production `pet-habitats.yml`.

**P1 — Recommended**
3. Optional **one-shot** soft enter FX per biotope kind (few particles + short sound) — never per-tick, never dense carpets.
4. Keep staff marker workflow intact (`MarkerVisibilityListener` / Dev Menu sticks).

**P2 — Optional / evidence-gated**
5. Clarify Eldervale forage TAB vs pet `ELDERVALE` only if you find a concrete mismatch in code/config you can fix without redesign.
6. Do **not** change density unless comments/playtest notes in-repo prove crowding.

### Quality bar

Reference craft (read-only scale): Crypt discover toast pacing, Hub spawn discover — not Seraphine spectacle.

Habitats should feel organic; spawn/terrain logic is **already good** — KEEP it.

### Hard technical rules

- Prefer new small listener in **Aethermobs** (pet UX ownership) over bloating Items
- Persist discovered UUIDs (yaml or existing storage style consistent with Crypt)
- Never remove hub quiet 10-block bubbles
- Never change `PetWalkSurface` canopy rules or `presentAt` thresholds without measured cause
- BlockDisplay / particles: accents only; cleanup any spawned displays

## Branch

Work on **`claude/polish-step2-habitats`** from `main` (or from STEP 1 branch if Robbi says so).  
Commit + push. Then **STOP**.

## Deliverable

- Habitat discovery UX shipped
- Commit + push
- Brief report: player-facing behavior, files, what you left alone, any live `pet-habitats.yml` ops note for Robbi

Do not start STEP 3.
