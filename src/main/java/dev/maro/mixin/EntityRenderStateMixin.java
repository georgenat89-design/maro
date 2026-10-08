package dev.maro.mixin;

import dev.maro.render.emote.Emote;
import dev.maro.render.emote.EmoteRenderState;
import dev.maro.render.esp.EntityRenderStateAccess;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Which entity a render state came from (adapted from Meteor Client, GPL-3.0), and any emote it is playing. */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityRenderStateAccess, EmoteRenderState {
    @Unique
    private Entity maro$entity;
    @Unique
    private Emote maro$emote;
    @Unique
    private float maro$emoteTime, maro$emoteBlend;

    @Override
    public void maro$setEmote(Emote emote, float time, float blend) {
        maro$emote = emote;
        maro$emoteTime = time;
        maro$emoteBlend = blend;
    }

    @Override
    public Emote maro$getEmote() {
        return maro$emote;
    }

    @Override
    public float maro$getEmoteTime() {
        return maro$emoteTime;
    }

    @Override
    public float maro$getEmoteBlend() {
        return maro$emoteBlend;
    }

    @Override
    public Entity maro$getEntity() {
        return maro$entity;
    }

    @Override
    public void maro$setEntity(Entity entity) {
        maro$entity = entity;
    }
}
