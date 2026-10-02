# Dialog voice (EN) + German overlay — ship

**AetherionQuests only.** This is additive and hash-guarded, and it is **not deployed**. Details, inventory and the click-test sheet are in `docs/DIALOG_VOICE_DE.md`.

- **Source:** this worktree (`dialog-voice-german-localization-651358`), base `origin/main` @ `f1f9d46`. The changes are uncommitted.
- **Where this folder lives:** inside the session worktree (untracked, not meant for git), because a guard blocks writes into the base checkout. Move or copy it next to the other `_…_ship` folders if you like; the apply script only needs `-Worktree`.
- **Live lineage:** all 10 touched Quests files are byte-identical (CRLF-normalized) to `mining-eldervale-progression-65660c`, the default apply target.

## Contents

| Path | What |
|---|---|
| `dialog-voice-de-files.zip` | 13 files (10 Quests sources/resources + 3 docs), CRLF |
| `MANIFEST.tsv` | path · base SHA-256 (CRLF) · new SHA-256 · status · base SHA-256 (LF) |
| `apply-dialog-voice-de.ps1` | guarded apply (see below) |
| `patches/0001-…patch` | same change as a `git apply`-able diff against `f1f9d46` |
| `jar/AetherionQuests-1.0.0.jar` | prebuilt from this source (`SHA256SUMS.txt`) |
| `docs/DIALOG_VOICE_DE.md` | design, EXISTS/CLAIMED/MISSING inventory, style sheet, test sheet |

## Apply

```powershell
powershell -ExecutionPolicy Bypass -File .\apply-dialog-voice-de.ps1 -DryRun
powershell -ExecutionPolicy Bypass -File .\apply-dialog-voice-de.ps1
```

- A file is overwritten only when it still matches the base this build was made from (CRLF or LF). Anything else is reported as **CONFLICT** and left alone.
- New files are written only if they're absent.
- Nothing is deleted, and nothing outside `AetherionQuests/`, `docs/dialogs/` and `docs/npc/` is touched.

Then:

```powershell
mvn -DskipTests -pl AetherionQuests package
python docs\dialogs\tools\validate_de_overlay.py
```

## Jar surface diff vs the live-lineage build (`mining-eldervale-progression-65660c/AetherionQuests/target`)

- Entries: 257 → 257. **MISSING 0**, **ADDED 0**.
- 15 changed: 9 classes (+ their inner classes) and `lang/de.yml`.
- `plugin.yml` and `config.yml` are identical: no new commands, permissions or config keys.

## Scope guard

- **TalkUx:** string lookups only; details in the doc. EN output is byte-identical.
- **Not touched:** Hub, Borderlands rites/vials/altar, spawn, Items, BossEngine, DevMenu, boosters, anvil, ranks, combat, shutdown, Crafty/deploy.
- Rite Warden + Proctor lines are left exactly as they were (CLAIMED by the Hub/Borderlands pass).
- Deploy is Quests jar only, whenever you choose. The German pack is read from the jar (`/language reload` re-reads it).
