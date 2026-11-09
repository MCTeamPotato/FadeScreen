package me.kall.fadescreen.compat.sodium;

import me.kall.fadescreen.FadeScreen;
import me.kall.fadescreen.client.FadeConfig;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Adds a FadeScreen page to Sodium's video options screen.
 * <p>
 * Sodium finds this class by scanning for {@link ConfigEntryPointForge}, instantiates it
 * reflectively and calls {@link #registerConfigLate} once its config GUI is built. Nothing in
 * the rest of the mod references this class, so it never loads when Sodium is absent.
 */
@ConfigEntryPointForge(FadeScreen.MOD_ID)
public final class SodiumIntegration implements ConfigEntryPoint {

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable("fadescreen.options.page"))
                        .addOptionGroup(builder.createOptionGroup()
                                .addOption(builder.createBooleanOption(id("enabled"))
                                        .setName(Component.translatable("fadescreen.options.enabled"))
                                        .setTooltip(Component.translatable("fadescreen.options.enabled.tooltip"))
                                        .setStorageHandler(FadeConfig::save)
                                        .setBinding(FadeConfig::setEnabled, FadeConfig::enabled)
                                        .setDefaultValue(true)
                                        .setImpact(OptionImpact.LOW))
                                .addOption(builder.createIntegerOption(id("duration"))
                                        .setName(Component.translatable("fadescreen.options.duration"))
                                        .setTooltip(Component.translatable("fadescreen.options.duration.tooltip"))
                                        .setStorageHandler(FadeConfig::save)
                                        .setBinding(FadeConfig::setDurationMillis, () -> (int) FadeConfig.durationMillis())
                                        .setDefaultValue(320)
                                        .setRange(0, 3000, 20)
                                        .setValueFormatter(millis -> Component.translatable("fadescreen.options.duration.value", millis))
                                        .setImpact(OptionImpact.LOW))
                                .addOption(builder.createBooleanOption(id("low_res"))
                                        .setName(Component.translatable("fadescreen.options.low_res"))
                                        .setTooltip(Component.translatable("fadescreen.options.low_res.tooltip"))
                                        .setStorageHandler(FadeConfig::save)
                                        .setBinding(FadeConfig::setLowResTransition, FadeConfig::lowResTransition)
                                        .setDefaultValue(false)
                                        .setImpact(OptionImpact.LOW))));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FadeScreen.MOD_ID, path);
    }
}
