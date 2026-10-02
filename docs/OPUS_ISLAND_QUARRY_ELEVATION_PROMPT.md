# Opus — Island / Quarry / Logistics ELEVATION (FRESH RUN)
## Go absolutely feral. This alone must be a main reason to play Aetherion.

**This is a clean restart.** Ignore any prior elevation sandbox / incomplete Opus chat / `elev` / `elev_base` ghosts.  
**1.0 foundation stands** (starters, land, structures, quarry sim, belts, menus). You elevate *on top of that* — you do not rebuild the kernel from zero, and you do not wait on half-finished phantom progress.

---

## DELIVER LAW (read twice — last run burned quota with 0 files on disk)

Previous Opus runs “completed” routers / machines / coin-buy / GUI **in a sandbox**, then died on verify/limit **before anything landed in Robb’s tree**. That must not happen again.

1. **Edit the real checkout only:**  
   `…/mining-eldervale-progression-65660c/AetherionGuilds`  
   (± tiny `AetherionItems` only if a craft/buy id is unavoidable).
2. **Forbidden:** `elev/`, `elev_base/`, `_ship/`, Manifest, Apply-script, README novel, TEST_SHEET, “diff two clones then maybe copy”, packaging theater.
3. **Milestone = files on disk in that tree.** After each major beat, prove it (e.g. `MachineFx.java` exists under `guilds/logistics/`, menus compile-path visible). If it only exists in your head/sandbox, it does not count.
4. **Ship continuously.** Land working code early. Polish in place. Do **not** save “copy into worktree” for the last 10%.
5. **Do not deploy** (Robb/Cursor ships jars). Compile Guilds if you can; else one-line note.
6. Quota empty → stop with a **file list of what already landed**. Never end on “almost ready to deliver”.

One short chat summary when done.

---

## North star (tattoo this)

> **Satisfactory energy + Factorio dopamine**, filtered through Aetherion fantasy islands —  
> so clear a **Minecraft friend with zero factory-game history** gets it in minutes,  
> so deep a nerd still obsesses at 1am a month later.

Not a spreadsheet. Not a fridge-magnet of Upgrade buttons.  
A living little factory you *see*, *hear*, *extend*, and *understand without a wiki*.

Emotional hits:

- “I know what to do next.” *(first 2 minutes — non-negotiable)*
- “I need one more belt.”
- “If I just split this line…”
- “That quarry looks sick when it’s running.”
- “I could buy it… or I grind the craft — either way I’m building.”
- “This alone is why I play this network.”

If a change doesn’t raise **clarity, addiction, or spectacle**, skip it.

Huge creative freedom. Invent machines, UI patterns, prop animations, belt flourishes, buy/craft paths, teach moments — additive, performant, no LOCKED systems.

---

## Twin pillars (equal weight — do not sacrifice one for the other)

### A) Newcomer clarity (Robb’s friend playtest)

A buddy who never played Satisfactory / factory Minecraft hopped on and **barely understood the loop**. Cool + impressive ≠ teachable.

You must make the system **self-explaining**:

- **One obvious next action** on first arrive (sign / lectern / guide tile / Build highlight — TalkUx *shell* LOCKED; content/lectern OK).
- **Physical language > menu essays:** quarry spout, glowing in/out faces, belt direction you can read from the ground.
- **One job per screen.** Words on buttons = words in the world. No ghost terms.
- Progressive disclosure: simple default → depth one click deeper.
- Status at a glance: running / starved / full / needs a belt — readable without lore walls.

**Litmus:** a smart non-dev friend pokes for ~2 minutes and can start a tiny chain (quarry → belt → something → storage) without you coaching.

### B) Absurd quality / depth (main-reason-to-play bar)

Polish and spectacle like there is no tomorrow:

- Trailer-grade running machines and belts.
- “One more piece” addiction (split / overflow / sorter — invent the minimum set that creates brain-itch).
- Optional **brutal-expensive coin buy** vs craft/build pride.
- Depth that unlocks *over time* — not a wall of systems on minute one.

Clarity first layer; insanity underneath. Never hide the toy behind jargon.

---

## What already exists (build ON it — do not gut)

Live highlight stack in `AetherionGuilds` (treat as **1.0 foundation**):

- Starter islands, land expand, placeable structures (`StructureService` / `templates/`)
- Quarry minion **sim** (`MinionService` / catch-up) + housing schems
- Logistics: `LogisticsService`, `Belt`, `BeltVisuals`, `CargoVisuals`
- Machines: Mill / Forge / Depot / Storage / Quarry housing etc.
- Menus: `QuarryMenu`, `MachineMenu`, `BuildMenu`, `ProductionMenu`, `StorageMenu`, `LandMenu`, …
- Guild projects (keep / deepen if it serves the fantasy)

**Keep** authoritative sim (catch-up, buffers, belt routing).  
**Elevate** presentation, I/O clarity, teachability, UI, spectacle, “one more tile” loop.

Prior docs (context only; this file wins on conflicts):  
`docs/OPUS_ISLANDS_QUARRY_OVERHAUL_BRIEF.md` · `docs/OPUS_ISLAND_QUARRY_POLISH_PROMPT.md`

---

## Absolute constraints

1. **Additive ship.** Never thin the jar. Don’t wipe island/quarry/bank/logistics data.
2. **Plugin home:** `AetherionGuilds` (+ tiny Items only if craft/buy id needed).
3. **LOCKED elsewhere:** TalkUx shell, ranks / `player-ranks.yml`, Blossom Blade / Gravwell combat, boosters/anvil sockets, ShutdownCountdown. Stop + `LOCKED … CONFLICT`.
4. **Performance is law.** Abstract sim + culled visuals. Caps. Near-player only. No item-entity rivers, no hopper megafactories, no always-on ArmorStand armies.
5. **Fantasy Aetherion** — harbour / mine / forge / mill. Not sci-fi chrome.
6. **Balancing = low priority.** Juicy rates OK. Coin buy = **obscene** prices.
7. Deliver law above beats clever process every time.

---

## THE ELEVATION (do these hard)

### 1) Machines = living props (not mini-houses)

- Compact (~2–5 blocks), instant silhouette.
- **Animated while running** (stones, hammers, quarry arm, rollers, dust/sparks, soft loops) — culled.
- Idle vs active obvious at a glance.
- Larger schems OK as landmarks; **production line = props**.

| Piece | Ports |
|-------|--------|
| **Quarry** | **Output only** (obvious chute / spout) |
| **Mill / Forge / Depot / processors** | **Input + output** (readable faces) |
| **Storage** | Input (+ optional output) |

Fresh player sees *where the belt plugs in* without a menu.  
Migrate old placed pieces so nobody stays on ArmorStand-only looks.

### 2) Belts = another tier of crazy

Push conveyors until they feel real: motion, turns, merges, machine plugs.  
Splitters / filters / overflows if they deepen the toy **and** stay readable.  
Caps + budgets — spectacle without entity spam.

### 3) Depth that creates addiction

- Juicy chains: quarry → belt → process → belt → storage / sink.
- Choices: upgrade vs extend vs land vs expensive buy shortcut.
- **Coin buy everything** optional path — same result as craft/place, shame-free, brutally priced.
- Upgrades that change *feel* (speed, buffer, visual tier).
- Guild: don’t orphan shared projects if you touch them.

### 4) GUI = pretty, obvious, zero confusion

Designed screens, not Bukkit lore essays. Hierarchy: Build / Machines / Quarries / Belts / Storage / Land.  
State readable. Progressive disclosure.

**Copy audit (non-negotiable):**

- Grep every player-facing string in island/logistics/menus.
- Never send players to a label that **doesn’t exist** in the UI.
- “Look in your ledger” with no Ledger = **forbidden forever**. Same for Manager / Codex / Workshop ghosts — rename UI or rewrite copy.

### 5) First-arrive + isle fantasy

- Clean placement ghost (no villager-green spam).
- Subtle local factory sound/FX (toggle if cheap).
- Starter still has **room to build** after essentials + a short line.
- Owner place/break on owned land.
- First-arrive “what next” without a novel.

### 6) “Perfect” bar

- Looks like a trailer.
- Feels like Satisfactory/Factorio dopamine on a fantasy isle.
- UI a non-dev friend understands in minutes.
- You’d boot the network *just* to mess with belts.
- Prefer a **brutal vertical slice of greatness** over a thin coat of everything.

---

## Attack order (reorder only if deliver stays continuous)

1. **Land skeleton files in the real tree first** (even stub classes that compile) — prove deliver path.
2. Prop-scale + animated machines + obvious I/O (quarry out-only) — **on disk**.
3. Belt spectacle + splitter/overflow/sorter if earned — **on disk**.
4. Newcomer teach path (first-arrive + status language) — **on disk**.
5. GUI redesign + full copy audit.
6. Coin buy (mega expensive) alongside craft/place.
7. Starter scale / build rights / placement FX leftovers.
8. Guild juice only if personal line already sings.

After each numbered beat: confirm paths exist under `AetherionGuilds/src/...` in the real worktree.

---

## Done when

- Production = **animated machine props** with clear in/out (quarry = out only) — files in real tree.
- Belts feel like a factory you extend at 1am.
- A friend with **no** factory background can start a tiny chain in ~2 minutes.
- At least one juicy multi-step chain is obvious without a guide.
- Coin buy OR craft/self-build both work; buy is absurdly expensive.
- GUIs make sense; **zero** orphan terms.
- Island still buildable; owner block place/break works.
- Guests would say “wait — this alone is why I’d play here.”
- Compile Guilds if you can; no deploy.
- Final message lists **concrete paths landed** (not vibes).

---

## Starter paste (drop this on Opus — new profile / fresh session)

> Fresh run. Ignore any prior elevation sandbox. Read `docs/OPUS_ISLAND_QUARRY_ELEVATION_PROMPT.md` end-to-end and obey **DELIVER LAW**: edit `AetherionGuilds` **directly** in the mining-eldervale worktree — no `elev`/`elev_base`/`_ship`. Milestone = files on disk.  
> Elevate the existing 1.0 island/quarry/logistics highlight until it is a **main reason to play Aetherion**: Satisfactory + Factorio dopamine on a fantasy isle — living animated machine props (not mini-houses), quarry output-only + other machines in/out, belts another tier crazier, deeper “one more belt” loop, optional brutal-expensive coin buy vs craft/build, GUIs that are intuitive/pretty.  
> **Equal pillar:** newcomer clarity — a Minecraft friend with zero factory experience must understand the loop in minutes (self-explaining I/O, one next action, no ghost copy like “ledger”). Depth over time; teachability on minute one. Huge creative freedom. Additive only. No deploy. Go feral like there is no tomorrow — and **land every beat on disk before the next**.
