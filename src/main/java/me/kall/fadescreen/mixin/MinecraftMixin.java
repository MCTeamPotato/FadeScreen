package me.kall.fadescreen.mixin;

import me.kall.fadescreen.client.ScreenFader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void fadescreen$setScreen(@Nullable Screen screen, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        ScreenFader.onScreenChanged(minecraft.screen, screen);
    }
}