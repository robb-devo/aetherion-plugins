# Opus Prompt — Combat Juice / Sekiro Feel (on top of shipped kit)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Target:** Paper **1.21.1**, Java 21, resource pack `pack_format: 34`.  
**Tree:** `C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`  
**Ops bible:** `C:\Users\Robbi\IdeaProjects\aetherion-ops\docs\OPUS_SESSION_BIBLE.md`  
**Quota:** Use Max / Extra freely. One mission only: **make the existing combat kit feel insane**.

---

## Mission (one sentence)

The shared combat kit already ships (stamina, dash, roll, parry/guard, melee cadence, dummy, `combat-system.yml`).  
**Do not rebuild it.** Elevate **feel, readability, and spectacle** until a vanilla client fight reads like a Soulslike / Sekiro hybrid — clear telegraphs, juicy success moments, punishing but fair windows — still 100% plugin + existing pack (no client mod).

---

## What already exists (start here — read first)

| Piece | Path |
|-------|------|
| Kit entry | `AetherionItems/.../combat/system/CombatSystem.java` |
| Input / events | `CombatListener.java`, `Mobility.java`, `Guard.java`, `MeleeCadence.java` |
| Juice today (too quiet) | `HitFeedback.java`, `DamageNumbers.java`, `StaminaHud.java` / `PlayerHud.java` |
| Trainer | `TrainingDummy.java` (`/combat dummy`) |
| Config + hot reload | `src/main/resources/combat-system.yml` + `/combat reload` |
| Live | mmor already has the kit jar; config at `plugins/AetherionItems/combat-system.yml` |

Foundation verdict (Robb + prior review): **architecture is strong; juice is mid.** Your job is the juice layer + tuning pass.

---

## Non-negotiable LOCKED

Stop with `LOCKED … SYSTEM CONFLICT: …` if touched wrongly:

- **Blossom Blade** (`ashen_katana`) + **Gravwell Cleaver** (`gravwell_cleaver`) — abilities / VFX / combat feel untouched. Cadence exempt stays.
- TalkUx bubble shell, boosters (stay socketed), ranks / `player-ranks.yml`, Anvil sockets, ShutdownCountdown, Borderlands vials.
- Additive ship — no DevMenu / factory wipe.
- Do **not** move the game loop into Core. Prefer Items-only. Core only if a tiny shared API is already there (`ActionBarLane`, etc.).
- No client mod. No Iris-required shader. Pack changes OK if they elevate combat glyphs / hit cues and stay pack_format 34.

---

## Feel goals (what “done” means for Robb)

After 60 seconds on `/combat dummy`, a player who never read the docs should feel:

1. **Telegraphs are obvious** — jab vs slam are unmissable (color, sound, world mark, timing).
2. **Perfect parry is orgasmic** — sound stack, sparks/flash particles, brief hitstop, big callout, golem reel; miss still readable (whiff clang + lockout).
3. **Perfect dodge / roll** — air trail or dust burst, distinct “PERFECT DODGE” moment, refund feels rewarding.
4. **Heavy / finisher / riposte** — each has its own number color + strike cue + sound; spam clicks feel weak and look grey on purpose.
5. **Stamina** — always visible in a fight; WINDED is scary; gauge pulses on spend.
6. **Bosses** — still no AI stun; perfect parry still *feels* huge (negate + juice) even without stagger.
7. **German + English** callouts stay.

Think Sekiro posture moments + Dark Souls weight — **illusion of weight** via timing, sound, particles, hitstop, camera kick (light), not fake animations.

---

## Hard constraints / realism

Allowed to push hard:
- Particles, sounds, ActionBarLane callouts, TextDisplays, dust rings, brief freeze/hitstop on mobs (already patterned), hurt-cam intensity, dummy windup art, config presets, stamina HUD emphasis, slightly wider windows if feel needs it.

Do **not** promise / fake:
- True camera lock-on, custom entity animations, frame-data UI like a fighting game, client-side shader packs as a requirement.

If a trick needs a small pack glyph (font / item model), add it to `AetherionItems/resourcepack/` and document; keep BarChrome / glyph advances intact unless you regenerate correctly.

---

## Work plan (do in this order)

### A) Audit current juice (short)
Open `HitFeedback`, dummy telegraphs, cadence multipliers. List what is too quiet / too short / too easy to miss. Then change it.

### B) Telegraph pass (dummy + reusable helpers)
- Jab: longer readable windup option, brighter `!`, distinct charge sound every few ticks.
- Slam: fat ground ring, `!!`, bass cue, **unparryable** stays; roll window feels fair.
- Extract shared “Telegraph” helper if it helps trash mobs later — but **do not** rewrite BossEngine AI this session unless a tiny opt-in hook is trivial and safe.

### C) Success juice (parry / dodge / riposte / finisher)
Scale with `feedback.intensity` (keep 0 = silent). At intensity 1.0 default should feel **loud**:
- Perfect parry: multi-sound, electric + gold dust, short world time feel via hitstop, callout, optional micro screen kick.
- Guard (non-perfect): steel scrape, half-damage still clear.
- Perfect dodge: cyan/white burst + whoosh + refund ping.
- Riposte / finisher: purple / orange number pop already exists — make the strike **impossible to miss** (extra sweep/crit particles + dedicated sting).

### D) Window / balance feel (yml first, code if needed)
Ship a tuned default **and** a named preset block or comments, e.g.:
- `feel: sekiro` style numbers Robb can `/combat reload`
Suggested direction (you may refine after playtesting with dummy):
- Perfect parry window a bit more forgiving than 5 ticks (still skillful).
- Dummy windup slightly longer for learning; pattern stays jab/jab/slam.
- Stamina costs readable but not constantly WINDED on one mistake.
Keep bosses: `parry.bosses: negate` (no AI pause). Roll-through-bosses stays configurable.

### E) HUD / callouts
- Stamina row must show whenever spending or below full in combat; don’t vanish mid-fight.
- Callouts: short, punchy, DE+EN; don’t fight Manager/ActionBarLane ownership — use the lane API correctly.
- Text-HUD mode: if stamina still missing, add a minimal fallback or document; prefer fixing if cheap.

### F) Polish + ship (Robb wants you to finish)
1. Build `AetherionItems`, deploy to **mmor** (and mmod only if Core there already has `ActionBarLane` — otherwise leave mmod, note it).
2. Backup live jar as `*.bak-pre-combat-juice-<ts>` before overwrite.
3. Restart **mmor** via `ae.ps1 restart mmor` (no Done-wait). Do not restart unrelated servers.
4. Verify: no “Combat kit failed to start”; `/combat` works; dummy telegraphs + parry/roll juice visible.
5. Leave `combat-system.yml` hot-reloadable; document knobs in file comments.

Do **not** commit unless Robb asks.

---

## Out of scope this session

- New weapons, new bosses, dungeon port to mmod Core upgrade (mention as follow-up if needed).
- Rewriting DamageListener formulas.
- Touching Blossom / Gravwell combat code.
- Full network parity / mmoc/mmoi combat copy.

---

## Deliverable report (end of session)

1. What changed (files + feel intent).
2. Config defaults / preset (before → after key numbers).
3. Jar path, backup name, mmor pid.
4. Click-test checklist (dummy first, then trash mob, then one boss parry feel).
5. Known limits still Paper-bound.
6. Rollback: `enabled: false` + `/combat reload`, or restore bak jar + restart.

---

## Mini paste (start of chat)

```text
Read docs/prompts/OPUS_COMBAT_JUICE_SEKIRO_PROMPT.md and aetherion-ops/docs/OPUS_SESSION_BIBLE.md.
Worktree: mining-eldervale-progression-65660c. Combat kit ALREADY SHIPS — do not rebuild architecture.
Mission: Sekiro/Souls-like JUICE + telegraph + window tuning on the existing combat/system kit.
LOCKED: Blossom Blade, Gravwell Cleaver, TalkUx, ranks, boosters-stay-socketed, additive ship.
At the end: build, deploy Items to mmor with bak, restart mmor, report click-test.
Robb has low Cursor balance — finish and polish yourself; one deep session, ship feel.
```
