package dev.maro.mixin;

import dev.maro.chunkpulse.PacketSniffer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive HEAD observers only. Vanilla receives the same packet and continues normally. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ChunkPulsePacketsMixin {
    @Inject(method={"onBlockEvent","onWorldEvent","onBlockUpdate","onChunkDeltaUpdate","onLightUpdate",
        "onPlaySound","onPlaySoundFromEntity","onParticle","onEntitySpawn","onEntitiesDestroy","onEntity",
        "onEntityPosition","onEntityPositionSync","onEntityTrackerUpdate","onBlockEntityUpdate"},at=@At("HEAD"))
    private void maro$observeActivity(@Coerce Object packet,CallbackInfo info){PacketSniffer.receive(packet);}
}
