package dev.maro.mixin;

import dev.maro.compat.CompanionBlockOutlineBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {"com.example.addon.modules.an", "com.example.addon.modules.BlockOutlines"}, remap = false)
public abstract class CompanionBlockOutlineMixin {
    @Inject(method = "onRender", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/renderer/MeshBuilder;vec3(DDD)Lmeteordevelopment/meteorclient/renderer/MeshBuilder;",
        ordinal = 0), cancellable = true)
    private void maro$reserveStar(@Coerce Object event, CallbackInfo ci) {
        if (!CompanionBlockOutlineBuffers.reserve(event)) ci.cancel();
    }
}
