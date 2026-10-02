# NPC / Dialogue Overhaul — placed Aetherion cast

Scope: the NPCs that are **actually placed** (live `npcs.yml`), owned by `AetherionQuests`.
Farm / Fish / Mine isle casts, Miss Canopy (Foraging) and the Dungeon Keeper (Dungeons) are untouched.

## TL;DR

- **Talk UX:** living NPCs speak in a **speech bubble above their head**. It types out with a per-NPC voice, and only the listener sees it. Quest offers become **reply chips beside the NPC**, plus a floating **quest card**. You pick a reply by looking at it and clicking, by scroll wheel + click, or with the mirrored chat buttons. The chest Quest Offer is still there: it sits behind *Details…*, and it's the fallback whenever the in-world path can't run.
- **Anonymous skins:** every placed NPC used to wear a **famous creator's skin**, assigned automatically (Grian, Technoblade, DanTDM, WilburSoot, Dinnerbone, …). That list is gone. There are now 39 original skins painted for Aetherion. They get signed through FancyNpcs' own MineSkin upload and cached. If one isn't signed yet, the NPC shows a default body in its old leather outfit. It never shows a borrowed face.
- **Dialogue:** the placed cast has been rewritten in a dry, short voice. Lines use the player's name. Return visits get a shorter intro. Completed-quest chatter depends on how far the player is. NPCs react to your answer, you get small-talk topics, and there's a *Bye*.
- **Life:** work beats (swings with job sounds, hand swaps, crouch, spyglass/eat/drink), idle glances, tiny ground-checked patrols, and gestures and emotes while talking. Social layer: greet-by-name on approach, rare idle barks, and NPC-to-NPC banter.
- **Additive:** no quest ids, placed NPCs or talk paths were removed. The chest GUIs, chat accept commands, gates and turn-ins all run unchanged underneath.

---

## Gate 0 — placed inventory

Source: live `plugins/AetherionQuests/npcs.yml` snapshot (`aetherion-ops/tmp-quest-npcs.yml`, 2026-09-26) × `QuestNPCRegistry` × `LivingNpcProfile`.
The worktree Quests module is byte-identical to `origin/main` (CRLF), so the code base is main.
Re-checked on 2026-09-29 after the Foraging and Codex+Skills ships. The live lineage (`e1ac96f` + Mining/Foraging/Codex ships) still has an untouched Quests module: all 96 sources and 3 resources match `e7a32c6`, and the worktree Quests jar is bytecode-identical to a build of `e7a32c6` (146/146 classes). Those ships touch only Items, Foraging and docs.

| id | name | where (x y z) | quest / dialog | old skin (auto) | new |
|---|---|---|---|---|---|
| egon | Egon the Equipper | 300 63 -382 | welcome_aboard / egon_intro | Grian | cast/egon — flat cap, grey beard, leather apron |
| lumberjack | Lumberjack ("Forager") | 350 66 -328 | gather_wood / lumberjack_intro | GeminiTay | cast/lumberjack — red check flannel, beanie |
| quartermaster | Quartermaster | 170 64 -360 | forge_coal / quartermaster_intro | MumboJumbo | cast/quartermaster — navy coat, spectacles |
| craftsman | Craftsman | 218 64 -388 | (gift flow) / craftsman_intro | BdoubleO100 | cast/craftsman — soot, apron, bracers |
| foreman | Shaft Foreman | 30 55 190 | first_shift / foreman_intro | Xisuma | cast/foreman — hard hat + lamp, hi-vis vest, pipe |
| booster_tutor | Temper | 54 60 114 | lesson_boost / booster_tutor_intro | SSundee | cast/booster_tutor — goggles, singed apron |
| ledger | Miss Ledger | -2 58 0 | lesson_manager / ledger_intro | GoodTimesWithScar | cast/ledger (slim) — plum blazer, bun, glasses |
| farmer | Farmer | -214 61 176 | farm_hand / farmer_intro | impulseSV | cast/farmer — straw hat, overalls |
| lark | Lark | -226 58 150 | pocket_zoo / lark_intro | ZombieCleo | cast/lark — field vest, satchel |
| fisher | Tackle | 278 64 -418 | a_good_catch / fisher_intro | VintageBeef | cast/fisher — yellow sou'wester slicker |
| fishmonger | Fishmonger | 278 63 -370 | (shop) | Docm77 | cast/fishmonger — bandana, striped apron |
| vex | Sergeant Vex | 162 58 74 | lesson_steel / vex_intro | DanTDM | cast/vex — crimson officer coat, scar |
| rite_keeper | Rite Warden | 252 65 174 | border_rites / rite_keeper_intro | JeromeASF | cast/rite_keeper — hooded robe, bone charm |
| arena_proctor | Proctor | -300 58 -102 | (escort) / arena_proctor_intro | SkyDoesMinecraft | cast/arena_proctor — waistcoat, bow tie |
| farm_isle_guide | Harrow | -256 61 186 | — / farm_isle_guide_intro | Tubbo | cast/farm_isle_guide — braid, green overalls |
| surveyor | Surveyor | -78 50 266 | (desk) / surveyor_intro | Cubfan135 | cast/surveyor — wide hat, monocle |
| vince | Lucky Vince | -6 60 74 | (casino) / vince_intro | jeb_ | cast/vince — fedora, gold tooth |
| bar_whisper | Bar Whisper | 196 64 -368 | — / bar_whisper_intro | Nihachu | cast/bar_whisper — robe, glowing eyes |
| eldervale_welcome | Maren | 56 90 490 | — / eldervale_welcome_intro | WilburSoot | cast/eldervale_welcome — fur coat, braid |
| eldervale_upgrade | Forgehand | 54 90 500 | (forge GUI) | Ph1LzA | cast/eldervale_upgrade — heat goggles, apron |
| merchant | Merchant | 250 64 -354 | (chests) / merchant_intro | TangoTek | cast/merchant — turban, gold trim |
| isle_clerk | Deed | 28 58 52 | — / isle_clerk_intro | Technoblade | cast/isle_clerk — green eyeshade, tie |
| forage_pad_guide | Twig | 366 65 -308 | — / forage_pad_guide_intro | Ranboo | cast/forage_pad_guide — leaf hood |
| liquidator | Crystal Liquidator | -24 58 34 | (crystal desk) | PearlescentMoon (explicit) | cast/liquidator — crystal shoulders, monocle |
| canopy_clerk | Canopy Clerk | 568 90 -204 | canopy_sample / canopy_clerk_intro | Purpled | cast/canopy_clerk — moss waistcoat |
| root_cellar | Root Cellar | -295 107 522 | (pantry GUI) | GeorgeNotFound | cast/root_cellar — headscarf, flour apron |

**Skipped:** `amethyst_mines_guide` ("Crystal Guide", 108 37 590). It's in the live `npcs.yml`, but there's no registry entry in this code (it's from another branch), so this build never renders it.

**Other placed NPCs, out of scope:**
- Editor NPCs (e.g. `mod_smoker`). Their presets used creator names too; they're now mapped to anonymous `preset_*` skins.
- Miss Canopy (`MHF_Oak` skin, Foraging).
- Dungeon Keeper (Dungeons).
- Isle casts (Farm, Fish, Mine plugins).

### EXISTS / CLAIMED / MISSING

| Area | Before | After |
|---|---|---|
| Talk channel | chat lines + chest Quest Offer | **in-world bubble + reply chips + quest card**; chat mirrored; chest kept (Details… / classic / fallback) |
| Quest accept / decline | chest slots 11/15, chat `/aetherionquest accept\|decline` | same handlers (`handleGuiAccept/Decline`), now also from chips; `/aetherionquest details <id>` added |
| Skins | auto-assigned famous accounts | original cast PNGs → MineSkin via FancyNpcs → `skins-signed.yml` |
| Voice | 8 spine NPCs | all 26 placed NPCs + typed-speech blips |
| Memory | quest flags only | `npc-memory.yml`: talks, last talk, last greet, heard-intro flags, talk-UI pref |
| Life | spine particles/sounds, Lark parrot, Liquidator runes | + work beats, glances, patrols, gestures, emotes, greetings, idle barks, banter |
| Ledger help desk | chest | in-world topics (Manager / Skills / Pets / Boosters) + *More topics…* → chest |
| DevMenu extras | — | `/questnpc extras`: Hollis (town crier), Bram (sweeper), Wick (lamplighter) — anchors, not placed |

---

## Talk UX

- Clicking a living NPC plays its lines exactly as before (DialogManager pacing, gates, LangPack). Each line is also typed into a bubble above the NPC's head. The bubble shows the NPC's name in its colour, and the previous line in grey above the current one. While you talk, the nametag and quest marker are hidden for you, because the bubble replaces them.
- **Quest offer:** a card on the left shows title, description, goals and pay. Chips on the right: *[NPC-flavoured accept]* · *[decline]* · *Details…* (opens the old chest).
  - If you already have a quest: *Drop "X" & take this* (asks you to click twice) · *Keep my current job* · *Details…*.
  - If a gate blocks you: the NPC says the gate line, then *Got it.* · *Show me the numbers*.
- **Picking a reply:** look at a chip (it highlights and ticks) and left- or right-click. Or scroll the hotbar and click; scroll selection only works on quest offers, never on small talk. Or click the chat buttons. Every quest chip mirrors to the durable `/aetherionquest accept|decline|details <id>` command it stands for, whatever the layout, so they still work after the bubble is gone. On a gate fail, *Got it.* declines. On a conflict, the chat accept goes through the existing CONFIRM ABORT step.
- **After an answer:** you get an echo line ("You: Point me at the oak."), then the NPC's reaction line and an emote. Turning a quest in shows ♥ and sparkles.
- **Small talk:** after flavour talk or completed-quest chatter you get up to 3 topics plus *Bye.*. They're short-lived (18 s), never grab the hotbar, and clicking the NPC again simply starts over.
- **Classic mode:**
  - Players: `/npctalk classic` (chat + chest), `/npctalk world` to switch back.
  - QA bots (`QaQuest…`, `StressM…`) are always classic, because the runner clicks the chest.
  - Admin: `/npctalk status` (skin signing progress), `/npctalk reload`.
- **When it falls back to the chest:** talk-ux off, classic mode, FancyNpcs missing, NPC not living/locatable, or the player is more than 10 blocks away.
- **Entities:** `TextDisplay` / `Interaction`, per-player (`setVisibleByDefault(false)`), non-persistent, tagged `ae_talk_ui`. Leftovers are purged on enable and disable.

Config (`config.yml`; everything has a code default, so a live config without these keys still works):
`talk-ux.enabled, voice-blips, chat-replies, show-previous-line, max-distance, reply-timeout-seconds, linger-ticks, bubble-scale, pack-glyphs, classic-name-prefixes` · `npc-life.enabled, patrols, social`.

### Resource pack (optional chrome)

`pack/delta/` adds font `aetherion:talk`: bubble tail, click icons, a chevron, and emote bubbles (! ? … ♥ ♪).
- **New files only.** The merged zip = your attached pack + 17 new entries: 0 changed, 0 removed.
- **Off by default** (`talk-ux.pack-glyphs: false`), because players without the pack would see boxes. Every glyph has a unicode fallback.
- Turn it on once the server pack ships the delta.
- I did not ship a screen-edge "RPG box" HUD. It needs negative-space font offsets that depend on GUI scale, and I can't test that from here. The in-world bubble works on vanilla clients.

---

## Skins

- **Source:** `docs/npc/skins/paint_skins.py`, deterministic pixel art composed in code from layers (tone, face, hair, hat, torso, legs, props). No third-party textures, no accounts.
- **Output:** `AetherionQuests/src/main/resources/skins/cast/*.png`
  - 26 placed NPCs
  - 3 idea NPCs
  - 10 editor presets
- **Signing:**
  - On enable, the PNGs are copied to `plugins/FancyNpcs/skins/aetherion/`.
  - When an NPC spawns, `SkinManager#getByFile` queues a MineSkin upload, which FancyNpcs does itself.
  - `SkinGeneratedEvent` → value + signature saved to `plugins/AetherionQuests/skins-signed.yml` → placeholder leather comes off → the NPC is re-sent.
  - Later boots apply the signed texture instantly and don't upload anything.
- **If skins stay pending:** check `/npctalk status`. MineSkin may be rate-limiting or require a key. A free key in `plugins/FancyNpcs/config.yml` → `mineskin_api_key` fixes it. Until then, NPCs are anonymous default bodies in their old leather.
- **Editor presets:** they now use `aetherion:preset_*`. Saved editor NPCs that still carry an old preset default (Grian, MumboJumbo, …) are routed to the matching anonymous preset. A username a moderator typed on purpose is still honoured.

## Life + atmosphere

- **`LivingNpcLife`** (new):
  - Work beats per NPC, only while someone within 24 blocks can see them. They pause during conversations and escorts.
  - Glances when nobody is within 5.5 blocks.
  - Patrols (≤ 3 blocks, feet planted, no liquids, no air, never across unloaded chunks) for Egon, Vex, Farmer, Fishmonger, Merchant and Proctor, plus the sweeper and crier.
- **`LivingNpcAtmosphere`** (extended):
  - The spine sounds now come with a matching arm swing.
  - Greet-by-name within 6.5 blocks, at most once per 12 minutes per NPC per player, and at most one greeting per player every 25 s.
  - Idle barks for players 4–14 blocks away (one per player per minute at most).
  - 12 NPC-to-NPC banter scripts, each pair at most every 6 minutes.

---

## Deploy

1. Land the ship folder: `apply-npc-dialogue.ps1 -DryRun`, then run it without `-DryRun`. It's hash-guarded, and any file that changed since this build is reported as CONFLICT and not overwritten.
2. `mvn -DskipTests package` → `AetherionQuests/target/AetherionQuests-1.0.0.jar`. The ship folder also has a prebuilt jar from the same source, compiled against your worktree's Core/BossEngine jars and the post-Codex Items jar (`f0f0c641…`). Surface diff against the live-lineage Quests jar: classes 146 → 167, resources 15 → 54, commands 4 → 5 (+`npctalk`), config keys 8 → 22, and **MISSING 0** everywhere. Existing config values are unchanged.
3. Deploy Quests only (`ae.ps1`, jar only). No Core, Items or Hub changes.
4. First boot: watch the log for `Cast skins: wrote N PNG(s)` and, over the next few minutes, `Cast skin signed: …`.

## Click-test sheet

Use a fresh alt, or `/aquest reset all` on your account.

**First hour, in order**
1. Arrive at Anker Harbour. Egon calls out, and his bubble appears above his head.
2. **Egon**: "{you}, right? Egon…" types out with his voice. You get a quest card and three chips. Pick *Point me at the oak.* → QUEST ACCEPTED, then "Good. Lumberjack's up the hill. Don't let him hug you."
3. **Lumberjack**: handoff lines in the bubble, then the chop demo. Chop 10 oak.
4. **Egon**: turn-in → ♥ + sparkles → kit lines ("Ten logs. Counted twice…").
5. **Quartermaster** → **Ore Ridge** → turn-in → **Craftsman** (gift) → **Shaft Foreman** → **Temper** → **Miss Ledger** → **Farmer** + **Lark** → **Miss Ledger** stamps you out.
   - At each stop: the offer comes as chips, the turn-in plays the celebration, and the NPC's next-stop line appears in the bubble.
6. Decline one offer, then click the NPC again. You should get the short return-visit intro, not the full speech.
7. `/npctalk classic` → the same NPC gives chat + the chest again. `/npctalk world` → back to bubbles.

**Three wow moments to show friends**
1. Walk up to the pier. Egon greets you *by name*, and his fishmonger neighbour starts bickering with him about a cod.
2. Take any quest by *looking at a floating reply and clicking*. The NPC answers your answer in its own voice.
3. Stand near Vex: he drills sword sweeps. Near the Quartermaster: he checks the harbour through a spyglass. Near Twig: he bounces on his heels with slime squelches.

**Regression checks**
- **Quest paths** (none are gone): accept, decline, conflict swap, gate-blocked (Ledger early, Rite Warden early), turn-in (Egon, QM, Foreman), Proctor vial escort, Surveyor desk, Forgehand forge, Root Cellar, Fishmonger shop, Liquidator desk, Vince.
- The chest Quest Offer still works through *Details…*.
- Chat `[Accept]` still works after walking away.
- Stress bots (`QaQuest`) still accept through the chest.

## Open / not done

- **German:** done in the follow-up pass, see [`docs/dialogs/DIALOG_VOICE_DE.md`](../dialogs/DIALOG_VOICE_DE.md). It covers return visits, topics, chips, greetings, barks, banter, turn-ins and all quest cards. (Originally: the DE overlay kept the old tone, and return-visit intros and topics were English-only.)
- **Unmerged pass:** `cursor/placed-npc-dialog-pass-f9dd` is still unmerged. Its `de.yml` can't be taken wholesale, because it would delete the Harbour Hour keys.
- **Not in-game tested:** there's no Paper server in this sandbox. Every FancyNpcs reflection target was checked against the 2.9.2 jar. The Paper API is compile-verified against 1.21.1.
- **Skins on the live server:** they depend on MineSkin being reachable from the server (see *Skins*).
