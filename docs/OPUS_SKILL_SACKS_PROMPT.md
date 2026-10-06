# Opus / Sonnet — Skill Sacks T1–T3 (simple)

Workspace root:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Stay in this root.  
**No deploy. No restart. No commit.** Edit source directly. Follow `docs/OPUS_PROMPT_HYGIENE.md`.

Sonnet is fine for this. Keep it boring and consistent — one design, apply to every skill. Do **not** invent recipes, shops, quests, or economy rebalance.

---

## Mission

Hypixel-style **skill sacks**: per gathering skill, **3 tiers**, clean open-UI.  
Design **once**, then stamp it onto each skill with that skill’s resource list. Stop.

---

## Read first (extend, don’t rebuild)

| Area | Path |
|---|---|
| Existing sacks | `AetherionItems/.../storage/SackType.java`, `SackItems.java`, `SackInventory.java`, `SackListener.java`, `BoosterSackMenu.java` |
| Storage | `AetherionItems/.../storage/AetherionStorage.java`, `StorageGUI.java`, `StorageInventory.java` |
| Compress / compact | `CompressedResource`, `EconomyCurve`, skill drop IDs in Mining / Farming / Foraging / Fishing / Quarry |
| Hygiene | `docs/OPUS_PROMPT_HYGIENE.md` |

LOCKED if conflict: TalkUx shell, boosters stack 64, Anvil sockets, ranks/`player-ranks.yml`, Blossom Blade / Gravwell combat feel, ShutdownCountdown, Borderlands vials.

---

## Design (one pattern for all)

| Tier | Craft feel (later — **no recipes now**) | Holds | Capacity vibe |
|---|---|---|---|
| **T1** | Easy | Raw skill drops | Good amount |
| **T2** | Mid | Raw + **compressed** | Much more |
| **T3** | Harder | Raw + compressed + **compact** | Huge |

Skills (at least): Mining, Farming, Foraging, Fishing. If Quarry / other gather skills already have a clear drop set, include them with the same template. Skip combat / pets / dungeon loot.

**UI:** one clean chest/menu language for every sack (titles, panes, lore). Per-slot = one resource type + count. Same look as Booster Sack / polished storage — not a junk chest dump. EN canonical; DE only if sack/storage already has lang hooks.

**Pickup:** while the sack is in inventory (or installed in Aetherion Storage — see below), matching drops auto-absorb into the sack (leftover → inv / ground as today).

---

## Special rule — Aetherion Storage

Sacks must be placeable **into Aetherion Storage** and **keep collecting** while sitting there (same absorb rules as in inventory). Wire this cleanly into the existing storage path — don’t invent a second vault.

---

## Out of scope

- Recipes / crafting trees / vendors  
- Balancing exact max counts to the decimal (sensible round numbers OK)  
- New skills, new ores, texture pack overhaul  
- Touching LOCKED systems  

---

## Done when

1. One shared sack framework (tier + skill + accepted IDs + capacity).  
2. T1/T2/T3 item for each skill, openable with the shared clean UI.  
3. Auto-collect works from inventory **and** from Aetherion Storage.  
4. Dev give / existing admin path can spawn them for testing.  

Stop after that. No essays, no recipe chapter, no “phase 2” docs.
