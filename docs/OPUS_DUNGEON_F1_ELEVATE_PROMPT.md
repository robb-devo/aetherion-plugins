# OPUS — Dungeon Floor 1 elevate + transfer sync (prompt)

**Role:** Ship a brutal clean dungeon transfer + elevate Floor 1 with a real room-template pool and matching gear. Additive. No silent overwrites of richer live systems.

**Workspace:** `IdeaProjects` / worktree `mining-eldervale-progression-65660c`  
**Servers:** hub ↔ **mmo-d** (dungeon backend). Shared transfer dir: `/var/opt/minecraft/crafty/shared/transfer`.

**Do NOT touch unless Robbi asks:** Floors 2 & 3 content rewrites (Endless XL / Ashes world stay as-is for now; only leave hooks clean). Signature weapons (Blossom Blade / Gravwell). Talk UX. Rank/wipe/special ranks. Island/Guilds polish mid-flight.

When you say **done**: patch worktree, compile, deploy jars that actually changed, restart **only** the servers that need it — no README/manifest theater.

---

## Goal A — Transfer / sync = 100% (priority 1)

Today feels ~80%. Make hub ↔ mmo-d feel like one character: join dungeon, leave dungeon, crash mid-run, rejoin — inventory, Ender, XP/levels, coins, pets, quests, dungeon gear progress, boosters, PDC custom items, location intent — **all land, nothing duplicates, nothing vanishes**.

### Own the truth
- Live path is owned by **AetherionDungeons** bridge (not a half-migrated Core network pack):
  - `bridge/TransferSnapshotStore.java` (YAML + Paper `serializeAsBytes`)
  - `bridge/NetworkPlayerDataSync.java`
  - `bridge/RemoteServerBridge.java` (Bungee Connect)
  - `bridge/HubPortalBridge.java` (`pending-floor` auto-enter)
- Core: `ProgressAccess` / `AetherServices` flush+reload only. If Core `network/*` stubs exist on a branch, **do not** fork a second transfer stack — one owner.
- Config: `AetherionDungeons` `role` / `remote-transfer` / portals — production must be **on** where live expects it; tree defaults may be `false`.

### Hard requirements
1. **Single snapshot writer** per hop; no race with quit-save / Autosave / Bungee.
2. **Apply once** on target; idempotent if player reconnects with same snapshot id.
3. **Flush before Connect** (Items/progress/pets/quests); **reload after apply** on arrival.
4. Inventory + armor + offhand + ender + XP + level + saturation/health policy (document choice: full restore vs soft).
5. Custom Items PDC survives round-trip (no remapper wipe of PDC; clear remapper cache on jar deploy only).
6. Dungeon **session** state: if they DC in instance, re-entry policy is explicit (resume vs fresh) — pick one and make it reliable.
7. Fail loud in logs if snapshot missing/corrupt; never soft-drop the player into a naked spawn with empty inv.

### Test bar (must pass)
- Hub → mmo-d Floor 1 → clear one room → hub → mmo-d again: gear + coins + XP match.
- Mid-combat DC → reconnect: no dupe stacks, no empty inv.
- Portal + `/dungeon transfer` + pending-floor paths all use the same pipeline.

---

## Goal B — Floor 1 room library (15–25 templates), shuffle per run

### Current state (replace / extend)
- Live F1: `LerfingTestBuilder` + `DungeonLayout.lerfingTest()` — fixed graph; pastes `prison1–12.nbt` **in order** (no shuffle).
- Jar also has unused `sao_brick_dungeon1–8.nbt`. Source pack has more under `lerfing-template/.../structures/` (~46 NBTs).
- Procedural `DungeonLayout.generate` / `PrototypeDungeonBuilder` exist but are **not** the live F1 enter path.

### Target
1. Author **15–25 Floor-1 rooms** as schematics/NBTs Opus builds (or adapt best of prison + sao_brick + new):
   - Mix **small / medium / large**; if feasible **very large** + **1 boss room**.
   - Same palette language as current F1 (prison / brick — stay coherent).
   - Clear combat volume, loot anchor, door/gate sockel, mob spawn pads.
2. **Per run:** shuffle — pick a random subset / random order from the pool (config: rooms per run, seed logged).
3. Still one coherent Floor-1 instance: lobby → combat chain → boss → exit. Graph can stay simple; **pieces** must shuffle.
4. Floors **2** (Endless XL schem) and **3** (Ashes world `aedun_f3_ashes`) = **out of scope** for rebuild. Only don’t break enter routing in `InstanceManager.enterPrototype(..., floor)`.

### Entry points
- `InstanceManager.java` — floor router  
- `LerfingTestBuilder.java` / layout — wire shuffle pool  
- `structures/lerfing/prison/*`, `structures/lerfing/sao_brick_*`, template pack under `lerfing-template/`

---

## Goal C — Dungeon gear pass (fit Floor 1 fantasy)

Canonical gear lives in **AetherionItems**, not Dungeons:

- `AetherionItems/.../items/dungeon/` — `DungeonArmor`, `DungeonCalling`, `DungeonGearTier` (T1–T3), `DungeonGearProgress`, `DungeonRelic`, identify/attune, `DungeonGearListener`
- Cores / factories: `item/DungeonCore.java`, infusions, `CustomItem.createDungeon*`
- Dungeons only **rolls** loot: `ItemLootBridge`, `DungeonLootFx` (F1 vestige/schematics; F2/F3 cores — leave F2/F3 tables alone unless broken)

### Target
- Floor-1 drops and identify/attune loop feel intentional (power, rarity, clear next step).
- No broken IDs, no dead menu paths, no “schematic that gives air”.
- Additive vs live: don’t delete Special Sets / other Items DevMenu pages while touching dungeon gear.

---

## Out of scope (for this pass)
- Full Floor 2 / Floor 3 schematic redesign  
- Multi-instance concurrency overhaul (unless transfer requires a small fix)  
- Island / Guilds / Hollis  
- Menu fluff without gameplay win  

`DungeonMenu` copy that still says wrong F3 fantasy may get a one-line fix if you touch the menu anyway.

---

## Ship checklist
1. Diff transfer path end-to-end; one pipeline; logs with snapshot id.  
2. Floor-1 pool 15–25 + shuffle wired; one dry run on mmo-d.  
3. Gear pass: Items jar + Dungeons loot tables aligned.  
4. Deploy: Dungeons (+ Items if gear changed, Core only if ProgressAccess touched). Restart **mmo-d** (and hub if portal/transfer jar changed).  
5. Short “what to test” in chat — no manifesto.

## Success
- Transfer feels invisible and never eats progress.  
- Each Floor-1 run feels like a different cut of the same dungeon.  
- Gear from Floor 1 is worth identifying and wearing.  
- F2/F3 still enter as today.
