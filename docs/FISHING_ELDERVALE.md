# Fishing — Eldervale expansion

Branch `claude/fishing-eldervale-expansion`. Turns the pasted Fishing Eldervale island
(`/aetherpaste fishing` at −585 90 −649, hub `world`) into the Fishing destination.
Off the isle nothing changes — the harbour tutorial and every other water fish exactly as before.

## Base lineage

This branch = `_wt_items_hotfix` HEAD `fa09a37` (the Farming Eldervale expansion lineage) **plus that
worktree's uncommitted Items hotfix** (Manager CMD 3500 + font title, DevMenu glass rule, Hollow Sun
set), imported as commit `83389c1` so the Items jar built here does not regress the live hotfix.

## Map

Schem `Fishing-Eldervale-Island.schem`: Offset (−215, −132, −215), so schem local (x, y, z) lands at
world **(x − 800, y − 42, z − 864)**. Main lake surface: world **y 89**. Island footprint used by the
isle loops: x −775…−415, z −805…−455 (`fish-isle.footprint`).

Every coordinate in config was measured from the schem (surface scan, water-body fit, standable-spot
search) — nothing is eyeballed:

| Kind | Waters |
|---|---|
| Lake (y 89) | Mirror Reach · South Deep · Lantern Cove · Reedwater · Westwater · Boathouse Reach · Bellwater Cove |
| Highland tarn | Skyfall Tarns (NW falls) · Westcliff Pools · Eastridge Tarns · Tower Pools (lighthouse rock) |
| Fountain | Wishing Fountain (plaza) |

NPCs stand by the four corner buildings: Harbourmaster's Hall (N), Bait Shack at the west boathouse,
Trophy House (E hall), Lakewatcher on the south pier to the lighthouse.

## Systems (AetherionFishing `isle` package unless noted)

| System | What it does | Config |
|---|---|---|
| **The Line** (core loop) | Species is decided at the bite. Commons name themselves; rares arrive as "Something heavy!" with a 1-cell narrower window; legendaries go quiet with a fast marker. **Heat** from the clean streak: Warm 3 / Hot 5 / Boiling 10 / Whirlpool 20 — rarer fish each step, lost on one miss. **Perfect chains** climb a pentatonic scale and re-roll the weight (keep heaviest). **Second chance**: lose a rare/legendary and it circles that water for 40 s. | — |
| **Waters & species** | 12 named waters, 18 species (4 common, 6 uncommon, 5 rare, 1 run-only, 2 legendary). Each lives in specific waters; some only at night / in rain. Walking up to a water names it (discovery title + 25 XP), all 12 → **Waterfinder** 2,500 coins. | `waters.*` |
| **The Shoal** | One boiling patch on the lake with leaping fish. Casts inside: wait ×0.5, +1 lure fish, uncommon ×1.3 / rare ×1.6 / legendary ×2. Fished out after 24 catches or 4 min, then gathers in *another* water. | `shoals.*` |
| **Lake events** | Every 18 min with anglers on, alternating. Bells toll 10 s first. **Silver Run** (2 min): lake bites ×3 faster, herring flood in, one miss forgiven, +2 XP / +8 coins per fish. **The Eldermaw** (3 min): a glowing Elder Guardian circles Mirror Reach or South Deep; every isle catch heaves its line (perfect ×2, Boiling +1, its own water +1); haul it before it dives → everyone who pulled gets coins + XP, strongest arm keeps an Eldermaw Scale. | `lake-events.*` |
| **Angler's Log & Rank** | Per species: count + heaviest. Log points = first catch by rarity + silver/gold/record weight. 10 Angler Ranks (Dock Rat → Master of Eldervale), each **+3 Fish Catch / +2 Fish Speed on the isle, permanent**, plus coins/XP on rank-up. Isle records per species. | — |
| **Trophies & Bait** (sinks) | Rare/legendary fish and any fish ≥ 90 % of its weight range come home as a trophy head; Odile buys them by weight. Tilly turns cod/salmon/kelp/tropical/prismarine/pufferfish/compressed fish (+coins) into bait charges that change what bites; a fish that takes the bait eats a charge even if it gets away. | — |
| **Cast** | Maren (Harbourmaster board), Tilly (Bait Shack), Odile (Angler's Log + trophy sales), Old Finn (shoal + events). Holograms, barks, wayfinder arrows. | `isle-cast.presets`, data `isle-cast.yml` |

Player data: `plugins/AetherionFishing/isle-anglers.yml` (Log, waters, trophies, rank, bait, records).

**Items:** 4 Fishing skills — *Lake Sense* (+20 Catch / +8 Speed, isle only), *Steady Line* (gold
cell two wide, Boiling streaks may survive a miss), *Tall Tales* (rare/legendary bites +50 %, extra
weight roll from ~Lv 40), *Tide Reader* (shoal wait −24 %, wake arrow to the shoal). `FishIsleHook`
(guarded isle check), DEV `FishIsleDevPages`. **Core:** `FishAccess` + `AetherServices.fishing()`.
Base loot, rod XP, Fish Shop and compression are untouched — the isle adds on top.

## DEV menu

`/devmenu` → page 1, slot 43 **Fishing Island** (Millstone moved to page 2, slot 24; still inside
Farming Island too).

- **Teleports** — landing, every NPC spot, every water, the live shoal.
- **NPC Cast** — left anchor · right preset · shift-left go · shift-right remove; *Place Whole Cast at Presets*.
- **Events & The Line** — Silver Run, Eldermaw, stop, shoal near me / elsewhere / clear, heat
  Cold/Warm/Hot/Boiling/Whirlpool, force next bite (rare, Goldscale, Pale Ghost, herring, Wishing Koi).
- **Progression & Skills** — Fishing skills Lv 1–100, Eldervale Angler loadout, rods T1–T5,
  sets shelf, all trophies, Log → every species, rank → V / X, waters all/reset, +16 every bait,
  reset Log, empty tin, profile wipe (shift).
- Hub: reload config, water outlines (20 s), where am I, **Set Landing Here**, open every board.

## Test sheet

1. DEV → Fishing Island → NPC Cast → *Place Whole Cast at Presets*. Walk Teleports; nudge any NPC with its anchor.
2. Hub → *Water Outlines*: rings sit on the water (splash lake, green tarn, wax fountain, flame = shoal spots).
3. Survival, fresh profile, TP *Landing* → welcome line + arrow to Maren. Walk to a lake → "Mirror Reach · Discovered 1/12".
4. Cast into any lake → boss bar shows `Fishing • 8.2s · Mirror Reach`. Bite card names the species (first 15 catches show the bar hint instead). Land it → action bar `On the line. │ Silver Perch 0.84 kg ★ new │ <skill bar>`, "New species!" title.
5. Reel three gold cells in a row → rising plings, `✧3` in the line; weights skew heavier.
6. Events & The Line → Heat → Boiling, cast → wait tag shows `Boiling`; land at ✦10 → Boiling card; at ✦20 isle-wide Whirlpool line.
7. Force *Rare*, miss on purpose → "still circling… 40s"; cast the same water → wait tag `circling 32s`, ~60 % "It's back!".
8. Force *Goldscale Emperor* → `! ! !`, narrow window, fast marker. Land it → legendary broadcast, trophy head, record.
9. *Shoal Near Me* → bubbles + leaping fish; cast inside → `≋ Shoal`, faster bites; 24 catches → "fished out", new water announced.
10. *Start Silver Run* → bells, white bar, fast bites, herring; miss once → "The run forgives one", streak kept; end tally.
11. *Start The Eldermaw* → bells + guardian sound, purple bar, glowing Elder Guardian circling; land fish → `Heave +N`, thrash every 25 %; haul → breach splash, payouts, Eldermaw Scale to top hauler. Let one time out → "dives".
12. Tilly → make Breadcrumb Mix (16 cod) → "on the hook"; wait tag shows it; bites faster; charges drop per bite. Right-click another bait to switch.
13. Land a trophy (or *All Trophies*) → Odile → Sell Trophies → coins; trophy heads cannot be placed.
14. Odile's Log: caught species show count/heaviest/points; unknown show "Lives in … · When …" hints.
15. Progression → *Angler Rank → V* → Maren's card shows rank V and `+15 Fish Catch · +10 Fish Speed` (isle only; `/stats` off-isle shows no bonus).
16. Progression → *Equip Eldervale Angler Loadout* → `/skills fishing` shows the four new skills with effect lines; Lake Sense stats only on the isle; Steady Line shows a 2-wide gold cell; Tide Reader shows the wake arrow while holding a rod.
17. Wishing Fountain: fish it → Bluegills and the Wishing Koi; now and then "A wished-on coin +N".
18. Night + rain (`/time set night`, `/weather rain`) at the Skyfall Tarns → Cloudscale Char more often; force *Pale Ghost* to see the legendary card.

## Deploy (Robbi ships)

Build: `mvn -DskipTests -pl AetherionFishing -am package` → Core, Items, Fishing jars. **Ship all three
together** (Items and Fishing call the new Core `FishAccess`; guarded, but the hub needs it).

- Config: new keys arrive via `copyDefaults`; no live key changes. First boot creates `isle-anglers.yml`
  and (after placing NPCs) `isle-cast.yml`.
- Travel: stand on the landing and run `/hubadmin set fishing_eldervale` (or aim an island pad at it).
  DEV → Fishing Island → *Set Landing Here* moves the isle's own landing.
- If the island was pasted somewhere else, update `fish-isle.footprint`, `waters`, `shoals.spots`,
  `lake-events`, `isle-cast.presets` with the same offset.
