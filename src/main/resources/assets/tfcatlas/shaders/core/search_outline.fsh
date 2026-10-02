#version 150
in vec4 vertexColor;
uniform vec3 TintColor;
uniform float ShadowPass;
out vec4 fragColor;
void main() {
    fragColor = mix(vec4(vertexColor.rgb * TintColor, 1.0), vec4(24.0, 32.0, 35.0, 208.0) / 255.0, ShadowPass);
}
