#version 460

// Simple test fragment shader with striped pattern
layout(location = 0) in vec4 in_color;

layout(location = 0) out vec4 out_color;

void main() {
    // Create a checkerboard pattern to make it visually obvious
    // Fragment coordinates in screen space
    vec2 coord = gl_FragCoord.xy;
    float pattern = mod(floor(coord.x / 16.0) + floor(coord.y / 16.0), 2.0);
    
    // Bright green and cyan checkerboard
    vec3 color = mix(vec3(0.0, 1.0, 0.0),  // Green
                     vec3(0.0, 1.0, 1.0),  // Cyan
                     pattern);
    
    out_color = vec4(color, 1.0);
}
