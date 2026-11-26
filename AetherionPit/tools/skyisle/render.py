"""Preview renderer for skyisle.npz: textured voxel ray caster (vanilla 1.21.1 textures from the client jar).

  python render.py <skyisle.npz> <client.jar> <out.png> x y z yaw pitch [W H fov]
  python render.py <skyisle.npz> <client.jar> <out.png> map
"""
import io
import math
import sys
import zipfile

import numpy as np
from PIL import Image

GRASS = np.array([0x91, 0xBD, 0x59]) / 255.0
FOLIAGE = np.array([0x77, 0xAB, 0x2F]) / 255.0
WATER = np.array([0x3F, 0x76, 0xE4]) / 255.0

ALIAS = {
    "smooth_quartz": "quartz_block_bottom", "quartz": "quartz_block_side", "smooth_stone": "smooth_stone",
    "prismarine_brick": "prismarine_bricks", "mossy_stone_brick": "mossy_stone_bricks", "stone_brick": "stone_bricks",
    "polished_blackstone_brick": "polished_blackstone_bricks", "red_nether_brick": "red_nether_bricks",
    "cherry": "cherry_planks", "oak": "oak_planks", "birch": "birch_planks", "water": "water_still",
    "lava": "lava_still", "snow_block": "snow", "cherry_wood": "cherry_log", "oak_wood": "oak_log",
    "stripped_birch_wood": "stripped_birch_log", "stripped_oak_wood": "stripped_oak_log", "oak_sign": "oak_planks",
    "cherry_sign": "cherry_planks", "red_banner": "red_wool", "soul_campfire": "soul_campfire_log_lit",
    "campfire": "campfire_log_lit", "big_dripleaf_stem": "big_dripleaf_stem", "cave_vines_plant": "cave_vines_plant",
    "tall_grass": "tall_grass_top", "large_fern": "large_fern_top", "lilac": "lilac_top", "peony": "peony_top",
    "rose_bush": "rose_bush_top", "pink_petals": "pink_petals", "moss_carpet": "moss_block",
    "cherry_fence": "cherry_planks", "polished_tuff": "polished_tuff", "copper_bulb": "copper_bulb_lit",
}
TINT_GRASS = {"grass_block", "short_grass", "tall_grass", "fern", "large_fern"}
TINT_FOLIAGE = {"vine", "oak_leaves"}


class Tex:
    def __init__(self, jar):
        self.z = zipfile.ZipFile(jar)
        self.cache = {}

    def load(self, name):
        if name in self.cache:
            return self.cache[name]
        path = "assets/minecraft/textures/block/%s.png" % name
        try:
            im = Image.open(io.BytesIO(self.z.read(path))).convert("RGBA")
        except KeyError:
            self.cache[name] = None
            return None
        arr = np.asarray(im, dtype=np.float32) / 255.0
        arr = arr[:16, :16] if arr.shape[0] >= 16 else np.resize(arr, (16, 16, 4))
        if arr.shape[1] > 16:
            arr = arr[:, :16]
        self.cache[name] = arr
        return arr

    def first(self, *names):
        for n in names:
            t = self.load(n)
            if t is not None:
                return t
        return None


def faces_for(state, tex):
    """returns (top, side, bottom) 16x16x4 arrays"""
    name = state.split("[")[0].replace("minecraft:", "")
    if name == "air":
        return None
    base = name.replace("waxed_", "")
    if base.endswith("_lit") or "copper_bulb" in base:
        base = base if base.endswith("bulb") else base
    for suf in ("_stairs", "_slab", "_wall", "_fence"):
        if base.endswith(suf):
            base = base[: -len(suf)]
            base = ALIAS.get(base, base)
            if tex.load(base) is None and tex.load(base + "s") is not None:
                base = base + "s"
            break
    if "copper_bulb" in base:
        base = base + "_lit" if "lit=true" in state else base
    base = ALIAS.get(base, base)
    top = tex.first(base + "_top", base, base + "_side", base + "_still")
    side = tex.first(base + "_side", base, base + "_top", base + "_still")
    bot = tex.first(base + "_bottom", base, base + "_side")
    if name == "grass_block":
        top = tex.load("grass_block_top").copy()
        top[..., :3] *= GRASS
        side = tex.load("grass_block_side")
        bot = tex.load("dirt")
    if top is None:
        if name == "end_gateway":
            g = np.zeros((16, 16, 4), np.float32)
            g[..., :3] = (0.02, 0.05, 0.08)
            g[..., 3] = 1
            return ("gateway", g, g, g)
        g = np.zeros((16, 16, 4), np.float32)
        g[..., :3] = (1, 0, 1)
        g[..., 3] = 1
        print("missing texture:", name)
        return (name, g, g, g)
    if name in TINT_GRASS:
        top, side = top.copy(), side.copy()
        top[..., :3] *= GRASS
        side[..., :3] *= GRASS
    if name in TINT_FOLIAGE:
        top, side = top.copy(), side.copy()
        top[..., :3] *= FOLIAGE
        side[..., :3] *= FOLIAGE
    if name == "water":
        top, side, bot = [t.copy() for t in (top, side, bot)]
        for t in (top, side, bot):
            t[..., :3] *= WATER
            t[..., 3] = 1
    return (name, top, side, bot if bot is not None else side)


def build_atlas(pal, tex):
    n = len(pal)
    atlas = np.zeros((n, 3, 16, 16, 4), np.float32)
    kind = np.zeros(n, np.int8)  # 0 air, 1 solid, 2 gateway, 3 water, 4 emissive
    for i, s in enumerate(pal):
        f = faces_for(s, tex)
        if f is None:
            continue
        name, t, sd, b = f
        atlas[i, 0], atlas[i, 1], atlas[i, 2] = t, sd, b
        kind[i] = 1
        if name == "gateway":
            kind[i] = 2
        elif name == "water":
            kind[i] = 3
        elif any(k in name for k in ("lantern", "froglight", "glowstone", "bulb", "end_rod", "campfire", "shroomlight",
                                     "magma", "lava")):
            kind[i] = 4
    return atlas, kind


def sky_color(d, sun):
    up = np.clip(d[..., 1], -1, 1)
    top = np.array([0.36, 0.55, 0.95])
    hor = np.array([0.78, 0.84, 0.98])
    t = np.clip(1 - np.maximum(up, 0), 0, 1) ** 3
    c = top * (1 - t[..., None]) + hor * t[..., None]
    # sunset glow toward the sun azimuth
    az = (d[..., 0] * sun[0] + d[..., 2] * sun[2]) / (np.hypot(d[..., 0], d[..., 2]) + 1e-6)
    glow = np.clip(az, 0, 1) ** 2 * np.exp(-np.abs(up) * 4.0)
    c = c * (1 - 0.7 * glow[..., None]) + np.array([1.0, 0.55, 0.3]) * 0.7 * glow[..., None]
    sd = d[..., 0] * sun[0] + d[..., 1] * sun[1] + d[..., 2] * sun[2]
    c = np.where((sd > 0.9985)[..., None], np.array([1.0, 0.95, 0.75]), c)
    below = up < 0
    c = np.where(below[..., None], c * (1 + up[..., None] * 0.5), c)
    return c


def march(a, kind, atlas, o, d, max_steps, want_uv=True):
    """vectorized DDA. o: (N,3) origins in voxel space, d: (N,3) dirs. returns hit info"""
    N = o.shape[0]
    sx, sy, sz = a.shape
    ix = np.floor(o).astype(np.int64)
    step = np.where(d > 0, 1, -1).astype(np.int64)
    with np.errstate(divide="ignore"):
        inv = np.where(np.abs(d) < 1e-9, 1e9, 1.0 / d)
    tmax = ((ix + (step > 0)) - o) * inv
    tdelta = np.abs(inv)
    hit = np.full(N, -1, np.int64)
    face = np.zeros(N, np.int8)
    thit = np.full(N, np.inf)
    active = np.arange(N)
    last_axis = np.zeros(N, np.int8)
    for it in range(max_steps):
        if active.size == 0:
            break
        tm = tmax[active]
        ax = np.argmin(tm, axis=1)
        r = np.arange(active.size)
        t_enter = tm[r, ax]
        ix[active, ax] += step[active, ax]
        tmax[active, ax] += tdelta[active, ax]
        cx, cy, cz = ix[active, 0], ix[active, 1], ix[active, 2]
        inb = (cx >= 0) & (cx < sx) & (cy >= 0) & (cy < sy) & (cz >= 0) & (cz < sz)
        b = np.zeros(active.size, np.int64)
        b[inb] = a[cx[inb], cy[inb], cz[inb]]
        k = kind[b]
        cand = (k > 0)
        if cand.any():
            ci = np.nonzero(cand)[0]
            p = o[active[ci]] + d[active[ci]] * t_enter[ci, None]
            axc = ax[ci]
            # uv on the face
            fu = np.where(axc == 1, p[:, 0], np.where(axc == 0, p[:, 2], p[:, 0]))
            fv = np.where(axc == 1, p[:, 2], p[:, 1])
            u = np.clip(((fu - np.floor(fu)) * 16).astype(int), 0, 15)
            v = np.clip(15 - ((fv - np.floor(fv)) * 16).astype(int), 0, 15)
            v = np.where(axc == 1, 15 - v, v)
            fidx = np.where(axc == 1, np.where(step[active[ci], 1] > 0, 2, 0), 1)
            alpha = atlas[b[ci], fidx, v, u, 3]
            ok = (alpha > 0.4) | (k[ci] >= 2)
            sel = ci[ok]
            gi = active[sel]
            hit[gi] = b[sel]
            face[gi] = (ax[sel] * 2 + (step[gi, ax[sel]] > 0)).astype(np.int8)
            thit[gi] = t_enter[sel]
            done = np.zeros(active.size, bool)
            done[sel] = True
        else:
            done = np.zeros(active.size, bool)
        # leaving the box
        out = ~inb & (((cx < 0) & (step[active, 0] < 0)) | ((cx >= sx) & (step[active, 0] > 0)) |
                      ((cy < 0) & (step[active, 1] < 0)) | ((cy >= sy) & (step[active, 1] > 0)) |
                      ((cz < 0) & (step[active, 2] < 0)) | ((cz >= sz) & (step[active, 2] > 0)))
        active = active[~(done | out)]
    return hit, face, thit


def render(npz, jar, out, cam, yaw, pitch, W=1280, H=720, fov=70.0):
    data = np.load(npz)
    a = data["a"].astype(np.int64)
    corner = data["corner"]
    pal = [str(s) for s in data["pal"]]
    tex = Tex(jar)
    atlas, kind = build_atlas(pal, tex)
    # pad so the camera is inside the volume
    cam = np.array(cam, float) - corner
    pad = 40
    a = np.pad(a, pad)
    cam = cam + pad
    yr, pr = math.radians(yaw), math.radians(pitch)
    f = np.array([-math.sin(yr) * math.cos(pr), -math.sin(pr), math.cos(yr) * math.cos(pr)])
    r = np.array([-math.cos(yr), 0, -math.sin(yr)])
    u = np.cross(r, f)
    th = math.tan(math.radians(fov) / 2)
    ys, xs = np.mgrid[0:H, 0:W]
    px = (2 * (xs + 0.5) / W - 1) * th * W / H
    py = (1 - 2 * (ys + 0.5) / H) * th
    d = f[None, None] + px[..., None] * r[None, None] + py[..., None] * u[None, None]
    d = d / np.linalg.norm(d, axis=-1, keepdims=True)
    d = d.reshape(-1, 3)
    o = np.repeat(cam[None], d.shape[0], 0)
    hit, face, thit = march(a, kind, atlas, o, d, 900)
    sun = np.array([-0.94, 0.34, 0.0])
    sun = sun / np.linalg.norm(sun)
    col = sky_color(d, sun)
    m = hit >= 0
    if m.any():
        hi = np.nonzero(m)[0]
        p = o[hi] + d[hi] * thit[hi, None]
        ax = face[hi] // 2
        pos = face[hi] % 2  # 1 = step positive -> we hit the negative face
        n = np.zeros((hi.size, 3))
        n[np.arange(hi.size), ax] = np.where(pos == 1, -1.0, 1.0)
        fu = np.where(ax == 1, p[:, 0], np.where(ax == 0, p[:, 2], p[:, 0]))
        fv = np.where(ax == 1, p[:, 2], p[:, 1])
        uu = np.clip(((fu - np.floor(fu)) * 16).astype(int), 0, 15)
        vv = np.clip(15 - ((fv - np.floor(fv)) * 16).astype(int), 0, 15)
        vv = np.where(ax == 1, 15 - vv, vv)
        fidx = np.where(ax == 1, np.where(n[:, 1] > 0, 0, 2), 1)
        texc = atlas[hit[hi], fidx, vv, uu, :3]
        k = kind[hit[hi]]
        shade = np.where(ax == 1, np.where(n[:, 1] > 0, 1.0, 0.5), np.where(ax == 2, 0.8, 0.6))
        # shadows
        so = p + n * 0.01
        sdir = np.repeat(sun[None], hi.size, 0)
        sh, _, _ = march(a, kind, atlas, so, sdir, 250)
        lit = (sh < 0).astype(float)
        lam = np.clip((n * sun).sum(1), 0, 1)
        light = 0.55 * shade + 0.55 * lam * lit + 0.08
        c = texc * light[:, None] * np.array([1.0, 0.93, 0.85])
        c = np.where((k == 4)[:, None], texc * 1.15, c)
        # gateway: starfield in screen space
        g = k == 2
        if g.any():
            idx = hi[g]
            rr = (np.sin(idx * 12.9898) * 43758.5453) % 1
            star = (rr > 0.985).astype(float)
            c[g] = np.array([0.03, 0.07, 0.10]) + star[:, None] * np.array([0.6, 0.9, 0.8]) + \
                   (((idx * 7) % 97) / 97.0 > 0.9)[:, None] * np.array([0.10, 0.25, 0.22])
        dist = thit[hi]
        fog = np.clip((dist - 90) / 110, 0, 1)[:, None]
        c = c * (1 - fog) + col[hi] * fog
        col[hi] = c
    img = np.clip(col.reshape(H, W, 3), 0, 1) ** (1 / 1.1)
    Image.fromarray((img * 255).astype(np.uint8)).save(out)
    print("wrote", out)


def topmap(npz, jar, out):
    data = np.load(npz)
    a = data["a"].astype(np.int64)
    pal = [str(s) for s in data["pal"]]
    tex = Tex(jar)
    atlas, kind = build_atlas(pal, tex)
    avg = atlas[:, 0, :, :, :3].mean((1, 2))
    sx, sy, sz = a.shape
    nz = a != 0
    ytop = np.where(nz.any(1), sy - 1 - np.argmax(nz[:, ::-1, :], axis=1), -1)
    ii, kk = np.meshgrid(np.arange(sx), np.arange(sz), indexing="ij")
    b = a[ii, np.clip(ytop, 0, sy - 1), kk]
    c = avg[b] * (0.6 + 0.4 * np.clip(ytop / sy, 0, 1))[..., None]
    c[ytop < 0] = (0.55, 0.7, 0.95)
    img = np.transpose(c, (1, 0, 2))  # rows = z, cols = x
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8)).resize((sx * 3, sz * 3), Image.NEAREST)
    im.save(out)
    print("wrote", out, "corner", data["corner"])


if __name__ == "__main__":
    npz, jar, out = sys.argv[1:4]
    if sys.argv[4] == "map":
        topmap(npz, jar, out)
    else:
        x, y, z, yaw, pitch = map(float, sys.argv[4:9])
        W = int(sys.argv[9]) if len(sys.argv) > 9 else 1280
        H = int(sys.argv[10]) if len(sys.argv) > 10 else 720
        fov = float(sys.argv[11]) if len(sys.argv) > 11 else 70.0
        render(npz, jar, out, (x, y, z), yaw, pitch, W, H, fov)
