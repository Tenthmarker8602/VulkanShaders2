package net.vulkanmod.render.shader.bsl;

/**
 * Generates a custom Vulkan GLSL 450 vertex shader for BSL terrain rendering.
 * This replaces BSL's vertex shader because VulkanMod's compressed terrain
 * vertex format (ivec4 Position, uvec2 UV0, uint PackedColor) is incompatible
 * with BSL's expected OpenGL attributes (gl_Vertex, gl_Normal, mc_Entity, etc.).
 *
 * The vertex shader computes all varyings that BSL's fragment shader expects:
 * - sunVec, upVec, eastVec (directional vectors in view space)
 * - normal (stub: reconstructed per-face in fragment via dFdx/dFdy)
 * - texCoord, lmCoord, color (decoded from VulkanMod format)
 * - mat, recolor (material ID — stubbed to 0, no mc_Entity available)
 */
public class BSLVertexShaderGenerator {

    /**
     * Generate the BSL-compatible terrain vertex shader.
     * UBO layout must match the fragment shader's BSL_VertexUBO.
     */
    public static String generate() {
        return VERTEX_SHADER_SOURCE;
    }

    /**
     * Generate the BSL-compatible water vertex shader.
     * Same as terrain but with additional varyings for water effects.
     */
    public static String generateWater() {
        return WATER_VERTEX_SHADER_SOURCE;
    }

    private static final String VERTEX_SHADER_SOURCE = """
            #version 450

            // ====================================================================
            // BSL Compatibility - Terrain Vertex Shader
            // ====================================================================
            // Adapted for VulkanMod's compressed terrain vertex format.
            // Computes BSL-compatible varyings for the transpiled fragment shader.
            // ====================================================================

            // ---- VulkanMod Compressed Terrain Inputs ----
            layout(location = 0) in ivec4 Position;
            layout(location = 1) in uvec2 UV0;
            layout(location = 2) in uint PackedColor;

            // ---- UBOs ----
            layout(binding = 0) uniform BSL_VertexUBO {
                mat4 MVP;
                mat4 gbufferModelView;
                mat4 gbufferModelViewInverse;
                float timeAngle;
                float sunPathRotation; // degrees
                float frameTimeCounter;
                float _vpad0;
                vec3 cameraPosition;
                float _vpad1;
            };

            // ---- Push Constants ----
            layout(push_constant) uniform PushConstants {
                vec3 ModelOffset;
            };

            // ---- Samplers ----
            layout(binding = 3) uniform sampler2D Sampler2; // Lightmap

            // ---- Outputs (must match BSL fragment shader inputs) ----
            layout(location = 0) out float mat;
            layout(location = 1) out float recolor;
            layout(location = 2) out vec2 texCoord;
            layout(location = 3) out vec2 lmCoord;
            layout(location = 4) out vec3 normal;
            layout(location = 5) out vec3 sunVec;
            layout(location = 6) out vec3 upVec;
            layout(location = 7) out vec3 eastVec;
            layout(location = 8) out vec4 color;
            layout(location = 9) out vec3 worldPos_v;
            layout(location = 10) out vec3 viewPos_v;

            // ---- Constants ----
            const vec3 POSITION_INV = vec3(1.0 / 2048.0);
            const float UV_INV = 1.0 / 32768.0;
            const float PI = 3.14159265358979;

            // ---- Helper: decode compressed vertex position ----
            vec3 getVertexPosition() {
                vec3 baseOffset = vec3(
                    float(bitfieldExtract(gl_InstanceIndex, 0, 8)),
                    float(bitfieldExtract(gl_InstanceIndex, 16, 8)),
                    float(bitfieldExtract(gl_InstanceIndex, 8, 8))
                );
                return fma(vec3(Position.xyz), POSITION_INV, ModelOffset + baseOffset);
            }

            // ---- Helper: sample lightmap from encoded UV ----
            vec4 sampleLightmap(uint uv) {
                ivec2 lm = ivec2(bitfieldExtract(uv, 4, 4), bitfieldExtract(uv, 12, 4));
                return texelFetch(Sampler2, lm, 0);
            }

            void main() {
                // ---- Decode vertex data ----
                vec3 pos = getVertexPosition();
                vec4 Color = unpackUnorm4x8(PackedColor);

                // ---- Clip-space position ----
                gl_Position = MVP * vec4(pos, 1.0);

                // ---- Texture coordinates ----
                texCoord = vec2(UV0) * UV_INV;

                // ---- Lightmap coordinates ----
                // VulkanMod encodes lightmap in Position.a
                // Extract block light (bits 4-7) and sky light (bits 12-15) → normalize to 0-1
                // IMPORTANT: use uint overload of bitfieldExtract to avoid sign extension!
                // The int overload sign-extends 4-bit values, so 8-15 become -8..-1 → broken.
                uint lmData = uint(Position.a);
                float blockLight = float(bitfieldExtract(lmData, 4, 4)) / 15.0;
                float skyLight = float(bitfieldExtract(lmData, 12, 4)) / 15.0;
                lmCoord = vec2(blockLight, skyLight);

                // ---- Vertex color ----
                color = Color;
                if (color.a < 0.1) color.a = 1.0;

                // ---- Material ID (no mc_Entity available → all solid) ----
                mat = 0.0;
                recolor = 0.0;

                // ---- Normal (stub — reconstructed per-face in fragment) ----
                // We pass up-facing normal as default; fragment shader will use
                // dFdx/dFdy on worldPos for per-face normal if needed.
                normal = normalize(mat3(gbufferModelView) * vec3(0.0, 1.0, 0.0));

                // ---- Sun direction (BSL formula) ----
                float sunPathRad = sunPathRotation * 0.01745329251994;
                vec2 sunRotData = vec2(cos(sunPathRad), -sin(sunPathRad));

                float ang = fract(timeAngle - 0.25);
                ang = (ang + (cos(ang * PI) * -0.5 + 0.5 - ang) / 3.0) * 2.0 * PI;
                sunVec = normalize(
                    (gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotData) * 2000.0, 1.0)).xyz
                );

                // ---- Up and East vectors (from model-view matrix columns) ----
                upVec = normalize(gbufferModelView[1].xyz);
                eastVec = normalize(gbufferModelView[0].xyz);

                // ---- World/view positions for fragment reconstruction ----
                worldPos_v = pos;  // Camera-relative world position
                viewPos_v = (gbufferModelView * vec4(pos, 1.0)).xyz;
            }
            """;

    private static final String WATER_VERTEX_SHADER_SOURCE = """
            #version 450

            // ====================================================================
            // BSL Compatibility - Water Vertex Shader
            // Same as terrain but with additional varyings for water effects.
            // ====================================================================

            layout(location = 0) in ivec4 Position;
            layout(location = 1) in uvec2 UV0;
            layout(location = 2) in uint PackedColor;

            layout(binding = 0) uniform BSL_VertexUBO {
                mat4 MVP;
                mat4 gbufferModelView;
                mat4 gbufferModelViewInverse;
                float timeAngle;
                float sunPathRotation;
                float frameTimeCounter;
                float _vpad0;
                vec3 cameraPosition;
                float _vpad1;
            };

            layout(push_constant) uniform PushConstants {
                vec3 ModelOffset;
            };

            layout(binding = 3) uniform sampler2D Sampler2;

            // ---- Terrain outputs ----
            layout(location = 0) out float mat;
            layout(location = 1) out float recolor;
            layout(location = 2) out vec2 texCoord;
            layout(location = 3) out vec2 lmCoord;
            layout(location = 4) out vec3 normal;
            layout(location = 5) out vec3 sunVec;
            layout(location = 6) out vec3 upVec;
            layout(location = 7) out vec3 eastVec;
            layout(location = 8) out vec4 color;
            layout(location = 9) out vec3 worldPos_v;
            layout(location = 10) out vec3 viewPos_v;

            // ---- Water-specific outputs ----
            layout(location = 11) out float dist;
            layout(location = 12) out vec3 binormal;
            layout(location = 13) out vec3 tangent;
            layout(location = 14) out vec3 viewVector;
            layout(location = 15) out vec4 vTexCoord;

            const vec3 POSITION_INV = vec3(1.0 / 2048.0);
            const float UV_INV = 1.0 / 32768.0;
            const float PI = 3.14159265358979;

            vec3 getVertexPosition() {
                vec3 baseOffset = vec3(
                    float(bitfieldExtract(gl_InstanceIndex, 0, 8)),
                    float(bitfieldExtract(gl_InstanceIndex, 16, 8)),
                    float(bitfieldExtract(gl_InstanceIndex, 8, 8))
                );
                return fma(vec3(Position.xyz), POSITION_INV, ModelOffset + baseOffset);
            }

            void main() {
                vec3 pos = getVertexPosition();
                vec4 Color = unpackUnorm4x8(PackedColor);

                gl_Position = MVP * vec4(pos, 1.0);

                texCoord = vec2(UV0) * UV_INV;

                uint lmData = uint(Position.a);
                float blockLight = float(bitfieldExtract(lmData, 4, 4)) / 15.0;
                float skyLight = float(bitfieldExtract(lmData, 12, 4)) / 15.0;
                lmCoord = vec2(blockLight, skyLight);

                color = Color;
                if (color.a < 0.1) color.a = 1.0;

                // Water material ID: BSL expects 1.0 for water blocks
                // (2.0 = glass, 3.0 = translucent, 4.0 = portal)
                mat = 1.0;
                recolor = 0.0;

                // Normal (stub up vector)
                normal = normalize(mat3(gbufferModelView) * vec3(0.0, 1.0, 0.0));

                // Sun direction
                float sunPathRad = sunPathRotation * 0.01745329251994;
                vec2 sunRotData = vec2(cos(sunPathRad), -sin(sunPathRad));
                float ang = fract(timeAngle - 0.25);
                ang = (ang + (cos(ang * PI) * -0.5 + 0.5 - ang) / 3.0) * 2.0 * PI;
                sunVec = normalize(
                    (gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotData) * 2000.0, 1.0)).xyz
                );

                upVec = normalize(gbufferModelView[1].xyz);
                eastVec = normalize(gbufferModelView[0].xyz);

                worldPos_v = pos;
                viewPos_v = (gbufferModelView * vec4(pos, 1.0)).xyz;

                // Water-specific
                dist = length(viewPos_v);
                tangent = normalize(cross(normal, vec3(0.0, 0.0, 1.0)));
                binormal = normalize(cross(tangent, normal));
                viewVector = normalize(pos);
                vTexCoord = vec4(texCoord, 0.0, 0.0);
            }
            """;
}
