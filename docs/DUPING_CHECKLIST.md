# Economy / Trade / Party — Duping checklist

Run before a Homie-Wochenende after economy changes.

Legend:

- **Mitigated (code)** — a Phase 3 code path closes the hole. Still spot-check once after deploy.
- **Manual QA** — remaining risk; follow the reproduction steps. Do not skip these.

`coins.yml` format is unchanged (`players.<uuid>`, `lifetime.<uuid>`). No wipe. Per-player coin files are **not** in this PR (Phase 3.1).

---

## Auction / Bazaar / Trades

- [x] **Mitigated (code)** List item → disconnect mid-list → item not duplicated, listing or inventory correct  
  `MarketService` returns the held item on `InventoryClose` if the price GUI was not committed. Quit/kick/`/stop` now **close open AH/Bazaar inventories** before YAML flush (`PersistenceFlushListener`, `AetherionItems.onDisable`).  
  **Still spot-check:** list an item, Alt+F4 on the price GUI, relog. Item must be in inventory **xor** listed, never both.

- [x] **Mitigated (code)** Buy listing → double-click spam → only one purchase / one coin debit  
  Confirm clicks are per-player gated; `buy()` **removes the listing first**, then `take()`; failed `take()` puts the listing back. Coin `take()` is CAS.  
  **Still spot-check:** two players confirm the same listing on the same tick; only one gets the item.

- [ ] **Manual QA** Seller offline when bought → coins/mail/returns still correct after relog  
  **Repro:** Player A lists gear and logs out. Player B buys. A relogs. A's purse must increase by the buyout; B has the item; listing gone. No mail system — coins are `CoinService.add(sellerId)` while A is offline (in-memory + `coins.yml`). After 60s autosave or B's quit flush, kill `-9` and confirm A's balance on reboot.

- [x] **Mitigated (code)** Server `/stop` with AH GUI open → no duplicate items on restart  
  `onDisable` closes every online player's inventory **before** `market.save()` / `coins.save()`. Price GUI close returns unlisted items.  
  **Still spot-check:** `/stop` on the price GUI (item in hand taken for listing) and on the confirm-purchase GUI.

- [ ] **Manual QA** Trade/sell GUI close with Esc during confirm → no free coins  
  **Repro:** Open player trade (sneak + right-click). Put items in. Both click confirm **or** one hits Esc mid-confirm. Esc/`InventoryClose` cancels and returns each side's offer (`TradeMenu.onClose`). Coins are not part of player trade. Also Esc the AH confirm GUI (red wool / Esc) — coins must not move.  
  Gear trader / fence / liquidator shops: Esc during confirm is still **manual** (not changed this PR).

## Coins / Shards

- [x] **Mitigated (code)** Earn coins → kill `-9` within 60s → after quit/periodic saves, balance survives (or only last few seconds lost)  
  `take()`/`add()` are concurrency-safe. `coins.yml` is **write-temp + atomic rename**. Quit flushes dirty coins; transfer flush saves before snapshot. A hard `-9` **without** quit can still lose the last unsaved seconds (autosave 60s). That window is unchanged by design.

- [x] **Mitigated (code)** Shard shop buy vial → disconnect → shards deducted XOR vial in inventory, not both free  
  `ShardService.take()` is CAS; quit/`/stop` close inventories then `saveIfDirty()`.  
  **Still spot-check:** buy vial, disconnect on the same tick as the click; shards and item must not both be "free".

## Party / Dungeons

- [ ] **Manual QA** Leader starts dungeon → member disconnect mid-run → no stuck lock / duplicate loot chest claims  
  **Repro:** 2-player party. Enter floor. Member Alt+F4 mid-run. Leader finishes / opens loot. Relog member. No second loot, no stuck party lock, no duplicate dungeon session. Party `onQuit` already `leave()`s. Dungeon `onQuit` schedules `leaveSession`. This PR does not change loot tables.

- [ ] **Manual QA** Two leaders somehow → only one entry  
  **Repro:** Two players both `/party` as if leader (invite desync, or both click the gate). Only one dungeon instance should start. Not a persistence bug; still QA.

- [x] **Mitigated (code)** Dungeon transfer mid-trade  
  **Repro (spot-check):** Hub trade GUI open, walk through the hub portal to mmo-d. `NetworkPlayerDataSync.flushPlayer` closes the inventory first (trade cancel returns offers), then flushes coins/storage/loadouts/quests, then writes the snapshot. Arrival applies once via `{uuid}.yml` → `{uuid}.claimed.yml` claim.  
  Residual: if Velocity connect fails after snapshot+inventory-clear, player is on hub with empty inv until the snapshot is applied or an admin restores `{uuid}.yml` from the shared transfer dir.

## Quests / Hub

- [x] **Mitigated (code)** Progress objective → stop server → progress still there (per-player YAML)  
  Quest YAML is now flushed on transfer (`QuestProgressAccess.flushPlayer`) and quit (`PlayerQuestStorage.unload`). Saves use atomic replace. `/stop` still relies on Quests `onDisable` `flush()`.

- [ ] **Manual QA** Unlock spawn → quit → still unlocked  
  **Repro:** Unlock a hub spawn, quit immediately, relog. Spawn must remain unlocked. Hub persist was not rewritten this PR; confirm no regression.

## Pets / Guilds / Storage

- [ ] **Manual QA** Catch/equip pet → quit → collection intact  
  **Repro:** Catch, equip, quit within a few seconds, relog. PetAccess flush already runs on transfer; quit path in AetherMobs was not changed this PR.

- [x] **Mitigated (code)** Guild bank deposit → stop → balance intact  
  Deposit still `take()` then `guild.setBankCoins` then `save()`. `guilds.yml` now uses atomic replace. Player coin `take()` is CAS.  
  **Still spot-check:** deposit, `/stop` immediately, restart. Purse + bank must sum to the pre-deposit total (no extra, no missing).

- [x] **Mitigated (code)** Storage / loadouts / sacks around quit and Velocity hop  
  Flush order: close GUI → save worn loadout (skip empty armor so post-transfer inv-clear cannot wipe YAML) → save storage → save coins/shards/progress → snapshot. Arrival overlays **that player only** (no full `reloadFromDisk()` of shared YAML). Sack close still saves; sack YAML is atomic.

---

## Remaining manual QA (do these)

1. **AH disconnect mid-list** — price GUI open, kill client, relog. Item xor listing.  
2. **AH double-click buy** — spam confirm; one debit, one item. Two buyers, same listing: one winner.  
3. **`/stop` with GUI open** — AH price, AH confirm, Bazaar browse, player trade, storage page, sack. No dupes, no lost listed items.  
4. **Dungeon transfer mid-trade** — trade open on hub, enter portal. Offers returned or snapshotted once; no double items on mmo-d.  
5. **Offline seller payout** — buy while seller offline; seller relog purse.  
6. **Hard `-9`** — earn coins, kill process inside 60s without quit. Expect last seconds lost; file must still parse (no truncated `coins.yml`).  
7. **Member disconnect mid-dungeon** — loot/lock.  
8. **Spawn unlock + pet catch** — quit immediately.  
9. **Esc during shop confirm** (fence/trader/liquidator) — no free coins.

Fix only holes you actually find; note them next to the checkbox.
