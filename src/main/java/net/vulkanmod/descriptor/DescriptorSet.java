package net.vulkanmod.descriptor;

import java.util.*;

/**
 * Represents a runtime descriptor set instance.
 * Contains actual resources bound to descriptors.
 */
public class DescriptorSet {
    
    private final DescriptorSetLayout layout;
    private final Map<Integer, Object> resources;
    private long vkDescriptorSet; // Vulkan descriptor set handle
    
    public DescriptorSet(DescriptorSetLayout layout) {
        this.layout = layout;
        this.resources = new HashMap<>();
        this.vkDescriptorSet = 0;
    }
    
    public DescriptorSetLayout getLayout() {
        return layout;
    }
    
    /**
     * Bind a resource to a descriptor binding.
     *
     * @param bindingIndex The binding index
     * @param resource     The resource to bind
     */
    public void bindResource(int bindingIndex, Object resource) {
        Binding binding = layout.getBinding(bindingIndex);
        if (binding == null) {
            throw new IllegalArgumentException("Binding " + bindingIndex + " not defined in layout");
        }
        
        resources.put(bindingIndex, resource);
        binding.setResource(resource);
    }
    
    /**
     * Bind a resource to a descriptor binding by name.
     *
     * @param bindingName The binding name
     * @param resource    The resource to bind
     */
    public void bindResourceByName(String bindingName, Object resource) {
        Binding binding = layout.getBindingByName(bindingName);
        if (binding == null) {
            throw new IllegalArgumentException("Binding '" + bindingName + "' not defined in layout");
        }
        
        bindResource(binding.getBindingIndex(), resource);
    }
    
    /**
     * Get a bound resource by binding index.
     *
     * @param bindingIndex The binding index
     * @return The resource or null if not bound
     */
    public Object getResource(int bindingIndex) {
        return resources.get(bindingIndex);
    }
    
    /**
     * Get a bound resource by binding name.
     *
     * @param bindingName The binding name
     * @return The resource or null if not bound
     */
    public Object getResourceByName(String bindingName) {
        Binding binding = layout.getBindingByName(bindingName);
        if (binding == null) {
            return null;
        }
        return resources.get(binding.getBindingIndex());
    }
    
    /**
     * Get all bound resources.
     *
     * @return Unmodifiable map of binding indices to resources
     */
    public Map<Integer, Object> getAllResources() {
        return Collections.unmodifiableMap(resources);
    }
    
    /**
     * Check if all required bindings have resources.
     *
     * @return true if all layout bindings have resources
     */
    public boolean isComplete() {
        for (Binding binding : layout.getBindings()) {
            if (!resources.containsKey(binding.getBindingIndex())) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Set the Vulkan descriptor set handle.
     *
     * @param handle The VkDescriptorSet handle
     */
    public void setVkDescriptorSet(long handle) {
        this.vkDescriptorSet = handle;
    }
    
    /**
     * Get the Vulkan descriptor set handle.
     *
     * @return The VkDescriptorSet handle or 0 if not created
     */
    public long getVkDescriptorSet() {
        return vkDescriptorSet;
    }
    
    /**
     * Check if the Vulkan descriptor set has been created.
     *
     * @return true if vkDescriptorSet is not 0
     */
    public boolean isCreated() {
        return vkDescriptorSet != 0;
    }
    
    @Override
    public String toString() {
        return "DescriptorSet{" +
                "layout=" + layout.getSetIndex() +
                ", boundResources=" + resources.size() +
                ", complete=" + isComplete() +
                '}';
    }
}
