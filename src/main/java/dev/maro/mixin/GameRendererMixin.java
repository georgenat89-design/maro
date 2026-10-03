package dev.maro.mixin;

import dev.maro.module.impl.visuals.StretchRes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stretch the shared perspective before it reaches rendering, culling and screen projection.
 * The hand builds a separate projection directly from its dimensions.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void maro$stretchPerspective(float fov, CallbackInfoReturnable<Matrix4f> info) {
        // Scaling only the uploaded copy left WorldRenderer and Meteor's ESP / nametag
        // projection using a different aspect ratio. Apply once at the shared source.
        Matrix4f projection = StretchRes.apply(info.getReturnValue());
        StretchRes.debug("perspective", projection);
        info.setReturnValue(projection);
    }

    /** The hand projection is built from a width/height pair: give it a width matching the target aspect. */
    @ModifyArg(method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ProjectionMatrix3;set(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 0, require = 1)
    private int maro$stretchHand(int width) {
        StretchRes stretch = StretchRes.get();
        if (stretch == null || !stretch.isEnabled()) return width;
        int height = MinecraftClient.getInstance().getWindow().getFramebufferHeight();
        return Math.max(1, Math.round(height * stretch.targetAspect()));
    }
}
