package dev.maro.mixin;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Give Maro's explicit blur toggle a visible radius without changing the saved vanilla option. */
@Mixin(GameOptions.class)
public abstract class MaroMenuBlurMixin {
    @Inject(method = "getMenuBackgroundBlurrinessValue", at = @At("RETURN"), cancellable = true)
    private void maro$menuBlur(CallbackInfoReturnable<Integer> cir) {
        var client = MinecraftClient.getInstance();
        if (client != null && client.world != null && client.currentScreen instanceof ClickGuiScreen
                && ClientSettings.backgroundBlur.get()) {
            cir.setReturnValue(Math.max(5, cir.getReturnValueI()));
        }
    }
}
