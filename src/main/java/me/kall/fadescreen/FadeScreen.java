package me.kall.fadescreen;

import me.kall.fadescreen.client.FadeConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;

@Mod(FadeScreen.MOD_ID)
public final class FadeScreen {
    public static final String MOD_ID = "fadescreen";

    public FadeScreen(ModContainer container) {
        if (FMLLoader.getDist().isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, FadeConfig.SPEC);
        }
    }
}