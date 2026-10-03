package dev.maro.mixin;

import dev.maro.Maro;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable=true)
    private void maro$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()) {
            if(dev.maro.runtime.RuntimeEvents.mouse(input,action)){ci.cancel();return;}
            Maro.onMouseButton(input.button(), action);
        }
    }

    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void maro$scroll(long window,double horizontal,double vertical,CallbackInfo info){
        if(window==MinecraftClient.getInstance().getWindow().getHandle() && dev.maro.runtime.MeteorClient.EVENT_BUS.post(new dev.maro.runtime.events.meteor.MouseScrollEvent(vertical)).isCancelled())info.cancel();
    }
}
