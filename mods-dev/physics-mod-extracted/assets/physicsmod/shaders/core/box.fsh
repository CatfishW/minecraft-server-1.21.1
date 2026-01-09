#version 150

#define BLUR 4.0

in vec2 pass_textureCoords;

out vec4 out_color;

uniform float near;
uniform float far;
uniform vec2 offset;
uniform vec2 texelSize;
uniform sampler2D imageMap;

float linearDepth(float depth, float nearValue, float farValue);
float remap(float value, float oldMin, float oldMax, float newMin, float newMax);

void main(void) {
	float center = texture(imageMap, pass_textureCoords).r;
	float linearCenter = linearDepth(center, near, far);
	float threshold = remap(linearCenter, 1.0, 10.0, 0.4, 2.0);
	
	float average = 0.0;
    float amount = 0.0;
    
    if (center != 0.0) {
	    for (float y = -BLUR; y <= BLUR; y += 1.0) {
	        for (float x = -BLUR; x <= BLUR; x += 1.0) {
	            float offsetDepth = texture(imageMap, pass_textureCoords + vec2(x, y) * texelSize).r;
	            
	            if (offsetDepth != 0.0) {
					float linearOffset = linearDepth(offsetDepth, near, far);
					
					if (abs(linearCenter - linearOffset) < threshold) {
			            average += offsetDepth;
			            amount += 1.0;
		            }
		        }
	        }
	    }
	
		out_color = vec4(average / amount, 1.0, 1.0, 1.0);
    } else {
		out_color = vec4(0.0, 1.0, 1.0, 1.0);
    }
}

float linearDepth(float depth, float nearValue, float farValue) {
	return 2.0 * nearValue * farValue / (farValue + nearValue - depth * (farValue - nearValue));
}

float remap(float value, float oldMin, float oldMax, float newMin, float newMax) {
	return clamp(newMin + (value - oldMin) / (oldMax - oldMin) * (newMax - newMin), newMin, newMax);
}