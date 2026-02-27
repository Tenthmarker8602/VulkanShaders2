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
    private static final MappedBuffer sunPositionBuf = new MappedBuffer(3 * 4);
    private static final MappedBuffer moonPositionBuf = new MappedBuffer(3 * 4);

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
     * Called once per frame to update time-dependent state (non-matrix).
     * Matrix updates happen separately in updateMatrices() which is called
     * from WorldRenderer with the correct 3D camera matrices.
     */
    public static void updatePerFrame() {
        frameCounter++;

        long now = System.nanoTime();
        float deltaSeconds = (now - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = now;
        frameTimeCounter += deltaSeconds;

        // Update camera position
        updateCameraPosition();

        // Update shadow matrices from BSLShadowPass
        updateShadowMatrices();
    }

    /**
     * Called from WorldRenderer.renderSectionLayer() with the correct 3D camera
     * matrices BEFORE sky/terrain rendering. This ensures BSL shaders get the
     * real perspective projection and camera view, not stale GUI/shadow matrices.
     */
    public static void updateMatrices(Matrix4f modelView, Matrix4f projection_) {
        try {
            // ModelView inverse
            Matrix4f mvInv = new Matrix4f(modelView).invert();
            mvInv.get(modelViewInverse.buffer.asFloatBuffer());

            // Projection: VulkanMod uses Vulkan [0,1] depth (zZeroToOne=true).
            // BSL expects OpenGL [-1,1] depth, and VulkanMod uses negative
            // viewport height which inverts gl_FragCoord.y relative to OpenGL.
            Matrix4f projGL = new Matrix4f(projection_);

            // Step 1: Convert Vulkan [0,1] depth to OpenGL [-1,1] depth.
            // z_opengl = 2 * z_vulkan - w → new_row2 = 2 * old_row2 - old_row3
            // JOML: mXY = column X, row Y
            float r2c0 = projGL.m02(), r2c1 = projGL.m12(), r2c2 = projGL.m22(), r2c3 = projGL.m32();
            float r3c0 = projGL.m03(), r3c1 = projGL.m13(), r3c2 = projGL.m23(), r3c3 = projGL.m33();
            projGL.m02(2.0f * r2c0 - r3c0);
            projGL.m12(2.0f * r2c1 - r3c1);
            projGL.m22(2.0f * r2c2 - r3c2);
            projGL.m32(2.0f * r2c3 - r3c3);

            // NOTE: We do NOT negate the projection Y here. VulkanMod uses
            // a negative viewport height which flips gl_FragCoord.y, but we
            // correct that in the GLSL transpiler by replacing gl_FragCoord
            // with a corrected version (_bsl_FragCoord). This keeps ALL
            // screen-space operations consistent (screen→view AND view→screen),
            // which is essential for SSR reflections, god rays, etc.

            // Store corrected projection and its inverse
            projGL.get(projection.buffer.asFloatBuffer());
            Matrix4f projInv = new Matrix4f(projGL).invert();
            projInv.get(projectionInverse.buffer.asFloatBuffer());

            // Update sun/moon position using the correct camera modelView
            updateSunMoonPosition(modelView);
        } catch (Exception e) {
            Matrix4f identity = new Matrix4f();
            identity.get(projection.buffer.asFloatBuffer());
            identity.get(modelViewInverse.buffer.asFloatBuffer());
            identity.get(projectionInverse.buffer.asFloatBuffer());
        }
    }

    /**
     * Compute sun/moon position in view space (OptiFine convention).
     * sunPosition = normalize(gbufferModelView * vec4(sunWorldDir, 0.0)) * 100.0
     */
    private static void updateSunMoonPosition(Matrix4f modelView) {
        float ta = getTimeAngle();
        float sunPathRad = getSunPathRotation() * 0.01745329251994f;
        float cosRot = (float) Math.cos(sunPathRad);
        float sinRot = (float) -Math.sin(sunPathRad);

        // BSL's smoothed celestial angle
        float ang = ta - 0.25f;
        ang = ang - (float) Math.floor(ang); // fract
        ang = (float) ((ang + (Math.cos(ang * Math.PI) * -0.5 + 0.5 - ang) / 3.0) * 2.0 * Math.PI);

        float sdx = (float) -Math.sin(ang);
        float sdy = (float) (Math.cos(ang) * cosRot);
        float sdz = (float) (Math.cos(ang) * sinRot);

        // Direction transform: modelView * vec4(sunDir, 0.0)
        // JOML mXY = col X, row Y → result[row] = sum(col) m[col][row] * v[col]
        float vx = modelView.m00() * sdx + modelView.m10() * sdy + modelView.m20() * sdz;
        float vy = modelView.m01() * sdx + modelView.m11() * sdy + modelView.m21() * sdz;
        float vz = modelView.m02() * sdx + modelView.m12() * sdy + modelView.m22() * sdz;

        // Normalize and scale to 100.0 (OptiFine convention)
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len > 0.001f) {
            vx = vx / len * 100.0f;
            vy = vy / len * 100.0f;
            vz = vz / len * 100.0f;
        }

        MemoryUtil.memPutFloat(sunPositionBuf.ptr, vx);
        MemoryUtil.memPutFloat(sunPositionBuf.ptr + 4, vy);
        MemoryUtil.memPutFloat(sunPositionBuf.ptr + 8, vz);

        // Moon is opposite direction
        MemoryUtil.memPutFloat(moonPositionBuf.ptr, -vx);
        MemoryUtil.memPutFloat(moonPositionBuf.ptr + 4, -vy);
        MemoryUtil.memPutFloat(moonPositionBuf.ptr + 8, -vz);
    }

    private static void updateCameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        // Use the actual render camera position, NOT the player foot position.
        // BSL expects cameraPosition to match the origin that worldPos is relative to.
        // VulkanMod's ModelOffset subtracts camera.getPosition(), so cameraPosition
        // must match that exactly. Using player.getX/Y/Z() causes water waves and
        // other world-space lookups (worldPos + cameraPosition) to slide with the
        // eye-height / interpolation offset.
        net.minecraft.client.Camera camera = mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null;
        if (camera != null && camera.isInitialized()) {
            net.minecraft.world.phys.Vec3 camPos = camera.getPosition();
            long ptr = cameraPositionBuf.ptr;
            MemoryUtil.memPutFloat(ptr, (float) camPos.x);
            MemoryUtil.memPutFloat(ptr + 4, (float) camPos.y);
            MemoryUtil.memPutFloat(ptr + 8, (float) camPos.z);
        } else if (mc.player != null) {
            // Fallback before camera is initialized
            long ptr = cameraPositionBuf.ptr;
            MemoryUtil.memPutFloat(ptr, (float) mc.player.getX());
            MemoryUtil.memPutFloat(ptr + 4, (float) mc.player.getEyeY());
            MemoryUtil.memPutFloat(ptr + 8, (float) mc.player.getZ());
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
    public static MappedBuffer getSunPosition() { return sunPositionBuf; }
    public static MappedBuffer getMoonPosition() { return moonPositionBuf; }

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
