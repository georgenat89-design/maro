package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.gui.spotify.SpotifyPhoneScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the phone visible while Minecraft owns normal captured mouse input. */
@Mixin(Mouse.class)
public abstract class SpotifyPhoneMouseMixin {
    @WrapOperation(method = "lockCursor", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private void maro$keepPhone(MinecraftClient client, Screen next, Operation<Void> original) {
        if (!(client.currentScreen instanceof SpotifyPhoneScreen phone) || !phone.allowsMouseLook())
            original.call(client, next);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    private Screen maro$capturedPhone(Screen screen) {
        return screen instanceof SpotifyPhoneScreen phone && phone.allowsMouseLook()
            && MinecraftClient.getInstance().mouse.isCursorLocked() ? null : screen;
    }
}
