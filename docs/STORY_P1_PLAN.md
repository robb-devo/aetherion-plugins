# Story Package 1 — Plan

**Status:** prep only · no code · no deploy · no commit  
**Canon:** `docs/STORY_BIBLE.md` · inventory: `docs/STORY_INVENTORY.md`  
**Scope name in bible:** Package 1 (Fundament) + Package 2 (Akt I) + Package 3 (Akt II) — merged into one Opus ship because they share the same quest spine and NPCs.

---

## Goal

Ship the playable Act I–II spine of **Das Schlaflied** (`lullaby_*`) on the live systems we already have: quests, TalkUx, LivingNpc, BossDeathEvent, Manager/Codex, LangPack. Players can walk from Hollis → Berit → Ysolde → Fallen Anchor → Pathwarden → Veil Quay flag. No exile island, no Act III bosses, no finale, no bell-event runtime, no fade VFX.

---

## In scope

| Area | Deliverable |
|---|---|
| Story stage | Per-player stage derived from `lullaby_*` quest state; thin Core interface + Quests impl |
| Server flag | Persist `first_lullaby_finale` (API + storage stub only — no hymn playback yet) |
| Chronik | New Codex/Manager journal tab; pages unlock from lore finds |
| Lore items | Factories + written books for Act I–II finds / letters |
| Fundstücke | Quests listener on `BossDeathEvent` — drop only when matching quest active |
| 5 NPCs | Ysolde, Berit, Nell, Hesper, Ferryman — registered, LivingNpc, `/questnpc` placeable, **not** auto-placed |
| Quests 1–9 | `lullaby_old_tale` … `lullaby_silent_road` |
| Dialog polish | Additive Act I / Act II variants on **FREE** existing NPCs only |
| Pathwarden writ | Completing `lullaby_silent_road` sets veil-quay unlock flag (no exile world load) |

## Out of scope (later packages)

- Exile void world, districts, Helios gate, Hollow Sun / Seraphine / Unbroken / Nihil placement
- Quests 10–18, Elin’s Doll, Summons altar, three-form Aetherion
- Dawn/Dusk toll mutation, False Toll event, Silent Minute, Veil fog light
- Berit fade VFX / skin swap (dialog “doesn’t know you” line only if cheap; no particle veil)
- Side-quests with moral branches (`Briefe an Majestät`, etc.) — stub IDs OK, no full ship
- Ankerhold map rebuild, OpeningOrbit, Farmer/Harlow, Rite Warden, Proctor
- Deploy / restart / commit

---

## Locked / do not touch

- TalkUx shell (bubble, chips, typewriter, visibility)
- Boosters, Anvil sockets, ranks/wipes, ShutdownCountdown
- Blossom Blade / Gravwell Cleaver
- Borderlands vials
- Existing boss YAML loot tables (fundstücke via listener only)
- Existing quest IDs / tutorial spine — **add** `lullaby_*`, do not thin or replace
- CastBook / DialogManager lines for locked NPCs (Farmer/Harlow, Rite, Proctor, isle casts)

---

## Architecture

### 1. Story stage (Core thin, Quests owns logic)

**Core** (`de.aetherion.core.api`):

```java
public interface StoryAccess {
    /** Coarse stage for bells/NPC variants. Quests computes; others only read. */
    int stage(Player player);           // 0 = none / tutorial not ready
    boolean flag(String key);           // server-wide, e.g. first_lullaby_finale
    void setFlag(String key, boolean value);
}
```

Register on `AetherServices` like `QuestProgressAccess`. Core holds **no** quest ID list and **no** stage math.

**Quests** implements `StoryAccess`:

| Stage | Meaning | Unlock condition (suggested) |
|---|---|---|
| 0 | Pre-story | Tutorial not done / no lullaby progress |
| 1 | Legend | `lullaby_old_tale` completed |
| 2 | Short toll aware | `lullaby_short_toll` completed |
| 3 | Eldervale named | `lullaby_old_seal` completed |
| 4 | Family known | `lullaby_letters` completed |
| 5 | Worlds fallen | `lullaby_third_anchor` completed |
| 6 | Fade personal | `lullaby_forgotten` completed |
| 7 | Road open | `lullaby_silent_road` completed |

Exact mapping can live in one class `LullabyStory` / `StoryStageService`. Other plugins call `AetherServices.story().stage(player)` — never parse quest YAML themselves.

**Server flag:** `players` or plugin `data/story-flags.yml` under Quests (or BossEngine if preferred). P1 only needs the read/write API; hymn use comes in Nachspiel.

### 2. Chronik (Manager journal tabs)

Today Manager slot 23 = **Dungeon Journal** only (`AetherionManagerGUI.JOURNAL_SLOT` → `JournalGUI`).

**Additive UX (preferred — no new Manager slot):**

1. Click Journal (still gated by existing `progress.journal(player)`).
2. Open a small **Journal Hub** (2 buttons):
   - **Boss Journal** → existing `DungeonJournalGUI` / journal GUI
   - **Chronicle** → new `ChronicleGUI`

Alternatively: add `CodexChrome.Tab.CHRONICLE` and open Chronicle from Codex hub + Journal hub. Do **not** delete Boss Journal.

**Chronicle data model (Items or Quests — pick one owner):**

- Page id = lore find id (`music_box_gear`, `sun_guard_insignia`, …)
- Unlock when player has completed the DELIVER that hands the find to Ysolde, **or** when find is granted (same tick)
- GUI: icon + short EN blurb + “Found at …” — no stats, no gear

Storage: per-player YAML or PDC set of unlocked page ids. Prefer Quests player storage if finds are quest-tied; Items if Chronik sits next to Codex ledgers. **Recommendation:** Quests owns unlock set; Items owns GUI chrome (matches Collection/Bestiary split).

### 3. Lore item factories (Items)

New factory methods (names illustrative — match repo style):

| Item id | Display | Role |
|---|---|---|
| `lore_tiny_paper_crown` | Tiny Paper Crown | McNugget find (optional Act I) |
| `lore_anchor_chain_link` | Anchor Chain Link | Bridge Troll find |
| `lore_music_box_gear` | Music Box Gear | Hollow Lurker → deliver |
| `lore_sun_guard_insignia` | Sun Guard Insignia | Skuldugery → deliver |
| `lore_sealed_writ_silent_year` | Sealed Writ of the Silent Year | Pathwarden → deliver |
| `lore_maelis_letter_1..3` | Queen’s letters | World chests / give via quest |
| `book_mad_king` | The Mad King | Readable children’s book (Rat version) |
| `book_queen_letters` | Letters of the Queen | Bound written book (optional; letters can be single-page papers) |

Rules:

- No combat stats. PDC tag e.g. `aetherion:lore_find` + `find_id`
- DevMenu: **add** a Lore / Chronicle give page or row — do not overwrite Special Sets / Weapons pages
- EN names/lore canonical; DE via existing Items lang path if present, else EN-only for P1 items is OK if LangPack pattern isn’t wired for Items yet — prefer matching existing custom-item DE if any

### 4. Fundstück listener (Quests)

Listen to `de.aetherion.bossengine.event.BossDeathEvent`.

| Boss id (YAML/key) | Quest must be ACTIVE | Item |
|---|---|---|
| hollow_lurker | `lullaby_gear` | Music Box Gear |
| skuldugery | `lullaby_insignia` | Sun Guard Insignia |
| pathwarden | `lullaby_silent_road` | Sealed Writ |
| mcnugget | (optional / free find) | Tiny Paper Crown |
| bridge_troll / troll | (optional) | Anchor Chain Link |

Grant into `lootByPlayer` for damagers (or killer only — match existing quest-kill patterns in QuestManager). **Do not** edit boss YAML loot.

Existing quests that already KILL these bosses stay; lullaby quests are parallel.

### 5. Five NPCs

| npcId | Display | Type | Role |
|---|---|---|---|
| `ysolde` | Ysolde Thorne | QUEST | Archive — mainline giver |
| `berit` | Mother Berit / Mütterchen Berit | QUEST | Emotional anchor |
| `nell` | Nell | FLAVOR / QUEST | Child mirror of Elin |
| `hesper` | Hesper Kael | QUEST | Fallen Anchor counter-voice |
| `ferryman` | Ferryman | FLAVOR | Veil Quay — placeable; exile talk later |

Wire:

- `QuestNPCRegistry.registerNPC` + add to `ideaNpcIds()` (or dedicated `storyNpcIds()`) so `/questnpc extras` (or equivalent) hands anchors
- `LivingNpcProfile` entries (names, colors, look)
- `DialogManager` intros / chips / completed lines
- `CastBook` voice (accept/decline/greet/idle/topics)
- `lang/de.yml` `cast.*` + talk keys — same beat count as EN (`DIALOG_VOICE_DE.md`)
- `QuestStoryGate`: allow these NPCs after `tutorialDone` (not in first-hour soft lock)

**Not placed by default.** Robb places anchors when Ankerhold spots are decided.

### 6. Quests 1–9

Register in `QuestRegistry` (or `LullabyQuests.register(QuestManager)` called from `registerAll`).

| # | Quest ID | Gate | Giver | Objectives | Learns |
|---|---|---|---|---|---|
| 1 | `lullaby_old_tale` | Tutorial done | Hollis (`town_crier`) | TALK `berit` | Legend + song without end |
| 2 | `lullaby_gear` | after 1 | Berit | KILL `hollow_lurker`, DELIVER gear → `ysolde` | Mine sound = her song |
| 3 | `lullaby_insignia` | after 2 | Ysolde | KILL `skuldugery`, DELIVER insignia → Ysolde | King was real |
| 4 | `lullaby_short_toll` | after 3 | Ysolde | TALK `sister_aurel` / bellkeeper id | Dusk toll short |
| 5 | `lullaby_old_seal` | AL ≥ 20 | Deed (`isle_clerk` / deed id) | TALK `ysolde` | Kingdom Eldervale |
| 6 | `lullaby_letters` | AL ≥ 30 (soft) | Ysolde | DELIVER 3 letters (item targets) | Wife + daughter |
| 7 | `lullaby_third_anchor` | AL ≥ 40 soft | Ysolde | TALK `hesper` | He dropped worlds |
| 8 | `lullaby_forgotten` | AL ≥ 50 soft | Nell | TALK `berit` | Berit doesn’t know you |
| 9 | `lullaby_silent_road` | AL ≥ 60 soft | Ysolde | KILL `pathwarden`, DELIVER writ → Ysolde | Road + veil quay flag |

**AL gates:** soft availability via `QuestStoryGate` / ProgressAccess — do not hard-block island/guild systems. Match bible suggestions; Robb can retune later.

**Hollis:** existing idea NPC `town_crier` / display Hollis — use that id; do not invent a second crier.

**Sister Aurel:** resolve live npcId (`bellkeeper` / OriginRole — scout at impl time). If missing as LivingNpc, register thin flavor NPC or redirect TALK to existing bell NPC.

**Deed:** resolve isle clerk npcId from Guilds/Quests (`deed` / isle clerk).

**Compass:** existing `QuestCompass` must resolve TALK/KILL/DELIVER targets for new NPC ids and boss ids (same as pathwarden quests already).

**Rewards:** small XP / flavor titles only — no progression flag theft. Completing #9 sets story flag `veil_quay_unlocked` (per player).

### 7. Dialog polish (FREE NPCs only)

Additive **variant lines** keyed by story stage (or quest completed), never replacing current defaults.

| NPC | Act I | Act II+ |
|---|---|---|
| Sister Aurel / bellkeeper | Seven bells line | Dusk toll was four |
| Stellan / observatory | Shooting stars | Too warm |
| Wick / lamp_lighter | Lanterns till dawn | Old law from a queen |
| Hollis / town_crier | Mad king bedtime threat | Someone forgot her name |
| Egon | Never sail past Veil | Ferry at royal quay |
| Bar Whisper | Bar remembers you | Bar stops remembering some |
| Deed / isle clerk | Old seal, ignore it | Kingdom of Eldervale… stop looking |
| Surveyor | Trolls hoard chain | Royal forge-work |
| Lucky Vince | Bet mad king is story | Bet’s off |
| Miss Ledger | — | No records before Silent Year |

**Do not** add lines to Farmer, Harlow, Rite Warden, Proctor, farm/fish/forage isle casts.

Tone: bible § Ton-Regeln + `docs/dialogs/DIALOG_VOICE_DE.md`. EN canonical; DE du, dry, same sentence count.

Berit “fade” in P1: completed-chatter / greeting that treats player as stranger when stage ≥ 6 — **dialog only**. Skin/particle later.

### 8. Pathwarden → Veil Quay

On `lullaby_silent_road` complete:

- Grant / consume Sealed Writ as designed
- Set per-player flag `veil_quay_unlocked`
- Optional: unlock a Hub/compass waypoint stub named Veil Quay / Schleierkai — **no** void world load, no ferryman auto-teleport

Ferryman dialog can mention waiting; destination talk is Package 4+.

---

## File touch map (expected)

| Plugin | Likely files |
|---|---|
| AetherionCore | `StoryAccess.java`, `AetherServices` register/clear |
| AetherionQuests | `LullabyStory` / stage impl, `QuestRegistry` or `LullabyQuests`, Fundstück listener, 5× NPC registry/profile/dialogs/CastBook, `lang/de.yml`, `QuestStoryGate`, player flag storage |
| AetherionItems | Lore factories, Chronicle GUI + Journal hub, DevMenu lore give row, CodexChrome tab if used |
| BossEngine | none (event already exists) |

---

## Acceptance checklist (for Opus + Robb)

1. Fresh graduate can accept `lullaby_old_tale` from placed Hollis → talk Berit → complete.
2. With `lullaby_gear` active, Hollow Lurker death grants Music Box Gear; deliver to Ysolde completes.
3. Same for Skuldugery + insignia.
4. Chronicle shows unlocked pages after delivers; Boss Journal still opens.
5. AL20 path: Deed → Ysolde seal talk works when island unlocked.
6. Letters deliver quest completable via DevMenu-given letters.
7. Hesper talk completes third-anchor quest.
8. Nell → Berit “forgotten” completes; Berit’s greeting is stranger-flavored at that stage.
9. Pathwarden kill + writ deliver sets `veil_quay_unlocked`; no exile world required.
10. `/questnpc` (or extras) can spawn all 5 story NPCs; none appear until placed.
11. DE `/sprache` shows DE for new cast lines; beat counts match.
12. No TalkUx / booster / rank / weapon / vial diffs.
13. No deploy, restart, or commit from Opus unless Robb says go.

---

## Open questions (not blocking P1)

From bible — Robb can answer later:

1. Ankerhold vs old Origin for place anchors  
2. Open-world Aetherion spawn vs castle-only  
3. F3 dungeon boss rename  
4. King / Mourner body tech  
5. AL vs tier gate tightness  
6. Name lock (Corvin / Maelis / Elin / …)

P1 uses bible names as written. Placement coords left to Robb after map decision.
