package me.kall.fadescreen.client;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;
import java.util.Set;

/**
 * Client config, written to {@code config/fadescreen-client.toml}.
 * Kept free of client-only types so it can be registered from the common mod class.
 */
public final class FadeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch for screen fading.")
            .define("enabled", true);

    public static final ForgeConfigSpec.IntValue DURATION = BUILDER
            .comment("Fade length in milliseconds.",
                    "A screen swap cross-fades over this single duration: the old screen fades out",
                    "while the new one fades in. 0 disables the animation.")
            .defineInRange("durationMillis", 320, 0, 3000);

    public static final ForgeConfigSpec.BooleanValue LOW_RES_TRANSITION = BUILDER
            .comment("Render the offscreen fade copy at GUI-scaled resolution instead of physical",
                    "resolution. Saves a lot of VRAM on 4K displays, but text is slightly softer",
                    "for the duration of the fade. Off by default because the softening is visible.")
            .define("lowResTransition", false);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SKIPPED = BUILDER
            .comment("Fully qualified screen class names that should never fade, e.g.",
                    "\"net.minecraft.client.gui.screens.LevelLoadingScreen\"")
            .defineListAllowEmpty(List.of("skippedScreens"), List::of, o -> o instanceof String);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static final List<? extends String> NO_SKIPPED = List.of();
    private static final Set<String> SKIPPED_CACHE = new ObjectOpenHashSet<>();
    /** The list instance the cache was built from; config reload hands back a new instance. */
    private static List<? extends String> cachedSkipped = NO_SKIPPED;
    /** Captured from the mod bus so changes made from the Embeddium page can be written out. */
    private static ModConfig config;

    private FadeConfig() {
    }

    public static void onConfigEvent(ModConfigEvent event) {
        ModConfig loaded = event.getConfig();
        if (loaded.getSpec() == SPEC) {
            config = loaded;
        }
    }

    /** Persists pending changes immediately; Embeddium calls this when the user applies edits. */
    public static void save() {
        if (config != null) {
            config.save();
        }
    }

    public static boolean enabled() {
        return !SPEC.isLoaded() || ENABLED.get();
    }

    public static long durationMillis() {
        return SPEC.isLoaded() ? DURATION.get() : 200L;
    }

    public static boolean lowResTransition() {
        return SPEC.isLoaded() && LOW_RES_TRANSITION.get();
    }

    public static void setEnabled(boolean value) {
        if (SPEC.isLoaded()) {
            ENABLED.set(value);
        }
    }

    public static void setDurationMillis(int millis) {
        if (SPEC.isLoaded()) {
            DURATION.set(millis);
        }
    }

    public static void setLowResTransition(boolean value) {
        if (SPEC.isLoaded()) {
            LOW_RES_TRANSITION.set(value);
        }
    }

    public static boolean isSkipped(String className) {
        List<? extends String> list = SPEC.isLoaded() ? SKIPPED.get() : NO_SKIPPED;
        // Rebuild on any reload, not just when the entry count changes: replacing one class
        // name with another keeps the size identical but must still refresh the cache.
        if (list != cachedSkipped) {
            SKIPPED_CACHE.clear();
            SKIPPED_CACHE.addAll(list);
            cachedSkipped = list;
        }
        return SKIPPED_CACHE.contains(className);
    }
}