# The Hush

Showcase boss, built 2026-11-14 in a cloud session. **Never run on a server yet**: it compiles and CI is
green, but nobody has fought it. Balance numbers are first guesses. Read the test list before the first deploy.

## What it is

The dusk toll has five strokes. Corvin is forgetting the end of the song, so it rings only four. The Hush is
what grows in the gap where the fifth should be: a bell with no tongue, sewn out of dark. It only knows the
lullaby, and it sings it back wrong. Elin is never shown or named; the only trace is a small white candle in
the middle of the belfry that burns after the fight.

Not on the main story line (the twelve bosses in `docs/STORY_BIBLE.md` stay as they are). Beating it does not
fix the bells: "The dusk toll is whole again. For tonight."

## Where the code is

`BossEngine/src/main/java/de/aetherion/bossengine/hush/` (self-contained package, started from `BossEngine`
next to Helios), `bosses/the_hush.yml` (HP 60000, gates 66 % / 33 %, loot), `hush.yml` (everything else).
It reuses from Helios only what is generic: the slot journal (`ArenaSlots`), the void generator
(`HeliosWorld.Void`), the bars (`HeliosBars`) and the location format of `Participants`.

| Class | Job |
|---|---|
| `HushModule` | start, registry, own void world `aetherion_hush` (created on first entry), entry |
| `HushEncounter` | one group's night: arena, people, dread, bars, every way out |
| `HushScript` | the fight (a `BossScript`): moves, both lullabies, echoes, the fifth toll |
| `HushArena` | the belfry in code: floor r=20, 7 lamps (r=10), 7 bells (r=17), rim, night light |
| `HushBody` | ~60 block displays in the shape of a bell |
| `HushGuard` | movement rules, quit/crash safety, clicks on lamps and bells, entry bells |
| `HushText` | every line, English and German (client language decides) |

## The fight

One rule: everything made of dark stops at the edge of a burning lamp (dread, hands, the wave, the echoes'
chill). Its body and the bells' own sound do not.

- **Lamps.** Seven real lanterns. In lamplight (4.6 blocks, drawn on the floor) the Hush takes full damage,
  in the dark a quarter. A snuffed lamp is a black candle; right-click lights it again (5 s lock after a snuff).
- **Dread.** Own bar per player. Rises in the dark, fades in light; Darkness on screen from 60, a hit at 100.
- **Moves.** Snuff (goes to a lamp and puts it out; 1.5 % of its HP inside the wind-up staggers it and saves
  the lamp), Grasp (hands from the floor), Hush (all sound stops, then a wave), Veil Lunge (the one parryable
  strike), Toll (from 66 %: a bell rings by itself, jump the ring).
- **Lullaby at 66 % and 33 %.** All lamps die. The bells show the hymn's first four notes; ring them back in
  order (which bell holds which note is shuffled every night). Each right note relights a lamp. The second
  song has five notes and the fifth bell never shows itself: it is the one with smoke on it. Success: every
  lamp burns, everyone who fell stands up, the Hush lies open for 12 s (x1.6 damage). Time out: a heavy hit
  for everyone in the dark, two lamps left, the fight goes on.
- **Compline (from 33 %).** Two echoes drift around and hum. The real one makes no sound. Hitting an echo
  bursts it and costs dread.
- **Deaths.** One respawn beside a lamp each. After that you watch from above until a teammate lights a lamp
  by hand (one revive per 15 s) or a lullaby holds. Nobody standing for 5 s is a wipe.
- **End.** The bells play all seven notes, then the dusk toll rings five times.
- **Enrage** after 9 minutes: the lamps go out for good.

## Entry

`/hush enter` (alias `/stille`), party leader brings members within 16 blocks. The permission is
`hush.enter` and defaults to **op**, so players cannot reach it until it has a place in the world. Two ways to
open it later, both in `hush.yml`: set `entry.permission: ""`, or list a bell under `entry.bells`
(`"world,x,y,z"`): right-clicking that bell enters.

Admin (`hush.admin`): `/hush start [player]`, `abort [slot]`, `status`, `skip` (HP just under the next gate),
`solve` (finish the running lullaby), `move <snuff|lunge|hush|grasp|toll>`, `lamps <on|off>`,
`dread <0-100>`, `reload`.

## Not done

- No lore find. A "Fundstück" belongs in `StoryLore` (Items) and `LoreFindListener` (Quests); this change
  ships BossEngine only.
- No resource pack assets, no custom sounds: vanilla blocks, displays, particles and note-block sounds.
- No place in the world: no NPC, no door, no quest.
