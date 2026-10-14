# Opus prompt — seamless network: MMO-C + MMO-I ↔ MMO-R / MMO-D

Copy everything below the line into a new Opus chat.

---

```text
Aufgabe: Das Aetherion-Netzwerk so verdrahten, dass MMO-C und MMO-I genauso smooth an MMO-R (und das bestehende MMO-D-Muster) angebunden sind wie MMO-D heute schon an MMO-R. Spieler merken keinen Serverwechsel. Backend bombensicher.

## Rollen (FIXED)
| Velocity | Port | Rolle |
|----------|------|--------|
| hub | 25566 | Hub |
| mmo-r | 25567 | Main world, Capital, Skill-Isles, Bosses |
| mmo-d | 25568 | Dungeons ONLY (bleibt exklusiv) |
| mmo-c | 25569 | Exile (aetherion_exile) — Story/Ferry |
| mmo-i | 25570 | Private + Guild islands ONLY (blank shell already provisioned) |

MMO-I Crafty id (live): d57d33a4-ee99-4b94-89de-cd676e548513
Pfad: /var/opt/minecraft/crafty/servers/d57d33a4-ee99-4b94-89de-cd676e548513
auto_start=0, plugins/ leer, paper.jar + Velocity-forwarder-Config vom Donor. Velocity.toml hat bereits `mmo-i = "127.0.0.1:25570"` (Proxy ggf. reload/restart nötig).

## Was Robb will
- Ein super smoothes Netzwerk. Spieler wissen nie, dass sie wirklich den Server verlassen haben.
- Vorbild: MMO-D ↔ MMO-R (z.B. /capital von D → Snapshot + Velocity Connect → Ankunft auf R am richtigen Spawn, Inventar intakt).
- MMO-C bleibt Exil (nicht Inseln). MMO-I ist nur Spieler-/Gildeninseln. Keine Kopie von Exile auf I.
- Dungeons bleiben NUR auf MMO-D. Niemals AetherionDungeons-Instancing auf C oder I.
- ADDITIV. LOCKED Systeme nicht anfassen (Ranks/player-ranks.yml, Boosters, Anvil GUI, Signature Weapons, Talk UX, ShutdownCountdown).

## Ist-Zustand
- Transfer: AetherionCore TransferSnapshotStore v5 + shared `/var/opt/minecraft/crafty/shared/transfer`
- MainWorldLink.handoff(player, spawnId) für Non-Main → mmo-r
- ExileLink / ExileHost / ExileArrival auf mmo-c (Core)
- AetherionGuilds Inselwelten heute oft noch auf mmo-r (und MMO-C hat historisch aether_islands/aether_guilds Ordner — aufräumen/migrieren nach mmo-i, nicht doppelt tickend auf R)
- Ops: aetherion-ops `ae.ps1` / remote ae-*.sh — Aliase mmoi|mmo-i, mmoc, mmod, mmor sind vorgesehen

## Deliverable
1. Audit live Plugin-Listen: hub / mmo-r / mmo-d / mmo-c / mmo-i.
2. Plugin-Parity-Pläne:
   - mmo-c: voller Gameplay-Stack wie mmo-r MINUS Dungeons; ExileHost/Cutscene behalten.
   - mmo-i: Stack der für Inseln nötig ist (mind. Core, Guilds, Items, + was Quarry/Minions brauchen). KEIN Dungeons. Keine Main-Overworld-Last. Inselwelten (aether_islands / aether_guilds) leben NUR hier.
3. Routing wie R↔D:
   - Von C oder I: /capital, Hub-Warps, „gehört auf Main“ → handoff nach mmo-r.
   - Von R: /exile → mmo-c; /island (o.ä.) → mmo-i.
   - Inventar/Progress über shared transfer, Quit mid-hop safe. Ränge nie wipen.
4. Migration: bestehende Insel-Welten/Daten von mmo-r (und ggf. Altlast auf mmo-c) → mmo-i ohne Datenverlust. Danach Insel-Tick auf mmo-r AUS.
5. Proxy: velocity.toml bereits mit mmo-i; Reload/Restart dokumentieren. Core ServerNames um ISLANDS=mmo-i erweitern (Port 25570).
6. Smoke-Test für Robb: Hub→R, R→D→R (/capital), R→C (/exile)→R, R→I (/island)→R, Inventar, Quest-State, Relog mid-transfer, Tod auf I/C.
7. Deploy nur wenn Robb sagt. Sonst Ship-Ordner + Apply-Scripts (Hash-Check), Restart-Liste klar (meist mmo-c, mmo-i, ggf. mmo-r/Core, Proxy reload).

## Nicht tun
- Exile-Map auf mmo-i legen.
- Dungeons auf C oder I.
- MMO-R Features droppen um C/I zu füttern (additive ship).
- LOCKED Rank/Wipe/Weapon/Talk/Shutdown anfassen.

## Erfolg
Spieler wechselt R ↔ C ↔ I ↔ D und merkt nur Inhalt/Flavor, keinen „Server-Hop“. Insel-Produktion lastet mmo-i, nicht Capital/Skill-Isles auf mmo-r. Exil bleibt mmo-c.
```
