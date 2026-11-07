package me.kall.fadescreen;

import me.kall.fadescreen.client.FadeConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod(FadeScreen.MOD_ID)
public final class FadeScreen {
    public static final String MOD_ID = "fadescreen";

    public FadeScreen(FMLJavaModLoadingContext context) {
        if (FMLLoader.getDist().isClient()) {
            context.registerConfig(ModConfig.Type.CLIENT, FadeConfig.SPEC);
        }
    }
}