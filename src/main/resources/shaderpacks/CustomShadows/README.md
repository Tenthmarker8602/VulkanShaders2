# Custom Shadows Shader Pack

Enhanced shadow rendering for VulkanMod terrain.

## Features

- **Blue-Tinted Ambient Shadows**: Physically-based shadow color — shadows are illuminated by sky light, giving them a natural blue-purple tint instead of flat black
- **Soft Shadow Penumbra**: Noise-based shadow edge softening for realistic, organic shadow boundaries
- **Height-Based Ambient Occlusion**: Lower terrain (caves, ravines) receives deeper ambient shadowing
- **Enhanced Light/Shadow Contrast**: Increased dynamic range between lit and shadowed surfaces
- **Directional Light Simulation**: Sun direction derived from fog/sky color for time-of-day aware shadows
- **Shadow Density Gradient**: Shadows get subtly softer and lighter at distance
- **Warm Highlight Boost**: Sunlit areas get a subtle warm color enhancement for contrast with cool shadows

## Technical Details

This shader works within the standard terrain pipeline bindings:
- Uses the lightmap (Sampler2) sky-light component to detect shadow vs lit areas
- Extracts ambient occlusion data from Minecraft's vertex color smooth lighting
- Passes world position from vertex to fragment shader for spatial shadow calculations
- All shadow effects are computed analytically — no shadow map texture needed
