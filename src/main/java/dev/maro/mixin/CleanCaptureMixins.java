package dev.maro.mixin;

import dev.maro.render.capture.CleanCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.gen.Accessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class CleanCaptureMixins {
    @Mixin(LightmapTextureManager.class)
    public interface Lighting {
        @Accessor("dirty") void maro$dirtyCaptureLighting(boolean dirty);
    }
    @Mixin(InGameHud.class)
    public abstract static class Chat {
        @Inject(method="renderChat",at=@At("HEAD"),cancellable=true)
        private void maro$privateChat(DrawContext c,RenderTickCounter ticks,CallbackInfo ci){if(dev.maro.render.capture.CapturePass.active()&&dev.maro.module.impl.misc.Streamproof.hideChat())ci.cancel();}
    }
    @Mixin(GameRenderer.class)
    public abstract static class Render {
        @Inject(method="render",at=@At("HEAD"))
        private void maro$cleanView(RenderTickCounter ticks,boolean tick,CallbackInfo ci){CleanCapture.before(ticks,tick);}
        @Inject(method="render",at=@At("RETURN"))
        private void maro$cleanOutput(RenderTickCounter ticks,boolean tick,CallbackInfo ci){CleanCapture.present();}
    }
    @Mixin(MinecraftClient.class)
    public abstract static class Target {
        @Inject(method="render",at=@At("HEAD"))
        private void maro$privateLayerVisibility(CallbackInfo ci){CleanCapture.syncVisibility();}
        @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gl/Framebuffer;blitToScreen()V"))
        private void maro$cleanMainWindow(Framebuffer framebuffer,Operation<Void> original){original.call(CleanCapture.mainTargetOr(framebuffer));}
        @Inject(method="getFramebuffer",at=@At("RETURN"),cancellable=true)
        private void maro$cleanTarget(CallbackInfoReturnable<Framebuffer> ci){ci.setReturnValue(CleanCapture.targetOr(ci.getReturnValue()));}
    }
}
