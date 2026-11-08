package me.kall.fadescreen.mixin;

import me.kall.fadescreen.client.ScreenFader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-binds the offscreen target after the screen background has been drawn.
 * <p>
 * Modern UI replaces {@code renderBackground} with its blur handler, which runs a post chain
 * and leaves the main render target bound. Everything drawn afterwards (the screen's widgets,
 * Modern UI's own layout) would then land on the main target instead of our offscreen copy,
 * which made those screens fade in with an empty texture - i.e. not at all.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void fadescreen$afterBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ScreenFader.rebindTargetIfCapturing();
    }
}