/*
 * Adapted from LogicalZoomMixin in Logical Zoom by LogicalGeekBoy and
 * contributors - https://github.com/LogicalGeekBoy/logical_zoom - under the MIT
 * licence: Copyright 2019 LogicalGeekBoy. The full notice is in
 * LICENSES/MIT-LogicalZoom.txt, which ships in the jar.
 */
package dev.maro.nathan.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import dev.maro.nathan.modules.FreeCam;
import dev.maro.nathan.modules.KeyZoom;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Leaves your hands out of the picture while Key Zoom is zoomed in first
 * person, and while Free Cam is out - when they would hang in front of a camera
 * that is nowhere near them. It wraps the one call that draws them and nothing else in the method,
 * so whatever else is drawn over the view - fire, water, a block your head is
 * in - is drawn as usual.
 */
@Mixin(GameRenderer.class)
public abstract class HandsMixin {
    @WrapWithCondition(
        method = "renderHand(FZLorg/joml/Matrix4f;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"
        )
    )
    private boolean nameeprotect$hideHandsWhenZooming(HeldItemRenderer renderer, float partialTick, MatrixStack poseStack,
                                                     OrderedRenderCommandQueue collector, ClientPlayerEntity player, int packedLight) {
        return !KeyZoom.hidingHands() && !FreeCam.active();
    }
}
