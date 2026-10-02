# Opus prompt — Player UI (Sidebar + TAB)

Copy everything below the line into the Cloud workspace chat.

---

You are polishing **Aetherion’s permanent player UI** — the **right-hand scoreboard (sidebar)** and the **TAB player list** (header/footer, prefixes, sorting) on Paper **1.21.1**.

## First read (in order)

1. `BossEngine/docs/OPUS_PLAYER_UI_AUDIT.md`  
2. `BossEngine/docs/OPUS_PLAYER_UI_HANDOFF.md`  
3. Then open **only** the files listed under “Must understand” / “Likely touch targets”. Do **not** ingest the entire monorepo.

## You are NOT here to

- Rewrite the UI stack or replace **TAB** with a custom packet scoreboard  
- Redesign inventory GUIs, item lore, or boss VFX  
- Change gameplay, economy rates, or progression formulas  
- Touch **LOCKED** systems: signature weapons (Blossom Blade / Gravwell Cleaver), boosters/sockets/anvil, **special-rank dyes / Admin identity / Dev Menu ranks / `player-ranks.yml` assignment rules**, shutdown countdown  
- Flatten special ranks to plain text or move special rank after level rank  
- Grow AetherionCore into UI ownership  
- Add new plugin dependencies  
- Drive-by-refactor unrelated plugins  
- Deploy/restart production unless this environment already has an approved deploy path (prefer commit/branch + verify notes)

## You ARE here to

Make sidebar + TAB feel **obviously more premium** while staying **Vanilla/TAB-shaped**.

Priority order:

1. Clarity & hierarchy (what matters at a glance)  
2. Tone (Aetherion harbour / mmor — not generic lobby paste)  
3. Useful context (area, economy, optional quest crumb)  
4. Restraint (no clutter)  
5. Performance (respect refresh intervals + existing dye paint loop)  
6. Technical quality  

## Existing architecture (respect it)

- **Presentation:** TAB (`config.yml` scoreboard + `groups.yml` + `animations.yml` + header-footer).  
- **Data:** PlaceholderAPI  
  - `%aetherion_*%` → `CoinPlaceholderExpansion` + `RankBadgeService`  
  - `%aetherionquests_*%` → quest compass/title (available; **not** on sidebar today)  
- **Rebind:** `TabScoreboardRebind` after ranks load — refresh placeholder cache, call `sendHighestScoreboard`, **never** pin via `showScoreboard`.  
- **Live sidebar:** one board `sidebar` (unified; old ultra/main split is historical). Title uses `%animation:Aetherion%`. Lines: ultra rank, level title, name, AREA, Coins.  
- **Dye animation:** `RankBadgeService.paintOnlineDyed` every 8 ticks for extras — keep; don’t hot-loop harder.

## Creative license (bounded)

Improve layout, copy, spacing, conditional lines, header/footer tone, refresh tuning, and light PAPI helpers that make TAB lines cleaner.

Examples of good directions (pick what earns its keep — quality over quantity):

- Collapse empty ultra line / smarter blank handling  
- One **quest** or **XP** crumb using existing placeholders  
- Header/footer that fits **mmor** (today’s “Netzwerk Lobby” reads wrong on the play server)  
- Quieter visual hierarchy (rules, section labels, animation restraint)  
- At most one extra scoreboard with a **clear** `display-condition` (e.g. dungeon/boss) — skip if not crisp  

## Explicit KEEP (leave alone unless broken)

- Special rank visuals (Monkey / Citrus / MVP++ / Admin / Beta) and sort-before-level intent  
- `player-ranks.yml` + Dev Menu as sole special-rank assignment path  
- `TabScoreboardRebind` approach  
- PAPI id `aetherion` / existing placeholder names (add; don’t rename live keys casually)  
- Guild-world TAB **layout** (out of scope unless broken)  
- Harbour Hour / quest ActionBar juice (don’t regress; don’t duplicate into a noisy sidebar)

## Explicit AVOID

- Permanent TAB BossBar as main HUD  
- Full combat stat block on the sidebar  
- Skill loadout grid on the sidebar  
- Custom client/resource-pack HUD as the primary solution  
- Per-player Bukkit scoreboards fighting TAB  
- Overengineering multi-mode boards “just because”

## Process

1. Confirm live/intended TAB YAML + PAPI wiring against the audit.  
2. Implement the smallest set of changes with the highest visible quality gain.  
3. Build what you touch (`AetherionItems` / `AetherionQuests` if code changes).  
4. Note how to verify: no ultra / with ultra; area change; optional active quest; Monkey or Admin still paints correctly.  
5. End with a short **changed vs consciously unchanged** summary.

## Tone

Premium MMO restraint. Match Aetherion’s harbour craft — dry, clear, a little stylish — not arcade spam.

---
