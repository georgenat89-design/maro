package dev.maro.gametest.mixin;

import dev.maro.gametest.ChestTransferChecks;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ChestTransferProbe {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void maroTest$chestClick(ClickSlotC2SPacket packet, CallbackInfo info) {
        if (ChestTransferChecks.click(player, packet)) info.cancel();
    }
}
