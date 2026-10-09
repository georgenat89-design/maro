package dev.maro.mixin;

import dev.maro.render.HandShaderRenderer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand Shader: the frame is copied just before the hand is drawn (for the looks that see through
 * it), and the hand is restyled as soon as it is drawn, while the depth buffer still holds only the
 * hand; the game clears depth again before the interface.
 */
@Mixin(GameRenderer.class)
public abstract class HandShaderMixin {
    @Inject(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"))
    private void maro$beforeHand(CallbackInfo ci) {
        HandShaderRenderer.captureBefore();
    }

    @Inject(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At("RETURN"))
    private void maro$afterHand(CallbackInfo ci) {
        HandShaderRenderer.apply();
    }
}
