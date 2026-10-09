package dev.maro.mixin;

import dev.maro.render.HandShaderRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand Shader: the frame as it is just before the hand is drawn, for the looks that see through it. */
@Mixin(GameRenderer.class)
public abstract class HandShaderMixin {
    @Inject(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"))
    private void maro$beforeHand(CallbackInfo ci) {
        HandShaderRenderer.captureBefore();
    }
}
