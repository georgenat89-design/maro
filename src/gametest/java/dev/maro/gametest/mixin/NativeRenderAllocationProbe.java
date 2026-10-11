package dev.maro.gametest.mixin;

import dev.maro.gametest.RenderAllocationChecks;
import dev.maro.module.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ModuleManager.class, remap=false)
public abstract class NativeRenderAllocationProbe {
    @Inject(method="onRender2D", at=@At("HEAD")) private static void maroTest$before(CallbackInfo ci) { RenderAllocationChecks.before(0); }
    @Inject(method="onRender2D", at=@At("RETURN")) private static void maroTest$after(CallbackInfo ci) { RenderAllocationChecks.after(0); }
}
