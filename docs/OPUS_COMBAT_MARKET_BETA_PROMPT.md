# Opus — Combat CD HUD · AH/Bazaar polish · Beta Journal (medium-high)

Workspace root:

`C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Owner: **Robb**. Stay in this root.  
**No deploy. No restart. No commit.** Edit source directly. Follow `docs/OPUS_PROMPT_HYGIENE.md`.

Three small polish surfaces. Same visual language as the rest of Aetherion (night/bronze/harbour-clean — not purple SaaS, not emoji spam). EN canonical; DE where player-facing strings already have lang paths.

---

## Mission

1. **Ability cooldowns** — clean, always readable while an ability is on CD.  
2. **Auction House + Bazaar** — same visual language as the polished menus (Manager / Dev shelves vibe), not a vintage chest dump.  
3. **Beta NPC + Journal** — elevate the old checklist into a live, trackable beta journal with optional small rewards.

Do not invent new combat systems, new economies, or wipe LOCKED systems.

---

## Read first

| Area | Files |
|---|---|
| CD HUD | `AetherionItems/.../combat/AbilityCooldownHud.java`, `.../listener/HealthListener.java` (ActionBar HP + CD), any ability listeners that call `AbilityCooldownHud.arm*` |
| Markets | `AetherionItems/.../economy/MarketService.java`, related GUIs / anchors (`createBazaarAnchor`, `createAuctionAnchor`, browse/sell flows) |
| Beta | `AetherionBeta/` — `BetaNpcService`, `BetaBook`, `ChecklistMenu`, `Milestone`, `BetaStore`, `/checklist`, `/beta` |
| Hygiene | `docs/OPUS_PROMPT_HYGIENE.md` |

LOCKED (stop if conflict): TalkUx shell, boosters stack 64, Anvil sockets, ranks/`player-ranks.yml`, Blossom Blade / Gravwell combat feel, ShutdownCountdown, Borderlands vials.

---

## Part 1 — Ability cooldown display

**Wanted:** When an ability is on cooldown, the player sees it **cleanly** — not buried, not spammy.

Already exists:
- `AbilityCooldownHud` gates + hotbar material cooldown overlay
- `HealthListener` stitches CD into the ActionBar next to ❤ HP / 🛡 defense

Elevate:
- Keep ActionBar as the primary “on CD / ready” readout (between / under HP as designed). Make the fragment readable: name + seconds (or a short bar), ready flash stays.
- Ensure **every** player ability that uses a CD actually arms the HUD (grep ability listeners / weapons — wire misses).
- Hotbar vanilla CD overlay stays in sync when a stack is passed.
- If a resource-pack custom cooldown glyph/bar already exists in the client pack, prefer aligning server text with it — do **not** rebuild the whole texture pack here.
- No second bossbar unless ActionBar is proven insufficient; ActionBar first.

Done when: use an ability → CD visible on ActionBar + hotbar; swap weapons → each tool keeps its own gate; ready flash is clear.

---

## Part 2 — Auction House + Bazaar polish

**Wanted:** Opening AH / Bazaar feels like the same game as the polished Manager — titles, panes, lore, EN(+DE if market already has lang hooks).

Scope:
- Presentation + UX clarity only (browse, sell, collect, empty states, page chrome).
- Keep economy math / fees / listing rules unless a bug blocks polish.
- Glass borders: calm, not casino-spam. No 10×/s animations.
- Anchors / NPC open paths stay working.

Done when: both channels look intentional and match Aetherion’s current UI language; no broken buy/sell.

---

## Part 3 — Beta NPC → live Journal

**Wanted:** The harbour Beta NPC + book feel current (2026 beta), not day-one leftovers.

Elevate `AetherionBeta`:
- NPC talk / give flow: short, sharp, EN+DE if the plugin already dual-langs (`BetaLang` / `Texts`).
- Journal item: still a book or a clean GUI — **live tracking** of milestones (auto-check when the player does the thing, as today), scannable progress, `/checklist` still works if book lost.
- Optional **small rewards** on milestone clusters or full clear (coins / shards / cosmetic fluff — **not** P2W power). Keep rewards modest; wire through existing coin/shard APIs if present.
- Admin `/beta` analysis path stays.

Do **not** replace TalkUx for living quest NPCs. This is the Beta courier / checklist loop only.

Done when: talk to Beta NPC → journal feels premium and tracks; a couple of rewards land; old awkward copy is gone.

---

## Process

1. Part 1 CD HUD + wire misses.  
2. Part 2 market GUIs.  
3. Part 3 Beta journal.  
4. Compile touched modules if `.m2` works; else one-line note.  
5. End note: what changed, how to spot-check (ability CD, open AH/Bazaar, `/beta npc` + journal). **No deploy.**

---

## Done when

- [ ] Ability CD readable on ActionBar (+ hotbar overlay) for armed abilities  
- [ ] AH + Bazaar visual polish, economy intact  
- [ ] Beta NPC + journal elevated, tracking live, light rewards OK  
- [ ] LOCKED systems untouched  
- [ ] No deploy / restart / commit  

---

## Copy-paste starter

```text
Read docs/OPUS_COMBAT_MARKET_BETA_PROMPT.md (+ docs/OPUS_PROMPT_HYGIENE.md).
Worktree: mining-eldervale-progression-65660c.

Three polish passes, medium-high:
1) AbilityCooldownHud + HealthListener — clean CD on ActionBar/hotbar; wire any missing ability arms.
2) Auction House + Bazaar — visual/UX polish to match current Aetherion menus; keep economy math.
3) AetherionBeta NPC + journal — elevate checklist into a live trackable journal with small optional rewards.
No deploy/restart/commit. LOCKED stays locked. Go.
```
