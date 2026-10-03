package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.maro.nathan.modules.SpinBot;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Spin Bot's one turn, added after the game's own.
 *
 * <p>By the end of this method the game has turned the model to face the way
 * the body faces and laid it over for gliding or swimming. One more turn about
 * the model's own upright, added here, spins it about whatever line it has been
 * laid along: upright when standing, along the flight when gliding. It is a turn
 * of the whole model, so everything on it - head, armour, held items - goes
 * round with it. Only what is drawn is turned; the player is not.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class SpinBotMixin {
    @Inject(
        method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V",
        at = @At("TAIL")
    )
    private void nameeprotect$spinBot(PlayerEntityRenderState state, MatrixStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        float angle = SpinBot.angleFor(state.id);

        if (angle != 0) poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
    }
}
