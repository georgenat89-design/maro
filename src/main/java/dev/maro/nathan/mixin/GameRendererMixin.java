package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.maro.nathan.modules.Bloom;
import dev.maro.render.esp.PlayerEspRenderer;
import dev.maro.nathan.modules.ColorCorrect;
import dev.maro.nathan.modules.MotionBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Pool;

/**
 * Runs the addon's frame passes, each at the one point in the frame where it
 * belongs.
 *
 * <p><b>Motion blur</b> runs inside {@code renderLevel}, at the call that
 * clears the depth buffer before the hand is drawn. At that instant the world
 * is complete - terrain, entities, Meteor's 3D overlays - and its depth is
 * still in the buffer, which the blur needs to place each pixel in the world.
 * A moment later that depth is cleared for the hand, so any later point would
 * hand the shader a depth of "far" for everything. The hand and held item are
 * drawn after the blur, sharp, on top of it.
 *
 * <p><b>Colour correction</b> runs in {@code render}, as the HUD begins to be
 * recorded - or, with no HUD this frame, the screen, or failing both, the call
 * that rasterises the GUI. In 1.21.11 the HUD, chat, any screen, toasts and
 * the debug overlay are only recorded and then drawn by that last call, so at
 * any of these instants the frame holds the world and the hand and nothing 2D.
 * Grading there tints the world and not the interface. The same goes for the
 * blur: both passes are over before a single pixel of interface exists. The
 * earliest of the three is taken because another mod may copy the frame while
 * a menu is recorded, to draw the world behind it, and that copy must already
 * be graded. The passes run once a frame, at whichever point comes first.
 *
 * <p>Neither can be the end of the method. The frame's resource pool has its
 * {@code endFrame} called a few instructions later, and the passes need that
 * pool to allocate the targets they render through.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Pool pool;

    @Inject(
        method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void nameeprotect$motionBlur(CallbackInfo ci) {
        // The world is drawn and its depth is still here; the hand is not yet.
        MotionBlur.applyTo(MinecraftClient.getInstance().getFramebuffer(), pool);
    }

    /** Whether this frame's world passes have run yet. */
    @Unique
    private boolean nameeprotect$framePassesDone;

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void nameeprotect$newFrame(CallbackInfo ci) {
        nameeprotect$framePassesDone = false;
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"),
        require = 0)
    private void nameeprotect$beforeHud(CallbackInfo ci) {
        nameeprotect$framePasses("hud");
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V"),
        require = 0)
    private void nameeprotect$beforeScreen(CallbackInfo ci) {
        nameeprotect$framePasses("screen");
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void nameeprotect$colorCorrect(CallbackInfo ci) {
        nameeprotect$framePasses("gui");
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
    private void nameeprotect$overMenu(CallbackInfo ci) {
        // With Grade Menus on and a menu open, the grade waited for the menu to be drawn.
        ColorCorrect.applyOverMenu(MinecraftClient.getInstance().getFramebuffer(), pool);
    }

    @Unique
    private void nameeprotect$framePasses(String point) {
        if (nameeprotect$framePassesDone) return;
        nameeprotect$framePassesDone = true;
        // The world and the hand are drawn; the HUD, chat and any screen are not yet.
        //
        // Bloom first, then the grade. Light bleeding is something the lens
        // does to the picture, and grading is done to the picture that comes
        // out of the lens - so the grade must see the bloom, not the other way
        // round. Either order works; this one is the one that matches how the
        // two controls read when they are both turned up.
        //
        // Player ESP and Block ESP go on before either, so their fills, tracers and glow bloom and
        // grade with the world.
        PlayerEspRenderer.composite();
        dev.maro.render.esp.BlockEspRenderer.composite();
        Bloom.applyTo(MinecraftClient.getInstance().getFramebuffer(), pool);
        ColorCorrect.applyTo(MinecraftClient.getInstance().getFramebuffer(), pool, point);
    }
}
