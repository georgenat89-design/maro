package dev.maro.mixin;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.nathan.modules.FreeCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class ScreenGuiInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void maro$worldMenuMovement(CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen gui) || !gui.worldControls() || FreeCam.active()) return;
        playerInput = gui.movementInput();
        float forward = (playerInput.forward() ? 1 : 0) - (playerInput.backward() ? 1 : 0);
        float sideways = (playerInput.left() ? 1 : 0) - (playerInput.right() ? 1 : 0);
        movementVector = new Vec2f(sideways, forward).normalize();
    }
}
