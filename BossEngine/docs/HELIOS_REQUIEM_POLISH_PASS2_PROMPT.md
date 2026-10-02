# Helios Requiem — Polish Pass 2 (Local Opus) — ship live yourself

**Copy everything below the line into the Opus chat.**

---

You are a **local Claude Opus agent** doing **Helios Requiem Polish Pass 2** only.

Robbi playtested Act I + Act II on MMO-R after the first polish. The fight is already **excellent** — especially Act II and the death sequence. Your job is a **focused escalation pass** on the weak spots below. Do **not** redesign the encounter. Do **not** touch Dev Menu, loot tables beyond the chest presentation, gear IDs, World Eater, early bosses, or locked systems unless a Helios file literally forces it.

When the pass is done: **build BossEngine, deploy to MMO-R, restart, verify `[Helios] Ready`**. Robbi will retest live. Do not wait for him to ship.

## 0) Ops — you deploy and restart (HARD)

Live = **Hetzner**, not Desktop Crafty. Never deploy to `C:\Users\Robbi\Desktop\Minecraft-Network\...`.

| Fact | Value |
|------|--------|
| SSH | `ssh aetherion-hetzner` → `root@135.181.18.162` (key `~/.ssh/aetherion_ed25519`) |
| Ops local | `C:\Users\Robbi\IdeaProjects\aetherion-ops` |
| Ops remote | `/root/aetherion-ops` |
| MMO-R UUID | `a28d676a-03ef-40f1-9ac7-7a21c2ef6383` |
| MMO-R plugins | `/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/` |
| BossEngine jar on MMO-R | `BossEngine-1.0.0.jar` |
| Helios world | `helios_requiem` |
| Docs | `aetherion-ops/docs/SERVERS.md` |

**Preferred ship sequence (Windows PowerShell):**

```powershell
cd C:\Users\Robbi\IdeaProjects\<your-helios-worktree>\BossEngine
mvn -q -DskipTests package

cd C:\Users\Robbi\IdeaProjects\aetherion-ops\bin
.\ae.ps1 deploy-jar mmor -Jar <path>\BossEngine-1.0.0.jar -DestName "BossEngine-1.0.0.jar" -NoRestart

# ae.ps1 restart often hangs on Crafty 401 — prefer direct scripts:
ssh aetherion-hetzner "bash /root/aetherion-ops/ae-stop.sh mmor; sleep 2; bash /root/aetherion-ops/ae-start.sh mmor --wait; echo START_SCRIPT_DONE"
```

Rules:
- Prefer `ae.ps1` / `/root/aetherion-ops/ae-*.sh` over ad-hoc scp.
- Graceful `stop` so the LOCKED shutdown countdown runs. Emergency only: `stop … -Now` / force.
- After `Done (` + `[Helios] Ready` in `…/logs/latest.log`, you are fine — kill hung waiters.
- Verify: `grep Helios …/logs/latest.log` → `[Helios] Ready: world 'helios_requiem'`
- In-game: `/helios start <player>` · `/helios hp 51|59|39|24|5`
- If `helios.yml` / boss YAMLs need live sync: bump config version so the plugin replaces stale copies (`.bak` old). Do **not** wipe unrelated plugin configs.
- Items jar only if you truly must change it (prefer not). DestName `AetherionItems-1.0.0.jar`.

**GitHub:** `https://github.com/robb-devo/aetherion-plugins.git`  
Worktree preference: Helios+WE live tree (`_wt_helios_polish` / `claude/helios-polish` or successor). Keep World Eater / early bosses compiling.

## First read (in order)

1. This prompt (all of it).
2. `BossEngine/docs/HELIOS_REQUIEM.md` + `HELIOS_REQUIEM_RESULT.md` + `HELIOS_POLISH_CHANGELOG.md` (context only).
3. Then open **only** Helios combat/FX under `BossEngine/src/main/java/de/aetherion/bossengine/helios/` — especially Herald, HeliosScript, portal/beam, singularity, arena, death/supernova, orbit debris, **reliquary / end chest** presentation.
4. Config only if needed: `BossEngine/src/main/resources/helios.yml` (+ version bump).

## You are NOT here to

- Rewrite the whole fight or change the musical / light-geometry identity
- Touch **Dev Menu** (`DevMenu.java`)
- Change Helios gear IDs / CMDs (`helios_*`, 4201–4205) or loot drop rates (presentation of the chest is OK)
- Touch locked systems: boosters, Borderlands vials, Custom Anvil, ranks/Admin UUID, **Blossom Blade / Gravwell Cleaver** combat, ShutdownCountdown
- “Polish” by deleting old important systems elsewhere
- Deploy BossEngine-only regressions that wipe World Eater / early bosses

## HARD rules

- **English-only** player-facing text.
- **Damage gates:** full damage in FIGHT. Invuln **only** intro / phase-shift interlude / cinematic / dying. Openings = bonus damage only, never exclusive hit windows.
- Prefer timing, pose, camera, sound, arena geometry over particle spam.
- Stutters → fix interpolation / tick / budget / teleport hitch — not more particles.
- Keep Act II’s greatness. Escalate, don’t flatten.

## Mission — what Robbi wants

### A) Act I — Herald (priority)

Feels **stockend**, sometimes **slow/dead**, a bit **too simple / boring**. May be intense — “darf gern auch krass sein.”

- Sharpen tells → hit → recovery (intentional, not sluggish/hitchy)
- Raise spectacle + threat, stay readable
- Fix body/display hitching
- Mirror stays readable; don’t make Act I a slog
- No DPS-sponge invuln

### B) Intro orbit debris / “comets”

Core motion smooth, **sometimes stutters**. Keep the idea; remove hitch (interpolation / no teleport snaps / budget).

### C) Act II — Signature beam / “Strahl” (Portal Beams / Constellation)

Still feels **random** / more show than hunt.

Make it an **absolute spectacle** and **mega dangerous**:
- Track / pressure the player (readable aim, real threat)
- Pompös + escalate: build → lock → fire → aftermath
- May **tear the arena** (scar floor, plates, debris)
- Stay dodgeable (telegraph before lethal)

Signature premium move = fight’s business card.

### D) Arena — slight expansion (optional, if clean)

Minimally expand/reshape during Act II escalation. Real stage geometry + displays. Don’t break pathing / void rescue / platforms.

### E) Black hole / singularity

Often feels like Helios **isn’t doing anything** — just run from pull.

- Keep pull identity; layer **real boss action** (beams, debris, ring collapses, pulse hazards, short tells)
- Feel hunted by Helios **and** the hole
- Not an invuln nap

### F) Death sequence — keep core, push further

Already chef’s kiss. Push: unstable star goes up — **over-dimensional** *scheppern*. Escalate existing structure; don’t replace with a generic boom.

### G) End chest — emerge from the dying star (mini add)

The current end chest / reliquary is already cool. Robbi wants the **payoff staging** upgraded to match the new giga death:

1. During / right after the final supernova detonation, the **chest is born out of the dying star** (not a quiet pop-in).
2. It **travels / settles into the center of the arena** and **floats** there (readable, inviting, mythic — not buried in debris).
3. Then **four staircases** build **epically**, one toward each **cardinal direction** (N/E/S/W), so the party can walk up to the floating chest from any approach.
4. Stairs should feel like light / stone / star-matter assembling beat-by-beat (timing + sound), not an instant fill.
5. Keep existing loot wiring / Starseed Reliquary interaction — this is **presentation + approach**, not a new loot economy.
6. Cleanup on abort / leave / next run must not leave stairs or a floating chest behind.

## Out of scope

- Act II overall structure (already phenomenal) — only callouts above
- Dev Menu / World Eater / early bosses / quests
- Damage-limit systems coming back

## Branch / deliverable

- Branch from Helios+WE live tip (e.g. `claude/helios-polish-pass2`)
- Implement Pass 2; commit clearly
- `mvn -DskipTests package` BossEngine
- **Deploy + restart MMO-R yourself** (section 0); confirm `[Helios] Ready`
- Brief report: A–G changes, files, left alone, config version, playtest notes

**STOP** after ship. No Dev Menu cleanup. No drive-by refactors.

---

## Robbi one-liner

> Act I snappier/krasser, orbit stutter weg, Act-II-Strahl = Spektakel+Killthreat+Arena-Reißerei, Black Hole = Helios handelt mit, Death noch dimensionaler, End-Chest aus dem sterbenden Stern → Mitte schweben + 4 epische Himmelsrichtungs-Treppen — rest ist schon geil. Dev Menu nicht anfassen. **Du deployest + restartest MMO-R selbst.**
