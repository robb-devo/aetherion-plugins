# Apply & deploy — Foraging Eldervale deep pass

Nothing was deployed. Nothing outside this folder was modified: the live-lineage worktree is untouched until you run step 1.

## 0. What this lands on

The target is the **live lineage**: `.claude\worktrees\mining-eldervale-progression-65660c`. It is e1ac96f + the uncommitted Mining Eldervale ship + the Items WIP. The live Core, Hub, Items and Mining jars on MMO-R are byte-identical to builds from that tree (checked 2026-09-29). This matters: build Items from anywhere else and features drop out.

## 1. Land the files (guarded)

```powershell
cd C:\Users\Robbi\IdeaProjects\_foraging_eldervale_ship
powershell -ExecutionPolicy Bypass -File .\apply-foraging-eldervale.ps1 -DryRun   # expect: 48 would be written, 0 conflicts
powershell -ExecutionPolicy Bypass -File .\apply-foraging-eldervale.ps1
```

- 48 files: 32 new, 16 changed. There are Foraging and Items sources, two docs, and no Core/Hub/Quests.
- A changed file is only overwritten if it is **exactly** the version this was built against. Both the raw SHA-256 and the CRLF→LF-normalised SHA-256 are checked, so line-ending differences don't count.
- Anything edited since is a **CONFLICT** and left untouched. `patches\foraging-eldervale.diff` shows every hunk if you need to merge by hand. `-Force` overwrites and keeps a `.bak-forage` copy.
- Re-running is safe: already-landed files are skipped.

## 2. Build

From the worktree root:

```powershell
mvn -DskipTests package
# or just the two jars:
mvn -DskipTests -pl AetherionForaging,AetherionItems -am package
```

`jars\` already holds both jars built exactly this way (from these files on top of the live lineage). See `SHA256SUMS.txt`. The Items build is class-for-class identical to live + this pass.

## 3. Surface check (ship law: MISSING 0)

```powershell
cd <worktree>
docs\tools\jar-surface-diff.ps1 -Old <live>\AetherionForaging-1.0.0.jar -New AetherionForaging\target\AetherionForaging-1.0.0.jar
docs\tools\jar-surface-diff.ps1 -Old <live>\AetherionForaging.jar       -New AetherionForaging\target\AetherionForaging-1.0.0.jar
docs\tools\jar-surface-diff.ps1 -Old <live>\AetherionItems-1.0.0.jar    -New AetherionItems\target\AetherionItems-1.0.0.jar
```

These were run against copies of today's live jars:

| Jar | Classes (live → new) | Commands | Result |
|---|---|---|---|
| Foraging (`AetherionForaging-1.0.0.jar`, the midgame build) | 48 → 106 | 1 → 2 | MISSING 0 |
| Foraging (`AetherionForaging.jar`, the staged WIP build) | 46 → 106 | — | MISSING 0 |
| Items | 585 → 585 | — | MISSING 0 (only skill/DEV edits inside existing classes) |

## 4. Deploy (your call — MMO-R, `a28d676a-…`)

1. Keep copies of the live `AetherionForaging-1.0.0.jar`, `AetherionForaging.jar` and `AetherionItems-1.0.0.jar` for rollback.
2. Upload the new `AetherionForaging-1.0.0.jar` and `AetherionItems-1.0.0.jar`.
3. **Delete `plugins/AetherionForaging.jar`.** It is the stale staged-WIP build. Its features (look-preview, any-stem chop, newcomer hints) are in the union, and it is why Paper logs *Ambiguous plugin name 'AetherionForaging'* on every boot. (Hub, Mining and Dungeons have the same double-jar problem — not touched here.)
4. The Items deploy needs the usual remapper clear (`ae.ps1 deploy-items` does it).
5. **Restart** (not `/reload`): new command, listeners and resources.

## 5. First boot — what to expect

- Log lines:
  - `Foraging Eldervale: grid loaded 71×32×65 · 15 places · 8 updrafts · Items skills found`
  - a one-time line saying hotspots (Golden Sap) and bait (Sap Lure) were switched on
  - `Grove cast: Pell Ardwin / Tamsin Holt / Juniper Quell placed at …` (4 s after enable)
- New files in `plugins/AetherionForaging/`:
  - `forage-isle.yml` — places, updrafts, cast spots, tuning. Edit freely; defaults are merged additively and never overwrite your values.
  - `forage-isle-players.yml` — profiles and records.
- `config.yml`: `hotspots.enabled` and `bait.enabled` become `true` once (they were unused placeholders). Set either back to `false` to opt out; the migration never runs again.
- The cast auto-places at measured spots. To skip that, pre-create `forage-isle.yml` with `cast: {auto-place: false}` before first boot, or remove them afterwards with `/grove dev cast:removeall`. Move anyone with `/grove dev` → Grove Cast → anchor. Skins are empty, so they show as Steve/Alex. Only ever set original textures.

Then run the in-game test sheet: `docs/FORAGING_ELDERVALE.md` §7.

## 6. Rollback

1. Run `/grove dev cast:removeall` first. FancyNpcs persists `ae_forage_cast_*` NPCs in its own save; the old jar wouldn't know to remove them.
2. Put the three saved jars back.
3. Optionally delete `forage-isle.yml` and `forage-isle-players.yml`. The old jar ignores both, and ignores `hotspots.designed`.

## 7. Commit (suggested, in the target worktree)

```
Foraging: Eldervale deep pass — seven forests (baked district grid), Titan fells, Crown Finds,
Grove Mastery, Lumber Board, Woodwright marks, Forest Ledger, isle events, critters, updrafts,
fall-catch, compass & tour, the Grove cast; union of midgame + staged look-preview WIP; Codex now
counts chopped wood. Items: six Eldervale foraging skills + DEV WORLDS "Forage Island" tile.
```
