# Tiefen-Scan 2. Oktober 2026

**Stand:** `main` @ `1922f36` (29.09.2026). `git fetch origin` hat keine neueren Commits gebracht. 93 Commits auf `main`, 222 über alle Branches, rund 70 Remote-Branches.
**Methode:** Nur gelesen. Kein Gameplay-Code geändert. Verglichen wurden alle Remote-Branches gegen `main`, die vier großen Deploy-Merges Eltern gegen Ergebnis, die Import-Commits vom 28./29.09., Registrierungen in `onEnable` / `plugin.yml`, und die Tick-/Event-Pfade in jedem Plugin.
**Live-Server:** Welches JAR wirklich auf dem Hetzner läuft, steht nicht in diesem Repo. `aetherion-control` ist eine Android-Fernbedienung für Crafty (Port 5055), kein Git-Deploy. Wenn zwischen dem 20. und 24.09. JARs aus `cursor/*`-Branches liefen und danach `main`, sind die unten genannten Fixes auf dem Server weg, obwohl `main` sie nie enthalten hat.

---

## Kurzfazit

Die Deploy-Kette vom 28./29. September hat Helios, World Eater, Seraphine, Midgame-Skills, Farming- und Fishing-Eldervale **zusammengeführt, ohne Dateien aus den Eltern zu löschen**. Die Additive-Regel hat auf dieser Kette gehalten.

Was fehlt, fehlt nicht, weil ein Merge es still überschrieben hat. Es fehlt, weil ab `ddcdc7c` (20.09., nach PR #19) Dutzende `cursor/*`- und `claude/*`-Branches **parallel** weiterliefen und nie in die Deploy-Kette kamen. `main` ist der Deploy-Stand. Diese Branches sind 1 bis 18 Commits voraus und 0 bis 50 Commits zurück.

Die bekannten Symptome haben im aktuellen `main` konkrete Treffer, aber nicht die, die man zuerst vermutet:

| Symptom | Was der Code heute hergibt |
|---|---|
| Boss nicht treffbar | Die Schaden-Pipeline ist da. Treffer sterben an `isDamageBlocked()`, Helios-Schild, Frost-Reflex oder Angriffs-Cooldown unter 0,85. World Eater im Fight ist treffbar. |
| Waffeneffekte weg | Listener sind registriert, doppelt sogar. Der Manager-Stern **überschreibt Hotbar-Slot 8** ohne das alte Item zu retten. Lag die Waffe dort, ist sie weg. |
| Dev-Menü / Manager | Manager-Tabs sind vollständig (19 Slots, unverändert durch den Sync). Dev-Seiten aus der Zeit vor dem Sync existieren noch. Citrus / Monkey / Beta sind Knöpfe ohne Backend. Ein „Death Menu“ gibt es in der gesamten Historie nicht. |
| Command-Skills | `/skills` ist in `plugin.yml` und in `onEnable` verdrahtet. Nicht verloren. |

Die Performance-Last sitzt in wenigen globalen Schleifen. Foraging-Vorschau und Borderlands-Mobzonen sind die zwei größten. Dungeons kommen dazu, weil Raum-Checks die ganze Welt scannen.

Die Matrix aller relevanten Branches steht in [`BRANCH_GAP_2026-10.md`](BRANCH_GAP_2026-10.md).

---

## Teil 1 — Verlauf und Regressionen

### 1.1 Was `main` ist

`main` ist nicht „der alte Stand plus alles“. Nach dem 20.09. (`ddcdc7c`, async `/aetherpaste`) geht die Linie in die Deploy-Bäume und nicht in die `cursor/*`-Fixes:

```
ddcdc7c  20.09.  letzter gemeinsamer Punkt mit fast allen cursor/*-Branches
f7f60a1  28.09.  World-Eater-Vorbereitung, bringt Hollow Sun + Seraphine als Referenz mit
d5a5c4b  28.09.  Midgame-Skills (Spitze von feature/midgame-skills-gather)
f7ec15c  28.09.  Merge World-Eater-Ending in den Midgame-Baum
571c919  28.09.  Merge World Eater in Helios (Helios + World Eater + Saint + Hollow Sun)
9f59031  28.09.  Merge Helios-Polish in den Farming-Baum
3949b28  28.09.  Merge Midgame-Baum in den Farming-Baum
5a79cc3  28.09.  Import Farm-Isle-WIP
83389c1  29.09.  Import Items-Hotfix-WIP
4ebaf43  29.09.  Fishing-Eldervale
26f625d  29.09.  Eldervale-Sync (Forage / Codex / NPC / Mining), 306 Dateien
1922f36  29.09.  Spitze von main
```

Vollständig in `main` enthalten (0 Commits voraus): `claude/helios-polish`, `claude/confident-bell-u0mcjv`, `feature/midgame-skills-gather`, `feature/world-eater-boss`, `claude/fishing-eldervale-expansion`, `cursor/dialog-de-overlay-cloud`.

Nicht in `main`: Early Game (`claude/early-game-harbour`, 5 Commits), Seraphine-Branch als Git-Linie (der Code ist trotzdem da, siehe unten), Hollow-Sun-Elevation, Terminus-Branch als Git-Linie (der Code ist da), `npc-life-elevate` (1 Commit auf der aktuellen Spitze), und der ganze `cursor/*`-Stapel vom 20.–24.09.

### 1.2 Die vier Deploy-Merges haben keine Dateien gelöscht

Geprüft mit `git diff --diff-filter=D` beider Eltern gegen das Merge-Ergebnis:

| Merge | Betreff | Dateien, die Eltern-1 verlor | Dateien, die Eltern-2 verlor |
|---|---|---|---|
| `f7ec15c` | World-Eater-Ending → Midgame | keine | keine |
| `571c919` | World Eater → Helios | keine | keine |
| `3949b28` | Midgame → Farming | keine | keine |
| `9f59031` | Helios-Polish → Farming | keine | keine |

Zeilen inside von Dateien wurden umgeschrieben. `3949b28` hat gegen Eltern-1 unter anderem `SkillMenu.java` stark verändert (`+565/−208`). Das ist der Skills-Spine aus dem Midgame-Baum, der auf `main` liegt und registriert ist, kein verschwundener Befehl. Gegen Eltern-2 (der eingehende Branch) sind die Diffs klein und additiv.

Seraphine ist byte-identisch: `HangingSaintDirector.java` hat auf `main` und auf `origin/claude/boss-hanging-saint` denselben Blob `788e4997`. Der Saint-Branch ist kein Vorfahr von `main`, der Fight wurde über `f7f60a1` in den Deploy-Baum kopiert. Loot-Listener `SeraphineGearListener` und `SeraphineMusicBoxListener` sind auf `main` registriert.

Terminus (`TerminusEdge`, `TerminusBorder`, Dev-Eintrag, `CustomItem.create` mit Kommentar „restored showcase factories“) ist seit `26f625d` auf `main`. Der Branch `cursor/terminus-absolute-item` muss nicht noch einmal gemergt werden.

### 1.3 Die Import-Commits

| Commit | Umfang | Urteil |
|---|---|---|
| `5a79cc3` Farm-Isle-WIP | 16 Dateien, +2399/−33 | additiv |
| `83389c1` Items-Hotfix | 4 Dateien, +207/−20 | additiv: Manager-CMD 3500, Dev-Glas-Regel, Hollow-Sun-Set im Dev-Menü |
| `4cb925c` / `4ebaf43` Eldervale-Ausbau | jeweils Tausende Zeilen neu, kaum Löschungen | additiv |
| `26f625d` Eldervale-Sync | **306 Dateien, +65210/−1782** | der einzige Commit, der breit überschreibt |

`26f625d` ist der gefährliche Sync, aber er hat die Menü-Kataloge nicht geleert:

- Jede Aktions-Zeichenkette (`page:…`, `give:…`, `skills:…`, `test:…`), die vor dem Commit in `DevMenu.java` stand, existiert danach irgendwo unter `AetherionItems/.../menu/dev/`. Die Seiten wurden aufgeteilt (`DevHubs` und Nachbarn), nicht gestrichen. Aktuell 130 Aktionen, vorher 94.
- `AetherionManagerGUI`: dieselben 19 Tab-Slots vorher und nachher (`SHOP` 10 bis `ISLAND` 40, `DEV` 45, `CLOSE` 49). Die −108 Zeilen sind Text, keine fehlenden Tabs.
- `CollectionGUI` / `BestiaryGUI`: innere Halter-Klassen umgebaut, die Menüs sind noch da.
- `DevMenu.applyChrome` (`DevMenu.java` 413–418) füllt Slot 4 nur, wenn dort nichts oder eine Glasscheibe liegt. Inhalt wird nicht übermalt. Der Kommentar bei Zeile 1863 („Webweave was only in the dead contents(SETS) list — surfaced here“) zeigt, dass eine tote Liste schon einmal aufgefallen und nachgezogen wurde.

Bewusst entfernt, und der Nachfolger ist registriert:

- `dc1f433` — fünf `QuestBossBarHook`-Reflections ersetzt durch `AetherServices`.
- `1a814ee` — vier kopierte `VoidChunkGenerator` zu gemeinsamer Infrastruktur.
- `c597d25` — `MiningListener` aus `AetherionItems` nach `AetherionMining`. Registriert in `AetherionMining.java` Zeile 51.
- `0890389` — Befehl `forage` heißt `forageadmin`. Einziger `plugin.yml`-Befehl, der auf `main` jemals verschwunden ist.

Konstruktoren, die `AetherionItems.java` jemals angelegt hat und heute nicht mehr: nur `MiningListener`. Das ist der Umzug, kein Loch.

### 1.4 Was wirklich nicht auf `main` ist

Das sind Features und Fixes, die nur auf Branches existieren. Details und welche Datei auf welchem Branch liegt: [`BRANCH_GAP_2026-10.md`](BRANCH_GAP_2026-10.md).

**Early Game, der volle Pass, ist nicht gemergt.** `claude/early-game-harbour` zweigt bei `ddcdc7c` ab. Auf `main` liegen Teile, die `9ae7fd1` (28.09., „Opus-ready polish pass“) schon mitgebracht hat: `HarbourArrival`, `StarterKitCeremony`, `NpcPresence`, `ExploreChestService`. Es fehlen vier Klassen, die der volle Pass (`b49a61c`) dazugeschrieben hat:

- `OpenRoads` / `OpenRoadsGUI` — Miss Ledgers Brett nach dem Stempel: vier Türen (Vex, Rite Warden, Craftsman, Surveyor), kein neuer Quest-State.
- `GraduationStamp` — der Stempel als kurze Szene auf dem Schreibtisch.
- `ExploreChestProp` — Explorationskisten als Display-Prop statt Vanilla-Block. Der Kisten-Service auf `main` bleibt der Block.

Die Midgame-Dokumente auf `main` (`docs/OPUS_MIDGAME_SKILLS_GATHER_CONTEXT.md`) reden von „nach Open Roads“, die Klassen gibt es nicht. Das ist die klarste Lücke zwischen Anspruch und Baum.

**NPC-Körper, ein Commit auf der aktuellen Spitze, nicht gemergt.** `origin/npc-life-elevate` (`7cd9cdb`) ändert nur `LivingNpcLife.java` (+1141/−329) und eine Config-Zeile: Personas, ruhigere Köpfe, ruhig während des Gesprächs, weichere Patrouillen. Die Sprechblase (`TalkUx`) fasst er nicht an. Nicht verloren, nur nicht auf `main`.

**Hollow-Sun-Elevation ist ein anderer Fight.** `main` hat `HollowSunDirector.java` mit 5095 Zeilen (seit `f7f60a1`). Der Branch `cursor/hollow-sun-elevation` hat 5464 Zeilen plus `HollowAccretion`, `HollowProps`, `HollowSky`. Die drei Klassen fehlen auf `main`. Nicht nachträglich gelöscht, nie in die Deploy-Kette gekommen.

**Hotbar-Slot 8 frisst Items.** Das ist der beste Treffer für „etwas ist plötzlich weg“:

```33:33:AetherionItems/src/main/java/de/aetherion/items/menu/AetherionManagerListener.java
    public static final int HOTBAR_SLOT = 8;
```

`giveManager` (Zeile 190–208) löscht Manager-Sterne in anderen Slots und schreibt dann **ohne Umparken** in Slot 8. Aufgerufen bei Join, Respawn, Weltwechsel und aus Quests (`refreshManagerItem`). Was in Slot 8 lag, ist weg. Der Fix `ManagerSlotGuard` existiert nur auf den nicht gemergten Branches um `8f314c1` (`cursor/stability-recipe-audit-6e79` und der Transfer-Stapel). Commit-Text dort: „Return items the manager star overwrites in hotbar slot 8.“

**Dev-Ränge Citrus / Monkey / Beta sind Knöpfe ohne Backend.** `DevMenu.java` 2889–2897 zeichnet sie. `RankBadgeService.RANKS` (Zeile 27–42) kennt sie nicht, `EXTRA` (Zeile 44) ist nur `mvpplusplus` und `admin`. `setRank` lehnt sie ab. `DevRankBridge` (Zeile 11–16) sagt das selbst: die APIs „only exist in some jar lineages“, Reflection soll das Menü auf jeder Linie kompilieren lassen. `isRobb` und `clearExtra` gibt es auf `main` nicht, der Aufruf schluckt die Exception und meldet „This build cannot remove ultras“ bzw. „Could not set that rank.“ Die Farbklassen `CitrusDye`, `CelestialDye`, `RainbowDye` liegen auf `cursor/playtest-polish-b848` und dem Cosmetic-Stapel, nicht auf `main`.

**Weitere Fixes, die nie ankamen** (nicht als „kaputt gegangen“ lesen, sondern als „nie im Deploy“):

- `DisplayEntities` + `ForageDisplayGuard` (`cursor/forage-hologram-leak-470a`). Die Insel-Hologramme auf `main` sind in `ForageCast` pro Rolle eine UUID, also begrenzt. Ein weltweiter Sweep gegen verwaiste `text_display` fehlt.
- `PlaytimeService` (`cursor/wipe-safe-playtime-99f7`).
- `TransferProgressGuard`, `InventoryTransfer`, `StorageFileMerge` (Transfer-Stapel ab `cursor/hub-inventory-isolation-b414`). `AetherionDungeons` hat ein älteres `TransferSnapshotStore` seit dem Initial-Commit, nicht diese Härtung.
- `MarkerLifecycle`, `NaturalVeins`, `WorldgenOnce` (`cursor/entity-lifecycle-ore-gen-d605`).
- `AshesChestIds`, Floor-2/3-Dichte (`cursor/f3-t3-chests-9aad`, `cursor/f2-f3-mob-density-e44b`).
- `InventorySnapshotPolicy` (Dungeon-Transfer soll Ziel-Gear behalten).

Amethyst ist kein Totalausfall: `VeinsDigZones` liegt auf `main`. Die späteren „Schematic nicht übermalen“-Commits des Dig-Zone-Stapels sind es nicht. Vor einem Merge den Diff lesen, nicht den ganzen Stapel ziehen. Der Stapel enthält auch Katana- und Anvil-Commits.

### 1.5 Die vier Symptom-Klassen, einzeln

**Bosse nicht treffbar.** Die Pipeline ist vollständig. `BossCombatListener.onDamage` hebt vorher abgebrochene Treffer für Boss-Körper wieder auf, dann entscheiden Gates. `BossInstance.isDamageBlocked()` (`BossInstance.java` 558–577) ist eine lange Oder-Kette: Phasenwechsel, `phaseArmorTicks`, sterbende Directors, `hollowSunDirector.blocksDamage()`, `saintDirector.blocksDamage()`, `worldEaterDirector.blocksDamage()`, Helios-`script.blocksDamage()`, T2-Eingraben. World Eater blockt nur `ARRIVING | TRANSITION | DYING` (`WorldEaterDirector.java` 197–203); im Fight kommt der volle Schaden an (`scaleIncoming`, Zeile 205). Die Commits `48c1022` und `09ee4fb` (immer treffbar, Pfeile auf die Hitbox) sind Vorfahren von `main`, `setCollidable(false)` kam danach nicht zurück. Helios blockt, sobald der Act nicht `FIGHT` ist oder ein Schild steht (`ActScript.java` 167–168). Zusätzlich bricht `BossCombatListener` 119–125 Nahkampf ab, wenn die Hand leer ist oder `getAttackCooldown() < 0.85` und es kein Skript-Schlag ist. Das fühlt sich an wie „der Boss nimmt nichts“, besonders bei schnellen Klicks und bei Waffen, die `damage()` mit niedrigem Cooldown aufrufen, wenn `isAbilityOrScriptedMelee` sie nicht erkennt. Ein zweiter, engerer Pfad: Waffen-Procs in `TestPrototypeAbilities` verlangen `bossengine:boss_id` auf dem Körper. Fehlt das Tag, läuft der Boss-HP-Balken und die Waffenfähigkeit tut so, als wäre da nichts.

**Waffeneffekte weg.** Registriert in `AetherionItems.registerCombatListeners` (Zeile 247–286), ohne Feature-Flag: Ashen Katana, Gravwell, Seraphine, Portal Gun, T2-Uniques, Test-Gear, Worldhide/Worldbite, Dawnbearer/Solstice, Hollow Sun, Healer, Dungeon-Gear. `/skills`-Procs laufen über `ProgressionEffects` (Zeile 401). Nichts davon wurde vom Helios-Merge oder vom Items-Hotfix abgeklemmt. Zwei echte Fehler bleiben:

1. Slot 8, siehe oben. Respawn und Weltwechsel (Dungeon, Insel, Hub) lösen `giveManager` aus. Eine Waffe in Slot 8 verschwindet, der Effekt mit ihr.
2. `TestPrototypeAbilities` registriert sich im Konstruktor selbst (`TestPrototypeAbilities.java` 95–98). `GravwellCleaverListener` (Zeile 36) und `TestGearListener` (Zeile 77) bauen je eine Instanz. Zwei Hörer auf denselben Events, zwei Cooldown-Maps. Das sieht aus wie doppelte Procs oder „der Cooldown stimmt nicht“, nicht wie ein fehlender Listener.

Die Katana-Polish-Branches (`cursor/katana-smooth-dash-07b5` und Nachfolger, `AshenKatanaListener` dort 385 Zeilen, auf `main` 576) sind **nicht** der verlorene Stand. Sie sind spätere Gefühls-Experimente und unter der Waffen-Sperre nicht anzufassen. `main` hat mehr Effekt-Code als diese Branches, nicht weniger (`SeraphineGearListener`, `HeliosGearListener`, `WorldEaterGearListener` existieren auf den Polish-Branches gar nicht).

**Dev-Menü, Death-Menü, Manager.** Manager: Tabs vollständig, Freischaltung über `ProgressionService` (`AetherionManagerGUI.gates`, ab Zeile 750). Ein Tab, dessen Plugin aus ist (Guilds, Hub, Pets), wird ausgelassen und zählt nicht als gesperrt. Dev: Seiten da, Glas-Regel übermalt Inhalt nicht, Citrus/Monkey/Beta tot, siehe 1.4. Death-Menü: Suche über die ganze Historie nach `Death Menu`, `death menu`, `Todesmenü` ist leer. Es gibt keine Klasse dieses Namens. Der Respawn-Pfad gibt den Stern neu (`AetherionManagerListener` auf `PlayerRespawnEvent`) und ruft dabei denselben Slot-8-Overwrite auf. Wenn „Death Menu“ eine Kiste beim Sterben meint, liegt sie nicht in diesem Repo.

**Command-Skills.** Vorhanden.

```99:104:AetherionItems/src/main/resources/plugin.yml
  skills:
    description: Open the Aetherion skill loadout
    aliases:
      - skill
      - aetherskills
      - aetherskill
```

`AetherionItems.java` 128–141 baut `SkillService`, `SkillMenu` und `SkillCommand`. Admin-`xp` aus `d5a5c4b` ist Vorfahr von `main`. Die Insel-Skills (perfekter Hieb, perfekte Rolle, Bird-Scare) liegen in Foraging/Fishing/Farming und haben nie eigene Befehle gehabt. Wenn der Skills-Tab „fehlt“, ist das die Progressions-Sperre im Manager, kein gelöschter Befehl.

### 1.6 Was man nicht als Verlust zählen sollte

- Website unter `cursor/website-platform`. Eigenes Produkt, nie Teil der Plugin-JARs.
- StressBots-Polish nach dem 19.09. QA-Werkzeug.
- `cursor/npc-editor-studio-wip`. Commit-Text: kompiliert nicht.
- Katana-/Sheath-Polish-Ketten. Waffen-Sperre. `main` ist der Deploy-Stand der Listener.
- `javac.*.args` auf dem Early-Game-Branch. Müll, nicht mergen.

---

## Teil 2 — Performance

Sortiert nach wahrscheinlicher Last auf einem laufenden Server mit Hub, Borderlands und Dungeons gleichzeitig. Zeilen sind der Stand `1922f36`.

### 1. Kritisch — Foraging-Vorschau, jede Tick, fast jede Welt

`AetherionForaging.java` Zeile 83 startet `foragingListener::tick` mit `1L, 1L`. Die Task wird nicht gespeichert. `tickLookPreviews` (`ForagingListener.java` 488) läuft über **alle** Online-Spieler. Wer eine Axt hält und nicht im Dungeon, nicht auf der Farm-Insel und nicht auf einer Gilden-/Privatinsel ist, gilt als Foraging-Welt (`isForagingMinigameWorld`, Zeile 636–654). Hub, Borderlands, Mining, Eldervale fallen durch. Pro Blick ruft `peekTreeId` (Zeile 614) `collectLogs` auf, eine BFS bis 96 Stämme (`MAX_LOGS`, Zeile 63, Schleife Zeile 1306) plus Kronen-Suche. Das skaliert mit der Spielerzahl, nicht mit der Bevölkerung der Forage-Insel. Daneben tickt `ForageIsle` noch einmal pro Tick, aber nur für die Insel und mit Abbruch in `shutdown()`.

### 2. Kritisch — Borderlands-Mobzonen, großes Budget und Welt-Scans

`MobZoneService` (Konstruktor Zeile 113) tickt alle 60 Ticks. Oberflächen-Ziel ist Dichte 0,0041, mindestens 200, höchstens 280 Kreaturen (`SURFACE_MIN` / `SURFACE_MAX`, Zeile 58–59), Krypta zusätzlich bis 55, Eldervale bis 40. Nachfüllen in Batches bis 36. `clearVexTaboo` (Zeile 1051) iteriert `world.getLivingEntities()` der ganzen Welt, nicht einen Radius. `findSpot` lädt Chunks. Leerstehende Zonen werden übersprungen (`playersNearZone`), das ist der gute Teil. Sobald jemand in den Borderlands steht, ist das Budget an KI und Pathfinding dauerhaft da, und der Vex-Sweep kostet proportional zu allem, was die Welt sonst noch hält (Dungeon-Leichen, Forage-Displays, Boss-Adds).

### 3. Hoch — Lebensbalken aller Spieler alle 5 Ticks

`HealthListener` Zeile 61–66, `tickHealthDisplay` Zeile 225. Jeder lebende Spieler, Ausrüstung lesen, Attribut schreiben, alle 16 Pulse natürliche Regeneration. Unabhängig davon, ob sich die Rüstung geändert hat. `refreshHealth` existiert schon für den Fall nach einem Ausrüstungswechsel. Der 5-Tick-Loop macht denselben Job noch einmal für alle.

### 4. Hoch — Boss-Caps scannen jede Welt

`BossManager.countLivingTagged` (Zeile 254) läuft für Templates mit `maxInstances == 1` über jede Welt und jede lebende Kreatur. Der Ticker selbst wird in `stop()` abgebrochen, das ist in Ordnung. Der Scan ist es nicht, sobald Mobzonen die Kreaturzahl hochziehen.

### 5. Hoch — Dungeon-Räume scannen die ganze Instanz-Welt

`InstanceManager` Zeile 36: `scanActiveRooms` alle 20 Ticks. Endless und Ashes steigen früher aus (Zeile 558–563). Der Prototyp-Floor nicht. `PrototypeDungeonBuilder.countRoomMobs` (Zeile 248) zählt mit `world.getLivingEntities()` und filtert danach per PDC. Pro Raum, pro Sekunde, pro laufender Instanz. Bei mehreren Räumen und übrig gebliebenen Kreaturen wird das quadratisch. Ein Index „Kreatur → Raum“, gepflegt beim Spawn und beim Tod, ersetzt den Scan. `onCombatMobDeath` ruft `tryClearRoom` schon gezielt auf. Der Sekunden-Scan ist der Gurt dazu, kein Ersatz für einen Index.

### 6. Hoch — Aetherion-Set-Minion, alle Spieler alle 2 Ticks, dazu ein Welt-Sweep

`AetherionSetListener` Zeile 66: Timer `2L, 2L`. Jeder Online-Spieler wird auf das volle Set geprüft. Wer es trägt, bekommt Teleport und Projektil. Alle 20 Timer-Ticks, also alle 40 Spielticks (2 Sekunden), ruft `purgeOrphanMinions` (Zeile 500) `world.getEntities()` auf **jeder** Welt auf. Nicht zehnmal pro Sekunde, aber ein voller Entity-Scan auf einem Server, der schon zu viele Entities hat. Kein gespeichertes `BukkitTask`, Abbruch nur über Plugin-Disable.

### 7. Hoch — Estate-Liquidator, jede Tick, jeder Spieler

`EstateLiquidatorListener.java` Zeile 57: `stopSpyglass` mit `1L, 1L`. Zeile 78 läuft über alle Online-Spieler und schaut Haupt- und Nebenhand an, auch wenn niemand das Item hält. Der 40-Tick-`tick` daneben ist harmlos im Vergleich.

### 8. Hoch — mehrere Schaden-Pipelines auf jedem Treffer

Auf `EntityDamageByEntityEvent` liegen mindestens `DamageListener` (Items, HIGHEST), `BossCombatListener` (HIGHEST, läuft auch wenn das Event schon abgebrochen ist, Zeile 60), `WildlifeCombatListener`, Dungeon, Pit, Pet-Skills, Schadenszahlen. Der Boss-Hörer steigt nach einem Map-Lookup aus, wenn es kein Boss ist. Trotzdem ist die Kette auf jedem Mobs-Treffer in den Borderlands und im Dungeon der Tick, den der Spieler spürt. Ein früher Typ-Check an einer Stelle wäre der strukturelle Schnitt. Die Formeln selbst nicht anfassen, schon gar nicht an Blossom Blade und Gravwell.

### 9. Mittel — Gilden-Minions und Sync-Saves

`AetherionGuilds.java` Zeile 97: `minions::tickVisuals` alle 4 Ticks. `MinionService.tickOneVisual` (Zeile 388) läuft pro Minion über die Spieler der Welt. Stündlich und an mehreren Stellen `guilds.save()` auf dem Main-Thread, dazu `BankMenu` bei jedem Klick (Zeile 160, 191, 206). Auf einer leeren Gildenwelt ist das still. Mit vielen Minen und Bank-Klicks hängt der Tick an der Platte.

### 10. Mittel — Borderlands-Riten, vier Timer und YAML vom Main-Thread

`BorderlandsRiteService.java` 125–128: Strahlen und Umriss alle 5 Ticks, NPC-Refresh alle 100 Ticks mit `YamlConfiguration.loadConfiguration` (Zeile 205), Boss-Blasen alle 20 Ticks inklusive Mob-Löschen in der Nähe. Der Altar-Umriss läuft, sobald der Service steht, nicht nur während eines Kampfs. Die Leak-Fixes auf `cursor/display-entity-leak-edae` (ein Display wiederverwenden) sind nicht auf `main`. Ob der aktuelle Umriss noch spawnt, ohne aufzuräumen, ist der erste Blick, bevor man die Timer anfasst.

### 11. Mittel — der Rest, kurz

- `ColosseumGateService`: Partikelring für Spieler in 48 m, plus `PlayerMoveEvent` im Ring.
- `DungeonProgressHud`: alle Spieler alle 5 Ticks, auch außerhalb. Wird in `onDisable` sauber gestoppt. Billig pro Spieler, unnötig global.
- `DungeonListener` Bewegung: Block-Lesen und Raum-Graph bei jedem Schritt in Dungeon-Welten. `gateCooldown` wird beim Quit nicht geleert (kleines UUID-Leck).
- `AutoPickupListener`: alle 10 Ticks `getNearbyEntities` für freigeschaltete Accounts.
- `HeliosModule` und `WorldEaterSite`: 1-Tick-Schleifen, aber nur solange der Fight bzw. die Site läuft, und beide brechen die Task ab. Während des Kampfs teuer, im Leerlauf nicht.
- `HabitatDiscovery` (Aethermobs): 1-Tick-Timer, aber Round-Robin, ein Spieler alle ~2 Sekunden, mit Cache. So lassen.
- `PortalGunPortals`: 1-Tick für aktive Portale, `shutdown()` bricht ab und wird aus Items-`onDisable` gerufen.
- `IsleWeatherService`: Fußabdruck-gefiltert, Periode mindestens 20 Ticks.
- Kurz- und Langbogen: 1-Tick-Timer, aber nur über Spieler, die gerade schießen (`ShortbowListener.run`, Map `firing`). Kein Welt-Scan.
- Guild-/Farm-/Mine-/Fish-Inseln: je ein eigener Event-Ticker. Nicht einzeln kritisch, zusammen mit Punkt 1 und 2 schon.
- `AetherionCore`, `AetherionBeta`: im Wesentlichen Speicher-Timer. `AetherionStressBots`: Last nur, wenn Bots laufen.

### 12. Doppelte Systeme

Dieselbe Arbeit an zwei Stellen, jede mit eigenem Timer:

- Feindliche Mobs: `MobZoneService`, `WildlifeLooks`, `BorderlandsRiteService.clearTrashMobs`, Aethermobs-Wildnis.
- Habitate: `PetHabitatZoneService` (Items), `PetHabitat` (Aethermobs), `ForageHabitatService`.
- Licht: `BorderlandsLightPass` und Hub-`SoftLightPass`, beide laufen Block-Timer.
- Lebensanzeige: `HealthListener`, Dungeon-Bossbar, Quest-Kompass.

Das ist weniger ein einzelner Hotspot als der Grund, warum ein Fix an einer Stelle die Last nicht halbiert.

### Vorschlag, ohne ihn zu bauen

Der Aufwand ist klein im Vergleich zur Wirkung, weil die Struktur schon halb da ist:

1. **Ein Tick-Register pro Plugin.** Jede `runTaskTimer`-Stelle gibt die `BukkitTask` an eine Liste, `onDisable` bricht sie ab. Heute verlassen sich die meisten auf „Plugin-Disable räumt alles ab“. Das stimmt beim Entladen, nicht beim `/reload` und nicht, wenn `onEnable` ein zweites Mal Listener hängt.
2. **Foraging-Vorschau nur auf der Insel.** `ForageIsle.playersOnIsle()` macht das schon, einmal pro Tick gecacht. Dieselbe Menge für `tickLookPreviews` nutzen, und den Baum-BFS cachen, solange der Spieler denselben Block ansieht.
3. **Mobzonen: Budget senken und `getLivingEntities` durch einen Radius ersetzen.** `clearVexTaboo` braucht die Kreaturen um Vex, nicht die Welt. Die 200–280 sind eine Design-Zahl. Sie ist der wahrscheinlichste Grund, warum Borderlands historisch verdächtig waren, und sie zieht jeden anderen Welt-Scan mit.
4. **Dungeon: Kreatur-Index statt Welt-Scan.** Spawn schreibt ein, Tod löscht. `scanActiveRooms` liest die Map.
5. **Eine CI-Prüfung gegen verlorene Registrierung.** Beim Build die Menge `plugin.yml`-Befehle und die Menge `registerEvents(new …)` / `getCommand(...).setExecutor` gegen eine eingecheckte Liste diffen. Ein Sync wie `26f625d` wäre dann rot, wenn ein Befehl oder ein Hörer fehlt, statt es drei Tage später im Spiel zu merken. Dieselbe Prüfung kann verbieten, dass `DevMenu`-Aktionen auf Methoden zeigen, die nur per Reflection existieren (`DevRankBridge`).
6. **Ein Deploy-Branch.** Alles, was live soll, landet auf `main` als Merge mit beiden Eltern, nicht als „import WIP“ und nicht als JAR aus einem `cursor/*`-Worktree. `26f625d` und die Reflection-Brücke sind genau das Muster, das die Kontrolle gekostet hat, auch wenn der Sync diesmal die Menü-Aktionen behalten hat.

---

## Teil 3 — Was stehen bleiben soll

Diese Stellen sind überraschend in Ordnung. Nicht „aufräumen“, nicht mit einem Nachbar-Fix anfassen.

- **Die vier Deploy-Merges.** Keine gelöschten Dateien. Wer den nächsten Boss mergen will, macht es genauso: Merge mit beiden Eltern, kein Datei-Import über die Spitze.
- **Boss-Schaden als Architektur.** `BossCombatListener` hebt WorldGuard-Abbrecher wieder auf, World Eater ist im Fight treffbar, der Ticker hat `start`/`stop`. Die Gates in `isDamageBlocked` sind Inhalt, kein Unfall. Nicht pauschal ausbauen, sonst werden Phasenwechsel und Helios-Schild wieder treffbar.
- **Waffen-Registrierung und die zwei gesperrten Waffen.** Blossom Blade (`AshenKatanaListener`) und Gravwell sind registriert. Ihre Fähigkeiten, VFX und Timings nicht anfassen. Die Polish-Branches nicht „der Ordnung halber“ mergen.
- **Sprechblase.** `TalkUx` plus `NpcPresence` ist auf `main` und wird von Dialog, Living-NPCs und dem Kit benutzt. `npc-life-elevate` darf Körper und Patrouille ändern. Die Blase, die Antwort-Chips und die Sicht nur für den sprechenden Spieler bleiben.
- **`/skills` und der Manager-Tab-Satz.** Verdrahtet, Slots seit vor dem Sync dieselben. Die Progressions-Sperren sind Absicht (Hafen-Reihenfolge in `gates`).
- **Seraphine auf `main`.** Gleicher Blob wie der Saint-Branch. Nicht noch einmal aus `claude/boss-hanging-saint` mergen, der Branch ist 50 Commits hinter `main` und würde den Rest zurückdrehen.
- **Terminus, Helios-Gear, Worldhide, Hollow-Sun-Set-Listener.** Seit dem 29.09. im Deploy, im Dev-Menü erreichbar.
- **Forage-Insel-Spieler-Cache, Boss-Ticker-Abbruch, Dungeon-HUD-`stop`, Portal-Gun-`shutdown`, Habitat-Round-Robin, Spawner-`reload` das erst `stop` aufruft.** Die sauberen Timer. Als Muster für Punkt 2.1 nehmen, nicht umschreiben.
- **Kurz- und Langbogen-Timer.** Nur aktive Schützen.
- **`AetherServices` statt Reflection-Hooks** (`dc1f433`). Die Richtung ist richtig. `DevRankBridge` ist die Ausnahme und sollte schrumpfen, nicht das Vorbild werden.
- **Mining-Listener-Umzug.** Bewusst, und auf der Mining-Seite registriert.

---

## Teil 4 — Reihenfolge

Zuerst das, was Spieler als Verlust sehen. Dann die zwei Performance-Stellen, die man an einem Abend misst. Dann die Struktur, damit der nächste Sync nicht wieder still wird. Aufwand ist Umfang und Risiko, keine Kalenderzeit.

| # | Was | Warum zuerst | Aufwand |
|---|---|---|---|
| 1 | `ManagerSlotGuard` aus `8f314c1` nach `giveManager` | Slot 8 frisst beim Respawn und Weltwechsel das Item, das dort lag. Erklärt verschwundene Waffen besser als jeder Listener. Nur diese Klasse, nicht den 15-Commit-Stapel. | Klein. Eine Klasse, ein Aufruf. |
| 2 | Citrus / Monkey / Beta: Backend nachziehen oder Knöpfe aus | Knöpfe sind da, `RankBadgeService` kennt die Gruppen nicht, Reflection schluckt den Fehlschlag. Dye-Klassen von `cursor/playtest-polish-b848`. Nicht den Cosmetic-Stapel mergen, da liegt auch Katana- und Anvil-Arbeit. | Klein bis mittel. `RankBadgeService` plus die drei Dye-Klassen. |
| 3 | Early Game: `OpenRoads`, `GraduationStamp`, `ExploreChestProp` | Vier Klassen, der Rest des Hafens ist schon auf `main`. Die Midgame-Doku setzt Open Roads voraus. Gegen den aktuellen Hafen diffen, `javac.*.args` weglassen. | Mittel. Quests-Konflikt möglich, weil `HarbourArrival` auf `main` weitergezogen ist. |
| 4 | `TestPrototypeAbilities` nur einmal bauen | Doppelte Procs und zwei Cooldown-Maps. Eine Instanz aus `AetherionItems`, an Gravwell und Test-Gear durchreichen. Fähigkeiten nicht umschreiben. | Klein. |
| 5 | Foraging-Vorschau auf die Insel begrenzen, BFS cachen | Größter globaler Tick. Die Insel-Menge gibt es schon. | Klein bis mittel. Hohe Wirkung, wenig Fläche. |
| 6 | Mobzonen-Budget und `clearVexTaboo` auf einen Radius | 200–280 Hostiles plus Welt-Scan, solange jemand in den Borderlands ist. Zahlen senken erst nach einem Spark, der Scan kann sofort eng werden. | Mittel. Design-Zahl, live gegen TPS halten. |
| 7 | Dungeon-Raum-Index statt `getLivingEntities` | Trifft die Meldung „auch in Dungeons“. Der Todes-Hook räumt schon gezielt auf. | Mittel. |
| 8 | `HealthListener` nur bei Ausrüstungswechsel und alle paar Sekunden für Regen | Der 5-Tick-Lauf über alle Spieler ist reine Wiederholung von `refreshHealth`. | Mittel. |
| 9 | `npc-life-elevate` lesen und mergen, wenn die Blase unberührt bleibt | Ein Commit auf der aktuellen Spitze. Körper, nicht Dialog-UX. | Klein. |
| 10 | Tick-Register plus CI-Liste für Befehle und `registerEvents` | Verhindert den nächsten `26f625d` und den nächsten toten Reflection-Knopf. | Mittel, einmal. Danach ist jeder Sync rot oder grün. |
| 11 | Hollow-Sun-Elevation als eigener Pass | `HollowAccretion` / `HollowSky` / `HollowProps` fehlen. Der Director auf `main` ist ein anderer, großer Stand. Nicht in denselben Deploy wie 1–3 mischen. | Groß. Eigener Review, eigener Merge. |
| 12 | Transfer-Guards, Playtime, Display-Sweep, Floor-2/3-Dichte, Ashes-T3-Kisten | Echt, und sie erklären Gear-Verlust beim Serverwechsel und Display-Leichen. Sie erklären nicht die vier genannten Symptome so direkt wie 1–4. Jeder Branch einzeln, nie den 18-Commit-Stapel `cursor/defer-wildlife-label-remove-ba0f` am Stück. | Je klein bis mittel, nachdem 1–5 draußen sind. |

Nicht tun: Katana- oder Gravwell-Polish mergen. `claude/boss-hanging-saint` mergen. Den Early-Game-Branch per Merge-Commit auf `main` ziehen (er ist 50 Commits zurück und würde den Deploy zurückdrehen). WIP-Imports über die Spitze kopieren.

---

## Grenze dieser Prüfung

Geprüft ist der Git-Stand aller Branches am 2. Oktober 2026 gegen `1922f36`. Nicht geprüft ist das laufende JAR, der Spark des Live-Servers und ob zwischen dem 20. und 29.09. ein Worktree-JAR deployt wurde, das nie committet ist. `83389c1` und `5a79cc3` sind als „import WIP“ beschriftet. Ihr Diff gegen den jeweiligen Eltern ist klein und additiv. Ein WIP, der nie committet wurde und nur auf der Platte des Servers lag, ist von hier aus unsichtbar.

Die Menü-Prüfung vergleicht Aktions-Zeichenketten und Tab-Slots, nicht jeden Klick im Spiel. Die Performance-Reihung folgt aus dem Code (Periode, ob die Schleife alle Spieler oder alle Entities sieht, ob sie abgebrochen wird). Sie ist keine Spark-Messung. Punkte 1 und 2 würde ich als Erstes auf dem Live-Server messen. Wenn sie dort nicht oben stehen, ist die Rechnung falsch und der Spark gewinnt.
