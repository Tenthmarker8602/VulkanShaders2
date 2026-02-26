package net.vulkanmod;

import net.vulkanmod.descriptor.*;
import net.vulkanmod.pack.ShaderPack;
import net.vulkanmod.pack.ShaderPackLoader;
import net.vulkanmod.pipeline.PipelineManager;
import net.vulkanmod.pipeline.PipelineType;
import net.vulkanmod.resource.ResourceLoader;
import net.vulkanmod.shader.ShaderCompiler;
import net.vulkanmod.shader.ShaderLoader;
import net.vulkanmod.shader.ShaderModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Main coordinator for the VulkanShaders system.
 * Provides a unified interface for shader loading, pipeline creation, and resource management.
 * 
 * This class acts as a facade coordinating:
 * - Shader compilation (GLSL → SPIR-V)
 * - Pipeline creation and management
 * - Descriptor set management
 * - GPU resource allocation
 */
public class VulkanShaders {
    
    private static VulkanShaders instance;
    
    private final ShaderLoader shaderLoader;
    private final ShaderPackLoader packLoader;
    private final PipelineManager pipelineManager;
    private final DescriptorManager descriptorManager;
    private final ResourceLoader resourceLoader;
    
    private ShaderPack currentShaderPack;
    private boolean initialized;
    
    private VulkanShaders(Path shadersBasePath) {
        this.shaderLoader = new ShaderLoader(shadersBasePath);
        this.packLoader = new ShaderPackLoader();
        this.pipelineManager = new PipelineManager();
        this.descriptorManager = new DescriptorManager();
        this.resourceLoader = new ResourceLoader();
        this.initialized = true;
    }
    
    /**
     * Initialize the VulkanShaders system.
     *
     * @param shadersBasePath The base path where shader files are located
     * @return The VulkanShaders instance
     */
    public static synchronized VulkanShaders initialize(Path shadersBasePath) {
        if (instance == null) {
            instance = new VulkanShaders(shadersBasePath);
        }
        return instance;
    }
    
    /**
     * Get the singleton instance (must be initialized first).
     *
     * @return The VulkanShaders instance
     * @throws IllegalStateException if not initialized
     */
    public static synchronized VulkanShaders getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VulkanShaders not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * Check if VulkanShaders is initialized.
     *
     * @return true if initialized
     */
    public static synchronized boolean isInitialized() {
        return instance != null && instance.initialized;
    }
    
    /**
     * Load a shader pack from a directory.
     *
     * @param packDirectory The directory containing pack.json
     * @return The loaded ShaderPack
     * @throws IOException if loading fails
     */
    public ShaderPack loadShaderPack(Path packDirectory) throws IOException {
        ShaderPack pack = packLoader.loadPack(packDirectory);
        this.currentShaderPack = pack;
        loadPackPipelines(pack);
        return pack;
    }
    
    /**
     * Load all pipelines from a shader pack.
     *
     * @param pack The shader pack
     */
    private void loadPackPipelines(ShaderPack pack) {
        for (ShaderPack.PipelineDefinition pipelineDef : pack.getPipelines()) {
            loadPipelineDefinition(pack, pipelineDef);
        }
    }
    
    /**
     * Load a single pipeline definition.
     *
     * @param pack          The shader pack
     * @param pipelineDef   The pipeline definition
     */
    private void loadPipelineDefinition(ShaderPack pack, ShaderPack.PipelineDefinition pipelineDef) {
        PipelineManager.PipelineBuilder builder = pipelineManager.createPipeline(
                pipelineDef.getName(),
                pipelineDef.getType()
        );
        
        // Load shaders based on pipeline type
        switch (pipelineDef.getType()) {
            case GRAPHICS -> {
                if (!pipelineDef.getVertexShader().isEmpty()) {
                    try {
                        ShaderModule vertexShader = shaderLoader.loadShader(
                                pipelineDef.getVertexShader(),
                                ShaderModule.ShaderStage.VERTEX
                        );
                        builder.withShader(vertexShader);
                    } catch (IOException e) {
                        System.err.println("Failed to load vertex shader: " + e.getMessage());
                    }
                }
                if (!pipelineDef.getFragmentShader().isEmpty()) {
                    try {
                        ShaderModule fragmentShader = shaderLoader.loadShader(
                                pipelineDef.getFragmentShader(),
                                ShaderModule.ShaderStage.FRAGMENT
                        );
                        builder.withShader(fragmentShader);
                    } catch (IOException e) {
                        System.err.println("Failed to load fragment shader: " + e.getMessage());
                    }
                }
            }
            case COMPUTE -> {
                if (!pipelineDef.getComputeShader().isEmpty()) {
                    try {
                        ShaderModule computeShader = shaderLoader.loadShader(
                                pipelineDef.getComputeShader(),
                                ShaderModule.ShaderStage.COMPUTE
                        );
                        builder.withShader(computeShader);
                    } catch (IOException e) {
                        System.err.println("Failed to load compute shader: " + e.getMessage());
                    }
                }
            }
            case RAY_TRACING -> {
                // Ray tracing shaders would be loaded here
                // Not fully implemented yet
            }
        }
        
        // Load descriptor sets
        for (ShaderPack.DescriptorSetDefinition dsDef : pipelineDef.getDescriptorSets()) {
            DescriptorSetLayout layout = descriptorManager.createLayout(
                    pipelineDef.getName() + "_ds_" + dsDef.getSetIndex(),
                    dsDef.getSetIndex()
            );
            
            for (ShaderPack.BindingDefinition bindingDef : dsDef.getBindings()) {
                DescriptorType bindingType = DescriptorType.fromJsonName(bindingDef.getType());
                Binding binding = new Binding(
                        bindingDef.getBindingIndex(),
                        bindingType,
                        bindingDef.getName(),
                        bindingDef.getArraySize()
                );
                layout.addBinding(binding);
            }
            
            builder.withDescriptorSetLayout(layout);
        }
        
        builder.build();
    }
    
    /**
     * Get the current shader pack.
     *
     * @return The current ShaderPack or null if none loaded
     */
    public ShaderPack getCurrentShaderPack() {
        return currentShaderPack;
    }
    
    /**
     * Get the pipeline manager.
     *
     * @return The PipelineManager instance
     */
    public PipelineManager getPipelineManager() {
        return pipelineManager;
    }
    
    /**
     * Get the descriptor manager.
     *
     * @return The DescriptorManager instance
     */
    public DescriptorManager getDescriptorManager() {
        return descriptorManager;
    }
    
    /**
     * Get the resource loader.
     *
     * @return The ResourceLoader instance
     */
    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }
    
    /**
     * Get the shader loader.
     *
     * @return The ShaderLoader instance
     */
    public ShaderLoader getShaderLoader() {
        return shaderLoader;
    }
    
    /**
     * Clean up all resources.
     * Call this when shutting down.
     */
    public void cleanup() {
        pipelineManager.cleanup();
        descriptorManager.cleanup();
        resourceLoader.cleanup();
        ShaderCompiler.getInstance().cleanup();
        initialized = false;
    }
    
    @Override
    public String toString() {
        return "VulkanShaders{" +
                "initialized=" + initialized +
                ", currentPack=" + (currentShaderPack != null ? currentShaderPack.getName() : "none") +
                ", pipelines=" + pipelineManager.getAllPipelines().size() +
                ", resources=" + resourceLoader.getResourceCount() +
                '}';
    }
}
