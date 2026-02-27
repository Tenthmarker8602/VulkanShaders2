package net.vulkanmod.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.render.chunk.build.thread.ThreadBuilderPack;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;

import java.util.function.Function;

public abstract class PipelineManager {
    public static VertexFormat terrainVertexFormat;

    public static void setTerrainVertexFormat(VertexFormat format) {
        terrainVertexFormat = format;
    }

    static GraphicsPipeline
            terrainShader, terrainShaderEarlyZ,
            fastBlitPipeline, cloudsPipeline, overlayPipeline;

    // Shader pack pipeline replacement system
    private static GraphicsPipeline originalTerrainShader;
    private static GraphicsPipeline shaderPackTerrainPipeline;
    private static GraphicsPipeline shaderPackWaterPipeline;

    private static Function<TerrainRenderType, GraphicsPipeline> shaderGetter;

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
        ThreadBuilderPack.defaultTerrainBuilderConstructor();
    }

    public static void setDefaultShader() {
        setShaderGetter(
                renderType -> renderType == TerrainRenderType.TRANSLUCENT ? terrainShaderEarlyZ : terrainShader);
    }

    private static void createBasicPipelines() {
        terrainShaderEarlyZ = createPipeline("terrain_earlyZ", terrainVertexFormat);
        terrainShader = createPipeline("terrain", terrainVertexFormat);
        fastBlitPipeline = createPipeline("blit", CustomVertexFormat.NONE);
        cloudsPipeline = createPipeline("clouds", DefaultVertexFormat.POSITION_COLOR);
        overlayPipeline = createPipeline("overlay", DefaultVertexFormat.POSITION_COLOR);
    }

    private static GraphicsPipeline createPipeline(String configName, VertexFormat vertexFormat) {
        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, configName);

        final String path = ShaderLoadUtil.resolveShaderPath("basic");
        JsonObject config = ShaderLoadUtil.getJsonConfig(path, configName);
        pipelineBuilder.parseBindings(config);

        ShaderLoadUtil.loadShaders(pipelineBuilder, config, configName, path);

        var pipeline = pipelineBuilder.createGraphicsPipeline();

        for (var buffer : pipeline.getBuffers()) {
            buffer.setUseGlobalBuffer(true);
        }

        return pipeline;
    }

    public static GraphicsPipeline getTerrainShader(TerrainRenderType renderType) {
        return shaderGetter.apply(renderType);
    }

    public static void setShaderGetter(Function<TerrainRenderType, GraphicsPipeline> consumer) {
        shaderGetter = consumer;
    }

    public static GraphicsPipeline getTerrainDirectShader(RenderType renderType) {
        return terrainShader;
    }

    public static GraphicsPipeline getTerrainIndirectShader(RenderType renderType) {
        return terrainShaderEarlyZ;
    }

    public static GraphicsPipeline getFastBlitPipeline() {
        return fastBlitPipeline;
    }

    public static GraphicsPipeline getCloudsPipeline() {
        return cloudsPipeline;
    }

    public static GraphicsPipeline getOverlayPipeline() {
        return overlayPipeline;
    }

    /**
     * Apply a shader pack's terrain pipeline, replacing the default.
     * Stores the original for later restoration.
     */
    public static void applyShaderPackTerrainPipeline(GraphicsPipeline newTerrain) {
        // Store original if not already stored
        if (originalTerrainShader == null) {
            originalTerrainShader = terrainShader;
        }

        // Clean up any previous shader pack pipeline
        if (shaderPackTerrainPipeline != null && shaderPackTerrainPipeline != terrainShader) {
            shaderPackTerrainPipeline.scheduleCleanUp();
        }

        shaderPackTerrainPipeline = newTerrain;
        terrainShader = newTerrain;

        // Update the shader getter to use the new terrain pipeline
        // If water pipeline is also set, use it for TRANSLUCENT; otherwise use earlyZ for TRANSLUCENT
        updateShaderGetter();

        System.out.println("[PipelineManager] Shader pack terrain pipeline applied");
    }

    /**
     * Apply a shader pack's water pipeline for translucent terrain.
     */
    public static void applyShaderPackWaterPipeline(GraphicsPipeline newWater) {
        if (shaderPackWaterPipeline != null && shaderPackWaterPipeline != terrainShaderEarlyZ) {
            shaderPackWaterPipeline.scheduleCleanUp();
        }

        shaderPackWaterPipeline = newWater;
        updateShaderGetter();

        System.out.println("[PipelineManager] Shader pack water pipeline applied");
    }

    /**
     * Update the shader getter to reflect current pipeline state.
     */
    private static void updateShaderGetter() {
        setShaderGetter(renderType -> {
            if (renderType == TerrainRenderType.TRANSLUCENT) {
                return shaderPackWaterPipeline != null ? shaderPackWaterPipeline : terrainShaderEarlyZ;
            }
            return terrainShader;
        });

        System.out.println("[PipelineManager] Shader pack terrain pipeline applied");
    }

    /**
     * Restore the default terrain pipeline, removing shader pack overrides.
     */
    public static void restoreDefaultPipelines() {
        if (originalTerrainShader != null) {
            // Clean up shader pack pipelines
            if (shaderPackTerrainPipeline != null) {
                shaderPackTerrainPipeline.scheduleCleanUp();
                shaderPackTerrainPipeline = null;
            }
            if (shaderPackWaterPipeline != null) {
                shaderPackWaterPipeline.scheduleCleanUp();
                shaderPackWaterPipeline = null;
            }

            terrainShader = originalTerrainShader;
            originalTerrainShader = null;

            // Restore default shader getter
            setDefaultShader();

            System.out.println("[PipelineManager] Default terrain pipeline restored");
        }
    }

    /**
     * Check if a shader pack pipeline is currently active.
     */
    public static boolean hasShaderPackPipeline() {
        return shaderPackTerrainPipeline != null;
    }

    public static void destroyPipelines() {
        // Clean up shader pack pipelines if active
        if (shaderPackTerrainPipeline != null) {
            shaderPackTerrainPipeline.cleanUp();
            shaderPackTerrainPipeline = null;
        }
        if (shaderPackWaterPipeline != null) {
            shaderPackWaterPipeline.cleanUp();
            shaderPackWaterPipeline = null;
        }
        // Restore original before cleanup if needed
        if (originalTerrainShader != null) {
            terrainShader = originalTerrainShader;
            originalTerrainShader = null;
        }

        terrainShaderEarlyZ.cleanUp();
        terrainShader.cleanUp();
        fastBlitPipeline.cleanUp();
        cloudsPipeline.cleanUp();
        overlayPipeline.cleanUp();
    }
}
