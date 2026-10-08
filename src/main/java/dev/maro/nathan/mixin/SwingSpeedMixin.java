package dev.maro.nathan.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import dev.maro.nathan.modules.SwingSpeed;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

/**
 * Shortens or lengthens the swing animation, for your own player only.
 *
 * <p><b>Where.</b> {@code getHandSwingDuration} is private on
 * {@code LivingEntity} and, in 1.21.11, is read from exactly two places -
 * {@code swingHand(hand, updateSelf)} and {@code tickHandSwing()}. Both are
 * animation. Nothing about cooldown, damage, mining or networking reads it, so
 * changing it there cannot reach them.
 *
 * <p>The value is scaled where those two read it, not as the method returns it.
 * Other client mods (Meteor's Hand View, for one) set the swing duration from
 * inside the method, and whichever of two such hooks ran last won - so Swing
 * Speed could do nothing at all. At the reads, it scales whatever the method
 * hands back, theirs included. The high priority puts it outside any other mod
 * wrapping the same reads.
 *
 * <p><b>Scoped to the local player.</b> {@code LivingEntity} is a common class,
 * so in single player the integrated server runs it too. The identity check keeps
 * this to the client's own player object; the server's copy, and every other
 * entity, keep the duration the game gave them.
 */
@Mixin(value = LivingEntity.class, priority = 2000)
public abstract class SwingSpeedMixin {
    @Shadow
    public Hand preferredHand;

    @ModifyExpressionValue(method = {"tickHandSwing()V", "swingHand(Lnet/minecraft/util/Hand;Z)V"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getHandSwingDuration()I"),
        require = 1)
    private int nameeprotect$scaleSwingDuration(int duration) {
        if ((Object) this != MinecraftClient.getInstance().player) return duration;

        double scale = SwingSpeed.scaleFor(preferredHand);

        // Exactly 1 means the module is off, or this is not its arm. Returning
        // the value untouched is what makes "disabled" identical to vanilla
        // rather than merely close to it.
        if (scale == 1) return duration;

        // Floored at one tick: a duration of zero would divide by zero in
        // tickHandSwing when it works out the swing progress.
        return Math.max(SwingSpeed.MIN_DURATION, (int) Math.round(duration / scale));
    }
}
