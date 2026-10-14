# Opus Prompt — Aetherion Combat System Elevate

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Target:** Paper **1.21.1**, Java 21, resource pack `pack_format: 34`.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md) — edit the real tree, no ship folders, no deploy/restart/commit unless Robb asks.  
**Quota:** Claude Project free balance / Ultrathink. Go deep. One mission only: **combat elevation**.

---

## Mission (one sentence)

Turn Aetherion player combat from **left-click spam** into a **readable, high-skill action loop** — dash / roll / parry (or better equivalents), ability windows, and **sick hit feedback** — using only Paper plugins + the existing pack, without nuking LOCKED systems or the current damage/stat pipeline.

---

## Why this exists

Today Aetherion already has:

- Custom **DAMAGE / DEFENSE / CRIT / HP** via `DamageListener` + `ActiveEquipmentStats`
- Per-weapon ability CDs on ActionBar (`AbilityCooldownHud` + glyph `PlayerHud` / `HudGlyphs`)
- One-off weapon actives (Warped Blade, sets, shortbows, wands, …)
- BossEngine / SamuraiBoss / Gnomon with their own hit contracts
- Scripted/raw hit flags in Core (`IncomingHits`, `ScriptedHits`)

What it does **not** have yet: a **universal movement + defense + offense cadence** that every fighter shares — the “this is a real game” layer between vanilla melee and boss spectacle.

You invent and ship that layer. Make it feel closer to a polished action MMO / Soulslike hybrid **that still reads as Minecraft**.

---

## Non-negotiable LOCKED (stop if conflict)

Say `LOCKED … SYSTEM CONFLICT: …` and wait. Do **not** “polish through” these:

| System | Rule |
|--------|------|
| **TalkUx** | In-world NPC speech bubble + reply chips stay. No chat-only rewrite. |
| **Blossom Blade** (`ashen_katana` / AshenKatana*) | Abilities, VFX, sounds, combat feel — **untouched**. May *use* the new shared cadence if it does not change feel/timing. |
| **Gravwell Cleaver** (`gravwell_cleaver` / GravwellCleaver*) | Same LOCKED rule. |
| **Boosters** | Stack 64, lore/tooltip stats, no ID/material rewrite. |
| **Anvil / BoosterSockets** | 14 sockets + vanilla anvil slot 8 — layout locked. |
| **Ranks** | Admin = Robb UUID only; `player-ranks.yml` never wiped; OP ≠ rank. |
| **ShutdownCountdown** | 10→8→6→4→2 — leave alone. |
| **Borderlands vials / rite** | Stackable + altar behavior unless Robb asked. |
| **Additive ship** | Extend menus/factories. Never silently drop live content. |

Also: do **not** move game loops into `AetherionCore` beyond shared keys/flags/APIs. Owning plugin stays **AetherionItems** (player combat). Boss plugins keep boss AI.

---

## Design north star

### Feel

1. **Every fight has a rhythm** — approach → commit → punish → reset. Not hold LMB.
2. **Defense is active** — parry / perfect guard / roll i-frames (pick a coherent kit; don’t ship five overlapping invuln systems).
3. **Mobility is a resource** — dash/roll costs stamina or charge; spam has clear fail (whiff, longer CD, vulnerability).
4. **Offense has weight** — light / heavy (or charged) distinction; telegraphs on big hits; crits and big numbers **pop**.
5. **Feedback is loud and fair** — screen punch, sound, particles, damage numbers, ghost HP, hitstop (short), directional indicators. Never spam so hard that TPS dies or epilepsy-disco.
6. **Readable for 1–8 players** — works in Borderlands trash, dungeon trash, and open-world elites. Bosses may *hook* into the same player kit; do not rewrite BossEngine AI in this run.

### Inputs (Paper-realistic — no Fabric client mod required)

Prefer bindings that work for **every** player on join (server pack OK):

| Input | Typical use |
|-------|-------------|
| Sprint + jump / double-tap sprint | Dash |
| Sneak + jump or sneak double-tap | Roll / dodge |
| Swap hands / drop (cancel drop) / shield interact | Parry / guard |
| Hold right-click / charge | Heavy / focus |
| Existing weapon right-click actives | Stay — compose with the new kit |

Document the final binding map in code comments + a short in-game tip the **first time** a player enters combat (not a novel).

If a binding fights vanilla hard, pick the less-broken option and gate with config.

### Soft-fail allowed

- No Perfect-Parry on every mob day-one if boss scripts need a follow-up — but **trash mobs + a test dummy** must showcase the full loop.
- Optional config toggles: `combat.system.enabled`, per-move enables, stamina regen, feedback intensity.

---

## Architecture (expected shape — improve if you find better)

Own package under Items, e.g.:

```text
AetherionItems/.../combat/system/
  CombatSystem.java          // enable, tick, register
  CombatConfig.java          // YAML
  Stamina.java / Charge.java // resource
  Mobility.java              // dash + roll
  Guard.java                 // parry / block window
  MeleeCadence.java          // light/heavy, anti-spam, recovery
  HitFeedback.java           // numbers, particles, sounds, hitstop, hurt cam
  CombatListener.java        // Bukkit events
  CombatTutorial.java        // first-fight hints (ActionBarLane-aware)
```

**Integrate, don’t fork:**

| Existing | How to compose |
|----------|----------------|
| `DamageListener` | Keep Aetherion DAMAGE stamp. Cadence may multiply / gate / cancel vanilla spam **before** or **with** that stamp — never invent a second hidden damage economy. |
| `AbilityCooldownHud` / `PlayerHud` / `HudGlyphs` | Show stamina / parry / dash CD on the glyph HUD if slots remain; do not fight ActionBarLane ownership — register as painter or feed fragments. |
| `DamageNumbers` | Elevate; do not delete. Crit / parry / weak-spot variants OK. |
| `IncomingHits` / `ScriptedHits` / Core flags | Boss → player scripted damage must still bypass player mitigation when marked raw/scripted. |
| `AetherEntities.isBoss` / Samurai hitbox | Player kit applies **to** bosses that take EntityDamage; don’t break virtual HP bosses that cancel vanilla (Samurai, BossEngine). |
| Weapon listeners (Warped, sets, bows) | Keep. Shared mobility/guard should not steal their CDs unless designed as global resource. |

**Config:** `AetherionItems/src/main/resources/combat-system.yml` (or section under `config.yml` if cleaner). Hot-reload via existing Items reload if present; else document `/items reload` / plugin reload path you wire.

**Pack:** Only if needed for new glyphs (stamina pip, parry flash). `pack_format: 34`. Keep Manager chrome + existing HUD glyphs. Prefer server-side particles/sounds first; pack only when it earns it.

---

## Must-ship gameplay kit (minimum viable “krank”)

Implement **all** of these unless a LOCKED conflict forces a documented substitute:

1. **Dash** — short burst, CD + resource cost, brief momentum, cancels into attack optionally.
2. **Roll / Dodge** — i-frames window (short, readable), longer recovery if mistimed, direction from look/input.
3. **Parry / Perfect Guard** — timing window; success → attacker stagger / vulnerability / reflect chip (tuned, not OP vs bosses). Fail → normal block or punish.
4. **Melee cadence** — attack cooldown / recovery so raw 10 CPS does less than timed hits; optional heavy/charge.
5. **Hit feedback suite**
   - Punchy hurt/hit sounds (vary by weapon class if cheap)
   - Crit / parry / perfect-dodge distinct audio
   - Damage numbers: crit pop, big-hit scale, color by type
   - Optional micro hitstop (1–2 ticks) on heavy/parry — **cancel-safe**, never freeze bosses mid-script dangerously
   - HP ghost bar already exists — lean into it on player hurt
6. **Anti-cheese** — no infinite i-frame chains; no dash through void cheese without existing fall protection rules; creative/spectator ignored.
7. **Test hook** — admin command e.g. `/combat dummy` or DevMenu tile: spawn a training dummy that telegraphs attacks so parry/roll can be practiced.

---

## Explicitly out of scope (this run)

- New story quests, Exile map, Hub rebuild, island factory
- Rewriting BossEngine directors / Seraphine / Helios / World Eater AI
- New full class system / talent trees (hooks OK, trees later)
- Client Fabric/Iris-required shaders as a hard dependency
- PvP balance pass (design PvE-first; PvP may reuse kit but do not spend the session on duel ladder)

---

## Quality bar (“ultracode”)

- Smooth at 20 TPS with 10 fighters in Borderlands
- No ActionBar flicker wars (compose with `ActionBarLane` / `PlayerHud`)
- No silent fails — denied dash/parry = short deny pulse (HUD already has deny pattern)
- Code matches Items style: clear packages, little ceremony, config-driven numbers
- German/English player-facing strings via existing lang patterns if the project has them; else clean English with DE mirror where Items already mirrors

---

## Read first (then code — do not re-audit the universe)

1. This file  
2. `docs/OPUS_PROMPT_HYGIENE.md`  
3. `AetherionItems/.../listener/DamageListener.java` (damage stamp + reclaim)  
4. `AetherionItems/.../combat/PlayerHud.java`, `HudGlyphs.java`, `AbilityCooldownHud.java`, `DamageNumbers.java`  
5. `AetherionCore/.../combat/IncomingHits.java`, `AetherEntities.java`, `api/ActionBarLane.java`  
6. One representative weapon active: `WarpedBladeListener.java` (CD + AbilityCooldownHud pattern)  
7. Skim `SkillService.java` only for stat hooks — do not gut skills  

---

## Deliverables

1. Working combat system in **AetherionItems** (+ Core only if a tiny shared key/API is required).  
2. `combat-system.yml` (or equivalent) with tuned defaults Robb can feel in 30 seconds.  
3. Pack additions **only if** required for new HUD glyphs — listed in the final note.  
4. Short end note (≤20 lines): bindings, how to test, config knobs, known limits. **No** README novel / ship theater.

---

## Test plan (Robb will run)

- [ ] Fresh join → enter Borderlands trash: dash / roll / parry each fire and feel distinct  
- [ ] Spam click vs timed hits: timed clearly better  
- [ ] Parry success vs fail readable (sound + VFX + maybe stun)  
- [ ] Roll i-frames dodge a telegraph; spam-roll gets punished  
- [ ] HUD: HP/DEF/CD still readable; new resource does not nuke glyph layout  
- [ ] Hit a BossEngine boss + SamuraiBoss hitbox: still damages; no softlock  
- [ ] Blossom Blade / Gravwell: feel unchanged  
- [ ] TalkUx NPC talk still works mid-area  
- [ ] `/combat dummy` (or DevMenu) trains the loop  

---

## Paste into Claude Project (answers to the intake questions)

**Goal:** Elevate PvE combat into a real action system (dash, roll, parry, cadence, hit feedback) on Paper 1.21.1 plugins — spectacle + skill, not click spam.  

**Setup:** Paper 1.21.1 network (Hub / MMO-R / MMO-C / …), custom plugins (`AetherionItems`, `AetherionCore`, `BossEngine`, …), server resource pack `pack_format` 34.  

**Work from:** This monorepo; start at `docs/OPUS_COMBAT_SYSTEM_ELEVATE_PROMPT.md`.  

**Pain now:** Combat is mostly left-click + occasional weapon actives; lacks shared mobility/defense rhythm and premium hit feedback.

---

## Closing order

> Read **only** this prompt + hygiene + the listed files. Design then implement the shared combat kit in AetherionItems. Ultracode. Additive. LOCKED stays locked. No deploy. When the loop feels sick on a training dummy and Borderlands trash, stop and write the ≤20-line note.

**Go.**
