# Grok — Performance deep dive (ANALYSIS ONLY)

**Paste everything below the line into Grok.**  
Mode: thorough / scary-complete. **Zero code changes.**

Workspace root:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

(If that path is missing, use the linked `aetherion-story-p1` / live Aetherion monorepo Robb points you at.)

---

```text
# AETHERION — Brutal performance autopsy (READ-ONLY)

You are doing a **performance deep dive only**.  
Stable beta is close. You must **not** change code, configs, YAML balancing, tick rates, or game logic.

## Absolute rules

1. **NO EDITS.** No patches, no “quick fixes”, no drive-by refactors, no “while we’re here”.
2. **NO LOGIC CHANGES** in recommendations that would alter gameplay feel unless you mark them as *optional later / needs Robb sign-off* and separate them from pure waste.
3. **If something is good, leave it.** Say “KEEP” and move on. Do not propose rewriting healthy systems for theoretical micro-gains.
4. Be **dumb-thorough and creepy-complete**: assume every plugin can hide a silent TPS eater. Grep hard. Follow timers. Count entities. Trace listeners. Do not skim.
5. Deliverable = **analysis + ranked optimization plan**. Nothing else.

## Context (so you don’t invent architecture)

Paper **1.21.x** multi-server network (Hub, MMO-R, MMO-D, MMO-C, MMO-I, Proxy). Plugins roughly:

| Plugin | Owns |
|---|---|
| AetherionCore | Keys, services, void gen, FancyNpc facade — must stay thin |
| AetherionItems | Items, economy/market, skills, combat HUD, wildlife guards, storage/sacks |
| BossEngine + AetherionGnomon | Boss AI / GNOMON encounter |
| AetherionQuests | Quests, TalkUx, LivingNpcLife ambient |
| AetherionHub | Hub spawns / origin |
| AetherionDungeons | Instances / warm pool |
| AetherMobs | Pets / catch |
| AetherionGuilds | Personal islands, quarry minions, belts/logistics, factory |
| Mining / Farming / Foraging / Fishing | Isles, regen, minigames |
| AetherionStressBots | QA only — ignore for live perf unless it can load on prod |

Read `ARCHITECTURE.md` first (1 page). Then hunt.

LOCKED systems (do not recommend “simplify away”): TalkUx shell, boosters stack 64, Anvil sockets, ranks/`player-ranks.yml`, Blossom Blade / Gravwell combat feel, ShutdownCountdown, Borderlands vials. Perf notes that *touch* those must be “measure only / defer”.

## How to hunt (mandatory methods)

Be systematic. For **each** Aetherion plugin:

### 1) Scheduler / tick tax
Grep and inventory every:
- `runTaskTimer` / `runTaskTimerAsynchronously` / `BukkitRunnable` / repeating tasks
- Period `1L` or `1` (every tick) — list owner, what it does, how many can run at once
- Per-player timers that are never cancelled on quit / world change / disable

### 2) Entity & world scans
Find:
- `getEntities`, `getNearbyEntities`, world entity iteration every tick/second
- Chunk load storms (`ChunkLoadEvent` heavy work, sync FAWE/schem, display entity spam)
- ArmorStand / ItemDisplay / TextDisplay / BlockDisplay leaks (island props, belts, TalkUx, bosses, pets)
- Pets / minions / quarry visuals / belt cargo displays — count growth with playtime

### 3) Event listener pile-ups
- Same event handled in many plugins (PlayerMoveEvent, CreatureSpawnEvent, InventoryClick, ChunkLoad)
- MONITOR + HIGH both doing heavy work
- PlayerMove without early distance/world gates
- Spawn cancel chains (WildlifeLooks, FarmIslandAmbience, Borderlands, Boss protect) — note double work

### 4) Packet / UI spam
- ActionBar / BossBar / scoreboard / TAB refresh rates
- HealthListener / AbilityCooldownHud / QuestBars / IslandBar fighting
- Particle + sound storms on loops (gather FX, belt FX, NPC ambient, boss telegraphs)

### 5) I/O on the main thread
- YAML save/load on join, quit, inventory close, every N ticks
- Sync disk in listeners
- Network blob sync / Character sync paths if they look hot (measure suspicion only)

### 6) Island / factory / dungeon specifics
- Personal island logistics tick (belts, mills, forges, quarry minions)
- Ghost holograms / PropBodies / BeltVisuals cull distance
- Dungeon warm pool / instance copy cost
- Void worlds generating or loading junk chunks

### 7) Cross-server / proxy
Only if visible in code: unnecessary sync, double plugin load of same heavy systems on servers that don’t need them. Don’t invent ops changes without evidence.

## Severity rubric

| Rank | Meaning |
|---|---|
| **P0** | Likely causes hitch/TPS drop under normal online play — fix first later |
| **P1** | Scales badly with players/entities/islands — will hurt soon |
| **P2** | Waste / duplicate / unclean — nice cleanup when stable |
| **KEEP** | Measured or clearly fine — do not touch |
| **DEFER** | Touches LOCKED / combat feel / needs profiler proof first |

For every finding: **file + symbol**, what it does, why it’s costly, evidence (grep hit / call frequency / O(n) shape), and a **safe later fix idea** that preserves behaviour.

## Explicit non-goals

- No rewrites of combat, bosses, TalkUx, economy math, factory gameplay
- No “delete the feature”
- No micro-optimizing string concat in lore builders unless it’s in a hot tick
- No Maven/Paper install rabbit holes
- No deploy / restart instructions beyond “Robb will apply later”

## Output format (strict)

### A) Executive (≤15 lines)
How bad is it? Where does hitch risk concentrate? What to ignore?

### B) Inventory tables
1. Every-tick / sub-5-tick tasks (plugin → class → period → cancel-safe?)
2. Heavy listeners (event → plugins → risk)
3. Entity/display suspects
4. I/O on main thread suspects

### C) Ranked optimization plan
Ordered P0 → P2. Each item:
- Symptom it would fix
- Touch files
- Risk to gameplay (none / low / needs sign-off)
- Suggested verify method (Spark, timings, entity count, `/paper dump`, in-game scenario)

### D) KEEP list
Things that look expensive but are justified — **do not “optimize” these**.

### E) Profiler wishlist
Exact Spark/timings scenarios Robb should run on MMO-R / Hub / island / dungeon to confirm P0s (join storm, 2 players on factory isle, forage fell spam, boss fight, harbour with LivingNpcLife, market open).

### F) Stop
No code. No patch. No “I already fixed it”.

## Starter line

> Read-only performance autopsy of the Aetherion monorepo. No edits. Brutal thoroughness. Rank waste vs KEEP. End with a P0→P2 optimization plan and profiler scenarios. Do not change tick logic or gameplay.
```
