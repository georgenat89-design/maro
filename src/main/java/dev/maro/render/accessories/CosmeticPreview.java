package dev.maro.render.accessories;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;

/** Puts an item in the preview player's hand, so a weapon skin can be seen before it is applied. */
public final class CosmeticPreview {
    private CosmeticPreview() {
    }

    public static void hold(PlayerEntityRenderState state, ItemStack stack, LivingEntity player) {
        if (stack.isEmpty()) return;
        boolean right = state.mainArm == Arm.RIGHT;
        var itemState = right ? state.rightHandItemState : state.leftHandItemState;
        MinecraftClient.getInstance().getItemModelManager().updateForLivingEntity(itemState, stack,
                right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, player);
        if (right) {
            state.rightHandItem = stack;
            state.rightArmPose = BipedEntityModel.ArmPose.ITEM;
        } else {
            state.leftHandItem = stack;
            state.leftArmPose = BipedEntityModel.ArmPose.ITEM;
        }
    }
}
