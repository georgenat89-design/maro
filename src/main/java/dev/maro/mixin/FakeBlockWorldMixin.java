package dev.maro.mixin;

import dev.maro.module.impl.visuals.FakeBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fake Block's Spoof World: the chunk meshes are built from the copy of the blocks taken for drawing, so swapping here changes only the picture. */
@Mixin(targets = "net.minecraft.client.render.chunk.RenderedChunk")
public abstract class FakeBlockWorldMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void maro$fakeBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!FakeBlock.spoofingWorld()) return;
        BlockState shown = FakeBlock.worldLook(cir.getReturnValue());
        if (shown != cir.getReturnValue()) cir.setReturnValue(shown);
    }
}
