import glob, gzip, os, shutil, struct, sys, time, zlib
# Exile into the void, run with mmo-c STOPPED:  EXV_D=<mmo-c folder>/ python3 exilevoid.py go   (plan = read-only count)
# The map is exil_gesamt.schem, pasted at origin 0,100,0: blocks x -160..199, y -33..316, z -190..195.
# Everything else is old overworld: every chunk outside that box goes, inside it the old bedrock/deepslate slab
# (y -64..-34) and the columns past the box edge are cleared. A full copy of region/entities/poi is kept first.
D = os.environ['EXV_D']  # the mmo-c server folder, with a trailing slash
WORLD = D + 'aetherion_exile/'
X0, X1, Z0, Z1, YCUT = -160, 199, -190, 195, -34
CX0, CX1, CZ0, CZ1 = X0 >> 4, X1 >> 4, Z0 >> 4, Z1 >> 4
AIR = 'minecraft:air'


def die(msg):
    print('STOP: ' + msg)
    sys.exit(1)


# ---------------------------------------------------------------- lossless NBT
def rd(b, i, t):
    if t == 1: return b[i:i + 1], i + 1
    if t == 2: return b[i:i + 2], i + 2
    if t in (3, 5): return b[i:i + 4], i + 4
    if t in (4, 6): return b[i:i + 8], i + 8
    if t == 7: n = struct.unpack('>i', b[i:i + 4])[0]; return b[i:i + 4 + n], i + 4 + n
    if t == 8: n = struct.unpack('>H', b[i:i + 2])[0]; return b[i + 2:i + 2 + n].decode('utf-8', 'surrogatepass'), i + 2 + n
    if t == 9:
        et = b[i]; n = struct.unpack('>i', b[i + 1:i + 5])[0]; i += 5; out = []
        for _ in range(n):
            v, i = rd(b, i, et); out.append(v)
        return (et, out), i
    if t == 10:
        out = {}
        while True:
            tt = b[i]; i += 1
            if tt == 0: return out, i
            n = struct.unpack('>H', b[i:i + 2])[0]; name = b[i + 2:i + 2 + n].decode('utf-8', 'surrogatepass'); i += 2 + n
            v, i = rd(b, i, tt); out[name] = (tt, v)
    if t == 11: n = struct.unpack('>i', b[i:i + 4])[0]; return b[i:i + 4 + 4 * n], i + 4 + 4 * n
    if t == 12:
        n = struct.unpack('>i', b[i:i + 4])[0]
        return list(struct.unpack('>%dq' % n, b[i + 4:i + 4 + 8 * n])), i + 4 + 8 * n
    raise ValueError('tag %d' % t)


def wr(t, v, out):
    if t in (1, 2, 3, 4, 5, 6, 7, 11):
        out.append(v)
    elif t == 8:
        e = v.encode('utf-8', 'surrogatepass'); out.append(struct.pack('>H', len(e))); out.append(e)
    elif t == 9:
        et, items = v
        if not items and et == 0:
            et = 0
        out.append(bytes([et])); out.append(struct.pack('>i', len(items)))
        for x in items:
            wr(et, x, out)
    elif t == 10:
        for name, (tt, x) in v.items():
            e = name.encode('utf-8', 'surrogatepass')
            out.append(bytes([tt])); out.append(struct.pack('>H', len(e))); out.append(e)
            wr(tt, x, out)
        out.append(b'\0')
    elif t == 12:
        out.append(struct.pack('>i', len(v))); out.append(struct.pack('>%dq' % len(v), *v))


def val(c, k, default=None):
    e = c.get(k)
    return default if e is None else e[1]


def byte_of(raw):
    return struct.unpack('>b', raw)[0]


def int_of(raw):
    return struct.unpack('>i', raw)[0]


# ---------------------------------------------------------------- region files
def read_region(path):
    with open(path, 'rb') as f:
        data = f.read()
    out = {}
    if len(data) < 8192:
        return out
    for k in range(1024):
        sec = int.from_bytes(data[k * 4:k * 4 + 3], 'big')
        if not sec:
            continue
        stamp = data[4096 + k * 4:4100 + k * 4]
        n, t = struct.unpack('>iB', data[sec * 4096:sec * 4096 + 5])
        if t & 0x80:
            cx, cz = (k & 31), (k >> 5)
            out[k] = ('ext', t, None, stamp)
        else:
            out[k] = ('in', t, data[sec * 4096 + 5:sec * 4096 + 4 + n], stamp)
    return out


def write_region(path, chunks):
    head = bytearray(8192); body = []; sec = 2
    for k in sorted(chunks):
        kind, t, blob, stamp = chunks[k]
        rec = struct.pack('>iB', len(blob) + 1, t) + blob
        n = (len(rec) + 4095) // 4096
        if n > 255:
            die('chunk too big to write in %s' % path)
        rec += b'\0' * (n * 4096 - len(rec))
        head[k * 4:k * 4 + 4] = sec.to_bytes(3, 'big') + bytes([n])
        head[4096 + k * 4:4100 + k * 4] = stamp
        body.append(rec); sec += n
    tmp = path + '.void-tmp'
    with open(tmp, 'wb') as f:
        f.write(bytes(head)); f.write(b''.join(body))
    st = os.stat(path)
    os.chown(tmp, st.st_uid, st.st_gid)
    os.replace(tmp, path)


def unpack(t, blob):
    raw = zlib.decompress(blob) if t == 2 else gzip.decompress(blob) if t == 1 else blob
    n = struct.unpack('>H', raw[1:3])[0]
    return raw[3:3 + n].decode('utf-8'), rd(raw, 3 + n, 10)[0]


def pack(name, root):
    out = [b'\x0a']
    e = name.encode('utf-8'); out.append(struct.pack('>H', len(e))); out.append(e)
    wr(10, root, out)
    return zlib.compress(b''.join(out))


# ---------------------------------------------------------------- block edits
def clear_section(sec, gone):
    """gone(x, yy, z) -> True for local positions that become air. Returns the number of cleared blocks."""
    bs = val(sec, 'block_states')
    if bs is None:
        return 0
    et, pal = val(bs, 'palette', (10, []))
    if not pal:
        return 0
    names = [val(p, 'Name', AIR) for p in pal]
    if len(pal) == 1:
        idx = [0] * 4096
    else:
        bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
        data = [v & 0xFFFFFFFFFFFFFFFF for v in val(bs, 'data', [])]
        idx = [(data[i // per] >> ((i % per) * bits)) & mask if i // per < len(data) else 0 for i in range(4096)]
    try:
        air = names.index(AIR)
    except ValueError:
        pal = pal + [{'Name': (8, AIR)}]; names.append(AIR); air = len(pal) - 1
    n = 0
    for i in range(4096):
        if names[idx[i]] != AIR and gone(i & 15, i >> 8, (i >> 4) & 15):
            idx[i] = air; n += 1
    if not n:
        return 0
    used = sorted(set(idx))
    remap = {o: j for j, o in enumerate(used)}
    newpal = [pal[o] for o in used]
    bs2 = dict(bs)
    bs2['palette'] = (9, (10, newpal))
    if len(newpal) == 1:
        bs2.pop('data', None)
    else:
        bits = max(4, (len(newpal) - 1).bit_length()); per = 64 // bits
        longs = []
        for start in range(0, 4096, per):
            v = 0
            for j, o in enumerate(idx[start:start + per]):
                v |= remap[o] << (j * bits)
            longs.append(v - (1 << 64) if v >= 1 << 63 else v)
        bs2['data'] = (12, longs)
    sec['block_states'] = (10, bs2)
    return n


def edit_chunk(root, cx, cz):
    edge = cx * 16 < X0 or cx * 16 + 15 > X1 or cz * 16 < Z0 or cz * 16 + 15 > Z1
    total = 0
    et, secs = val(root, 'sections', (10, []))
    for sec in secs:
        y = byte_of(val(sec, 'Y'))
        base = y * 16

        def gone(x, yy, z, base=base):
            wx, wz = cx * 16 + x, cz * 16 + z
            return base + yy <= YCUT or wx < X0 or wx > X1 or wz < Z0 or wz > Z1
        if base > YCUT and not edge:
            continue
        total += clear_section(sec, gone)
    if total:
        be = val(root, 'block_entities')
        if be is not None:
            et2, items = be
            keep = [e for e in items if not (int_of(val(e, 'y')) <= YCUT or not (X0 <= int_of(val(e, 'x')) <= X1)
                                             or not (Z0 <= int_of(val(e, 'z')) <= Z1))]
            root['block_entities'] = (9, (et2 if keep else 10, keep))
        root.pop('Heightmaps', None)
        if 'isLightOn' in root:
            root['isLightOn'] = (1, b'\0')
    return total


# ---------------------------------------------------------------- main
def running():
    for p in glob.glob('/proc/[0-9]*'):
        try:
            if os.readlink(p + '/cwd').startswith(D.rstrip('/')):
                return p[6:]
        except OSError:
            pass
    return None


mode = sys.argv[1] if len(sys.argv) > 1 else 'plan'
if mode == 'go':
    pid = running()
    if pid:
        die('mmo-c is still running (pid %s), nothing changed' % pid)
    backup = D + 'aetherion_exile.void2-backup-' + time.strftime('%Y%m%d-%H%M%S') + '/'
    for d in ('region', 'entities', 'poi'):
        if os.path.isdir(WORLD + d):
            shutil.copytree(WORLD + d, backup + d)
    print('full copy of region/entities/poi in', backup)
    aside = backup + 'outside/'

dropped = edited = blocks = files_out = 0
for d in ('region', 'entities', 'poi'):
    for path in sorted(glob.glob(WORLD + d + '/r.*.mca')):
        rx, rz = map(int, os.path.basename(path).split('.')[1:3])
        chunks = read_region(path)
        keep, changed = {}, False
        for k, c in chunks.items():
            cx, cz = rx * 32 + (k & 31), rz * 32 + (k >> 5)
            if not (CX0 <= cx <= CX1 and CZ0 <= cz <= CZ1):
                dropped += d == 'region'; changed = True
                continue
            if d == 'region' and c[0] == 'in':
                name, root = unpack(c[1], c[2])
                n = edit_chunk(root, cx, cz)
                if n:
                    blocks += n; edited += 1; changed = True
                    c = ('in', 2, pack(name, root), c[3])
            keep[k] = c
        if mode != 'go' or not changed:
            continue
        if not keep:
            os.makedirs(aside + d, exist_ok=True)
            shutil.move(path, aside + d + '/' + os.path.basename(path))
            files_out += 1
            continue
        if any(c[0] == 'ext' for c in keep.values()):
            die('external chunk in %s, stopping before writing it (copy is in the backup)' % path)
        write_region(path, keep)
    if mode == 'go':
        for ext in glob.glob(WORLD + d + '/c.*.mcc'):
            cx, cz = map(int, os.path.basename(ext).split('.')[1:3])
            if not (CX0 <= cx <= CX1 and CZ0 <= cz <= CZ1):
                os.makedirs(aside + d, exist_ok=True)
                shutil.move(ext, aside + d + '/' + os.path.basename(ext))
print('%s: %d chunks outside the map %s, %d map chunks cleaned (%d old blocks to air), %d region files emptied'
      % (mode, dropped, 'deleted' if mode == 'go' else 'to delete', edited, blocks, files_out))
