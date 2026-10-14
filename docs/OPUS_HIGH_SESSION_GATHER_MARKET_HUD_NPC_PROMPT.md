# Opus / Claude High — Gathering · AH/Bazaar · CD HUD · NPC ambient

**Paste everything below the line into a new Claude chat.**  
Mode: **High** (not Extreme). Budget: **~70% session left** — finish products one-by-one; stop cleanly if quota ends.

Workspace root:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Stay in this root.  
Follow `docs/OPUS_PROMPT_HYGIENE.md`.

---

```text
# AETHERION — High session polish pack (4 products)

You have ~70% session balance on **High**. Robb will deploy/restart himself — you never deploy, never restart, never hang on servers.

## Process (mandatory)

1. Finish **one product completely**, then the next. Do not parallel-scatter.
2. Order: **① Gathering minigames → ② AH/Bazaar → ③ Combat CD + ActionBar layout → ④ NPC ambient life**.
3. After each product: short note (what changed + how Robb spot-checks). Then start the next.
4. If time runs out mid-list: leave a crisp residual list. Do **not** burn tokens on packaging, ship folders, or things Composer can do (deploy, pack ZIP, Hetzner upload, restart).
5. No Maven gate if Paper `.m2` is blocked — edit source, note “compile skipped”, continue.
6. **No commit / no push** unless Robb asks in-chat.

## LOCKED (stop if conflict)

- TalkUx shell (speech bubble, chips, typewriter) — **do not rewrite**. Dialog *lines* untouched unless a typo blocks ambient.
- Boosters stack 64, Anvil sockets, ranks/`player-ranks.yml`, Blossom Blade / Gravwell combat feel, ShutdownCountdown, Borderlands vials.
- Manager GUI chrome pack fonts — already shipping; do not rebuild the texture pack.

## Product ① — Gathering minigame feel

Plugins: `AetherionFishing`, `AetherionForaging`, `AetherionFarming` (Mining light only if sharing HUD patterns).

Hot files (start here):
- Fish: `FishingController`, `CastSession`, `StrikeBar`, `FishingHud`, lure/encounter
- Forage: `ForagingStrike`, `FellPulse`, `ForagingHud`, `ForagingFx` — look-bar already isle-gated; polish fail→feel / optional minigame clarity without forcing a hard lock forever
- Farm: `BirdScareEvent`, `ScarecrowEvent`, crop listener

Goals:
- Same telegraph → interact → succeed/fail → reward beat across three loops
- Readable HUD (no fight with QuestBars/TAB) — use existing suppress patterns
- Juice with restraint; light ticks; no leaks on quit/world change
- Feel Hypixel-adjacent clarity, Aetherion harbour tone (not casino neon)

Done when: each loop feels intentional midgame, not tutorial leftovers.

## Product ② — Auction House + Bazaar (Skyblock-grade sell UX)

Anchors: `AetherionItems/.../economy/MarketService.java` (+ related GUIs / sell / confirm flows).

Manager chrome titles already exist — polish **UX + flow**, not re-art the pack.

Robb’s pain:
- **Holding an item to sell only that item feels bad.** Prefer inventory/browse sell like Hypixel Skyblock: pick from bag / GUI, not “must hold in hand.”
- Last polish pass: empty states, browse clarity, sell/list/confirm, collect — calm glass, not spam.

Keep economy math / fees / listing rules unless a bug blocks the UX.

Done when: sell without the hold-in-hand gate (or with a clear Skyblock-like alternative that feels first-class); AH + Bazaar feel like one product.

## Product ③ — Combat cooldown + hearts/defense ActionBar (stable layout)

Hot files:
- `AetherionItems/.../combat/AbilityCooldownHud.java`
- `AetherionItems/.../listener/HealthListener.java` (❤ HP / 🛡 defense + CD stitch)
- Ability listeners that arm the HUD (wire misses OK)

Robb’s pain:
- When a cooldown appears, the whole ActionBar **jumps / shifts** — hearts and defense look wild.
- Wanted: **fixed slots** for HP and defense; CD **adds** into a reserved lane without reflowing the bar.
- CD should feel clean and premium, not “everything teleports.”

Elevate:
- Stable ActionBar layout (pad / fixed segments / reserved CD region — pick the least invasive that stops the jump)
- Keep hotbar vanilla CD overlay in sync
- No second bossbar unless ActionBar truly cannot hold it

Done when: toggle CD on/off → HP+defense stay put; CD appears/disappears smoothly.

## Product ④ — NPC ambient life (not dialog)

Allowlist-first (see also `docs/OPUS_NPC_LIFE_ELEVATE_PROMPT.md`):
- `AetherionQuests/.../npc/LivingNpcLife.java` (primary)
- `npc-life:` in Quests `config.yml` only if needed
- Optional peek: `LivingNpcService` move/hand helpers; `LivingNpcAtmosphere` only if ambient (not TalkUx)

Robb likes: TalkUx bubbles / how they speak — **leave that alone**.
Robb wants: ambient behaviour — Egon (and peers) still feel a bit **random**; patrol/work beats more job-true, less twitchy; light **content ambient** (presence, not new quest plots).

Do **not**: rewrite dialogs, DE voice packs, TalkUx architecture, Hub Origin cast scripts, red thread.

Done when: watching NPCs feels alive and intentional; talking still pauses work cleanly.

## Out of scope (Composer / Robb handles)

- Deploy jars, Hetzner resource pack `:8088`, SHA1, server restarts
- Exile map world polish (other Claude profile)
- Manager chrome Storage/Loadouts/Pets wiring (already being done locally)
- Signature weapons, ranks, anvil sockets

## End state

Four products finished or clear residuals. Source edited in-tree. No deploy. Short spot-check list per product.
```
