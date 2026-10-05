package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.maro.render.esp.EntityRenderStateAccess;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Records which entity each render state came from. Adapted from Meteor Client (GPL-3.0). */
@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
    @ModifyExpressionValue(
        method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;"))
    private <E extends Entity> EntityRenderState maro$linkEntity(EntityRenderState state, E entity, float tickProgress) {
        ((EntityRenderStateAccess) state).maro$setEntity(entity);
        return state;
    }
}
