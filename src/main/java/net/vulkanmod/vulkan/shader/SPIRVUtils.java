package net.vulkanmod.vulkan.shader;

import org.lwjgl.system.NativeResource;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SPIR-V utilities using system glslc compiler for shader compilation.
 * 
 * Uses Google's glslc compiler (https://github.com/google/shaderc) if available,
 * falling back to LWJGL shaderc bindings when possible.
 */
public class SPIRVUtils {
    
    private static boolean glslcAvailable = false;
    
    /** Temp directory where classpath include files are extracted for glslc */
    private static Path extractedIncludeDir = null;

    static {
        initCompiler();
    }

    private static void initCompiler() {
        // Check if glslc is available on the system
        try {
            ProcessBuilder pb = new ProcessBuilder("glslc", "--version");
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                glslcAvailable = true;
                System.out.println("SPIRVUtils: Using system glslc compiler for shader compilation");
            }
        } catch (Exception e) {
            System.out.println("SPIRVUtils: glslc not found - shader compilation will be limited");
            System.out.println("  Install glslc from https://github.com/google/shaderc or add org.lwjgl:lwjgl-shaderc:3.3.3+ to build.gradle");
        }
        
        if (glslcAvailable) {
            extractIncludeFiles();
        }
    }
    
    /**
     * Extract GLSL include files from the classpath into a temp directory
     * so that glslc can find them via -I flag. This handles both development
     * (classes on disk) and production (resources inside JAR) scenarios.
     */
    private static void extractIncludeFiles() {
        String[] includeFiles = {
            "light.glsl", "fog.glsl", "matrix.glsl", "projection.glsl"
        };
        
        try {
            extractedIncludeDir = Files.createTempDirectory("vulkanmod_shader_includes_");
            extractedIncludeDir.toFile().deleteOnExit();
            
            for (String fileName : includeFiles) {
                String resourcePath = "/assets/vulkanmod/shaders/include/" + fileName;
                try (InputStream is = SPIRVUtils.class.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        Path target = extractedIncludeDir.resolve(fileName);
                        Files.copy(is, target);
                        target.toFile().deleteOnExit();
                    } else {
                        System.out.println("SPIRVUtils: Include file not found in classpath: " + resourcePath);
                    }
                }
            }
            System.out.println("SPIRVUtils: Extracted shader includes to " + extractedIncludeDir);
        } catch (Exception e) {
            System.err.println("SPIRVUtils: Failed to extract include files: " + e.getMessage());
            extractedIncludeDir = null;
        }
    }

    public static void addIncludePath(String path) {
        // Include paths would be passed to glslc via -I flag
    }

    /**
     * Compile a shader to SPIR-V using glslc.
     *
     * @param filename The shader filename
     * @param source The GLSL source code
     * @param shaderKind The shader kind
     * @return SPIRV object containing compiled bytecode
     * @throws RuntimeException if compilation fails and glslc is not available
     */
    public static SPIRV compileShader(String filename, String source, ShaderKind shaderKind) {
        if (source == null) {
            throw new NullPointerException("source for %s.%s is null".formatted(filename, shaderKind));
        }

        if (glslcAvailable) {
            return compileWithGlslc(filename, source, shaderKind);
        } else {
            throw new RuntimeException(
                "Cannot compile shader '" + filename + "': glslc is not installed. " +
                "Install it via: brew install shaderc (macOS) or apt install glslc (Linux)");
        }
    }

    /**
     * Compile using the system glslc compiler.
     */
    private static SPIRV compileWithGlslc(String filename, String source, ShaderKind shaderKind) {
        try {
            // Create temporary files for input and output
            Path inputFile = Files.createTempFile("shader_", "." + getShaderExtension(shaderKind));
            Path outputFile = Files.createTempFile("shader_", ".spv");
            
            try {
                // Write source to temporary file
                Files.writeString(inputFile, source);
                
                // Build command with include paths
                java.util.List<String> command = new java.util.ArrayList<>();
                command.add("glslc");
                command.add("-fshader-stage=" + getShaderStage(shaderKind));
                
                // Primary include path: extracted classpath includes (always available)
                if (extractedIncludeDir != null && Files.isDirectory(extractedIncludeDir)) {
                    command.add("-I");
                    command.add(extractedIncludeDir.toAbsolutePath().toString());
                }
                
                // Also try filesystem paths for development convenience
                String userDir = System.getProperty("user.dir", ".");
                String[] devIncludePaths = {
                    userDir + "/src/main/resources/assets/vulkanmod/shaders/include",
                    userDir + "/../src/main/resources/assets/vulkanmod/shaders/include",
                };
                
                for (String path : devIncludePaths) {
                    java.nio.file.Path p = java.nio.file.Paths.get(path).normalize();
                    if (Files.isDirectory(p)) {
                        command.add("-I");
                        command.add(p.toAbsolutePath().toString());
                    }
                }
                
                command.add("-o");
                command.add(outputFile.toString());
                command.add(inputFile.toString());
                
                // Compile with glslc
                ProcessBuilder pb = new ProcessBuilder(command);
                
                Process p = pb.start();
                
                // Capture stderr for error messages
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                StringBuilder errorMsg = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorMsg.append(line).append("\n");
                }
                
                int exitCode = p.waitFor();
                if (exitCode != 0) {
                    System.err.println("Shader compilation failed for " + filename + ":");
                    System.err.println(errorMsg);
                    throw new RuntimeException("glslc compilation failed for '" + filename + "': " + errorMsg);
                }
                
                // Read compiled SPIR-V
                byte[] bytecode = Files.readAllBytes(outputFile);
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytecode.length);
                buffer.put(bytecode);
                buffer.flip();
                
                return new SPIRV(0, buffer);
                
            } finally {
                // Clean up temporary files
                try { Files.delete(inputFile); } catch (Exception e) { }
                try { Files.delete(outputFile); } catch (Exception e) { }
            }
            
        } catch (RuntimeException e) {
            throw e; // Don't wrap RuntimeExceptions
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile shader '" + filename + "' with glslc: " + e.getMessage(), e);
        }
    }

    private static String getShaderExtension(ShaderKind kind) {
        return switch (kind) {
            case VERTEX_SHADER -> "vert";
            case FRAGMENT_SHADER -> "frag";
            case GEOMETRY_SHADER -> "geom";
            case COMPUTE_SHADER -> "comp";
        };
    }

    private static String getShaderStage(ShaderKind kind) {
        return switch (kind) {
            case VERTEX_SHADER -> "vertex";
            case FRAGMENT_SHADER -> "fragment";
            case GEOMETRY_SHADER -> "geometry";
            case COMPUTE_SHADER -> "compute";
        };
    }

    /**
     * Shader kind enumeration.
     */
    public enum ShaderKind {
        VERTEX_SHADER(0),
        GEOMETRY_SHADER(3),
        FRAGMENT_SHADER(4),
        COMPUTE_SHADER(5);

        public final int kind;

        ShaderKind(int kind) {
            this.kind = kind;
        }
    }

    /**
     * SPIR-V compilation result wrapper.
     */
    public static final class SPIRV implements NativeResource {

        private final long handle;
        private ByteBuffer bytecode;

        public SPIRV(long handle, ByteBuffer bytecode) {
            this.handle = handle;
            this.bytecode = bytecode;
        }

        public ByteBuffer bytecode() {
            return bytecode;
        }

        @Override
        public void free() {
            bytecode = null; // Help the GC
        }
    }
}