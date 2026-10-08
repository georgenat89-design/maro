package dev.maro.mixin;

import dev.maro.module.impl.visuals.CustomTotem;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Totem: the pop animation hidden, or drawn bigger or smaller around the middle of the screen. */
@Mixin(InGameOverlayRenderer.class)
public abstract class CustomTotemPopMixin {
    @Unique
    private boolean maro$scaled;

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void maro$popStart(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (!CustomTotem.showsPop()) {
            ci.cancel();
            return;
        }
        float scale = CustomTotem.popScale();
        if (scale == 1f) return;
        matrices.push();
        // Seen through the camera's perspective, scaling across the view grows the whole animation
        // about the centre of the screen, its path included.
        matrices.scale(scale, scale, 1f);
        maro$scaled = true;
    }

    @Inject(method = "renderFloatingItem", at = @At("RETURN"))
    private void maro$popEnd(MatrixStack matrices, float tickProgress, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (!maro$scaled) return;
        maro$scaled = false;
        matrices.pop();
    }
}
