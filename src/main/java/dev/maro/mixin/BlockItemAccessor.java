package dev.maro.mixin;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(BlockItem.class)
public interface BlockItemAccessor {
    @Invoker("getPlacementState") BlockState maro$placementState(ItemPlacementContext context);
}
