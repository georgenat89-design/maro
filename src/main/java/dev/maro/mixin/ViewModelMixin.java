package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.module.impl.visuals.ViewModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * View Model: each first-person hand is drawn inside its own push and pop, moved first; the item
 * itself is turned and sized just before it is drawn, about its own middle.
 */
@Mixin(HeldItemRenderer.class)
public abstract class ViewModelMixin {
    @WrapOperation(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void maro$viewModel(HeldItemRenderer renderer, AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                OrderedRenderCommandQueue queue, int light, Operation<Void> original) {
        boolean mainHand = hand == Hand.MAIN_HAND;
        if (!mainHand && ViewModel.hideOffHand()) return;
        Arm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        matrices.push();
        ViewModel.handTransform(matrices, mainHand, arm == Arm.LEFT);
        // Better Looks' Low Shield.
        if (item.isOf(net.minecraft.item.Items.SHIELD) && dev.maro.module.impl.visuals.BetterLooks.lowShield()) matrices.translate(0, -0.22, 0);
        original.call(renderer, player, tickProgress, pitch, hand, ViewModel.swingProgress(swingProgress, mainHand, item), item,
                ViewModel.equipProgress(equipProgress), matrices, queue, light);
        matrices.pop();
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void maro$viewModelItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, MatrixStack matrices,
                                    OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player) return;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        boolean leftArm = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean mainHand = leftArm == (entity.getMainArm() == Arm.LEFT);
        ViewModel.itemTransform(matrices, mainHand, leftArm);
        // Better Looks' Short Sword.
        if (stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) && dev.maro.module.impl.visuals.BetterLooks.shortSword()) {
            matrices.scale(0.72f, 0.72f, 0.72f);
        }
        // Cosmetics' Item Size, for skinned swords, pickaxes and shovels.
        if (dev.maro.module.impl.visuals.SkinAccessories.skinFor(stack) != null) {
            float size = dev.maro.module.impl.visuals.SkinAccessories.itemScale();
            if (size != 1) matrices.scale(size, size, size);
        }
    }
}
