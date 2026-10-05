package dev.maro.gametest.mixin;

import dev.maro.gametest.BuilderPacketChecks;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class BuilderPacketProbe {
    @Inject(method="send(Lnet/minecraft/network/packet/Packet;)V",at=@At("HEAD"))
    private void maroTest$outbound(Packet<?> packet,CallbackInfo info){BuilderPacketChecks.outbound(packet);}
}
