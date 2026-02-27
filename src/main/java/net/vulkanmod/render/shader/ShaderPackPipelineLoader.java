package net.vulkanmod.render.shader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.pack.ShaderPack;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.shader.bsl.BSLShaderPackLoader;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bridges shader packs to VulkanMod's real Vulkan pipeline system.
 * 
 * Reads GLSL shaders from the shader pack directory, compiles them to SPIR-V
 * using SPIRVUtils (glslc), creates real GraphicsPipeline objects using
 * Pipeline.Builder, and applies them as terrain pipeline replacements.
 * 
 * Also detects BSL-format (OptiFine/Iris) shader packs and routes them
 * through the BSL compatibility layer.
 */
public class ShaderPackPipelineLoader {

    private static ShaderPackPipelineLoader instance;

    private ShaderPackPipelineLoader() {}

    public static ShaderPackPipelineLoader getInstance() {
        if (instance == null) {
            instance = new ShaderPackPipelineLoader();
        }
        return instance;
    }

    /**
     * Load and apply a shader pack's pipelines as real Vulkan pipelines.
     * Detects BSL-format packs and routes them through the BSL compatibility layer.
     * 
     * @param pack The shader pack to apply
     * @return true if at least one pipeline was successfully applied
     */
    public boolean applyShaderPack(ShaderPack pack) {
        if (pack == null) {
            restoreDefaults();
            return false;
        }

        Path packDir = pack.getPackPath();

        // Check if this is a BSL-format (OptiFine/Iris) shader pack
        if (BSLShaderPackLoader.isBSLFormat(packDir)) {
            System.out.println("[ShaderPackPipelineLoader] Detected BSL-format shader pack, routing to BSL loader");
            return BSLShaderPackLoader.loadAndApply(packDir);
        }

        boolean anyApplied = false;

        for (ShaderPack.PipelineDefinition pipelineDef : pack.getPipelines()) {
            String target = pipelineDef.getTarget();
            if (target == null || target.isEmpty()) {
                System.out.println("[ShaderPackPipelineLoader] Pipeline '" + pipelineDef.getName() + "' has no target, skipping");
                continue;
            }

            try {
                GraphicsPipeline pipeline = loadPipeline(packDir, pipelineDef);
                if (pipeline != null) {
                    applyPipelineToTarget(target, pipeline);
                    anyApplied = true;
                    System.out.println("[ShaderPackPipelineLoader] Applied pipeline '" + pipelineDef.getName() + "' to target '" + target + "'");
                }
            } catch (Exception e) {
                System.err.println("[ShaderPackPipelineLoader] Failed to load pipeline '" + pipelineDef.getName() + "': " + e.getMessage());
                e.printStackTrace();
            }
        }

        return anyApplied;
    }

    /**
     * Restore all pipelines to their defaults (remove shader pack overrides).
     */
    public void restoreDefaults() {
        PipelineManager.restoreDefaultPipelines();
        System.out.println("[ShaderPackPipelineLoader] Restored default pipelines");
    }

    /**
     * Load a single pipeline from a shader pack definition.
     */
    private GraphicsPipeline loadPipeline(Path packDir, ShaderPack.PipelineDefinition pipelineDef) throws Exception {
        String configPath = pipelineDef.getConfigPath();
        String vertexPath = pipelineDef.getVertexShader();
        String fragmentPath = pipelineDef.getFragmentShader();

        // Determine vertex format based on target
        VertexFormat vertexFormat = getVertexFormatForTarget(pipelineDef.getTarget());

        // Read pipeline JSON config
        Path configFile = packDir.resolve(configPath);
        if (!Files.exists(configFile)) {
            throw new Exception("Pipeline config not found: " + configFile);
        }
        String configJson = Files.readString(configFile, StandardCharsets.UTF_8);
        JsonObject config = JsonParser.parseString(configJson).getAsJsonObject();

        // Read GLSL sources
        String vertexSource = readShaderSource(packDir, vertexPath);
        String fragmentSource = readShaderSource(packDir, fragmentPath);

        if (vertexSource == null) {
            throw new Exception("Vertex shader not found: " + vertexPath);
        }
        if (fragmentSource == null) {
            throw new Exception("Fragment shader not found: " + fragmentPath);
        }

        System.out.println("[ShaderPackPipelineLoader] Compiling shaders for '" + pipelineDef.getName() + "'...");

        // Create Pipeline.Builder with proper vertex format
        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, pipelineDef.getName());

        // Parse UBO/sampler/push constant bindings from JSON config
        pipelineBuilder.parseBindings(config);

        // Compile GLSL to SPIR-V using glslc
        SPIRVUtils.SPIRV vertSpirv = SPIRVUtils.compileShader(
                pipelineDef.getName() + ".vsh", vertexSource, SPIRVUtils.ShaderKind.VERTEX_SHADER);
        SPIRVUtils.SPIRV fragSpirv = SPIRVUtils.compileShader(
                pipelineDef.getName() + ".fsh", fragmentSource, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

        pipelineBuilder.setVertShaderSPIRV(vertSpirv);
        pipelineBuilder.setFragShaderSPIRV(fragSpirv);

        // Create the real Vulkan graphics pipeline
        GraphicsPipeline pipeline = pipelineBuilder.createGraphicsPipeline();

        // Mark UBO buffers to use the global buffer (same as built-in pipelines)
        for (var buffer : pipeline.getBuffers()) {
            buffer.setUseGlobalBuffer(true);
        }

        System.out.println("[ShaderPackPipelineLoader] Successfully created pipeline '" + pipelineDef.getName() + "'");
        return pipeline;
    }

    /**
     * Read a GLSL shader source file from the pack directory.
     */
    private String readShaderSource(Path packDir, String shaderPath) {
        Path file = packDir.resolve(shaderPath);
        if (Files.exists(file)) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[ShaderPackPipelineLoader] Failed to read " + file + ": " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get the VertexFormat for a given pipeline target.
     */
    private VertexFormat getVertexFormatForTarget(String target) {
        return switch (target) {
            case "terrain", "terrain_earlyZ" -> CustomVertexFormat.COMPRESSED_TERRAIN;
            default -> CustomVertexFormat.COMPRESSED_TERRAIN;
        };
    }

    /**
     * Apply a compiled pipeline to the specified target in PipelineManager.
     */
    private void applyPipelineToTarget(String target, GraphicsPipeline pipeline) {
        switch (target) {
            case "terrain" -> PipelineManager.applyShaderPackTerrainPipeline(pipeline);
            default -> System.out.println("[ShaderPackPipelineLoader] Unknown target: " + target);
        }
    }
}
