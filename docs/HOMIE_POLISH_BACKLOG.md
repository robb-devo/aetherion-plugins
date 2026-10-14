# Homie / Staff polish backlog

**Date:** 2026-10-15  
**Sources:** Discord Shy ↔ Mr.Dunnwall (staff testers). Lemon evening chat **not in this session** — paste when you want it merged in.  
**Stance:** Beta closed; friends/staff only. Polish toward “feels done”, not open-IP spam.

---

## How to read

| Tag | Meaning |
|-----|---------|
| **BUG** | Looks wrong / counts wrong / client break — investigate |
| **UX** | Clearer copy / teach / signposting |
| **DESIGN** | Product call (Robb already answered or needs watch) |
| **KEEP** | Positive — don’t “fix” |
| **P0–P3** | Ship order (feel → clarity → QoL → nice) |

---

## From Shy (priority extract)

### P0 — Quest-accept UI looks broken (image)
- **Tag:** BUG (likely client)
- **Report:** “Critical… looks bad for the server” — happens **every time** he accepts a quest. Screenshot in Discord.
- **Robb:** Nobody else reported; suspects **client > 1.21.1** while server is 1.21.1.
- **Do:** Reproduce on **vanilla 1.21.1** vs newer. If only newer: document “play 1.21.1” + soft warn, or fix TalkUx/quest-offer layout for cross-version. **TalkUx shell stays LOCKED** — presentation/pack/version only.
- **Staff ask:** Exact client version + resource pack on/off + screenshot of the broken accept state.

### P0 — Invisible water
- **Tag:** BUG (pack / version)
- **Report:** Water invisible; unmodded client; might be texture pack.
- **Do:** Check pack water/still/flow on 1.21.1 vs newer. Homie: pack forced? OptiFine/Sodium? Fresh pack download.
- **Not:** worldedit water or biome rewrite unless pack is clean and still broken.

### P1 — Foreman “32 ores” felt like 19
- **Tag:** UX (maybe not a bug)
- **Report:** First foreman quest finished at ~19 breaks; expected 32.
- **Robb:** Objective is **gather**, not break count; Fortune / multi-drop can finish early.
- **Do:** Audit objective text + progress UI (`first_shift` / foreman). If it says “mine 32 ores” but counts **items**, change copy to **“Gather 32 ore”** / show item progress. Only dig code if counter really tracks breaks.

### P2 — Auto-pickup mob drops (Bestiary farm lag)
- **Tag:** DESIGN (Robb kept gate)
- **Report:** Wants Hypixel-style vacuum for mob drops; leaving loot = lag + inconvenience.
- **Robb:** Auto-pickup from **Aetherion level 5**, not instant — intentional progression.
- **Do (light):** Make the unlock **impossible to miss** (level-up toast already exists? reinforce in tutorial / skill menu / first bestiary hint). Optional later: earlier **combat-only** vacuum or magnet radius — only if more testers echo Shy.
- **Don’t:** Remove the level gate because one tester prefers day-1 QoL.

### P3 — Standing in regenerating forage tree can kill
- **Tag:** DESIGN / tiny safety
- **Report:** Tree regen crushes player.
- **Robb:** Low value; death soft; “just move”; not Hypixel-critical.
- **Do (optional):** No crush damage / slight push-out when trunk replaces air — only if cheap. Else leave.

### KEEP — positive (do not regress)
- Wood gather **minigame** feel
- **Text animations** / TalkUx bubbles
- Closed beta / low pop is expected (“dead” ≠ bug)

### DESIGN watch — “progression for progression’s sake”
- Shy pushback on level-gated auto-pickup.
- Robb: keep until more staff agree it feels fake. Revisit after 3+ independent reports.

---

## Staff workflow (Shy wants to help)

1. Always note: **client version**, pack yes/no, world/coords, quest id, screenshot.
2. Prefer **1.21.1 vanilla + official pack** for bug reports.
3. Tag Discord: `bug` / `ux` / `idea` so triage stays clean.
4. Big systems (combat Sat, tutorial after): collect notes, don’t hot-patch mid-Opus.

---

## Suggested Opus / agent slices (when you open a session)

| Slice | Scope | Blocked by |
|-------|--------|------------|
| **A — Client/pack triage** | Quest-accept glitch + invisible water | Need Shy’s client ver + screenshots in-thread |
| **B — Foreman copy/counter** | Quest objective clarity | None |
| **C — Pickup teach** | Level-5 vacuum messaging | None (no gate removal) |
| **D — Tree crush** | Optional one-liner | Low priority |
| Tutorial overhaul | Full onboarding | **After combat Sat** |
| Lemon merge | Add his evening notes | **Paste chat** |

---

## Lemon = LimePuppet (Server-Chat, Abend ~20:19–22:43)

| Signal | Tag | Note |
|--------|-----|------|
| “whens auto collect?” / “finally auto pickup” | UX | Gate unclear; he hit it ~lvl 5–8 fast |
| “eh it only takes like a minute” | DESIGN | Softens Shy’s “gate is fake progression” — still teach it |
| Rhythm cool / pet catch anim cool | KEEP | |
| “wth is crystal find” → nvm | UX | Self-solved; one Foreman tip helps |
| “why any blueprint instead of veinminer” | UX | Eldervale drop clarity |
| Mining power “only working in the dark” | BUG? | Check if Night/Dark power tooltip confuses |
| Pickaxe needing dungeon core | UX | Recipe/gate communication |
| “where do i get obsidian” (not on Eldervale) | UX | Source unclear |
| “upgrading gear deletes boosters” / boots deleted | **BUG** | Booster-on-upgrade — LOCKED booster rules; investigate loss path, don’t nerf stack-64 |
| “i mine so slowly” | DESIGN | Mid progression feel; not tutorial |
| Veinminer / Siphon chase | KEEP energy | Don’t over-gate discovery after tutorial teaches skills |

---

## Out of scope here

Combat elevate (Sat) · Tutorial rewrite · Crossing/BL · Chest scatter (shipped) · Signature weapons · ranks/boosters LOCKED.
