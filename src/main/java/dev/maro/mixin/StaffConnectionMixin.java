package dev.maro.mixin;
import dev.maro.anubis.StaffPacketHooks;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientConnection.class)
public abstract class StaffConnectionMixin {
    @Inject(method="send(Lnet/minecraft/network/packet/Packet;)V",at=@At("HEAD"))
    private void maro$staffSend(Packet<?> packet,CallbackInfo ci){StaffPacketHooks.send(packet);}
}
