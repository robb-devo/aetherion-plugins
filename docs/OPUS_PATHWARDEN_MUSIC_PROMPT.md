# Opus prompt — Pathwarden NBS (Balkan Bump)

Copy everything below the line into a **new** Opus chat.
Recommended effort: **Extra or Max**.
Attach: `docs/Balkan_Bump_Varshaver_Remix.nbs` (also in `BossEngine/src/main/resources/melodies/balkan_bump.nbs`).

Wire-up already exists in BossEngine (`NbsSong` + `FightMusic` + `PathwardenDirector`). Your job is **song quality only** — improve / replace the `.nbs`. Do not redesign Pathwarden combat.

---

```text
# PATHWARDEN FIGHT MUSIC — improve this .nbs only

You are refining one Open Note Block Studio song for the Aetherion boss **Pathwarden**.

## Attachments / files
- `Balkan_Bump_Varshaver_Remix.nbs` (also shipped as `BossEngine/.../melodies/balkan_bump.nbs`)
- Optional: original MP3 if Robb attaches it again

## Hard rules
1. Touch **only** the `.nbs` (and a short README/notes if useful). No Pathwarden combat, no Gravwell Cleaver, no other bosses, no network code.
2. Do **not** connect systems, deploy, or rewrite Java unless the existing NBS v5 parser is broken (it already reads version-5 headers; tempo = ticks_per_second × 100).
3. Parser contract (must stay true):
   - Modern NBS header: first short = 0, then version byte
   - Tempo field = **ticks per second × 100** (this file: 973 → 9.73 tps)
   - Player must use that tempo — never assume fixed 10 or 20 song-tps
4. Keep length in the same ballpark (~228 s / ~2224 song ticks) unless shortening clearly improves the loop for a fight bed.
5. Layers today: Lead, Gegenstimme, Bass, Kick, Snare, Hihat.
   Instruments already octave-mapped for in-game pitch: Iron Xylophone, Pling, Bass + drum kit (Bass Drum / Snare / Click).

## Source quality (honest brief from the transcriber)
This `.nbs` was auto-derived from the MP3 with **no ear and no hand polish**. Match vs original was only moderate.
- Bass / rhythm / drums: should be recognizable
- Melody: approximation
- Gegenstimme: can sound restless — mute/delete that layer if it hurts

## Your mission
Open the file in Note Block Studio (or equivalent), listen against the MP3, and make it a **fight-ready Pathwarden bed**:
- Keep the Balkan groove / drive that sells a road-gate toll fight
- Clean the lead so it sings in note-block range
- Tame or rewrite the Gegenstimme (or mute it)
- Tighten drums so kick/snare/hat read in Minecraft
- Stay inside note-block instrument + octave limits so BossEngine playback stays in tune
- Prefer a clean loop point for `pathwarden-music.loop: true`

## Deliverable
1. Improved `balkan_bump.nbs` (same resource name preferred)
2. 5–10 line NOTES.md: what you changed, which layers to mute if still imperfect, tempo confirmation (raw + tps)

Go make the gate sound expensive.
```
