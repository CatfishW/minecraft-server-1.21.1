#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in float particleState;
in float transparency;

out vec4 fragColor;

void main() {
	if (particleState > 0.05) {
		// rain particle
		float offset = length(texCoord0 - vec2(0.5)) * 120.0 - (particleState - 0.1) * 120.0 + 2.0;
    	float wave = sin(offset);
    	if (offset > 3.141 || offset < 0.0) wave = 0.0;
	    fragColor = vec4(wave * transparency);
	} else {
		vec4 puddleColor = vec4(texture(Sampler0, texCoord0).rgba * transparency);
	    fragColor = puddleColor;
    }
}