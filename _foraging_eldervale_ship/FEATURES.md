# Foraging Eldervale — what changed (one page)

**Seven forests, measured from the build.** A district grid baked offline from the schem (8×8×8 cells, y-aware — the Blossom Shelf sits over the Gloamwood Hollow) gives the forest in one array read. The seven are:

| Forest | Wood |
|---|---|
| Frostpine Ridge | spruce |
| Blossom Shelf | cherry |
| Sunscar Mesa | acacia |
| Elderwood Vale | oak and birch |
| Gloamwood Hollow | dark oak |
| Brinefall Mangroves | mangrove |
| Canopy Crown | jungle |

There are 15 named places: the Landing, Emberlit Hollow (the lantern grotto under the shelf), the Blossom Pagoda, the Bell Lodge, the Mossgate Ruin, the Crown Lookout at y 258, and others. **TAB now shows the forest or place**, and **weather rolls per forest**. Both used to be stuck on "Forage Isle" / plains because the habitat sense is off for FPS; it stays off.

**The chop, deeper** (the fell pulse, streak and look-preview are kept and unioned):
- **Titans** (giant trees) take two clean cuts.
- **Crown Finds** drift down from the crown; catch them mid-air. There are 7 finds × 4 grades, and Heartsong is announced to the isle.
- **District extras** (saplings, petals, cocoa…) roll per fell, and the weather tilts them.
- **Widowmaker**: a loose limb gives a tell; dodge it for a Deadfall.
- **Grove Mastery** runs I–VII per wood, with permanent perks.
- A real **marker-speed** stat and a mastery beat after every tally.

**Progression**:
- **Lumber Board** (Pell, at the Landing): 3 rotating orders.
- **Woodwright** (Tamsin, at the Bell Lodge): 5 Grove Mark lines × V — Keen Edge, Deep Roots, Canopy Eye, Sure Grip, Wind Step. She also makes the Sap Lure (the designed *bait*), the Crown Shaker and Heartwood Incense.
- **Forest Ledger** (Juniper, up in the Pagoda): collections from the real Codex, a 28-slot Find Cabinet with row/column rewards, find sales, and a live record board.
- **Warden Standing** gates the top bench tiers. There is no second currency.

**Isle life**: four telegraphed events on one boss bar:
- **Golden Sap** — the designed *hotspot*
- **Windfall** — a crew goal
- **Blossom Storm**
- **Bark Blight** — a crew goal

Critters, all display entities and capped: Canopy Squirrel, Frost Moth (Frostlit), Glowcap Wisp (follow it), Bark Beetle.

**Getting around a 170-block-tall island**:
- **8 updrafts**, each a measured clear shaft (Landing → Shelf is 107 blocks).
- **Fall-catch**: falls never kill on the isle.
- **Edge rescue**: only for people who were just on the isle.
- A **compass** with height hints, discovery cards and rewards, Grove Walker, Cartographer, and a first-visit **tour** through the cast that ends with the Landing Updraft.

**Cast**: Pell, Tamsin and Juniper, as isle-local FancyNPCs (`ae_forage_cast_*`) with no borrowed skins. Miss Canopy is step 1 of the tour.

**Fixes**:
- The **Codex never counted chopped wood**, because the break event is cancelled and collapse logs fire no event. Every paid log is now filed.
- The two live Foraging jars (midgame + staged WIP) are unioned into one. Deleting the stale one ends the *Ambiguous plugin name* error.

**Items (small)**:
- Six flag-only Foraging skills: Grove Born, Sap Sense, Steady Hands, Deadfall Dancer, Board Rates, Heart Hunter.
- DEV → WORLDS → **Forage Island** tile in the empty slot 38, which opens `/grove dev`.

**Commands**: `/grove` (the journal), `/grove tour`, `go <place>`, `where`, `places`, `mastery`, `cabinet`, `board`, `bench`, `ledger`, and `/grove dev [action]`.

**FPS**: no runtime block scans, weather visuals still off, displays capped and non-persistent, particles near players only.
