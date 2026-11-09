package me.kall.fadescreen.compat.embeddium;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import me.kall.fadescreen.FadeScreen;
import me.kall.fadescreen.client.FadeConfig;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Adds a FadeScreen page to Embeddium's video options screen.
 * <p>
 * Embeddium fires {@link OptionGUIConstructionEvent} while building the page list, so the page is
 * inserted into its native screen without a mixin. The same page also reaches the Modern UI screen
 * that ModernEmb builds from the captured list.
 * <p>
 * This class is only loaded when Embeddium is present (see {@code FadeScreen}); it must not be
 * referenced from common code paths that run without Embeddium installed.
 */
public final class EmbeddiumIntegration {
    private static final OptionStorage<Void> STORAGE = new OptionStorage<>() {
        @Override
        public Void getData() {
            return null;
        }

        @Override
        public void save() {
            FadeConfig.save();
        }
    };

    private static final OptionIdentifier<Void> PAGE_ID = OptionIdentifier.create(FadeScreen.MOD_ID, "fade");
    private static final OptionIdentifier<Void> GROUP_ID = OptionIdentifier.create(FadeScreen.MOD_ID, "fade.general");

    private EmbeddiumIntegration() {
    }

    public static void register() {
        OptionGUIConstructionEvent.BUS.addListener(EmbeddiumIntegration::onOptionGuiConstruction);
    }

    private static void onOptionGuiConstruction(OptionGUIConstructionEvent event) {
        for (OptionPage page : event.getPages()) {
            if (FadeScreen.MOD_ID.equals(page.getId().getModId())) {
                return;
            }
        }
        event.addPage(buildPage());
    }

    private static OptionPage buildPage() {
        OptionGroup group = OptionGroup.createBuilder()
                .setId(GROUP_ID)
                .add(toggle("enabled", FadeConfig::setEnabled, FadeConfig::enabled))
                .add(duration())
                .add(toggle("low_res", FadeConfig::setLowResTransition, FadeConfig::lowResTransition))
                .build();

        return new OptionPage(PAGE_ID, Component.translatable("fadescreen.options.page"), ImmutableList.of(group));
    }

    private static OptionImpl<Void, Boolean> toggle(String path, Consumer<Boolean> setter, BooleanSupplier getter) {
        return OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(OptionIdentifier.create(FadeScreen.MOD_ID, path, Boolean.class))
                .setName(Component.translatable("fadescreen.options." + path))
                .setTooltip(Component.translatable("fadescreen.options." + path + ".tooltip"))
                .setBinding((ignored, value) -> setter.accept(value), ignored -> getter.getAsBoolean())
                .setControl(TickBoxControl::new)
                .setImpact(OptionImpact.LOW)
                .build();
    }

    private static OptionImpl<Void, Integer> duration() {
        IntConsumer setter = FadeConfig::setDurationMillis;
        IntSupplier getter = () -> (int) FadeConfig.durationMillis();

        return OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(OptionIdentifier.create(FadeScreen.MOD_ID, "duration", Integer.class))
                .setName(Component.translatable("fadescreen.options.duration"))
                .setTooltip(Component.translatable("fadescreen.options.duration.tooltip"))
                .setBinding((ignored, value) -> setter.accept(value), ignored -> getter.getAsInt())
                .setControl(option -> new SliderControl(option, 0, 3000, 20,
                        ControlValueFormatter.translateVariable("fadescreen.options.duration.value")))
                .setImpact(OptionImpact.LOW)
                .build();
    }
}
