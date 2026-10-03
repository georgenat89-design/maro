package dev.maro.mixin;
import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.events.world.BlockUpdateEvent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
@Mixin(ClientWorld.class)
public abstract class BlockEventsMixin {
    @Inject(method="setBlockState",at=@At("HEAD"))
    private void maro$beforeBlock(BlockPos pos,BlockState next,int flags,int maxDepth,CallbackInfoReturnable<Boolean> result,
                                 @Share("maro-old-state") LocalRef<BlockState> old){
        ClientWorld world=(ClientWorld)(Object)this;
        old.set(world.getBlockState(pos));
    }
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void maro$afterBlock(BlockPos pos,BlockState next,int flags,int maxDepth,CallbackInfoReturnable<Boolean> result,
                                @Share("maro-old-state") LocalRef<BlockState> old){
        if(result.getReturnValue() && old.get()!=null && !old.get().equals(next))
            MeteorClient.EVENT_BUS.post(new BlockUpdateEvent(pos.toImmutable(),old.get(),next));
    }
}
