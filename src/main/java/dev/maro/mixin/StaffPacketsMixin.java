package dev.maro.mixin;
import dev.maro.anubis.StaffPacketHooks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class StaffPacketsMixin {
    @Inject(method={"onPlayerList","onPlayerRemove","onGameMessage","onCommandSuggestions",
        "onEntityTrackerUpdate","onExplosion","onPlaySoundFromEntity","onParticle","onPlaySound"},at=@At("HEAD"),cancellable=true)
    private void maro$staffReceive(@Coerce Packet<?> packet,CallbackInfo ci){if(StaffPacketHooks.receive(packet))ci.cancel();}
}
