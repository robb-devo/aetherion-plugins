# Opus prompt — Early Game (Phase 1)

Copy everything below the line into the Cloud workspace chat.

---

You are improving **Aetherion’s Early Game** — the first **1–2 hours** for a brand-new player.

## First read (in order)

1. `BossEngine/docs/OPUS_EARLY_GAME_AUDIT.md`  
2. `BossEngine/docs/OPUS_EARLY_GAME_HANDOFF.md`  
3. Then open **only** the files listed under “Must understand” / “Likely touch targets” in the handoff. Do **not** ingest the entire monorepo.

## You are NOT here to

- Rebuild the whole server  
- Redesign Mid Game or Late Game  
- Add new legendary weapons or world bosses  
- Reshuffle the tutorial quest graph / `tutorialDone` set  
- Touch locked systems (signature weapons, boosters/sockets/anvil, ranks/TAB, shutdown countdown, Hanging Saint / Seraphine loot)  
- Grow AetherionCore into a game loop  
- Deploy, restart, or administer production  
- Do drive-by refactors of unrelated plugins  

## You ARE here to

Make the first hour feel **obviously cared for** — like a designed game, not a pile of plugin features.

Priority order:

1. Player experience  
2. Clarity (“what do I do next?”)  
3. Atmosphere  
4. Immersion  
5. Natural progression (existing spine)  
6. Feedback / juice  
7. Technical quality  

## Existing spine (keep)

Join → **Egon** (pier) → Forager / `gather_wood` → starter kit → Quartermaster / mine → Foreman → Temper + Ledger + Fields → **Miss Ledger stamps tutorial done** → world opens.

Harbour soft walls and `QuestStoryGate` stay. Polish *around* them.

## Suggested focus bundle (“Harbour Hour”)

Implement a **coherent** set (quality over quantity). Preferred targets:

1. **Harbour arrival vignette** — unify join welcome + FIND EGON into one pier-toned beat; Egon attractor  
2. **Egon kit ceremony** — when starter gear is granted, make it a moment (Display / sound / timing). Hook `StarterGearReward` / Egon turn-in — don’t `/give`-spam  
3. **Living atmosphere** for spine NPCs — extend the existing `LivingNpcAtmosphere` pattern (Lark / Liquidator already exist) to Egon + Forager (+ optional Foreman / Ledger)  
4. **Tutorial trail upgrade** — same harbour corners as `TutorialQuestTrail`, better craft (small Display breadcrumbs OK). Do **not** invent a new navigation system  
5. **Dialog presence helper** — optional shared glance/FX on paced lines (`DialogPace`); wire carefully; don’t rewrite every dialog string  
6. **UTF-8 / copy cleanup** where onboarding hints are garbled  

Optional if time: spawn-discover + explore-chest “world noticed you” FX (shared small kit).

Optional combat teach: **framing only** around existing Borderlands / Vex / `DamageNumbers` — **no new signature weapons**, no boss redesign.

## Quality bar

Reference (read-only): Hollow Reliquary, Seraphine music box claim pacing, CombatTheatrics timing, SaintFx **display push via `setTransformationMatrix` only** (do **not** revive TRS decompose — it broke the Hand/strings).

Early game should feel like the **same craft**, smaller scale, harbour tone (dry, practical, a little funny) — not epic empty monologue.

## Hard technical rules

- Prefer extending existing classes over parallel systems  
- One speech rhythm: `DialogPace.LINE_GAP_TICKS`  
- Starter tools still go through `StarterGearReward` + `StarterSetBalance`  
- BlockDisplay matrices: `setTransformationMatrix(m)`  
- Prefer not to invent SSH/Crafty access — Robbi’s local agent can deploy  
- If this Cloud environment already has a working deploy path to the mmor/Crafty test server, a careful jar deploy + restart is **allowed**; otherwise just push the branch and stop  

## Branch

Work on **`claude/early-game-harbour`** (already has this audit / handoff / prompt).  
Commit + push your implementation back to that same branch so Robbi can pull and deploy.

## Deliverable

- Implement the Harbour Hour bundle (or a clearly argued subset that still feels complete)  
- Commit + push to **`claude/early-game-harbour`**  
- Then **STOP**

Tell me briefly:

- What you shipped (player-facing)  
- Files/hooks touched  
- What you deliberately left alone  
- Branch commit(s) / whether you deployed  
- Anything that still needs a human playtest on the pier  

Nothing more.
