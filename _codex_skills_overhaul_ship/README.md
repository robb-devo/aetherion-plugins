# Codex + Skills overhaul ship folder

This folder holds the Collection, Bestiary and Skills overhaul for AetherionItems. It lands on the live-lineage worktree with one guarded command. **APPLY.md** has the steps and **FEATURES.md** is the one-page overview. The full design and test sheet are in `files\docs\CODEX_SKILLS_OVERHAUL.md`.

| Path | What |
|---|---|
| `apply-codex-skills-overhaul.ps1` | Guarded apply. Checks the raw and LF-normalised SHA-256 of each file. Never overwrites an edited file (CONFLICT). Flags: `-DryRun`, `-Force` (keeps `.bak-codex`), `-Worktree` |
| `MANIFEST.tsv` | Per file: base SHA (raw and LF), new SHA (raw and LF). `NEW` means the file must not exist yet |
| `files\` | 36 files as full contents (CRLF, like the repo): 20 new, 16 changed |
| `patches\codex-skills-overhaul.diff` | The same change as a unified diff against the live lineage |
| `jars\` | `AetherionItems-1.0.0.jar` built from `files\` on the live lineage, plus `SHA256SUMS.txt` |

Checked before handing over:
- Dry run against `.claude\worktrees\mining-eldervale-progression-65660c`: 36 to write, **0 conflicts**.
- Applied to a fresh copy of that tree: 36 written. A second run: 36 already up to date. The build from it is content-identical to `jars\`.
- Jar surface-diff against the live Items jar: **MISSING 0** (classes +46, commands +3).
- Foraging, Mining, Fishing, Farming and AetherMobs compile unchanged against it.
- Git: branch `claude/collection-bestiary-skills-overhaul-fbeb47`, `d5c85c2` (live import) → `70434d4` (overhaul).

Not tested on a running server. The in-game test sheet is §4 of the doc.
