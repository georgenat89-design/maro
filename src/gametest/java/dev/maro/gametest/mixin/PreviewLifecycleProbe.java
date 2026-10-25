package dev.maro.gametest.mixin;

import dev.maro.gametest.UnifiedPreviewChecks;
import dev.maro.module.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes real lifecycle calls; preview isolation cannot be faked by restoring the final flag. */
@Mixin(Module.class)
public abstract class PreviewLifecycleProbe {
    @Inject(method = "setEnabled", at = @At("HEAD"))
    private void maroTest$previewLifecycle(boolean enabled, CallbackInfo ci) {
        UnifiedPreviewChecks.lifecycle((Module) (Object) this, enabled);
        dev.maro.gametest.RedesignHandPlacementChecks.lifecycle((Module) (Object) this, enabled);
    }
}
