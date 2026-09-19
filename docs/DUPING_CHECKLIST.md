# Economy / Trade / Party — Duping checklist

Run before a Homie-Wochenende after economy changes.

## Auction / Bazaar / Trades

- [ ] List item → disconnect mid-list → item not duplicated, listing or inventory correct  
- [ ] Buy listing → double-click spam → only one purchase / one coin debit  
- [ ] Seller offline when bought → coins/mail/returns still correct after relog  
- [ ] Server `/stop` with AH GUI open → no duplicate items on restart  
- [ ] Trade/sell GUI close with Esc during confirm → no free coins  

## Coins / Shards

- [ ] Earn coins → kill `-9` within 60s → after Phase-2 quit/periodic saves, balance survives (or only last few seconds lost)  
- [ ] Shard shop buy vial → disconnect → shards deducted XOR vial in inventory, not both free  

## Party / Dungeons

- [ ] Leader starts dungeon → member disconnect mid-run → no stuck lock / duplicate loot chest claims  
- [ ] Two leaders somehow → only one entry  

## Quests / Hub

- [ ] Progress objective → stop server → progress still there (per-player YAML)  
- [ ] Unlock spawn → quit → still unlocked  

## Pets / Guilds

- [ ] Catch/equip pet → quit → collection intact  
- [ ] Guild bank deposit → stop → balance intact  

Fix only holes you actually find; note them next to the checkbox.
