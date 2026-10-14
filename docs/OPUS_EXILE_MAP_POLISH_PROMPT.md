# Opus prompt — Exile map FINAL POLISH (geisteskrank)

Copy everything below the line into a **new** Opus chat.
Recommended effort: **Max** (full session — interiors + streets + building polish).
Attach / point Opus at the current map package (see paths below). Schematic/world art only.

---

```text
# AETHERION EXILE — FINAL POLISH PASS
## Professional MC builder team energy. One whole session. Make this absurd.

You are elevating an **already excellent** Exile island into a trailer-grade masterpiece.
Build craft only. No plugins. No Java. Do **not** edit the live mmo-c world. Work offline on the local package, export updated schems / world zip for Robb.

---

## WHAT YOU ARE POLISHING (attach these)

Canonical local package (prefer this tree):

| Path | What |
|---|---|
| `C:\Users\Robbi\IdeaProjects\Claude outputs\EXIL\` | Full build package |
| `...\EXIL\schem\exil_gesamt.schem` | Whole island (~360×350×386) |
| `...\EXIL\schem\distrikte\*.schem` | Per-district schems |
| `...\EXIL\schem\zustaende\*.schem` | State overlays (throne destroyed / all gates open) |
| `...\EXIL\anchors.yml` | Plugin anchors — **preserve / update if layout shifts** |
| `...\EXIL\renders\` | Reference shots (chapel/bells, hofstadt, interiors, topdown) |
| `...\EXIL\README.md` | Build bible for this map |
| `C:\Users\Robbi\Downloads\EXIL_runde4.zip` | Alternate zip of the current map if needed |

Also read (story + tone DNA — do not invent a different island):

- `aetherion-story-p1/docs/STORY_BIBLE.md` → section **Die Exil-Insel** + Act III bosses/places
- `aetherion-story-p1/docs/AETHERION_MAIN_HUB_WORLD_BIBLE.md` → craft standard for authored places (density, interiors, environmental storytelling) — apply that bar here, Exile fantasy not Hub fantasy
- `Claude outputs/EXIL/README.md` → districts, house scenes, physics rules, world settings

Robb’s verdict already: **buildings are top, chapel/Kirche is insanely awesome.** Keep that DNA. Raise everything else to match the chapel.

---

## HARD CONSTRAINTS

1. **Additive elevate, not rebuild.** Same island, same story moments, same silhouette language (bell tower, chain coast, hofstadt, observatory, puppet theatre, garden/root, castle).
2. **Vanilla blocks only** (Paper 1.21.1). No resource-pack-required custom models.
3. **Physics-safe** (no torch-on-air, floating gravel, flower-on-stone). Match the existing physics discipline in `build_scripts/physics.py` / README.
4. **Preserve story beats** from STORY_BIBLE + EXIL README:
   - Fog-break arrival, 16 cut chains, broken Caedmon colossus, hofstadt “time stopped mid-evening”
   - Observatory (Orrin), Helios / Hollow Sun / Seraphine / Unbroken+Nihil spatial rooms
   - Eighth bell, Elin’s room, wedding table, tavern “The Last Bell”, packing family, etc.
5. **Do not wipe interiors to place “prettier empty shells”.** Interiors must gain character, not lose it.
6. **anchors.yml stays truthful.** If you move a landmark, update anchors.
7. No live SSH / Crafty / plugin work. Deliver schems (+ optional updated renders).

Size stays in the existing ~300–400 class footprint. Do not sprawl into a new continent.

---

## MISSION — WHERE TO GO INSANE

### A. Interiors (PRIMARY — “realistic + with character”)
Every enterable room should feel like people lived here the night the Veil closed.

Do a full interior pass across:
- Hofstadt houses (all scene houses + upper floors + attics)
- Castle rooms (throne / bedchamber / writing room / lab / bell chamber / Elin’s room / wedding leftovers)
- Observatory interior
- Puppet theatre backstage + audience
- Tavern rooms, chapel/Kirche (already elite — polish details, light, pew storytelling, not a redesign)
- Ferry hut, customs, chain-lift cage, side niches

Interior craft bar:
- Lived-in asymmetry (one chair pushed back, half-packed trunk, cold hearth with one last ember prop)
- Vertical circulation that feels real (stairs, railings, loft hatches)
- Light as storytelling (candles still lit; warm windows vs dead backs)
- Micro-props with purpose (letters, plates for two, clocks stopped together, mould for bell VIII)
- No “furniture wallpaper” — groupings people would actually use
- Weather/age on backs of buildings vs golden street faces (already in DNA — deepen it)

### B. Streets, paths, processional ways
- Prozessionsstraße, terraces, stairs, alleys, market edges, garden paths, cliff stair, chain-lift approach
- Edge wear, mud/moss transitions, fallen cart logic, lantern spacing, sightlines to tower/colossi
- Make walking the island feel directed and cinematic without corridor-forcing the player

### C. Building exteriors — final polish
Silhouettes are strong. Now:
- Roofline / gutter / cornice / timber join consistency
- Window depth, shutters, flower boxes, broken panes where story wants them
- Material weathering gradients (west wind / north shade)
- Small “hero details” every 8–16 blocks on major facades (already started on quay wall — spread that discipline)

### D. Landmark reverence
Chapel / Kirche / bell stack: **protect the wow.** Only micro-polish (light wells, floor wear, rope fray, dust shafts). Do not “improve” it into a different building.

### E. Environmental storytelling density
STORY_BIBLE rule for every room: *something happened here a thousand years ago, and the room froze in that second.*
Push that harder in under-visited corners without cluttering combat arenas.

---

## WHAT WE NEED THIS MAP TO SERVE (story functions)

Keep / strengthen support for Act III:

| Need | Place |
|---|---|
| Ferry arrival + Ferryman | Königskai / fog break |
| Hofstadt exploration + court deliverables | District 2 scene houses |
| Hollow Sun / Caedmon beat | Procession + crater / knight fall |
| Helios / Orrin | Observatory spur |
| Seraphine | Puppet theatre |
| Unbroken + Nihil / garden | Wurzelgrund + greenhouse |
| Castle + eighth bell + Form beats | Schloss / tower / Form-3 platform |
| Emotional quiet rooms | Elin’s room, wedding house, widow house, packing family |

You are not wiring bosses. You are making the **stage** undeniable.

---

## DELIVERABLES

1. Updated `exil_gesamt.schem` (and district schems if you split work)
2. Updated `anchors.yml` if anything moved
3. Short `POLISH_NOTES.md`: what you elevated (interiors / streets / facades), what you deliberately left alone (chapel wow), any physics fixes
4. Optional: a few new renders of best interior + street shots
5. Ship folder OK: `IdeaProjects\_exile_polish_ship\` if cleaner

**Success:** Robb walks the same island and says the interiors/streets finally match the chapel. Feels like a pro team’s final art pass on a map that was already scary good.

Go. One session. Full builder rage. Respect the frozen evening.
```
