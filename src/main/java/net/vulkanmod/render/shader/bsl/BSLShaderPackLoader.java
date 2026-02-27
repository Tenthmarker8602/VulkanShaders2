package net.vulkanmod.render.shader.bsl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.vulkanmod.render.PipelineManager;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads BSL-format shader packs (OptiFine/Iris style) and compiles them
 * into Vulkan GraphicsPipeline objects.
 *
 * Strategy: Single-pass terrain replacement.
 * - Reads the world0/gbuffers_terrain dimension wrapper
 * - Resolves all #include directives
 * - Transpiles the GLSL 120 fragment shader to Vulkan GLSL 450
 * - Generates a custom vertex shader for VulkanMod's compressed terrain format
 * - Creates a Pipeline.Builder with the BSL UBO layout
 * - Compiles to SPIR-V and creates the Vulkan pipeline
 */
public class BSLShaderPackLoader {

    /**
     * Check if a shader pack directory is a BSL-format (OptiFine/Iris) pack.
     */
    public static boolean isBSLFormat(Path packDir) {
        Path shadersDir = packDir.resolve("shaders");
        if (!Files.isDirectory(shadersDir)) return false;

        // BSL packs have shaders.properties and program/ directory
        boolean hasProperties = Files.exists(shadersDir.resolve("shaders.properties"));
        boolean hasProgram = Files.isDirectory(shadersDir.resolve("program"));

        // Also check for world0/ wrapper files (dimension-specific shaders)
        boolean hasWorld0 = Files.isDirectory(shadersDir.resolve("world0"));

        return hasProperties && hasProgram && (hasWorld0 || hasTerrainGlsl(shadersDir));
    }

    private static boolean hasTerrainGlsl(Path shadersDir) {
        return Files.exists(shadersDir.resolve("program").resolve("gbuffers_terrain.glsl"));
    }

    /**
     * Load the BSL pack and apply its terrain shader.
     *
     * @param packDir The root directory of the shader pack (containing shaders/)
     * @return true if the terrain pipeline was successfully created and applied
     */
    public static boolean loadAndApply(Path packDir) {
        Path shadersDir = packDir.resolve("shaders");
        System.out.println("[BSL] Loading BSL-format shader pack from: " + packDir);

        try {
            // Update BSL per-frame state
            BSLUniformProvider.updatePerFrame();

            // Step 1: Read and merge the BSL fragment shader source
            String fragmentSource = readBSLFragmentSource(shadersDir);
            if (fragmentSource == null) {
                System.err.println("[BSL] Failed to read BSL fragment source");
                return false;
            }

            // Step 2: Resolve all #include directives
            fragmentSource = BSLIncludeResolver.resolve(shadersDir, fragmentSource);
            System.out.println("[BSL] Resolved includes, fragment source: " + fragmentSource.length() + " chars");

            // Step 3: Transpile from GLSL 120 to Vulkan GLSL 450
            String transpiledFragment = GLSLTranspiler.transpileFragment(fragmentSource);
            System.out.println("[BSL] Transpiled fragment shader: " + transpiledFragment.length() + " chars");

            // Step 4: Generate the BSL-compatible vertex shader
            String vertexSource = BSLVertexShaderGenerator.generate();

            // Debug: dump transpiled shaders
            dumpShader(packDir, "bsl_terrain.fsh", transpiledFragment);
            dumpShader(packDir, "bsl_terrain.vsh", vertexSource);

            // Step 5: Build the pipeline JSON config programmatically
            JsonObject config = buildPipelineConfig();

            // Step 6: Create Pipeline.Builder with compressed terrain format
            Pipeline.Builder builder = new Pipeline.Builder(
                    CustomVertexFormat.COMPRESSED_TERRAIN, "bsl_terrain");
            builder.parseBindings(config);

            // Step 7: Compile GLSL to SPIR-V
            System.out.println("[BSL] Compiling vertex shader...");
            SPIRVUtils.SPIRV vertSpirv = SPIRVUtils.compileShader(
                    "bsl_terrain.vsh", vertexSource, SPIRVUtils.ShaderKind.VERTEX_SHADER);

            System.out.println("[BSL] Compiling fragment shader...");
            SPIRVUtils.SPIRV fragSpirv = SPIRVUtils.compileShader(
                    "bsl_terrain.fsh", transpiledFragment, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

            builder.setVertShaderSPIRV(vertSpirv);
            builder.setFragShaderSPIRV(fragSpirv);

            // Step 8: Create the Vulkan graphics pipeline
            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (var buffer : pipeline.getBuffers()) {
                buffer.setUseGlobalBuffer(true);
            }

            // Step 9: Apply as the terrain pipeline replacement
            PipelineManager.applyShaderPackTerrainPipeline(pipeline);
            System.out.println("[BSL] Successfully applied BSL terrain pipeline!");

            // Step 10: Initialize shadow map pass
            try {
                boolean shadowOk = BSLShadowPass.init(packDir);
                if (shadowOk) {
                    System.out.println("[BSL] Shadow mapping enabled!");
                } else {
                    System.out.println("[BSL] Shadow mapping failed, continuing without shadows");
                }
            } catch (Exception e) {
                System.err.println("[BSL] Shadow init error: " + e.getMessage());
                e.printStackTrace();
            }

            // Step 11: Load and apply water shader for translucent terrain
            try {
                GraphicsPipeline waterPipeline = loadWaterPipeline(shadersDir, packDir);
                if (waterPipeline != null) {
                    PipelineManager.applyShaderPackWaterPipeline(waterPipeline);
                    System.out.println("[BSL] Successfully applied BSL water pipeline!");
                }
            } catch (Exception e) {
                System.err.println("[BSL] Water shader load error: " + e.getMessage());
                e.printStackTrace();
            }

            // Step 12: Initialize BSL sky shader (fullscreen atmospheric sky)
            try {
                boolean skyOk = BSLSkyPass.init(shadersDir, packDir);
                if (skyOk) {
                    System.out.println("[BSL] Sky shader enabled!");
                } else {
                    System.out.println("[BSL] Sky shader failed, continuing with vanilla sky");
                }
            } catch (Exception e) {
                System.err.println("[BSL] Sky shader init error: " + e.getMessage());
                e.printStackTrace();
            }

            // Step 13: Load BSL textures (noisetex, etc.)
            try {
                BSLTextureManager.init(packDir);
            } catch (Exception e) {
                System.err.println("[BSL] Texture loading error: " + e.getMessage());
                e.printStackTrace();
            }

            // Step 14: Initialize composite pass system (deferred + composite + final)
            try {
                BSLCompositePass.init(shadersDir, packDir);
                if (BSLCompositePass.isEnabled()) {
                    System.out.println("[BSL] Composite passes enabled!");
                }
            } catch (Exception e) {
                System.err.println("[BSL] Composite init error: " + e.getMessage());
                e.printStackTrace();
            }

            return true;

        } catch (Exception e) {
            System.err.println("[BSL] Failed to load BSL shader pack: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Read the BSL fragment shader source with proper dimension wrapper setup.
     * BSL uses dimension wrappers (world0/gbuffers_terrain.fsh) that set defines
     * and #include the main program file.
     */
    private static String readBSLFragmentSource(Path shadersDir) throws IOException {
        // Try world0 wrapper first (Overworld)
        Path wrapper = shadersDir.resolve("world0/gbuffers_terrain.fsh");
        if (Files.exists(wrapper)) {
            System.out.println("[BSL] Reading Overworld fragment wrapper");
            String source = Files.readString(wrapper, StandardCharsets.UTF_8);

            // The wrapper typically looks like:
            //   #version 120
            //   #define OVERWORLD
            //   #define FSH
            //   #include "/program/gbuffers_terrain.glsl"
            // We read and resolve it, which merges the program file + all lib/ includes
            return source;
        }

        // Fallback: try direct program file with manual defines
        Path programFile = shadersDir.resolve("program/gbuffers_terrain.glsl");
        if (Files.exists(programFile)) {
            System.out.println("[BSL] Reading direct program file (no world0 wrapper)");
            String source = Files.readString(programFile, StandardCharsets.UTF_8);

            // Manually extract the #ifdef FSH section
            String fshSource = extractSection(source, "FSH");
            if (fshSource != null) {
                return "#version 120\n#define OVERWORLD\n#define FSH\n" + fshSource;
            }
        }

        return null;
    }

    /**
     * Extract a #ifdef SECTION ... #endif block from BSL's combined .glsl file.
     */
    private static String extractSection(String source, String sectionName) {
        String startMarker = "#ifdef " + sectionName;
        int start = source.indexOf(startMarker);
        if (start < 0) return null;

        // Find the matching #endif
        int depth = 1;
        int pos = start + startMarker.length();
        while (pos < source.length() && depth > 0) {
            int nextIf = source.indexOf("#if", pos);
            int nextEndif = source.indexOf("#endif", pos);

            if (nextEndif < 0) break; // No matching endif

            if (nextIf >= 0 && nextIf < nextEndif) {
                depth++;
                pos = nextIf + 3;
            } else {
                depth--;
                if (depth == 0) {
                    return source.substring(start + startMarker.length(), nextEndif);
                }
                pos = nextEndif + 6;
            }
        }

        // If we couldn't find matching endif, return everything after the marker
        return source.substring(start + startMarker.length());
    }

    /**
     * Build the Pipeline.Builder JSON config for BSL's UBO layout.
     * This must match exactly what GLSLTranspiler generates in the GLSL.
     */
    private static JsonObject buildPipelineConfig() {
        JsonObject config = new JsonObject();

        JsonArray ubos = new JsonArray();

        // Vertex UBO (binding 0)
        JsonObject vertexUbo = new JsonObject();
        vertexUbo.addProperty("type", "vertex");
        vertexUbo.addProperty("binding", 0);
        JsonArray vertexFields = new JsonArray();
        addField(vertexFields, "MVP", "matrix4x4", 16);
        addField(vertexFields, "gbufferModelView", "matrix4x4", 16);
        addField(vertexFields, "gbufferModelViewInverse", "matrix4x4", 16);
        addField(vertexFields, "timeAngle", "float", 1);
        addField(vertexFields, "sunPathRotation", "float", 1);
        addField(vertexFields, "frameTimeCounter", "float", 1);
        // Padding to align cameraPosition to vec4 boundary
        addField(vertexFields, "_vpad0", "float", 1);
        addField(vertexFields, "cameraPosition", "float", 3);
        addField(vertexFields, "_vpad1", "float", 1);
        vertexUbo.add("fields", vertexFields);
        ubos.add(vertexUbo);

        // Fragment UBO (binding 1)
        JsonObject fragmentUbo = new JsonObject();
        fragmentUbo.addProperty("type", "fragment");
        fragmentUbo.addProperty("binding", 1);
        JsonArray fragmentFields = new JsonArray();

        // Matrices (6 x mat4)
        addField(fragmentFields, "gbufferProjection", "matrix4x4", 16);
        addField(fragmentFields, "gbufferProjectionInverse", "matrix4x4", 16);
        addField(fragmentFields, "gbufferModelView", "matrix4x4", 16);
        addField(fragmentFields, "gbufferModelViewInverse", "matrix4x4", 16);
        addField(fragmentFields, "shadowProjection", "matrix4x4", 16);
        addField(fragmentFields, "shadowModelView", "matrix4x4", 16);

        // Vec4
        addField(fragmentFields, "FogColor_bsl", "float", 4);

        // Vec3 + padding (std140: vec3 aligns to 16 bytes)
        addField(fragmentFields, "cameraPosition", "float", 3);
        addField(fragmentFields, "_pad0", "float", 1);
        addField(fragmentFields, "relativeEyePosition", "float", 3);
        addField(fragmentFields, "_pad1", "float", 1);

        // 16 Floats
        addField(fragmentFields, "timeAngle", "float", 1);
        addField(fragmentFields, "timeBrightness", "float", 1);
        addField(fragmentFields, "frameTimeCounter", "float", 1);
        addField(fragmentFields, "rainStrength", "float", 1);
        addField(fragmentFields, "nightVision", "float", 1);
        addField(fragmentFields, "shadowFade", "float", 1);
        addField(fragmentFields, "near", "float", 1);
        addField(fragmentFields, "far", "float", 1);
        addField(fragmentFields, "viewWidth", "float", 1);
        addField(fragmentFields, "viewHeight", "float", 1);
        addField(fragmentFields, "screenBrightness", "float", 1);
        addField(fragmentFields, "cloudHeight", "float", 1);
        addField(fragmentFields, "endFlashIntensity", "float", 1);
        addField(fragmentFields, "aspectRatio", "float", 1);
        addField(fragmentFields, "AlphaCutout", "float", 1);
        addField(fragmentFields, "sunPathRotation", "float", 1);

        // Integer uniforms stored as floats
        addField(fragmentFields, "bsl_frameCounter", "float", 1);
        addField(fragmentFields, "bsl_isEyeInWater", "float", 1);
        addField(fragmentFields, "bsl_moonPhase", "float", 1);
        addField(fragmentFields, "bsl_worldTime", "float", 1);

        // Eye brightness
        addField(fragmentFields, "eyeBrightnessSmooth_x", "float", 1);
        addField(fragmentFields, "eyeBrightnessSmooth_y", "float", 1);

        // Handlight stubs
        addField(fragmentFields, "heldBlockLightValue", "float", 1);
        addField(fragmentFields, "heldBlockLightValue2", "float", 1);

        fragmentUbo.add("fields", fragmentFields);
        ubos.add(fragmentUbo);

        config.add("UBOs", ubos);

        // Samplers
        JsonArray samplers = new JsonArray();
        addSampler(samplers, "Sampler0");    // Block atlas texture (binding 2)
        addSampler(samplers, "Sampler2");    // Lightmap (binding 3)
        addSampler(samplers, "Sampler4");    // Shadow depth (shadowtex0) - slot 4
        addSampler(samplers, "Sampler5");    // Shadow depth (shadowtex1) - slot 5
        addSampler(samplers, "Sampler6");    // noisetex (stub/noise) - slot 6
        addSampler(samplers, "Sampler7");    // depthtex1 (stub) - slot 7
        addSampler(samplers, "Sampler8");    // depthtex0 (stub) - slot 8
        addSampler(samplers, "Sampler9");    // gaux1 (stub) - slot 9
        addSampler(samplers, "Sampler10");   // gaux2 (stub) - slot 10
        config.add("samplers", samplers);

        // Push constants (ModelOffset for terrain chunks)
        JsonArray pushConstants = new JsonArray();
        addField(pushConstants, "ModelOffset", "float", 3);
        config.add("PushConstants", pushConstants);

        return config;
    }

    private static void addField(JsonArray array, String name, String type, int count) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("type", type);
        field.addProperty("count", count);
        // Add default values
        JsonArray values = new JsonArray();
        if (count == 16) {
            // Identity matrix
            for (int i = 0; i < 16; i++) {
                values.add((i % 5 == 0) ? 1.0f : 0.0f);
            }
        } else {
            for (int i = 0; i < count; i++) {
                values.add(0.0f);
            }
        }
        field.add("values", values);
        array.add(field);
    }

    private static void addSampler(JsonArray array, String name) {
        JsonObject sampler = new JsonObject();
        sampler.addProperty("name", name);
        array.add(sampler);
    }

    /**
     * Dump a transpiled shader to the pack directory for debugging.
     */
    private static void dumpShader(Path packDir, String filename, String source) {
        try {
            Path debugDir = packDir.resolve("debug");
            Files.createDirectories(debugDir);
            Files.writeString(debugDir.resolve(filename), source, StandardCharsets.UTF_8);
            System.out.println("[BSL] Dumped debug shader: " + debugDir.resolve(filename));
        } catch (IOException e) {
            System.err.println("[BSL] Could not dump debug shader: " + e.getMessage());
        }
    }

    /**
     * Load the BSL water shader (gbuffers_water) and create a pipeline for translucent terrain.
     */
    private static GraphicsPipeline loadWaterPipeline(Path shadersDir, Path packDir) {
        try {
            // Read water fragment source from world0 wrapper
            Path wrapper = shadersDir.resolve("world0/gbuffers_water.fsh");
            if (!Files.exists(wrapper)) {
                System.out.println("[BSL] No water shader found (world0/gbuffers_water.fsh)");
                return null;
            }

            String fragmentSource = Files.readString(wrapper, StandardCharsets.UTF_8);
            fragmentSource = BSLIncludeResolver.resolve(shadersDir, fragmentSource);
            System.out.println("[BSL Water] Resolved includes: " + fragmentSource.length() + " chars");

            // Transpile with water-specific varying locations
            String transpiledFragment = GLSLTranspiler.transpileWaterFragment(fragmentSource);
            System.out.println("[BSL Water] Transpiled fragment shader: " + transpiledFragment.length() + " chars");

            // Generate water vertex shader (same as terrain but with extra varyings)
            String vertexSource = BSLVertexShaderGenerator.generateWater();

            // Debug dump
            dumpShader(packDir, "bsl_water.fsh", transpiledFragment);
            dumpShader(packDir, "bsl_water.vsh", vertexSource);

            // Build pipeline config (same as terrain)
            JsonObject config = buildPipelineConfig();

            Pipeline.Builder builder = new Pipeline.Builder(
                    CustomVertexFormat.COMPRESSED_TERRAIN, "bsl_water");
            builder.parseBindings(config);

            SPIRVUtils.SPIRV vertSpirv = SPIRVUtils.compileShader(
                    "bsl_water.vsh", vertexSource, SPIRVUtils.ShaderKind.VERTEX_SHADER);
            SPIRVUtils.SPIRV fragSpirv = SPIRVUtils.compileShader(
                    "bsl_water.fsh", transpiledFragment, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

            builder.setVertShaderSPIRV(vertSpirv);
            builder.setFragShaderSPIRV(fragSpirv);

            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (var buffer : pipeline.getBuffers()) {
                buffer.setUseGlobalBuffer(true);
            }

            return pipeline;

        } catch (Exception e) {
            System.err.println("[BSL Water] Failed to load water shader: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
