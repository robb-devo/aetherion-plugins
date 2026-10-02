# Player UI — Audit (Sidebar + TAB)

**Scope:** permanent right-hand scoreboard (sidebar) + player list (TAB) + closely related rank paint.  
**Not in scope:** inventory GUIs, item lore, boss spectacles, quest dialogs (except as placeholder sources).  
**Paper:** 1.21.1 · **TAB:** v5/v6 style (`TAB v6.1.2`) · **PAPI:** PlaceholderAPI  
**Live server sampled:** mmor (`a28d676a-…`) · 2026-09-27

---

## Architecture (how it actually works)

```
┌─────────────────────┐     PAPI %aetherion_*%      ┌──────────────────┐
│ AetherionItems      │ ─────────────────────────► │ TAB plugin       │
│ RankBadgeService    │                            │ config.yml       │
│ CoinPlaceholder…    │◄── rebind / refresh ───────│ scoreboard       │
│ Celestial/Citrus/…  │     TabScoreboardRebind    │ groups.yml       │
│ SkillService (skill │                            │ animations.yml   │
│   tab lines unused) │                            │ header-footer    │
└─────────────────────┘                            └──────────────────┘
         ▲
         │ %aetherionquests_*%
┌─────────────────────┐
│ AetherionQuests     │  compass / title / arrow
│ QuestPlaceholder…   │  (NOT wired into sidebar today)
└─────────────────────┘
```

There is **no custom Bukkit Scoreboard sidebar** owned by Aetherion for the permanent HUD. The visible right panel is **TAB’s scoreboard feature**, fed by **PlaceholderAPI** expansions from Items (+ Quests available but unused in the board).

Nametag / tablist prefixes are also TAB (`groups.yml` → `%aetherion_tab_prefix%`), with `RankBadgeService.paint()` additionally setting Adventure `displayName` / `playerListName` / `customName` (needed for animated dyes).

---

## Relevant code (Items)

| Piece | Path | Role |
|-------|------|------|
| Rank source of truth | `items/rank/RankBadgeService.java` | Special extras (`player-ranks.yml`), level titles, `tabPrefix` / `nametag` / `sidebarLine1/2`, LP sync, dye paint timer |
| TAB rebind | `items/rank/TabScoreboardRebind.java` | After extras load: refresh TAB placeholder cache + `sendHighestScoreboard` (does **not** force-pin a board) |
| Dyes (LOCKED look) | `CelestialDye`, `CitrusDye`, `RainbowDye` | Monkey #B2FFFF, Citrus, Beta rainbow; tick-based frames |
| PAPI | `items/placeholder/CoinPlaceholderExpansion.java` | Identifier `aetherion` — coins, shards, ranks, area, weather, stats, skills, HP, XP bar, … |
| Level titles | `items/skill/AetherionLevel.java` | Colored titles / tags / XP bar math |
| Skill tab helpers | `SkillService.tabSlotLine` / `tabExtraLine` | Exist as `%aetherion_skill_N%` — **not** on live sidebar |
| Paint loop | `RankBadgeService` ctor | `runTaskTimer(..., 80L, 8L)` → `paintOnlineDyed()` for players with extras |

## Relevant code (Quests)

| Piece | Path | Role |
|-------|------|------|
| Quest PAPI | `quests/placeholder/QuestPlaceholderExpansion.java` | `%aetherionquests_compass%`, `_title%`, `_arrow%`, `_distance%`, `_target%` |
| ActionBar hints | `QuestHint`, Harbour arrival, etc. | Ephemeral UX — separate from sidebar |

## Live TAB config (authoritative for what players see)

**Plugin folder:** `plugins/TAB/`  
**Boards today:** single board `sidebar` (unified 2026-09-23; old `ultra`/`main` pair archived in `config.yml.bak-pre-sidebar-unify-…`).

### Sidebar (current lines)

| # | Content |
|---|--------|
| title | `%animation:Aetherion%` (gold/yellow ✦ AETHERION ✦) |
| 0 | strikethrough rule |
| 1 | `%aetherion_ultra_rank%` (blank if no special) |
| 2 | `%aetherion_level_title%` |
| 3 | `&f%player%` |
| 4 | empty |
| 5–6 | 📍 AREA → `%aetherion_area%` |
| 7 | empty |
| 8–10 | 💰 ECONOMY / Coins / `%aetherion_coins%` |
| 11 | strikethrough rule |

- `delay-on-join-milliseconds`: **0** (rebind comments mentioning 2500ms are stale relative to live).  
- Toggle: `/sb` · not hidden by default.  
- **No** `display-condition` on the single board → no lobby/combat/quest modes.

### TAB list

- `groups.yml`: every group uses `%aetherion_tab_prefix%` + `&f%player%` (cosmetic + level tag from Items).  
- Sorting: special groups first (`admin,monkey,citrus,mvpplusplus,beta,…`) then name.  
- Header/footer **enabled**, design `default`: gradient ✦ AETHERION ✦, subtitle **“Netzwerk Lobby”**, footer online count.  
- TAB **layout** enabled for guild world (`aether_guilds`) — separate column layout; not the mmor overworld sidebar.

### Bossbar (TAB)

- Disabled globally.

---

## What the player sees (mmor overworld)

1. **Right sidebar:** brand animation, optional special rank, level title, name, area, coins.  
2. **TAB (player list):** gradient header “AETHERION” + “Netzwerk Lobby”; names with special dye (if any) + level-colored tag; sorted by rank weight.  
3. **Nametag above head:** same prefix path via TAB teams + paint.  
4. **Not on permanent HUD:** quest objective, XP-to-next, shards, combat stats, weather, skill loadout (placeholders exist).

---

## Contextual states?

| State | Sidebar/TAB aware today? |
|-------|---------------------------|
| Hub lobby (`RankBadgeService.hubLobby()`) | Partially — placeholders blank some level lines; Admin-only ultra on lobby |
| Special rank vs none | Ultra line empty vs filled (same board) |
| Area change | Yes — `%aetherion_area%` @ 2s refresh |
| Quest active | No (quest PAPI unused in TAB config) |
| Combat / boss | No |
| Forage weather | Placeholder `%aetherion_weather%` exists, unused |
| Guild island | Separate TAB **layout**, not scoreboard swap |

---

## Update / performance notes

| Mechanism | Interval / trigger | Risk |
|-----------|-------------------|------|
| TAB placeholder default refresh | 500ms | Fine for short boards |
| `%aetherion_area%` | 2000ms | Good |
| Dye `paintOnlineDyed` | every **8 ticks** (~2.5/s) for dyed players only | Necessary for animation; don’t increase frequency |
| `TabScoreboardRebind` | 1 / 60 / 120 ticks after rank events | Targeted; keep |
| Scoreboard line rebuild | TAB-owned | Avoid custom per-tick board rewrite |

**Team packet limit:** already documented in Borderlands outline code — don’t grow huge shared teams for UI.

---

## Minecraft / Paper client limits (relevant)

- Sidebar: ~15 lines practical; team/entry length limits; legacy `§` + some MiniMessage/gradients via TAB.  
- No true “panels”, icons beyond Unicode/emoji, or clickable sidebar rows.  
- TAB list header/footer are text only; layout slots are a different feature (fixed columns).  
- Permanent BossBar competes with event bossbars (forge, millstone, bosses) — currently off for TAB.  
- ActionBar is already used heavily for quests/feedback — don’t steal it for a second permanent HUD without design intent.

---

## KEEP (do not “improve away”)

- **Special ranks + dyes** (Admin / MVP++ / Monkey / Citrus / Beta) — LOCKED system; Dev Menu + `player-ranks.yml` only.  
- Special rank **before** level rank in prefix / list sorting intent.  
- `TabScoreboardRebind` pattern (refresh cache, `sendHighestScoreboard`, never pin with `showScoreboard`).  
- Single unified sidebar (ultra/main merge) unless a new condition board is clearly better.  
- PAPI identifier `aetherion` + existing placeholder names (add new keys; don’t rename live ones without migration).  
- Guild TAB layout as its own world feature.  
- Harbour Hour / quest ActionBar polish — orthogonal; don’t regress.

---

## Opportunities (real polish, not rewrite)

1. **Sidebar hierarchy** — blank ultra line wastes space; collapse / conditional lines; quieter section headers; less emoji noise if it feels Hypixel-generic.  
2. **Progression crumb** — `%aetherion_level_bar%` or into/needed XP (already computed) for early game clarity.  
3. **Quest crumb** — one line from `%aetherionquests_title%` / compass when active (early game win).  
4. **Header/footer tone** — “Netzwerk Lobby” on mmor is wrong; harbour / world-appropriate footer without clutter.  
5. **Refresh tuning** — coins/area vs rank lines; avoid 500ms on static strings.  
6. **Optional 2nd board** with TAB `display-condition` only if a clear mode exists (e.g. active boss / dungeon) — otherwise skip.  
7. **Wire unused high-value placeholders** carefully; leave skill grid off the sidebar (too tall / noisy).

---

## AVOID

- Replacing TAB with a custom packet scoreboard stack.  
- Permanent TAB BossBar as “main HUD”.  
- Rewriting `RankBadgeService` / dye math / Admin identity.  
- Full combat stat dump on sidebar.  
- Per-player Bukkit scoreboards fighting TAB.  
- Resource-pack-only HUD as the primary fix.  
- Touching signature weapons, boosters, anvil, shutdown countdown.  
- Growing AetherionCore for UI.
