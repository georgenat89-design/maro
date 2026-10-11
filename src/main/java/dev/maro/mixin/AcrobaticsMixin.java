package dev.maro.mixin;

import dev.maro.module.impl.visuals.Acrobatics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Acrobatics' flips and spins, added once the game has turned the model the way the player faces.
 * Only what is drawn turns; the player does not.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class AcrobaticsMixin {
    @Inject(
        method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V",
        at = @At("TAIL")
    )
    private void maro$acrobatics(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        Acrobatics.transform(state.id, matrices, state.height, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));
    }
}
