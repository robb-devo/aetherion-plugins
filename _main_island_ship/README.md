# Main Island / Origin Hub ship folder

The Main Island (Origin) overhaul, ready to land on the live-lineage worktree in one guarded command. It sits here, outside `.claude\worktrees`, like the previous ships.

- Steps: **APPLY.md**
- One-page overview: **FEATURES.md**
- In-game checks: **TEST_SHEET.md**
- Full design (Gate 0, systems, config, deploy): `files\docs\MAIN_ISLAND_ORIGIN.md`

| Path | What |
|---|---|
| `apply-main-island.ps1` | Guarded apply. Checks raw and LF-normalised SHA-256 per file and never overwrites an edited file (CONFLICT). Options: `-DryRun`, `-Force` (keeps `.bak-origin`), `-Worktree`, `-WithItemsTile` |
| `MANIFEST.tsv` | **Hub part**, 55 files: 8 changed Hub files + 47 new. The new files are 22 `origin/` classes, the prop wand union (4 classes + 18 schems), `origin.yml`, `docs/MAIN_ISLAND_ORIGIN.md` and its map. `NEW` = must not exist yet |
| `MANIFEST-items.tsv` | **Optional** Items DEV tile, 2 changed files: `DevHubs` (WORLDS slot 39) and `DevMenu` (`originisle:open` → `/origin dev`). Its base is the Items DEV files *after* the Foraging Eldervale ship |
| `files\` | Full contents (text in CRLF, like the repo) |
| `patches\main-island.diff`, `patches\items-origin-tile.diff` | The same changes as unified diffs against the live lineage, for review or `git apply`. Binaries (18 `.schem`, the map `.jpg`) are only in `files\` |
| `jars\` | `AetherionHub-1.0.0.jar` built from `files\` on the live lineage, plus `SHA256SUMS.txt` |

Verified before handing over:
- **Guarded apply**, run on a CRLF copy of the live-lineage sources with a faithful port of the script:
  - dry run → 57 to write (Hub 55 + Items 2), **0 conflicts**
  - apply → 57 written
  - second run → 57 already up to date
  - a locally edited base file → reported as CONFLICT and left untouched
- **Build:** the jar built from the applied tree is class-for-class identical to the one here.
- `git apply --check` passes for both diffs against the live lineage.
- **Jar surface diff** → **MISSING 0**, against both:
  - the live Hub jar (`dc0383fa…`): 25 → 77 classes, 16 → 20 commands
  - the main-checkout jar (`IdeaProjects\AetherionHub\target`, `563bfc9d…`, prop wand + Bloodstone): 31 → 77 classes
- **Config and placement:**
  - `origin.yml` validated against the original world file: every spot standable, every landmark in its district, walking routes from the updraft tops confirmed, **BAD 0**
  - the 4 flight paths checked point-by-point against the terrain
  - every townsperson and glowcap is ≥ 12 blocks from any Quests NPC

The Items tile was not compiled here (Items needs the full workspace). It is a 12-line copy of the Forage Island tile pattern.

Nothing was deployed. No worktree, live server or repo was written to.
