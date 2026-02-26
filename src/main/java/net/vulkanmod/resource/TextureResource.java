package net.vulkanmod.resource;

/**
 * Represents a Vulkan image/texture resource.
 * Supports 2D, 3D, and cube map textures.
 */
public class TextureResource implements GpuResource {
    
    public enum TextureType {
        TEXTURE_2D,
        TEXTURE_3D,
        TEXTURE_CUBE,
        TEXTURE_2D_ARRAY
    }
    
    public enum Format {
        R8_UNORM,
        RGBA8_UNORM,
        RGBA16_FLOAT,
        RGBA32_FLOAT,
        DEPTH32_FLOAT
    }
    
    private final String name;
    private final TextureType type;
    private final Format format;
    private int width;
    private int height;
    private int depth; // For 3D textures
    private int mipLevels;
    
    private long vkImage;
    private long vkDeviceMemory;
    private long vkImageView;
    private long vkSampler; // For sampling in shaders
    
    public TextureResource(String name, TextureType type, Format format, int width, int height) {
        this.name = name;
        this.type = type;
        this.format = format;
        this.width = width;
        this.height = height;
        this.depth = 1;
        this.mipLevels = 1;
        this.vkImage = 0;
        this.vkDeviceMemory = 0;
        this.vkImageView = 0;
        this.vkSampler = 0;
    }
    
    public TextureResource(String name, TextureType type, Format format, int width, int height, int depth) {
        this(name, type, format, width, height);
        this.depth = depth;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public TextureType getTextureType() {
        return type;
    }
    
    public Format getFormat() {
        return format;
    }
    
    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public int getDepth() {
        return depth;
    }
    
    public void setDepth(int depth) {
        this.depth = depth;
    }
    
    public int getMipLevels() {
        return mipLevels;
    }
    
    public void setMipLevels(int levels) {
        this.mipLevels = levels;
    }
    
    @Override
    public long getVkHandle() {
        return vkImage;
    }
    
    public void setVkImage(long handle) {
        this.vkImage = handle;
    }
    
    public long getVkDeviceMemory() {
        return vkDeviceMemory;
    }
    
    public void setVkDeviceMemory(long handle) {
        this.vkDeviceMemory = handle;
    }
    
    public long getVkImageView() {
        return vkImageView;
    }
    
    public void setVkImageView(long handle) {
        this.vkImageView = handle;
    }
    
    public long getVkSampler() {
        return vkSampler;
    }
    
    public void setVkSampler(long handle) {
        this.vkSampler = handle;
    }
    
    @Override
    public long getSize() {
        // Approximate size in bytes
        return (long) width * height * depth * getBytesPerPixel(format);
    }
    
    private int getBytesPerPixel(Format format) {
        return switch (format) {
            case R8_UNORM -> 1;
            case RGBA8_UNORM -> 4;
            case RGBA16_FLOAT -> 8;
            case RGBA32_FLOAT -> 16;
            case DEPTH32_FLOAT -> 4;
        };
    }
    
    @Override
    public boolean isValid() {
        return vkImage != 0 && vkDeviceMemory != 0 && vkImageView != 0;
    }
    
    @Override
    public void cleanup() {
        vkImage = 0;
        vkDeviceMemory = 0;
        vkImageView = 0;
        vkSampler = 0;
    }
    
    @Override
    public String toString() {
        return "TextureResource{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", format=" + format +
                ", resolution=" + width + "x" + height + "x" + depth +
                ", valid=" + isValid() +
                '}';
    }
}
