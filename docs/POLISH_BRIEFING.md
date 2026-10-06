# AETHERION — Full Polish Briefing

Date: 2026-10-03  
Author: session notes for Robb (A3therion)  
Worktree: `mining-eldervale-progression-65660c` / branch `ship/live-hardening`  
Status: **ready to implement** after Robb’s progression look-over

---

## 0. Where we are (context, not polish)

### Live now on MMO-R
- **Progression P0–P6** shipped (Signatures, T4/T5 recipe links, dungeon relic bridge, curve levers, Summons, teaching quests).
- Economy.yml bumped to **v3** (backup `.v2.bak`).
- Boss YAMLs for Colossus / Helios / Hollow Sun / Insolvent / Balthazar / World Eater overwritten on disk.
- Opening orbit gated to **`mmo-c` only** (not MMO-R first-join).
- Ankerhold on **MMO-C** sits in a **void** world + v2 paste.
- Gameplay 1–4 earlier: Forager chop teach, vanilla station/breeding blocks, Aetherion→vanilla uncraft block, world chests always open.

### Still open (Opus left for Robb — not polish)
1. Booster carry T3→T5 craft (one-line; LOCKED area — ask before touch)
2. Keep +16% rarity bonus on infusable boss sets vs only-when-cored
3. Crit soft-cap on ranged too? (never Blossom Blade)
4. Disable `aetherion_home` auto-respawn so Summons is the gate
5. **P5 boss HP** — plan only until fight-log has real data

### Parallel rule
Progression is live. Polish is a **separate pass**: additive, TalkUx shell LOCKED, no surprise wipes of menus.

---

## 1. Polish backlog (Robb feedback 2026-10-03)

Everything below is from your playtest notes. Ordered for **ship order** (feel → onboarding → daily UX → safety → content).

### P-A — TalkUx: einheitliches Zeilen-Timing  ★ first
**Problem:** Manche NPCs reden extrem schnell; Abstände nicht einheitlich — auch **mitten** im Dialog, nicht nur am Start.  
**Constraint:** In-world bubble / TalkUx shell bleibt **LOCKED**. Nur Timing.  
**Heute im Code:** `TalkUx` setzt `cps = max(1.1, total/32)` pro Zeile → kurze Zeilen knallen durch.  
**Soll:**
- Fester Char-Pace (gleiche Lesegeschwindigkeit für jede Zeile)
- Fester Gap **zwischen** fertiger Zeile und nächster (überall im Dialog)
- Per-NPC Voice darf Pitch/Blip behalten, aber nicht die Lesegeschwindigkeit sprengen

**Effort:** S–M · **Impact:** sehr hoch · **Risk:** niedrig

---

### P-B — Farmer → Harlow (hinterm Barn) + Dialoge
**Problem:** Farm-„Portal-Welt“-Framing ist tot. Flow muss auf die Farming Island zeigen.  
**Soll:**
- **Farmer:** minimal umplatzieren / Dialog anpassen → verweist auf **Harlow hinter dem Barn**
- **Harlow:** hinter dem Barn; erklärt Farming Island („Weg runter“); gibt **Pfeile** + **Hint/Waypoint** dorthin
- Kein TalkUx-Rewrite — Text + Spot + QuestHint/Compass

**Code touch:** LivingNpc spots / lang packs / `QuestHint` / ggf. `NpcListener` / `farm_isle_guide` (Harlow = vermutlich Display-Name oder Spot-Rename — vor Move live-ID checken)

**Effort:** S · **Impact:** hoch (Onboarding) · **Risk:** niedrig

---

### P-C — Ledger → Tackle → Fishing-Isle Waypoint
**Problem:** Nach Tutorial schickt Ledger zu Tackle; Tackle soll wie Harlow die Fishing Island pitchen + Marker setzen.  
**Soll:**
- Tackle (`fisher`) Dialog: Fishing Island kurz erklären
- Gelbe Arrow / QuestHint / Compass-Ziel zum Fishing-Pad / Isle-Entry (analog Harlow-Pattern)

**Effort:** S · **Impact:** hoch · **Risk:** niedrig

---

### P-D — Recipe Book: Combat + Mining wie Farming/Forage/Fish
**Problem:**
- Combat/Mining sollen Layout wie die Skill-Leitern: **Rüstung oben→unten**, **Tool neben dem Helm**, nächstes Tier = nächste Spalte
- Mining-Tools liegen aktuell **eine Reihe voll**, dann neue Seite — soll **weitere Zeilen** nutzen, weniger Pages

**Code:** `RecipeBookGUI` / `RecipeBookLayout` / Gear-Grouping (`ARMOR_SETS_PER_PAGE`, `RECIPE_SLOTS`)

**Effort:** M · **Impact:** mittel–hoch (täglich) · **Risk:** niedrig (Presentation; Clicks/Unlocks prüfen)

---

### P-E — Monster nur in Borderlands
**Problem:** Hostiles (bes. T2/T3) sollen **außerhalb** Borderlands nicht spawnen.  
**Soll:**
- Borderlands = Kampfzone
- Rest der Hub-/Overworld: keine T2/T3 (ideal: keine Vanilla/Custom Hostiles außer BL / bewusste Arenen)

**Audit:** Items `AreaZone` / Borderlands profile, WorldGuard, custom spawners, Aethermobs (pets dürfen nicht sterben), Quest-Wildlife

**Effort:** M · **Impact:** hoch (Feel + Balance) · **Risk:** mittel (Spawn-Pfade nicht kaputtmachen)

---

### P-F — Skill-Isle Guide-NPCs (Ledger-Style)
**Problem:** Farming-Pad-Guide funktioniert, wirkt aber „alt“ vs Bubble/Chip-UX. Jede Skill-Insel braucht mindestens einen kleinen Power-Guide.  
**Soll:**
- TalkUx bubble + reply chips (wie Miss Ledger Desk Feel)
- Kurz: Insel erklären + Fragen zu Themen (Ernten / Pads / Shops / …)
- Template einmal (Farm), dann Forage / Fish / Mine

**Effort:** L · **Impact:** sehr hoch langfristig · **Risk:** mittel (Content-Pass; nicht TalkUx-Shell anfassen)

---

## 2. Empfohlene Implementierungs-Reihenfolge

| # | ID | Warum zuerst |
|---|-----|----------------|
| 1 | **P-A** Talk timing | Sofort spürbar bei jedem NPC |
| 2 | **P-B** Farmer→Harlow | Onboarding Farm klar |
| 3 | **P-C** Tackle→Fish waypoint | Onboarding Fish klar |
| 4 | **P-D** Recipe book layout | Täglicher Manager |
| 5 | **P-E** Borderlands-only mobs | Welt-Feel / Safety |
| 6 | **P-F** Isle guides | Größerer Content-Block (Template first) |

Ship **einzeln** (jar + restart MMO-R pro Point oder gebündelt A+B+C wenn du willst).  
Regel: nach jedem Point kurze Bestätigung von dir, dann weiter.

---

## 3. Was wir bewusst NICHT in diesen Polish-Pass stecken

- Progression curve / P5 HP (Telemetrie zuerst)
- Opus-Entscheidungen 1–4 (Booster-Carry, +16%, ranged crit, aetherion_home)
- Opening Orbit auf MMO-R freischalten
- Ankerhold → Hub cutover
- Blossom Blade / Gravwell / Booster-Socket-GUI / Ranks / Shutdown countdown
- TalkUx Architektur (Bubble weg, nur Chest, etc.)

---

## 4. Dein „richtig durchzocken“-Plan

1. Polish Points **A→F** (oder zumindest A–E) live  
2. Du **frisch starten** / Wipe nur wenn du willst — Special ranks bleiben (LOCKED)  
3. Einmal Full Run: Harbour → Tutorial → Skills → Isles → mit neuer Curve + Polish  
4. Feedback → Hotfixes

Erwartung: Onboarding klarer, Dialoge lesbar, Recipe Book nicht nervig, Borderlands = Combat, Isles erklärt, Progression-Links (Signatures/T4+) spürbar.

---

## 5. „Weißt du noch alles?“ — Kurz-Check ja

| Track | Stand |
|-------|--------|
| Restart-Regel | Nach Patch restart; nicht auf Done hängen |
| Orbit | nur mmo-c; MMO-C Ankerhold = void |
| Progression | P0–P6 live auf MMO-R |
| Polish | diese 6 Points, Reihenfolge oben |
| LOCKED | TalkUx shell, Boosters intent, Signature weapons, Ranks/Wipes, Shutdown |

### Opus handoff (ready)

Creative implement pass: paste boot from  
[`docs/OPUS_POLISH_A_F_PROMPT.md`](./OPUS_POLISH_A_F_PROMPT.md)  
(live coords + allowlist + LOCKED already inlined).

After Opus lands code: Composer merge/compile/deploy/restart MMO-R (unless `-NoRestart`).

Wenn du „GO Polish solo“ sagst (ohne Opus): Start mit **P-A Talk-Timing**.
