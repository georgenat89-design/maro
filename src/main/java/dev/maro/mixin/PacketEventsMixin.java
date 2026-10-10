package dev.maro.mixin;
import dev.maro.runtime.RuntimeEvents;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class PacketEventsMixin {
    @Inject(method="onPlayerPositionLook",at=@At("RETURN"))
    private void maro$homeArrival(PlayerPositionLookS2CPacket packet,CallbackInfo info){
        dev.maro.module.impl.player.AutoBuilder.serverHomeArrival();
    }
    @Inject(method="onInventory",at=@At("RETURN"))
    private void maro$inventory(InventoryS2CPacket packet,CallbackInfo info){
        var builder=dev.maro.module.ModuleManager.get(dev.maro.module.impl.player.AutoBuilder.class);
        if(builder!=null){builder.chestInventoryReceived(packet.syncId());builder.placementInventoryReceived(packet.syncId(),-1);}
    }
    @Inject(method="onScreenHandlerSlotUpdate",at=@At("RETURN"))
    private void maro$placementSlot(ScreenHandlerSlotUpdateS2CPacket packet,CallbackInfo info){
        var builder=dev.maro.module.ModuleManager.get(dev.maro.module.impl.player.AutoBuilder.class);
        if(builder!=null)builder.placementInventoryReceived(packet.getSyncId(),packet.getSlot());
    }
    @Inject(method="onSetPlayerInventory",at=@At("RETURN"))
    private void maro$placementInventory(SetPlayerInventoryS2CPacket packet,CallbackInfo info){
        var builder=dev.maro.module.ModuleManager.get(dev.maro.module.impl.player.AutoBuilder.class);
        if(builder!=null&&packet.slot()<9)builder.placementInventoryReceived(0,36+packet.slot());
    }
    @Inject(method="onBlockBreakingProgress",at=@At("HEAD"))
    private void maro$breaking(BlockBreakingProgressS2CPacket packet,CallbackInfo info){RuntimeEvents.packet(packet);}
    @Inject(method="onWorldEvent",at=@At("HEAD"))
    private void maro$worldEvent(WorldEventS2CPacket packet,CallbackInfo info){RuntimeEvents.packet(packet);}
}
