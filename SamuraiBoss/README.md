# 桜木 刃斎 – Sakuragi Jinsai · 散華の剣聖

**Ein eigenständiger Bosskampf für Paper 1.21.1** – ein legendärer Samurai-Meister in seinem eigenen,
zerstörbaren Tempel-Dojo. Kein ModelEngine, kein MythicMobs, kein PacketEvents: Modell, Animationen,
Effekte, Arena, Zerstörung und Wiederaufbau laufen komplett über **Display-Entities** und die Paper-API.

> *„Tausend Frühlinge habe ich unter diesem Baum gewacht …"*

---

## Inhalt

1. [Die Legende](#die-legende)
2. [Schnellstart](#schnellstart)
3. [Installation & Bauen](#installation--bauen)
4. [Das Dojo](#das-dojo)
5. [Den Kampf starten](#den-kampf-starten)
6. [Befehle & Berechtigungen](#befehle--berechtigungen)
7. [Phasen](#phasen)
8. [Fähigkeiten](#fähigkeiten)
9. [Zerstörung & Wiederaufbau](#zerstörung--wiederaufbau)
10. [Resource Pack (optional)](#resource-pack-optional)
11. [Konfiguration – die wichtigsten Schalter](#konfiguration--die-wichtigsten-schalter)
12. [Für Entwickler: Events & Aufbau](#für-entwickler-events--aufbau)
13. [Was getestet ist – und was nicht](#was-getestet-ist--und-was-nicht)
14. [Bekannte Grenzen](#bekannte-grenzen)
15. [Ideen für Ausbaustufen](#ideen-für-ausbaustufen)

---

## Die Legende

Vor tausend Jahren lehrte **Sakuragi Jinsai** im Tempelhof **千年桜道場** – dem *Dojo der
Tausendjährigen Kirsche*. Er schwor, die heilige Hängekirsche im Hof zu hüten, bis ein Schüler
käme, der ihn übertrifft. Er starb in Seiza unter dem Baum, sein Schwert **花吹雪 Hanafubuki**
(„Blütensturm") auf den Knien – doch der Schwur ließ ihn nicht gehen.

Jedes Mal, wenn die Tempelglocke schlägt, sammeln sich die Blütenblätter zu seiner Gestalt.
Er verneigt sich, erhebt sich – und prüft jeden, der den Hof betritt. Wer ihn besiegt, schenkt ihm
für einen Frühling Frieden. Sein Todesgedicht:

> 花は散り / 刃は鞘へ / 春の夢
> *Die Blüten fallen, / die Klinge kehrt in die Scheide – / ein Frühlingstraum.*

Sein Kampf hat drei Formen und eine letzte Technik:

| | Form | Wesen |
|---|---|---|
| 一 | **静 – Stille** | reines Iaijutsu: das Schwert ruht in der Scheide, jeder Schnitt ist ein Blitz |
| 二 | **嵐 – Sturm** | die Zeit hält an, er zieht die Klinge – Sturm, Regen, Geisterklingen, Schattenklone |
| 三 | **散華 – Fallende Blüten** | seine Rüstung zerbricht, er wird zum Geist; ein roter Mond geht auf |
| 終 | **天地一閃 → 無 – Leere** | ein Schnitt zwischen Himmel und Erde zerteilt das ganze Dojo; der letzte Kampf in den schwebenden Ruinen |

---

## Schnellstart

```text
1. SamuraiBoss-1.0.0.jar nach plugins/ kopieren, Server starten
2. Im Spiel an die Stelle gehen, wo die Mitte des Tempelhofs sein soll
3. /samuraiboss build          → Maße & Hinweise lesen
4. /samuraiboss build confirm  → das Dojo wächst aus der Erde (ca. 20 s)
5. Mit allen Mitspielern in den Hof gehen
6. /samuraiboss spawn          → die Glocke schlägt, der Kampf beginnt
```

---

## Installation & Bauen

**Voraussetzungen:** Paper **1.21.1** (oder ein kompatibler Fork wie Purpur/Leaves), **Java 21**.
Keine weiteren Plugins nötig.

**Fertiges Plugin:** `SamuraiBoss-1.0.0.jar` in den Ordner `plugins/` legen und den Server starten.
Beim ersten Start entsteht `plugins/SamuraiBoss/config.yml`.

**Aus dem Quellcode bauen** (z. B. durch deinen Cursor-Agenten):

```bash
mvn clean package
# Ergebnis: target/SamuraiBoss-1.0.0.jar
```

Die einzige Abhängigkeit ist `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT` (scope *provided*,
aus `https://repo.papermc.io/repository/maven-public/`).

---

## Das Dojo

`/samuraiboss build` baut **千年桜道場** an deiner Position – du stehst danach in der Mitte des
Hofes, auf Laufhöhe.

| | |
|---|---|
| **Größe** | 55 × 34 × 63 Blöcke (inkl. Fundament bis 6 Blöcke unter dir) |
| **Ausrichtung** | Haupthalle im **Norden**, Tor & Torii im **Süden**, Kirschbaum im **Westen**, Teich & Glockenturm im **Osten** |
| **Spielfläche** | ca. 43 × 33 Blöcke offener Hof – Platz für viele Spieler |

Was drin ist: Haupthalle mit geschwungenem Dach, Shoji-Papierwänden, Tatami, Schriftrolle 「無心」
und Schwertständer · ein großes rotes Torii mit Plakette 千年桜 · die **Tausendjährige Hängekirsche**
mit herabhängenden, im Wind schwingenden Blütenzweigen aus Display-Entities · Koi-Teich mit
schwimmenden Koi und roter Bogenbrücke · Glockenturm mit Bonshō-Glocke (schwingt beim Anschlagen) ·
Steinlaternen · Nobori-Banner im Wind · Zen-Garten · Mauer mit Ziegeldach und Tor.

- **Vor dem Bau wird das Gelände gesichert** (`prebuild.snap`). `/samuraiboss remove` stellt es
  später exakt wieder her.
- Die Deko-Entities sind **nicht persistent** und werden automatisch gesetzt, sobald die Chunks
  geladen sind – es entstehen nie doppelte oder verwaiste Entities.
- Außerhalb von Kämpfen ist das Dojo eine normale Baustelle. Während Bau, Kampf und Wiederaufbau
  ist es geschützt (kein Abbauen/Setzen, keine Explosionen, kein Feuer, kein Wasserfluss).

> **Warum keine .schem-Datei?** Das Dojo wird aus einem deterministischen Bauplan erzeugt, in dem
> **jeder Block einem Bauteil zugeordnet** ist (Shoji, Torii-Balken, Dach, Mauer …). Genau das
> ermöglicht die gestaffelte Zerstörung über die Phasen. Eine fremde Schematic hätte diese
> Information nicht – siehe [Ausbaustufen](#ideen-für-ausbaustufen).

---

## Den Kampf starten

- **`/samuraiboss spawn`** – alle Spieler, die sich **innerhalb der Mauern** befinden, nehmen teil.
  Wer später den Hof betritt, steigt automatisch ein; wer ihn verlässt, steigt aus.
- **Glocke** (optional): mit `arena.bell-challenge: true` startet ein Rechtsklick auf die
  Tempelglocke den Kampf (Berechtigung `samuraiboss.challenge`, Standard: alle).
- Die **Lebenspunkte** skalieren mit der Spielerzahl beim Start
  (`boss.health` + `boss.health-per-extra-player` je weiterem Spieler).
- Sind **30 Sekunden lang** keine Herausforderer mehr im Hof (alle tot oder gegangen), verneigt er
  sich, löst sich in Blüten auf und das Dojo baut sich wieder auf.
- Wer im Kampf fällt, **behält sein Inventar** (`arena.keep-inventory`).

---

## Befehle & Berechtigungen

Hauptbefehl `/samuraiboss` – Aliase `/kensei`, `/samurai`. Ohne Namen gilt das Dojo, in dem du
stehst (oder das nächste).

| Befehl | Wirkung |
|---|---|
| `/samuraiboss build [name]` | Maße & Hinweise anzeigen |
| `/samuraiboss build confirm [name]` | Dojo an deiner Position bauen (animiert oder sofort) |
| `/samuraiboss spawn [name]` | Kampf starten |
| `/samuraiboss stop [name]` | Kampf abbrechen – das Dojo baut sich (animiert) wieder auf |
| `/samuraiboss reset [name]` | Dojo **sofort exakt** in den Ausgangszustand versetzen |
| `/samuraiboss remove [name] confirm` | Dojo abreißen, Gelände wie vor dem Bau |
| `/samuraiboss list` · `info [name]` · `tp [name]` | Übersicht, Zustand, Teleport zum Tor |
| `/samuraiboss reload` | `config.yml` neu laden (laufende Kämpfe behalten ihre Werte) |
| `/samuraiboss debug phase 2\|3\|ult` | Phasenwechsel / Ultimate erzwingen |
| `/samuraiboss debug ability <id>` | Fähigkeit auslösen (IDs siehe unten) |
| `/samuraiboss debug hp <prozent>` · `debug kill` | Lebenspunkte setzen · besiegen |

| Berechtigung | Standard | |
|---|---|---|
| `samuraiboss.admin` | OP | alle Befehle |
| `samuraiboss.challenge` | alle | Kampf per Glocke starten (wenn aktiviert) |

---

## Phasen

### Einleitung (≈ 13 s)
Die Glocke schlägt zweimal, der Himmel färbt sich in Abenddämmerung. Blütenblätter wirbeln in einer
Spirale zusammen, ein Siegelkreis aus Kanji dreht sich am Boden, eine Lichtsäule steigt auf – und er
sitzt in Seiza im Hof. Er spricht, verneigt sich, erhebt sich. Name und Titel erscheinen, die
Bossleiste füllt sich, die Musik (Koto, Shakuhachi, Taiko) setzt ein.

### 一の型 「静」 Stille (100 % → 66 %)
Das Schwert bleibt in der Scheide. Jeder Angriff ist Iaijutsu: Ziehen, Schneiden, Blut abschütteln,
Einstecken – und **erst beim „Klick" des Einrastens fällt der Schaden**.

### Übergang 1 → 2: **Die Zeit steht still**
Alle Blütenblätter **erstarren mitten in der Luft**, die Musik verstummt, ihr seid wie gelähmt. Nur
er bewegt sich – und zieht seine Klinge absurd langsam. Hinter ihm erscheint riesig **嵐**, zerspringt
– die Zeit läuft weiter: ein Kreisschnitt, Sturm, Regen, Donner. Sein Helm und das Rückenbanner
werden davongerissen, die **Shoji-Wände zerfetzen**, Laternenköpfe stürzen, die Nobori-Banner fliegen
davon.

### 二の型 「嵐」 Sturm (66 % → 33 %)
Gezogene, rosa leuchtende Klinge, Regen und Nacht. Schnellere Kombinationen, Geisterklingen,
Blütentornado, Schattenklone.

### Übergang 2 → 3: **散華**
Stille. Er steckt die Klinge ein und kniet in Seiza, alle Blüten sinken zu Boden. Seine
**Rüstungsplatten fallen klirrend ab**. Der Hof zerbricht: der **Torii-Balken kracht herab**, der
**Glockenturm stürzt ein** (die Glocke schlägt ein letztes Mal), die Mauerdächer bröckeln ringsum, die
**Kirsche verliert all ihre Blüten**, die Brücke bricht in den Teich. Er erhebt sich als
**leuchtender Geist** (Yūrei – ohne Füße), ein **roter Mond aus Blüten** geht auf, der Bildschirmrand
glüht rot, die Blüten steigen zum Himmel.

### 三の型 「散華」 Fallende Blüten (33 % → 12 %)
Der Geist schwebt, alle Techniken stehen ihm offen – dazu Pentagramm-Schnitt, Klingenregen und die
Kette der Nachbilder.

### Ultimate: **天地一閃** – „Ein Blitz zwischen Himmel und Erde"
Bei 12 % (oder spätestens nach `phases.ultimate-timeout`): Er springt hoch über den Hof, die Zeit
erstarrt erneut. Über dem **gesamten Dojo spannt sich auf Kopfhöhe eine gleißende Schnittebene**, die
im Herzschlag pulsiert, während er seine Klinge zentimeterweise zieht.

> **Verneigt euch!** Wer im Moment des Schnitts **schleicht**, bleibt unter der Ebene und wird
> verschont. Wer steht, wird getroffen (standardmäßig nicht tödlich).

Dann: Stille, ein Blitz – **das ganze Dojo wird durchtrennt**. Der obere Teil gleitet an der
Schnittebene ab, alles explodiert nach außen – und die **Trümmer erstarren schwebend in der Luft**,
ein Ring aus Ruinen um den leeren Hof.

### Erschöpfung → 終の型 「無」 Leere (12 % → 0 %)
Er landet, kniet erschöpft auf sein Schwert gestützt – **6 Sekunden lang nimmt er 50 % mehr Schaden**.
Dann steht er auf: der letzte Kampf zwischen den schwebenden Ruinen.

### Tod & Sieg
Er kniet nieder und spricht sein **Todesgedicht** (花は散り / 刃は鞘へ / 春の夢), während die Sonne
aufgeht. Sein Körper löst sich in Blüten auf, das Katana bleibt im Boden stecken, die Kirsche erblüht
neu. **見事** – Belohnungen werden verteilt, der Sieg wird verkündet.

### Wiederaufbau
Die Ruinen bleiben `arena.ruin-seconds` liegen. Dann **schwebt jeder Block an seinen Platz zurück** –
die schwebenden Trümmer fliegen heim, fehlende Blöcke kommen von überall herbei, **getragen von
Kirschblütenblättern**. Zum Schluss wird alles exakt mit dem Zustand vor dem Kampf abgeglichen.

---

## Fähigkeiten

Jede Fähigkeit ist am Boden **vorher telegrafiert** (rote Flächen, die sich füllen) – was man sieht,
trifft. Er wählt nach Gewichtung (Phase, Abstand), hat Abklingzeiten und **bevorzugt in jeder Phase
Techniken, die er noch nicht gezeigt hat**.

| ID | Name | Phase | Beschreibung · Gegenmittel |
|---|---|---|---|
| `issen` | **一閃 Issen** | 1 | Tiefe Iai-Haltung, eine Linie füllt sich. Blitzschritt durch die Linie mit Nachbild-Spur, die Luft ist zerschnitten – **der Schaden fällt erst beim Einrasten der Klinge**. *Raus aus der Linie, bevor er losstürmt.* |
| `kesagiri` | **袈裟斬り Kesagiri** | 1–3 | Diagonaler Schnitt von der Schulter zur Hüfte im Kegel vor ihm (in Phase 1 aus der Scheide). *Hinter ihn oder auf Abstand.* |
| `tsubame` | **燕返し Tsubame-gaeshi** | 2–3 | „Schwalbenwende": Kesagiri + sofort ein aufsteigender Gegenschnitt. *Nicht nach dem ersten Schnitt zurück in den Kegel laufen.* |
| `kazekiri` | **風斬り Kaze-giri** | 1–3 | Fliegende Windklinge in Kniehöhe über den Boden (ab Phase 2 drei im Fächer). *Drüberspringen oder seitlich ausweichen.* |
| `utsusemi` | **空蝉 Utsusemi** | 1–3 | Konter: Wer ihn zu sehr bedrängt, schlägt plötzlich in Blütenblätter – er erscheint **hinter** dem Angreifer, ein Kreis füllt sich, Rundumschlag. *Nicht blind weiterhauen – raus aus dem Kreis.* |
| `triple-issen` | **三段一閃 Sandan-Issen** | 2 | Er steckt ein und stürmt dreimal hintereinander durch euch – jede Linie zielt neu. Alle Schnitte fallen gemeinsam beim Einrasten. *In Bewegung bleiben.* |
| `thousand-blades` | **千本桜 Senbon-zakura** | 2–3 | Er reckt das Schwert in den Himmel: eine kreisende **Krone aus Geisterklingen**. Er deutet auf euch – die Klingen drehen sich zu ihren Zielen und stürzen nacheinander herab (rote Kreise am Boden). *Kreise verlassen; sie zielen leicht voraus.* |
| `tornado` | **桜嵐 Sakura-arashi** | 2–3 | Doppelte Drehung mit drei Treffern im Kreis, dann löst sich ein **Tornado aus Blüten und Windschlieren** und wandert auf euch zu: zieht an, schleudert hoch, blendet (Phase 3: zwei Tornados). *Abstand halten, quer laufen.* |
| `shadow-clones` | **影分身 Kage-bunshin** | 2–3 | Er tritt in seinen Schatten, violette Klone steigen aus Pfützen rund um euch. Er stürmt durch die Mitte – und **jeder Klon wiederholt exakt seine Bewegung**, zeitversetzt, aus seiner Richtung (aufgezeichnete Posen). *Die Mitte ist der gefährlichste Ort – violette Linien zeigen alle Bahnen.* |
| `star` | **五芒星 Gobōsei** | 3 | Der Geist zeichnet mit fünf Sprints ein **Pentagramm** um euch; die Linien bleiben als leuchtende Narben im Boden. Beim Klick der Scheide explodiert der Stern. *Von allen Linien runter – Lücken zwischen den Zacken nutzen.* |
| `blade-rain` | **刃の雨 Ha-no-ame** | 3 | Er schwebt empor, ein Kanji-Siegel dreht sich hoch über dem Hof, in vier Wellen stürzen Geisterklingen senkrecht herab. (Während er schwebt, ist er nicht zu treffen.) *Kreise verlassen.* |
| `afterimage` | **残像連斬 Zanzō-renzan** | 3 | Fünf Sprünge rund um euch – an jeder Stelle bleibt ein **erstarrtes Nachbild** in Schlagpose mit einem Kreis, der sich füllt. Beim Einstecken schlagen **alle Nachbilder gleichzeitig** zu. *Zwischen die Kreise.* |
| — | **天地一閃 Tenchi-Issen** | Ultimate | Siehe oben – **schleichen!** |

---

## Zerstörung & Wiederaufbau

| Stufe | Wann | Was zerbricht |
|---|---|---|
| 1 | Übergang zu 嵐 | Shoji-Papierwände (Fetzen wehen davon), Laternenköpfe, Nobori-Banner |
| 2 | Übergang zu 散華 | Torii-Balken, Glockenturm (mit Glocke), Mauerdächer, alle Kirschblüten, Brücke |
| 3 | 天地一閃 | alles Übrige: Halle, Dach, Mauern, Tor, Torii, Laternen, Garten – schwebende Ruinen |

- Jeder zerstörte Block wird gemerkt. Ein Teil wird als **BlockDisplay an exakt derselben Stelle**
  animiert (nahtloser Übergang Block → Trümmer), begrenzt durch `performance.max-debris-displays`.
- Fundament, Boden, Teich, Hallenboden und der Stamm der Kirsche bleiben immer stehen.
- **Absturzsicher:** Beim Kampfbeginn wird der gesamte Bereich (Blöcke + BlockData) als Snapshot
  gespeichert (`arenas/<name>/fight.snap`) und `fight-active: true` vermerkt. Stürzt der Server ab,
  stellt das Plugin beim nächsten Start den Zustand vor dem Kampf exakt wieder her. Alle Entities des
  Plugins sind nicht persistent – es bleibt nichts zurück.
- Bei `/reload`, Server-Stopp oder `/samuraiboss reset` wird **sofort** exakt wiederhergestellt.

---

## Resource Pack (optional)

Das Plugin funktioniert **komplett ohne Resource Pack** (alles aus Vanilla-Blöcken und -Klängen).
Mit dem Pack (Ordner `resourcepack/` bzw. `SamuraiBoss-ResourcePack.zip`) kommen dazu:

- echtes **Katana „Hanafubuki"** (Ito-Wicklung, Tsuba, Hamon), Saya, Griff, **Geister-Katana**
- **Blütenblatt-Modelle** statt Wollflocken, **Schnitt-Sicheln**, **Siegelkreise**, **roter Mond**
- **Pinsel-Kanji** (aus der Schrift *Yuji Syuku*, SIL OFL) – mit Bruchstück-Glyphen, sodass z. B.
  嵐 beim Phasenwechsel wirklich **in Scherben zerspringt**
- eigene **Klänge**: Ziehen („Shiiing"), Einrasten-Klick, tiefe Bonshō-Glocke, Taiko, Schnitt,
  Zeitstillstand, Zerspringen, Wind, sowie Koto & Shakuhachi für die Musik (mit deutschen/englischen
  Untertiteln)

**Einrichten:**

1. `SamuraiBoss-ResourcePack.zip` irgendwo per HTTPS erreichbar ablegen und in
   `server.properties` eintragen:
   ```properties
   resource-pack=https://example.com/SamuraiBoss-ResourcePack.zip
   resource-pack-sha1=<SHA-1 aus SamuraiBoss-ResourcePack.sha1>
   require-resource-pack=false
   ```
   (oder den Inhalt in euer bestehendes Server-Pack mischen)
2. In `plugins/SamuraiBoss/config.yml`: `resource-pack.enabled: true`, dann `/samuraiboss reload`.

**Technik (1.21.1):** Eigener Namespace `samurai`. Die einzige Datei im `minecraft`-Namespace ist
`assets/minecraft/models/item/pink_dye.json` – **Pinke Farbe sieht dadurch exakt wie in Vanilla aus**,
die Datei fügt nur `overrides` für `custom_model_data` **7310001–7310014** hinzu (so funktioniert
custom_model_data in 1.21.1; die `item_model`-Komponente gibt es erst ab 1.21.2). Benutzt euer
Server-Pack bereits `pink_dye.json`, die Overrides zusammenführen oder `resource-pack.base-material`
+ `model-data` in der Config ändern.

| CMD | Modell | CMD | Modell |
|---|---|---|---|
| 7310001 | katana | 7310008 | seal |
| 7310002 | katana_ghost | 7310009 | seal_ring |
| 7310003 | saya | 7310010 | mon |
| 7310004 | petal | 7310011 | crest |
| 7310005 | petal_white | 7310012 | menpo |
| 7310006 | slash | 7310013 | moon |
| 7310007 | slash_pink | 7310014 | hilt |

Das Pack wird von `tools/pack/make_pack.py` erzeugt (Texturen, Modelle, Schrift und Klänge sind
prozedural) – Änderungen also dort vornehmen und neu erzeugen.

---

## Konfiguration – die wichtigsten Schalter

Alle Werte sind in `config.yml` kommentiert. Zeiten in Ticks (20 = 1 s), Schaden in Lebenspunkten
(2 = 1 Herz).

| Schlüssel | Standard | |
|---|---|---|
| `boss.health` / `health-per-extra-player` | 1600 / 900 | Lebenspunkte (virtuell, unabhängig vom Vanilla-Limit) |
| `boss.damage-reduction` | 0.0 | Schutz gegen Spielerschaden |
| `phases.arashi-at` / `sange-at` / `ultimate-at` | 0.66 / 0.33 / 0.12 | Phasengrenzen |
| `phases.exhausted-ticks` / `exhausted-damage-multiplier` | 120 / 1.5 | Erschöpfung nach der Ultimate |
| `damage.*` | – | Schaden jeder Fähigkeit, `multiplier` für alles |
| `damage.ultimate-lethal` | false | darf die Ultimate töten? |
| `cooldowns.*` | – | Abklingzeiten, `global-min/max` = Pause zwischen Fähigkeiten |
| `intro.enabled` | true | Einleitung abspielen |
| `arena.ruin-seconds` | 6 | wie lange die Ruinen nach dem Sieg liegen bleiben |
| `arena.rebuild-per-tick` / `rebuild-flight-ticks` | 18 / 26 | Tempo des Wiederaufbaus |
| `arena.bell-challenge` | false | Glocke startet den Kampf |
| `arena.keep-inventory` | true | Inventar behalten beim Tod im Kampf |
| `performance.max-effect-displays` | 700 | Obergrenze gleichzeitiger Effekt-Displays |
| `performance.max-debris-displays` | 1600 | Obergrenze für Trümmer-Displays |
| `performance.effect-density` | 1.0 | 0.3–1.0, reduziert Partikel-/Display-Mengen |
| `performance.floating-debris` | true | Trümmer schweben nach der Ultimate (sonst fallen sie) |
| `performance.model-update-interval` | 2 | 2 = flüssig, 3 = spart Bandbreite |
| `effects.*` | – | Kamerawackeln, Himmel, roter Rand, Musik, Partikel, Titel einzeln abschaltbar |
| `rewards.commands` | [] | Befehle beim Sieg, Platzhalter `%player%`, `%damage%`, `%rank%` |
| `messages.*` | – | alle Texte (MiniMessage) |

**Leistung:** Der Boss selbst besteht aus rund 100 Display-Teilen. Im normalen Kampf sind etwa
300–700 Displays aktiv, nach der Ultimate (schwebende Ruinen) bis ca. 2 000. Wird es eng:
`max-debris-displays` senken, `floating-debris: false`, `effect-density: 0.6`,
`model-update-interval: 3`.

---

## Für Entwickler: Events & Aufbau

**Events** (`net.aetherion.samuraiboss.api`):
`SamuraiFightStartEvent` · `SamuraiPhaseChangeEvent` (von/nach Phase) ·
`SamuraiBossDefeatEvent` (Teilnehmer + Schaden pro UUID – für eigene Belohnungen) ·
`SamuraiFightEndEvent` (Sieg ja/nein, nach dem Wiederaufbau).

**Pakete:**

```
net.aetherion.samuraiboss
├─ SamuraiBossPlugin, Settings          Einstieg, typisierte config.yml
├─ command/                             /samuraiboss
├─ listener/                            Treffer auf die Hitbox, Schutz des Dojos, Glocke …
├─ boss/
│  ├─ Fight, FightManager               Kampf-Zustandsmaschine, HP, Teilnehmer, Bossleiste
│  ├─ Cinematics                        Einleitung, Phasenübergänge, Ultimate, Tod
│  ├─ BossBrain                         Zielwahl, Bewegung, gewichtete Fähigkeitswahl
│  ├─ SamuraiBoss                       Hitbox + Display-Modell + Animator + prozedurale Ebenen
│  ├─ SamuraiModel/Poses/Animations/Skins   Modell (Knochen, ~100 Teile), Posen (IK), Clips, Skins
│  ├─ ShadowClone                       Klon, der aufgezeichnete Posen zeitversetzt abspielt
│  └─ ability/                          je Fähigkeit eine Klasse (Choreografie als Sequence)
├─ render/
│  ├─ model/, anim/                     Knochen-Modelle, Keyframe-Clips, Überblendung, IK
│  └─ fx/                               Effekte: Telegrafie, Sicheln, Schnittwände, Kanji, Tornado …
├─ arena/                               Dojo-Bauplan, Bauen, Deko, Snapshots, Wiederherstellung
│  └─ destruction/                      gestaffelte Zerstörung, schwebende Trümmer, Wiederaufbau
├─ audio/                               Klangschichten + prozedurale Musik (In-Tonleiter)
└─ atmosphere/                          Tageszeit/Wetter/roter Rand/Wackeln pro Spieler
```

**Technische Grundsätze:** Alle Displays sind nicht persistent und mit PersistentData markiert;
Effekt-Displays kommen aus einem Pool (wiederverwendet statt neu gespawnt, harte Obergrenze);
Modellteile reiten auf einem Anker-Display (eine Bewegung = ein Paket), nur sichtbar geänderte Teile
werden gesendet, Interpolation übernimmt der Client. Alles läuft über einen einzigen Tick-Task.

---

## Was getestet ist – und was nicht

Ehrlich gesagt: **Ein echter Minecraft-Server/-Client stand in der Entwicklungsumgebung nicht zur
Verfügung** (die Download-Server von Mojang/PaperMC waren dort gesperrt). Deshalb:

- ✅ **Kompiliert gegen die echte Paper-API 1.21.1** – ohne Fehler und ohne Warnungen.
- ✅ **Offline-Simulation:** Die echten Plugin-Klassen liefen gegen eine nachgebaute Bukkit-Welt
  (Blöcke, Entities, Spieler-Bots, Interpolation der Displays). Komplette Kämpfe von der Einleitung
  bis zum Wiederaufbau ohne eine einzige Exception; jede Fähigkeit einzeln ausgelöst und als
  Bildfolge gerendert.
- ✅ **Randfälle** mit exaktem Blockvergleich danach: alle verlassen das Dojo, Admin-Abbruch,
  Server-Stopp während der Ultimate, Server-Stopp während des Wiederaufbaus, Spielertode,
  **Absturz** (nur Dateien überleben → Neustart stellt alles exakt her). Jedes Mal: 0 abweichende
  Blöcke, keine übrig gebliebenen Entities.
- ⚠️ **Nicht getestet:** das tatsächliche Aussehen im Minecraft-Client (Licht, Glanz, Glühen),
  Netzwerklast mit echten Spielern, das Resource Pack im Client.

**Empfehlung für den ersten Test:** Testserver, `/samuraiboss build confirm`, dann
`/samuraiboss spawn` und mit `/samuraiboss debug ability <id>` bzw. `debug phase 2|3|ult` alles
einmal durchspielen.

---

## Bekannte Grenzen

- **Bedrock-Spieler (Geyser)** sehen Display-Entities nicht oder falsch – der Boss ist für Java-Clients
  gebaut.
- Ein Kampf pro Dojo; mehrere Dojos (an verschiedenen Orten) sind möglich.
- Keine eigene Schematic-Unterstützung (siehe oben).
- Der Boss ist ein „Bodenkämpfer": Spieler auf hohen Dächern erreicht er nur mit Fernangriffen.
- Während der Kino-Momente ist er unverwundbar (Treffer klirren an ihm ab).
- Ohne Resource Pack sind Kanji in der Standardschrift, Blütenblätter kleine Woll-Flocken und alle
  Klänge Vanilla-Schichten.
- Spieler-Zeit, Wetter und roter Rand sind rein clientseitig pro Spieler – die echte Welt bleibt
  unverändert.
- Sehr schwache Clients können bei der Ultimate (≈ 2 000 Displays) kurz ruckeln → Leistungs-Schalter
  oben.

---

## Ideen für Ausbaustufen

- **Schwierigkeitsgrade** (Normal/Meister/Legende) mit eigenen Phasengrenzen, Zusatzangriffen und
  Belohnungen; „No-Hit"-Wertung.
- **Bestenliste** (Zeit, Schaden, Treffer kassiert) per Hologramm am Tor.
- **Schematic-Import** mit automatischer Bauteil-Erkennung (Holz → Säulen, Papier → Shoji …) für
  eigene Arenen.
- **Saisonaler Kampf**: nur im Minecraft-„Frühling" oder einmal pro Echtzeit-Tag; die Glocke schlägt
  dann von selbst.
- **Belohnungs-Katana** „Hanafubuki" als Item mit eigenem Modell und Fähigkeit (Iai-Dash).
- **Weitere Formen**: ein „Mondphasen"-Duell nur gegen den besten Spieler, Wasser-Techniken am Teich,
  ein Kampf auf dem einstürzenden Hallendach.
- **Echte Stimme** (japanische Sprachaufnahmen) und orchestrale Musik im Resource Pack.
- **Folia-Unterstützung** (regionales Scheduling).

---

## Credits

Plugin, Modelle, Animationen, Effekte, Dojo-Bauplan, Texturen und Klänge: für dieses Projekt erstellt.
Pinsel-Kanji im Resource Pack: Schrift *Yuji Syuku* (Kinuta Font Factory), SIL Open Font License 1.1.
