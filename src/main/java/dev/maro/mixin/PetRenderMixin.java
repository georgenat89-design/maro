package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Pet;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class PetRenderMixin {
    @Shadow @Final private EntityRenderManager entityRenderManager;

    @Inject(method = "pushEntityRenders", at = @At("TAIL"))
    private void maro$pet(MatrixStack matrices, WorldRenderState state, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        Pet pet = ModuleManager.get(Pet.class);
        if (pet != null) pet.render(entityRenderManager, matrices, state, queue);
    }
}
