package dev.maro.mixin;
import dev.maro.runtime.RuntimeEvents;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PacketEventsMixin {
    @Inject(method="onBlockBreakingProgress",at=@At("HEAD"))
    private void maro$breaking(BlockBreakingProgressS2CPacket packet,CallbackInfo info){RuntimeEvents.packet(packet);}
    @Inject(method="onWorldEvent",at=@At("HEAD"))
    private void maro$worldEvent(WorldEventS2CPacket packet,CallbackInfo info){RuntimeEvents.packet(packet);}
}
