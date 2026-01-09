#version 150

in float transparency;

out vec4 fragColor;

void main() {
    fragColor = vec4(gl_FragCoord.z, 1.0, 1.0, 1.0);
}