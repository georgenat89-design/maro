package dev.maro.gametest.mixin;

import dev.maro.gametest.BuilderPacketChecks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native teleport acknowledgement sends one movement packet outside the player tick. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class BuilderTeleportProbe {
    @Inject(method="onPlayerPositionLook",at=@At(value="INVOKE",target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V",shift=At.Shift.AFTER))
    private void maroTest$teleportStart(PlayerPositionLookS2CPacket packet,CallbackInfo info){BuilderPacketChecks.vanillaTeleport=true;}
    @Inject(method="onPlayerPositionLook",at=@At("RETURN"))
    private void maroTest$teleportEnd(PlayerPositionLookS2CPacket packet,CallbackInfo info){BuilderPacketChecks.vanillaTeleport=false;}
}
