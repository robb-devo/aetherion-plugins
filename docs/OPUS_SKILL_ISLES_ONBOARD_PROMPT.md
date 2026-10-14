# Opus Prompt — Skill Isles: real short tutorials (all four)

**Owner:** Robb (IGN A3therion). Call him **Robb**.  
**Effort:** **Max**. Quality > legacy. Revert is fine — lame is not.  
**Tree:** `mining-eldervale-progression`.  
**Hygiene:** [`docs/OPUS_PROMPT_HYGIENE.md`](OPUS_PROMPT_HYGIENE.md). Deploy every jar you touch. Restart MMOR once. No commit unless asked.  
**Parallel:** Guilds / Hollis / private island = other session. Hands off.

---

## Mission (one sentence)

**Every** skill isle (Farm, Forage, Fish, Mine) gets its own **short, doable tutorial** that teaches that isle’s real Aetherion loop — so a dumb-smart player lands, understands, acts, and feels smart. Not “one NPC with flavour text.”

---

## Role — brutal top-server architect

You are the harshest product + systems architect in the room. Audit what exists (pad guides, Miss Canopy chest, welcome lines, first-visit flags, SkillRoads).

- Anything that is **only text / chest walls / “talk to guide and leave”** → **kill or replace**.
- Anything unclear, optional-feeling, or “figure it out” → **rebuild** until a cold player can’t fail the first loop.
- Depth is allowed. Confusion is not. **Simple on the surface, correct underneath.**
- If a beat isn’t perfect for a paid Hypixel-adjacent MMO funnel, it doesn’t ship.

Harbour Acts I–III just shipped (`docs/ONBOARDING_ACT_MAP.md`) and **end at the pad**. Your job is Acts IV–VII: one act per isle.

---

## What “tutorial” means here (non-negotiable)

Per isle, first visit must be a **mini-spine**, not a desk:

| Beat | Must happen |
|------|-------------|
| 1 · Land | Instant next step (hint / guide / title). No empty field. |
| 2 · Hook | TalkUx (bubble + chips). One sentence: what this isle *is*. |
| 3 · Do | A **forced practice** of the signature loop (objective that only completes when they *perform* it). |
| 4 · Payoff | Tiny reward / unlock / clear “you got it” + where to go next (shop / pad / deeper zone). |
| 5 · Free | Tutorial flags done; guide becomes optional desk, never a wall. |

**Signature loops (teach these, not vanilla):**

| Isle | Loop to prove |
|------|----------------|
| **Forage** | Fell bar — green window / NOW / streak (same DNA as harbour wood, but *isle* stakes) |
| **Farm** | Plant → wait/tend → harvest rhythm (whatever the isle actually uses — teach *that*) |
| **Fish** | Cast → bite window / reel cadence (Aetherion fishing, not “hold right-click”) |
| **Mine** | Break/gather with **Mining Power / Rhythm** (or the real isle mechanic) so the UI isn’t mysticism |

If today’s code can’t detect “they did the loop,” **add the hook** (`QuestProgressHook` / isle listeners). Do not fake completion on “talked to NPC.”

---

## Hard no

- One NPC, three paragraphs, “have fun” → **forbidden**
- Chest briefing as the main teach (Miss Canopy today) → demote to Details… only or delete from path
- Shared copy-paste monologue on all four isles → each isle must feel native
- Teaching Minecraft literacy (“this is a hoe”) → only Aetherion twists
- Soft-locking the harbour stamp / `tutorialDone` / story gates

## LOCKED

TalkUx **shell** (bubble + chips stay; content/structure free) · Blossom/Gravwell · booster stack / Anvil socket **mechanics** · ranks · ShutdownCountdown · private island factory · Sat combat formulas.

---

## Freedom

- New short quest ids per isle, new flags, new guide staging, trail/arrows, title cards, rewards (teleport crumbs, coins, starter tools — additive, not economy rewrite).
- Replace or rewrite pad guides + on-isle guides. Keep FancyNPC ids only if wiring is cheaper; **product > id nostalgia**.
- Steal harbour patterns: `lesson_*`, title cards, “!” only on next stop, defer side NPCs until isle tutorial done.

---

## Audit → ship order

1. Cold path: stamp → road picker → each pad → 10 minutes. Write what’s lame.
2. Design four mini act maps (≤½ page each) before huge code.
3. Implement all four to the same quality bar. No “Forage perfect, Mine TODO.”
4. DE mirror. Deploy. End note.

---

## Done when

A fresh alt can, without Discord:

1. Finish harbour stamp.  
2. Pick **any** of the four roads.  
3. Complete that isle’s short tutorial by **doing** the loop once.  
4. Explain in one sentence what that isle’s skill *feels* like.  
5. Know how to leave / what to do next.

If you wouldn’t put your name on a trailer clip of that first isle visit — keep going.

## End note (≤20 lines)

Per-isle: quest ids · what they must do · what you killed · jars shipped · walk order for Robb.
