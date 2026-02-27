package net.vulkanmod.render.shader.bsl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages the BSL sky rendering pass.
 * Renders a fullscreen triangle with BSL's atmospheric sky shader
 * before terrain, so terrain depth-tests correctly against sky.
 */
public class BSLSkyPass {

    private static boolean enabled = false;
    private static boolean initialized = false;
    private static GraphicsPipeline skyPipeline;

    // Per-frame tracking
    private static boolean skyPassDoneThisFrame = false;

    /**
     * Initialize sky resources. Called once when BSL pack is loaded.
     */
    public static boolean init(Path shadersDir, Path packDir) {
        if (initialized) return true;

        try {
            System.out.println("[BSL Sky] Initializing sky shader...");

            // Read skybasic fragment source from world0 wrapper
            Path wrapper = shadersDir.resolve("world0/gbuffers_skybasic.fsh");
            if (!Files.exists(wrapper)) {
                System.out.println("[BSL Sky] No skybasic shader found");
                return false;
            }

            String fragmentSource = Files.readString(wrapper, StandardCharsets.UTF_8);
            fragmentSource = BSLIncludeResolver.resolve(shadersDir, fragmentSource);
            System.out.println("[BSL Sky] Resolved includes: " + fragmentSource.length() + " chars");

            // Transpile with sky-specific varying locations
            String transpiledFragment = GLSLTranspiler.transpileSkyFragment(fragmentSource);
            System.out.println("[BSL Sky] Transpiled fragment: " + transpiledFragment.length() + " chars");

            // Generate fullscreen sky vertex shader
            String vertexSource = generateSkyVertexShader();

            // Debug dump
            try {
                Path debugDir = packDir.resolve("debug");
                Files.createDirectories(debugDir);
                Files.writeString(debugDir.resolve("bsl_sky.fsh"), transpiledFragment);
                Files.writeString(debugDir.resolve("bsl_sky.vsh"), vertexSource);
            } catch (Exception e) { /* ignore */ }

            // Build pipeline config (same UBO layout as terrain + noisetex sampler)
            JsonObject config = buildSkyPipelineConfig();

            Pipeline.Builder builder = new Pipeline.Builder(
                    CustomVertexFormat.NONE, "bsl_sky");
            builder.parseBindings(config);

            SPIRVUtils.SPIRV vertSpirv = SPIRVUtils.compileShader(
                    "bsl_sky.vsh", vertexSource, SPIRVUtils.ShaderKind.VERTEX_SHADER);
            SPIRVUtils.SPIRV fragSpirv = SPIRVUtils.compileShader(
                    "bsl_sky.fsh", transpiledFragment, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

            builder.setVertShaderSPIRV(vertSpirv);
            builder.setFragShaderSPIRV(fragSpirv);

            skyPipeline = builder.createGraphicsPipeline();
            for (var buffer : skyPipeline.getBuffers()) {
                buffer.setUseGlobalBuffer(true);
            }

            initialized = true;
            enabled = true;
            System.out.println("[BSL Sky] Sky shader initialized successfully");
            return true;

        } catch (Exception e) {
            System.err.println("[BSL Sky] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Render the BSL sky as a fullscreen triangle.
     * Should be called once per frame, before terrain rendering,
     * while the main render pass is active.
     */
    public static void renderSky() {
        if (!enabled || !initialized || skyPipeline == null) return;
        if (skyPassDoneThisFrame) return;

        // Ensure all sampler slots have valid textures before binding descriptors.
        // Slots 0-3 may be null on the first frame (before terrain binds atlas/lightmap).
        VulkanImage placeholder = VTextureSelector.getWhiteTexture();
        for (int i = 0; i < 4; i++) {
            if (VTextureSelector.getImage(i) == null) {
                VTextureSelector.bindTexture(i, placeholder);
            }
        }

        // Bind shadow textures (includes noisetex stub at slot 6)
        BSLShadowPass.bindShadowTextures();
        // Override stub with real noisetex
        BSLTextureManager.bindTextures();

        // Disable depth test, cull, and blending for fullscreen sky triangle
        // Blending must be explicitly disabled to prevent vanilla sky from bleeding through
        VRenderSystem.disableDepthTest();
        VRenderSystem.disableCull();
        VRenderSystem.disableBlend();
        VRenderSystem.setPrimitiveTopologyGL(GL11.GL_TRIANGLES);

        Renderer renderer = Renderer.getInstance();
        renderer.bindGraphicsPipeline(skyPipeline);
        renderer.uploadAndBindUBOs(skyPipeline);

        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();
        VK11.vkCmdDraw(commandBuffer, 3, 1, 0, 0);

        // Restore state
        VRenderSystem.enableDepthTest();
        VRenderSystem.enableCull();

        skyPassDoneThisFrame = true;
    }

    /**
     * Reset per-frame state. Called at the beginning of each frame.
     */
    public static void resetFrame() {
        skyPassDoneThisFrame = false;
    }

    public static boolean isEnabled() { return enabled; }

    /**
     * Generate the fullscreen sky vertex shader.
     * Produces a fullscreen triangle from gl_VertexIndex
     * and computes BSL's sun/up direction vectors.
     */
    private static String generateSkyVertexShader() {
        return """
                #version 450

                // BSL Sky Pass - Fullscreen Vertex Shader
                // Generates fullscreen triangle and computes sun/up vectors.

                layout(binding = 0) uniform SkyVertexUBO {
                    mat4 MVP;                      // unused for fullscreen, but needed for UBO layout compat
                    mat4 gbufferModelView;
                    mat4 gbufferModelViewInverse;
                    float timeAngle;
                    float sunPathRotation;
                    float frameTimeCounter;
                    float _vpad0;
                    vec3 cameraPosition;
                    float _vpad1;
                };

                layout(location = 0) out float alpha;
                layout(location = 1) out vec3 sunVec;
                layout(location = 2) out vec3 upVec;

                void main() {
                    // Fullscreen triangle from vertex index (covers entire screen)
                    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
                    gl_Position = vec4(uv * 2.0 - 1.0, 0.9999, 1.0);

                    alpha = 1.0; // Sky background (not stars)

                    // BSL sun direction computation
                    const float sunPathRad = sunPathRotation * 0.01745329251994;
                    vec2 sunRotationData = vec2(cos(sunPathRad), -sin(sunPathRad));

                    float ang = fract(timeAngle - 0.25);
                    ang = (ang + (cos(ang * 3.14159265358979) * -0.5 + 0.5 - ang) / 3.0) * 6.28318530717959;

                    sunVec = normalize((gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotationData) * 2000.0, 1.0)).xyz);
                    upVec = normalize(gbufferModelView[1].xyz);
                }
                """;
    }

    /**
     * Build the sky pipeline config. Uses same UBO layout as terrain
     * for uniform compatibility, but only needs noisetex sampler.
     */
    private static JsonObject buildSkyPipelineConfig() {
        JsonObject config = new JsonObject();
        JsonArray ubos = new JsonArray();

        // Vertex UBO (binding 0) - same layout as terrain
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

        // Fragment UBO (binding 1) - same as terrain
        JsonObject fragmentUbo = new JsonObject();
        fragmentUbo.addProperty("type", "fragment");
        fragmentUbo.addProperty("binding", 1);
        JsonArray fragmentFields = new JsonArray();
        addField(fragmentFields, "gbufferProjection", "matrix4x4", 16);
        addField(fragmentFields, "gbufferProjectionInverse", "matrix4x4", 16);
        addField(fragmentFields, "gbufferModelView", "matrix4x4", 16);
        addField(fragmentFields, "gbufferModelViewInverse", "matrix4x4", 16);
        addField(fragmentFields, "shadowProjection", "matrix4x4", 16);
        addField(fragmentFields, "shadowModelView", "matrix4x4", 16);
        addField(fragmentFields, "FogColor_bsl", "float", 4);
        addField(fragmentFields, "cameraPosition", "float", 3);
        addField(fragmentFields, "_pad0", "float", 1);
        addField(fragmentFields, "relativeEyePosition", "float", 3);
        addField(fragmentFields, "_pad1", "float", 1);
        addField(fragmentFields, "timeAngle", "float", 1);
        addField(fragmentFields, "timeBrightness", "float", 1);
        addField(fragmentFields, "frameTimeCounter", "float", 1);
        addField(fragmentFields, "rainStrength", "float", 1);
        addField(fragmentFields, "nightVision", "float", 1);
        addField(fragmentFields, "shadowFade", "float", 1);
        addField(fragmentFields, "near", "float", 1);
        addField(fragmentFields, "far", "float", 1);
        addField(fragmentFields, "viewWidth", "float", 1);
        addField(fragmentFields, "viewHeight", "float", 1);
        addField(fragmentFields, "screenBrightness", "float", 1);
        addField(fragmentFields, "cloudHeight", "float", 1);
        addField(fragmentFields, "endFlashIntensity", "float", 1);
        addField(fragmentFields, "aspectRatio", "float", 1);
        addField(fragmentFields, "AlphaCutout", "float", 1);
        addField(fragmentFields, "sunPathRotation", "float", 1);
        addField(fragmentFields, "bsl_frameCounter", "float", 1);
        addField(fragmentFields, "bsl_isEyeInWater", "float", 1);
        addField(fragmentFields, "bsl_moonPhase", "float", 1);
        addField(fragmentFields, "bsl_worldTime", "float", 1);
        addField(fragmentFields, "eyeBrightnessSmooth_x", "float", 1);
        addField(fragmentFields, "eyeBrightnessSmooth_y", "float", 1);
        addField(fragmentFields, "heldBlockLightValue", "float", 1);
        addField(fragmentFields, "heldBlockLightValue2", "float", 1);
        // Sun/moon position in view space (vec3 + std140 padding)
        addField(fragmentFields, "sunPosition", "float", 3);
        addField(fragmentFields, "_padSun", "float", 1);
        addField(fragmentFields, "moonPosition", "float", 3);
        addField(fragmentFields, "_padMoon", "float", 1);
        fragmentUbo.add("fields", fragmentFields);
        ubos.add(fragmentUbo);

        config.add("UBOs", ubos);

        // Samplers - only noisetex needed for sky
        // Use same sequential bindings as terrain for compatibility
        JsonArray samplers = new JsonArray();
        addSampler(samplers, "Sampler0");    // binding 2 (unused by sky but needed for layout compat)
        addSampler(samplers, "Sampler2");    // binding 3 (unused)
        addSampler(samplers, "Sampler4");    // binding 4 (unused)
        addSampler(samplers, "Sampler5");    // binding 5 (unused)
        addSampler(samplers, "Sampler6");    // binding 6 - noisetex
        config.add("samplers", samplers);

        return config;
    }

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

    public static void cleanUp() {
        if (skyPipeline != null) {
            skyPipeline.cleanUp();
            skyPipeline = null;
        }
        initialized = false;
        enabled = false;
    }
}
