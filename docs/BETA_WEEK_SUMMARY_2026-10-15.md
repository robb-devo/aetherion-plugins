# Aetherion — last days overview (Robb · ~2026-10-13 → 10-16)

Ship tree: `mining-eldervale-progression` / live-hardening. Nothing in this list is committed unless noted.

---

## Harbour onboarding (Acts I–III) — live

- Replaced old wood→coal→ore→wheat→pet spine.
- **I Harbour:** Egon TalkUx → Forager Fell minigame → kit + Capital TP.
- **II Capital:** Ledger = skill loadout + Codex (+ Lv5 auto-pickup teach); Temper = boosters/Anvil.
- **III Roads:** Vex = combat **stub** (`lesson_combat` / `CombatTeach.java`, fill Sat); stamp → road picker.
- Side quests (QM / Foreman / Farmer / Lark / Tackle) after stamp only.
- Doc: `docs/ONBOARDING_ACT_MAP.md`.

## Skill isle lessons (Acts IV–VII) — live (22:01 ship)

| Isle | Teacher | Do |
|------|---------|-----|
| Forage | Miss Canopy | 3 green-window fells + Crown Find |
| Farm | Elsie | Harvest Rhythm → Steady + Prize Crop |
| Fish | Maren | 3 Warm-water fish + rare bite |
| Mine | Old Wick | Mining Power check + Strike Rhythm + geode |

- Not Quests slots — per-isle plugins; Ledger road picker shows ✓.
- Canopy chest briefing gone; Steel→Mining road; Fishing coords shifted to real isle.
- Jars: Quests, Hub, Farming, Fishing, Foraging, Mining. Backups `*.bak-pre-isle-lessons-*`.

## Crossing / Quay / Borderlands — earlier this week

- Causeway `/exile causeway` (cliff → pier).
- Ferryman “Cross the Veil” + crouch copy (EN/DE).
- Quay paste no longer eats BL (`BL_SAFE_WEST` / pad clear = deck only).
- FAWE undo restored BL; Seraphine clear = void-gap + entities.
- Explore chests Common→Mythic scatter (50 live).

## SamuraiBoss

- EN default, HP **50k**, damage **×7**, BarChrome BOSS frame, less debris.
- Live config patched.

## HUD / bars (prior)

- BarChrome / TextWidth / textured boss + ritual bars (pack + Core).

## Combat elevate

- Opus Max session done off-box (~$100 / heavy tokens). **Apply Saturday.** Tutorial combat beat waits on that.

## Ops notes you hit

- Crafty stdin sometimes desync → use **RCON** for console cmds.
- Opus can full-ship (build → backup → deploy → restart) when the prompt says so.

---

## Patched 2026-10-16 night (this agent)

| Fix | What |
|-----|------|
| Fishing TP | `fishing_eldervale` → landing **-380 90 -815.5** yaw 180; Hub reload |
| Mining TP gate | `eldervale` **unlocked-by-default: false** (pad/stamp matters); location already at landing |
| Temper booster lie | EN+DE: socketed boosters **stay** until you pull them — upgrades do **not** eject them. Quests redeployed + MMOR restart |

---

## Still open / decide later

- Walk smoke: harbour stamp → all four isle lessons.
- Sat: combat jar + fill `CombatTeach` TODOs.
- Commit when you want.
- Farm/Fish lesson replay cmds (alt / DevMenu wipe for now).
- Giga texture-pack Max session → `docs/OPUS_TEXTUREPACK_GIGA_PROMPT.md`.
