"""Status panel, radar and VFX art + the HUD anchor shader + generated sounds.

Usage: python gen_panel.py [art]   ("art" skips the ffmpeg sounds, which re-encode to new bytes every run)

Writes into AetherionItems/resourcepack:
  assets/aetherion/font/panel.json, font/vfx.json, textures/font/panel/*, textures/font/vfx/*
  assets/minecraft/shaders/core/rendertype_text.vsh   (palette parsed from PanelPalette.java)
  assets/aetherion/sounds.json + sounds/*.ogg          (synthesized with ffmpeg, original audio)
"""
import json
import math
import os
import re
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gen_gui_chrome import hx  # noqa: E402
import gen_panel_hd as hd  # noqa: E402

PACK = os.path.join(HERE, '..', 'resourcepack')
JAVA = os.path.join(HERE, '..', 'src', 'main', 'java', 'de', 'aetherion', 'items', 'hud', 'PanelPalette.java')

NEG_SPACE, POS_SPACE = 0xF801, 0xF821
# Shader geometry (GUI px). Mirrored in StatusPanel / RadarHud.
PANEL_X, PANEL_Y, ROW_H = 2, 3, 12
RADAR_W, RADAR_RIGHT, RADAR_Y = 64, 3, 54


def out(*parts):
    p = os.path.join(PACK, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def img(w, h):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def spaces():
    adv = {}
    for i in range(8):
        adv[chr(NEG_SPACE + i)] = -(1 << i)
        adv[chr(POS_SPACE + i)] = 1 << i
    return {'type': 'space', 'advances': adv}


# ----------------------------------------------------------------------------- panel + radar art (gen_panel_hd.py)

# Marker glyphs from U+E421 on, in this order. Sizes are GUI px (the HD textures are 4x).
DOT_SIZES = {'quest': 7, 'npc': 5, 'party': 5, 'boss': 7, 'north': 5, 'quest_up': 7, 'quest_down': 7}


# ----------------------------------------------------------------------------- vfx art (white, tinted in game)

def vfx(kind, n=128):
    im = img(n, n)
    px = im.load()
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy) / c
            ang = math.atan2(dy, dx)
            a = 0.0
            if kind == 'slash':
                # crescent: band between two offset circles, thick in the middle, fading to the tips
                d2 = math.hypot(dx, dy + c * 0.35) / c
                if d < 0.95 and d2 > 0.92 and dy < c * 0.15:
                    t = max(0.0, 1.0 - abs(dx) / (c * 0.95))
                    a = min(1.0, t * 1.6) * (1.0 - max(0.0, d - 0.85) / 0.1)
            elif kind == 'ring':
                a = max(0.0, 1.0 - abs(d - 0.88) / 0.07)
            elif kind == 'rune':
                ring = max(0.0, 1.0 - abs(d - 0.9) / 0.04) + max(0.0, 1.0 - abs(d - 0.7) / 0.03)
                ticks = 0.0
                if 0.72 < d < 0.88:
                    seg = (ang / (2 * math.pi) * 24) % 1.0
                    ticks = 1.0 if seg < 0.18 else 0.0
                    if int((ang / (2 * math.pi) * 24) + 24) % 3 == 0 and seg < 0.5:
                        ticks = 0.8
                star = 0.0
                for k in range(6):
                    t = k * math.pi / 3
                    ux, uy = math.cos(t), math.sin(t)
                    vx, vy = math.cos(t + 2 * math.pi / 3), math.sin(t + 2 * math.pi / 3)
                    # line from u*0.7 to v*0.7
                    ax, ay, bx, by = ux * 0.7 * c, uy * 0.7 * c, vx * 0.7 * c, vy * 0.7 * c
                    ex, ey = bx - ax, by - ay
                    tt = max(0.0, min(1.0, ((dx - ax) * ex + (dy - ay) * ey) / (ex * ex + ey * ey)))
                    dist = math.hypot(dx - ax - ex * tt, dy - ay - ey * tt)
                    star = max(star, 1.0 - dist / 1.6)
                a = min(1.0, ring + ticks + max(0.0, star))
            elif kind == 'tele_edge':
                band = max(0.0, 1.0 - abs(d - 0.93) / 0.05)
                dash = 1.0 if ((ang / (2 * math.pi) * 32) % 1.0) < 0.6 else 0.35
                a = band * dash
            elif kind == 'tele_fill':
                a = 0.0 if d > 0.95 else 0.25 + 0.45 * d * d
            if a > 0.01:
                px[x, y] = (255, 255, 255, int(255 * min(1.0, a)))
    return im


# ----------------------------------------------------------------------------- shader

def palette():
    src = open(JAVA, encoding='utf-8').read()
    block = src[src.index('palette:start'):src.index('palette:end')]
    return [int(h, 16) for h in re.findall(r'0x([0-9A-Fa-f]{6})', block)]


def shader(pal):
    entries = ',\n    '.join('vec3(%.4f, %.4f, %.4f)' % ((c >> 16 & 255) / 255, (c >> 8 & 255) / 255, (c & 255) / 255)
                            for c in pal)
    return f'''#version 150

#moj_import <fog.glsl>

// Vanilla rendertype_text plus the Aetherion HUD anchor (AetherionItems/tools/gen_panel.py).
// ActionBar glyphs coloured (76, 32 + 4 * code, b) are moved: code 0..15 = status panel row (top-left,
// b = palette index), 16 = radar body (top-right), 17 = radar marker (b = y offset + 128).
// Their drop shadows (76/4, 8..26) are dropped. Everything else renders exactly like vanilla.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

const int PAL_N = {len(pal)};
const vec3 PALETTE[PAL_N] = vec3[](
    {entries}
);

void main() {{
    vec3 pos = Position;
    vec4 col = Color;
    bool drop = false;
    if (ProjMat[3][3] == 1.0 && ProjMat[2][3] == 0.0) {{
        float guiW = 2.0 / ProjMat[0][0];
        float guiH = -2.0 / ProjMat[1][1];
        float fromBottom = guiH - Position.y;
        if (fromBottom > -96.0 && fromBottom < 160.0) {{
            ivec3 c = ivec3(round(Color.rgb * 255.0));
            if (c.r == 76 && c.g >= 32 && c.g <= 104 && (c.g & 3) == 0) {{
                int code = (c.g - 32) >> 2;
                float y = Position.y - guiH + 41.0;
                if (code < 16) {{
                    pos.x = Position.x - guiW * 0.5 + {PANEL_X:.1f};
                    pos.y = y + {PANEL_Y:.1f} + float(code) * {ROW_H:.1f};
                    col.rgb = PALETTE[min(c.b, PAL_N - 1)];
                }} else {{
                    pos.x = Position.x - guiW * 0.5 + guiW - {RADAR_RIGHT + RADAR_W:.1f};
                    pos.y = y + {RADAR_Y:.1f};
                    if (code == 16) {{
                        col.rgb = PALETTE[min(c.b, PAL_N - 1)];
                    }} else {{
                        pos.y += float(c.b - 128);
                        col.rgb = vec3(1.0);
                    }}
                }}
            }} else if (c.r == 19 && c.g >= 8 && c.g <= 26) {{
                drop = true;
            }}
        }}
    }}
    gl_Position = drop ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = col * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}}
'''


# ----------------------------------------------------------------------------- sounds (ffmpeg lavfi synthesis)

SOUNDS = {
    # key: (lavfi graph, seconds)
    'ui/click': ("sine=f=1800:d=0.05,afade=t=out:st=0.005:d=0.045,volume=0.6", 0.05),
    'ui/open': ("sine=f=520:d=0.22,asetrate=44100*1.0,afade=t=in:d=0.02,afade=t=out:st=0.06:d=0.16,"
                "aecho=0.6:0.5:40:0.35,volume=0.7", 0.3),
    'ui/confirm': ("sine=f=880:d=0.09[a];sine=f=1320:d=0.14[b];[a][b]concat=n=2:v=0:a=1,"
                   "afade=t=out:st=0.12:d=0.11,aecho=0.6:0.4:30:0.3,volume=0.6", 0.25),
    'ui/deny': ("sine=f=220:d=0.16,vibrato=f=28:d=0.6,afade=t=out:st=0.04:d=0.12,volume=0.7", 0.16),
    'ui/quest_ping': ("sine=f=1046:d=0.5,afade=t=out:st=0.02:d=0.48,aecho=0.7:0.6:90|180:0.4|0.25,volume=0.55", 0.7),
    'ui/levelup': ("sine=f=523:d=0.12[a];sine=f=659:d=0.12[b];sine=f=784:d=0.12[c];sine=f=1046:d=0.5[d];"
                   "[a][b][c][d]concat=n=4:v=0:a=1,afade=t=out:st=0.5:d=0.36,aecho=0.7:0.6:60|120:0.4|0.3,volume=0.6", 0.95),
    'combat/slash': ("anoisesrc=d=0.22:c=pink:a=0.8,highpass=f=1400,lowpass=f=7000,"
                     "afade=t=in:d=0.03,afade=t=out:st=0.05:d=0.17,volume=1.4", 0.22),
    'combat/heavy': ("anoisesrc=d=0.35:c=brown:a=0.9,lowpass=f=900,afade=t=out:st=0.02:d=0.33[n];"
                     "sine=f=70:d=0.35,afade=t=out:st=0.02:d=0.33[s];[n][s]amix=inputs=2:weights=1 1.2,volume=2.0", 0.35),
    'combat/ring': ("sine=f=1568:d=0.6,afade=t=out:st=0.0:d=0.6[a];sine=f=2349:d=0.6,afade=t=out:st=0:d=0.45[b];"
                    "[a][b]amix=inputs=2,aecho=0.7:0.5:50:0.4,volume=1.2", 0.7),
    'boss/telegraph': ("sine=f=110:d=0.9,vibrato=f=6:d=0.3,afade=t=in:d=0.6,afade=t=out:st=0.75:d=0.15[a];"
                       "anoisesrc=d=0.9:c=pink:a=0.3,highpass=f=2500,afade=t=in:d=0.85[b];[a][b]amix=inputs=2,volume=1.6", 0.9),
    'boss/impact': ("anoisesrc=d=0.8:c=brown:a=1.0,lowpass=f=500,afade=t=out:st=0.03:d=0.77[n];"
                    "sine=f=45:d=0.8,afade=t=out:st=0.02:d=0.78[s];[n][s]amix=inputs=2:weights=1 1.5,volume=2.4", 0.8),
    'boss/rune': ("sine=f=196:d=1.6[a];sine=f=294:d=1.6[b];[a][b]amix=inputs=2,tremolo=f=5:d=0.4,"
                  "afade=t=in:d=0.4,afade=t=out:st=1.0:d=0.6,aecho=0.8:0.7:120:0.4,volume=1.0", 1.8),
    'boss/appear': ("sine=f=98:d=1.4[a];sine=f=147:d=1.4[b];sine=f=196:d=1.4[c];[a][b][c]amix=inputs=3,"
                    "afade=t=in:d=0.08,afade=t=out:st=0.4:d=1.0,aecho=0.8:0.8:200|400:0.5|0.3,volume=1.3", 1.6),
}

# Reworked vanilla: sounds.json points at vanilla files with new pitch/volume (no vanilla audio is copied).
VANILLA = {
    'combat/slash': [{'name': 'entity/player/attack/sweep1', 'pitch': 1.25, 'volume': 0.45, 'weight': 1}],
    'combat/heavy': [{'name': 'entity/player/attack/strong1', 'pitch': 0.7, 'volume': 0.6}],
    'boss/impact': [{'name': 'random/explode1', 'pitch': 0.6, 'volume': 0.5}],
}


def sounds():
    defs = {}
    for key, (graph, _) in SOUNDS.items():
        path = out('assets', 'aetherion', 'sounds', key + '.ogg')
        cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-filter_complex', graph + ',aformat=channel_layouts=mono',
               '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '4', path]
        subprocess.run(cmd, check=True)
        event = key.replace('/', '.')
        entry = [{'name': 'aetherion:' + key}]
        for v in VANILLA.get(key, []):
            entry.append({'name': 'minecraft:' + v['name'], 'pitch': v['pitch'], 'volume': v['volume']})
        defs[event] = {'sounds': entry}
    with open(out('assets', 'aetherion', 'sounds.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(defs, f, indent=2)


# ----------------------------------------------------------------------------- write

def main():
    tex = ('assets', 'aetherion', 'textures', 'font', 'panel')
    for k, im in hd.plates().items():
        im.save(out(*tex, 'plate_%s.png' % k))
    icons = hd.icons()
    for k, im in icons.items():
        im.save(out(*tex, 'icon_%s.png' % k))
    hd.radar().save(out(*tex, 'radar.png'))
    for k, im in hd.markers().items():
        im.save(out(*tex, 'dot_%s.png' % k))

    prov = [spaces(),
            {'type': 'bitmap', 'file': 'aetherion:font/panel/plate_top.png', 'ascent': -23, 'height': 12, 'chars': ['\ue400']},
            {'type': 'bitmap', 'file': 'aetherion:font/panel/plate_mid.png', 'ascent': -23, 'height': 12, 'chars': ['\ue401']},
            {'type': 'bitmap', 'file': 'aetherion:font/panel/plate_bot.png', 'ascent': -23, 'height': 12, 'chars': ['\ue402']}]
    for i, k in enumerate(icons):
        prov.append({'type': 'bitmap', 'file': 'aetherion:font/panel/icon_%s.png' % k, 'ascent': -26, 'height': 7,
                     'chars': [chr(0xE410 + i)]})
    prov.append({'type': 'bitmap', 'file': 'aetherion:font/panel/radar.png', 'ascent': -24, 'height': RADAR_W,
                 'chars': ['\ue420']})
    for i, (k, h) in enumerate(DOT_SIZES.items()):
        prov.append({'type': 'bitmap', 'file': 'aetherion:font/panel/dot_%s.png' % k, 'ascent': -24, 'height': h,
                     'chars': [chr(0xE421 + i)]})
    with open(out('assets', 'aetherion', 'font', 'panel.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'providers': prov}, f, indent=1)

    vt = ('assets', 'aetherion', 'textures', 'font', 'vfx')
    vprov = []
    for i, k in enumerate(('slash', 'ring', 'rune', 'tele_edge', 'tele_fill')):
        vfx(k).save(out(*vt, k + '.png'))
        vprov.append({'type': 'bitmap', 'file': 'aetherion:font/vfx/%s.png' % k, 'ascent': 18, 'height': 32,
                      'chars': [chr(0xE500 + i)]})
    with open(out('assets', 'aetherion', 'font', 'vfx.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'providers': vprov}, f, indent=1)

    with open(out('assets', 'minecraft', 'shaders', 'core', 'rendertype_text.vsh'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(shader(palette()))

    if 'art' in sys.argv[1:]:
        print('panel/radar/vfx art and shader written (sounds skipped)')
        return
    sounds()
    print('panel/radar/vfx art, shader and %d sounds written' % len(SOUNDS))


if __name__ == '__main__':
    main()
