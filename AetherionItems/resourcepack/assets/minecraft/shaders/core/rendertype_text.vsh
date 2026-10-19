#version 150

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

const int PAL_N = 30;
const vec3 PALETTE[PAL_N] = vec3[](
    vec3(1.0000, 1.0000, 1.0000),
    vec3(0.8510, 0.2275, 0.2510),
    vec3(0.4275, 0.7333, 0.2275),
    vec3(0.3529, 0.3294, 0.3843),
    vec3(0.5255, 0.8039, 0.9333),
    vec3(0.9490, 0.8510, 0.8118),
    vec3(0.7098, 0.6627, 0.7373),
    vec3(1.0000, 0.7882, 0.8000),
    vec3(0.9490, 0.7882, 0.2980),
    vec3(0.8118, 0.9608, 0.9373),
    vec3(0.8510, 0.6431, 0.2549),
    vec3(1.0000, 0.3529, 0.2902),
    vec3(0.3843, 0.8510, 0.4667),
    vec3(0.3098, 0.6588, 0.3843),
    vec3(1.0000, 0.9451, 0.8549),
    vec3(1.0000, 0.7608, 0.7294),
    vec3(0.9490, 0.7569, 0.3059),
    vec3(0.8784, 0.4392, 0.2275),
    vec3(0.7490, 0.8510, 0.9255),
    vec3(1.0000, 0.8471, 0.4196),
    vec3(0.5412, 0.1176, 0.0863),
    vec3(1.0000, 0.9529, 0.8549),
    vec3(1.0000, 0.7529, 0.7294),
    vec3(0.6549, 0.4824, 0.9176),
    vec3(1.0000, 0.8784, 0.5412),
    vec3(0.9608, 0.7725, 0.2588),
    vec3(0.6196, 0.9020, 1.0000),
    vec3(0.8118, 0.7804, 0.8510),
    vec3(0.5608, 0.5255, 0.6275),
    vec3(0.4980, 0.8275, 1.0000)
);

void main() {
    vec3 pos = Position;
    vec4 col = Color;
    bool drop = false;
    if (ProjMat[3][3] == 1.0 && ProjMat[2][3] == 0.0) {
        float guiW = 2.0 / ProjMat[0][0];
        float guiH = -2.0 / ProjMat[1][1];
        float fromBottom = guiH - Position.y;
        if (fromBottom > -96.0 && fromBottom < 160.0) {
            ivec3 c = ivec3(round(Color.rgb * 255.0));
            if (c.r == 76 && c.g >= 32 && c.g <= 104 && (c.g & 3) == 0) {
                int code = (c.g - 32) >> 2;
                float y = Position.y - guiH + 41.0;
                if (code < 16) {
                    pos.x = Position.x - guiW * 0.5 + 2.0;
                    pos.y = y + 3.0 + float(code) * 12.0;
                    col.rgb = PALETTE[min(c.b, PAL_N - 1)];
                } else {
                    pos.x = Position.x - guiW * 0.5 + guiW - 67.0;
                    pos.y = y + 54.0;
                    if (code == 16) {
                        col.rgb = PALETTE[min(c.b, PAL_N - 1)];
                    } else {
                        pos.y += float(c.b - 128);
                        col.rgb = vec3(1.0);
                    }
                }
            } else if (c.r == 19 && c.g >= 8 && c.g <= 26) {
                drop = true;
            }
        }
    }
    gl_Position = drop ? vec4(2.0, 2.0, 2.0, 1.0) : ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = col * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
}
