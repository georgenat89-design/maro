package dev.maro.mixin;

import dev.maro.inventory.ChestSessions;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class ChestTransferMixins {
    private ChestTransferMixins() { }

    @Mixin(ClientPlayerInteractionManager.class)
    public static abstract class Interaction {
        @Inject(method = "interactBlock", at = @At("HEAD"))
        private void maro$container(ClientPlayerEntity player, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> info) {
            ChestSessions.interact(hit.getBlockPos());
        }
    }

    @Mixin(ClientPlayNetworkHandler.class)
    public static abstract class Packets {
        @Inject(method = "onOpenScreen", at = @At("RETURN"))
        private void maro$opened(OpenScreenS2CPacket packet, CallbackInfo info) { ChestSessions.opened(); }

        @Inject(method = "onInventory", at = @At("RETURN"))
        private void maro$contents(InventoryS2CPacket packet, CallbackInfo info) { ChestSessions.contents(packet.syncId()); }

        @Inject(method = "sendChatCommand", at = @At("HEAD"))
        private void maro$command(String command, CallbackInfo info) { ChestSessions.command(); }
    }
}
