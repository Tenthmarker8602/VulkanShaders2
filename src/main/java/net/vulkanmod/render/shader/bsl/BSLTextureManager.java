package net.vulkanmod.render.shader.bsl;

import com.mojang.blaze3d.platform.NativeImage;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages BSL shader pack textures (noisetex, etc.).
 * Loads PNG textures from the shader pack and creates Vulkan images
 * that can be bound to sampler slots.
 */
public class BSLTextureManager {

    private static VulkanImage noiseTexture;
    private static boolean initialized = false;

    /**
     * Initialize textures from the shader pack directory.
     * Call once when the BSL pack is loaded.
     */
    public static void init(Path packDir) {
        if (initialized) return;

        Path shadersDir = packDir.resolve("shaders");

        // Load noise texture (used by sky clouds, water caustics, aurora, etc.)
        Path noisePath = shadersDir.resolve("tex/noise.png");
        if (Files.exists(noisePath)) {
            noiseTexture = loadTextureFromFile(noisePath, "noisetex", true);
            if (noiseTexture != null) {
                System.out.println("[BSL Textures] Loaded noisetex: " + noiseTexture.width + "x" + noiseTexture.height);
            }
        } else {
            System.out.println("[BSL Textures] noise.png not found at: " + noisePath);
        }

        initialized = true;
    }

    /**
     * Load a PNG file as a Vulkan texture with RGBA8 format.
     *
     * @param path     Path to the PNG file
     * @param name     Name for the VulkanImage (debug purposes)
     * @param wrapRepeat Whether to use repeat wrapping (true) or clamp-to-edge (false)
     * @return VulkanImage or null on failure
     */
    private static VulkanImage loadTextureFromFile(Path path, String name, boolean wrapRepeat) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(inputStream);

            int width = image.getWidth();
            int height = image.getHeight();

            System.out.println("[BSL Textures] Loading " + name + " (" + width + "x" + height + ")");

            // Create the Vulkan image
            VulkanImage vulkanImage = VulkanImage.builder(width, height)
                    .setName(name)
                    .setFormat(VK_FORMAT_R8G8B8A8_UNORM)
                    .setUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .setLinearFiltering(true)
                    .setClamp(!wrapRepeat)  // clamp = !repeat
                    .createVulkanImage();

            // Upload pixel data from NativeImage
            // NativeImage stores ABGR pixels; we need to convert to RGBA for Vulkan
            // Minecraft's NativeImage format is RGBA when created from PNG (STBI decodes to RGBA)
            // The getPointer() method returns the raw pixel data pointer
            vulkanImage.uploadSubTextureAsync(0, 0, width, height,
                    0, 0, 0, 0, width, image.getPointer());

            image.close();

            return vulkanImage;
        } catch (Exception e) {
            System.err.println("[BSL Textures] Failed to load " + name + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Bind BSL textures to their sampler slots.
     * Called after shadow texture binding to override stubs with real textures.
     */
    public static void bindTextures() {
        if (noiseTexture != null) {
            VTextureSelector.bindTexture(6, noiseTexture);  // noisetex at slot 6
        }
    }

    /**
     * Get the noise texture (for direct access if needed).
     */
    public static VulkanImage getNoiseTexture() {
        return noiseTexture;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void cleanUp() {
        if (noiseTexture != null) {
            noiseTexture.free();
            noiseTexture = null;
        }
        initialized = false;
    }
}
