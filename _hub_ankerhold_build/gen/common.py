import math, random
import numpy as np
from world import X0, Z0, NX, NZ, World

def gy(T, x, z):
    ix, iz = int(x) - X0, int(z) - Z0
    if 0 <= ix < NX and 0 <= iz < NZ:
        return int(T.top[ix, iz])
    return -999

def npc(W, key, x, y, z, yaw, role=None, note=None):
    """Quest/Origin NPC dock: x,z = block centre (.5), y = feet."""
    d = dict(role=role or key)
    if note: d['note'] = note
    W.anchor('npc.' + key, x, y, z, yaw=yaw, **d)

YAW = {'south': 0.0, 'west': 90.0, 'north': 180.0, 'east': -90.0}
