package net.vulkanmod.render.shader.bsl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.SwapChain;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkOffset3D;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages the BSL composite/post-processing pass chain.
 *
 * Architecture:
 * 1. After all scene rendering, the swapchain content is blitted to an offscreen
 *    texture (colortex0).
 * 2. A sequence of fullscreen passes reads from intermediate textures and writes
 *    to the swapchain or other intermediate textures.
 * 3. The "final" pass applies color grading, dithering, and sharpening,
 *    outputting to the swapchain.
 *
 * Current implementation: single "final" pass for color grading + dithering + sharpening.
 * Future: full deferred + composite chain with HDR GBuffer.
 */
public class BSLCompositePass {

    private static boolean enabled = false;
    private static boolean initialized = false;

    // Offscreen textures (the BSL colortex buffers)
    private static VulkanImage colortex0;  // Scene color copy
    private static int lastWidth = 0;
    private static int lastHeight = 0;

    // Composite pipeline (final pass: color grading + dithering + sharpening)
    private static GraphicsPipeline finalPipeline;

    /**
     * Initialize the composite pass system.
     * Creates offscreen textures and compiles the final pass shader.
     */
    public static boolean init(Path shadersDir, Path packDir) {
        if (initialized) return enabled;

        try {
            System.out.println("[BSL Composite] Initializing composite pass system...");

            // Create offscreen textures at current screen resolution
            SwapChain swapChain = Renderer.getInstance().getSwapChain();
            createTextures(swapChain.getWidth(), swapChain.getHeight());

            // Build the final pass pipeline
            finalPipeline = createFinalPipeline(shadersDir, packDir);
            if (finalPipeline == null) {
                System.err.println("[BSL Composite] Failed to create final pipeline");
                return false;
            }

            initialized = true;
            enabled = true;
            System.out.println("[BSL Composite] Composite pass system initialized successfully");
            return true;

        } catch (Exception e) {
            System.err.println("[BSL Composite] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create or resize the offscreen textures.
     */
    private static void createTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && colortex0 != null) return;

        // Clean up old textures
        if (colortex0 != null) colortex0.free();

        System.out.println("[BSL Composite] Creating offscreen textures: " + width + "x" + height);

        // colortex0: scene color copy (same format as swapchain for direct blit)
        colortex0 = VulkanImage.builder(width, height)
                .setName("bsl_colortex0")
                .setFormat(VK_FORMAT_B8G8R8A8_UNORM)  // Match swapchain format
                .setUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .setLinearFiltering(true)
                .setClamp(true)
                .createVulkanImage();

        lastWidth = width;
        lastHeight = height;
    }

    /**
     * Run the composite post-processing chain.
     * Called from Renderer.endFrame() BEFORE mainPass.end().
     *
     * Flow:
     * 1. End current render pass (swapchain is being rendered to)
     * 2. Blit swapchain color → colortex0
     * 3. Rebind main target (starts new render pass for swapchain)
     * 4. Render fullscreen quad with final pipeline (reads colortex0)
     * 5. Render pass left active for mainPass.end()
     */
    public static void process() {
        if (!enabled || !initialized || finalPipeline == null) return;

        Renderer renderer = Renderer.getInstance();
        SwapChain swapChain = renderer.getSwapChain();
        VkCommandBuffer commandBuffer = Renderer.getCommandBuffer();

        // Handle resize
        if (swapChain.getWidth() != lastWidth || swapChain.getHeight() != lastHeight) {
            createTextures(swapChain.getWidth(), swapChain.getHeight());
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Step 1: End the current render pass
            renderer.endRenderPass(commandBuffer);

            // Step 2: Transition swapchain color to TRANSFER_SRC
            VulkanImage swapChainColor = swapChain.getColorAttachment();
            swapChainColor.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

            // Step 3: Transition colortex0 to TRANSFER_DST
            colortex0.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

            // Step 4: Blit swapchain → colortex0
            VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
            blit.srcOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
            blit.srcOffsets(1, VkOffset3D.calloc(stack).set(swapChain.getWidth(), swapChain.getHeight(), 1));
            blit.srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);

            blit.dstOffsets(0, VkOffset3D.calloc(stack).set(0, 0, 0));
            blit.dstOffsets(1, VkOffset3D.calloc(stack).set(lastWidth, lastHeight, 1));
            blit.dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);

            vkCmdBlitImage(commandBuffer,
                    swapChainColor.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    colortex0.getId(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    blit, VK_FILTER_NEAREST);

            // Step 5: Transition colortex0 to SHADER_READ_ONLY
            colortex0.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            // Step 6: Transition swapchain back to COLOR_ATTACHMENT_OPTIMAL
            swapChainColor.transitionImageLayout(stack, commandBuffer, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }

        // Step 7: Rebind main target (starts auxRenderPass on swapchain)
        renderer.getMainPass().rebindMainTarget();

        // Step 8: Render fullscreen quad with final pipeline
        VRenderSystem.disableDepthTest();
        VRenderSystem.disableCull();
        VRenderSystem.disableBlend();
        VRenderSystem.setPrimitiveTopologyGL(GL11.GL_TRIANGLES);

        renderer.bindGraphicsPipeline(finalPipeline);

        // Bind colortex0 AFTER bindGraphicsPipeline but INSTEAD of bindShaderTextures.
        // bindShaderTextures reads from RenderSystem which still has the block atlas
        // from terrain rendering — that would overwrite our colortex0 binding.
        VTextureSelector.bindTexture(0, colortex0);

        renderer.uploadAndBindUBOs(finalPipeline);

        VK11.vkCmdDraw(commandBuffer, 3, 1, 0, 0);

        // Restore state
        VRenderSystem.enableDepthTest();
        VRenderSystem.enableCull();
    }

    /**
     * Create the final post-processing pipeline.
     * Fullscreen triangle that reads colortex0 and applies color grading, dithering, sharpening.
     */
    private static GraphicsPipeline createFinalPipeline(Path shadersDir, Path packDir) {
        try {
            String vertexSource = FINAL_VERTEX_SHADER;
            String fragmentSource = FINAL_FRAGMENT_SHADER;

            // Debug dump
            try {
                Path debugDir = packDir.resolve("debug");
                Files.createDirectories(debugDir);
                Files.writeString(debugDir.resolve("bsl_final.vsh"), vertexSource, StandardCharsets.UTF_8);
                Files.writeString(debugDir.resolve("bsl_final.fsh"), fragmentSource, StandardCharsets.UTF_8);
            } catch (Exception e) { /* ignore */ }

            // Pipeline config: simple UBO + 1 sampler
            JsonObject config = buildFinalPipelineConfig();

            Pipeline.Builder builder = new Pipeline.Builder(
                    CustomVertexFormat.NONE, "bsl_final");
            builder.parseBindings(config);

            SPIRVUtils.SPIRV vertSpirv = SPIRVUtils.compileShader(
                    "bsl_final.vsh", vertexSource, SPIRVUtils.ShaderKind.VERTEX_SHADER);
            SPIRVUtils.SPIRV fragSpirv = SPIRVUtils.compileShader(
                    "bsl_final.fsh", fragmentSource, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

            builder.setVertShaderSPIRV(vertSpirv);
            builder.setFragShaderSPIRV(fragSpirv);

            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (var buffer : pipeline.getBuffers()) {
                buffer.setUseGlobalBuffer(true);
            }

            return pipeline;

        } catch (Exception e) {
            System.err.println("[BSL Composite] Failed to create final pipeline: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Pipeline config for the final pass.
     * Minimal: fragment UBO with screen dimensions + 1 sampler (colortex0).
     */
    private static JsonObject buildFinalPipelineConfig() {
        JsonObject config = new JsonObject();
        JsonArray ubos = new JsonArray();

        // Fragment UBO (binding 0) — minimal: just screen dimensions and time
        JsonObject fragmentUbo = new JsonObject();
        fragmentUbo.addProperty("type", "fragment");
        fragmentUbo.addProperty("binding", 0);
        JsonArray fragmentFields = new JsonArray();
        addField(fragmentFields, "viewWidth", "float", 1);
        addField(fragmentFields, "viewHeight", "float", 1);
        addField(fragmentFields, "aspectRatio", "float", 1);
        addField(fragmentFields, "frameTimeCounter", "float", 1);
        fragmentUbo.add("fields", fragmentFields);
        ubos.add(fragmentUbo);

        config.add("UBOs", ubos);

        // Sampler: colortex0
        JsonArray samplers = new JsonArray();
        addSampler(samplers, "Sampler0");  // colortex0 bound at slot 0
        config.add("samplers", samplers);

        return config;
    }

    private static void addField(JsonArray array, String name, String type, int count) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("type", type);
        field.addProperty("count", count);
        JsonArray values = new JsonArray();
        for (int i = 0; i < count; i++) values.add(0.0f);
        field.add("values", values);
        array.add(field);
    }

    private static void addSampler(JsonArray array, String name) {
        JsonObject sampler = new JsonObject();
        sampler.addProperty("name", name);
        array.add(sampler);
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isInitialized() { return initialized; }

    public static void cleanUp() {
        if (colortex0 != null) {
            colortex0.free();
            colortex0 = null;
        }
        if (finalPipeline != null) {
            finalPipeline.cleanUp();
            finalPipeline = null;
        }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        enabled = false;
    }

    // ---- Shader Sources ----

    /**
     * Fullscreen triangle vertex shader for post-processing passes.
     * Generates a fullscreen triangle from gl_VertexIndex and outputs texCoord.
     */
    private static final String FINAL_VERTEX_SHADER = """
            #version 450

            layout(location = 0) out vec2 texCoord;

            void main() {
                vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
                gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
                // Flip Y for Vulkan coordinate system
                texCoord = vec2(uv.x, 1.0 - uv.y);
            }
            """;

    /**
     * BSL-style final composite fragment shader.
     * Applies color grading, dithering (reduces banding), and CAS sharpening.
     *
     * Based on BSL v10's composite5 and final pass effects.
     */
    private static final String FINAL_FRAGMENT_SHADER = """
            #version 450

            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;

            layout(binding = 0) uniform FinalUBO {
                float viewWidth;
                float viewHeight;
                float aspectRatio;
                float frameTimeCounter;
            };

            layout(binding = 1) uniform sampler2D colortex0;

            // ---- BSL Color Grading Parameters ----
            // Saturation and vibrance (BSL defaults)
            const float SATURATION = 1.00;
            const float VIBRANCE = 1.00;
            // Tint (neutral)
            const vec3 TINT_COLOR = vec3(1.0, 1.0, 1.0);
            // Exposure
            const float EXPOSURE = 1.0;

            // ---- Hash function for dithering ----
            vec3 hash33(vec3 p) {
                p = fract(p * vec3(0.1031, 0.1030, 0.0973));
                p += dot(p, p.yxz + 33.33);
                return fract((p.xxy + p.yxx) * p.zyx);
            }

            // ---- Saturation / Vibrance ----
            void applySaturationVibrance(inout vec3 color) {
                float luma = dot(color, vec3(0.299, 0.587, 0.114));
                float mn = min(color.r, min(color.g, color.b));
                float mx = max(color.r, max(color.g, color.b));
                float saturation_val = (mx > 0.0) ? (1.0 - mn / mx) : 0.0;

                // Vibrance: boost desaturated colors more
                float vibranceFactor = VIBRANCE * (1.0 - saturation_val);
                color = mix(vec3(luma), color, vec3(1.0 + vibranceFactor));

                // Saturation
                color = mix(vec3(luma), color, vec3(SATURATION));
            }

            // ---- CAS Sharpening (AMD FidelityFX Contrast Adaptive Sharpening) ----
            vec3 casFilter(vec2 uv) {
                vec2 texelSize = 1.0 / vec2(viewWidth, viewHeight);

                // Sample center and 4 neighbors
                vec3 c = texture(colortex0, uv).rgb;
                vec3 n = texture(colortex0, uv + vec2(0.0, -texelSize.y)).rgb;
                vec3 s = texture(colortex0, uv + vec2(0.0,  texelSize.y)).rgb;
                vec3 e = texture(colortex0, uv + vec2( texelSize.x, 0.0)).rgb;
                vec3 w = texture(colortex0, uv + vec2(-texelSize.x, 0.0)).rgb;

                // Min/max of neighborhood
                vec3 mn4 = min(min(n, s), min(e, w));
                vec3 mx4 = max(max(n, s), max(e, w));

                // CAS weight
                vec3 rcpM = 1.0 / (4.0 * mx4 + 0.001);
                vec3 amp = clamp(min(mn4, 2.0 - mx4) * rcpM, 0.0, 1.0);
                amp = sqrt(amp);

                float sharpness = 0.4; // BSL default CAS strength
                vec3 w_all = amp * sharpness;

                // Apply filter: sharpen center based on neighborhood contrast
                return clamp((c + (c - (n + s + e + w) * 0.25) * w_all), 0.0, 1.0);
            }

            void main() {
                // CAS Sharpening
                vec3 color = casFilter(texCoord);

                // Exposure
                color *= EXPOSURE;

                // Color tint
                color *= TINT_COLOR;

                // Saturation and vibrance
                applySaturationVibrance(color);

                // Dithering (reduces color banding in gradients)
                vec3 dither = hash33(vec3(gl_FragCoord.xy, frameTimeCounter * 100.0));
                color += (dither - 0.5) / 255.0;

                fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
            """;
}
