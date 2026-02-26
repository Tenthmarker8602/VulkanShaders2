# Test Shader Pack

This is a simple example shader pack designed to visually demonstrate that VulkanShaders is compiling and running shaders correctly.

## What to Look For

When this shader pack is active in VulkanMod, you should see:

- **Bright Green and Cyan Checkerboard Pattern** - A large checkerboard pattern covering the screen with alternating green (0, 255, 0) and cyan (0, 255, 255) colors
- **16x16 Pixel Squares** - Each check in the checkerboard is 16 pixels wide, making the pattern obvious and unmistakable

## How to Use

1. Make sure VulkanMod is running
2. Open the Video Settings menu (Esc → Options → Video Settings)
3. Navigate to the "Shaders" tab
4. Select "TestShaders" from the shader pack list  
5. The world should immediately show a bright green/cyan checkerboard pattern

## Visual Verification

This shader is designed to make it **completely obvious** when shader compilation and rendering is working:

- If the shader compiles correctly, you'll see the bright patterns
- The shader contains no complex logic - just basic color output and a simple checkerboard pattern
- The glslc compiler's stdout/stderr will show compilation success in the logs

## Files

- **test_color.vert** - Vertex shader that outputs bright green color
- **test_color.frag** - Fragment shader that creates the checkerboard pattern
- **pack.json** - Shader pack configuration defining the pipeline

## Technical Details

- Uses GLSL 4.60 (compatible with Vulkan)
- Creates a 16x16 pixel checkerboard using fragment coordinates
- No external dependencies or includes
- Minimal uniform bindings (just camera matrices)

## Expected Console Output

When the shader is loaded, you should see in the Minecraft console:

```
SPIRVUtils: Using system glslc compiler for shader compilation
[Shader compilation output for test_color.vert]
[Shader compilation output for test_color.frag]
```

If you see these messages without errors, the shaders have compiled successfully!
