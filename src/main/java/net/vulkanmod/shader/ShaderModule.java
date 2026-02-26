package net.vulkanmod.shader;

/**
 * Represents a compiled SPIR-V shader module.
 */
public class ShaderModule {
    
    private final String name;
    private final ShaderStage stage;
    private final byte[] spirvBytecode;
    private final String glslSource;
    
    public ShaderModule(String name, ShaderStage stage, String glslSource, byte[] spirvBytecode) {
        this.name = name;
        this.stage = stage;
        this.glslSource = glslSource;
        this.spirvBytecode = spirvBytecode;
    }
    
    public String getName() {
        return name;
    }
    
    public ShaderStage getStage() {
        return stage;
    }
    
    public byte[] getSpirvBytecode() {
        return spirvBytecode;
    }
    
    public String getGlslSource() {
        return glslSource;
    }
    
    /**
     * Shader stage enumeration matching Vulkan stages.
     * Values correspond to shaderc shader kind constants.
     */
    public enum ShaderStage {
        VERTEX(0),              // shaderc_glsl_vertex_shader
        FRAGMENT(4),            // shaderc_glsl_fragment_shader
        COMPUTE(5),             // shaderc_glsl_compute_shader
        RAYGEN(7),              // shaderc_glsl_raygen_shader
        CLOSEST_HIT(8),         // shaderc_glsl_closesthit_shader
        ANY_HIT(9),             // shaderc_glsl_anyhit_shader
        MISS(10);               // shaderc_glsl_miss_shader
        
        private final int shaderCKind;
        
        ShaderStage(int shaderCKind) {
            this.shaderCKind = shaderCKind;
        }
        
        public int getShaderCKind() {
            return shaderCKind;
        }
    }
}
