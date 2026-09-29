# Cloud chat handoff

Paste this repo + file into a new Cursor Cloud chat.

**Repo:** https://github.com/robb-devo/aetherion-plugins  
**Branch:** `cursor/dialog-de-overlay-cloud` (or `main` — same tip when last synced)

## Who you are

Pair agent for Robbi on the Aetherion Paper plugin suite. Full Java source is in this repo.

## Hard locks

- **Talk UX bubble** (`AetherionQuests` `TalkUx`): in-world TextDisplay speech bubble + reply chips + quest card — KEEP. Content lines may change; UX shell must not be removed/simplified.
- Hub map / Hub polish / Borderlands — out of scope unless Robbi says otherwise.
- Boosters stack/lore, Anvil 14 sockets, Rank/Admin UUID, Blossom Blade + Gravwell Cleaver combat, Shutdown countdown `10→8→6→4→2`.
- Additive ship only — never replace a richer live tree with a thinner one.

## Live thread (as of 2026-09-29)

Opus EXTRA in flight: **Dialog EN rewrite + German overlay** (Quests-first).

Done (Opus status):
- Java changes (~9 files), Quests compiles
- `ui.hint.*` → `ui.tip.*` (YAML key collision fix)
- `AetherionQuests/.../lang/de.yml` largely expanded
- `validate_de_overlay.py` green; Bukkit YAML accessor check OK
- EN bubble length OK for placed cast; Borderlands Rite Warden left alone

Next: ship folder like `_npc_dialogue_ship` (hash-guarded apply), then Robbi applies/deploys.

Also already live locally earlier: Items keep boosters on upgrade; BossEngine SFX on `MASTER` (not Hostile); pack SHA synced across backends.

## Useful docs

- [README.md](../README.md) — plugin module index
- [docs/npc/NPC_DIALOGUE_OVERHAUL.md](npc/NPC_DIALOGUE_OVERHAUL.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md) · [SETUP.md](../SETUP.md)

## Starter line for the agent

> Read `docs/CLOUD_CHAT_HANDOFF.md`. Stay on branch `cursor/dialog-de-overlay-cloud`. TalkUx locked. Wait for Robbi — or continue Opus dialog/DE ship if a ship folder appears.
