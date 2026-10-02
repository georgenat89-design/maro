package dev.maro.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Accessor("itemUseCooldown")
    int maro$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void maro$setItemUseCooldown(int ticks);
}
