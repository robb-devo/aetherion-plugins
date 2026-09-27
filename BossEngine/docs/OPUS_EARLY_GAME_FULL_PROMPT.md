# Opus prompt — Early Game Full Pass (through mid-game opening)

Copy everything below the line into a Cloud Opus chat on **`aetherion-plugins`**.  
Prefer branch **`claude/early-game-harbour`** as base (Harbour Hour already there / [PR #65](https://github.com/robb-devo/aetherion-plugins/pull/65)).

Also read:
1. `BossEngine/docs/OPUS_EARLY_GAME_FULL_AUDIT.md`
2. `BossEngine/docs/OPUS_EARLY_GAME_FULL_HANDOFF.md`

---

You are improving **Aetherion’s Early Game as a complete player experience** — not just the pier tutorial.

## Mission

After 1–2 hours a new player should think: **“Okay, there’s really something here.”**

Keep the game’s identity, quest IDs, progression spine, and systems. Raise **presentation, feedback, immersion, orientation, UX, world life, and small wow moments**. Connect systems that already exist so the journey feels designed past Miss Ledger’s stamp into the soft mid-game opening.

Harbour Hour (arrival, kit ceremony, pier ambience, trail) is **already done** on this branch — **do not redo it**. Your job starts from “orientation continues / after stamp / first real systems & combat / early→mid.”

## You are NOT here to

- Rebuild the server or invent Mid/Late Game content wholesale  
- Reshuffle `tutorialDone` / core quest graph for novelty  
- Redesign combat formulas or add legendary weapons  
- Touch LOCKED systems (signature weapons, boosters/sockets/anvil, ranks/TAB, shutdown, Seraphine fight)  
- Grow AetherionCore into a game loop  
- Copy Seraphine/Hollow scale into the harbour  
- Feature-creep (100 tiny tickets, new frameworks, parallel UIs)  
- Deploy production (push branch; Robbi deploys)

## You ARE here to

Prioritize by **player impact × (clarity + immersion) / (risk × effort)**.

Implement a coherent bundle from the audit’s **MUST** + strongest **HIGH-VALUE** items. Skip OPTIONAL unless leftover budget and perfect fit. Explicitly leave strong systems alone.

### Priority targets (solve the problems; you choose the craft)

1. **Post-graduation cliff** — After `tutorialDone`, players must not feel dumped. Soft spine in `/guide` + Ledger graduation handoff + clear next verbs (Vex / Rite / Craftsman / Surveyor / explore) without forcing one quest.  
2. **Guidance beyond the pier** — Path/hint craft for QM → Mine → Temper → Ledger → Fields (and first post-stamp beat). Don’t invent a second navigation system; extend `TutorialQuestTrail` / `QuestHint` patterns.  
3. **World life past harbour** — Atmosphere for Foreman / Fields / Ledger / Vex-gate (extend `LivingNpcAtmosphere`). Many small “world lives” beats > one giant system.  
4. **Early UI density** — Recipe Book + Manager first-open must not look like empty glass warehouses. Progressive / compact presentation when unlocks are few.  
5. **Craftsman / Recipe onboarding clarity** — Fix the confusing mid-tutorial vs post-stamp craft quest feel.  
6. **First combat teach** — Frame Borderlands / Vex / DamageNumbers as a moment. No rebalance.  
7. **Milestone juice** — Graduation, area discover, recipe unlock, first systems — “world noticed you” (extend UnlockToast / discover / QuestFeedback).  
8. **Exploration invitation** — Subtle POIs / readability; not loot spam.

### World-building / environment (optional, high bar)

If during analysis you find places where **a few small, high-quality structures** would make early zones feel dramatically more real, you may add them.

Allowed examples: mine entrance, tiny workshop/hut, overlook, well, small ruin, landmark tied to an NPC/quest, decorative POI.

Rules:
- **Not** a world rebuild, new region, dungeon, or worldgen system  
- Few pieces, modular, placeable via existing FAWE / `/aetherpaste` / schem workflow when possible  
- Conscious architecture: silhouette, materials, function, detail, fits biome/harbour tone  
- Prefer extending existing paste/schem paths over a parallel builder framework  
- If there’s no clear win, **skip this entire bullet**

## Quality standard

Same *care* as Harbour Hour / Hollow Reliquary claim pacing / Seraphine prop craft — **smaller scale, dry harbour/RPG tone**, not endgame spectacle. Prefer authored timing, geometry, sound, and UI hierarchy over particle carpets.

## Process

1. Read audit + handoff; open only listed files (+ neighbours you need).  
2. Confirm Harbour Hour baseline is present; don’t regress it.  
3. Pick a coherent implementation bundle (MUST + best HIGH-VALUE).  
4. Implement by extending existing classes/APIs.  
5. Compile affected modules.  
6. Commit + push to the working branch.  
7. Short finish doc: what changed, what you left alone, any schem/coords for env pieces, residual risks.

## Tone

Authored. Readable. Alive. Curious. Premium early game — not a plugin checklist, not an endgame trailer.

---
