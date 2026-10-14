# HUD Polish — Opus runs (order)

Ship as **two Opus sessions**. Do not merge into one — player HUD and bossbar theming are both heavy (art + fonts + send throttling + GUI-scale QA).

| Order | Prompt | Scope |
|-------|--------|--------|
| **1st** | [`OPUS_HUD_POLISH_A_PLAYER_PROMPT.md`](OPUS_HUD_POLISH_A_PLAYER_PROMPT.md) | Hunger hide · custom HP · Defense · Weapon CD · ActionBar layout |
| **2nd** | [`OPUS_HUD_POLISH_B_BOSSBARS_PROMPT.md`](OPUS_HUD_POLISH_B_BOSSBARS_PROMPT.md) | Per-type bossbar frames/icons · epic bosses · quiet hints · QuestBars leases |

**Why this order:** Player combat HUD is always on; bossbars are situational and already coordinated via `QuestBars`. Finish the bottom bar so ActionBar glyph math is stable before restyling the top bar.

**Not in these runs:** TalkUx bubble (LOCKED), Manager chest chrome (already shipped), Iris shader pack, gameplay/stat formula changes.

Follow [`OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md): code + pack assets only, no ship theater, no deploy unless Robb asks.
