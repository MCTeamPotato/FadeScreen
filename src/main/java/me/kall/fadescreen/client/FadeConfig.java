package me.kall.fadescreen.client;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

/**
 * Client config, written to {@code config/fadescreen-client.toml}.
 * Kept free of client-only types so it can be registered from the common mod class.
 */
public final class FadeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch for screen fading.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue DURATION = BUILDER
            .comment("Fade length in milliseconds.",
                    "A screen swap cross-fades over this single duration: the old screen fades out",
                    "while the new one fades in. 0 disables the animation.")
            .defineInRange("durationMillis", 320, 0, 3000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SKIPPED = BUILDER
            .comment("Fully qualified screen class names that should never fade, e.g.",
                    "\"net.minecraft.client.gui.screens.LevelLoadingScreen\"")
            .defineListAllowEmpty(List.of("skippedScreens"), List::of, () -> "screenClassID", o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static final Set<String> SKIPPED_CACHE = new ObjectOpenHashSet<>();
    private static int skippedSize = -1;

    private FadeConfig() {
    }

    public static boolean enabled() {
        return !SPEC.isLoaded() || ENABLED.get();
    }

    public static long durationMillis() {
        return SPEC.isLoaded() ? DURATION.get() : 200L;
    }

    public static boolean isSkipped(String className) {
        List<? extends String> list = SPEC.isLoaded() ? SKIPPED.get() : List.of();
        if (list.size() != skippedSize) {
            SKIPPED_CACHE.clear();
            SKIPPED_CACHE.addAll(list);
            skippedSize = list.size();
        }
        return SKIPPED_CACHE.contains(className);
    }
}