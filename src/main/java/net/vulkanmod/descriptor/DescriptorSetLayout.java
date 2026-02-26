package net.vulkanmod.descriptor;

import java.util.*;

/**
 * Layout definition for a descriptor set.
 * Defines the bindings available in a descriptor set.
 */
public class DescriptorSetLayout {
    
    private final int setIndex;
    private final Map<Integer, Binding> bindings;
    private long vkLayout; // Vulkan descriptor set layout handle
    
    public DescriptorSetLayout(int setIndex) {
        this.setIndex = setIndex;
        this.bindings = new TreeMap<>();
        this.vkLayout = 0;
    }
    
    public int getSetIndex() {
        return setIndex;
    }
    
    /**
     * Add a binding to this descriptor set layout.
     *
     * @param binding The binding to add
     */
    public void addBinding(Binding binding) {
        if (bindings.containsKey(binding.getBindingIndex())) {
            throw new IllegalArgumentException("Binding " + binding.getBindingIndex() + " already exists in set " + setIndex);
        }
        bindings.put(binding.getBindingIndex(), binding);
    }
    
    /**
     * Get a binding by index.
     *
     * @param index The binding index
     * @return The Binding or null if not found
     */
    public Binding getBinding(int index) {
        return bindings.get(index);
    }
    
    /**
     * Get a binding by name.
     *
     * @param name The binding name
     * @return The Binding or null if not found
     */
    public Binding getBindingByName(String name) {
        return bindings.values().stream()
                .filter(b -> b.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get all bindings in this descriptor set.
     *
     * @return Unmodifiable collection of bindings
     */
    public Collection<Binding> getBindings() {
        return Collections.unmodifiableCollection(bindings.values());
    }
    
    /**
     * Get the number of bindings.
     *
     * @return Number of bindings
     */
    public int getBindingCount() {
        return bindings.size();
    }
    
    /**
     * Set the Vulkan descriptor set layout handle.
     *
     * @param handle The VkDescriptorSetLayout handle
     */
    public void setVkLayout(long handle) {
        this.vkLayout = handle;
    }
    
    /**
     * Get the Vulkan descriptor set layout handle.
     *
     * @return The VkDescriptorSetLayout handle or 0 if not created
     */
    public long getVkLayout() {
        return vkLayout;
    }
    
    /**
     * Check if the Vulkan layout has been created.
     *
     * @return true if vkLayout is not 0
     */
    public boolean isCreated() {
        return vkLayout != 0;
    }
    
    @Override
    public String toString() {
        return "DescriptorSetLayout{" +
                "setIndex=" + setIndex +
                ", bindings=" + bindings.size() +
                ", created=" + (vkLayout != 0) +
                '}';
    }
}
