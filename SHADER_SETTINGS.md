# VulkanShaders - Video Settings Integration

## Overview

The VulkanShaders system now integrates directly into the video settings menu of VulkanMod. Players can easily manage and switch between shader packs without manual configuration.

## Features

### Shaders Page in Video Settings

A new "Shaders" page has been added to the video settings screen with the following options:

#### 1. **Shader Pack Selection**
- Dropdown menu displaying all available shader packs
- Displays "None" when no shader pack is active
- Automatically detects shader packs in the `shaderpacks/` directory
- Loads and applies the selected shader pack immediately

#### 2. **Reload Shaders**
- Toggle to rescan and reload all available shader packs
- Useful for testing new shader packs without restarting the game
- Refreshes the shader pack list from disk

#### 3. **Shader Debug Mode**
- Enable debug output for shader compilation
- Logs detailed information about GLSL → SPIR-V compilation
- Useful for shader pack developers and troubleshooting

#### 4. **Hot Reload Shaders** (Experimental)
- Automatically reload shaders when shader files are modified
- Allows rapid iteration during development
- Note: Currently marked as experimental

## File Structure

The shader menu integration involves the following files:

```
src/main/java/net/vulkanmod/
├── config/
│   ├── Config.java                 # Added shader config fields
│   ├── gui/
│   │   └── VOptionScreen.java      # Added shader page registration
│   └── option/
│       ├── Options.java            # Added getShaderOpts() method
│       └── ShaderPackManager.java  # New: Manages shader packs
└── (VulkanShaders system files)

src/main/resources/assets/vulkanmod/lang/
└── en_us.json                      # Added shader-related strings
```

## Shader Pack Directory Structure

Shader packs should be placed in the `shaderpacks/` directory with the following structure:

```
shaderpacks/
├── MyShaderPack/
│   ├── pack.json
│   └── shaders/
│       ├── gbuffer.vert.glsl
│       ├── gbuffer.frag.glsl
│       └── (other shader files)
└── AnotherPack/
    ├── pack.json
    └── shaders/
        └── (shader files)
```

### pack.json Format

```json
{
  "name": "My Shader Pack",
  "version": "1.0",
  "description": "A custom shader pack for VulkanMod",
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
            }
          ]
        }
      ]
    }
  ]
}
```

## Configuration

Shader settings are automatically saved to the VulkanMod configuration file:

```json
{
  "shaderPack": "MyShaderPack",
  "shaderDebugMode": false,
  "hotReloadShaders": false
}
```

### Config Fields

- **`shaderPack`**: Name of the currently loaded shader pack (or "None")
- **`shaderDebugMode`**: Enable debug output for shader compilation
- **`hotReloadShaders`**: Enable hot reloading of shader files

## Integration with VulkanShaders

The shader menu integrates with the VulkanShaders system:

```java
// Load a shader pack
ShaderPackManager.scanAvailablePacks();
ShaderPack pack = ShaderPackManager.getPackByName("MyShaderPack");
ShaderPackManager.setCurrentPack(pack);

// Access the current pack
ShaderPack current = ShaderPackManager.getCurrentPack();

// Access VulkanShaders directly
VulkanShaders vs = VulkanShaders.getInstance();
VulkanPipeline pipeline = vs.getPipelineManager().getPipeline("gbuffer");
```

## Usage

1. **Access the Settings**: Open the video settings screen while in-game
2. **Navigate to Shaders Tab**: Click on the "Shaders" tab
3. **Select a Shader Pack**: Use the dropdown menu to select from available packs
4. **Apply Settings**: Click "Apply" to activate the shader pack
5. **Debug (Optional)**: Enable "Shader Debug Mode" to see compilation logs

## Troubleshooting

### Shader pack not appearing in the list
- Ensure the shader pack folder is in the `shaderpacks/` directory
- Check that `pack.json` exists and is valid JSON
- Review the debug logs for parsing errors

### Shader compilation fails
- Enable "Shader Debug Mode" for detailed error messages
- Check GLSL shader syntax for Vulkan compatibility
- Ensure shader files exist at the paths specified in pack.json

### Changes not applied
- Click the "Reload Shaders" option to refresh the pack list
- Use "Apply" button to confirm settings
- Restart the game if changes don't take effect

## Extending the System

### Adding New Shader Options

To add new shader-related options:

1. Add a field to [Config.java](../../config/Config.java)
2. Add a translation key to [en_us.json](../lang/en_us.json)
3. Add an Option to the `getShaderOpts()` method in [Options.java](Options.java)

Example:
```java
new SwitchOption(
    Component.translatable("vulkanmod.options.myOption"),
    value -> config.myOption = value,
    () -> config.myOption
).setTooltip(Component.translatable("vulkanmod.options.myOption.tooltip"))
```

### Creating Custom Shader Packs

See the [VulkanShaders Specification](../../../../spec.md) and [Quick Start Guide](../../../../QUICK_START.md) for detailed instructions on creating shader packs.

## Performance Notes

- Shader compilation happens at runtime, which may cause brief stuttering when loading packs
- Enable hot reloading only during development, as it can impact performance
- Debug mode may have a small performance impact; disable for production

## Future Enhancements

- Visual shader editor for beginners
- Real-time shader compilation error messages in-game
- Shader pack marketplace integration
- Per-shader performance profiling
- Shader pack preview thumbnails
