package dev.maro.gametest.mixin;

import dev.maro.gametest.AirFallChecks;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class AirFallProbe {
    @Inject(method = "sendImmediately", at = @At("HEAD"))
    private void maroTest$sent(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo info) {
        AirFallChecks.sent((ClientConnection) (Object) this, packet);
        dev.maro.gametest.AntiCheatOffChecks.sent((ClientConnection) (Object) this, packet);
    }
}
