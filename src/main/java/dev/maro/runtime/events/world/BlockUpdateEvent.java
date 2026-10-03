package dev.maro.runtime.events.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
public final class BlockUpdateEvent { public final BlockPos pos; public final BlockState oldState, newState; public BlockUpdateEvent(BlockPos p, BlockState old, BlockState next) { pos = p; oldState = old; newState = next; } }
