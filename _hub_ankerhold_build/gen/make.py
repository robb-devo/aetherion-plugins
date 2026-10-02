"""Full pipeline: build -> check anchors -> flights -> export schem + anchors.yml -> read-back verification."""
import time, json, sys, os
os.makedirs("out", exist_ok=True)
import numpy as np
from build import run
import check_anchors, flights, export, validate
from world import X0, Y0, Z0

t0 = time.time()
W, T, P = run()
check_anchors.run(W)
n, bad = check_anchors.run(W)
assert not bad, bad
F = flights.plan_flights(W, T)
assert all(not v['hits'] for v in F.values()), F
badp, partial = validate.validate_palette(W.pal)
assert not badp, badp
W.anchor('world.spawn', **{k: W.anchors['spawn.harbour'][k] for k in ('x', 'y', 'z', 'yaw')}, note='setworldspawn / first join')
info = export.write_schem(W, 'out/ankerhold_hub.schem', origin=(0, 63, 0))
print('schem', info, '%.1fs' % (time.time() - t0))
extra = dict(flights=F, bells={k: v for k, v in W.anchors.items() if k.startswith('bell.')})
export.write_anchors(W, 'out/ankerhold_anchors.yml', info, extra=dict(flights=F))
# read back & compare
rb = export.read_back('out/ankerhold_hub.schem')
mn = info['min']
sub = W.a[mn[0] - X0: mn[0] - X0 + rb['W'], mn[1] - Y0: mn[1] - Y0 + rb['H'], mn[2] - Z0: mn[2] - Z0 + rb['L']]
pal_rev = rb['palette']
names = np.array([pal_rev[i] for i in range(len(pal_rev))], dtype=object)
# map back to world palette ids
idx = {s: i for i, s in enumerate(W.pal)}
back = np.vectorize(lambda i: idx[names[i]])(np.arange(len(names)))
recon = back[rb['arr']]  # (H, L, W) = y,z,x
same = np.array_equal(np.transpose(recon, (2, 0, 1)), sub)
print('read-back identical:', same, 'version', rb['version'], 'dv', rb['dv'], 'offset', rb['offset'], 'origin', rb['origin'],
      'BE', rb['be'], 'palette', len(pal_rev))
import pickle
pickle.dump(dict(anchors=W.anchors, info=info, flights=F, bells=None, capital_houses=len(T.capital_houses)),
            open('out/meta.pkl', 'wb'))
if '--dump' in sys.argv:  # for render_final.py / reach.py
    np.save('out/world.npy', W.a)
    json.dump(W.pal, open('out/palette.json', 'w'))
