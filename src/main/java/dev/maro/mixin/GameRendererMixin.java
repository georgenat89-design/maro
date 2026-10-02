package dev.maro.mixin;

import dev.maro.module.impl.visuals.StretchRes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Stretch Res hooks. In 1.21.11 renderWorld uploads two perspective projections: the world one
 * (RawProjectionMatrix.set(Matrix4f)) and the first-person hand one (ProjectionMatrix3.set(w, h, fov)).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            require = 1)
    private Matrix4f maro$stretchWorld(Matrix4f projection) {
        StretchRes.debug("world", projection);
        return StretchRes.apply(projection);
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
