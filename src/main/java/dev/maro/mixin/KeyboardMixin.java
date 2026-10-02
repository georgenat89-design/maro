package dev.maro.mixin;

import dev.maro.Maro;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void maro$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()) Maro.onKey(input.key(), action);
    }
}
