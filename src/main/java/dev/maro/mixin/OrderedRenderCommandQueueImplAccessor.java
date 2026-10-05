package dev.maro.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The per-order command batches, which the Player ESP's silhouette queue fills with its own kind. */
@Mixin(OrderedRenderCommandQueueImpl.class)
public interface OrderedRenderCommandQueueImplAccessor {
    @Accessor("batchingQueues")
    Int2ObjectAVLTreeMap<BatchingRenderCommandQueue> maro$getBatchingQueues();
}
