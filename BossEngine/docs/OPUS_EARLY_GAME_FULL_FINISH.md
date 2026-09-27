# Early Game Full Pass — Finish notes (Opus)

**Branch:** `claude/early-game-harbour` (on top of Harbour Hour, `3d6d8d3` + `70e2c8c`)
**Scope:** past the pier and past `tutorialDone`. Pier arrival / kit ceremony / pier trail untouched.
**Build:** not compiled in this session — the cloud sandbox gets 403 from `repo.papermc.io`. Every changed
file passes a `javac` parse check; Robbi compiles locally (`mvn -q clean install`).

No quest IDs, no `tutorialDone` set, no gates, no combat numbers, no LOCKED systems touched.

---

## What a new player now gets

### 1. The stamp is a moment, and it doesn't drop you into a void (A1 / B2 / B5)
- **Graduation stamp** (`ui/GraduationStamp.java`): an orientation slip appears on Ledger's side of the desk,
  her stamp comes down on it, **QUEST COMPLETE + the 250-coin reward fire on the hit** (not on the click),
  a wine-ink seal is left, the slip stands up to face you and is filed into your pocket, then two quiet
  harbour bells answer. About 2.5 s, BlockDisplays only, per-player, non-persistent. Quit / world change /
  disable fires the reward immediately and removes every piece.
- **Open Roads** (`util/OpenRoads.java`): the soft mid-game opening as one list —
  Sergeant Vex → Rite Warden → Craftsman side job → Surveyor → the wider map. "Done" is read from state
  the game already tracks (`lesson_steel`, `border_rites`, the Craftsman gift flag, blueprint hunt enabled).
  Nothing new is persisted.
- **Ledger's desk** (`ui/OpenRoadsGUI.java`): after the stamp her lines explain the arrow, pin the first open
  road, and (if you're still at the desk) open the **Open Roads** board: four road cards + "the wider map",
  the suggested one glinting, filed ones greyed. Clicking a road points the yellow arrow at that NPC
  (`QuestHint`) — nothing is accepted or forced. Return visits open the board; the old topic briefing is one
  click away (bottom-left).
- **`/guide` after the stamp** (`QuestStoryGate.roadTips` → `GuideAdvice` → `GuideCommand`): "Orientation filed —
  open roads, pick any:" with the next road first (➜), two more, the wider map, then the usual unlock tips.
  Discord `guide` gets the same tips.
- Pinned roads survive in `QuestHint` until that road is filed (the old rule dropped Craftsman after the stamp).
  After Border Rites the arrow quietly moves to the next road.

### 2. Guidance past the pier (A2)
`TutorialQuestTrail` gains a **compass mode** — same painted yellow chevrons, no second nav system:
- Target = the turn-in NPC while a quest is READY, else whatever `QuestHint` already points at
  (QM, Foreman, Craftsman, Temper, Ledger, Farmer, Lark, Vex, Rite Warden, Surveyor). Never while objectives
  are being worked (the quest bar owns that stretch). Egon/Forager keep the authored pier route.
- A short run of chevrons from where you stand toward the target, **only on walkable ground with headroom** —
  it stops at a wall, a drop or a roof instead of painting over it. Re-anchors when you drift > 6 blocks off
  the line.
- A thin **beacon in the NPC's name colour** over their head once they're within 64 blocks.

### 3. World life past the harbour (A3 / B3)
`LivingNpcAtmosphere` now carries the rest of the early cast, same budget (particles only when watched,
sounds rare and quiet):
Quartermaster (crate lid / tally), Craftsman (two hammer taps, sparks only when it lands), Temper (heat glint),
Farmer (chaff, hoe, hens off somewhere), **Sergeant Vex** (ash blowing through the gate, whetstone or a two-beat
drill), **Rite Warden** (crimson spores, a soul lifting off the altar dust, soul-sighs), Surveyor (blueprint ink,
spyglass). `NpcPresence` voices for Vex, Rite Warden, Craftsman, Surveyor.

### 4. Early UI density (A4 / B4)
- **Recipe Book categories:** header shows `Known x/y` + bar; each category shows `n known · m to discover`,
  categories with nothing learned read quieter ("Nothing learned here yet · Click to peek").
  Two new cards in the free cells: **Ready to craft** (glints when your bag can pay for something, lists the
  first three) and **Where recipes come from**.
- **Ready to craft view** (`GUIType.READY`): every known recipe your inventory can pay for right now; click →
  detail → Insta Craft → Back returns to Ready.
- **Flat category lists** (Resources / Boosters / Charms): known first, then one row of undiscovered
  silhouettes, the rest fold into a "+N more to discover" card. Listener maps clicks through the same list.
- Locked recipes are a grey **firework-star silhouette "???" / UNDISCOVERED** instead of a red barrier.
  Gear-set layouts (columns per set, specials on the next page) are unchanged.
- **Manager:** header shows `Open x/y` + bar + **Next: <unlock> + how**. The lock that opens next is a glinting
  lime dye "· next"; far-future locks (Journal, Island, Guild) recede to quiet panes; dark rails top/bottom.
  Slots and click handling unchanged.

### 5. Craftsman clarity (A5)
- Intro says it plainly: *side job, not on Ledger's list* — craft a Mining Pickaxe, show it whenever, he pays.
- Crafting the pick (Recipe Book) → Craftsman calls it out once; the arrow remembers him only if nothing else
  is pinned.
- Showing it → a real (small) **QUEST COMPLETE · Second Recipe**, then the existing reward block.
  Next step depends on where you are: Foreman before the shift; next open road after the stamp; otherwise the
  orientation arrow is left alone.
- Talking to him later no longer says "Next: Shaft Foreman" after the shift.

### 6. First combat, framed (B1)
During `lesson_steel` (`VexDeathHintListener`): the first hit on a Borderlands hostile gets a word from the gate
about the damage number you just saw (white = you, gold ✦ = crit); the first kill gets a count. Once each,
persisted with the starter-kit flags. Uses the same Borderlands test as the kill objective
(`QuestObjectiveListener.isBorderlandsHostile`). No numbers changed.

### 7. Milestone juice (B5)
- `UnlockToast` (every Manager unlock) gets the same "world noticed you" ring as the Hub discover popup,
  plus an answering chime. Player-only particles.
- Graduation stamp (above). Craftsman side job now has its own completion beat.

### 8. Exploration invitation (B6) — see chests below
Ready chests glint faintly from 8–48 blocks (only for players they're ready for), say "✦ Something catches the
light · Rare Chest" once per session the first time you come within 14 blocks, and rattle their hasp when you
walk right up.

---

## Explore chests — authored props (`chest/ExploreChestProp.java`)

**Contract kept:** `give:explore-chest:<rarity>` → `DevBridges.exploreChest` → `QuestProgressAccess.exploreChest`
→ `ExploreChestListener.create` (same placer item). Place / sneak-remove / open / 12 h cooldown /
`ExploreChestLoot` roll+give / merchant unlock gate / `explore-chests.yml` rows all unchanged.

**How it's built:** the block is now an invisible **BARRIER** (hitbox for click / break / explosion / piston —
the listener works unchanged). The chest you see is 8–18 BlockDisplay/ItemDisplay parts, each a unit cube placed
by `setTransformationMatrix`; the lid and everything on it hang from one hinge on the back top edge.

| Rarity | Look | Idle | Open |
|---|---|---|---|
| Rare | spruce, verdigris-copper bands, iron hasp | lid "breathes" (6°) every 7–13 s with a faint creak | chain latch + chest open, aqua spill |
| Epic | dark oak, amethyst bands, lit gem on the hasp | three amethyst shards orbit slowly | chest open + amethyst chime, violet spill |
| Legendary | blackstone on gold feet, gold corner posts | small gold hex halo turning above the lid | gold equip + chest open + bell resonance; bell on reveal |
| Mythic | crying obsidian floating over a glowing obsidian plinth | bobs, lid never quite shut, end-rod motes circle, a mote escapes the gap | ender-chest open + amethyst resonance; beacon + light column on reveal |

**Open sequence:** lid swings open in 30° steps (so the client's interpolation reads as a hinge) → the loot rises
out of the chest (the spinner now parks *inside* the body when idle) → existing sample spin → reward lands →
rarity reveal → loot paid 16 ticks later (unchanged) → ~2 s later the loot sinks back and the lid shuts.

**Persistence / cleanup:**
- `explore-chests.yml` gains a `facing:` list (`world|x|y|z|FACE`). Old files load fine (default SOUTH).
- **Migration:** older vanilla-block chests (CHEST / TRAPPED_CHEST / ENDER_CHEST / PURPLE_SHULKER_BOX) are
  converted to a barrier in place on restore/chunk load, facing taken from the block. Any other block type at a
  chest coordinate is left alone.
- Prop parts are **non-persistent** and tagged `aether_explore_prop`: chunk unload drops them, chunk load
  rebuilds; `remove`/sneak-break removes them (plus a tag sweep); plugin disable removes all. A single 5-tick
  ticker animates only props with a player within 40 blocks, and rebuilds a prop whose parts went missing.
- If AetherionQuests is ever removed, placed chests are left as bare barriers (was: vanilla chests).

---

## Deliberately left alone
Pier arrival / kit ceremony / pier trail / `HarbourOnboardingGate`, `QuestStoryGate.redirectToTutorial`,
QuestAcceptGUI, `SpawnDiscoverListener` (already has the ring), DamageNumbers itself, Merchant chests,
loot tables, all LOCKED systems.

**World-building:** skipped. Without the live map to look at, a mine mouth or overlook would be a guess; the
paste pipeline (`/aetherpaste`, `FaweIslandPaste`) is ready if Robbi wants to point at a spot. No schems / coords
added.

## Test pass (Robbi, local)
1. `/dev` → skip tutorial on a fresh alt **or** walk Egon → … → Farmer + Lark.
2. Ledger: stamp scene, QUEST COMPLETE on the hit, three lines, arrow on Vex, Open Roads board opens.
   Click Rite Warden → arrow moves. Right-click Ledger again → board. `/guide` → roads.
3. Between QM → Foreman → Temper → Ledger → Fields: chevrons toward the hinted NPC, stop at walls; colour beacon
   over the NPC inside 64 m.
4. Vex lesson: first hit → Vex line about numbers; first kill → count line (once each).
5. Recipe Book on a fresh account: counts, quiet categories, Ready card, Resources folded with "+N more".
   Manager: `Open x/y`, one glinting "next", Journal/Island/Guild as panes.
6. Dev Menu → NPCs → Services / Tools → place all four chests (face them from different sides): idle, walk-up
   rattle, glint from ~20 m, open/close choreography, sneak-remove leaves nothing behind. Restart with a
   pre-existing vanilla explore chest → it becomes the prop, same facing.

## Known risks
- Compiled against nothing here — first local build may surface a typo-class error; logic was reviewed.
- Pre-existing: on chunk load the persistent spinner/label lookup can run before entities load (Paper loads
  entities separately) and spawn a duplicate; now the duplicate spinner is parked inside the chest, so it's
  hidden, but the label could double. Unchanged by this pass.
