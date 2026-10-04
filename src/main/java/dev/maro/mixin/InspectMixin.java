package dev.maro.mixin;

import dev.maro.module.impl.visuals.ItemInspect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Inspect: turns the item in your own first-person hand just before it is drawn. The caller
 * pushes and pops the matrix stack round this, so the turn goes no further than the item.
 */
@Mixin(HeldItemRenderer.class)
public abstract class InspectMixin {
    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void maro$inspect(LivingEntity entity, ItemStack stack, ItemDisplayContext context, MatrixStack matrices,
                              OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        boolean mainLeft = entity.getMainArm() == net.minecraft.util.Arm.LEFT;
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && !mainLeft) ItemInspect.transform(matrices, false);
        else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND && mainLeft) ItemInspect.transform(matrices, true);
    }
}
