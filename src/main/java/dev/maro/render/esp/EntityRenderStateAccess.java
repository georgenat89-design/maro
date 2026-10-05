package dev.maro.render.esp;

import net.minecraft.entity.Entity;

/**
 * Links an entity's per-frame render state back to the entity it was extracted from, so the Player
 * ESP can pick players out of the frame's render states. Implemented on EntityRenderState by
 * {@link dev.maro.mixin.EntityRenderStateMixin}.
 */
public interface EntityRenderStateAccess {
    /** Null when another mod skipped the extraction that sets it. */
    Entity maro$getEntity();

    void maro$setEntity(Entity entity);
}
