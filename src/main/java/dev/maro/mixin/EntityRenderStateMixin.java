package dev.maro.mixin;

import dev.maro.render.esp.EntityRenderStateAccess;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Adapted from Meteor Client (GPL-3.0). */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityRenderStateAccess {
    @Unique
    private Entity maro$entity;

    @Override
    public Entity maro$getEntity() {
        return maro$entity;
    }

    @Override
    public void maro$setEntity(Entity entity) {
        maro$entity = entity;
    }
}
