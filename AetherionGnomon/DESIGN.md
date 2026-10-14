# GNOMON — design bible (short)

> *gnomon* (Greek γνώμων): "the one who knows" — the arm of a sundial whose shadow tells the hour.

## Fantasy (one line)

A 20-block surveying instrument that walks on three needle legs, hangs its heart from a chain, and **measures you**. Its
shadow is its weapon. Its ledger is the floor. By the end of the fight, the arena is a drawing of everything you did,
and it is using that drawing against you.

## The one rule players learn

**The eye never lies.** The lens always looks at where the real hit lands.
Legs can feint. Openings can be fake. The eye can't. Strong players watch the eye; weak players watch the leg.

## Silhouette and material

| Part | Look | Job |
|------|------|-----|
| Hub | octagonal blackstone body + brass collar, ~13 blocks up | the "head"; leans before every action |
| Legs ×3 | 2-bone needle legs (thigh 8.5 / shin 9.5), brass knees, dripstone spike feet | procedural IK gait; stomps pin points on the "map" |
| Lens | octagonal white lens + iris | **the tell**: iris color = intent, gaze = target |
| Needle | 7-block gnomon spike on top | casts the Hour Hand shadow; tilts toward sweeps |
| Chain + Plumb bob | lodestone weight with a dripstone point, hangs ~3 blocks over the floor | **the hitbox / heart**; swings with real pendulum physics, recoils when hit |

## Color language (three colors, nothing else)

* **Chalk white** — observation. Paths it saw you take, glowing on the night floor.
* **Crimson** — decision. Committed telegraphs, exploited reads, Phase II ink.
* **Gold** — opening. The bob glows gold and gets an outline: hit here.

Iris: white = OBSERVE · amber = PRESS · violet = DENY · red = PREY / exploiting a read.

## Motion language

Body slow and heavy (springs, weight shift, foot plants with hit-stop). **Eye fast** (it moves first, every time).
Anticipation on every big move: the hub leans or crouches before it acts. Secondary motion: the pendulum lags and swings.

## Audio language

Silence by default. Spyglass zoom = it is focusing on you. Cartography-table scratch + page turn = it wrote something
down. Mace heavy-smash + anvil = foot plants. Vault shutters / trial-spawner = commitments. Bell = openings and the dial.

## Adaptive mind (Pillar B)

```
OBSERVE (per player, every 2 ticks) → MEMORY (decaying evidence, half-life ~40 s) → READS (confidence 0..1)
→ POSTURE (observe / press / deny / prey) → UTILITY pick from authored attack families → HONEST TELEGRAPH
→ OUTCOME (did the read pay?) → MEMORY
```

Reads (per player, party aggregate for focus):

| Read | Evidence | What the Gnomon does about it |
|------|----------|-------------------------------|
| Dodge side L/R | lateral displacement vs. threat during windups | stomps **lead** into your dodge side; **feint stomp** (leg over you, eye on your dodge spot) |
| Orbit CW/CCW | angular velocity around the hub | Hour Hand sweeps **against** your orbit; Interdict walls cut the route ahead |
| Hugger / Kiter | time under the hub vs. at range | Plumb Drop under itself / Survey Line fissure at range |
| Greedy | hits in the last third of an opening | **False opening**: gold glow but the eye stays on you → trap ring |
| Early / late dodger | exit time inside the windup | holds the windup longer (tracks you) / rushes it (never under the fairness floor) |
| Camper | sector occupancy | ink floods your sector; Phase II walls |

Tells (required, all wired):

* **Noticed** (conf ≥ 0.35): lens snaps to you, spyglass + scratch, your trail flares, one personal subtitle per read
  (*"…left. Always left."*).
* **Exploiting** (conf ≥ 0.55): iris red, a crimson mark is drawn where it expects you, then the attack.
* **Forgotten** (conf < 0.25): trail dims, page turn, *"…changed. Interesting."* — you laundered the habit.
* **Outplayed**: an exploit that whiffs because you did the opposite → the lens flinches, the read is halved and the
  bob drops into a short gold **stagger**. Baiting the mind is rewarded with damage.

Fairness invariants: minimum telegraph and commit times (config floors); never re-aims after commit; damage only
inside the drawn shape; ground waves are jumpable; memory decays so changing behavior always works.

## Phases (first playable)

1. **SURVEY** (100 → 50 %) — first ~35 s is teaching (OBSERVE: long telegraphs, chalk trails, no exploits). Then
   it starts to press and prey.
2. **The Ledger Opens** (transition) — it rears up, every chalk line turns to ink, the dial turns, triple slam.
   *"I have read enough."*
3. **VERDICT** (50 → 0 %) — predatory postures, stickier memory, **Recital** (it replays your own recorded path as a
   trail of detonations: walk your habit again and you walk into your past), Interdict walls, double sweeps.
4. **Death** — legs buckle one by one, the lens cracks, the whole ledger lifts off the floor and dissolves upward.
   Last line depends on how well it read you: *"…I knew you."* or *"…unmeasurable."*

## Engineering contract

* Dedicated plugin `AetherionGnomon`; BossEngine owns HP pool, phase gates, damage tracker, HUD bar, loot, telemetry
  (`BossScript` contract — same path as Helios). The plugin owns rig, arena, AI, attacks, cleanup.
* Template `bosses/gnomon.yml` is dropped into BossEngine's folder on `onLoad` (versioned, live edits backed up).
* Every display: tag `ae_gnomon`, `persistent=false`. Temp blocks: journaled, restored on end / enable / `/gnomon cleanup`.
  Per-player sky / border / weather: restored on leave, death, quit, world change, end.
