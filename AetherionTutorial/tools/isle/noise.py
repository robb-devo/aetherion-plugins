"""Small deterministic noise helpers (numpy): value noise, fbm, hash-based scatter."""
import numpy as np


def _lattice(seed, gx, gz):
    """Deterministic pseudo-random values in [0,1) for integer lattice points."""
    h = (gx.astype(np.int64) * 374761393 + gz.astype(np.int64) * 668265263 + int(seed) * 1442695041) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / float(0x1000000)


def value(x, z, scale, seed=0):
    """Smooth value noise in [0,1], feature size `scale` blocks. x, z: arrays (broadcastable)."""
    fx, fz = np.asarray(x, np.float64) / scale, np.asarray(z, np.float64) / scale
    x0, z0 = np.floor(fx), np.floor(fz)
    tx, tz = fx - x0, fz - z0
    tx = tx * tx * (3 - 2 * tx)
    tz = tz * tz * (3 - 2 * tz)
    a = _lattice(seed, x0, z0)
    b = _lattice(seed, x0 + 1, z0)
    c = _lattice(seed, x0, z0 + 1)
    d = _lattice(seed, x0 + 1, z0 + 1)
    return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz


def fbm(x, z, scale, octaves=4, seed=0, gain=0.5):
    """Fractal noise in roughly [-1, 1]."""
    total = 0.0
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total = total + (value(x, z, scale / (2 ** o), seed + o * 101) * 2 - 1) * amp
        norm += amp
        amp *= gain
    return total / norm


def hash01(x, y, z, seed=0):
    """Per-cell hash in [0,1) for scalars or arrays."""
    if isinstance(x, int) and isinstance(y, int) and isinstance(z, int):
        h = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (int(seed) * 2654435761)) & 0xFFFFFFFF
        h = ((h ^ (h >> 15)) * 2246822519) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 3266489917) & 0xFFFFFFFF
        h = h ^ (h >> 16)
        return (h & 0xFFFFFF) / float(0x1000000)
    x = np.asarray(x, np.int64).astype(np.uint64)
    y = np.asarray(y, np.int64).astype(np.uint64)
    z = np.asarray(z, np.int64).astype(np.uint64)
    m = np.uint64(0xFFFFFFFF)
    h = ((x * np.uint64(73856093)) ^ (y * np.uint64(19349663)) ^ (z * np.uint64(83492791))
         ^ np.uint64((int(seed) * 2654435761) & 0xFFFFFFFFFFFFFFFF)) & m
    h = ((h ^ (h >> np.uint64(15))) * np.uint64(2246822519)) & m
    h = ((h ^ (h >> np.uint64(13))) * np.uint64(3266489917)) & m
    h = h ^ (h >> np.uint64(16))
    return (h & np.uint64(0xFFFFFF)).astype(np.float64) / float(0x1000000)


def smoothstep(x, a, b):
    t = np.clip((np.asarray(x, np.float64) - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Rng:
    """Tiny seeded generator for scalar decisions (stable across runs and platforms)."""

    def __init__(self, seed):
        self.s = (int(seed) * 2654435761 + 12345) & 0xFFFFFFFF

    def next(self):
        self.s = (self.s * 1664525 + 1013904223) & 0xFFFFFFFF
        return (self.s >> 8) / float(1 << 24)

    def range(self, a, b):
        return a + (b - a) * self.next()

    def int(self, a, b):
        """inclusive"""
        return a + int(self.next() * (b - a + 1))

    def chance(self, p):
        return self.next() < p

    def pick(self, items):
        return items[int(self.next() * len(items)) % len(items)]

    def weighted(self, pairs):
        total = sum(w for _, w in pairs)
        r = self.next() * total
        for item, w in pairs:
            r -= w
            if r <= 0:
                return item
        return pairs[-1][0]
