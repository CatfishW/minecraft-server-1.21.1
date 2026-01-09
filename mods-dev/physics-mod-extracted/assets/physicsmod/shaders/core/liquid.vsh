#version 150

in vec3 Position;
in vec3 Normal;
in vec4 Offset;
in vec4 OffsetNew;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float RenderPercent;
uniform vec3 LiquidCameraPos;

out float transparency;
out float linearDepth;
out vec3 worldNormal;

void main() {
	float scale = OffsetNew.w;
	vec4 currentWorldPos = ModelViewMat * vec4(Position.xyz * scale + mix(Offset.xyz - LiquidCameraPos, OffsetNew.xyz - LiquidCameraPos, RenderPercent), 1.0);
    gl_Position = ProjMat * currentWorldPos;
}
