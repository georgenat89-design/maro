package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import dev.maro.gui.render.ScreenGuiSurface;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class ScreenGuiRendererMixin {
    @ModifyExpressionValue(method = "renderPreparedDraws", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getFramebuffer()Lnet/minecraft/client/gl/Framebuffer;"))
    private Framebuffer maro$menuTarget(Framebuffer original) { return ScreenGuiSurface.targetOr(original); }

    @Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("RETURN"))
    private void maro$screenGui(GpuBufferSlice fog, CallbackInfo ci) { ScreenGuiSurface.finish(fog); }
}
