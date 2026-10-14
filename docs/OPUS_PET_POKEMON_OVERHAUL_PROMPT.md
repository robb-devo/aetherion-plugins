# Aethermobs — Pet Pokémon-light OVERHAUL (Opus Extra / Ultra — AUSRASTEN)

Fresh chat. Owner: **Robb**. Call him Robb.

Workspace:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Cloud write blocked under `.claude`? Refresh zips (include **Aethermobs**):

```powershell
cd C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c
Compress-Archive -Force -Path Aethermobs\src,AetherionItems\src\main\resources,AetherionCore\src\main\java\de\aetherion\core\api\PetAccess.java,AetherionCore\src\main\java\de\aetherion\core\AetherKeys.java -DestinationPath _opus_pets_src.zip
```

Deliver: **`aethermobs-pet-bodies.patch`** (git apply clean). No deploy. No `_ship/`.

Hygiene: `docs/OPUS_PROMPT_HYGIENE.md`. TalkUx / ranks / boosters / signature weapons LOCKED. Core stays thin (`AetherKeys.PET_ENTITY`, `PetAccess`).

---

```text
# AETHERMOBS — POKÉMON-LIGHT PET OVERHAUL (GO FERAL)

Fresh Opus Extra/Max/Ultra-code. Owner: **Robb**.

## Mission

Kill Hypixel floating-skull pets. Ship **living companions**:
- Real small bodies (readable silhouette at 8–12 blocks)
- Locomotion that matches the creature (walk, trot, hop, swim, hover, glide, soar…)
- Catch spheres that feel like **real balls** — throw, arc, impact, settle, shake, click/bust — **visually spectacular**, not a quiet snowball
- Idle life + rarity presence (soft FX, not particle cancer)

Internalize the **entire** Aethermobs pet stack first (manageable size — not a mammut). Then build a **skeleton library** and migrate the **whole catalog**. One demo pet = fail.

Robb: **dämlich ausrasten.** Result > limit. Multiple sessions OK with crisp handoffs — but aim to land a complete v1 body system + catalog map in this run.

## Perf law (non-negotiable)

- Common/uncommon pets: lean (few Display parts). Rare/epic: a bit richer. **Dragons: allowed to be BRUTAL** (wings, idle, trails) — still not 200 blocks, still sleep when far, still LOD.
- Distance sleep + simplify. Tick budgets. Mentally stress: many players × 1 pet.
- No TPS melt. If a dragon looks insane up close and cheap far away, you win.

## Skeletons — invent many (not “only 3”)

Create as many locomotion/body skeletons as the roster needs. Examples (expand freely):

| Skeleton ideas | Behaviour |
|----------------|-----------|
| GROUND_QUAD / GROUND_BIPED / CRITTER_SCUTTLE | Walk/trot/hop on surface (`PetWalkSurface` lineage) |
| SLITHER / CRAWL | Low profile ground |
| FLY_HOVER / FLY_FLAP / FLY_GLIDE / FLY_SOAR | Altitude bands, bank on turn |
| AQUA_SWIM / AQUA_SURFACE | Water pets |
| AMBER_ORB / SPIRIT_WISP | Magical floaters with body, not skull |
| DRAGON_WYRM / DRAGON_WESTERN | Heavy multipart — **spectacle OK** |

Every pet id → one skeleton. Missing custom models → procedural body from habitat/rarity/palette until CMD art exists. Heads may remain **menu icons only**.

## Catch balls — make it sexy

Upgrade the whole catch presentation (keep odds/flee/timing **rules** unless broken):
- Sphere item + in-flight projectile read as a **ball** (custom model / ItemDisplay rider)
- Throw arc, bounce/settle, shake beats with weight and juice (sound + motion + light FX)
- Success click / fail crack should feel premium
- Rarity tint on the ball is fine; don’t imply P2W gear

## Dragons (explicit)

`ice_dragon`, `fire_dragon`, `water_dragon`, `nature_dragon`, `mining_dragon`/`poison_dragon`, `forest_dragon`, `lightning_dragon`, `dungeon_dragon`, …  
These are mega-rare / strongest. **Ausrasten:** wings, jaw, idle breath, rarity aura — the pet you show in a trailer. Still budgeted (LOD/sleep). One beautiful dragon nearby > twenty melting the hub.

## Own these paths

`Aethermobs/.../pet/*` especially `PetEntity`, `PetHead`, `PetDefinition`, `PetRegistry`, `ActivePetManager`, `PetWalkSurface`, `SurfacePetSpawner`, `PetCatchListener`, `CatchSphere*`, skills only if presentation hooks need them.  
Items RP for sphere/pet CMD textures if needed.  
Do not rewrite habitat economy, combat pet DPS formulas, or collection menus unless required for the new look.

## Deliverables

1) Skeleton system + catalog migration off skull-primary
2) Locomotion variety live in wild + equipped
3) Catch-ball visual/feel overhaul
4) Dragon spectacle pass
5) Soft ambient FX per rarity/skeleton (budgeted)
6) EN+DE only if you touch player-facing strings
7) End note: smoke cmds, pack files, part caps, residual nits. **No deploy.**

## OUT

Harbour/Origin/Quests. ModelEngine unless already on server (prefer Display + RP). Catch-rate economy redesign. Core game loop.

## Done when

- Ground + flyer + critter + dragon examples all read as **creatures**
- Catching looks like Pokémon-adjacent juice, not Hypixel click
- Dragons look sick without killing TPS
- Honest self-score: “Pokémon-light v1 for the roster” — not a tech demo

Go. Feral. Budgeted. Legendary.
```

---

## Paste starter (full send)

```text
Read docs/OPUS_PET_POKEMON_OVERHAUL_PROMPT.md + docs/OPUS_PROMPT_HYGIENE.md.
Workspace: C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c
Focus: Aethermobs only. Refresh _opus_pets_src.zip if cloud can’t deep-read.

POKÉMON-LIGHT PET OVERHAUL — ausrasten.
Kill floating skulls. Skeleton library (MANY kinds — not only 3): walk/hop/slither/hover/flap/glide/soar/swim/spirit/dragon…
Migrate whole catalog. Living motion + soft FX. Catch spheres = real juicy throwable balls (visual ritual upgrade; keep catch odds/rules).
Dragons: BRUTAL spectacle (rare/strong) with LOD/sleep so TPS lives.
Perf hard for normals. Deliver aethermobs-pet-bodies.patch. No deploy. Go.
```
