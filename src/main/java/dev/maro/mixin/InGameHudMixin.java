package dev.maro.mixin;

import dev.maro.module.impl.misc.ScreenHider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** With the HUD hidden (F1) HUD elements don't run, but hidden areas must stay hidden. */
    @Inject(method = "render", at = @At("HEAD"))
    private void maro$hiderWhenHudHidden(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (MinecraftClient.getInstance().options.hudHidden) ScreenHider.renderOver(null, context);
    }
}
