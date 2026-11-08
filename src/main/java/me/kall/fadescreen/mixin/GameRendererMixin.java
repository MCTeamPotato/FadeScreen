package me.kall.fadescreen.mixin;

import me.kall.fadescreen.client.ScreenFader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /**
     * Runs right after the frame's GUI geometry has been flushed, while the main target is
     * still bound and the orthographic GUI projection is still active. That is the only place
     * a fading-out screen can be drawn on top when no screen is currently open.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void fadescreen$afterGui(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ScreenFader.afterGui();
    }
}