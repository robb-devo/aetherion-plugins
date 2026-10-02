# NPC life — movement elevate (Opus EXTRA, ~18%)

Base: GitHub `robb-devo/aetherion-plugins` main @ `1922f36e884b` (2026-09-29 22:36).
The live worktree `.claude/worktrees/dialog-voice-german-localization-651358` was too deep to stage (11 levels),
so the allowlisted files were checked against it by CRLF size / hash:

| File | live worktree | main (LF → CRLF) | match |
|---|---|---|---|
| LivingNpcLife.java | 32 379 B | 31 538 + 841 lines = 32 379 | ✅ |
| LivingNpcProfile.java | 23 274 B | 22 762 + 512 = 23 274 | ✅ |
| LivingNpcService.java | 26 769 B | 26 116 + 653 = 26 769 | ✅ |
| config.yml | sha256 `bed0912c…` | sha256 `bed0912c…` | ✅ byte-identical |

## Files
| Path | Change |
|---|---|
| `AetherionQuests/src/main/java/de/aetherion/quests/npc/LivingNpcLife.java` | rewritten body-language engine (same class, same public API + `calmForTalk`) |
| `AetherionQuests/src/main/resources/config.yml` | +1 line in `npc-life:` → `pace: 1.0` |

`LivingNpcProfile.java`: **not touched**. No default hand was nonsense once beats stopped swinging it; the two bad cases
(anvil-block swings on `booster_tutor` / `eldervale_upgrade`) are fixed with beat props instead.

Shipped: `files/` (CRLF drop-ins), `npc-life-elevate.patch` (git diff vs main), `jar/AetherionQuests-1.0.0.jar` (+ SHA256SUMS),
`apply-npc-life-elevate.ps1` (hash-guarded copy into a repo root). **Not deployed.**

## Before → after feel
1. **Beats are sentences, not noise.** Before: random swing / crouch coin-flips. After: every NPC has a *persona* of 3–5
   signature beats (weighted, never the same twice in a row) — one clear action, short hold, prop + head go back.
2. **Heads turn, they don't snap.** Glances, "look down at the ledger", "raise the spyglass to the sea" are eased
   over 3–5 two-tick steps (smoothstep), then ease back to the counter.
3. **Anti-flail.** Per-NPC swing limiter (≥10 ticks between any two, ≥24 for Atmosphere greet/banter). Triple swings
   (craftsman, vex, upgrade) → one strike, or a rare two-strike spaced 12–14 ticks.
4. **Crouch is seasoning.** Before: 30–50 % of beats for egon/foreman/farmer/fisher/ledger/lark/…. After: only
   job-true kneels (farmer checks crop, foreman checks ore, fisher baits, sweeper picks up), ≤40 ticks, 45–75 s cooldown.
   Pad guide's bounce is the only uncooled crouch (the crouch *is* the job).
5. **Job-true props, restored.** Axe chops wood (wood-hit + log chips at the block in front), QM: spyglass/log-book/map,
   ledger/clerks: quill scribble (cartography scratch) + stamp, farmer: hoe till / sow seeds, fisher: cast → splash →
   reel (rare cod shown), crier: bell, sweeper: brush, lamp-lighter: torch at night / wick-trim by day.
   No more anvil-block or iron-ingot swinging. Snack (lumberjack apple) ≥2 min cooldown and never while watched close.
6. **Longer quiet gaps.** 110–400 ticks per persona (was 70–180 for everyone) + `npc-life.pace` multiplier (0.5–3.0).
7. **Talk is sacred (stronger).** Calm triggers on TalkUx busy, *any* player ≤5.5 blocks, or a forager **chop-demo within
   12 blocks** (Core `ForageAccess.isChopDemoRunning`). Calm = generation token cancels every pending delayed swing/sound,
   prop back, stand, stop use, face front. `gesture()`/`emote()` call `calmForTalk` instantly — no 5-tick gap.
8. **Talk gestures calmer.** Was: every `!` or `?` line + 30 % → arm swing. Now: `!` 75 %, else 12 %, max one per 2.5 s.
9. **Patrols stroll.** Motion loop 5 → 2 ticks (client lerp covers it), eased start/stop, turn-before-walk, 8° path gaze,
   job beat at the far point (farmer tills, vex cuts, crier rings, sweeper sweeps, merchant checks a crate…),
   turn around, stroll home, then ease back to the counter. Routes unchanged (≤2.5 blocks, ground-checked).
10. **Less teleporty.** Player within 3 blocks mid-patrol → quick walk home (0.22 b/t) instead of a snap. Snap only if
    TalkUx is actually busy or the chunk unloaded (safety kept).
11. **Sweeper sweeps while walking** (brush stroke every 16 ticks mid-leg) — the only walk-and-swing, because it's his job.
12. **Escort-safe.** While a scripted walk holds the movement lock, Life cancels its steps and never pushes the job prop
    back into the emptied hand (old timed restore could).

## Deliberately not touched
TalkUx / CastBook / DialogManager / NpcListener / lang, LivingNpcAtmosphere (its `swing()` calls just get rate-limited
inside Life), LivingNpcService, LivingNpcProfile (skins/voice/hands), Hub Origin, red thread / SkillRoads, Borderlands,
Items, weapons, boosters, ranks, NPC ids, routes, new NPCs/quests/commands, deploy.

`temper` has no LivingNpcProfile entry (only appears in CastBook), so Life never ticks it — no beat added.

## Compile
javac 21 over the full `AetherionQuests` source tree (Core compiled from main source; paper-api 1.21.1, Items, BossEngine,
PAPI from Robbi's local `.m2`): **0 errors, 0 warnings in LivingNpcLife**. Maven Central was unreachable from the build box,
so run `mvn -pl AetherionQuests -am package` locally before deploying to double-check.
