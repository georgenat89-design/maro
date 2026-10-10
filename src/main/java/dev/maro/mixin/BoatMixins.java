package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.maro.module.impl.movement.BoatControl;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class BoatMixins {
    @Mixin(Entity.class)
    public static abstract class Movement {
        @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
        private Vec3d maro$boatMovement(Vec3d original, MovementType type) {
            return BoatControl.movement((Entity) (Object) this, type, original);
        }
    }
    @Mixin(AbstractBoatEntity.class)
    public static abstract class Boat {
        @ModifyExpressionValue(method = "updatePaddles", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;pressingLeft:Z"))
        private boolean maro$left(boolean original) { return original && !BoatControl.locksTurning((AbstractBoatEntity) (Object) this); }
        @ModifyExpressionValue(method = "updatePaddles", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;pressingRight:Z"))
        private boolean maro$right(boolean original) { return original && !BoatControl.locksTurning((AbstractBoatEntity) (Object) this); }
        @ModifyReturnValue(method = "collidesWith", at = @At("RETURN"))
        private boolean maro$collision(boolean original) { return original && !BoatControl.noClip((AbstractBoatEntity) (Object) this); }
    }
    @Mixin(ClientConnection.class)
    public static abstract class Packets {
        @ModifyVariable(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private Packet<?> maro$boatPacket(Packet<?> packet) { return BoatControl.rewrite((ClientConnection) (Object) this, packet); }
    }
    @Mixin(ClientPlayNetworkHandler.class)
    public static abstract class Corrections {
        @Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
        private void maro$boatCorrection(VehicleMoveS2CPacket packet, CallbackInfo info) {
            var module = BoatControl.active;
            if (module == null || !MinecraftClient.getInstance().isOnThread()) return;
            var player = MinecraftClient.getInstance().player;
            if (player == null || !module.controls(player.getVehicle())) return;
            module.forget();
            if (module.cancelCorrection()) info.cancel();
        }
    }
}
