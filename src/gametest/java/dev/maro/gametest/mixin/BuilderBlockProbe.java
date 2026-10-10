package dev.maro.gametest.mixin;

import dev.maro.gametest.BuilderBlockDelay;
import dev.maro.gametest.BuilderPlacementProbe;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class BuilderBlockProbe {
    @Inject(method="onInventory",at=@At("HEAD"),cancellable=true)
    private void maroTest$inventory(InventoryS2CPacket packet,CallbackInfo info){if(BuilderPlacementProbe.intercept(packet))info.cancel();}
    @Inject(method="onScreenHandlerSlotUpdate",at=@At("HEAD"),cancellable=true)
    private void maroTest$slot(ScreenHandlerSlotUpdateS2CPacket packet,CallbackInfo info){if(BuilderPlacementProbe.intercept(packet))info.cancel();}
    @Inject(method="onSetPlayerInventory",at=@At("HEAD"),cancellable=true)
    private void maroTest$playerInventory(SetPlayerInventoryS2CPacket packet,CallbackInfo info){if(BuilderPlacementProbe.intercept(packet))info.cancel();}
    @Inject(method="onSetCursorItem",at=@At("HEAD"),cancellable=true)
    private void maroTest$cursor(SetCursorItemS2CPacket packet,CallbackInfo info){if(BuilderPlacementProbe.intercept(packet))info.cancel();}
    @Inject(method="onBlockUpdate",at=@At("HEAD"),cancellable=true)
    private void maroTest$block(BlockUpdateS2CPacket packet,CallbackInfo info){if(BuilderBlockDelay.intercept(packet))info.cancel();}
    @Inject(method="onChunkDeltaUpdate",at=@At("HEAD"),cancellable=true)
    private void maroTest$delta(ChunkDeltaUpdateS2CPacket packet,CallbackInfo info){if(BuilderBlockDelay.intercept(packet))info.cancel();}
    @Inject(method="onPlayerActionResponse",at=@At("HEAD"),cancellable=true)
    private void maroTest$ack(PlayerActionResponseS2CPacket packet,CallbackInfo info){if(BuilderBlockDelay.intercept(packet))info.cancel();}
}
