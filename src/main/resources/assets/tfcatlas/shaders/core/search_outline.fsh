#version 150
in vec4 vertexColor;
uniform vec3 TintColor;
out vec4 fragColor;
void main() {
    fragColor = vec4(vertexColor.rgb * TintColor, 1.0);
}
