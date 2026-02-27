package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.vulkanmod.render.shader.bsl.BSLUniformProvider;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import net.vulkanmod.vulkan.util.MappedBuffer;

import java.util.function.Supplier;

public class Uniforms {

    public static Object2ReferenceOpenHashMap<String, Supplier<Integer>> vec1i_uniformMap = new Object2ReferenceOpenHashMap<>();

    public static Object2ReferenceOpenHashMap<String, Supplier<Float>> vec1f_uniformMap = new Object2ReferenceOpenHashMap<>();
    public static Object2ReferenceOpenHashMap<String, Supplier<MappedBuffer>> vec2f_uniformMap = new Object2ReferenceOpenHashMap<>();
    public static Object2ReferenceOpenHashMap<String, Supplier<MappedBuffer>> vec3f_uniformMap = new Object2ReferenceOpenHashMap<>();
    public static Object2ReferenceOpenHashMap<String, Supplier<MappedBuffer>> vec4f_uniformMap = new Object2ReferenceOpenHashMap<>();

    public static Object2ReferenceOpenHashMap<String, Supplier<MappedBuffer>> mat4f_uniformMap = new Object2ReferenceOpenHashMap<>();

    public static void setupDefaultUniforms() {

        //Mat4
        mat4f_uniformMap.put("ModelViewMat", VRenderSystem::getModelViewMatrix);
        mat4f_uniformMap.put("ProjMat", VRenderSystem::getProjectionMatrix);
        mat4f_uniformMap.put("MVP", VRenderSystem::getMVP);
        mat4f_uniformMap.put("TextureMat", VRenderSystem::getTextureMatrix);

        //Vec1i
        vec1i_uniformMap.put("EndPortalLayers", () -> 15);

        //Vec1
        vec1f_uniformMap.put("FogStart", () -> VRenderSystem.getFogData().renderDistanceStart);
        vec1f_uniformMap.put("FogEnd", () -> VRenderSystem.getFogData().renderDistanceEnd);
        vec1f_uniformMap.put("FogEnvironmentalStart", () -> VRenderSystem.getFogData().environmentalStart);
        vec1f_uniformMap.put("FogEnvironmentalEnd", () -> VRenderSystem.getFogData().environmentalEnd);
        vec1f_uniformMap.put("FogRenderDistanceStart", () -> VRenderSystem.getFogData().renderDistanceStart);
        vec1f_uniformMap.put("FogRenderDistanceEnd", () -> VRenderSystem.getFogData().renderDistanceEnd);
        vec1f_uniformMap.put("FogSkyEnd", () -> VRenderSystem.getFogData().skyEnd);
        vec1f_uniformMap.put("FogCloudsEnd", () -> VRenderSystem.getFogData().cloudEnd);
        vec1f_uniformMap.put("LineWidth", RenderSystem::getShaderLineWidth);
        vec1f_uniformMap.put("AlphaCutout", () -> VRenderSystem.alphaCutout);

        //Vec2
        vec2f_uniformMap.put("ScreenSize", VRenderSystem::getScreenSize);

        //Vec3
        vec3f_uniformMap.put("Light0_Direction", () -> VRenderSystem.lightDirection0);
        vec3f_uniformMap.put("Light1_Direction", () -> VRenderSystem.lightDirection1);
        vec3f_uniformMap.put("ModelOffset", () -> VRenderSystem.modelOffset);
        vec3f_uniformMap.put("ChunkOffset", () -> VRenderSystem.modelOffset);

        //Vec4
        vec4f_uniformMap.put("ColorModulator", VRenderSystem::getShaderColor);
        vec4f_uniformMap.put("FogColor", VRenderSystem::getShaderFogColor);

        // ---- BSL Shader Pack Uniforms ----
        setupBSLUniforms();
    }

    private static void setupBSLUniforms() {
        // BSL Mat4 uniforms
        mat4f_uniformMap.put("gbufferModelView", VRenderSystem::getModelViewMatrix);
        mat4f_uniformMap.put("gbufferModelViewInverse", BSLUniformProvider::getModelViewInverse);
        mat4f_uniformMap.put("gbufferProjection", BSLUniformProvider::getProjection);
        mat4f_uniformMap.put("gbufferProjectionInverse", BSLUniformProvider::getProjectionInverse);
        mat4f_uniformMap.put("shadowModelView", BSLUniformProvider::getShadowModelView);
        mat4f_uniformMap.put("shadowProjection", BSLUniformProvider::getShadowProjection);

        // BSL Vec3 uniforms
        vec3f_uniformMap.put("cameraPosition", BSLUniformProvider::getCameraPosition);
        vec3f_uniformMap.put("relativeEyePosition", BSLUniformProvider::getCameraPosition);

        // BSL Vec4 uniforms (FogColor mapped as FogColor_bsl)
        vec4f_uniformMap.put("FogColor_bsl", VRenderSystem::getShaderFogColor);

        // BSL Float uniforms
        vec1f_uniformMap.put("timeAngle", BSLUniformProvider::getTimeAngle);
        vec1f_uniformMap.put("timeBrightness", BSLUniformProvider::getTimeBrightness);
        vec1f_uniformMap.put("frameTimeCounter", BSLUniformProvider::getFrameTimeCounter);
        vec1f_uniformMap.put("rainStrength", BSLUniformProvider::getRainStrength);
        vec1f_uniformMap.put("nightVision", BSLUniformProvider::getNightVision);
        vec1f_uniformMap.put("shadowFade", BSLUniformProvider::getShadowFade);
        vec1f_uniformMap.put("near", BSLUniformProvider::getNear);
        vec1f_uniformMap.put("far", BSLUniformProvider::getFar);
        vec1f_uniformMap.put("viewWidth", BSLUniformProvider::getViewWidth);
        vec1f_uniformMap.put("viewHeight", BSLUniformProvider::getViewHeight);
        vec1f_uniformMap.put("screenBrightness", BSLUniformProvider::getScreenBrightness);
        vec1f_uniformMap.put("cloudHeight", BSLUniformProvider::getCloudHeight);
        vec1f_uniformMap.put("endFlashIntensity", BSLUniformProvider::getEndFlashIntensity);
        vec1f_uniformMap.put("aspectRatio", BSLUniformProvider::getAspectRatio);
        vec1f_uniformMap.put("sunPathRotation", BSLUniformProvider::getSunPathRotation);

        // BSL integer uniforms stored as floats (BSL UBO uses float for these)
        vec1f_uniformMap.put("bsl_frameCounter", () -> (float) BSLUniformProvider.getFrameCounter());
        vec1f_uniformMap.put("bsl_isEyeInWater", () -> (float) BSLUniformProvider.getIsEyeInWater());
        vec1f_uniformMap.put("bsl_moonPhase", () -> (float) BSLUniformProvider.getMoonPhase());
        vec1f_uniformMap.put("bsl_worldTime", () -> (float) BSLUniformProvider.getWorldTime());

        // BSL eye brightness components (stored as floats)
        vec1f_uniformMap.put("eyeBrightnessSmooth_x", BSLUniformProvider::getEyeBrightnessX);
        vec1f_uniformMap.put("eyeBrightnessSmooth_y", BSLUniformProvider::getEyeBrightnessY);

        // BSL hand light stubs
        vec1f_uniformMap.put("heldBlockLightValue", BSLUniformProvider::getHeldBlockLightValue);
        vec1f_uniformMap.put("heldBlockLightValue2", BSLUniformProvider::getHeldBlockLightValue2);

        // Padding fields (UBO alignment — always 0.0)
        vec1f_uniformMap.put("_vpad0", () -> 0.0f);
        vec1f_uniformMap.put("_vpad1", () -> 0.0f);
        vec1f_uniformMap.put("_pad0", () -> 0.0f);
        vec1f_uniformMap.put("_pad1", () -> 0.0f);
    }

    public static Supplier<MappedBuffer> getUniformSupplier(String type, String name) {
        return switch (type) {
            case "mat4" -> Uniforms.mat4f_uniformMap.get(name);
            case "vec4" -> Uniforms.vec4f_uniformMap.get(name);
            case "vec3" -> Uniforms.vec3f_uniformMap.get(name);
            case "vec2" -> Uniforms.vec2f_uniformMap.get(name);

            default -> null;
        };
    }
}
