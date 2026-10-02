package dev.maro.mixin;

import dev.maro.module.impl.visuals.StretchRes;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Stretch Res: the world projection uploaded for this frame. */
    @ModifyArg(method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            require = 1)
    private Matrix4f maro$stretchWorld(Matrix4f projection) {
        StretchRes.debug("world", projection);
        return StretchRes.apply(projection);
    }

    /** Stretch Res: the first-person hand gets its own projection. */
    @ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true, require = 1)
    private Matrix4f maro$stretchHand(Matrix4f projection) {
        StretchRes.debug("hand", projection);
        return StretchRes.apply(projection);
    }
}
