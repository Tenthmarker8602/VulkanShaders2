package net.vulkanmod.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads shader packs from the filesystem.
 * Parses shader pack JSON configuration files.
 */
public class ShaderPackLoader {
    
    private static final String CONFIG_FILE = "pack.json";
    private final Gson gson;
    
    public ShaderPackLoader() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
    }
    
    /**
     * Load a shader pack from a directory.
     *
     * @param packDirectory The directory containing pack.json
     * @return The loaded ShaderPack
     * @throws IOException if loading fails
     */
    public ShaderPack loadPack(Path packDirectory) throws IOException {
        Path configPath = packDirectory.resolve(CONFIG_FILE);
        
        if (!Files.exists(configPath)) {
            throw new FileNotFoundException("Shader pack config not found: " + configPath);
        }
        
        String configJson = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        ShaderPackConfig config = gson.fromJson(configJson, ShaderPackConfig.class);
        
        if (config == null) {
            throw new IOException("Failed to parse shader pack config");
        }
        
        return new ShaderPack(config, packDirectory);
    }
    
    /**
     * Load a shader pack from a JSON string.
     *
     * @param configJson  The JSON configuration string
     * @param packPath    The path to the shader pack directory
     * @return The loaded ShaderPack
     */
    public ShaderPack loadPackFromJson(String configJson, Path packPath) {
        ShaderPackConfig config = gson.fromJson(configJson, ShaderPackConfig.class);
        
        if (config == null) {
            throw new IllegalArgumentException("Failed to parse shader pack config");
        }
        
        return new ShaderPack(config, packPath);
    }
    
    /**
     * Create a default shader pack configuration for testing.
     *
     * @return A basic ShaderPackConfig
     */
    public static ShaderPackConfig createDefaultConfig() {
        ShaderPackConfig config = new ShaderPackConfig();
        config.name = "Default";
        config.version = "1.0";
        config.description = "Default shader pack";
        config.raytracing = false;
        config.pipelines = new java.util.ArrayList<>();
        return config;
    }
}
