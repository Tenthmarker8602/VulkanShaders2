package net.vulkanmod;

import net.vulkanmod.descriptor.*;
import net.vulkanmod.pack.ShaderPack;
import net.vulkanmod.pipeline.VulkanPipeline;
import net.vulkanmod.resource.BufferResource;
import net.vulkanmod.resource.ResourceLoader;
import net.vulkanmod.resource.TextureResource;
import net.vulkanmod.shader.ShaderModule;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example usage of the VulkanShaders system.
 * Demonstrates loading shader packs, creating pipelines, and managing resources.
 */
public class VulkanShadersExample {
    
    public static void main(String[] args) {
        try {
            // 1. Initialize VulkanShaders with a base path for shaders
            Path shadersPath = Paths.get("assets/vulkanmod/shaders");
            VulkanShaders vs = VulkanShaders.initialize(shadersPath);
            System.out.println("VulkanShaders initialized: " + vs);
            
            // 2. Load a shader pack (if available)
            // ShaderPack pack = vs.loadShaderPack(Paths.get("shaderpacks/MyPack"));
            
            // 3. Example: Create a simple graphics pipeline manually
            exampleManualPipelineCreation(vs);
            
            // 4. Example: Create and use descriptor sets
            exampleDescriptorSets(vs);
            
            // 5. Example: Create GPU resources
            exampleResourceCreation(vs);
            
            // 6. List all pipelines
            System.out.println("\nRegistered Pipelines:");
            vs.getPipelineManager().getAllPipelines().forEach((name, pipeline) ->
                    System.out.println("  - " + pipeline)
            );
            
            // 7. Clean up
            vs.cleanup();
            System.out.println("\nVulkanShaders cleanup complete");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Example of manually creating a graphics pipeline.
     */
    private static void exampleManualPipelineCreation(VulkanShaders vs) throws IOException {
        // Create a simple G-buffer pipeline
        VulkanPipeline pipeline = vs.getPipelineManager()
                .createPipeline("gbuffer", net.vulkanmod.pipeline.PipelineType.GRAPHICS)
                
                // Add shader modules (note: actual shader files would need to exist)
                // .withShader(vs.getShaderLoader().loadShader("shaders/gbuffer.vert.glsl", ShaderModule.ShaderStage.VERTEX))
                // .withShader(vs.getShaderLoader().loadShader("shaders/gbuffer.frag.glsl", ShaderModule.ShaderStage.FRAGMENT))
                
                // Add pipeline settings
                .withSetting("cullMode", "back")
                .withSetting("polygonMode", "fill")
                
                .build();
        
        System.out.println("\nCreated pipeline: " + pipeline);
    }
    
    /**
     * Example of creating and using descriptor sets.
     */
    private static void exampleDescriptorSets(VulkanShaders vs) {
        DescriptorManager dm = vs.getDescriptorManager();
        
        // Create a descriptor set layout for camera data
        DescriptorSetLayout layout = dm.createLayout("camera_layout", 0);
        
        // Add bindings for common camera resources
        layout.addBinding(new Binding(0, DescriptorType.UNIFORM_BUFFER, "Camera", 1));
        layout.addBinding(new Binding(1, DescriptorType.UNIFORM_BUFFER, "ViewProjection", 1));
        
        System.out.println("\nCreated descriptor set layout: " + layout);
        
        // Allocate a descriptor set from the layout
        DescriptorSet set = dm.allocateDescriptorSet("camera_layout");
        System.out.println("Allocated descriptor set: " + set);
        
        // In real usage, you would bind actual GPU resources here:
        // set.bindResource(0, cameraBuffer);
        // set.bindResource(1, viewProjectionBuffer);
    }
    
    /**
     * Example of creating GPU resources.
     */
    private static void exampleResourceCreation(VulkanShaders vs) {
        ResourceLoader loader = vs.getResourceLoader();
        
        // Create a uniform buffer
        BufferResource uniformBuffer = loader.createBuffer(
                "camera_uniform",
                BufferResource.BufferType.UNIFORM_BUFFER,
                256 // 256 bytes for camera matrices
        );
        System.out.println("\nCreated buffer: " + uniformBuffer);
        
        // Create a texture for G-buffer position
        TextureResource positionTexture = loader.createTexture(
                "gbuffer_position",
                TextureResource.TextureType.TEXTURE_2D,
                TextureResource.Format.RGBA32_FLOAT,
                1920, // width
                1080  // height
        );
        System.out.println("Created texture: " + positionTexture);
        
        // List all resources
        System.out.println("\nTotal resources allocated: " + loader.getResourceCount());
    }
}
