#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float StrokeWidth;
out vec4 vertexColor;
void main() {
    vec4 position = ModelViewMat * vec4(Position, 1.0);
    position.xy += UV0 * StrokeWidth;
    gl_Position = ProjMat * position;
    vertexColor = Color;
}
