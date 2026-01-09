#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>
#moj_import <ocean_vanilla.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 TextureMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out vec4 vertexColor;
out vec3 lightColor;
out vec2 texCoord0;

out vec3 physics_localPosition;
out float physics_localWaviness;

void main() {
	physics_localWaviness = texelFetch(physics_waviness, ivec2(Position.xz) - physics_textureOffset, 0).r;
	vec3 finalPosition = vec3(Position.x, Position.y + physics_waveHeight(Position.xz, PHYSICS_ITERATIONS_OFFSET, physics_localWaviness, physics_gameTime), Position.z);
    physics_localPosition = finalPosition;
    gl_Position = ProjMat * ModelViewMat * vec4(finalPosition, 1.0);

    vertexDistance = fog_distance(ModelViewMat, finalPosition, FogShape);
    vec4 tmpColor = texelFetch(Sampler2, UV2 / 16, 0);
    vertexColor = tmpColor * Color;
    lightColor = tmpColor.rgb;
    
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}
