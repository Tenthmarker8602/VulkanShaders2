#version 460

// Simple test vertex shader - sunset edition
layout(location = 0) in vec3 in_position;
layout(location = 1) in vec4 in_color;

layout(location = 0) out vec4 out_color;

layout(binding = 0) uniform Camera {
    mat4 projection;
    mat4 view;
} camera;

void main() {
    gl_Position = camera.projection * camera.view * vec4(in_position, 1.0);
    out_color = vec4(1.0, 0.5, 0.0, 1.0); // Orange
}
