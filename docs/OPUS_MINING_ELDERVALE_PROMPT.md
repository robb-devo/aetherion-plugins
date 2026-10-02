# Opus prompt — Mining Eldervale (LOCAL, go harder than Farming)

**Local agent.** Fresh Opus chat. Paste this whole block. Open the product page for vibe + screenshots.

Product / visual reference (bought map):  
https://orionixservices.com/products/mining-eldervale-island

Specs from OrioniX: **~400×400**, interiors, **huge underground mine**, fishing lake, exploration zones, World + Schem, MC 1.21+.

---

You are a **local** Claude Opus agent. Mission: turn **Mining** into the **flagship gather destination** — denser, juicier, and more intentional than the Eldervale **Farming** expansion that just shipped. Farming is already crazy good (rhythm, prizes, mastery, plots, events, orders, bakehouse, cast, DEV hub). Your bar: make that Farming pass look like a **cheap beginner plugin** next to what Mining becomes. Robbi’s words — treat them as the quality target, not a dunk on Farming.

Creative leash: **wide open inside Mining.** Skills, mechanics, NPCs, props, collections, Dev Menu, ambience, The Veins integration, ore fantasy — escalate. Ship **real systems**. Prefer Aetherion tone (English-only player text, readable spectacle, no particle soup).

## Hard delivery rules (read twice)

1. **Base = current live-aligned tip.** Do **not** mix stale worktrees or invent coords that fight live paste. Preferred checkout lineage:
   - Start from the tree that already has **Farming Eldervale + Helios gear + live Mining** compiling together  
     (practical: `_wt_items_hotfix` / `claude/items-manager-devmenu-hotfix` tip, **or** merge/cherry equivalent of live Core+Items+Farming+Mining jars Robbi has on MMO-R).  
   - Branch name suggestion: `claude/mining-eldervale-expansion`
2. **Do NOT deploy to Hetzner / MMO-R yourself.** No `ae.ps1`, no SSH ship, no Crafty restart. Opus Hetzner deploys have been flaky — Robbi (or Auto) will ship. Your job ends at: **compiling jars + clear ship notes**.
3. **Ship-ready package** so Auto only uploads + restarts:
   - Built jars: **AetherionCore** (only if API grew), **AetherionItems**, **AetherionMining** (and **only** those that actually changed — Prefer shipping Core+Items+Mining together if FarmAccess-style API grew for mining).
   - `docs/MINING_ELDERVALE.md` (systems table, config, test sheet, deploy bullets) — mirror `docs/FARMING_ELDERVALE.md` quality.
   - Optional: `ship-mining-eldervale.ps1` that only documents checksums / jar paths (no remote deploy), or a short `SHIP.md`.
4. **Full Mining fokus.** Do not “while you’re here” rewrite Farming, Foraging, Helios, BossEngine combat, Quests story, Hub layout outside mining pads, or Menus for unrelated skills.
5. **Small conflicts only:** if Mining Isle needs a thin Core API or Dev Menu root slot, adapt carefully (Farming used `FarmAccess` defaults + Farming Island slot 42). Do not break Farming Island DEV hub or Manager CMD/font fixes.

## Why Mining feels thin (Robbi’s read — verify in code)

Mining has a spine forming (seal-regen, T1–T5 picks, Mining Power gates, Veins, Vein Siphon, Ore Troll, Codex ores) — but vs Farming Eldervale it still feels like **break ore → wait → craft pick**. The pasted Mining Eldervale island is mostly **map + pads + Veins**, not a destination OS. Farming now has: Harvest Rhythm, Prize Crops, Crop Mastery, 15 plots + compass, Bee Bloom / Harvest Moon, Harvest Orders, Oven House, 8-NPC cast, five isle skills, full DEV hub. **Mining needs that class of systems — and then go further.**

## LIVE / product truth (do not invent placement)

| Fact | Value |
|------|--------|
| Schem name | `Mining-Eldervale-Island.schem` |
| Paste command | `/deepmines eldervale paste` (admin) — **not** `/aetherpaste` (farm/fish only) |
| Default paste origin (repo config) | **world `world` @ 22 90 580** (`AetherionMining` `eldervale.paste`) |
| Hub spawn | id `eldervale` — “Mining island past the slime jump pad”, discover r=36 |
| Jump pads (Hub `island-pads`) | Origin→Mining pad ~**20–24, y54, z304–306** → **53.5 91 482.5** (stamped blueprint gated); return pad ~**52–55, y89, z481–483** → **22.5 56 305.5** |
| Schem on server (expect) | FAWE/WorldEdit schematics dirs under MMO-R plugins — **file may not be in git** |
| Product | OrioniX Mining Eldervale Island — interiors, huge underground mine, fishing lake, exploration zones, ~400×400 |
| The Veins | separate void `aether_veins`, `/deepmines`, Foreman — **keep and elevate**, don’t delete |

Measure a soft **mine-isle-footprint** AABB from the live paste / schem (like Farming’s −481…−107 / 420…782). Put it in config. All new isle loops **only** run inside that footprint (plus explicit Veins hooks if you intentionally bridge).

Desktop Crafty is stale. Prefer schem + product screenshots + repo config. Optional SSH read-only scan of live footprint is OK for POI placement; **no jar deploy**.

## First read (in order)

1. This prompt + OrioniX product page / screenshots  
2. `docs/FARMING_ELDERVALE.md` + `AetherionFarming/.../isle/*` — **architecture to mirror-escalate**  
3. `AetherionMining`: `MiningListener`, `MiningBlocks`, `MiningRespawnTimes`, `island/EldervalePaste`, `veins/*`, `config.yml`, `README.md`  
4. `AetherionItems`: `HarvestListener`, `mining/HarvestRules`, `BalanceTargets` (MINING_*), `CustomItem` mining factories, `VeinSiphonListener`, `EmeraldSpreadListener`, `OreTrollListener`, `CompressedResource` (mining keys), `skill/AetherSkill` + `SkillService` mining XP, `codex/CodexCatalog` ores/stone  
5. `AetherionHub`: `IslandLaunchPads`, Eldervale spawn, **do not** break farm/fish `/aetherpaste`  
6. `DevMenu` — Farming Island pages (`FarmIsleDevPages`) as the template for a **Mining Island** hub  
7. `docs/OWNERSHIP.md` — Mining owns world loops; Items owns pick stats / XP / compact  

## Architecture (do not fight ownership)

| Layer | Owns |
|-------|------|
| **AetherionMining** | World loops on Eldervale footprint + Veins: rhythm/events/plots/NPCs/orders/props/ambience, seal-regen stays here |
| **AetherionItems** | Skills, gear, Fortune/MP, Codex/collections hooks, thin `MineIsleHook` / `MiningAccess` calls (guarded like `FarmIsleHook`) |
| **AetherionCore** | Only if needed: default methods on `MiningAccess` (mirror `FarmAccess` growth) — **no** combat formulas, no tick OS in Core |
| **AetherionHub** | Pads / spawn unlock only if coords or unlock UX must change |
| **AetherionQuests** | Optional English NPC boards / light hooks — don’t rewrite harbour tutorial |

## Bar to hit (mandatory outcomes)

### 1) Honest audit (chat, short)
- What Farming Eldervale gives that Mining lacks  
- What Mining already has (list honestly)  
- What’s empty on the pasted island (dead interiors, unused mine levels, no reasons to walk)

### 2) Destination OS (code) — mirror Farming, then exceed it
Build a Mining `isle`-grade package (name freely: `mineisle`, `eldervale`, …) with at least this **class** of systems (names yours):

| Farming analogue | Mining direction (escalate) |
|------------------|-----------------------------|
| Harvest Rhythm | Swing / streak meter for ore breaks — audio + boss bar, ore-only Fortune/MP (never crop/fish leak) |
| Prize Crops | Rare **Prize Ores / Crystal Finds** (giant glowing node, finder's dibs, sell/orders/records) |
| Crop Mastery | **Per-ore mastery** ledger (isle weight ×2+), permanent ore Fortune / MP crumbs |
| 15 plots + compass | Named **districts / shafts / lakes / forge halls** from the schem — discovery titles + wayfinder |
| Bee Bloom / Harvest Moon | Timed isle events (e.g. Rich Vein, Cave Surge, Ember Hour) — telegraphed, shared bar |
| Harvest Orders | Foreman / clerk **contracts** (raw / compressed / prize) paid above trader |
| Oven House | Forge / smelter / “miner’s kitchen” — **mining-only** timed buffs (MP, Fortune, rhythm, prize odds) |
| 8-NPC cast | Field cast with holograms, barks, boards, door presets, non-persistent rebuild on chunk load |
| 5 isle skills | New Mining skills (isle-gated where it fits) — identity, not clone of Soil Sense |
| DEV hub | `/devmenu` → **Mining Island** (teleports, cast, props, events, progression) — keep `Page.MINING` as **gear** |

**Go further than Farming where it fits Mining fantasy**, e.g.:
- Deeper **underground route** fantasy (shaft tiers, depth bonuses, Cave Sense synergy)  
- **The Veins** as endgame annex (orders that send you down, shared mastery, don’t flatten Veins into the overworld isle)  
- **Vein Siphon / Emerald Spread / Ore Troll** woven into events or contracts (don’t delete; elevate)  
- **Collections / Codex**: make ore collection feel *juicy* — milestones, isle board, titles, or Foreman ledger that surfaces Codex progress (no fake stats). Cooler UX, not a second spreadsheet players ignore.

### 3) Progression honesty
- Keep Mining Power gates meaningful; retune only with clear reason  
- T1–T5 pick + armor ladder stays craftable; names/fantasy can be upgraded if it sings  
- Compact / compacted ore sink stays; optional refining loop **only if** it doesn’t clone Millstone poorly  
- English-only text

### 4) Map respect
- Work **inside** the Mining Eldervale footprint + intentional Veins bridges  
- Don’t trash Hub Origin, Farm Isle footprint, or fishing paste  
- NPCs/props: DEV placeable + preset spots measured from schem (Farming `isle-cast.presets` pattern)

## Locked / do not touch

- Boosters (lore, stack 64, IDs/stats)  
- Borderlands vials / rites  
- Custom Anvil / Booster sockets  
- Ranks / Admin UUID / `player-ranks.yml`  
- Blossom Blade / Gravwell Cleaver combat  
- Shutdown countdown  
- Farming Eldervale systems (don’t regress)  
- Helios Requiem Pass 2 / BossEngine combat feel  
- Manager item CMD **3500** + font title `\uE005\uE004`  
- DevMenu glass rule: **one glass ring** — content only in inner columns (slots like 10–16), never absolute edge 0/8/17/44  

## Ops context (for your SHIP notes only — you do not run these)

| Fact | Value |
|------|--------|
| SSH | `ssh aetherion-hetzner` → `root@135.181.18.162` |
| Ops | `C:\Users\Robbi\IdeaProjects\aetherion-ops\bin\ae.ps1` |
| MMO-R UUID | `a28d676a-03ef-40f1-9ac7-7a21c2ef6383` |
| Plugins | `/var/opt/minecraft/crafty/servers/<uuid>/plugins/` |
| Jar names | `AetherionMining-1.0.0.jar`, `AetherionItems-1.0.0.jar`, `AetherionCore-1.0.0.jar` |
| Restart | graceful `ae-stop.sh mmor` then `ae-start.sh mmor --wait` (LOCKED countdown) |

Write deploy steps for Robbi; **do not execute remote ship**.

## Key file map (start here)

```
AetherionMining/
  MiningListener.java, MiningBlocks.java, MiningRespawnTimes.java
  island/EldervalePaste.java
  veins/* , VeinsCommand.java
  config.yml          # eldervale.paste 22 90 580, veins.*, mine.* cleared
AetherionItems/
  listener/HarvestListener.java, mining/HarvestRules.java
  item/BalanceTargets.java, CustomItem (mining_*), VeinSiphon*, OreTroll*
  economy/CompressedResource.java
  skill/AetherSkill.java, SkillService.java
  codex/CodexCatalog.java (ores / stone)
  menu/dev/DevMenu.java, FarmIsleDevPages.java  # mirror for Mining Island
AetherionCore/api/MiningAccess.java (+ grow like FarmAccess if needed)
AetherionHub/ island pads + eldervale spawn
docs/FARMING_ELDERVALE.md   # quality + structure template
docs/OWNERSHIP.md
```

## Mining progression cheat sheet (current)

**Skills:** rock_whisper, extra_pocket, pack_rat, spread_sheet, quarry_manners, cave_sense  
**Picks:** `mining_pickaxe` … `_5` (MP/Fort/Spread ladder in `BalanceTargets.MINING_PICK`)  
**Power gates (HarvestRules):** coal 8 → … diamond 80 → emerald 140 → debris 250  
**XP:** stone ~3, ores ~8, dense ~14, diamond/emerald/debris ~18 (`SkillService.miningXp`)  
**Veins:** `/deepmines`, world `aether_veins`, reset 24h  

## Done means

1. Branch with clear commits  
2. `mvn -DskipTests package` for touched modules — green  
3. `docs/MINING_ELDERVALE.md` + test sheet (≥ Farming’s)  
4. DEV → **Mining Island** hub works offline  
5. Chat report: systems A–…, files, left alone, config version/keys, **exact jar paths + checksums**, “Robbi ships with ae.ps1”  
6. **STOP.** No Hetzner deploy. No drive-by Farming/Helios edits.

---

## Robbi one-liner

> Mining Eldervale = Farming Eldervale quality **and then some**. Destination OS on the OrioniX mining island, collections that slap, Veins woven in — ship-ready jars only, **no Hetzner deploy from Opus**. Don’t touch Farming/Helios/locked systems. Make Farming look beginner next to this.
