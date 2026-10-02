import pickle
m = pickle.load(open('out/meta.pkl', 'rb'))
A = m['anchors']; F = m['flights']
def c(v): return f"`{v['x']:g} {v['y']:g} {v['z']:g}`"
def yaw(v): return f"{v['yaw']:g}" if 'yaw' in v else ''
def mm(v): return f"`{' '.join(map(str, v['min']))}` → `{' '.join(map(str, v['max']))}`"
G = {}
for k, v in A.items():
    g, _, n = k.partition('.'); G.setdefault(g, {})[n] = v
out = []
out.append("### Spawn camps (`spawns.*` in Hub `config.yml`)\n")
out.append("| id | feet x y z | yaw | discover radius | spot |\n|---|---|---|---|---|")
disc = {'capital': 'capital', 'ore_ridge': 'ore_ridge', 'farm': 'farm', 'borderlands': 'borderlands', 'whisperwood': 'whisperwood', 'summit': 'summit'}
for n, v in G['spawn'].items():
    r = G['discover'].get(disc.get(n, ''), {}).get('radius', '')
    out.append(f"| `{n}` | {c(v)} | {yaw(v)} | {r} | {v.get('note','')} |")
w = A['world.spawn']
out.append(f"\nWorld spawn (`/setworldspawn`, first join): {c(w)}, yaw {yaw(w)} — the arrival pier. Off-hub camps (`forage_isle`, `farm_isle`, `eldervale`, `fishing_eldervale`) don't change.\n")
out.append("### Island pads (`island-pads.pads.*`)\n")
out.append("| pad | slime volume (min → max) | lands at | note |\n|---|---|---|---|")
out.append(f"| `origin_to_mining` | {mm(A['pad.origin_to_mining'])} | `53.5 91 482.5` (as before) | {F['origin_to_mining']['horizontal']:g} blocks due south: raise `boost-ticks` |")
out.append(f"| `mining_to_origin` | as before (on the isle) | {c(A['pad.mining_to_origin.land'])} | new `target`: the Surveyor's Ledge |")
out.append(f"| `origin_to_forage` | {mm(A['pad.origin_to_forage'])} | `479.5 74 -240.5` (as before) | {F['origin_to_forage']['horizontal']:g} blocks ENE: raise `boost-ticks` |")
out.append(f"| `forage_to_origin` | as before (on the isle) | {c(A['pad.forage_to_origin.land'])} | new `target`: Twig's deck |")
out.append("\nThe arcs were checked against the build; nothing on the island is in the way. As a rule of thumb, `boost-ticks ≈ distance ÷ horiz-speed`: about 100 for Mining and about 125 for Forage.\n")
out.append("### Portals\n")
out.append("| portal | at | note |\n|---|---|---|")
for n, v in G['portal'].items():
    extra = f", frame {mm(v)}" if 'min' in v else (f", r = {v['radius']}" if 'radius' in v else '')
    out.append(f"| `{n}` | {c(v)}{extra} | {v.get('note','')} |")
out.append("\n### Quest NPC docks (AetherionQuests `npcs.yml`)\n")
out.append("The ids, quests and TalkUx all stay as they are. Only the position and yaw change.\n")
out.append("| npc id | feet x y z | yaw | spot |\n|---|---|---|---|")
for n, v in sorted(G['npc'].items()):
    if v.get('role', '').startswith('origin:'): continue
    out.append(f"| `{n}` | {c(v)} | {yaw(v)} | {v.get('note','')} |")
out.append("\nThese stay off the hub, where they are now: `eldervale_welcome`, `eldervale_upgrade`, `canopy_clerk`, `amethyst_mines_guide`, `root_cellar`.\n")
out.append("### Origin townsfolk (Hub boards, `origin-cast.yml`)\n")
out.append("| who | feet x y z | yaw | note |\n|---|---|---|---|")
for n, v in sorted(G['npc'].items()):
    if not v.get('role', '').startswith('origin:'): continue
    out.append(f"| {n.replace('_', ' ').title()} | {c(v)} | {yaw(v)} | {v.get('note','')} |")
out.append("\nSister Aurel (bells) and Fen Glowmoor (glowcaps) are folded into Orla's board.\n")
out.append("### Origin-light anchors (`origin.yml`)\n")
out.append("| kind | id | at | note |\n|---|---|---|---|")
for g in ('bell', 'vista', 'waystone', 'updraft', 'glide', 'fountain', 'emitter', 'secret', 'hook'):
    for n, v in G.get(g, {}).items():
        out.append(f"| {g} | `{n}` | {c(v)} | {v.get('note','')} |")
sg = F['summit_glide']['path']
out.append(f"\nSummit Glide waypoints (checked against the terrain, 0 hits): `{'  /  '.join(' '.join(f'{q:g}' for q in pt) for pt in sg)}`")
su = F['skyreach_updraft']
out.append(f"\nSkyreach Updraft: the column runs `{' '.join(f'{q:g}' for q in su['column'][0])}` → `{' '.join(f'{q:g}' for q in su['column'][1])}`, then glides `{'  /  '.join(' '.join(f'{q:g}' for q in pt) for pt in su['glide'])}` (0 hits).\n")
out.append("### Break / regen regions (protection seams)\n")
out.append("| region | min → max | what |\n|---|---|---|")
for n, v in G['region'].items():
    out.append(f"| `{n}` | {mm(v)} | {v.get('note','')} |")
r = A['rescue.footprint']
out.append(f"\nOrigin `footprint` / rescue box: x/z `{r['min'][0]}..{r['max'][0]}`. `rescue.floor-y` stays at −60. Everything else should default to deny-break (via WorldGuard or a Hub guard).\n")
out.append("### Landmarks (district / landmark discovery)\n")
out.append("| id | at | note |\n|---|---|---|")
for n, v in G['landmark'].items():
    out.append(f"| `{n}` | {c(v)} | {v.get('note','')} |")
open('out/tables.md', 'w').write('\n'.join(out) + '\n')
print('ok', len(out))
