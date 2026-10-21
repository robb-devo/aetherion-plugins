#version 150

// Vanilla position_tex plus the GUI y of each vertex, so position_tex.fsh can hide the HUD carrier's bar
// (AetherionItems/tools/gen_carrier.py).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float guiY;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    guiY = (ProjMat[3][3] == 1.0 && ProjMat[2][3] == 0.0) ? Position.y : -1000.0;
}
