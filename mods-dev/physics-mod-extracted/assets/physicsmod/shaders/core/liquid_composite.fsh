#version 150

#moj_import <fog.glsl>
#moj_import <liquids.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec2 pass_textureCoords;

out vec4 fragColor;

void main() {
	float physics_fragZ = texture(physics_depth, gl_FragCoord.xy / textureSize(physics_depth, 0)).x;

	physics_normal = physics_getNormalFromDepth();
	if (physics_fragZ == 0.0) discard;

	vec3 eyePos = physics_decodeDepth(gl_FragCoord.xy / textureSize(physics_depth, 0), physics_depth, physics_invProjectionMatrix);
	float vertexDistance = length(eyePos);

	// VANILLA STYLE
	vec3 oceanColor = vec3(mix(abs(physics_normal.x) * 0.5 + 0.5, 0.0, pow(abs(physics_normal.y), 4.0)));
    vec3 color = vec3(0.24705884, 0.46274513, 0.8941177);
    fragColor = linear_fog(vec4(clamp(color * 0.7 + oceanColor * 0.3, vec3(0.0), vec3(1.0)), 0.8375), vertexDistance, FogStart, FogEnd, FogColor);
    //fragColor = clamp(vec4(color + oceanColor * 0.3, 0.8375), vec4(0.0), vec4(1.0));
    //fragColor = vec4(eyePos, 1.0);
}
