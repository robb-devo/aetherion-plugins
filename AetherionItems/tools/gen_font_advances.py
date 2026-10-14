"""
Vanilla 1.21.1 default-font advances for de.aetherion.core.api.TextWidth.

    python gen_font_advances.py ../../AetherionCore/src/main/resources/font/advances-1.21.1.bin

One byte per BMP code point (0 = no vanilla glyph). Same rules as the client: bitmap pages
round(opaque width * scale) + 1, unifont 15.1.05 (glyph width) / 2 + 1, space 4.
Needs the 1.21.1 client jar and its asset index 17 in %APPDATA%/.minecraft.
"""
import io, re, json, sys, zipfile, os
from PIL import Image
JAR = os.path.expandvars(r'%APPDATA%\.minecraft\versions\1.21.1\1.21.1.jar')
OBJ = os.path.expandvars(r'%APPDATA%\.minecraft\assets\objects')
UNI = os.path.join(OBJ, '66', '666999116c467f10622db1527a06ddc0a6efad2a')
UNIJSON = os.path.join(OBJ, 'f4', 'f40266672fa6cd5c678cbfd5a7fdcfe06e30c629')
jar = zipfile.ZipFile(JAR)
adv = {' ': 4, '‌': 0}
for p in json.loads(jar.read('assets/minecraft/font/include/default.json'))['providers']:
    path = 'assets/minecraft/textures/' + p['file'].split(':')[1]
    im = Image.open(io.BytesIO(jar.read(path))).convert('RGBA')
    rows = p['chars']; ch = im.height // len(rows); cw = im.width // len(rows[0]); scale = p.get('height', 8) / ch
    for r, line in enumerate(rows):
        for c, cp in enumerate(line):
            if cp == '\0' or cp in adv:
                continue
            w = 0
            for x in range(cw - 1, -1, -1):
                if any(im.getpixel((c * cw + x, r * ch + y))[3] for y in range(ch)):
                    w = x + 1
                    break
            adv[cp] = int(0.5 + w * scale) + 1
ov = []
text = re.sub(r',(\s*[\]}])', lambda m: m.group(1), open(UNIJSON, encoding='utf-8').read())
for p in json.loads(text)['providers']:
    if p.get('filter', {}).get('jp'):
        continue
    for o in p.get('size_overrides', []):
        ov.append((ord(o['from']), ord(o['to']), o['left'], o['right']))
z = zipfile.ZipFile(UNI)
hexname = [n for n in z.namelist() if n.endswith('.hex')][0]
for line in z.read(hexname).decode().splitlines():
    k, v = line.split(':')
    cp = int(k, 16)
    if cp > 0xFFFF or chr(cp) in adv:
        continue
    bits = len(v) * 4 // 16
    step = len(v) // 16
    mask = 0
    for i in range(16):
        mask |= int(v[i * step:(i + 1) * step], 16)
    left = right = None
    for (a, b, l, r) in ov:
        if a <= cp <= b:
            left, right = l, r
    if left is None:
        if mask == 0:
            left, right = 0, bits
        else:
            m32 = mask << (32 - bits)
            left = 32 - m32.bit_length()
            right = 32 - ((m32 & -m32).bit_length() - 1) - 1
    adv[chr(cp)] = (right - left + 1) // 2 + 1
table = bytearray(0x10000)
for ch, v in adv.items():
    if ord(ch) > 0xFFFF:
        continue
    assert 0 <= v < 256
    table[ord(ch)] = v
os.makedirs(os.path.dirname(os.path.abspath(sys.argv[1])), exist_ok=True)
with open(sys.argv[1], 'wb') as f:
    f.write(table)
print(sum(1 for v in table if v), 'glyphs')
