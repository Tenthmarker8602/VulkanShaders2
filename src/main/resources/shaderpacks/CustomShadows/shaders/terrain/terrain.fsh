#version 450

// ============================================================================
// CustomShadows Pack - Fragment Shader
// ============================================================================
// Sun-based directional shadow rendering:
//   1. Real sun direction from Minecraft's Light0_Direction uniform
//   2. Per-face normals reconstructed via dFdx/dFdy on world position
//   3. N·L directional lighting — faces away from sun are shadowed
//   4. Blue-tinted ambient shadows (sky hemisphere illumination)
//   5. Warm sunlit highlight boost that shifts with sun color
//   6. Soft shadow penumbra via noise at shadow/light boundary
//   7. Height-based ambient occlusion for caves and ravines
//   8. Contact shadow darkening at AO boundaries
//   9. Sun elevation drives shadow strength (low sun = long shadows)
// ============================================================================

// ---- Fog functions (inlined from fog.glsl) ----

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance,
                      float environmentalStart, float environmantalEnd,
                      float renderDistanceStart, float renderDistanceEnd) {
    return max(
        linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd),
        linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd)
    );
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance,
               float environmentalStart, float environmantalEnd,
               float renderDistanceStart, float renderDistanceEnd, vec4 fogColor) {
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
                                     environmentalStart, environmantalEnd,
                                     renderDistanceStart, renderDistanceEnd);
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

// ---- Noise functions for shadow softening ----

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    float a = hash12(i + vec2(0.0, 0.0));
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < 3; i++) {
        value += amplitude * valueNoise(p * frequency);
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

// ---- Bindings ----

layout(binding = 2) uniform sampler2D Sampler0;

layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
    vec3 Light0_Direction;  // Primary sun/moon direction from Minecraft
    vec3 Light1_Direction;  // Secondary fill light direction
};

// ---- Inputs from vertex shader ----

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in float sphericalVertexDistance;
layout(location = 3) in float cylindricalVertexDistance;
layout(location = 4) in vec3 worldPos;
layout(location = 5) in vec4 rawColor;
layout(location = 6) in vec4 lightmapColor;

layout(location = 0) out vec4 fragColor;

// ---- Configuration constants ----

const vec3  SKY_SHADOW_TINT   = vec3(0.18, 0.22, 0.45);  // Blue shadow tint (sky hemisphere)
const vec3  INDOOR_SHADOW     = vec3(0.14, 0.12, 0.16);   // Dark grey-purple for indoor shadows
const float SHADOW_STRENGTH   = 0.60;                      // Max directional shadow darkening
const float AMBIENT_MIN       = 0.25;                      // Minimum ambient light in shadow
const float AO_STRENGTH       = 0.35;                      // Vertex AO darkening strength
const float HEIGHT_AO_RANGE   = 80.0;                      // Y range for height-based AO
const float CONTACT_SHADOW_K  = 0.25;                      // Extra darkening at AO edges
const float NOISE_SCALE       = 3.0;                       // Shadow noise spatial frequency
const float NOISE_STRENGTH    = 0.10;                      // How much noise perturbs shadow edge
const float CONTRAST_BOOST    = 1.12;                      // Saturation/contrast multiplier
const float SUN_FILL_RATIO    = 0.75;                      // Light0 vs Light1 blend

void main() {
    // ---- Base color ----
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < AlphaCutout) {
        discard;
    }

    // ---- Reconstruct face normal from world position derivatives ----
    vec3 dPdx = dFdx(worldPos);
    vec3 dPdy = dFdy(worldPos);
    vec3 faceNormal = normalize(cross(dPdx, dPdy));

    // ---- Sun direction ----
    vec3 sunDir = normalize(Light0_Direction);
    vec3 fillDir = normalize(Light1_Direction);

    // Sun elevation: how high the sun is (0 = horizon, 1 = zenith)
    float sunElevation = clamp(sunDir.y, 0.0, 1.0);
    // Low sun = stronger, longer shadows; high sun = softer
    float sunIntensity = smoothstep(-0.05, 0.5, sunDir.y);
    // Night detection: when sun is below horizon
    float isDay = smoothstep(-0.1, 0.15, sunDir.y);

    // ---- Directional N·L lighting ----
    // Primary sun light
    float NdotL_sun = dot(faceNormal, sunDir);
    // Secondary fill light (opposite side, softer)
    float NdotL_fill = dot(faceNormal, fillDir);

    // Combine: sun is dominant, fill is subtle ambient
    float directLight = max(0.0, NdotL_sun) * SUN_FILL_RATIO
                       + max(0.0, NdotL_fill) * (1.0 - SUN_FILL_RATIO) * 0.5;

    // ---- Lightmap and AO data ----
    float vertexAO = dot(rawColor.rgb, vec3(0.333));
    float lightmapBrightness = dot(lightmapColor.rgb, vec3(0.299, 0.587, 0.114));
    float baseLightLevel = vertexAO * lightmapBrightness;

    // ---- Shadow factor from sun direction ----
    // Faces pointing away from sun get shadowed
    // Combine N·L with lightmap (lightmap gates whether sun can reach at all)
    float sunShadow = 1.0 - smoothstep(0.0, 0.45, directLight);
    // Only apply directional shadows where lightmap says sun reaches (outdoors)
    float outdoorFactor = smoothstep(0.3, 0.8, lightmapBrightness);
    float directionalShadow = sunShadow * outdoorFactor * sunIntensity * SHADOW_STRENGTH;

    // ---- Soft shadow penumbra (noise at shadow boundary) ----
    float noise = fbm(worldPos.xz * NOISE_SCALE + worldPos.y * 0.5);
    float noisyNdotL = directLight + (noise - 0.5) * NOISE_STRENGTH;
    float softDirectional = 1.0 - smoothstep(0.0, 0.4, noisyNdotL);
    // Distance-based softening: far away = softer shadows
    float distSoftness = smoothstep(8.0, 64.0, sphericalVertexDistance);
    directionalShadow = mix(directionalShadow, softDirectional * outdoorFactor * sunIntensity * SHADOW_STRENGTH, distSoftness * 0.5);

    // ---- Lightmap-based ambient shadow (caves, indoors) ----
    float ambientShadow = (1.0 - smoothstep(0.2, 0.75, baseLightLevel)) * (1.0 - outdoorFactor) * 0.4;

    // ---- Combined shadow intensity ----
    float totalShadow = clamp(directionalShadow + ambientShadow, 0.0, SHADOW_STRENGTH);

    // ---- Shadow color tinting ----
    // Outdoor shadows are blue (lit by sky), indoor shadows are neutral-dark
    vec3 shadowTint = mix(INDOOR_SHADOW, SKY_SHADOW_TINT, outdoorFactor);

    // At night, shadows are overall more neutral/dark
    shadowTint = mix(vec3(0.12, 0.11, 0.14), shadowTint, isDay);

    // Apply shadow: blend toward tinted shadow color
    vec3 shadowed = mix(color.rgb, color.rgb * shadowTint * (1.0 / SHADOW_STRENGTH) * 1.3, totalShadow);

    // ---- Sun warmth boost on lit faces ----
    // Sun color shifts: high sun = white-warm, low sun = orange-gold
    float sunWarmth = 1.0 - sunElevation; // More warmth when sun is low
    vec3 sunColor = mix(
        vec3(1.05, 1.02, 0.95),   // High noon: nearly white
        vec3(1.3, 0.9, 0.55),     // Sunset/sunrise: golden-orange
        smoothstep(0.1, 0.7, sunWarmth)
    );

    // Apply warm highlights to sunlit faces
    float highlightFactor = smoothstep(0.3, 0.9, directLight) * outdoorFactor * isDay;
    shadowed *= mix(vec3(1.0), sunColor, highlightFactor * 0.35);

    // ---- Height-based ambient occlusion ----
    float heightNorm = clamp(worldPos.y / HEIGHT_AO_RANGE, 0.0, 1.0);
    float heightAO = mix(1.0 - AO_STRENGTH, 1.0, smoothstep(0.0, 0.5, heightNorm));
    shadowed *= heightAO;

    // ---- Contact shadows at AO boundaries ----
    float aoGradient = fwidth(vertexAO);
    float contactShadow = 1.0 - smoothstep(0.0, 0.12, aoGradient) * CONTACT_SHADOW_K;
    shadowed *= contactShadow;

    // ---- Contrast enhancement ----
    float luma = dot(shadowed, vec3(0.299, 0.587, 0.114));
    shadowed = mix(vec3(luma), shadowed, CONTRAST_BOOST);

    // ---- Ensure minimum ambient so nothing goes pure black ----
    shadowed = max(shadowed, color.rgb * AMBIENT_MIN);

    color.rgb = clamp(shadowed, 0.0, 1.0);

    // ---- Apply fog ----
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
                          FogEnvironmentalStart, FogEnvironmentalEnd,
                          FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
