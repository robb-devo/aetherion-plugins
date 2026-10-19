# Aetherion — Open-Beta-Audit (2026-10-19)

Senior-Dev-Sicht auf Code, Builds und Spielstand. Ist-Zustand siehe [PROJECT_STATE.md](PROJECT_STATE.md).
Betrieb, Hosting und Sicherheit stehen bewusst nicht in diesem öffentlichen Repo, sondern privat in
`aetherion-ops/docs/BETA_AUDIT_OPS_2026-10-19.md`.

**Regel für die Beta: keine neuen Systeme. P0 und P1 abarbeiten, dann öffnen.**

---

## P0 — Blocker

| # | Problem | Fix |
|---|---|---|
| 0.1 | mmo-r stoppte am 18.10. per Watchdog: Der tägliche Veins-Reset (AetherionMining) löscht `aether_veins` und fügt Amethyst Mines synchron per FAWE ein (6 s Main-Thread), danach hing das Chunk-Entladen | Kurzfristig mehr Heap (Ops). Code: Reset asynchron/gestückelt, Chunks vorher entladen, Reset in Wartungsfenster legen |
| 0.2 | Live-Code war nicht in Git (HUD-Panel, Radar, Kampfsystem) | Erledigt mit diesem Commit; `main` = Live |
| 0.3 | Doppelte Jars auf mmo-d: `AetherionDungeons.jar` neben `AetherionDungeons-1.0.0.jar`, dazu alte `AetherionFarming.jar`, `AetherionHub.jar`, `AetherMobs.jar` | Je Plugin genau eine Datei `<Name>-1.0.0.jar` |

## P1 — Vor dem Öffnen

### Gleicher Build überall
- Sammelinseln auf mmo-c sind vom 10-03 bis 10-06, auf mmo-r vom 10-15. Gehören nur auf mmo-r, also auf mmo-c entfernen oder angleichen.
- Farming/Fishing auf mmo-d (nur Dungeons) entfernen.
- Hub-Jar auf hub ist vom 10-05, auf mmo-r vom 10-15: angleichen.
- AetherMobs auf mmo-c/mmo-i (ohne Pets) und mmo-d auf den mmo-r-Build bringen.
- Guilds und Beta: mmo-d weicht ab.
- StressBots für die Beta von mmo-r und mmo-d entfernen.
- Ein Parity-Skript (Soll-Matrix → sha1-Vergleich → Deploy) macht diese Liste künftig überflüssig.

### Spielfluss (einmal komplett mit frischem Account)
- Harbour → Capital → Stempel → alle vier Insel-Lektionen → erster Dungeon → Exil-Überfahrt → Insel.
- `CombatTeach` ist ein Stub, obwohl das Kampfsystem live ist: Tutorial-Beat füllen oder rausnehmen.
- Homie-Backlog P0: Quest-Annahme-UI kaputt und unsichtbares Wasser. Vermutlich neuere Clients über ViaVersion, also nur 1.21.1 zulassen oder das Pack für neuere Versionen testen.
- Foreman „32 Erze“: Text auf „Sammle 32 Erz“ ändern.
- Booster müssen beim Item-Upgrade drauf bleiben (Robb-Wunsch, wird gerade umgesetzt).
- 18 Quests-Zeilen ohne Deutsch nachziehen.
- `DUPING_CHECKLIST.md`: alle „Manual QA“-Punkte abhaken. Dupes zerstören eine Beta-Wirtschaft.

### Beta-Rahmen
- Geschlossene oder offene Beta entscheiden, Wipe-Plan kommunizieren, Bug-Report-Weg (Discord + `/report`).

## P2 — Nach dem Start

### Codequalität
- ~470k Zeilen Java, **4 Tests**. Zuerst Tests für Geld- und Item-Transfer (`InventoryTransfer`, Auktion, Trade) und Quest-Fortschritt.
- `AetherionItems` hat 142k Zeilen: HUD, Kampf und Wirtschaft mittelfristig trennen.
- CI kompiliert nur. Jars als CI-Artefakt bauen, damit Deploys reproduzierbar sind.

### Repo-Hygiene
- 29 Worktrees, 14 davon mit offenen Änderungen: fertige löschen, offene committen.
- Root-Checkout `IdeaProjects/` (09-28, ~6000 Dateien) nicht mehr benutzen.
- ~30 `origin/backup/2026-10-02/*` Branches nach Sichtung aufräumen.

---

## Definition „Beta-bereit“
1. Alle Server laufen stabil, Ausfälle werden automatisch behoben und gemeldet.
2. Welten werden regelmäßig gesichert, ein Restore wurde getestet.
3. Jar-Matrix überall gleich, keine Duplikate, keine StressBots.
4. `main` = Live-Stand.
5. Ein frischer Account kommt ohne Admin-Hilfe von Harbour bis zum ersten Dungeon.
6. Duping-Checkliste abgehakt.
