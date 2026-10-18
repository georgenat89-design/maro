package dev.maro.mixin;
import dev.maro.gui.render.BootFlow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class MaroLoadingLifecycleMixin {
    @Inject(method = "setScreen", at = @At("TAIL"))
    private void maro$screen(Screen screen, CallbackInfo ci) { BootFlow.screenChanged(screen); }
}
