package me.kall.fadescreen.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.ClientHooks;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL14;

/**
 * Renders every {@link Screen} into an offscreen target and composites it back with an
 * animated alpha, so the screen itself (not a black overlay) fades in and out.
 * <p>
 * The last frame of a screen is kept, so when a screen is replaced the previous frame can be
 * cross-faded out on top of whatever comes next (including the world when a screen is closed).
 * <p>
 * All drawing happens on the render thread inside the GUI pass, where the orthographic
 * projection and the identity model view are already set up.
 */
public final class ScreenFader {
    /** Offscreen copy of the screen rendered this frame. */
    private static RenderTarget contentTarget;
    /** Copy of the previous screen's final frame, faded out after a screen swap. */
    private static RenderTarget ghostTarget;

    private static boolean contentValid;
    private static boolean ghostVisible;
    private static float ghostStrength = 1.0F;
    private static Screen pendingGhost;

    private static long fadeStart = -1L;

    /** Set on a screen change; the first frame that draws the transition starts {@link #fadeStart}. */
    private static boolean fadePending;

    /** False until the game has opened its first screen by itself (see {@link #onScreenChanged}). */
    private static boolean firstScreenSeen;

    /** Guards against nested screen rendering (a screen drawing another screen inside itself). */
    private static int nesting;

    /** Set while the outgoing screen is re-rendered into the ghost target. */
    private static boolean capturingGhost;

    /** True when the screen or the ghost was composited this frame. */
    private static boolean composited;

    /** Frames the current screen has actually been presented for; used to skip no-op transitions. */
    private static int renderedFrames;

    private ScreenFader() {
    }

    /* ------------------------------------------------------------------ state */

    public static void onScreenChanged(@Nullable Screen previous, @Nullable Screen next) {
        if (previous == next) {
            return;
        }

        // The very first screen of a session is the one the game opens by itself (usually
        // TitleScreen). Vanilla already fades that one in, and the back buffer at that moment
        // can still hold the early loading display, so fading it again double-dips and reads
        // as a flicker with leftovers bleeding through. Start fading from the next change on.
        if (previous == null && !firstScreenSeen) {
            firstScreenSeen = true;
            fadeStart = -1L;
            fadePending = false;
            pendingGhost = null;
            ghostVisible = false;
            return;
        }
        if (next != null) {
            firstScreenSeen = true;
        }

        // The outgoing screen may have been replaced before it was ever drawn: creative mode
        // swaps InventoryScreen for CreativeModeInventoryScreen, Embeddium swaps Sodium's GUI
        // for its own. There is nothing to fade out, but the replacement still has to fade in,
        // so keep the transition already scheduled (and any ghost of the last screen that
        // really was visible) and just retarget it.
        boolean previousPresented = previous != null && renderedFrames > 0;
        renderedFrames = 0;
        if (previous != null && !previousPresented) {
            if (!fadePending) {
                fadeStart = -1L;
                fadePending = true;
            }
            return;
        }

        if (!FadeConfig.enabled()) {
            return;
        }

        if (isFadable(previous)) {
            pendingGhost = previous;
            // A screen closed in the middle of its own fade-in must keep its current opacity.
            ghostStrength = currentAlpha();
            ghostVisible = true;
        }

        // The clock starts on the first frame the transition is actually drawn, not here:
        // a hitch between setScreen() and the next frame would otherwise eat the whole fade.
        fadeStart = -1L;
        fadePending = true;
    }

    /** Called at the head of {@code ClientHooks#drawScreen}; the screen renders into the offscreen target afterwards. */
    public static void beginScreen(GuiGraphics guiGraphics, Screen screen, int mouseX, int mouseY, float partialTick) {
        if (capturingGhost) {
            return;
        }
        if (nesting > 0) {
            nesting++;
            return;
        }
        if (!isFadable(screen)) {
            return;
        }

        // No transition is running: render straight to the main target, exactly like vanilla.
        // Nothing is composited, so there is no chance of the fade path altering a static screen.
        boolean composite = needsComposite();
        if (!composite) {
            contentValid = false;
            return;
        }

        beginClockIfPending();

        Minecraft minecraft = Minecraft.getInstance();
        if (!ensureTargets(minecraft)) {
            return;
        }

        // Snapshot the outgoing screen before its frame is replaced.
        if (pendingGhost != null) {
            captureGhost(minecraft, guiGraphics, mouseX, mouseY, partialTick);
        }

        // Accumulated HUD geometry belongs on the main target, not inside the screen copy.
        guiGraphics.flush();

        contentTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        // RenderTarget#clear ends with unbindWrite(), so bind again afterwards.
        RenderSystem.disableScissor();
        contentTarget.clear(Minecraft.ON_OSX);
        contentTarget.bindWrite(true);

        nesting = 1;
    }

    /** True while a fade or a fading-out ghost still has something to draw. */
    private static boolean needsComposite() {
        return fadePending || pendingGhost != null || ghostVisible || currentAlpha() < 0.999F;
    }

    /** Starts the transition clock on the frame the transition is first drawn. */
    private static void beginClockIfPending() {
        if (fadePending) {
            fadeStart = Util.getMillis();
            fadePending = false;
        }
    }

    /**
     * Re-binds whichever offscreen target is currently capturing.
     * <p>
     * Modern UI's blur background runs a post chain and leaves the main target bound, so the
     * rest of the screen would be drawn outside the offscreen copy.
     */
    public static void rebindTargetIfCapturing() {
        if (capturingGhost && ghostTarget != null) {
            ghostTarget.bindWrite(true);
        } else if (nesting > 0 && contentTarget != null) {
            contentTarget.bindWrite(true);
        }
    }
    /** Called at the return of {@code ClientHooks#drawScreen}; composites the screen with its current alpha. */
    public static void endScreen(GuiGraphics guiGraphics, Screen screen) {
        if (nesting == 0) {
            // Rendered straight to the main target; count it so the next switch knows this
            // screen really was on screen.
            renderedFrames++;
            return;
        }

        nesting--;
        if (nesting > 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        // Screen geometry is still batched, and the offscreen target is still bound.
        guiGraphics.flush();
        minecraft.getMainRenderTarget().bindWrite(true);
        contentValid = true;

        drawTexture(contentTarget.getColorTextureId(), currentAlpha(), false);
        composited = true;
        renderedFrames++;
    }

    /** Called once per frame after the GUI is fully drawn; draws the fading-out previous screen on top. */
    public static void afterGui() {
        if (!FadeConfig.enabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        beginClockIfPending();

        if (pendingGhost != null) {
            Window window = minecraft.getWindow();
            int guiMouseX = (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
            int guiMouseY = (int) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
            captureGhost(minecraft, null, guiMouseX, guiMouseY, 0.0F);
        }

        if (ghostVisible) {
            float alpha = ghostStrength * (1.0F - currentAlpha());
            if (alpha <= 0.002F) {
                ghostVisible = false;
            } else {
                minecraft.getMainRenderTarget().bindWrite(true);
                drawTexture(ghostTarget.getColorTextureId(), alpha, false);
                composited = true;
            }
        }

        if (composited) {
            composited = false;
            makeWindowOpaque();
        }
    }

    /**
     * Restores the alpha channel of the main target to 1.
     * <p>
     * The window is composited with the back buffer alpha, so a "screen faded to 30%" would
     * otherwise become a 70% transparent window and let whatever is behind it (the early
     * loading display, the desktop) bleed through. Only the alpha channel is written, so the
     * colour composite above is left untouched.
     */
    private static void makeWindowOpaque() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getMainRenderTarget().bindWrite(true);

        Window window = minecraft.getWindow();
        float width = (float) (window.getWidth() / window.getGuiScale());
        float height = (float) (window.getHeight() / window.getGuiScale());

        GlStateManager._colorMask(false, false, false, true);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(0.0F, height, 0.0F);
        builder.addVertex(width, height, 0.0F);
        builder.addVertex(width, 0.0F, 0.0F);
        builder.addVertex(0.0F, 0.0F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        GlStateManager._colorMask(true, true, true, true);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
    }

    private static boolean isFadable(Screen screen) {
        return screen != null && FadeConfig.enabled() && !FadeConfig.isSkipped(screen.getClass().getName());
    }

    /* ------------------------------------------------------------------ alpha */

    private static float currentAlpha() {
        if (fadeStart < 0L) {
            return 1.0F;
        }

        long duration = FadeConfig.durationMillis();
        long elapsed = Util.getMillis() - fadeStart;
        if (duration <= 0L || elapsed >= duration) {
            fadeStart = -1L;
            return 1.0F;
        }

        return ease(elapsed / (float) duration);
    }

    /** Smoothstep: no hard start or stop on either end of the fade. */
    private static float ease(float t) {
        float clamped = t < 0.0F ? 0.0F : (Math.min(t, 1.0F));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    /* ------------------------------------------------------------------ targets */

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean ensureTargets(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        if (width <= 0 || height <= 0) return false;

        if (contentTarget == null) {
            contentTarget = new TextureTarget(width, height, true, false);
            contentTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }

        if (contentTarget.width != width || contentTarget.height != height) {
            contentTarget.resize(width, height, false);
            contentTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            contentValid = false;
        }

        if (ghostTarget == null) {
            ghostTarget = new TextureTarget(width, height, true, false);
            ghostTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        }

        if (ghostTarget.width != width || ghostTarget.height != height) {
            ghostTarget.resize(width, height, false);
            ghostTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            ghostVisible = false;
        }

        return true;
    }

    private static void captureGhost(Minecraft minecraft, @Nullable GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Screen source = pendingGhost;
        pendingGhost = null;

        if (!ensureTargets(minecraft)) {
            ghostVisible = false;
            return;
        }

        ghostTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.disableScissor();
        ghostTarget.clear(Minecraft.ON_OSX);
        ghostTarget.bindWrite(true);

        if (contentValid) {
            // Blending against a cleared target at alpha 1 is an exact copy of the last frame.
            drawTexture(contentTarget.getColorTextureId(), 1.0F, true);
        } else if (source != null) {
            // The screen was rendering straight to the main target, so there is no frame to
            // copy: draw it once into the ghost target instead. We are still inside the GUI
            // pass, so the orthographic projection and model view are the ones it expects.
            GuiGraphics graphics = guiGraphics != null ? guiGraphics : new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            capturingGhost = true;
            graphics.pose().pushPose();
            try {
                ClientHooks.drawScreen(source, graphics, mouseX, mouseY, partialTick);
            } finally {
                graphics.pose().popPose();
                capturingGhost = false;
            }
            graphics.flush();
        } else {
            ghostVisible = false;
            minecraft.getMainRenderTarget().bindWrite(true);
            return;
        }

        minecraft.getMainRenderTarget().bindWrite(true);
        ghostVisible = true;
    }

    /* ------------------------------------------------------------------ drawing */

    /**
     * Draws a full-screen quad for {@code textureId} into the currently bound target.
     * <p>
     * The fade cannot use the offscreen alpha channel: vanilla's GUI blend
     * ({@code defaultBlendFunc} = {@code SRC_ALPHA/ONE_MINUS_SRC_ALPHA} for colour, but
     * {@code ONE/ZERO} for alpha) <em>replaces</em> the framebuffer alpha with each draw's own
     * alpha instead of accumulating coverage. A sky gradient or an antialiased logo edge would
     * therefore leave a low alpha behind, and a premultiplied composite would add their colour
     * on top ("white fringes"). Fading with a constant alpha keeps the image intact:
     * {@code out = tex.rgb * alpha + dst * (1 - alpha)}.
     */
    private static void drawTexture(int textureId, float alpha, boolean copy) {
        Window window = Minecraft.getInstance().getWindow();
        // Match GameRenderer's orthographic projection exactly (double division, then float).
        float width = (float) (window.getWidth() / window.getGuiScale());
        float height = (float) (window.getHeight() / window.getGuiScale());

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        if (copy) {
            // Exact copy into a cleared target, alpha included.
            RenderSystem.disableBlend();
            GlStateManager._colorMask(true, true, true, true);
        } else {
            GL14.glBlendColor(0.0F, 0.0F, 0.0F, alpha);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.CONSTANT_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            // A composite into the main target must not lower the window alpha (makeWindowOpaque).
            GlStateManager._colorMask(true, true, true, false);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(0.0F, height, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(width, height, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex(width, 0.0F, 0.0F).setUv(1.0F, 1.0F);
        builder.addVertex(0.0F, 0.0F, 0.0F).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        GlStateManager._colorMask(true, true, true, true);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }
}
