# Opus Prompt — Tutorial / Onboarding Revamp (combat slot reserved)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Target:** Paper **1.21.1**, Java 21. Server language = **English** (German via `/language de` / `lang/de.yml`).  
**Tree:** `mining-eldervale-progression` worktree.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md). Deploy **Quests** (+ Items/Hub only if you must touch them). Restart MMOR once when green. No commit unless Robb asks.  
**Model:** **Max**. Ultrathink OK if the spine rewrite is huge — quality > speed.  
**Sibling:** Combat elevate lands **Saturday** — leave an explicit **combat teach beat** as a stub; do not invent dash/combo rules.

---

## Mission (one sentence)

Replace the first-map “vanilla chores” tutorial with a **professional Aetherion onboarding spine**: NPCs + TalkUx teach what *this* server is, with a clear reserved slot for combat after Sat — not “how to walk / break wood in Minecraft”.

---

## Mandate — quality over legacy (read this)

The current tutorial is **not** sacred. It is below the level of the rest of the server. Your job is a **real revamp**, not a dialogue polish on the old wood→mine→farm chain.

1. **Audit first:** what the live server actually is (skills/loadout, gather twists, pads, TalkUx, codex, temper/boosters, travel, story gate). Build the spine from *that*, not from the old quest list.
2. **Kill what doesn’t earn its place.** If 9 of 10 tutorial beats/NPCs should change roles, lines, order, or leave the tutorial path — **do it**. Do not keep weak beats alive for nostalgia.
3. **Suggested spine below = checklist of systems to teach, not a cast list to preserve.** Egon/QM/Ledger/etc. may stay, move, merge, or get replaced if a stronger staging exists.
4. **Gates only:** `QuestStoryGate.tutorialDone` (and anything that hard-blocks story/exile on “tutorial finished”) must still work. Prefer stable **graduation semantics**; quest/NPC **ids** may change if you update every reference. Worst case Robb reverts the tree — prefer a great spine.
5. **One combat stub** stays reserved for Saturday. Everything else: make it feel finished.

---

## Brutal product brief (Robb + Shai + staff)

### What’s wrong today
- Onboarding is still the **first draft** after the map switch: wood → mine → second mine → farm. It *works*, but feels **random** and teaches **Minecraft literacy**, not Aetherion.
- Cool systems stay **undiscovered** (e.g. **skill loadout** — staff called it “geisteskrank geil”, but nobody is taught).
- Homies bounce on friction: auto-pickup gate unclear, mining power / crystal find / dungeon-core picks / booster-on-upgrade, etc. Tutorial should **surface** the real loops.

### What “bombastic” means here
- Strong **structure** (acts / chapters), not a longer quest laundry list.
- Every beat answers: **why does this matter on Aetherion?**
- Skip or one-line anything a Minecraft player already knows (walking, basic break block) unless it has an **Aetherion twist** (gather minigame, rhythm, pads, TalkUx accept, sacks, codex).
- Freedom on dialogue, pacing, NPC staging, trail — **TalkUx shell LOCKED** (bubble + chips stay).

### Combat beat (REQUIRED stub — do not fake Sat combat)
- Insert one chapter/beat: **“How you fight here”** (arena / workshop / sparring NPC — your call).
- Content for now: short placeholder lines + gate that says combat teach unlocks after the new combat lands **or** a minimal “open the skill loadout / hold a weapon / hit a dummy” if that already exists safely.
- Leave **hooks** (quest id / NPC id / location / stub method) so Sat only fills telegraphs/dash/combos — no full tutorial rewrite again.

---

## LOCKED

TalkUx shell · Blossom Blade / Gravwell feel · boosters stack/Anvil sockets · ranks / `player-ranks.yml` · ShutdownCountdown · Borderlands vials · additive ship · Exile quay/BL clears · do not apply unfinished combat elevate.

---

## Teach these (Aetherion, not vanilla)

Prioritize discovery of **server systems**. Suggested spine (reorder freely if it reads better):

1. **TalkUx** — accept / reply chips / bubble (fix the “broken accept” *feel* in copy/staging if needed; don’t rewrite the shell).
2. **Harbour → Capital spine** — who matters (Egon / QM / Ledger / …) and why you talk to them.
3. **Gather with Aetherion twist** — one strong forage *or* mine beat with minigame/rhythm — not “go break 32 oak”.
4. **Skills + loadout** — open menu, equip a skill, feel why loadout exists (Ledger or Temper — make it impossible to miss).
5. **Pads / travel** — how you leave the harbour for isles without getting lost.
6. **Codex / progress taste** — one claim or one “this tracks your life here”.
7. **Combat stub** — reserved beat (see above).
8. **Graduation** — Miss Ledger (or better owner) closes orientation; story gate stays coherent (`QuestStoryGate.tutorialDone`).

Optional light polish in the same run (only if cheap):
- Foreman objective copy = **gather**, not break count.
- Auto-pickup: **teach** the unlock clearly (level / toast). Do **not** remove the level gate unless Robb already decided in-chat this session to move it — default = keep gate, make it loud.
- Join/MOTD note: **recommended client 1.21.1** + official pack (newer Via clients can break water/UI).

---

## Staff friction to absorb (do not expand into mining balance pass)

From **LimePuppet (Lemon)** evening chat + **Shy** Discord — tutorial/signposting only unless trivial:

| Signal | Tutorial / UX angle |
|--------|---------------------|
| “whens auto collect?” | Teach unlock moment |
| Skill loadout never explained | Dedicated beat |
| crystal find / mining power / dungeon core on pick | One line from Foreman/Temper, not a lecture |
| upgrading gear deletes boosters | Warn once near Temper/Anvil beat if on path |
| Quest accept looks broken / invisible water | Version note; don’t rebuild pack here |
| Obsidian “where?” | Not tutorial-critical; skip or one Ledger tip |

---

## Code / content anchors

- Tutorial trail: `TutorialQuestTrail`, harbour onboarding quests, `QuestStoryGate`
- NPC spine: `NpcListener`, CastBook / LivingNpc profiles
- Lang: EN in code / messages; DE in `lang/de.yml` — **mirror new lines**
- Graduation / `tutorialDone` must stay coherent. Renaming quest/NPC ids is fine if **every** gate, trail, hint, and CastBook reference is updated in the same pass.

---

## Deliverables

1. Written **act map** (≤1 page in the end note or a small `docs/` sketch) before huge rewrites.
2. Implemented spine in Quests (dialogs, objectives, trail, graduation).
3. Combat beat stub + clear `TODO(combat-sat)` markers.
4. Deploy Quests → MMOR restart → end note ≤20 lines: act map, what players do now, what’s stubbed for Sat, jars shipped.

---

## Out of scope

Sat combat formulas/VFX · full skill rebalance · Exile map · chest scatter · Homie bug squash marathon · signature weapons.

---

## End note (≤20 lines)

Act map · stub location for combat · anything Robb must walk · deploy status.
