OPUS — Personal Island / Factory: VOLLGAS

Du baust die Personal Island / Factory in AetherionGuilds neu, auf AAA-Niveau: Aetherion + Satisfactory. So gut, dass es allein den Server tragen könnte. Volle Freiheit. Eskaliere bei Feel, Klarheit, Sucht-Loop. Feature-Anzahl ist egal — Gefühl zählt. Wenn etwas Müll ist, wirf es weg und bau Besseres.

Kein Warten auf OK. Keine Zwischenfragen an Robb. Keine „Pass-Pläne zum Abnicken“. Du analysierst kurz im Kopf, dann baust du durch bis es sich brutal geil anfühlt. Am Ende: kompiliert, lauffähig, rückwärtskompatibel zu bestehenden Inseln, deploybar.

Arbeitstree: ship/live-hardening (nicht main).

═══════════════════════════════════════
WAS ES SEIN SOLL
═══════════════════════════════════════

Satisfactory-Feel in Aetherion:

• EIN Bau-Item in der Hand. Damit wählst du, platzierst, drehst, entfernst.
• Freischaltungen (Blueprints): erst freischalten, dann mit Ressourcen bauen.
• HUB = Upgrade-Zentrale / Stufen / nächste Freischaltung. Nicht ein Müllhaufen aus Menüs.
• Alles als Custom Props. Keine Schematics als Look. Maschinen, Bänder, Lager, Hub, Steinbruch — Props.
• GUIs wirklich clean. Ein Job pro Screen. Keine Textwände. Keine Fachwörter.
• Steinbruch (Quarry) behalten und härter machen — der Teil ist schon cool, soll unfassbar aussehen.
• Kein Hand-Craften von Zwischenkram — Maschinen machen das.
• Onboarding in der Welt: jemand ohne Vorkenntnis checkt in Minuten, was zu tun ist. TalkUx darf genutzt werden, aber TalkUx selbst NICHT umbauen.

Sprache überall (UI, Chat, Props, Tipps):
einfache Wörter. Jeder Freund versteht es sofort.
VERBOTEN / ersetzen: Ledger, Reliquary, Footprint, Atomic, Migration (im Spielertext), Pipeline, Buffer, Sink, Cap (als Wort), Workshop-Jargon.
STATTDESSEN z.B.: Lager, Gürtel/Band, Maschine, Hub, Freischaltung, Stufe, Kosten, Vorrat, Steinbruch, Bau-Werkzeug, Status (grün/gelb/rot).

═══════════════════════════════════════
WAS HEUTE FALSCH IST (Wahrheit)
═══════════════════════════════════════

Jetzt: Workshop-Pult → Menüs → Crafting → Platzieren. Zu viele Klicks. Wirkt ambitioniert, ist nicht durchdacht. Storage und Hub fühlen sich zu, unsauber, unerklärt. Ein Neuling versteht nichts. Quarry ist die Ausnahme — den stärker machen, nicht plattwalzen. PropBodies / BeltVisuals / MachineFx existieren schon — darauf aufbauen, nicht von null erfinden und das Gute killen.

═══════════════════════════════════════
ZIEL-FANTASY (Spielablauf)
═══════════════════════════════════════

1) Bau-Werkzeug nehmen
2) Freigeschaltetes Teil wählen (einfache Auswahl, kein Menü-Zirkus)
3) Ghost in der Welt sehen → platzieren → kostet Ressourcen aus dem Insel-Lager / Netz
4) Bänder verbinden sich klar sichtbar, Kisten bewegen sich, Steinbruch füttert Lager
5) Am Hub: nächste Freischaltung / Stufe kaufen
6) Mehr bauen → mehr Durchsatz → geiler Loop

Spielergefühl: „Ich baue eine Fabrik.“ Nicht: „Ich klicke Plugin-Menüs.“

Extra-Ideen (darfst eskalieren):
Diagnose (Items/min, wo’s hakt), Maschinen-Licht grün/gelb/rot, Hub als „nächste Freischaltung“-Tafel, Gilden-Großprojekt, Steinbruch-Module / Overclock, Blueprint-Teilen in der Gilde, Sound/VFX pro Produktions-Takt, Tutorial in der Welt.

═══════════════════════════════════════
HARTE TECH-REGELN (nicht verhandelbar)
═══════════════════════════════════════

SPEICHERN
• Eine Datei pro Insel, Versionsfeld.
• AtomicYaml (de.aetherion.core.persist). Kein neues yaml.save(file) direkt.
• Bestehende Inseln und Struktur-IDs migrieren. Fehler loggen, nicht schlucken.
• Das guilds.yml Full-Rewrite-Problem nicht schlimmer machen.

PERFORMANCE
• Props = Entities. Nur rendern wenn Besitzer/Gäste nah sind. Chunk-Gating.
• Produktion als Rate × Zeit rechnen. Keine Entity pro Item.
• Persistente Displays nie doppelt spawnen.

OFFLINE
• Bewusst entscheiden und kurz im Code/Kommentar festhalten (aktuell: Downtime bis Cap wird gutgeschrieben). Nicht „vergessen“.

WIRTSCHAFT
• Output und Kosten gegen die Hauptprogression. Sinks und Caps.
• Keine Dupes. Remove-before-Credit (wie MarketService).

MULTI-SERVER
• Prüfen wo Inseln laufen (sehr wahrscheinlich mmo-r).
• Beim Serverwechsel dürfen Daten nicht überschrieben / kaputt gehen.

ADDITIV
• Nichts Lebendes löschen ohne strikt besseren, sofort verständlichen Ersatz.
• Quarry-Identität behalten und steigern.

LOCKED — nicht anfassen
TalkUx-Shell (nur nutzen), Signature-Waffen (Blossom Blade / Gravwell Cleaver), Ränge, Booster, Shutdown-Countdown, CoinService-Format. Keine Drive-by-Änderungen an anderen Plugins.

DEPLOY
• mvn package (+ Tests die es gibt) bevor live.
• Vor Deploy: jar-surface-diff — Live-Oberfläche darf nicht schrumpfen.
• Cursor/Deploy-Pfad wie üblich; du lieferst fertige, shipbare Arbeit.

═══════════════════════════════════════
WAS AUF PAPER + RESOURCE PACK GEHT
═══════════════════════════════════════

Bau die beste machbare Variante. Ehrliche Grenze ziehen, dann das Maximum draus:
Ghost-Vorschau, Auswahl am Bau-Item (Scroll/Hotbar/Radial soweit machbar), Rotation, Raycast-Platzieren, Bänder die sich logisch verbinden — alles was Vanilla Paper + Pack hergibt. Was nicht geht: nicht versprechen, Ersatz der sich genauso geil anfühlt.

═══════════════════════════════════════
MESSEN (leicht)
═══════════════════════════════════════

Einfache Zähler/Logs: erste Maschine, erstes Band, erster Steinbruch, erstes Upgrade, Zeit bis dahin. Robb soll in Logs sehen wo Leute abspringen — ohne Code lesen.

═══════════════════════════════════════
LIEFERUNG
═══════════════════════════════════════

Wenn ein großer Block steht: kurz in normaler Sprache was Spieler jetzt anders sehen. Dateiliste. Migrationshinweis. Rollback-Idee. Kein Roman. Kein Warten.

LOS. Koch. Aetherion + Satisfactory. AAA.
