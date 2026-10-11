package dev.maro.mixin;

import dev.maro.render.emote.EmoteRenderState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: once the game has posed a player model, the emote its render state carries takes over. */
@Mixin(BipedEntityModel.class)
public abstract class EmoteModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("RETURN"))
    private void maro$emote(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof EmoteRenderState emote) || emote.maro$getEmote() == null) return;
        try {
            emote.maro$getEmote().pose(emote.maro$getEmoteTime()).apply((BipedEntityModel<?>) (Object) this, emote.maro$getEmoteBlend());
        } catch (RuntimeException ignored) {
            // A pose that cannot be worked out leaves the player as the game drew them.
        }
    }
}
