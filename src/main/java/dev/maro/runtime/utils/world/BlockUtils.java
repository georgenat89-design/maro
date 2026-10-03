package dev.maro.runtime.utils.world;

import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
public final class BlockUtils {
 public static boolean canPlace(BlockPos pos,boolean entities){var world=MinecraftClient.getInstance().world;return world!=null&&world.getBlockState(pos).isReplaceable()&&(!entities||world.canPlace(Blocks.ENDER_CHEST.getDefaultState(),pos,ShapeContext.absent()));}
 public static boolean isClickable(Block block){return block instanceof AbstractChestBlock<?>||block instanceof AbstractFurnaceBlock||block instanceof CraftingTableBlock||block instanceof AnvilBlock||block instanceof BarrelBlock||block instanceof ShulkerBoxBlock||block instanceof HopperBlock||block instanceof BrewingStandBlock||block instanceof DoorBlock||block instanceof TrapdoorBlock||block instanceof FenceGateBlock||block instanceof ButtonBlock||block instanceof LeverBlock||block instanceof BedBlock;}
}
