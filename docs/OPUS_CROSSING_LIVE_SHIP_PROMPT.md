# Opus Prompt — Crossing Live Ship (BL safe)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Target:** Paper **1.21.1**, Java 21. Server language = **English** (German via `/language de`).  
**Tree:** `mining-eldervale-progression` worktree (ship/live-hardening). `main` has no Exile code.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md) — edit real tree. **Deploy Core + Quests to MMOR when the walk is green**; restart MMOR once. No commit unless Robb asks.  
**Model:** Max / Extra is enough. Not Ultrathink.

---

## Mission (one sentence)

**Walk and ship** Borderlands → Veil Quay → Ferryman → board so it works **in-game on live**, without eating Borderlands terrain again.

---

## What just happened (do not re-break)

| Fact | Detail |
|------|--------|
| Root cause of BL wipe | `/exile placequay` aired `ox-130` → from origin 600 that cleared **x=485→620**, plus a Seraphine pad clear **485–545**. That ate the cliff approach. |
| Guard now in Core | Pad clear = deck footprint only (`ox + DECK_WEST − 8`, floor `BL_SAFE_WEST=520`). Seraphine block clear is **`/exile clearsaint` only** (void gap **520–535**, entities wider). **Never** air west of 520 for BL dress. |
| Live restore | `/exile quayundo 4` (FAWE console history) + `/exile clearsaint` already ran. **Undo may have moved the quay / causeway** — re-verify and re-paste **only if needed**, with the new pad clamp. |
| Causeway | `/exile causeway` (stand cliff, z≈327) lays 3-wide air-only walk to pier; `undo` / re-lay after quay re-paste. |
| Samurai (out of scope) | HP 50k, damage multiplier 7, BarChrome BOSS frame — already live. Do not retune. |
| Quests jar | Ferryman “Cross the Veil” / crouch / EN-DE glue may still be **only in the worktree** — ship Quests with Core. |

---

## LOCKED

TalkUx shell · Blossom Blade / Gravwell · boosters / Anvil / ranks / ShutdownCountdown / Borderlands vials · additive ship · combat elevate (Sat) · tutorial overhaul · story chest scatter.

---

## Do this

1. **Read** (code truth): `ExileQuayPaste`, `ExileSaintClear`, `ExileCauseway`, `ExileCommand`, Quests `ExileStory` / `StoryLines` / `StoryFlow`. Confirm pad clear cannot reach x&lt;520.
2. **Live walk** (MMOR main world): cliff → sightline to quay → causeway (lay if missing) → Ferryman chips → board → exile arrival → return text points **west** / causeway.
3. If quay missing/wrong after FAWE undo: `/exile placequay` at **`600 51 322`** (or current `exile.quay.origin`) — **only** with the new Core jar. Then `/exile causeway` from the cliff. Then `/exile clearsaint` if Seraphine leftovers remain in the void gap. **Do not** widen clear boxes west.
4. **Deploy** Core + Quests → restart MMOR → one short confirmation. No Done-wait hang.
5. Stop when the journey reads smooth in-game. ≤15-line end note.

---

## Out of scope

Combat patch · tutorial · loot chests · Homie bug list · Exile island rewrite · Ultra map polish.

---

## End note (≤15 lines)

What you walked, what you re-pasted/laid, jars deployed, anything left for Robb’s eyes.
