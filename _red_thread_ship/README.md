# Soft red thread — ship folder (EXTRA: tutorial + skill isles)

**AetherionQuests only**, plus one new doc, `docs/RED_THREAD.md`. **Not deployed.** Core and the other modules are untouched.

The full design is in `docs/RED_THREAD.md`: spine before → after, kept / elevated / added, walls and the in-game test plan. The short version follows.

## What changed, in one breath

The first-hour spine stays as it was. Crumbs point at the skill isles from places players already stand:
- the Forager points at Twig's pad
- Egon (after the coal run) points at Tackle
- Miss Ledger foreshadows the isles

**Graduation** now opens the roads. Miss Ledger offers reply chips: *Forage Isle · Twig* / *Farm Isle · Harrow* / *Fishing · Tackle* / *Combat · Vex* / *I'll wander.* Each sets one compass target. Her desk gets a permanent **Where next?** chip, which moves *Boosters* behind *More topics…* because TalkUx shows 5 chips max.

**Vex** gets a readiness cue:
- his intro now carries the gear checklist
- a one-time, skippable **gear check** plays when the player is missing armour or Temper's booster (in-world talk only)

The harbour pad auto-hint no longer overrides a road the player already picked.

**New hard walls: none.**

## Files

| Path | What |
|---|---|
| `red-thread-files.zip` + `MANIFEST.tsv` | 9 files (CRLF): 2 new (`ui/SkillRoads.java`, `docs/RED_THREAD.md`), 7 changed. Each row has the base SHA-256 (CRLF and LF) this build expects, plus the new hash |
| `apply-red-thread.ps1` | Guarded copy into the worktree, same rules as the NPC and DE ships. A file that changed since this build is reported as **CONFLICT** and left alone (`-Force` keeps a `.bak-redthread`). Nothing is deleted |
| `patches/0001-…patch` | The same change as one unified diff (LF), for review or `git apply --3way` |
| `jar/AetherionQuests-1.0.0.jar` | Prebuilt, for comparing or rolling forward. `SHA256SUMS.txt` sits beside it |
| `docs/RED_THREAD.md` | Design + test plan (also inside the zip) |

Changed sources:
- `dialog/DialogManager.java`
- `listener/NpcListener.java`
- `listener/ForageHarbourHintListener.java`
- `npc/CastBook.java`
- `ui/QuestHint.java`
- `util/QuestStoryGate.java`
- `resources/lang/de.yml`

## Base and checks

- **Target:** `.claude\worktrees\mining-eldervale-progression-65660c`, the live tree, with the NPC and DE-overlay ships applied.
  - Remote tools can't read that deep, so the sources were rebuilt from `origin/main` @ `1922f36` + `_dialog_voice_de_ship`.
  - Every file this ship touches was checked against your tree three ways:
    - its size on disk
    - the DE ship's new hashes (all 5 overlapping files match)
    - the live `target/AetherionQuests-1.0.0.jar` (same string constants and members for all 6 touched classes)
  - `de.yml` is byte-identical to the one in the live jar.
- **Not touched, and differing on your disk:** `LivingNpcLife`, `LivingNpcProfile`, `QuestProgressAccessImpl`, `ExploreChest*` and `config.yml`. Those are your local edits, and this ship leaves them alone.
- **Apply dry-run** (simulated on a CRLF copy of the base): 9 would be written, **0 conflicts**.
- **Compile (javac 21, paper-api 1.21.1 + your Core / Items / BossEngine jars):**
  - full reconstructed tree: 167 → 170 classes, clean
  - touched sources compiled against the live Quests jar: clean
- **Jar vs live `target` jar:**

| Kind | Live → new | Result |
|---|---|---|
| classes | 171 → 174 | MISSING 0 (+`SkillRoads`, `SkillRoads$Road`, `QuestStoryGate$CombatReadiness`) |
| resources | 90 → 90 | only `lang/de.yml` changed |
| `plugin.yml`, `config.yml` | — | identical (no new commands, permissions or config keys) |

- **DE:**
  - `validate_de_overlay.py` → OK
  - `de.yml`: +56 keys, 5 changed, 0 removed
  - Bukkit `YamlConfiguration` reads every new key, and the list / map accessors resolve
- **Not tested in-game.** There's no Paper server in this sandbox.

## Land it

```powershell
cd C:\Users\Robbi\IdeaProjects\_red_thread_ship
powershell -ExecutionPolicy Bypass -File .\apply-red-thread.ps1 -DryRun   # expect: 9 would be written, 0 conflicts
powershell -ExecutionPolicy Bypass -File .\apply-red-thread.ps1
cd ..\.claude\worktrees\mining-eldervale-progression-65660c
mvn -o -DskipTests -pl AetherionQuests -am package
python docs\dialogs\tools\validate_de_overlay.py
```

Deploy is **Quests jar only**, whenever you choose. The prebuilt jar in `jar\` is your current live `target` jar with just the touched classes and `lang/de.yml` swapped. A local `mvn package` after applying produces the same thing.

## Rollback

Put the previous `AetherionQuests-1.0.0.jar` back. The only new per-player flags are `vex_gear_check`, in the existing starter-kit store; the old jar ignores it. `forage_harbour_pad_hint` is the existing key, reused.
