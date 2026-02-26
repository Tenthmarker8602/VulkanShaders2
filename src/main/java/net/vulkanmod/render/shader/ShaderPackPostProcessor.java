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

    private boolean pipelineApplied = false;

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
                ShaderPackPipelineLoader.getInstance().restoreDefaults();
                lastPackName = null;
                pipelineApplied = false;
            }
            return;
        }

        // On first activation or pack change, apply the real Vulkan pipeline
        if (!pack.getName().equals(lastPackName)) {
            System.out.println("[ShaderPackPostProcessor] =========================================");
            System.out.println("[ShaderPackPostProcessor] SHADER PACK ACTIVE: " + pack.getName());
            System.out.println("[ShaderPackPostProcessor] Version: " + pack.getVersion());
            System.out.println("[ShaderPackPostProcessor] Pipelines: " + pack.getPipelines().size());
            System.out.println("[ShaderPackPostProcessor] =========================================");
            lastPackName = pack.getName();
            frameCount = 0;
            pipelineApplied = false;
        }

        // Lazily apply pipeline replacement (needs to happen on the render thread)
        if (!pipelineApplied) {
            try {
                boolean applied = ShaderPackPipelineLoader.getInstance().applyShaderPack(pack);
                if (applied) {
                    System.out.println("[ShaderPackPostProcessor] Pipeline replacement applied for: " + pack.getName());
                } else {
                    System.out.println("[ShaderPackPostProcessor] No pipeline replacements in pack: " + pack.getName());
                }
                pipelineApplied = true;
            } catch (Exception e) {
                System.err.println("[ShaderPackPostProcessor] Failed to apply pipeline: " + e.getMessage());
                e.printStackTrace();
                pipelineApplied = true; // Don't retry
            }
        }

        renderShaderPackIndicator(pack);

        frameCount++;
        if (frameCount % 600 == 0) {
            System.out.println("[ShaderPackPostProcessor] Shader pack '" + pack.getName() + "' active, frame " + frameCount);
        }
    }

    /**
     * Draws a small indicator bar at the top of the screen to show active shader pack.
     * The real visual effect comes from the terrain pipeline replacement.
     */
    private void renderShaderPackIndicator(ShaderPack pack) {
        Renderer renderer = Renderer.getInstance();
        if (renderer.getBoundFramebuffer() == null) {
            return;
        }

        // Determine color based on shader pack
        int r, g, b, a;
        if (pack.getName().contains("Test") && !pack.getName().contains("Sunset")) {
            r = 0; g = 200; b = 100; a = 100;   // Green
        } else if (pack.getName().contains("Sunset")) {
            r = 255; g = 140; b = 0; a = 100;    // Orange
        } else {
            r = 0; g = 200; b = 255; a = 100;    // Cyan
        }
        int color = (a << 24) | (r << 16) | (g << 8) | b;

        int fbWidth = renderer.getBoundFramebuffer().getWidth();
        int fbHeight = renderer.getBoundFramebuffer().getHeight();
        float barHeight = 3.0f; // thin indicator bar

        try {
            GraphicsPipeline pipeline = PipelineManager.getOverlayPipeline();

            VRenderSystem.enableBlend();
            VRenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            VRenderSystem.disableDepthTest();
            VRenderSystem.disableCull();
            VRenderSystem.setPrimitiveTopologyGL(GL11.GL_TRIANGLES);
            VRenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            Matrix4f modelView = new Matrix4f().identity();
            Matrix4f projection = new Matrix4f().setOrtho(0.0f, fbWidth, fbHeight, 0.0f, -1.0f, 1.0f);
            VRenderSystem.applyMVP(modelView, projection);

            renderer.bindGraphicsPipeline(pipeline);
            VTextureSelector.bindShaderTextures(pipeline);
            renderer.uploadAndBindUBOs(pipeline);

            Renderer.setViewport(0, 0, fbWidth, fbHeight);
            Renderer.setScissor(0, 0, fbWidth, fbHeight);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferBuilder = tesselator.begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            // Top indicator bar
            bufferBuilder.addVertex(0, 0, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, 0, 0).setColor(color);
            bufferBuilder.addVertex(fbWidth, barHeight, 0).setColor(color);
            bufferBuilder.addVertex(0, barHeight, 0).setColor(color);

            MeshData meshData = bufferBuilder.buildOrThrow();
            Renderer.getDrawer().draw(
                meshData.vertexBuffer(), VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR, meshData.drawState().vertexCount());
            meshData.close();

            VRenderSystem.enableDepthTest();
            VRenderSystem.enableCull();
            VRenderSystem.disableBlend();

        } catch (Exception e) {
            if (frameCount % 600 == 0) {
                System.err.println("[ShaderPackPostProcessor] Indicator render error: " + e.getMessage());
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

