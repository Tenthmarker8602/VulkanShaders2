package net.vulkanmod.pipeline;

import net.vulkanmod.shader.ShaderModule;
import java.util.*;

/**
 * Manages Vulkan pipeline creation, caching, and lifecycle.
 * Coordinates with shader and descriptor managers to construct complete pipelines.
 */
public class PipelineManager {
    
    private final Map<String, VulkanPipeline> pipelines;
    private final Map<String, PipelineBuilder> builders;
    
    public PipelineManager() {
        this.pipelines = new HashMap<>();
        this.builders = new HashMap<>();
    }
    
    /**
     * Create a new pipeline builder for constructing pipelines step by step.
     *
     * @param name The pipeline name
     * @param type The pipeline type (graphics, compute, or ray tracing)
     * @return A PipelineBuilder instance
     */
    public PipelineBuilder createPipeline(String name, PipelineType type) {
        if (pipelines.containsKey(name)) {
            throw new IllegalArgumentException("Pipeline '" + name + "' already exists");
        }
        
        PipelineBuilder builder = new PipelineBuilder(name, type, this);
        builders.put(name, builder);
        return builder;
    }
    
    /**
     * Register a built pipeline in the manager.
     * Called by PipelineBuilder when build() is finished.
     *
     * @param pipeline The pipeline to register
     */
    protected void registerPipeline(VulkanPipeline pipeline) {
        pipelines.put(pipeline.getName(), pipeline);
        builders.remove(pipeline.getName());
    }
    
    /**
     * Get a registered pipeline by name.
     *
     * @param name The pipeline name
     * @return The VulkanPipeline or null if not found
     */
    public VulkanPipeline getPipeline(String name) {
        return pipelines.get(name);
    }
    
    /**
     * Get all registered pipelines.
     *
     * @return Unmodifiable map of all pipelines
     */
    public Map<String, VulkanPipeline> getAllPipelines() {
        return Collections.unmodifiableMap(pipelines);
    }
    
    /**
     * Check if a pipeline exists.
     *
     * @param name The pipeline name
     * @return true if pipeline exists
     */
    public boolean hasPipeline(String name) {
        return pipelines.containsKey(name);
    }
    
    /**
     * Remove a pipeline from the manager.
     *
     * @param name The pipeline name
     * @return The removed pipeline or null if not found
     */
    public VulkanPipeline removePipeline(String name) {
        builders.remove(name);
        return pipelines.remove(name);
    }
    
    /**
     * Clean up all pipelines and resources.
     */
    public void cleanup() {
        pipelines.clear();
        builders.clear();
    }
    
    /**
     * Builder class for fluent pipeline construction.
     */
    public static class PipelineBuilder {
        private final String name;
        private final PipelineType type;
        private final PipelineManager manager;
        private final VulkanPipeline pipeline;
        
        PipelineBuilder(String name, PipelineType type, PipelineManager manager) {
            this.name = name;
            this.type = type;
            this.manager = manager;
            this.pipeline = new VulkanPipeline(name, type);
        }
        
        /**
         * Add a shader module to this pipeline.
         *
         * @param shader The shader module to add
         * @return This builder for chaining
         */
        public PipelineBuilder withShader(ShaderModule shader) {
            pipeline.addShaderModule(shader);
            return this;
        }
        
        /**
         * Add multiple shader modules to this pipeline.
         *
         * @param shaders The shader modules to add
         * @return This builder for chaining
         */
        public PipelineBuilder withShaders(ShaderModule... shaders) {
            for (ShaderModule shader : shaders) {
                pipeline.addShaderModule(shader);
            }
            return this;
        }
        
        /**
         * Add a descriptor set layout to this pipeline.
         *
         * @param layout The descriptor set layout
         * @return This builder for chaining
         */
        public PipelineBuilder withDescriptorSetLayout(net.vulkanmod.descriptor.DescriptorSetLayout layout) {
            pipeline.addDescriptorSetLayout(layout);
            return this;
        }
        
        /**
         * Add a pipeline setting.
         *
         * @param key The setting key
         * @param value The setting value
         * @return This builder for chaining
         */
        public PipelineBuilder withSetting(String key, Object value) {
            pipeline.setSetting(key, value);
            return this;
        }
        
        /**
         * Build and register the pipeline.
         *
         * @return The created VulkanPipeline
         */
        public VulkanPipeline build() {
            // Validate pipeline configuration
            if (pipeline.getShaderModules().isEmpty()) {
                throw new IllegalStateException("Pipeline '" + name + "' must have at least one shader");
            }
            
            // Register the pipeline
            manager.registerPipeline(pipeline);
            
            return pipeline;
        }
    }
}
