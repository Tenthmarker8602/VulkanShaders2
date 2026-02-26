package net.vulkanmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.render.chunk.build.frapi.VulkanModRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Initializer implements ClientModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("VulkanMod");

	private static String VERSION;
	public static Config CONFIG;

	@Override
	public void onInitializeClient() {

		VERSION = FabricLoader.getInstance()
				.getModContainer("vulkanmod")
				.get()
				.getMetadata()
				.getVersion().getFriendlyString();

		LOGGER.info("== VulkanMod ==");

		Platform.init();
		VideoModeManager.init();

		var configPath = FabricLoader.getInstance()
				.getConfigDir()
				.resolve("vulkanmod_settings.json");

		CONFIG = loadConfig(configPath);

		// Initialize VulkanShaders system with shader assets path
		try {
			Path shadersPath = Paths.get("assets/vulkanmod/shaders");
			VulkanShaders.initialize(shadersPath);
			LOGGER.info("VulkanShaders system initialized");
		} catch (Exception e) {
			LOGGER.warn("Failed to initialize VulkanShaders", e);
		}

		// Initialize shader pack manager - this will extract and scan shader packs
		try {
			net.vulkanmod.config.option.ShaderPackManager.getAvailablePacks();
			LOGGER.info("Shader pack manager initialized");
		} catch (Exception e) {
			LOGGER.warn("Failed to initialize shader pack manager", e);
		}

		Renderer.register(VulkanModRenderer.INSTANCE);
	}

	private static Config loadConfig(Path path) {
		Config config = Config.load(path);

		if(config == null) {
			config = new Config();
			config.write();
		}

		return config;
	}

	public static String getVersion() {
		return VERSION;
	}
}
