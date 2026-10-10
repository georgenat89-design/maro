package dev.maro.mixin;

import dev.maro.module.impl.visuals.Nametags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The hooks behind {@link Nametags}. */
public final class NametagsMixins {
    private NametagsMixins() {
    }

    /** The game's own name label is left off players Nametags draws a tag for. */
    @Mixin(EntityRenderer.class)
    public abstract static class HideLabel {
        @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
        private void maro$hideLabel(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
            if (!Nametags.hidesVanilla(entity)) return;
            EntityRenderState state = cir.getReturnValue();
            state.displayName = null;
            state.nameLabelPos = null;
        }
    }

    /** Totem pops and deaths, as the server reports them, for the pop count. */
    @Mixin(ClientPlayNetworkHandler.class)
    public abstract static class Pops {
        @Inject(method = "onEntityStatus", at = @At("RETURN"))
        private void maro$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) Nametags.entityStatus(entity, packet.getStatus());
        }
    }
}
