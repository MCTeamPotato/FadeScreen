package me.kall.fadescreen;

import me.kall.fadescreen.client.FadeConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FadeScreen.MOD_ID)
public final class FadeScreen {
    public static final String MOD_ID = "fadescreen";
    private static final Logger LOGGER = LogManager.getLogger("FadeScreen");

    public FadeScreen(FMLJavaModLoadingContext context) {
        if (FMLLoader.getDist().isClient()) {
            context.registerConfig(ModConfig.Type.CLIENT, FadeConfig.SPEC);
            context.getModEventBus().addListener(FadeConfig::onConfigEvent);

            // Embeddium integration is optional; only touch its classes when it is actually present.
            if (ModList.get().isLoaded("embeddium")) {
                try {
                    me.kall.fadescreen.compat.embeddium.EmbeddiumIntegration.register();
                } catch (Throwable throwable) {
                    LOGGER.error("Failed to hook Embeddium's video options screen", throwable);
                }
            }
        }
    }
}
