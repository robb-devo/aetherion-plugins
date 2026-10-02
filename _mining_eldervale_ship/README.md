# Mining Eldervale ship folder

This folder exists because remote tools may not write inside `.claude\`, so the build couldn't be put straight into `.claude\worktrees\mining-eldervale-progression-65660c`. Everything is here instead, ready to land in one command.

- `files\`: 48 files (CRLF), as full contents: 28 new, 20 changed.
- `MANIFEST.tsv`: per file, the SHA-256 of the version it was built against and of the new version.
- `apply-mining-eldervale.ps1`: copies the files into the worktree.
  - A file whose on-disk content differs from the expected base is **never overwritten**. It's reported as CONFLICT.
  - The only exception is `AetherionMining.java`, where your local `/amethyst` edit is recognised and folded in.
- `patches\`: the same work as `git format-patch` commits on top of the worktree's index snapshot. Use it for `git apply --3way` or review.

## Land it

```powershell
cd C:\Users\Robbi\IdeaProjects\_mining_eldervale_ship
powershell -ExecutionPolicy Bypass -File .\apply-mining-eldervale.ps1 -DryRun   # look first
powershell -ExecutionPolicy Bypass -File .\apply-mining-eldervale.ps1
```

Then:

1. Paste the ~12-line DevMenu Hypixel-IA splice. It's in `docs/MINING_ELDERVALE.md` §4, which the apply step also lands in the worktree.
   - Until you do, the Mining Island DEV pages just aren't linked.
   - Everything else, and the build itself, works without it.
2. `mvn -DskipTests package` from the worktree root.
3. Before replacing the live Mining jar, run `docs\tools\jar-surface-diff.ps1 -Old <live AetherionMining.jar> -New AetherionMining\target\AetherionMining-1.0.0.jar`. It must report 0 MISSING.

Nothing was deployed.
