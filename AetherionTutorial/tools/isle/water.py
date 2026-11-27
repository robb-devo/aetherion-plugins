"""Would any water start to run if the game looked at it?

Chunks written straight into region files are never ticked, so water stands as it was written, until the day a
block next to it changes: then the game applies its rules and a badly sealed pond empties over the meadow.
`leaks` finds every place where that would happen, so the world can be built as a fixed point of those rules.
"""
import numpy as np

from world import base_name, props_of

# what water washes away or simply fills (besides air)
WASHED = {
    "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "dandelion", "poppy", "blue_orchid", "allium",
    "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower",
    "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "brown_mushroom", "red_mushroom",
    "sweet_berry_bush", "moss_carpet", "pink_petals", "torch", "wall_torch", "redstone_wire", "lever", "rail",
    "snow", "cobweb", "fire", "wheat", "carrots", "potatoes", "beetroots", "flower_pot", "tripwire",
}
IN_WATER = {"seagrass", "tall_seagrass", "kelp", "kelp_plant", "bubble_column"}


def classify(w):
    n = len(w.palette)
    still = np.zeros(n, bool)       # a source: still water, a water plant, a waterlogged block
    running = np.zeros(n, bool)     # levels 1..7
    falling = np.zeros(n, bool)     # levels 8..15
    opening = np.zeros(n, bool)     # what water runs into
    for i, s in enumerate(w.palette):
        b = base_name(s)
        p = props_of(s)
        if b == "water":
            lv = int(p.get("level", 0))
            if lv == 0:
                still[i] = True
            elif lv >= 8:
                falling[i] = True
            else:
                running[i] = True
        elif b in IN_WATER or p.get("waterlogged") == "true":
            still[i] = True
        elif b == "air" or b in WASHED or b.endswith("_carpet") or b.startswith("potted_") or b.endswith("_button"):
            opening[i] = True
    return still, running, falling, opening


def leaks(w, limit=400):
    """[(x, y, z, why)] for water that would move. Empty means the water is at rest."""
    still, running, falling, opening = classify(w)
    B = w.blocks
    S, R, F, O = still[B], running[B], falling[B], opening[B]
    wet = S | R | F
    out = np.zeros(B.shape, bool)
    why = {}

    def note(mask, text):
        nonlocal out
        new = mask & ~out
        out |= mask
        ys, zs, xs = np.nonzero(new)
        for y, z, x in zip(ys.tolist(), zs.tolist(), xs.tolist()):
            if len(why) >= limit:
                break
            why[(x + w.x0, y + w.y0, z + w.z0)] = text

    below_open = np.zeros(B.shape, bool)
    below_open[1:] = O[:-1]
    below_wet = np.zeros(B.shape, bool)
    below_wet[1:] = wet[:-1]
    side_open = np.zeros(B.shape, bool)
    side_open[:, 1:, :] |= O[:, :-1, :]
    side_open[:, :-1, :] |= O[:, 1:, :]
    side_open[:, :, 1:] |= O[:, :, :-1]
    side_open[:, :, :-1] |= O[:, :, 1:]

    note(S & below_open, "still water over a gap")
    note(S & ~below_open & side_open, "still water beside a gap")
    note((R | F) & below_open, "running water over a gap")
    # running water that lies on solid ground spreads; over water it stays where it is
    note((R | F) & ~below_open & ~below_wet & side_open, "running water on dry ground")
    return [(x, y, z, t) for (x, y, z), t in sorted(why.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))] \
        if np.count_nonzero(out) <= limit else \
        [(x, y, z, t) for (x, y, z), t in list(why.items())] + [("...", int(np.count_nonzero(out)), "in all", "")]
