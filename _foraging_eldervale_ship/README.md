# Foraging Eldervale ship folder

The Foraging Eldervale deep pass, ready to land on the live-lineage worktree in one guarded command. It sits here, outside `.claude\worktrees`, as the brief asked. Steps are in **APPLY.md**, the one-page overview is **FEATURES.md**, and the full design is `files\docs\FORAGING_ELDERVALE.md`.

| Path | What |
|---|---|
| `apply-foraging-eldervale.ps1` | Guarded apply. Checks raw and LF-normalised SHA-256 per file; never overwrites an edited file (CONFLICT); `-DryRun`, `-Force` (keeps `.bak-forage`), `-Worktree` |
| `MANIFEST.tsv` | Per file: base SHA (raw and LF), new SHA (raw and LF), text/binary. `NEW` = must not exist yet |
| `files\` | 48 files as full contents (text in CRLF, like the repo): 32 new, 16 changed |
| `patches\foraging-eldervale.diff` | The same change as a unified diff against the live lineage, for review or `git apply`. The one binary, `forage-districts.bin`, is only in `files\` |
| `jars\` | `AetherionForaging-1.0.0.jar` and `AetherionItems-1.0.0.jar` built from `files\` on the live lineage, plus `SHA256SUMS.txt` |

Verified before handing over:
- A dry run against `.claude\worktrees\mining-eldervale-progression-65660c` → 48 to write, **0 conflicts**.
- Applied to a copy of that tree → 48 written; a second run → 48 already up to date. The build from it matches the jars here class-for-class.
- Jar surface-diff vs both live Foraging jars and live Items → **MISSING 0**.
- The baked district grid was cross-checked in Java against the Python bake: 3,011 points, 0 mismatches. `docs/tools/forage_grove_bake.py bake` reproduces it byte-for-byte.

Nothing was deployed, and no worktree, live server or repo was written to (the live server was only read over SSH).
