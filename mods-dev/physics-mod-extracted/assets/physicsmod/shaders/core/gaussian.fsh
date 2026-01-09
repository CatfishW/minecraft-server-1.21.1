#version 150

in vec2 pass_textureCoords;

out vec4 out_color;

uniform float kernel[#SIZE];

uniform vec2 offset;
uniform vec2 texelSize;
uniform sampler2D imageMap;

void main(void) {
	float colorCenter = texture(imageMap, pass_textureCoords).r;
	float color = colorCenter * kernel[0];
	float kernelAmount = kernel[0];
	
	if (colorCenter != 0.0) {
		for (int i = 1; i < #SIZE; i++) {
			vec2 texelOffset = offset * texelSize * i;
			float offsetColor = texture(imageMap, pass_textureCoords - texelOffset).r;
			
			if (offsetColor == 0.0 || abs(colorCenter - offsetColor) > 1.4) break;
			
			color += offsetColor * kernel[i];
			kernelAmount += kernel[i];
		}
		
		for (int i = 1; i < #SIZE; i++) {
			vec2 texelOffset = offset * texelSize * i;
			float offsetColor = texture(imageMap, pass_textureCoords + texelOffset).r;
			
			if (offsetColor == 0.0 || abs(colorCenter - offsetColor) > 1.4) break;
			
			color += offsetColor * kernel[i];
			kernelAmount += kernel[i];
		}
	}
	
	out_color = vec4(color / kernelAmount, 1.0, 1.0, 1.0);
}