"""Flight corridors (Summit Glide, Skyreach Updraft glide, pad arcs) + clearance checks against the voxel grid."""
import math
import numpy as np
from world import X0, Y0, Z0, NX, NY, NZ


def solid_at(W, x, y, z):
    ix, iy, iz = int(math.floor(x)) - X0, int(math.floor(y)) - Y0, int(math.floor(z)) - Z0
    if not (0 <= ix < NX and 0 <= iy < NY and 0 <= iz < NZ):
        return False
    return W.a[ix, iy, iz] != 0


def clearance(W, pts, step=0.5, body=2):
    """Return list of colliding sample points along a polyline (checks feet..head)."""
    hits = []
    for a, b in zip(pts[:-1], pts[1:]):
        a, b = np.array(a, float), np.array(b, float)
        L = np.linalg.norm(b - a)
        n = max(1, int(L / step))
        for k in range(n + 1):
            p = a + (b - a) * k / n
            for dy in range(body):
                if solid_at(W, p[0], p[1] + dy, p[2]):
                    hits.append(tuple(np.round(p, 1)))
                    break
    return hits


def arc(p0, p1, apex, n=80):
    p0, p1 = np.array(p0, float), np.array(p1, float)
    top = max(p0[1], p1[1]) + apex
    out = []
    for k in range(n + 1):
        t = k / n
        x = p0[0] + (p1[0] - p0[0]) * t
        z = p0[2] + (p1[2] - p0[2]) * t
        # quadratic through p0.y, top at t*, p1.y (approximate: blend)
        y = (1 - t) * p0[1] + t * p1[1] + 4 * (top - (p0[1] + p1[1]) / 2) * t * (1 - t)
        out.append((x, y, z))
    return out


def plan_flights(W, T):
    A = W.anchors
    res = {}
    # Summit Glide: ring -> over the spur and the Grand Stair -> Ledger's Court
    ring = A["glide.summit.ring"]
    land = A["spawn.capital"]
    sy = ring["y"]
    path = [(ring["x"], sy + 0.3, ring["z"]), (ring["x"], sy + 2, ring["z"] + 4), (-30, sy + 6, -96),
            (-12, sy - 2, -64), (-6, 104, -30), (-3, 86, -2), (land["x"], land["y"], land["z"])]
    res["summit_glide"] = dict(path=[list(map(lambda v: round(v, 1), p)) for p in path], hits=clearance(W, path[1:]))
    # Skyreach Updraft: vertical column then glide onto the terrace
    fl = A["updraft.skyreach.floor"]
    tp = A["updraft.skyreach.top"]
    col = [(fl["x"], fl["y"], fl["z"]), (fl["x"], tp["y"] + 8, fl["z"])]
    glide = [(fl["x"], tp["y"] + 8, fl["z"]), (tp["x"] + 6, tp["y"] + 5, tp["z"] + 6), (tp["x"], tp["y"] + 0.2, tp["z"])]
    res["skyreach_updraft"] = dict(column=[list(p) for p in col], glide=[list(map(lambda v: round(v, 1), p)) for p in glide],
                                   hits=clearance(W, col) + clearance(W, glide))
    # pad arcs (in-box part only: from pad to the bbox edge)
    for key, target, apex in (("origin_to_mining", (53.5, 91.0, 482.5), 55), ("origin_to_forage", (479.5, 74.0, -240.5), 45)):
        pad = A["pad." + key]
        p0 = (pad["x"], pad["y"] + 0.5, pad["z"])
        pts = arc(p0, target, apex, n=200)
        inbox = [p for p in pts if X0 <= p[0] <= X0 + NX - 1 and Z0 <= p[2] <= Z0 + NZ - 1]
        hits = clearance(W, inbox[2:])
        dist = math.dist((p0[0], p0[2]), (target[0], target[2]))
        res[key] = dict(from_=list(p0), to=list(target), horizontal=round(dist, 1), apex=apex, hits=hits[:5])
    return res
