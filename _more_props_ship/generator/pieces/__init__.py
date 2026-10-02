"""Registry of Aetherion props. Each builder returns an aeprops.core.Build.

Conventions (all pieces):
  * front faces SOUTH (+z); x = east, y = up.
  * y = 0 is the ground layer: it replaces the terrain block you stand on when pasting.
  * anchor (paste point) = footprint centre at y = 1 (your feet), unless a piece says otherwise.
"""
PIECES = {}


def piece(name, zone, vibe, tip, group="accent"):
    def deco(fn):
        PIECES[name] = (fn, dict(zone=zone, vibe=vibe, tip=tip, group=group))
        return fn
    return deco


def center_anchor(b, y=1):
    (x1, _, z1), (x2, _, z2) = b.bounds()
    ground = [p for p in b.blocks if p[1] == 0] or list(b.blocks)
    xs = [p[0] for p in ground]
    zs = [p[2] for p in ground]
    b.anchor = ((min(xs) + max(xs)) // 2, y, (min(zs) + max(zs)) // 2)


from . import accents  # noqa: E402,F401
from . import accents2  # noqa: E402,F401
from . import mining  # noqa: E402,F401
from . import harbour  # noqa: E402,F401
from . import wilds  # noqa: E402,F401

# ---- second pass (_more_props_ship) ----
from . import more_wilds  # noqa: E402,F401
from . import more_harbour  # noqa: E402,F401
from . import more_fishing  # noqa: E402,F401
from . import more_mining  # noqa: E402,F401
from . import more_edge  # noqa: E402,F401
