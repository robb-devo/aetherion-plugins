# Aetherion — Project state (read this first)

Snapshot: **2026-10-21**. One page so a new session does not have to rescan the
repo. Update the dated sections when they change.
Open work and priorities: [BETA_AUDIT_2026-10-19.md](BETA_AUDIT_2026-10-19.md).
Ops, hosting and access details are deliberately **not** in this repo; they live in
the private `aetherion-ops/` folder on Robb's PC (`docs/OPUS_SESSION_BIBLE.md`,
`docs/LIVE_STATE_*.md`, `docs/BETA_AUDIT_OPS_*.md`).

## What this is

Minecraft MMORPG network "Aetherion" on Paper 1.21.1 behind a Velocity proxy.
17 Maven modules (one plugin each) plus the Velocity plugin AetherionProxy, ~470k lines of Java, 4 unit tests.
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
| AetherionProxy | Velocity plugin: sends the resource pack once per session (own build, JDK 25) | 0.2k |

Backends: **hub** (lobby + Pit), **mmo-r** (main world, capital, isles, bosses, market),
**mmo-d** (dungeons only), **mmo-c** (exile/story), **mmo-i** (private + guild islands).

## Where the truth lives

- **GitHub `main` = live code.** Every jar on the servers is built from it.
- The local ship worktree is `IdeaProjects/.claude/worktrees/mining-eldervale-progression-65660c`
  (branch `ship/live-hardening`). Build and deploy from there, commit, push, merge to `main`.
- The `IdeaProjects/` root checkout (branch `claude/polish-pets`) is **stale (09-28)** with
  ~6000 dirty files. Do not build or commit from it.
- Agent prompts and briefs live in `docs/prompts/`; they are history, not specs.

## Live plugin builds (sha1 prefix, 2026-10-21)

Same on every server that loads them: Core `536b35c5`, Items `e0b18ce1` (10-21, HUD carrier + menu icons; mmo-r, mmo-d, mmo-c, mmo-i; not on hub), Quests `06201980` (10-20; not on hub),
BossEngine `0b4760db`, Gnomon `684013f0`, AetherMobs `078432e9`, Guilds `60342686`. Each of these is
byte-identical to a build of `main` (classes and resources).

| Jar | mmo-r | mmo-d | mmo-c | mmo-i | hub |
|---|---|---|---|---|---|
| Dungeons | sha256 cd896d99 (10-20, PR #76) | same | – | – | – |
| Mining / Foraging / Farming / Fishing | d55c57f6 / 8d58797b (10-20) / 5e8a58ce / a6aae3f0 | – | same as mmo-r | – | – |
| Hub | 96ed909c | – | – | – | **d1a097e7 (10-05)** |
| Beta | ed9d1621 | ed9d1621 | – | – | – |
| SamuraiBoss / Pit | mmo-r only | | | | Pit `7e494d00` (10-21, HP readout) on hub |

The proxy runs **AetherionProxy** `44947554` (Velocity 4, built on JDK 25, see "Resource pack").

The bold cell is drift on purpose: a newer Hub would switch on Origin systems (props, chests, NPCs) in the
lobby. Hub and Fishing left mmo-d on 10-20 (jars in `plugins.bak-pre-mmod-cleanup-*`, data folders kept);
there Dungeons registers `/spawn` and `/hub` itself and sends players back to the main world. StressBots left mmo-r and mmo-d on 10-19 (jar in the ship backup, data folder kept).
Old Farming left mmo-d on 10-19 (it piled ~283k ambient mobs into one chunk).

## Resource pack (2026-10-21)

**One pack for the whole network, sent once by the proxy.** `Aetherion_texturepack.zip` sha1 `52784940`.
The Velocity plugin `AetherionProxy` offers it when a player first reaches a backend (required, hash read
from the served zip); no backend advertises a pack any more (`resource-pack=` empty everywhere), so moving
between hub, mmo-r, mmo-d, mmo-c and mmo-i never reloads resources. The hub has the full pack too: vanilla
hearts are hidden network-wide, and the Pit shows HP as text on the ActionBar.

Pack swap: build with `AetherionItems/tools/build_served_pack.py <live.zip> <out.zip> <1.21.1 client jar>`
(the live zip plus exactly the HUD/GUI paths the generators own; never copy the repo pack's item models or
textures, they are stale) and upload it over the served zip. That is all: the proxy re-reads the zip's hash
every minute and sends a changed pack to everyone online. No `server.properties` edits, no restarts.

HUD (10-21): status frame top left and radar top right ride an invisible BLUE boss bar
(`de.aetherion.items.hud.HudCarrier`), not the ActionBar, so they no longer fade on lag or vanish when
something else writes to the ActionBar. Art, font and shaders: `tools/gen_carrier.py`; the text shader also
caps the HUD's size on large GUI scales. Manager and menu icons: `tools/gen_gui_icons.py` (16 px objects in
each menu's accent colour; locked tabs keep their picture, greyed with a padlock).

## Content state (verified against live 2026-10-19)

Live and committed: onboarding v2 (Acts I–VII incl. skill-isle lessons), exile crossing,
explore-chest scatter, pet bodies, Saitama main island (parts 1–3 + harbour), Codex/Skills
overhaul, GIGA texture pack, German overlay, player HUD + boss bars, HUD frame + radar on the boss-bar carrier
(10-21, radar quest marker shows above/below), Manager GUI icons (redrawn 10-21) and invisible fillers,
combat system (guard, cadence, mobility, stamina HUD), SamuraiBoss.

Known gaps:
- AetherMobs on mmo-c/mmo-i is the 10-04 build without pets; mmo-d runs an old `AetherMobs.jar`.
- 18 newer Quests lines lack German (`validate_de_overlay.py`: `ui.roads.*`,
  `dialogs.amethyst_mines_guide_intro`).
- `CombatTeach` (tutorial combat beat) is still a stub.
- Veins reset fixed 10-20 (PR #71): the schematic is pasted in place on FAWE's async queue (~1 s, no
  main-thread stall). mmo-c has `veins.daily-reset: false`. The Eldervale and Forage isle pastes moved to
  the same queue (PR #73, Mining `d55c57f6` / Foraging `8d58797b` on mmo-r and mmo-c). Exile, Hub, Pit and
  Guilds already paste async; Dungeons endless stays synchronous until mmo-d has FAWE.
- ServerWaypoint 2.8.4 on mmo-r fails to enable (its data file has the wrong format); third-party.
- `AetherionItems` test sources do not compile, so local builds need `-Dmaven.test.skip=true`.
  `CelestialDyeTest` asserts an unshipped rank change (Monkey/Citrus/Beta/MVP++ no longer LuckPerms
  parents, which would drop Monkey's content kit); see closed PR #74. Needs a product decision.
- Quests on `main` drops two unused `de.yml` keys (PR #72); live Quests still carries them until the
  next Quests deploy. No behaviour difference.

## Uncommitted work in other worktrees

| What | Worktree |
|---|---|
| Pet bodies WIP | `aethermobs-pokemon-pets-bdadef` (40 dirty) |
| Dialog voice | `dialog-voice-german-localization-651358` (13 dirty) |
| Website | `aetherion-website` (49 dirty) |

29 worktrees exist, 14 with uncommitted files. All branches were mirrored to
`origin/backup/2026-10-02/*` on 10-02.
