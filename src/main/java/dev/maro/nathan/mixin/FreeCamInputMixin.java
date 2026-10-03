package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.maro.nathan.modules.FreeCam;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

/**
 * While Free Cam is out, the movement keys fly the camera and do not reach the
 * player. The game reads the keyboard into this object once a tick and moves
 * the player by what it finds there; after it has, what it found is put back to
 * nothing - no direction, no jump, no sneak, no sprint - so the player stands
 * still. The camera reads the keys for itself.
 */
@Mixin(KeyboardInput.class)
public abstract class FreeCamInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void nameeprotect$freeCamInput(CallbackInfo ci) {
        if (!FreeCam.active()) return;

        playerInput = PlayerInput.DEFAULT;
        movementVector = Vec2f.ZERO;
    }
}
