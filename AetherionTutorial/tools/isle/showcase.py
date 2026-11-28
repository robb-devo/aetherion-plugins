"""Builds the house variants on a flat lawn and renders them, for judging the kit on its own.

  python showcase.py <out dir>
"""
import os
import sys

from build import Canvas
from houses import House, dress_front, PLASTER_WARM
from world import World
import shots


def main(out):
    w = World(-48, -32, 96, 64, 56, 48)
    w.fill(-48, 56, -32, 47, 61, 31, "dirt")
    w.fill(-48, 62, -32, 47, 62, 31, "grass_block")
    y = 63
    specs = [
        (-40, dict(w=7, d=7, floors=2, jetty=True, ridge="z", roof="shingle", chimney=("left", 3))),
        (-28, dict(w=9, d=7, floors=2, jetty=True, ridge="x", roof="slate", chimney=("right", 2), door=2)),
        (-14, dict(w=6, d=6, floors=1, ridge="z", roof="spruce", chimney=None)),
        (-4, dict(w=11, d=8, floors=2, jetty=True, ridge="x", roof="tile", chimney=("left", 4), door=5,
                  plaster=PLASTER_WARM)),
        (12, dict(w=7, d=9, floors=2, jetty=False, ridge="z", roof="shingle", chimney=("right", 5), door=3,
                  stone_ground="full")),
    ]
    for x0, kw in specs:
        c = Canvas(w, x0, y, -2)
        ww, dd = kw.pop("w"), kw.pop("d")
        h = House(c, ww, dd, **kw)
        h.build()
        dress_front(c, ww, dd, h)
    w.finalize()
    dump = os.path.join(out, "dump")
    w.dump(dump)
    shots.sheet(dump, os.path.join(out, "houses.png"), [
        ("front left", -30, 66, 20, 180, 2, 75), ("front right", 6, 66, 20, 180, 2, 75),
        ("east side", 0, 67, 14, 120, 4, 75), ("above", -12, 84, 30, 180, 38, 70),
    ])


if __name__ == "__main__":
    main(sys.argv[1])
