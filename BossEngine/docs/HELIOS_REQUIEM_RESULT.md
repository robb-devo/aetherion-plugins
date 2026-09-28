# Helios Requiem: Ergebnis

Stand: Branch `claude/confident-bell-u0mcjv`, Paper 1.21.1 (CI-Compile grün). **Nicht im Spiel getestet**:
Timings, Blickwinkel und Display-Last müssen auf dem Server abgenommen werden (Checkliste unten).
Architektur und Begründungen: [HELIOS_REQUIEM.md](HELIOS_REQUIEM.md).

## Installation

1. BossEngine bauen und deployen wie gewohnt. Beim ersten Start entstehen
   `plugins/BossEngine/helios.yml`, `bosses/helios_herald.yml`, `bosses/helios_requiem.yml`
   und die Welt `helios_requiem` (Void, eigenes Biom `the_void`).
2. Server, auf dem der Boss läuft: ein Backend (z. B. `mmo-d`). Über Velocity schickt ihr die Gruppe
   dorthin und startet mit `/helios enter` (Befehl, NPC oder Portal-Plugin, das den Befehl ausführt).
3. Loot eintragen: `bosses/helios_requiem.yml` → `loot:` (aktuell Platzhalter mit `random_booster`).

## Befehle

| Befehl | Wer | Wirkung |
|---|---|---|
| `/helios enter` | Spieler (`entry.permission`) | Instanz für dich bzw. deine Party (Leader, Radius `entry.radius`) |
| `/helios leave` | Spieler | Kampf verlassen (zählt als Aufgabe), zurück zum Startpunkt |
| `/helios start <spieler…>` | `helios.admin` | Instanz für genau diese Spieler (Tests ohne Party) |
| `/helios list` | admin | Slots, laufende Instanzen, Builder-Queue |
| `/helios debug [watch]` | admin | Lastbericht; `watch` = Live-Actionbar (Displays, Spawns/Pushes pro Tick, µs/Tick, MSPT) |
| `/helios skip` | admin | Herold stirbt sofort (spielt seinen Tod) → Akt 2 |
| `/helios hp <prozent>` | admin | HP des aktuellen Bosses setzen (Phasen testen: 55, 38, 24, 5) |
| `/helios attack [id]` | admin | Attacke erzwingen; ohne id: Liste |
| `/helios abort [slot\|all]` | admin | Instanz sofort beenden, alle heim, Slot leeren |
| `/helios restore <slot>` | admin | Slot-Box komplett leeren (Reparatur) |
| `/helios tp <slot>` | admin | Slot von oben ansehen |
| `/helios reload` | admin | `helios.yml` neu lesen (laufende Kämpfe behalten ihren Stand) |

`helios.bypass`: ausgenommen von Perlen/Flug/Elytra/Block-Sperren (Staff).

Attack-IDs: Akt 1 `blink blink3 swarm spiral fan pincer cage platform lance`,
Akt 2 `portals portals5 plasma flares wind meteors icosa cross seismic burn shatter`.

## Was wo passiert

| Datei | Inhalt |
|---|---|
| `instance/BossScript(s).java` | generischer Hook: gescriptete Körper, Loot-Übergabe, HUD-Opt-out, Treffer-Umformung |
| `helios/HeliosModule` | Bootstrap, Welt, Recovery, Tick, Entry |
| `helios/HeliosGuard` | Perle/Chorus/Wind Charge/Elytra/Fly/Blöcke, Cutscene-Schutz, Tod → Echo, Quit/Join/Weltwechsel |
| `helios/encounter/HeliosEncounter` | Zustandsmaschine, Treffer mit i-Frames, Void-Rettung, Enrage, Wipe, Close |
| `helios/world/*` | Layout (= Snapshot), Builder, Journal, Live-Arena mit Repliken |
| `helios/core/*` | Bühne + Budget, Tempo, Score, Kamera, Himmel, Risse, Formen |
| `helios/star/DyingStar` | der Stern |
| `helios/herald/*` | Akt 1 |
| `helios/requiem/*` | Akt 2, Singularität, Requiem |
| `helios/reward/*` | Sternensaat-Reliquiar, Nachlieferung |

## Fail-Safes (umgesetzt)

* **Crash**: `helios/state.yml` (atomar). Beim Start wird jeder nicht freie Slot komplett geleert.
  Spieler tragen Rückkehrort/Gamemode/Flug in ihrer eigenen PDC; beim nächsten Login geht es heim.
* **Disconnect**: sofort heim (solange das Spielerobjekt noch gültig ist); Instanz schließt, wenn niemand mehr da ist.
* **Fremder Teleport** (`/spawn`, `/hub` …) oder Weltwechsel = Verlassen, sauber zurückgesetzt.
* **Tod**: Echo (Zuschauer, an die Arena gebunden); bei Sieg zurück in Survival zum Abholen der Kapsel.
* **Void**: `RESCUE` zieht zurück auf den nächsten festen Boden (harter Treffer), `DEATH` optional.
* **Cutscenes**: alle Spieler und der Boss unverwundbar, keine Attacken.
* **Loot**: nie verloren. Offline-Anteile landen in `helios/pending.yml` und werden beim Login geliefert.

## Performance

* Budget pro Instanz: `performance.display-cap` (420), `spawns-per-tick` (48). Kosmetische Teile werden
  bei vollem Tick übersprungen, tragende (Körper, Telegraphs) nie. Qualitätsstufe sinkt automatisch über
  `degrade-mspt` und erholt sich nach 30 s Ruhe.
* Erwartete Spitzen (geschätzt, bitte mit `/helios debug watch` messen):
  Herold ~50 Displays, Spiegelphase ~200; Helios ~85 + Stern ~50 + Singularität ~60 + Attacken ~80–150.
* Der teuerste Moment ist die Spiegelphase (4 Rigs); Klone rendern deshalb nur jeden zweiten Tick.

## Resourcepack (optional)

Ohne Pack funktioniert alles (Vanilla-Sound-Layering). Jeder Vanilla-Sound, den der Kampf nutzt, lässt
sich in `helios.yml → sounds.overrides` auf einen eigenen Sound umbiegen, z. B.:

```yaml
sounds:
  overrides:
    entity.warden.heartbeat: helios:heartbeat
    entity.warden.sonic_boom: helios:seismic_boom
    block.beacon.ambient: helios:star_hum
```

Gerüst: `BossEngine/resourcepack-helios/` (sounds.json mit den vorgesehenen Keys; `.ogg` liefert ihr).
Nicht umgesetzt (braucht ein Pack und ist bewusst offen): Vollbild-Weißblitz per Font-Glyph,
pixelgenaue Phasenmarker in der Bossbar, eigene Himmelsfarbe.

## Rüstung & Waffe: bewusst noch nicht gebaut

`docs/OPUS_POLISH_PASS_AUDIT.md` nennt „bloating `CustomItem`“ als Freeze-Gate. Deshalb liegt hier der
Entwurf, nicht der Code; auf euer OK baue ich es nach dem Muster von Worldhide/Worldbite:

* **Set „Requiem des Herolds“** (4 Teile, Netherite-Basis, CMD 4201–4204):
  2 Teile: +8 % Schaden auf dem Takt (ein Schlag innerhalb 150 ms nach einem 1-Sekunden-Puls der Rüstung);
  4 Teile: *Letzter Akkord*: der fünfte Treffer in Folge ruft eine flache Lichtwelle (kleiner Radius).
* **Waffe „Heliosklinge“** (Schwert, CMD 4205): Rechtsklick = *Blink* (kurzer Sprung hinter das Ziel,
  Lichtlinie als Tell, 8 s CD). Lore: „Die vierte Klinge. Sie kehrte nicht zurück.“
* Blockbench: Klinge als flaches Modell (Quarz + Goldschneide), Rüstung per CIT oder Trims wie bestehende Sets.

## Abnahme-Checkliste (auf dem Server)

1. `/helios start <du>` → Arena baut sich in wenigen Ticks, Schwarzblende, Intro (≈12 s), Bossbar füllt sich.
2. Herold: jede Attacke per `/helios attack <id>` einzeln ansehen; Tells lesbar? Treffer fair?
3. `/helios hp 51` → Spiegelphase: ist der positionale Herzschlag hörbar? Falschen Klon schlagen → Nova + Tausch.
4. `/helios skip` → Tod mit Gravitationsstrahl, Stille, Herzschlag, Zwischenspiel, Titel HELIOS.
5. Akt 2: `/helios hp 59` (Korona verglüht, echter Boden weg), `/helios hp 39` (Inseln), `/helios hp 24`
   (Singularität: Sog, Linse, Pitch sinkt nahe dem Loch), `/helios hp 5` (Requiem, dann Herzfenster).
6. Tod: Zeitlupe, Supernova, Morgendämmerung, Arena baut sich aus Licht wieder auf, Kapseln gleiten herab,
   nur der Besitzer kann seine öffnen.
7. Nach Ende: Slot leer (`/helios list` = FREE), niemand hängt in der Helios-Welt.
8. Crash-Test: während Akt 2 den Server hart beenden → Neustart: Slot wird geleert, Spieler landen beim Login daheim.
9. Last: `/helios debug watch` in Spiegelphase, Singularität und Requiem; Displays < Cap, MSPT stabil.
10. Weißblitz (negativ skalierter Würfel) auf verschiedenen Clients prüfen; falls er nicht rendert, ist es ein
    Einzeiler auf reine Blindheit/Darkness umzustellen.

## Balance-Startwerte

HP: Herold 26 000, Helios 52 000 (+45 % je weiterem Spieler), Enrage 7 min / 13 min. Schaden als
BossHits-„power“ je Attacke in `helios.yml`. Requiem-Fenster 12 s mit ×2,5; hält das Herz, spielen die
letzten acht Takte erneut.
