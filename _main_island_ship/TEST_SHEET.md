# In-game test sheet — Origin Isle

Setup: deploy the Hub jar (plus Items if you applied the DEV tile), restart, and join as op on MMO-R.

1. **Boot.** Check the log:
   - `Origin Isle: 13 districts · 39 landmarks · 8 waystones · 5 vistas · 7 bells · 2 updrafts · 4 flights · 40 emitters`
   - `Origin: seeded camp 'summit' …` and `… 'whisperwood' …` (first boot only)
   - no stack traces
   - `plugins/AetherionHub/origin.yml` and `origin-cast.yml` exist.
2. **Surface regression.**
   - `/spawns` shows every old camp in its old slot, plus Summit (23) and Whisperwood (24).
   - `/harbour`, `/mining`, `/fishing`, `/forage` etc. still teleport.
   - `/hubadmin list` works, and `/propwand` gives the wand.
   - The Mining and Forage pads still launch and land, and a sealed pad still refuses.
3. **Fresh journey.** `/origin dev profile reset`, then `/capital` (or walk in):
   - you get the district card and `+150`;
   - the clickable **[Take the tour]** line appears;
   - `/origin` shows 1/13 districts.
4. **Tour.** Click it:
   - Orla at Ledger's Court: the step advances.
   - Walk to Fountain Square.
   - Cobb at the Mountain Gate.
   - Step on the puffing vent behind him: you rise about 94 blocks and glide onto the terrace. No fall damage; Skyrider counter +1.
   - Walk up to Stellan: vista labels float toward every district (only you see them).
   - Step into the ring at the cliff edge: the Summit Glide lands you on the plaza.
   - Sister Aurel at Scholars' Hall, then Hearthcap on the west terrace: attune.
   - Result: **Tour complete +1,000**. `/origin tour` again → it completes without coins.
5. **Glowcaps.** Right-click Hearthcap. The menu lists the 8, with attuned ones clickable. Travel to one you attuned: 8 s cooldown, slow fall, no damage. An unattuned one points the compass.
6. **Bells.**
   - Ring Scholars' Bell: note + `+60` + `1/7`.
   - Shoot the High Tower Bell at Bell Wharf with an arrow: it counts.
   - `/origin dev toll`: everyone nearby hears the toll.
7. **Skyways.**
   - `/origin go flight:southeast_skyway`, then step on the SE arch pad: a 12 s flight lands on the plaza.
   - Same for the SW pad and the Crag Glide (`/origin dev tp crag_peak`, then step into the ring).
   - Mid-flight, `/spawn`: the flight ends cleanly.
   - Sprint while flying: nothing breaks.
   - Log out mid-glide and log back in: you are on the landing spot with slow falling, not 170 blocks up. The same goes for logging out mid-updraft (you land on the ledge).
   - Get teleported mid-updraft (`/spawn`): the updraft lets go.
8. **Rescue.** Jump off the east edge: below y −60 you are put back on your last footing with slow fall and no damage. In creative, nothing happens.
9. **Moments.** `/origin dev event lantern-festival` (lanterns rise, fireworks don't hurt), then `aurora`, `starfall` (sneak + look up → `+25`, a second time in the same Starfall → nothing), `petal-storm`, `harbour-fog` (at the docks), `rainbow`, then `event stop`: lanterns vanish.
10. **Fountain.** Sneak + right-click the Fountain Square water with an empty hand: `-10`, a fortune, a splash. Again within 60 s → "still settling". With a full hand → vanilla behaviour.
11. **Settings.** `/origin settings`: turn particles off → the lighthouse, fireflies, pad columns and moments disappear for you only. Turn ambience off → no soundscape or tolls.
12. **Camps.**
    - `/summit` while locked: the locked message plus a line like "It's 280m N of you. /origin go camp:summit points the way."
    - Walk onto the summit: **NEW AREA · SKYREACH SUMMIT**. `/summit` now works.
    - Same for Whisperwood at the old shrine.
13. **DEV.**
    - Items DEV → WORLDS → slot 39 **Origin Island** opens `/origin dev` (Mining 37 and Forage 38 are unchanged).
    - Take Orla's anchor and place her somewhere else; `/origin dev cast presets` puts everyone back.
    - `/origin dev softlight capital`, wait for "done", then `softlight undo` removes the same count.
14. **Wipe.** Items Full Player Wipe on a test account: the hub unlocks **and** the `origin-players/<uuid>.yml` file are gone.
15. **Restart twice.** No duplicate villagers, glowcap labels or sprouts. `/origin dev status` shows cast 5/5.
16. **Regression.** Quests NPCs on the island (Ledger, Vex, Egon, Vince, Proctor…) talk exactly as before. The Harbour Hour tutorial is not interrupted: a quiet player (Capital camp not unlocked yet) gets no district titles and no tour offer.

If something fails: `/origin dev status` gives a one-line state. Grep the log for `Origin` (and `origin-*.yml` save warnings). `enabled: false` in `origin.yml` plus `/origin dev reload` switches the whole layer off without a restart; the Hub itself keeps running.
