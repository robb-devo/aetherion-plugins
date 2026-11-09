# Branch-Lücke gegen `main` @ `1922f36`

Stand 2. Oktober 2026. „Voraus“ = Commits, die nicht in `main` sind. Basis fast aller September-`cursor/*`-Branches ist `ddcdc7c` (20.09.). Sie sind damit auch ~50 Commits hinter `main`. Ein Merge dieser Branches auf `main` würde den Deploy zurückdrehen. Übernehmen heißt: einzelne Dateien lesen und auf den aktuellen Stand portieren.

`main` enthält bereits, trotz gleichnamigem Branch der nicht Vorfahr ist: Helios, World Eater, Seraphine (Blob gleich), Midgame-`/skills`, Farming- und Fishing-Eldervale, Terminus, Hollow-Sun-Director (5095 Zeilen, nicht die Elevation), Harbour-Ankunft, Starter-Kit, Explore-Chest-Service, `VeinsDigZones`, Ashen-Katana-Listener (576 Zeilen, Deploy-Stand).

## Gameplay, das auf `main` fehlt

| Branch | Voraus | Fehlt auf `main` | Anmerkung |
|---|---|---|---|
| `claude/early-game-harbour` | 5 | `OpenRoads`, `OpenRoadsGUI`, `GraduationStamp`, `ExploreChestProp` | Hafen-Teile sind seit `9ae7fd1` schon auf `main`. `javac.*.args` nicht übernehmen. |
| `npc-life-elevate` | 1 | Körper-Pass in `LivingNpcLife` (+1141/−329) | Sitzt auf der aktuellen Spitze. Sprechblase nicht anfassen. |
| `cursor/hollow-sun-elevation` | 1 | `HollowAccretion`, `HollowProps`, `HollowSky`, längerer Director (5464 Zeilen) | Eigener Boss-Pass, nicht mit dem Hafen mischen. |

## Fixes, die zu den bekannten Symptomen passen

| Branch (repräsentativ) | Was | Auf `main` |
|---|---|---|
| `cursor/stability-recipe-audit-6e79` (`8f314c1`) | `ManagerSlotGuard`: Item in Hotbar-Slot 8 umparken, bevor der Stern es überschreibt | Klasse fehlt. `giveManager` schreibt Slot 8 direkt (`AetherionManagerListener` 208). |
| `cursor/playtest-polish-b848` und Cosmetic-Stapel | `CitrusDye`, `CelestialDye`, `RainbowDye` | Knöpfe im Dev-Menü, Backend fehlt. `DevRankBridge` ruft die Methoden per Reflection. |
| `cursor/forage-hologram-leak-470a`, `cursor/forage-displayentities-4851` | `DisplayEntities`, `ForageDisplayGuard` | Insel-Hologramme in `ForageCast` sind pro Rolle begrenzt. Weltweiter Sweep fehlt. |
| `cursor/display-entity-leak-edae` | Ein Altar-Display wiederverwenden, Wildlife-HP-Labels deckeln | Nicht gemergt. `BorderlandsRiteService` tickt den Umriss alle 5 Ticks. |
| `cursor/hub-inventory-isolation-b414`, `cursor/transfer-guards-display-leaks-ba0f` | `InventoryTransfer`, `TransferProgressGuard`, `StorageFileMerge`, Tests | Dungeons hat nur das alte `TransferSnapshotStore` aus dem Initial-Commit. |
| `cursor/wipe-safe-playtime-99f7` | `PlaytimeService`, `/playtime` | Fehlt. |
| `cursor/entity-lifecycle-ore-gen-d605` | `MarkerLifecycle`, `NaturalVeins`, `WorldgenOnce` | Fehlt. |
| `cursor/f2-f3-mob-density-e44b`, `cursor/f3-t3-chests-9aad` | Floor-2/3-Dichte, `AshesChestIds`, `InventorySnapshotPolicy` | Fehlt. |
| `cursor/amethyst-dig-zones-9a6f` | Schematic beim Graben nicht überschreiben, Hub-Spalten auslassen | `VeinsDigZones` ist auf `main`, diese Commits nicht. Diff lesen. |

Dieselben Commits liegen auf mehreren Branches übereinander (`defer-wildlife-label-remove-ba0f` hat 18, `transfer-guards-display-leaks-ba0f` 17, `dungeon-sync-boosters-6447` 13). Es sind Stapel, keine 18 unabhängigen Features. Einen Stapel nicht am Stück mergen: ab `4e20798` / `43d5a6f` hängen Katana- und Amboss-Änderungen mit drin.

## Schon auf `main`, Branch nicht noch einmal mergen

| Branch | Warum der Merge schadet |
|---|---|
| `claude/helios-polish`, `claude/confident-bell-u0mcjv` | 0 voraus, vollständig enthalten. |
| `feature/midgame-skills-gather`, `feature/world-eater-boss` | 0 voraus. |
| `claude/fishing-eldervale-expansion` | 0 voraus, ein Commit hinter `main` (nur die Handoff-Doku). |
| `claude/boss-hanging-saint` | `HangingSaintDirector` ist byte-gleich (`788e4997`). Branch ist 50 Commits zurück. |
| `cursor/terminus-absolute-item` | Terminus ist seit `26f625d` auf `main`. |
| `cursor/dialog-de-overlay-cloud` | Ist `main`. |

## Nicht anfassen

| Branch | Grund |
|---|---|
| `cursor/katana-smooth-dash-07b5`, `katana-dash-hang-ce3e`, `katana-rise-jump-1afb`, `katana-dash-smooth-4a3c`, `ashen-katana-polish-011a`, `ashen-petal-line-5758`, `ashen-sheath-leaf-read-a248`, `samurai-fx-polish-af37`, `ashen-sheath-polish-8643` | Blossom Blade / Ashen Sheath. Gesperrt. `main` hat den längeren Listener (576 gegen 385 auf dem Dash-Branch). |
| `cursor/katana-anvil-polish-6785`, `cursor/altar-outline-leak-417b` | Dieselbe Kette, plus Amboss. Der Altar-Leak-Teil darf einzeln gelesen werden, der Waffen-Teil nicht. |
| `cursor/npc-editor-studio-wip` | Kompiliert nicht. |

## Nicht Gameplay

| Branch | Was |
|---|---|
| `cursor/website-platform`, `cursor/website-copy-polish-c629`, `cursor/aetherion-website-5c20` | Statische Seite, nie im Plugin-JAR. |
| `cursor/stress-bots-realism-cba5`, `cursor/stressbots-overnight-polish-3ec0` | QA-Bots nach dem Stand, der am 19.09. schon auf `main` ist. |
| `cursor/setup-dev-environment-f204` | Lokaler Test-Server. |
| `cursor/aenet-restart-countdown-398b`, `cursor/npc-quest-editor-polish-aca7` | Restart-Countdown, Ops. |

## Deploy-Merges, zur Kontrolle

Keine gelöschten Dateien gegen entweder Eltern:

- `f7ec15c` World-Eater-Ending → Midgame
- `571c919` World Eater → Helios
- `3949b28` Midgame → Farming
- `9f59031` Helios-Polish → Farming

Der einzige breite Überschrieb danach ist `26f625d` (306 Dateien, −1782 Zeilen). Dev-Menü-Aktionen und die 19 Manager-Tabs haben ihn überlebt. Siehe `DEEP_SCAN_2026-10.md` Abschnitt 1.3.
