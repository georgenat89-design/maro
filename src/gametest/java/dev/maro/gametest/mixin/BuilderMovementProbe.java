package dev.maro.gametest.mixin;

import dev.maro.gametest.BuilderPacketChecks;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class BuilderMovementProbe {
    @Inject(method="sendMovementPackets",at=@At("HEAD"))
    private void maroTest$normalMovementStart(CallbackInfo info){BuilderPacketChecks.vanillaMovement=true;}
    @Inject(method="sendMovementPackets",at=@At("RETURN"))
    private void maroTest$normalMovementEnd(CallbackInfo info){BuilderPacketChecks.vanillaMovement=false;}
}
