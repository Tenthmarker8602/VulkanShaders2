package net.vulkanmod.config.option;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.vulkanmod.Initializer;
import net.vulkanmod.VulkanShaders;
import net.vulkanmod.config.Config;
import net.vulkanmod.pack.ShaderPack;
import net.vulkanmod.pack.ShaderPackConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages shader pack detection and loading.
 * Scans shader pack directory for available packs and extracts bundled example packs.
 */
public class ShaderPackManager {
    
    private static final Path SHADERPACKS_DIR = Paths.get("shaderpacks");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ShaderPack currentPack;
    private static List<ShaderPack> availablePacks;
    private static final String NONE_OPTION = "None";
    private static final Config config = Initializer.CONFIG;
    
    static {
        System.out.println("[ShaderPackManager] Initializing...");
        availablePacks = new ArrayList<>();
        extractBundledShaderPacks();
        System.out.println("[ShaderPackManager] Extraction complete, scanning packs...");
        scanAvailablePacks();
        System.out.println("[ShaderPackManager] Scan complete, found " + availablePacks.size() + " packs");
        loadCurrentPack();
        System.out.println("[ShaderPackManager] Initialization complete");
    }
    
    /**
     * Extract bundled example shader packs from resources if they don't exist.
     */
    private static void extractBundledShaderPacks() {
        // Create shaderpacks directory if it doesn't exist
        try {
            Files.createDirectories(SHADERPACKS_DIR);
        } catch (Exception e) {
            System.err.println("Failed to create shaderpacks directory: " + e.getMessage());
            return;
        }
        
        // Extract bundled shader packs
        extractBundledPack("TestShaders");
        extractBundledPack("SunsetTest");
    }
    
    /**
     * Extract a single bundled shader pack from resources.
     */
    private static void extractBundledPack(String packName) {
        Path packDir = SHADERPACKS_DIR.resolve(packName);
        
        // Skip if already exists
        if (Files.exists(packDir)) {
            return;
        }
        
        try {
            Files.createDirectories(packDir);
            
            // Extract pack.json from classpath resources
            String jsonResource = "/shaderpacks/" + packName + "/pack.json";
            InputStream jsonStream = ShaderPackManager.class.getResourceAsStream(jsonResource);
            if (jsonStream != null) {
                Files.copy(jsonStream, packDir.resolve("pack.json"));
                jsonStream.close();
                System.out.println("Extracted shader pack: " + packName);
            } else {
                System.err.println("Could not find resource: " + jsonResource);
            }
        } catch (Exception e) {
            System.err.println("Failed to extract shader pack " + packName + ": " + e.getMessage());
        }
    }
    
    /**
     * Scan the shaderpacks directory for available shader packs.
     */
    public static void scanAvailablePacks() {
        System.out.println("[ShaderPackManager] scanAvailablePacks() called, VulkanShaders.isInitialized()=" + VulkanShaders.isInitialized());
        availablePacks.clear();
        
        if (!Files.exists(SHADERPACKS_DIR)) {
            try {
                Files.createDirectories(SHADERPACKS_DIR);
            } catch (Exception e) {
                System.err.println("Failed to create shaderpacks directory: " + e.getMessage());
            }
            return;
        }
        
        try (var stream = Files.list(SHADERPACKS_DIR)) {
            System.out.println("[ShaderPackManager] Listing directories in " + SHADERPACKS_DIR);
            stream.filter(Files::isDirectory)
                    .forEach(packDir -> {
                        System.out.println("[ShaderPackManager] Found directory: " + packDir);
                        try {
                            // Load pack metadata from pack.json
                            loadPackMetadata(packDir);
                        } catch (Exception e) {
                            System.err.println("Failed to load shader pack from " + packDir + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            System.err.println("Failed to scan shader packs directory: " + e.getMessage());
        }
        
        // Sort by name
        availablePacks.sort(Comparator.comparing(ShaderPack::getName));
    }
    
    /**
     * Load shader pack metadata from pack.json without full VulkanShaders initialization.
     */
    private static void loadPackMetadata(Path packDir) {
        System.out.println("[ShaderPackManager] loadPackMetadata() called for " + packDir);
        try {
            Path packJsonPath = packDir.resolve("pack.json");
            if (Files.exists(packJsonPath)) {
                System.out.println("[ShaderPackManager] pack.json found at " + packJsonPath);
                String json = new String(Files.readAllBytes(packJsonPath), StandardCharsets.UTF_8);
                System.out.println("[ShaderPackManager] JSON content: " + json.substring(0, Math.min(100, json.length())));
                ShaderPackConfig config = GSON.fromJson(json, ShaderPackConfig.class);
                System.out.println("[ShaderPackManager] Parsed config: " + (config != null ? config.name : "null"));
                
                if (config != null && config.name != null) {
                    ShaderPack pack = new ShaderPack(config, packDir);
                    availablePacks.add(pack);
                    System.out.println("Loaded shader pack metadata: " + pack.getName());
                } else {
                    System.out.println("[ShaderPackManager] Config name is null");
                }
            } else {
                System.out.println("[ShaderPackManager] pack.json not found");
            }
        } catch (Exception e) {
            System.err.println("Failed to load pack metadata from " + packDir + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load the current shader pack from config.
     */
    private static void loadCurrentPack() {
        String packToLoad = config.shaderPack;
        
        // Auto-select TestShaders if no pack is configured
        if (packToLoad == null || packToLoad.equals(NONE_OPTION) || packToLoad.isEmpty()) {
            packToLoad = "Test Shader Pack";  // Default to TestShaders
            System.out.println("[ShaderPackManager] No shader pack configured, auto-selecting: " + packToLoad);
        }
        
        ShaderPack pack = getPackByName(packToLoad);
        if (pack != null) {
            currentPack = pack;
            config.shaderPack = pack.getName();
            config.write();
            System.out.println("[ShaderPackManager] Loaded shader pack: " + pack.getName());
        } else {
            System.out.println("[ShaderPackManager] Could not find pack: " + packToLoad);
        }
    }
    
    /**
     * Get all available shader packs.
     *
     * @return List of shader packs
     */
    public static List<ShaderPack> getAvailablePacks() {
        return Collections.unmodifiableList(availablePacks);
    }
    
    /**
     * Get the names of all available shader packs.
     *
     * @return Array of pack names
     */
    public static String[] getPackNames() {
        List<String> names = new ArrayList<>();
        names.add(NONE_OPTION);
        availablePacks.stream()
                .map(ShaderPack::getName)
                .forEach(names::add);
        return names.toArray(new String[0]);
    }
    
    /**
     * Get a shader pack by name.
     *
     * @param name The pack name
     * @return The ShaderPack or null if not found
     */
    public static ShaderPack getPackByName(String name) {
        if (name.equals(NONE_OPTION)) {
            return null;
        }
        return availablePacks.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Set the currently active shader pack.
     *
     * @param pack The shader pack to activate
     */
    public static void setCurrentPack(ShaderPack pack) {
        currentPack = pack;
        
        // Update config
        if (pack != null) {
            config.shaderPack = pack.getName();
        } else {
            config.shaderPack = NONE_OPTION;
        }
        config.write();
    }
    
    /**
     * Get the currently active shader pack.
     *
     * @return The active shader pack or null if none
     */
    public static ShaderPack getCurrentPack() {
        return currentPack;
    }
    
    /**
     * Get the name of the currently active shader pack.
     *
     * @return The pack name or "None"
     */
    public static String getCurrentPackName() {
        return currentPack == null ? NONE_OPTION : currentPack.getName();
    }
    
    /**
     * Reload available packs from disk.
     */
    public static void reload() {
        scanAvailablePacks();
    }
}
