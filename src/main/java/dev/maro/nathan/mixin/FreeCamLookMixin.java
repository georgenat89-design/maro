package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.maro.nathan.modules.FreeCam;
import dev.maro.nathan.modules.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

/**
 * While Free Cam is out, or Freelook is on, the mouse turns the camera and not
 * you. This is the
 * one place the game turns an entity by the mouse; for your own player, and
 * only then, the turn is handed to the camera and goes no further, so your own
 * yaw and pitch are left exactly where they were.
 */
@Mixin(Entity.class)
public abstract class FreeCamLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void nameeprotect$freeCamLook(double byX, double byY, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;

        if (FreeCam.active()) {
            FreeCam.look(byX, byY);
            ci.cancel();
        } else if (FreeLook.active()) {
            FreeLook.look(byX, byY);
            ci.cancel();
        }
    }
}
