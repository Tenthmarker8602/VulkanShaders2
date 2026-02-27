package net.vulkanmod.render.shader.bsl;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transpiles BSL's GLSL 120 fragment shader code to Vulkan GLSL 450.
 * Handles: varying→in, texture2D→texture, uniform→UBO, gl_FragData→output,
 * integer uniform emulation via floats, and feature disabling.
 */
public class GLSLTranspiler {

    // ---- Patterns ----
    // All declaration patterns support comma-separated names: "type name1, name2, name3;"
    private static final Pattern VERSION_PATTERN = Pattern.compile("#version\\s+\\d+");
    private static final Pattern EXTENSION_PATTERN = Pattern.compile(
            "#extension\\s+GL_ARB_shader_texture_lod\\s*:\\s*enable");
    // varying TYPE name1[, name2[, ...]];
    private static final Pattern VARYING_PATTERN = Pattern.compile(
            "^(\\s*)varying\\s+(\\w+)\\s+(\\w+(?:\\s*,\\s*\\w+)*)\\s*;", Pattern.MULTILINE);
    private static final Pattern UNIFORM_SAMPLER_PATTERN = Pattern.compile(
            "^(\\s*)uniform\\s+(sampler\\w+)\\s+(\\w+)\\s*;", Pattern.MULTILINE);
    // uniform int name1[, name2];
    private static final Pattern UNIFORM_INT_PATTERN = Pattern.compile(
            "^(\\s*)uniform\\s+int\\s+(\\w+(?:\\s*,\\s*\\w+)*)\\s*;", Pattern.MULTILINE);
    private static final Pattern UNIFORM_IVEC2_PATTERN = Pattern.compile(
            "^(\\s*)uniform\\s+ivec2\\s+(\\w+(?:\\s*,\\s*\\w+)*)\\s*;", Pattern.MULTILINE);
    private static final Pattern UNIFORM_FLOAT_PATTERN = Pattern.compile(
            "^(\\s*)uniform\\s+float\\s+(\\w+(?:\\s*,\\s*\\w+)*)\\s*;", Pattern.MULTILINE);
    // uniform vec3/mat3/etc name1[, name2];
    private static final Pattern UNIFORM_VEC_PATTERN = Pattern.compile(
            "^(\\s*)uniform\\s+(vec[234]|mat[234](?:x[234])?)\\s+(\\w+(?:\\s*,\\s*\\w+)*)\\s*;", Pattern.MULTILINE);
    // uniform mat4 name1[, name2];
    private static final Pattern UNIFORM_MAT4_PATTERN = Pattern.compile(
            "^(\\s*)uniform\\s+mat4\\s+(\\w+(?:\\s*,\\s*\\w+)*)\\s*;", Pattern.MULTILINE);
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "^(\\s*)attribute\\s+(\\w+)\\s+(\\w+)\\s*;", Pattern.MULTILINE);

    private static final Pattern FRAG_DATA_PATTERN = Pattern.compile("gl_FragData\\[(\\d+)\\]");
    private static final Pattern FRAG_COLOR_PATTERN = Pattern.compile("gl_FragColor");

    // const float sunPathRotation = ...; — conflicts with UBO member
    private static final Pattern CONST_SUNPATH_PATTERN = Pattern.compile(
            "^(\\s*)const\\s+float\\s+sunPathRotation\\s*=.*?;", Pattern.MULTILINE);

    // Texture function replacements
    private static final Pattern TEX2D_PATTERN = Pattern.compile("texture2D\\s*\\(");
    private static final Pattern TEX2DLOD_PATTERN = Pattern.compile("texture2DLod\\s*\\(");
    private static final Pattern TEX2DGRAD_PATTERN = Pattern.compile("texture2DGradARB\\s*\\(");
    private static final Pattern TEX3D_PATTERN = Pattern.compile("texture3D\\s*\\(");
    private static final Pattern SHADOW2D_PATTERN = Pattern.compile("shadow2D\\s*\\(");

    // sampler2DShadow → sampler2D (we don't use shadow samplers)
    private static final Pattern SAMPLER2DSHADOW_PATTERN = Pattern.compile("sampler2DShadow");

    /**
     * Feature overrides to inject after settings.glsl has been merged.
     * These disable features requiring infrastructure we don't have.
     */
    private static final String FEATURE_OVERRIDES = """
            
            // ====== BSL Compatibility: Feature Overrides ======
            // SHADOW is enabled - shadow map pass is implemented
            #ifdef SHADOW_COLOR
            #undef SHADOW_COLOR
            #endif
            #ifdef SHADOW_CLOUD
            #undef SHADOW_CLOUD
            #endif
            #ifdef WEATHER_PERBIOME
            #undef WEATHER_PERBIOME
            #endif
            #ifdef MULTICOLORED_BLOCKLIGHT
            #undef MULTICOLORED_BLOCKLIGHT
            #endif
            #ifdef MCBL_SS
            #undef MCBL_SS
            #endif
            #ifdef MCBL_FOG
            #undef MCBL_FOG
            #endif
            #ifdef DISTANT_HORIZONS
            #undef DISTANT_HORIZONS
            #endif
            #ifdef VOXY
            #undef VOXY
            #endif
            #ifdef VOXY_PATCH
            #undef VOXY_PATCH
            #endif
            #ifdef WORLD_CURVATURE
            #undef WORLD_CURVATURE
            #endif
            #ifdef TAA
            #undef TAA
            #endif
            #ifdef ADVANCED_MATERIALS
            #undef ADVANCED_MATERIALS
            #endif
            #ifdef REFLECTION_RAIN
            #undef REFLECTION_RAIN
            #endif
            #ifdef REFLECTION_SPECULAR
            #undef REFLECTION_SPECULAR
            #endif
            #ifdef NIGHT_MOON_PHASE
            #undef NIGHT_MOON_PHASE
            #endif
            #ifdef OUTLINE_ENABLED
            #undef OUTLINE_ENABLED
            #endif
            // ====== End Feature Overrides ======
            
            """;

    /** Varying location assignments for BSL terrain shader (must match vertex shader) */
    private static final Map<String, Integer> VARYING_LOCATIONS = new LinkedHashMap<>();
    static {
        // Order matters: must match the custom vertex shader output layout
        VARYING_LOCATIONS.put("mat", 0);      // float
        VARYING_LOCATIONS.put("recolor", 1);   // float
        VARYING_LOCATIONS.put("texCoord", 2);  // vec2
        VARYING_LOCATIONS.put("lmCoord", 3);   // vec2
        VARYING_LOCATIONS.put("normal", 4);    // vec3
        VARYING_LOCATIONS.put("sunVec", 5);    // vec3
        VARYING_LOCATIONS.put("upVec", 6);     // vec3
        VARYING_LOCATIONS.put("eastVec", 7);   // vec3
        VARYING_LOCATIONS.put("color", 8);     // vec4
        // Extra for fragment position reconstruction:
        VARYING_LOCATIONS.put("worldPos_v", 9);  // vec3 — camera-relative world position
        VARYING_LOCATIONS.put("viewPos_v", 10);  // vec3 — view-space position
    }

    /** Water shader varying locations (extends terrain with extra varyings) */
    private static final Map<String, Integer> WATER_VARYING_LOCATIONS = new LinkedHashMap<>();
    static {
        WATER_VARYING_LOCATIONS.putAll(VARYING_LOCATIONS);
        // Water-specific varyings
        WATER_VARYING_LOCATIONS.put("dist", 11);       // float — distance from camera
        WATER_VARYING_LOCATIONS.put("binormal", 12);   // vec3
        WATER_VARYING_LOCATIONS.put("tangent", 13);    // vec3
        WATER_VARYING_LOCATIONS.put("viewVector", 14); // vec3
        WATER_VARYING_LOCATIONS.put("vTexCoord", 15);  // vec4
        // vTexCoordAM removed — exceeds max locations, stub in fragment
    }

    /** Sampler binding assignments */
    private static final Map<String, Integer> SAMPLER_BINDINGS = new LinkedHashMap<>();
    static {
        SAMPLER_BINDINGS.put("texture", 2);   // BSL's main texture → Sampler0 binding
        SAMPLER_BINDINGS.put("Sampler0", 2);
        SAMPLER_BINDINGS.put("colortex0", 2); // alias after rename
        SAMPLER_BINDINGS.put("Sampler2", 3);  // lightmap
        SAMPLER_BINDINGS.put("shadowtex0", 4); // shadow depth map
        SAMPLER_BINDINGS.put("shadowtex1", 5); // shadow depth (no translucents)
        SAMPLER_BINDINGS.put("noisetex", 6);  // noise texture
        // specular, normals, shadowcolor0 NOT mapped — their usages are inside
        // #ifdef ADVANCED_MATERIALS / SHADOW_COLOR which are undefined, so the preprocessor
        // removes all references. Commenting out declarations avoids binding collisions.
        // Stub samplers — each needs a unique, sequential binding matching pipeline auto-increment.
        // Pipeline order after 2 UBOs: Sampler0(2), Sampler2(3), Sampler4(4), Sampler5(5),
        // Sampler6(6), Sampler7(7), Sampler8(8), Sampler9(9), Sampler10(10)
        SAMPLER_BINDINGS.put("depthtex1", 7);  // depth buffer (stub)
        SAMPLER_BINDINGS.put("depthtex0", 8);  // main depth (stub)
        SAMPLER_BINDINGS.put("gaux1", 9);      // aux buffer 1 (stub)
        SAMPLER_BINDINGS.put("gaux2", 10);     // aux buffer 2 (stub)
        SAMPLER_BINDINGS.put("gcolor", 2);     // alias for colortex0
    }

    /**
     * Transpile a BSL GLSL 120 fragment shader (with all includes merged) to Vulkan GLSL 450.
     *
     * @param source The fully-merged GLSL 120 fragment shader source
     * @return Vulkan GLSL 450 compatible fragment shader
     */
    public static String transpileFragment(String source) {
        // Inject feature overrides right after settings.glsl include marker
        source = injectFeatureOverrides(source);

        // Remove const declarations that conflict with UBO members
        source = removeConflictingConsts(source);

        // Replace version
        source = VERSION_PATTERN.matcher(source).replaceFirst("#version 450");

        // Remove extensions
        source = EXTENSION_PATTERN.matcher(source).replaceAll("");

        // Collect and remove varying declarations → layout(location) in
        source = convertVaryingsToInputs(source);

        // Collect and remove uniform declarations
        source = convertUniforms(source);

        // Rename 'texture' sampler to 'colortex0' — 'texture' is a reserved built-in in GLSL 450
        // Must happen BEFORE replaceTextureFunctions() because texture2D/textureLod/textureGrad
        // contain 'texture' as prefix and \btexture\b won't match them (no word boundary before 2/L/G)
        source = source.replaceAll("\\btexture\\b", "colortex0");

        // Replace texture functions
        source = replaceTextureFunctions(source);

        // Remove sampler2DShadow overloads that would conflict after type conversion.
        // BSL defines e.g. texture2DShadow(sampler2DShadow, vec3) alongside
        // texture2DShadow(sampler2D, vec3). After converting sampler2DShadow→sampler2D,
        // these become duplicate definitions. Remove the sampler2DShadow version.
        source = removeShadowSamplerOverloads(source);

        // Replace shadow2DSampler types
        source = SAMPLER2DSHADOW_PATTERN.matcher(source).replaceAll("sampler2D");

        // Replace gl_FragData[N] → bsl_fragDataN
        source = convertFragmentOutputs(source);

        // Replace gl_FragColor → bsl_fragData0 (same output)
        source = FRAG_COLOR_PATTERN.matcher(source).replaceAll("bsl_fragData0");

        // Remove attribute declarations (fragment shader shouldn't have them, but just in case)
        source = ATTRIBUTE_PATTERN.matcher(source).replaceAll("// [BSL removed attribute] $0");

        // Add the preamble (output declarations, UBO block)
        source = addFragmentPreamble(source);

        return source;
    }

    /** Sky shader varying locations (minimal: alpha, sunVec, upVec) */
    private static final Map<String, Integer> SKY_VARYING_LOCATIONS = new LinkedHashMap<>();
    static {
        SKY_VARYING_LOCATIONS.put("alpha", 0);     // float
        SKY_VARYING_LOCATIONS.put("sunVec", 1);    // vec3
        SKY_VARYING_LOCATIONS.put("upVec", 2);     // vec3
    }

    /**
     * Transpile the BSL sky basic fragment shader (gbuffers_skybasic).
     * Uses sky-specific varying locations and stubs missing uniforms.
     */
    public static String transpileSkyFragment(String source) {
        Map<String, Integer> savedLocations = new LinkedHashMap<>(VARYING_LOCATIONS);
        VARYING_LOCATIONS.clear();
        VARYING_LOCATIONS.putAll(SKY_VARYING_LOCATIONS);

        try {
            // Add sky-specific feature overrides
            String skyFeatureOverrides = """

                    // ====== BSL Sky Feature Overrides ======
                    #ifdef SKY_DEFERRED
                    #undef SKY_DEFERRED
                    #endif
                    // Enable procedural sun/moon disc in sky shader
                    #ifndef SHADER_SUN_MOON
                    #define SHADER_SUN_MOON
                    #endif
                    // Stubs for missing uniforms
                    #define blindFactor 0.0
                    #define darknessFactor 0.0
                    #define bedrockLevel 0
                    // ====== End Sky Feature Overrides ======

                    """;

            source = injectFeatureOverrides(source);

            String endMarker = "// ====== End Feature Overrides ======";
            int endIdx = source.indexOf(endMarker);
            if (endIdx >= 0) {
                endIdx += endMarker.length();
                source = source.substring(0, endIdx) + skyFeatureOverrides + source.substring(endIdx);
            }

            source = removeConflictingConsts(source);
            source = VERSION_PATTERN.matcher(source).replaceFirst("#version 450");
            source = EXTENSION_PATTERN.matcher(source).replaceAll("");
            source = convertVaryingsToInputs(source);
            source = convertUniforms(source);
            source = source.replaceAll("\\btexture\\b", "colortex0");
            source = replaceTextureFunctions(source);
            source = removeShadowSamplerOverloads(source);
            source = SAMPLER2DSHADOW_PATTERN.matcher(source).replaceAll("sampler2D");
            source = convertFragmentOutputs(source);
            source = FRAG_COLOR_PATTERN.matcher(source).replaceAll("bsl_fragData0");
            source = ATTRIBUTE_PATTERN.matcher(source).replaceAll("// [BSL removed attribute] $0");
            source = addFragmentPreamble(source);

            return source;

        } finally {
            VARYING_LOCATIONS.clear();
            VARYING_LOCATIONS.putAll(savedLocations);
        }
    }

    /**
     * Transpile the BSL water fragment shader (gbuffers_water).
     * Uses water-specific varying locations and adds depthtex/gaux stubs.
     */
    public static String transpileWaterFragment(String source) {
        // Same pipeline as terrain but with water varying locations
        // Swap in water varying locations temporarily
        Map<String, Integer> savedLocations = new LinkedHashMap<>(VARYING_LOCATIONS);
        VARYING_LOCATIONS.clear();
        VARYING_LOCATIONS.putAll(WATER_VARYING_LOCATIONS);

        try {
            // Add extra water feature overrides
            String waterFeatureOverrides = """

                    // ====== BSL Water Feature Overrides ======
                    #ifdef REFLECTION_SPECULAR
                    #undef REFLECTION_SPECULAR
                    #endif
                    #ifdef REFLECTION_RAIN
                    #undef REFLECTION_RAIN
                    #endif
                    // vTexCoordAM stub — not available from VulkanMod terrain vertex format
                    #define vTexCoordAM vec4(0.0, 0.0, 1.0, 1.0)
                    // Stub missing uniforms
                    #define blindFactor 0.0
                    #define darknessFactor 0.0
                    // ====== End Water Feature Overrides ======

                    """;

            source = injectFeatureOverrides(source);

            // Insert water overrides after the main feature overrides
            String endMarker = "// ====== End Feature Overrides ======";
            int endIdx = source.indexOf(endMarker);
            if (endIdx >= 0) {
                endIdx += endMarker.length();
                source = source.substring(0, endIdx) + waterFeatureOverrides + source.substring(endIdx);
            }

            source = removeConflictingConsts(source);
            source = VERSION_PATTERN.matcher(source).replaceFirst("#version 450");
            source = EXTENSION_PATTERN.matcher(source).replaceAll("");
            source = convertVaryingsToInputs(source);
            source = convertUniforms(source);
            source = source.replaceAll("\\btexture\\b", "colortex0");
            source = replaceTextureFunctions(source);
            source = removeShadowSamplerOverloads(source);
            source = SAMPLER2DSHADOW_PATTERN.matcher(source).replaceAll("sampler2D");
            source = convertFragmentOutputs(source);
            source = FRAG_COLOR_PATTERN.matcher(source).replaceAll("bsl_fragData0");
            source = ATTRIBUTE_PATTERN.matcher(source).replaceAll("// [BSL removed attribute] $0");

            // depthtex1, depthtex0, gaux1, gaux2 are declared with unique bindings and
            // bound to stub textures at runtime (shadow depth image as placeholder).
            // No need to redirect reads — stubs provide valid depth-like values.

            source = addFragmentPreamble(source);

            return source;

        } finally {
            // Restore terrain varying locations
            VARYING_LOCATIONS.clear();
            VARYING_LOCATIONS.putAll(savedLocations);
        }
    }

    /**
     * Remove const declarations that would conflict with UBO members.
     */
    private static String removeConflictingConsts(String source) {
        source = CONST_SUNPATH_PATTERN.matcher(source).replaceAll(
                "$1// [BSL] const sunPathRotation moved to UBO");
        return source;
    }

    /**
     * Remove function overloads that use sampler2DShadow parameter type.
     * BSL defines e.g.:
     *   float texture2DShadow(sampler2D shadowtex, vec3 shadowPos) { ... }
     *   float texture2DShadow(sampler2DShadow shadowtex, vec3 shadowPos) { ... }
     * After converting sampler2DShadow → sampler2D, these become duplicates.
     * Remove the sampler2DShadow versions since we use software shadow comparison.
     */
    private static String removeShadowSamplerOverloads(String source) {
        // Match function definitions with sampler2DShadow parameter
        // Pattern: returnType funcName(sampler2DShadow ...) { ... }
        Pattern shadowOverloadPattern = Pattern.compile(
                "\\w+\\s+\\w+\\s*\\([^)]*sampler2DShadow[^)]*\\)\\s*\\{[^}]*\\}",
                Pattern.DOTALL);
        source = shadowOverloadPattern.matcher(source).replaceAll(
                "// [BSL] Removed sampler2DShadow overload (using sampler2D version)");
        return source;
    }

    private static String injectFeatureOverrides(String source) {
        // Insert after the first settings.glsl include
        String marker = "// [BSL] End include: /lib/settings.glsl";
        int idx = source.indexOf(marker);
        if (idx >= 0) {
            idx += marker.length();
            return source.substring(0, idx) + FEATURE_OVERRIDES + source.substring(idx);
        }
        // If no settings.glsl marker found, inject at the top after version
        Matcher m = VERSION_PATTERN.matcher(source);
        if (m.find()) {
            int end = m.end();
            return source.substring(0, end) + "\n" + FEATURE_OVERRIDES + source.substring(end);
        }
        return FEATURE_OVERRIDES + source;
    }

    private static String convertVaryingsToInputs(String source) {
        // Find all varying declarations and track them
        // Handles comma-separated: "varying float mat, recolor;" → two separate layout(location) in declarations
        Set<String> declaredVaryings = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = VARYING_PATTERN.matcher(source);
        int lastEnd = 0;

        while (m.find()) {
            sb.append(source, lastEnd, m.start());
            String type = m.group(2);
            String namesStr = m.group(3); // may be "mat, recolor" or just "normal"
            String[] names = namesStr.split("\\s*,\\s*");

            StringBuilder replacement = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                String name = names[i].trim();
                if (name.isEmpty()) continue;

                if (!declaredVaryings.contains(name)) {
                    Integer loc = VARYING_LOCATIONS.get(name);
                    if (loc != null) {
                        if (replacement.length() > 0) replacement.append("\n");
                        replacement.append("layout(location = ").append(loc).append(") in ")
                                .append(type).append(" ").append(name).append(";");
                    } else {
                        if (replacement.length() > 0) replacement.append("\n");
                        replacement.append("// [BSL] Unknown varying: ").append(type).append(" ").append(name);
                    }
                    declaredVaryings.add(name);
                } else {
                    if (replacement.length() > 0) replacement.append("\n");
                    replacement.append("// [BSL duplicate varying removed] ").append(name);
                }
            }
            sb.append(replacement);
            lastEnd = m.end();
        }
        sb.append(source, lastEnd, source.length());
        return sb.toString();
    }

    private static String convertUniforms(String source) {
        // Track which uniforms are found (for UBO generation)
        Set<String> intUniforms = new LinkedHashSet<>();
        Set<String> ivec2Uniforms = new LinkedHashSet<>();
        Set<String> floatUniforms = new LinkedHashSet<>();
        Set<String> vecUniforms = new LinkedHashSet<>();
        Set<String> mat4Uniforms = new LinkedHashSet<>();
        Map<String, Integer> samplerBindings = new LinkedHashMap<>();

        // Remove int uniform declarations (handles comma-separated: "int a, b;")
        Matcher m = UNIFORM_INT_PATTERN.matcher(source);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String namesStr = m.group(2);
            for (String name : namesStr.split("\\s*,\\s*")) {
                name = name.trim();
                if (!name.isEmpty()) intUniforms.add(name);
            }
            m.appendReplacement(sb, "// [BSL int uniform -> UBO] " + namesStr);
        }
        m.appendTail(sb);
        source = sb.toString();

        // Remove ivec2 uniform declarations (handles comma-separated)
        m = UNIFORM_IVEC2_PATTERN.matcher(source);
        sb = new StringBuffer();
        while (m.find()) {
            String namesStr = m.group(2);
            for (String name : namesStr.split("\\s*,\\s*")) {
                name = name.trim();
                if (!name.isEmpty()) ivec2Uniforms.add(name);
            }
            m.appendReplacement(sb, "// [BSL ivec2 uniform -> UBO] " + namesStr);
        }
        m.appendTail(sb);
        source = sb.toString();

        // Remove float uniform declarations (may have multiple names: "float near, far;")
        m = UNIFORM_FLOAT_PATTERN.matcher(source);
        sb = new StringBuffer();
        while (m.find()) {
            String names = m.group(2);
            for (String name : names.split("\\s*,\\s*")) {
                name = name.trim();
                if (!name.isEmpty()) floatUniforms.add(name);
            }
            m.appendReplacement(sb, "// [BSL float uniform -> UBO] " + names);
        }
        m.appendTail(sb);
        source = sb.toString();

        // Remove mat4 uniform declarations (handles comma-separated: "mat4 a, b;")
        m = UNIFORM_MAT4_PATTERN.matcher(source);
        sb = new StringBuffer();
        while (m.find()) {
            String namesStr = m.group(2);
            for (String name : namesStr.split("\\s*,\\s*")) {
                name = name.trim();
                if (!name.isEmpty()) mat4Uniforms.add(name);
            }
            m.appendReplacement(sb, "// [BSL mat4 uniform -> UBO] " + namesStr);
        }
        m.appendTail(sb);
        source = sb.toString();

        // Remove vec/mat uniform declarations (handles comma-separated)
        m = UNIFORM_VEC_PATTERN.matcher(source);
        sb = new StringBuffer();
        while (m.find()) {
            String type = m.group(2);
            String namesStr = m.group(3);
            for (String name : namesStr.split("\\s*,\\s*")) {
                name = name.trim();
                if (!name.isEmpty()) vecUniforms.add(type + " " + name);
            }
            m.appendReplacement(sb, "// [BSL " + type + " uniform -> UBO] " + namesStr);
        }
        m.appendTail(sb);
        source = sb.toString();

        // Convert sampler uniforms to layout(binding) declarations
        m = UNIFORM_SAMPLER_PATTERN.matcher(source);
        sb = new StringBuffer();
        while (m.find()) {
            String samplerType = m.group(2);
            String name = m.group(3);
            Integer binding = SAMPLER_BINDINGS.get(name);
            if (binding != null) {
                samplerBindings.put(name, binding);
                // Convert sampler2DShadow to sampler2D since we don't use shadow samplers
                String actualType = samplerType.equals("sampler2DShadow") ? "sampler2D" : samplerType;
                m.appendReplacement(sb,
                        "layout(binding = " + binding + ") uniform " + actualType + " " + name + ";");
            } else {
                // Unknown sampler — comment out
                m.appendReplacement(sb,
                        "// [BSL unknown sampler] uniform " + samplerType + " " + name + ";");
            }
        }
        m.appendTail(sb);
        source = sb.toString();

        return source;
    }

    private static String replaceTextureFunctions(String source) {
        source = TEX2DGRAD_PATTERN.matcher(source).replaceAll("textureGrad(");
        source = TEX2DLOD_PATTERN.matcher(source).replaceAll("textureLod(");
        source = TEX2D_PATTERN.matcher(source).replaceAll("texture(");
        source = TEX3D_PATTERN.matcher(source).replaceAll("texture(");
        // shadow2D(sampler, vec3(xy, z)) → bsl_shadow2D(sampler, vec3(xy, z))
        // We use a manual comparison function since we convert sampler2DShadow to sampler2D
        source = SHADOW2D_PATTERN.matcher(source).replaceAll("bsl_shadow2D(");
        return source;
    }

    private static String convertFragmentOutputs(String source) {
        // Find the highest gl_FragData index used
        Set<Integer> usedOutputs = new TreeSet<>();
        Matcher m = FRAG_DATA_PATTERN.matcher(source);
        while (m.find()) {
            usedOutputs.add(Integer.parseInt(m.group(1)));
        }

        // Replace gl_FragData[N] with bsl_fragDataN
        source = FRAG_DATA_PATTERN.matcher(source).replaceAll("bsl_fragData$1");

        return source;
    }

    private static String addFragmentPreamble(String source) {
        // Build preamble with output declarations and UBO block
        StringBuilder preamble = new StringBuilder();

        // Fragment outputs (for single-pass terrain, only output 0)
        preamble.append("\n// ====== BSL Fragment Outputs ======\n");
        preamble.append("layout(location = 0) out vec4 bsl_fragData0;\n");
        // Additional outputs for MRT — declare as regular variables since we only
        // have a single color attachment. Writes are silently absorbed.
        for (int i = 1; i <= 7; i++) {
            if (source.contains("bsl_fragData" + i)) {
                preamble.append("vec4 bsl_fragData").append(i).append(" = vec4(0.0); // MRT stub\n");
            }
        }

        // UBO block with all BSL fragment uniforms
        preamble.append("\n// ====== BSL Fragment UBO ======\n");
        preamble.append("layout(binding = 1) uniform BSL_FragmentUBO {\n");
        // Matrices first (biggest alignment)
        preamble.append("    mat4 gbufferProjection;\n");
        preamble.append("    mat4 gbufferProjectionInverse;\n");
        preamble.append("    mat4 gbufferModelView;\n");
        preamble.append("    mat4 gbufferModelViewInverse;\n");
        preamble.append("    mat4 shadowProjection;\n");
        preamble.append("    mat4 shadowModelView;\n");
        // Vec4
        preamble.append("    vec4 FogColor_bsl;\n");
        // Vec3
        preamble.append("    vec3 cameraPosition;\n");
        preamble.append("    float _pad0;\n"); // std140 padding
        preamble.append("    vec3 relativeEyePosition;\n");
        preamble.append("    float _pad1;\n");
        // Floats
        preamble.append("    float timeAngle;\n");
        preamble.append("    float timeBrightness;\n");
        preamble.append("    float frameTimeCounter;\n");
        preamble.append("    float rainStrength;\n");
        preamble.append("    float nightVision;\n");
        preamble.append("    float shadowFade;\n");
        preamble.append("    float near;\n");
        preamble.append("    float far;\n");
        preamble.append("    float viewWidth;\n");
        preamble.append("    float viewHeight;\n");
        preamble.append("    float screenBrightness;\n");
        preamble.append("    float cloudHeight;\n");
        preamble.append("    float endFlashIntensity;\n");
        preamble.append("    float aspectRatio;\n");
        preamble.append("    float AlphaCutout;\n");
        preamble.append("    float sunPathRotation;\n");
        // Int uniforms stored as floats
        preamble.append("    float bsl_frameCounter;\n");
        preamble.append("    float bsl_isEyeInWater;\n");
        preamble.append("    float bsl_moonPhase;\n");
        preamble.append("    float bsl_worldTime;\n");
        // ivec2 stored as vec2
        preamble.append("    float bsl_eyeBrightnessSmooth_x;\n");
        preamble.append("    float bsl_eyeBrightnessSmooth_y;\n");
        // Dynamic handlight stubs
        preamble.append("    float bsl_heldBlockLightValue;\n");
        preamble.append("    float bsl_heldBlockLightValue2;\n");
        // Sun/moon position in view space (OptiFine convention, vec3 + std140 padding)
        preamble.append("    vec3 sunPosition;\n");
        preamble.append("    float _padSun;\n");
        preamble.append("    vec3 moonPosition;\n");
        preamble.append("    float _padMoon;\n");
        preamble.append("};\n");

        // Integer uniform aliases — use global variables instead of #defines
        // so that BSL code can redeclare the same name as a local variable (shadowing).
        // BSL's sunmoon.glsl has "float moonPhase = ..." which shadows the int uniform.
        preamble.append("\n// ====== BSL Integer Uniform Aliases ======\n");
        preamble.append("int frameCounter = int(bsl_frameCounter);\n");
        preamble.append("int isEyeInWater = int(bsl_isEyeInWater);\n");
        preamble.append("int moonPhase = int(bsl_moonPhase);\n");
        preamble.append("int worldTime = int(bsl_worldTime);\n");
        preamble.append("ivec2 eyeBrightnessSmooth = ivec2(int(bsl_eyeBrightnessSmooth_x), int(bsl_eyeBrightnessSmooth_y));\n");
        preamble.append("int heldBlockLightValue = int(bsl_heldBlockLightValue);\n");
        preamble.append("int heldBlockLightValue2 = int(bsl_heldBlockLightValue2);\n");

        // Note: 'texture' sampler is renamed to 'colortex0' in transpileFragment()
        // to avoid conflict with GLSL 450 built-in texture() function.

        // RGB2HSV is defined in BSL's hardcodedEmission.glsl — do NOT duplicate here.

        // Shadow comparison function (replaces hardware sampler2DShadow)
        preamble.append("\n// ====== BSL Shadow Comparison ======\n");
        preamble.append("float bsl_shadow2D(sampler2D shadowSampler, vec3 shadowCoord) {\n");
        preamble.append("    return float(texture(shadowSampler, shadowCoord.xy).r > shadowCoord.z);\n");
        preamble.append("}\n");
        preamble.append("vec4 bsl_shadow2D_vec4(sampler2D shadowSampler, vec3 shadowCoord) {\n");
        preamble.append("    float s = float(texture(shadowSampler, shadowCoord.xy).r > shadowCoord.z);\n");
        preamble.append("    return vec4(s, s, s, 1.0);\n");
        preamble.append("}\n");

        // Fog function compatibility
        preamble.append("\n// ====== BSL Fog Compatibility ======\n");
        preamble.append("float linear_fog_value_bsl(float dist, float start, float end) {\n");
        preamble.append("    if (dist <= start) return 0.0;\n");
        preamble.append("    if (dist >= end) return 1.0;\n");
        preamble.append("    return (dist - start) / (end - start);\n");
        preamble.append("}\n");

        // Insert preamble right after #version line
        int versionEnd = source.indexOf('\n', source.indexOf("#version"));
        if (versionEnd < 0) versionEnd = 0;

        return source.substring(0, versionEnd + 1) + preamble + source.substring(versionEnd + 1);
    }
}
