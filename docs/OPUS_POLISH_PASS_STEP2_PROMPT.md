# Opus prompt — Polish Pass STEP 3-of-3 (Pets / Habitats)

**Local agent.** Run **after rituals** (or if Robbi explicitly prioritizes pets next).  
Impact order is Textures → Rituals → **Pets**.

Copy everything below the line into the Opus chat.

---

You are a **local** Claude Opus agent executing **Aetherion Polish Pass — Pets + Habitats only**.

## First read (in order)

1. `docs/OPUS_POLISH_PASS_AUDIT.md` — sections A2, B, C (pets), D, F (pets)
2. Then open **only**:
   - `Aethermobs/.../pet/PetHabitat.java`
   - `Aethermobs/.../pet/PetHabitatZones.java`
   - `Aethermobs/.../pet/PetSpawnManager.java` (read — prefer not edit)
   - `Aethermobs/.../pet/PetWalkSurface.java` (read — prefer not edit)
   - `Aethermobs/.../pet/PetEntity.java` (read — prefer not edit ambient feel)
   - `AetherionItems/.../world/PetHabitatZoneService.java`
   - `AetherionItems/.../world/CryptDiscoverListener.java` (**pattern to mirror**)
   - `AetherionHub/.../listener/SpawnDiscoverListener.java` (secondary pattern)
   - `Aethermobs/.../pet/PetSpawnZones.java` (hub quiet bubbles — KEEP)

Do **not** rewrite `PetFactory` / registration / weights.

## Preflight (Robbi / ops)

Before changing auto-detect thresholds: audit live `plugins/AetherionItems/pet-habitats.yml` coverage on Origin. If paint is thin, discovery still helps; do not retune thresholds blindly.

## You are NOT here to

- Retune spawn weights, caps, flee/bob, or catch timing “for fun”
- Expand paint kinds to FARM / VILLAGE / SHORE (stay auto)
- Merge forage habitats with pet habitats
- Add new pets or combat skills
- Touch showcase bosses, textures, or Borderlands/Colosseum rites
- Touch locked systems; wipe live `pet-habitats.yml`

## You ARE here to

Make habitats feel like **actual places** with small discovery polish. Spawn/terrain already good — KEEP.

### Scope

**P0 — Essential**
1. First-enter dominant habitat discovery (title/actionbar), persist UUID set, edge debounce — prefer new small listener in **Aethermobs**, mirror `CryptDiscoverListener`. Resolve via `PetHabitat.dominantAt` / painted+auto. Never spam hub quiet bubbles or dungeon hub.

**P1 — Recommended**
2. Optional one-shot soft enter FX per biotope (few particles + short sound). Never per-tick carpets.

**P2 — Optional**
3. Docs/messaging clarify Eldervale naming collisions (forage TAB vs pet `ELDERVALE` paint) if concrete mismatch.
4. Density review only with playtest evidence.

## Branch

`claude/polish-step3-pets`. Commit. Push if possible. **STOP.**

## Deliverable

- Discovery polish shipped
- Report: files, hysteresis approach, what was left alone, any ops notes on live paint
