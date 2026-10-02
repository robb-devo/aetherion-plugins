# Dungeon Floor 1 elevate + transfer sync

Scope: AetherionDungeons, plus a 2-line AetherionQuests fix. Core, Items and Hub are unchanged. Floors 2/3, Blossom Blade, Gravwell, Talk UX, ranks and Island/Guilds are not touched.

## What's in it

- **Transfer v6: one character on mmo-r and mmo-d.**
  - Every way out packs the character once: portal, `/dungeon transfer|enter|boss|home`, `/dhub`, pending floor, quit, shutdown.
  - Every way in applies it once. The snapshot id is recorded in `ledger/<uuid>.yml`, so a second apply is skipped.
  - The player is frozen while crossing. If Connect gets no answer within 8 s, the snapshot is taken back.
  - Before anything is overwritten it goes to `quarantine/`. Nothing is silently dropped; an item that can't be encoded blocks the hop.
  - DC inside a run uses the **fresh** policy: the gear stays and the run is closed. Crash: the player is redirected to the backend that holds the character.
  - First v6 contact per backend: the old per-server leftovers are merged, minus anything the snapshot already carries (no ender-chest dupes).
- **Floor 1 room pool.**
  - 27 templates in a prison palette: lobby, 14 small, 5 medium, 4 large, 2 huge, and the boss.
  - Per run: a seeded shuffle of 7 combat rooms (2 of them branches), lobby → halls → mini-boss hall → boss → exit.
  - Config: `floor1-pool.*`. Extra rooms go in `plugins/AetherionDungeons/structures/floor1/` plus `pool.yml`.
  - Previews: `contact.png` (all rooms) and `sample-runs.png` (3 seeds).
- **Gear loop.** Vestige slot rolls count pieces already owned (bag and ender), so you don't get duplicate vestige drops. All loot ids resolved; no dead paths found.

## Land, build, deploy

```powershell
cd C:\Users\Robbi\IdeaProjects\_dungeon_f1_ship
powershell -ExecutionPolicy Bypass -File .\apply-dungeon-f1.ps1 -DryRun
powershell -ExecutionPolicy Bypass -File .\apply-dungeon-f1.ps1              # worktree mining-eldervale-progression-65660c
# or: .\apply-dungeon-f1.ps1 -Target ..                                       # main tree instead
mvn -DskipTests -pl AetherionDungeons,AetherionQuests -am package
```

- **How the apply script behaves:**
  - Guarded by SHA-256. On any conflict it writes nothing (`-Force` overrides).
  - Overwritten files are backed up to `backup\<time>\`.
  - It also copies the main tree's newer DungeonChestProps / AshesEncounter / BossEngineBridge / DungeonLootFx / DungeonListener into the worktree (the live jar has them).
- **Deploy targets:**
  - `AetherionDungeons.jar` → mmo-r **and** mmo-d, **together**. v6 and the old v4 jar don't understand each other's files.
  - `AetherionQuests.jar` → mmo-r + mmo-d.
- **Restart:** restart mmo-r and mmo-d with a graceful stop, so `onDisable` packs online players.
- **Config:** no config edit needed; new keys have code defaults. On startup, check `[Transfer] character-sync=true here=mmo-r` (or `here=mmo-d`). If `here` is wrong, set `remote-transfer.this-server`.
- **Rollback:** set `remote-transfer.character-sync: false` and restart.
  - With that setting, pending characters still get applied, but quits stop packing. That is the old per-server behaviour.
  - Don't put the old jar back straight away. v4 never reads `*.char.yml`, so characters packed on quit would sit in the shared dir unapplied.

`patches\dungeon-f1-vs-1922f36.patch` is the same Dungeons change as a git diff (without the main-tree sync). `gen\` holds the room generator (`python build_pool.py out`).

Not compiled against real Paper here, because there's no Maven repo in my sandbox. Here is what was checked instead:

- Full module javac against generated Paper API stubs and the real AetherionCore jar. The only errors left are decompile artifacts in the 5 main-tree files, which don't ship as-is.
- The planner, over 3000 seeds × 4 configs: 0 failures, 0 overlaps, 0 misaligned doors.
- The apply script, on a simulated tree.

The first real compile is yours.

## Test

1. **Portal mmo-r → mmo-d → back.**
   - Check that these come across the same: inventory, armor, offhand, ender, XP level, coins, pets, active quest progress, booster/vial stack counts.
   - Logs: `[Transfer] SAVE id=…` on the sender, `[Transfer] APPLY id=… from=…` on the receiver.
2. **Spam the portal / `/dungeon transfer`.** Expect one hop, no dupes.
3. **Quit during a Floor 1 run on mmo-d, rejoin.** Gear is intact and you get the "Floor 1 run closed" message.
4. **Quit mmo-r, immediately `/server mmo-d`.** It waits up to about 6 s, then applies.
5. **Hard-stop mmo-d while a player is on it, then join mmo-r.** Expect a redirect to mmo-d or the guest message. Gear must not be copied.
6. **`/dungeon sync <player>`** shows the holder and last snapshot ids. `quarantine/` in the shared transfer dir should stay empty in normal play.
7. **`/dungeon pool`** shows 25 combat + lobby + boss.
   - Run Floor 1 three times: a different room order each time, and a log line `[Floor1] Run seed=… lobby=… c0=…@rot …`.
   - Every door opens into a corridor. The mini-boss is in the last hall. There's one loot room. The boss gate opens and the exit portal is behind the boss.
8. **Floors 2/3 and `/dungeon enter`** still behave as before.
