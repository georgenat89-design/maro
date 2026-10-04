package dev.maro.gametest.mixin;

import dev.maro.gametest.PetChecks;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Pet;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.*;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe actual queued pet states without shipping any diagnostic code in the main mod. */
@Mixin(EntityRenderManager.class)
public abstract class PetRenderProbe {
    @Inject(method = "render", at = @At("HEAD"))
    private void maroTest$pet(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                             MatrixStack matrices, OrderedRenderCommandQueue queue, CallbackInfo info) {
        Pet pet = ModuleManager.get(Pet.class);
        if (pet == null || !pet.isEnabled() || pet.companion() == null || state.entityType != pet.companion().getType()) return;
        if (pet.position().squaredDistanceTo(state.x, state.y, state.z) > 1) return;
        PetChecks.renderedFrames++;
        PetChecks.renderedAge = state.age;
        PetChecks.renderedScale = ((LivingEntityRenderState)state).baseScale;
        PetChecks.renderedCollar = state instanceof CatEntityRenderState cat ? cat.collarColor : null;
    }
}
