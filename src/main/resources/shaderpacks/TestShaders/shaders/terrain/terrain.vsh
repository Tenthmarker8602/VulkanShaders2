#version 460

// Inlined light.glsl
const float MINECRAFT_LIGHT_POWER = (0.6);
const float MINECRAFT_AMBIENT_LIGHT = (0.4);

vec4 sample_lightmap2(sampler2D lightMap, uint uv) {
    const ivec2 lm = ivec2(bitfieldExtract(uv, 4, 4), bitfieldExtract(uv, 12, 4));
    return texelFetch(lightMap, lm, 0);
}

// Inlined fog.glsl
float fog_spherical_distance(vec3 pos) {
    return length(pos);
}

float fog_cylindrical_distance(vec3 pos) {
    float distXZ = length(pos.xz);
    float distY = abs(pos.y);
    return max(distXZ, distY);
}

layout (binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};

layout (push_constant) uniform pushConstant {
    vec3 ModelOffset;
};

layout (binding = 3) uniform sampler2D Sampler2;

layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord0;
layout (location = 2) out float sphericalVertexDistance;
layout (location = 3) out float cylindricalVertexDistance;

#define COMPRESSED_VERTEX

layout (location = 0) in ivec4 Position;
layout (location = 1) in uvec2 UV0;
layout (location = 2) in uint PackedColor;

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
const vec3 POSITION_OFFSET = vec3(4.0);

vec3 getVertexPosition() {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);
    return fma(Position.xyz, POSITION_INV, ModelOffset + baseOffset);
}

void main() {
    const vec3 pos = getVertexPosition();
    gl_Position = MVP * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    const vec4 Color = unpackUnorm4x8(PackedColor);
    vertexColor = Color * sample_lightmap2(Sampler2, Position.a);

    texCoord0 = UV0 * UV_INV;
}
