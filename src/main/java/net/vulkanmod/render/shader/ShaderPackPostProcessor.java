package net.vulkanmod.render.shader;

import com.mojang.blaze3d.vertex.*;
import net.vulkanmod.config.option.ShaderPackManager;
import net.vulkanmod.pack.ShaderPack;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Applies shader pack effects as a visual overlay.
 * Uses VulkanMod's Vulkan rendering pipeline (clouds pipeline with POSITION_COLOR format)
 * to draw colored borders indicating active shader pack.
 */
public class ShaderPackPostProcessor {

    private static ShaderPackPostProcessor instance;
    private boolean enabled = true;
    private int frameCount = 0;
    private String lastPackName = null;

    private ShaderPackPostProcessor() {
    }

    public static ShaderPackPostProcessor getInstance() {
        if (instance == null) {
            instance = new ShaderPackPostProcessor();
        }
        return instance;
    }

    /**
     * Apply shader pack post-processing effects.
     * Called BEFORE mainPass.end() while the Vulkan render pass is still active.
     */
    public void applyShaderPack() {
        if (!enabled) {
            return;
        }

        ShaderPack pack = ShaderPackManager.getCurrentPack();
        if (pack == null) {
            if (lastPackName != null) {
                System.out.println("[ShaderPackPostProcessor] Shader pack disabled");
                lastPackName = null;
            }
            return;
        }

        // Log shader pack activity (only on first activation)
        if (!pack.getName().equals(lastPackName)) {
            System.out.println("[ShaderPackPostProcessor] =========================================");
            System.out.println("[ShaderPackPostProcessor] SHADER PACK ACTIVE: " + pack.getName());
            System.out.println("[ShaderPackPostProcessor] Version: " + pack.getVersion());
            System.out.println("[ShaderPackPostProcessor] Pipelines: " + pack.getPipelines().size());
            System.out.println("[ShaderPackPostProcessor] =========================================");
            lastPackName = pack.getName();
            frameCount = 0;
        }

        renderShaderPackIndicator(pack);

        frameCount++;
        if (frameCount % 300 == 0) {
            System.out.println("[ShaderPackPostProcessor] Shader pack '" + pack.getName() + "' processing frame " + frameCount);
        }
    }

    /**
     * Draws a colored border around the screen using VulkanMod's Vulkan rendering.
     * Uses the clouds pipeline (POSITION_COLOR format) to draw quads directly.
     */
    private void renderShaderPackIndicator(ShaderPack pack) {
        Renderer renderer = Renderer.getInstance();
        if (renderer.getBoundFramebuffer() == null) {
            return;
        }

        // Determine color based on shader pack
        int r, g, b, a;
        if (pack.getName().contains("Test")) {
            r = 0; g = 255; b = 0; a = 128;   // Green
        } else if (pack.getName().contains("Sunset")) {
            r = 255; g = 128; b = 0; a = 128;  // Orange
        } else {
            r = 0; g = 255; b = 255; a = 128;  // Cyan
        }
        int color = (a << 24) | (r << 16) | (g << 8) | b;

        int fbWidth = renderer.getBoundFramebuffer().getWidth();
        int fbHeight = renderer.getBoundFramebuffer().getHeight();
        float bw = Math.max(4.0f, fbWidth * 0.01f);  // 1% border width, min 4px
        float bh = Math.max(4.0f, fbHeight * 0.01f);

        try {
            GraphicsPipeline pipeline = PipelineManager.getOverlayPipeline();

            // Set render state for 2D overlay
            VRenderSystem.enableBlend();
            VRenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            VRenderSystem.disableDepthTest();
            VRenderSystem.disableCull();
            VRenderSystem.setPrimitiveTopologyGL(GL11.GL_TRIANGLES);

            // Set shader color to white (color comes from vertex data)
            VRenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            // Set up orthographic projection matching screen pixels
            Matrix4f modelView = new Matrix4f().identity();
            Matrix4f projection = new Matrix4f().setOrtho(0.0f, fbWidth, fbHeight, 0.0f, -1.0f, 1.0f);
            VRenderSystem.applyMVP(modelView, projection);

            // Bind pipeline and uniforms
            renderer.bindGraphicsPipeline(pipeline);
            VTextureSelector.bindShaderTextures(pipeline);
            renderer.uploadAndBindUBOs(pipeline);

            // Set viewport and scissor for full framebuffer
            Renderer.setViewport(0, 0, fbWidth, fbHeight);
            Renderer.setScissor(0, 0, fbWidth, fbHeight);

            // Build border quads
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            // Top border
            bufferBuilder.addVertex(0, 0, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, 0, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, bh, 0).setColor(color);
            bufferBuilder.addVertex(0, bh, 0).setColor(color);

            // Bottom border
            bufferBuilder.addVertex(0, fbHeight - bh, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, fbHeight - bh, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, fbHeight, 0).setColor(color);
            bufferBuilder.addVertex(0, fbHeight, 0).setColor(color);

            // Left border
            bufferBuilder.addVertex(0, bh, 0).setColor(color);
            bufferBuilder.addVertex(bw, bh, 0).setColor(color);
            bufferBuilder.addVertex(bw, fbHeight - bh, 0).setColor(color);
            bufferBuilder.addVertex(0, fbHeight - bh, 0).setColor(color);

            // Right border
            bufferBuilder.addVertex(fbWidth - bw, bh, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, bh, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, fbHeight - bh, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth - bw, fbHeight - bh, 0).setColor(color);

            MeshData meshData = bufferBuilder.buildOrThrow();
            Renderer.getDrawer().draw(
                meshData.vertexBuffer(), VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR, meshData.drawState().vertexCount());
            meshData.close();

            // Restore render state
            VRenderSystem.enableDepthTest();
            VRenderSystem.enableCull();
            VRenderSystem.disableBlend();

        } catch (Exception e) {
            if (frameCount % 300 == 0) {
                System.err.println("[ShaderPackPostProcessor] Render error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

