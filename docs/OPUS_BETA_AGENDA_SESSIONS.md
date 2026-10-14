# Opus — Beta agenda sessions (Robb · 2026-10-11)

**Du machst nichts in Composer hier.** Paste den jeweiligen Block unten in eine **frische Claude Code / Opus**-Session.

Workspace immer:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Standing: `docs/OPUS_PROMPT_HYGIENE.md` — edit source, **no** `_ship/`, **no deploy**, TalkUx shell LOCKED, ranks / boosters / signature weapons LOCKED.

---

## Smarteste Reihenfolge (nicht alles in einem Run)

| Session | Thema | Warum allein / so geschnitten |
|--------|--------|-------------------------------|
| **1** | Tutorial klar + Tutorial-/Origin-Island anbinden | Gleiche Early-Funnel-Spur (Quests + Hub Origin). Ein Run, ein Taste-Bar. |
| **2** | Private Insel (Satisfactory) | Eigener Rage-Quit-Rewrite. Frisst allein eine volle High-Session. **Nicht** mit Tutorial mischen. |
| **3** | Exil anbinden + besetzen + Polish-Run | Map-Polish existiert; Connect + Cast + Story fehlen. Eigenes Thema. |
| **4** | Mainwelt befüllen (Truhen / Schems / Props / QoL-NPCs) | Content-Placement + Ambient — anderer Modus als Code-Funnel. |
| **5** | Bosse sauber + fehlende Rezepte / Drops | Items + BossEngine / Codex — eigene Allowlist. |

**Nicht** 1+2+3 in einem Prompt: Insel allein ist schon „geisteskrank“; Tutorial daneben wird halb.  
Nach Session 1: smoke cold. Dann Session 2. 3–5 erst wenn 1–2 sitzen oder Beta-Tester nur Main brauchen.

---

## SESSION 1 — paste below

```text
# AETHERION — Session 1: Tutorial klar + Tutorial/Origin Island anbinden

Fresh Opus / Claude High session. Owner: **Robb** (call him Robb).
Workspace: C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c
Branch context: ship/live-hardening worktree.

Read first (do not re-litigate):
- docs/OPUS_PROMPT_HYGIENE.md
- docs/POLISH_BRIEFING.md (tutorial / opening notes)
- docs/MAIN_ISLAND_ORIGIN.md (OriginIsle — additive Hub layer)
- docs/AETHERION_OPENING_CINEMATIC_HANDOFF.md (orbit / Egon handoff — wire if half-done, don’t invent a second cinematic)
- docs/OPUS_RED_THREAD_PROMPT.md if present
- Live paths: Quests SkillRoads, QuestHint, NpcListener (Egon→…→Ledger), Hub OriginIsle / OriginProfiles

## Mission

Make the **first 30–60 minutes** idiot-proof for a Reddit stranger:
1) They always know the **one next step** (TalkUx chip / action bar / QuestHint — not a wall of lore).
2) The **main/tutorial island (Origin / Opening)** feels connected to that funnel — not a separate abandoned layer.
3) Harbour → Egon → woods/coal/mines → Temper → Ledger → graduation → SkillRoads still works; you harden clarity, you don’t rewrite the story.

Taste bar: a friend who never played can finish orientation without Discord.

## LOCKED (stop if conflict)

- TalkUx shell (bubble, typewriter, chips, quest card, per-listener, chat mirror + chest Details fallback) — timing/content OK, no architecture kill.
- Signature weapons, Boosters stack/intent, Anvil sockets, Ranks / player-ranks.yml, Shutdown countdown.
- Additive ship — don’t gut DevMenus, live roads, or Origin systems that already work.
- Do **not** rewrite private/guild island factory (Hollis / belts / quarries) — that’s Session 2.
- Do **not** rewrite Exile, Mainwelt loot scatter, or boss loot tables — Sessions 3–5.

## Deliverables (this session only)

### A) Tutorial clarity (Quests)
- Audit cold path: join/harbour → Egon → Forager → Quartermaster → Foreman → Temper → Ledger → Farmer/Lark → graduation → SkillRoads picker.
- Every handoff: one clear line + yellow QuestHint / spot arrow + short action bar tip (LangPack EN + DE keys where de.yml already mirrors).
- Kill “portal world” / vague next-step lines; use concrete NPC names + places (Harlow pad, Twig pad, Tackle pad already exist — keep patterns).
- Soft-lock check: Foreman → Temper must stick (re-assert if something steals the pin). No mines-only dead end after first_shift.
- Homie friction: humor OK; next action always explicit.

### B) Tutorial / Origin island bind (Hub + Quests)
- Trace OpeningOrbit / OriginIsle / first-visit tour vs Quests harbour spawn.
- If Opening is gated wrong (e.g. only mmo-c) or never hands off to Egon: fix the handoff so MMO-R beta players get a clean “here is your world → talk to Egon” without breaking Hub.
- Origin townsfolk / boards must not fight Quests TalkUx (keep distance / Hub-owned GUIs as MAIN_ISLAND_ORIGIN says).
- Spawn unlocks + pads: after orientation crumbs, SkillRoads still point at Twig/Harlow/Tackle; Origin landmarks don’t steal the soft compass forever.
- If cinematic half-exists: finish wire to existing onboarding. If missing pieces block beta: ship a **minimal** lift→settle→Egon path, not a 60s movie.

### C) Out of scope
Private island Satisfactory rewrite. Exile. Mainwelt chest/schem spam. Boss drops. Deploy. Perf Grok P0s.

## Done when
1. Cold mental walkthrough (or code-path proof) shows one next step at every beat through Ledger graduation.
2. Origin/Opening either cleanly feeds Egon or is explicitly no-op with zero soft-lock (document which in ≤3 lines).
3. ≤15 line end note: files touched, player-facing before/after, anything Robb must place in-world (`/questnpc`, origin.yml). No essay. No ship folder.

Go. Edit source directly.
```

---

## SESSION 2 — paste below (Private Island)

Use the **full** existing prompt (already hard after playtest). Open and paste **all of**:

`docs/OPUS_ISLAND_SATISFACTORY_ROBB_VERDICT.md`

(It embeds the north star + belt + Hollis tour deliverables. Also keep open: `docs/OPUS_ISLAND_SATISFACTORY_GO_PROMPT.md`, `docs/OPUS_ISLAND_FACTORY_ONBOARD_110_PROMPT.md`, `docs/OPUS_PERSONAL_ISLAND_SATISFACTORY_BRIEF.md`.)

Optional one-liner to prepend in Claude:

```text
This is Session 2 of the beta agenda. Session 1 (tutorial/Origin) may have landed in the same worktree — do not regress Quests SkillRoads / Temper hints. Focus only on personal island Satisfactory feel + door/grant path. Hygiene: docs/OPUS_PROMPT_HYGIENE.md. No deploy.
```

---

## SESSION 3 — later (Exil) — paste when ready

```text
# AETHERION — Session 3: Exile connect + populate + polish run

Fresh Opus session. Robb. Worktree: mining-eldervale-progression-65660c.
Hygiene: docs/OPUS_PROMPT_HYGIENE.md. TalkUx LOCKED. No deploy.

Read: docs/OPUS_EXILE_MAP_POLISH_PROMPT.md (map craft bar), docs/STORY_BIBLE.md Exile / Act III sections if present, Core ExileHost/ExileLink/ExileArrival, Quests story gates toward Exile.

## Mission
1) Story/network **anbinden**: player can reach Exile for real from the live funnel (not a stranded map).
2) **Besetzen**: cast / interactables / clear next-step once there (not empty masterpiece).
3) One more **polish run** on weak beats only — don’t flatten the already-strong map pass.

Out: private island factory, tutorial harbour rewrite, mainwelt chest spam, boss loot tables (Session 5).
End ≤15 lines. Edit source + authored Exile assets only as needed.
```

---

## SESSION 4+5 — even later (stubs)

**4 Mainwelt:** chests, schematics, props, QoL ambient NPCs — use `docs/OPUS_MORE_PROPS_PROMPT.md` + `docs/OPUS_AMBIENT_IDEA_NPCS_PROMPT.md` + Hub prop wand / Origin density bar. Placement-heavy; separate session.

**5 Bosse / Rezepte / Drops:** audit BossEngine + Items recipes + drop tables for unfinished uniques; additive only; no signature-weapon combat retune.

---

## How Robb runs this in Claude Code

1. Open the worktree folder as the project root.  
2. New chat → Opus/High → paste **Session 1** block only.  
3. When that ends: smoke in-game (or ask Composer to deploy if you want jars live).  
4. New chat → paste Session 2 (Island verdict).  
5. Don’t start Session 3 until you’re happy with 1–2 or explicitly park islands for post-beta.
