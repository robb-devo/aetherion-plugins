"""Build the island/quarry highlight templates -> $AE_OUT/templates (+ previews if vanilla assets exist)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from aeprops.style import check_support  # noqa: E402
from aeg.starters import STARTERS  # noqa: E402
from aeg.structures import STRUCTURES  # noqa: E402

OUT = os.environ.get("AE_OUT", os.path.join(HERE, "out"))


def build(name):
    fn = STARTERS.get(name) or STRUCTURES.get(name)
    b = fn()
    b.name = name
    b.resolve()
    return b


def run(names, render=False):
    os.makedirs(os.path.join(OUT, "templates"), exist_ok=True)
    rows = []
    for name in names:
        b = build(name)
        warns = check_support(b)
        size, offset, pal = b.save_schem(os.path.join(OUT, "templates", name + ".schem"))
        (mnx, mny, mnz), (mxx, mxy, mxz) = b.bounds()
        rows.append((name, size, offset, pal, len(b.blocks), (mnx, mny, mnz), (mxx, mxy, mxz), warns, b.meta))
        print(f"{name:22s} size={size} offset={offset} palette={pal} blocks={len(b.blocks)} "
              f"x[{mnx},{mxx}] y[{mny},{mxy}] z[{mnz},{mxz}] warns={len(warns)}")
        for w in warns[:6]:
            print("    !", w)
        if render:
            try:
                from aeprops.render import Renderer
                os.makedirs(os.path.join(OUT, "previews"), exist_ok=True)
                r = Renderer(u=int(os.environ.get("AE_U", "12")))
                img = r.render(b.blocks, title=name)
                img.save(os.path.join(OUT, "previews", name + ".png"))
            except Exception as ex:  # previews are optional
                print("    (preview skipped:", ex, ")")
    return rows


if __name__ == "__main__":
    want = [a for a in sys.argv[1:] if not a.startswith("--")]
    names = want or (list(STARTERS) + list(STRUCTURES))
    run(names, render="--render" in sys.argv)
