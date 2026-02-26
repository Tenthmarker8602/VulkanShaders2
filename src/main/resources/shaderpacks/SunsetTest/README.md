# Sunset Test Shader Pack

A second test shader pack with dramatic orange-to-red gradient effect for easy visual verification of shader compilation.

## What to Look For

When this shader pack is active, you should see:

- **Vibrant Orange Gradient** - A smooth transition from bright orange at the top to deep red at the bottom
- **Sunset Effect** - Creates a dramatic sunset/screen tint effect across the entire viewport
- **Smooth Color Transition** - Unlike the checkerboard, this one shows smooth interpolation

## How to Use

1. Open Video Settings → Shaders tab
2. Select "SunsetTest" from the shader pack list
3. The entire screen should immediately tint orange-to-red

## Visual Verification

This is a **different test than TestShaders**:
- **TestShaders** = Bright green/cyan checkerboard (pixelated pattern)
- **SunsetTest** = Orange-to-red smooth gradient (sunset effect)

If both work, your shader compilation pipeline is fully functional!

## Files

- **test_sunset.vert** - Vertex shader with fixed orange color
- **test_sunset.frag** - Fragment shader with vertical gradient
- **pack.json** - Shader pack configuration

## Technical Difference

While TestShaders uses hardcoded per-pixel checkerboard logic in the fragment shader, SunsetTest demonstrates:
- Smooth color interpolation using `mix()`
- Viewport-aware coordinates for gradient effects
- A different visual result to confirm multiple shaders work
