# Opus — Island / Quarry highlight POLISH (scale + feel + bugs)

> **Superseded for the next Opus run** by  
> [`OPUS_ISLAND_QUARRY_ELEVATION_PROMPT.md`](./OPUS_ISLAND_QUARRY_ELEVATION_PROMPT.md)  
> (brutal Satisfactory/Factorio elevation). Keep this file as the earlier scale/feel bug list.

**Hygiene:** Edit `AetherionGuilds` source **directly**. Code + schems/templates only.  
**No** `_ship/` folder, Manifest, Apply-Script, README novel, TEST_SHEET.  
One short chat summary when done is enough. Do **not** deploy (Robbi/Cursor deploys).

Robbi playtested the highlight pass. Direction is good. **Not shippable yet** — scale and several feels/bugs break the fantasy.

---

## What Robbi felt (fix)

1. **Starter island too small for the buildings.** Storage Hut + Workshop alone fill the plot. Looks gorgeous, zero room left to build / place more machines / belts. Player stops testing because the island is “full” after two starter pieces.
2. **Production pieces should be prop-scale, not mini-houses.** Mill, Forge, Depot, Quarry housing / quarry-side storage should read like **Satisfactory machines** — small, animated, recognizable in ~2–5 blocks — not another building that eats the isle. Nice larger buildings (e.g. a real Storage Hut / Workshop) can stay **if** the island has room; production chain = props.
3. **Placement ghost:** green/red dust feels scouty / spammy. Want cleaner preview FX (not HAPPY_VILLAGER energy, not a supernova). Keep readable valid/invalid, just less ugly.
4. **Belts are plain rails.** Functional maybe, but not the expected conveyor fantasy. Need a clearer belt look (still performant — no hopper megafactory).
5. **Can’t place/break normal blocks** on the personal island (or feels that way). Must work for the owner inside the build zone / owned parcels. Fix if broken.
6. **Quarries still look like old ArmorStand minions.** Housing / machine body not reading in-world. New place should raise a real compact housing; existing ones should upgrade visually too (not stay frameless forever as the default look).

Keep: starter vibe, claim descend, land buy idea, belt graph sim, guild projects idea, juicy rates OK.

---

## Goals (this pass)

### A) Scale so the island is playable

Pick the mix that actually works (do both if needed):

- **Slightly larger starters** (authored templates) — not mega-hubs, just enough for hut + workshop + a short production line + player builds.
- **Shrink production structures** hard: Mill / Forge / Depot / Quarry housing → **prop footprint**. Storage Hut + Workshop may stay “building” but must leave open pad space; if still too fat, shrink them too.
- Player must be able to place **starter essentials + at least one short chain** (quarry → belt → mill/depot → storage) **and** still have free blocks to build on without buying land first.

Proportion rule: if it belongs on the production line, it should feel like a **machine prop**, not a second house.

Do **not** delete cool schems if you can keep them as optional “landmark” variants later — but live default catalog sizes must fit.

### B) Quarry visual = machine, not old minion

- Placing a quarry must clearly change the world (compact housing / headframe / chute).
- Old ArmorStand can remain as interact hitbox if needed, but must not be the only look.
- Migrate existing quarries to show housing (not permanent frameless-only as the player experience).
- Chute / belt start must be obvious.

### C) Belts feel like conveyors

- Keep abstract sim + rail or similar placeable path if that’s the data layer.
- Presentation: players should see **conveyor**, not “I laid vanilla track”. BlockDisplay / item display / custom block mix OK if cheap and culled.
- Still: caps, near-player only, no item-entity rivers.

### D) Placement FX cleaner

- Replace loud green/red scout dust with a cleaner valid/invalid preview (subtle dust colors, end-rod crumbs, block outline — your call, just not villager-green spam).

### E) Build rights

- Owner can place and break blocks on their island inside owned land / build zone.
- If land/parcel logic is wrong after starters, fix it.
- Don’t soft-lock building because two structures ate the plot.

### F) Optional small: first-arrive guide

- If cheap: a short on-island NPC or clear in-world sign/lectern that points to Build / Quarry / Belts (TalkUx shell LOCKED — content NPC or chest/lectern OK). Not the main focus if scale eats the pass.

---

## Constraints

- Plugin: **AetherionGuilds** only (± tiny Items only if a craft id is unavoidable).
- Additive: don’t wipe player islands / quarry storage / guild banks.
- Locked elsewhere: TalkUx shell, ranks, weapons, boosters, shutdown countdown.
- Balancing still irrelevant.
- Performance: same cull/caps mindset as before.
- Quota empty → wait → continue. When done: stop. No packaging theater.

---

## Done when

- Fresh claim: Hut + Workshop leave real free space; a short production chain fits.
- Mill/Forge/Depot/Quarry housing read as small machines.
- New quarry placement looks different from pre-highlight minion stands.
- Belts read as conveyors at a glance.
- Owner can build/break on their island.
- Placement ghost is cleaner.
- Compile Guilds if you can without m2-begging; otherwise skip compile and say so in one line.

## Starter paste

> Read `docs/OPUS_ISLAND_QUARRY_POLISH_PROMPT.md`. Polish the live Island/Quarry highlight in AetherionGuilds: scale (bigger starters and/or much smaller production props), Satisfactory-style compact machines, real quarry housing visuals (not old ArmorStand-only), conveyor look (not plain rails cosplay), cleaner placement FX, fix owner build/break if broken. Edit source directly. No ship/README/manifest. No deploy.
