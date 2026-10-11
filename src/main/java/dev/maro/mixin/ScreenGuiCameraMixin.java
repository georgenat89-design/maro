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

/** The camera and the stationary menu use the same look angles, without changing player aim. */
@Mixin(value = Camera.class, priority = 900)
public abstract class ScreenGuiCameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void maro$menuParallax(World world, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ClickGuiScreen gui) {
            gui.prepareScreenView();
            if (gui.screenPose().active()) {
                Camera camera = (Camera) (Object) this;
                var base = new org.joml.Quaternionf(camera.getRotation());
                setRotation(camera.getYaw() + gui.screenPose().yaw(), camera.getPitch() + gui.screenPose().pitch());
                gui.screenPose().view(base, camera.getRotation(), client.options.getFov().getValue());
            }
        }
    }
}
