# Codex + Skills overhaul — Collection, Bestiary, Skills

Items-only pass (AetherionItems jar). Base: the live lineage (`mining-eldervale-progression-65660c` working tree,
byte-identical to the live Items jar on 2026-09-29), imported as commit `d5c85c2`.

## 0. What was there (Gate 0 inventory)

| System | Live state | Thin spots / gaps |
|---|---|---|
| Collection | `CollectionGUI` → one paged list per category, count + top 3 per block. Fed by `BlockBreakEvent` (MONITOR) and Foraging's `codexWood`. | No tiers, no goals, no rewards. Seedlings counted as harvests. Leaves counted as Oak. Stems counted as melons. Place-and-break farmed it. **Fishing never counted.** **Vein Siphon vacuum never counted.** No command. |
| Bestiary | `BestiaryGUI` → same list for kills. | **Bosses tab always 0**: boss kills live in a separate `bosses` map the tab never read, and its roster was stale (Squidward, Lobby Cleaner…) vs the real BossEngine list (Hollow Sun, Hanging Saint, World Eater, Helios…). Sea creatures filed as plain Drowned/Guardian. Ore Trolls as Iron Golem-less "unknown". Borderlands Sturdy/Brute/Crypt invisible. New dungeon mobs silently dropped. |
| Dungeon Journal | Boss templates + loot, kills. | Showed `test_*` rigs; loot visible before you ever met the boss. |
| Skills | Polished loadout locker (`SkillMenu`), 60 skills, rarity every 20 levels, level beats. | No way to see every skill at once, no loadout presets, rarity-ups had no reward beyond the title, no session read-out, no cross-link to the Codex. |

Live data (MMO-R, 31 players): wheat 19.7k, coal 5.9k, diamond 3.6k… **wood 0** (fixed by the Foraging ship this morning), **catches 0**, boss kills 150 across 13 bosses (never shown in the Bestiary).

## 1. Design

### Shared chrome (every Codex page + the Skill Overview)
```
row 0  [Codex][Collection][Bestiary][Journal][ HEADER ][Skills][Milestones][tool][tool]
row 5  [Back][ ][Prev][Sort][Close][Filter][Next][ ][ ]
```
Tabs gate on the same progression flags as the Manager (locked tabs show the hint). Pages re-render in place.

### Ladders
Every entry climbs nine tiers, I–IX. The scale sets the rungs:

| Scale | I … IX | Used for |
|---|---|---|
| Bulk | 100 … 250,000 | stone, deepslate, dirt, sand, wheat, carrot, potato, wart, cane, melon, bamboo, netherrack |
| Common | 50 … 100,000 | coal, iron, copper, lapis, redstone, logs, most blocks, cod, junk |
| Uncommon | 25 … 50,000 | gold, diamond, emerald, clay, obsidian, salmon… |
| Rare | 10 … 25,000 | ancient debris, amethyst, sculk, tropical fish, pufferfish |
| Precious | 5 … 6,000 | sponge, treasure |
| Mob common / tough / rare | 1 … 2,500 / 1,000 / 100 | Borderlands, Nether, End, dungeon floors |
| Wildlife | 1 … 500 | animals, water mobs |
| Sea creature | 1 … 500 | the ten fishing encounter bands |
| Boss | 1, 2, 3, 5, 10, 15, 25, 50, 100 | every live BossEngine boss |

**Level** = tiers reached across a ledger. **Milestone** = every 10 levels → permanent perk:
Collection **+1 Fortune**, Bestiary **+1 Damage +2 Health** (`codex-perks: false` in Items `config.yml` switches perks off).

### Claim loop
Reaching a tier makes it *claimable* (chat line with **[CLAIM]** / **[view]**, gold chest on the ladder, glow on the card).
Claiming pays coins (count toward lifetime → skill slots), Aetherion XP, and Shards on tiers V/VII/IX.
Collection tier t pays `150·w·t²` coins + `20·t` XP; Bestiary `100·w·t²` + `15·t`; bosses `1,500·t` + `40·t` XP + `5·t` Shards.
Rarity seals (skills): Uncommon 2.5k/10 Shards … Mythic 62.5k/400 Shards.
Everything is retroactive — existing players log in to back pay ("✦ Codex » N rewards waiting").

### Data (`codex.yml`, backward compatible)
Existing `kills` / `blocks` / `bosses` untouched. New per player: `variants` (`ZOMBIE@brute`), `claims` (claim key → tier),
`found` (claim key → epoch day). Presets live in `skills.yml` (`players.<id>.presets`).
Claim keys: `c:<collection>`, `b:<mob>`, `b:boss:<template>`, `s:<skill>`.

## 2. Features

**Collection** (`/collection [category]`, Manager tile)
- 76 entries on 8 shelves + *Everything*: Ores (now incl. raw/mineral blocks the mines use), Stone (+ sandstone), Wood (+ giant mushrooms, stripped wood), Dirt & Sand (+ mud), Crops (+ cactus, mushrooms), **Catches (new)**, Nether Blocks, Oddities (+ chorus).
- Ripe crops only; stems and leaves no longer count; blocks you placed yourself don't count (session memory, 200k).
- Fishing catches (Cod / Salmon / Tropical / Puffer / Treasure / Junk) from every landed fish, lake included.
- Vein Siphon vacuum now credits the ledger.

**Bestiary** (`/bestiary [category]`, Manager tile)
- 9 shelves: Everything, Borderlands, Nether, End, Wildlife, Waters, **Sea Creatures (new, 10)**, Dungeon, Bosses.
- **Bosses tab is live**: every BossEngine template (no `test_*`), kills from the real boss ledger, everyone who dealt damage credited.
- Borderlands **Sturdy / Brute / Crypt** variants tallied per mob; **Ore Troll** entry; unknown dungeon mobs get a card the first time you meet them.

**Both ledgers**
- Cards: tier colour, 20-cell bar, count/next, ladder pips, next reward, server rank, claim state. Undiscovered = `???` + where-to-find hint.
- Sort (catalog, closest to next tier, highest tier, most, A–Z) and filter (all, found, missing, ready to claim, not maxed), remembered per session.
- Detail page: nine-rung ladder (click to claim), your record (rank, first-entry date, variants), top-10 board, field notes (boss loot **unlocks on first kill**), reward totals, ◄ ► to walk the list (skips `???`).
- Header: level, milestone bar, found/maxed, perks held; Claim-ledger button; "How it works".

**Codex hub** (`/codex`) — profile + Codex score, Collection / Bestiary / Skills / Journal / Milestones tiles, **Claim All**, and a *Next up* row of the five entries closest to a tier.
**Milestones** — Collection road, Bestiary road (done / next with bar / future), rarity-seal counts with Claim.
**Boss Journal** — chrome, All / World / Dungeon shelves, slain-first order, loot previews for slain bosses only.

**Skills**
- `SkillMenu` header row: Codex · **Overview** · Readout · **Presets** · Profile · **Rarity Seals** · Next slot · **Session** · Guide. Pool cards show seal pips; Shift-click claims a ready seal.
- **Skill Overview** (`/skills overview`): all 60 skills, sort (level / category / closest to next rarity / A–Z), filter (all / equipped / seal ready / trained / untouched), right-click to equip/unequip, click to claim or jump to the skill's page.
- **Loadout Presets** (`/skills presets`, `/skills preset <1-3>`): save / load / clear; preset 2 at Aetherion Lv 10, preset 3 at Lv 25. Levels never move.
- **Rarity seals**: one-off reward per skill per rarity; `[CLAIM RARITY SEAL]` button on every rarity-up.
- **This Session**: XP earned, level-ups, XP/h, top skill.

**Glue** — Manager tiles show `Level N · ✦ K to claim`; placeholders `%aetherion_collection_level%`, `_bestiary_level`, `_codex_score`, `_codex_claimable`.

**Admin** (`aetherion.dev`): `/codex dev <player> set <key> <n>` (raw count, no notices), `resetclaims`, `info`.

## 3. Not touched
AetherionQuests (LivingNpc/TalkUx/etc.), Anvil BoosterSockets GUI, ranks, Blossom Blade / Gravwell Cleaver, ShutdownCountdown,
Borderlands vials, DevMenu tiles, Foraging/Mining isle loops (they keep calling the unchanged `CodexService` API).

## 4. In-game test sheet

Setup: survival, a test account, `/codex dev <you> info` before and after.

| # | Do | Expect |
|---|---|---|
| 1 | Join as an existing player with history | 8 s later: `✦ Codex » N rewards waiting [OPEN CODEX]` |
| 2 | `/codex` | Hub: profile head, three tiles, Journal, Milestones, Claim All (glowing if N>0), Next up row |
| 3 | Click Claim All | One summary line; coins/Shards/XP land; level-up lines print after it; tiles stop glowing |
| 4 | `/collection` | Ores shelf; cards with tier/bar/rank; `???` for unfound with hints |
| 5 | Sort → Closest to next tier; Filter → Ready to claim; reopen | Order/filter change; remembered on reopen |
| 6 | Click Everything tab, page with ◄ ► | 76 entries over 4 pages |
| 7 | Click a card | Ladder I–IX coloured green/chest/yellow/red, record, board, notes |
| 8 | ◄ ► on the detail page | Walks the list, skips `???` |
| 9 | Mine 50 coal ore | Chat: `COLLECTION » Coal reached Tier I [CLAIM] [view]`; click CLAIM → paid, card updates |
| 10 | Place stone, break it | Stone count unchanged |
| 11 | Break a half-grown wheat, then a ripe one | Only the ripe one counts |
| 12 | Catch a fish | Catches → Cod (or Treasure/Junk) +1; first catch shows the Collection toast if new |
| 13 | Vein Siphon an ore | That ore's count goes up |
| 14 | `/bestiary bosses` | Real bosses (Hollow Sun, Hanging Saint, World Eater…); no `test_*`; kills match `codex.yml bosses` |
| 15 | Open a boss you never killed | Field notes: "Loot table unlocks on your first kill." |
| 16 | Kill a Borderlands Brute zombie | Zombie card shows `Variants … Brute 1` |
| 17 | Kill a fishing encounter | Sea Creatures → its band +1 (not Drowned) |
| 18 | `/codex dev <you> set c:stone 99` then mine 1 stone | Tier I line; at level 10 overall: milestone title + `+1 Fortune`; the fortune placeholder / stats page +1 |
| 19 | Bestiary milestone | `+1 Damage · +2 Health`, max HP refreshes |
| 20 | `/codex milestones` | Roads with done/next/future, perks held, seal counts |
| 21 | `/skills` | Header row: Codex, Overview, Readout, Presets, Profile, Seals, Next slot, Session, Guide |
| 22 | `/skills setlevel <you> heavy_hands 20` | Rarity headline + `[CLAIM RARITY SEAL]`; claim → 2,500 coins + 10 Shards |
| 23 | Shift-click a pool skill with a ready seal | Claims instead of equipping |
| 24 | `/skills overview` | 60 skills, sort/filter, right-click equips/unequips, click with ready seal claims |
| 25 | `/skills presets`: Shift-click Preset 1, unequip all, click Preset 1 | Loadout restored; Preset 2/3 locked below Lv 10/25 |
| 26 | `/skills preset 1` in chat | Same load from chat |
| 27 | Session tile after some grinding | XP, level-ups, XP/h, top skill |
| 28 | Manager (Nether Star) | Bestiary/Collection tiles show `Level N · ✦ K to claim`; clicking opens the new pages; Back returns to Manager |
| 29 | `/codex dev <you> resetclaims` | Everything claimable again (counts kept) |
| 30 | Restart | `codex.yml` keeps kills/blocks/bosses + new variants/claims/found; presets in `skills.yml` |
