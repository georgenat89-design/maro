package dev.maro.gametest.mixin;

import dev.maro.gametest.BuilderChestDelay;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class BuilderChestProbe {
    @Inject(method="onInventory",at=@At("HEAD"),cancellable=true)
    private void maroTest$holdContents(InventoryS2CPacket packet,CallbackInfo info){if(BuilderChestDelay.intercept(packet))info.cancel();}
}
