package net.vulkanmod.render.shader;

import net.vulkanmod.VulkanShaders;
import net.vulkanmod.pack.ShaderPack;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.config.option.ShaderPackManager;

/**
 * Handles rendering with shader packs.
 * Applies the currently selected shader pack's effects to the rendered scene.
 */
public class ShaderPackRenderer {
    
    private static ShaderPackRenderer instance;
    private ShaderPack currentPack;
    private boolean shaderPackDirty = false;
    
    private ShaderPackRenderer() {
    }
    
    public static ShaderPackRenderer getInstance() {
        if (instance == null) {
            instance = new ShaderPackRenderer();
        }
        return instance;
    }
    
    /**
     * Called when a shader pack is selected in the UI.
     * Marks the shader pack as needing to be loaded.
     */
    public void onShaderPackSelected(ShaderPack pack) {
        this.currentPack = pack;
        this.shaderPackDirty = true;
    }
    
    /**
     * Get the current shader pack.
     */
    public ShaderPack getCurrentPack() {
        return currentPack;
    }
    
    /**
     * Update and apply the current shader pack.
     * Called during the render loop.
     */
    public void updateShaderPack() {
        if (!shaderPackDirty) {
            return;
        }
        
        shaderPackDirty = false;
        
        try {
            ShaderPack pack = ShaderPackManager.getCurrentPack();
            if (pack != null && VulkanShaders.isInitialized()) {
                System.out.println("[ShaderPackRenderer] Applying shader pack: " + pack.getName());
                VulkanShaders.getInstance().loadShaderPack(pack.getPackPath());
                this.currentPack = pack;
                System.out.println("[ShaderPackRenderer] Successfully applied shader pack");
            }
        } catch (Exception e) {
            System.err.println("[ShaderPackRenderer] Failed to apply shader pack: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Apply shader pack effects post-render.
     * This is called after the main scene rendering is complete.
     */
    public void applyShaderPackEffects() {
        if (currentPack == null) {
            return;
        }
        
        // TODO: Implement post-processing pass with shader pack's fragment shader
        // This would overlay the shader effect on the rendered framebuffer
    }
}
