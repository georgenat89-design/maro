package dev.maro.mixin;

import dev.maro.render.accessories.ItemScale;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmetics' Item Size: a skinned item carries its size on its render state and is drawn that much
 * bigger, wherever it is drawn from (first person, third person, the Cosmetics preview).
 */
@Mixin(ItemRenderState.class)
public abstract class ItemScaleMixin implements ItemScale {
    @Unique
    private float maro$scale = 1;
    @Unique
    private boolean maro$scaled;

    @Override
    public void maro$setScale(float scale) {
        maro$scale = scale;
    }

    @Override
    public float maro$scale() {
        return maro$scale;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void maro$clear(CallbackInfo ci) {
        maro$scale = 1;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void maro$grow(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, int outline, CallbackInfo ci) {
        if (maro$scale == 1) return;
        matrices.push();
        matrices.scale(maro$scale, maro$scale, maro$scale);
        maro$scaled = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void maro$restore(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, int outline, CallbackInfo ci) {
        if (!maro$scaled) return;
        maro$scaled = false;
        matrices.pop();
    }
}
