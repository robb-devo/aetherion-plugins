# Helios Requiem — Polish Pass (Cloud Agent)

**Copy everything below the line into the Cloud Agent.**

---

You are a **Cursor Cloud Agent** polishing **Helios Requiem** (Act I Herald + Act II Helios) on the live Aetherion stack. Robbi playtested on MMO-R. The skeleton and creative core stay — escalate polish, pacing, spectacle, and loot. Then **build, deploy, and restart MMO-R** yourself so he can retest.

## 0) Ops — you can and should ship live (read this first)

Live = **Hetzner**, not Desktop Crafty. Never deploy to `C:\Users\Robbi\Desktop\Minecraft-Network\...`.

| Fact | Value |
|------|--------|
| SSH | `ssh aetherion-hetzner` → `root@135.181.18.162` key `~/.ssh/aetherion_ed25519` |
| Ops local | `C:\Users\Robbi\IdeaProjects\aetherion-ops` (or clone / sync equivalent on the cloud box) |
| Ops remote | `/root/aetherion-ops` |
| MMO-R UUID | `a28d676a-03ef-40f1-9ac7-7a21c2ef6383` |
| MMO-R plugins | `/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/` |
| BossEngine jar name on MMO-R | `BossEngine-1.0.0.jar` |
| Helios world | `helios_requiem` (BossEngine creates it; void world; Multiverse optional) |
| Docs | `aetherion-ops/docs/SERVERS.md` |

**Preferred commands (Windows-style; adapt if your shell is Linux):**

```powershell
cd C:\Users\Robbi\IdeaProjects\aetherion-ops\bin
# after mvn package of BossEngine:
.\ae.ps1 deploy-jar mmor -Jar <path>\BossEngine-1.0.0.jar -DestName "BossEngine-1.0.0.jar" -NoRestart
.\ae.ps1 restart mmor
```

Rules:
- Prefer `ae.ps1` / `/root/aetherion-ops/ae-*.sh` over ad-hoc scp one-offs.
- Default: graceful `stop` so the LOCKED shutdown countdown runs. Emergency only: `stop … -Now`.
- After deploy, `ae.ps1` often hangs on Crafty API 401 after `Done` — once log shows `Done (` and `[Helios] Ready`, you are fine; kill the hung waiter.
- Verify: `grep Helios …/logs/latest.log` → `[Helios] Ready: world 'helios_requiem'`
- Test command in-game: `/helios start <player>` · phase jumps `/helios hp 51|59|39|24|5`

If `AetherionItems` changes for the set/weapon: deploy that jar too (`AetherionItems-1.0.0.jar` on MMO-R), then one restart.

## 1) Where the code lives / current live state

**GitHub:** `https://github.com/robb-devo/aetherion-plugins.git`

| Branch / tree | Role |
|---------------|------|
| `claude/confident-bell-u0mcjv` | Original Helios Acts I+II (~13k LOC under `BossEngine/.../helios/`) |
| `claude/helios-live-deploy` | Live merge: Helios + World Eater + early-boss polish in one BossEngine (what MMO-R runs) |
| Local worktree used for last ship | `C:\Users\Robbi\IdeaProjects\_wt_helios_deploy` |

**Already live on MMO-R (do not regress):**
1. Helios world + `/helios` commands + instance slots.
2. World Eater + early bosses still load in the same jar.
3. **Death:** no wipe while on death screen; respawn back on the **arena floor as Survival** (continue fighting). Not permanent spectator echo, not main-world kick. (Fixes earlier wipe when `party.alive()` went empty.)
4. **Quest/hint bossbars suppressed** inside `helios_requiem` (same pattern as World Eater `QuestBars.suppress` + `QuestHint.clear`).
5. Herald pays **no** loot (Act I); Helios loot via capsules / `grantToChest`.
6. Sky packets throttled when unchanged.
7. Result/architecture docs: `BossEngine/docs/HELIOS_REQUIEM.md`, `HELIOS_REQUIEM_RESULT.md`.

Create a **new branch** from the current live Helios+WE tree (prefer `claude/helios-live-deploy` if present on remote; else merge/cherry-pick onto confident-bell). Do **not** overwrite World Eater / early-boss directors while polishing Helios.

**Locked systems (never touch):** Boosters, Borderlands vials, Custom Anvil sockets, Rank/Admin UUID rules, Blossom Blade / Gravwell Cleaver abilities, ShutdownCountdown.

**Language (HARD):** Player-facing text must be **English only**. No German in titles, bossbars, actionbars, death messages, phase names, tells, loot names, or chat. The previous pass mixed DE/EN — strip German. Code comments may stay English. `Lang.pick` may keep a DE branch only if the English path is what players see by default and DE is unused — prefer cleaning to English-only strings.

## 2) Mission

Keep the **musical / light-geometry / black-hole / rebuild-on-death** identity. Escalate spectacle and readability. Fix pacing so the fight is hard but **readable**, with real attack windows. Ship armor set + weapon for the drop. Deploy to MMO-R and restart.

Creative leash: **open**. Core stays; polish and escalate where Robbi called out weak spots. Arena may lean harder into **destruction**. Portal beams should become a **signature premium move**.

## 3) Must-fix (from playtest)

### A) Loot — armor set + weapon
- Design and implement a **Helios-themed armor set + weapon** (create items, wire into Helios reward / `helios_requiem.yml` / `StarseedReliquary` / `grantToChest` — match World Eater gear patterns under `AetherionItems` + BossEngine loot).
- Previous freeze note (“don’t bloat CustomItem”) is **lifted for this pass** — Robbi wants the set/weapon created now.
- English names/lore only. Keep IDs stable once chosen.

### B) Pacing — too fast / too strong / no windows
- Both Herald and Helios feel **too fast** and **too strong**; attacks chain with no clear punish windows.
- Slow tells and recovery; keep hitstop; add **readable attack windows** where players can DPS.
- Do not turn it into a tutorial slog — still lethal, but patterned.

### C) Black hole / singularity — too strong
- Pull is delicious but currently “no counterplay, just eaten.”
- Keep the setpiece; tone pull / time-dilation / camera so skilled play can resist or escape briefly; still scary.

### D) Shock / sound / ring waves — too much KB, too frequent
- Rings that fire outward are great but knock players off the arena constantly; respawn → instantly hit again.
- Reduce frequency and/or knockback; prefer **staying on the platform**. Optionally improve void rescue (yank back up / soft land) if fall-offs still happen — make that feel intentional, not slapstick.

### E) Mystery glass block over the player’s head
- Investigate. If it is an intentional effect prop (lens / vignette / tell), keep and make it make sense. If orphaned / useless, **remove**.

### F) Supernova finale — broken + boring
- Collapse / spiral: blocks **glitch hard** — fix interpolation / restore / display cleanup.
- Actual supernova beat is **unspectacular** (feels like two blocks + “SUPERNOVA” text). This must hit Minecraft’s spectacle ceiling: dying star, light geometry, camera, sky, silence → detonation — worthy of Helios. Escalate freely.

### G) Portal + beam attack — must become a premium signature
- Currently flat / boring. This should be one of Helios’s **most premium unique moves**.
- Full creative escalation: tells, color pairs, prism, geometry, hit timing, audio, camera — make players say holy shit.

### H) Arena
- Core layout can stay; allow a **more destructive** read as the fight progresses (rings burning, islands, debris) — still fair footing and clear safe ground.

## 4) Do not break

- Instance void world + slots + crash journal + return home.
- Cutscene invuln, exploit locks (pearls/chorus/elytra/fly/build), enrage, scaling, display budget / quality tiers.
- Live patches in §1 (arena respawn, quest hush, herald no loot).
- World Eater / early bosses in the same jar.

## 5) Suggested read order

1. `BossEngine/docs/HELIOS_REQUIEM.md` + `HELIOS_REQUIEM_RESULT.md`
2. `BossEngine/.../helios/` (module, encounter, guard, herald, requiem, star, reward)
3. World Eater gear + loot as reference: `WorldEaterGearListener`, WE directors, `grantToChest`
4. `helios.yml`, `bosses/helios_herald.yml`, `bosses/helios_requiem.yml`

## 6) Deliverable

1. Branch with polish + set/weapon.
2. `mvn -pl BossEngine -am package -DskipTests` (+ Items if needed).
3. Deploy jar(s) to **mmor**, restart, confirm `[Helios] Ready` in log.
4. Short changelog for Robbi: pacing numbers, supernova/portals/waves/BH notes, item ids, anything left open.

When unsure between “safer” and “more insane spectacle” on portals / supernova / arena destruction: **choose insane spectacle**, as long as players can still stand on the floor and read a window to hit.
