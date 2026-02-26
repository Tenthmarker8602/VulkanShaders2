# VulkanShaders Testing Guide

This guide shows how to verify that VulkanShaders shader compilation is working correctly using the included example shader packs.

## Quick Start

The mod includes **two test shader packs** designed to make shader compilation visually obvious:

### Test Shader Pack 1: Checkerboard (Green & Cyan)

**Path:** `shaderpacks/TestShaders/`

**Visual Effect:** Large bright green and cyan 16×16 pixel checkerboard pattern covering the entire screen.

```
Screenshot expectation:
████████████████████████████
████ Green ████ Cyan  ████ Green ████
████████████████████████████
████ Cyan  ████ Green ████ Cyan  ████
████████████████████████████
```

**Color Values:**
- Green squares: RGB(0, 255, 0) - Pure neon green
- Cyan squares: RGB(0, 255, 255) - Pure bright cyan

**verification:** If you see a bright alternating green/cyan checkerboard, your shaders are compiling and rendering correctly!

---

### Test Shader Pack 2: Sunset Gradient (Orange to Red)

**Path:** `shaderpacks/SunsetTest/`

**Visual Effect:** Smooth vertical gradient from bright orange at the top to deep red at the bottom, creating a "sunset" overlay effect.

```
Screenshot expectation:
═══════════════════════════════════════
║                                     ║
║   Bright Orange (RGB 255, 179, 0)   ║  ← Top of screen
║                                     ║
║  Medium Orange-Red (RGB 255, 102, 0)║
║                                     ║
║    Deep Red (RGB 255, 51, 0)        ║  ← Bottom of screen
║                                     ║
═══════════════════════════════════════
```

**Verification:** If you see a smooth sunset gradient, your shader interpolation is working!

---

## How to Activate Test Shaders

1. **Start Minecraft with VulkanMod**
   ```bash
   ./gradlew runClient
   ```

2. **Open Video Settings**
   - Press `ESC` to open pause/menu
   - Click "Options..." → "Video Settings..."

3. **Navigate to Shaders Tab**
   - Look for a "Shaders" tab between other graphics options
   - The tab should appear if ShaderPackManager integration is working

4. **Select a Test Shader Pack**
   - Dropdown menu should show available packs:
     - `TestShaders` (Checkerboard)
     - `SunsetTest` (Sunset Gradient)
   - Select one and confirm

5. **Observe the Effect**
   - Screen should **immediately** change to show the selected shader effect
   - The world will be completely tinted with the shader's colors

---

## Technical Verification

### Console Output

If shader compilation is working, you should see messages like:

```
[Render thread/INFO] (Minecraft) [STDOUT]: SPIRVUtils: Using system glslc compiler for shader compilation
[Render thread/INFO] (Minecraft) [STDOUT]: Shader compiled successfully for test_color.vert
[Render thread/INFO] (Minecraft) [STDOUT]: Shader compiled successfully for test_color.frag
```

### Compilation Process

1. **Shader Loading:** VulkanShaders loads `pack.json` from shader pack directory
2. **Pipeline Definition:** Reads vertex/fragment shader paths from JSON
3. **glslc Compilation:** System `glslc` compiler translates GLSL → SPIR-V bytecode
4. **Module Creation:** Creates shader modules from bytecode
5. **Pipeline Creation:** Assembles complete graphics pipeline
6. **Rendering:** VulkanMod uses pipeline to render the test effect

---

## What Can Go Wrong (and How to Fix)

| Problem | Cause | Solution |
|---------|-------|----------|
| Shaders tab not visible | ShaderPackManager not initialized | Restart client, check console for errors |
| "None" selected by default | No packs detected in shaderpacks/ dir | Ensure TestShaders/SunsetTest directories exist in build |
| Screen doesn't change color | Shader not activating | Check console for glslc compilation errors |
| Crash on shader selection | Invalid GLSL syntax | Check .vert/.frag files for syntax errors |
| Compilation timeout | glslc not installed or too slow | Run `glslc --version` in terminal |

---

## Example Shader Files Explained

### test_color.vert
```glsl
#version 460
// Outputs bright green color
layout(location = 0) out vec4 out_color;
void main() {
    gl_Position = camera.projection * camera.view * vec4(in_position, 1.0);
    out_color = vec4(0.0, 1.0, 0.0, 1.0);  // Green!
}
```

### test_color.frag  
```glsl
#version 460
// Creates 16x16 checkerboard using fragment coordinates
void main() {
    vec2 coord = gl_FragCoord.xy;
    float pattern = mod(floor(coord.x / 16.0) + floor(coord.y / 16.0), 2.0);
    vec3 color = mix(vec3(0.0, 1.0, 0.0),   // Green
                     vec3(0.0, 1.0, 1.0),   // Cyan
                     pattern);
    out_color = vec4(color, 1.0);
}
```

### test_sunset.frag
```glsl
#version 460
// Creates vertical gradient from orange (top) to red (bottom)
void main() {
    float gradient = clamp(coord.y / 1080.0, 0.0, 1.0);
    vec3 color = mix(botColor, topColor, gradient);
    out_color = vec4(color, 1.0);
}
```

---

## Performance Notes

These test shaders are **extremely lightweight**:
- No complex calculations
- No texture lookups
- No loops or branching per-pixel
- Direct color output

They run at full frame rate and won't impact performance.

---

## Next Steps

Once you've verified the test shaders work:

1. **Create Custom Shaders** - Try modifying the color values or patterns
2. **Add Includes** - Use `#include` directives with shaders in `include/` directory  
3. **Complex Pipelines** - Add lighting calculations, texturing, or post-processing
4. **Submit Issues** - If tests fail, gather console logs and report the issue

---

## Shader Pack Structure

```
shaderpacks/
├── TestShaders/
│   ├── pack.json          ← Shader pack config
│   └── README.md          ← Pack documentation
├── SunsetTest/
│   ├── pack.json
│   └── README.md

assets/vulkanmod/shaders/
├── core/
│   ├── test_color.vert    ← Checkerboard shader (vertex)
│   ├── test_color.frag    ← Checkerboard shader (fragment)
│   ├── test_sunset.vert   ← Sunset gradient (vertex)
│   └── test_sunset.frag   ← Sunset gradient (fragment)
├── include/
│   ├── light.glsl
│   ├── fog.glsl
│   └── ...
```

---

## Troubleshooting Commands

```bash
# Check if glslc is installed
glslc --version

# Manually compile a test shader to SPIR-V
glslc -fshader-stage=vertex test_color.vert -o test_color.spv

# Check Minecraft console output (from launcher logs)
tail -f logs/latest.log | grep -i "spir\|shader\|glslc"

# Verify build includes shaders
unzip -l build/libs/vulkanshaders-*.jar | grep -i shader
```

---

## Success Checklist

✅ Shaders tab appears in Video Settings  
✅ TestShaders shows in pack list  
✅ SunsetTest shows in pack list  
✅ Selecting TestShaders → green/cyan checkerboard appears  
✅ Selecting SunsetTest → orange-to-red gradient appears  
✅ Console shows no compilation errors  
✅ Switching between packs works smoothly  
✅ Performance is unaffected (100+ FPS)

If all checkboxes pass, **VulkanShaders shader compilation is fully functional!**
