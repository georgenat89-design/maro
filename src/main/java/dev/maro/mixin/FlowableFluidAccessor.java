package dev.maro.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only vanilla fluid feasibility queries for contained bucket placement. */
@Mixin(FlowableFluid.class)
public interface FlowableFluidAccessor {
    @Invoker("receivesFlow")
    static boolean maro$receivesFlow(Direction direction,BlockView world,BlockPos from,BlockState source,BlockPos to,BlockState destination){throw new AssertionError();}
    @Invoker("canFill")
    static boolean maro$canFill(BlockView world,BlockPos pos,BlockState state,Fluid fluid){throw new AssertionError();}
}
