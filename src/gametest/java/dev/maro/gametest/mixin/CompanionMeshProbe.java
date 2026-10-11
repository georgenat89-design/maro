package dev.maro.gametest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="meteordevelopment.meteorclient.renderer.MeshBuilder", remap=false)
public abstract class CompanionMeshProbe {
    @Shadow private long verticesPointer;
    @Shadow private boolean building;
    @Inject(method="vec2", at=@At("HEAD"))
    private void maroTest$position2(double x, double y, CallbackInfoReturnable<Object> result) {
        if (!building || verticesPointer == 0) dev.maro.gametest.RenderFaultChecks.fail("Companion mesh write outside begin/end: vec2, pointer=" + verticesPointer);
    }
    @Inject(method="vec3", at=@At("HEAD"))
    private void maroTest$position3(double x, double y, double z, CallbackInfoReturnable<Object> result) {
        if (!building || verticesPointer == 0) dev.maro.gametest.RenderFaultChecks.fail("Companion mesh write outside begin/end: vec3, pointer=" + verticesPointer);
    }
}
