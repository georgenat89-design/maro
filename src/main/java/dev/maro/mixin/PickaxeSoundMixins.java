package dev.maro.mixin;

import dev.maro.render.pickaxe.PickaxeSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class PickaxeSoundMixins {
    @Mixin(LivingEntity.class)
    public abstract static class Swing {
        @Inject(method="swingHand(Lnet/minecraft/util/Hand;Z)V",at=@At("TAIL"))
        private void maro$swing(Hand hand,boolean fromServer,CallbackInfo ci){
            var c=MinecraftClient.getInstance();if((Object)this==c.player&&hand==Hand.MAIN_HAND&&c.player.handSwingTicks==-1)PickaxeSounds.swing();
        }
    }
    @Mixin(ClientPlayerInteractionManager.class)
    public abstract static class Impact {
        @org.spongepowered.asm.mixin.Unique private boolean maro$attackSolid,maro$progressSolid;
        @Inject(method="attackBlock",at=@At("HEAD"))
        private void maro$beforeAttack(BlockPos pos,Direction direction,CallbackInfoReturnable<Boolean> ci){var c=MinecraftClient.getInstance();maro$attackSolid=c.world!=null&&!c.world.getBlockState(pos).isAir();}
        @Inject(method="attackBlock",at=@At("RETURN"))
        private void maro$afterAttack(BlockPos pos,Direction direction,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValue()&&maro$attackSolid)PickaxeSounds.impact();}
        @Inject(method="updateBlockBreakingProgress",at=@At("HEAD"))
        private void maro$beforeProgress(BlockPos pos,Direction direction,CallbackInfoReturnable<Boolean> ci){var c=MinecraftClient.getInstance();maro$progressSolid=c.world!=null&&!c.world.getBlockState(pos).isAir();}
        @Inject(method="updateBlockBreakingProgress",at=@At("RETURN"))
        private void maro$afterProgress(BlockPos pos,Direction direction,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValue()&&maro$progressSolid)PickaxeSounds.impact();}
        @Inject(method="attackEntity",at=@At("TAIL"))
        private void maro$entity(PlayerEntity player,Entity target,CallbackInfo ci){if(player==MinecraftClient.getInstance().player&&target instanceof LivingEntity)PickaxeSounds.impact();}
    }
}
