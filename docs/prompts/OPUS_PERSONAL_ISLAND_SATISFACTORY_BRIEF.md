# OPUS BRIEF — Personal Island / Factory Overhaul (Satisfactory-grade)
**Owner:** Robb (A3therion)  
**Plugin:** `AetherionGuilds` (personal island + quarry + logistics)  
**Date:** 2026-10-02  
**Mode:** ANALYSIS FIRST. Do not ship a half-rewrite. Propose a brutal, coherent redesign — then implement in ordered passes.

---

## 1) Why this exists (Robb's pain — read twice)

The personal island / factory loop currently **does not hook**. It feels ambitious but unthought. UIs feel cluttered and unclean. A player without prior knowledge understands **nothing**. Robb knows what to do; newcomers don't. That is a design failure, not a “player skill” issue.

Quarries are the one part that already feels cool — keep elevating them to “insanely sexy,” not flattening them.

Robb's north star, said many times:

> **SATISFACTORY-LIKE.** Absolute death-tier awesome. Should feel like it could carry its **own server**.

That means: clarity, addiction loop, one coherent building fantasy, clean UI, custom props everywhere — **not** schematic pastes, not nested workshop menus that feel like homework.

---

## 2) Explicit design targets (non-negotiable direction)

### Hub = Satisfactory Hub
- The **Hub is for upgrades / phases / tiers** — the brain of the factory.
- **Not** a dumping ground of every submenu.
- Visual + UX should read like a factory control hub: one glance → “what can I unlock next?”

### One building tool
- There is **one building item**.
- With it you select / place / rotate / remove machines, belts, storages, etc.
- No: open Workshop → Crafting → pick piece → place → back to menu circus.

### Blueprints / unlocks
- Pieces are **unlocked** (blueprints / research / milestones).
- Then they **cost resources** to place or craft.
- Progression should feel like Satisfactory: unlock → build → automate → unlock bigger.

### Custom props for EVERYTHING
- **No schematics** as the identity of the system.
- Custom prop bodies / displays for machines, belts, storages, hub, quarry — already partly there (`PropBodies`, `BeltVisuals`, `MachineFx`). Push that to 100%. Schematics are not the fantasy.

### Storages + Workshop
- Must be redesigned with the Hub philosophy.
- Storage should be obvious: what goes in, what comes out, how belts connect.
- Workshop/Hub should teach the loop, not hide it behind lectern + five menus.

### GUIs
- **Actually clean.** Sparse. One job per screen. No trash chrome.
- If a screen needs a paragraph of lore to explain itself, the UX failed.

### Quarries
- Keep the cool core.
- Polish until it looks like flagship content: VFX, readability, sound, feedback, “I want another one.”

---

## 3) Current system inventory (what Opus must analyze)

Primary tree:  
`AetherionGuilds/src/main/java/de/aetherion/guilds/`

| Area | Likely files / packages | What it does today (analyze, don't assume) |
|------|-------------------------|-----------------------------------------------|
| Island shell | `island/*`, `IslandMenu`, `IslandGuide`, `HostService`, `LandService` | Personal void island, starter, land unlocks, guide |
| Hub / Workshop | `HubMenu`, workshop lectern flow in `IslandMenu` | “Hub desk” after workshop built |
| Build | `BuildMenu`, `PieceMenu`, `PlacementService`, `StructureService` | Place structures via menus |
| Production | `ProductionMenu`, `MachineMenu`, `LogisticsService` | Machines + recipes |
| Storage | `StorageMenu` | Buffer / withdraw |
| Quarry | `QuarryMenu`, quarry minions / logistics hooks | Mining loop — strongest part |
| Visuals | `logistics/PropBodies`, `BeltVisuals`, `CargoVisuals`, `MachineFx` | Custom props (GOOD direction — expand) |
| Market / bank | `MarketMenu`, `BankMenu` | Coin shortcuts / economy side |
| Goals | `FactoryGoals` | Soft objectives |

**Opus task:** map the *actual player click path* from first island visit → first belt → first quarry → first upgrade. Count menus opened. List every dead-end and every unexplained term.

---

## 4) Analysis questions Opus must answer

1. **Onboarding:** Where does a cold player get stuck in the first 5 minutes? What is never explained in-world?
2. **Click tax:** How many GUI hops to place one machine vs Satisfactory's “select building → click world”?
3. **Hub clarity:** Does HubMenu actually feel like upgrades/phases, or a junk drawer?
4. **Storage:** Is storage a first-class factory citizen or an inventory dump UI?
5. **Schematic debt:** Where do we still lean on schems / vanilla blocks instead of props?
6. **Quarry:** What already slaps? What would make it 2× more addictive (feedback, throughput readability, upgrade ladder)?
7. **Addiction loop:** What is the 30-second dopamine cycle? What is the 30-minute goal? Are both present?
8. **Scope cut:** What can die immediately (menus, items, flows) without losing the fantasy?

---

## 5) Proposed target fantasy (for Opus to stress-test)

```
[Building Tool in hand]
   → radial / hotbar blueprint select (unlocked only)
   → ghost place in world (custom prop)
   → confirm costs resources from island storage/network
   → belts connect visually; cargo moves; quarry feeds storage
[Hub]
   → research / unlock next blueprint tier
   → phase upgrades (power, land, logistics tier)
   → NOT crafting every screw by GUI
```

Player should feel: **“I am growing a factory,”** not **“I am navigating plugin menus.”**

---

## 6) Constraints / do-not-break

- Additive ship rule: do not silently delete live features without replacement that is strictly better and teachable.
- Keep quarry identity; elevate, don't replace with a worse abstraction.
- Custom props path already exists — prefer extending it over schematic pastes.
- No drive-by changes to locked Aetherion systems (TalkUx, signature weapons, ranks, boosters, shutdown countdown).
- Hub server vs MMO island hosting: confirm where islands run today before moving worlds.

---

## 7) Deliverables (analysis pass — before big code)

1. **Brutal audit doc:** current click-path + screenshots-in-words of each GUI's job.
2. **Kill list:** menus/flows to remove or merge.
3. **Satisfactory mapping:** Hub / Building Tool / Blueprint unlock / Resource cost / Prop visuals — one paragraph each.
4. **Pass plan:** P0 onboarding+tool, P1 Hub upgrades, P2 storage/belts clarity, P3 quarry spectacle, P4 polish pass.
5. **Risk list:** what breaks existing islands / player data if we change structure IDs.

**Do not** start a 40-file rewrite until Robb signs the pass plan.

---

## 8) Tone for Opus

Be honest. If something is trash, say trash. Robb already knows. Optimize for **feel** and **clarity**, not feature count. Satisfactory is the reference — not Hypixel SkyBlock island menus, not another chest GUI stack.
