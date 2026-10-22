#version 150

#moj_import <fog.glsl>

// Vanilla rendertype_text plus the Aetherion HUD (AetherionItems/tools/gen_carrier.py).
// The HUD is the title of the first boss bar (text y = 3). Its glyphs are coloured (76, 4 * code, b):
//   code  0..39  status frame, top left; y += 2 * code; b = palette index
//   code 40..63  radar block, top right; state = (code - 40) / 8 pushes it under the vanilla effect icons
//                (top at 3 / 27 / 52 GUI px), part = (code - 40) % 8: 0 disc, 1 marker (y = b - 64),
//                2..7 a label row at y = 66 + 4 * (part - 2).
// The HUD is drawn at eff screen px per art px: the GUI scale, capped by the screen size, so a huge GUI
// scale does not blow it up. Their drop shadows (19, code, b / 4) are dropped. Only vertices at GUI y 8..100
// are looked at: the title's glyphs reach from y 10 (top) to y 91 (the tallest frame plate). Everything else is vanilla.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec2 ScreenSize;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

// aetherion-hud:begin  (newer_clients.py copies this block into the shaders of newer clients)
const int PAL_N = 30;
const vec3 PALETTE[PAL_N] = vec3[](
    vec3(1.0000, 1.0000, 1.0000),
    vec3(0.8784, 0.2510, 0.3059),
    vec3(0.4275, 0.7333, 0.2275),
    vec3(0.4157, 0.3922, 0.4549),
    vec3(0.5255, 0.8039, 0.9333),
    vec3(0.9490, 0.8510, 0.8118),
    vec3(0.7098, 0.6627, 0.7373),
    vec3(1.0000, 0.7882, 0.8000),
    vec3(0.9490, 0.7882, 0.2980),
    vec3(0.9490, 0.7569, 0.3059),
    vec3(0.8784, 0.4392, 0.2275),
    vec3(0.7490, 0.8510, 0.9255),
    vec3(1.0000, 0.8471, 0.4196),
    vec3(1.0000, 0.3529, 0.2902),
    vec3(0.5412, 0.1176, 0.0863),
    vec3(0.6902, 0.5412, 0.2275),
    vec3(1.0000, 0.9529, 0.8549),
    vec3(1.0000, 0.7529, 0.7294),
    vec3(0.6549, 0.4824, 0.9176),
    vec3(1.0000, 0.8784, 0.5412),
    vec3(0.9608, 0.7725, 0.2588),
    vec3(0.6196, 0.9020, 1.0000),
    vec3(0.5608, 0.8902, 0.8235),
    vec3(0.8118, 0.7804, 0.9020),
    vec3(0.5608, 0.5255, 0.6275),
    vec3(0.4980, 0.8275, 1.0000),
    vec3(0.8510, 0.6431, 0.2549),
    vec3(0.3843, 0.8510, 0.4667),
    vec3(0.3098, 0.6588, 0.3843),
    vec3(1.0000, 0.9451, 0.8549)
);

const float MARGIN = 3.0;
const float RADAR = 64.0;

// Moves and recolours one vertex of a HUD carrier glyph (pos and col start as Position and Color).
// Returns true for the drop shadow of a carrier glyph: that vertex is not drawn.
bool aetherion_hud(inout vec3 pos, inout vec4 col) {
    if (ProjMat[3][3] != 1.0 || ProjMat[2][3] != 0.0 || pos.y <= 8.0 || pos.y >= 100.0) {
        return false;
    }
    ivec3 c = ivec3(round(col.rgb * 255.0));
    if (c.r == 19 && c.g < 64) {
        return true;
    }
    if (c.r != 76 || (c.g & 3) != 0) {
        return false;
    }
    int code = c.g >> 2;
    float guiW = 2.0 / ProjMat[0][0];
    // ScreenSize is 0 (or junk) where a client does not hand it to this shader: then draw at GUI scale.
    bool sized = ScreenSize.x > 0.5 && ScreenSize.y > 0.5 && ScreenSize.x < 32768.0 && ScreenSize.y < 32768.0;
    float gui = sized ? max(1.0, floor(ScreenSize.x / guiW + 0.5)) : 1.0;
    sized = sized && gui <= 16.0;
    gui = sized ? gui : 1.0;
    float eff = sized ? clamp(floor(min(ScreenSize.y / 320.0, ScreenSize.x / 520.0)), 1.0, gui) : gui;
    float k = eff / gui;
    float startX = floor(ceil(guiW - 0.01) * 0.5);
    vec2 local = vec2(pos.x - startX, pos.y - 10.0);
    col.rgb = PALETTE[min(c.b, PAL_N - 1)];
    if (code < 40) {
        local.y += float(code) * 2.0;
        pos.xy = (vec2(MARGIN) + local) * k;
    } else {
        int part = (code - 40) - ((code - 40) / 8) * 8;
        int state = (code - 40) / 8;
        float top = state == 0 ? 3.0 : (state == 1 ? 27.0 : 52.0);
        if (part == 1) {
            local.y += float(c.b) - 64.0;
            col.rgb = vec3(1.0);
        } else if (part >= 2) {
            local.y += 66.0 + 4.0 * float(part - 2);
        }
        float left = floor(guiW * gui / eff + 0.001) - MARGIN - RADAR;
        pos.x = (left + local.x) * k;
        pos.y = top + local.y * k;
    }
    return false;
}
// aetherion-hud:end

void main() {
    vec3 pos = Position;
    vec4 col = Color;
    bool drop = aetherion_hud(pos, col);
    gl_Position = drop ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = col * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
