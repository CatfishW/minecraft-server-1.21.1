#version 150

#moj_import <fog.glsl>
#moj_import <ocean_vanilla.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec3 lightColor;
in vec2 texCoord0;

in vec3 physics_localPosition;
in float physics_localWaviness;

out vec4 fragColor;

void main() {
    WavePixelData wave = physics_wavePixel(physics_localPosition.xz, physics_localWaviness, physics_iterationsNormal, physics_gameTime);

	// VANILLA STYLE
	vec4 oceanColor = vec4(mix(wave.normal.x * 0.5 + 0.5, 0.0, pow(wave.normal.y, 4.0)));
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator * vertexColor;
    fragColor = linear_fog(clamp(color + (wave.foam + oceanColor * 0.3) * vec4(lightColor, 1.0), vec4(0.0), vec4(1.0)), vertexDistance, FogStart, FogEnd, FogColor);
}
