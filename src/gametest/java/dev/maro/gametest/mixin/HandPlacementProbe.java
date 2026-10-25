package dev.maro.gametest.mixin;

import dev.maro.gametest.RedesignHandPlacementChecks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the real native item/animation path; it never changes render arguments. */
@Mixin(HeldItemRenderer.class)
public abstract class HandPlacementProbe {
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"))
    private void maroTest$nativeHands(AbstractClientPlayerEntity player,float tickProgress,float pitch,Hand hand,
            float swing,ItemStack stack,float equip,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){
        var arm=hand==Hand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
        RedesignHandPlacementChecks.nativeHand(arm,stack,swing,equip);
    }
}
