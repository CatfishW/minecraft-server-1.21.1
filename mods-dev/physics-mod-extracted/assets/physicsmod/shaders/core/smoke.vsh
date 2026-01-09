#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in vec4 Offset;
in ivec4 ObjectID;
in vec4 OffsetNew;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float RenderPercent;
uniform vec3 SmokeCameraPos;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord1;
out vec4 normal;
out vec3 worldPos;
out float transparencyModulator;
flat out ivec4 ObjectIDPass;
flat out float ParticleDensityPass;

float remap(float value, float oldMin, float oldMax, float newMin, float newMax);
mat4 rotationMatrix(vec3 axis, float angle);
float fog_distance(mat4 modelViewMat, vec3 pos, int shape);

void main() {
	// scale it from 0.4 to 0.7
	float objIDx = ObjectID.x / 255.0;
	float objIDy = ObjectID.y / 255.0;
	float objScale = remap(ObjectID.w / 255.0, 0.0, 1.0, 0.25, 5.0);
	float randomScale = ((objIDx + objIDy) * 0.5 * 0.3 + 0.4) * objScale;
	mat4 randomRotation = rotationMatrix(vec3(0.2 + objIDy, 0.6, 0.4 + objIDx), (objIDx + objIDy) * 2.0);
	vec4 currentWorldPos = ModelViewMat * vec4((randomRotation * vec4(Position * randomScale, 1.0)).xyz + mix(Offset.xyz, OffsetNew.xyz, RenderPercent) - SmokeCameraPos, 1.0);
    gl_Position = ProjMat * currentWorldPos;
    worldPos = currentWorldPos.xyz;
    
    #SHADOW_TRANSFORM

	ivec2 remappedLightCoords = ivec2(ObjectID.z & 0xF, (ObjectID.z >> 4) & 0xF); 
    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    vertexColor = texelFetch(Sampler2, remappedLightCoords, 0);
    texCoord0 = UV0;
    texCoord1 = UV1;
    normal = normalize(ProjMat * ModelViewMat * randomRotation * vec4(Normal, 0.0));
    ObjectIDPass = ObjectID;
	ParticleDensityPass = OffsetNew.w;
    transparencyModulator = Offset.w;
}

float remap(float value, float oldMin, float oldMax, float newMin, float newMax) {
	return newMin + (value - oldMin) / (oldMax - oldMin) * (newMax - newMin);
}

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}

float fog_distance(mat4 modelViewMat, vec3 pos, int shape) {
    if (shape == 0) {
        return length((modelViewMat * vec4(pos, 1.0)).xyz);
    } else {
        float distXZ = length((modelViewMat * vec4(pos.x, 0.0, pos.z, 1.0)).xyz);
        float distY = length((modelViewMat * vec4(0.0, pos.y, 0.0, 1.0)).xyz);
        return max(distXZ, distY);
    }
}
