package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.maro.nathan.modules.SwingSpeed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

/**
 * Shortens or lengthens the swing animation, for your own player only.
 *
 * <p><b>Why this method.</b> {@code getCurrentSwingDuration} is private on
 * {@code LivingEntity} and, in 1.21.11, is read from exactly two places -
 * {@code swing(hand, updateSelf)} and {@code updateSwingTime()}. Both are
 * animation. Nothing about cooldown, damage, mining or networking reads it, so
 * changing it here cannot reach them. Verified against the bytecode rather than
 * assumed.
 *
 * <p>The alternative hook, if this method were ever inlined away, is
 * {@code updateSwingTime} itself - it is protected, and {@code attackAnim} could
 * be scaled there instead. That would be worse: it would also have to reproduce
 * the restart rule in {@code swing}, which is what stops a fast repeated swing
 * from snapping back to the start.
 *
 * <p><b>Scoped to the local player.</b> {@code LivingEntity} is a common class,
 * so in single player the integrated server runs it too. The identity check keeps
 * this to the client's own player object; the server's copy, and every other
 * entity, keep the duration the game gave them.
 */
@Mixin(LivingEntity.class)
public abstract class SwingSpeedMixin {
    @Shadow
    public Hand preferredHand;

    @Inject(method = "getHandSwingDuration()I", at = @At("RETURN"), cancellable = true)
    private void nameeprotect$scaleSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this != MinecraftClient.getInstance().player) return;

        double scale = SwingSpeed.scaleFor(preferredHand);

        // Exactly 1 means the module is off, or this is not its arm. Returning
        // without touching the value is what makes "disabled" identical to
        // vanilla rather than merely close to it.
        if (scale == 1) return;

        int vanilla = cir.getReturnValue();

        // Floored at one tick: a duration of zero would divide by zero in
        // updateSwingTime when it works out attackAnim.
        int scaled = (int) Math.round(vanilla / scale);

        cir.setReturnValue(Math.max(SwingSpeed.MIN_DURATION, scaled));
    }
}
