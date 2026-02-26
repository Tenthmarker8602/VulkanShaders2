package net.vulkanmod.resource;

/**
 * Base interface for GPU resources.
 * All buffer and texture resources implement this interface.
 */
public interface GpuResource {
    
    /**
     * Get the resource name.
     *
     * @return The name identifier
     */
    String getName();
    
    /**
     * Get the Vulkan resource handle.
     *
     * @return The VkBuffer or VkImage handle
     */
    long getVkHandle();
    
    /**
     * Get the size of this resource in bytes.
     *
     * @return The size in bytes
     */
    long getSize();
    
    /**
     * Check if this resource is valid (has a non-zero Vulkan handle).
     *
     * @return true if resource is valid
     */
    boolean isValid();
    
    /**
     * Clean up this resource and release GPU memory.
     */
    void cleanup();
}
