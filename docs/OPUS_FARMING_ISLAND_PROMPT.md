# Opus prompt — Farming Eldervale (LOCAL, go hard)

**Local agent.** Fresh local Opus chat. Paste this whole block, then paste/open the product link + any extra screenshots.

Product / visual reference (bought map — screenshots + page copy are enough for vibe):  
https://orionixservices.com/products/farming-eldervale-island

---

You are a **local** Claude Opus agent. Mission: make **Farming** feel as intentional and juicy as **Combat** (and close the gap toward **Mining**). The Eldervale farming map is already on the live hub world — it must feel alive, useful, and progression-rich, not “break wheat → sell → craft hoe set.”

Creative leash: **wide open**. Skills, mechanics, NPCs, props, schematics, Dev Menu, ambience, quests hooks — escalate. Ship real systems, not a timid polish pass.

## Why Farming is behind (Robbi’s read — verify in code)

Combat is already deep. Mining has a real progression spine forming. **Farming is near-empty:** harvest crops, craft armor/hoe ladder, sell crops. That is not enough. Fix the fantasy and the loop so players *want* to be on Eldervale.

## LIVE server truth (do not trust repo defaults blindly)

On **MMO-R** the dedicated void `aether_farm_island` was **retired 2026-09-20**. Eldervale lives **inside hub `world`**.

Live `plugins/AetherionFarming/config.yml` (authoritative for placement):

| Key | Live value |
|-----|------------|
| `farm-island.enabled` | `false` (void world retired) |
| `farm-island.world` | comment: Hub Farming Island via jump pads + `/farming` `/farm_isle` |
| `farm-isle-footprint` | `world`, **min-x -481 … max-x -107**, **min-z 420 … max-z 782** |
| `island-exit` | `world` **-303.5, 107.0, 483.5** |
| `hub-portal` | `world` **-294.5, 55.0, 340.5** |
| Bird / farm-zone | also older field around **-211.5, 61, 183.5** r=90 (survival farm) — do not confuse with Eldervale footprint |
| Featured crop / cane seed / field seed / scarecrow / bees | enabled on live footprint |

Schematic on disk (FAWE):  
`/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/FastAsyncWorldEdit/schematics/Farming-Eldervale-Island.schem`  
(~350×350 build; product: interiors, crop areas, lakes, highly detailed.)

Optional: SSH `aetherion-hetzner` and scan the live footprint (blocks, POIs, empty interiors) if it helps placement. Desktop Crafty mirror is **stale** — prefer Hetzner + schem + product screenshots.

Ops (only if Robbi asks to ship): `C:\Users\Robbi\IdeaProjects\aetherion-ops\bin\ae.ps1` → `deploy-jar mmor` / `restart mmor`. Host `aetherion-hetzner`.

## First read (in order)

1. Product page + screenshots (vibe, density, what the map promises)
2. Live vs repo `AetherionFarming` config — footprint, scarecrow, featured crop, cane, ambience, portal leftovers
3. `AetherionFarming` Java: harvest/regen, bird scare, scarecrow, island/portal packages, ambience
4. `AetherionItems` farming gear: `FarmingItems`, `FarmingHoeProgress`, Dev Menu `Page.FARMING` (**gear only today**)
5. **Benchmark progression** (read enough to feel the bar, do not rewrite them):
   - Combat skill / sets / loops
   - Mining skill / veins / progression
6. `DevMenu.java` / `DevMenuListener` — how ROOT categories and subpages work
7. Quest NPC patterns (AetherionQuests) if you add farm NPCs — English only, fit Aetherion tone

## Bar to hit

Farming on Eldervale should feel **mega**. Players leave thinking the island is a destination: work, events, people, places, rewards, skill growth — not a wheat AFK pad.

### Mandatory outcomes

1. **Honest audit (chat)**  
   - What Combat/Mining give that Farming lacks  
   - What Farming already has (gear T1–T5, regen crops, events, footprint systems)  
   - What is empty / fake / underused on the pasted island (dead interiors, no NPCs, no reasons to walk the map)

2. **Progression overhaul (code)**  
   Touch skills freely: add, remove, retune, new mechanics, island-only bonuses, tool identity, set bonuses, sell sinks that aren’t braindead. Keep harvest satisfying. Do not leave Farming as “crop break + shop.”

3. **Island life (code + content hooks)**  
   NPCs (quest / flavor / vendors / teachers), props, schematic buildings, POIs, ambience — whatever makes Eldervale feel finished. Use FAWE schem library on the server (`ae_*.schem`, market stall, well, shrines, etc.) when it fits. Paste carefully inside the footprint; don’t trash hub outside the isle.

4. **Dev Menu: new category `Farming Island`**  
   Separate from existing `Page.FARMING` gear page. ROOT → submenu ideas (adapt freely):
   - Teleport / footprint tools / reload farming config  
   - NPCs (give/spawn farm cast)  
   - Props / schematics  
   - Skills / progression cheats (set level, give hoe tiers, trigger events)  
   - Ambience / featured crop / scarecrow test buttons  
   Match existing Dev Menu patterns (`Page` enum, `page:…`, `DevMenu.canUse`).

5. **English only** for player-facing text.

## Locked — do not touch

Boosters, Borderlands vials, Custom Anvil sockets, ranks/Admin UUID rules, Blossom Blade / Gravwell Cleaver combat, ShutdownCountdown, Helios/World Eater showcase bosses (unless a one-line Dev Menu link is harmless).

## Scope discipline

- Prefer `AetherionFarming` + Dev Menu in `AetherionItems` + Quests only if NPCs need it  
- Core only for shared keys/APIs that already belong there  
- Branch/worktree clean of unrelated WIP  
- Deploy/restart only when Robbi asks

## Deliverable

1. Brutal audit vs Combat/Mining  
2. Implemented Farming progression + island life + Dev Menu hub  
3. Test sheet: how to reach the isle, `/devmenu` paths, new skills/events, what to click on the map  
4. Note any live config keys you changed so MMO-R can be updated without wiping the footprint

When choosing between “safe tiny polish” and “this finally makes Farming feel like a real Aetherion skill on a real island,” **choose the latter** and ship it.
