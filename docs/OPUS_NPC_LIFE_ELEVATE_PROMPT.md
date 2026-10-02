# Opus EXTRA — NPC life movement elevate (~18%)

**Budget:** ~18%. This is a **surgical** pass. If you spend more than a few minutes scanning, you are doing it wrong.

**Scan rule (GOLDEN):** Do **not** crawl the monorepo. Do **not** open dialog, DE, Hub Origin, red thread, Borderlands, Items, or TalkUx. Open the allowlist only. Grep only the keywords below. Then elevate.

**Golden rule:** Touch **nothing** outside NPC life / movement. No dialog rewrites, no quests, no lang, no Hub, no skins pipeline, no TalkUx shell.

---

## Goal

Take living NPC **body language** from “already good / a bit clunky” to **absolutely insane** — readable, job-true, calm when talking, alive when watched.

Problems Robbi still feels:
- movement / beats can feel clunky
- wild arm flailing
- too much crouch / sneak
- held items that don’t match the job (or swinging nonsense)
- patrols that look stiff

**Keep:** TalkUx bubbles, talk-pause (`isBusy` / chop-demo calm), existing NPC ids, patrol safety (ground check, short range).

**Elevate:** timing, beat variety, held-item sense, glance/patrol feel, less spam, more *character*.

---

## Allowlist (read these — almost only these)

| Path | Why |
|---|---|
| `AetherionQuests/src/main/java/de/aetherion/quests/npc/LivingNpcLife.java` | **Primary.** workBeat / patrol / glance / crouch / hold / use / later / calmForTalk |
| `AetherionQuests/src/main/resources/config.yml` | only the `npc-life:` block (tune if needed) |
| `AetherionQuests/src/main/java/de/aetherion/quests/npc/LivingNpcProfile.java` | **only** `.hand(...)` defaults if a held item is nonsense — do not rewrite skins/voice |

### Optional peek (max 1 file if stuck)

| Path | Why |
|---|---|
| `AetherionQuests/.../npc/LivingNpcService.java` | only `moveTo` / `setMainHand` / turn-to-player distance if patrol smoothness needs it |

### Grep keywords (then stop)

`workBeat`, `routeFor`, `crouch`, `later(`, `calmForTalk`, `nextBeat`, `beginPatrol`, `glance`

---

## Hard deny-list

- `TalkUx.java` (any UX shell change)
- `CastBook`, `DialogManager`, `NpcListener`, `lang/de.yml`, red thread / SkillRoads
- `LivingNpcAtmosphere` unless a one-line sound sync is required — prefer Life only
- Hub Origin cast, Borderlands, weapons, boosters, ranks, shutdown
- New NPCs / new quests / new commands
- Deploy

---

## Design bar (“geisteskrank”)

1. **Job-true props** — axe chops wood, book/quill for clerks, spyglass for QM, hoe for farmer, rod for fisher. No iron-ingot flailing. Snack is rare and never mid-talk.
2. **Anti-spam** — fewer multi-swings; longer quiet gaps; crouch is a seasoning, not a lifestyle.
3. **Readable beats** — one clear action, short hold, restore hand; particles/sounds match the job at low volume.
4. **Talk is sacred** — if `TalkUx.isBusy` or chop-demo nearby: stand, restore hand, cancel delayed swings (keep existing calm guards; strengthen if gaps remain).
5. **Patrols** — still tiny (≤ ~3 blocks), ground-safe; smoother step / pause-at-end “work” beat, less teleporty feel if easy.
6. **Per-NPC personality** — 2–3 signature beats each for the harbour spine (egon, lumberjack, quartermaster, foreman, temper, ledger, farmer, vex, fisher) — not identical random noise.
7. **Do not** invent a second life system. Elevate `LivingNpcLife`.

Ambient extras (`town_crier`, `street_sweeper`, `lamp_lighter`) may get nicer beats too — they already exist; Robbi places them. No placement code required.

---

## Deliverable

1. Prefer **only** `LivingNpcLife.java` (+ tiny Profile hand fixes / `npc-life` config if needed).
2. Ship folder `_npc_life_elevate_ship/` with:
   - file list
   - 8–12 bullets: before → after feel
   - what you deliberately did *not* touch
3. Compile `AetherionQuests`.
4. Do **not** deploy.

If the worktree path is too deep to read: rebuild from GitHub `main` + note hash checks like prior ships; target files must match Robbi’s live Quests life class size/intent.

---

## Starter line (paste into a **new** Opus chat, High is enough)

> Read **only** `docs/OPUS_NPC_LIFE_ELEVATE_PROMPT.md` and its allowlist. ~18% EXTRA: elevate `LivingNpcLife` movement/body language from good → insane. No monorepo scan. Touch nothing else (TalkUx / dialog / red thread / Hub / weapons LOCKED). Ship `_npc_life_elevate_ship/`, compile Quests, do not deploy.
