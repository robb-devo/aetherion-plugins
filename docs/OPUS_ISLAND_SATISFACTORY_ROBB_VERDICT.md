# Opus — Private Island / Factory: Robb verdict (2026-10-09)

**Paste into a fresh Claude High / Opus session.** No deploy/restart/commit unless Robb asks.
Workspace: `C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Follow `docs/OPUS_PROMPT_HYGIENE.md`. TalkUx shell **LOCKED**. Boosters / ranks / signature weapons **LOCKED**.

---

```text
# AETHERION — Private islands are BAD. Make them Satisfactory-clean.

Robb + friend playtest (yesterday): personal island tutorial is unreadable, conveyor/belt placement feels broken/unoptimized, nothing “clicks”. They both rage-quit the feature. This is not polish — rewrite the feel until a dumb friend gets it in minutes.

You already have docs:
- docs/OPUS_ISLAND_SATISFACTORY_GO_PROMPT.md
- docs/OPUS_ISLAND_FACTORY_ONBOARD_110_PROMPT.md
- docs/OPUS_PERSONAL_ISLAND_SATISFACTORY_BRIEF.md

Those stand. This addendum is the HARD priority after live playtest.

## Absolute north star

Satisfactory in Minecraft:
1) One Build Tool in hand
2) Pick part (scroll / F picker) — unlocked only
3) Ghost → place → cost from island storage
4) Belts snap / chain like Satisfactory — not a puzzle
5) One next step always obvious (Hollis + island bar + /island agree)
6) Zero menu circus to finish the first Compacted Cobble line

If something needs a FAQ, delete the FAQ and fix the UX.

## What Robb hates right now

1. Tutorial / Hollis: “Man checkt 0 was.” Too much talk, unclear next action, markers/chips don’t carry the player.
2. Placement especially BELTS: feels shit, not optimized, not Satisfactory. Chaining, aim, ghost, rotate, cancel, cost feedback — all must feel instant and obvious.
3. Possible regression when “own island server / island create” was wired (IslandAccess / IslandsHost / CharacterTransport). If create/join/teleport/build-tool grant is broken or soft-locks the tour, FIX that first — don’t add features on a broken door.
4. No new invented systems. No new plugins. Extend Guilds: IslandGuide, FactoryGoals, PlacementService, LogisticsService, PropBodies, BeltVisuals, Hub menus.

## Mandatory deliverables (this session)

### A) Door + grant path (regression check)
Trace fresh personal island create → world/host ready → Hollis present → Build Tool in inventory → first free hut unlock.
If anything from IslandAccess / IslandsHost / network transfer broke local island create on MMO-R (or wherever islands live), repair it. Additive. Don’t break Exile/Hub transfers.

### B) Belt placement = Satisfactory
In PlacementService (+ BeltVisuals / logistics place path):
- Start click → end click (straight or one bend) must be idiot-proof
- Ghost always visible and truthful (green place / red blocked) with ONE short action line
- Chaining continues without re-picking Belt
- Cancel is obvious (Q / sneak+cancel / left-click empty — pick ONE and teach it once)
- Cost toast: “−N Cobble from storage” or “free (tour)” — never silent fail
- Max-run / blocked path: say WHY in ≤1 line
Kill any double-confirm, menu diversion, or “open building while placing belt” footgun during belt mode unless it’s clearly intentional.

### C) Tutorial that a friend survives
IslandGuide / FactoryGoals / IslandBar:
- One sentence next step on bar at all times
- Hollis TalkUx: short lines, one chip = the action (“Build the hut here”), not lore essays
- Markers on pads stay until that step is done
- Act 1: Hut → Quarry → Belt into hut (free tiles)
- Act 2: Hub → Mill → Forge → Compacted in storage (generous free unlocks/mats on tour — already intended)
- Skip/resume via /island or Hollis “What’s next?” never soft-locks
- EN copy first; DE only if island already has lang hooks (don’t invent a new i18n layer)

### D) Done when (spot-check)
1. Fresh island with zero factory knowledge: finishes Compacted Cobble without asking Discord.
2. Belt from quarry chute to hut in under 30 seconds, first try.
3. Friend can explain: Build Tool places, Hub unlocks, belts move goods, Mill/Forge refine.
4. Existing islands still load (AtomicYaml, structure ids). No wipe.

## Out of scope
TalkUx architecture rewrite. Guild social rewrite. New factory plugin. Schematic-as-look for machines (props stay). Deploy/restart (Robb’s other agent).

## End
Compile Guilds. Summarize files + player-facing before/after in ≤15 lines. No “pass plan”.
```
