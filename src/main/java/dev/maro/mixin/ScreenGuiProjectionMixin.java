package dev.maro.mixin;

import dev.maro.gui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use the world FOV including sprint/zoom, rather than the hand's separate projection. */
@Mixin(value = GameRenderer.class, priority = 800)
public abstract class ScreenGuiProjectionMixin {
    @Inject(method = "getFov", at = @At("RETURN"))
    private void maro$worldMenuFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> ci) {
        if (changingFov && MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen gui && gui.worldControls())
            gui.screenPose().view(camera, ci.getReturnValueF(), dev.maro.config.ClientSettings.screenDistance.getFloat());
    }
}
