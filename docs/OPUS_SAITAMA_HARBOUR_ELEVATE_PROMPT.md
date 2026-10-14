# SAITAMA — Harbour elevate (short Extra pass, FRESH chat)

Parts 1–3 shipped. Harbour **feels crowded / not clean wow**. This pass is **harbour craft only** — not curiosities, not island-wide.

Workspace: `C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c`

Deliver: `saitama-harbour-elevate.patch`. No deploy. TalkUx shell LOCKED.

---

```text
# SAITAMA HARBOUR ELEVATE — CLEAN WOW (not more NPC spam)

Fresh Opus Extra. Owner: **Robb**.

Parts 1–3 are DONE (bay, cast, errands, patrols, curiosities, props, tales). Do NOT expand exploration.
Robb’s live feedback after play:

1) Harbour looks like “mass NPCs dumped” — not elevated / cinematic / clean.
2) Quill (harbourmaster) stands right behind Egon with his own errand chip while the tutorial points to the Quartermaster — confusing dual “…-master” + wrong sightline.
3) Crane still feels random + stutters. Place it where cargo / pier geometry **earns** a crane; fix motion hitch. New config keys if live yml would keep stale coords (same pattern as Part 2 dock-crane).

## Mission

Make Anker Harbour **read as a composed harbour scene** in the first 30–90 seconds:
- Clear silhouette from spawn pier: water life (Halcyon/skiffs) + **one** strong cargo beat (crane that makes sense) + sparse readable workers — not a convention crowd.
- Tutorial spine stays sacred: **Egon is the only “talk to me first” gravity well.** Ambient/side NPCs must not compete for the same camera cone or chip energy.

## Concrete work

### A) Cast density + placement (harbour zone only)
- Audit seats/rounds for IDs that crowd the pier/plaza/Egon cone (esp. `harbourmaster` / Quill).
- **Either** rename Quill’s role (not “Harbourmaster”) **or** move him off Egon’s back (wharf office / crane deck / north quay) **or** gate his errand chip until tutorial/quartermaster beat is past — pick the cleanest fix; Robb hates “…-master” stacking next to Quartermaster.
- Thin or retime harbour patrols so at any glance you see **composition**, not a mob. Workers at stations > orbiting the spawn.
- Keep cast useful elsewhere on the island; this is harbour framing, not a mass despawn.

### B) Crane
- Re-survey map: place base/jib/pick/drop where two ships / cargo piles / boardwalk **logically** need a crane (not “somewhere on the pier”).
- Fix stutter (entity sync / per-piece move like Halcyon note; sleep when far). Haul’s seat follows the new base.
- Update `origin.yml` keys so live merge picks new coords (don’t rely on stale `bay.crane` / `dock-crane`).

### C) Bay timing (light touch only if needed)
- First-minute Halcyon arrival should still stun; don’t fight Part 1. Only retune if it clashes with the new crane composition.

## OUT
Curiosities, landmarks page, prop paste list, tales, new districts, Ankerhold, TalkUx rewrite, Quests red-thread rewrite (except Quill chip gating / placement).

## Done when
- Spawn pier view: “holy shit harbour” not “NPC flea market”
- No Quill-behind-Egon / dual-master confusion
- Crane location + motion feel intentional
- Patch apply-clean; self-score honest on **harbour craft** only

Go.
```

---

## Paste starter

```text
Read docs/OPUS_SAITAMA_HARBOUR_ELEVATE_PROMPT.md.
Workspace: C:\Users\Robbi\IdeaProjects\.claude\worktrees\mining-eldervale-progression-65660c

HARBOUR ELEVATE only. Parts 1–3 done — do not touch curiosities/explore.
Harbour feels NPC-spammy, not clean wow. Fix composition.
Quill/harbourmaster behind Egon + “…-master” vs Quartermaster = confusing — move/rename/gate errand.
Crane: place where it makes sense; fix stutter; new config keys for live yml.
Deliver saitama-harbour-elevate.patch. No deploy. Go.
```
