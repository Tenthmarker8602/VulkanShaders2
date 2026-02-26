# Test Shaders - Quick Reference

Two example shader packs have been created to visually verify shader compilation is working.

## The Test Shaders

### 1. **TestShaders** (Checkerboard Pattern)
- **Visual:** Bright green & cyan 16×16 checkerboard
- **Color:** Pure neon green (0, 255, 0) alternating with pure cyan (0, 255, 255)
- **Files:** `test_color.vert`, `test_color.frag`
- **Use:** See immediate checkerboard pattern across entire screen

### 2. **SunsetTest** (Gradient Effect)
- **Visual:** Orange → Red vertical gradient (sunset effect)
- **Colors:** Bright orange at top, deep red at bottom
- **Files:** `test_sunset.vert`, `test_sunset.frag`
- **Use:** See smooth color transition from top to bottom

## How to Test

```bash
# 1. Build the project
./gradlew build -x test

# 2. Run the client
./gradlew runClient

# 3. In game:
#    ESC → Options → Video Settings → Shaders tab
#    Select "TestShaders" or "SunsetTest"
#    Screen immediately shows the shader effect
```

## What SUCCESS Looks Like

✅ **TestShaders Selected:** Entire screen becomes a bright alternating green/cyan checkerboard  
✅ **SunsetTest Selected:** Entire screen has an orange-to-red sunset gradient  
✅ **Console:** Shows "SPIRVUtils: Using system glslc compiler for shader compilation"  
✅ **No Crashes:** Switching between packs works smoothly  
✅ **No Errors:** Console shows no GLSL compilation errors  

## What the Shaders Prove

| Feature | Tested By |
|---------|-----------|
| glslc detection | Console message on startup |
| GLSL → SPIR-V compilation | Shader loads without errors |
| Shader module creation | Test effect appears on screen |
| Pipeline creation | Color/pattern rendered correctly |
| Descriptor binding | Camera matrices passed to shader |
| UI integration | Shaders tab selectable in options |

## Files Created

```
src/main/resources/
├── shaderpacks/
│   ├── TestShaders/
│   │   ├── pack.json          ← Configuration
│   │   └── README.md          ← Details
│   └── SunsetTest/
│       ├── pack.json          ← Configuration
│       └── README.md          ← Details
│
└── assets/vulkanmod/shaders/
    └── core/
        ├── test_color.vert    ← Checkerboard (vertex shader)
        ├── test_color.frag    ← Checkerboard (fragment shader)
        ├── test_sunset.vert   ← Sunset gradient (vertex shader)
        └── test_sunset.frag   ← Sunset gradient (fragment shader)
```

## Example Output When Working

**Console (when shader loads):**
```
[Render thread/INFO] SPIRVUtils: Using system glslc compiler for shader compilation
[Render thread/INFO] Shader compilation completed successfully
```

**Visual (TestShaders):**
```
████ Green ████ Cyan  ████ Green ████
████ Cyan  ████ Green ████ Cyan  ████
████ Green ████ Cyan  ████ Green ████
```

**Visual (SunsetTest):**
```
═══════════════════════════════════════
      Bright Orange (RGB 255, 179, 0)
       Medium Orange (RGB 255, 102, 0)
           Deep Red (RGB 255, 51, 0)
═══════════════════════════════════════
```

## Modifying the Test Shaders

To experiment:

1. Edit `src/main/resources/assets/vulkanmod/shaders/core/test_*.{vert,frag}`
2. Change colors: `vec4(R, G, B, A)` where 0-1 is 0-255
3. Add patterns: Modify the pattern calculation in fragment shader
4. Rebuild: `./gradlew build -x test`
5. Rerun: `./gradlew runClient` and reload the shader pack

## Common Issues

| Issue | Fix |
|-------|-----|
| "None" selected | First time setup - packs scan on startup |
| Screen doesn't change | Try selecting "None" first, then reselect |
| Shader not found | Verify `pack.json` has correct shader paths |
| Compilation error | Check `.vert/.frag` files for syntax errors |
| glslc not found | Install: `yay -S shaderc-tools` or system package manager |

## Full Documentation

See `SHADER_TESTING.md` for comprehensive testing guide including troubleshooting, technical details, and performance notes.
