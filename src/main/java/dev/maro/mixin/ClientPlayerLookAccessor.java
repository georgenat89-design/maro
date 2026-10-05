package dev.maro.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the look last published by vanilla; never emit an extra movement packet. */
@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerLookAccessor {
    @Accessor("lastYawClient") float maro$lastSentYaw();
    @Accessor("lastPitchClient") float maro$lastSentPitch();
}
