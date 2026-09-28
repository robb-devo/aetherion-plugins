# World Eater: finish notes

**Encounter:** *The Last Seed.* A dead-end road through the leftovers of eaten worlds, a bedrock
keeper chained to a gate, and behind it the last intact 3x3-chunk slab of an ordinary Minecraft
world. **Nihil, the World Eater**, is a serpent whose vertebrae are chunk-slabs of the 16 worlds
it already ate. It doesn't only eat blocks. It eats what players take for granted: chunks, the
stars, the rules (missing textures, lag), and finally the world border. It dies by eating itself,
and the world regenerates chunk by chunk, down to "Saving the game" and a **Bonus Chest**.

## Ids

| What | Id / path |
|---|---|
| Pre-boss template | `world_eater_unbroken` (`bosses/world_eater_unbroken.yml`, IRON_GOLEM hitbox) |
| Main boss template | `world_eater` (`bosses/world_eater.yml`, MAGMA_CUBE hitbox riding the skull) |
| Directors | `instance/worldeater/UnbrokenDirector`, `instance/worldeater/WorldEaterDirector` |
| Site / world | `instance/worldeater/WorldEaterSite`, layout `SiteLayout`, generator `WorldEaterVoid` |
| Loot prop | `loot/WorldEaterBonusChest` + `listener/WorldEaterChestListener` |
| Site protection | `listener/WorldEaterSiteListener` |
| World name | `world_eater` (config `world-eater.world`) |
| Generator id | `BossEngine:worldeater` |

## Setup (once)

```
/boss worldeater create     # creates/loads the void world (BossEngine supplies the generator)
/boss worldeater build      # builds road, plaza, gate, island from the layout (~2 s)
/boss worldeater go         # teleports you to the arrival point
```

`world-eater.auto-load: true` (default) reloads the world on startup if its folder exists.
**Multiverse is optional.** If you want MV to own the world, import it *after* BossEngine is up:
`/mv import world_eater normal -g BossEngine:worldeater`. MV loads worlds before BossEngine
enables, so either keep `auto-load` on and leave the world out of MV, or add
`loadbefore: [Multiverse-Core]` to BossEngine's `plugin.yml`. Without the generator, new chunks
would generate as vanilla terrain. Set gamerules in-world if you want: the site already turns off
mob spawning, daylight/weather cycles, fire tick, mob griefing and random ticks.

## Test path

1. `/boss worldeater go` in survival. Walk the road (+Z). Halfway down, something passes beneath
   and bites the desert (plays once per build).
2. Step onto the plaza: the statue wakes (**The Unbroken**). At 50% its arm breaks. Its death tears
   the gate leaves out of the wall and daylight comes through.
3. Cross the bridge onto the island: the sky tears, night falls in two seconds, **Nihil** pours out
   of the tear and circles the island once. Fight: 100% Hunger → 70% Starless → 40% Unmade →
   15% Ouroboros (the world border is its body).
4. Death: it eats its tail, then itself; "Generating world", chunks regenerate, dawn,
   "Saving the game", the Bonus Chest generates on the podzol. Right-click it for your share.
5. 20 s after the chest unloads the site resets itself (anyone on the island is set down on the
   plaza) and the statue kneels again.

Shortcuts: `/boss worldeater open` opens the gate without the Unbroken. `/boss worldeater status`.
`/boss worldeater reset` despawns both bosses and rebuilds everything. `/boss spawn world_eater` /
`/boss spawn world_eater_unbroken` anywhere else runs a *foreign-mode* fight: same moves, displays
and client-side paint only, no block edits.

## Cleanup model

- **Layout is truth.** Every real block edit (statue, doors, bites, trenches, eaten chunks, rift
  gateway blocks) happens only inside the site boxes (`SiteLayout.SITE`, which now includes
  `RIFT`). Reset, rebuild and crash recovery all mean "make the boxes match the layout" (a job
  spread over ticks). No journals. The state is persisted in the world PDC; on startup any
  non-dormant state rebuilds.
- Aborted fight → director `clear()` (displays, paint, sky/border) and the site restores the island.
- Per-player reality (time, weather, virtual world border, client paint) is released on
  quit/world change (`WorldEaterSiteListener`), on fight end, and on plugin disable
  (`Senses.releaseEverything`).
- Bonus Chest shares are delivered on expiry and on plugin disable (same contract as the
  reliquary and music box).

## Residual risks (read before shipping)

- **Not compiled with Maven and not run on a server.** repo.papermc.io was blocked here. All new
  and changed code type-checks clean with `javac` against Paper **1.21.4** API sources + Maven
  Central deps (full BossEngine tree; the only errors are pre-existing `GENERIC_MAX_HEALTH` uses in
  `SandboxDirector`/`SandboxFx`, a constant renamed after 1.21.1). Differences between 1.21.1 and 1.21.4 that the checker can't see
  are possible. Run `mvn -pl BossEngine -am package` first.
- **Balance is placeholder:** HP (50k / 150k), `BossHits` powers, cooldowns, loot chances.
- **Display load:** Nihil is about 150 displays (the body is sent every other tick). Watch
  client FPS and bandwidth with several viewers.
- **Per-player sky:** `setPlayerTime` updates are stepped on 1.21.1. The star-eating beat fades
  via per-player weather in the `the_end` biome (black sky by design).
- **Camera pans** (`Player#lookAt`) during the arrival may feel intrusive. They're easy to drop in
  `tickArrive`.
- **Rubber-band teleport** at the Unmade transition moves players to where they stood 3 s earlier
  (only onto safe ground). Anti-cheat plugins may complain.
- The site edits only the dedicated world. Nothing in production worlds is touched unless someone
  names a production world in `world-eater.world`. Don't.

## Ending polish (Seed Vault + death climax)

**Files:** `loot/WorldEaterBonusChest.java` (visuals rewritten; the contract is unchanged:
per-player shares, expiry delivery, plugin-disable delivery, `onFinish` → site reset) and
`instance/worldeater/WorldEaterDirector.java` (`burstWorlds` / `tickRelics` inside `tickDeath`).

- **Seed Vault** (the Bonus Chest): a void pool opens inside an F3+G chunk border (yellow/cyan).
  A miniature chunk of the Last Seed generates bottom-up (bedrock → deepslate → stone with ores →
  dirt → a grass lid split in four), and four end rods ("torches of the End") are placed around
  it, lighting the podzol with client-side light blocks. **Bloom (reward beat):** the grass petals
  fold open, a 40-block beam of starlight goes up, the ender eye rises out of a sculk heart, a
  sapling sprouts, and the beacon activate, amethyst resonance and toast sounds play. Right-click →
  the heart pulses and a stream of end-rod stars flies to the player with the share. Unload:
  the rods go out, the petals close, the chunk de-generates top-down and the pool closes.
  No chest sounds, no oak.
- **Death climax** (~2.5 s, inside the existing self-eating window, so total length is unchanged):
  all 16 eaten worlds burst out of the skull as slabs, circle it once, then implode into the
  shrinking head. The black point lands with a violet double shock ring across the island and a
  sonic boom, then "Generating world" as before. No camera moves.
- **Signature gear:** see "Nihil's gear" below.

**Test:** `/boss worldeater reset` → `/boss worldeater open` → step onto the island → fight (or
lower `max-health` in `world_eater.yml` temporarily) → watch the burst/implosion at the end of
the death, then the vault bloom after "Saved the game". Right-click it for the share, and wait for
the unload plus the 20 s site reset. Quick check away from the site: `/boss spawn world_eater` in
a test world. The vault then generates where the boss was summoned.

## Nihil's gear (Worldhide + Worldbite)

| Item id | Name | Base | CMD |
|---|---|---|---|
| `worldhide_helmet` | Worldhide Crown | NETHERITE_HELMET | 4101 |
| `worldhide_chestplate` | Worldhide Carapace | NETHERITE_CHESTPLATE | 4102 |
| `worldhide_leggings` | Worldhide Coils | NETHERITE_LEGGINGS | 4103 |
| `worldhide_boots` | Worldhide Treads | NETHERITE_BOOTS | 4104 |
| `worldbite` | Worldbite | NETHERITE_SWORD | 4105 |

`random_worldhide_armor` gives one random piece. The CMD range 4101-4105 was unused (fishing
uses 29xx, bows/tools 21xx-23xx).

- **Code (AetherionItems):** factories in `item/CustomItem.java` (WORLDHIDE section; BossEngine
  resolves the ids by reflection, `create<Id>()`), stats in `BossGearBalance.base` (one step over
  the Aetherion set, MYTHIC, stamped for migration), profiles in `model/ItemProfile.java`, glow in
  `BossWeaponLook`, flavor in `ItemFlavor`. Abilities are in `listener/WorldEaterGearListener.java`
  (registered next to `VoidStickListener`):
  - **Worldbite: Devour** (right-click, 8 s cooldown). An F3+G chunk-border square snaps down
    five blocks ahead, mobs inside are pulled to its center, then bitten for 60 magic damage;
    15% of it heals you (capped at 40). It never touches players, pets, bosses or boss minions
    (same rules as the Void Stick).
  - **Worldhide full set: Last Seed.** Standing on grass, moss or podzol gives Regeneration I and
    Absorption I, refreshed every 2 s.
- **Looks:** the armor wears an **amethyst eye armor trim** (vanilla; tooltip hidden), so the
  worn model is distinct without shaders. The inventory icons are hand-made 16x16 textures: a
  void hide with an amethyst rim, an ender eye on the crown, a spine of eaten-world colors on the
  carapace, sculk accents on the coils, grass tufts under the treads, and a void fang with an
  amethyst edge, a grass-block guard and an eye pommel for Worldbite.
- **Pack:** `AetherionItems/resourcepack/assets/minecraft/textures/item/worldhide_*.png` and
  `worldbite.png`, models `models/item/worldhide_*.json` and `worldbite.json`, and overrides
  appended to `netherite_{helmet,chestplate,leggings,boots,sword}.json`. Rebuild and redeploy
  the pack the usual way.
- **Loot (`bosses/world_eater.yml`):** rank 1 gets Worldbite 12% plus one Worldhide piece 100%,
  rank 2 gets 5% / 70%, rank 3 gets 2% / 35%, and every damager has a 5% chance at a piece.
  Boosters, the nether star and the vanilla extras stay. It all lands in the Seed Vault. The
  server only writes this YAML if it is missing, so **copy the `loot:` section into the live
  `plugins/BossEngine/bosses/world_eater.yml`**.
- **Quick test:** `/boss give` doesn't cover these (they aren't spawn items). Use the existing
  AetherionItems admin give if it accepts ids, or kill Nihil at low HP.
