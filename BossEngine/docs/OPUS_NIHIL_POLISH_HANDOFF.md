# Opus handoff — Nihil / World Eater encounter polish

**Preserve the encounter.** This is not a redesign. Hunger / Starless / Unmade / Ouroboros / world-eating / death / loot stay.

**Quality target:** Fair ≠ easy. Brutal, but readable.

---

## Current encounter state

Canonical director: `instance/worldeater/WorldEaterDirector.java`  
Body/rig: `Serpent.java` · FX: `WeFx.java` · Site: `WorldEaterSite.java`  
Template: `resources/bosses/world_eater.yml`  
Phase flow: ARRIVING → FIGHT (moves) → TRANSITION → Ouroboros (phase 4) → DYING

Combat hitbox: LivingEntity teleports to **skull only** (`placeHitbox`). Body is BlockDisplays — no vanilla hitbox.

---

## Polish already performed (Cursor / this pass)

### 1. Lunge / bite vulnerability
- Stuck window extended ~1.5s (30 ticks):
  - P1: `46 → 76`
  - P2: `38 → 68`
  - P3: `32 → 62`
- `expose(stuck + 6)` still tracks the stuck phase (white eyes + resonate).
- Knockback / crater / bite power **unchanged**.

### 2. Head vs body damage honesty
- Head (LivingEntity): full damage via existing `absorbDamage` path.
- Body: new `tryBodySwing(Player)` — arm-swing toward a vertebra registers **~25%** of attack attribute damage with distinct sculk/dragon-hurt feedback.
- Wired from `BossCombatListener.onNihilBodySwing` (`PlayerAnimationEvent`).
- First body hit teaches: *"The body yields. The head is the wound that opens it."*
- `BossInstance.worldEater()` accessor added.

### 3. Ouroboros readability
- Ring idle: soft white dust + reverse-portal on skull/mouth every 14 ticks (target cue).
- `regurgitate()`: stronger layered audio, END_ROD stream mouth→tail, white/void dust, brief eye expose, one-shot action bar on first success.
- Mechanic unchanged: damage → `ringDamage` → every 2% max HP regurgitates a segment / opens border.

### 4. Void breath audio
- Charge: sonic charge + beacon ambient + cave score; mid heartbeat; pre-fire dragon growl.
- Fire: louder sonic boom + explode + growl + thunder score.
- Travel: dirt + stone break at carve tip.
- Aftermath: anvil/explode + cave residual, then gravel/dirt settle.

### 5. Expose cue
- On first expose: resonate + warden heartbeat + white dust (not only eye recolor).

---

## Files touched

| File | Why |
|------|-----|
| `WorldEaterDirector.java` | stuck timing, body swing, breath/expose/ouroboros feedback |
| `BossCombatListener.java` | arm-swing → body hit |
| `BossInstance.java` | `worldEater()` accessor |

**Not touched:** Serpent rig, Unbroken, other bosses, loot, site build, YAML HP gates, BossEngine core loop.

---

## Remaining for Opus (high-quality final pass)

Do **not** invent new mechanics. Prefer sensory clarity + micro-timing.

1. **In-game verify** the stuck window feels punishable after crater knockback (adjust ±10 ticks only if needed).
2. **Projectile/body:** bows into body segments still do nothing — optional ray/projectile body hit at 25% if it stays small.
3. **Ouroboros first-time:** if ring still confuses, strengthen *visual* progress (which vertebra returns) without chat spam.
4. **Audio language pass:** selective growls on major windups / phase gates only — no tick spam.
5. **Hitbox vs silhouette:** if skull LivingEntity feels small vs visual head, nudge `placeHitbox` offset slightly — do not make whole boss head-only.
6. Report unrelated bugs separately; do not “clean up” intentional weirdness.

---

## Explicit do-nots

- No boss rewrite / phase replacement / new attack kit
- No global BossEngine refactor
- No arena redesign / loot change
- No making Nihil easier by cutting damage or removing danger
- Locked weapons / other showcase bosses stay out of scope
