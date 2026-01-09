uniform mat4 physics_invProjectionMatrix;
uniform mat4 physics_invViewMatrix;
uniform sampler2D physics_depth;

vec3 physics_normal;
vec4 physics_fragdepth;

vec3 physics_decodeDepth(vec2 texCoord, sampler2D depthMap, mat4 invProjectionMatrix) {
	float z = texture(depthMap, texCoord).x * 2.0 - 1.0;
	
    float x = texCoord.x * 2.0 - 1.0;
    float y = texCoord.y * 2.0 - 1.0;
    
    vec4 projectedPos = vec4(x, y, z, 1.0);
    
    vec4 position = invProjectionMatrix * projectedPos;  
    
    return position.xyz / position.w;  
}

vec3 physics_getNormalFromDepth() {
	vec2 texCoord = gl_FragCoord.xy / textureSize(physics_depth, 0);
    vec2 texelSize = 1.0 / textureSize(physics_depth, 0);
    vec3 eyePos = physics_decodeDepth(texCoord, physics_depth, physics_invProjectionMatrix);
    vec3 ddx1 = physics_decodeDepth(texCoord + vec2(texelSize.x, 0.0), physics_depth, physics_invProjectionMatrix) - eyePos;
    vec3 ddx2 = eyePos - physics_decodeDepth(texCoord - vec2(texelSize.x, 0.0), physics_depth, physics_invProjectionMatrix);
    
    if (abs(ddx1.z) > abs(ddx2.z)) {
    	ddx1 = ddx2;
    }
    
    vec3 ddy1 = physics_decodeDepth(texCoord + vec2(0.0, texelSize.y), physics_depth, physics_invProjectionMatrix) - eyePos;
    vec3 ddy2 = eyePos - physics_decodeDepth(texCoord - vec2(0.0, texelSize.y), physics_depth, physics_invProjectionMatrix);
    
    if (abs(ddy1.z) > abs(ddy2.z)) {
    	ddy1 = ddy2;
    }
    
    return normalize((physics_invViewMatrix * vec4(normalize(cross(ddx1, ddy1)), 0.0)).xyz);
}