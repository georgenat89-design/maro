package dev.maro.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Use vanilla movement publication and its tracked view instead of hand-built packets. */
@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerLookAccessor {
    @Accessor("lastYawClient") float maro$lastSentYaw();
    @Accessor("lastPitchClient") float maro$lastSentPitch();
    @Invoker("sendMovementPackets") void maro$publishMovement();
}
