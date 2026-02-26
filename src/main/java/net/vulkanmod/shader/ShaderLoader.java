package net.vulkanmod.shader;

import net.vulkanmod.shader.ShaderModule.ShaderStage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles loading GLSL shader files and compiling them to SPIR-V.
 */
public class ShaderLoader {
    
    private final ShaderCompiler compiler;
    private final Path basePath;
    
    public ShaderLoader(Path basePath) {
        this.compiler = ShaderCompiler.getInstance();
        this.basePath = basePath;
    }
    
    /**
     * Load and compile a shader from a file.
     *
     * @param relativePath The path relative to basePath
     * @param stage        The shader stage
     * @return Compiled ShaderModule
     * @throws IOException if file reading fails
     */
    public ShaderModule loadShader(String relativePath, ShaderStage stage) throws IOException {
        Path shaderPath = basePath.resolve(relativePath);
        
        // Try filesystem first (for development)
        if (Files.exists(shaderPath)) {
            String source = new String(Files.readAllBytes(shaderPath), StandardCharsets.UTF_8);
            return compileShader(relativePath, source, stage);
        }
        
        // Try classpath resource (for packaged JAR)
        String resourcePath = "/" + shaderPath.toString().replace('\\', '/');
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is != null) {
                String source = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return compileShader(relativePath, source, stage);
            }
        }
        
        throw new FileNotFoundException("Shader file not found: " + shaderPath + " (also tried: " + resourcePath + ")");
    }
    
    /**
     * Compile a shader from source code string.
     *
     * @param name   The shader name
     * @param source The GLSL source code
     * @param stage  The shader stage
     * @return Compiled ShaderModule
     */
    public ShaderModule compileShader(String name, String source, ShaderStage stage) {
        // Preprocess source (add version directive if needed)
        String processedSource = preprocessGLSL(source);
        
        // Compile to SPIR-V
        byte[] spirv = compiler.compile(processedSource, stage, name);
        
        return new ShaderModule(name, stage, source, spirv);
    }
    
    /**
     * Preprocess GLSL source for SPIR-V compilation.
     * Ensures compatibility with Vulkan GLSL requirements.
     */
    private String preprocessGLSL(String source) {
        if (source.trim().startsWith("#version")) {
            // Already has version directive
            return source;
        }
        
        // Add Vulkan-compatible version directive
        return "#version 460\n" + source;
    }
    
    /**
     * Load multiple shaders for a pipeline.
     *
     * @param pathToStageMap Map of file paths to shader stages
     * @return Array of compiled ShaderModules
     * @throws IOException if any file reading fails
     */
    public ShaderModule[] loadShaders(java.util.Map<String, ShaderStage> pathToStageMap) throws IOException {
        ShaderModule[] modules = new ShaderModule[pathToStageMap.size()];
        int index = 0;
        
        for (java.util.Map.Entry<String, ShaderStage> entry : pathToStageMap.entrySet()) {
            modules[index++] = loadShader(entry.getKey(), entry.getValue());
        }
        
        return modules;
    }
}
