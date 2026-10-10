package dev.maro.mixin;

import dev.maro.module.impl.movement.AirStuck;
import dev.maro.module.impl.movement.NoFall;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class AirFallMixins {
    private AirFallMixins() { }

    @Mixin(ClientPlayerEntity.class)
    public static abstract class Player {
        @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
        private void maro$airStuck(CallbackInfo info) {
            if (AirStuck.freezing((ClientPlayerEntity) (Object) this)) info.cancel();
        }

        @Inject(method = "tick", at = @At("HEAD"))
        private void maro$noFall(CallbackInfo info) { NoFall.beforeTick(); }
    }

    @Mixin(ClientConnection.class)
    public static abstract class Packets {
        @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
        private void maro$freezePacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo info) {
            if (AirStuck.blocks((ClientConnection) (Object) this, packet)) info.cancel();
        }

        @ModifyVariable(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private Packet<?> maro$fallPacket(Packet<?> packet) {
            return NoFall.rewrite((ClientConnection) (Object) this, packet);
        }
    }
}
