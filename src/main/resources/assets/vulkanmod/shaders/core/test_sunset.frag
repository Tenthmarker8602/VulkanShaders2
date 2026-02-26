#version 460

// Simple test fragment shader - sunset with gradient
layout(location = 0) in vec4 in_color;

layout(location = 0) out vec4 out_color;

void main() {
    // Create a sunset gradient effect - orange to red to dark
    vec2 coord = gl_FragCoord.xy;
    
    // Vertical gradient: more red at bottom, more orange at top
    float gradient = clamp(coord.y / 1080.0, 0.0, 1.0);
    
    vec3 topColor = vec3(1.0, 0.7, 0.0);  // Bright orange
    vec3 botColor = vec3(1.0, 0.2, 0.0);  // Dark red/orange
    
    vec3 color = mix(botColor, topColor, gradient);
    
    out_color = vec4(color, 1.0);
}
