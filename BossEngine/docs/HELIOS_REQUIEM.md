# HELIOS REQUIEM: der sterbende Stern

Showcase-Encounter in zwei Akten für BossEngine (Paper 1.21.1, Stand `pom.xml`).
Dieses Dokument ist der Architektur-Vorschlag und zugleich die Referenz für Config, Befehle und Grenzen.

---

## 1. Was ich aus den Referenz-Bossen abgeleitet habe

Gelesen: `HollowSunDirector` + `HollowProps/Accretion/Sky` (Branch `cursor/hollow-sun-elevation`),
`HangingSaintDirector` + `saint/*` (Branch `claude/boss-hanging-saint`),
`WorldEaterDirector` + `worldeater/*`, `WorldEaterBonusChest`, `HollowReliquary`, `SeraphineMusicBox` (Branch `feature/world-eater-boss`),
dazu `BossInstance`, `BossManager`, `LootService`, `BossHits`, `BossBarHud`, `BossCombatListener`.

Die Handschrift, die ich übernehme:

| Prinzip | Wie es bei euch aussieht | Wie Helios es umsetzt |
|---|---|---|
| **Fester Anker, keine Teleports** | `WeFx`/`SaintFx`: jedes Display spawnt am Bühnenanker, Bewegung nur per Transformation + Interpolation | `HeliosStage`: gleiches Muster, dazu ein Display-Budget pro Tick |
| **Geometrie > Partikel** | Hollow Sun: Partikelaufrufe 151 → 104, Dust-Telegraphs gestrichen | Telegraphs sind Lichtlinien und Ringe aus Displays; Partikel nur als einmalige Garnitur |
| **Wind-up → Signal → Hit → Stille** | Hitstop 2–4 Ticks, „Spent“-Fenster | Jede Attacke hat Ton-Tell UND Bild-Tell, Treffer landen auf dem Takt |
| **Eine Idee pro Beat** | „One family of ground hazards at a time“ | Attack-Scheduler mit Hazard-Familien, nie zwei Boden-Gefahren gleichzeitig (außer im Finale, dort bewusst) |
| **Rücksichtsloses Aufräumen** | `clear()` auf jedem Pfad, nicht-persistente Displays, Tagging `beam_fx` | Alles hängt an einer `HeliosStage`; Arena-Restore aus Layout; Journal für Crash-Recovery |
| **Loot als Inszenierung** | Music Box, Reliquary, Seed Vault: `LootService.grantToChest` → Claim per Spieler | Gleicher Vertrag: `StarseedReliquary`, pro Spieler eine Kapsel |

## 2. Wichtiger Hinweis: Überschneidung mit Hollow Sun

Hollow Sun ist bereits ein Stern-Boss mit Implosion, Akkretionsscheibe aus Bodenplatten, Ereignishorizont,
Supernova und Pro-Spieler-Himmel. Das Konzept „Helios“ würde 1:1 dieselben Beats wiederholen.
Damit Helios nicht wie ein Remake wirkt, bekommt er eine **eigene Sprache**:

| | Hollow Sun | Helios Requiem |
|---|---|---|
| Motiv | Schmiede, Ritter mit Streitkolben | **Requiem**: der ganze Kampf ist ein Musikstück. Der Herzschlag des Sterns ist die Kampfuhr (BPM), jede Attacke zählt ein und landet auf dem Takt |
| Tell-Sprache | Sonnen-Glyphen am Boden (gold/rot) | **Lichtgeometrie**: haarfeine weiße Zielinien → bernsteinfarbene Füllung → Blitz. Ringe, Polyeder, Armillarsphäre |
| Schwarzes Loch | Scheibe aus echten Bodenplatten | **Gravitationslinse + Zeitdilatation**: Displays nahe der Singularität werden gestreckt, Sounds werden mit Nähe tiefer gepitcht, der Himmel „friert“ für Spieler nahe am Horizont ein |
| Tod | Supernova, Himmel blitzt Mittag | **Letzter Akkord**: Zeitlupe, Stille, Einatmen, Supernova. Das Nachleuchten **baut die Arena Ring für Ring aus Licht wieder auf** (der Restore ist Teil des Finales) |
| Boss-Körper | Ritter | Akt 1 humanoid (Herold), Akt 2 **Armillarsphäre** um eine Porzellan-Gold-Maske: die vier Herold-Klingen sind ihre Ringe |

## 3. Architektur

### 3.1 Engine-Integration (klein und generisch)

Statt einen weiteren Sonderfall in `BossInstance` zu verdrahten (dort gibt es schon 10+ Director-Felder),
führe ich **einen** generischen Hook ein:

```
instance/BossScript.java      Interface: onBind, tick, beginDeath, abort, blocksDamage, incoming(), ownsBossBar(), takesLoot()
instance/BossScripts.java     Registry: templateId → Factory (BossInstance → BossScript)
```

`BossInstance` fragt an 8 Stellen `script` ab (bind/rebind, tick, isDamageBlocked, isCinematicDying,
startDeathCinematic, abortCinematic/despawnMinions, Phase-Gates). Ein gescripteter Körper wird von der Engine
**nicht** angefasst: kein Leash, kein Unstick, kein AI-Polish, keine YAML-Transition-Visuals.
`BossManager.payoutDeath` gibt Loot an `script.handLoot(...)`, `BossBarHud` blendet sich für `ownsBossBar()` aus,
`BossCombatListener` lässt `script.incoming(...)` Treffer umlenken (z. B. Spiegel-Klone).

Zukünftige Showcase-Bosse brauchen damit keine `BossInstance`-Änderung mehr. Beim Merge mit
`feature/world-eater-boss` sind es wenige, triviale Konfliktzeilen.

### 3.2 Paketstruktur `de.aetherion.bossengine.helios`

```
helios/
  HeliosModule          Bootstrap aus BossEngine.onEnable: Config, Welt, Listener, Befehl, Tick-Loop, Recovery
  HeliosConfig          typisierter Snapshot von helios.yml (reloadbar)
  HeliosCommand         /helios enter|leave + Admin (skip, attack, hp, debug, restore, abort, list, reload)
  core/
    HMath               Easing, Vektoren, Quaternionen, Geometrie (Bukkit-frei, offline testbar)
    HeliosStage         Anker, Display-Fabrik, Tracking, Budget, Audience/Targets, Transform-Helfer
    DisplayBudget       Spawns/Tick, Pushes/Tick, Live-Cap, Qualitätsstufe, Messwerte für /helios debug
    Score / Tempo       Sound-Layering mit Pitch-Kurven, Takt (BPM), Beat-Callbacks, Stille
    Camera              Hurt-Shake, Weißblitz, Darkness/Blindheit, Vignette (Worldborder), Zeitlupe
    SkyControl          Pro-Spieler-Zeit/-Wetter + Bossbar-Flags (DARKEN_SCREEN, WORLD_FOG)
    BlockCracks         Paket-Risse (sendBlockDamage) mit Fortschritt und Auto-Clear
    Beam / Shapes       Strahl (Kern+Hülle), Ringsegmente, Polyeder-Kanten, Scheiben
  world/
    HeliosWorld         eigene Void-Welt „helios_requiem“ (Hauptwelt nie berührt)
    ArenaSlots          Slot-Raster (1024 Blöcke Abstand) + Journal (AtomicYaml) → Crash-Recovery
    ArenaLayout         prozedurales Layout = Snapshot (Ringe, Sektoren, Nähte, Kiel)
    ArenaBuilder        gedrosseltes Bauen/Löschen/Wiederherstellen (Blöcke/Tick aus Config)
    Arena               Laufzeit: Sektor-Zustände, Risse, Absturz, Verglühen, Inseln, Deckung, Safe-Spots
  encounter/
    HeliosEncounter     Zustandsmaschine pro Gruppe: PREPARE → INTRO → HERALD → INTERLUDE → HELIOS → DEATH → REWARD → RESTORE
    Participants        Rückkehrpunkt, Gamemode, Geister-Zustand; in Spieler-PDC gespiegelt (crash-sicher)
    HeliosGuard         Listener: Perle/Chorus/Elytra/Fly/Blöcke/Befehle/Teleport/Quit/Join/Tod/Void
    HeliosBars          eigene Bossbars (Adventure) mit Phasenmarkern, 2-Bar-Layout in Akt 2
  star/DyingStar        der Stern: Kern, gegenläufige Schalen, Korona-Ringe, Trümmer in Kepler-Bahnen
  herald/               HeraldRig (Display-Skelett + 4 Klingen), HeraldScript, attacks/*
  helios/               HeliosRig (Armillarsphäre + Maske), HeliosScript, attacks/*, Singularity, Requiem
  reward/               StarseedReliquary (Leuchtpunkt → Kapseln gleiten herab → Claim)
```

### 3.3 Instanzierung und Crash-Sicherheit

* **Eine** Void-Welt, viele Slots: Welt-Erstellung pro Gruppe kostet Sekunden Main-Thread. Slots liegen 1024 Blöcke
  auseinander (außerhalb jeder Tracking-/Hör-Distanz). Max. gleichzeitige Instanzen per Config.
* Die Arena wird bei jedem Start **aus dem Layout gebaut**; das Layout *ist* der Snapshot.
  Zerstörung im Kampf ist echt (echte Blöcke fallen weg, Spieler fallen wirklich), weil es unsere eigene Welt ist.
* **Journal** `helios/state.yml` (atomar geschrieben): Slot-Zustand + Teilnehmer. Beim Start wird jeder nicht freie
  Slot gelöscht und freigegeben. Displays sind nicht-persistent, Boss-Körper `persistent: false`.
* **Spieler**: Rückkehrort + Gamemode liegen zusätzlich in der Spieler-PDC. Wer nach einem Crash in der Helios-Welt
  einloggt, wird zurückgesetzt, auch wenn das Journal fehlt.

### 3.4 Fail-Safes

| Fall | Verhalten |
|---|---|
| Disconnect | Spieler verlässt die Instanz, zählt als Aufgabe; beim Login zurück an den Rückkehrort |
| Tod | Respawn als **Echo** (Zuschauer, auf die Arena begrenzt), am Ende Rückkehr + Loot-Anteil nach Schaden |
| Wipe | Alle tot/weg → Kampf endet, Rückkehr, Restore |
| Void | `RESCUE` (Standard): der Stern zieht dich zurück auf festen Boden, harter Treffer; `DEATH` optional |
| Enderperle, Chorus, Elytra, Fly, Blöcke setzen/abbauen, fremde Teleports | blockiert (Admins mit Bypass-Permission ausgenommen) |
| Cutscenes | alle Spieler unverwundbar, Boss unverwundbar, keine Attacken |
| Plugin-Disable / `/helios abort` | alles weg, Spieler zurück, Arena gelöscht |

### 3.5 Performance-Budget

* Live-Displays pro Instanz: Soft-Cap (Standard 420), Spawns pro Tick gedrosselt (Queue), Qualitätsstufen
  `HIGH/MEDIUM/LOW` reduzieren Trümmer, Segmente, Scheibenteile.
* Auto-Degrade bei MSPT > Schwelle.
* `/helios debug` zeigt pro Instanz: Displays, Spawns/Tick, Transformation-Pushes/Tick, µs/Tick (EMA), Block-Ops in Queue, MSPT.

### 3.6 Balancing

`helios.yml`: Spielerzahl-Skalierung (HP/Schaden pro Zusatzspieler), Schaden jeder Attacke (als BossHits-„power“),
Timings, BPM je Phase, Enrage-Timer je Akt, Void-Modus, Qualitätsstufe, Reward-Timings.
Boss-HP und Phasenschwellen in `bosses/helios_herald.yml` und `bosses/helios_requiem.yml` wie gewohnt.

## 4. Ablauf (Beat-Sheet)

**Intro (≈12 s).** Schwarzblende, Stille, nur der Stern atmet. Herzschlag. Vier Klingen lösen sich aus der Korona und
bohren sich in Ring B. Rüstungsplatten fliegen spiralförmig aus dem Trümmergürtel und setzen den Herold zusammen.
Der Sonnenkern zündet (Weißblitz). Bossbar füllt sich animiert.

**Akt 1 – Der Herold (90 BPM).**
* *Blink-Strike*: steigender Glockenton, Nachbild flackert, eine vertikale Lichtlinie markiert die Landung hinter dir.
* *Klingen-Schwarm*: Spirale / Fächer / Zange; die Bahnen werden als Linien vorgezeichnet.
* *Laser-Käfig*: rotierendes Strahlengitter auf Kniehöhe, rückt zusammen; Zellen wandern mit.
* *Plattform-Sturz*: Paket-Risse wachsen im Takt, dann stürzt der Sektor echt ab (Display-Replik taumelt in die Tiefe), formt sich später von unten neu.
* *Sonnenlanze*: er zieht die Lanze aus dem Stern, Deckungsplatten fahren hoch, Dauerstrahl mit echter Sichtlinien-Prüfung.
* *Spiegelphase (50 %)*: drei Klone. Der echte schlägt **hörbar** im Takt des Sterns (positionaler Herzschlag), die Klone flackern minimal aus dem Takt. Falscher Treffer: Klon zerspringt mit Strafwelle, alle tauschen per Blink.
* *Tod*: er sackt zusammen, Stille, Gravitationsstrahl, Streckung und Zerlegung in den Stern, Wackeln, Weißblitz, Stille, Herzschlag.

**Zwischenspiel (≈16 s).** Die Schalen des Sterns reißen entlang leuchtender Nähte, der Himmel kippt pro Spieler in Nacht,
die vier Klingen steigen aus der Tiefe und werden zu den Ringen der Armillarsphäre. HELIOS.

**Akt 2 – Helios.**
* *I · Korona (100–60 %, 100 BPM)*: Portal-Strahlen, Plasma-Ringe (bernstein = springen, cyan = ducken), Flares mit Bodenmarkierung, Sonnenwind.
* *II · Zerfall (60–25 %)*: der äußere Ring verglüht. Meteor-Regen aus seinen Trümmern, Geometrie-Käfige (Ikosaeder, Balkenkreuze), **Seismische Bomben** (Heavy-Core-Bombe, pulsierender Kern, gedehnter Ton, Aussetzer, flache Schockwelle, Risse; Springen weicht aus). Bei 40 % reißt eine Supernova-Welle Ring B in Trümmerinseln.
* *III · Singularität (< 25 %, 80 BPM)*: Schwarzes Loch mit Akkretionsscheibe, Sog mit Drall, schrumpfender Ereignishorizont, Zeitdilatation im Ton.
* *Finale (≤ 6 %)*: Stille. Dann **Requiem**: 32 Takte, alle Attacken zusammengeschnitten auf die Musik. Wer überlebt, bekommt das Fenster auf das nackte Herz.
* *Tod*: Zeitlupe, Kollaps, Aufblähen, Supernova (Weißblitz, flache Schockwellenringe, Trümmer), Nachleuchten, Morgendämmerung, Restore Ring für Ring aus Licht.

**Belohnung.** Ein Leuchtpunkt bleibt. Aus ihm gleitet für jeden Teilnehmer eine eigene Sternensaat-Kapsel herab
(mit Namen, nur vom Besitzer zu öffnen). Loot über `LootService.grantToChest`, also exakt euer bestehendes System.

## 5. Grenzen (ehrlich)

| Wunsch | Vanilla-Plugin | Mit Resourcepack |
|---|---|---|
| Himmel pro Spieler | Zeit, Wetter, Bossbar-Flags `DARKEN_SCREEN`/`CREATE_WORLD_FOG`, Darkness | echte Himmelsfarbe nur per Datapack-Biom (pro Welt, nicht pro Spieler) oder Shader |
| Weißblitz | invertierter weißer Display-Würfel nur für den Spieler sichtbar (negativer Scale) | Vollbild-Glyph (Font) mit Alpha-Fade, deutlich sauberer |
| Kamera-Wackeln | Hurt-Animation (`sendHurtAnimation`), kein freies Kamera-Shake | nur per Shader/Mod |
| Kippende, begehbare Plattformen | Kippen ist Inszenierung (Blöcke → Display-Replik → Animation → zurück); währenddessen ist der Boden weg | nicht lösbar ohne Mods; bewegte Kollision ginge nur mit Shulker-Tricks (ruckelt, bewusst nicht genutzt) |
| Bossbar mit Phasenmarkern | `NOTCHED_20` (5-%-Kerben) + Marker-Glyphen im Titel | pixelgenaue Marker über Font mit Negativ-Spacing |
| Eigene Sounds | Vanilla-Layering mit Pitch-Kurven (Standard) | jeder Cue ist per Config auf `helios:*`-Sounds umstellbar |
| Rüstung/Waffe-Optik | CustomModelData + vorhandene Pipeline | Texturen/Modelle nötig (Blockbench-Vorbereitung liegt bei) |

## 6. Bauplan

1. Engine-Hook `BossScript` + Grundgerüst (Welt, Slots, Journal, Arena, Encounter, Guard, Befehl, Config, Bars)
2. Stern-Rendering, Arena-Ringe
3. Akt 1 (Rig, Attacken, Spiegelphase, Tod)
4. Zwischenspiel
5. Akt 2 (Rig, Attacken, Zerfall, Singularität, Requiem, Supernova)
6. Belohnung
7. Optional: Rüstungsset + Waffe (AetherionItems) und Resourcepack-Gerüst

Nicht im Spiel testbar in dieser Umgebung: Alles ist kompiliert, aber Timings, Blickwinkel und Display-Last
müssen auf dem Server abgenommen werden (Checkliste am Ende des Ergebnis-Dokuments).
