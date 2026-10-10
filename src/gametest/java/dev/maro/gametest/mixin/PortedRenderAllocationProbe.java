package dev.maro.gametest.mixin;

import dev.maro.gametest.RenderAllocationChecks;
import dev.maro.runtime.RuntimeEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RuntimeEvents.class, remap=false)
public abstract class PortedRenderAllocationProbe {
    @Inject(method="hud", at=@At("HEAD")) private static void maroTest$before(CallbackInfo ci) { RenderAllocationChecks.before(1); }
    @Inject(method="hud", at=@At("RETURN")) private static void maroTest$after(CallbackInfo ci) { RenderAllocationChecks.after(1); }
}
