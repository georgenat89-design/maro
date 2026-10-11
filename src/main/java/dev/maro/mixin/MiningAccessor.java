package dev.maro.mixin;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ClientPlayerInteractionManager.class)
public interface MiningAccessor {
    @Accessor("currentBreakingProgress") float maro$progress();
    @Accessor("currentBreakingProgress") void maro$progress(float value);
    @Accessor("currentBreakingPos") BlockPos maro$breakingPos();
    @Accessor("blockBreakingCooldown") void maro$cooldown(int ticks);
    @Accessor("blockBreakingCooldown") int maro$cooldown();
    @Invoker("sendSequencedPacket") void maro$sequenced(ClientWorld world, SequencedPacketCreator creator);
}
