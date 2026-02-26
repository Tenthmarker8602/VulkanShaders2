package net.vulkanmod.descriptor;

import java.util.*;

/**
 * Manages descriptor set layouts and instances.
 * Coordinates allocation and binding of GPU resources to descriptors.
 */
public class DescriptorManager {
    
    private final Map<String, DescriptorSetLayout> layouts;
    private final Map<String, List<DescriptorSet>> descriptorSets;
    
    public DescriptorManager() {
        this.layouts = new HashMap<>();
        this.descriptorSets = new HashMap<>();
    }
    
    /**
     * Create a new descriptor set layout.
     *
     * @param name     A unique name for this layout
     * @param setIndex The Vulkan descriptor set index
     * @return The created DescriptorSetLayout
     */
    public DescriptorSetLayout createLayout(String name, int setIndex) {
        if (layouts.containsKey(name)) {
            throw new IllegalArgumentException("Layout '" + name + "' already exists");
        }
        
        DescriptorSetLayout layout = new DescriptorSetLayout(setIndex);
        layouts.put(name, layout);
        descriptorSets.put(name, new ArrayList<>());
        
        return layout;
    }
    
    /**
     * Get a descriptor set layout by name.
     *
     * @param name The layout name
     * @return The DescriptorSetLayout or null if not found
     */
    public DescriptorSetLayout getLayout(String name) {
        return layouts.get(name);
    }
    
    /**
     * Get all descriptor set layouts.
     *
     * @return Unmodifiable map of all layouts
     */
    public Map<String, DescriptorSetLayout> getAllLayouts() {
        return Collections.unmodifiableMap(layouts);
    }
    
    /**
     * Allocate a descriptor set from a layout.
     *
     * @param layoutName The name of the layout to use
     * @return The created DescriptorSet
     */
    public DescriptorSet allocateDescriptorSet(String layoutName) {
        DescriptorSetLayout layout = layouts.get(layoutName);
        if (layout == null) {
            throw new IllegalArgumentException("Layout '" + layoutName + "' not found");
        }
        
        DescriptorSet set = new DescriptorSet(layout);
        descriptorSets.get(layoutName).add(set);
        
        return set;
    }
    
    /**
     * Allocate multiple descriptor sets from a layout.
     *
     * @param layoutName The name of the layout to use
     * @param count      Number of descriptor sets to allocate
     * @return List of created DescriptorSets
     */
    public List<DescriptorSet> allocateDescriptorSets(String layoutName, int count) {
        List<DescriptorSet> allocated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            allocated.add(allocateDescriptorSet(layoutName));
        }
        return allocated;
    }
    
    /**
     * Get all descriptor sets for a layout.
     *
     * @param layoutName The layout name
     * @return Unmodifiable list of descriptor sets
     */
    public List<DescriptorSet> getDescriptorSets(String layoutName) {
        List<DescriptorSet> sets = descriptorSets.get(layoutName);
        if (sets == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(sets);
    }
    
    /**
     * Get the number of allocated descriptor sets for a layout.
     *
     * @param layoutName The layout name
     * @return Number of allocated sets
     */
    public int getDescriptorSetCount(String layoutName) {
        List<DescriptorSet> sets = descriptorSets.get(layoutName);
        return sets == null ? 0 : sets.size();
    }
    
    /**
     * Create a layout and immediately add bindings to it.
     *
     * @param name     A unique name for this layout
     * @param setIndex The Vulkan descriptor set index
     * @param bindings Array of bindings to add
     * @return The created DescriptorSetLayout
     */
    public DescriptorSetLayout createLayoutWithBindings(String name, int setIndex, Binding... bindings) {
        DescriptorSetLayout layout = createLayout(name, setIndex);
        for (Binding binding : bindings) {
            layout.addBinding(binding);
        }
        return layout;
    }
    
    /**
     * Clean up all layouts and descriptor sets.
     */
    public void cleanup() {
        layouts.clear();
        descriptorSets.clear();
    }
}
