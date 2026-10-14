# Opus Prompt — Exile Crossing Verify + Light Quay Polish

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Target:** Paper **1.21.1**, Java 21. Server language = **English** (German only via `/language de`).  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md) — edit real tree, no ship folders, no deploy/restart/commit unless Robb asks.  
**Sibling (later, not this run):** combat system (Sat), tutorial overhaul, loot-chest scatter, Homie bug list.

---

## Mission (one sentence)

Make the player path **Borderlands end → Veil Quay (ferry islet) → talk Ferryman → board → Exile arrival/cutscene → dock** feel **perfectly smooth and readable** — verify placement, glue missing signs/pathing/NPC lines, light-polish the quay only if something is ugly or broken. **No world rewrite.**

---

## Why this run (context — do not re-audit the universe)

Live state Robb cares about (Oct 2026):

| Piece | Status |
|-------|--------|
| Veil Quay schem | Pasted on **main world** at roughly **`600 51 322`** (`exile.quay.origin` in Core config). Schem grows ~west of origin. |
| Seraphine / Hanging Saint pad | Cleared east of **x≥485**; must stay gone. Do **not** re-clear into Borderlands. |
| Borderlands cliff | Must stay intact west of the quay. Earlier FAWE clears ate it — treat **x &lt; 485** as sacred terrain. |
| Exile island | On **mmo-c**, world `aetherion_exile`, void generator + `exil_gesamt` paste. Cutscene already looks good to players. |
| Ferry | `/exile go` near quay boards; Ferryman FancyNPC + TalkUx. |

Your job is **end-to-end glue + verify**, not another full map polish pass.

---

## LOCKED (stop if conflict)

| System | Rule |
|--------|------|
| **TalkUx shell** | In-world bubble + reply chips stay. Content line rewrites OK. |
| **Blossom Blade / Gravwell** | Untouched. |
| **Boosters / Anvil sockets / Ranks / ShutdownCountdown / Borderlands vials** | Untouched. |
| **Additive ship** | Don’t drop live menus/factories/NPCs. |
| **Combat system elevate** | Out of scope (separate Sat patch). |
| **Tutorial overhaul** | Out of scope (after combat). |
| **Story chests** | Do **not** place/move story chests. Loot Common→Mythic scatter is a **later** session. |

---

## Player journey (must feel continuous)

```text
Borderlands (King's Road / cliff end)
    → readable path / sightline to Veil Quay
    → stand on quay, see Ferryman
    → TalkUx: boarding intent clear in ENGLISH (DE if lang=de)
    → board /ferry /exile go (existing systems)
    → cutscene (keep — polish cues only if broken)
    → land on aetherion_exile dock, oriented, not softlocked
    → /exile return path still works
```

If any step is confusing, cryptic, or dead-ends: **fix it**.

---

## What to read first

1. This file + hygiene  
2. `AetherionCore/.../exile/ExileQuay.java`, `ExileQuayPaste.java`, `ExileCommand.java`  
3. `AetherionCore/.../network/ExileLink.java`, `ExileHost.java`, `ExileArrival.java` (cutscene — don’t gut)  
4. Quests: ferryman NPC id / `story_ferryman` / lang keys under `AetherionQuests/src/main/resources/lang/en.yml` + `de.yml`  
5. Live coords: Core `exile.quay.origin` (expect `600 51 322` on `world`)  

Do **not** SSH the live box unless Robb asks. Code + configs in the monorepo are enough; assume quay at 600 unless config in repo/resources says otherwise.

---

## Deliverables (smart scope)

### A) Verify checklist (fix only what’s broken)

- [ ] Path from Borderlands cliff to quay is walkable / obvious (light blocks, railing, lanterns, one sign — not a new district)  
- [ ] No Seraphine leftovers in the pad zone; no new hole in Borderlands  
- [ ] Quay origin/placement still sensible (not absurdly far; not eating BL). Nudge only if clearly wrong — prefer **config + small FAWE-safe paste helpers in Core**, not a second oversized clear  
- [ ] Ferryman present, TalkUx works, English lines first; DE mirrors where Quests already mirrors  
- [ ] Boarding: player near quay can leave without admin `/exile` teleport cheese  
- [ ] Arrival on exile: spawn/cutscene/dock not softlock; return still works  
- [ ] No German-only player-facing strings on the **EN** path  

### B) Light quay polish (optional — only if A finds gaps)

Allowed:

- 1–2 ambient props, lighting, a short English sign (“Veil Quay” / ferry leave)  
- Ferryman dialogue clarity (board / wait / lore bite)  
- Tiny path markers from cliff → pier  

Forbidden:

- New mini-game, new boss, new district, full schem rebuild, “while we’re here” Hub/Tutorial  
- Clearing west of **x = 485**  
- Touching SamuraiBoss / combat kit  

### C) End note ≤15 lines

Bindings/commands to test, what you changed, known leftover (if any). No README novel.

---

## Language law

- **Default = English.**  
- German only when `LangAccess.german(player)` / existing Quests lang files.  
- Never ship DE as the only string for a new tip.

---

## Out of scope

- Combat dash/parry/roll  
- Tutorial rewrite  
- Random loot chest scatter  
- Exile interior districts / new bosses  
- Performance autopsy of the whole network  

---

## Test plan (Robb / friend)

- [ ] Walk Borderlands end → see quay without F3 coords  
- [ ] Talk Ferryman (EN client) → board  
- [ ] Cutscene plays; land on exile dock  
- [ ] `/exile return` (or designed return) back to main  
- [ ] DE language: Ferryman still readable in German  
- [ ] Borderlands cliff still looks complete  

---

## Closing order

> Read this prompt + the listed files. Walk the crossing in code. Fix glue and light quay polish only. English default. LOCKED stays locked. No deploy. When the journey reads smooth, stop and write the ≤15-line note.

**Go.**
