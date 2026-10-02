# Apply & deploy — Codex + Skills overhaul

Nothing was deployed. Nothing outside this folder and the session worktree was modified (the live server was only read over SSH).

## 0. What this lands on

The target is the **live lineage**: `.claude\worktrees\mining-eldervale-progression-65660c`, which is e1ac96f + the Mining and Foraging ships + the Items WIP. The live `AetherionItems-1.0.0.jar` on MMO-R (sha `e61db092…`, 14:13) is byte-identical to that tree's build, and a rebuild from its sources is content-identical. **Items only**: no Core, Hub, Quests, Mining, Foraging, Fishing or Farming change.

The same change is also committed on branch `claude/collection-bestiary-skills-overhaul-fbeb47`: `d5c85c2` is the live-lineage import and `70434d4` is the overhaul.

## 1. Land the files (guarded)

```powershell
cd C:\Users\Robbi\IdeaProjects\_codex_skills_overhaul_ship
powershell -ExecutionPolicy Bypass -File .\apply-codex-skills-overhaul.ps1 -DryRun   # expect: 36 would be written, 0 conflicts
powershell -ExecutionPolicy Bypass -File .\apply-codex-skills-overhaul.ps1
```

- 36 files: 20 new and 16 changed. That is 35 Items sources/resources plus `docs/CODEX_SKILLS_OVERHAUL.md`.
- A changed file is only overwritten if it is **exactly** the version this was built against. Both the raw SHA-256 and the CRLF→LF SHA-256 are checked.
- Anything edited since is a **CONFLICT** and is left untouched. `patches\codex-skills-overhaul.diff` has every hunk. `-Force` overwrites and keeps a `.bak-codex` copy.
- Re-running is safe: files that already match are skipped.

## 2. Build

```powershell
mvn -o -DskipTests -pl AetherionItems -am package
```

`jars\AetherionItems-1.0.0.jar` was built this way. It is content-identical to a build of a fresh copy of the target with this ship applied. See `SHA256SUMS.txt`.

## 3. Surface check (ship law: MISSING 0)

```powershell
docs\tools\jar-surface-diff.ps1 -Old <live>\AetherionItems-1.0.0.jar -New AetherionItems\target\AetherionItems-1.0.0.jar
```

Run against today's live jar:

| Kind | Live → new | Result |
|---|---|---|
| classes | 585 → 631 | MISSING 0 (+46: Codex pages/model, skill overview/presets/seals/session) |
| commands | 18 → 21 | MISSING 0 (+`codex`, `collection`, `bestiary`) |
| config keys | 1 → 1 | MISSING 0 |
| resources | 3 → 3 | MISSING 0 |

Foraging, Mining, Fishing, Farming and AetherMobs compile unchanged against the new Items. The `CodexService` and `SkillService` APIs they call are kept as they were.

## 4. Deploy (your call — MMO-R `a28d676a-…`; the dungeon server runs Items too)

1. Keep a copy of the live `AetherionItems-1.0.0.jar` for rollback.
2. Deploy with `ae.ps1 deploy-items` (it clears the remapped jar), or upload `jars\AetherionItems-1.0.0.jar` and clear `.paper-remapped/AetherionItems*`.
3. **Restart** (not `/reload`). The restart brings a new listener, new commands and a new stat provider.
4. Optional: `codex-perks: false` in `plugins/AetherionItems/config.yml` turns off the milestone perks (+Fortune, +Damage/+Health). Rewards and pages stay.

## 5. First boot — what to expect

- No new files. `codex.yml` gains `variants`, `claims` and `found` per player as they play. `skills.yml` gains `presets` for players who save one. All old keys stay as they are.
- **Back pay:** existing players have tiers and seals already reached. 8 s after join they see `✦ Codex » N rewards waiting [OPEN CODEX]`. Claiming is what pays out; nothing is paid automatically.
- The Bestiary Bosses tab now shows the real kill counts from `codex.yml bosses` (it showed 0 before).
- Milestone perks count tiers **reached**, so veterans get their Fortune, Damage and Health on the first stat read.

Then run the in-game test sheet: `docs/CODEX_SKILLS_OVERHAUL.md` §4 (30 checks).

## 6. Rollback

Put the saved jar back and restart. The old jar reads `kills`, `blocks` and `bosses` as before and ignores `variants`, `claims`, `found` and `presets`. Anything already claimed stays paid.

## 7. Commit (suggested, in the target worktree)

```
Items: Codex + Skills overhaul — tiered Collection/Bestiary, live bosses, claim loop, milestones,
skill overview/presets/seals. Collection gains Catches, ripe-crop + placed-block rules, Vein Siphon credit.
```
