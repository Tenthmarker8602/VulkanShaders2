# VulkanShaders Implementation Guide

This document describes the basic implementation of the VulkanShaders system as specified in `spec.md`.

## Overview

VulkanShaders is a modular shader system for Minecraft Java using Vulkan. This implementation provides:

- **GLSL → SPIR-V compilation** at runtime using shaderc
- **Pipeline management** for graphics, compute, and ray tracing
- **Descriptor set management** for GPU resource binding
- **Resource allocation** for buffers and textures
- **Shader pack loading** from JSON configuration files

## Package Structure

### `net.vulkanmod.shader`
Handles shader compilation and management.

- **`ShaderModule.java`**: Represents a compiled SPIR-V shader module with metadata
- **`ShaderCompiler.java`**: Singleton that compiles GLSL to SPIR-V using shaderc
- **`ShaderLoader.java`**: Loads GLSL shader files and compiles them

### `net.vulkanmod.pipeline`
Manages Vulkan pipelines (graphics, compute, ray tracing).

- **`PipelineType.java`**: Enumeration for pipeline types
- **`VulkanPipeline.java`**: Represents a compiled pipeline with shaders and descriptor layouts
- **`PipelineManager.java`**: Creates and manages pipelines with builder pattern

### `net.vulkanmod.descriptor`
Manages descriptor sets and bindings.

- **`DescriptorType.java`**: Enumeration for descriptor binding types
- **`Binding.java`**: Represents a single descriptor binding
- **`DescriptorSetLayout.java`**: Layout definition for a descriptor set
- **`DescriptorSet.java`**: Runtime descriptor set instance with bound resources
- **`DescriptorManager.java`**: Creates and manages descriptor sets and layouts

### `net.vulkanmod.resource`
Manages GPU resources (buffers and textures).

- **`GpuResource.java`**: Interface for GPU resources
- **`BufferResource.java`**: Represents a Vulkan buffer (uniform, storage, etc.)
- **`TextureResource.java`**: Represents a Vulkan image/texture
- **`ResourceLoader.java`**: Creates and manages GPU resources

### `net.vulkanmod.pack`
Loads shader packs from disk.

- **`ShaderPackConfig.java`**: POJO classes for JSON deserialization
- **`ShaderPack.java`**: Represents a loaded shader pack with pipeline definitions
- **`ShaderPackLoader.java`**: Loads shader packs from JSON files using GSON

### `net.vulkanmod`
Main coordination layer.

- **`VulkanShaders.java`**: Singleton facade coordinating all subsystems
- **`VulkanShadersExample.java`**: Example usage demonstrating the API

## Key Features

### 1. GLSL → SPIR-V Compilation

```java
ShaderLoader loader = new ShaderLoader(basePath);
ShaderModule vertexShader = loader.loadShader("shaders/basic.vert", ShaderModule.ShaderStage.VERTEX);
byte[] spirvBytecode = vertexShader.getSpirvBytecode();
```

Features:
- Automatic version directive addition (#version 460)
- Vulkan 1.2 target compatibility
- SPIR-V 1.5 output
- Error reporting and validation

### 2. Pipeline Construction

```java
VulkanPipeline pipeline = pipelineManager
    .createPipeline("gbuffer", PipelineType.GRAPHICS)
    .withShader(vertexShader)
    .withShader(fragmentShader)
    .withDescriptorSetLayout(layout)
    .withSetting("cullMode", "back")
    .build();
```

Features:
- Fluent builder API
- Support for graphics, compute, and ray tracing pipelines
- Configurable pipeline settings
- Validation of required shaders

### 3. Descriptor Set Management

```java
DescriptorSetLayout layout = descriptorManager.createLayout("camera", 0);
layout.addBinding(new Binding(0, DescriptorType.UNIFORM_BUFFER, "Camera"));

DescriptorSet set = descriptorManager.allocateDescriptorSet("camera");
set.bindResource(0, cameraBuffer);
```

Features:
- Layout-based descriptor set creation
- Multiple binding types (uniform/storage buffers, samplers, images)
- Runtime resource binding with validation
- Array descriptor support

### 4. GPU Resource Creation

```java
BufferResource buffer = resourceLoader.createBuffer(
    "camera_uniform",
    BufferResource.BufferType.UNIFORM_BUFFER,
    256
);

TextureResource texture = resourceLoader.createTexture(
    "gbuffer_position",
    TextureResource.TextureType.TEXTURE_2D,
    TextureResource.Format.RGBA32_FLOAT,
    1920, 1080
);
```

Features:
- Type-safe resource creation
- Support for various buffer and texture formats
- Resource caching and lifecycle management
- Batch resource queries

### 5. Shader Pack Loading

```java
ShaderPack pack = vulkanShaders.loadShaderPack(Paths.get("shaderpacks/MyPack"));

for (ShaderPack.PipelineDefinition pipelineDef : pack.getPipelines()) {
    // Pipeline automatically created with shaders and descriptor sets
}
```

Supports JSON configuration:
```json
{
  "name": "MyPack",
  "version": "1.0",
  "pipelines": [
    {
      "name": "gbuffer",
      "type": "graphics",
      "vertex": "shaders/gbuffer.vert.glsl",
      "fragment": "shaders/gbuffer.frag.glsl",
      "descriptor_sets": [
        {
          "set": 0,
          "bindings": [
            {"binding": 0, "type": "uniform_buffer", "name": "Camera"}
          ]
        }
      ]
    }
  ]
}
```

## Usage Example

```java
// Initialize
VulkanShaders vs = VulkanShaders.initialize(Paths.get("assets/vulkanmod/shaders"));

// Load shader pack
ShaderPack pack = vs.loadShaderPack(Paths.get("shaderpacks/MyPack"));

// Access loaded pipelines
VulkanPipeline pipeline = vs.getPipelineManager().getPipeline("gbuffer");

// Create resources
BufferResource buffer = vs.getResourceLoader().createBuffer(
    "camera",
    BufferResource.BufferType.UNIFORM_BUFFER,
    256
);

// Bind resources to descriptors
DescriptorSet set = vs.getDescriptorManager().allocateDescriptorSet("camera_layout");
set.bindResource(0, buffer);

// Cleanup
vs.cleanup();
```

## Implementation Status

### Completed ✓
- Shader module and compilation system
- Pipeline management with builder pattern
- Descriptor set layout and allocation
- GPU resource creation and caching
- Shader pack JSON loading
- Main VulkanShaders coordinator

### Not Yet Implemented (Future Work)
- Actual Vulkan API calls (VkPipeline creation, etc.)
- Buffer memory allocation and uploads
- Texture memory allocation and uploads
- Render pass definition and execution
- Vulkan command buffer recording
- Synchronization (semaphores, fences)
- Ray tracing pipeline support (advanced)
- Shader hot-reloading capability
- Performance profiling and optimization

## Dependencies

The implementation currently requires:

- **LWJGL 3** with shaderc bindings for GLSL → SPIR-V compilation
- **GSON** for JSON deserialization
- **Java 16+** for modern language features (switch expressions, records)

## Integration Points

To integrate VulkanShaders with VulkanMod:

1. Initialize when VulkanMod starts:
   ```java
   VulkanShaders.initialize(getShaderPackPath());
   ```

2. Load user shader packs:
   ```java
   VulkanShaders.getInstance().loadShaderPack(selectedPackPath);
   ```

3. Create Vulkan pipelines using loaded shaders:
   ```java
   VulkanPipeline pipeline = vulkanShaders.getPipelineManager().getPipeline("gbuffer");
   long vkPipeline = createVulkanPipeline(pipeline); // Custom VulkanMod integration
   ```

4. Bind descriptor sets during rendering:
   ```java
   DescriptorSet set = vulkanShaders.getDescriptorManager().getDescriptorSets("layout").get(0);
   vkCmdBindDescriptorSets(..., set.getVkDescriptorSet(), ...);
   ```

5. Clean up on shutdown:
   ```java
   VulkanShaders.getInstance().cleanup();
   ```

## Architecture Diagram

```
User Application
    |
    v
VulkanShaders (Coordinator)
    |
    +-- ShaderLoader ------> ShaderCompiler (GLSL → SPIR-V)
    |                            |
    |                            v
    |                        ShaderModules
    |
    +-- PipelineManager ------> VulkanPipeline(s)
    |
    +-- DescriptorManager ----> DescriptorSetLayout(s)
    |                            |
    |                            v
    |                        DescriptorSet(s) <-- Resources
    |
    +-- ResourceLoader -------> BufferResource(s)
                                TextureResource(s)
    |
    +-- ShaderPackLoader ----> ShaderPack (JSON config)
                                |
                                v
                            Pipelines from Pack
```

## Future Roadmap

Per the specification, planned phases:

1. ✓ **Phase 1**: Shader manager & GLSL loader → SPIR-V
2. ⏳ **Phase 2**: Pipeline & descriptor manager (partially done)
3. ⏳ **Phase 3**: Compute pipelines
4. ⏳ **Phase 4**: Ray tracing support
5. ⏳ **Phase 5**: Shader pack ecosystem
6. ⏳ **Phase 6**: Optimization & profiling
7. ⏳ **Phase 7**: Official Vulkan compatibility

## Notes

- This implementation focuses on the logical/data structure layer
- Actual Vulkan API calls must be implemented by VulkanMod integration
- The system is designed to be backend-agnostic and can be adapted for official Vulkan when available
- All manager classes use thread-safe singleton or stateless patterns where appropriate
