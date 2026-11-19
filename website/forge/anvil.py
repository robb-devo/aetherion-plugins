"""Minecraft Java region files (Anvil, 1.18 and newer) -> numpy (y,z,x) palette-index array.

A world on disk is a folder of r.<rx>.<rz>.mca files, each 32 x 32 chunks. This reads a box of
blocks out of them in the same shape schem.py and nbtstruct.py produce, so the baker does not
care where a build came from.

  python3 anvil.py <region dir>                       what is in there: chunks, heights, bounds
  python3 anvil.py <region dir> x0 z0 x1 z1 [y0 y1]   load a box and print what it is made of
"""
import gzip, io, os, re, struct, sys, zlib
import numpy as np
from schem import _read_payload

AIR = ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')


def _chunk_nbt(raw):
    f = io.BytesIO(raw)
    t = f.read(1)[0]
    n = struct.unpack('>H', f.read(2))[0]
    f.read(n)
    return _read_payload(f, t)


def _lz4_java(data):
    """LZ4BlockOutputStream framing, which servers write when region-file-compression=lz4."""
    try:
        import lz4.block
    except ImportError as e:
        raise RuntimeError('this world is LZ4-compressed: pip install lz4') from e
    out = []
    o = 0
    while o + 21 <= len(data) and data[o:o + 8] == b'LZ4Block':
        token = data[o + 8]
        clen, dlen = struct.unpack('<ii', data[o + 9:o + 17])
        body = data[o + 21:o + 21 + clen]
        if dlen == 0:
            break
        out.append(body if (token & 0xf0) == 0x10 else lz4.block.decompress(body, uncompressed_size=dlen))
        o += 21 + clen
    return b''.join(out)


def _inflate(kind, data):
    if kind == 1: return gzip.decompress(data)
    if kind == 2: return zlib.decompress(data)
    if kind == 3: return data
    if kind == 4: return _lz4_java(data)
    raise ValueError(f'unknown chunk compression {kind}')


def regions(folder):
    out = {}
    for name in os.listdir(folder):
        m = re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca', name)
        if m and os.path.getsize(os.path.join(folder, name)) >= 8192:
            out[(int(m.group(1)), int(m.group(2)))] = os.path.join(folder, name)
    return out


def chunks(path, rx, rz, want=None):
    """Yields (chunk x, chunk z, nbt root) for every chunk stored in one region file."""
    with open(path, 'rb') as f:
        raw = f.read()
    for i in range(1024):
        loc = raw[i * 4:i * 4 + 4]
        offset = (loc[0] << 16) | (loc[1] << 8) | loc[2]
        if offset == 0 or loc[3] == 0:
            continue
        cx, cz = rx * 32 + (i % 32), rz * 32 + (i // 32)
        if want and not want(cx, cz):
            continue
        a = offset * 4096
        if a + 5 > len(raw):
            continue
        length = struct.unpack('>I', raw[a:a + 4])[0]
        kind = raw[a + 4]
        if kind & 0x80:  # too big for the region file: it lives next to it
            side = os.path.join(os.path.dirname(path), f'c.{cx}.{cz}.mcc')
            if not os.path.exists(side):
                continue
            data = open(side, 'rb').read()
            kind &= 0x7f
        else:
            data = raw[a + 5:a + 4 + length]
        try:
            yield cx, cz, _chunk_nbt(_inflate(kind, data))
        except Exception as e:  # one broken chunk should not cost the island
            print(f'chunk {cx},{cz} unreadable: {e}', file=sys.stderr)


def _state(p):
    props = p.get('Properties') or {}
    return p['Name'] + ('[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']' if props else '')


_IDX = np.arange(4096)


def _section(sec, intern):
    """One 16^3 section as global palette ids, shape (16,16,16) in y,z,x; None if it is only air."""
    bs = sec.get('block_states')
    if not bs:
        return None
    names = [_state(p) for p in bs['palette']]
    if all(n in AIR for n in names):
        return None
    ids = np.array([intern(n) for n in names], dtype=np.uint16)
    data = bs.get('data')
    if data is None or len(names) == 1:
        return np.full((16, 16, 16), ids[0], dtype=np.uint16)
    bits = max(4, (len(names) - 1).bit_length())
    per = 64 // bits  # entries never straddle two longs
    d = data.astype('>i8').view('>u8').astype(np.uint64)
    v = (d[_IDX // per] >> ((_IDX % per) * bits).astype(np.uint64)) & np.uint64((1 << bits) - 1)
    return ids[np.minimum(v, len(names) - 1).astype(np.intp)].reshape(16, 16, 16)


def scan(folder):
    """Where the blocks are, without keeping them: per region the chunk count, overall bounds and heights."""
    lo, hi, n, version = [10**9] * 3, [-10**9] * 3, 0, None
    dummy = {}
    intern = lambda s: dummy.setdefault(s, len(dummy))
    for (rx, rz), path in sorted(regions(folder).items()):
        k = 0
        for cx, cz, root in chunks(path, rx, rz):
            version = root.get('DataVersion', version)
            ys = [int(s['Y']) for s in root.get('sections', []) if _section(s, intern) is not None]
            if not ys:
                continue
            k += 1
            lo = [min(lo[0], cx * 16), min(lo[1], min(ys) * 16), min(lo[2], cz * 16)]
            hi = [max(hi[0], cx * 16 + 16), max(hi[1], max(ys) * 16 + 16), max(hi[2], cz * 16 + 16)]
        n += k
        print(f'r.{rx}.{rz}.mca  {k} chunks with blocks')
    print(f'DataVersion {version}, {n} chunks, x {lo[0]}..{hi[0]}, y {lo[1]}..{hi[1]}, z {lo[2]}..{hi[2]}')
    return lo, hi


def load(folder, x0, z0, x1, z1, y0=None, y1=None):
    """Blocks of the box [x0,x1) x [z0,z1), heights y0..y1 or whatever is occupied.
    Returns (arr (H,L,W) uint16, palette of 'name[props]' strings, info with the world offset)."""
    pal = ['minecraft:air']
    index = {'minecraft:air': 0}
    def intern(s):
        i = index.get(s)
        if i is None:
            i = index[s] = len(pal)
            pal.append(s)
        return i
    inside = lambda cx, cz: cx * 16 < x1 and cx * 16 + 16 > x0 and cz * 16 < z1 and cz * 16 + 16 > z0
    secs = {}
    version = None
    for (rx, rz), path in sorted(regions(folder).items()):
        if rx * 512 >= x1 or rx * 512 + 512 <= x0 or rz * 512 >= z1 or rz * 512 + 512 <= z0:
            continue
        for cx, cz, root in chunks(path, rx, rz, inside):
            version = root.get('DataVersion', version)
            for s in root.get('sections', []):
                a = _section(s, intern)
                if a is not None:
                    secs[(cx, int(s['Y']), cz)] = a
    if not secs:
        raise SystemExit('no blocks in that box')
    if len(pal) > 65535:
        raise SystemExit('more than 65535 block states: load a smaller box')
    ys = [k[1] for k in secs]
    if y0 is None: y0 = min(ys) * 16
    if y1 is None: y1 = max(ys) * 16 + 16
    arr = np.zeros((y1 - y0, z1 - z0, x1 - x0), dtype=np.uint16)
    for (cx, sy, cz), a in secs.items():
        ax, ay, az = cx * 16, sy * 16, cz * 16
        gx0, gx1 = max(ax, x0), min(ax + 16, x1)
        gy0, gy1 = max(ay, y0), min(ay + 16, y1)
        gz0, gz1 = max(az, z0), min(az + 16, z1)
        if gx0 >= gx1 or gy0 >= gy1 or gz0 >= gz1:
            continue
        arr[gy0 - y0:gy1 - y0, gz0 - z0:gz1 - z0, gx0 - x0:gx1 - x0] = a[gy0 - ay:gy1 - ay, gz0 - az:gz1 - az, gx0 - ax:gx1 - ax]
    return arr, pal, dict(W=x1 - x0, H=y1 - y0, L=z1 - z0, offset=[x0, y0, z0], version=version)


def trim(arr, pal, info, pad=1):
    """Cuts the box down to what is occupied, keeping `pad` cells of air around it."""
    air = np.array([n.split('[')[0] in AIR for n in pal])
    solid = ~air[arr]
    ax = [np.flatnonzero(solid.any(axis=tuple(j for j in range(3) if j != i))) for i in range(3)]
    lo = [max(0, int(a[0]) - pad) for a in ax]
    hi = [min(arr.shape[i], int(a[-1]) + 1 + pad) for i, a in enumerate(ax)]
    out = arr[lo[0]:hi[0], lo[1]:hi[1], lo[2]:hi[2]]
    off = info['offset']
    return out, dict(info, H=out.shape[0], L=out.shape[1], W=out.shape[2], offset=[off[0] + lo[2], off[1] + lo[0], off[2] + lo[1]])


if __name__ == '__main__':
    import collections
    if len(sys.argv) < 6:
        scan(sys.argv[1])
    else:
        box = [int(v) for v in sys.argv[2:6]]
        ys = [int(v) for v in sys.argv[6:8]] or [None, None]
        arr, pal, info = load(sys.argv[1], *box, *ys)
        arr, info = trim(arr, pal, info)
        print(info, 'palette', len(pal))
        c = np.bincount(arr.ravel(), minlength=len(pal))
        agg = collections.Counter()
        for i, n in enumerate(c): agg[pal[i].split('[')[0]] += int(n)
        for k, v in agg.most_common(24): print(v, k)
