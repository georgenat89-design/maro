package dev.maro.mixin;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoBuilder;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Run an aimed builder action after this tick's ordinary movement publication. */
@Mixin(ClientPlayerEntity.class)
public abstract class BuilderMovementMixin {
    @Inject(method="sendMovementPackets",at=@At("HEAD"))
    private void maro$builderFinalAim(CallbackInfo info){
        var builder=ModuleManager.get(AutoBuilder.class);if(builder!=null)builder.beforeNormalMovement();
    }
    @Inject(method="sendMovementPackets",at=@At("RETURN"))
    private void maro$builderInteraction(CallbackInfo info){
        var builder=ModuleManager.get(AutoBuilder.class);if(builder!=null)builder.afterNormalMovement();
    }
}
