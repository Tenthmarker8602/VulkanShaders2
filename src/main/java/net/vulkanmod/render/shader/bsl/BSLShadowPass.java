package net.vulkanmod.render.shader.bsl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the BSL shadow mapping pass.
 * Creates a depth-only shadow map framebuffer, shadow pipeline,
 * computes shadow matrices, and renders terrain from the sun's perspective.
 */
public class BSLShadowPass {

    public static final int SHADOW_MAP_SIZE = 2048;
    private static final float SHADOW_DISTANCE = 128.0f;
    private static final float SHADOW_MAP_BIAS = 0.85f; // BSL's shadowMapBias default

    private static boolean enabled = false;
    private static boolean initialized = false;

    // Shadow resources
    private static Framebuffer shadowFramebuffer;
    private static RenderPass shadowRenderPass;
    private static GraphicsPipeline shadowPipeline;
    private static VulkanImage shadowDepthImage;

    // Shadow matrices
    private static final Matrix4f shadowModelViewMatrix = new Matrix4f();
    private static final Matrix4f shadowProjectionMatrix = new Matrix4f();
    private static final Matrix4f shadowMVPMatrix = new Matrix4f();

    // Per-frame tracking
    private static boolean shadowPassDoneThisFrame = false;

    /**
     * Initialize shadow map resources. Called once when BSL pack is loaded.
     */
    public static boolean init(Path packDir) {
        if (initialized) return true;

        try {
            System.out.println("[BSL Shadow] Initializing shadow map (" + SHADOW_MAP_SIZE + "x" + SHADOW_MAP_SIZE + ")");

            // Create shadow framebuffer (depth-only)
            shadowFramebuffer = Framebuffer.builder(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, 0, true)
                    .setDepthLinearFiltering(true)
                    .build();

            shadowDepthImage = shadowFramebuffer.getDepthAttachment();

            // Create shadow render pass (depth-only, clear on load, STORE depth)
            RenderPass.Builder rpBuilder = RenderPass.builder(shadowFramebuffer);
            // Override depth store op to STORE so we can read the depth texture later
            // setOps(loadOp, storeOp) — use CLEAR + STORE
            rpBuilder.getDepthAttachmentInfo().setOps(
                    org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR,
                    org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE);
            shadowRenderPass = rpBuilder.build();

            // Create shadow pipeline
            shadowPipeline = createShadowPipeline(packDir);
            if (shadowPipeline == null) {
                System.err.println("[BSL Shadow] Failed to create shadow pipeline");
                return false;
            }

            initialized = true;
            enabled = true;
            System.out.println("[BSL Shadow] Shadow map initialized successfully");
            return true;

        } catch (Exception e) {
            System.err.println("[BSL Shadow] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create the shadow rendering pipeline.
     * Uses a simple vertex shader (position + alpha test) and minimal fragment shader.
     */
    private static GraphicsPipeline createShadowPipeline(Path packDir) {
        try {
            String vertexSource = SHADOW_VERTEX_SHADER;
            String fragmentSource = SHADOW_FRAGMENT_SHADER;

            // Dump debug shaders
            try {
                Path debugDir = packDir.resolve("debug");
                Files.createDirectories(debugDir);
                Files.writeString(debugDir.resolve("bsl_shadow.vsh"), vertexSource);
                Files.writeString(debugDir.resolve("bsl_shadow.fsh"), fragmentSource);
            } catch (Exception e) { /* ignore */ }

            // Build pipeline config
            JsonObject config = buildShadowPipelineConfig();

            Pipeline.Builder builder = new Pipeline.Builder(
                    CustomVertexFormat.COMPRESSED_TERRAIN, "bsl_shadow");
            builder.parseBindings(config);

            SPIRVUtils.SPIRV vertSpirv = SPIRVUtils.compileShader(
                    "bsl_shadow.vsh", vertexSource, SPIRVUtils.ShaderKind.VERTEX_SHADER);
            SPIRVUtils.SPIRV fragSpirv = SPIRVUtils.compileShader(
                    "bsl_shadow.fsh", fragmentSource, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

            builder.setVertShaderSPIRV(vertSpirv);
            builder.setFragShaderSPIRV(fragSpirv);

            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (var buffer : pipeline.getBuffers()) {
                buffer.setUseGlobalBuffer(true);
            }

            return pipeline;

        } catch (Exception e) {
            System.err.println("[BSL Shadow] Failed to create shadow pipeline: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Build pipeline config JSON for the shadow pass.
     * Minimal UBO: just MVP matrix + camera position.
     */
    private static JsonObject buildShadowPipelineConfig() {
        JsonObject config = new JsonObject();
        JsonArray ubos = new JsonArray();

        // Vertex UBO (binding 0) - same layout as terrain for compatibility
        JsonObject vertexUbo = new JsonObject();
        vertexUbo.addProperty("type", "vertex");
        vertexUbo.addProperty("binding", 0);
        JsonArray vertexFields = new JsonArray();

        addField(vertexFields, "MVP", "matrix4x4", 16);
        addField(vertexFields, "gbufferModelView", "matrix4x4", 16);
        addField(vertexFields, "gbufferModelViewInverse", "matrix4x4", 16);
        addField(vertexFields, "timeAngle", "float", 1);
        addField(vertexFields, "sunPathRotation", "float", 1);
        addField(vertexFields, "frameTimeCounter", "float", 1);
        addField(vertexFields, "_vpad0", "float", 1);
        addField(vertexFields, "cameraPosition", "float", 3);
        addField(vertexFields, "_vpad1", "float", 1);

        vertexUbo.add("fields", vertexFields);
        ubos.add(vertexUbo);
        config.add("UBOs", ubos);

        // Samplers: only block atlas for alpha testing
        JsonArray samplers = new JsonArray();
        addSampler(samplers, "Sampler0");
        config.add("samplers", samplers);

        // Push constants (ModelOffset)
        JsonArray pushConstants = new JsonArray();
        addField(pushConstants, "ModelOffset", "float", 3);
        config.add("PushConstants", pushConstants);

        return config;
    }

    /**
     * Compute shadow matrices based on current sun position.
     * Called once per frame before the shadow pass.
     */
    public static void updateShadowMatrices() {
        float timeAngle = BSLUniformProvider.getTimeAngle();
        float sunPathRotation = BSLUniformProvider.getSunPathRotation();
        float sunPathRad = (float) (sunPathRotation * Math.PI / 180.0);

        // BSL sun angle calculation (matches vertex shader)
        float ang = timeAngle - 0.25f;
        ang = (float) ((ang + (Math.cos(ang * Math.PI) * -0.5 + 0.5 - ang) / 3.0) * 2.0 * Math.PI);

        // Sun direction in world space
        float sunX = (float) -Math.sin(ang);
        float sunY = (float) (Math.cos(ang) * Math.cos(sunPathRad));
        float sunZ = (float) (Math.cos(ang) * -Math.sin(sunPathRad));

        // Use moon direction during night (when sun is below horizon)
        float sunVisibility = Math.max(sunY, 0.0f);
        if (sunVisibility < 0.01f) {
            sunX = -sunX;
            sunY = -sunY;
            sunZ = -sunZ;
        }

        // Shadow model-view: look from sun direction toward origin
        float dist = 200.0f;
        shadowModelViewMatrix.identity().lookAt(
                sunX * dist, sunY * dist, sunZ * dist,
                0, 0, 0,
                0, 1, 0
        );

        // Shadow projection: orthographic
        shadowProjectionMatrix.identity().ortho(
                -SHADOW_DISTANCE, SHADOW_DISTANCE,
                -SHADOW_DISTANCE, SHADOW_DISTANCE,
                0.05f, dist * 2.0f
        );
    }

    /**
     * Begin the shadow render pass.
     * Ends any current render pass and begins rendering to the shadow framebuffer.
     */
    public static void beginShadowPass(VkCommandBuffer commandBuffer) {
        Renderer renderer = Renderer.getInstance();
        // Use Renderer.beginRenderPass which handles ending current pass,
        // setting viewport/scissor, and tracking bound framebuffer/renderpass
        renderer.beginRenderPass(shadowRenderPass, shadowFramebuffer);
    }

    /**
     * End the shadow render pass and transition the shadow depth to shader-readable.
     */
    public static void endShadowPass(VkCommandBuffer commandBuffer) {
        Renderer renderer = Renderer.getInstance();
        renderer.endRenderPass();

        // Transition shadow depth to shader-read-only so terrain shader can sample it
        try (MemoryStack stack = MemoryStack.stackPush()) {
            shadowDepthImage.readOnlyLayout(stack, commandBuffer);
        }
    }

    /**
     * Get the shadow MVP matrix pre-multiplied with camera translation.
     * This allows the shadow vertex shader to use camera-relative positions directly.
     */
    public static Matrix4f getShadowMVP(double camX, double camY, double camZ) {
        shadowMVPMatrix.set(shadowProjectionMatrix)
                .mul(shadowModelViewMatrix)
                .translate((float) camX, (float) camY, (float) camZ);
        return shadowMVPMatrix;
    }

    /**
     * Bind the shadow depth texture and stub textures to VTextureSelector for BSL shaders.
     */
    public static void bindShadowTextures() {
        if (shadowDepthImage != null) {
            // Bind shadow depth to slots 4 and 5 (shadowtex0 and shadowtex1)
            VTextureSelector.bindTexture(4, shadowDepthImage);
            VTextureSelector.bindTexture(5, shadowDepthImage);

            // Bind stub textures for samplers that we don't have real data for.
            // Use the shadow depth image as a valid placeholder to avoid null descriptor writes.
            VTextureSelector.bindTexture(6, shadowDepthImage);  // noisetex stub
            VTextureSelector.bindTexture(7, shadowDepthImage);  // depthtex1 stub
            VTextureSelector.bindTexture(8, shadowDepthImage);  // depthtex0 stub
            VTextureSelector.bindTexture(9, shadowDepthImage);  // gaux1 stub
            VTextureSelector.bindTexture(10, shadowDepthImage); // gaux2 stub
        }
    }

    /**
     * Reset per-frame state. Called at the beginning of each frame.
     */
    public static void resetFrame() {
        shadowPassDoneThisFrame = false;
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isInitialized() { return initialized; }
    public static boolean isShadowPassDone() { return shadowPassDoneThisFrame; }
    public static void markShadowPassDone() { shadowPassDoneThisFrame = true; }

    public static GraphicsPipeline getShadowPipeline() { return shadowPipeline; }
    public static Framebuffer getShadowFramebuffer() { return shadowFramebuffer; }
    public static Matrix4f getShadowModelView() { return shadowModelViewMatrix; }
    public static Matrix4f getShadowProjection() { return shadowProjectionMatrix; }

    public static void cleanUp() {
        if (shadowFramebuffer != null) {
            shadowFramebuffer.cleanUp();
            shadowFramebuffer = null;
        }
        if (shadowPipeline != null) {
            shadowPipeline.cleanUp();
            shadowPipeline = null;
        }
        shadowDepthImage = null;
        initialized = false;
        enabled = false;
    }

    // ---- Helper methods for pipeline config ----

    private static void addField(JsonArray array, String name, String type, int count) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("type", type);
        field.addProperty("count", count);
        JsonArray values = new JsonArray();
        if (count == 16) {
            for (int i = 0; i < 16; i++) values.add((i % 5 == 0) ? 1.0f : 0.0f);
        } else {
            for (int i = 0; i < count; i++) values.add(0.0f);
        }
        field.add("values", values);
        array.add(field);
    }

    private static void addSampler(JsonArray array, String name) {
        JsonObject sampler = new JsonObject();
        sampler.addProperty("name", name);
        array.add(sampler);
    }

    // ---- Shadow Shaders ----

    private static final String SHADOW_VERTEX_SHADER = """
            #version 450

            // ====================================================================
            // BSL Shadow Pass - Vertex Shader
            // Renders terrain from sun's perspective for shadow mapping.
            // ====================================================================

            layout(location = 0) in ivec4 Position;
            layout(location = 1) in uvec2 UV0;
            layout(location = 2) in uint PackedColor;

            layout(binding = 0) uniform ShadowVertexUBO {
                mat4 MVP;                      // shadowProjection * shadowModelView * cameraTranslation
                mat4 gbufferModelView;         // unused but needed for UBO layout compatibility
                mat4 gbufferModelViewInverse;  // unused
                float timeAngle;
                float sunPathRotation;
                float frameTimeCounter;
                float _vpad0;
                vec3 cameraPosition;           // unused (folded into MVP)
                float _vpad1;
            };

            layout(push_constant) uniform PushConstants {
                vec3 ModelOffset;
            };

            layout(location = 0) out vec2 texCoord;

            const vec3 POSITION_INV = vec3(1.0 / 2048.0);
            const float UV_INV = 1.0 / 32768.0;

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
                gl_Position = MVP * vec4(pos, 1.0);

                // BSL shadow map distortion
                float dist = sqrt(gl_Position.x * gl_Position.x + gl_Position.y * gl_Position.y);
                float distortFactor = dist * 0.85 + 0.15;
                gl_Position.xy /= distortFactor;
                gl_Position.z *= 0.2;

                texCoord = vec2(UV0) * UV_INV;
            }
            """;

    private static final String SHADOW_FRAGMENT_SHADER = """
            #version 450

            layout(location = 0) in vec2 texCoord;

            layout(binding = 1) uniform sampler2D Sampler0;

            void main() {
                float alpha = texture(Sampler0, texCoord).a;
                if (alpha < 0.1) discard;
                // No color output - depth-only framebuffer
            }
            """;
}
