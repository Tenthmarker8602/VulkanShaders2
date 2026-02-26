package net.vulkanmod.resource;

/**
 * Represents a Vulkan buffer resource.
 * Can be used as uniform buffer, storage buffer, or other buffer types.
 */
public class BufferResource implements GpuResource {
    
    public enum BufferType {
        UNIFORM_BUFFER,
        STORAGE_BUFFER,
        VERTEX_BUFFER,
        INDEX_BUFFER,
        INDIRECT_BUFFER,
        TRANSFER_SRC,
        TRANSFER_DST
    }
    
    private final String name;
    private final BufferType type;
    private long size;
    private long vkBuffer;
    private long vkDeviceMemory;
    private boolean dynamic; // Can be updated frequently
    
    public BufferResource(String name, BufferType type, long size) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.vkBuffer = 0;
        this.vkDeviceMemory = 0;
        this.dynamic = false;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public BufferType getBufferType() {
        return type;
    }
    
    @Override
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    @Override
    public long getVkHandle() {
        return vkBuffer;
    }
    
    public void setVkBuffer(long handle) {
        this.vkBuffer = handle;
    }
    
    public long getVkDeviceMemory() {
        return vkDeviceMemory;
    }
    
    public void setVkDeviceMemory(long handle) {
        this.vkDeviceMemory = handle;
    }
    
    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }
    
    public boolean isDynamic() {
        return dynamic;
    }
    
    @Override
    public boolean isValid() {
        return vkBuffer != 0 && vkDeviceMemory != 0;
    }
    
    @Override
    public void cleanup() {
        // Actual cleanup would be done by ResourceLoader or VulkanContext
        vkBuffer = 0;
        vkDeviceMemory = 0;
    }
    
    @Override
    public String toString() {
        return "BufferResource{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", size=" + size +
                ", valid=" + isValid() +
                '}';
    }
}
