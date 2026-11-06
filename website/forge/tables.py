"""Resolve Minecraft block states to coloured boxes for the C baker.

Only derived averages (colour, contrast) leave this script: no game textures ship
with the website. Output: states.bin + colors.json
"""
import json, os, struct, functools, re
import numpy as np
from PIL import Image

ROOT = os.environ.get('MC_ASSETS', os.path.join(os.path.dirname(__file__), 'node_modules'))
ASSETS = os.path.join(ROOT, 'minecraft-assets/minecraft-assets/data/1.21.1')
DATA = os.path.join(ROOT, 'minecraft-data/minecraft-data/data/pc/1.21.1')
TEXDIR = os.path.join(ASSETS, 'blocks')
MODELS = json.load(open(os.path.join(ASSETS, 'blocks_models.json')))
STATES = json.load(open(os.path.join(ASSETS, 'blocks_states.json')))
BLOCKS = {b['name']: b for b in json.load(open(os.path.join(DATA, 'blocks.json')))}
FACES = ['down', 'up', 'north', 'south', 'west', 'east']

GRASS = (124, 189, 107)
FOLIAGE = (72, 158, 64)
WATER = (63, 118, 228)
SPECIAL_TINT = {'birch_leaves': (128, 167, 85), 'spruce_leaves': (97, 153, 97), 'lily_pad': (32, 128, 48), 'mangrove_leaves': (146, 198, 72)}
GRASS_TINTED = {'grass_block', 'short_grass', 'tall_grass', 'fern', 'large_fern', 'sugar_cane', 'potted_fern', 'pink_petals'}
FOLIAGE_TINTED = {'oak_leaves', 'jungle_leaves', 'acacia_leaves', 'dark_oak_leaves', 'vine', 'mangrove_leaves'}
INVISIBLE = {'air', 'cave_air', 'void_air', 'light', 'barrier', 'structure_void', 'moving_piston', 'tripwire', 'string'}

def emission(name, props):
    if name == 'light': return int(props.get('level', 15))
    if name.endswith('candle') or name == 'candle':
        return 3 * int(props.get('candles', 1)) if props.get('lit') == 'true' else 0
    if 'copper_bulb' in name:
        if props.get('lit') != 'true': return 0
        return {'copper_bulb': 15, 'exposed_copper_bulb': 12, 'weathered_copper_bulb': 8, 'oxidized_copper_bulb': 4}[name.replace('waxed_', '')]
    if name in ('campfire', 'soul_campfire', 'furnace', 'smoker', 'blast_furnace', 'redstone_lamp', 'redstone_torch', 'redstone_wall_torch', 'redstone_ore', 'deepslate_redstone_ore'):
        if props.get('lit') != 'true': return 0
        return {'campfire': 15, 'soul_campfire': 10, 'redstone_lamp': 15}.get(name, 13 if 'furnace' in name or name in ('smoker', 'blast_furnace') else 7)
    if name == 'sea_pickle': return 3 + 3 * int(props.get('pickles', 1)) if props.get('waterlogged') == 'true' else 0
    if name in ('cave_vines', 'cave_vines_plant'): return 14 if props.get('berries') == 'true' else 0
    if name == 'respawn_anchor': return {0: 0, 1: 3, 2: 7, 3: 11, 4: 15}[int(props.get('charges', 0))]
    b = BLOCKS.get(name)
    return b.get('emitLight', 0) if b else 0

def _mname(ref):
    r = ref.replace('minecraft:', '')
    return r[6:] if r.startswith('block/') else r

@functools.lru_cache(maxsize=None)
def resolve_model(ref):
    name = _mname(ref)
    m = MODELS.get(name)
    if m is None: return {}, None
    tex, elems = {}, None
    if 'parent' in m:
        ptex, pel = resolve_model(m['parent'])
        tex.update(ptex); elems = pel
    tex.update(m.get('textures', {}))
    if 'elements' in m: elems = m['elements']
    return tex, elems

def restex(tex, key):
    seen = 0
    while key and key.startswith('#') and seen < 10:
        key = tex.get(key[1:]); seen += 1
    if not key or key.startswith('#'): return None
    return key.replace('minecraft:', '').replace('block/', '').replace('blocks/', '')

def _match(cond, props):
    if 'OR' in cond: return any(_match(c, props) for c in cond['OR'])
    if 'AND' in cond: return all(_match(c, props) for c in cond['AND'])
    for k, v in cond.items():
        if props.get(k) not in str(v).split('|'): return False
    return True

def variants_for(name, props):
    s = STATES.get(name)
    if not s: return []
    out = []
    if 'variants' in s:
        for key, v in s['variants'].items():
            ok = True
            if key:
                for kv in key.split(','):
                    k, val = kv.split('=')
                    if props.get(k) != val: ok = False; break
            if ok:
                out.append(v[0] if isinstance(v, list) else v); break
    if 'multipart' in s:
        for part in s['multipart']:
            if 'when' not in part or _match(part['when'], props):
                a = part['apply']; out.append(a[0] if isinstance(a, list) else a)
    return out

@functools.lru_cache(maxsize=None)
def tex_stats(name):
    p = os.path.join(TEXDIR, name + '.png')
    if not os.path.exists(p): return None
    im = Image.open(p).convert('RGBA'); w, h = im.size
    if h > w: im = im.crop((0, 0, w, w))
    a = np.asarray(im, float)
    al = a[:, :, 3] > 8
    if not al.any(): return None
    rgb = a[:, :, :3][al]
    avg = rgb.mean(axis=0)
    lum = rgb @ np.array([.2126, .7152, .0722])
    contrast = float(lum.std() / max(lum.mean(), 8))
    cover = float(al.mean())
    return tuple(avg), contrast, cover

def _box(f, t, tex, faces=FACES):
    return {'from': f, 'to': t, 'tex': tex, 'faces': faces}

def _wood(w): return (w + '_planks') if w else 'oak_planks'

def fallback(name, props):
    fac = props.get('facing', 'north')
    roty = {'north': 0, 'east': 90, 'south': 180, 'west': 270}.get(fac, 0)
    if name in ('chest', 'trapped_chest', 'ender_chest'):
        return [_box([1, 0, 1], [15, 14, 15], 'oak_planks' if name != 'ender_chest' else 'obsidian')], 0
    if name.endswith('_bed'):
        col = name[:-4]; el = [_box([0, 3, 0], [16, 9, 16], col + '_wool')]
        return el, 0
    if name.endswith('_wall_banner'):
        col = name[:-12]; return [_box([1, 0, 14], [15, 16, 15], col + '_wool'), _box([0, 14, 13], [16, 15, 14], 'oak_planks')], roty
    if name.endswith('_banner'):
        col = name[:-7]; return [_box([7, 0, 7], [9, 16, 9], 'oak_planks'), _box([1, 2, 6], [15, 16, 7], col + '_wool')], 0
    if name.endswith('_wall_hanging_sign'): return [_box([1, 0, 7], [15, 10, 9], _wood(name[:-18]))], roty
    if name.endswith('_hanging_sign'): return [_box([1, 0, 7], [15, 10, 9], _wood(name[:-13]))], 0
    if name.endswith('_wall_sign'): return [_box([0, 4, 14], [16, 12, 16], _wood(name[:-10]))], roty
    if name.endswith('_sign'): return [_box([7, 0, 7], [9, 9, 9], _wood(name[:-5])), _box([0, 9, 7], [16, 16, 9], _wood(name[:-5]))], 0
    if name.endswith('_wall_skull') or name.endswith('_wall_head'): return [_box([4, 4, 8], [12, 12, 16], 'bone_block_side')], roty
    if name.endswith('_skull') or name.endswith('_head'): return [_box([4, 0, 4], [12, 8, 12], 'bone_block_side')], 0
    if name == 'bell': return [_box([4, 4, 4], [12, 11, 12], 'gold_block'), _box([5, 11, 5], [11, 13, 11], 'gold_block')], 0
    if name == 'decorated_pot': return [_box([1, 0, 1], [15, 16, 15], 'terracotta')], 0
    if name == 'conduit': return [_box([5, 5, 5], [11, 11, 11], 'prismarine')], 0
    if name.endswith('shulker_box'): return [_box([0, 0, 0], [16, 16, 16], 'purpur_block')], 0
    return None, 0

def rot_point(p, vx, vy):
    x, y, z = p
    for _ in range((vx // 90) % 4):
        y, z = 16 - z, y
    for _ in range((vy // 90) % 4):
        x, z = 16 - z, x
    return [x, y, z]

def face_after(d, vx, vy):
    # rotate the normal the same way as points
    n = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}[d]
    x, y, z = n
    for _ in range((vx // 90) % 4):
        y, z = -z, y
    for _ in range((vy // 90) % 4):
        x, z = -z, x
    return {(0, -1, 0): 'down', (0, 1, 0): 'up', (0, 0, -1): 'north', (0, 0, 1): 'south', (-1, 0, 0): 'west', (1, 0, 0): 'east'}[(x, y, z)]

PATTERN = [
    (r'(^|_)(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|bamboo|crimson|warped)_(planks|stairs|slab|fence|door|trapdoor)|planks', 2),
    (r'_log|_wood|stem|hyphae|mangrove_roots', 5),
    (r'leaves|moss|vine|azalea|lichen', 6),
    (r'(tuff|stone|deepslate|blackstone|sandstone|end_stone|quartz|nether)_brick|bricks|polished_tuff|polished_blackstone|chiseled', 4),
    (r'(^|_)brick|mud_brick', 3),
    (r'cobble|gravel|dripstone|mossy', 10),
    (r'copper|iron_block|gold_block|chain|anchor|bell', 8),
    (r'glass|pane', 9),
    (r'grass_block|dirt|sand|podzol|mycelium|mud|clay|rooted|soul|snow', 7),
    (r'stone|andesite|tuff|calcite|deepslate|basalt|granite|diorite|ore|blackstone|prismarine', 1),
]
_pat = [(re.compile(p), i) for p, i in PATTERN]
def pattern_of(name):
    for rx, i in _pat:
        if rx.search(name): return i
    return 0

GLOW_NAMES = re.compile(r'lantern|torch|glowstone|lava|shroomlight|froglight|sea_lantern|end_rod|fire|campfire|candle|beacon|jack_o|redstone_lamp|magma')

# weathering blocks in light walls: lift them so the mottling reads as age, not as holes
LIFT = {'tuff_bricks': (1.42, 1.3, 1.12), 'cracked_tuff_bricks': (1.42, 1.3, 1.12), 'polished_tuff': (1.3, 1.22, 1.08), 'chiseled_tuff_bricks': (1.3, 1.2, 1.08), 'deepslate_bricks': (1.5, 1.42, 1.3), 'cracked_deepslate_bricks': (1.5, 1.42, 1.3), 'polished_deepslate': (1.35, 1.3, 1.2)}

class Colors:
    def __init__(self): self.idx = {}; self.list = []
    def get(self, rgb, pat, con, flags):
        key = (int(round(rgb[0])), int(round(rgb[1])), int(round(rgb[2])), pat, int(round(min(con, 1) * 15)), flags)
        if key not in self.idx:
            self.idx[key] = len(self.list); self.list.append(key)
        return self.idx[key]

FL_FULL, FL_PASS, FL_RAY, FL_LEAF, FL_INVIS = 1, 2, 4, 8, 16
CF_GLOW, CF_GLASS, CF_WATER = 1, 2, 4

def build(palette, out_states, colors=None):
    colors = colors or Colors()
    unknown = {}
    import io
    records = []
    if True:
        for st in palette:
            f = io.BytesIO(); records.append(f)
            name = st.replace('minecraft:', '').split('[')[0]
            props = {}
            if '[' in st:
                props = dict(kv.split('=') for kv in st[st.index('[') + 1:-1].split(','))
            flags = 0; emit = emission(name, props)
            boxes = []; cross = None
            if name in INVISIBLE:
                f.write(struct.pack('<BBBBH', FL_PASS | FL_INVIS, emit, 0, 0, 0xffff)); continue
            tint = None
            if name in GRASS_TINTED: tint = GRASS
            elif name in SPECIAL_TINT: tint = SPECIAL_TINT[name]
            elif name in FOLIAGE_TINTED: tint = FOLIAGE
            pat = pattern_of(name)
            cf = 0
            if GLOW_NAMES.search(name) and emit > 0: cf |= CF_GLOW
            if 'glass' in name: cf |= CF_GLASS

            def col(tn, tinted):
                s = tex_stats(tn) if tn else None
                if s is None: return None
                rgb, con, cover = s
                if tinted and tint is not None: rgb = tuple(a * b / 255 for a, b in zip(rgb, tint))
                if name in LIFT: rgb = tuple(min(255, a * b) for a, b in zip(rgb, LIFT[name]))
                return colors.get(rgb, pat, con, cf)

            if name in ('water', 'bubble_column'):
                c = colors.get(WATER, 0, 0.05, CF_WATER)
                lvl = int(props.get('level', 0)); h = 14 if lvl == 0 else max(2, 14 - int(lvl * 1.7))
                boxes.append(([0, 0, 0], [16, h, 16], [c] * 6))
                flags |= FL_PASS | FL_RAY * 0
            elif name == 'lava':
                c = colors.get((238, 120, 30), 0, 0.2, CF_GLOW)
                boxes.append(([0, 0, 0], [16, 15, 16], [c] * 6)); flags |= FL_PASS; emit = 15
            else:
                parts = variants_for(name, props)
                for v in parts:
                    tex, elems = resolve_model(v['model'])
                    if not elems: continue
                    vx, vy = v.get('x', 0), v.get('y', 0)
                    for e in elems:
                        rot = e.get('rotation')
                        fr, to = list(e['from']), list(e['to'])
                        if rot and abs(rot.get('angle', 0)) >= 45 and rot.get('axis') == 'y':
                            # cross plane
                            fd = next(iter(e.get('faces', {}).values()), None)
                            if fd:
                                tn = restex(tex, fd.get('texture'))
                                c = col(tn, 'tintindex' in fd)
                                if c is not None:
                                    hh = int(round(to[1]))
                                    cross = (hh, c)
                            continue
                        p0 = rot_point(fr, vx, vy); p1 = rot_point(to, vx, vy)
                        lo = [min(a, b) for a, b in zip(p0, p1)]; hi = [max(a, b) for a, b in zip(p0, p1)]
                        fc = [0xffff] * 6
                        for d, fd in e.get('faces', {}).items():
                            tn = restex(tex, fd.get('texture'))
                            c = col(tn, 'tintindex' in fd)
                            if c is None: continue
                            fc[FACES.index(face_after(d, vx, vy))] = c
                        if all(x == 0xffff for x in fc): continue
                        lo = [int(round(max(-16, min(32, a)))) for a in lo]; hi = [int(round(max(-16, min(32, a)))) for a in hi]
                        # zero-thickness planes keep their single visible face
                        boxes.append((lo, hi, fc))
                if not boxes and cross is None:
                    els, ry = fallback(name, props)
                    if els:
                        for e in els:
                            p0 = rot_point(e['from'], 0, ry); p1 = rot_point(e['to'], 0, ry)
                            lo = [min(a, b) for a, b in zip(p0, p1)]; hi = [max(a, b) for a, b in zip(p0, p1)]
                            c = col(e['tex'], False)
                            if c is None: c = colors.get((128, 128, 128), 0, 0.1, cf)
                            boxes.append(([int(a) for a in lo], [int(a) for a in hi], [c] * 6))
                    else:
                        unknown[name] = unknown.get(name, 0) + 1
                        flags |= FL_PASS | FL_INVIS
            if name in ('mangrove_roots', 'muddy_mangrove_roots') and boxes:
                # layered model: treat as one solid cube for the bake
                lo, hi, fc = boxes[0]
                fc = [next((c for c in fc if c != 0xffff), 0xffff) if v == 0xffff else v for v in fc]
                boxes = [([0, 0, 0], [16, 16, 16], fc)]
            # identical bounds from layered models: keep the first
            seen = {}; ub = []
            for bx in boxes:
                k = (tuple(bx[0]), tuple(bx[1]))
                if k in seen: continue
                seen[k] = 1; ub.append(bx)
            boxes = ub
            full = len(boxes) == 1 and boxes[0][0] == [0, 0, 0] and boxes[0][1] == [16, 16, 16] and all(c != 0xffff for c in boxes[0][2])
            cutout = False
            if full:
                # opaque only if the texture has no holes
                for v in variants_for(name, props):
                    tex, elems = resolve_model(v['model'])
                    for e in (elems or []):
                        for fd in e.get('faces', {}).values():
                            s = tex_stats(restex(tex, fd.get('texture')) or '')
                            if s and s[2] < 0.995: cutout = True
                if cf & CF_GLASS: cutout = True
            if name in ('mangrove_roots', 'muddy_mangrove_roots'): cutout = False
            solid_ish = full and not cutout
            if solid_ish: flags |= FL_FULL | FL_RAY
            elif full and (cf & CF_GLASS): flags |= FL_RAY  # glass seals the interior, blocks flood
            elif 'leaves' in name: flags |= FL_LEAF | FL_RAY  # leaves are stochastic for light but seal the crown
            elif re.search(r'_door|_trapdoor|iron_bars|_pane', name): flags |= FL_RAY
            elif re.search(r'stairs|_slab|_wall|^(mud_|)bricks?_wall|piston|chest|barrel|shulker', name): flags |= FL_RAY | FL_PASS
            else: flags |= FL_PASS
            if name in ('water', 'bubble_column', 'lava'): flags |= FL_PASS
            f.write(struct.pack('<BBBBH', flags, emit, len(boxes), cross[0] if cross else 0, cross[1] if cross else 0xffff))
            for lo, hi, fc in boxes:
                f.write(struct.pack('<6b', *lo, *hi)); f.write(struct.pack('<6H', *fc))
    # blocks that bake identically share one id, so their common faces cull and merge
    uniq = {}; order = []; lut = []
    for r in records:
        b = r.getvalue()
        if b not in uniq: uniq[b] = len(order); order.append(b)
        lut.append(uniq[b])
    with open(out_states, 'wb') as f:
        f.write(struct.pack('<i', len(order)))
        for b in order: f.write(b)
    return colors, unknown, np.array(lut, np.uint16)
