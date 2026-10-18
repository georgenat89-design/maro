package dev.maro.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Invoker("doAttack") boolean maro$attack();
    @Invoker("doItemUse") void maro$useItem();
    @Invoker("handleBlockBreaking") void maro$breakBlock(boolean held);
    @Accessor("attackCooldown") void maro$setAttackCooldown(int ticks);
    @Accessor("itemUseCooldown")
    int maro$getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void maro$setItemUseCooldown(int ticks);
}
