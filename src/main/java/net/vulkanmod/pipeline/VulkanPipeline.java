package net.vulkanmod.pipeline;

import net.vulkanmod.shader.ShaderModule;
import net.vulkanmod.descriptor.DescriptorSetLayout;

import java.util.*;

/**
 * Represents a compiled Vulkan pipeline ready for rendering.
 * Supports graphics, compute, and ray tracing pipeline types.
 */
public class VulkanPipeline {
    
    private final String name;
    private final PipelineType type;
    private final List<ShaderModule> shaderModules;
    private final List<DescriptorSetLayout> descriptorSetLayouts;
    private final Map<String, Object> pipelineSettings;
    
    private long vkPipeline; // Vulkan pipeline handle (populated after creation)
    private long vkPipelineLayout; // Vulkan pipeline layout handle
    
    public VulkanPipeline(String name, PipelineType type) {
        this.name = name;
        this.type = type;
        this.shaderModules = new ArrayList<>();
        this.descriptorSetLayouts = new ArrayList<>();
        this.pipelineSettings = new HashMap<>();
        this.vkPipeline = 0;
        this.vkPipelineLayout = 0;
    }
    
    public String getName() {
        return name;
    }
    
    public PipelineType getType() {
        return type;
    }
    
    public void addShaderModule(ShaderModule module) {
        if (module == null) {
            throw new IllegalArgumentException("Shader module cannot be null");
        }
        this.shaderModules.add(module);
    }
    
    public List<ShaderModule> getShaderModules() {
        return Collections.unmodifiableList(shaderModules);
    }
    
    public void addDescriptorSetLayout(DescriptorSetLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("Descriptor set layout cannot be null");
        }
        this.descriptorSetLayouts.add(layout);
    }
    
    public List<DescriptorSetLayout> getDescriptorSetLayouts() {
        return Collections.unmodifiableList(descriptorSetLayouts);
    }
    
    public void setSetting(String key, Object value) {
        pipelineSettings.put(key, value);
    }
    
    public Object getSetting(String key) {
        return pipelineSettings.get(key);
    }
    
    public Object getSetting(String key, Object defaultValue) {
        return pipelineSettings.getOrDefault(key, defaultValue);
    }
    
    // Vulkan handle management
    public void setVkPipeline(long handle) {
        this.vkPipeline = handle;
    }
    
    public long getVkPipeline() {
        return vkPipeline;
    }
    
    public void setVkPipelineLayout(long handle) {
        this.vkPipelineLayout = handle;
    }
    
    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }
    
    public boolean isCreated() {
        return vkPipeline != 0 && vkPipelineLayout != 0;
    }
    
    @Override
    public String toString() {
        return "VulkanPipeline{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", shaderModules=" + shaderModules.size() +
                ", descriptorSetLayouts=" + descriptorSetLayouts.size() +
                '}';
    }
}
