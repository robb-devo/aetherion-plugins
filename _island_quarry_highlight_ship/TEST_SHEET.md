# Test sheet: Island · Quarry · Guild highlight

Two accounts help (A = new-ish player, B = existing island owner). Admin needs `aetherion.guild.admin`.
Check the console after each block; anything red → note the step.

## 0. Boot
- [ ] Log: templates extracted (first boot), `loaded N structures`, `loaded N belt tiles`, `linked N existing quarries as frameless housings`.
- [ ] `plugins/AetherionGuilds/templates/` has 18 `.schem`.
- [ ] B: `/island home` still lands on the old pad; build/break still works where it did.

## 1. Beats (admin preview)
- [ ] `/island admin fx foreshadow`: gull note, actionbar, clickable *Peek at the starters*.
- [ ] `/island admin fx unlock`: title, three sounds, totem burst, spiral, clickable *Choose your starter*.
- [ ] `/island admin fx guild`: raid horn, title, *Found a guild* suggests `/guild create `.

## 2. Claim (A, Level 20+, no island)
- [ ] On join (or within 5 s): the unlock beat plays once, never again after relog.
- [ ] `/island` → **Choose your Starter**: three glowing choices.
- [ ] Pick one: "The island rises…", paste with particles (≈1–2 s), you drift down onto the spawn, fanfare, *first steps* card with clickable [Build] / [Expand].
- [ ] `/island home` lands on the same spot facing the island. Flight on your island still toggles.
- [ ] Try all three starters on test accounts (or wipe between): distinct looks, Hut Site path ring visible.
- [ ] Below Level 20: `/island` shows the same screen as a locked preview.

## 3. Build
- [ ] `/island build`: Storage Hut shows **free**. Click → ghost follows the crosshair; green on the Hut Site, red on the tent / pond / edge with red blocked cells; left-click rotates; yellow arrow = front.
- [ ] Right-click on the Hut Site: pays nothing, builds bottom-up with sounds, title "✦ Storage Hut ✦".
- [ ] Break a hut wall block → refused ("Part of your Storage Hut…"). Place a torch inside, break it → works.
- [ ] Workshop (1,500): builds; its **lectern** opens the blueprints.
- [ ] Mill without a Quarry Mill item → "Needs 1 Quarry Mill"; with it → builds, item consumed.
- [ ] Limits: 2nd Workshop refused; Depot count respects tier.

## 4. Quarry → belt → hut
- [ ] Place a quarry on the island (Quarry Pad on the Outpost): headframe rises round it (only into air), chute faces you, message about the chute.
- [ ] `/island belts` (Rail Layer): chutes glow orange. Right-click the orange cell, right-click a cell next to the hut wall (same height, L-shape OK) → rails laid with corners, coins taken, tiles counted.
- [ ] Extend: right-click again from the end → it chains; left-click a rail → taken up, refunded.
- [ ] Within ~1 s: cargo items glide from the chute along the rails into the hut; bucket bobs on the headframe; hut puffs.
- [ ] Quarry nametag ends with `→ Storage Hut`. Quarry menu slot 23 says "→ Storage Hut (N tiles)".
- [ ] Hut barrel → storage menu fills; click takes a stack, shift fills inventory, *Take everything* works.
- [ ] Break a rail / the block under it → refused. Try a minecart on it → refused.
- [ ] Belt into nothing: Production shows "dead end", nametag `→ ✖`, quarry fills its own storage again (old behaviour).

## 5. Chain extends
- [ ] Quarry → belt → **Mill** (enter any side) → belt from the Mill's chute → hut. Millstone turns while working; hut receives **Compressed** (green cargo icon changes after the mill).
- [ ] Add a **Forge** between Mill and hut → hut receives **Compacted** once 128 compressed went through; leftovers visible in the Forge menu (collectable).
- [ ] Walk off the island (or log out) for 2+ minutes, come back: hut totals grew by roughly the quarry output (offline catch-up), no cargo spam on return.

## 6. Land
- [ ] `/island land`: 9×5 map, your 3×3 as ground, heart in the middle, green panes around, ● you are here. Scroll works.
- [ ] Buy a green parcel: price shown, coins taken, ground rises layer by layer (grass / rock / sand by starter), green outline.
- [ ] Toggle *Raise new ground: OFF*, buy another → only the outline, you can build there in the air.
- [ ] Build right at the new edge works; one block past it doesn't.
- [ ] B (classic island): land map works too; old radius still buildable.

## 7. Island Tier
- [ ] Island menu slot 24 → coins only (5k first). Title "✦ Island Tier 2 ✦". Classic pads still grow their platform; starters don't get a slab ring.
- [ ] Belt cap and machine counts rise in the Build menu.

## 8. Guild
- [ ] Level 75 player without a guild: guild unlock beat once.
- [ ] `/guild create Test`: "Raising the harbour…", Guild Harbour pastes, founder drifts down: "⚑ Test — We have a place.", steps card.
- [ ] Invite B, B accepts and `/guild home`: B's first arrival → "⚑ Test — Our place." (once).
- [ ] Plaza lectern (project board) → Guild Projects. Footman sees contribute buttons, not Start.
- [ ] Mayor starts **Guild Hall**: starts on the north site (no ghost); broadcast to members.
- [ ] Contribute: pack (cobble / oak logs), coins (left/right/shift), guild bank (Soldier+). Bars move.
- [ ] Guild quarry → belt → guild Storage Hut, then *Give from guild storage* pulls cobble from it.
- [ ] Stage paid → site transitions (scaffolding goes, walls rise …), every online member gets the title; stage 3 roof + chimney smoke, "Finished. Built together.", renown shown.
- [ ] Harbour Beacon: same on the east site.
- [ ] Classic guild island (created before this ship): Start opens the site ghost; pick flat ground, the site marker pastes.
- [ ] `/guild build` / `land` pay from the guild bank; Footman can't place, Soldier can; Mayor buys land.

## 9. Robustness
- [ ] Restart: structures, belts, storage, parcels, projects, unlock state all persist. Cargo/millstone reappear only when someone's there.
- [ ] Pick up a quarry: its housing is cleared (only blocks still the housing's), belt stays.
- [ ] Take down an empty Depot: half coins back; a non-empty one refuses.
- [ ] `/island admin rebuild` on an island re-places missing rails.
- [ ] `/spark` on a busy island: paste jobs spread, no spikes from belts/cargo.
