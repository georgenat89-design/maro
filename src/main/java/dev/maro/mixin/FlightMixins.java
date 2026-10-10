package dev.maro.mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.maro.module.impl.movement.Flight;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
public final class FlightMixins {
    @Mixin(ClientPlayerEntity.class)
    public static abstract class Player {
        @Inject(method = "tick", at = @At("HEAD"))
        private void maro$flightTick(CallbackInfo info) { Flight.beforeTick(); }
        @ModifyReturnValue(method = "isSneaking", at = @At("RETURN"))
        private boolean maro$noSneak(boolean original) { return original && !Flight.hideSneak((ClientPlayerEntity) (Object) this); }
    }
    @Mixin(PlayerEntity.class)
    public static abstract class Speed {
        @ModifyReturnValue(method = "getOffGroundSpeed", at = @At("RETURN"))
        private float maro$flightSpeed(float original) {
            return (Object) this instanceof ClientPlayerEntity player ? Flight.offGroundSpeed(player, original) : original;
        }
    }
    @Mixin(ClientPlayNetworkHandler.class)
    public static abstract class Abilities {
        @Inject(method = "onPlayerAbilities", at = @At("RETURN"))
        private void maro$flightAbilities(PlayerAbilitiesS2CPacket packet, CallbackInfo info) { Flight.abilitiesUpdated(); }
    }
    @Mixin(ClientConnection.class)
    public static abstract class Packets {
        @ModifyVariable(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private Packet<?> maro$flightPacket(Packet<?> packet) { return Flight.rewrite((ClientConnection) (Object) this, packet); }
    }
}
