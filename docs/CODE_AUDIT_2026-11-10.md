# Aetherion Code-Audit 2026-11-10: aufräumen, stabilisieren, beschleunigen

Audit-Grundlage: `main` = `df5504e` (PR #144), unverändert seit 02.11. Das ganze Repo (alle 17 Plugins plus
Proxy und Helm) wurde mit Suchmustern durchkämmt; Zeile für Zeile gelesen habe ich die Stellen, die dabei
auffielen, nicht jede der 1.600 Dateien.

## Übergabe (Stand 10.11.2026 abends): hier weitermachen

**Wo der Code liegt.** Alle Fixes stehen im Branch `claude/project-thread-knvhf4`, PR #146, acht Commits mit je
einem Thema, CI grün (compile, compile-samurai, test, compile-proxy). Nicht gemergt: `main` ist live, und
deployt werden kann erst vom neuen PC aus (SSH-Schlüssel lag auf dem alten). Wer weitermacht, arbeitet auf
diesem Branch oder auf einem neuen Branch von dessen Spitze, nicht auf `main`. Was jeder Punkt technisch
ändert, steht in `docs/PROJECT_STATE.md` unter "Cleanup round 1" und "Cleanup round 2".

**Erledigt (im PR):** A1 gitattributes, A2 Void-Trim raus, A3 63 Speicherstellen über `AtomicYaml`, A4 CI
(SamuraiBoss, Tests), A5 Doku, A7 Arena-Befehle nur für Staff, B1 Start-Schutz für die Nebenfunktionen in
Items, C1 Ausrüstungswerte über `EquippedGear` (nur neu gelesen, wenn sich ein Item ändert), B2 Schritt 1
(Dungeons-Kopie von `NetworkPlayerDataSync` gelöscht), B3 erster Teil (Speicher-Tasks zeitlich versetzt,
`SaveSlot`). Die "Stand"-Notizen unter den Punkten unten sagen jeweils, was davon genau umgesetzt ist.

**Nächste Schritte, in dieser Reihenfolge:**
1. Neuer PC eingerichtet: PR #146 mergen, dann aus dem sauberen Klon von `main` bauen und auf allen Backends
   deployen (fast jedes Plugin ändert sich), jeder Server einmal neu starten. Klicktest und Prüfung vorher
   (Symlinks bei den Spielstand-Dateien) stehen in der PR-Beschreibung. Danach Spark-Profil auf mmo-r (F1).
2. B2 Schritt 2 ist im Code erledigt (14.11., siehe "Stand" unter B2): mit Runde 1 und 2 deployen, dann
   Test-Sprünge über alle Serverpaare.
3. B2 Schritt 3: Cores v5-`TransferSnapshotStore` abschalten, nachdem live geprüft ist, dass keine alten
   Snapshot-Dateien mehr liegen (F3).
4. B3 Rest: gemeinsame Dateien im Hintergrund schreiben, beim Logout nur den gehenden Spieler sichern.
5. C3 `teleportAsync` auf den Reisewegen, C4 Wächter-Tasks nur dort, wo es den Inhalt gibt.
6. B4 Exile-Einmalwerkzeuge raus (Robb sagt, welche bleiben), danach D und E nach Absprache.

**Offen bei Robb:** Ist PR #145 (Exile, 02.11.) live, mergen oder schließen? Wurden SSH-Schlüssel und
`aetherion-ops` vom alten PC gesichert? Go für das Aufräumen der alten Branches und PRs (A6)? Welche Befehle
meint er mit "serverübergreifend"? Heute gehen Reisebefehle und der Markt von jedem Backend zur Hauptwelt
und werden dort ausgeführt, `/island`, `/guild`, `/friend` werden vom Hub und von mmo-r weitergereicht
(Core `MainCommandRouter` und `RouteBook`).

**Wie arbeiten.** Neue Code-Threads auf der Cloud-Umgebung "aetherion-plugins" starten: nur dort erreicht
Maven das Paper-Repo, die Standard-Umgebung prüft nur über die GitHub-CI. Die Tests von AetherionItems
kompilieren nicht, Items hat deshalb keine Unit-Tests in der CI. Gesperrte Systeme (TalkUx-Shell, Blossom
Blade, Gravwell Cleaver, Boosters, Anvil/BoosterSockets, Ränge, ShutdownCountdown, Borderlands) nicht
anfassen. Commits als `Robbi <robb@aetherion.local>` über Umgebungsvariablen, vor jedem Push nach
Zugangsdaten suchen, nichts aus `aetherion-ops` ins Repo.

## Entscheidungen von Robb (2026-11-10)

- Go für: Void-Trim entfernen (A2), absturzsicheres Speichern (A3), Stats nur beim Ausrüstungswechsel neu
  berechnen und die Dauerlast abbauen (C1), Transfer sauber ziehen (B2, Ziel: alle Server derselbe Stand,
  sauber angebunden, Befehle serverübergreifend), Speichern entlasten (B3).
- **Gnomon ist ein Testboss** (nur ein Konzept-Check), gehört nicht zur Story oder zum Spiel und bekommt
  keine Arbeit. A1 bleibt trotzdem drin, weil es nichts kostet.
- Ziel insgesamt: ein so sauberes und effizientes Netzwerk wie möglich.
- Umgesetzt ist, was in der Übergabe oben unter "Erledigt" steht; der Rest wartet auf Build und Testserver.

## Kurzfassung

Der Code ist im Kleinen sauberer als erwartet: Dateien werden meist absturzsicher geschrieben, die
Tick-Schleifen steigen früh aus, es gibt kaum toten Code und keine Zugangsdaten im Repo. Das Problem ist die
**Breite**: 506.000 Zeilen, 1.063 Event-Handler, 324 Stellen mit wiederkehrenden Tasks (111 davon im
Tick-Takt, die meisten sind kurzlebige Effekte), und mehrere Systeme gibt es
doppelt bis fünffach. Dazu kommen ein paar wenige echte Risiken für Spielstände und eine Stelle, die pro
Spieler dauerhaft Rechenzeit kostet.

Die fünf wichtigsten Punkte:

1. **Geladene Waffe im Core:** der Exile-Void-Trim, der am 23.10. die Karte zerschnitten hat, ist noch im Code
   und startet, sobald eine Marker-Datei existiert (A2).
2. **Spielstände können bei einem Absturz kaputtgehen:** rund 70 Speicherstellen schreiben nicht
   absturzsicher, darunter Codex, Rezepte, Blueprints und Pets (A3).
3. **Ausrüstungswerte werden zehnmal pro Sekunde pro Spieler komplett neu aus den Items gelesen** (C1). Das
   ist die größte Dauerlast, die ich im Code gefunden habe.
4. **Der Charakter-Transfer zwischen den Servern existiert dreimal** und die Kopien laufen schon
   auseinander (B2).
5. **Alle Spieler stehen in gemeinsamen Dateien, die jede Minute und bei jedem Logout komplett neu
   geschrieben werden** (B3). Heute unsichtbar, mit hunderten Accounts ein Lag-Spike pro Minute.

## Grenzen dieses Audits

- Kein Zugriff auf die Live-Server (der SSH-Schlüssel lag auf dem verkauften PC). Alles, was Live-Configs,
  Logs oder Messwerte braucht, steht gesammelt in Abschnitt F.
- Performance-Aussagen stammen aus dem Lesen des Codes, nicht aus einer Messung. Die Reihenfolge in C sollte
  ein Spark-Profil vom Live-Server bestätigen, bevor größere Umbauten starten.
- In der Cloud lässt sich nicht bauen: das Paper-Maven-Repo (`repo.papermc.io`) ist im Netzwerk der
  Umgebung gesperrt. Jeder PR wird aber von der GitHub-CI kompiliert, das reicht für die Fixes.

## Zahlen zur Lage

| Plugin | Zeilen | Event-Handler | wiederkehrende Tasks (Stellen im Code) |
|---|---|---|---|
| AetherionItems | 145.800 | 379 | 111 |
| BossEngine | 86.400 | 84 | 13 |
| AetherionQuests | 54.900 | 107 | 33 |
| AetherMobs | 36.600 | 36 | 21 |
| AetherionGuilds | 28.500 | 41 | 16 |
| AetherionCore | 21.500 | 66 | 22 |
| Mining / Hub / Dungeons / Foraging / Samurai | 20.400 / 17.900 / 17.400 / 15.800 / 16.100 | 70 / 51 / 46 / 36 / 21 | 21 / 18 / 16 / 10 / 1 |
| Farming / Gnomon / Fishing / übrige | 11.300 / 9.200 / 9.000 / 9.200 | 47 / 9 / 23 / 39 | 20 / 0 / 11 / 8 |

Größte Einzeldateien: `CustomItem.java` 8.486 Zeilen, `PetEntity.java` 6.214, `HollowSunDirector.java` 5.297,
`AshenSovereignDirector.java` 5.150, `DevMenu.java` 4.275.

---

## A. Sofort, klein, praktisch ohne Risiko

**A1. `.gitattributes`: Binärdateien als binär markieren.**
Die drei Gnomon-Melodien (`*.nbm`) erkennt Git als Text. Ein frisch installiertes Git auf dem neuen PC
wandelt sie beim Auschecken um, und der nächste Gnomon-Build liefert kaputte Melodien aus (stand schon im
Status vom 02.11., ist noch offen). Fix: `*.nbm`, `*.schem`, `*.nbt`, `*.nbs`, `*.ogg`, `*.png` als `binary`.
Kein Deploy nötig. **Vor dem ersten Build auf dem neuen PC erledigen.**

**A2. Exile-Void-Trim entfernen.**
`AetherionCore.onLoad` ruft `ExileVoidTrim` auf, sobald `plugins/AetherionCore/exile-void-trim.armed`
existiert. PROJECT_STATE sagt selbst: die Annahme war falsch, er hat den Kern der Karte herausgeschnitten,
"nie wieder scharf schalten". Solange der Code da ist, reicht eine Datei, um die Exile-Welt zu zerstören.
Fix: Klasse, Aufruf und die drei Skripte unter `tools/deploy/` löschen. Geht mit dem nächsten Core-Deploy mit.

**A3. Alle Spielstand-Dateien absturzsicher schreiben.**
Es gibt den sicheren Weg schon (`AtomicYaml`: erst Temp-Datei, dann umbenennen). Rund 70 Stellen nutzen ihn
nicht und schreiben direkt (nicht alle davon sind Spielstände, manche sind Welt- oder Dev-Dateien): Items 27 (u. a. `codex.yml`, `recipe_unlocks.yml`, `unlocked_blueprints.yml`,
`xp-boosts.yml`, Blueprint-Forge), Quests 10, Hub 7 (`PlayerHubStorage`), Mining 5, AetherMobs
(`PetDataManager`, die Pets jedes Spielers), Guilds (`FriendService`), Pit, Beta. Stirbt der Prozess mitten
im Schreiben (wie beim Hänger am 26.10.), ist die Datei abgeschnitten und der Fortschritt aller Spieler in
dieser Datei weg. Fix: je Stelle eine Zeile. Risiko sehr gering. Betrifft fast jedes Plugin, also ein
Deploy auf allen Servern.

**A4. CI ehrlich machen.**
Die CI kompiliert nur und überspringt Tests. Die Tests von Items kompilieren nicht (bekannt), SamuraiBoss
wird von der CI gar nicht gebaut (eigenes `pom.xml`, nicht im Haupt-Build), und `helios-jars.yml` hängt an
einem alten Branch. Fix: SamuraiBoss in die CI, Tests reparieren und laufen lassen, alten Workflow
entfernen. Für `CelestialDyeTest` braucht es deine schon dokumentierte Entscheidung zu den Rängen, sonst
wird er gelöscht. Kein Deploy.

**A5. Doku auf Stand bringen.**
`docs/PROJECT_STATE.md` steht auf 25.10. (mmo-m fehlt, Build-Tabelle alt), `CLAUDE.md` zeigt für offene
Arbeit noch auf das Audit vom 19.10. Ein kleiner Doku-PR, kein Deploy.

**A6. GitHub aufräumen (braucht dein Go).**
142 Branches (63 `cursor/*`, 48 `claude/*`, 24 `backup/2026-10-02/*`) und 52 offene PRs. #4 bis #67 stammen
aus September und Anfang Oktober, fast alles Entwürfe, durch spätere Arbeit überholt. Offen und neu ist
**#145** (02.11., Exile: Seraphine im Little Theatre, Aetherion auf dem Star Court, Harbour-Rand; 2 Commits,
mergebar). Hier brauche ich deine Ansage: mergen und deployen oder schließen. Falls #145 schon vom Branch
aus deployt wurde, ist `main` gerade nicht gleich live.

**A7. Zwei Arena-Befehle ohne Rechteprüfung.**
`/bloodstonearena` (auch `/bloodstone`, `/hsarena`) und `/ashenvoid` teleportieren jeden Spieler ohne
Rechteprüfung in die alten Arena-Welten. Wenn es diese Welten auf mmo-r noch gibt, ist das ein Weg am
Vial-Altar vorbei. Fix: Recht `aetherion.dev` verlangen. Zwei Zeilen in Items.

---

## B. Stabilität

**B1. Ein Fehler beim Start darf nicht das ganze Plugin mitnehmen.**
`AetherionItems.onEnable` startet rund 100 Dienste und Listener hintereinander, fast ohne Absicherung (7
`try` in der ganzen Hauptklasse). AetherMobs: 770 Zeilen Start, Quests 412, Guilds 253. Wirft ein
Nebendienst (Casino, Mühle, Colosseum) beim Start einen Fehler, schaltet Paper das komplette Plugin ab:
keine Items, kein Kampf, keine Wirtschaft auf diesem Server. Fix: Neben-Inhalte über einen kleinen
Schutz-Wrapper starten, der den Fehler laut loggt und weitermacht; Kernsysteme dürfen weiter hart
scheitern. Risiko gering, Nutzen hoch.

**B2. Charakter-Transfer gibt es dreimal.**
Der Code, der Inventar und Fortschritt beim Serverwechsel mitnimmt:
- `AetherionDungeons/bridge/TransferSnapshotStore` (1.209 Zeilen, v6, mmo-r und mmo-d)
- `AetherionCore/network/CharacterStore` (975 Zeilen, laut Kommentar ein "getreuer Nachbau" für mmo-c und mmo-i)
- `AetherionCore/network/TransferSnapshotStore` (790 Zeilen, altes v5, wird im Core noch gestartet)
- dazu `NetworkPlayerDataSync` zweimal (Core 621, Dungeons 670) mit von Hand abgeglichener Dateiliste.

Die Kopien sind schon auseinandergelaufen: die Regel, wann der Lagerbestand beim Transfer zusammengeführt
wird, ist in beiden `NetworkPlayerDataSync` verschieden. Jeder Fix muss an zwei bis drei Stellen passieren,
und genau dieser Code entscheidet über Item-Verlust und Dupes.
Schritte: (1) jetzt, risikoarm: die Dateiliste nur noch einmal im Core definieren, Dungeons liest sie von
dort, plus ein Test. (2) später, eigener PR mit Test-Sprüngen über alle Serverpaare: Dungeons benutzt den
`CharacterStore` aus dem Core. (3) v5 abschalten, nachdem live geprüft ist, dass keine alten
Snapshot-Dateien mehr unterwegs sind. Schritt 2 und 3 haben mittleres bis hohes Risiko, deshalb nicht
zwischen Tür und Angel.

*Stand 10.11., Schritt 1 erledigt, und eine Korrektur:* beim genauen Vergleich der beiden
`NetworkPlayerDataSync` war die Zusammenführung des Lagers in beiden Kopien dieselbe; verschieden war nur,
wann die Warnung ins Log geschrieben wird. Auseinandergelaufen war also die Meldung, nicht der Spielstand.
Die Dungeons-Kopie ist gelöscht (670 Zeilen). Der Dungeons-Store benutzt jetzt die Klasse aus dem Core, und
zwar nur mit Methoden, die der Core auf den Servern schon hat. Damit gibt es die Dateiliste und die
Zusammenführung genau einmal. Die zwei Schritte, die nur Dungeons braucht (Dateien vor dem Login
bereitlegen, alte Schlüssel reparieren), liegen in `PendingSnapshotFiles`.

*Stand 14.11., Schritt 2 erledigt (eigener Branch auf dem PR von Runde 1 und 2, nicht deployt):* den
v6-Charakter gibt es nur noch einmal, im Core (`CharacterStore` und `CharacterHost`). Die gewollten
Unterschiede zwischen mmo-r/mmo-d und den übrigen Backends stehen in `CharacterStore.Policy` (`gate` und
`plain`) und verhalten sich wie vorher. Dungeons baut den Host aus seiner bisherigen Config und behält nur das,
was zum Tor gehört (Portale, Ankunfts-Teleports, Floor-Start). Die sechs Dungeons-Klassen des Transfers sind
gelöscht (rund 2.000 Zeilen). Core und Dungeons müssen zusammen deployt werden. Vor dem Abhaken: Test-Sprünge
über alle Serverpaare (Inventar, Enderkiste, Level, Quit und Rejoin, Absturz in einem Dungeon).

**B3. Gemeinsame Spielstand-Dateien skalieren nicht.**
`skills.yml`, `progress.yml`, `coins.yml`, `codex.yml`, `shards.yml`, `recipe_unlocks.yml` und weitere
enthalten jeweils alle Spieler. Beispiel `SkillService.save()`: liest die ganze Datei von der Platte, trägt
jeden jemals gesehenen Spieler neu ein (Spieler werden beim Logout nicht aus dem Speicher entfernt) und
schreibt alles zurück, auf dem Haupt-Thread. Das passiert jede Minute (acht solche Speicher-Tasks allein in
Items, alle mit demselben Takt, also im selben Tick) und zusätzlich bei jedem Logout und Kick (`PersistenceFlushListener` stößt sieben davon an).
Mit vier Testern merkt man nichts. Mit ein paar hundert registrierten Accounts wird daraus ein Ruckler pro
Minute und pro Logout, der mit jedem neuen Account wächst.
Fix klein: Speicher-Tasks zeitlich versetzen, das eigentliche Schreiben in einen Hintergrund-Thread, beim
Logout nur die Daten dieses Spielers sichern. Fix richtig: eine Datei pro Spieler (so machen es Quests und
Pets schon) oder eine Datenbank, siehe E1. **Vor einer öffentlichen Open Beta nötig, nicht vorher.**

*Stand 10.11.:* der erste Teil ist drin. Die acht Speicher-Tasks in Items laufen weiter jede Minute, starten
aber versetzt (`core/SaveSlot`), sodass nie zwei im selben Tick schreiben. Offen bleiben das Schreiben im
Hintergrund und das Sichern nur des einen Spielers beim Logout; beides fasst jeden Spielstand an und gehört
in eine Runde mit Build und Testserver.

**B4. Einmal-Werkzeuge für die Exile-Welt liegen im Core.**
`ExileQuayTurn`, `ExileCauseway`, `ExileMapRestore`, `ExileQuayPaste`, `ExileGateCut`, `ExileMapPaste`,
`ExileFaweUndo`, `ExileQuayRepair`, `ExileSaintClear`, `ExileTidy`: zusammen rund 2.450 Zeilen Befehle, die
die Welt umbauen und ihren Job erledigt haben. Jedes davon ist ein Befehl, mit dem man die Karte
versehentlich beschädigen kann, und es läuft auf allen fünf Servern mit. Fix: entfernen, was erledigt ist.
Welche du noch brauchst (`/exile tidy`, `/exile vial` bleiben sicher), entscheidest du.

**B5. Fehler werden verschluckt.**
Rund 400 Stellen fangen Fehler ab und ignorieren sie, und rund 550 Stellen rufen andere Plugins über
Klassennamen als Text auf (47 eigene Klassen, z. B. `de.aetherion.items.AetherionItems` neunmal). Wird eine
Klasse umbenannt, fällt das Feature still aus, ohne Zeile im Log. Den sauberen Weg gibt es schon
(`AetherServices` im Core, in 123 Dateien benutzt). Fix: kein Großumbau, sondern jedes Mal, wenn ein Plugin
ohnehin angefasst wird, dessen Text-Aufrufe auf `AetherServices` umstellen und mindestens einmal loggen,
wenn eine Brücke nicht greift.

---

## C. Performance

**C1. Ausrüstungswerte nicht mehr bei jeder Abfrage aus den Items lesen.** (größter Fund)
Jede Abfrage wie "wie viel Leben gibt die Ausrüstung" geht über `ActiveEquipmentStats.getStat`: sechs
Ausrüstungsplätze, pro Item drei bis vier Aufrufe von `getItemMeta()`. Dieser Aufruf kopiert jedes Mal
das komplette Item (Name, Lore, alle Werte). Es gibt keinen Zwischenspeicher. `HealthListener` macht das
alle 2 Ticks für jeden Spieler: einmal für Leben, dann zeichnet das HUD und fragt Verteidigung und das
gehaltene Item ab. Das sind grob 40 bis 50 Item-Kopien pro Spieler, zehnmal pro Sekunde, auch wenn er nur
herumsteht. Im Kampf fragt `DamageListener` pro Treffer achtmal ab.
Fix: pro Spieler einen Werte-Schnappschuss halten, der nur neu berechnet wird, wenn sich die Ausrüstung
ändert (Rüstungswechsel, Slot-Wechsel, Inventar-Klick), höchstens aber einmal pro Sekunde zur Sicherheit.
Zusätzlich die Item-ID ohne Kopie lesen, wenn die Paper-Version das hergibt (beim Bauen prüfen).
Die Werte bleiben identisch, es ändert sich keine Balance, Blossom Blade und Gravwell Cleaver werden nicht
angefasst. Risiko mittel (ein vergessener Auslöser hieße kurz veraltete Werte, deshalb die
Sekunden-Sicherung). Nutzen wächst mit der Spielerzahl.

*Stand 10.11.: umgesetzt, anders als oben skizziert und ohne das Risiko.* `EquippedGear` merkt sich pro
Spieler und Ausrüstungsplatz, was aus dem Item gelesen wurde (ID, Werte, Dungeon-Stufe), und wirft es weg,
sobald das Item im Platz nicht mehr dem gelesenen gleicht. Das ist ein Vergleich statt einer Kopie, braucht
keinen Auslöser und keine Sekunden-Sicherung: ein Wechsel, ein Upgrade oder ein neuer Booster gilt beim
nächsten Zugriff, also sofort. Beim Nachzählen kam mehr heraus als geschätzt: mit den Set-Boni
(`DungeonGearListener` prüft vier Sets mit je vier Teilen bei jeder Abfrage) waren es rund 80 Item-Kopien
pro Spieler in voller Ausrüstung alle 2 Ticks, dazu zwei Set-Schleifen im 2-Tick-Takt (Aetherion-Set, Hollow Sun). Alle diese
Wege lesen jetzt aus dem Schnappschuss. Boni, die vom Ort oder von der Zeit abhängen (Skills, Pets, Inseln,
Events), werden weiter live gerechnet. Gemessen ist das noch nicht: das Spark-Profil steht aus (F).

**C2. 50 Rechtsklick-Handler in Items.**
Jeder Klick und jeder Schlag läuft durch 50 `PlayerInteractEvent`-Handler in Items (101 im ganzen Netz), ein
Treffer durch knapp 100 Schadens-Handler. Die meisten Waffen-Listener fragen als Erstes "ist das mein
Item?", also wieder eine Item-Kopie. Fix: ein Verteiler, der die ID des gehaltenen Items einmal liest und
nur die passende Waffe aufruft. Risiko mittel (Reihenfolge der Handler). Erst nach C1 und nur, wenn das
Profil zeigt, dass es noch nötig ist. C1 nimmt hier schon den Großteil der Kosten.

**C3. Teleports laden Chunks auf dem Haupt-Thread.**
Rund 360 `teleport`-Aufrufe, kein einziger `teleportAsync`. Ein Teleport in nicht geladene Chunks hält den
ganzen Server kurz an. Fix: die zentralen Reisewege (Hub-Spawns und Warps, Sprung-Pads, Arena-Eintritte) auf
die asynchrone Variante umstellen, also ein Dutzend Stellen, nicht alle 360. Risiko gering bis mittel.

**C4. Wächter-Tasks laufen auch dort, wo es den Inhalt nicht gibt.**
Rund zehn Dienste in Items suchen alle 10 Sekunden ihre Anzeigen (Krypta-Hologramme, Banner, Gebiets-Marker,
Weltkarte, Tierzonen, Pet-Habitate; Casino und Mühle durchsuchen dabei alle Display-Entities aller Welten).
Das läuft auf jedem Server mit Items, auch auf mmo-d, mmo-c, mmo-i und mmo-m, wo es weder Casino noch
Mühle gibt. Einzeln klein, zusammen unnötig. Fix: nur auf Servern mit dem Inhalt starten. Die Tasks von
Borderlands bleiben unberührt (gesperrtes System).

**C5. Endless-Testboden baut synchron.**
`EndlessSchemBuilder` fügt die Schematic auf dem Haupt-Thread ein, laut Doku "bis mmo-d FAWE hat". FAWE ist
seit 28.10. da. Nur Dev-Boden, niedrige Priorität.

---

## D. Doppelte Systeme und Aufräumen

**D1. Schematic-Einfügen: mindestens sieben eigene Umsetzungen.**
`ForageIslePaste` (497 Zeilen), `FaweIslandPaste` im Hub (434), `EndlessSchemBuilder` (204),
`HubSchemPaster` in Pit (203), `AmethystPaste` (190), `EldervalePaste` (143), dazu fünf im Core.
`FaweQueue.java` liegt identisch in Mining und Foraging. Fix: ein gemeinsamer Helfer in der Core-API.

**D2. Das Insel-Gerüst gibt es viermal.**
Farming, Fishing, Mining und Foraging (plus die Origin-Insel im Hub als Variante) haben jeweils eigene
`...Cast`, `...World`, `...Events`. `TalkDesk.java` ist viermal dieselbe Datei (249 Zeilen), `IsleCast`,
`LakeCast` und `MineCast` sind zu 69 bis 76 % gleich. Jeder Fix an den Insel-NPCs muss drei- bis fünfmal
gemacht werden. Fix: gemeinsame Basis, sobald die nächste Insel ohnehin angefasst wird. `TalkDesk` spricht
über die Core-API mit TalkUx; die TalkUx-Shell selbst bleibt unverändert.

**D3. Gut 16.000 Zeilen Dev- und Testwerkzeug in den Live-Jars.**
DevMenu mit Unterseiten (rund 8.000), Test-Waffen und Test-Arena (2.300), Dev-Props (2.600), StressBots
(3.600, seit 25.10. aus). Alles hinter `aetherion.dev`, also nicht gefährlich, aber es macht Items
unübersichtlich und die Test-Waffen-Listener laufen immer mit. Mittelfristig in ein eigenes Dev-Plugin, das
nur dort liegt, wo du es brauchst.

**D4. ActionBar-Wildwuchs.**
342 direkte ActionBar-Nachrichten in rund 130 Dateien; den gemeinsamen Kanal (`ActionBarLane`) nutzen 11.
Nachrichten überschreiben sich gegenseitig. Seit das HUD auf der Bossbar sitzt, ist das nur noch Optik.
Gehört in die spätere UI-Runde.

**D5. Repo-Hygiene.**
Ein fremdes Plugin-Jar im Repo (`AetherionQuests/libs/FancyNpcs-2.9.2.jar`, 6,6 MB), eine 7-MB-WAV, zwei
Texturepack-Zips und 60 alte Agenten-Prompts unter `docs/prompts`. Wichtiger: die Pack-Quelle in Items ist
laut eigener Doku veraltet, das echte Live-Pack existiert nur als Zip auf dem Server und in den Backups. Es
lässt sich also nicht aus Git wiederherstellen. Fix: das Live-Zip versioniert sichern (Release-Anhang oder
eigenes privates Repo).

**D6. Kleinkram.**
Wirklich toter Code ist wenig: rund 570 Zeilen in neun Klassen (`OpenRoadsGUI`, `TabScoreboardRebind`,
`CraftCurve`, `QuestNPCManager`, `MiningQuestListener`, `QuestConfig`, `PetEquipmentChangeEvent`,
`BossEngineConstants`, `SpawnTriggerFactory`). `/aetherion`, `/hub` und `/spawn` werden von zwei Plugins
gleichzeitig angemeldet (Items oder Hub und Pit); auf dem Hub-Server gewinnt, wer zuerst lädt.

---

## E. Größere Umbauten, die eine bewusste Entscheidung brauchen

**E1. Ein Spielstand für alle Server.** Heute hat jeder der fünf Server seine eigene Kopie der Spielstände,
und bei jedem Wechsel wird der Teil des Spielers hin- und herkopiert. Daraus kommt die ganze Geschichte
der Transfer-Bugs (v4, v5, v6). Eine gemeinsame Datenbank auf dem Host würde diese Fehlerklasse entfernen und
B2 und B3 gleich miterledigen. Großes Projekt, lohnt sich vor einer wirklich öffentlichen Beta.

**E2. AetherionItems entflechten.** 146.000 Zeilen, 430 Klassen. Welt-Inhalte (Casino, Mühle, Colosseum,
Krypta, Arenen) und das Dev-Werkzeug gehören laut eigener Architektur-Doku nicht hinein. Heute startet ein
Casino-Fix vier bis fünf Server neu.

**E3. Core verschlanken.** Die Regel sagt: Core hält nur gemeinsame Schlüssel, Flags und APIs. Tatsächlich
liegen dort knapp 9.000 Zeilen Exile-Inhalt (Quay, Türen, Fähre, Lift, Schwert-Prop). Jede Exile-Änderung
ist damit ein Core-Deploy und ein Neustart aller Server. Beim nächsten Exile-Thema in ein eigenes Plugin.

**Für den neuen Boss:** nach dem Gnomon-Muster bauen (eigenes Paket mit getrenntem Rig, Effekten und
Angriffen), nicht als Einzeldatei wie `HollowSunDirector` (5.297 Zeilen) oder `AshenSovereignDirector`
(5.150). Die sind kaum noch wartbar.

---

## F. Braucht einen Blick auf die Live-Server

1. Spark-Profil unter Last auf mmo-r, um die Reihenfolge in C zu bestätigen.
2. Größe von `skills.yml`, `progress.yml`, `codex.yml`, und ob Datenordner zwischen Servern geteilt sind (B3).
3. Liegen noch alte v5-Snapshot-Dateien im Transfer-Ordner (B2 Schritt 3)?
4. Existieren die Welten `bloodstone` und Ashen Void noch auf mmo-r (A7)?
5. Ist PR #145 live oder nicht (A6)?
6. Wer gewinnt `/hub` und `/spawn` auf dem Hub-Server (D6)?

Nicht Code, aber wichtig: Laut Status vom 02.11. lagen der SSH-Schlüssel, die Ops-Doku (`aetherion-ops`:
Bible, LIVE_STATE, SERVERS) und das lokale Backup mit Zugangsdaten nur auf dem alten PC. Falls davon nichts
gesichert wurde, braucht der neue PC einen neuen Schlüssel über die Hetzner-Konsole, und die Ops-Doku muss
aus den Skripten auf dem Server neu aufgebaut werden.

---

## Vorgeschlagene Reihenfolge

| Paket | Inhalt | Risiko | Braucht Deploy |
|---|---|---|---|
| 1 | A1 gitattributes, A4 CI, A5 Doku | keins | nein, sofort mergebar |
| 2 | A2 Void-Trim raus, A3 sicheres Speichern, A7 Arena-Befehle, B1 Start-Schutz | gering | ja, alle Server |
| 3 | C1 Werte-Schnappschuss, danach Spark-Messung | mittel | ja, Items |
| 4 | B2 Schritt 1 (eine Dateiliste, Test), C3 Teleports, C4 Wächter | gering bis mittel | ja |
| 5 | B3 Speichern entlasten, B4 Exile-Werkzeuge raus | mittel | ja |
| später | B2 Schritt 2 und 3, D1 bis D3, E1 bis E3 | mittel bis hoch | je nach Thema |

Paket 1 geht sofort ohne PC. Pakete 2 bis 5 kann ich als PRs vorbereiten; gemergt wird nach der Regel
"main gleich live" erst, wenn vom neuen PC aus deployt werden kann. A6 (Branches und alte PRs) wartet auf
dein Go.

Keiner der Vorschläge ändert ein gesperrtes System (TalkUx-Shell, Blossom Blade, Gravwell Cleaver,
Boosters, Anvil/BoosterSockets, Ränge, ShutdownCountdown, Borderlands).

## Was gut ist und so bleiben soll

- `AtomicYaml` und die Dirty-Flags: das Muster stimmt, es wird nur nicht überall benutzt.
- Tick-Schleifen in Foraging, Fishing und im Kampfsystem steigen früh aus und sind sauber kommentiert.
- Effekt-Entities sind fast überall nicht-persistent, sie überleben also keinen Neustart als Leichen.
- Der Transfer v6 hat Ledger, Quarantäne und Rollback. Das Konzept ist gut, nur eben mehrfach vorhanden.
- Keine Hosts, Schlüssel oder Passwörter im Repo gefunden.
