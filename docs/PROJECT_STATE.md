# Aetherion — Project state (read this first)

Snapshot: **2026-10-19**. One page so a new session does not have to rescan the
repo. Update the dated sections when they change.
Open work and priorities: [BETA_AUDIT_2026-10-19.md](BETA_AUDIT_2026-10-19.md).
Ops, hosting and access details are deliberately **not** in this public repo; they live in
the private `aetherion-ops/` folder on Robb's PC (`docs/OPUS_SESSION_BIBLE.md`,
`docs/LIVE_STATE_*.md`, `docs/BETA_AUDIT_OPS_*.md`).

## What this is

Minecraft MMORPG network "Aetherion" on Paper 1.21.1 behind a Velocity proxy.
17 Maven modules (one plugin each), ~470k lines of Java, 4 unit tests.
CI (`.github/workflows/compile.yml`) only compiles.

| Module | Role | Java LOC |
|---|---|---|
| AetherionCore | Shared API, network transfer, persistence, shutdown | 15k |
| AetherionItems | Items, combat, HUD, economy UI, recipes, resource pack source | 142k |
| AetherionQuests | Quests, onboarding acts, TalkUx, Codex | 54k |
| BossEngine | Boss framework + 43 boss YAMLs | 80k |
| Aethermobs | Mobs, pets | 37k |
| AetherionGuilds | Guilds, private/guild islands | 28k |
| AetherionDungeons | Dungeon gate (mmo-r) + instances (mmo-d) | 17k |
| AetherionHub | Hub pads, spawns, warps | 17k |
| SamuraiBoss | Standalone boss | 16k |
| AetherionMining / Foraging / Farming / Fishing | Gathering isles (Eldervale) | 15k / 16k / 11k / 9k |
| AetherionGnomon | Adaptive boss encounter | 9k |
| AetherionBeta / Pit / StressBots | Beta tooling, Pit minigame, QA load bots | 3k / 2k / 4k |

Backends: **hub** (lobby + Pit), **mmo-r** (main world, capital, isles, bosses, market),
**mmo-d** (dungeons only), **mmo-c** (exile/story), **mmo-i** (private + guild islands).

## Where the truth lives

- **GitHub `main` = live code.** Every jar on the servers is built from it.
- The local ship worktree is `IdeaProjects/.claude/worktrees/mining-eldervale-progression-65660c`
  (branch `ship/live-hardening`). Build and deploy from there, commit, push, merge to `main`.
- The `IdeaProjects/` root checkout (branch `claude/polish-pets`) is **stale (09-28)** with
  ~6000 dirty files. Do not build or commit from it.
- Agent prompts and briefs live in `docs/prompts/`; they are history, not specs.

## Live plugin builds (sha1 prefix, 2026-10-19 17:30)

Same on every server that loads them: Core `536b35c5`, Items `5419e52f`, Quests `1bc884e1`,
BossEngine `0b4760db`, Gnomon `684013f0`, AetherMobs `078432e9`, Guilds `60342686`. Each of these is
byte-identical to a build of `main` (classes and resources).

| Jar | mmo-r | mmo-d | mmo-c | mmo-i | hub |
|---|---|---|---|---|---|
| Dungeons | a92a2c42 | a92a2c42 | – | – | – |
| Mining / Foraging / Farming / Fishing | fde4cece / f9de95e9 / 5e8a58ce / a6aae3f0 | Fishing **03aedf90 (10-02)** | same as mmo-r | – | – |
| Hub | 96ed909c | **`AetherionHub.jar` (09-22)** | – | – | **d1a097e7 (10-05)** |
| Beta | ed9d1621 | ed9d1621 | – | – | – |
| StressBots | f173a00b | f173a00b | – | – | – |
| SamuraiBoss / Pit | mmo-r only | | | | Pit on hub |

Bold cells are still drift on purpose: a newer Hub would switch on Origin systems (props, chests, NPCs)
in the lobby and dungeon worlds, and Fishing/Hub/StressBots on mmo-d are a removal decision.
Old Farming left mmo-d on 10-19 (it piled ~283k ambient mobs into one chunk).

## Content state (verified against live 2026-10-19)

Live and committed: onboarding v2 (Acts I–VII incl. skill-isle lessons), exile crossing,
explore-chest scatter, pet bodies, Saitama main island (parts 1–3 + harbour), Codex/Skills
overhaul, GIGA texture pack, German overlay, player HUD + boss bars, HUD panel + radar,
combat system (guard, cadence, mobility, stamina HUD), SamuraiBoss.

Known gaps:
- AetherMobs on mmo-c/mmo-i is the 10-04 build without pets; mmo-d runs an old `AetherMobs.jar`.
- 18 newer Quests lines lack German (`validate_de_overlay.py`: `ui.roads.*`,
  `dialogs.amethyst_mines_guide_intro`).
- `CombatTeach` (tutorial combat beat) is still a stub.
- The daily Veins reset in AetherionMining (world delete + synchronous FAWE paste) caused a
  watchdog stop on mmo-r on 10-18; needs an async/chunked rework.

## Uncommitted work in other worktrees

| What | Worktree |
|---|---|
| Pet bodies WIP | `aethermobs-pokemon-pets-bdadef` (40 dirty) |
| Dialog voice | `dialog-voice-german-localization-651358` (13 dirty) |
| Website | `aetherion-website` (49 dirty) |

29 worktrees exist, 14 with uncommitted files. All branches were mirrored to
`origin/backup/2026-10-02/*` on 10-02.
