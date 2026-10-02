# mmor TAB polish (sidebar, TAB list, header/footer)

Proposed live TAB files for MMO-R (`a28d676a-…/plugins/TAB/`). These are a copy of
the live files from 2026-09-27 19:06 with only the presentation changes below.
Nothing is deployed yet. `live-to-proposed.diff` shows every changed line.

Snapshot sha256 (first 16): `config.yml` `400455013b234692`, `animations.yml` `694bade13ded823f`.
If the live files no longer match these, re-diff before copying anything over.

No Java changed. Every line uses PAPI keys that already exist.

## Sidebar

```
      ✦ AETHERION ✦           steady gold, one white sheen every ~5s
 ────────────────
 [Monkey]                     only with a special rank (unchanged placeholder)
 Adventurer · Lv 12           %aetherion_level_title% + %aetherion_level_colored%
 ██████████ 45/100            %aetherion_level_bar%
 
 Area Anker Harbour
 Coins 1,234
 ────────────────
```

## Changed

| Where | Before | After | Why |
|---|---|---|---|
| Sidebar lines | 12 lines: emoji headings (`📍 AREA`, `💰 ECONOMY`), "Coins" label and value on separate lines, own name | 7–8 lines: level + number, XP bar, one `Area` line, one `Coins` line | The level number and XP progress were missing. Emoji headings rendered as Unifont glyphs. Your own name is already in TAB, chat and your nametag. |
| Sidebar title | `Aetherion` animation toggled bold every 200ms, so the whole sidebar changed width | New `AetherionTitle`: always bold, steady 3s, then one sheen over 2s | Width no longer jumps. Fewer title packets (repeated frames are not resent). The old `Aetherion` animation is still in the file for rollback. |
| Header | `Netzwerk Lobby` | `— MMO Realm —` | The hub subtitle was showing on the play server. |
| Footer | `Online: x/y` | `Online x/y · Ping Nms` | Ping moved here from the player-list objective. |
| `playerlist-objective` | enabled | disabled | TAB warns on every boot that the layout breaks it and it "will only look bad and consume resources". |
| `layout.default` (the mmor TAB list) | Server-panel filler: Status Online, TPS, RAM, Version, "The world of adventure", ✦ AETHERION ✦ three times, invalid `25\|` `30\|` `35\|` slots | 4 columns: **Profile / Purse / Area + Weather / Pet**, then **Players**, **Combat**, **Gathering**. Stat lines are unchanged, only moved. | Removes debug info and repeated branding. Wires in `%aetherion_weather%`, which was written for TAB but never used. |
| `layout.guild_island` | `25\|`, `35\|` (TAB warns they are invalid) | `'25\| '`, `'35\| '` | Fixes the boot warnings only. The guild layout is otherwise unchanged. |
| Refresh intervals | Everything at the 500ms default except area | 1000ms for level, coins and stat lines; 2000ms for shards, weather and pet name/level | These only change on XP, coin, gear or pet events. Stat lines go through the equipment scan, so this halves that work. |

## Consciously unchanged

- **Special ranks:** `%aetherion_ultra_rank%` line, `%aetherion_tab_prefix%` in `groups.yml`, sorting order, dye animations, Dev Menu / `player-ranks.yml`. Both dye placeholders stay at the 500ms default so the animation speed is the same. `groups.yml` is untouched.
- `TabScoreboardRebind`, the single `sidebar` board, `delay-on-join-milliseconds: 0`, `/sb`.
- **Quest crumb:** skipped. `QuestProgressDisplay` already keeps a BossBar with the quest title, objective counts and compass arrow, and `QuestHint` uses the ActionBar. A sidebar line would be a third copy.
- **Second board with `display-condition`:** skipped. Dungeons run on MMO-D. mmor has no clear combat or boss state to key on.
- Shards and weather on the sidebar: not added. Early players have 0 shards, and weather is `—` outside the Forage Isle. Both are in the TAB list instead.
- `belowname-objective`, bossbar (off), scoreboard-teams, `groups.yml`, and hub/MMO-D/MMO-C TAB configs.

## Correction to OPUS_PLAYER_UI_AUDIT.md

The audit says the TAB layout is only for the guild world. `layout.layouts.default` has no
`display-condition`, so it is the TAB list **every mmor player sees** outside `aether_guilds`.
That is why this pass edits it.

## Found, not fixed here

`NetworkPlayerDataSync` (AetherionDungeons `bridge/`, and the new untracked copy in AetherionCore
`network/`) runs `tab scoreboard show main <player>` 20 ticks after a transfer import. That is
the pinning call `TabScoreboardRebind` was written to avoid. Today it only logs
`No scoreboard found with the name "main"` (seen in latest.log), because `main` was removed in
the 2026-09-23 unify. If anything is ever named `main` again, transfers would pin it.

## Verify in-game

1. **No special rank** (fresh or wiped account): the sidebar starts with `Adventurer · Lv 1` and
   there is no blank line above it. The XP bar shows `0/100`. Gain XP and the bar moves within ~1s.
2. **Special rank** (Monkey / Citrus / Admin via Dev Menu): the badge line is above the level line
   and animates at the same speed as before. TAB list and nametag dyes are unchanged.
3. **Area:** walk Harbour → Ore Ridge. The `Area` line updates within ~2s. On the Forage Isle,
   TAB list → Area → Weather shows the weather, and `—` elsewhere.
4. **Coins:** sell something. `Coins` updates within ~1s.
5. **Title:** the sidebar width stays fixed while the sheen runs.
6. **TAB list:** header reads `MMO Realm`. There are 4 columns (Profile, Players, Combat,
   Gathering) and no TPS/RAM. Ping is in the footer.
7. **Guild island** (`aether_guilds`): the guild layout is unchanged.
8. **Console after `tab reload`:** the five `invalid fixed slot` warnings and the
   playerlist-objective warning (TAB currently reports "6 issues") are gone.
9. **Active quest:** the quest BossBar and ActionBar work as before, and the sidebar does not repeat them.

## Apply (not done yet)

From PowerShell (Git Bash's `ssh` rejects the BOM in `~/.ssh/config`):

```powershell
$S='/var/opt/minecraft/crafty/servers/a28d676a-03ef-40f1-9ac7-7a21c2ef6383/plugins/TAB'
$T=Get-Date -Format yyyyMMdd-HHmmss
ssh aetherion-hetzner "sha256sum $S/config.yml $S/animations.yml | cut -c1-16"   # must match the snapshot above
ssh aetherion-hetzner "cp -p $S/config.yml $S/config.yml.bak-pre-playerui-$T && cp -p $S/animations.yml $S/animations.yml.bak-pre-playerui-$T"
scp docs\tab\mmor\config.yml docs\tab\mmor\animations.yml "aetherion-hetzner:$S/"
ssh aetherion-hetzner "chown crafty:crafty $S/config.yml $S/animations.yml"
```

Then run `tab reload` in the Crafty console. No restart is needed.
**Rollback:** copy the `.bak-pre-playerui-*` files back, then run `tab reload`.

Do not copy this config to the hub. There, `hub-lobby` blanks the level placeholders, so the
sidebar level line would read ` · Lv `.
