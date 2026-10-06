# Opus — TalkUx presentation polish (SAHNEHÄUBCHEN)

**Local agent. Presentation shell only. Not dialogue. Not NPC cast.**

Read **only** this file + the allowlist. Do not rescan the monorepo. Do not deploy. Do not touch network/transfer/ranks/weapons.

Standing hygiene: `docs/OPUS_PROMPT_HYGIENE.md` — code only, no `_ship/` / README / manifest theater.

---

## Mission

The in-world Talk UX is already strong (speech bubbles, typewriter, reply chips, per-listener visibility). The **old surrounding presentation** (nametags, quest marks, idle → click → dialog open/close, choice row) does not yet feel like one visual system with the new bubbles.

**Goal:** unify + elevate **only the presentation layer** so name → click → bubble → choices → close feels like one polished product — same family as the bubbles, not a mash of eras.

**NOT the job:** rewrite dialog lines, add/remove NPCs, change quest logic, rebalance, Hub/MMO network, weapons, boosters, ranks.

---

## LOCKED (stop if conflict)

- **TalkUx shell stays.** Keep TextDisplay speech bubble over living NPCs, typewriter / per-NPC voice, reply chips + quest card beside NPC, per-listener visibility, chat mirror + chest Quest Offer as Details… / classic / fallback.
- Do **not** remove the bubble, force chat-only, or “simplify away” TalkUx.
- Signature weapons / boosters / anvil / ranks / shutdown / additive-ship: do not touch.
- Today’s network/transfer harden (MMO-R/D) + Hub Pit warp fix: **do not regress.** Quests presentation only.

If something outside allowlist seems required → write `LOCKED TALK UX CONFLICT: …` and **stop**.

---

## Allowlist (edit only these)

Primary:

- `AetherionQuests/src/main/java/de/aetherion/quests/talk/TalkUx.java`
- `AetherionQuests/src/main/java/de/aetherion/quests/talk/TalkText.java`
- `AetherionQuests/src/main/java/de/aetherion/quests/talk/TalkGlyphs.java`
- Any **new** helper classes you add under `AetherionQuests/.../quests/talk/` (prefer small focused files over bloating TalkUx)
- `AetherionQuests/src/main/java/de/aetherion/quests/npc/LivingNpcProfile.java` — nametag / color / presentation only
- `AetherionQuests/src/main/java/de/aetherion/quests/npc/LivingNpcService.java` — nametag glue / display follow only
- `AetherionQuests/src/main/java/de/aetherion/quests/npc/LivingNpcAtmosphere.java` — ambient bubble / busy / mark polish only
- Quest-mark / overhead UI already tied to living NPCs (extend in `npc/` + `talk/` — **do not fork a second system**)
- Optional tune only: `AetherionQuests/src/main/resources/config.yml` keys under `talk-ux:` (or adjacent presentation keys you already own)

Allowed to **read** (no drive-by rewrites):

- `TalkCommand.java`, `TalkAccessImpl.java`
- Quest offer / Details chest hooks TalkUx already uses
- `CastBook` / profile colors for name hue by role
- Existing particle/sound helpers TalkUx already calls

**Hard out:** `DialogManager` dialog *content*, quest graphs, `lang/*.yml` line rewrites, Hub Origin, Core network, Items, Dungeons, Pit, Guilds, BossEngine, LivingNpcLife movement (separate pass).

---

## Design brief (direction — taste > feature count)

NPCs already have a **name color** by who they are / where they sit. Pull that thread through the whole talk chrome.

Ideas to explore (implement what actually looks insane in-game — not every bullet):

1. **Bubble frame / edge** — a *very* subtle border or soft glow on the speech bubble in the NPC’s name color (readable first; never neon spam).
2. **Quest mark** — clearer, prettier mark above NPCs that have something for you. Idle vs ready-to-turn-in readable in one glance without screaming.
3. **Nameplate vertical layout** — where the name sits over the NPC; what happens when dialog opens (fade, slide up, tuck under bubble, dim). No flicker, no double-name stack.
4. **Idle → click → open** — one continuous motion language: mark/name settle → bubble appears → typewriter → chips. Same exit when dialog ends.
5. **Reply chips / final choice row** — match bubble typography / weight / color language so choices don’t look like a different UI era.
6. **Depth & rhythm** — soft shadow, consistent gaps between name / bubble / chips / quest card. One scale system.

Go deeper if you see a better unity beat. **One cohesive composition** beats ten unrelated flourishes.

---

## Hard constraints

- **Additive / elevate only.** Do not gut working TalkUx. Do not replace with chest-only.
- **Performance:** no per-tick particle storms; no new global scoreboards; TextDisplay budgets stay sane.
- **Per-listener:** only the talking player sees their bubble/chips (keep).
- **Classic mode** (`/npctalk classic`) must keep working as fallback.
- No new plugin dependencies.
- Prefer small helpers under `talk/` over a rewrite-for-sport of TalkUx architecture.

---

## Process

1. Read allowlist. Note current name / bubble / chip / mark / busy behavior privately (max ~10 bullets — do not essay in chat).
2. Implement presentation polish **directly in this worktree**.
3. Compile `AetherionQuests` if you can without network/m2 theater; else one-line skip.
4. **No deploy.** No `_ship/` folder. No README/manifest.
5. Short chat summary for Robb: what changed visually + which files + what you deliberately left.

---

## Done means

Standing in front of a living NPC: name, quest affordance, click, bubble, choices, and close feel like **one** design — bubble-quality chrome everywhere, not “new bubble + old placard”.

**STOP** when that lands. Do not expand into dialog writing or new NPCs.

---

## One-liner paste for Opus

> Read **only** `docs/OPUS_TALKUX_PRESENTATION_POLISH_PROMPT.md`. TalkUx **presentation** sahnehäubchen: unify nametag / quest mark / idle→click→bubble→chips with the existing bubble quality; optional subtle name-color bubble frame. Shell LOCKED (keep bubbles). No dialog rewrites, no network, no deploy. Allowlist only. Go.
