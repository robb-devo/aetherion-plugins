# OPUS — Dungeon Floor 1: rooms that actually slap + gear that feels new

**Continue this chat.** Do **not** rescan the monorepo. Do **not** open Eggquelizer / Hub / Islands / Pets / Traversal. Only Dungeons Floor 1 (+ Items dungeon gear if needed).

Robb’s read (correct): the pool/templates exist, but rooms feel **empty / samey / underlit**. You can already build insane arenas (Seraphine, Eggquelizer stage, Guild islands). Floor 1 rooms must hit that bar. Gear also still feels like the same loop.

---

## Hard truths

1. **Templates are authored once.** No per-run random clutter spawn. Shuffle = which templates paste + order — **not** filling empty boxes each run.
2. Each `.nbt` must already look complete: dressed, lit, readable combat space, identity.
3. Quality bar = Seraphine arena / island buildings — **not** “technically a prison room.”
4. Joke/Eggquelizer/other bosses = **out of scope.**

---

## Goal A — Floor 1 room templates (priority)

### What exists
`AetherionDungeons/src/main/resources/structures/floor1/` + `pool.yml` (~20–25 entries: gatehouse, armory, cells, chapel, cistern, prison_*, throne, …).

### What’s wrong
They read as hollow shells: sparse props, weak lighting, low silhouette, little “this room has a story.”

### What to do
**Elevate / rebuild the template NBTs** (and `pool.yml` only if sizes/doors/pads must change).

Ship **20–25 rooms** that are:
- **Abgefuckt geil** — ruin, dread, Warden prison fantasy, vertical interest, strong silhouette
- **Fully dressed in the schematic** — furniture, debris, chains, banners, cells with stuff, tables, cages, cracked floors, blood/moss accents (palette-coherent)
- **Actually lit** — lanterns, soul lanterns, hidden light sources, contrast (dark corners + clear fight lanes). No pitch-black boxes, no flat floodlight either
- Clear **combat volume**, **loot anchors**, **door sockets**, **mob pads** (keep pool contract)
- Mix SMALL / MEDIUM / LARGE + lobby + boss room — keep classes if already wired
- Same overall F1 language (prison / warden / brick) so shuffle still feels one dungeon

**Do NOT:** invent a runtime decoration system that sprinkles random blocks each run.  
**Do:** make each template a finished set piece.

If a room can’t be saved, replace it. Prefer fewer god-tier rooms over 25 mediocre ones — but target still ~20–25 if quality holds.

Compare yourself to Seraphine’s authored space. If a room wouldn’t make Robb say “damn,” keep working.

---

## Goal B — Dungeon gear must feel different

Canonical gear: **AetherionItems** `items/dungeon/*` (armor, calling, tiers, identify/attune, relics). Dungeons only rolls via `ItemLootBridge` / `DungeonLootFx`.

Robb’s feel: still the same stuff. So this pass must produce **visible identity**, not only wiring:

- Floor-1 set / weapons / relics that look and read as **Warden’s Prison** (names, lore, materials/models if already in pack, rarity beats)
- Identify/attune loop still clear — but **drops must feel new when you open the chest**
- Additive: don’t wipe Special Sets / other DevMenu while touching dungeon give lists
- No balance essay — placeholder numbers OK if fantasy/readability wins

If you already “did gear” but only shuffled IDs: **redo the fantasy surface**.

---

## Out of scope

- Floors 2 & 3 redesign  
- Transfer/network (assume OK unless you broke it)  
- Eggquelizer, Seraphine combat, locked weapons, ranks, boosters, TalkUx, Islands  
- New monorepo audit / ship folders / README theater  

---

## Ship

1. Edit NBTs + `pool.yml` as needed under `structures/floor1/`  
2. Gear in Items only if Goal B needs it  
3. Compile Dungeons (± Items)  
4. **Do not deploy** unless Robb asks  
5. Short chat list: which rooms elevated, what gear changed, how to spot it in-game  

## Success

Walk into Floor 1 and think **“this room was built,”** not **“this is empty brick.”**  
Open a chest and think **“new shit,”** not **“same dungeon grey.”**

Ausrasten on rooms + gear identity. Stay in Dungeons (+ Items dungeon gear). Go.
