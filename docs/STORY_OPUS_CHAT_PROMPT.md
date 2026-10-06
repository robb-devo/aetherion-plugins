# Aetherion — Story + Dialog Mega-Chat (paste for Opus)

Workspace root (must be this folder, nothing above it):

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Strongest model. Stay inside this root. Touch mainly `docs/`, `AetherionQuests/`, `AetherionItems/`, `AetherionCore/` (thin only). Ignore `**/target/**` and other worktrees. **No deploy, no restart, no commit** unless Robb says go.

Copy everything below the line into a **fresh** Opus chat.

---

You are shipping **Aetherion Story Package 1 + dialog cleanup** in this worktree. Call the owner **Robb**.

## Read first (in order)

1. `docs/STORY_BIBLE.md` — canon tone, names, lullaby 1–9, finds, cosmology
2. `docs/STORY_P1_PLAN.md` — exact architecture, quest table, items, acceptance
3. `docs/STORY_INVENTORY.md` — what already exists live
4. `docs/dialogs/DIALOG_VOICE_DE.md` — DE overlay rules

Then scout patterns before inventing APIs: `QuestRegistry`, `DialogManager`, `CastBook`, `LivingNpcProfile`, `QuestNPCRegistry`, `AetherionManagerGUI` journal, `BossDeathEvent`, `lang/de.yml`, `CustomItem` lore factories, DevMenu give pages.

## Mission (ship in this order)

### 1) Dialog cleanup — bible-fit craft pass (existing FREE NPCs)

**Aufräumen + neu schreiben, passend zur Bibel.** Not a thin “tone sprinkle.” Every touched line must earn its place and fit *Das Schlaflied* (grief under a villain mask; world still laughs; show, don’t lecture).

Do this for **active FREE harbour / tutorial / capital cast** that already exists. Raise the bar:

- Funny when it fits. Serious when it fits. Heartbreaking when it fits. Quiet — not melodrama.
- A kid gets the point; adults catch sharper edges (grief, shame, bitterness, dry humor). Never crude for shock. Never creepy toward Nell or any child.
- Kill weird, illogical, or “why would they say that?” lines.
- No cryptic riddles, no lore walls, no rare fancy words to sound smart.
- After every quest offer the player always knows **where / what / next unlock**.
- Keep tutorial GPS facts; polish the voice around them — do not delete the facts.
- TalkUx: ~70 chars per beat, usually 1–3 beats.
- **EN canonical for dialog.** **DE for all dialogs you touch** — ship real `lang/de.yml` / `cast.*` / talk keys in **this** session (informal **du**, dry, same beat count, same kick). Not a stub. Not “EN only, DE later.”
- Bible layers: Act I **legend** / Act II **doubt** as **additive** variants by story stage or completed `lullaby_*`. Public knowledge early. Keep secrets out of casual barks (Maelis in Helios, Elin’s death, Corvin ringing the bells).

Surfaces to rewrite/cleanup: intros + state variants, completed chatter, CastBook (returning, accept/decline, reactions, greet, idle, topics, banter), quest titles/descriptions you touch, inline turn-in lines you touch.

**Include at least:** Aurel/bellkeeper, Stellan, Wick/`lamp_lighter`, Hollis/`town_crier`, Egon, Bar Whisper, Deed/`isle_clerk`, Surveyor, Vince, Miss Ledger, plus other live FREE harbour NPCs you find wired.

**Do not rewrite dialog for:** Farmer/Harlow, Rite Warden, Proctor, isle casts (Farm/Fish/Mine/Forage), Miss Canopy, Dungeon Keeper.

### 2) Story NPCs — add the ones that are **not** in the game yet

These five are **missing** today. **Add them** (LivingNpc + full EN/DE voice + CastBook + registry). Robb places them later — **not auto-placed**.

| npcId | Display | Role |
|---|---|---|
| `ysolde` | Ysolde Thorne | Mainline archivist / archive desk |
| `berit` | Mother Berit | Emotional anchor |
| `nell` | Nell | Child, afraid of the dark |
| `hesper` | Hesper Kael | Fallen Anchor counter-voice |
| `ferryman` | Ferryman | Veil Quay (exile destination may stub) |

Rules:

- Wire `QuestNPCRegistry` + `ideaNpcIds()` / `storyNpcIds()` so `/questnpc` (extras) can give anchors.
- Full `LivingNpcProfile`, `DialogManager`, `CastBook`, DE `cast.*` / talk keys.
- `QuestStoryGate`: available after `tutorialDone` (not first-hour soft-lock).
- **Reuse live ids** for people who already exist (Hollis = `town_crier`, Deed = isle clerk id, Sister Aurel = bellkeeper id — **grep**, don’t invent duplicates).
- **Do not invent random sixth story NPCs.** Only these five new ids + polish existing FREE cast.

### 3) Story items + Chronicle + Fundstücke

- Lore item factories, **no combat stats** (ids/names in `STORY_P1_PLAN.md` §3): Music Box Gear, Sun Guard Insignia, Tiny Paper Crown, Anchor Chain Link, Sealed Writ, Maelis letters, readable books as planned.
- PDC e.g. `aetherion:lore_find` + `find_id`.
- **Item lore language:** EN names/lore/book text only in this session. **Do not** invent or polish DE item lore / DE written books here — Robb runs a **fresh dedicated session** for perfect DE item lore later. Wire factories + EN presentation cleanly; leave DE item strings alone unless an existing Items-lang hook already requires a key (then add a short EN-parity stub, not a craft pass).
- DevMenu: **add** a Lore/Chronicle give row — never overwrite Special Sets / Weapons / Wipe / Ambient pages (**additive ship**).
- Manager Journal hub (slot 23): Boss Journal **and** Chronicle — add, don’t replace Boss Journal.
- `BossDeathEvent`: grant finds only when matching `lullaby_*` is **active** — **no** boss YAML loot edits.

### 4) Quests `lullaby_*` 1–9 + story stage

Implement exactly as `STORY_P1_PLAN.md` (quest IDs, givers, objectives, soft AL gates).

```
lullaby_old_tale
lullaby_gear
lullaby_insignia
lullaby_short_toll
lullaby_old_seal
lullaby_letters
lullaby_third_anchor
lullaby_forgotten
lullaby_silent_road
```

- Core stays thin: `StoryAccess` on `AetherServices` only; stage math lives in Quests.
- Old boss-hunt quests are already removed. Keep `closed_road` (Pathwarden) and `border_rites`. Do **not** resurrect retired hunts.
- Completing `lullaby_silent_road` sets per-player `veil_quay_unlocked` (no exile world load).

## Out of scope (do not build)

- Exile void world wiring / districts / jump-pad transfer (map may exist on MMO-C preview — leave it alone)
- Act III bosses, three-form finale, bell events, fade VFX
- Deleting unused bosses/NPCs, Ashen Sheath redesign
- Ankerhold map rebuild, OpeningOrbit
- Auto-placing the five story NPCs in the world

## Hard bans (LOCKED)

- TalkUx shell (bubble, chips, typewriter, per-player visibility) — **content lines OK, shell forbidden**
- Boosters, Anvil sockets, ranks/wipes (`player-ranks.yml`), ShutdownCountdown
- Blossom Blade / Gravwell Cleaver abilities/VFX
- Borderlands vials stack/rite contract
- Farmer / Harlow / Rite Warden / Proctor dialogs
- Thinning richer live DevMenu / give lists

If a LOCKED system would need changing: stop, say `LOCKED … SYSTEM CONFLICT: …`, wait.

## Canon tone (hard)

1. Aetherion is a grief story wearing a villain mask.
2. Corvin is not excused and not a cartoon monster.
3. Victims stay right to hate him.
4. The world still laughs — without air, grief doesn’t land.
5. Show, don’t lecture. Each NPC knows a piece; none know everything.
6. Additive ship — never replace a richer live page with a thinner one.

## Done when

1. Touched modules compile (Quests + Items + Core if Core changed).
2. Dialog cleanup done for FREE cast (**EN + DE**); 5 story NPCs placeable + voiced (**EN + DE**); lore items (**EN only**) + Chronicle; lullaby 1–9; finds wired.
3. Short summary for Robb: how to place NPCs with `/questnpc`, how to test with DevMenu, what was deferred.
4. **No deploy / restart / commit.**

## Process

Scout → ship **1 → 2 → 3 → 4**. Prefer one coherent change set. No drive-by refactors. Match existing repo style.

Start now.
