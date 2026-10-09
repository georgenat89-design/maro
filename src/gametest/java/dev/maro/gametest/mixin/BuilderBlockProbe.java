package dev.maro.gametest.mixin;

import dev.maro.gametest.BuilderBlockDelay;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class BuilderBlockProbe {
    @Inject(method="onBlockUpdate",at=@At("HEAD"),cancellable=true)
    private void maroTest$block(BlockUpdateS2CPacket packet,CallbackInfo info){if(BuilderBlockDelay.intercept(packet))info.cancel();}
    @Inject(method="onChunkDeltaUpdate",at=@At("HEAD"),cancellable=true)
    private void maroTest$delta(ChunkDeltaUpdateS2CPacket packet,CallbackInfo info){if(BuilderBlockDelay.intercept(packet))info.cancel();}
    @Inject(method="onPlayerActionResponse",at=@At("HEAD"),cancellable=true)
    private void maroTest$ack(PlayerActionResponseS2CPacket packet,CallbackInfo info){if(BuilderBlockDelay.intercept(packet))info.cancel();}
}
