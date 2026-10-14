# Opus Prompt — Explore Chest Scatter (Common → Mythic)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Target:** Paper **1.21.1**, Java 21. Server language = **English** (German via `/language de`).  
**Tree:** `mining-eldervale-progression` worktree.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md) — edit real tree. Deploy **Quests** (and Core only if you touch it) when scatter is green; restart MMOR once. No commit unless Robb asks.  
**Model:** Max / Extra. Not Ultrathink.

---

## Mission (one sentence)

Scatter **authored explore loot chests** across the main-world journey (Harbour → Capital → Borderlands edge, skill approaches) so rarity reads **Common → Mythic**, finds feel earned, and nothing breaks TalkUx / story gates.

---

## Already done — leave alone

| System | Status |
|--------|--------|
| Borderlands cliff + Veil Quay + Ferryman crossing | Done (prior Opus + Core guards). Do **not** re-paste quay, widen clears, or touch causeway/BL. |
| SamuraiBoss | Live (50k HP, dmg×7, BarChrome). Out of scope. |
| Combat elevate | Saturday — out of scope. |
| Tutorial overhaul | After combat — out of scope. |

---

## Code truth (start here)

| Piece | Path |
|-------|------|
| Kinds + placer | `AetherionQuests/.../chest/ExploreChestKind.java`, `ExploreChestListener`, `ExploreChestService` |
| Loot tables | `ExploreChestLoot.java` |
| Props / persistence | `ExploreChestProp`, `explore-chests.yml` (plugin data) |
| Lore hook | `LoreFinds.onExploreChest` |

**Note:** enum today is **Rare / Epic / Legendary / Mythic**. If Robb’s “Common → Mythic” needs Common + Uncommon, **add them** (kind + loot + placer item + prop) instead of faking with Rare everywhere. Keep Rare+ as the mid/high ladder.

---

## LOCKED

TalkUx shell · Blossom Blade / Gravwell · boosters / Anvil / ranks / ShutdownCountdown / Borderlands vials · additive ship · Exile quay/BL clears · combat · tutorial rewrite.

---

## Do this

1. Inventory what exists live + in tree (how many chests, where, which kinds).
2. Design a **light scatter plan** by district density (more Common/Uncommon near early roads; Rare+ rarer and better hidden; Mythic few, landmark-adjacent, never on the ferry path or inside boss pads).
3. Implement gaps (kinds/loot/prop if Common/Uncommon missing). Prefer the existing place/persist pipeline — no parallel chest system.
4. Place / author positions (YAML + in-world as the system already does). No random every restart unless the system already supports it — **stable authored finds**.
5. Smoke: place → open → loot → despawn/cooldown behavior matches design; EN strings; DE mirror if messages exist.
6. Deploy Quests → MMOR restart → ≤15-line end note.

---

## Out of scope

Quay / causeway / Seraphine clear · Samurai · combat · tutorial · Homie bug list · Exile island rewrite · dungeon loot tables.

---

## End note (≤15 lines)

Counts per rarity, districts touched, jars shipped, anything Robb should walk.
