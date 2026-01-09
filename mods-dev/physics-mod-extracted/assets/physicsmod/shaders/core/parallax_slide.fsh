#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
// this is actually the time, not using GameTime uniform because it is getting adjusted to day time in minecraft
uniform float FogStart;
uniform vec2 ScreenSize;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
	float curveFunction = pow(cos(pow(sin(FogStart * 0.7), 3.0)), 10.0);
    float perc = (curveFunction - 0.0021202) / (1.0 - 0.0021202);
    
    if (gl_FragCoord.x < ScreenSize.x * perc) {
    	discard;
    }
    
    vec4 color = texture(Sampler0, texCoord0);
    fragColor = color * ColorModulator;
}
