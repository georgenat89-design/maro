package dev.maro.mixin;

import dev.maro.gui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gentle world parallax is camera-only: opening the menu never turns the player. */
@Mixin(value = Camera.class, priority = 900)
public abstract class ScreenGuiCameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void maro$menuParallax(World world, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen gui && gui.screenPose().active()) {
            Camera camera = (Camera) (Object) this;
            setRotation(camera.getYaw() + gui.screenPose().yaw() * .35f, camera.getPitch() - gui.screenPose().pitch() * .35f);
        }
    }
}
