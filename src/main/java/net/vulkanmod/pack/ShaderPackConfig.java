package net.vulkanmod.pack;

import java.util.List;

/**
 * JSON configuration for a Vulkan shader pack.
 * Contains metadata and pipeline definitions.
 */
public class ShaderPackConfig {
    
    public String name;
    public String version;
    public String description;
    public boolean raytracing;
    public List<PipelineConfig> pipelines;
    
    public static class PipelineConfig {
        public String name;
        public String type; // "graphics", "compute", or "ray_tracing"
        public String target; // Target pipeline to replace: "terrain", "clouds", etc.
        public String vertex;
        public String fragment;
        public String compute;
        public String config; // Path to VulkanMod-format pipeline JSON config
        public List<DescriptorSetConfig> descriptor_sets;
        public List<String> extensions; // Optional Vulkan extensions
    }
    
    public static class DescriptorSetConfig {
        public int set;
        public List<BindingConfig> bindings;
    }
    
    public static class BindingConfig {
        public int binding;
        public String type;
        public String name;
        public int array_size;
        
        public BindingConfig() {
            this.array_size = 1;
        }
    }
}
