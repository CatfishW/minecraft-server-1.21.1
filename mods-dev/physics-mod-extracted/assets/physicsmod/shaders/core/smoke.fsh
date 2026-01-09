#version 150

uniform sampler2D Sampler0;
uniform sampler2D DepthTexture;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 SmokeColor;
uniform vec3 SmokeDenseColor;
uniform float SmokeDensity;
uniform mat4 InvProjMat;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec4 normal;
in vec3 worldPos;
flat in ivec4 ObjectIDPass;
flat in float ParticleDensityPass;
in float transparencyModulator;

out vec4 fragColor;

float calcDitherDispersed(vec2 offset);
vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor);
vec3 decodeDepth(vec2 texCoord, float z, mat4 invProjectionMatrix);
vec3 decodeDepth(vec2 texCoord, sampler2D depthMap, mat4 invProjectionMatrix);
float remapClamp(float value, float oldMin, float oldMax, float newMin, float newMax);

void main() {
	vec4 texColor = texture(Sampler0, texCoord0);
    vec4 color = vertexColor * vec4(ColorModulator.rgb, 1.0);
	float addTransparency = 1.0 - pow((normalize(normal).z * 0.5 + 0.5) * 2.5, 2.0);
	ivec2 depthSize = textureSize(DepthTexture, 0);
	vec3 oldWorldPos = decodeDepth(gl_FragCoord.xy / depthSize, DepthTexture, InvProjMat);
	float depthOld = length(oldWorldPos);
	float depthNew = length(worldPos);
	float depthBasedTransparency = clamp((depthOld - depthNew) * (depthNew - 0.5), 0.0, 1.0);
    float densityBasedTransparency = remapClamp(ParticleDensityPass, 1.0, 20.0, 0.8, 1.0);
    
    if (calcDitherDispersed(ObjectIDPass.xy / 255.0 * 8.0) >= texColor.r * transparencyModulator * ColorModulator.a * SmokeDensity * addTransparency * depthBasedTransparency * densityBasedTransparency) {
    	discard;
    }
    
    float densityBasedColor = remapClamp(ParticleDensityPass, 20.0, 1.0, 0.0, 1.0);
    float smokeColorOffset = (1.0 - texColor.r) * 0.3 + 0.4;
	vec3 finalSmokeColor = mix(SmokeDenseColor, SmokeColor, densityBasedColor);
    
    fragColor = linear_fog(color * vec4(finalSmokeColor * smokeColorOffset, 1.0), vertexDistance, FogStart, FogEnd, FogColor);
}

const int ditherDispersed[64] = int[]( 0, 48, 12, 60, 3, 51, 15, 61, 32, 16, 44, 28, 35, 19, 47, 31, 8, 56, 4, 52, 11, 59, 7,
				55, 40, 24, 36, 20, 43, 27, 39, 23, 2, 50, 14, 62, 1, 49, 13, 61, 34, 18, 46, 30, 33, 17, 45, 29, 10,
				58, 6, 54, 9, 57, 5, 53, 42, 26, 38, 22, 41, 25, 37, 21 );

float calcDitherDispersed(vec2 offset) {
	int x = int(mod(gl_FragCoord.x + offset.x, 8.0));
	int y = int(mod(gl_FragCoord.y + offset.y, 8.0));
	return ditherDispersed[x + y * 8] / 64.0;
}

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

vec3 decodeDepth(vec2 texCoord, float z, mat4 invProjectionMatrix) {
	z = z * 2.0 - 1.0;
	
    float x = texCoord.x * 2.0 - 1.0;
    float y = texCoord.y * 2.0 - 1.0;
    
    vec4 projectedPos = vec4(x, y, z, 1.0);
    
    vec4 position = invProjectionMatrix * projectedPos;  
    
    return position.xyz / position.w;  
}

vec3 decodeDepth(vec2 texCoord, sampler2D depthMap, mat4 invProjectionMatrix) {
    return decodeDepth(texCoord, texture(depthMap, texCoord).x, invProjectionMatrix);
}

float remapClamp(float value, float oldMin, float oldMax, float newMin, float newMax) {
	return clamp(newMin + (value - oldMin) / (oldMax - oldMin) * (newMax - newMin), newMin, newMax);
}