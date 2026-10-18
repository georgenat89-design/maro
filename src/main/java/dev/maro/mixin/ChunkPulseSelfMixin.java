package dev.maro.mixin;

import dev.maro.module.impl.visuals.RedstoneBypass;
import net.minecraft.client.network.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only intent masks. All vanilla break/use paths continue without alteration. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ChunkPulseSelfMixin {
    @Inject(method={"attackBlock","updateBlockBreakingProgress"},at=@At("HEAD"))
    private void maro$pulseBreak(BlockPos pos,Direction side,CallbackInfoReturnable<Boolean> info){RedstoneBypass.selfAction(pos,false);}
    @Inject(method="breakBlock",at=@At("HEAD"))
    private void maro$pulseBroken(BlockPos pos,CallbackInfoReturnable<Boolean> info){RedstoneBypass.selfAction(pos,false);}
    @Inject(method="interactBlock",at=@At("HEAD"))
    private void maro$pulseUse(ClientPlayerEntity player,Hand hand,BlockHitResult hit,CallbackInfoReturnable<ActionResult> info){
        boolean light=RedstoneBypass.lightItem(player.getStackInHand(hand));
        RedstoneBypass.selfAction(hit.getBlockPos(),light);RedstoneBypass.selfAction(hit.getBlockPos().offset(hit.getSide()),light);
    }
}
