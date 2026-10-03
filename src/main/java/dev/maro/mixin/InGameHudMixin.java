package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomCrosshair;
import dev.maro.module.impl.misc.ScreenHider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Replace just the crosshair sprite, keeping vanilla's attack indicator and visibility checks. */
    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V",
        ordinal = 0))
    private void maro$customCrosshair(DrawContext ctx, RenderPipeline pipeline, Identifier texture,
        int x, int y, int width, int height, Operation<Void> original) {
        CustomCrosshair module = ModuleManager.get(CustomCrosshair.class);
        if (module != null && module.isEnabled()) module.renderCrosshair(ctx);
        else original.call(ctx, pipeline, texture, x, y, width, height);
    }

    /** With the HUD hidden (F1) HUD elements don't run, but hidden areas must stay hidden. */
    @Inject(method = "render", at = @At("HEAD"))
    private void maro$hiderWhenHudHidden(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (MinecraftClient.getInstance().options.hudHidden) ScreenHider.renderOver(null, context);
    }
}
