# Opus — Island / Quarry / Logistics ELEVATION
## Go absolutely feral. This is the highlight that makes people play Aetherion.

**Hygiene:** Edit `AetherionGuilds` source **directly** (± tiny Items only if a craft/buy id is unavoidable).  
Code + schems/templates + menus + copy.  
**No** `_ship/`, Manifest, Apply-Script, README novel, TEST_SHEET.  
One short chat summary when done. **Do not deploy** (Robb/Cursor ships).

Prior briefs still true for foundations:  
`docs/OPUS_ISLANDS_QUARRY_OVERHAUL_BRIEF.md` · `docs/OPUS_ISLAND_QUARRY_POLISH_PROMPT.md`  

This pass **supersedes** “small polish”. The skeleton is already strong. Your job is to make it **addictive, intuitive, and visually insane** — the kind of loop people open the game *just* to tinker with their island.

---

## North star (tattoo this)

> **Satisfactory energy + Factorio dopamine**, filtered through Aetherion fantasy islands.  
> Not a spreadsheet. Not a fridge-magnet of Upgrade buttons.  
> A living little factory you *see*, *hear*, *extend*, and *obsess over*.

Emotional hits players must feel:

- “I need one more belt.”
- “If I just split this line…”
- “That quarry looks sick when it’s running.”
- “I could buy it… or I grind the craft — either way I’m building.”
- “I get this UI without a wiki.”
- “This alone is why I play.”

If a change doesn’t raise **addiction, clarity, or spectacle**, skip it.

You have **huge creative freedom**. Invent machines, UI patterns, prop animations, belt flourishes, buy/craft paths, expand beats — as long as you stay additive, performant, and don’t touch LOCKED systems.

---

## What already exists (build ON it — do not gut)

Live highlight stack in `AetherionGuilds`:

- Starter islands, land expand, placeable structures (`StructureService` / schems under `templates/`)
- Quarry minion **sim** backbone (`MinionService` / catch-up) + housing schems
- Logistics graph: `LogisticsService`, `Belt`, `BeltVisuals`, `CargoVisuals` (plates + conveyor dressing + gliding cargo)
- Machines: Mill / Forge / Depot / Storage / Quarry housing etc.
- Menus: `QuarryMenu`, `MachineMenu`, `BuildMenu`, `ProductionMenu`, `StorageMenu`, `LandMenu`, …
- Guild projects track (keep / deepen if it serves the fantasy)

**Keep** the authoritative sim (catch-up, buffers, belt routing).  
**Elevate** presentation, IO clarity, UI, depth of the toy, and the “one more tile” loop.

---

## Absolute constraints

1. **Additive ship.** Never thin the jar. Don’t wipe island/quarry/bank/logistics data.
2. **Plugin home:** `AetherionGuilds` (Items only for buy/craft ids if needed).
3. **LOCKED elsewhere:** TalkUx shell, ranks / `player-ranks.yml`, Blossom Blade / Gravwell combat, boosters/anvil sockets, ShutdownCountdown. Stop + say `LOCKED … CONFLICT` if you must touch them.
4. **Performance is law.** Abstract sim + culled visuals. Caps. Near-player only. **No** item-entity rivers, no hopper megafactories, no always-on ArmorStand armies.
5. **Fantasy Aetherion** — harbour / mine / forge / mill vibe. Not sci-fi chrome, not vanilla industrial clutter cosplay.
6. **Balancing = low priority.** Juicy rates OK. Coin buy path must feel **brutally expensive**, not “fair”.
7. Quota empty → wait → continue. When done: stop.

---

## THE ELEVATION (do these hard)

### 1) Machines = living props (not mini-houses)

Production pieces must read as **Satisfactory-scale machines**:

- Compact footprint (~2–5 blocks typical), instantly recognizable silhouette.
- **Animated while running:** spinning millstones, pounding forge, quarry head/arm, rollers, dust/sparks, soft loops — BlockDisplay / ItemDisplay / cheap particles OK if culled.
- Idle vs active must be obvious at a glance.
- Cool larger schems can remain as **landmark / housing / storage buildings** if the island still has build room — but the *production line itself* is props.

**I/O ports (mandatory clarity):**

| Piece | Ports |
|-------|--------|
| **Quarry** | **Output only** (obvious chute / spout where belts attach) |
| **Mill / Forge / Depot / processors** | **Input + output** (readable faces / marked sides) |
| **Storage** | Input (and optional output if you want pull lines) |

A fresh player should see *where the belt plugs in* without opening a menu.

Migrate existing placed quarries / machines so nobody is stuck on old ArmorStand-only looks.

### 2) Belts = another tier of crazy

Current conveyor plates + `BeltVisuals` are the base. Push them until they feel like **real conveyors**:

- Motion you feel: chevrons / rollers / cargo glide that sell throughput.
- Turns, merges, ends, and machine plug-ins must look intentional (not “rail cosplay”, not a flat iron sidewalk).
- Splitters / filters / overflows — **add if they deepen the toy** and stay readable (you invent the minimum set that creates Factorio brain-itch).
- Still: tile caps, run merges, entity budgets — go hard on *look*, not on entity spam.

Goal: someone screenshots a line and it looks like a factory trailer.

### 3) Depth that creates addiction

Elevate the **loop**, not the wiki:

- Longer satisfying chains: quarry → belt → process → belt → storage / craft sink.
- Meaningful choices: upgrade machine vs extend line vs buy land vs buy the expensive shortcut.
- Optional **coin buy everything** path: every structure/machine/belt kit (or equivalent) purchasable for **obscene** coin prices — same end result as crafting/placing yourself. Grind pride vs whale shortcut. No shame either way.
- Upgrades that change *feel* (speed, buffer, visual tier), not just a number in a chest.
- Guild side: shared projects / shared lines should still feel like “we built that” if you touch them — don’t orphan guild.

Invent freely: under/over belts, vertical drops, sorter props, overflow dump, status lamps — if it’s readable and cheap, it’s in scope.

### 4) GUI = intuitive, pretty, zero confusion

Menus must feel **designed**, not “Bukkit chest with lore essays”.

- One job per screen. Hierarchy obvious (Build / Machines / Quarries / Belts / Storage / Land).
- State at a glance: running / starved / full / disconnected / needs belt.
- Actions labeled with words that **exist in the UI** (buttons, titles, item names).
- Progressive disclosure: simple default, depth one click deeper — not a wall of stats on open.

**Copy audit (non-negotiable):**

- Grep every player-facing string in Guilds island/logistics/menus.
- **Never** tell the player to open / check / look at something that **isn’t named that** in the UI.
- Real ragebait example: “look in your ledger” when **nowhere** says Ledger → **forbidden forever**.
- Same for: Manager, Codex, Ledger, Workshop, Depot, etc. — either rename the UI to match the copy, or rewrite the copy to match the UI. No ghosts.

If a friend can’t learn the loop in ~2 minutes of poking, the UI failed.

### 5) Visual polish pass (whole isle fantasy)

- Placement ghost: clean valid/invalid (no villager-green scout spam, no supernova).
- Running factory sound/particle taste (subtle, local, toggleable if cheap).
- Starter island still leaves **room to build** after hut + workshop + a short line.
- Owner can place/break blocks on owned land (fix if broken).
- First-arrive clarity: what to do next without a novel (sign / lectern / short guide OK; TalkUx shell locked).

### 6) “Perfect” bar

Robb’s bar is not “better than yesterday”. It’s:

- Looks like a trailer.
- Feels like Satisfactory/Factorio dopamine on a fantasy island.
- UI a non-dev friend understands.
- You’d boot the server *just* to mess with belts.

Ship the strongest elevation you can in this run. Prefer a **brutal vertical slice of greatness** over a thin layer of everything.

---

## Suggested attack order (you may reorder)

1. Prop-scale + animated machines + obvious I/O (quarry out-only).
2. Belt spectacle + any missing “one more piece” toys (splitter etc. if earned).
3. GUI redesign + full copy audit (kill ledger-class bugs).
4. Coin buy (mega expensive) alongside craft/place.
5. Starter scale / build rights / placement FX leftovers.
6. Guild project juice only if personal line already sings.

---

## Done when

- Production reads as **animated machine props** with clear in/out (quarry = out only).
- Belts look and feel like a factory you want to extend at 1am.
- At least one juicy multi-step chain is obvious without a guide.
- Coin buy OR craft/self-build both work; buy is absurdly expensive.
- GUIs make sense; **zero** orphan terms (no “ledger” without a Ledger).
- Island still buildable; owner block place/break works.
- Guests/friends would say “wait this is actually sick.”
- Compile Guilds if you can; else one-line note. No deploy.

---

## Starter paste (drop this on Opus)

> Read `docs/OPUS_ISLAND_QUARRY_ELEVATION_PROMPT.md` end-to-end.  
> The island/quarry/logistics highlight is already strong — **elevate it like never before**. Target: Satisfactory + Factorio addiction on an Aetherion fantasy isle — living animated machine props (not mini-houses), quarry output-only + other machines in/out for conveyors, belts another tier crazier, deeper “one more belt” loop, optional brutal-expensive coin buy vs craft/build yourself, GUIs that are intuitive/pretty, and a ruthless copy audit (never say “ledger” or any label that doesn’t exist in the UI). Huge creative freedom. Additive only. Edit `AetherionGuilds` directly. No ship packaging. No deploy. Go feral.
