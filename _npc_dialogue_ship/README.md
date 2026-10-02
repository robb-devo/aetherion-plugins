# NPC / Dialogue overhaul — ship folder

**AetherionQuests only**, plus new files under `docs/npc/`. Nothing was deployed.
Remote tools can't write that deep into `.claude\worktrees\…`, so everything lands through this folder.

## Re-synced against the live lineage (2026-09-29, after the Foraging + Codex/Skills ships)

- **Target:** `.claude\worktrees\mining-eldervale-progression-65660c`. That is `e1ac96f`, plus the Mining, Foraging and Codex/Skills ships.
- **Other ships:** they touch only Items, Foraging and `docs/`. This one touches none of those.
- **Quests module:** untouched on that tree. All 96 sources and 3 resources match the base this was built on (`e7a32c6`). The tree's Quests jar is bytecode-identical to a build of that base (146/146 classes).
- **Compiled against:** the post-Codex `AetherionItems-1.0.0.jar` (`f0f0c641…`) and the tree's Core/BossEngine jars.
- **Surface diff vs the live-lineage Quests jar:**

| Kind | Live → new | Result |
|---|---|---|
| classes | 146 → 167 | MISSING 0 |
| resources | 15 → 54 | MISSING 0 (+39 cast skin PNGs) |
| commands | 4 → 5 | MISSING 0 (+`npctalk`) |
| config keys | 8 → 22 | MISSING 0, existing values unchanged |
| lang/de keys | 118 → 118 | MISSING 0 |

- **Apply dry-run:** simulated on a CRLF copy of the base. 68 files, 50 new + 18 updated, **0 conflicts**. The applied tree compiles to the same 167 classes.

## Contents

| Path | What |
|---|---|
| `npc-dialogue-files.zip` + `MANIFEST.tsv` | 68 files (CRLF): 50 new, 18 changed. Each has the SHA-256 of the base it was built against (raw and LF-normalised) plus the new hash. |
| `apply-npc-dialogue.ps1` | Copies into the worktree. A file that changed since this build is **never overwritten** (CONFLICT) unless you pass `-Force`, which keeps a `.bak-npc` copy. Nothing is deleted. |
| `patches\0001-*.patch` | The same work as one `git format-patch` commit on `e7a32c6`. Use it with `git apply --3way` or for review. |
| `jar\AetherionQuests-1.0.0.jar` | Prebuilt from the same source (javac 21). The checksum is in `jar\SHA256SUMS.txt`. |
| `pack\talk-font-delta.zip` | New pack files only: font `aetherion:talk` + 10 glyph PNGs. Merge it into whichever pack is live (your Desktop pack). |
| `pack\Aetherion_texturepack_overhaul+talk.zip` | Your attached pack + the delta. 0 entries changed or removed. |
| `docs\NPC_DIALOGUE_OVERHAUL.md` | Gate 0 inventory, design, config, click-test sheet, open items. |
| `preview\*.png` | Cast skins (front/back), faces, talk glyphs. |

## Land it

```powershell
cd C:\Users\Robbi\IdeaProjects\_npc_dialogue_ship
powershell -ExecutionPolicy Bypass -File .\apply-npc-dialogue.ps1 -DryRun   # expect: 68 would be written, 0 conflicts
powershell -ExecutionPolicy Bypass -File .\apply-npc-dialogue.ps1
cd ..\.claude\worktrees\mining-eldervale-progression-65660c
mvn -o -DskipTests -pl AetherionQuests -am package
```

Then deploy **only** `AetherionQuests/target/AetherionQuests-1.0.0.jar` (`ae.ps1`, jar only), and restart when you choose to. There are no Core, Items, Foraging or Hub changes.

## First boot — what to expect

- Log line: `Cast skins: wrote 39 PNG(s) to plugins/FancyNpcs/skins/aetherion`.
- Over the next minutes, `Cast skin signed: egon (…)` and so on. The NPCs switch bodies as each skin lands.
- `/npctalk status` shows the signing progress.
- If skins stay pending, add a free MineSkin key to `plugins/FancyNpcs/config.yml` → `mineskin_api_key`. Until then NPCs are anonymous default bodies in their old leather. Never a creator's skin.
- `talk-ux.pack-glyphs` stays `false` until the live pack carries `talk-font-delta`. The unicode fallbacks look fine without it.

## Rollback

Put the previous `AetherionQuests-1.0.0.jar` back and restart. `npc-memory.yml` and `skins-signed.yml` are new files, and the old jar ignores them.
