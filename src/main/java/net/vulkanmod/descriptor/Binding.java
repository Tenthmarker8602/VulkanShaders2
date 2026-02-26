package net.vulkanmod.descriptor;

/**
 * Represents a single binding in a descriptor set.
 * Maps a binding point to a resource (uniform buffer, storage buffer, sampler, etc.).
 */
public class Binding {
    
    private final int bindingIndex;
    private final DescriptorType type;
    private final String name;
    private final int arraySize; // For array descriptors
    
    private Object resource; // The actual GPU resource (buffer, texture, sampler, etc.)
    
    public Binding(int bindingIndex, DescriptorType type, String name) {
        this(bindingIndex, type, name, 1);
    }
    
    public Binding(int bindingIndex, DescriptorType type, String name, int arraySize) {
        this.bindingIndex = bindingIndex;
        this.type = type;
        this.name = name;
        this.arraySize = arraySize;
    }
    
    public int getBindingIndex() {
        return bindingIndex;
    }
    
    public DescriptorType getType() {
        return type;
    }
    
    public String getName() {
        return name;
    }
    
    public int getArraySize() {
        return arraySize;
    }
    
    /**
     * Set the GPU resource for this binding.
     *
     * @param resource The resource (VulkanBuffer, VulkanTexture, etc.)
     */
    public void setResource(Object resource) {
        this.resource = resource;
    }
    
    /**
     * Get the GPU resource for this binding.
     *
     * @return The resource or null if not set
     */
    public Object getResource() {
        return resource;
    }
    
    /**
     * Check if this binding has a resource assigned.
     *
     * @return true if resource is set
     */
    public boolean hasResource() {
        return resource != null;
    }
    
    @Override
    public String toString() {
        return "Binding{" +
                "bindingIndex=" + bindingIndex +
                ", type=" + type +
                ", name='" + name + '\'' +
                ", arraySize=" + arraySize +
                ", hasResource=" + (resource != null) +
                '}';
    }
}
