package net.vulkanmod.descriptor;

/**
 * Enumeration of supported descriptor types in Vulkan.
 */
public enum DescriptorType {
    UNIFORM_BUFFER("uniform_buffer", "VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER"),
    STORAGE_BUFFER("storage_buffer", "VK_DESCRIPTOR_TYPE_STORAGE_BUFFER"),
    SAMPLER2D("sampler2D", "VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER"),
    SAMPLER3D("sampler3D", "VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER"),
    IMAGE2D("image2D", "VK_DESCRIPTOR_TYPE_STORAGE_IMAGE"),
    IMAGE3D("image3D", "VK_DESCRIPTOR_TYPE_STORAGE_IMAGE"),
    UNIFORM_TEXEL_BUFFER("uniform_texel_buffer", "VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER"),
    STORAGE_TEXEL_BUFFER("storage_texel_buffer", "VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER"),
    INPUT_ATTACHMENT("input_attachment", "VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT");
    
    private final String jsonName;
    private final String vulkanName;
    
    DescriptorType(String jsonName, String vulkanName) {
        this.jsonName = jsonName;
        this.vulkanName = vulkanName;
    }
    
    public String getJsonName() {
        return jsonName;
    }
    
    public String getVulkanName() {
        return vulkanName;
    }
    
    public static DescriptorType fromJsonName(String name) {
        for (DescriptorType type : values()) {
            if (type.jsonName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown descriptor type: " + name);
    }
}
