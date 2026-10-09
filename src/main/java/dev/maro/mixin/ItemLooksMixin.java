package dev.maro.mixin;

import dev.maro.render.accessories.ItemLooks;
import dev.maro.render.accessories.ItemScale;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every item drawn passes here on its way to a model: Cosmetics' sword, pickaxe and shovel skins
 * and Fake Block's item look swap the model it is drawn with, and a skin in your hand takes
 * Cosmetics' Item Size. Only the drawing changes.
 */
@Mixin(ItemModelManager.class)
public abstract class ItemLooksMixin {
    @Unique
    private boolean maro$swapping;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void maro$look(ItemRenderState state, ItemStack stack, ItemDisplayContext context, World world, HeldItemContext holder, int seed,
                           CallbackInfo ci) {
        if (maro$swapping || stack.isEmpty()) return;
        ItemStack shown = ItemLooks.shown(stack, context, holder);
        if (shown == stack) return;
        maro$swapping = true;
        try {
            ((ItemModelManager) (Object) this).update(state, shown, context, world, holder, seed);
            float scale = ItemLooks.handScale(stack, context, holder);
            if (scale != 1) ((ItemScale) state).maro$setScale(scale);
        } finally {
            maro$swapping = false;
        }
        ci.cancel();
    }
}
