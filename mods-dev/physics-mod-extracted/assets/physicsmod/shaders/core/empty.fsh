#version 150

in vec2 pass_textureCoords;

out vec4 out_color;

uniform mat4 invProjectionMatrix;
uniform mat4 invViewMatrix;
uniform sampler2D diffuseMap;

vec3 decodeDepth(vec2 texCoord, sampler2D depthMap, mat4 invProjectionMatrix) {
	float z = texture(depthMap, texCoord).x * 2.0 - 1.0;
	
    float x = texCoord.x * 2.0 - 1.0;
    float y = texCoord.y * 2.0 - 1.0;
    
    vec4 projectedPos = vec4(x, y, z, 1.0);
    
    vec4 position = invProjectionMatrix * projectedPos;  
    
    return position.xyz / position.w;  
}

vec3 calculatePhysicsNormal(vec2 texCoord, sampler2D depthMap) {
    vec2 texelSize = 1.0 / textureSize(depthMap, 0);
    vec3 eyePos = decodeDepth(pass_textureCoords, depthMap, invProjectionMatrix);
    vec3 ddx1 = decodeDepth(pass_textureCoords + vec2(texelSize.x, 0.0), depthMap, invProjectionMatrix) - eyePos;
    vec3 ddx2 = eyePos - decodeDepth(pass_textureCoords - vec2(texelSize.x, 0.0), depthMap, invProjectionMatrix);
    
    if (abs(ddx1.z) > abs(ddx2.z)) {
    	ddx1 = ddx2;
    }
    
    vec3 ddy1 = decodeDepth(pass_textureCoords + vec2(0.0, texelSize.y), diffuseMap, invProjectionMatrix) - eyePos;
    vec3 ddy2 = eyePos - decodeDepth(pass_textureCoords - vec2(0.0, texelSize.y), diffuseMap, invProjectionMatrix);
    
    if (abs(ddy1.z) > abs(ddy2.z)) {
    	ddy1 = ddy2;
    }
    
    return normalize((invViewMatrix * vec4(normalize(cross(ddx1, ddy1)), 0.0)).xyz);
}

void main(void) {
	vec4 result = texture(diffuseMap, pass_textureCoords.st).rgba;
	
	if (result.r <= 0.01) discard;
	
	out_color = vec4(calculatePhysicsNormal(pass_textureCoords, diffuseMap), 1.0);
}