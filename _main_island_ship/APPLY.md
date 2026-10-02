# Apply & deploy — Main Island / Origin Hub overhaul

Nothing was deployed, and nothing outside this folder was modified. The live-lineage worktree stays untouched until you run step 1.

## 0. What this lands on

The target is the **live lineage**: `.claude\worktrees\mining-eldervale-progression-65660c`. The live Hub jar on MMO-R was built from it.

This pass also **unions the main checkout's Hub** (`IdeaProjects\AetherionHub`): the prop wand, the 18 prop schems, `HubService#wipePlayer` and the `bloodstone` camp id. So one Hub jar now carries everything both Hub jars had.

The Hub files it changes were checked against the Mining Eldervale ship's output. They are the same bytes as that worktree.

## 1. Land the files (guarded)

```powershell
cd C:\Users\Robbi\IdeaProjects\_main_island_ship
powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1 -DryRun       # expect: 55 would be written, 0 conflicts
powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1
# optional DEV tile in Items (WORLDS slot 39 → /origin dev):
powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1 -WithItemsTile -DryRun   # expect: 2 more, 0 conflicts
powershell -ExecutionPolicy Bypass -File .\apply-main-island.ps1 -WithItemsTile
```

**What gets written:**
- **Hub part:** 55 files. 8 are changed: `AetherionHub.java`, `HubService`, `IslandLaunchPads`, `SpawnGotoCommand`, `SpawnMenu`, `SpawnDiscoverListener`, `PlayerHubStorage` and `plugin.yml`. 47 are new: `origin/*` (22 classes), the prop wand (4 classes + 18 schems), `origin.yml`, and `docs/MAIN_ISLAND_ORIGIN.md` with its map. No Core, Quests, Mining, Foraging, Farming or Fishing files.
- **Items part:** only with `-WithItemsTile`. Its base is `DevHubs`/`DevMenu` **after the Foraging Eldervale ship** (the Forage tile in slot 38). If that ship isn't applied, these 2 files report CONFLICT and nothing is written; the Hub part is independent.

**How the guard works:**
- A changed file is only overwritten if it is **exactly** the version this was built against. Both the raw and the CRLF→LF-normalised SHA-256 are checked, so line-ending differences don't count.
- Anything edited since is a **CONFLICT** and left untouched. `patches\*.diff` shows every hunk. `-Force` overwrites and keeps a `.bak-origin` copy.
- Re-running is safe: files that already landed are skipped.

## 2. Build

From the worktree root:

```powershell
mvn -DskipTests -pl AetherionHub -am package
# with the Items tile too:
mvn -DskipTests -pl AetherionHub,AetherionItems -am package
```

`jars\AetherionHub-1.0.0.jar` was built from exactly these files on top of the live lineage (see `SHA256SUMS.txt`). A Maven build differs only in timestamps; its classes and resources are the same.

## 3. Surface check (ship law: MISSING 0)

```powershell
cd <worktree>
docs\tools\jar-surface-diff.ps1 -Old <live>\AetherionHub-1.0.0.jar -New AetherionHub\target\AetherionHub-1.0.0.jar
# if plugins\ also holds the main-checkout build (AetherionHub.jar), check it too:
docs\tools\jar-surface-diff.ps1 -Old <live>\AetherionHub.jar       -New AetherionHub\target\AetherionHub-1.0.0.jar
```

Results here:

| Old jar | Classes | Commands | Resources | Result |
|---|---|---|---|---|
| Live Hub (`dc0383fa…`) | 25 → 77 | 16 → 20 | 2 → 21 | **MISSING 0** |
| Main-checkout Hub (`563bfc9d…`, prop wand) | 31 → 77 | 15 → 20 | 20 → 21 | **MISSING 0** |

- **New commands:** `origin` (`originisle`, `originjournal`), `summit` (`skyreach`), `whisperwood` (`wilds`), `propwand` (`aeprops`, `propstick`).
- **New permissions:** `aetherion.origin.admin`, `aetherion.origin.dev` and `aetherion.propwand` (all op).
- **`config.yml`:** unchanged (7 top-level keys).

## 4. Deploy (your call — MMO-R)

1. Keep a copy of the live Hub jar(s) for rollback.
2. Upload the worktree-built `AetherionHub-1.0.0.jar`.
   - **Don't use `ae.ps1 deploy-hub`.** It builds `IdeaProjects\AetherionHub` (the main checkout, which has no `/mining` or `/fishing`).
   - Use: `.\ae.ps1 deploy-jar -Jar <worktree>\AetherionHub\target\AetherionHub-1.0.0.jar -DestName AetherionHub-1.0.0.jar -NoRestart`
3. If `plugins/` holds a **second Hub jar** (e.g. `AetherionHub.jar`), delete it after the step-3 check passes. Its features are all in the union. Two Hub jars means Paper picks one ("Ambiguous plugin name").
4. Items tile (optional): upload the worktree-built `AetherionItems-1.0.0.jar` with the usual remapper clear. `deploy-items` builds `IdeaProjects\AetherionItems`, so don't use it for this.
5. **Restart** (not `/reload`): new commands and listeners.

## 5. First boot — what to expect

- **Log lines:**
  - `Origin Isle: 13 districts · 39 landmarks · 8 waystones · 5 vistas · 7 bells · 2 updrafts · 4 flights · 40 emitters`
  - on first boot only: `Origin: seeded camp 'summit' at -6 200 -269` and `… 'whisperwood' at -285 69 -498`
- **New files in `plugins/AetherionHub/`:**
  - `origin.yml`: all measured spots and tuning. Edit freely; missing keys are merged on every start and your values are never overwritten. `/origin dev reload` re-reads it.
  - `origin-cast.yml`: the 5 townsfolk, auto-placed once at the measured presets.
  - `origin-players/`: journals.
- **`config.yml`** gains `spawns.summit` (slot 23) and `spawns.whisperwood` (slot 24), with walk-in radii 18/30. Both are locked by default and unlock by walking in.
- **Opting out:**
  - Disable Origin entirely: `enabled: false` in `origin.yml`. The DEV hub still opens.
  - Skip the auto-placed townsfolk: pre-create `origin.yml` with `cast: {auto-place: false}`, or run `/origin dev cast remove`.

Then run **TEST_SHEET.md**.

## 6. Rollback

1. Put the saved jar(s) back and restart. The Origin townsfolk, glowcap labels, sprouts and lanterns are non-persistent, so nothing is left in the world.
2. The old jar's `repairSpawnLayout` removes the `summit`/`whisperwood` sections from `config.yml` by itself, as it does for any unknown camp.
3. Optionally delete `origin.yml`, `origin-cast.yml`, `origin-softlight.yml` and `origin-players/`. The old jar ignores them.
4. If you ran Origin softlight, run `/origin dev softlight undo` **before** rolling back. It removes exactly the LIGHT blocks it placed.

## 7. Commit (suggested, in the target worktree)

```
Hub: Origin Isle — main-island showcase layer (13 districts, 39 landmarks, glowcap waystones, vistas,
the Seven Bells, updrafts + scripted skyways/glides, ambience + lighthouse, island moments, wishing
fountain, Origin townsfolk + journal + tour, district softlight, DEV hub), Summit/Whisperwood camps;
union of the main-checkout Hub (prop wand, schems, wipePlayer, bloodstone id). Items: DEV WORLDS
"Origin Island" tile (slot 39).
```
