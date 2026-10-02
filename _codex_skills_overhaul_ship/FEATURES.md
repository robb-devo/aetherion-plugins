# Codex + Skills overhaul — features at a glance

Full design and the 30-point test sheet: `files\docs\CODEX_SKILLS_OVERHAUL.md`.

## One system, three ledgers
- **Shared chrome** on every page: a tab strip (Codex · Collection · Bestiary · Journal · Skills · Milestones) and the same nav row. Every page is one click from every other. Locked tabs show the progression hint.
- **Nine-tier ladders (I–IX)** on every entry, spaced by scale (Bulk … Precious, Common mob … Boss).
- **Claim loop**: each tier you reach pays coins (counted as lifetime, which opens skill slots), Aetherion XP, and Shards at V/VII/IX. You can claim from:
  - the card
  - the ladder
  - the chat `[CLAIM]` button
  - Claim Ledger
  - **Claim All**
- **Milestones**: every 10 ledger levels. Collection gives **+1 Fortune**; Bestiary gives **+1 Damage +2 Health**. Both are permanent, and `codex-perks: false` turns them off.
- **Back pay**: rewards are retroactive, and players get a join reminder when some are waiting.

## Collection — `/collection`
- 76 entries on 8 shelves plus *Everything*. **New Catches shelf** (Cod, Salmon, Tropical Fish, Pufferfish, Treasure, Junk) plus mud, sandstone, cactus, mushrooms, giant mushrooms, chorus, and the raw/mineral ore blocks.
- Accuracy fixes:
  - ripe crops only
  - no stems or leaves
  - your own placed blocks don't count
  - **Vein Siphon vacuum now counts**
  - **fishing now counts**

## Bestiary — `/bestiary`
- **The Bosses tab works now.** It is built live from BossEngine templates (no `test_*` rigs) and reads the real boss ledger. Before, it always showed 0.
- **Sea Creatures shelf** (10 fishing encounter bands), **Ore Troll**, **Borderlands Sturdy / Brute / Crypt** variant tallies, and auto-cards for new dungeon mobs.

## Pages
- **Cards** show:
  - tier colour
  - progress bar
  - count / next
  - ladder pips
  - next reward
  - server rank
  - claim glow
  - `???` with a where-to-find hint while undiscovered
- **Sort** (5 modes) and **filter** (5 modes), remembered for the session.
- **Detail**:
  - clickable ladder
  - your record (rank, first-entry date, variants)
  - top-10 board
  - field notes, where **boss loot unlocks on your first kill**
  - reward totals
  - ◄ ► browsing that skips `???`
- **Codex hub** `/codex`:
  - profile and Codex score
  - five tiles
  - Claim All
  - *Next up* (the 5 entries closest to their next tier)
- **Milestones** `/codex milestones`: Collection and Bestiary roads plus rarity-seal counts.
- **Boss Journal**: All / World / Dungeon shelves, slain bosses first, loot previews.

## Skills
- **SkillMenu header row**: Codex · Overview · Readout · Presets · Profile · Rarity Seals · Next slot · This Session · Guide.
- **Skill Overview** `/skills overview`:
  - all 60 skills
  - 4 sorts and 5 filters
  - right-click to equip or unequip
  - click to claim a ready seal or jump to the skill
- **Loadout Presets** `/skills presets`, `/skills preset <1-3>`: save, load and clear. Presets 2 and 3 open at Aetherion Lv 10 and 25.
- **Rarity Seals**: a one-off reward per skill per rarity (Uncommon 2.5k + 10 Shards … Mythic 62.5k + 400 Shards). There is a `[CLAIM RARITY SEAL]` button on every rarity-up, and Shift-click claims from the pool.
- **This Session**: skill XP, level-ups, XP/h and your top skill since login.

## Glue & tools
- **Manager tiles** show `Level N · ✦ K to claim`.
- **Placeholders**: `%aetherion_collection_level%`, `%aetherion_bestiary_level%`, `%aetherion_codex_score%`, `%aetherion_codex_claimable%`.
- **Admin** `/codex dev <player> set <key> <n> | resetclaims | info` (`aetherion.dev`).
