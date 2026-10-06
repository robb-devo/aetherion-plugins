# Opus — Polish A→F (Talk timing · Harlow · Tackle pad · Recipe Book · Borderlands mobs · Isle guides)

**Fresh session. Creative + clean implementation pass.**  
Worktree: `mining-eldervale-progression-65660c` · branch `ship/live-hardening`  
Owner: Robb (A3therion). Call him **Robb**.

Standing hygiene: `docs/OPUS_PROMPT_HYGIENE.md` — edit source directly. **No** `_ship/` / README / manifest / apply-script theater. **No deploy.** Compile only if easy; if blocked, one line + stop.

Background (read, do not re-litigate): `docs/POLISH_BRIEFING.md`

---

## Mission

Ship the **six polish points** Robb playtested, in order **P-A → P-F**. Make onboarding and daily UX feel intentional: readable Talk, clear Farm/Fish roads, Recipe Book like Farming, combat only where it belongs, Skill-Isle guides that feel like Miss Ledger’s desk (bubble + chips).

**Taste bar:** Isle-guide TalkUx + Harlow/Tackle dialogs should feel *great* — short, characterful, chip-driven. Not a wall of text. Not a chest briefing pretending to be Talk.

**Not the job:** Progression curve / P5 HP / Opus open decisions (booster carry, +16%, ranged crit, `aetherion_home`) / Opening Orbit / Ankerhold cutover / network transfer.

---

## LOCKED (stop if conflict)

- **TalkUx shell** — keep TextDisplay bubble, typewriter, reply chips, quest card, per-listener visibility, chat mirror + chest Details/classic fallback. Timing + content only; no architecture rewrite, no bubble removal.
- Signature weapons (Blossom Blade / Gravwell), Boosters stack/intent, Anvil socket GUI, Ranks / `player-ranks.yml`, Shutdown countdown.
- Additive ship — do not gut DevMenus / existing cast / live roads.

If blocked: write `LOCKED … CONFLICT: …` and **stop**.

---

## Live facts (MMO-R scout 2026-10-03 — use these, don’t rediscover)

Server: Crafty `a28d676a-…` = MMO-R. World: `world`.

### Placed NPCs (`plugins/AetherionQuests/npcs.yml`)

| id | Display (live) | xyz / yaw | Notes |
|----|----------------|-----------|--------|
| `farmer` | Farmer | `-214.5 61 175.5` yaw `161.6` | Fields wheat quest |
| `lark` | Lark | `-225.5 58 149.5` | Pets |
| `farm_isle_guide` | **Harrow** | `-256.5 61 185.5` yaw `44.5` | Barn far end · portal guide · **rename display → Harlow** (id stays) |
| `fisher` | Tackle | `278.5 64 -418.5` yaw `-93.9` | Harbour pier · fishing lesson |
| `ledger` | Miss Ledger | `-1.5 58 -0.5` | Capital desk · chip template |
| `forage_pad_guide` | Twig | `365.5 65 -308.5` | Points toward forage pad road |
| `eldervale_welcome` | Maren | `56.5 90 490.5` | Mining isle landing |

Farmer ↔ Harlow distance ≈ 43 blocks (same farm yard). Harlow is already “other end of the barn”.

### Jump pads (live Hub `island-pads`)

| Pad | Hub footprint (min→max) | Landing target |
|-----|-------------------------|----------------|
| `origin_to_farming` | `-296..-291, 54, 339..341` | `-303.5 107 483` (Farm Isle) |
| `origin_to_fishing` | `-368..-363, 62, -489..-488` | `-380 90 -815.5` (Fishing Eldervale) |
| `origin_to_forage` | `435..441, 54..56, -237..-231` | `559.5 90 -208` |
| `origin_to_mining` | `20..24, 54, 304..306` | `53.5 91 482.5` |

Harlow should pitch **Farm Isle** (pad / “way down” / Farming 10) — not a void portal-world.

Tackle should pitch **Fishing Eldervale** and set a soft compass toward the **fishing pad** (`origin_to_fishing` hub side ≈ `-365.5 63 -488.5`), not only pin himself.

### Mob zones / areas (live)

- MobZone **borderlands**: `220.5 58 160.5` r=140 profile `borderlands`
- MobZone **eldervale**: `47.5 89.5 563.5` r=200 profile `eldervale` (deep packs — **conscious arena**, keep)
- Area `borderlands` disk also at `317.5` r=250
- NaturalSpawnGuard already cancels natural hostiles + culls outside BL/Eldervale
- T2 Sturdy / T3 Brute are stamped in `WildlifeLooks` for Borderlands surface weights

**P-E intent:** Hub / Capital / Farm / Harbour / roads must not get T2/T3 (ideally no hostiles). Keep: Borderlands, Crypt/Colosseum rites, Eldervale deep, BossEngine, pets, fishing encounters, MobZone CUSTOM. Audit leaks — don’t delete Eldervale combat.

---

## Points (implement all, ship-order quality)

### P-A — TalkUx unified line timing ★ first

**Bug:** `TalkUx.line` sets `cps = max(1.1, total/32)` → short lines finish almost instantly; mid-dialog rhythm feels uneven even though `DialogPace.LINE_GAP_TICKS = 60` queues between `LivingNpcProfile.say` calls.

**Soll:**
- Fixed chars/tick (same reading speed every line)
- Fixed **read gap after typewriter finishes** before the next queued line may start (everywhere — start *and* mid-dialog)
- Per-NPC voice blips/pitch stay; they must not blow the pace

Touch primarily: `TalkUx.java`, possibly `DialogPace.java` / `LivingNpcProfile.enqueueSay`. Config knobs under `talk-ux:` OK. **Shell LOCKED.**

### P-B — Farmer → Harlow (barn) + dialogs

**Soll:**
- Display rename **Harrow → Harlow** everywhere player-facing (`npcs.yml` name is live — code/lang/SkillRoads/CastBook/DE+EN). Internal id **`farm_isle_guide` stays**.
- Farmer lines: stop “portal world” framing; point to **Harlow behind the barn**
- Harlow: Farm Isle pitch (“way down” / pad / Farming 10) + `QuestHint` / yellow arrow pattern (already used by SkillRoads → `farm_isle_guide`)
- Optional tiny spot nudge only if needed — live spot is already barn-far-end; prefer dialog + hint over relocating

Files: `NpcListener` farmer/isle briefs, `lang/de.yml` (+ EN if present), `SkillRoads`, `CastBook`, `QuestNPCRegistry` display name, `LivingNpcProfile` labels if shown.

### P-C — Ledger → Tackle → Fishing pad waypoint

**Soll:**
- After tutorial / SkillRoads FISH (and Tackle’s own post-lesson beat): Tackle briefly explains Fishing Eldervale
- Soft compass / QuestHint toward **fishing pad** (Twig-pattern). Prefer new flavor NPC id e.g. `fish_pad_guide` at hub pad, **or** extend `QuestHint` with a location target if cleaner — pick one pattern, don’t invent two systems
- Do not break `a_good_catch` / dock pass / Fishmonger flow

Reference: `QuestHint` + `SkillRoads` + Twig (`forage_pad_guide`).

### P-D — Recipe Book Combat + Mining like Farming

**Today:** `RecipeBookGUI.groupedArmorThenSpecials` puts Mining/Combat armor on page 1, **tools+specials on the next page** (padded). Farming keeps **set tool as its own column beside the tier**.

**Soll:**
- Helmet→boots top-to-bottom
- **Tool beside the helm / tier** (Farming pattern)
- Prefer **more rows / denser page** over “one tool row then new page”
- Keep unlock/click paths working (`getGearRecipe`, listener)

Files: `RecipeBookGUI.java`, `RecipeBookLayout.java` (only if needed — `ARMOR_PIECES` / slots).

### P-E — Hostiles only in Borderlands (+ conscious arenas)

**Soll:**
- No T2/T3 (and ideally no hostiles) in Hub/Capital/Farm/Harbour/overworld roads
- Keep Borderlands + Crypt/Colosseum + Eldervale deep MobZone + BossEngine + pets + fishing encounters
- Audit: `NaturalSpawnGuard`, `MobZoneService`, `WildlifeLooks`, any stray CUSTOM spawners / zones outside BL

Harden code paths; don’t wipe Eldervale or rite content.

### P-F — Skill-Isle Guide NPCs (Ledger-style) ★ creative peak

**Soll:** Template once on **Farm** (Harlow / on-isle Field Warden), then mirror Forage / Fish / Mine.

Pattern to steal: `NpcListener.offerLedgerDesk` — bubble line + ≤5 reply chips + topic lines via `npcSay` + optional “More…” chest fallback.

| Isle | Existing hook | Elevate to |
|------|---------------|------------|
| Farm | `farm_isle_guide` (Harlow) hub; on-isle `IsleRole` / Field Warden | TalkUx desk: what is the isle, harvest rhythm, shops/pantry, how to leave |
| Forage | Twig hub pad; **Miss Canopy** `IsleGuideNpc` still opens **chest** `IsleGuideBriefingGUI` | Prefer TalkUx + chips; chest = Details/fallback only |
| Fish | Tackle hub; on-isle `LakeRole.HARBOURMASTER` (Maren Hollowtide) | Same desk feel near landing |
| Mine | `eldervale_welcome` (Maren) | Same desk feel at landing |

Use `TalkAccess` / `GuestSpeakers` when the speaker lives outside Quests (Foraging/Fishing plugins). Do **not** invent a second bubble system.

Content: short, witty, useful. Topics like Harvest / Pads / Shops / Safety / “Where next?”.

---

## Allowlist (prefer these)

**Quests**
- `talk/TalkUx.java`, `TalkText.java`, `DialogPace.java`
- `npc/LivingNpcProfile.java`, `CastBook.java`, `QuestNPCRegistry.java`
- `listener/NpcListener.java` (farmer / fisher / farm_isle_guide / ledger desk patterns)
- `ui/QuestHint.java`, `ui/SkillRoads.java`
- `lang/de.yml` (+ EN lang if present)
- `resources/config.yml` `talk-ux:` only as needed

**Items**
- `recipe/GUI/RecipeBookGUI.java`, `RecipeBookLayout.java`
- `world/NaturalSpawnGuard.java`, `MobZoneService.java`, `WildlifeLooks.java` (spawn/tier gates only)

**Isle plugins (P-F)**
- `AetherionForaging/.../npc/IsleGuideNpc.java`, `IsleGuideBriefingGUI.java` — TalkUx path via Core `TalkAccess`
- `AetherionFarming/.../isle/*` guide/talk hooks if on-isle Warden needs chips
- `AetherionFishing/.../isle/LakeCast.java` / `LakeRole` / talk entry for Harbourmaster
- Hub config **only if** a new pad-guide NPC needs a documented spot (prefer Quests `npcs.yml` placement instructions / code defaults — do not require live SCP)

New small helpers under `quests/talk/` or `quests/ui/` OK.

**Hard out:** BossEngine combat, Guilds island factory, Core network, signature weapons, ranks, boosters socket GUI, OpeningOrbit, progression P0–P6 rebalance.

---

## Deliverable

1. Implement **P-A → P-F** in the real worktree.
2. When done: **≤15 lines** — what changed per point, files touched, anything you want Robb to place in-world (`/questnpc` move Harlow / new fish pad guide).
3. No deploy. No ship folder. No essay.

---

## Paste boot (copy below the line into a new Opus session)

---

Read **only** `docs/OPUS_POLISH_A_F_PROMPT.md` (+ `docs/OPUS_PROMPT_HYGIENE.md` + `docs/POLISH_BRIEFING.md` if you need the backlog wording). Do not rescan the monorepo. Implement Polish **P-A → P-F** in `ship/live-hardening` worktree `mining-eldervale-progression-65660c`. TalkUx shell LOCKED (timing + content only). Rename display **Harrow → Harlow** (id `farm_isle_guide` stays). Use the live coords in the prompt. Isle guides = Ledger-style bubble + chips (Farm template first, then Forage/Fish/Mine). Recipe Book Combat/Mining = Farming column pattern (tool beside tier). Hostiles: Borderlands (+ conscious arenas), not Hub roads. Edit source directly. No `_ship/`, no deploy. Go.
