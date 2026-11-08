package me.kall.fadescreen.mixin;

import me.kall.fadescreen.client.ScreenFader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps the whole Forge screen draw, not just {@code Screen#renderWithTooltip}.
 * <p>
 * Screens replaced by Modern UI (pause menu, video settings, ...) submit their geometry from
 * {@code ScreenEvent.Render.Post}, which fires after {@code renderWithTooltip} returns. Hooking
 * there would composite the vanilla content and leave Modern UI's own drawing at full opacity
 * outside the fade.
 */
@Mixin(ClientHooks.class)
public abstract class ClientHooksMixin {
    @Inject(method = "drawScreen", at = @At("HEAD"))
    private static void fadescreen$beginScreen(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY,
                                               float partialTick, CallbackInfo ci) {
        ScreenFader.beginScreen(guiGraphics, screen, mouseX, mouseY, partialTick);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private static void fadescreen$endScreen(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY,
                                             float partialTick, CallbackInfo ci) {
        ScreenFader.endScreen(guiGraphics, screen);
    }
}