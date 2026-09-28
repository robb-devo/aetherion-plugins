# Opus — Fishing Eldervale (LOCAL)

**Scope:** Fishing Eldervale only. Nothing else. No Hetzner deploy.

**Product:** https://orionixservices.com/products/fishing-eldervale-island  
**Schem:** `IdeaProjects\_tmp_maptools\Fishing-Eldervale-Island.schem`  
**Paste (live):** `/aetherpaste fishing` → world `@ -585 90 -649`  
**Base tree:** `_wt_items_hotfix` → branch `claude/fishing-eldervale-expansion`  
**Plugins that may change:** `AetherionFishing`, `AetherionItems`, thin `AetherionCore` / Hub glue only if the isle needs it.

---

You are a local Claude Opus agent.

## Mission

Make **Fishing Eldervale** so good that someone could run a **server with only this island** and players would stay for **hours**.

Not a checklist of 20 systems. Prefer **few loops**, each **insanely sticky**:
- cast → bite → strike → reward that feels juiced
- “one more cast” pressure
- discoveries that make you walk the map
- progression that changes how the water *feels*
- events that make the whole lake go loud for a few minutes

Quality bar: Farming Eldervale (`docs/FARMING_ELDERVALE.md` + `AetherionFarming/.../isle/*`) is the craft reference for *feel* and polish — not a template to clone 1:1. Invent **fishing-native** fantasy. English-only player text. Readable spectacle, no particle soup.

## What already exists (elevate, don’t throw away)

- Minigame: lure fish → strike bar → perfect gold cell → catch streak (`AetherionFishing`)
- Encounters: Dock Dredger → Leviathan Bait (`FishingEncounterTier`)
- Gear: rods/armor T1–T5, diving pieces, Fish Catch / Speed / Fortune
- Loot: `FishingLootPool` + compressed/compacted fish + Fish Shop
- Harbour Fisher / Fishmonger tutorial — leave it; isle is the destination

Today the pasted island is mostly **map**. Recast feels thin. Fix that.

## What to ship (few, but god-tier)

Design **3–6** systems max. Each must pass: *would I stay another hour for this alone?*

Required outcomes (how you name them is free):

1. **Core cast loop** — the strike/streak/perfect path must feel addictive on the isle (audio, feedback, risk/reward). One more cast.
2. **Map reasons to move** — named spots / hotspots / discoveries from the schem (lakes, plaza, interiors). Standing in one pier forever is a fail.
3. **At least one shared event** that flips the whole lake for a window (telegraphed, loud, fair).
4. **Progression that sticks** — mastery / trophies / records / skills — something permanent that makes yesterday’s cast feel weaker than today’s.
5. **A sink or goal** beyond raw fish (orders, smokehouse, trophies, board — pick what sings, don’t build all of them).
6. **DEV hub** — `/devmenu` → Fishing Island (teleports, force event, place cast, test tools). Mirror Farming Island quality, not bulk.

Optional if it elevates the fantasy without bloating: diving synergy, weather/tide bite tables, a tiny pier cast (2–4 NPCs, not 8 clones).

**Do not** pad with busywork systems.

## Ownership (don’t fight it)

- **AetherionFishing** — world loops, minigame, isle OS  
- **AetherionItems** — loot, rod progress, skills, gear  
- **Core** — only thin `FishAccess`-style defaults if needed  

Write `docs/FISHING_ELDERVALE.md` (short systems table + test sheet). Build jars. Stop. Robbi ships.

## Done

1. Branch + commits  
2. `mvn -DskipTests package` green for touched modules  
3. `docs/FISHING_ELDERVALE.md`  
4. Chat: what you built, why it’s addictive, jar paths + checksums  
5. **STOP** — no deploy, no drive-bys

---

> Make Fishing Eldervale the most addictive gather destination on Aetherion. Few systems. Unreal loops. Hours of “one more cast.” Ship-ready jars only.
