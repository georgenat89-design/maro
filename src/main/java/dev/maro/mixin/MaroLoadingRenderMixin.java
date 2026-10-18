package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.maro.gui.render.BootFlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MaroLoadingRenderMixin {
    // The splash draws its own underlying screen during the fade. Drawing it again here
    // covers the overlay and produces flashes during server resource-pack reloads.
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void maro$singleScreen(Screen screen, DrawContext ctx, int mx, int my, float delta, Operation<Void> original) {
        if (!(MinecraftClient.getInstance().getOverlay() instanceof SplashOverlay)) original.call(screen, ctx, mx, my, delta);
    }
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void maro$handoff(RenderTickCounter ticks, boolean tick, CallbackInfo ci, @Local DrawContext ctx) {
        BootFlow.finishFrame(ctx);
    }
}
