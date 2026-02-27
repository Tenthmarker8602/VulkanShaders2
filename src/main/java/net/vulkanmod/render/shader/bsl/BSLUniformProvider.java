package net.vulkanmod.render.shader.bsl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Provides OptiFine-compatible uniform values for BSL shaders.
 * Maps Minecraft game state to the uniform values BSL expects.
 */
public class BSLUniformProvider {

    // Cached matrices
    private static final MappedBuffer projection = new MappedBuffer(16 * 4);
    private static final MappedBuffer modelViewInverse = new MappedBuffer(16 * 4);
    private static final MappedBuffer projectionInverse = new MappedBuffer(16 * 4);
    private static final MappedBuffer shadowModelView = new MappedBuffer(16 * 4);
    private static final MappedBuffer shadowProjection = new MappedBuffer(16 * 4);
    private static final MappedBuffer cameraPositionBuf = new MappedBuffer(3 * 4);

    // Frame counter
    private static int frameCounter = 0;
    private static float frameTimeCounter = 0.0f;
    private static long lastFrameTime = System.nanoTime();

    // Identity matrix for shadow stubs
    static {
        // Shadow matrices will be updated when shadow pass runs
        Matrix4f identity = new Matrix4f();
        identity.get(shadowModelView.buffer.asFloatBuffer());
        identity.get(shadowProjection.buffer.asFloatBuffer());
    }

    /**
     * Called once per frame to update time-dependent state.
     */
    public static void updatePerFrame() {
        frameCounter++;

        long now = System.nanoTime();
        float deltaSeconds = (now - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = now;
        frameTimeCounter += deltaSeconds;

        // Update inverse matrices
        updateInverseMatrices();

        // Update camera position
        updateCameraPosition();

        // Update shadow matrices from BSLShadowPass
        updateShadowMatrices();
    }

    private static void updateInverseMatrices() {
        try {
            // ModelView inverse
            Matrix4f mv = new Matrix4f(VRenderSystem.modelViewMatrix.buffer.asFloatBuffer());
            Matrix4f mvInv = new Matrix4f(mv).invert();
            mvInv.get(modelViewInverse.buffer.asFloatBuffer());

            // Projection: VulkanMod stores Vulkan [0,1] depth projection (zZeroToOne=true).
            // BSL expects OpenGL [-1,1] depth conventions, so we must convert.
            // The conversion from [0,1] to [-1,1] clip space is:
            //   z_opengl = 2 * z_vulkan - w
            // Applied as a correction matrix left-multiplied onto the projection:
            //   P_opengl = DepthCorrection * P_vulkan
            Matrix4f proj = new Matrix4f(VRenderSystem.projectionMatrix.buffer.asFloatBuffer());

            // Convert Vulkan [0,1] projection to OpenGL [-1,1] for BSL compatibility.
            // Row 2 of the result: new_z = 2*old_z - old_w
            // In column-major JOML: affects m20,m21,m22,m23 (row 2 elements)
            Matrix4f projGL = new Matrix4f(proj);
            // new row2 = 2*row2 - row3: for each column i, m2i = 2*m2i - m3i
            float m20 = projGL.m20(), m21 = projGL.m21(), m22 = projGL.m22(), m23 = projGL.m23();
            float m30 = projGL.m30(), m31 = projGL.m31(), m32 = projGL.m32(), m33 = projGL.m33();
            projGL.m20(2.0f * m20 - m30);
            projGL.m21(2.0f * m21 - m31);
            projGL.m22(2.0f * m22 - m32);
            projGL.m23(2.0f * m23 - m33);

            // Store the OpenGL-convention projection and its inverse for BSL
            projGL.get(projection.buffer.asFloatBuffer());
            Matrix4f projInv = new Matrix4f(projGL).invert();
            projInv.get(projectionInverse.buffer.asFloatBuffer());
        } catch (Exception e) {
            // Matrix might be singular during init; use identity
            Matrix4f identity = new Matrix4f();
            identity.get(projection.buffer.asFloatBuffer());
            identity.get(modelViewInverse.buffer.asFloatBuffer());
            identity.get(projectionInverse.buffer.asFloatBuffer());
        }
    }

    private static void updateCameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            long ptr = cameraPositionBuf.ptr;
            MemoryUtil.memPutFloat(ptr, (float) x);
            MemoryUtil.memPutFloat(ptr + 4, (float) y);
            MemoryUtil.memPutFloat(ptr + 8, (float) z);
        }
    }

    /**
     * Update shadow matrices from BSLShadowPass computed values.
     */
    private static void updateShadowMatrices() {
        if (BSLShadowPass.isInitialized()) {
            BSLShadowPass.updateShadowMatrices();
            BSLShadowPass.getShadowModelView().get(shadowModelView.buffer.asFloatBuffer());
            BSLShadowPass.getShadowProjection().get(shadowProjection.buffer.asFloatBuffer());
        }
    }

    // ---- Matrix suppliers ----

    public static MappedBuffer getModelViewInverse() { return modelViewInverse; }
    public static MappedBuffer getProjection() { return projection; }
    public static MappedBuffer getProjectionInverse() { return projectionInverse; }
    public static MappedBuffer getShadowModelView() { return shadowModelView; }
    public static MappedBuffer getShadowProjection() { return shadowProjection; }
    public static MappedBuffer getCameraPosition() { return cameraPositionBuf; }

    // ---- Time uniforms ----

    /**
     * timeAngle: 0.0-1.0 representing the sun's angle through the day cycle.
     * 0.0 = sunrise, 0.25 = noon, 0.5 = sunset, 0.75 = midnight
     */
    public static float getTimeAngle() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return 0.25f; // Default to noon

        long dayTime = level.getDayTime();
        // Minecraft day: 0=sunrise(6am), 6000=noon, 12000=sunset, 18000=midnight
        // BSL timeAngle: 0.0=sunrise, 0.25=noon, 0.5=sunset, 0.75=midnight
        return ((dayTime % 24000L) / 24000.0f);
    }

    /**
     * timeBrightness: brightness of the sun, derived from timeAngle.
     * 1.0 at noon, 0.0 at midnight.
     */
    public static float getTimeBrightness() {
        float angle = getTimeAngle();
        // BSL formula: timeBrightness is high when sun is up
        float brightness = (float) Math.max(Math.cos(angle * Math.PI * 2.0) * 0.5 + 0.5, 0.0);
        return brightness;
    }

    public static float getFrameTimeCounter() { return frameTimeCounter; }
    public static int getFrameCounter() { return frameCounter; }

    // ---- Weather/environment ----

    public static float getRainStrength() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0f;
        // Use 1.0f as partial tick — exact interpolation not critical for BSL
        return mc.level.getRainLevel(1.0f);
    }

    public static float getNightVision() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return 0.0f;
        return player.hasEffect(MobEffects.NIGHT_VISION) ? 1.0f : 0.0f;
    }

    public static float getShadowFade() {
        // shadowFade is 1.0 when shadows are fully active, 0.0 when disabled
        // During sunrise/sunset transitions, it fades
        return BSLShadowPass.isEnabled() ? 1.0f : 0.0f;
    }

    // ---- View/screen ----

    public static float getNear() { return 0.05f; }

    public static float getFar() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getEffectiveRenderDistance() * 16.0f;
    }

    public static float getViewWidth() {
        return (float) Minecraft.getInstance().getWindow().getWidth();
    }

    public static float getViewHeight() {
        return (float) Minecraft.getInstance().getWindow().getHeight();
    }

    public static float getScreenBrightness() {
        return Minecraft.getInstance().options.gamma().get().floatValue();
    }

    public static float getAspectRatio() {
        float w = getViewWidth();
        float h = getViewHeight();
        return h > 0 ? w / h : 1.0f;
    }

    // ---- World state ----

    public static int getWorldTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 6000; // noon
        return (int) (mc.level.getDayTime() % 24000L);
    }

    public static int getIsEyeInWater() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        return mc.player.isUnderWater() ? 1 : 0;
    }

    public static int getMoonPhase() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        return mc.level.getMoonPhase();
    }

    public static float getCloudHeight() { return 192.0f; }
    public static float getEndFlashIntensity() { return 0.0f; }

    /**
     * sunPathRotation in degrees (BSL default is -40.0).
     * BSL uses this in its vertex shader to compute sunVec.
     */
    public static float getSunPathRotation() { return -40.0f; }

    // ---- Eye brightness ----

    /**
     * eyeBrightnessSmooth.x = block light (0-240)
     * eyeBrightnessSmooth.y = sky light (0-240)
     */
    public static float getEyeBrightnessX() {
        // Approximate from lightmap
        return 0.0f; // Block light at player eye
    }

    public static float getEyeBrightnessY() {
        // Sky light at player position — approximate
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            int skyLight = mc.level.getBrightness(
                    net.minecraft.world.level.LightLayer.SKY,
                    mc.player.blockPosition());
            return skyLight * 16.0f; // Scale to 0-240 range
        }
        return 240.0f; // Default to full sky light
    }

    // ---- Hand light stubs ----
    public static float getHeldBlockLightValue() { return 0.0f; }
    public static float getHeldBlockLightValue2() { return 0.0f; }
}
