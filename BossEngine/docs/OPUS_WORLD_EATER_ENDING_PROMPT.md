# Opus follow-up — World Eater ending polish (~€13)

**Repo:** `robb-devo/aetherion-plugins`  
**Branch:** `feature/world-eater-boss` (already has the fight live)  
**Mode:** Small, high-impact pass. Budget is tight. Prioritize what the player *sees* when they win.

Copy **everything below the line** into the Cloud Agent chat.

---

You already shipped **Nihil, the World Eater**. The fight is strong. Robbi is happy with the core.

This is a **ending polish pass** only. Remaining Cloud budget is small (~€13). Do the highest-impact visual wins first. Push commits. **Do not deploy. Do not run Maven** (`repo.papermc.io` is blocked here — Robbi builds locally).

## Priority order (do in this order; stop when budget is thin)

### 1) MUST — Bonus Chest must not look like a boring vanilla chest
Right now `loot/WorldEaterBonusChest.java` is a **custom prop** that *looks* like a normal Minecraft Bonus Chest. That undercuts killing the World Eater.

Make the chest **visually spectacular** and on-theme (void / eaten worlds / last seed / night-sky sculk / regenerating grass — your call). Still:

- Session-less display prop + Interaction (same contract as now: shares, expiry delivery, plugin-disable delivery, site reset hook)
- Four “torches” or equivalent Light props can stay conceptually, but they should match the new aesthetic
- Opening beat should feel like a **reward**, not `BLOCK_CHEST_OPEN` on oak

Study `HollowReliquary` / `SeraphineMusicBox` for juice level — then make this one *more* “end of Minecraft itself” than either.

### 2) SHOULD — Death beat needs one more visual punch
The death *structure* is good (eats self → black point → “Generating world” → chunks → dawn → “Saving the game” → chest). Keep that fantasy.

What is thin: the **serpent’s last moments** (spiral / shrink / vanish) feel underwhelming for killing the World Eater.

Add a **short, readable death flourish** on the body/skull before or during the shrink — particles, displays, sound, camera-safe. Not a second supernova. Not longer than ~2–4 seconds of spectacle before the worldgen parody takes over. Do **not** rewrite phases or balance.

Hot file: `instance/worldeater/WorldEaterDirector.java` (`tickDeath` / ouroboros end) + `Serpent` / `WeFx` as needed.

### 3) IF BUDGET ALLOWS — Signature loot: armor + weapon (textures welcome)
Current `bosses/world_eater.yml` loot is mostly vanilla + random boosters. That is fine as filler; the **killer / rank / chest highlight** should be World-Eater identity gear.

You may:

- Add factories on `AetherionItems` `CustomItem` (+ profiles / ability listener if the weapon has a simple signature move)
- Wire them into `world_eater.yml` loot (and the Bonus Chest bundles)
- Ship **resource-pack assets** under the existing Aetherion pack layout (`assets/minecraft/models/item/…`, textures, `custom_model_data` predicates on the base item JSONs). Pick **unused CMD ids** — do not collide with existing ranges (bows 22xx, tools, seraphine, etc.). Document the CMDs in the finish note.
- **Go hard on art:** clean, premium, Hypixel-tier if you can. Animated frames / emissive-friendly textures are welcome — pack + shaders exist on the server. Prefer readable silhouette over noise.
- Fantasy: this is loot from the *last seed / eaten worlds / Nihil* — not another gold set, not Blossom Blade / Gravwell Cleaver clones.

**LOCKED — do not touch:** Blossom Blade (`ashen_katana` / AshenKatana*), Gravwell Cleaver (`gravwell_cleaver`), boosters/anvil/ranks/shutdown, other bosses’ combat.

If time is almost gone after (1)+(2), ship gear as **item factories + CMD + pack stubs** (even placeholder PNGs with clear names) rather than half-breaking the fight.

## Explicitly do NOT

- Rebalance HP / phases / hitboxes / hints (already tuned live)
- Touch Multiverse / world generator
- Touch dungeons
- Rewrite the Unbroken
- Maven / deploy / production restart
- “Polish” locked signature weapons

## Done means

1. Bonus Chest looks like a World Eater ending, not oak storage  
2. Death has one clear visual climax on the serpent before worldgen takes over  
3. (If possible) Unique armor + weapon + pack hooks wired into loot  
4. Branch pushed + short finish note (files, CMD ids, how Robbi tests `/boss worldeater reset` → kill → chest)

Clarity > novelty. Make winning Nihil feel finished.
