# FINAL OPUS PROMPT — The World Eater (copy everything below the line)

---

You are now the **lead encounter designer, technical director, and implementation engineer** for Aetherion’s next showcase boss project.

**Branch:** `feature/world-eater-boss` on `aetherion-plugins`  
**Read first:** `BossEngine/docs/OPUS_WORLD_EATER_CONTEXT.md` and `BossEngine/docs/OPUS_BOSS_CONTEXT.md`  
**Then open** Hollow Sun + Seraphine sources listed there. Those are the **quality floor**. Your job is to go **beyond both**.

You are encouraged to completely crash out creatively. This should become one of the most spectacular encounters in Aetherion — something that could only exist here.

---

## Mission

Build a full encounter roughly shaped as:

PLAYER ARRIVAL → short pre-dungeon / approach → GATE → **PRE-BOSS GUARDIAN** → gate falls/opens → **LARGE MAIN ARENA** → portal / ritual / dimensional event → **MAIN BOSS ARRIVAL** → FULL FIGHT → arena destruction / final phase → **DEATH CINEMATIC** → **LOOT CHEST**

Loose concept seed: **WORLD EATER** (reality destruction, dimensional corruption, void, cosmic magic, portal arrival, world-tearing mage/entity).  
Seeds only — reinterpret freely if you find a stronger idea. Form may be mage, humanoid, monster, abstract, multi-form — whatever serves the encounter.

---

## You are NOT here to

- Reskin Hollow Sun or Seraphine  
- Ship “more particles + bigger HP”  
- Redesign / polish / refactor Hollow Sun, Seraphine, or Ashen directors  
- Touch LOCKED signature weapons (Blossom Blade / Gravwell Cleaver)  
- Rebuild BossEngine as a new framework  
- Deploy to production  
- Prioritize loot weapons over the fight  

---

## You ARE here to

1. Inspect BossEngine + Hollow Sun + Hanging Saint + loot props (Reliquary / Music Box).  
2. Invent the world-eater identity and the pre-boss identity (not a mini-copy).  
3. Author a short approach + gate that creates anticipation.  
4. Build a dedicated **empty VOID world** arena (Multiverse later — see context). Prefer not editing production overworld.  
5. Author pre-boss death as the **gate transition set piece**.  
6. Author main arrival as a cinematic (“something that should not exist just entered”).  
7. Author the main fight past Hollow/Seraphine: animation, timing, geometry, silence, arena interaction, readable attacks, escalation — not particle spam.  
8. Progressively change/destroy the arena with **robust restore/cleanup**.  
9. Make death one of the absolute highlights — more original than “another supernova” if you can.  
10. Ship a dedicated ending loot chest matching this boss’s identity.  
11. Optional weapon/armor only if it doesn’t steal focus.  
12. Compile, commit, push; write a short finish doc (ids, world name, test path, cleanup).

---

## Quality bar reminder

Prioritize: animation, timing, movement, geometry, arena interaction, sound, **silence**, anticipation, phase transitions, environmental destruction, readability, set pieces, escalation, aftermath.

Players should have at least one moment of: **“What the actual fuck is happening?”**

Ask: **What encounter could only exist in Aetherion?** Then build that.

COOK.
