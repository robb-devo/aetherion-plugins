# Aetherion — Main Island SAITAMA punch (1000×1000 live)

Paste into a **fresh** Opus Extra / Max / Ultra-code session.  
Owner: **Robb**. Call him Robb.

Workspace:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

---

```text
# AETHERION — MAIN ISLAND SAITAMA PUNCH (1000×1000 LIVE)

Fresh Claude Opus at absolute highest effort (Extra / Max / Ultra). Owner: **Robb**.

Workspace:
C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c

Hygiene: docs/OPUS_PROMPT_HYGIENE.md — edit source directly. NO _ship/ theater. NO essay READMEs. NO production deploy unless Robb asks. Multi-session OK if the punch needs it — leave crisp handoffs; RESULT > quota.

Resources / token limits are irrelevant to Robb. **It takes what it takes.** Dig deeper than you ever have on this tree. Invent when invention wins. Steal the spirit of the best MMO hubs you’ve seen and **rebuild that feeling in Paper plugins + schems** — don’t paste foreign code blindly; make it Aetherion.

---

## THE CLEAR GOAL (read twice)

You are polishing the **live 1000×1000 Origin / Skyblock RPG Spawn map** on **MMO-R `world`** (already pasted 1:1).

**Ankerhold is OUT.** That map was a capability test. Do not cut over to it. Do not spend the session on Ankerhold. Stay on the big live island.

Two feelings must both be true when you stop:

1) **First minutes (harbour / spawn):** the player lands and is **stunned**. Harbour is the starting place — today it feels barely important and the map around it feels empty. Fix that. The first few minutes must be awe: density, life, silhouette, “I want to walk *there*.”
2) **Countless hours later:** the **same main hub world** is still fun to **explore**. Not a pretty spawn + void. Secrets, props, NPCs, side moments, vistas, districts that reward curiosity forever.

Harbour = strong doorway. The whole 1000² = the product.

Taste bar: a Reddit stranger joins → holy shit in 30–180 seconds → still finding cool corners after tens of hours. Trailer stills possible from a dozen spots.

---

## ATTITUDE (Robb’s words — obey)

- We have ideas below. **You are invited to completely ausrasten.** Exceed the brief when the map asks for it.
- Use what we already have **first**. If you catch yourself thinking: “holup — this spot would be breathtaking if I cooked something new” → **cook it.** Schem, prop, NPC, beat, quest, system — if it makes the island legendary, ship it.
- **No over-engineering for sport.** If a simple placement + existing Life beat wins, do that. If a new system / deeper Life / map-smart quest engine is what makes it Saitama-level, **it is what it is** — build it clean, performant, maintainable enough for a live server.
- Server must **run**. Push every corner of what Minecraft + our plugins can do — particles, ambience, NPC life, props, quests — without melting TPS. Measure mentally: off-screen cheap, on-screen rich.
- **Self-grade ruthlessly.** Before you stop, write a brutal scorecard. If you cannot honestly say **110% / Saitama done**, it is **not finished** — keep going or hand off the exact remaining gap. Soft “pretty good” = fail.

This is the One Punch. Nobody should recover. Because it’s that heftig.

---

## READ FIRST (ground truth — then dig)

| Doc / place | Why |
|-------------|-----|
| `docs/OPUS_PROMPT_HYGIENE.md` | Standing rules |
| `docs/MAIN_ISLAND_ORIGIN.md` | Live Origin overlay: districts, landmarks, pads, ambience — **additive** |
| `docs/AETHERION_MAIN_HUB_WORLD_BIBLE.md` | Harbour-city DNA / density taste |
| `docs/OPUS_RED_THREAD_PROMPT.md` + red thread if present | Spine must stay clear (Egon → … → Ledger → pads) |
| `docs/PUBLIC_LORE` / Story spoilers sense | Side flavour OK; **no Act III spoilers** on public quests |
| Hub: `PropCatalog`, `/propwand`, `props/*.schem` | ~40 landmark props — **use them** |
| Items: Ambient Props / DevMenu ambient | BlockDisplay props — place; don’t wipe DevMenu |
| Quests: FancyNPC cast, `LivingNpcLife`, `LivingNpcAtmosphere`, skins, TalkUx | Life + talk — **TalkUx shell LOCKED** |
| Hub Origin: `origin.yml`, ambience, pads, townsfolk boards | Don’t fight Quests cast proximity |
| Live map feel | Empty stretches between landmarks = your enemy |

Grep/scout as deep as you need. Prefer owning plugins: Hub, Quests, Items (ambient/props only). Core stays thin.

---

## PILLARS (do all of them)

### 1) Density — kill the empty
- Flood meaningful space with **existing** Hub prop schems + Ambient Props: quay, market, alleys, pad edges, mine/forage/farm/fish approaches, overlooks, under-island moments if the map has them.
- Add new `ae_*` schems / props / emitters when a hole screams for a landmark.
- Chest / dressing: storytelling containers (towers, piers, attics, camps) — fun rewards, not a loot-table rewrite of the whole economy.
- Origin districts / vistas / waystones / moments: elevate and **wire into the feel**; don’t leave them as YAML that nobody notices.

### 2) First-minute awe (harbour + spawn plaza)
- Spawn sightlines, crowd density, sound, light, motion (NPCs + ambience + props).
- Player must instantly feel: this server is alive and expensive-looking.
- Still leave a **clear next step** toward Egon / tutorial spine — awe ≠ portal-wall confusion.

### 3) Exploration forever
- Treat the full footprint (see MAIN_ISLAND_ORIGIN extents) as content.
- Reward walking off the spine: micro-stories, props, NPCs, side quests, vistas, “why is that there?”
- After hours, new corners should still pop.

### 4) NPCs — living cast
- Elevate **LivingNpcLife** / Atmosphere: job-true idle, tools, glance, patrols, busywork. Push the system hard — new beats, slightly longer routes if safe, interaction hooks — without turning TPS to ash.
- Place unplaced extras (crier, sweeper, lamp lighter, …) and **add new** FancyNPCs with skins + personality where the map wants them.
- Mix: workers who **do** things + walkers + talkers. Statue spam = fail.
- Origin Hub townsfolk vs Quests cast: keep distance / ownership rules from MAIN_ISLAND_ORIGIN.

### 5) Quests — weird, map-smart, amusing
- Not “mine 20 ores.” Unique side quests that **use the map**: towers, bells, roofs, piers, districts, timing, delivery with character, rumours, small rituals, landmark hunts, funny failures.
- Soft-point toward skills/pads when natural; **don’t** smash the red-thread main path.
- TalkUx: new lines + chips OK. **Do not rewrite TalkUx architecture.**
- Spoiler ban: no Act III / Nihil / World Eater / “he rings the bells to hold the world” as fact.

### 6) Performance
- Rich where the player looks; cheap where they don’t.
- No per-tick particle storms for every NPC island-wide.
- Document any hot paths you add; prefer distance gates, sleep, budgets.

---

## LOCKED (stop and say LOCKED … CONFLICT)

- TalkUx shell (bubble, typewriter, chips, quest card, per-listener, chat mirror)
- Ranks / `player-ranks.yml`, Boosters stack/intent, Anvil sockets
- Blossom Blade / Gravwell Cleaver combat
- Shutdown countdown
- Additive ship: don’t gut DevMenus, Ambient Props page, live gotos/pads
- Private/guild island factory (Session 2)
- Exile connect / Exile map rewrite (Session 3)
- Ankerhold cutover
- Core must not grow a game loop

---

## PROCESS

1. Skeptical walk of the live design (docs + coords + empty zones). List the dead stretches.
2. Plan density + life + quest spikes per district (harbour first for awe, then the rest of the island).
3. Execute: place + code + schems + quests. Invent when the punch needs it.
4. Perf pass.
5. Cold-path check: stranger still finds Egon / spine.
6. **Brutal self-scorecard** (must reach honest 110% or keep going / hand off gaps).
7. End note for Robb: what changed, where new schems/NPCs/quests live, how to smoke on MMO-R, residual nits. **No deploy** (other agent).

---

## DONE WHEN

- [ ] Harbour/spawn first minutes = stun
- [ ] Map no longer feels emptily large — exploration still pays after many hours
- [ ] Props/schems/NPCs visibly own the world; new content where it earned the right
- [ ] Side quests feel unique and map-aware (not chore sheets)
- [ ] Red thread / TalkUx shell / LOCKED systems intact
- [ ] Server still performant in principle (budgets, distance gates)
- [ ] Self-score: you can defend **110% Saitama** without lying

Go. One Punch. Make the 1000² legendary.
```

---

## Paste starter (short)

```text
Read docs/OPUS_MAIN_ISLAND_SAITAMA_PROMPT.md + docs/OPUS_PROMPT_HYGIENE.md + docs/MAIN_ISLAND_ORIGIN.md.
Workspace: C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c

MAIN ISLAND SAITAMA — live 1000×1000 only (Ankerhold OUT).
Harbour/spawn = awe in first minutes. Whole map = explore for countless hours (kill empty).
Use existing props/schems/NPC life first; invent when it would be breathtaking.
Unique map-smart side quests (not mine-20). Ultra code OK if it serves the punch — no sport over-engineering. Perf must hold.
Self-grade to honest 110% or keep going. No deploy. TalkUx shell LOCKED. Go.
```
