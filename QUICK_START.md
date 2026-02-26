# VulkanShaders Quick Start Guide

## Quick Start (5 minutes)

### 1. Initialize VulkanShaders

```java
import net.vulkanmod.VulkanShaders;
import java.nio.file.Paths;

// In your mod initialization code
VulkanShaders vs = VulkanShaders.initialize(Paths.get("assets/shaders"));
```

### 2. Load a Shader Pack

Create a shader pack directory with this structure:

```
my_shader_pack/
├── pack.json
└── shaders/
    ├── gbuffer.vert.glsl
    └── gbuffer.frag.glsl
```

Then load it:

```java
var pack = vs.loadShaderPack(Paths.get("shaderpacks/my_shader_pack"));
System.out.println("Loaded pack: " + pack.getName());
```

### 3. Access Pipelines

```java
var pipeline = vs.getPipelineManager().getPipeline("gbuffer");
if (pipeline != null) {
    System.out.println("Found pipeline: " + pipeline);
    var shaders = pipeline.getShaderModules();
    System.out.println("Pipeline has " + shaders.size() + " shaders");
}
```

### 4. Create Resources

```java
var buffer = vs.getResourceLoader().createBuffer(
    "my_buffer",
    net.vulkanmod.resource.BufferResource.BufferType.UNIFORM_BUFFER,
    256
);

var texture = vs.getResourceLoader().createTexture(
    "my_texture",
    net.vulkanmod.resource.TextureResource.TextureType.TEXTURE_2D,
    net.vulkanmod.resource.TextureResource.Format.RGBA8_UNORM,
    512, 512
);
```

### 5. Create Descriptor Sets

```java
var dm = vs.getDescriptorManager();

// Create layout
var layout = dm.createLayout("my_layout", 0);
layout.addBinding(new net.vulkanmod.descriptor.Binding(
    0,
    net.vulkanmod.descriptor.DescriptorType.UNIFORM_BUFFER,
    "CameraData"
));

// Allocate set and bind resources
var set = dm.allocateDescriptorSet("my_layout");
set.bindResource(0, buffer);
```

### 6. Create Pipelines Manually

```java
var pipeline = vs.getPipelineManager()
    .createPipeline("my_pipeline", net.vulkanmod.pipeline.PipelineType.GRAPHICS)
    // Shaders would be loaded here if files exist
    .withDescriptorSetLayout(layout)
    .withSetting("cullMode", "back")
    .build();
```

### 7. Cleanup

```java
vs.cleanup(); // Call when your mod shuts down
```

## Example: Basic shader.json

```json
{
  "name": "Basic Shaders",
  "version": "1.0",
  "description": "A basic shader pack for VulkanMod",
  "raytracing": false,
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
            {
              "binding": 0,
              "type": "uniform_buffer",
              "name": "Camera"
            },
            {
              "binding": 1,
              "type": "sampler2D",
              "name": "ShadowMap"
            }
          ]
        }
      ]
    }
  ]
}
```

## Example: Simple Vertex Shader

```glsl
#version 460

layout(binding = 0) uniform Camera {
    mat4 viewProj;
    vec3 cameraPos;
} camera;

layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;

layout(location = 0) out vec3 outNormal;

void main() {
    gl_Position = camera.viewProj * vec4(position, 1.0);
    outNormal = normal;
}
```

## Common Tasks

### Load Custom Shaders

```java
var shaderLoader = vs.getShaderLoader();
var vertShader = shaderLoader.loadShader(
    "shaders/custom.vert.glsl",
    net.vulkanmod.shader.ShaderModule.ShaderStage.VERTEX
);
```

### List All Resources

```java
vs.getResourceLoader().getAllResources().forEach((name, resource) ->
    System.out.println(name + ": " + resource)
);
```

### List All Pipelines

```java
vs.getPipelineManager().getAllPipelines().forEach((name, pipeline) ->
    System.out.println(name + ": " + pipeline)
);
```

### Check Descriptor Set Status

```java
var sets = vs.getDescriptorManager().getDescriptorSets("my_layout");
for (var set : sets) {
    if (set.isComplete()) {
        System.out.println("Set is ready!");
    }
}
```

## Troubleshooting

### "Shader compilation failed"
- Check shader file path is correct
- Ensure GLSL syntax is valid
- Check for Vulkan compatibility (use appropriate extensions)

### "Binding X not defined in layout"
- Make sure all bindings are added to the layout before allocating sets
- Verify binding indices match between pack.json and code

### "Resource not found"
- Check resource name spelling
- Ensure resource was created before accessing it
- Call getBuffer() or getTexture() for type-safe access

### "VulkanShaders not initialized"
- Call VulkanShaders.initialize() before using getInstance()
- Initialize early in your mod startup sequence

## API Documentation

See [IMPLEMENTATION.md](IMPLEMENTATION.md) for detailed class and method documentation.

## File Structure Reference

```
src/main/java/net/vulkanmod/
├── VulkanShaders.java              # Main entry point (singleton)
├── VulkanShadersExample.java       # Usage examples
│
├── shader/
│   ├── ShaderModule.java           # Compiled shader
│   ├── ShaderCompiler.java         # GLSL → SPIR-V
│   └── ShaderLoader.java           # File loader
│
├── pipeline/
│   ├── VulkanPipeline.java         # Pipeline instance
│   ├── PipelineType.java           # Type enum
│   └── PipelineManager.java        # Pipeline manager
│
├── descriptor/
│   ├── Binding.java                # Descriptor binding
│   ├── DescriptorType.java         # Type enum
│   ├── DescriptorSet.java          # Runtime set
│   ├── DescriptorSetLayout.java    # Layout definition
│   └── DescriptorManager.java      # Manager
│
├── resource/
│   ├── GpuResource.java            # Base interface
│   ├── BufferResource.java         # Buffer
│   ├── TextureResource.java        # Texture
│   └── ResourceLoader.java         # Manager
│
└── pack/
    ├── ShaderPackConfig.java       # JSON model
    ├── ShaderPack.java             # Loaded pack
    └── ShaderPackLoader.java       # Loader
```

## Next Steps

1. Create your first shader pack with a simple quad/triangle rendering pipeline
2. Load descriptor sets and bind camera matrices
3. Integrate with VulkanMod's command buffer recording
4. Test shader hot-reloading by watching pack.json for changes
5. Explore advanced features like compute shaders and ray tracing

Happy shader coding! 🎨
