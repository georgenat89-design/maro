package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.MaroRelog;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class RelogTeleportMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
    private void maro$relogArrival(PlayerPositionLookS2CPacket packet, CallbackInfo info) {
        var relog = ModuleManager.get(MaroRelog.class);
        if (relog != null) relog.serverTeleport();
    }
}
