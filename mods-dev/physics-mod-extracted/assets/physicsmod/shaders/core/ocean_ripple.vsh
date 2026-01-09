#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Offset;
in vec4 OffsetNew;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float RenderPercent;
uniform vec3 RippleCameraPos;

out vec2 texCoord0;
out float transparency;
out float particleState;

void main() {
	float scale = OffsetNew.w;
	//vec4 currentWorldPos = ModelViewMat * vec4(Position.xyz * scale + mix(Offset.xyz - RippleCameraPos, OffsetNew.xyz - RippleCameraPos, RenderPercent), 1.0);
	vec4 currentWorldPos = ModelViewMat * vec4(Position.xyz * scale + mix(Offset.xyz - RippleCameraPos, vec3(OffsetNew.x, Offset.y, OffsetNew.z) - RippleCameraPos, RenderPercent), 1.0);
    gl_Position = ProjMat * currentWorldPos;
    
    particleState = OffsetNew.y;
    texCoord0 = UV0;
    transparency = Offset.w;
}
