package net.vulkanmod.resource;

import java.util.*;

/**
 * Manages GPU resource creation, caching, and cleanup.
 * Coordinates with Vulkan context to allocate buffers and textures.
 */
public class ResourceLoader {
    
    private final Map<String, GpuResource> resources;
    private final List<GpuResource> allocatedResources;
    
    public ResourceLoader() {
        this.resources = new HashMap<>();
        this.allocatedResources = new ArrayList<>();
    }
    
    /**
     * Create a new buffer resource.
     *
     * @param name The resource name
     * @param type The buffer type
     * @param size The size in bytes
     * @return The created BufferResource
     */
    public BufferResource createBuffer(String name, BufferResource.BufferType type, long size) {
        if (resources.containsKey(name)) {
            throw new IllegalArgumentException("Resource '" + name + "' already exists");
        }
        
        BufferResource buffer = new BufferResource(name, type, size);
        resources.put(name, buffer);
        allocatedResources.add(buffer);
        
        return buffer;
    }
    
    /**
     * Create a new texture resource.
     *
     * @param name   The resource name
     * @param type   The texture type
     * @param format The texture format
     * @param width  The width in pixels
     * @param height The height in pixels
     * @return The created TextureResource
     */
    public TextureResource createTexture(String name, TextureResource.TextureType type,
                                        TextureResource.Format format, int width, int height) {
        if (resources.containsKey(name)) {
            throw new IllegalArgumentException("Resource '" + name + "' already exists");
        }
        
        TextureResource texture = new TextureResource(name, type, format, width, height);
        resources.put(name, texture);
        allocatedResources.add(texture);
        
        return texture;
    }
    
    /**
     * Create a new 3D texture resource.
     *
     * @param name   The resource name
     * @param type   The texture type
     * @param format The texture format
     * @param width  The width in pixels
     * @param height The height in pixels
     * @param depth  The depth in pixels
     * @return The created TextureResource
     */
    public TextureResource createTexture3D(String name, TextureResource.TextureType type,
                                          TextureResource.Format format, int width, int height, int depth) {
        if (resources.containsKey(name)) {
            throw new IllegalArgumentException("Resource '" + name + "' already exists");
        }
        
        TextureResource texture = new TextureResource(name, type, format, width, height, depth);
        resources.put(name, texture);
        allocatedResources.add(texture);
        
        return texture;
    }
    
    /**
     * Get a resource by name.
     *
     * @param name The resource name
     * @return The GpuResource or null if not found
     */
    public GpuResource getResource(String name) {
        return resources.get(name);
    }
    
    /**
     * Get a resource by name, cast to BufferResource.
     *
     * @param name The resource name
     * @return The BufferResource or null
     */
    public BufferResource getBuffer(String name) {
        GpuResource resource = resources.get(name);
        return resource instanceof BufferResource ? (BufferResource) resource : null;
    }
    
    /**
     * Get a resource by name, cast to TextureResource.
     *
     * @param name The resource name
     * @return The TextureResource or null
     */
    public TextureResource getTexture(String name) {
        GpuResource resource = resources.get(name);
        return resource instanceof TextureResource ? (TextureResource) resource : null;
    }
    
    /**
     * Get all registered resources.
     *
     * @return Unmodifiable map of all resources
     */
    public Map<String, GpuResource> getAllResources() {
        return Collections.unmodifiableMap(resources);
    }
    
    /**
     * Get the number of allocated resources.
     *
     * @return Number of resources
     */
    public int getResourceCount() {
        return resources.size();
    }
    
    /**
     * Remove a resource from the manager.
     *
     * @param name The resource name
     * @return The removed resource or null if not found
     */
    public GpuResource removeResource(String name) {
        GpuResource resource = resources.remove(name);
        if (resource != null) {
            allocatedResources.remove(resource);
        }
        return resource;
    }
    
    /**
     * Get all allocated resources (for batch operations).
     *
     * @return Unmodifiable list of all allocated resources
     */
    public List<GpuResource> getAllAllocatedResources() {
        return Collections.unmodifiableList(allocatedResources);
    }
    
    /**
     * Clean up all resources.
     */
    public void cleanup() {
        for (GpuResource resource : allocatedResources) {
            resource.cleanup();
        }
        resources.clear();
        allocatedResources.clear();
    }
}
