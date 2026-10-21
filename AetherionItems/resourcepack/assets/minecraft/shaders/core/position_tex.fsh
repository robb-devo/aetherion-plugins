#version 150

// Vanilla position_tex, except: the first boss bar is the Aetherion HUD carrier (BLUE, empty). Its
// background sprite is marked with alpha 253 and sits at GUI y 12..17, so those texels are dropped there.
// A real BLUE bar further down the stack keeps its background.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in float guiY;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    if (guiY > 11.5 && guiY < 17.5 && abs(color.a * 255.0 - 253.0) < 0.5) {
        discard;
    }
    fragColor = color * ColorModulator;
}
