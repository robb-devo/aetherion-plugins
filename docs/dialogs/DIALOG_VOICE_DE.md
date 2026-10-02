# Dialog voice pass (EN) + German overlay — AetherionQuests

Content and localization pass on top of the existing talk pipeline. `DialogManager` pacing, gates and quest state are unchanged, and so are quest ids, NPC ids, accept/decline handlers and talk commands. The in-world bubble, reply chips, quest card, typewriter and per-NPC voice also work exactly as before.

## TL;DR

- **EN:** the placed cast's lines were tightened for the bubble. Walls of text are split into beats of about 70 characters or less. A few facts were wrong and are fixed: Temper hands out the Emerald *on accept*, the Skills tab only blinks *after* accepting Ledger, the Lumberjack no longer says "go collect" after you've already been paid, and one Quartermaster greeting said "coal's filed" before it was. Late visits no longer repeat stale "next stop" lines (new graduate / after-shift variants). The "not yet" gate went from 5 beats down to 4.
- **DE:** `lang/de.yml` now covers every surface the EN cast has:
  - intros and their state variants
  - completed chatter
  - inline turn-in, brief and redirect lines
  - return visits, reply chips and NPC reactions
  - small-talk topics and *Bye*
  - approach greetings, idle barks, NPC-to-NPC banter
  - quest card titles and descriptions for all 40 quests
  - talk-UI labels and action-bar hints

  Switching `/sprache` swaps the voice. Quest logic is untouched.
- **TalkUx:** there is one string-only hook, 23 fixed literals (+ CastBook chip labels) now read through `LangPack.ui(...)`. EN output is byte-identical. No layout, timing, input or visibility code changed.
- **Additive:** `de.yml` keeps every existing key; the validator checks this. Nothing was deleted.

---

## Gate 0 — inventory (EXISTS / CLAIMED / MISSING)

Base: `origin/main` @ `f1f9d46`. The Quests module is byte-identical (CRLF-normalized) to the live-lineage worktree `mining-eldervale-progression-65660c`, and all 10 touched files match.

| Surface | Source | EN before | DE before | After |
|---|---|---|---|---|
| NPC intros (72 ids) | `DialogManager#getDialogLines` | EXISTS, several walls (Egon, QM, Temper, Vex, Craftsman, Lark, Maren) | 26 ids, old tone; variants flattened (farmer, foreman, lamplighter lost their state branches) | EN split into beats; DE **all 72** (variants as nested keys) |
| Completed chatter (45 NPCs) | `DialogManager#getCompletedLines` | EXISTS; stale "next" lines on late visits (Lark, Craftsman, Farmer); Lumberjack "go collect" after payout | 11 NPCs; flat lists overrode EN's stage variants (Egon always said "Quartermaster next") | EN graduate / after-shift variants; DE **all**, variant-aware |
| Return-visit intro | `CastBook.returning` | EXISTS (11 quest NPCs); Temper/Ledger lines assumed accepted state | **MISSING** (EN-only gate in DialogManager) | EN facts fixed; DE `cast.<npc>.returning` |
| Reply chips accept/decline | `CastBook.accept/decline` via TalkUx | EXISTS | **MISSING** (always EN) | DE `cast.<npc>.accept/decline` + generic default |
| Reaction to accept/decline | `CastBook.onAccept/onDecline` | EXISTS | **MISSING** | DE |
| Small talk + Bye | `CastBook.topics/farewell`, `DialogManager#offerTopics` | EXISTS | **MISSING** (EN-only gate) | DE topics (same count per NPC) |
| Approach greetings | `CastBook.greet*` via `LivingNpcAtmosphere` | EXISTS; Lark/QM greeting facts off | **MISSING** (EN barks to DE players) | DE; stage logic identical |
| Idle barks | `CastBook.idle` | EXISTS | **MISSING** | DE; shared display picks majority language of the audience |
| NPC-to-NPC banter (12) | `CastBook.banter` | EXISTS | **MISSING** | DE, index-aligned, same speakers |
| Inline turn-in / brief lines (~50) | `NpcListener` | EXISTS; Foreman + Ledger briefs were walls | **MISSING** (only Egon's kit handoff) | EN split; DE `talk.*` |
| Ledger in-world help desk | `NpcListener#offerLedgerDesk` + `EgonBriefingGUI.talkTopics` | EXISTS | **MISSING** | DE labels, echo, lines (chest desk GUI untouched) |
| Gate + redirect lines | `QuestStoryGate`, `QuestManager` | EXISTS; "not yet" stacked 5 beats with a duplicate "complete the tutorial" | `tutorial_blocked` only | EN trimmed; DE `dialogs.ledger_blocked`, `talk.redirect.*`, `talk.gate.*` |
| Vex death whisper | `VexDeathHintListener` | EXISTS ("Soft gear." unclear) | **MISSING** | EN clarified; DE |
| Quest card title/desc | `QuestRegistry` → `LangPack.questTitle/Description` | EXISTS | 10 of 40 quests | DE **all 40** |
| Quest card labels, chip fixed labels, action-bar hints | `TalkUx`, `DialogManager`, `NpcListener`, `QuestStoryGate` | EXISTS | **MISSING** | DE `ui.talk_ux.*`, `ui.tip.*` |
| Rite Warden, Proctor (Borderlands rites / vials / escort) | DialogManager, CastBook, NpcListener | EXISTS | intros only | **CLAIMED** by the Hub / Borderlands pass: EN + DE left as they were. In DE sessions these two stay quiet for ambient barks and topics instead of barking English |
| NPC role subtitles (nametags) | `LivingNpcProfile` | EXISTS | — | Out of scope: shared entities, one text for all viewers |
| Chest Quest Offer, Egon briefing chest, NPC editor | GUIs | EXISTS | mostly DE already | Untouched (no GUI rebuilds) |
| Isle casts (Farm, Fish, Mine, Forage plugins), Items casino/liquidator GUIs | other plugins | — | — | Out of scope |

---

## EN voice bar (what "premium" meant here)

- **Bubble-first.** A line types out in about 22 ticks and each beat lasts 40 ticks, with the previous line shown in grey above. So one beat should be one thought. The placed cast is now ≤ 90 visible characters per line; most lines are 45–70.
- **Dry, short, named.** `{player}` shows up where it lands, in openers and nudges, not in every line.
- **Return visits shorter than intros**, and they don't assume state the player doesn't have yet.
- **Late visits don't re-issue old orders.** The graduate / after-shift / after-coal variants cover this.
- **Facts match the code.** Emerald on accept, 3 Compressed Wheat (not "stacks"), Tackle (not "Fisher"), and Temper is on the road to Capital.

Changed EN, by NPC: Egon intro (4 beats), Quartermaster intro + completed, Craftsman intro (4 beats) + after-shift variant, Foreman crafting-unlocked variant + completed + turn-in (split), Temper intro + accept reaction + return visit, Miss Ledger intro / return visit / field brief / graduation hints / blocked lines, Farmer intro + graduate variant, Lark intro + greeting + completed + graduate variant, Vex intro (4 beats) + death whisper, Maren intro, Lumberjack completed, Twig greeting, tutorial gate + redirect lead. Two non-placed NPCs got one-word fact fixes (Dock Whisper "Tackle", Larder "three").

## How the German overlay resolves

```
LangPack.dialogs(p, "farmer_intro.lark_done", EN)    → dialogs.farmer_intro.lark_done  (variant)
LangPack.dialogs(p, "farmer_intro", lines)           → dialogs.farmer_intro is a section → empty → keeps the variant
LangPack.completed(p, "egon.after_coal", EN)         → completed.egon.after_coal
LangPack.say(p, "foreman.shift_done", EN)            → talk.foreman.shift_done
LangPack.ui(p, "talk_ux.goals", "Goals")             → ui.talk_ux.goals
CastBook.*(npc, …, player)                           → cast.<npc>.<field>   (DE only; no entry = quiet)
CastBook.banterGerman(i)                             → banter[i] with CastBook speakers
```

- Every EN caller still passes its English literal. When the key is missing, or the player reads EN, the output is exactly the English. The one exception is `cast.*`: a German reader with no German entry gets **silence**, so no English barks or chips leak into a German session. Chips then fall back to the generic `ui.talk_ux.accept_default/decline_default`.
- **Shared barks** (idle, banter) show one TextDisplay to everyone in range. The language is the audience majority, and ties go to English. That's why `{player}` never appears in `cast.*.idle` or `banter`: multi-viewer barks can't fill it. The validator enforces this.
- `/sprache de|en` (`LanguageCommand`) and `/language reload` are unchanged. Reload re-reads `de.yml` from the jar.

## German style sheet

- **Voice.** Informal *du*, dry, short. It matches the EN beat count, and the lines are written in German rather than translated word for word. There are a few idioms: *Immer eine Handbreit Wasser unterm Kiel*, *Die Sau im Sack*, *Schäbig trifft's*.
- **Proper nouns as the game shows them:**
  - Places: Anker Harbour, Ore Ridge, Shabby Mine, Capital, Borderlands, Farm Isle, Eldervale.
  - Nametags: Quartermaster, Shaft Foreman, Temper, Miss Ledger, Tackle.
  - GUI and items: Manager, Recipe Book, Anvil, Skills / Pets tab, Catch Sphere, Simple Axe, Mining Pickaxe, Emerald Booster, Compressed …

  Teleports, the compass, nametags and menus are English, so German speech points at what's actually on screen.
- Generic nouns are German: Eichenstämme, Kohle, Erz, Weizen, Kiste, Rüstung, Pfeil. The mechanic verb is *fusen* (German gamer usage for the Anvil's fuse).
- Neutral forms where German would gender the player: *Kundschaft*, *Kampfbereit*, *Gestempelt und entlassen*.
- **Length.** German runs about 20% longer than English. DE lines stay ≤ 95 visible characters; the validator warns above that. Umlauts are measured as 8 px by `TalkText.advance`, a slight overestimate, so the bubble wraps early and never overflows.

## TalkUx — the one hook

`talk/TalkUx.java`, strings only. A private `ui(player, key, english)` helper wraps `LangPack.ui(player, "talk_ux." + key, english)`, and these literals go through it:

- chips: *Got it.*, *Show me the numbers*, *Drop "X" & take this* (+ echo), *Keep my current job* (+ echo), *Details…*, the armed label *Sure? Click again*
- CastBook accept/decline labels, now `(…, player)`
- the offer-stands chat line and its buttons
- card headings: *Goals*, *Pays*, *Replaces your current job*
- the *You:* echo
- four action-bar hints
- *That conversation moved on.*

`chipText` gained a `Player` parameter, which the armed label needs. Nothing else changed: no layout, scale, timing, hitboxes, visibility, input handling, sessions or config. For EN readers every string is the same literal as before.

## Files

| File | Change |
|---|---|
| `lang/LangPack.java` | + `german`, `say`, `sayLines`, `castLines`, `castText`, `castTopics`, `banterLines` |
| `npc/CastBook.java` | EN fact fixes; player-aware overloads (old signatures kept); DE banter |
| `dialog/DialogManager.java` | EN rewrites + variants; return visits and topics work in both languages; accept/decline chat lines |
| `listener/NpcListener.java` | inline lines keyed; Foreman/Ledger walls split; Ledger desk localized |
| `util/QuestStoryGate.java` | gate/redirect lines keyed; trimmed "not yet" |
| `npc/LivingNpcAtmosphere.java` | idle/banter language by audience |
| `talk/TalkUx.java` | string hook (see above) |
| `listener/VexDeathHintListener.java`, `manager/QuestManager.java` | one line each |
| `resources/lang/de.yml` | full overlay (every old key kept) |
| `docs/dialogs/tools/validate_de_overlay.py` | coverage/safety validator |

## Checks run

- `mvn -o -pl AetherionQuests package` passes (there are no unit tests in the module).
- `python docs/dialogs/tools/validate_de_overlay.py` passes. It checks:
  - every base key is kept
  - every Java-referenced key has DE
  - all 72 intro ids and all completed NPCs are covered
  - CastBook fields are mirrored for 27 voices, and topic counts match
  - banter is 12/12 with beat counts matching
  - no `{player}` in shared barks
  - all 40 quests have DE

  I also ran a mutation test: I deleted four keys and it failed on each one.
- `de.yml` parsed with Bukkit `YamlConfiguration` (paper-api 1.21.1) confirms each accessor LangPack uses. A variant section returns an empty `getStringList`, the variants resolve, `getMapList` returns the topics, `getList("banter")` returns lists, and umlauts survive.
- **Not tested in-game.** There's no Paper server in this sandbox.

## Click-test sheet

**EN (fresh alt or `/aquest reset all`)**
1. Egon: 4 short beats. Pick *Point me at the oak.* → reaction line.
2. Decline Temper, click again: *Still no fuse, {you}? The Emerald's waiting on your yes.* Accept: *Emerald's in your bag…*
3. Ledger before the Foreman: 3 beats plus the Foreman redirect. Surveyor mid-tutorial: 2 beats, then *Where you're actually meant to be:* and the next stop.
4. Foreman turn-in: two beats, *That's a shift…* / *Road to Capital next…*
5. After graduation: Lark, Farmer and Craftsman completed lines don't send you back to Ledger or the Foreman.

**DE (`/sprache de`)**
1. Egon offer: German bubble, chips *Zeig mir die Eiche.* / *Später, Egon.* / *Details…*. The card reads *Egons Holzlauf*, *▸ Ziele*, *▸ Lohn*.
2. Decline → *Der Pier läuft nicht weg. Ich auch nicht.* Click again → the short German return visit.
3. Finish a flavour talk (e.g. Harrow) → German topics + *Tschüss.*
4. Stand near the pier: German greeting by name. Egon and the Fishmonger bicker in German (if most people nearby read DE).
5. Ledger after graduation → desk chips *Aetherion Manager / Skills / Pets / Booster / Mehr Themen…*
6. `/sprache en` → the same NPCs are back in English, and quest progress is unchanged.

## Open

- **Rite Warden + Proctor** (CLAIMED by the Hub / Borderlands pass). They have no German cast voice yet, so they stay quiet for ambient barks in DE. Their intros keep the old DE text. Add `cast.rite_keeper` / `cast.arena_proctor` once that pass settles their EN.
- Some reward names on the quest card (*Coins*, *Spawn: …*) and objective names are item or registry strings and stay English.
- Non-placed NPCs (Dock Whisper, Bait Theory, Dungeon Gate) keep EN lines over 90 characters; their DE is trimmed.
